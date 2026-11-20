package com.example.musicapp.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicapp.data.catalog.AlbumRef
import com.example.musicapp.data.catalog.ChannelRef
import com.example.musicapp.data.catalog.PlaylistRef
import com.example.musicapp.data.catalog.SaavnCatalog
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.recommend.MixCard
import com.example.musicapp.data.recommend.RecommendationEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Home sections. A null list means "still loading"; empty means "nothing to show". */
data class HomeState(
    val madeForYou: List<MixCard>? = null,
    val becauseSeed: SongInfo? = null,
    val becauseSongs: List<SongInfo> = emptyList(),
    val topArtists: List<String> = emptyList(),
    val charts: List<PlaylistRef>? = null,
    val newReleases: List<AlbumRef>? = null,
    val featured: List<PlaylistRef>? = null,
    val moods: List<ChannelRef>? = null
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val engine = RecommendationEngine.getInstance(app)
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var loadedAtMs = 0L

    init {
        refresh(force = true)
    }

    /** Reloads sections (each independently, so a slow one never blocks the rest). */
    fun refresh(force: Boolean = false) {
        if (!force && System.currentTimeMillis() - loadedAtMs < 10 * 60 * 1000L) return
        loadedAtMs = System.currentTimeMillis()
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val taste = runCatching { engine.taste() }.getOrNull()
            _state.update { it.copy(topArtists = taste?.topArtists.orEmpty().take(10)) }
            launch {
                val mixes = runCatching { engine.dailyMixes() }.getOrDefault(emptyList())
                val extra = listOf(
                    MixCard("fresh", "Fresh Finds", "New to you, every week", mixes.getOrNull(1)?.imageUrl ?: mixes.firstOrNull()?.imageUrl.orEmpty()),
                    MixCard("radar", "Release Radar", "New music from your artists", mixes.firstOrNull()?.imageUrl.orEmpty())
                )
                _state.update { it.copy(madeForYou = mixes + extra) }
            }
            launch {
                val because = runCatching { engine.becauseYouListened() }.getOrNull()
                _state.update { it.copy(becauseSeed = because?.first, becauseSongs = because?.second.orEmpty()) }
            }
            launch { _state.update { it.copy(charts = runCatching { SaavnCatalog.charts() }.getOrDefault(emptyList())) } }
            launch { _state.update { it.copy(newReleases = runCatching { SaavnCatalog.newAlbums(20) }.getOrDefault(emptyList())) } }
            launch { _state.update { it.copy(featured = runCatching { SaavnCatalog.featuredPlaylists(12) }.getOrDefault(emptyList())) } }
            launch { _state.update { it.copy(moods = runCatching { SaavnCatalog.moodChannels() }.getOrDefault(emptyList())) } }
        }
    }
}

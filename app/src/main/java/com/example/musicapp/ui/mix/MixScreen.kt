package com.example.musicapp.ui.mix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.musicapp.data.catalog.PodcastShow
import com.example.musicapp.data.catalog.Podcasts
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.recommend.RecommendationEngine
import com.example.musicapp.ui.components.TrackListPage
import com.example.musicapp.ui.main.MainScreenViewModel
import com.example.musicapp.ui.navigation.Screen
import kotlinx.serialization.json.Json

/**
 * Any generated list: Daily Mixes, Fresh Finds, Release Radar, song/artist radio, charts,
 * mood channels and podcast shows. What [Screen.Mix.mixId] means is decided by
 * [RecommendationEngine.mixSongs] (and podcasts here).
 */
@Composable
fun MixScreen(
    screen: Screen.Mix,
    viewModel: MainScreenViewModel,
    onBack: () -> Unit,
    onNavigate: (Screen) -> Unit
) {
    val context = LocalContext.current
    var songs by remember(screen.mixId) { mutableStateOf<List<SongInfo>?>(null) }
    LaunchedEffect(screen.mixId) {
        songs = runCatching {
            if (screen.mixId.startsWith("podcast:")) {
                val show = Json.decodeFromString<PodcastShow>(screen.mixId.removePrefix("podcast:"))
                Podcasts.episodes(show)
            } else {
                RecommendationEngine.getInstance(context).mixSongs(screen.mixId)
            }
        }.getOrDefault(emptyList())
    }
    val reason = when {
        screen.mixId == "fresh" -> "New to you, picked from what you play most. Refreshes every week."
        screen.mixId == "radar" -> "Latest releases from artists you follow and play."
        screen.mixId.startsWith("daily:") -> "Built from what you listen to. Change how adventurous it is in Settings → Playback → Discovery."
        screen.mixId.startsWith("radio:") -> "Keeps going with similar music."
        else -> ""
    }
    TrackListPage(
        title = screen.title,
        subtitle = screen.subtitle,
        coverUrl = screen.coverUrl,
        songs = songs,
        viewModel = viewModel,
        onBack = onBack,
        onNavigate = onNavigate,
        reason = reason,
        numbered = screen.mixId.startsWith("playlist:")
    )
}

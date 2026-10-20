package com.example.musicapp.ui.main

import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.musicapp.data.DataRepository
import com.example.musicapp.data.MusicCatalog
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.service.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import androidx.core.content.ContextCompat
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.util.concurrent.ConcurrentHashMap

sealed interface MainScreenUiState {
    object Idle : MainScreenUiState
    object Loading : MainScreenUiState
    data class Error(val message: String) : MainScreenUiState
    data class Success(val songs: List<SongInfo>) : MainScreenUiState
}

@OptIn(FlowPreview::class)
class MainScreenViewModel(private val dataRepository: DataRepository, private val context: Context) : ViewModel() {
    private val _uiState = MutableStateFlow<MainScreenUiState>(MainScreenUiState.Idle)
    val uiState: StateFlow<MainScreenUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    
    private val searchCache = ConcurrentHashMap<String, List<SongInfo>>()
    private var searchJob: kotlinx.coroutines.Job? = null
    private val fallbackAttemptedTrackIds = mutableSetOf<String>()
    private var bufferingWatchdog: kotlinx.coroutines.Job? = null

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var controllerListener: Player.Listener? = null
    private val nextTrackCallback: () -> Unit = { skipToNext() }
    private val previousTrackCallback: () -> Unit = { skipToPrevious() }

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentSong = MutableStateFlow<SongInfo?>(null)
    val currentSong: StateFlow<SongInfo?> = _currentSong.asStateFlow()

    private val _loadingSongVideoId = MutableStateFlow<String?>(null)
    val loadingSongVideoId: StateFlow<String?> = _loadingSongVideoId.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _isRepeat = MutableStateFlow(false)
    val isRepeat: StateFlow<Boolean> = _isRepeat.asStateFlow()

    private val _currentQueue = MutableStateFlow<List<SongInfo>>(emptyList())
    val currentQueue: StateFlow<List<SongInfo>> = _currentQueue.asStateFlow()

    private var currentQueueIndex = 0
    private var unshuffledQueue: List<SongInfo> = emptyList()

    fun getUpcomingQueue(): List<SongInfo> {
        val queue = _currentQueue.value
        val firstUpcoming = (currentQueueIndex + 1).coerceIn(0, queue.size)
        return queue.drop(firstUpcoming)
    }

    fun moveUpcomingQueueItem(fromIndex: Int, toIndex: Int) {
        val queue = _currentQueue.value.toMutableList()
        val upcomingStart = currentQueueIndex + 1
        val from = upcomingStart + fromIndex
        val to = upcomingStart + toIndex
        if (from !in upcomingStart until queue.size || to !in upcomingStart until queue.size || from == to) return
        val moved = queue.removeAt(from)
        queue.add(to, moved)
        _currentQueue.value = queue
        // A manual reorder takes precedence over random shuffle ordering.
        _isShuffle.value = false
        unshuffledQueue = queue.toList()
    }

    fun playNext(song: SongInfo) {
        val queue = _currentQueue.value.toMutableList()
        val current = _currentSong.value
        if (queue.isEmpty()) {
            if (current != null) {
                _currentQueue.value = listOf(current, song)
                currentQueueIndex = 0
            } else {
                playSong(song)
                return
            }
        } else {
            val insertIndex = (currentQueueIndex + 1).coerceIn(0, queue.size)
            queue.add(insertIndex, song)
            _currentQueue.value = queue
        }
        // Manual "play next" takes precedence over random shuffle ordering
        _isShuffle.value = false
        unshuffledQueue = _currentQueue.value.toList()
        dataRepository.prefetchStreamUrls(listOf(song))
    }

    fun addToQueue(song: SongInfo) {
        val queue = _currentQueue.value.toMutableList()
        val current = _currentSong.value
        if (queue.isEmpty()) {
            if (current != null) {
                _currentQueue.value = listOf(current, song)
                currentQueueIndex = 0
            } else {
                playSong(song)
                return
            }
        } else {
            queue.add(song)
            _currentQueue.value = queue
        }
        unshuffledQueue = _currentQueue.value.toList()
        dataRepository.prefetchStreamUrls(listOf(song))
    }

    private val _activeAudioProfile = MutableStateFlow<com.example.musicapp.audio.ActiveAudioProfile?>(null)
    val activeAudioProfile: StateFlow<com.example.musicapp.audio.ActiveAudioProfile?> = _activeAudioProfile.asStateFlow()

    fun setAudioPreset(mode: com.example.musicapp.audio.EqPresetMode) {
        PlaybackService.activeAudioEffectManager?.setPreset(mode)
        PlaybackService.activeAudioEffectManager?.activeProfile?.value?.let {
            _activeAudioProfile.value = it
        }
    }

    fun setBassBoostStrength(strength: Short) {
        PlaybackService.activeAudioEffectManager?.setBassBoostStrength(strength)
        PlaybackService.activeAudioEffectManager?.activeProfile?.value?.let {
            _activeAudioProfile.value = it
        }
    }

    fun setVirtualizerStrength(strength: Short) {
        PlaybackService.activeAudioEffectManager?.setVirtualizerStrength(strength)
        PlaybackService.activeAudioEffectManager?.activeProfile?.value?.let {
            _activeAudioProfile.value = it
        }
    }

    fun setLoudnessGain(gainMb: Int) {
        PlaybackService.activeAudioEffectManager?.setLoudnessGain(gainMb)
    }

    fun downloadAlbum(songs: List<SongInfo>) {
        if (songs.isEmpty()) return
        if (com.example.musicapp.data.auth.UserManager.getInstance(context).currentUser.value.isGuest) {
            android.widget.Toast.makeText(context, "Sign in to download music", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val manager = com.example.musicapp.data.download.MusicDownloadManager.getInstance(context)
        val pending = songs.filterNot { manager.isDownloaded(it.videoId) }
        if (pending.isEmpty()) {
            android.widget.Toast.makeText(context, "This album is already downloaded", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        pending.forEach { song ->
            manager.downloadSong(song, streamUrlProvider = { track ->
                dataRepository.getStreamUrl(track.videoId, track.title, track.artist, track.durationSeconds)
                    ?: dataRepository.getFallbackStreamUrl(track.videoId, track.title, track.artist, track.durationSeconds)
            }, notify = false)
        }
        android.widget.Toast.makeText(context, "Downloading ${pending.size} album tracks", android.widget.Toast.LENGTH_SHORT).show()
    }

    init {
        PlaybackService.onNextTrackRequested = nextTrackCallback
        PlaybackService.onPreviousTrackRequested = previousTrackCallback

        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                val controller = runCatching { future.get() }.getOrNull()
                if (controller == null) {
                    _isPlaying.value = false
                    return@addListener
                }
                mediaController = controller
                val listener = object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        syncPlaybackState(controller)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        syncPlaybackState(controller)
                        if (playbackState == Player.STATE_READY) {
                            bufferingWatchdog?.cancel()
                            _loadingSongVideoId.value = null
                        } else if (playbackState == Player.STATE_BUFFERING) {
                            _currentSong.value?.let { song ->
                                _loadingSongVideoId.value = song.videoId
                                mediaController?.let { watchForStalledPlayback(song, it) }
                            }
                        }
                        if (playbackState == Player.STATE_ENDED) {
                            if (_isRepeat.value) {
                                controller.seekTo(0L)
                                controller.play()
                            } else {
                                skipToNext()
                            }
                        }
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        handlePlaybackError(error)
                    }
                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int
                    ) {
                        syncPlaybackState(controller)
                    }
                }
                controllerListener = listener
                controller.addListener(listener)
                // A ViewModel can reconnect to a player that is already running.
                // Initialize all observable state instead of waiting for the next callback.
                syncPlaybackState(controller)
            },
            ContextCompat.getMainExecutor(context)
        )

        // Progress polling loop (250ms)
        viewModelScope.launch {
            while (true) {
                mediaController?.let { controller ->
                    if (controller.isPlaying) {
                        _currentPosition.value = controller.currentPosition
                        if (controller.duration > 0) {
                            _duration.value = controller.duration
                        }
                    }
                }
                PlaybackService.activeAudioEffectManager?.activeProfile?.value?.let { profile ->
                    if (_activeAudioProfile.value != profile) {
                        _activeAudioProfile.value = profile
                    }
                }
                kotlinx.coroutines.delay(250)
            }
        }

        // Debounced search
        viewModelScope.launch {
            _searchQuery
                .debounce(350)
                .collect { query ->
                    val trimmed = query.trim()
                    if (trimmed.isNotBlank()) {
                        executeSearch(trimmed)
                    }
                }
        }
    }

    override fun onCleared() {
        controllerListener?.let { listener -> mediaController?.removeListener(listener) }
        controllerListener = null
        mediaController = null
        if (PlaybackService.onNextTrackRequested === nextTrackCallback) PlaybackService.onNextTrackRequested = null
        if (PlaybackService.onPreviousTrackRequested === previousTrackCallback) PlaybackService.onPreviousTrackRequested = null
        super.onCleared()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }

    private fun syncPlaybackState(controller: MediaController) {
        _isPlaying.value = controller.isPlaying
        _currentPosition.value = controller.currentPosition.coerceAtLeast(0L)
        _duration.value = controller.duration.takeIf { it > 0L } ?: 0L

        val item = controller.currentMediaItem ?: return
        val mediaId = item.mediaId.takeIf { it.isNotBlank() } ?: return
        val queuedSong = _currentQueue.value.firstOrNull { it.videoId == mediaId }
        val activeSong = when {
            queuedSong != null -> queuedSong
            _currentSong.value?.videoId == mediaId -> _currentSong.value
            else -> {
                val metadata = item.mediaMetadata
                SongInfo(
                    videoId = mediaId,
                    title = metadata.title?.toString().orEmpty(),
                    artist = metadata.artist?.toString().orEmpty(),
                    albumArtUrl = metadata.artworkUri?.toString().orEmpty(),
                    durationSeconds = _duration.value / 1_000L
                )
            }
        }
        _currentSong.value = activeSong
        val queueIndex = _currentQueue.value.indexOfFirst { it.videoId == mediaId }
        if (queueIndex >= 0) currentQueueIndex = queueIndex
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun search() {
        val query = _searchQuery.value.trim()
        if (query.isNotBlank()) {
            executeSearch(query)
        }
    }

    private fun executeSearch(query: String) {
        searchJob?.cancel()
        // Instant response if cached
        searchCache[query]?.let { cached ->
            _uiState.value = MainScreenUiState.Success(cached)
            dataRepository.prefetchStreamUrls(cached.take(3))
            return
        }

        searchJob = viewModelScope.launch {
            _uiState.value = MainScreenUiState.Loading
            try {
                val results = dataRepository.searchSongs(query)
                if (!isActive || _searchQuery.value.trim() != query) return@launch
                searchCache[query] = results
                _uiState.value = MainScreenUiState.Success(results)
            } catch (e: Exception) {
                if (isActive) _uiState.value = MainScreenUiState.Error(e.message ?: "Search failed. Please try again.")
            }
        }
    }

    private var playJob: kotlinx.coroutines.Job? = null

    private suspend fun awaitMediaController(): MediaController? {
        mediaController?.let { return it }
        val future = controllerFuture ?: return null
        return try {
            withContext(Dispatchers.IO) { future.get() }.also { mediaController = it }
        } catch (_: Exception) {
            null
        }
    }

    private fun createMediaItem(song: SongInfo, url: String): MediaItem {
        val metadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle("Lumn")
            .apply {
                if (song.albumArtUrl.isNotBlank()) {
                    runCatching { setArtworkUri(android.net.Uri.parse(song.albumArtUrl)) }
                }
            }
            .build()
        return MediaItem.Builder()
            .setUri(url)
            .setMediaId(song.videoId)
            // Providers can return different encodings for the same track. Keep their cached
            // bytes separate so a partial failed stream cannot poison its fallback source.
            .setCustomCacheKey("${song.videoId}:${android.net.Uri.parse(url).host ?: "stream"}")
            .setMediaMetadata(metadata)
            .build()
    }

    private fun handlePlaybackError(error: androidx.media3.common.PlaybackException) {
        val song = _currentSong.value ?: return
        _isPlaying.value = false
        requestPlaybackFallback(song)
    }

    private fun watchForStalledPlayback(song: SongInfo, controller: MediaController) {
        bufferingWatchdog?.cancel()
        bufferingWatchdog = viewModelScope.launch {
            kotlinx.coroutines.delay(15_000)
            if (_currentSong.value?.videoId == song.videoId &&
                controller.playbackState == Player.STATE_BUFFERING &&
                !controller.isPlaying
            ) {
                requestPlaybackFallback(song)
            }
        }
    }

    private fun requestPlaybackFallback(song: SongInfo) {
        bufferingWatchdog?.cancel()
        if (!fallbackAttemptedTrackIds.add(song.videoId)) {
            _loadingSongVideoId.value = null
            android.widget.Toast.makeText(context, "Couldn't play \"${song.title}\". Skipping to next...", android.widget.Toast.LENGTH_SHORT).show()
            if (_currentQueue.value.size > 1 && currentQueueIndex + 1 < _currentQueue.value.size) {
                viewModelScope.launch {
                    kotlinx.coroutines.delay(1200)
                    skipToNext()
                }
            }
            return
        }

        _loadingSongVideoId.value = song.videoId
        viewModelScope.launch {
            val fallbackUrl = try {
                dataRepository.getFallbackStreamUrl(song.videoId, song.title, song.artist, song.durationSeconds)
            } catch (_: Exception) {
                null
            }
            _loadingSongVideoId.value = null
            if (_currentSong.value?.videoId != song.videoId) return@launch
            val controller = awaitMediaController()
            if (fallbackUrl != null && controller != null) {
                controller.setMediaItem(createMediaItem(song, fallbackUrl))
                controller.prepare()
                controller.play()
                watchForStalledPlayback(song, controller)
            } else {
                android.widget.Toast.makeText(context, "Couldn't load audio for \"${song.title}\". Please try again.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun generateDynamicQueueFor(song: SongInfo, existingQueue: List<SongInfo> = emptyList()): List<SongInfo> {
        val cleanExisting = existingQueue.distinctBy { it.videoId }
        val seenIds = mutableSetOf(song.videoId)
        seenIds.addAll(cleanExisting.map { it.videoId })
        val seenNormalizedTitles = mutableSetOf(song.title.trim().lowercase())
        seenNormalizedTitles.addAll(cleanExisting.map { it.title.trim().lowercase() })

        val recommendations = mutableListOf<SongInfo>()

        // 1. Artist's other top tracks from MusicCatalog
        val artistData = MusicCatalog.getArtistData(song.artist)
        for (track in artistData.topTracks) {
            val normTitle = track.title.trim().lowercase()
            if (!seenIds.contains(track.videoId) && !seenNormalizedTitles.contains(normTitle)) {
                seenIds.add(track.videoId)
                seenNormalizedTitles.add(normTitle)
                recommendations.add(track)
            }
        }

        // 2. Similar artists' tracks
        for (similar in artistData.similarArtists.take(3)) {
            val simData = MusicCatalog.getArtistData(similar.first)
            for (track in simData.topTracks.take(2)) {
                val normTitle = track.title.trim().lowercase()
                if (!seenIds.contains(track.videoId) && !seenNormalizedTitles.contains(normTitle)) {
                    seenIds.add(track.videoId)
                    seenNormalizedTitles.add(normTitle)
                    recommendations.add(track)
                }
            }
        }

        // 3. User history & curated catalog tracks matching taste
        try {
            val historyUserId = com.example.musicapp.data.auth.UserManager.getInstance(context).currentUser.value.id
            val userHistory = com.example.musicapp.data.history.PlayHistoryManager.getInstance(context)
                .getUserHistory(historyUserId)
                .map { SongInfo(it.videoId, it.title, it.artist, it.albumArtUrl) }
            val pool = (userHistory + MusicCatalog.hitsSongs + MusicCatalog.mostFavSongs)
            for (track in pool) {
                if (recommendations.size >= 12) break
                val normTitle = track.title.trim().lowercase()
                if (!seenIds.contains(track.videoId) && !seenNormalizedTitles.contains(normTitle)) {
                    seenIds.add(track.videoId)
                    seenNormalizedTitles.add(normTitle)
                    recommendations.add(track)
                }
            }
        } catch (_: Exception) {}

        return cleanExisting.ifEmpty { listOf(song) } + recommendations
    }

    fun playSong(song: SongInfo, queue: List<SongInfo> = emptyList()) {
        playJob?.cancel()
        fallbackAttemptedTrackIds.remove(song.videoId)
        
        // Instantly stop previous track audio and clear media items so NO stale audio can bleed or resume
        try {
            mediaController?.stop()
            mediaController?.clearMediaItems()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
        _currentSong.value = song
        _loadingSongVideoId.value = song.videoId

        val effectiveQueue = if (queue.isNotEmpty() && queue == _currentQueue.value) {
            queue
        } else {
            val cleanQueue = queue.distinctBy { it.videoId }.filter { it.title.isNotBlank() }
            if (cleanQueue.size <= 1) {
                generateDynamicQueueFor(song)
            } else {
                cleanQueue
            }
        }

        if (!_isShuffle.value) unshuffledQueue = effectiveQueue
        _currentQueue.value = effectiveQueue
        currentQueueIndex = effectiveQueue.indexOfFirst { it.videoId == song.videoId }.coerceAtLeast(0)
        val nextIndex = currentQueueIndex + 1
        if (nextIndex < effectiveQueue.size) {
            dataRepository.prefetchStreamUrls(listOf(effectiveQueue[nextIndex]))
        }

        playJob = viewModelScope.launch {
            var handedToPlayer = false
            try {
                // Keep a provider that stalls before returning a URL from pinning the UI in
                // loading forever. If normal resolution times out, bypass Saavn and try again.
                val offlineMode = com.example.musicapp.data.settings.SettingsManager.getInstance(context).offlineMode.value
                val preferredUrl = if (offlineMode) {
                    // Resolve by ID through the repository so only a real downloaded file is
                    // used in offline mode, even when a search result carries a remote URL.
                    kotlinx.coroutines.withTimeoutOrNull(5_000L) {
                        dataRepository.getStreamUrl(song.videoId, song.title, song.artist, song.durationSeconds)
                    }
                } else if (!song.audioStreamUrl.isNullOrBlank()) {
                    song.audioStreamUrl
                } else {
                    kotlinx.coroutines.withTimeoutOrNull(12_000L) {
                        dataRepository.getStreamUrl(song.videoId, song.title, song.artist, song.durationSeconds)
                    }
                }
                if (!isActive || _currentSong.value?.videoId != song.videoId) return@launch

                val url = if (offlineMode) preferredUrl else preferredUrl ?: kotlinx.coroutines.withTimeoutOrNull(25_000L) {
                    dataRepository.getFallbackStreamUrl(song.videoId, song.title, song.artist, song.durationSeconds)
                }
                if (!isActive || _currentSong.value?.videoId != song.videoId) return@launch

                if (!url.isNullOrBlank()) {
                    val currentUserId = com.example.musicapp.data.auth.UserManager.getInstance(context).currentUser.value.id
                    com.example.musicapp.data.history.PlayHistoryManager.getInstance(context).recordPlay(song, currentUserId)

                    val controller = awaitMediaController()
                    if (controller != null) {
                        controller.setMediaItem(createMediaItem(song, url))
                        controller.prepare()
                        controller.play()
                        handedToPlayer = true
                        watchForStalledPlayback(song, controller)
                    } else {
                        android.widget.Toast.makeText(context, "Audio player is starting. Please tap play again.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    try {
                        mediaController?.stop()
                        mediaController?.clearMediaItems()
                    } catch (_: Exception) { }
                    val offline = com.example.musicapp.data.settings.SettingsManager.getInstance(context).offlineMode.value
                    val message = if (offline) {
                        "Offline mode is on. Only downloaded music is available."
                    } else {
                        "Couldn't find a playable stream for \"${song.title}\"."
                    }
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                    // Auto-advance so queue continues execution seamlessly
                    if (_currentQueue.value.size > 1 && currentQueueIndex + 1 < _currentQueue.value.size) {
                        viewModelScope.launch {
                            kotlinx.coroutines.delay(1200)
                            skipToNext()
                        }
                    }
                }
            } catch (e: Exception) {
                if (isActive) android.widget.Toast.makeText(context, "Couldn't load \"${song.title}\". Please try again.", android.widget.Toast.LENGTH_LONG).show()
            } finally {
                // Media3 owns the spinner after it receives the source. Before that, every
                // timeout, cancellation, or thrown error must release the row loading state.
                if (!handedToPlayer && _currentSong.value?.videoId == song.videoId) {
                    _loadingSongVideoId.value = null
                }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    fun togglePlayPause() {
        val song = _currentSong.value
        mediaController?.let { controller ->
            if (controller.currentMediaItem == null && song != null) {
                playSong(song, _currentQueue.value)
            } else if (controller.isPlaying) {
                controller.pause()
            } else {
                controller.play()
            }
        } ?: run {
            if (song != null) {
                playSong(song, _currentQueue.value)
            }
        }
    }

    fun skipToNext() {
        val queue = _currentQueue.value
        if (queue.isNotEmpty()) {
            if (_isShuffle.value && queue.size > 1) {
                val nextIdx = (0 until queue.size).filter { it != currentQueueIndex }.randomOrNull() ?: 0
                currentQueueIndex = nextIdx
                playSong(queue[currentQueueIndex], queue)
            } else if (currentQueueIndex + 1 < queue.size) {
                currentQueueIndex++
                playSong(queue[currentQueueIndex], queue)
            } else {
                // End of queue reached! Dynamically generate more fresh songs instead of repeating
                val lastSong = queue.getOrNull(currentQueueIndex) ?: _currentSong.value
                if (lastSong != null) {
                    val extendedQueue = generateDynamicQueueFor(lastSong, queue)
                    if (extendedQueue.size > queue.size) {
                        _currentQueue.value = extendedQueue
                        currentQueueIndex = (currentQueueIndex + 1).coerceAtMost(extendedQueue.size - 1)
                        playSong(extendedQueue[currentQueueIndex], extendedQueue)
                        return
                    }
                }
                currentQueueIndex = 0
                playSong(queue[currentQueueIndex], queue)
            }
        }
    }

    fun skipToPrevious() {
        val queue = _currentQueue.value
        if (queue.isNotEmpty()) {
            if (_currentPosition.value > 3000L) {
                seekTo(0)
            } else if (currentQueueIndex > 0) {
                currentQueueIndex--
                playSong(queue[currentQueueIndex], queue)
            } else if (_isRepeat.value) {
                currentQueueIndex = queue.size - 1
                playSong(queue[currentQueueIndex], queue)
            } else {
                seekTo(0)
            }
        } else {
            seekTo(0)
        }
    }

    fun toggleShuffle() {
        val enableShuffle = !_isShuffle.value
        val queue = _currentQueue.value
        val playingId = _currentSong.value?.videoId
        if (enableShuffle) {
            unshuffledQueue = queue
            val current = queue.getOrNull(currentQueueIndex)
            if (current != null && queue.size > 1) {
                _currentQueue.value = listOf(current) + queue.filterIndexed { index, _ -> index != currentQueueIndex }.shuffled()
                currentQueueIndex = 0
            }
        } else if (unshuffledQueue.isNotEmpty()) {
            _currentQueue.value = unshuffledQueue
            currentQueueIndex = unshuffledQueue.indexOfFirst { it.videoId == playingId }.coerceAtLeast(0)
        }
        _isShuffle.value = enableShuffle
    }

    fun toggleRepeat() {
        _isRepeat.value = !_isRepeat.value
    }
}

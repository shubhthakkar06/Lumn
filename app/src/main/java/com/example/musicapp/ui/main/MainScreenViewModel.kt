package com.example.musicapp.ui.main

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.example.musicapp.data.DataRepository
import com.example.musicapp.data.download.MusicDownloadManager
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.settings.SettingsManager
import com.example.musicapp.service.PlaybackService
import com.example.musicapp.service.TrackMediaItems
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

sealed interface MainScreenUiState {
    object Idle : MainScreenUiState
    object Loading : MainScreenUiState
    data class Error(val message: String) : MainScreenUiState
    data class Success(val songs: List<SongInfo>) : MainScreenUiState
}

/**
 * Screen-side remote control for [PlaybackService].
 *
 * The queue itself lives in the service's player. This ViewModel mirrors it for the UI and
 * applies changes optimistically so taps feel instant, then sends them to the player.
 */
@OptIn(FlowPreview::class)
class MainScreenViewModel(private val dataRepository: DataRepository, private val context: Context) : ViewModel() {
    private val _uiState = MutableStateFlow<MainScreenUiState>(MainScreenUiState.Idle)
    val uiState: StateFlow<MainScreenUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // Small LRU so a long search session doesn't grow memory without bound.
    private val searchCache = object : LinkedHashMap<String, List<SongInfo>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<SongInfo>>?) = size > 40
    }
    private var searchJob: Job? = null

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

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    /** [Player.REPEAT_MODE_OFF], [Player.REPEAT_MODE_ALL] or [Player.REPEAT_MODE_ONE]. */
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _isRepeat = MutableStateFlow(false)
    val isRepeat: StateFlow<Boolean> = _isRepeat.asStateFlow()

    private val _currentQueue = MutableStateFlow<List<SongInfo>>(emptyList())
    val currentQueue: StateFlow<List<SongInfo>> = _currentQueue.asStateFlow()

    private var currentQueueIndex = 0

    private val soundEngine = com.example.musicapp.audio.SoundEngine.get(context)
    val soundSettings: StateFlow<com.example.musicapp.audio.SoundSettings> = soundEngine.settings
    val soundOutput: StateFlow<com.example.musicapp.audio.SoundEngine.Output> = soundEngine.output

    // Song / Video switch: the current song's music video, if it has one.
    private val _availableVideoId = MutableStateFlow<String?>(null)
    val availableVideoId: StateFlow<String?> = _availableVideoId.asStateFlow()
    private val _isVideoMode = MutableStateFlow(false)
    val isVideoMode: StateFlow<Boolean> = _isVideoMode.asStateFlow()
    private var videoLookupFor: String? = null
    private var videoLookupJob: Job? = null

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private val controllerReady = CompletableDeferred<MediaController>()
    private var pendingCommands = 0
    private var positionJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val controller = mediaController ?: return
            if (pendingCommands > 0) return
            val queueChanged = events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION)
            syncFromController(controller, includeQueue = queueChanged)
        }
    }

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
                    _isShuffle.value = extras.getBoolean(PlaybackService.EXTRA_SHUFFLE, _isShuffle.value)
                }
            })
            .buildAsync()
        controllerFuture = future
        future.addListener(
            {
                val controller = runCatching { future.get() }.getOrNull() ?: return@addListener
                mediaController = controller
                controller.addListener(playerListener)
                if (pendingCommands == 0) {
                    // Adopt the service's queue (for example one restored after a restart)
                    // unless the user already started something before the connection finished.
                    _isShuffle.value = controller.sessionExtras.getBoolean(PlaybackService.EXTRA_SHUFFLE, false)
                    syncFromController(controller, includeQueue = true)
                }
                controllerReady.complete(controller)
            },
            ContextCompat.getMainExecutor(context)
        )

        // Debounced search
        viewModelScope.launch {
            _searchQuery.debounce(350).collect { query ->
                val trimmed = query.trim()
                if (trimmed.isNotBlank()) executeSearch(trimmed)
            }
        }

    }

    override fun onCleared() {
        mediaController?.removeListener(playerListener)
        mediaController = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }

    /** Runs [block] on the controller now, or as soon as it connects, preserving order. */
    private fun withController(block: (MediaController) -> Unit) {
        val controller = mediaController
        if (controller != null && pendingCommands == 0) {
            block(controller)
            return
        }
        pendingCommands++
        viewModelScope.launch {
            val ready = controllerReady.await()
            try {
                block(ready)
            } finally {
                pendingCommands--
                if (pendingCommands == 0) syncFromController(ready, includeQueue = true)
            }
        }
    }

    private fun syncFromController(controller: MediaController, includeQueue: Boolean) {
        _isPlaying.value = controller.isPlaying
        _currentPosition.value = controller.currentPosition.coerceAtLeast(0L)
        _duration.value = controller.duration.takeIf { it > 0L } ?: 0L
        _repeatMode.value = controller.repeatMode
        _isRepeat.value = controller.repeatMode != Player.REPEAT_MODE_OFF

        if (includeQueue) {
            _currentQueue.value = (0 until controller.mediaItemCount).map { TrackMediaItems.toSong(controller.getMediaItemAt(it)) }
        }
        currentQueueIndex = controller.currentMediaItemIndex.coerceAtLeast(0)
        val current = controller.currentMediaItem
        _currentSong.value = if (current != null) {
            _currentQueue.value.getOrNull(currentQueueIndex)?.takeIf { it.videoId == current.mediaId }
                ?: TrackMediaItems.toSong(current)
        } else null

        val playingVideo = TrackMediaItems.videoIdOf(current)
        _isVideoMode.value = playingVideo != null
        _currentSong.value?.let { song -> if (song.videoId != videoLookupFor) lookUpVideo(song, playingVideo) }

        val buffering = controller.playbackState == Player.STATE_BUFFERING && controller.playWhenReady
        _loadingSongVideoId.value = if (buffering) current?.mediaId else null
        updatePositionTicker(controller)
    }

    private fun updatePositionTicker(controller: MediaController) {
        if (controller.isPlaying) {
            if (positionJob?.isActive == true) return
            positionJob = viewModelScope.launch {
                while (isActive) {
                    _currentPosition.value = controller.currentPosition.coerceAtLeast(0L)
                    controller.duration.takeIf { it > 0L }?.let { _duration.value = it }
                    delay(250)
                }
            }
        } else {
            positionJob?.cancel()
            positionJob = null
        }
    }

    // ---------------------------------------------------------------- queue

    fun getUpcomingQueue(): List<SongInfo> = _currentQueue.value.drop((currentQueueIndex + 1).coerceAtLeast(0))

    fun moveUpcomingQueueItem(fromIndex: Int, toIndex: Int) {
        val queue = _currentQueue.value.toMutableList()
        val from = currentQueueIndex + 1 + fromIndex
        val to = currentQueueIndex + 1 + toIndex
        if (from !in queue.indices || to !in queue.indices || from == to) return
        queue.add(to, queue.removeAt(from))
        _currentQueue.value = queue
        withController { it.moveMediaItem(from, to) }
    }

    fun playNext(song: SongInfo) {
        if (_currentQueue.value.isEmpty() && _currentSong.value == null) {
            playSong(song)
            return
        }
        val queue = _currentQueue.value.toMutableList()
        val insertAt = (currentQueueIndex + 1).coerceIn(0, queue.size)
        queue.add(insertAt, song)
        _currentQueue.value = queue
        withController { c -> c.addMediaItem((c.currentMediaItemIndex + 1).coerceIn(0, c.mediaItemCount), TrackMediaItems.toMediaItem(song)) }
        dataRepository.prefetchStreamUrls(listOf(song))
    }

    fun addToQueue(song: SongInfo) {
        if (_currentQueue.value.isEmpty() && _currentSong.value == null) {
            playSong(song)
            return
        }
        _currentQueue.value = _currentQueue.value + song
        withController { it.addMediaItem(TrackMediaItems.toMediaItem(song)) }
    }

    fun playUpcomingQueueItem(upcomingIndex: Int) {
        val absolute = currentQueueIndex + 1 + upcomingIndex
        val queue = _currentQueue.value
        if (absolute !in queue.indices) return
        currentQueueIndex = absolute
        _currentSong.value = queue[absolute]
        withController { c ->
            if (absolute < c.mediaItemCount) {
                c.seekTo(absolute, 0L)
                c.prepare()
                c.play()
            }
        }
    }

    /** Plays [song], with [queue] (if given) as the list it belongs to. */
    fun playSong(song: SongInfo, queue: List<SongInfo> = emptyList()) {
        var effective = queue.filter { it.title.isNotBlank() }.distinctBy { it.videoId }
        if (effective.none { it.videoId == song.videoId }) effective = listOf(song) + effective

        if (SettingsManager.getInstance(context).offlineMode.value) {
            val downloads = MusicDownloadManager.getInstance(context)
            if (!downloads.isDownloaded(song.videoId)) {
                android.widget.Toast.makeText(context, "Offline mode is on. Only downloaded music is available.", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            effective = effective.filter { downloads.isDownloaded(it.videoId) }
        }

        val startIndex = effective.indexOfFirst { it.videoId == song.videoId }.coerceAtLeast(0)
        val shuffle = _isShuffle.value

        // Optimistic UI state.
        _currentQueue.value = effective
        currentQueueIndex = startIndex
        _currentSong.value = song
        _loadingSongVideoId.value = song.videoId
        _currentPosition.value = 0L
        _duration.value = 0L

        withController { c ->
            val sameQueue = c.mediaItemCount == effective.size &&
                (0 until c.mediaItemCount).all { c.getMediaItemAt(it).mediaId == effective[it].videoId }
            if (sameQueue && !shuffle) {
                c.seekTo(startIndex, 0L)
            } else {
                if (shuffle) setShuffleCommand(c, false)
                c.setMediaItems(effective.map(TrackMediaItems::toMediaItem), startIndex, 0L)
                if (shuffle) setShuffleCommand(c, true)
            }
            c.prepare()
            c.play()
        }
        val next = effective.getOrNull(startIndex + 1)
        if (next != null) dataRepository.prefetchStreamUrls(listOf(next))
    }

    fun togglePlayPause() {
        val controller = mediaController
        if (controller == null || controller.mediaItemCount == 0) {
            _currentSong.value?.let { playSong(it, _currentQueue.value) }
            return
        }
        when {
            controller.isPlaying -> controller.pause()
            controller.playbackState == Player.STATE_ENDED -> {
                controller.seekTo(0, 0L)
                controller.play()
            }
            else -> {
                if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
                controller.play()
            }
        }
    }

    fun skipToNext() = withController { c ->
        if (c.hasNextMediaItem()) {
            c.seekToNextMediaItem()
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun skipToPrevious() = withController { c ->
        c.seekToPrevious()
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
    }

    fun seekTo(positionMs: Long) {
        _currentPosition.value = positionMs
        withController { it.seekTo(positionMs) }
    }

    private fun lookUpVideo(song: SongInfo, alreadyPlaying: String?) {
        videoLookupFor = song.videoId
        videoLookupJob?.cancel()
        _availableVideoId.value = alreadyPlaying
        if (alreadyPlaying != null) return
        videoLookupJob = viewModelScope.launch {
            val id = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { com.example.musicapp.data.catalog.VideoMatch.find(song) }.getOrNull()
            }
            if (videoLookupFor == song.videoId) _availableVideoId.value = id
        }
    }

    /** Switches between the song and its music video, keeping the position. */
    fun setVideoMode(enabled: Boolean) {
        val videoId = _availableVideoId.value
        if (enabled && videoId == null) return
        _isVideoMode.value = enabled
        withController { c ->
            c.sendCustomCommand(
                SessionCommand(PlaybackService.ACTION_SET_VIDEO, Bundle.EMPTY),
                Bundle().apply {
                    putBoolean(PlaybackService.EXTRA_ENABLED, enabled)
                    putString(PlaybackService.EXTRA_VIDEO_ID, videoId)
                }
            )
        }
    }

    /** Shows the playing video in [view] (the player itself lives in the playback service). */
    fun attachVideo(view: androidx.media3.ui.PlayerView) {
        view.player = mediaController
    }

    fun toggleShuffle() {
        val enable = !_isShuffle.value
        _isShuffle.value = enable
        withController { c -> setShuffleCommand(c, enable) }
    }

    private fun setShuffleCommand(controller: MediaController, enabled: Boolean) {
        controller.sendCustomCommand(
            SessionCommand(PlaybackService.ACTION_SET_SHUFFLE, Bundle.EMPTY),
            Bundle().apply { putBoolean(PlaybackService.EXTRA_ENABLED, enabled) }
        )
    }

    /** Cycles repeat: off → whole queue → this song → off. */
    fun toggleRepeat() {
        val next = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = next
        _isRepeat.value = next != Player.REPEAT_MODE_OFF
        withController { it.repeatMode = next }
    }

    // ---------------------------------------------------------------- search

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun search() {
        val query = _searchQuery.value.trim()
        if (query.isNotBlank()) executeSearch(query)
    }

    private fun executeSearch(query: String) {
        searchJob?.cancel()
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

    // ---------------------------------------------------------------- downloads & effects

    fun downloadAlbum(songs: List<SongInfo>) {
        if (songs.isEmpty()) return
        if (com.example.musicapp.data.auth.UserManager.getInstance(context).currentUser.value.isGuest) {
            android.widget.Toast.makeText(context, "Sign in to download music", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val manager = MusicDownloadManager.getInstance(context)
        val pending = songs.filterNot { manager.isDownloaded(it.videoId) }
        if (pending.isEmpty()) {
            android.widget.Toast.makeText(context, "These songs are already downloaded", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        pending.forEach { manager.downloadSong(it, notify = false) }
        android.widget.Toast.makeText(context, "Downloading ${pending.size} songs", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun selectSoundMode(mode: com.example.musicapp.audio.SoundMode) = soundEngine.selectMode(mode)

    fun tweakSound(change: (com.example.musicapp.audio.SoundSettings) -> com.example.musicapp.audio.SoundSettings) = soundEngine.tweak(change)

    fun setSoundAdaptive(enabled: Boolean) = soundEngine.update(soundSettings.value.copy(adaptive = enabled))
}

package com.example.musicapp.wear.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.musicapp.data.network.SongInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The watch UI's handle on [WatchPlaybackService]. */
class WatchPlayer(context: Context) {
    private var controller: MediaController? = null
    private val pending = mutableListOf<(MediaController) -> Unit>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _current = MutableStateFlow<SongInfo?>(null)
    val current: StateFlow<SongInfo?> = _current.asStateFlow()
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    private val _progress = MutableStateFlow(0f)
    /** 0..1 of the current song, for the ring around the play button. */
    val progress: StateFlow<Float> = _progress.asStateFlow()
    private val _queue = MutableStateFlow<List<SongInfo>>(emptyList())
    val queue: StateFlow<List<SongInfo>> = _queue.asStateFlow()
    private val _index = MutableStateFlow(0)
    val index: StateFlow<Int> = _index.asStateFlow()

    init {
        val token = SessionToken(context, ComponentName(context, WatchPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = refresh(c)
            })
            pending.forEach { it(c) }
            pending.clear()
            refresh(c)
        }, ContextCompat.getMainExecutor(context))
        scope.launch {
            while (isActive) {
                controller?.let { c ->
                    val d = c.duration
                    _progress.value = if (d > 0) (c.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
                }
                delay(500)
            }
        }
    }

    private fun refresh(c: MediaController) {
        _isPlaying.value = c.isPlaying
        _queue.value = (0 until c.mediaItemCount).map { WatchPlaybackService.songOf(c.getMediaItemAt(it)) }
        _index.value = c.currentMediaItemIndex.coerceAtLeast(0)
        _current.value = c.currentMediaItem?.let(WatchPlaybackService::songOf)
    }

    private fun withController(block: (MediaController) -> Unit) {
        controller?.let(block) ?: pending.add(block)
    }

    fun play(songs: List<SongInfo>, start: Int = 0) = withController { c ->
        c.setMediaItems(songs.map(WatchPlaybackService::toMediaItem), start.coerceIn(0, (songs.size - 1).coerceAtLeast(0)), 0L)
        c.prepare()
        c.play()
    }

    fun toggle() = withController { c -> if (c.isPlaying) c.pause() else { if (c.playbackState == Player.STATE_IDLE) c.prepare(); c.play() } }
    fun next() = withController { it.seekToNextMediaItem() }
    fun previous() = withController { it.seekToPrevious() }
    fun jumpTo(i: Int) = withController { it.seekTo(i, 0L); it.play() }

    fun release() {
        controller?.release()
        controller = null
    }
}

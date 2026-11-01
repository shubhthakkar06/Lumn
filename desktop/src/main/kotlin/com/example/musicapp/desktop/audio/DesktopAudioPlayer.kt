package com.example.musicapp.desktop.audio

import com.example.musicapp.desktop.model.DesktopSong
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.sound.sampled.*

class DesktopAudioPlayer {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var progressJob: Job? = null

    private val _currentSong = MutableStateFlow<DesktopSong?>(null)
    val currentSong: StateFlow<DesktopSong?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progressMs = MutableStateFlow(0L)
    val progressMs: StateFlow<Long> = _progressMs.asStateFlow()

    private val _volume = MutableStateFlow(0.75f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _queue = MutableStateFlow<List<DesktopSong>>(emptyList())
    val queue: StateFlow<List<DesktopSong>> = _queue.asStateFlow()

    fun setQueue(songs: List<DesktopSong>) {
        _queue.value = songs
    }

    fun play(song: DesktopSong) {
        _currentSong.value = song
        _progressMs.value = 0L
        _isPlaying.value = true
        startProgressTimer()
    }

    fun togglePlayPause() {
        if (_currentSong.value == null && _queue.value.isNotEmpty()) {
            play(_queue.value.first())
            return
        }
        val newState = !_isPlaying.value
        _isPlaying.value = newState
        if (newState) {
            startProgressTimer()
        } else {
            progressJob?.cancel()
        }
    }

    fun seekTo(positionMs: Long) {
        val duration = (_currentSong.value?.durationSeconds ?: 0L) * 1000L
        _progressMs.value = positionMs.coerceIn(0L, duration.coerceAtLeast(1000L))
    }

    fun setVolume(vol: Float) {
        _volume.value = vol.coerceIn(0f, 1f)
    }

    fun skipNext() {
        val q = _queue.value
        if (q.isEmpty()) return
        val currentIndex = q.indexOfFirst { it.id == _currentSong.value?.id }
        val nextIndex = (currentIndex + 1).takeIf { it in q.indices } ?: 0
        play(q[nextIndex])
    }

    fun skipPrev() {
        val q = _queue.value
        if (q.isEmpty()) return
        val currentIndex = q.indexOfFirst { it.id == _currentSong.value?.id }
        val prevIndex = (currentIndex - 1).takeIf { it in q.indices } ?: (q.size - 1)
        play(q[prevIndex])
    }

    private fun startProgressTimer() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && _isPlaying.value) {
                delay(250)
                val song = _currentSong.value ?: break
                val totalMs = song.durationSeconds * 1000L
                val nextPos = _progressMs.value + 250L
                if (totalMs > 0 && nextPos >= totalMs) {
                    _progressMs.value = 0L
                    skipNext()
                    break
                } else {
                    _progressMs.value = nextPos
                }
            }
        }
    }
}

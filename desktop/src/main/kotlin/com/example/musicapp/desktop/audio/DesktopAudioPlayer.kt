package com.example.musicapp.desktop.audio

import com.example.musicapp.desktop.data.DesktopMusicRepository
import com.example.musicapp.desktop.model.DesktopSong
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class DesktopAudioPlayer(
    private val musicRepository: DesktopMusicRepository = DesktopMusicRepository()
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val httpClient = OkHttpClient()
    private val cacheDir = File(System.getProperty("user.home"), ".rivara/cache").apply { mkdirs() }

    private val playerBin: String by lazy {
        listOf("/opt/homebrew/bin/ffplay", "/usr/local/bin/ffplay")
            .firstOrNull { File(it).canExecute() }
            ?: runCatching {
                val p = ProcessBuilder("which", "ffplay").start()
                val out = p.inputStream.bufferedReader().readLine()?.trim().orEmpty()
                if (out.isNotBlank() && File(out).canExecute()) out else null
            }.getOrNull()
            ?: "/usr/bin/afplay"
    }

    private var activeProcess: Process? = null
    private var activePid: Long = -1L
    private var isIntentionallyStopped = false
    private var tickerJob: Job? = null
    private var playJob: Job? = null

    private val _currentSong = MutableStateFlow<DesktopSong?>(null)
    val currentSong: StateFlow<DesktopSong?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progressMs = MutableStateFlow(0L)
    val progressMs: StateFlow<Long> = _progressMs.asStateFlow()

    private val _volume = MutableStateFlow(0.85f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _queue = MutableStateFlow<List<DesktopSong>>(emptyList())
    val queue: StateFlow<List<DesktopSong>> = _queue.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _isRepeat = MutableStateFlow(false)
    val isRepeat: StateFlow<Boolean> = _isRepeat.asStateFlow()

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
    }

    fun toggleRepeat() {
        _isRepeat.value = !_isRepeat.value
    }

    fun setQueue(songs: List<DesktopSong>) {
        _queue.value = songs
    }

    fun addToQueue(song: DesktopSong) {
        val current = _queue.value.toMutableList()
        if (current.none { it.id == song.id }) {
            current.add(song)
            _queue.value = current
        }
    }

    fun playNextInQueue(song: DesktopSong) {
        val q = _queue.value.toMutableList()
        val currentIndex = q.indexOfFirst { it.id == _currentSong.value?.id }
        q.removeAll { it.id == song.id }
        if (currentIndex != -1 && currentIndex + 1 <= q.size) {
            q.add(currentIndex + 1, song)
        } else {
            q.add(0, song)
        }
        _queue.value = q
    }

    fun play(song: DesktopSong, startPositionMs: Long = 0L) {
        stopCurrentPlayback()
        isIntentionallyStopped = false
        _currentSong.value = song
        _progressMs.value = startPositionMs
        _isPlaying.value = true

        if (_queue.value.none { it.id == song.id }) {
            _queue.value = listOf(song) + _queue.value
        }

        playJob?.cancel()
        playJob = scope.launch {
            var streamUrl = song.audioUrl
            if (streamUrl.isNullOrBlank()) {
                streamUrl = musicRepository.resolveAudioUrl(song)
            }

            if (streamUrl.isNullOrBlank()) {
                startProgressTimer(song.durationSeconds * 1000L)
                return@launch
            }

            val isMp3 = streamUrl.contains(".mp3", ignoreCase = true) ||
                streamUrl.contains("buzzsprout", ignoreCase = true) ||
                streamUrl.contains("anchor.fm", ignoreCase = true)
            val ext = if (isMp3) "mp3" else "m4a"

            val safeId = song.id.replace(Regex("[^a-zA-Z0-9_]"), "_")
            val targetFile = File(cacheDir, "$safeId.$ext")
            val isFfplay = playerBin.endsWith("ffplay")

            if (isFfplay) {
                // ffplay plays direct streams with zero buffering delay & full seeking!
                val audioSource = if (targetFile.exists() && targetFile.length() > 50000) {
                    targetFile.absolutePath
                } else {
                    streamUrl
                }
                startAudioEngine(audioSource, startPositionMs / 1000L)

                // Cache in the background without blocking audio startup
                if (!targetFile.exists() || targetFile.length() < 10000) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val req = Request.Builder()
                                .url(streamUrl)
                                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                                .build()
                            val res = httpClient.newCall(req).execute()
                            if (res.isSuccessful) {
                                res.body?.byteStream()?.use { input ->
                                    FileOutputStream(targetFile).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            } else {
                // afplay fallback requires a downloaded file on disk
                if (!targetFile.exists() || targetFile.length() < 10000) {
                    try {
                        val req = Request.Builder()
                            .url(streamUrl)
                            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .build()
                        val res = httpClient.newCall(req).execute()
                        if (res.isSuccessful) {
                            res.body?.byteStream()?.use { input ->
                                FileOutputStream(targetFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (targetFile.exists() && targetFile.length() > 10000) {
                    startAudioEngine(targetFile.absolutePath, startPositionMs / 1000L)
                } else {
                    startProgressTimer(song.durationSeconds * 1000L)
                }
            }
        }
    }

    private fun startAudioEngine(audioSource: String, startSeconds: Long) {
        stopPlayerProcess()
        try {
            val cmd = mutableListOf<String>()
            val isFfplay = playerBin.endsWith("ffplay")

            if (isFfplay) {
                val volInt = (_volume.value * 100).toInt().coerceIn(0, 100)
                cmd.add(playerBin)
                cmd.add("-nodisp")
                cmd.add("-autoexit")
                cmd.add("-user_agent")
                cmd.add("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                cmd.add("-volume")
                cmd.add(volInt.toString())
                if (startSeconds > 0) {
                    cmd.add("-ss")
                    cmd.add(startSeconds.toString())
                }
                cmd.add(audioSource)
            } else {
                val volStr = String.format(Locale.US, "%.2f", _volume.value)
                cmd.add(playerBin)
                cmd.add(audioSource)
                cmd.add("-v")
                cmd.add(volStr)
                // afplay does NOT support -s, so starts from beginning
            }

            val process = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()
            activeProcess = process
            activePid = process.pid()

            startProgressTimer((_currentSong.value?.durationSeconds ?: 180L) * 1000L)

            scope.launch {
                val errorLines = mutableListOf<String>()
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (errorLines.size < 20) errorLines.add(line)
                    }
                }
                val exitCode = process.waitFor()
                if (activeProcess == process && !isIntentionallyStopped && _isPlaying.value) {
                    if (exitCode == 0) {
                        withContext(Dispatchers.Main) {
                            if (_isRepeat.value) {
                                _currentSong.value?.let { play(it, 0L) }
                            } else {
                                skipNext()
                            }
                        }
                    } else {
                        System.err.println("Audio process exited with code $exitCode: ${errorLines.joinToString("\n")}")
                        // If ffplay exited with error and we tried a URL directly, stop ticker
                        tickerJob?.cancel()
                        _isPlaying.value = false
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            tickerJob?.cancel()
            _isPlaying.value = false
        }
    }

    fun togglePlayPause() {
        val song = _currentSong.value
        if (song == null) {
            if (_queue.value.isNotEmpty()) {
                play(_queue.value.first())
            }
            return
        }

        if (_isPlaying.value) {
            pause()
        } else {
            resume()
        }
    }

    fun pause() {
        if (!_isPlaying.value) return
        _isPlaying.value = false
        tickerJob?.cancel()
        if (activePid > 0) {
            try {
                Runtime.getRuntime().exec(arrayOf("kill", "-STOP", activePid.toString()))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun resume() {
        if (_isPlaying.value) return
        _isPlaying.value = true
        if (activePid > 0 && activeProcess?.isAlive == true) {
            try {
                Runtime.getRuntime().exec(arrayOf("kill", "-CONT", activePid.toString()))
                startProgressTimer((_currentSong.value?.durationSeconds ?: 180L) * 1000L)
            } catch (e: Exception) {
                _currentSong.value?.let { play(it, _progressMs.value) }
            }
        } else {
            _currentSong.value?.let { play(it, _progressMs.value) }
        }
    }

    fun seekTo(positionMs: Long) {
        val song = _currentSong.value ?: return
        val totalMs = song.durationSeconds * 1000L
        val clamped = positionMs.coerceIn(0L, totalMs.coerceAtLeast(1000L))
        _progressMs.value = clamped

        val safeId = song.id.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val mp3File = File(cacheDir, "$safeId.mp3")
        val m4aFile = File(cacheDir, "$safeId.m4a")
        val targetFile = if (mp3File.exists()) mp3File else m4aFile
        val isFfplay = playerBin.endsWith("ffplay")

        if (_isPlaying.value) {
            val audioSource = if (targetFile.exists() && targetFile.length() > 50000) {
                targetFile.absolutePath
            } else if (isFfplay && !song.audioUrl.isNullOrBlank()) {
                song.audioUrl
            } else {
                null
            }

            if (audioSource != null) {
                startAudioEngine(audioSource, clamped / 1000L)
            }
        }
    }

    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        _volume.value = clamped
    }

    fun skipNext() {
        val q = _queue.value
        if (q.isEmpty()) return
        if (_isShuffle.value) {
            val unplayed = q.filter { it.id != _currentSong.value?.id }
            if (unplayed.isNotEmpty()) {
                play(unplayed.random())
                return
            }
        }
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

    private fun stopCurrentPlayback() {
        isIntentionallyStopped = true
        tickerJob?.cancel()
        stopPlayerProcess()
    }

    private fun stopPlayerProcess() {
        activeProcess?.let {
            try {
                it.destroyForcibly()
            } catch (_: Exception) {}
        }
        activeProcess = null
        activePid = -1L
    }

    private fun startProgressTimer(totalMs: Long) {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            val startTime = System.currentTimeMillis() - _progressMs.value
            while (isActive && _isPlaying.value) {
                delay(200)
                val current = System.currentTimeMillis() - startTime
                if (totalMs > 0 && current >= totalMs) {
                    _progressMs.value = totalMs
                    break
                } else {
                    _progressMs.value = current
                }
            }
        }
    }
}

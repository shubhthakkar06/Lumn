package com.example.musicapp.desktop.audio

import com.example.musicapp.desktop.data.DesktopDownloadManager
import com.example.musicapp.desktop.data.DesktopLibraryManager
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
import javax.sound.sampled.*

/**
 * DesktopAudioPlayer: High-performance audio engine for Rivara Desktop on macOS.
 * Uses FFmpeg to decode any audio stream/codec (AAC, MP4, MP3, FLAC, M4A) directly
 * into 48kHz 16-bit stereo PCM, and plays it via Java Sound's DirectAudioDevice (CoreAudio HAL).
 *
 * Supports variable playback speed (0.5x to 2.0x), sleep timers, and automatic stream recovery.
 */
class DesktopAudioPlayer(
    private val musicRepository: DesktopMusicRepository = DesktopMusicRepository()
) {
    var libraryManager: DesktopLibraryManager? = null
    var downloadManager: DesktopDownloadManager? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val httpClient = OkHttpClient()
    private val cacheDir = File(System.getProperty("user.home"), ".rivara/cache").apply { mkdirs() }

    private val audioFormat = AudioFormat(48000f, 16, 2, true, false)
    private val bytesPerMs = 192L // 48000 samples/sec * 2 channels * 2 bytes/sample / 1000 ms

    private val ffmpegBin: String by lazy {
        listOf("/opt/homebrew/bin/ffmpeg", "/usr/local/bin/ffmpeg")
            .firstOrNull { File(it).canExecute() }
            ?: runCatching {
                val p = ProcessBuilder("which", "ffmpeg").start()
                val out = p.inputStream.bufferedReader().readLine()?.trim().orEmpty()
                if (out.isNotBlank() && File(out).canExecute()) out else null
            }.getOrNull()
            ?: "ffmpeg"
    }

    private var activeLine: SourceDataLine? = null
    private var activeProcess: Process? = null
    private var targetDeviceName: String? = null
    private var isIntentionallyStopped = false
    @Volatile private var isPaused = false
    private var playJob: Job? = null

    // Playback Speed
    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        if (_playbackSpeed.value == clamped) return
        _playbackSpeed.value = clamped
        val song = _currentSong.value
        val pos = _progressMs.value
        if (song != null && _isPlaying.value) {
            play(song, pos)
        }
    }

    // Sleep Timer
    private var sleepTimerJob: Job? = null
    private val _sleepTimerRemainingSeconds = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingSeconds: StateFlow<Long?> = _sleepTimerRemainingSeconds.asStateFlow()

    private val _sleepTimerLabel = MutableStateFlow<String?>(null)
    val sleepTimerLabel: StateFlow<String?> = _sleepTimerLabel.asStateFlow()

    var isSleepTimerEndOfTrack: Boolean = false
        private set

    fun setSleepTimerMinutes(minutes: Int, label: String = "$minutes min") {
        sleepTimerJob?.cancel()
        isSleepTimerEndOfTrack = false
        _sleepTimerLabel.value = label
        val totalSecs = minutes * 60L
        _sleepTimerRemainingSeconds.value = totalSecs

        sleepTimerJob = scope.launch {
            var remaining = totalSecs
            while (isActive && remaining > 0) {
                delay(1000L)
                remaining--
                _sleepTimerRemainingSeconds.value = remaining
            }
            if (isActive) {
                withContext(Dispatchers.Main) {
                    pause()
                    cancelSleepTimer()
                }
            }
        }
    }

    fun setSleepTimerEndOfTrack() {
        sleepTimerJob?.cancel()
        isSleepTimerEndOfTrack = true
        _sleepTimerLabel.value = "End of track"
        _sleepTimerRemainingSeconds.value = null
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        isSleepTimerEndOfTrack = false
        _sleepTimerRemainingSeconds.value = null
        _sleepTimerLabel.value = null
    }

    fun setOutputDevice(name: String) {
        targetDeviceName = name
        val song = _currentSong.value
        val pos = _progressMs.value
        if (song != null && _isPlaying.value) {
            play(song, pos)
        }
    }

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

    fun play(song: DesktopSong, startPositionMs: Long = 0L, queueContext: List<DesktopSong>? = null) {
        stopCurrentPlayback()
        isIntentionallyStopped = false
        isPaused = false

        val safeId = song.id.replace(Regex("[^a-zA-Z0-9_]"), "_")

        // 1. Resolve local downloaded file first if available (supports offline and offline-mode)!
        val localDownloadPath = downloadManager?.getDownloadedFilePath(song.id)
            ?: listOf(
                File(downloadManager?.downloadDir ?: File(System.getProperty("user.home"), ".rivara/downloads"), "$safeId.m4a"),
                File(downloadManager?.downloadDir ?: File(System.getProperty("user.home"), ".rivara/downloads"), "$safeId.mp3")
            ).firstOrNull { it.exists() && it.length() > 50_000 }?.absolutePath
            ?: song.audioUrl?.takeIf { it.startsWith("/") && File(it).exists() && File(it).length() > 50_000 }

        val isOfflineOnly = downloadManager?.isOfflineOnly?.value == true

        if (localDownloadPath == null && isOfflineOnly) {
            // Offline Mode is active but this track is not downloaded: try finding next downloaded track in queue!
            val effectiveQueue = queueContext ?: _queue.value
            val nextDownloaded = effectiveQueue.dropWhile { it.id != song.id }.drop(1)
                .firstOrNull { downloadManager?.isDownloaded(it.id) == true }
                ?: effectiveQueue.firstOrNull { downloadManager?.isDownloaded(it.id) == true }

            if (nextDownloaded != null && nextDownloaded.id != song.id) {
                play(nextDownloaded, 0L, queueContext)
                return
            } else {
                _currentSong.value = song
                _isPlaying.value = false
                return
            }
        }

        val effectiveSong = if (localDownloadPath != null) song.copy(audioUrl = localDownloadPath) else song
        _currentSong.value = effectiveSong
        _progressMs.value = startPositionMs
        _isPlaying.value = true

        if (queueContext != null && queueContext.isNotEmpty()) {
            _queue.value = queueContext
        } else if (_queue.value.none { it.id == effectiveSong.id }) {
            _queue.value = listOf(effectiveSong) + _queue.value
        }

        playJob?.cancel()
        playJob = scope.launch {
            val audioSource: String
            val remoteStreamUrl: String

            if (localDownloadPath != null) {
                // Play directly from pristine downloaded audio file! Zero network, full offline capability.
                audioSource = localDownloadPath
                remoteStreamUrl = localDownloadPath
            } else {
                var streamUrl = song.audioUrl
                if (streamUrl.isNullOrBlank() || streamUrl.contains("p.scdn.co/mp3-preview") || streamUrl.contains("spotify.com")) {
                    streamUrl = musicRepository.resolveAudioUrl(song)
                    if (!streamUrl.isNullOrBlank()) {
                        libraryManager?.updateSongAudioUrl(song.id, streamUrl)
                    }
                }

                if (streamUrl.isNullOrBlank()) {
                    simulateProgress(song.durationSeconds * 1000L, startPositionMs)
                    return@launch
                }

                remoteStreamUrl = streamUrl
                val isMp3 = streamUrl.contains(".mp3", ignoreCase = true)
                val ext = if (isMp3) "mp3" else "m4a"
                val targetFile = File(cacheDir, "$safeId.$ext")
                val tmpFile = File(cacheDir, "$safeId.tmp")

                val isCachedValid = targetFile.exists() && targetFile.length() > 500_000
                audioSource = if (isCachedValid) {
                    targetFile.absolutePath
                } else {
                    if (targetFile.exists()) targetFile.delete()
                    streamUrl
                }

                // Cache in background for offline / replay using atomic .tmp rename
                if (!isCachedValid && !tmpFile.exists() && !isOfflineOnly) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val req = Request.Builder()
                                .url(streamUrl)
                                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)")
                                .build()
                            val res = httpClient.newCall(req).execute()
                            if (res.isSuccessful) {
                                res.body?.byteStream()?.use { input ->
                                    FileOutputStream(tmpFile).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                if (tmpFile.length() > 500_000) {
                                    tmpFile.renameTo(targetFile)
                                } else {
                                    tmpFile.delete()
                                }
                            }
                        } catch (_: Exception) {
                            try { tmpFile.delete() } catch (_: Exception) {}
                        }
                    }
                }
            }

            startDirectPcmEngine(audioSource, remoteStreamUrl, startPositionMs, song.durationSeconds * 1000L)
        }
    }

    private suspend fun startDirectPcmEngine(
        audioSource: String,
        remoteStreamUrl: String,
        startPositionMs: Long,
        totalDurationMs: Long
    ): Unit = withContext(Dispatchers.IO) {
        var line: SourceDataLine? = null
        var proc: Process? = null
        var bytesWritten = 0L

        try {
            // Open Java DirectAudio line (talks directly to CoreAudio HAL, bypasses AudioQueue)
            val lineInfo = DataLine.Info(SourceDataLine::class.java, audioFormat)
            line = try {
                val mixer = targetDeviceName?.let { devName ->
                    AudioSystem.getMixerInfo().firstOrNull { it.name.equals(devName, ignoreCase = true) }
                }
                if (mixer != null) {
                    AudioSystem.getMixer(mixer).getLine(lineInfo) as SourceDataLine
                } else {
                    AudioSystem.getLine(lineInfo) as SourceDataLine
                }
            } catch (_: Exception) {
                AudioSystem.getLine(lineInfo) as SourceDataLine
            }
            line.apply {
                open(audioFormat, 48000 * 4) // 1 second hardware buffer
                start()
            }
            activeLine = line

            // Build ffmpeg decoding process to output raw 48kHz 16-bit stereo PCM
            val isRemote = audioSource.startsWith("http://", ignoreCase = true) || audioSource.startsWith("https://", ignoreCase = true)
            val cmd = mutableListOf(ffmpegBin)

            if (isRemote) {
                cmd.addAll(listOf(
                    "-headers", "User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36\r\n",
                    "-reconnect", "1",
                    "-reconnect_streamed", "1",
                    "-reconnect_delay_max", "5"
                ))
            }

            if (startPositionMs > 0) {
                cmd.add("-ss")
                cmd.add(String.format(Locale.US, "%.3f", startPositionMs / 1000.0))
            }

            val speed = _playbackSpeed.value
            if (speed != 1.0f) {
                cmd.addAll(listOf("-filter:a", "atempo=${String.format(Locale.US, "%.2f", speed)}"))
            }

            cmd.addAll(listOf(
                "-i", audioSource,
                "-vn",
                "-f", "s16le",
                "-ar", "48000",
                "-ac", "2",
                "pipe:1"
            ))

            val pb = ProcessBuilder(cmd)
            pb.redirectError(ProcessBuilder.Redirect.DISCARD)
            proc = pb.start()
            activeProcess = proc

            val inputStream = proc.inputStream
            val buffer = ByteArray(4096)
            bytesWritten = 0L
            var lastUpdateMs = System.currentTimeMillis()

            while (isActive && !isIntentionallyStopped) {
                if (isPaused) {
                    delay(30)
                    continue
                }

                val read = inputStream.read(buffer)
                if (read <= 0) break

                // Software volume adjustment directly on PCM 16-bit signed samples
                val vol = _volume.value
                if (vol < 0.99f) {
                    var i = 0
                    while (i < read - 1) {
                        val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
                        val scaled = (sample.toShort() * vol).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        buffer[i] = (scaled and 0xFF).toByte()
                        buffer[i + 1] = ((scaled shr 8) and 0xFF).toByte()
                        i += 2
                    }
                }

                // Apply DSP Equalizer adjustments
                DesktopEqualizer.processPcm(buffer, read)

                line.write(buffer, 0, read)
                bytesWritten += read

                val now = System.currentTimeMillis()
                if (now - lastUpdateMs >= 80) {
                    lastUpdateMs = now
                    val currentPos = startPositionMs + ((bytesWritten / bytesPerMs) * speed).toLong()
                    val clamped = if (totalDurationMs > 0) currentPos.coerceAtMost(totalDurationMs) else currentPos
                    _progressMs.value = clamped
                }
            }

            if (!isIntentionallyStopped && isActive) {
                val playedMs = startPositionMs + ((bytesWritten / bytesPerMs) * speed).toLong()
                val reachedEnd = totalDurationMs > 0 && playedMs >= (totalDurationMs - 4000L).coerceAtLeast(1000L)

                if (bytesWritten == 0L) {
                    simulateProgress(totalDurationMs, startPositionMs)
                } else if (!reachedEnd && audioSource != remoteStreamUrl && audioSource.startsWith("/")) {
                    val isDownloadFile = audioSource.contains("/.rivara/downloads/")
                    if (!isDownloadFile) {
                        println("Local cached audio ended prematurely at $playedMs ms / $totalDurationMs ms. Retrying directly from remote stream.")
                        try { File(audioSource).delete() } catch (_: Exception) {}
                        startDirectPcmEngine(remoteStreamUrl, remoteStreamUrl, playedMs, totalDurationMs)
                    }
                } else if (reachedEnd) {
                    line.drain()
                    withContext(Dispatchers.Main) {
                        if (isSleepTimerEndOfTrack) {
                            cancelSleepTimer()
                            pause()
                        } else if (_isRepeat.value) {
                            _currentSong.value?.let { play(it, 0L) }
                        } else {
                            skipNext()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val playedMs = startPositionMs + (bytesWritten / bytesPerMs)
            if (!isIntentionallyStopped && audioSource != remoteStreamUrl && audioSource.startsWith("/")) {
                // If local file failed to decode, delete and retry with remoteStreamUrl
                try { File(audioSource).delete() } catch (_: Exception) {}
                startDirectPcmEngine(remoteStreamUrl, remoteStreamUrl, playedMs, totalDurationMs)
            } else if (!isIntentionallyStopped) {
                simulateProgress(totalDurationMs, startPositionMs)
            }
        } finally {
            try {
                proc?.destroyForcibly()
            } catch (_: Exception) {}
            try {
                line?.stop()
                line?.close()
            } catch (_: Exception) {}
        }
    }

    private suspend fun simulateProgress(totalMs: Long, startMs: Long) {
        val startWallTime = System.currentTimeMillis() - startMs
        while (currentCoroutineContext().isActive && _isPlaying.value && !isIntentionallyStopped) {
            if (isPaused) {
                delay(100)
                continue
            }
            delay(150)
            val current = System.currentTimeMillis() - startWallTime
            if (totalMs > 0 && current >= totalMs) {
                _progressMs.value = totalMs
                withContext(Dispatchers.Main) {
                    if (_isRepeat.value) _currentSong.value?.let { play(it, 0L) }
                    else skipNext()
                }
                break
            } else {
                _progressMs.value = current
            }
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
        isPaused = true
        try {
            activeLine?.stop()
        } catch (_: Exception) {}
    }

    fun resume() {
        if (_isPlaying.value) return
        _isPlaying.value = true
        if (isPaused && activeLine != null) {
            isPaused = false
            try {
                activeLine?.start()
            } catch (_: Exception) {
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
        play(song, clamped)
    }

    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        _volume.value = clamped
    }

    fun skipNext() {
        val q = _queue.value
        if (q.isEmpty()) return
        val isOfflineOnly = downloadManager?.isOfflineOnly?.value == true
        val candidateQueue = if (isOfflineOnly) {
            val dl = q.filter { downloadManager?.isDownloaded(it.id) == true }
            if (dl.isNotEmpty()) dl else q
        } else q

        if (_isShuffle.value) {
            val unplayed = candidateQueue.filter { it.id != _currentSong.value?.id }
            if (unplayed.isNotEmpty()) {
                play(unplayed.random())
                return
            }
        }
        val currentIndex = candidateQueue.indexOfFirst { it.id == _currentSong.value?.id }
        val nextIndex = (currentIndex + 1).takeIf { it in candidateQueue.indices } ?: 0
        play(candidateQueue[nextIndex])
    }

    fun skipPrev() {
        val q = _queue.value
        if (q.isEmpty()) return
        val isOfflineOnly = downloadManager?.isOfflineOnly?.value == true
        val candidateQueue = if (isOfflineOnly) {
            val dl = q.filter { downloadManager?.isDownloaded(it.id) == true }
            if (dl.isNotEmpty()) dl else q
        } else q

        val currentIndex = candidateQueue.indexOfFirst { it.id == _currentSong.value?.id }
        val prevIndex = (currentIndex - 1).takeIf { it in candidateQueue.indices } ?: (candidateQueue.size - 1)
        play(candidateQueue[prevIndex])
    }

    private fun stopCurrentPlayback() {
        isIntentionallyStopped = true
        isPaused = false
        playJob?.cancel()
        try {
            activeProcess?.destroyForcibly()
        } catch (_: Exception) {}
        try {
            activeLine?.stop()
            activeLine?.close()
        } catch (_: Exception) {}
        activeProcess = null
        activeLine = null
    }
}

package com.example.musicapp.wear.audio

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.musicapp.wear.model.WearSongItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class WearAudioPlayer(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    private val audioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Linux; Android 14; Mobile)")
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(15_000)

    private val mediaSourceFactory = DefaultMediaSourceFactory(
        DefaultDataSource.Factory(context, httpDataSourceFactory)
    )

    private val player = ExoPlayer.Builder(context)
        .setMediaSourceFactory(mediaSourceFactory)
        .setAudioAttributes(audioAttributes, true)
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()

    private val _currentSong = MutableStateFlow<WearSongItem?>(null)
    val currentSong: StateFlow<WearSongItem?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progressMs = MutableStateFlow(0L)
    val progressMs: StateFlow<Long> = _progressMs.asStateFlow()

    private val _durationMs = MutableStateFlow(180_000L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _volume = MutableStateFlow(0.85f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private var progressJob: Job? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                _isPlaying.value = isPlayingNow
                if (isPlayingNow) startProgressUpdates() else stopProgressUpdates()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val dur = player.duration
                    if (dur > 0) _durationMs.value = dur
                } else if (playbackState == Player.STATE_ENDED) {
                    _isPlaying.value = false
                    _progressMs.value = _durationMs.value
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("WearAudioPlayer", "Playback error: ${error.errorCodeName}", error)
                _isPlaying.value = false
            }
        })
        player.volume = _volume.value
    }

    fun play(song: WearSongItem) {
        _currentSong.value = song
        _isPlaying.value = true
        _progressMs.value = 0L
        if (song.durationSeconds > 0) {
            _durationMs.value = song.durationSeconds * 1000L
        }

        scope.launch(Dispatchers.IO) {
            val streamUrl = resolveStreamUrl(song)
            withContext(Dispatchers.Main) {
                if (!streamUrl.isNullOrBlank()) {
                    android.util.Log.d("WearAudioPlayer", "Playing stream for ${song.title}: $streamUrl")
                    val mediaItem = MediaItem.fromUri(streamUrl)
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.play()
                } else {
                    android.util.Log.w("WearAudioPlayer", "Could not resolve stream URL for ${song.title}")
                    _isPlaying.value = false
                }
            }
        }
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE && _currentSong.value != null) {
                play(_currentSong.value!!)
            } else {
                player.play()
            }
        }
    }

    fun pause() {
        player.pause()
    }

    fun resume() {
        player.play()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _progressMs.value = positionMs
    }

    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        _volume.value = clamped
        player.volume = clamped
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && player.isPlaying) {
                _progressMs.value = player.currentPosition
                val dur = player.duration
                if (dur > 0) _durationMs.value = dur
                delay(300)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
    }

    private fun resolveStreamUrl(song: WearSongItem): String? {
        // 1. Resolve live 320kbps URL dynamically from JioSaavn
        try {
            val query = "${song.title} ${song.artist}".trim()
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.jiosaavn.com/api.php?__call=search.getResults&q=$encoded&_format=json&_marker=0&ctx=android&p=1&n=5"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile)")
                .build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val results = root["results"]?.jsonArray
                val first = results?.firstOrNull()?.jsonObject
                val enc = first?.get("encrypted_media_url")?.jsonPrimitive?.contentOrNull
                    ?: first?.get("more_info")?.jsonObject?.get("encrypted_media_url")?.jsonPrimitive?.contentOrNull
                if (!enc.isNullOrBlank()) {
                    val decrypted = decryptSaavnUrl(enc)
                    if (decrypted.isNotBlank()) {
                        android.util.Log.d("WearAudioPlayer", "Resolved live stream for ${song.title}: $decrypted")
                        return decrypted
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("WearAudioPlayer", "Failed to resolve JioSaavn stream for ${song.title}", e)
        }

        // 2. Fallback to song.audioUrl if present
        if (song.audioUrl.isNotBlank()) return song.audioUrl
        return null
    }

    private fun decryptSaavnUrl(encryptedMediaUrl: String): String {
        return try {
            val key = "38346591".toByteArray(Charsets.UTF_8)
            val spec = SecretKeySpec(key, "DES")
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, spec)
            val decoded = try {
                Base64.getDecoder().decode(encryptedMediaUrl.trim())
            } catch (_: Exception) {
                android.util.Base64.decode(encryptedMediaUrl.trim(), android.util.Base64.DEFAULT)
            }
            val decrypted = cipher.doFinal(decoded)
            val rawUrl = String(decrypted, Charsets.UTF_8)
            rawUrl.replace("_96.mp4", "_320.mp4")
                .replace("_160.mp4", "_320.mp4")
                .replace("_48.mp4", "_320.mp4")
        } catch (e: Exception) {
            ""
        }
    }

    fun release() {
        scope.cancel()
        player.release()
    }
}

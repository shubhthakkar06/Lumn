package com.example.musicapp.wear.data

import android.content.Context
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.provider.ProviderRouter
import com.example.musicapp.data.provider.StreamQuality
import com.example.musicapp.data.provider.StreamResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** Songs saved on the watch for listening without the phone or a connection. */
class WatchDownloads private constructor(private val context: Context) {
    private val dir = File(context.filesDir, "downloads").apply { mkdirs() }
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val queue = Channel<SongInfo>(Channel.UNLIMITED)

    private val _downloaded = MutableStateFlow(dir.listFiles()?.filter { it.name.endsWith(".audio") }?.map { it.name.removeSuffix(".audio") }?.toSet().orEmpty())
    val downloaded: StateFlow<Set<String>> = _downloaded.asStateFlow()
    private val _pending = MutableStateFlow<Set<String>>(emptySet())
    val pending: StateFlow<Set<String>> = _pending.asStateFlow()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            for (song in queue) {
                runCatching { download(song) }
                _pending.update { it - song.videoId }
            }
        }
    }

    fun file(id: String): File? = File(dir, "${safe(id)}.audio").takeIf { it.exists() && it.length() > 0 }

    fun enqueue(songs: List<SongInfo>) {
        songs.filter { it.videoId !in _downloaded.value && it.videoId !in _pending.value }.forEach { song ->
            _pending.update { it + song.videoId }
            queue.trySend(song)
        }
    }

    fun remove(id: String) {
        File(dir, "${safe(id)}.audio").delete()
        _downloaded.update { it - id }
    }

    private suspend fun download(song: SongInfo) {
        // Normal quality keeps watch storage and battery use reasonable.
        val result = ProviderRouter.shared.resolveStream(song.videoId, song.title, song.artist, song.durationSeconds, quality = StreamQuality.NORMAL)
        val url = (result as? StreamResult.Success)?.streamUrl ?: song.audioStreamUrl ?: return
        val target = File(dir, "${safe(song.videoId)}.audio")
        val temp = File(dir, "${safe(song.videoId)}.part")
        http.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android 14)").build()).execute().use { r ->
            if (!r.isSuccessful) return
            r.body?.byteStream()?.use { input -> temp.outputStream().use { input.copyTo(it) } } ?: return
        }
        if (temp.length() > 10_000 && temp.renameTo(target)) _downloaded.update { it + song.videoId } else temp.delete()
    }

    private fun safe(id: String) = id.replace(Regex("[^A-Za-z0-9_-]"), "_")

    companion object {
        @Volatile
        private var instance: WatchDownloads? = null
        fun get(context: Context): WatchDownloads = instance ?: synchronized(this) {
            instance ?: WatchDownloads(context.applicationContext).also { instance = it }
        }
    }
}

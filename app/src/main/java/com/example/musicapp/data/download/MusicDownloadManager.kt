package com.example.musicapp.data.download

import android.content.Context
import com.example.musicapp.data.network.SongInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class DownloadedTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val albumArtUrl: String,
    val localFilePath: String,
    val sizeBytes: Long,
    val downloadedAtMs: Long = System.currentTimeMillis()
) {
    fun toSongInfo(): SongInfo {
        return SongInfo(
            videoId = videoId,
            title = title,
            artist = artist,
            albumArtUrl = albumArtUrl,
            audioStreamUrl = "file://$localFilePath"
        )
    }
}

sealed interface DownloadStatus {
    object NotDownloaded : DownloadStatus
    data class Downloading(val progress: Float) : DownloadStatus
    data class Downloaded(val filePath: String) : DownloadStatus
}

class MusicDownloadManager private constructor(private val context: Context) {
    // Bound simultaneous transfers so album downloads remain stable on mid-range phones.
    private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(3))
    private val okHttpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val downloadsDir = File(context.filesDir, "downloads").apply { if (!exists()) mkdirs() }
    private val catalogFile = File(context.filesDir, "downloaded_tracks.json")

    private val _downloadedSongs = MutableStateFlow<List<DownloadedTrack>>(emptyList())
    val downloadedSongs: StateFlow<List<DownloadedTrack>> = _downloadedSongs.asStateFlow()

    private val _downloadStates = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadStatus>> = _downloadStates.asStateFlow()

    private val inProgressVideos = ConcurrentHashMap.newKeySet<String>()

    init {
        loadCatalog()
    }

    private fun loadCatalog() {
        try {
            if (catalogFile.exists()) {
                val content = catalogFile.readText()
                val list = json.decodeFromString<List<DownloadedTrack>>(content)
                // Filter out any entries where file was deleted on disk
                val validList = list.filter { File(it.localFilePath).exists() }
                _downloadedSongs.value = validList

                val states = mutableMapOf<String, DownloadStatus>()
                validList.forEach { track ->
                    states[track.videoId] = DownloadStatus.Downloaded(track.localFilePath)
                }
                _downloadStates.value = states
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _downloadedSongs.value = emptyList()
        }
    }

    private fun saveCatalog() {
        try {
            val content = json.encodeToString(_downloadedSongs.value)
            catalogFile.writeText(content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isDownloaded(videoId: String): Boolean {
        return _downloadedSongs.value.any { it.videoId == videoId }
    }

    fun getDownloadedFile(videoId: String): File? {
        val track = _downloadedSongs.value.find { it.videoId == videoId } ?: return null
        val file = File(track.localFilePath)
        return if (file.exists() && file.length() > 0) file else null
    }

    fun downloadSong(song: SongInfo, notify: Boolean = true, streamUrlProvider: suspend (SongInfo) -> String?) {
        if (isDownloaded(song.videoId)) {
            if (notify) android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, "\"${song.title}\" is already downloaded", android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (inProgressVideos.contains(song.videoId)) return
        inProgressVideos.add(song.videoId)

        if (notify) android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(context, "Starting download: ${song.title}...", android.widget.Toast.LENGTH_SHORT).show()
        }

        scope.launch {
            try {
                updateState(song.videoId, DownloadStatus.Downloading(0.05f))

                var streamUrl = if (!song.audioStreamUrl.isNullOrBlank()) {
                    song.audioStreamUrl
                } else {
                    streamUrlProvider(song)
                }
                if (streamUrl == null) {
                    updateState(song.videoId, DownloadStatus.NotDownloaded)
                    inProgressVideos.remove(song.videoId)
                    if (notify) android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(context, "Failed to resolve stream for ${song.title}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val targetFile = File(downloadsDir, "${song.videoId}.m4a")
                val tempFile = File(downloadsDir, "${song.videoId}.tmp")

                val mobileUserAgent = "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                val requestBuilder = Request.Builder()
                    .url(streamUrl)
                    .header("User-Agent", mobileUserAgent)
                    .header("Accept", "*/*")

                var response = okHttpClient.newCall(requestBuilder.build()).execute()

                // If 403 Forbidden or expired URL, fetch fresh stream URL and retry once
                if (!response.isSuccessful || response.code == 403) {
                    response.close()
                    val freshUrl = streamUrlProvider(song)
                    if (freshUrl != null) {
                        streamUrl = freshUrl
                        val retryRequest = Request.Builder()
                            .url(streamUrl)
                            .header("User-Agent", mobileUserAgent)
                            .header("Accept", "*/*")
                            .build()
                        response = okHttpClient.newCall(retryRequest).execute()
                    }
                }

                if (!response.isSuccessful || response.body == null) {
                    response.close()
                    updateState(song.videoId, DownloadStatus.NotDownloaded)
                    inProgressVideos.remove(song.videoId)
                    if (notify) android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(context, "Download failed for ${song.title} (HTTP ${response.code})", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val body = response.body!!
                val contentLength = body.contentLength()
                val inputStream = java.io.BufferedInputStream(body.byteStream(), 64 * 1024)
                val outputStream = java.io.BufferedOutputStream(FileOutputStream(tempFile), 64 * 1024)

                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                var totalBytesRead = 0L
                var lastProgressUpdateMs = 0L
                var lastProgressValue = 0f

                inputStream.use { input ->
                    outputStream.use { output ->
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            if (contentLength > 0) {
                                val progress = (totalBytesRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                                val now = System.currentTimeMillis()
                                if (progress - lastProgressValue >= 0.03f || (now - lastProgressUpdateMs) > 150) {
                                    lastProgressUpdateMs = now
                                    lastProgressValue = progress
                                    updateState(song.videoId, DownloadStatus.Downloading(progress))
                                }
                            }
                        }
                        output.flush()
                    }
                }

                if (tempFile.exists() && tempFile.length() > 0) {
                    if (targetFile.exists()) targetFile.delete()
                    tempFile.renameTo(targetFile)

                    val downloadedTrack = DownloadedTrack(
                        videoId = song.videoId,
                        title = song.title,
                        artist = song.artist,
                        albumArtUrl = song.albumArtUrl,
                        localFilePath = targetFile.absolutePath,
                        sizeBytes = targetFile.length()
                    )

                    val updatedList = _downloadedSongs.value.filter { it.videoId != song.videoId } + downloadedTrack
                    _downloadedSongs.value = updatedList
                    saveCatalog()

                    updateState(song.videoId, DownloadStatus.Downloaded(targetFile.absolutePath))

                    if (notify) android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(context, "Downloaded: ${song.title}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    updateState(song.videoId, DownloadStatus.NotDownloaded)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                updateState(song.videoId, DownloadStatus.NotDownloaded)
                if (notify) android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(context, "Download error: ${e.localizedMessage ?: "Unknown"}", android.widget.Toast.LENGTH_SHORT).show()
                }
            } finally {
                inProgressVideos.remove(song.videoId)
            }
        }
    }

    fun deleteDownload(videoId: String) {
        scope.launch {
            val track = _downloadedSongs.value.find { it.videoId == videoId }
            if (track != null) {
                val file = File(track.localFilePath)
                if (file.exists()) file.delete()
                _downloadedSongs.value = _downloadedSongs.value.filter { it.videoId != videoId }
                saveCatalog()
                updateState(videoId, DownloadStatus.NotDownloaded)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(context, "Removed from downloads: ${track.title}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateState(videoId: String, status: DownloadStatus) {
        val map = _downloadStates.value.toMutableMap()
        map[videoId] = status
        _downloadStates.value = map
    }

    companion object {
        @Volatile
        private var instance: MusicDownloadManager? = null

        fun getInstance(context: Context): MusicDownloadManager {
            return instance ?: synchronized(this) {
                instance ?: MusicDownloadManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

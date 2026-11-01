package com.example.musicapp.desktop.data

import com.example.musicapp.desktop.model.DesktopSong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class DesktopDownloadManager(
    private val musicRepository: DesktopMusicRepository = DesktopMusicRepository()
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    val downloadDir = File(System.getProperty("user.home"), ".rivara/downloads").apply { mkdirs() }
    private val metadataFile = File(downloadDir, "downloaded_songs.json")

    private val _downloadedSongs = MutableStateFlow<List<DesktopSong>>(emptyList())
    val downloadedSongs: StateFlow<List<DesktopSong>> = _downloadedSongs.asStateFlow()

    private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingIds: StateFlow<Set<String>> = _downloadingIds.asStateFlow()

    private val _isOfflineOnly = MutableStateFlow(false)
    val isOfflineOnly: StateFlow<Boolean> = _isOfflineOnly.asStateFlow()

    init {
        loadMetadata()
    }

    fun toggleOfflineOnly() {
        _isOfflineOnly.value = !_isOfflineOnly.value
    }

    fun isDownloaded(songId: String): Boolean {
        return _downloadedSongs.value.any { it.id == songId }
    }

    fun isDownloading(songId: String): Boolean {
        return _downloadingIds.value.contains(songId)
    }

    fun downloadSong(song: DesktopSong, onComplete: ((Boolean) -> Unit)? = null) {
        if (isDownloaded(song.id) || isDownloading(song.id)) return

        _downloadingIds.value = _downloadingIds.value + song.id

        scope.launch {
            try {
                var streamUrl = song.audioUrl
                if (streamUrl.isNullOrBlank()) {
                    streamUrl = musicRepository.resolveAudioUrl(song)
                }

                if (streamUrl.isNullOrBlank()) {
                    _downloadingIds.value = _downloadingIds.value - song.id
                    onComplete?.invoke(false)
                    return@launch
                }

                val safeId = song.id.replace(Regex("[^a-zA-Z0-9_]"), "_")
                val isMp3 = streamUrl.contains(".mp3", ignoreCase = true)
                val ext = if (isMp3) "mp3" else "m4a"
                val targetFile = File(downloadDir, "$safeId.$ext")

                val req = Request.Builder()
                    .url(streamUrl)
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)")
                    .build()

                val res = httpClient.newCall(req).execute()
                if (res.isSuccessful) {
                    res.body?.byteStream()?.use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    val offlineSong = song.copy(
                        audioUrl = targetFile.absolutePath
                    )
                    val updated = _downloadedSongs.value.filter { it.id != song.id } + offlineSong
                    _downloadedSongs.value = updated
                    saveMetadata()
                    _downloadingIds.value = _downloadingIds.value - song.id
                    onComplete?.invoke(true)
                } else {
                    _downloadingIds.value = _downloadingIds.value - song.id
                    onComplete?.invoke(false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _downloadingIds.value = _downloadingIds.value - song.id
                onComplete?.invoke(false)
            }
        }
    }

    fun removeDownload(songId: String) {
        val safeId = songId.replace(Regex("[^a-zA-Z0-9_]"), "_")
        File(downloadDir, "$safeId.mp3").delete()
        File(downloadDir, "$safeId.m4a").delete()
        _downloadedSongs.value = _downloadedSongs.value.filter { it.id != songId }
        saveMetadata()
    }

    fun clearAllDownloads() {
        downloadDir.listFiles()?.forEach { it.delete() }
        _downloadedSongs.value = emptyList()
        saveMetadata()
    }

    private fun loadMetadata() {
        try {
            if (metadataFile.exists()) {
                val list = json.decodeFromString<List<DesktopSong>>(metadataFile.readText())
                // Verify file still exists on disk
                val valid = list.filter {
                    val url = it.audioUrl
                    url != null && File(url).exists()
                }
                _downloadedSongs.value = valid
            }
        } catch (_: Exception) {
            _downloadedSongs.value = emptyList()
        }
    }

    private fun saveMetadata() {
        try {
            metadataFile.writeText(json.encodeToString(_downloadedSongs.value))
        } catch (_: Exception) {}
    }
}

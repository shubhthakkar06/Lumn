package com.example.musicapp.data.download

import android.content.Context
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.storage.AtomicFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Serializable
data class DownloadedTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val albumArtUrl: String,
    val localFilePath: String,
    val sizeBytes: Long,
    val downloadedAtMs: Long = System.currentTimeMillis(),
    val albumName: String = "",
    val durationSeconds: Long = 0L
) {
    fun toSongInfo(): SongInfo {
        return SongInfo(
            videoId = videoId,
            title = title,
            artist = artist,
            albumArtUrl = albumArtUrl,
            audioStreamUrl = "file://$localFilePath",
            albumName = albumName,
            durationSeconds = durationSeconds
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    private val downloadsDir = File(context.filesDir, "downloads").apply { if (!exists()) mkdirs() }
    private val catalogFile = File(context.filesDir, "downloaded_tracks.json")
    private val catalogBackupFile = File(context.filesDir, "downloaded_tracks.backup.json")

    // Catalog writes are serialized on one thread so parallel downloads can't interleave
    // writes to the same file (which used to corrupt it and wipe every download).
    private val catalogWriter = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "rivara-download-catalog").apply { isDaemon = true }
    }

    private val _downloadedSongs = MutableStateFlow<List<DownloadedTrack>>(emptyList())
    val downloadedSongs: StateFlow<List<DownloadedTrack>> = _downloadedSongs.asStateFlow()

    private val _downloadStates = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadStatus>> = _downloadStates.asStateFlow()

    private val inProgressVideos = ConcurrentHashMap.newKeySet<String>()

    init {
        loadCatalog()
    }

    private fun readCatalog(file: File): List<DownloadedTrack>? {
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<List<DownloadedTrack>>(file.readText()) }.getOrNull()
    }

    private fun loadCatalog() {
        // Fall back to the backup if the main file is unreadable, rather than treating a bad
        // read as "no downloads".
        val list = readCatalog(catalogFile) ?: readCatalog(catalogBackupFile) ?: emptyList()
        val validList = list.filter { File(it.localFilePath).let { f -> f.exists() && f.length() > 0 } }
        _downloadedSongs.value = validList
        _downloadStates.value = validList.associate { it.videoId to DownloadStatus.Downloaded(it.localFilePath) }
        // Clean up partial files left behind by a download interrupted when the app was killed.
        downloadsDir.listFiles { f -> f.name.endsWith(".tmp") }?.forEach { it.delete() }
    }

    private fun saveCatalog() {
        catalogWriter.execute {
            try {
                // Always write the latest state, so a burst of finished downloads coalesces.
                val content = json.encodeToString(_downloadedSongs.value)
                AtomicFiles.writeText(catalogFile, content)
                AtomicFiles.writeText(catalogBackupFile, content)
            } catch (e: Exception) {
                android.util.Log.e("MusicDownloadManager", "Could not save download catalog", e)
            }
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

    private fun toast(message: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    enum class Outcome { SUCCESS, ALREADY_DONE, FAILED }

    /**
     * Queues [song] for download. The work runs through WorkManager, so it continues when the
     * app is closed, resumes after it is killed, and waits for a network connection.
     */
    fun downloadSong(song: SongInfo, notify: Boolean = true) {
        if (com.example.musicapp.data.auth.UserManager.getInstance(context).currentUser.value.isGuest) {
            if (notify) toast("Sign in to download music")
            return
        }
        if (isDownloaded(song.videoId)) {
            if (notify) toast("\"${song.title}\" is already downloaded")
            return
        }
        if (_downloadStates.value[song.videoId] is DownloadStatus.Downloading) return

        updateState(song.videoId, DownloadStatus.Downloading(0.02f))
        if (notify) toast("Downloading ${song.title}…")

        val request = androidx.work.OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                androidx.work.workDataOf(
                    DownloadWorker.KEY_SONG to DownloadWorker.encode(song),
                    DownloadWorker.KEY_NOTIFY to notify
                )
            )
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag(WORK_TAG)
            .build()
        androidx.work.WorkManager.getInstance(context)
            .enqueueUniqueWork("download_${song.videoId}", androidx.work.ExistingWorkPolicy.KEEP, request)
    }

    /** Runs one download attempt. Called by [DownloadWorker]. */
    internal suspend fun performDownload(song: SongInfo, notify: Boolean): Outcome {
        if (isDownloaded(song.videoId)) return Outcome.ALREADY_DONE
        if (!inProgressVideos.add(song.videoId)) return Outcome.ALREADY_DONE
        updateState(song.videoId, DownloadStatus.Downloading(0.02f))
        val tempFile = File(downloadsDir, "${safeName(song.videoId)}.tmp")
        try {
            val repository = com.example.musicapp.data.DefaultDataRepository(context)
            val result = download(song, tempFile) { track ->
                repository.getDownloadStreamUrl(track.videoId, track.title, track.artist, track.durationSeconds)
            }
            if (result == null) {
                updateState(song.videoId, DownloadStatus.Downloading(0.02f))
                return Outcome.FAILED
            }
            val (extension, size) = result
            val targetFile = File(downloadsDir, "${safeName(song.videoId)}.$extension")
            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.delete()
                return Outcome.FAILED
            }
            val downloadedTrack = DownloadedTrack(
                videoId = song.videoId,
                title = song.title,
                artist = song.artist,
                albumArtUrl = song.albumArtUrl,
                localFilePath = targetFile.absolutePath,
                sizeBytes = size,
                albumName = song.albumName,
                durationSeconds = song.durationSeconds
            )
            _downloadedSongs.update { list -> list.filter { it.videoId != song.videoId } + downloadedTrack }
            saveCatalog()
            updateState(song.videoId, DownloadStatus.Downloaded(targetFile.absolutePath))
            if (notify) toast("Downloaded: ${song.title}")
            return Outcome.SUCCESS
        } catch (e: CancellationException) {
            tempFile.delete()
            throw e
        } catch (e: Exception) {
            android.util.Log.w("MusicDownloadManager", "Download failed for ${song.videoId}", e)
            tempFile.delete()
            return Outcome.FAILED
        } finally {
            inProgressVideos.remove(song.videoId)
        }
    }

    /** Called after the last retry fails. */
    internal fun markFailed(song: SongInfo, notify: Boolean) {
        updateState(song.videoId, DownloadStatus.NotDownloaded)
        if (notify) toast("Couldn't download \"${song.title}\". Please try again.")
    }

    /**
     * Downloads into [tempFile]. Returns the file extension and size, or null when no source
     * produced real audio. A freshly resolved URL is tried first: URLs stored on a song may
     * have expired, and the song's own URL is only a last resort.
     */
    private suspend fun download(
        song: SongInfo,
        tempFile: File,
        streamUrlProvider: suspend (SongInfo) -> String?
    ): Pair<String, Long>? {
        val candidates = buildList {
            runCatching { streamUrlProvider(song) }.getOrNull()?.let { add(it) }
            song.audioStreamUrl?.takeIf { it.startsWith("http") }?.let { add(it) }
        }.distinct()

        for (url in candidates) {
            if (url.startsWith("file://")) continue
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept", "*/*")
                .build()
            val outcome = runCatching {
                okHttpClient.newCall(request).execute().use { response -> writeAudio(song.videoId, response, tempFile) }
            }.getOrNull()
            if (outcome != null) return outcome
            tempFile.delete()
        }
        return null
    }

    private fun writeAudio(videoId: String, response: Response, tempFile: File): Pair<String, Long>? {
        if (!response.isSuccessful) return null
        val body = response.body ?: return null
        val contentType = body.contentType()?.let { "${it.type}/${it.subtype}" }.orEmpty().lowercase()
        // Error pages and JSON errors used to be saved as songs.
        if (contentType.startsWith("text/") || contentType.contains("json") || contentType.contains("xml")) return null

        val contentLength = body.contentLength()
        var total = 0L
        var lastProgress = 0f
        var lastUpdateMs = 0L
        body.byteStream().use { input ->
            FileOutputStream(tempFile).buffered(64 * 1024).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    total += read
                    if (contentLength > 0) {
                        val progress = (total.toFloat() / contentLength).coerceIn(0f, 1f)
                        val now = System.currentTimeMillis()
                        if (progress - lastProgress >= 0.03f || now - lastUpdateMs > 250) {
                            lastProgress = progress
                            lastUpdateMs = now
                            updateState(videoId, DownloadStatus.Downloading(progress))
                        }
                    }
                }
            }
        }
        if (contentLength > 0 && total < contentLength) return null // connection dropped
        if (total < MIN_AUDIO_BYTES) return null
        return extensionFor(contentType) to total
    }

    private fun extensionFor(contentType: String): String = when {
        contentType.contains("mpeg") || contentType.contains("mp3") -> "mp3"
        contentType.contains("webm") -> "webm"
        contentType.contains("ogg") || contentType.contains("opus") -> "ogg"
        contentType.contains("aac") -> "aac"
        else -> "m4a"
    }

    private fun safeName(videoId: String): String = videoId.replace(Regex("[^A-Za-z0-9_-]"), "_")

    fun deleteDownload(videoId: String) {
        scope.launch {
            val track = _downloadedSongs.value.find { it.videoId == videoId } ?: return@launch
            File(track.localFilePath).delete()
            _downloadedSongs.update { list -> list.filter { it.videoId != videoId } }
            saveCatalog()
            updateState(videoId, DownloadStatus.NotDownloaded)
            toast("Removed from downloads: ${track.title}")
        }
    }

    private fun updateState(videoId: String, status: DownloadStatus) {
        _downloadStates.update { it + (videoId to status) }
    }

    companion object {
        const val WORK_TAG = "rivara_download"

        /** Anything smaller than this is an error response or a truncated file, not a song. */
        private const val MIN_AUDIO_BYTES = 64 * 1024L

        @Volatile
        private var instance: MusicDownloadManager? = null

        fun getInstance(context: Context): MusicDownloadManager {
            return instance ?: synchronized(this) {
                instance ?: MusicDownloadManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

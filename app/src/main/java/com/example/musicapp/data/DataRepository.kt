package com.example.musicapp.data

import android.content.Context
import android.net.ConnectivityManager
import com.example.musicapp.data.download.MusicDownloadManager
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.provider.ProviderRouter
import com.example.musicapp.data.provider.StreamQuality
import com.example.musicapp.data.provider.StreamResult
import com.example.musicapp.data.settings.SettingsManager
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

interface DataRepository {
    suspend fun searchSongs(query: String): List<SongInfo>
    suspend fun getStreamUrl(videoId: String, songTitle: String? = null, artist: String? = null, durationSeconds: Long = 0L): String?
    suspend fun getFallbackStreamUrl(videoId: String, songTitle: String? = null, artist: String? = null, durationSeconds: Long = 0L): String? =
        getStreamUrl(videoId, songTitle, artist, durationSeconds)
    /** URL for saving a song to the device, at the user's download quality. */
    suspend fun getDownloadStreamUrl(videoId: String, songTitle: String? = null, artist: String? = null, durationSeconds: Long = 0L): String? =
        getStreamUrl(videoId, songTitle, artist, durationSeconds)
    fun prefetchStreamUrls(songs: List<SongInfo>)
}

class DefaultDataRepository(private val context: Context) : DataRepository {
    private val providerRouter = ProviderRouter.shared
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val inFlightRequests = ConcurrentHashMap<String, Deferred<String?>>()

    override suspend fun searchSongs(query: String): List<SongInfo> {
        val settings = SettingsManager.getInstance(context)
        if (settings.offlineMode.value) {
            val downloaded = MusicDownloadManager.getInstance(context).downloadedSongs.value
            return downloaded.filter {
                it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
            }.map { it.toSongInfo() }
        }

        val results = providerRouter.search(query)
        // Automatically prefetch top 2 results
        prefetchStreamUrls(results.take(2))
        return results
    }

    /**
     * Streaming quality from settings. Data Saver "On" always streams low; "Automatic"
     * streams low on metered connections (mobile data, hotspots).
     */
    private fun streamingQuality(): StreamQuality {
        val settings = SettingsManager.getInstance(context)
        val chosen = StreamQuality.fromLabel(settings.streamingQuality.value)
        return when (settings.dataSaverMode.value) {
            "On" -> StreamQuality.LOW
            "Automatic" -> if (isMetered()) minOf(chosen, StreamQuality.NORMAL) else chosen
            else -> chosen
        }
    }

    private fun isMetered(): Boolean = runCatching {
        (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered
    }.getOrDefault(false)

    override suspend fun getStreamUrl(videoId: String, songTitle: String?, artist: String?, durationSeconds: Long): String? =
        resolve(videoId, songTitle, artist, durationSeconds, streamingQuality())

    override suspend fun getDownloadStreamUrl(videoId: String, songTitle: String?, artist: String?, durationSeconds: Long): String? {
        if (SettingsManager.getInstance(context).offlineMode.value) return null
        val quality = StreamQuality.fromLabel(SettingsManager.getInstance(context).downloadQuality.value)
        return when (val res = providerRouter.resolveStream(videoId, songTitle, artist, durationSeconds, quality = quality)) {
            is StreamResult.Success -> res.streamUrl
            is StreamResult.Failure -> null
        }
    }

    private suspend fun resolve(
        videoId: String,
        songTitle: String?,
        artist: String?,
        durationSeconds: Long,
        quality: StreamQuality
    ): String? {
        // 1. Instant check: If song is downloaded locally, play from disk
        MusicDownloadManager.getInstance(context).getDownloadedFile(videoId)?.let { return "file://${it.absolutePath}" }

        if (SettingsManager.getInstance(context).offlineMode.value) return null

        val cacheKey = requestKey(videoId, songTitle, artist, durationSeconds) + "|" + quality.name
        val deferred = inFlightRequests.computeIfAbsent(cacheKey) { _ ->
            scope.async {
                when (val res = providerRouter.resolveStream(videoId, songTitle, artist, durationSeconds, quality = quality)) {
                    is StreamResult.Success -> {
                        lastProvider[videoId] = res.providerName
                        res.streamUrl
                    }
                    is StreamResult.Failure -> null
                }
            }
        }

        return try {
            deferred.await()
        } finally {
            inFlightRequests.remove(cacheKey, deferred)
        }
    }

    override suspend fun getFallbackStreamUrl(videoId: String, songTitle: String?, artist: String?, durationSeconds: Long): String? {
        if (SettingsManager.getInstance(context).offlineMode.value) return null
        providerRouter.invalidateCache(videoId, songTitle, artist)
        // Rotate away from the source that just failed. If the first URL came directly
        // from a search result, infer its provider from the stable catalog ID.
        val previousProvider = lastProvider.remove(videoId) ?: when {
            videoId.startsWith("saavn_") -> "Saavn"
            videoId.startsWith("audius_") -> "Audius"
            videoId.startsWith("jamendo_") -> "Jamendo"
            videoId.length == 11 && !videoId.startsWith("track_") -> "YouTube"
            else -> null
        }
        return when (val result = providerRouter.resolveStream(
            videoId,
            songTitle,
            artist,
            durationSeconds = durationSeconds,
            excludedProviders = listOfNotNull(previousProvider).toSet(),
            quality = streamingQuality()
        )) {
            is StreamResult.Success -> {
                lastProvider[videoId] = result.providerName
                result.streamUrl
            }
            is StreamResult.Failure -> null
        }
    }

    private fun requestKey(videoId: String, title: String?, artist: String?, durationSeconds: Long): String =
        listOf(videoId, title.orEmpty().trim(), artist.orEmpty().trim(), durationSeconds.toString()).joinToString("|")

    override fun prefetchStreamUrls(songs: List<SongInfo>) {
        songs.forEach { song ->
            scope.launch {
                runCatching { getStreamUrl(song.videoId, song.title, song.artist, song.durationSeconds) }
            }
        }
    }

    companion object {
        /** Which provider served each track last, shared by every repository instance. */
        private val lastProvider = ConcurrentHashMap<String, String>()
    }
}

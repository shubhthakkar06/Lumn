package com.example.musicapp.data

import android.content.Context
import com.example.musicapp.data.download.MusicDownloadManager
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.provider.ProviderRouter
import com.example.musicapp.data.provider.StreamResult
import com.example.musicapp.data.settings.SettingsManager
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap

interface DataRepository {
    suspend fun searchSongs(query: String): List<SongInfo>
    suspend fun getStreamUrl(videoId: String, songTitle: String? = null, artist: String? = null, durationSeconds: Long = 0L): String?
    suspend fun getFallbackStreamUrl(videoId: String, songTitle: String? = null, artist: String? = null, durationSeconds: Long = 0L): String? =
        getStreamUrl(videoId, songTitle, artist, durationSeconds)
    fun prefetchStreamUrls(songs: List<SongInfo>)
}

class DefaultDataRepository(private val context: Context) : DataRepository {
    private val providerRouter = ProviderRouter()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val inFlightRequests = ConcurrentHashMap<String, Deferred<String?>>()
    private val providerByRequestKey = ConcurrentHashMap<String, String>()

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

    override suspend fun getStreamUrl(videoId: String, songTitle: String?, artist: String?, durationSeconds: Long): String? {
        // 1. Instant check: If song is downloaded locally, play from disk
        val localFile = MusicDownloadManager.getInstance(context).getDownloadedFile(videoId)
        if (localFile != null) {
            return "file://${localFile.absolutePath}"
        }

        val settings = SettingsManager.getInstance(context)
        if (settings.offlineMode.value) {
            return null
        }

        val cacheKey = requestKey(videoId, songTitle, artist, durationSeconds)
        val deferred = inFlightRequests.computeIfAbsent(cacheKey) { _ ->
            scope.async {
                when (val res = providerRouter.resolveStream(videoId, songTitle, artist, durationSeconds)) {
                    is StreamResult.Success -> {
                        providerByRequestKey[cacheKey] = res.providerName
                        res.streamUrl
                    }
                    is StreamResult.Failure -> null
                }
            }
        }

        return try {
            deferred.await()
        } finally {
            inFlightRequests.remove(cacheKey)
        }
    }

    override suspend fun getFallbackStreamUrl(videoId: String, songTitle: String?, artist: String?, durationSeconds: Long): String? {
        if (SettingsManager.getInstance(context).offlineMode.value) return null
        val key = requestKey(videoId, songTitle, artist, durationSeconds)
        inFlightRequests.remove(key)?.cancel()
        providerRouter.invalidateCache(videoId, songTitle, artist)
        // Rotate away from the source that just failed. If the first URL came directly
        // from a search result, infer its provider from the stable catalog ID.
        val previousProvider = providerByRequestKey.remove(key) ?: when {
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
            excludedProviders = listOfNotNull(previousProvider).toSet()
        )) {
            is StreamResult.Success -> {
                providerByRequestKey[key] = result.providerName
                result.streamUrl
            }
            is StreamResult.Failure -> null
        }
    }

    private fun requestKey(videoId: String, title: String?, artist: String?, durationSeconds: Long): String =
        listOf(videoId, title.orEmpty().trim(), artist.orEmpty().trim(), durationSeconds.toString()).joinToString("|")

    override fun prefetchStreamUrls(songs: List<SongInfo>) {
        songs.forEach { song ->
            if (!inFlightRequests.containsKey(song.videoId)) {
                scope.launch {
                    getStreamUrl(song.videoId, song.title, song.artist, song.durationSeconds)
                }
            }
        }
    }
}

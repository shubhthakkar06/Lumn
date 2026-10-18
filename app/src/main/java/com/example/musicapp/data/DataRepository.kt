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
    suspend fun getStreamUrl(videoId: String, songTitle: String? = null, artist: String? = null): String?
    suspend fun getFallbackStreamUrl(videoId: String, songTitle: String? = null, artist: String? = null): String? =
        getStreamUrl(videoId, songTitle, artist)
    fun prefetchStreamUrls(songs: List<SongInfo>)
}

class DefaultDataRepository(private val context: Context) : DataRepository {
    private val providerRouter = ProviderRouter()
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

    override suspend fun getStreamUrl(videoId: String, songTitle: String?, artist: String?): String? {
        // 1. Instant check: If song is downloaded locally, play from disk
        val localFile = MusicDownloadManager.getInstance(context).getDownloadedFile(videoId)
        if (localFile != null) {
            return "file://${localFile.absolutePath}"
        }

        val settings = SettingsManager.getInstance(context)
        if (settings.offlineMode.value) {
            return null
        }

        val cacheKey = if (!songTitle.isNullOrBlank()) "${videoId}_$songTitle" else videoId
        val deferred = inFlightRequests.computeIfAbsent(cacheKey) { _ ->
            scope.async {
                when (val res = providerRouter.resolveStream(videoId, songTitle, artist)) {
                    is StreamResult.Success -> res.streamUrl
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

    override suspend fun getFallbackStreamUrl(videoId: String, songTitle: String?, artist: String?): String? {
        if (SettingsManager.getInstance(context).offlineMode.value) return null
        val key = if (!songTitle.isNullOrBlank()) "${videoId}_$songTitle" else videoId
        inFlightRequests.remove(key)?.cancel()
        providerRouter.invalidateCache(videoId, songTitle, artist)
        return when (val result = providerRouter.resolveStream(
            videoId,
            songTitle,
            artist,
            excludedProviders = setOf("Saavn")
        )) {
            is StreamResult.Success -> result.streamUrl
            is StreamResult.Failure -> null
        }
    }

    override fun prefetchStreamUrls(songs: List<SongInfo>) {
        songs.forEach { song ->
            if (!inFlightRequests.containsKey(song.videoId)) {
                scope.launch {
                    getStreamUrl(song.videoId, song.title, song.artist)
                }
            }
        }
    }
}

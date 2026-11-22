package com.example.musicapp.service

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.ContentMetadata
import com.example.musicapp.data.DataRepository
import com.example.musicapp.data.download.MusicDownloadManager
import com.example.musicapp.data.settings.SettingsManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Thrown when a track can't be played right now; the service decides whether to retry or skip. */
class TrackUnavailableException(message: String) : IOException(message)

/**
 * Resolves `rivara://track` URIs when the player starts loading an item:
 * 1. a downloaded file is played straight from disk;
 * 2. fully cached audio is played from the cache without touching the network;
 * 3. otherwise a fresh stream URL is resolved (with provider fallback after a failure).
 *
 * Runs on the player's loader thread, so blocking for the network here is expected.
 */
@UnstableApi
class TrackDataSource(
    private val context: Context,
    private val repository: DataRepository,
    private val cache: Cache?,
    private val cachedFactory: DataSource.Factory,
    private val fallbackRequested: Set<String>
) : DataSource {

    private val transferListeners = mutableListOf<TransferListener>()
    private var delegate: DataSource? = null
    private var openedUri: Uri? = null

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners += transferListener
    }

    override fun open(dataSpec: DataSpec): Long {
        val ref = TrackMediaItems.parse(dataSpec.uri)
        val (source, spec) = if (ref == null) {
            cachedFactory.createDataSource() to dataSpec
        } else {
            route(ref, dataSpec)
        }
        transferListeners.forEach(source::addTransferListener)
        delegate = source
        openedUri = spec.uri
        return source.open(spec)
    }

    private fun routeVideo(ref: TrackMediaItems.TrackRef, dataSpec: DataSpec): Pair<DataSource, DataSpec> {
        val key = dataSpec.key ?: "v:${ref.id}"
        if (isFullyCached(key)) {
            return cachedFactory.createDataSource() to dataSpec.buildUpon().setUri("https://cache.rivara.invalid/$key").setKey(key).build()
        }
        val url = runBlocking {
            withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { com.example.musicapp.data.catalog.VideoMatch.videoStreamUrl(ref.id, maxVideoHeight()) }
        } ?: throw TrackUnavailableException("This video isn't available right now.")
        return cachedFactory.createDataSource() to dataSpec.buildUpon().setUri(Uri.parse(url)).setKey(key).build()
    }

    /** 720p on Wi-Fi, 480p on mobile data, 360p with Data Saver. */
    private fun maxVideoHeight(): Int {
        if (SettingsManager.getInstance(context).dataSaverMode.value == "On") return 360
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
        return if (cm?.isActiveNetworkMetered == true) 480 else 720
    }

    private fun route(ref: TrackMediaItems.TrackRef, dataSpec: DataSpec): Pair<DataSource, DataSpec> {
        if (ref.videoOnly) return routeVideo(ref, dataSpec)
        MusicDownloadManager.getInstance(context).getDownloadedFile(ref.id)?.let { file ->
            return FileDataSource() to dataSpec.buildUpon().setUri(Uri.fromFile(file)).setKey(null).build()
        }
        val key = dataSpec.key ?: ref.id
        if (isFullyCached(key)) {
            // Upstream is never contacted for fully cached content.
            return cachedFactory.createDataSource() to dataSpec.buildUpon().setUri("https://cache.rivara.invalid/$key").setKey(key).build()
        }
        if (SettingsManager.getInstance(context).offlineMode.value) {
            throw TrackUnavailableException("Offline mode: \"${ref.title}\" isn't downloaded.")
        }
        val url = ref.episodeUrl ?: runBlocking {
            withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                if (ref.id in fallbackRequested) {
                    repository.getFallbackStreamUrl(ref.id, ref.title, ref.artist, ref.durationSeconds)
                } else {
                    repository.getStreamUrl(ref.id, ref.title, ref.artist, ref.durationSeconds)
                        ?: repository.getFallbackStreamUrl(ref.id, ref.title, ref.artist, ref.durationSeconds)
                }
            }
        } ?: throw TrackUnavailableException("Couldn't find a playable source for \"${ref.title}\".")

        if (url.startsWith("file://")) {
            return FileDataSource() to dataSpec.buildUpon().setUri(Uri.parse(url)).setKey(null).build()
        }
        return cachedFactory.createDataSource() to dataSpec.buildUpon().setUri(Uri.parse(url)).setKey(key).build()
    }

    private fun isFullyCached(key: String): Boolean {
        val cache = cache ?: return false
        return runCatching {
            val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
            length > 0 && cache.isCached(key, 0, length)
        }.getOrDefault(false)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        delegate?.read(buffer, offset, length) ?: throw IOException("Data source not open")

    override fun getUri(): Uri? = openedUri

    override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            delegate?.close()
        } finally {
            delegate = null
            openedUri = null
        }
    }

    class Factory(
        private val context: Context,
        private val repository: DataRepository,
        private val cache: Cache?,
        private val cachedFactory: DataSource.Factory
    ) : DataSource.Factory {
        /** Track IDs whose next load should skip the provider that just failed. */
        val fallbackRequested: MutableSet<String> = ConcurrentHashMap.newKeySet()

        override fun createDataSource(): DataSource =
            TrackDataSource(context, repository, cache, cachedFactory, fallbackRequested)
    }

    companion object {
        private const val RESOLVE_TIMEOUT_MS = 25_000L
    }
}

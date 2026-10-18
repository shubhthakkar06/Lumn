package com.example.musicapp.data.cache

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.offline.DownloadManager
import java.io.File
import java.util.concurrent.Executors

/**
 * Process-wide Media3 unified Cache & Download architecture.
 *
 * One SimpleCache instance is shared by:
 * 1. ExoPlayer CacheDataSource.Factory (instant playback from disk)
 * 2. Media3 DownloadManager (background resumable downloading)
 * 3. ResolvingDataSource (uses a stable cache key for each provider stream)
 */
object MediaCacheProvider {

    @Volatile
    private var downloadCache: SimpleCache? = null

    @Volatile
    private var databaseProvider: DatabaseProvider? = null

    @Volatile
    private var downloadManager: DownloadManager? = null

    private val downloadExecutor = Executors.newFixedThreadPool(4)
    @Synchronized
    fun getDatabaseProvider(context: Context): DatabaseProvider {
        if (databaseProvider == null) {
            databaseProvider = StandaloneDatabaseProvider(context.applicationContext)
        }
        return databaseProvider!!
    }

    @Synchronized
    fun getSimpleCache(context: Context): SimpleCache {
        if (downloadCache == null) {
            val cacheDir = File(context.applicationContext.cacheDir, "media3_shared_cache")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val evictor = LeastRecentlyUsedCacheEvictor(500 * 1024 * 1024L) // 500 MB LRU
            downloadCache = SimpleCache(cacheDir, evictor, getDatabaseProvider(context))
        }
        return downloadCache!!
    }

    fun createLoadControl(): LoadControl {
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 500, // Starts in 500ms
                /* bufferForPlaybackAfterRebufferMs = */ 1_500
            )
            .setBackBuffer(
                /* backBufferDurationMs = */ 30_000,
                /* retainBackBufferFromKeyframe = */ true
            )
            .build()
    }

    fun getCacheDataSourceFactory(context: Context): DataSource.Factory {
        val cache = getSimpleCache(context)
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14; Mobile)")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(10_000)

        val upstreamFactory = DefaultDataSource.Factory(context.applicationContext, httpDataSourceFactory)

        // Keep cached bytes associated with the provider-specific stream key. Playback errors
        // are handled by the ViewModel, which asks the repository for a fresh provider URL.
        val resolvingFactory = ResolvingDataSource.Factory(upstreamFactory) { dataSpec ->
            val uriStr = dataSpec.uri.toString()
            val trackRef = dataSpec.customData as? String ?: dataSpec.key ?: uriStr

            // If it's a file:// URI or active URL, proceed
            if (uriStr.startsWith("file://") || uriStr.startsWith("content://")) {
                dataSpec
            } else {
                // Return DataSpec keyed by stable track ID (customData or key) rather than temporary URL
                dataSpec.buildUpon()
                    .setKey(trackRef)
                    .build()
            }
        }

        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(resolvingFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    @Synchronized
    fun getDownloadManager(context: Context): DownloadManager {
        if (downloadManager == null) {
            val cache = getSimpleCache(context)
            val dbProvider = getDatabaseProvider(context)
            val dataSourceFactory = getCacheDataSourceFactory(context)

            downloadManager = DownloadManager(
                context.applicationContext,
                dbProvider,
                cache,
                dataSourceFactory,
                downloadExecutor
            ).apply {
                maxParallelDownloads = 3
            }
        }
        return downloadManager!!
    }
}

package com.example.musicapp.wear.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.provider.ProviderRouter
import com.example.musicapp.data.provider.StreamQuality
import com.example.musicapp.data.provider.StreamResult
import com.example.musicapp.wear.data.WatchDownloads
import com.example.musicapp.wear.data.WatchLibrary
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/**
 * The watch's own player: plays to Bluetooth headphones (Wear OS asks to connect them when
 * none is), keeps going with the screen off, and plays downloaded songs offline.
 */
@UnstableApi
class WatchPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(TrackSourceFactory()))
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            // Watch speakers aren't for music: prompt for Bluetooth headphones instead.
            .setSuppressPlaybackOnUnsuitableOutput(true)
            .build()
        session = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>
                ) = com.google.common.util.concurrent.Futures.immediateFuture(mediaItems.map { item ->
                    if (item.localConfiguration != null) item else toMediaItem(songOf(item))
                }.toMutableList())
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }

    /** Resolves rivara:// items to a downloaded file or a fresh stream when they start loading. */
    private inner class TrackSourceFactory : DataSource.Factory {
        private val http = DefaultHttpDataSource.Factory().setUserAgent("Mozilla/5.0 (Linux; Android 14)").setAllowCrossProtocolRedirects(true)
        override fun createDataSource(): DataSource = object : DataSource {
            private var delegate: DataSource? = null
            private val listeners = mutableListOf<TransferListener>()
            override fun addTransferListener(transferListener: TransferListener) { listeners += transferListener }
            override fun open(dataSpec: DataSpec): Long {
                val uri = dataSpec.uri
                val (source, spec) = if (uri.scheme != "rivara") {
                    http.createDataSource() to dataSpec
                } else {
                    val id = uri.getQueryParameter("id").orEmpty()
                    val local = WatchDownloads.get(this@WatchPlaybackService).file(id)
                    if (local != null) FileDataSource() to dataSpec.buildUpon().setUri(Uri.fromFile(local)).build()
                    else {
                        val url = uri.getQueryParameter("ep") ?: runBlocking {
                            withTimeoutOrNull(20_000) {
                                (ProviderRouter.shared.resolveStream(
                                    id, uri.getQueryParameter("t"), uri.getQueryParameter("a"),
                                    uri.getQueryParameter("d")?.toLongOrNull() ?: 0L, quality = StreamQuality.NORMAL
                                ) as? StreamResult.Success)?.streamUrl
                            }
                        } ?: throw IOException("Couldn't find a playable source")
                        http.createDataSource() to dataSpec.buildUpon().setUri(Uri.parse(url)).build()
                    }
                }
                listeners.forEach(source::addTransferListener)
                delegate = source
                return source.open(spec)
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int) = delegate?.read(buffer, offset, length) ?: -1
            override fun getUri(): Uri? = delegate?.uri
            override fun close() { delegate?.close(); delegate = null }
        }
    }

    companion object {
        private const val EXTRA_SONG = "song"

        fun toMediaItem(song: SongInfo): MediaItem {
            val uri = Uri.Builder().scheme("rivara").authority("track")
                .appendQueryParameter("id", song.videoId)
                .appendQueryParameter("t", song.title)
                .appendQueryParameter("a", song.artist)
                .appendQueryParameter("d", song.durationSeconds.toString())
                .apply { if (song.isEpisode && !song.audioStreamUrl.isNullOrBlank()) appendQueryParameter("ep", song.audioStreamUrl) }
                .build()
            val meta = MediaMetadata.Builder()
                .setTitle(song.title).setArtist(song.artist)
                .setExtras(Bundle().apply { putString(EXTRA_SONG, WatchLibrary.json.encodeToString(SongInfo.serializer(), song)) })
                .apply { runCatching { if (song.albumArtUrl.isNotBlank()) setArtworkUri(Uri.parse(song.albumArtUrl)) } }
                .build()
            return MediaItem.Builder().setMediaId(song.videoId).setUri(uri).setMediaMetadata(meta).build()
        }

        fun songOf(item: MediaItem): SongInfo =
            item.mediaMetadata.extras?.getString(EXTRA_SONG)?.let { runCatching { WatchLibrary.json.decodeFromString(SongInfo.serializer(), it) }.getOrNull() }
                ?: SongInfo(item.mediaId, item.mediaMetadata.title?.toString().orEmpty(), item.mediaMetadata.artist?.toString().orEmpty(), item.mediaMetadata.artworkUri?.toString().orEmpty())
    }
}

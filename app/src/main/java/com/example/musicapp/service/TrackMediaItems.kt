package com.example.musicapp.service

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.musicapp.data.network.SongInfo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Converts songs to player items and back.
 *
 * Items carry a `rivara://track?...` URI instead of a real stream URL. The player resolves it
 * to a fresh URL (or a downloaded file) only when it starts loading that item, so a whole
 * playlist can sit in the player's queue without expiring links, and the next track is
 * preloaded for gapless playback.
 */
object TrackMediaItems {
    const val SCHEME = "rivara"
    private const val EXTRA_SONG = "rivara_song_json"
    private val json = Json { ignoreUnknownKeys = true }

    fun toMediaItem(song: SongInfo): MediaItem {
        val uri = Uri.Builder()
            .scheme(SCHEME)
            .authority("track")
            .appendQueryParameter("id", song.videoId)
            .appendQueryParameter("t", song.title)
            .appendQueryParameter("a", song.artist)
            .appendQueryParameter("d", song.durationSeconds.toString())
            .apply {
                // Podcast enclosures are permanent links; other stream URLs are never used.
                if (song.isEpisode && !song.audioStreamUrl.isNullOrBlank()) appendQueryParameter("ep", song.audioStreamUrl)
            }
            .build()

        val stored = if (song.isEpisode) song else song.copy(audioStreamUrl = null)
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.albumName.ifBlank { null })
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setExtras(Bundle().apply { putString(EXTRA_SONG, json.encodeToString(stored)) })
            .apply {
                if (song.albumArtUrl.isNotBlank()) runCatching { setArtworkUri(Uri.parse(song.albumArtUrl)) }
            }
            .build()

        return MediaItem.Builder()
            .setMediaId(song.videoId)
            .setUri(uri)
            // Cached audio is keyed by track, not by the (temporary) URL it came from.
            .setCustomCacheKey(song.videoId)
            .setMediaMetadata(metadata)
            .build()
    }

    fun toSong(item: MediaItem): SongInfo {
        item.mediaMetadata.extras?.getString(EXTRA_SONG)?.let { encoded ->
            runCatching { return json.decodeFromString<SongInfo>(encoded) }
        }
        val meta = item.mediaMetadata
        val uri = item.localConfiguration?.uri
        return SongInfo(
            videoId = item.mediaId,
            title = meta.title?.toString().orEmpty(),
            artist = meta.artist?.toString().orEmpty(),
            albumArtUrl = meta.artworkUri?.toString().orEmpty(),
            albumName = meta.albumTitle?.toString().orEmpty(),
            durationSeconds = uri?.getQueryParameter("d")?.toLongOrNull() ?: 0L
        )
    }

    /** The parts of a `rivara://track` URI the stream resolver needs. */
    data class TrackRef(
        val id: String,
        val title: String,
        val artist: String,
        val durationSeconds: Long,
        val episodeUrl: String?
    )

    fun parse(uri: Uri): TrackRef? {
        if (uri.scheme != SCHEME) return null
        val id = uri.getQueryParameter("id")?.takeIf { it.isNotBlank() } ?: return null
        return TrackRef(
            id = id,
            title = uri.getQueryParameter("t").orEmpty(),
            artist = uri.getQueryParameter("a").orEmpty(),
            durationSeconds = uri.getQueryParameter("d")?.toLongOrNull() ?: 0L,
            episodeUrl = uri.getQueryParameter("ep")
        )
    }
}

package com.example.musicapp.service

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.musicapp.data.auth.UserManager
import com.example.musicapp.data.history.PlayHistoryManager
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.playlist.PlaylistManager

/**
 * Browse tree shown by Android Auto and other media browsers:
 * Liked Songs, Recently played, and each playlist.
 */
class MediaLibraryTree(private val context: Context) {
    private val playlists get() = PlaylistManager.getInstance(context)

    fun root(): MediaItem = folder(ROOT, "Rivara", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)

    fun children(parentId: String): List<MediaItem> = when {
        parentId == ROOT -> listOf(
            folder(LIKED, "Liked Songs", MediaMetadata.MEDIA_TYPE_PLAYLIST),
            folder(RECENT, "Recently played", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
            folder(PLAYLISTS, "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
        )
        parentId == LIKED -> playable(LIKED, playlists.likedSongs.value)
        parentId == RECENT -> playable(RECENT, recentSongs())
        parentId == PLAYLISTS -> playlists.playlists.value.map { pl ->
            folder("$PLAYLIST_PREFIX${pl.id}", pl.name, MediaMetadata.MEDIA_TYPE_PLAYLIST, pl.coverUrl)
        }
        parentId.startsWith(PLAYLIST_PREFIX) -> {
            val id = parentId.removePrefix(PLAYLIST_PREFIX)
            playable(parentId, playlists.getPlaylist(id)?.songs.orEmpty())
        }
        else -> emptyList()
    }

    fun item(mediaId: String): MediaItem? {
        if (mediaId == ROOT) return root()
        children(ROOT).firstOrNull { it.mediaId == mediaId }?.let { return it }
        val (songs, index) = playContext(mediaId) ?: return null
        return TrackMediaItems.toMediaItem(songs[index])
    }

    /** For a playable browse ID ("parent|videoId"), the list it belongs to and its position. */
    fun playContext(mediaId: String): Pair<List<SongInfo>, Int>? {
        val parent = mediaId.substringBefore(SEPARATOR, "")
        val videoId = mediaId.substringAfter(SEPARATOR, "")
        if (parent.isEmpty() || videoId.isEmpty()) return null
        val songs = when {
            parent == LIKED -> playlists.likedSongs.value
            parent == RECENT -> recentSongs()
            parent.startsWith(PLAYLIST_PREFIX) -> playlists.getPlaylist(parent.removePrefix(PLAYLIST_PREFIX))?.songs.orEmpty()
            else -> emptyList()
        }
        val index = songs.indexOfFirst { it.videoId == videoId }
        return if (index >= 0) songs to index else null
    }

    private fun recentSongs(): List<SongInfo> {
        val userId = UserManager.getInstance(context).currentUser.value.id
        return PlayHistoryManager.getInstance(context).getUserHistory(userId)
            .distinctBy { it.videoId }
            .take(30)
            .map { SongInfo(it.videoId, it.title, it.artist, it.albumArtUrl) }
    }

    private fun playable(parent: String, songs: List<SongInfo>): List<MediaItem> =
        songs.take(200).map { song ->
            TrackMediaItems.toMediaItem(song).buildUpon().setMediaId("$parent$SEPARATOR${song.videoId}").build()
        }

    private fun folder(id: String, title: String, type: Int, artwork: String = ""): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(type)
                    .apply { if (artwork.isNotBlank()) runCatching { setArtworkUri(Uri.parse(artwork)) } }
                    .build()
            )
            .build()

    companion object {
        const val ROOT = "root"
        const val LIKED = "liked"
        const val RECENT = "recent"
        const val PLAYLISTS = "playlists"
        const val PLAYLIST_PREFIX = "playlist:"
        private const val SEPARATOR = "|"
    }
}

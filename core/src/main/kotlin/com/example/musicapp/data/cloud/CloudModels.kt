package com.example.musicapp.data.cloud

import com.example.musicapp.data.network.SongInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Playlist as stored in the cloud; the same JSON on phone, Mac and watch. */
@Serializable
data class CloudPlaylist(
    val id: String,
    val name: String,
    val creator: String = "You",
    val coverUrl: String = "",
    val songs: List<SongInfo> = emptyList(),
    val isPinned: Boolean = false,
    val createdAtMs: Long = 0L,
    val sourceUrl: String = ""
)

/** A followed podcast or YouTube channel. */
@Serializable
data class FollowedShow(
    /** "rss:<feed url>" or "yt:<channel url>". */
    val id: String,
    val title: String,
    val author: String = "",
    val imageUrl: String = "",
    val followedAtMs: Long = 0L
)

/** What the phone sends its paired watch: the signed-in account and its library. */
@Serializable
data class WatchLibraryPayload(
    val name: String = "",
    val email: String = "",
    val playlists: List<CloudPlaylist> = emptyList(),
    val liked: List<SongInfo> = emptyList(),
    val updatedAtMs: Long = 0L
)

@Serializable
data class CloudProfile(val name: String = "", val photo: String = "", val photoAt: Long = 0L)

object CloudJson {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /** Stream URLs expire, so they never go to the cloud (podcast enclosures are permanent). */
    fun portable(song: SongInfo): SongInfo =
        if (song.isEpisode || song.audioStreamUrl.isNullOrBlank()) song else song.copy(audioStreamUrl = null)

    fun song(song: SongInfo): String = json.encodeToString(SongInfo.serializer(), portable(song))
    fun songOrNull(text: String): SongInfo? = runCatching { json.decodeFromString(SongInfo.serializer(), text) }.getOrNull()

    fun playlist(p: CloudPlaylist): String = json.encodeToString(CloudPlaylist.serializer(), p.copy(songs = p.songs.map(::portable)))
    fun playlistOrNull(text: String): CloudPlaylist? = runCatching { json.decodeFromString(CloudPlaylist.serializer(), text) }.getOrNull()

    fun show(s: FollowedShow): String = json.encodeToString(FollowedShow.serializer(), s)
    fun showOrNull(text: String): FollowedShow? = runCatching { json.decodeFromString(FollowedShow.serializer(), text) }.getOrNull()

    fun likedSnapshot(songs: List<SongInfo>): Map<String, String> =
        LinkedHashMap<String, String>().apply { songs.forEach { put(CloudPaths.key(it.videoId), song(it)) } }

    fun playlistSnapshot(playlists: List<CloudPlaylist>): Map<String, String> =
        LinkedHashMap<String, String>().apply { playlists.forEach { put(CloudPaths.key(it.id), playlist(it)) } }

    fun artistSnapshot(names: Collection<String>): Map<String, String> =
        LinkedHashMap<String, String>().apply { names.forEach { put(CloudPaths.key(it), it.trim().lowercase()) } }

    fun showSnapshot(shows: List<FollowedShow>): Map<String, String> =
        LinkedHashMap<String, String>().apply { shows.forEach { put(CloudPaths.key(it.id), show(it)) } }
}

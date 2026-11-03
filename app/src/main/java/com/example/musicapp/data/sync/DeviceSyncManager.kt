package com.example.musicapp.data.sync

import android.content.Context
import com.example.musicapp.data.auth.UserManager
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.playlist.PlaylistManager
import com.example.musicapp.data.playlist.UserPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
data class SyncSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationSeconds: Long = 0L,
    val albumArtUrl: String = "",
    val audioUrl: String? = null
)

@Serializable
data class SyncPlaylist(
    val id: String,
    val name: String,
    val creator: String = "",
    val coverUrl: String = "",
    val songs: List<SyncSong> = emptyList(),
    val createdAtMs: Long = System.currentTimeMillis()
)

@Serializable
data class RivaraSyncPayload(
    val version: Int = 1,
    val deviceId: String = "android_" + android.os.Build.MODEL.replace(" ", "_"),
    val deviceName: String = "Android (${android.os.Build.MODEL})",
    val deviceType: String = "Phone",
    val userEmail: String,
    val userId: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val playlists: List<SyncPlaylist> = emptyList(),
    val likedSongs: List<SyncSong> = emptyList()
)

object DeviceSyncManager {
    private const val DEFAULT_MAC_IP = "10.50.42.24"
    private const val PREFS_NAME = "rivara_sync_prefs"
    private const val KEY_LAST_MAC_IP = "last_mac_ip"

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun syncWithMac(
        context: Context,
        explicitIp: String? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        val userManager = UserManager.getInstance(context)
        val currentUser = userManager.currentUser.value
        val userEmail = currentUser.email.ifBlank { "navya.shubh2006@gmail.com" }
        val playlistManager = PlaylistManager.getInstance(context)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedIp = prefs.getString(KEY_LAST_MAC_IP, null)

        val candidateHosts = listOfNotNull(
            explicitIp?.trim()?.takeIf { it.isNotBlank() },
            savedIp,
            DEFAULT_MAC_IP,
            "127.0.0.1",
            "10.0.2.2"
        ).distinct()

        val outgoingPlaylists = playlistManager.playlists.value.map { pl ->
            SyncPlaylist(
                id = pl.id,
                name = pl.name,
                creator = pl.creator,
                coverUrl = pl.coverUrl.ifBlank { pl.songs.firstOrNull { it.albumArtUrl.isNotBlank() }?.albumArtUrl.orEmpty() },
                songs = pl.songs.map { s ->
                    SyncSong(
                        id = s.videoId,
                        title = s.title,
                        artist = s.artist,
                        album = s.albumName,
                        durationSeconds = s.durationSeconds,
                        albumArtUrl = s.albumArtUrl,
                        audioUrl = s.audioStreamUrl
                    )
                },
                createdAtMs = pl.createdAtMs
            )
        }

        val outgoingLiked = playlistManager.likedSongs.value.map { s ->
            SyncSong(
                id = s.videoId,
                title = s.title,
                artist = s.artist,
                album = s.albumName,
                durationSeconds = s.durationSeconds,
                albumArtUrl = s.albumArtUrl,
                audioUrl = s.audioStreamUrl
            )
        }

        val payload = RivaraSyncPayload(
            userEmail = userEmail,
            userId = currentUser.id,
            playlists = outgoingPlaylists,
            likedSongs = outgoingLiked
        )

        val payloadJson = json.encodeToString(payload)
        val mediaType = "application/json; charset=utf-8".toMediaType()

        var lastError: Exception? = null

        for (host in candidateHosts) {
            val url = "http://$host:8765/api/sync/library"
            try {
                val req = Request.Builder()
                    .url(url)
                    .post(payloadJson.toRequestBody(mediaType))
                    .build()

                http.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val respBody = resp.body?.string().orEmpty()
                        val merged = json.decodeFromString<RivaraSyncPayload>(respBody)

                        // Save the working host in preferences
                        prefs.edit().putString(KEY_LAST_MAC_IP, host).apply()

                        // Merge into Android's local library
                        mergeIntoPlaylistManager(playlistManager, currentUser.id, merged)

                        return@withContext Result.success(merged.playlists.size)
                    } else {
                        lastError = IllegalStateException("Mac sync returned HTTP ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                lastError = e
            }
        }

        Result.failure(lastError ?: IllegalStateException("Could not connect to Rivara on Mac ($DEFAULT_MAC_IP:8765)"))
    }

    private fun mergeIntoPlaylistManager(
        playlistManager: PlaylistManager,
        activeUserId: String,
        payload: RivaraSyncPayload
    ) {
        val currentPlaylists = playlistManager.playlists.value.toMutableList()

        for (syncPl in payload.playlists) {
            val existingIdx = currentPlaylists.indexOfFirst {
                it.id == syncPl.id || it.name.trim().equals(syncPl.name.trim(), ignoreCase = true)
            }
            val syncSongs = syncPl.songs.map { s ->
                SongInfo(
                    videoId = s.id,
                    title = s.title,
                    artist = s.artist,
                    albumArtUrl = s.albumArtUrl,
                    audioStreamUrl = s.audioUrl,
                    albumName = s.album,
                    durationSeconds = s.durationSeconds,
                    importMatchFound = true
                )
            }

            if (existingIdx >= 0) {
                val existing = currentPlaylists[existingIdx]
                val mergedSongs = (existing.songs + syncSongs).distinctBy {
                    it.videoId.ifBlank { "${it.title}_${it.artist}".lowercase() }
                }
                val cover = existing.coverUrl.ifBlank { syncPl.coverUrl }.ifBlank {
                    mergedSongs.firstOrNull { it.albumArtUrl.isNotBlank() }?.albumArtUrl.orEmpty()
                }
                currentPlaylists[existingIdx] = existing.copy(
                    songs = mergedSongs,
                    coverUrl = cover
                )
            } else {
                val cover = syncPl.coverUrl.ifBlank {
                    syncSongs.firstOrNull { it.albumArtUrl.isNotBlank() }?.albumArtUrl.orEmpty()
                }
                currentPlaylists.add(
                    UserPlaylist(
                        id = syncPl.id,
                        name = syncPl.name,
                        creator = syncPl.creator.ifBlank { payload.userEmail.substringBefore("@") },
                        coverUrl = cover,
                        songs = syncSongs,
                        createdAtMs = syncPl.createdAtMs,
                        userId = activeUserId
                    )
                )
            }
        }

        // Apply to PlaylistManager
        playlistManager.applyMergedPlaylists(currentPlaylists)

        // Merge Liked Songs
        val currentLiked = playlistManager.likedSongs.value.toMutableList()
        for (syncLiked in payload.likedSongs) {
            if (currentLiked.none { it.videoId == syncLiked.id || (it.title.equals(syncLiked.title, true) && it.artist.equals(syncLiked.artist, true)) }) {
                currentLiked.add(
                    SongInfo(
                        videoId = syncLiked.id,
                        title = syncLiked.title,
                        artist = syncLiked.artist,
                        albumArtUrl = syncLiked.albumArtUrl,
                        audioStreamUrl = syncLiked.audioUrl,
                        albumName = syncLiked.album,
                        durationSeconds = syncLiked.durationSeconds,
                        importMatchFound = true
                    )
                )
            }
        }
        playlistManager.applyMergedLikedSongs(currentLiked)
    }
}

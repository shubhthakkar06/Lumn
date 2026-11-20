package com.example.musicapp.data.sync

import android.content.Context
import com.example.musicapp.data.auth.UserManager
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.playlist.PlaylistManager
import com.example.musicapp.data.playlist.UserPlaylist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    // Kept for wire compatibility with older desktop builds. Stream URLs expire and are
    // provider specific, so this app never sends or trusts them.
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

class MacNotPairedException : IllegalStateException(
    "Pair with your Mac first: open Rivara on the Mac, go to Sync, and enter its address and pairing code."
)

/**
 * Library sync between this phone and a Rivara desktop app on the same network.
 *
 * Sync only happens with a Mac the user has explicitly paired by entering its address and
 * the pairing code shown in the desktop app. Nothing is uploaded to any shared or public
 * storage, and the Mac rejects requests that do not carry its pairing code.
 */
object DeviceSyncManager {
    const val SYNC_PORT = 8765
    const val PAIR_CODE_HEADER = "X-Rivara-Pair-Code"

    private const val PREFS_NAME = "rivara_sync_prefs"
    private const val KEY_LAST_MAC_IP = "last_mac_ip"
    private const val KEY_PAIR_CODE = "mac_pair_code"
    private const val SILENT_SYNC_DELAY_MS = 5_000L

    data class MacPairing(val host: String, val code: String)

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val syncMutex = Mutex()
    @Volatile private var pendingSilentSync: Job? = null

    fun getPairing(context: Context): MacPairing? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val host = prefs.getString(KEY_LAST_MAC_IP, null)?.takeIf { it.isNotBlank() } ?: return null
        val code = prefs.getString(KEY_PAIR_CODE, null)?.takeIf { it.isNotBlank() } ?: return null
        return MacPairing(host, code)
    }

    fun forgetPairing(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_LAST_MAC_IP)
            .remove(KEY_PAIR_CODE)
            .apply()
    }

    private fun savePairing(context: Context, pairing: MacPairing) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_MAC_IP, pairing.host)
            .putString(KEY_PAIR_CODE, pairing.code)
            .apply()
    }

    /** Accepts "192.168.1.5", "192.168.1.5:8765" or "http://192.168.1.5:8765/". */
    internal fun normalizeHost(raw: String): String = raw.trim()
        .removePrefix("http://")
        .removePrefix("https://")
        .substringBefore('/')
        .substringBefore(':')
        .trim()

    /**
     * Debounced background sync after a library change. Does nothing unless a Mac is paired,
     * so libraries never leave the device without the user setting that up.
     */
    fun triggerSilentSync(context: Context) {
        val appContext = context.applicationContext
        if (getPairing(appContext) == null) return
        if (UserManager.getInstance(appContext).currentUser.value.isGuest) return
        pendingSilentSync?.cancel()
        pendingSilentSync = syncScope.launch {
            delay(SILENT_SYNC_DELAY_MS)
            runCatching { syncWithMac(appContext) }
        }
    }

    /**
     * Sends this account's library to the paired Mac and merges the Mac's library back.
     * Pass [host] and [pairCode] to pair (or re-pair); they are saved after a successful sync.
     */
    suspend fun syncWithMac(
        context: Context,
        host: String? = null,
        pairCode: String? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val currentUser = UserManager.getInstance(appContext).currentUser.value
        if (currentUser.isGuest) {
            return@withContext Result.failure(IllegalStateException("Sign in to sync your library with your Mac."))
        }

        val explicit = if (!host.isNullOrBlank() && !pairCode.isNullOrBlank()) {
            MacPairing(normalizeHost(host), pairCode.trim())
        } else null
        val pairing = explicit ?: getPairing(appContext)
            ?: return@withContext Result.failure(MacNotPairedException())
        if (pairing.host.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Enter the address shown in Rivara on your Mac."))
        }

        syncMutex.withLock {
            val playlistManager = PlaylistManager.getInstance(appContext)
            val payload = RivaraSyncPayload(
                userEmail = currentUser.email,
                userId = currentUser.id,
                playlists = playlistManager.playlists.value.map { it.toSyncPlaylist() },
                likedSongs = playlistManager.likedSongs.value.map { it.toSyncSong() }
            )
            val request = Request.Builder()
                .url("http://${pairing.host}:$SYNC_PORT/api/sync/library")
                .header(PAIR_CODE_HEADER, pairing.code)
                .post(json.encodeToString(payload).toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            try {
                http.newCall(request).execute().use { resp ->
                    when {
                        resp.code == 401 || resp.code == 403 -> Result.failure(
                            IllegalStateException("Your Mac rejected the pairing code. Check the code shown in Rivara's Sync window on the Mac.")
                        )
                        !resp.isSuccessful -> Result.failure(IllegalStateException("Mac sync returned HTTP ${resp.code}"))
                        else -> {
                            val merged = json.decodeFromString<RivaraSyncPayload>(resp.body?.string().orEmpty())
                            if (explicit != null) savePairing(appContext, explicit)
                            mergeIntoPlaylistManager(playlistManager, currentUser.id, merged)
                            Result.success(merged.playlists.size)
                        }
                    }
                }
            } catch (e: Exception) {
                Result.failure(
                    IllegalStateException(
                        "Couldn't reach your Mac at ${pairing.host}. Make sure Rivara is open on the Mac and both devices are on the same Wi-Fi.",
                        e
                    )
                )
            }
        }
    }

    private fun UserPlaylist.toSyncPlaylist() = SyncPlaylist(
        id = id,
        name = name,
        creator = creator,
        coverUrl = coverUrl.ifBlank { songs.firstOrNull { it.albumArtUrl.isNotBlank() }?.albumArtUrl.orEmpty() },
        songs = songs.map { it.toSyncSong() },
        createdAtMs = createdAtMs
    )

    private fun SongInfo.toSyncSong() = SyncSong(
        id = videoId,
        title = title,
        artist = artist,
        album = albumName,
        durationSeconds = durationSeconds,
        albumArtUrl = albumArtUrl,
        audioUrl = null
    )

    private fun SyncSong.toSongInfo() = SongInfo(
        videoId = id,
        title = title,
        artist = artist,
        albumArtUrl = albumArtUrl,
        audioStreamUrl = null,
        albumName = album,
        durationSeconds = durationSeconds
    )

    private fun mergeIntoPlaylistManager(
        playlistManager: PlaylistManager,
        activeUserId: String,
        payload: RivaraSyncPayload
    ) {
        playlistManager.updatePlaylistsFromSync { current ->
            val merged = current.toMutableList()
            for (syncPl in payload.playlists) {
                val existingIdx = merged.indexOfFirst {
                    it.id == syncPl.id || it.name.trim().equals(syncPl.name.trim(), ignoreCase = true)
                }
                val syncSongs = syncPl.songs.map { it.toSongInfo() }
                if (existingIdx >= 0) {
                    val existing = merged[existingIdx]
                    val syncKeys = syncSongs.map { it.syncKey() }.toSet()
                    val localOnly = existing.songs.filter { it.syncKey() !in syncKeys }
                    // Keep local metadata for songs both sides know about, so a richer local
                    // entry (for example one with a match flag) is not replaced by the copy.
                    val localByKey = existing.songs.associateBy { it.syncKey() }
                    val mergedSongs = syncSongs.map { localByKey[it.syncKey()] ?: it } + localOnly
                    val cover = existing.coverUrl.ifBlank { syncPl.coverUrl }.ifBlank {
                        mergedSongs.firstOrNull { it.albumArtUrl.isNotBlank() }?.albumArtUrl.orEmpty()
                    }
                    merged[existingIdx] = existing.copy(songs = mergedSongs, coverUrl = cover)
                } else {
                    merged.add(
                        UserPlaylist(
                            id = syncPl.id,
                            name = syncPl.name,
                            creator = syncPl.creator.ifBlank { "You" },
                            coverUrl = syncPl.coverUrl.ifBlank {
                                syncSongs.firstOrNull { it.albumArtUrl.isNotBlank() }?.albumArtUrl.orEmpty()
                            },
                            songs = syncSongs,
                            createdAtMs = syncPl.createdAtMs,
                            userId = activeUserId
                        )
                    )
                }
            }
            merged
        }

        playlistManager.updateLikedSongsFromSync { current ->
            val merged = current.toMutableList()
            for (syncLiked in payload.likedSongs) {
                val exists = merged.any {
                    it.videoId == syncLiked.id ||
                        (it.title.equals(syncLiked.title, true) && it.artist.equals(syncLiked.artist, true))
                }
                if (!exists) merged.add(syncLiked.toSongInfo())
            }
            merged
        }
    }

    private fun SongInfo.syncKey(): String = videoId.ifBlank { "${title}_${artist}".lowercase() }
}

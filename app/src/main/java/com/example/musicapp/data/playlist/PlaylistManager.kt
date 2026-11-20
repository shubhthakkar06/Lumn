package com.example.musicapp.data.playlist

import android.content.Context
import com.example.musicapp.data.MusicCatalog
import com.example.musicapp.data.auth.UserProfile
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.storage.AtomicFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Serializable
data class UserPlaylist(
    val id: String,
    val name: String,
    val creator: String = "You",
    val coverUrl: String = "",
    val songs: List<SongInfo> = emptyList(),
    val isPinned: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis(),
    val sourceUrl: String = "",
    val userId: String = ""
)

class PlaylistManager private constructor(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    // All disk writes go through one background thread, in order. The UI thread only updates
    // in-memory state, so saving a 1,000-song import can no longer freeze the app.
    private val writer: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "rivara-playlist-writer").apply { isDaemon = true }
    }

    private val userManager = com.example.musicapp.data.auth.UserManager.getInstance(context)
    @Volatile private var activeUserId: String = userManager.currentUser.value.id

    private val legacyPlaylistsFile = File(context.filesDir, "user_playlists.json")
    private val legacyLikedSongsFile = File(context.filesDir, "liked_songs.json")

    private val _playlists = MutableStateFlow<List<UserPlaylist>>(emptyList())
    val playlists: StateFlow<List<UserPlaylist>> = _playlists.asStateFlow()

    private val _likedSongs = MutableStateFlow<List<SongInfo>>(emptyList())
    val likedSongs: StateFlow<List<SongInfo>> = _likedSongs.asStateFlow()

    init {
        loadData(activeUserId)
        scope.launch(Dispatchers.Main.immediate) {
            userManager.currentUser.collect { user ->
                if (user.id != activeUserId) {
                    loadData(user.id)
                }
            }
        }
    }

    private fun sanitizeUserId(id: String): String =
        id.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "default" }

    private fun getPlaylistsFile(userId: String): File =
        File(context.filesDir, "user_playlists_${sanitizeUserId(userId)}.json")

    private fun getLikedSongsFile(userId: String): File =
        File(context.filesDir, "liked_songs_${sanitizeUserId(userId)}.json")

    fun switchUser(userId: String) {
        if (userId != activeUserId) {
            loadData(userId)
        }
    }

    /** Blocks until every queued write has reached disk. */
    fun flushPendingWrites() {
        runCatching { writer.submit {}.get() }
    }

    private fun loadData(userId: String) {
        // A user switch reads files from disk, so writes queued for the previous user must
        // land first or a just-made change would be lost.
        flushPendingWrites()
        activeUserId = userId
        val userPlaylistsFile = getPlaylistsFile(userId)
        val userLikedSongsFile = getLikedSongsFile(userId)
        val otherUsers = userManager.allUsers.value.filter { it.id != userId }

        // 1. Liked Songs
        try {
            // A readable file is authoritative, even when it is an empty list: the user may have
            // unliked everything. Recovery only runs when the file is missing or unreadable.
            val parsedLiked = if (userLikedSongsFile.exists()) {
                runCatching { json.decodeFromString<List<SongInfo>>(userLikedSongsFile.readText()) }.getOrNull()
            } else null
            var loadedLiked = parsedLiked.orEmpty()

            if (parsedLiked == null) {
                val recovered = recoverLikedSongsOnDevice(userId, otherUsers)
                if (recovered.isNotEmpty()) {
                    loadedLiked = recovered
                    AtomicFiles.writeText(userLikedSongsFile, json.encodeToString(loadedLiked))
                }
            }

            _likedSongs.value = loadedLiked.map(::storable)
        } catch (e: Exception) {
            e.printStackTrace()
            _likedSongs.value = emptyList()
        }

        // 2. Playlists
        try {
            var loadedPlaylists = emptyList<UserPlaylist>()
            val rawPlaylists = if (userPlaylistsFile.exists()) {
                runCatching { json.decodeFromString<List<UserPlaylist>>(userPlaylistsFile.readText()) }.getOrNull()
            } else null
            if (rawPlaylists != null) {
                val rawList = rawPlaylists
                if (rawList.isNotEmpty()) {
                    // Sanitize against duplicate or foreign playlists while preserving user's own playlists
                    val sanitized = rawList.mapNotNull { pl ->
                        when {
                            // Explicitly matches this user
                            pl.userId == userId -> pl
                            // Explicitly assigned to another active account on this device -> filter out
                            pl.userId.isNotBlank() && otherUsers.any { it.id == pl.userId } -> null
                            // Legacy unassigned with creator matching another registered account -> filter out
                            pl.userId.isBlank() && otherUsers.any {
                                it.name.equals(pl.creator, ignoreCase = true) || it.username.equals(pl.creator, ignoreCase = true)
                            } -> null
                            // Otherwise it belongs to this user (e.g. legacy or migrated from google_ / guest)
                            else -> pl.copy(userId = userId)
                        }
                    }
                    loadedPlaylists = sanitized
                    if (loadedPlaylists != rawList) {
                        AtomicFiles.writeText(userPlaylistsFile, json.encodeToString(loadedPlaylists))
                    }
                }
            }

            // Missing or unreadable: recover from backup / previous Google, Guest or legacy files
            // on this device (never other accounts' files).
            if (rawPlaylists == null) {
                val recovered = recoverPlaylistsOnDevice(userId, otherUsers)
                if (recovered.isNotEmpty()) {
                    loadedPlaylists = recovered.map { it.copy(userId = userId) }
                    AtomicFiles.writeText(userPlaylistsFile, json.encodeToString(loadedPlaylists))
                }
            }

            _playlists.value = loadedPlaylists.map { pl -> pl.copy(songs = pl.songs.map(::storable)) }
            if (loadedPlaylists.isEmpty() && !userId.startsWith("guest_")) {
                com.example.musicapp.data.sync.DeviceSyncManager.triggerSilentSync(context)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _playlists.value = emptyList()
        }
    }

    private fun recoverPlaylistsOnDevice(userId: String, otherUsers: List<UserProfile>): List<UserPlaylist> {
        val filesDir = context.filesDir ?: return emptyList()
        val allFiles = filesDir.listFiles() ?: return emptyList()

        // 1. User backup file
        val backupFile = File(filesDir, "user_playlists_${sanitizeUserId(userId)}_backup.json")
        if (backupFile.exists() && backupFile.length() > 5) {
            runCatching {
                val list = json.decodeFromString<List<UserPlaylist>>(backupFile.readText())
                if (list.isNotEmpty()) return list
            }
        }

        // Files that belong to other named accounts (usr_* or user_*) must NEVER be adopted
        val currentFileName = "user_playlists_${sanitizeUserId(userId)}.json"

        // Candidate legacy files: only older un-namespaced files or google_/guest_ transitions
        val candidateFiles = allFiles.filter { f ->
            f.name.endsWith(".json") &&
            f.length() > 5 &&
            f.name != currentFileName &&
            !f.name.startsWith("user_playlists_usr_") &&
            !f.name.startsWith("user_playlists_user_") &&
            (f.name.startsWith("user_playlists_google_") ||
             f.name.startsWith("user_playlists_guest_") ||
             f.name == "user_playlists.json")
        }.sortedByDescending { it.lastModified() }

        var bestMatch: List<UserPlaylist> = emptyList()
        var maxSongs = -1

        for (file in candidateFiles) {
            runCatching {
                val list = json.decodeFromString<List<UserPlaylist>>(file.readText())
                val validForThisUser = list.filter { pl ->
                    !otherUsers.any { it.id == pl.userId || (pl.userId.isBlank() && it.name.equals(pl.creator, true)) }
                }
                val totalSongs = validForThisUser.sumOf { it.songs.size }
                if (validForThisUser.isNotEmpty() && totalSongs > maxSongs) {
                    maxSongs = totalSongs
                    bestMatch = validForThisUser
                }
            }
        }

        return bestMatch
    }

    private fun recoverLikedSongsOnDevice(userId: String, otherUsers: List<UserProfile>): List<SongInfo> {
        val filesDir = context.filesDir ?: return emptyList()
        val allFiles = filesDir.listFiles() ?: return emptyList()

        val backupFile = File(filesDir, "liked_songs_${sanitizeUserId(userId)}_backup.json")
        if (backupFile.exists() && backupFile.length() > 5) {
            runCatching {
                val list = json.decodeFromString<List<SongInfo>>(backupFile.readText())
                if (list.isNotEmpty()) return list
            }
        }

        val currentFileName = "liked_songs_${sanitizeUserId(userId)}.json"

        val candidateFiles = allFiles.filter { f ->
            f.name.endsWith(".json") &&
            f.length() > 5 &&
            f.name != currentFileName &&
            !f.name.startsWith("liked_songs_usr_") &&
            !f.name.startsWith("liked_songs_user_") &&
            (f.name.startsWith("liked_songs_google_") ||
             f.name.startsWith("liked_songs_guest_") ||
             f.name == "liked_songs.json")
        }.sortedByDescending { it.lastModified() }

        var bestMatch: List<SongInfo> = emptyList()

        for (file in candidateFiles) {
            runCatching {
                val list = json.decodeFromString<List<SongInfo>>(file.readText())
                if (list.size > bestMatch.size) {
                    bestMatch = list
                }
            }
        }

        return bestMatch
    }

    private fun savePlaylists(notifySync: Boolean = true) {
        val userId = activeUserId
        val snapshot = _playlists.value
        writer.execute {
            try {
                // Write the latest state for this user (a later change may already be in memory);
                // fall back to the snapshot if the active user changed meanwhile.
                val content = json.encodeToString(if (activeUserId == userId) _playlists.value else snapshot)
                AtomicFiles.writeText(getPlaylistsFile(userId), content)
                AtomicFiles.writeText(File(context.filesDir, "user_playlists_${sanitizeUserId(userId)}_backup.json"), content)
            } catch (e: Exception) {
                android.util.Log.e("PlaylistManager", "Could not save playlists", e)
            }
        }
        if (notifySync) com.example.musicapp.data.sync.DeviceSyncManager.triggerSilentSync(context)
    }

    private fun saveLikedSongs(notifySync: Boolean = true) {
        val userId = activeUserId
        val snapshot = _likedSongs.value
        writer.execute {
            try {
                val content = json.encodeToString(if (activeUserId == userId) _likedSongs.value else snapshot)
                AtomicFiles.writeText(getLikedSongsFile(userId), content)
                AtomicFiles.writeText(File(context.filesDir, "liked_songs_${sanitizeUserId(userId)}_backup.json"), content)
            } catch (e: Exception) {
                android.util.Log.e("PlaylistManager", "Could not save liked songs", e)
            }
        }
        if (notifySync) com.example.musicapp.data.sync.DeviceSyncManager.triggerSilentSync(context)
    }

    /**
     * Stream URLs expire and belong to one provider, so they are never persisted. Podcast
     * episodes keep theirs because it is the feed's permanent enclosure URL, and downloaded
     * files are looked up by ID at play time.
     */
    private fun storable(song: SongInfo): SongInfo =
        if (song.audioStreamUrl.isNullOrBlank() || song.isEpisode) song else song.copy(audioStreamUrl = null)

    fun isLiked(videoId: String): Boolean {
        return _likedSongs.value.any { it.videoId == videoId }
    }

    fun toggleLike(song: SongInfo): Boolean {
        val currentlyLiked = isLiked(song.videoId)
        _likedSongs.update { liked ->
            if (currentlyLiked) liked.filter { it.videoId != song.videoId }
            else listOf(storable(song)) + liked.filter { it.videoId != song.videoId }
        }
        saveLikedSongs()
        return !currentlyLiked
    }

    fun createPlaylist(
        name: String,
        creator: String = "",
        coverUrl: String = "",
        songs: List<SongInfo> = emptyList(),
        sourceUrl: String = ""
    ): UserPlaylist? {
        val currentProfile = userManager.currentUser.value
        val isGuest = if (activeUserId.startsWith("user_") || activeUserId.startsWith("google_") || activeUserId.startsWith("usr_")) {
            false
        } else {
            currentProfile.isGuest || activeUserId.startsWith("guest_")
        }
        if (isGuest) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, "Sign in to create playlists", android.widget.Toast.LENGTH_SHORT).show()
            }
            return null
        }
        val resolvedCreator = creator.ifBlank { currentProfile.name.ifBlank { "You" } }
        val resolvedCoverUrl = coverUrl.ifBlank {
            songs.firstOrNull { it.albumArtUrl.isNotBlank() }?.albumArtUrl.orEmpty()
        }
        val id = "pl_" + System.currentTimeMillis() + "_" + (1000..9999).random()
        val newPlaylist = UserPlaylist(
            id = id,
            name = name.trim(),
            creator = resolvedCreator,
            coverUrl = resolvedCoverUrl,
            songs = songs.map(::storable),
            sourceUrl = sourceUrl,
            userId = activeUserId
        )
        _playlists.update { listOf(newPlaylist) + it }
        savePlaylists()
        return newPlaylist
    }

    fun addSongToPlaylist(playlistId: String, song: SongInfo) {
        if (com.example.musicapp.data.auth.UserManager.getInstance(context).currentUser.value.isGuest) return
        _playlists.update { all ->
            all.map { pl ->
                if (pl.id == playlistId && pl.songs.none { it.videoId == song.videoId }) {
                    pl.copy(songs = pl.songs + storable(song))
                } else pl
            }
        }
        savePlaylists()
    }

    fun removeSongFromPlaylist(playlistId: String, videoId: String) {
        _playlists.update { all ->
            all.map { pl -> if (pl.id == playlistId) pl.copy(songs = pl.songs.filter { it.videoId != videoId }) else pl }
        }
        savePlaylists()
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        if (newName.isBlank()) return
        _playlists.update { all -> all.map { pl -> if (pl.id == playlistId) pl.copy(name = newName.trim()) else pl } }
        savePlaylists()
    }

    fun moveSong(playlistId: String, fromIndex: Int, toIndex: Int) {
        var changed = false
        _playlists.update { all ->
            all.map { pl ->
                if (pl.id != playlistId) return@map pl
                val songs = pl.songs.toMutableList()
                if (fromIndex in songs.indices && toIndex in songs.indices && fromIndex != toIndex) {
                    songs.add(toIndex, songs.removeAt(fromIndex))
                    changed = true
                    pl.copy(songs = songs)
                } else pl
            }
        }
        if (changed) savePlaylists()
    }

    fun deletePlaylist(playlistId: String) {
        _playlists.update { all -> all.filter { it.id != playlistId } }
        savePlaylists()
    }

    fun getPlaylist(playlistId: String): UserPlaylist? {
        return _playlists.value.find { it.id == playlistId }
    }

    /** Applies a sync merge atomically against the latest state, without re-triggering sync. */
    fun updatePlaylistsFromSync(transform: (List<UserPlaylist>) -> List<UserPlaylist>) {
        _playlists.update { current ->
            transform(current).map { pl -> pl.copy(songs = pl.songs.map(::storable)) }
        }
        savePlaylists(notifySync = false)
    }

    fun updateLikedSongsFromSync(transform: (List<SongInfo>) -> List<SongInfo>) {
        _likedSongs.update { current -> transform(current).map(::storable) }
        saveLikedSongs(notifySync = false)
    }

    companion object {
        @Volatile
        private var instance: PlaylistManager? = null

        fun getInstance(context: Context): PlaylistManager {
            return instance ?: synchronized(this) {
                instance ?: PlaylistManager(context.applicationContext).also { instance = it }
            }
        }

        fun resetForTesting(context: Context) {
            instance?.flushPendingWrites()
            instance = null
            context.filesDir.listFiles()?.filter {
                it.name.startsWith("user_playlists") || it.name.startsWith("liked_songs")
            }?.forEach { it.delete() }
        }
    }
}

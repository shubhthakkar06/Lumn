package com.example.musicapp.data.playlist

import android.content.Context
import com.example.musicapp.data.MusicCatalog
import com.example.musicapp.data.auth.UserProfile
import com.example.musicapp.data.network.SongInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class UserPlaylist(
    val id: String,
    val name: String,
    val creator: String = "Shubh Thakkar",
    val coverUrl: String = "",
    val songs: List<SongInfo> = emptyList(),
    val isPinned: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis(),
    val sourceUrl: String = "",
    val userId: String = ""
)

class PlaylistManager private constructor(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val userManager = com.example.musicapp.data.auth.UserManager.getInstance(context)
    private var activeUserId: String = userManager.currentUser.value.id

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

    private fun loadData(userId: String) {
        activeUserId = userId
        val userPlaylistsFile = getPlaylistsFile(userId)
        val userLikedSongsFile = getLikedSongsFile(userId)
        val otherUsers = userManager.allUsers.value.filter { it.id != userId }

        // 1. Liked Songs
        try {
            var loadedLiked = emptyList<SongInfo>()
            if (userLikedSongsFile.exists() && userLikedSongsFile.length() > 5) {
                val content = userLikedSongsFile.readText()
                loadedLiked = runCatching { json.decodeFromString<List<SongInfo>>(content) }.getOrDefault(emptyList())
            }

            // If empty or missing, search and recover from backups, google_*, guest_*, or legacy files
            if (loadedLiked.isEmpty()) {
                val recovered = recoverLikedSongsOnDevice(userId, otherUsers)
                if (recovered.isNotEmpty()) {
                    loadedLiked = recovered
                    userLikedSongsFile.writeText(json.encodeToString(loadedLiked))
                }
            }

            _likedSongs.value = loadedLiked
        } catch (e: Exception) {
            e.printStackTrace()
            _likedSongs.value = emptyList()
        }

        // 2. Playlists
        try {
            var loadedPlaylists = emptyList<UserPlaylist>()
            if (userPlaylistsFile.exists() && userPlaylistsFile.length() > 5) {
                val content = userPlaylistsFile.readText()
                val rawList = runCatching { json.decodeFromString<List<UserPlaylist>>(content) }.getOrDefault(emptyList())
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
                        userPlaylistsFile.writeText(json.encodeToString(loadedPlaylists))
                    }
                }
            }

            // If empty, recover from previous Google / Guest / legacy files on device (excluding other users' files)
            if (loadedPlaylists.isEmpty()) {
                val recovered = recoverPlaylistsOnDevice(userId, otherUsers)
                if (recovered.isNotEmpty()) {
                    loadedPlaylists = recovered.map { it.copy(userId = userId) }
                    userPlaylistsFile.writeText(json.encodeToString(loadedPlaylists))
                }
            }

            _playlists.value = loadedPlaylists
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

    private fun savePlaylists() {
        val targetFile = getPlaylistsFile(activeUserId)
        val current = _playlists.value
        try {
            val content = json.encodeToString(current)
            targetFile.writeText(content)
            if (current.isNotEmpty()) {
                File(context.filesDir, "user_playlists_${sanitizeUserId(activeUserId)}_backup.json").writeText(content)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveLikedSongs() {
        val targetFile = getLikedSongsFile(activeUserId)
        val current = _likedSongs.value
        try {
            val content = json.encodeToString(current)
            targetFile.writeText(content)
            if (current.isNotEmpty()) {
                File(context.filesDir, "liked_songs_${sanitizeUserId(activeUserId)}_backup.json").writeText(content)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isLiked(videoId: String): Boolean {
        return _likedSongs.value.any { it.videoId == videoId }
    }

    fun toggleLike(song: SongInfo): Boolean {
        val currentlyLiked = isLiked(song.videoId)
        val updated = if (currentlyLiked) {
            _likedSongs.value.filter { it.videoId != song.videoId }
        } else {
            listOf(song) + _likedSongs.value
        }
        _likedSongs.value = updated
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
        val id = "pl_" + System.currentTimeMillis()
        val newPlaylist = UserPlaylist(
            id = id,
            name = name.trim(),
            creator = resolvedCreator,
            coverUrl = resolvedCoverUrl,
            songs = songs,
            sourceUrl = sourceUrl,
            userId = activeUserId
        )
        _playlists.value = listOf(newPlaylist) + _playlists.value
        savePlaylists()
        return newPlaylist
    }

    fun addSongToPlaylist(playlistId: String, song: SongInfo) {
        if (com.example.musicapp.data.auth.UserManager.getInstance(context).currentUser.value.isGuest) return
        val updated = _playlists.value.map { pl ->
            if (pl.id == playlistId) {
                if (pl.songs.none { it.videoId == song.videoId }) {
                    pl.copy(songs = pl.songs + song)
                } else pl
            } else pl
        }
        _playlists.value = updated
        savePlaylists()
    }

    fun removeSongFromPlaylist(playlistId: String, videoId: String) {
        val updated = _playlists.value.map { pl ->
            if (pl.id == playlistId) {
                pl.copy(songs = pl.songs.filter { it.videoId != videoId })
            } else pl
        }
        _playlists.value = updated
        savePlaylists()
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        if (newName.isBlank()) return
        val updated = _playlists.value.map { pl ->
            if (pl.id == playlistId) pl.copy(name = newName.trim()) else pl
        }
        _playlists.value = updated
        savePlaylists()
    }

    fun moveSong(playlistId: String, fromIndex: Int, toIndex: Int) {
        val pl = _playlists.value.find { it.id == playlistId } ?: return
        val songs = pl.songs.toMutableList()
        if (fromIndex in songs.indices && toIndex in songs.indices && fromIndex != toIndex) {
            val item = songs.removeAt(fromIndex)
            songs.add(toIndex, item)
            val updated = _playlists.value.map {
                if (it.id == playlistId) it.copy(songs = songs) else it
            }
            _playlists.value = updated
            savePlaylists()
        }
    }

    fun deletePlaylist(playlistId: String) {
        _playlists.value = _playlists.value.filter { it.id != playlistId }
        savePlaylists()
    }

    fun getPlaylist(playlistId: String): UserPlaylist? {
        return _playlists.value.find { it.id == playlistId }
    }

    fun applyMergedPlaylists(merged: List<UserPlaylist>) {
        _playlists.value = merged
        savePlaylists()
    }

    fun applyMergedLikedSongs(merged: List<SongInfo>) {
        _likedSongs.value = merged
        saveLikedSongs()
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
            instance = null
            context.filesDir.listFiles()?.filter {
                it.name.startsWith("user_playlists") || it.name.startsWith("liked_songs")
            }?.forEach { it.delete() }
        }
    }
}

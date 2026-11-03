package com.example.musicapp.data.playlist

import android.content.Context
import com.example.musicapp.data.MusicCatalog
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

        val primaryAccountId = userManager.allUsers.value.firstOrNull { !it.isGuest }?.id.orEmpty()
        val currentProfile = userManager.allUsers.value.find { it.id == userId }
            ?: userManager.currentUser.value

        // 1. Liked Songs
        try {
            if (userLikedSongsFile.exists()) {
                val content = userLikedSongsFile.readText()
                val list = json.decodeFromString<List<SongInfo>>(content)
                val primaryLikedSongsFile = if (primaryAccountId.isNotBlank() && primaryAccountId != userId) {
                    getLikedSongsFile(primaryAccountId)
                } else null

                // If this non-primary account has an exact copy of the primary user's liked songs file
                // from an earlier buggy migration, isolate and clear it for the secondary account.
                if (primaryLikedSongsFile != null && primaryLikedSongsFile.exists() &&
                    primaryLikedSongsFile.readText() == content && list.isNotEmpty()) {
                    _likedSongs.value = emptyList()
                    userLikedSongsFile.writeText(json.encodeToString(emptyList<SongInfo>()))
                } else {
                    _likedSongs.value = list
                }
            } else if (!userId.startsWith("guest_") && legacyLikedSongsFile.exists()) {
                // Migrate legacy global liked songs for primary existing user
                val content = legacyLikedSongsFile.readText()
                val list = json.decodeFromString<List<SongInfo>>(content)
                _likedSongs.value = list
                userLikedSongsFile.writeText(content)
                legacyLikedSongsFile.delete()
            } else {
                _likedSongs.value = emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _likedSongs.value = emptyList()
        }

        // 2. Playlists
        try {
            if (userPlaylistsFile.exists()) {
                val content = userPlaylistsFile.readText()
                val rawList = json.decodeFromString<List<UserPlaylist>>(content)
                val sanitized = rawList.mapNotNull { pl ->
                    when {
                        // Explicitly owned by this user
                        pl.userId == userId -> pl
                        // Explicitly owned by another user -> filter out
                        pl.userId.isNotBlank() && pl.userId != userId -> null
                        // Legacy unassigned playlists without userId:
                        // If this is the primary account, assign ownership to this user
                        primaryAccountId.isNotBlank() && userId == primaryAccountId -> pl.copy(userId = userId)
                        // If creator matches this profile's name/username, assign ownership
                        currentProfile.name.isNotBlank() && pl.creator.equals(currentProfile.name, ignoreCase = true) -> pl.copy(userId = userId)
                        currentProfile.username.isNotBlank() && pl.creator.equals(currentProfile.username, ignoreCase = true) -> pl.copy(userId = userId)
                        // Otherwise it belongs to another account on this device
                        else -> null
                    }
                }
                _playlists.value = sanitized
                if (sanitized != rawList) {
                    userPlaylistsFile.writeText(json.encodeToString(sanitized))
                }
            } else if (!userId.startsWith("guest_") && legacyPlaylistsFile.exists()) {
                val content = legacyPlaylistsFile.readText()
                val rawList = json.decodeFromString<List<UserPlaylist>>(content)
                val assignedList = rawList.map { it.copy(userId = userId) }
                _playlists.value = assignedList
                userPlaylistsFile.writeText(json.encodeToString(assignedList))
                legacyPlaylistsFile.delete()
            } else {
                _playlists.value = emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _playlists.value = emptyList()
        }
    }

    private fun savePlaylists() {
        val targetFile = getPlaylistsFile(activeUserId)
        val current = _playlists.value
        try {
            targetFile.writeText(json.encodeToString(current))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveLikedSongs() {
        val targetFile = getLikedSongsFile(activeUserId)
        val current = _likedSongs.value
        try {
            targetFile.writeText(json.encodeToString(current))
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
        val isGuest = if (activeUserId.startsWith("user_") || activeUserId.startsWith("google_")) {
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

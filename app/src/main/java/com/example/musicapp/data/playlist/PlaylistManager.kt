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
    val sourceUrl: String = ""
)

class PlaylistManager private constructor(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val playlistsFile = File(context.filesDir, "user_playlists.json")
    private val likedSongsFile = File(context.filesDir, "liked_songs.json")

    private val _playlists = MutableStateFlow<List<UserPlaylist>>(emptyList())
    val playlists: StateFlow<List<UserPlaylist>> = _playlists.asStateFlow()

    private val _likedSongs = MutableStateFlow<List<SongInfo>>(emptyList())
    val likedSongs: StateFlow<List<SongInfo>> = _likedSongs.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        // 1. Liked Songs
        try {
            if (likedSongsFile.exists()) {
                val content = likedSongsFile.readText()
                val list = json.decodeFromString<List<SongInfo>>(content)
                _likedSongs.value = list
            } else {
                _likedSongs.value = emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _likedSongs.value = emptyList()
        }

        // 2. Playlists
        try {
            if (playlistsFile.exists()) {
                val content = playlistsFile.readText()
                val list = json.decodeFromString<List<UserPlaylist>>(content)
                _playlists.value = list
            } else {
                _playlists.value = emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _playlists.value = emptyList()
        }
    }

    private fun savePlaylists() {
        scope.launch {
            try {
                playlistsFile.writeText(json.encodeToString(_playlists.value))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveLikedSongs() {
        scope.launch {
            try {
                likedSongsFile.writeText(json.encodeToString(_likedSongs.value))
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
        creator: String = "Shubh Thakkar",
        coverUrl: String = "",
        songs: List<SongInfo> = emptyList(),
        sourceUrl: String = ""
    ): UserPlaylist? {
        if (com.example.musicapp.data.auth.UserManager.getInstance(context).currentUser.value.isGuest) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, "Sign in to create playlists", android.widget.Toast.LENGTH_SHORT).show()
            }
            return null
        }
        val id = "pl_" + System.currentTimeMillis()
        val newPlaylist = UserPlaylist(
            id = id,
            name = name.trim(),
            creator = creator,
            coverUrl = coverUrl,
            songs = songs,
            sourceUrl = sourceUrl
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
    }
}

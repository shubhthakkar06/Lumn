package com.example.musicapp.desktop.data

import com.example.musicapp.desktop.model.DesktopPlaylist
import com.example.musicapp.desktop.model.DesktopSong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class DesktopLibraryManager {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val dataDir = File(System.getProperty("user.home"), ".rivara").apply { mkdirs() }
    private val playlistsFile = File(dataDir, "playlists.json")
    private val likedSongsFile = File(dataDir, "liked_songs.json")
    private val playlistSongsFile = File(dataDir, "playlist_songs.json")

    private val _playlists = MutableStateFlow<List<DesktopPlaylist>>(emptyList())
    val playlists: StateFlow<List<DesktopPlaylist>> = _playlists.asStateFlow()

    private val _playlistSongs = MutableStateFlow<Map<String, List<DesktopSong>>>(emptyMap())
    val playlistSongs: StateFlow<Map<String, List<DesktopSong>>> = _playlistSongs.asStateFlow()

    private val _likedSongs = MutableStateFlow<List<DesktopSong>>(emptyList())
    val likedSongs: StateFlow<List<DesktopSong>> = _likedSongs.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        try {
            if (playlistsFile.exists()) {
                val list = json.decodeFromString<List<DesktopPlaylist>>(playlistsFile.readText())
                _playlists.value = list
            } else {
                val defaults = listOf(
                    DesktopPlaylist("pl_1", "Chill Mix", songCount = 0),
                    DesktopPlaylist("pl_2", "Workout Beats", songCount = 0),
                    DesktopPlaylist("pl_3", "Satsang & Katha", songCount = 1)
                )
                _playlists.value = defaults
                savePlaylists()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            if (likedSongsFile.exists()) {
                val list = json.decodeFromString<List<DesktopSong>>(likedSongsFile.readText())
                _likedSongs.value = list
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            if (playlistSongsFile.exists()) {
                val map = json.decodeFromString<Map<String, List<DesktopSong>>>(playlistSongsFile.readText())
                _playlistSongs.value = map
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isLiked(songId: String): Boolean {
        return _likedSongs.value.any { it.id == songId }
    }

    fun toggleLike(song: DesktopSong): Boolean {
        val current = _likedSongs.value
        val alreadyLiked = current.any { it.id == song.id }
        val updated = if (alreadyLiked) {
            current.filter { it.id != song.id }
        } else {
            listOf(song.copy(isLiked = true)) + current
        }
        _likedSongs.value = updated
        saveLikedSongs()
        return !alreadyLiked
    }

    fun createPlaylist(name: String): DesktopPlaylist {
        val clean = name.trim().ifBlank { "New Playlist" }
        val id = "pl_" + System.currentTimeMillis()
        val playlist = DesktopPlaylist(id, clean, songCount = 0)
        val updated = _playlists.value + playlist
        _playlists.value = updated
        savePlaylists()
        return playlist
    }

    fun addSongToPlaylist(playlistId: String, song: DesktopSong) {
        val currentMap = _playlistSongs.value.toMutableMap()
        val existing = currentMap[playlistId].orEmpty()
        if (existing.none { it.id == song.id }) {
            currentMap[playlistId] = existing + song
            _playlistSongs.value = currentMap
            _playlists.value = _playlists.value.map {
                if (it.id == playlistId) it.copy(songCount = currentMap[playlistId]?.size ?: 0) else it
            }
            savePlaylists()
            savePlaylistSongs()
        }
    }

    fun removeSongFromPlaylist(playlistId: String, songId: String) {
        val currentMap = _playlistSongs.value.toMutableMap()
        val existing = currentMap[playlistId].orEmpty()
        val filtered = existing.filter { it.id != songId }
        currentMap[playlistId] = filtered
        _playlistSongs.value = currentMap
        _playlists.value = _playlists.value.map {
            if (it.id == playlistId) it.copy(songCount = filtered.size) else it
        }
        savePlaylists()
        savePlaylistSongs()
    }

    fun getSongsForPlaylist(playlistId: String): List<DesktopSong> {
        return _playlistSongs.value[playlistId].orEmpty()
    }

    private fun savePlaylists() {
        try {
            playlistsFile.writeText(json.encodeToString(_playlists.value))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveLikedSongs() {
        try {
            likedSongsFile.writeText(json.encodeToString(_likedSongs.value))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun savePlaylistSongs() {
        try {
            playlistSongsFile.writeText(json.encodeToString(_playlistSongs.value))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

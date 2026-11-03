package com.example.musicapp.desktop.data

import com.example.musicapp.desktop.model.DesktopPlaylist
import com.example.musicapp.desktop.model.DesktopSong
import com.example.musicapp.desktop.sync.RivaraSyncPayload
import com.example.musicapp.desktop.sync.SyncPlaylist
import com.example.musicapp.desktop.sync.SyncSong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class DesktopLibraryManager {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val dataDir = File(System.getProperty("user.home"), ".rivara").apply { mkdirs() }

    private var activeUserId: String = "guest_default"

    private val _playlists = MutableStateFlow<List<DesktopPlaylist>>(emptyList())
    val playlists: StateFlow<List<DesktopPlaylist>> = _playlists.asStateFlow()

    private val _playlistSongs = MutableStateFlow<Map<String, List<DesktopSong>>>(emptyMap())
    val playlistSongs: StateFlow<Map<String, List<DesktopSong>>> = _playlistSongs.asStateFlow()

    private val _likedSongs = MutableStateFlow<List<DesktopSong>>(emptyList())
    val likedSongs: StateFlow<List<DesktopSong>> = _likedSongs.asStateFlow()

    init {
        loadDataForUser(activeUserId)
    }

    fun switchUser(user: DesktopUser?) {
        val newUserId = user?.id ?: "guest_default"
        if (newUserId == activeUserId) return

        // Save current user state before switching
        saveDataForUser(activeUserId)

        // Load new user's isolated library
        activeUserId = newUserId
        loadDataForUser(activeUserId)
    }

    private fun sanitize(userId: String): String {
        return userId.replace(Regex("[^a-zA-Z0-9_]"), "_")
    }

    private fun getPlaylistsFile(userId: String) = File(dataDir, "user_playlists_${sanitize(userId)}.json")
    private fun getLikedSongsFile(userId: String) = File(dataDir, "user_liked_${sanitize(userId)}.json")
    private fun getPlaylistSongsFile(userId: String) = File(dataDir, "user_playlist_songs_${sanitize(userId)}.json")

    private fun loadDataForUser(userId: String) {
        val plFile = getPlaylistsFile(userId)
        val likedFile = getLikedSongsFile(userId)
        val plSongsFile = getPlaylistSongsFile(userId)

        try {
            if (plFile.exists()) {
                val list = json.decodeFromString<List<DesktopPlaylist>>(plFile.readText())
                _playlists.value = list
            } else {
                // If legacy playlists.json exists and this is the primary user, migrate
                val legacyPl = File(dataDir, "playlists.json")
                if (legacyPl.exists() && !userId.startsWith("guest")) {
                    val list = json.decodeFromString<List<DesktopPlaylist>>(legacyPl.readText())
                    _playlists.value = list
                    savePlaylists(userId)
                } else {
                    val defaults = if (userId.startsWith("guest")) {
                        listOf(DesktopPlaylist("pl_guest_1", "Guest Chill Mix", songCount = 0))
                    } else {
                        listOf(
                            DesktopPlaylist("pl_1", "Chill Mix", songCount = 0),
                            DesktopPlaylist("pl_2", "Workout Beats", songCount = 0),
                            DesktopPlaylist("pl_3", "Satsang & Katha", songCount = 1)
                        )
                    }
                    _playlists.value = defaults
                    savePlaylists(userId)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _playlists.value = emptyList()
        }

        try {
            if (likedFile.exists()) {
                val list = json.decodeFromString<List<DesktopSong>>(likedFile.readText())
                _likedSongs.value = list
            } else {
                val defaultLiked = listOf(
                    DesktopSong(
                        id = "baps_ep_1",
                        title = "Patience and True Refuge in Bhagwan | Pujya Somprakashdas Swami | BAPS Satsang",
                        artist = "Pujya Somprakashdas Swami",
                        album = "Episode • BAPS Satsang Gujarati",
                        durationSeconds = 2837L,
                        albumArtUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts211/v4/00/72/c6/0072c641-891b-71ff-8c1c-3e6f20be1f02/mza_13154730571231814752.jpg/600x600bb.jpg",
                        audioUrl = "https://p.scdn.co/mp3-preview/fa88a13eafcd9aa77ca98e20ceb435c786e229c6.mp3",
                        isLiked = true,
                        isEpisode = true,
                        description = "આપણું જીવન સુખ અને દુઃખ બંનેથી ભરેલું છે. જ્યારે મુશ્કેલીઓ આવે છે, ત્યારે આપણે ઘણીવાર નિરાશ થઈ જઈએ છીએ અથવા ખોટી જગ્યાએ ઉકેલ શોધીએ છીએ. પૂજ્ય સોમપ્રકાશદાસ સ્વામી આ કથામાં સમજાવે છે કે સુખ અને દુઃખ એ...",
                        releaseDate = "Oct 12"
                    ),
                    DesktopSong(
                        id = "yellow_coldplay",
                        title = "Yellow",
                        artist = "Coldplay",
                        album = "Parachutes",
                        durationSeconds = 269L,
                        albumArtUrl = "https://c.saavncdn.com/712/Parachutes-English-2000-20201020084321-500x500.jpg",
                        audioUrl = "https://aac.saavncdn.com/712/f2a4f4d2fb2d35889fa6e9f1a260f8ea_320.mp4",
                        isLiked = true
                    ),
                    DesktopSong(
                        id = "tum_hi_ho",
                        title = "Tum Hi Ho",
                        artist = "Arijit Singh",
                        album = "Aashiqui 2",
                        durationSeconds = 262L,
                        albumArtUrl = "https://c.saavncdn.com/026/Aashiqui-2-Hindi-2013-500x500.jpg",
                        audioUrl = "https://aac.saavncdn.com/026/02dfef8e359a35e38d6df72aebf8ad22_320.mp4",
                        isLiked = true
                    )
                )
                _likedSongs.value = defaultLiked
                saveLikedSongs(userId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _likedSongs.value = emptyList()
        }

        try {
            val map = if (plSongsFile.exists()) {
                json.decodeFromString<Map<String, List<DesktopSong>>>(plSongsFile.readText()).toMutableMap()
            } else mutableMapOf()

            // Ensure default playlists have their songs populated
            if (map["pl_1"].isNullOrEmpty() && _playlists.value.any { it.id == "pl_1" }) {
                map["pl_1"] = listOf(
                    DesktopSong("tum_hi_ho", "Tum Hi Ho", "Arijit Singh", "Aashiqui 2", 262L, "https://c.saavncdn.com/026/Aashiqui-2-Hindi-2013-500x500.jpg", "https://aac.saavncdn.com/026/02dfef8e359a35e38d6df72aebf8ad22_320.mp4"),
                    DesktopSong("yellow_coldplay", "Yellow", "Coldplay", "Parachutes", 269L, "https://c.saavncdn.com/712/Parachutes-English-2000-20201020084321-500x500.jpg", "https://aac.saavncdn.com/712/f2a4f4d2fb2d35889fa6e9f1a260f8ea_320.mp4")
                )
            }
            if (map["pl_2"].isNullOrEmpty() && _playlists.value.any { it.id == "pl_2" }) {
                map["pl_2"] = listOf(
                    DesktopSong("starboy_weeknd", "Starboy", "The Weeknd, Daft Punk", "Starboy", 230L, "https://c.saavncdn.com/408/Starboy-English-2016-500x500.jpg", "https://aac.saavncdn.com/408/8d7cae0b969c3a387ee3b6a73c914eb4_320.mp4")
                )
            }
            if (map["pl_3"].isNullOrEmpty() && _playlists.value.any { it.id == "pl_3" }) {
                map["pl_3"] = listOf(
                    DesktopSong("baps_ep_1", "Patience and True Refuge in Bhagwan | Pujya Somprakashdas Swami | BAPS Satsang", "Pujya Somprakashdas Swami", "Episode • BAPS Satsang Gujarati", 2837L, "https://is1-ssl.mzstatic.com/image/thumb/Podcasts211/v4/00/72/c6/0072c641-891b-71ff-8c1c-3e6f20be1f02/mza_13154730571231814752.jpg/600x600bb.jpg", "https://p.scdn.co/mp3-preview/fa88a13eafcd9aa77ca98e20ceb435c786e229c6.mp3")
                )
            }

            _playlistSongs.value = map
            if (!plSongsFile.exists() && map.isNotEmpty()) {
                savePlaylistSongs(userId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _playlistSongs.value = emptyMap()
        }
    }

    private fun saveDataForUser(userId: String) {
        savePlaylists(userId)
        saveLikedSongs(userId)
        savePlaylistSongs(userId)
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
        saveLikedSongs(activeUserId)
        return !alreadyLiked
    }

    fun createPlaylist(name: String, coverUrl: String = ""): DesktopPlaylist {
        val clean = name.trim().ifBlank { "New Playlist" }
        val id = "pl_" + System.currentTimeMillis()
        val playlist = DesktopPlaylist(id, clean, songCount = 0, coverUrl = coverUrl)
        val updated = _playlists.value + playlist
        _playlists.value = updated
        savePlaylists(activeUserId)
        return playlist
    }

    fun getSyncPayload(user: DesktopUser?): RivaraSyncPayload {
        val uId = user?.id ?: activeUserId
        val email = user?.email.orEmpty().ifBlank { "navya.shubh2006@gmail.com" }
        val syncPlaylists = _playlists.value.map { pl ->
            val songs = getSongsForPlaylist(pl.id).map { s ->
                SyncSong(
                    id = s.id,
                    title = s.title,
                    artist = s.artist,
                    album = s.album,
                    durationSeconds = s.durationSeconds,
                    albumArtUrl = s.albumArtUrl,
                    audioUrl = s.audioUrl
                )
            }
            SyncPlaylist(
                id = pl.id,
                name = pl.name,
                creator = pl.creator,
                coverUrl = pl.coverUrl.ifBlank { songs.firstOrNull { it.albumArtUrl.isNotBlank() }?.albumArtUrl.orEmpty() },
                songs = songs
            )
        }
        val syncLiked = _likedSongs.value.map { s ->
            SyncSong(
                id = s.id,
                title = s.title,
                artist = s.artist,
                album = s.album,
                durationSeconds = s.durationSeconds,
                albumArtUrl = s.albumArtUrl,
                audioUrl = s.audioUrl
            )
        }
        return RivaraSyncPayload(
            userEmail = email,
            userId = uId,
            playlists = syncPlaylists,
            likedSongs = syncLiked
        )
    }

    fun mergeFromSync(payload: RivaraSyncPayload) {
        val incomingPlaylists = payload.playlists
        val incomingLiked = payload.likedSongs

        val currentPls = _playlists.value.toMutableList()
        val currentSongsMap = _playlistSongs.value.toMutableMap()

        for (inc in incomingPlaylists) {
            val existingIdx = currentPls.indexOfFirst {
                it.id == inc.id || it.name.trim().equals(inc.name.trim(), ignoreCase = true)
            }
            val incSongs = inc.songs.map { s ->
                DesktopSong(
                    id = s.id,
                    title = s.title,
                    artist = s.artist,
                    album = s.album,
                    durationSeconds = s.durationSeconds,
                    albumArtUrl = s.albumArtUrl,
                    audioUrl = s.audioUrl,
                    isLiked = isLiked(s.id)
                )
            }

            if (existingIdx >= 0) {
                val existing = currentPls[existingIdx]
                val existingSongs = currentSongsMap[existing.id].orEmpty()
                val mergedSongs = (existingSongs + incSongs).distinctBy {
                    it.id.ifBlank { "${it.title}_${it.artist}".lowercase() }
                }
                currentSongsMap[existing.id] = mergedSongs
                val updatedCover = existing.coverUrl.ifBlank { inc.coverUrl }.ifBlank {
                    mergedSongs.firstOrNull { it.albumArtUrl.isNotBlank() }?.albumArtUrl.orEmpty()
                }
                currentPls[existingIdx] = existing.copy(
                    songCount = mergedSongs.size,
                    coverUrl = updatedCover
                )
            } else {
                val plId = inc.id.ifBlank { "pl_" + System.currentTimeMillis() }
                val cover = inc.coverUrl.ifBlank {
                    incSongs.firstOrNull { it.albumArtUrl.isNotBlank() }?.albumArtUrl.orEmpty()
                }
                currentPls.add(
                    DesktopPlaylist(
                        id = plId,
                        name = inc.name,
                        creator = inc.creator.ifBlank { payload.userEmail.substringBefore("@") },
                        songCount = incSongs.size,
                        coverUrl = cover
                    )
                )
                currentSongsMap[plId] = incSongs
            }
        }

        // Merge liked songs
        val currentLiked = _likedSongs.value.toMutableList()
        for (l in incomingLiked) {
            if (currentLiked.none { it.id == l.id || (it.title.equals(l.title, true) && it.artist.equals(l.artist, true)) }) {
                currentLiked.add(
                    DesktopSong(
                        id = l.id,
                        title = l.title,
                        artist = l.artist,
                        album = l.album,
                        durationSeconds = l.durationSeconds,
                        albumArtUrl = l.albumArtUrl,
                        audioUrl = l.audioUrl,
                        isLiked = true
                    )
                )
            }
        }

        _playlists.value = currentPls
        _playlistSongs.value = currentSongsMap
        _likedSongs.value = currentLiked

        saveDataForUser(activeUserId)
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
            savePlaylists(activeUserId)
            savePlaylistSongs(activeUserId)
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
        savePlaylists(activeUserId)
        savePlaylistSongs(activeUserId)
    }

    fun getSongsForPlaylist(playlistId: String): List<DesktopSong> {
        return _playlistSongs.value[playlistId].orEmpty()
    }

    private fun savePlaylists(userId: String) {
        try {
            getPlaylistsFile(userId).writeText(json.encodeToString(_playlists.value))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveLikedSongs(userId: String) {
        try {
            getLikedSongsFile(userId).writeText(json.encodeToString(_likedSongs.value))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun savePlaylistSongs(userId: String) {
        try {
            getPlaylistSongsFile(userId).writeText(json.encodeToString(_playlistSongs.value))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

package com.example.musicapp.data.playlist

import android.content.Context
import com.example.musicapp.data.network.SongInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PlaylistImportState {
    object Idle : PlaylistImportState
    data class Running(val completed: Int, val total: Int) : PlaylistImportState
    data class Done(
        val playlist: UserPlaylist,
        val importedCount: Int,
        val expectedCount: Int
    ) : PlaylistImportState
    data class Failed(val message: String) : PlaylistImportState
}

/**
 * Runs playlist imports in an app-wide scope, so leaving the Library screen, closing the
 * dialog or rotating the phone no longer cancels an import halfway through.
 */
object PlaylistImportManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<PlaylistImportState>(PlaylistImportState.Idle)
    val state: StateFlow<PlaylistImportState> = _state.asStateFlow()

    @Volatile private var job: Job? = null

    val isRunning: Boolean get() = job?.isActive == true

    fun start(context: Context, input: String, creatorName: String) {
        if (isRunning) return
        val appContext = context.applicationContext
        _state.value = PlaylistImportState.Running(0, 0)
        job = scope.launch {
            _state.value = try {
                val imported = SpotifyPlaylistImporter.import(appContext, input) { completed, total ->
                    _state.value = PlaylistImportState.Running(completed, total)
                }
                val songs = toSongs(imported)
                val playlist = PlaylistManager.getInstance(appContext).createPlaylist(
                    name = imported.name,
                    creator = creatorName,
                    coverUrl = imported.coverUrl.ifBlank {
                        imported.tracks.firstOrNull { it.albumArtUrl.isNotBlank() }?.albumArtUrl.orEmpty()
                    },
                    songs = songs,
                    sourceUrl = imported.spotifyUrl
                )
                if (playlist == null) {
                    PlaylistImportState.Failed("Sign in to import playlists.")
                } else {
                    notifyDone(appContext, playlist.name, songs.size, imported.expectedTrackCount)
                    PlaylistImportState.Done(playlist, songs.size, imported.expectedTrackCount)
                }
            } catch (e: CancellationException) {
                PlaylistImportState.Idle
            } catch (e: Exception) {
                PlaylistImportState.Failed(e.message ?: "Could not import this playlist.")
            }
        }
    }

    /** Call after the UI has shown a Done/Failed result. */
    fun acknowledge() {
        if (!isRunning) _state.value = PlaylistImportState.Idle
    }

    internal fun toSongs(imported: SpotifyImportedPlaylist): List<SongInfo> {
        val fallbackCover = imported.coverUrl.ifBlank {
            imported.tracks.firstOrNull { it.albumArtUrl.isNotBlank() }?.albumArtUrl.orEmpty()
        }
        return imported.tracks.map { track ->
            val match = track.resolvedSong
            val stableId = track.id.replace(Regex("[^A-Za-z0-9_-]"), "_")
            SongInfo(
                // Keep a confident catalog match's own ID so it resolves through its provider;
                // otherwise use a stable Spotify-based ID and match by title/artist at play time.
                videoId = match?.videoId
                    ?: if (track.id.length == 11 && !track.id.contains("_")) track.id else "spotify_$stableId",
                title = match?.title ?: track.title,
                artist = match?.artist ?: track.artists,
                albumArtUrl = track.albumArtUrl.ifBlank { match?.albumArtUrl.orEmpty().ifBlank { fallbackCover } },
                audioStreamUrl = null,
                albumName = match?.albumName?.ifBlank { track.albumName } ?: track.albumName,
                durationSeconds = match?.durationSeconds?.takeIf { it > 0L } ?: track.durationSeconds,
                // null = not checked yet (matched on play); false = searched and nothing matched.
                importMatchFound = when {
                    match != null -> true
                    track.matchAttempted -> false
                    else -> null
                }
            )
        }
    }

    private fun notifyDone(context: Context, name: String, imported: Int, expected: Int) {
        val message = if (expected > imported) {
            "Imported $imported of $expected tracks into \"$name\". Spotify only shared part of this playlist. Try importing it again later for the rest."
        } else {
            "Imported $imported tracks into \"$name\""
        }
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }
}

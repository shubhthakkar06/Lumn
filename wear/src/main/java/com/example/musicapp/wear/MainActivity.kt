package com.example.musicapp.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.example.musicapp.wear.audio.WearAudioPlayer
import com.example.musicapp.wear.model.WearPlaybackState
import com.example.musicapp.wear.model.defaultWearMusicCatalog
import com.example.musicapp.wear.presentation.*
import com.example.musicapp.wear.theme.RivaraWearTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            RivaraWearTheme {
                val navController = rememberSwipeDismissableNavController()
                val audioPlayer = remember { WearAudioPlayer(applicationContext) }

                DisposableEffect(Unit) {
                    onDispose {
                        audioPlayer.release()
                    }
                }

                val isPlaying by audioPlayer.isPlaying.collectAsState()
                val progressMs by audioPlayer.progressMs.collectAsState()
                val durationMs by audioPlayer.durationMs.collectAsState()
                val currentSong by audioPlayer.currentSong.collectAsState()
                val volume by audioPlayer.volume.collectAsState()

                var playbackState by remember {
                    mutableStateOf(
                        WearPlaybackState(
                            title = defaultWearMusicCatalog[0].title,
                            artist = defaultWearMusicCatalog[0].artist,
                            albumArtUrl = defaultWearMusicCatalog[0].albumArtUrl,
                            durationMs = defaultWearMusicCatalog[0].durationSeconds * 1000L,
                            queue = defaultWearMusicCatalog
                        )
                    )
                }

                val currentDisplayState = playbackState.copy(
                    title = currentSong?.title ?: playbackState.title,
                    artist = currentSong?.artist ?: playbackState.artist,
                    albumArtUrl = currentSong?.albumArtUrl ?: playbackState.albumArtUrl,
                    isPlaying = isPlaying,
                    progressMs = progressMs,
                    durationMs = if (durationMs > 0) durationMs else playbackState.durationMs,
                    volume = volume
                )

                SwipeDismissableNavHost(
                    navController = navController,
                    startDestination = "now_playing"
                ) {
                    composable("now_playing") {
                        NowPlayingScreen(
                            state = currentDisplayState,
                            onPlayPauseToggle = {
                                if (currentSong == null) {
                                    val songToPlay = playbackState.queue.firstOrNull() ?: defaultWearMusicCatalog[0]
                                    audioPlayer.play(songToPlay)
                                } else {
                                    audioPlayer.togglePlayPause()
                                }
                            },
                            onSkipNext = {
                                val currentIdx = playbackState.queue.indexOfFirst {
                                    it.title.equals(currentDisplayState.title, ignoreCase = true)
                                }
                                val nextIdx = if (currentIdx in playbackState.queue.indices) {
                                    (currentIdx + 1) % playbackState.queue.size
                                } else 0
                                val nextSong = playbackState.queue[nextIdx]
                                audioPlayer.play(nextSong)
                            },
                            onSkipPrev = {
                                val currentIdx = playbackState.queue.indexOfFirst {
                                    it.title.equals(currentDisplayState.title, ignoreCase = true)
                                }
                                val prevIdx = if (currentIdx in playbackState.queue.indices) {
                                    (currentIdx - 1 + playbackState.queue.size) % playbackState.queue.size
                                } else (playbackState.queue.size - 1)
                                val prevSong = playbackState.queue[prevIdx]
                                audioPlayer.play(prevSong)
                            },
                            onOpenSearch = { navController.navigate("search") },
                            onOpenPlaylists = { navController.navigate("playlists") },
                            onOpenAccount = { navController.navigate("account") },
                            onOpenVolume = { navController.navigate("volume") },
                            onOpenQueue = { navController.navigate("queue") },
                            onVolumeAdjust = { newVol ->
                                audioPlayer.setVolume(newVol)
                            }
                        )
                    }

                    composable("search") {
                        WearSearchScreen(
                            onSongSelected = { song ->
                                audioPlayer.play(song)
                                val updatedQueue = if (playbackState.queue.any { it.id == song.id }) {
                                    playbackState.queue
                                } else {
                                    listOf(song) + playbackState.queue
                                }
                                playbackState = playbackState.copy(queue = updatedQueue)
                                navController.popBackStack()
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("playlists") {
                        WearPlaylistsScreen(
                            onSongSelected = { song, playlistTracks ->
                                audioPlayer.play(song)
                                playbackState = playbackState.copy(queue = playlistTracks)
                                navController.popBackStack("now_playing", false)
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("account") {
                        WearAccountScreen(
                            currentName = playbackState.userProfileName,
                            currentEmail = playbackState.userProfileEmail,
                            onProfileSelected = { newName, newEmail ->
                                playbackState = playbackState.copy(
                                    userProfileName = newName,
                                    userProfileEmail = newEmail
                                )
                            },
                            onSyncTriggered = {
                                playbackState = playbackState.copy(
                                    isPhoneConnected = true,
                                    queue = defaultWearMusicCatalog
                                )
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("queue") {
                        QueueScreen(
                            queue = playbackState.queue,
                            currentTitle = currentDisplayState.title,
                            onSongSelected = { song ->
                                audioPlayer.play(song)
                                navController.popBackStack()
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("volume") {
                        VolumeScreen(
                            currentVolume = currentDisplayState.volume,
                            onVolumeChanged = { newVol ->
                                audioPlayer.setVolume(newVol)
                            }
                        )
                    }
                }
            }
        }
    }
}

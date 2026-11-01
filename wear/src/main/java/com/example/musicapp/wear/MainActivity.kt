package com.example.musicapp.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.example.musicapp.wear.model.WearPlaybackState
import com.example.musicapp.wear.presentation.NowPlayingScreen
import com.example.musicapp.wear.presentation.QueueScreen
import com.example.musicapp.wear.presentation.VolumeScreen
import com.example.musicapp.wear.theme.RivaraWearTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            RivaraWearTheme {
                val navController = rememberSwipeDismissableNavController()
                var playbackState by remember { mutableStateOf(WearPlaybackState()) }

                SwipeDismissableNavHost(
                    navController = navController,
                    startDestination = "now_playing"
                ) {
                    composable("now_playing") {
                        NowPlayingScreen(
                            state = playbackState,
                            onPlayPauseToggle = {
                                playbackState = playbackState.copy(isPlaying = !playbackState.isPlaying)
                            },
                            onSkipNext = {
                                val nextIndex = (playbackState.queue.indexOfFirst { it.title == playbackState.title } + 1)
                                    .takeIf { it in playbackState.queue.indices } ?: 0
                                val nextSong = playbackState.queue[nextIndex]
                                playbackState = playbackState.copy(
                                    title = nextSong.title,
                                    artist = nextSong.artist,
                                    progressMs = 0L,
                                    isPlaying = true
                                )
                            },
                            onSkipPrev = {
                                val prevIndex = (playbackState.queue.indexOfFirst { it.title == playbackState.title } - 1)
                                    .takeIf { it in playbackState.queue.indices } ?: (playbackState.queue.size - 1)
                                val prevSong = playbackState.queue[prevIndex]
                                playbackState = playbackState.copy(
                                    title = prevSong.title,
                                    artist = prevSong.artist,
                                    progressMs = 0L,
                                    isPlaying = true
                                )
                            },
                            onOpenQueue = { navController.navigate("queue") },
                            onOpenVolume = { navController.navigate("volume") },
                            onVolumeAdjust = { newVol ->
                                playbackState = playbackState.copy(volume = newVol)
                            }
                        )
                    }

                    composable("queue") {
                        QueueScreen(
                            queue = playbackState.queue,
                            currentTitle = playbackState.title,
                            onSongSelected = { song ->
                                playbackState = playbackState.copy(
                                    title = song.title,
                                    artist = song.artist,
                                    progressMs = 0L,
                                    isPlaying = true
                                )
                                navController.popBackStack()
                            }
                        )
                    }

                    composable("volume") {
                        VolumeScreen(
                            currentVolume = playbackState.volume,
                            onVolumeChanged = { newVol ->
                                playbackState = playbackState.copy(volume = newVol)
                            }
                        )
                    }
                }
            }
        }
    }
}

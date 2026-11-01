package com.example.musicapp.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.musicapp.desktop.audio.DesktopAudioPlayer
import com.example.musicapp.desktop.data.DesktopLibraryManager
import com.example.musicapp.desktop.data.DesktopMusicRepository
import com.example.musicapp.desktop.data.SyncedLyricLine
import com.example.musicapp.desktop.mac.MacTrayIntegration
import com.example.musicapp.desktop.ui.DesktopLyricsPanel
import com.example.musicapp.desktop.ui.DesktopMainContent
import com.example.musicapp.desktop.ui.DesktopPlayerBar
import com.example.musicapp.desktop.ui.DesktopSidebar
import kotlinx.coroutines.launch

fun main() = application {
    val windowState = rememberWindowState(width = 1240.dp, height = 840.dp)
    var isWindowVisible by remember { mutableStateOf(true) }

    val musicRepository = remember { DesktopMusicRepository() }
    val libraryManager = remember { DesktopLibraryManager() }
    val audioPlayer = remember { DesktopAudioPlayer(musicRepository) }

    val coroutineScope = rememberCoroutineScope()
    var currentLyrics by remember { mutableStateOf<List<SyncedLyricLine>>(emptyList()) }

    val currentSong by audioPlayer.currentSong.collectAsState()
    val isPlaying by audioPlayer.isPlaying.collectAsState()
    val progressMs by audioPlayer.progressMs.collectAsState()
    val volume by audioPlayer.volume.collectAsState()
    val queue by audioPlayer.queue.collectAsState()
    val playlists by libraryManager.playlists.collectAsState()

    var selectedTab by remember { mutableStateOf("Home") }
    var isLyricsOpen by remember { mutableStateOf(false) }
    var isQueueOpen by remember { mutableStateOf(false) }

    // Fetch live lyrics whenever the current track changes
    LaunchedEffect(currentSong) {
        val song = currentSong
        if (song != null) {
            coroutineScope.launch {
                currentLyrics = musicRepository.fetchLyrics(song.title, song.artist)
            }
        } else {
            currentLyrics = emptyList()
        }
    }

    // Setup macOS Menu Bar Tray
    LaunchedEffect(Unit) {
        MacTrayIntegration.setupTray(
            onPlayPause = { audioPlayer.togglePlayPause() },
            onNext = { audioPlayer.skipNext() },
            onPrev = { audioPlayer.skipPrev() },
            onShowWindow = { isWindowVisible = true },
            onQuit = { exitApplication() }
        )
    }

    // Update macOS tray tooltip whenever track or playing state changes
    LaunchedEffect(currentSong, isPlaying) {
        currentSong?.let {
            MacTrayIntegration.updateNowPlaying(it.title, it.artist, isPlaying)
        }
    }

    if (isWindowVisible) {
        Window(
            onCloseRequest = { isWindowVisible = false }, // Close to macOS menu bar tray
            title = "Rivara",
            state = windowState,
            onKeyEvent = { keyEvent ->
                // Spacebar toggles play / pause
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Spacebar) {
                    audioPlayer.togglePlayPause()
                    true
                } else false
            }
        ) {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF1ED760),
                    background = Color(0xFF121212),
                    surface = Color(0xFF181818)
                )
            ) {
                Column(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
                    // Top Area: Sidebar + Main Content + Lyrics/Queue Side Panel
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        DesktopSidebar(
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it },
                            playlists = playlists,
                            onCreatePlaylist = { name ->
                                libraryManager.createPlaylist(name)
                            }
                        )

                        DesktopMainContent(
                            selectedTab = selectedTab,
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            playlists = playlists,
                            libraryManager = libraryManager,
                            musicRepository = musicRepository,
                            onPlaySong = { song ->
                                audioPlayer.play(song)
                            },
                            onTogglePlayPause = {
                                audioPlayer.togglePlayPause()
                            },
                            onPlayAll = { songs ->
                                if (songs.isNotEmpty()) {
                                    audioPlayer.setQueue(songs)
                                    audioPlayer.play(songs.first())
                                }
                            },
                            onPlayNext = { song ->
                                audioPlayer.playNextInQueue(song)
                            },
                            onAddToQueue = { song ->
                                audioPlayer.addToQueue(song)
                            },
                            onTabSelected = { selectedTab = it },
                            modifier = Modifier.weight(1f)
                        )

                        if (isLyricsOpen || isQueueOpen) {
                            DesktopLyricsPanel(
                                currentSong = currentSong,
                                lyrics = currentLyrics,
                                progressMs = progressMs,
                                isQueueMode = isQueueOpen,
                                queue = queue,
                                onClose = {
                                    isLyricsOpen = false
                                    isQueueOpen = false
                                },
                                onSongSelected = { audioPlayer.play(it) },
                                onSeek = { audioPlayer.seekTo(it) }
                            )
                        }
                    }

                    // Bottom Area: Persistent Wavy Audio Player Bar
                    DesktopPlayerBar(
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        progressMs = progressMs,
                        volume = volume,
                        isLyricsOpen = isLyricsOpen,
                        isQueueOpen = isQueueOpen,
                        onTogglePlayPause = { audioPlayer.togglePlayPause() },
                        onSkipNext = { audioPlayer.skipNext() },
                        onSkipPrev = { audioPlayer.skipPrev() },
                        onSeek = { audioPlayer.seekTo(it) },
                        onVolumeChange = { audioPlayer.setVolume(it) },
                        onToggleLyrics = {
                            isLyricsOpen = !isLyricsOpen
                            if (isLyricsOpen) isQueueOpen = false
                        },
                        onToggleQueue = {
                            isQueueOpen = !isQueueOpen
                            if (isQueueOpen) isLyricsOpen = false
                        },
                        onToggleLike = {
                            currentSong?.let { song ->
                                libraryManager.toggleLike(song)
                            }
                        }
                    )
                }
            }
        }
    }
}

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
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.musicapp.desktop.audio.DesktopAudioPlayer
import com.example.musicapp.desktop.mac.MacTrayIntegration
import com.example.musicapp.desktop.model.DesktopPlaylist
import com.example.musicapp.desktop.model.DesktopSong
import com.example.musicapp.desktop.ui.DesktopLyricsPanel
import com.example.musicapp.desktop.ui.DesktopMainContent
import com.example.musicapp.desktop.ui.DesktopPlayerBar
import com.example.musicapp.desktop.ui.DesktopSidebar

fun main() = application {
    val windowState = rememberWindowState(width = 1200.dp, height = 800.dp)
    var isWindowVisible by remember { mutableStateOf(true) }

    val audioPlayer = remember { DesktopAudioPlayer() }

    // Seed default tracks (including BAPS Satsang Podcast episode from user's screenshot!)
    var songCatalog by remember {
        mutableStateOf(
            listOf(
                DesktopSong(
                    id = "song_1",
                    title = "Yellow",
                    artist = "Coldplay",
                    album = "Parachutes",
                    durationSeconds = 269L,
                    isLiked = true,
                    lyrics = listOf(
                        "Look at the stars",
                        "Look how they shine for you",
                        "And everything you do",
                        "Yeah, they were all yellow",
                        "I came along",
                        "I wrote a song for you",
                        "And all the things you do",
                        "And it was called \"Yellow\""
                    )
                ),
                DesktopSong(
                    id = "song_2",
                    title = "Patience and True Refuge in Bhagwan",
                    artist = "BAPS Satsang Gujarati • Pujya Somprakashdas Swami",
                    album = "Episode • BAPS Satsang",
                    durationSeconds = 2837L, // 47 min 17 sec from screenshot!
                    isLiked = true,
                    lyrics = listOf(
                        "Patience and True Refuge in Bhagwan",
                        "Pujya Somprakashdas Swami",
                        "BAPS Satsang Gujarati",
                        "આપણું જીવન સુખ અને દુઃખ બંનેથી ભરેલું છે.",
                        "જ્યારે મુશ્કેલીઓ આવે છે, ત્યારે આપણે ઘણીવાર નિરાશ થઈ જઈએ છીએ.",
                        "પૂજ્ય સોમપ્રકાશદાસ સ્વામી આ કથામાં સમજાવે છે કે સુખ અને દુઃખ એ જીવનના બે પાસાં છે."
                    )
                ),
                DesktopSong(
                    id = "song_3",
                    title = "Starboy",
                    artist = "The Weeknd ft. Daft Punk",
                    album = "Starboy",
                    durationSeconds = 230L
                ),
                DesktopSong(
                    id = "song_4",
                    title = "Levitating",
                    artist = "Dua Lipa",
                    album = "Future Nostalgia",
                    durationSeconds = 203L
                ),
                DesktopSong(
                    id = "song_5",
                    title = "Tum Hi Ho",
                    artist = "Arijit Singh",
                    album = "Aashiqui 2",
                    durationSeconds = 262L,
                    isLiked = true
                ),
                DesktopSong(
                    id = "song_6",
                    title = "Dil Diyan Gallan",
                    artist = "Atif Aslam",
                    album = "Tiger Zinda Hai",
                    durationSeconds = 320L
                )
            )
        )
    }

    LaunchedEffect(Unit) {
        audioPlayer.setQueue(songCatalog)
        // Setup macOS Menu Bar Tray
        MacTrayIntegration.setupTray(
            onPlayPause = { audioPlayer.togglePlayPause() },
            onNext = { audioPlayer.skipNext() },
            onPrev = { audioPlayer.skipPrev() },
            onShowWindow = { isWindowVisible = true },
            onQuit = { exitApplication() }
        )
    }

    val currentSong by audioPlayer.currentSong.collectAsState()
    val isPlaying by audioPlayer.isPlaying.collectAsState()
    val progressMs by audioPlayer.progressMs.collectAsState()
    val volume by audioPlayer.volume.collectAsState()
    val queue by audioPlayer.queue.collectAsState()

    // Update macOS tray tooltip whenever track or playing state changes
    LaunchedEffect(currentSong, isPlaying) {
        currentSong?.let {
            MacTrayIntegration.updateNowPlaying(it.title, it.artist, isPlaying)
        }
    }

    var selectedTab by remember { mutableStateOf("Home") }
    var isLyricsOpen by remember { mutableStateOf(false) }
    var isQueueOpen by remember { mutableStateOf(false) }

    val playlists = remember {
        listOf(
            DesktopPlaylist("pl_1", "Chill Mix", songCount = 18),
            DesktopPlaylist("pl_2", "Workout Beats", songCount = 24),
            DesktopPlaylist("pl_3", "Satsang & Katha", songCount = 12)
        )
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
                            playlists = playlists
                        )

                        DesktopMainContent(
                            currentSong = currentSong,
                            songs = songCatalog,
                            onPlaySong = { song ->
                                audioPlayer.play(song)
                            },
                            onToggleLike = { song ->
                                songCatalog = songCatalog.map {
                                    if (it.id == song.id) it.copy(isLiked = !it.isLiked) else it
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )

                        if (isLyricsOpen || isQueueOpen) {
                            DesktopLyricsPanel(
                                currentSong = currentSong,
                                isQueueMode = isQueueOpen,
                                queue = queue,
                                onClose = {
                                    isLyricsOpen = false
                                    isQueueOpen = false
                                },
                                onSongSelected = { audioPlayer.play(it) }
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
                                songCatalog = songCatalog.map {
                                    if (it.id == song.id) it.copy(isLiked = !it.isLiked) else it
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

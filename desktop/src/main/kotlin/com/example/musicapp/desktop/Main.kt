package com.example.musicapp.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.musicapp.desktop.audio.DesktopAudioPlayer
import com.example.musicapp.desktop.data.*
import com.example.musicapp.desktop.mac.MacTrayIntegration
import com.example.musicapp.desktop.model.DesktopSong
import com.example.musicapp.desktop.sync.DesktopSyncService
import com.example.musicapp.desktop.theme.DesktopThemeManager
import com.example.musicapp.desktop.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

fun main() = application {
    val windowState = rememberWindowState(width = 1260.dp, height = 860.dp)
    var isWindowVisible by remember { mutableStateOf(true) }

    val musicRepository = remember { DesktopMusicRepository() }
    val libraryManager = remember { DesktopLibraryManager() }
    val userManager = remember { DesktopUserManager() }
    val downloadManager = remember { DesktopDownloadManager(musicRepository) }
    val audioPlayer = remember { DesktopAudioPlayer(musicRepository) }

    val coroutineScope = rememberCoroutineScope()
    var currentLyrics by remember { mutableStateOf<List<SyncedLyricLine>>(emptyList()) }

    val currentTheme by DesktopThemeManager.currentTheme.collectAsState()
    val themeAccent = currentTheme.primaryColor

    val currentUser by userManager.currentUser.collectAsState()
    val currentSong by audioPlayer.currentSong.collectAsState()
    val isPlaying by audioPlayer.isPlaying.collectAsState()
    val progressMs by audioPlayer.progressMs.collectAsState()
    val volume by audioPlayer.volume.collectAsState()
    val queue by audioPlayer.queue.collectAsState()
    val isShuffle by audioPlayer.isShuffle.collectAsState()
    val isRepeat by audioPlayer.isRepeat.collectAsState()
    val playlists by libraryManager.playlists.collectAsState()
    val likedSongs by libraryManager.likedSongs.collectAsState()

    var selectedTab by remember { mutableStateOf("Home") }
    var isLyricsOpen by remember { mutableStateOf(false) }
    var isQueueOpen by remember { mutableStateOf(false) }
    var showAuthDialog by remember { mutableStateOf(false) }
    var showSyncDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showSoundCapsuleDialog by remember { mutableStateOf(false) }
    var showEqualizerDialog by remember { mutableStateOf(false) }
    var showDeviceSelectorDialog by remember { mutableStateOf(false) }
    var showPlaylistImportDialog by remember { mutableStateOf(false) }
    var isFullscreenPlayerOpen by remember { mutableStateOf(false) }
    var showLaunchSplash by remember { mutableStateOf(true) }
    var selectedDeviceName by remember { mutableStateOf("MacBook Pro Speakers") }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    val isCurrentSongLiked = currentSong?.let { s -> likedSongs.any { it.id == s.id } } ?: false

    // Start sync service, intro splash and check if user needs to sign in on first launch
    LaunchedEffect(Unit) {
        DesktopSyncService.start(libraryManager, currentUser)
        DesktopSyncService.onSyncCompleted = { payload ->
            toastMessage = "✓ Synced with ${payload.deviceName} (${payload.playlists.size} playlists)!"
        }
        DesktopSyncService.onAuthCallbackReceived = { email, name ->
            userManager.signIn(name = name, email = email, isGuest = false)
            showAuthDialog = false
            toastMessage = "✓ Connected to Google: $name ($email)"
        }
        delay(1800)
        showLaunchSplash = false
        if (currentUser == null) {
            showAuthDialog = true
        }
    }

    // Automatically sync library data & prompt sign-in when currentUser changes
    LaunchedEffect(currentUser) {
        libraryManager.switchUser(currentUser)
        DesktopSyncService.updateUser(currentUser)
        if (!showLaunchSplash && currentUser == null) {
            showAuthDialog = true
        }
    }

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

    // Auto-dismiss floating toast messages
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(2800)
            toastMessage = null
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

    fun shareSong(song: DesktopSong) {
        try {
            val text = "🎵 Listening to \"${song.title}\" by ${song.artist} on Rivara Music! https://rivara.app/track/${song.id}"
            val sel = StringSelection(text)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
            toastMessage = "Copied track link to clipboard!"
        } catch (_: Exception) {
            toastMessage = "Copied track details!"
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
                } else if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape && isFullscreenPlayerOpen) {
                    isFullscreenPlayerOpen = false
                    true
                } else false
            }
        ) {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = themeAccent,
                    background = Color(0xFF121212),
                    surface = Color(0xFF181818)
                )
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
                        // Top Area: Sidebar + Main Content + Lyrics/Queue Side Panel
                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            DesktopSidebar(
                                selectedTab = selectedTab,
                                onTabSelected = { selectedTab = it },
                                playlists = playlists,
                                themeAccent = themeAccent,
                                onCreatePlaylist = { name ->
                                    libraryManager.createPlaylist(name)
                                },
                                onOpenImportPlaylist = { showPlaylistImportDialog = true },
                                onOpenSoundCapsule = { showSoundCapsuleDialog = true },
                                onOpenSync = { showSyncDialog = true },
                                onOpenSettings = { showSettingsDialog = true }
                            )

                            DesktopMainContent(
                                selectedTab = selectedTab,
                                currentUser = currentUser,
                                currentSong = currentSong,
                                isPlaying = isPlaying,
                                playlists = playlists,
                                libraryManager = libraryManager,
                                musicRepository = musicRepository,
                                downloadManager = downloadManager,
                                themeAccent = themeAccent,
                                onPlaySong = { song ->
                                    audioPlayer.play(song)
                                },
                                onPlaySongWithQueue = { song, queue ->
                                    audioPlayer.play(song, queueContext = queue)
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
                                onOpenAuth = { showAuthDialog = true },
                                onOpenSync = { showSyncDialog = true },
                                onShareSong = { shareSong(it) },
                                modifier = Modifier.weight(1f)
                            )

                            if (isLyricsOpen || isQueueOpen) {
                                DesktopLyricsPanel(
                                    currentSong = currentSong,
                                    lyrics = currentLyrics,
                                    progressMs = progressMs,
                                    isQueueMode = isQueueOpen,
                                    queue = queue,
                                    themeAccent = themeAccent,
                                    onClose = {
                                        isLyricsOpen = false
                                        isQueueOpen = false
                                    },
                                    onOpenFullscreen = { isFullscreenPlayerOpen = true },
                                    onShareSong = { currentSong?.let { shareSong(it) } },
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
                            isShuffle = isShuffle,
                            isRepeat = isRepeat,
                            isLyricsOpen = isLyricsOpen,
                            isQueueOpen = isQueueOpen,
                            isLiked = isCurrentSongLiked,
                            isDownloaded = currentSong?.let { downloadManager.isDownloaded(it.id) } ?: false,
                            isDownloading = currentSong?.let { downloadManager.isDownloading(it.id) } ?: false,
                            themeAccent = themeAccent,
                            onTogglePlayPause = { audioPlayer.togglePlayPause() },
                            onSkipNext = { audioPlayer.skipNext() },
                            onSkipPrev = { audioPlayer.skipPrev() },
                            onSeek = { audioPlayer.seekTo(it) },
                            onVolumeChange = { audioPlayer.setVolume(it) },
                            onToggleShuffle = { audioPlayer.toggleShuffle() },
                            onToggleRepeat = { audioPlayer.toggleRepeat() },
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
                            },
                            onToggleDownload = {
                                currentSong?.let { song ->
                                    if (downloadManager.isDownloaded(song.id)) downloadManager.removeDownload(song.id)
                                    else downloadManager.downloadSong(song)
                                }
                            },
                            onOpenEqualizer = { showEqualizerDialog = true },
                            onOpenDeviceSelector = { showDeviceSelectorDialog = true },
                            onShareSong = { currentSong?.let { shareSong(it) } },
                            onOpenFullscreen = { isFullscreenPlayerOpen = true }
                        )
                    }

                    // Fullscreen Player Overlay
                    if (isFullscreenPlayerOpen && currentSong != null) {
                        DesktopFullScreenPlayer(
                            song = currentSong!!,
                            isPlaying = isPlaying,
                            progressMs = progressMs,
                            volume = volume,
                            isShuffle = isShuffle,
                            isRepeat = isRepeat,
                            isLiked = isCurrentSongLiked,
                            themeAccent = themeAccent,
                            lyrics = currentLyrics,
                            queue = queue,
                            downloadManager = downloadManager,
                            onTogglePlayPause = { audioPlayer.togglePlayPause() },
                            onSkipNext = { audioPlayer.skipNext() },
                            onSkipPrev = { audioPlayer.skipPrev() },
                            onSeek = { audioPlayer.seekTo(it) },
                            onVolumeChange = { audioPlayer.setVolume(it) },
                            onToggleShuffle = { audioPlayer.toggleShuffle() },
                            onToggleRepeat = { audioPlayer.toggleRepeat() },
                            onToggleLike = { libraryManager.toggleLike(currentSong!!) },
                            onOpenEqualizer = { showEqualizerDialog = true },
                            onOpenDeviceSelector = { showDeviceSelectorDialog = true },
                            onShareSong = { shareSong(currentSong!!) },
                            onSongSelectedFromQueue = { audioPlayer.play(it) },
                            onClose = { isFullscreenPlayerOpen = false }
                        )
                    }

                    // Launch Splash Screen Animation
                    AnimatedVisibility(
                        visible = showLaunchSplash,
                        enter = fadeIn(animationSpec = tween(200)),
                        exit = fadeOut(animationSpec = tween(500))
                    ) {
                        DesktopLaunchSplash(modifier = Modifier.fillMaxSize())
                    }

                    // Sign In / Sign Up Dialog
                    if (showAuthDialog) {
                        DesktopAuthDialog(
                            userManager = userManager,
                            onDismiss = { showAuthDialog = false }
                        )
                    }

                    // Sync Hub Dialog
                    if (showSyncDialog) {
                        DesktopSyncDialog(
                            libraryManager = libraryManager,
                            userManager = userManager,
                            themeAccent = themeAccent,
                            onOpenAuth = { showAuthDialog = true },
                            onDismiss = { showSyncDialog = false }
                        )
                    }

                    // Settings Dialog
                    if (showSettingsDialog) {
                        DesktopSettingsDialog(
                            themeAccent = themeAccent,
                            currentUser = currentUser,
                            downloadManager = downloadManager,
                            onOpenEqualizer = { showEqualizerDialog = true },
                            onOpenDeviceSelector = { showDeviceSelectorDialog = true },
                            onOpenSoundCapsule = { showSoundCapsuleDialog = true },
                            onOpenAuth = { showAuthDialog = true },
                            onOpenSync = { showSyncDialog = true },
                            onDismiss = { showSettingsDialog = false }
                        )
                    }

                    // Sound Capsule Dialog (2026 Listening Journey Recap)
                    if (showSoundCapsuleDialog) {
                        DesktopSoundCapsuleDialog(
                            currentUser = currentUser,
                            themeAccent = themeAccent,
                            onPlaySong = { audioPlayer.play(it) },
                            onDismiss = { showSoundCapsuleDialog = false }
                        )
                    }

                    // Audio Equalizer Dialog
                    if (showEqualizerDialog) {
                        DesktopEqualizerDialog(
                            themeAccent = themeAccent,
                            onDismiss = { showEqualizerDialog = false }
                        )
                    }

                    // Output Device Selector Dialog
                    if (showDeviceSelectorDialog) {
                        DesktopDeviceSelectorDialog(
                            themeAccent = themeAccent,
                            selectedDeviceName = selectedDeviceName,
                            onDeviceSelected = {
                                selectedDeviceName = it
                                audioPlayer.setOutputDevice(it)
                            },
                            onDismiss = { showDeviceSelectorDialog = false }
                        )
                    }

                    // Playlist Import Dialog
                    if (showPlaylistImportDialog) {
                        DesktopPlaylistImportDialog(
                            themeAccent = themeAccent,
                            libraryManager = libraryManager,
                            musicRepository = musicRepository,
                            onDismiss = { showPlaylistImportDialog = false }
                        )
                    }

                    // Floating Toast Notification Pill
                    if (toastMessage != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 104.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color(0xFF262626))
                                .border(1.dp, themeAccent.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = themeAccent, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(toastMessage!!, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

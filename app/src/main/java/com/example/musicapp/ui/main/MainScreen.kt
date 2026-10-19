package com.example.musicapp.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.ui.artist.ArtistScreen
import com.example.musicapp.ui.capsule.SoundCapsuleScreen
import com.example.musicapp.ui.drawer.ProfileDrawerContent
import com.example.musicapp.ui.home.HomeScreen
import com.example.musicapp.ui.library.LibraryScreen
import com.example.musicapp.ui.navigation.Screen
import com.example.musicapp.data.playlist.UserPlaylist
import com.example.musicapp.ui.player.FullScreenPlayerSheet
import com.example.musicapp.ui.player.SpotifyConnectSheet
import com.example.musicapp.ui.playlist.PlaylistScreen
import com.example.musicapp.ui.recents.RecentsScreen
import com.example.musicapp.ui.search.SearchScreen
import com.example.musicapp.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    viewModel: MainScreenViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    modifier: Modifier = Modifier,
    sharedPlaylist: UserPlaylist? = null,
    onSharedPlaylistOpened: () -> Unit = {}
) {
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var backstack by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }
    val currentScreen = backstack.lastOrNull() ?: Screen.Home

    var isPlayerExpanded by remember { mutableStateOf(false) }
    var showConnectFromMini by remember { mutableStateOf(false) }

    LaunchedEffect(sharedPlaylist) {
        sharedPlaylist?.let { playlist ->
            backstack = listOf(
                Screen.Home,
                Screen.Playlist(playlist.id, playlist.name, playlist.coverUrl, "Playlist • ${playlist.creator}")
            )
            onSharedPlaylistOpened()
        }
    }

    fun navigateTo(screen: Screen) {
        backstack = backstack + screen
    }

    fun popBack() {
        if (backstack.size > 1) {
            backstack = backstack.dropLast(1)
        }
    }

    fun switchTab(screen: Screen) {
        backstack = listOf(screen)
    }

    BackHandler(enabled = backstack.size > 1 || isPlayerExpanded) {
        if (isPlayerExpanded) {
            isPlayerExpanded = false
        } else {
            popBack()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ProfileDrawerContent(
                onNavigate = { screen ->
                    scope.launch { drawerState.close() }
                    navigateTo(screen)
                },
                onClose = {
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Box(modifier = modifier.fillMaxSize().background(Color(0xFF121212))) {
            // Main Content Area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF351515), Color(0xFF121212)),
                            startY = 0f,
                            endY = 1000f
                        )
                    )
            ) {
                when (currentScreen) {
                    is Screen.Home -> HomeScreen(
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onNavigate = { navigateTo(it) },
                        viewModel = viewModel
                    )
                    is Screen.Search -> SearchScreen(
                        viewModel = viewModel,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onNavigate = { navigateTo(it) },
                        onOpenPlayer = { if (currentSong != null) isPlayerExpanded = true }
                    )
                    is Screen.Library -> LibraryScreen(
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onNavigate = { navigateTo(it) },
                        viewModel = viewModel
                    )
                    is Screen.Playlist -> PlaylistScreen(
                        playlistId = currentScreen.playlistId,
                        title = currentScreen.title,
                        coverUrl = currentScreen.coverUrl,
                        creator = currentScreen.subtitle.removePrefix("Playlist • "),
                        savesAndDuration = currentScreen.saves,
                        viewModel = viewModel,
                        onBack = { popBack() },
                        onNavigate = { navigateTo(it) }
                    )
                    is Screen.Artist -> ArtistScreen(
                        artistName = currentScreen.artistName,
                        imageUrl = currentScreen.imageUrl,
                        monthlyListeners = currentScreen.listeners,
                        viewModel = viewModel,
                        onBack = { popBack() },
                        onNavigate = { navigateTo(it) }
                    )
                    is Screen.Settings -> SettingsScreen(onBack = { popBack() })
                    is Screen.Recents -> RecentsScreen(
                        onBack = { popBack() },
                        viewModel = viewModel,
                        onNavigate = { navigateTo(it) }
                    )
                    is Screen.SoundCapsule -> SoundCapsuleScreen(
                        onBack = { popBack() },
                        viewModel = viewModel,
                        onNavigate = { navigateTo(it) }
                    )
                }
            }

            // Bottom Navigation and Floating MiniPlayer Overlay
            if (!isPlayerExpanded) {
                // Use one blue-tinted translucent surface behind both the tabs
                // and the system gesture inset, with no separate edge strip.
                val navigationBarColor = Color(0xE80A1018)
                Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    // Floating MiniPlayer
                    if (currentSong != null) {
                        FloatingCapsulePlayer(
                            song = currentSong!!,
                            isPlaying = isPlaying,
                            viewModel = viewModel,
                            onClick = { isPlayerExpanded = true },
                            onOpenConnect = { showConnectFromMini = true }
                        )
                    }

                    // Draw the same surface behind the system inset so the panel
                    // reaches the physical bottom edge without a color break.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(navigationBarColor)
                            .navigationBarsPadding()
                    ) {
                        NavigationBar(
                            containerColor = Color.Transparent,
                            contentColor = Color.White,
                            windowInsets = WindowInsets(0, 0, 0, 0),
                            modifier = Modifier.fillMaxWidth().height(64.dp)
                        ) {
                            val isHome = currentScreen is Screen.Home
                            val isSearch = currentScreen is Screen.Search
                            val isLibrary = currentScreen is Screen.Library

                            val navItemColors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF00F59B),
                                unselectedIconColor = Color(0xFF94A3B8),
                                selectedTextColor = Color(0xFF00F59B),
                                unselectedTextColor = Color(0xFF94A3B8),
                                indicatorColor = Color.Transparent
                            )

                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Home, contentDescription = "Home", modifier = Modifier.size(28.dp)) },
                                label = { Text("Home", style = MaterialTheme.typography.labelSmall) },
                                selected = isHome,
                                onClick = { switchTab(Screen.Home) },
                                colors = navItemColors
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(28.dp)) },
                                label = { Text("Search", style = MaterialTheme.typography.labelSmall) },
                                selected = isSearch,
                                onClick = { switchTab(Screen.Search) },
                                colors = navItemColors
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Your Library", modifier = Modifier.size(28.dp)) },
                                label = { Text("Your Library", style = MaterialTheme.typography.labelSmall) },
                                selected = isLibrary,
                                onClick = { switchTab(Screen.Library) },
                                colors = navItemColors
                            )
                        }
                    }
                }
            }

            // Full Screen Player Animated Overlay
            AnimatedVisibility(
                visible = isPlayerExpanded,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.fillMaxSize()
            ) {
                if (currentSong != null) {
                    FullScreenPlayerSheet(
                        song = currentSong!!,
                        viewModel = viewModel,
                        onClose = { isPlayerExpanded = false },
                        onNavigateToArtist = { artistName ->
                            isPlayerExpanded = false
                            val cleanArtist = artistName.split(",").firstOrNull()?.trim() ?: artistName
                            navigateTo(Screen.Artist(cleanArtist, com.example.musicapp.data.MusicCatalog.getArtistImageUrl(cleanArtist)))
                        }
                    )
                }
            }
            // Connect Sheet from MiniPlayer
            if (showConnectFromMini && currentSong != null) {
                SpotifyConnectSheet(
                    song = currentSong!!,
                    onDismiss = { showConnectFromMini = false }
                )
            }
        }
    }
}

@Composable
fun FloatingCapsulePlayer(
    song: SongInfo,
    isPlaying: Boolean,
    viewModel: MainScreenViewModel? = null,
    onClick: () -> Unit,
    onOpenConnect: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val playlistManager = remember { com.example.musicapp.data.playlist.PlaylistManager.getInstance(context) }
    val audioDeviceManager = remember { com.example.musicapp.ui.util.AudioDeviceManager.getInstance(context) }
    val devices by audioDeviceManager.devices.collectAsStateWithLifecycle()
    val activeDeviceId by audioDeviceManager.activeDeviceId.collectAsStateWithLifecycle()
    val activeDevice = remember(devices, activeDeviceId) { audioDeviceManager.getActiveDevice() }

    val likedSongs by playlistManager.likedSongs.collectAsStateWithLifecycle()
    val isLiked = remember(likedSongs, song.videoId) { playlistManager.isLiked(song.videoId) }

    var playBounced by remember { mutableStateOf(false) }
    val playScale by animateFloatAsState(
        targetValue = if (playBounced) 1.25f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        finishedListener = { playBounced = false },
        label = "capsulePlayBounce"
    )

    var heartBounced by remember { mutableStateOf(false) }
    val heartScale by animateFloatAsState(
        targetValue = if (heartBounced) 1.3f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        finishedListener = { heartBounced = false },
        label = "capsuleHeartBounce"
    )

    var dragX by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(elevation = 14.dp, shape = RoundedCornerShape(22.dp), spotColor = Color(0xFF00F59B))
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(Color(0xF00F1722), Color(0xF5090E15))
                )
            )
            .border(
                1.dp,
                Brush.horizontalGradient(listOf(Color(0x5500F59B), Color(0x3300D2FF))),
                RoundedCornerShape(22.dp)
            )
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragX += dragAmount.x
                        dragY += dragAmount.y
                    },
                    onDragEnd = {
                        if (dragY < -50f) {
                            onClick()
                        } else if (dragX < -60f) {
                            viewModel?.skipToNext()
                        } else if (dragX > 60f) {
                            viewModel?.skipToPrevious()
                        }
                        dragX = 0f
                        dragY = 0f
                    },
                    onDragCancel = {
                        dragX = 0f
                        dragY = 0f
                    }
                )
            }
            .clickable(onClick = onClick)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, Color(0x3300F59B), RoundedCornerShape(14.dp))
                ) {
                    com.example.musicapp.ui.components.TrackArtwork(
                        model = song.albumArtUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onOpenConnect() }
                    ) {
                        Icon(
                            imageVector = activeDevice.icon,
                            contentDescription = null,
                            tint = Color(0xFF00F59B),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = activeDevice.name,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFF00F59B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Connect Device Button
                IconButton(
                    onClick = onOpenConnect,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = activeDevice.icon,
                        contentDescription = "Audio Routing",
                        tint = Color(0xFF00F59B),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Heart Button
                IconButton(
                    onClick = {
                        heartBounced = true
                        playlistManager.toggleLike(song)
                    },
                    modifier = Modifier.size(34.dp).scale(heartScale)
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) Color(0xFFFF416C) else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Play / Pause glowing pill button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .scale(playScale)
                        .clip(CircleShape)
                        .background(Color(0xFF00F59B))
                        .clickable {
                            playBounced = true
                            viewModel?.togglePlayPause()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Real-time Holographic Progress Line
            MiniPlayerProgressBar(viewModel = viewModel)
        }
    }
}

@Composable
fun MiniPlayerProgressBar(
    viewModel: MainScreenViewModel? = null,
    modifier: Modifier = Modifier
) {
    val currentPosition = viewModel?.currentPosition?.collectAsStateWithLifecycle()?.value ?: 0L
    val duration = viewModel?.duration?.collectAsStateWithLifecycle()?.value ?: 0L
    val totalDuration = if (duration > 0) duration else 216000L
    val progress = (currentPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)

    androidx.compose.foundation.Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(2.5.dp)
    ) {
        // Subtle track
        drawRect(
            color = Color(0x22FFFFFF),
            size = size
        )
        // Neon progress gradient
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color(0xFF00F59B), Color(0xFF00D2FF))),
            size = androidx.compose.ui.geometry.Size(size.width * progress, size.height)
        )
    }
}

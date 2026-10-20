package com.example.musicapp.ui.player

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicapp.ui.components.TrackArtwork
import com.example.musicapp.data.download.DownloadStatus
import com.example.musicapp.data.download.MusicDownloadManager
import com.example.musicapp.data.MusicCatalog
import com.example.musicapp.data.auth.UserManager
import com.example.musicapp.data.lyrics.LyricLine
import com.example.musicapp.data.lyrics.LyricsManager
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.playlist.PlaylistManager
import com.example.musicapp.ui.main.MainScreenViewModel
import com.example.musicapp.ui.util.rememberDynamicGradient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenPlayerSheet(
    song: SongInfo,
    viewModel: MainScreenViewModel,
    onClose: () -> Unit,
    onNavigateToArtist: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val playlistManager = remember { PlaylistManager.getInstance(context) }
    val downloadManager = remember { MusicDownloadManager.getInstance(context) }
    val currentUser by remember { UserManager.getInstance(context).currentUser }.collectAsStateWithLifecycle()
    val lyricsManager = remember { LyricsManager.getInstance(context) }

    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val isShuffle by viewModel.isShuffle.collectAsStateWithLifecycle()
    val isRepeat by viewModel.isRepeat.collectAsStateWithLifecycle()
    val currentQueue by viewModel.currentQueue.collectAsStateWithLifecycle()
    val upcomingQueue = remember(currentQueue, song.videoId) { viewModel.getUpcomingQueue() }

    val likedSongs by playlistManager.likedSongs.collectAsStateWithLifecycle()
    val isLiked = remember(likedSongs, song.videoId) { playlistManager.isLiked(song.videoId) }

    val downloadStates by downloadManager.downloadStates.collectAsStateWithLifecycle()
    val downloadStatus = downloadStates[song.videoId] ?: DownloadStatus.NotDownloaded

    var showConnectDialog by remember { mutableStateOf(false) }
    var showOptionsDialog by remember { mutableStateOf(false) }
    var showQueueDialog by remember { mutableStateOf(false) }
    var showAudioEffectsSheet by remember { mutableStateOf(false) }
    var isLyricsExpanded by remember { mutableStateOf(false) }
    val activeAudioProfile by viewModel.activeAudioProfile.collectAsStateWithLifecycle()

    // Dynamic gradient extracted from artwork
    val gradientColors = rememberDynamicGradient(song.albumArtUrl)

    // Genre-adaptive lyrics typography helper
    val lyricStyle = remember(song.videoId) { LyricsTypographyHelper.getFontStyle(song) }

    // Similar Songs & Fans Also Like
    val artistCatalog = remember(song.artist) { MusicCatalog.getArtistData(song.artist) }
    val similarSongs = remember(artistCatalog, song.videoId) {
        val filtered = artistCatalog.topTracks.filter { it.videoId != song.videoId && !it.title.equals(song.title, ignoreCase = true) }
        if (filtered.size >= 4) filtered.take(5)
        else (filtered + MusicCatalog.hitsSongs.filter { it.videoId != song.videoId }).distinctBy { it.videoId }.take(5)
    }
    val similarArtists = remember(artistCatalog) { artistCatalog.similarArtists }

    // Lyrics State
    var lyrics by remember(song.videoId) { mutableStateOf<List<LyricLine>>(emptyList()) }
    LaunchedEffect(song.videoId) {
        lyrics = lyricsManager.getLyrics(song.title, song.artist)
    }

    val activeLyricIndex = remember(lyrics, currentPosition) {
        if (lyrics.isEmpty()) 0
        else lyrics.indexOfLast { it.timestampMs <= currentPosition }.coerceAtLeast(0)
    }

    val lyricsListState = rememberLazyListState()
    LaunchedEffect(activeLyricIndex, isLyricsExpanded) {
        if (isLyricsExpanded && activeLyricIndex in lyrics.indices) {
            lyricsListState.animateScrollToItem((activeLyricIndex - 1).coerceAtLeast(0))
        }
    }

    val totalDuration = if (duration > 0) duration else 216000L

    // Spring bouncy scale for play button
    var playButtonBounced by remember { mutableStateOf(false) }
    val playButtonScale by animateFloatAsState(
        targetValue = if (playButtonBounced) 1.15f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        finishedListener = { playButtonBounced = false },
        label = "playBounce"
    )

    // Spring bouncy scale for heart button
    var heartBounced by remember { mutableStateOf(false) }
    val heartScale by animateFloatAsState(
        targetValue = if (heartBounced) 1.25f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        finishedListener = { heartBounced = false },
        label = "heartBounce"
    )

    fun shareCurrentSong() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, song.title)
            putExtra(Intent.EXTRA_TEXT, "Listen to \"${song.title}\" by ${song.artist}")
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share song via"))
    }

    var swipeOffsetY by remember { mutableStateOf(0f) }
    val animatedOffsetY by animateFloatAsState(targetValue = swipeOffsetY, label = "swipeDismiss")
    val dismissThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 48.dp.toPx() }
    var horizontalDragArtwork by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { androidx.compose.ui.unit.IntOffset(0, animatedOffsetY.toInt()) }
            .pointerInput(dismissThresholdPx) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        if (dragAmount.y > 0f && (kotlin.math.abs(dragAmount.y) > kotlin.math.abs(dragAmount.x) || swipeOffsetY > 0f)) {
                            change.consume()
                            swipeOffsetY = (swipeOffsetY + dragAmount.y).coerceAtLeast(0f)
                        }
                    },
                    onDragEnd = {
                        if (swipeOffsetY > dismissThresholdPx) {
                            onClose()
                        } else {
                            swipeOffsetY = 0f
                        }
                    },
                    onDragCancel = {
                        swipeOffsetY = 0f
                    }
                )
            }
            .background(
                Brush.verticalGradient(
                    colors = gradientColors,
                    startY = 0f,
                    endY = 1600f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Drag Handle for intuitive swipe down minimize
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                if (dragAmount.y > 0f || swipeOffsetY > 0f) {
                                    change.consume()
                                    swipeOffsetY = (swipeOffsetY + dragAmount.y).coerceAtLeast(0f)
                                }
                            },
                            onDragEnd = {
                                if (swipeOffsetY > dismissThresholdPx) {
                                    onClose()
                                } else {
                                    swipeOffsetY = 0f
                                }
                            },
                            onDragCancel = { swipeOffsetY = 0f }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 42.dp, height = 4.5.dp)
                        .clip(CircleShape)
                        .background(Color(0x66FFFFFF))
                )
            }

            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                if (dragAmount.y > 0f || swipeOffsetY > 0f) {
                                    change.consume()
                                    swipeOffsetY = (swipeOffsetY + dragAmount.y).coerceAtLeast(0f)
                                }
                            },
                            onDragEnd = {
                                if (swipeOffsetY > dismissThresholdPx) {
                                    onClose()
                                } else {
                                    swipeOffsetY = 0f
                                }
                            },
                            onDragCancel = { swipeOffsetY = 0f }
                        )
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "PLAYING FROM PLAYLIST",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color(0xFFA0A0A0)
                    )
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { showOptionsDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Album Artwork with horizontal swipe to skip next / prev
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                horizontalDragArtwork += dragAmount.x
                            },
                            onDragEnd = {
                                if (horizontalDragArtwork < -80f) {
                                    viewModel.skipToNext()
                                } else if (horizontalDragArtwork > 80f) {
                                    viewModel.skipToPrevious()
                                }
                                horizontalDragArtwork = 0f
                            },
                            onDragCancel = { horizontalDragArtwork = 0f }
                        )
                    }
            ) {
                TrackArtwork(
                    model = song.albumArtUrl,
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Dynamic Single-Line Live Lyric Preview beneath Artwork (Matching Screenshot 133831 & 133902)
            val activeSingleLyric = lyrics.getOrNull(activeLyricIndex)?.text
            if (!activeSingleLyric.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(18.dp))
                AnimatedContent(
                    targetState = activeSingleLyric,
                    transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
                    label = "SingleLineLyricPreview"
                ) { lyricText ->
                    Text(
                        text = lyricText,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = lyricStyle.fontFamily,
                            fontStyle = lyricStyle.fontStyle,
                            fontWeight = lyricStyle.activeWeight,
                            letterSpacing = lyricStyle.letterSpacing,
                            fontSize = 17.sp
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isLyricsExpanded = true }
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(20.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Title, Artist, Like & Download Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFA0A0A0),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Like button with bouncy micro-interaction
                    IconButton(
                        enabled = !currentUser.isGuest,
                        onClick = {
                            heartBounced = true
                            playlistManager.toggleLike(song)
                        },
                        modifier = Modifier.scale(heartScale)
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.AddCircleOutline,
                            contentDescription = "Like",
                            tint = if (isLiked) Color(0xFFFF416C) else Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Download button with live state
                    IconButton(
                        onClick = {
                            if (downloadStatus is DownloadStatus.NotDownloaded) {
                                downloadManager.downloadSong(song) { target ->
                                    val repo = com.example.musicapp.data.DefaultDataRepository(context)
                                    repo.getStreamUrl(target.videoId, target.title, target.artist, target.durationSeconds)
                                }
                            }
                        }
                    ) {
                        when (downloadStatus) {
                            is DownloadStatus.Downloaded -> {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Downloaded",
                                    tint = Color(0xFF00F59B),
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            is DownloadStatus.Downloading -> {
                                CircularProgressIndicator(
                                    progress = { downloadStatus.progress },
                                    modifier = Modifier.size(22.dp),
                                    color = Color(0xFF00F59B),
                                    strokeWidth = 2.dp
                                )
                            }
                            is DownloadStatus.NotDownloaded -> {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Seekbar Slider & Timestamps hoisted to avoid root recomposition
            PlayerProgressBarSection(
                viewModel = viewModel,
                totalDuration = totalDuration
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Playback Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.toggleShuffle() }) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) Color(0xFF00F59B) else Color(0xFFA0A0A0),
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = { viewModel.skipToPrevious() }) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                // Play / Pause Circle with spring bounce
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .scale(playButtonScale)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(listOf(Color(0xFF00F59B), Color(0xFF00D2FF), Color(0xFF00F59B)))
                        )
                        .clickable {
                            playButtonBounced = true
                            viewModel.togglePlayPause()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(36.dp)
                    )
                }
                IconButton(onClick = { viewModel.skipToNext() }) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                IconButton(onClick = { viewModel.toggleRepeat() }) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (isRepeat) Color(0xFF00F59B) else Color(0xFFA0A0A0),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bottom Device Connect, Share & Queue Row (Matching Screenshot 133831)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x33FFFFFF))
                        .clickable { showAudioEffectsSheet = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    val deviceIcon = when (activeAudioProfile?.category) {
                        com.example.musicapp.audio.DeviceCategory.EARBUDS,
                        com.example.musicapp.audio.DeviceCategory.HEADPHONES -> Icons.Default.Headphones
                        com.example.musicapp.audio.DeviceCategory.HOME_THEATRE -> Icons.Default.Speaker
                        com.example.musicapp.audio.DeviceCategory.CAR_AUDIO -> Icons.Default.DirectionsCar
                        else -> Icons.Default.PhoneAndroid
                    }
                    Icon(
                        imageVector = deviceIcon,
                        contentDescription = null,
                        tint = Color(0xFF00F59B),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = activeAudioProfile?.deviceName ?: "Audio Device",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF00F59B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• EQ",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFF00D2FF)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { shareCurrentSong() }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(onClick = { showQueueDialog = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Queue",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Translucent Frosted Glass Lyrics Card Preview with Genre Typography
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                (gradientColors.firstOrNull() ?: Color(0xFF1B3B4B)).copy(alpha = 0.50f),
                                Color(0xD9101622)
                            )
                        )
                    )
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                    .clickable { isLyricsExpanded = true }
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Lyrics",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x3300F59B))
                                    .border(1.dp, Color(0x6600F59B), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = lyricStyle.genreLabel,
                                    color = Color(0xFF00F59B),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = lyricStyle.fontFamily,
                                        fontStyle = lyricStyle.fontStyle,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { shareCurrentSong() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { isLyricsExpanded = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.OpenInFull, contentDescription = "Expand", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    val activeText = lyrics.getOrNull(activeLyricIndex)?.text ?: "Tap to load lyrics..."
                    val nextText = lyrics.getOrNull(activeLyricIndex + 1)?.text ?: ""

                    Text(
                        text = activeText,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = lyricStyle.fontFamily,
                            fontStyle = lyricStyle.fontStyle,
                            fontWeight = lyricStyle.activeWeight,
                            letterSpacing = lyricStyle.letterSpacing,
                            fontSize = 20.sp,
                            lineHeight = 28.sp
                        ),
                        color = Color.White
                    )
                    if (nextText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = nextText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = lyricStyle.fontFamily,
                                fontStyle = lyricStyle.fontStyle,
                                fontWeight = lyricStyle.inactiveWeight,
                                fontSize = 16.sp,
                                lineHeight = 24.sp
                            ),
                            color = Color(0x88FFFFFF)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Similar Songs Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF141922))
                    .border(1.dp, Color(0xFF232B3A), RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Similar Songs",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "Based on this track",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF8793A2)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    similarSongs.forEach { simSong ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.playSong(simSong) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TrackArtwork(
                                model = simSong.albumArtUrl,
                                contentDescription = simSong.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = simSong.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = simSong.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFA0A0A0),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = {
                                    viewModel.playNext(simSong)
                                    Toast.makeText(context, "Playing next: ${simSong.title}", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlaylistAdd,
                                    contentDescription = "Play Next",
                                    tint = Color(0xFF00F59B),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Fans Also Like Section (Similar Artists Carousel)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF141922))
                    .border(1.dp, Color(0xFF232B3A), RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Fans Also Like",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "Similar Artists",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF8793A2)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(similarArtists) { (simName, simImg) ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(90.dp)
                                    .clickable {
                                        onClose()
                                        onNavigateToArtist?.invoke(simName)
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, Color(0xFF00F59B), CircleShape)
                                ) {
                                    TrackArtwork(
                                        model = simImg,
                                        contentDescription = simName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = simName,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Artist",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF8793A2)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }

        // Full Screen Translucent Synchronized Lyrics View with Genre Typography
        AnimatedVisibility(visible = isLyricsExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                (gradientColors.firstOrNull() ?: Color(0xFF1B3B4B)).copy(alpha = 0.88f),
                                Color(0xEE0A0E17),
                                Color(0xF806080E)
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { isLyricsExpanded = false }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = lyricStyle.fontFamily,
                                    fontStyle = lyricStyle.fontStyle,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFA0A0A0),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "• ${lyricStyle.genreLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF00F59B)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(32.dp))
                    }

                    // Synchronized Interactive Lyrics List
                    LazyColumn(
                        state = lyricsListState,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 16.dp)
                    ) {
                        itemsIndexed(lyrics, key = { index, item -> "${item.timestampMs}_$index" }) { index, line ->
                            val isCurrent = index == activeLyricIndex
                            Text(
                                text = line.text,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontFamily = lyricStyle.fontFamily,
                                    fontStyle = lyricStyle.fontStyle,
                                    fontWeight = if (isCurrent) lyricStyle.activeWeight else lyricStyle.inactiveWeight,
                                    letterSpacing = lyricStyle.letterSpacing,
                                    fontSize = if (isCurrent) 26.sp else 20.sp,
                                    lineHeight = if (isCurrent) 38.sp else 30.sp
                                ),
                                color = if (isCurrent) Color.White else Color(0x55FFFFFF),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        // Tap any line to seek instantly!
                                        viewModel.seekTo(line.timestampMs)
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp)
                            )
                        }
                    }

                    // Bottom Controls Row in Expanded Lyrics (Matching Screenshot 133846)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { shareCurrentSong() }) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        IconButton(onClick = { showOptionsDialog = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }

                    // Seekbar in lyrics screen hoisted
                    PlayerProgressBarSection(
                        viewModel = viewModel,
                        totalDuration = totalDuration
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Play/Pause circular button centered
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable { viewModel.togglePlayPause() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            }
        }

        // Spotify Connect Bottom Sheet
        if (showConnectDialog) {
            SpotifyConnectSheet(
                song = song,
                onDismiss = { showConnectDialog = false }
            )
        }

        // Player Options Bottom Sheet
        if (showOptionsDialog) {
            PlayerOptionsSheet(
                song = song,
                onDismiss = { showOptionsDialog = false },
                onPlayNext = {
                    viewModel.playNext(song)
                    android.widget.Toast.makeText(context, "Playing next: ${song.title}", android.widget.Toast.LENGTH_SHORT).show()
                    showOptionsDialog = false
                },
                onAddToQueue = {
                    viewModel.addToQueue(song)
                    android.widget.Toast.makeText(context, "Added to queue: ${song.title}", android.widget.Toast.LENGTH_SHORT).show()
                    showOptionsDialog = false
                },
                onViewArtist = { artistName ->
                    showOptionsDialog = false
                    onClose()
                    onNavigateToArtist?.invoke(artistName)
                }
            )
        }

        // Up Next Queue Bottom Sheet
        if (showQueueDialog) {
            QueueSheet(
                currentSong = song,
                queue = upcomingQueue,
                onSongSelected = { viewModel.playSong(it, currentQueue) },
                onMoveItem = viewModel::moveUpcomingQueueItem,
                onDismiss = { showQueueDialog = false }
            )
        }

        // Adaptive Audio Effects & EQ Bottom Sheet
        if (showAudioEffectsSheet) {
            AudioEffectsSheet(
                viewModel = viewModel,
                onDismiss = { showAudioEffectsSheet = false }
            )
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

@Composable
fun PlayerProgressBarSection(
    viewModel: MainScreenViewModel,
    totalDuration: Long,
    modifier: Modifier = Modifier,
    activeColor: Color = Color(0xFF00F59B)
) {
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    var userSliderPosition by remember { mutableStateOf<Float?>(null) }
    val displayPos = userSliderPosition?.toLong() ?: currentPosition
    val currentProgress = if (totalDuration > 0) {
        (displayPos.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = currentProgress,
            onValueChange = { userSliderPosition = it * totalDuration.toFloat() },
            onValueChangeFinished = {
                userSliderPosition?.let { viewModel.seekTo(it.toLong()) }
                userSliderPosition = null
            },
            colors = SliderDefaults.colors(
                thumbColor = activeColor,
                activeTrackColor = activeColor,
                inactiveTrackColor = Color(0x33FFFFFF)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatMs(displayPos),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFA0A0A0)
            )
            Text(
                text = formatMs(totalDuration),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFA0A0A0)
            )
        }
    }
}

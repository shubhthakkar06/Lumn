package com.example.musicapp.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.musicapp.desktop.data.DesktopDownloadManager
import com.example.musicapp.desktop.data.SyncedLyricLine
import com.example.musicapp.desktop.model.DesktopSong

enum class FullScreenMode {
    ARTWORK,
    LYRICS,
    QUEUE
}

@Composable
fun DesktopFullScreenPlayer(
    song: DesktopSong,
    isPlaying: Boolean,
    progressMs: Long,
    volume: Float,
    isShuffle: Boolean,
    isRepeat: Boolean,
    isLiked: Boolean,
    themeAccent: Color,
    lyrics: List<SyncedLyricLine>,
    queue: List<DesktopSong>,
    downloadManager: DesktopDownloadManager,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleLike: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenDeviceSelector: () -> Unit,
    onShareSong: () -> Unit,
    onSongSelectedFromQueue: (DesktopSong) -> Unit,
    onClose: () -> Unit
) {
    var mode by remember { mutableStateOf(FullScreenMode.ARTWORK) }
    val durationSeconds = song.durationSeconds.coerceAtLeast(1L)
    val totalMs = durationSeconds * 1000L
    val progressFraction = (progressMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)

    val lyricsListState = rememberLazyListState()
    val activeLyricIndex = remember(progressMs, lyrics) {
        val idx = lyrics.indexOfLast { it.timestampMs <= progressMs }
        if (idx != -1) idx else 0
    }

    LaunchedEffect(activeLyricIndex, mode) {
        if (mode == FullScreenMode.LYRICS && lyrics.isNotEmpty() && activeLyricIndex in lyrics.indices) {
            lyricsListState.animateScrollToItem((activeLyricIndex - 3).coerceAtLeast(0))
        }
    }

    val isDownloaded = downloadManager.isDownloaded(song.id)
    val isDownloading = downloadManager.isDownloading(song.id)

    // Dynamic ambient background gradient
    val backgroundBrush = remember(song.id, themeAccent) {
        Brush.verticalGradient(
            listOf(
                Color(0xFF1B2822),
                Color(0xFF101614),
                Color(0xFF0B0D0C)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(horizontal = 48.dp, vertical = 32.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Navigation Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse Fullscreen",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (song.isEpisode) "PLAYING FROM PODCAST" else "PLAYING FROM YOUR LIBRARY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.Gray
                    )
                    Text(
                        text = song.album.ifBlank { "Rivara Music" },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mode Switcher Pills
                    listOf(
                        FullScreenMode.ARTWORK to "Player",
                        FullScreenMode.LYRICS to "Lyrics",
                        FullScreenMode.QUEUE to "Queue"
                    ).forEach { (m, label) ->
                        val isSel = mode == m
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSel) themeAccent else Color(0xFF262626))
                                .clickable { mode = m }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isSel) Color.Black else Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(onClick = onShareSong) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.FullscreenExit, contentDescription = "Exit Fullscreen", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Middle Body (Artwork Mode vs Lyrics Mode vs Queue Mode)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (mode) {
                    FullScreenMode.ARTWORK -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 40.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Large High-Res Artwork
                            Box(
                                modifier = Modifier
                                    .size(340.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFF242424))
                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                val localAsset = when {
                                    song.id == "baps_ep_aksharatitdas" || song.title.contains("અક્ષરાતીતદાસ") -> "assets/aksharatitdas_episode.png"
                                    song.id == "baps_ep_uttamcharan" || song.title.contains("ઉત્તમયોગીદાસ") -> "assets/uttamyogidas_episode.png"
                                    song.id == "baps_ep_atmatruptdas" || song.title.contains("આત્મતૃપ્તદાસ") || song.title.contains("Atmatruptdas") -> "assets/atmatruptdas_episode.png"
                                    song.id == "baps_ep_1" || song.title.contains("Somprakashdas") || song.title.contains("સોમપ્રકાશદાસ") -> "assets/somprakashdas_episode.png"
                                    song.albumArtUrl.startsWith("assets/") -> song.albumArtUrl
                                    else -> null
                                }

                                if (localAsset != null) {
                                    Image(
                                        painter = painterResource(localAsset),
                                        contentDescription = song.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else if (song.albumArtUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = song.albumArtUrl,
                                        contentDescription = song.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(80.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(60.dp))

                            // Track Details & Actions
                            Column(modifier = Modifier.width(420.dp)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 26.sp,
                                        lineHeight = 34.sp
                                    ),
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                                    color = Color(0xFFB3B3B3)
                                )

                                if (song.description.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = song.description,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 13.5.sp,
                                            lineHeight = 20.sp
                                        ),
                                        color = Color(0xFFAAAAAA),
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Like Button
                                    IconButton(
                                        onClick = onToggleLike,
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = "Like",
                                            tint = if (isLiked) themeAccent else Color.Gray,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    // Download Button
                                    IconButton(
                                        onClick = {
                                            if (isDownloaded) downloadManager.removeDownload(song.id)
                                            else downloadManager.downloadSong(song)
                                        },
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        if (isDownloading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                color = themeAccent,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (isDownloaded) Icons.Default.CheckCircle else Icons.Default.ArrowCircleDown,
                                                contentDescription = if (isDownloaded) "Downloaded" else "Download Offline",
                                                tint = if (isDownloaded) themeAccent else Color.Gray,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Quick Karaoke Lyrics Pill
                                    Button(
                                        onClick = { mode = FullScreenMode.LYRICS },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF262626)),
                                        shape = RoundedCornerShape(20.dp)
                                    ) {
                                        Icon(Icons.Default.Lyrics, contentDescription = null, tint = themeAccent, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Open Lyrics", color = Color.White, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }

                    FullScreenMode.LYRICS -> {
                        if (lyrics.isEmpty()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Synced lyrics not available for this track", color = Color.Gray, fontSize = 16.sp)
                            }
                        } else {
                            LazyColumn(
                                state = lyricsListState,
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(28.dp),
                                contentPadding = PaddingValues(vertical = 120.dp)
                            ) {
                                itemsIndexed(lyrics) { index, line ->
                                    val isActive = index == activeLyricIndex
                                    Text(
                                        text = line.text,
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                                            fontSize = if (isActive) 32.sp else 22.sp,
                                            lineHeight = if (isActive) 44.sp else 32.sp
                                        ),
                                        color = if (isActive) Color.White else Color(0x55FFFFFF),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onSeek(line.timestampMs) }
                                    )
                                }
                            }
                        }
                    }

                    FullScreenMode.QUEUE -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .fillMaxHeight()
                        ) {
                            Text("Upcoming Tracks", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            Spacer(modifier = Modifier.height(14.dp))
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(queue) { _, qSong ->
                                    val isCur = qSong.id == song.id
                                    Card(
                                        onClick = { onSongSelectedFromQueue(qSong) },
                                        colors = CardDefaults.cardColors(containerColor = if (isCur) themeAccent.copy(alpha = 0.2f) else Color(0xFF1E1E1E)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(qSong.title, color = if (isCur) themeAccent else Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                            Text(qSong.artist, color = Color.Gray, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. Transport Controls & Equalizer Scrubber Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Scrubber Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(progressMs / 1000L),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = Color.Gray,
                        modifier = Modifier.width(45.dp)
                    )

                    Slider(
                        value = progressFraction,
                        onValueChange = { frac ->
                            onSeek((frac * totalMs).toLong())
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = themeAccent,
                            inactiveTrackColor = Color(0xFF383838)
                        )
                    )

                    Text(
                        text = formatTime(durationSeconds),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = Color.Gray,
                        modifier = Modifier.width(45.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Transport Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Actions: Audio Device & Equalizer
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onOpenDeviceSelector,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF262626)),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = themeAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Output Device", color = Color.White, fontSize = 12.sp)
                        }

                        Button(
                            onClick = onOpenEqualizer,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF262626)),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(Icons.Default.Equalizer, contentDescription = null, tint = themeAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Equalizer", color = Color.White, fontSize = 12.sp)
                        }
                    }

                    // Center: Main Playback Controls
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onToggleShuffle, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = if (isShuffle) themeAccent else Color.Gray, modifier = Modifier.size(22.dp))
                        }
                        IconButton(onClick = onSkipPrev, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(32.dp))
                        }

                        // Big Play/Pause Button
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable(onClick = onTogglePlayPause),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        IconButton(onClick = onSkipNext, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        IconButton(onClick = onToggleRepeat, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Repeat, contentDescription = "Repeat", tint = if (isRepeat) themeAccent else Color.Gray, modifier = Modifier.size(22.dp))
                        }
                    }

                    // Right: Volume Slider
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.width(180.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                        Slider(
                            value = volume,
                            onValueChange = onVolumeChange,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = themeAccent,
                                inactiveTrackColor = Color(0xFF383838)
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

package com.example.musicapp.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.musicapp.desktop.data.SyncedLyricLine
import com.example.musicapp.desktop.model.DesktopSong

@Composable
fun DesktopLyricsPanel(
    currentSong: DesktopSong?,
    lyrics: List<SyncedLyricLine>,
    progressMs: Long,
    isQueueMode: Boolean,
    queue: List<DesktopSong>,
    onClose: () -> Unit,
    onSongSelected: (DesktopSong) -> Unit,
    onSeek: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val lyricsListState = rememberLazyListState()

    // Determine the active lyric index based on current playback progress
    val activeIndex = remember(progressMs, lyrics) {
        val idx = lyrics.indexOfLast { it.timestampMs <= progressMs }
        if (idx != -1) idx else 0
    }

    // Auto-scroll smoothly to the active lyric line
    LaunchedEffect(activeIndex) {
        if (lyrics.isNotEmpty() && activeIndex in lyrics.indices) {
            lyricsListState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
        }
    }

    // Genre-aware font style: Italian/Romantic serif for romantic/acoustic songs, bold sans for pop/rock
    val isRomantic = remember(currentSong) {
        val text = "${currentSong?.title} ${currentSong?.artist}".lowercase()
        text.contains("tum hi ho") || text.contains("dil") || text.contains("love") ||
            text.contains("romantic") || text.contains("lover") || text.contains("acoustic") ||
            text.contains("lo-fi") || text.contains("sufi")
    }

    val lyricFontFamily = if (isRomantic) FontFamily.Serif else FontFamily.SansSerif
    val lyricFontStyle = if (isRomantic) FontStyle.Italic else FontStyle.Normal

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(360.dp),
        color = Color(0xEB141414) // Translucent background as requested
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // Header: Title and Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isQueueMode) "Up Next (${queue.size})" else "Live Lyrics",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )

                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isQueueMode) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(queue, key = { index, song -> "${song.id}_$index" }) { _, song ->
                        val isCurrent = song.id == currentSong?.id
                        Card(
                            onClick = { onSongSelected(song) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrent) Color(0xFF1B3D23) else Color(0xFF1E1E1E)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF282828)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (song.albumArtUrl.isNotBlank()) {
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
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (isCurrent) Color(0xFF1ED760) else Color.White,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = song.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                if (lyrics.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Lyrics not available for this track",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = lyricsListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        contentPadding = PaddingValues(vertical = 40.dp)
                    ) {
                        itemsIndexed(lyrics) { index, line ->
                            val isActive = index == activeIndex
                            Text(
                                text = line.text,
                                fontFamily = lyricFontFamily,
                                fontStyle = lyricFontStyle,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                                    fontSize = if (isActive) 22.sp else 16.sp,
                                    lineHeight = if (isActive) 30.sp else 24.sp
                                ),
                                color = if (isActive) Color(0xFF1ED760) else Color(0x88FFFFFF),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isActive) Color(0x331ED760) else Color.Transparent)
                                    .clickable { onSeek?.invoke(line.timestampMs) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

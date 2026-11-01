package com.example.musicapp.wear.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.*
import com.example.musicapp.wear.model.WearSongItem
import com.example.musicapp.wear.theme.WearPrimary

data class WearPlaylist(
    val id: String,
    val name: String,
    val songs: List<WearSongItem>
)

@Composable
fun WearPlaylistsScreen(
    onSongSelected: (WearSongItem, List<WearSongItem>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedPlaylist by remember { mutableStateOf<WearPlaylist?>(null) }
    val downloadedPlaylistIds = remember { mutableStateMapOf<String, Boolean>("pl_chill" to true) }

    val playlists = remember {
        listOf(
            WearPlaylist(
                id = "pl_chill",
                name = "Chill Mix",
                songs = listOf(
                    WearSongItem("1", "Yellow", "Coldplay", 269L),
                    WearSongItem("4", "Kesariya", "Arijit Singh", 268L),
                    WearSongItem("5", "Tum Hi Ho", "Arijit Singh", 262L),
                    WearSongItem("15", "O Rangrez", "Aamir Mir", 220L)
                )
            ),
            WearPlaylist(
                id = "pl_hits",
                name = "Top Hits",
                songs = listOf(
                    WearSongItem("2", "Starboy", "The Weeknd", 230L),
                    WearSongItem("3", "Levitating", "Dua Lipa", 203L),
                    WearSongItem("6", "Blinding Lights", "The Weeknd", 200L),
                    WearSongItem("7", "Shape of You", "Ed Sheeran", 233L)
                )
            ),
            WearPlaylist(
                id = "pl_workout",
                name = "Workout Beats",
                songs = listOf(
                    WearSongItem("8", "Believer", "Imagine Dragons", 204L),
                    WearSongItem("13", "Closer", "The Chainsmokers", 245L),
                    WearSongItem("2", "Starboy", "The Weeknd", 230L),
                    WearSongItem("3", "Levitating", "Dua Lipa", 203L)
                )
            ),
            WearPlaylist(
                id = "pl_liked",
                name = "Liked Songs",
                songs = listOf(
                    WearSongItem("1", "Yellow", "Coldplay", 269L),
                    WearSongItem("9", "Apna Bana Le", "Arijit Singh", 261L),
                    WearSongItem("10", "Channa Mereya", "Arijit Singh", 289L),
                    WearSongItem("14", "As It Was", "Harry Styles", 167L)
                )
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        if (selectedPlaylist == null) {
            // Playlists List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Your Playlists",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                items(playlists, key = { it.id }) { pl ->
                    val isDownloaded = downloadedPlaylistIds[pl.id] == true

                    Card(
                        onClick = { selectedPlaylist = pl },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF181818)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(WearPrimary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QueueMusic,
                                    contentDescription = null,
                                    tint = WearPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pl.name,
                                    color = Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${pl.songs.size} tracks" + if (isDownloaded) " • Offline" else "",
                                    color = if (isDownloaded) WearPrimary else Color.Gray,
                                    fontSize = 10.sp
                                )
                            }
                            IconButton(
                                onClick = {
                                    downloadedPlaylistIds[pl.id] = !(downloadedPlaylistIds[pl.id] ?: false)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (isDownloaded) Icons.Default.CheckCircle else Icons.Default.ArrowDownward,
                                    contentDescription = "Download",
                                    tint = if (isDownloaded) WearPrimary else Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }
        } else {
            // Selected Playlist Detail & Tracks View
            val pl = selectedPlaylist!!
            val isDownloaded = downloadedPlaylistIds[pl.id] == true

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { selectedPlaylist = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = pl.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                downloadedPlaylistIds[pl.id] = !(downloadedPlaylistIds[pl.id] ?: false)
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (isDownloaded) Icons.Default.CheckCircle else Icons.Default.ArrowDownward,
                                contentDescription = "Download",
                                tint = if (isDownloaded) WearPrimary else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                items(pl.songs, key = { it.id }) { song ->
                    Card(
                        onClick = { onSongSelected(song, pl.songs) },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF181818)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(WearPrimary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = WearPrimary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
                                    color = Color.Gray,
                                    fontSize = 9.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = WearPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }
        }
    }
}

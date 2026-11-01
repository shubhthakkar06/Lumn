package com.example.musicapp.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.musicapp.desktop.model.DesktopPlaylist
import com.example.musicapp.desktop.model.DesktopSong

@Composable
fun DesktopEpisodeCard(
    episode: DesktopSong,
    isPlayingThis: Boolean,
    isLiked: Boolean,
    playlists: List<DesktopPlaylist>,
    onPlay: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleLike: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showPlaylistSubmenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlayingThis) Color(0xFF1B2E21) else Color(0xFF181C19)
        ),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isPlayingThis) Color(0xFF1ED760).copy(alpha = 0.5f) else Color(0xFF263329),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onPlay)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: High quality Episode Artwork
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(96.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF222622)),
                contentAlignment = Alignment.Center
            ) {
                if (episode.albumArtUrl.isNotBlank()) {
                    AsyncImage(
                        model = episode.albumArtUrl,
                        contentDescription = episode.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Podcasts,
                        contentDescription = null,
                        tint = Color(0xFFE88A34),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Middle: Episode Details
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = if (isPlayingThis) Color(0xFF1ED760) else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = episode.album.ifBlank { "Episode • ${episode.artist}" },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = Color(0xFFAAAAAA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                val desc = episode.description.ifBlank {
                    "Pujya Somprakashdas Swami delivers an inspiring discourse from BAPS Satsang."
                }
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
                    color = Color(0xFF889988),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom row: Release date and duration
                val minutes = episode.durationSeconds / 60
                val seconds = episode.durationSeconds % 60
                val durationStr = if (minutes >= 60) {
                    val hrs = minutes / 60
                    val remMins = minutes % 60
                    "$hrs hr $remMins min"
                } else {
                    "$minutes min $seconds sec"
                }
                val dateStr = episode.releaseDate.ifBlank { "Oct 12" }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$dateStr • $durationStr",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = Color(0xFF888888)
                    )
                }
            }

            // Right: Like Icon + Big Circular Play Button + 3-dots
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Like / Checkmark Icon
                IconButton(
                    onClick = onToggleLike,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.CheckCircle else Icons.Default.FavoriteBorder,
                        contentDescription = "Save episode",
                        tint = if (isLiked) Color(0xFF1ED760) else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Play / Pause Circle Button (just like Spotify episode card)
                Button(
                    onClick = {
                        if (isPlayingThis) onTogglePlayPause() else onPlay()
                    },
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = if (isPlayingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlayingThis) "Pause" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 3-dots Menu
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Episode options",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = {
                            showMenu = false
                            showPlaylistSubmenu = false
                        },
                        modifier = Modifier.background(Color(0xFF242424))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Play Next", color = Color.White) },
                            leadingIcon = {
                                Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color(0xFF1ED760))
                            },
                            onClick = {
                                showMenu = false
                                onPlayNext()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add to Queue", color = Color.White) },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = Color.White)
                            },
                            onClick = {
                                showMenu = false
                                onAddToQueue()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isLiked) "Remove from Library" else "Save to Library", color = Color.White) },
                            leadingIcon = {
                                Icon(if (isLiked) Icons.Default.CheckCircle else Icons.Default.FavoriteBorder, contentDescription = null, tint = Color(0xFF1ED760))
                            },
                            onClick = {
                                showMenu = false
                                onToggleLike()
                            }
                        )
                        HorizontalDivider(color = Color(0xFF383838))
                        DropdownMenuItem(
                            text = { Text("Add to Playlist...", color = Color.White) },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = Color.White)
                            },
                            onClick = {
                                showPlaylistSubmenu = true
                            }
                        )

                        if (showPlaylistSubmenu) {
                            playlists.forEach { pl ->
                                DropdownMenuItem(
                                    text = { Text("   ↳ ${pl.name}", color = Color(0xFF1ED760), fontSize = 13.sp) },
                                    onClick = {
                                        showMenu = false
                                        showPlaylistSubmenu = false
                                        onAddToPlaylist(pl.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

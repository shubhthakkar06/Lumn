package com.example.musicapp.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.musicapp.desktop.model.DesktopPlaylist
import com.example.musicapp.desktop.model.DesktopSong

@Composable
fun DesktopSongRow(
    index: Int,
    song: DesktopSong,
    isCurrent: Boolean,
    isLiked: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    themeAccent: Color,
    playlists: List<DesktopPlaylist>,
    onPlay: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleDownload: () -> Unit,
    onShareSong: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showPlaylistSubmenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isCurrent) themeAccent.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onPlay)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track number or playing equalizer indicator
        if (isCurrent) {
            Icon(
                imageVector = Icons.Default.Equalizer,
                contentDescription = "Playing",
                tint = themeAccent,
                modifier = Modifier.width(32.dp).size(18.dp)
            )
        } else {
            Text(
                text = "${index + 1}",
                color = Color.Gray,
                modifier = Modifier.width(32.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // Thumbnail Album Art
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF282828)),
            contentAlignment = Alignment.Center
        ) {
            val localAsset = when {
                song.id == "baps_ep_aksharatitdas" -> "assets/aksharatitdas_episode.png"
                song.id == "baps_ep_uttamcharan" -> "assets/uttamyogidas_episode.png"
                song.id == "baps_ep_atmatruptdas" -> "assets/atmatruptdas_episode.png"
                song.id == "baps_ep_1" -> "assets/somprakashdas_episode.png"
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
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Title and Subtitle
        Column(modifier = Modifier.weight(2f)) {
            Text(
                text = song.title,
                color = if (isCurrent) themeAccent else Color.White,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (song.album.isNotBlank()) {
                Text(
                    text = song.album,
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Artist Name
        Text(
            text = song.artist,
            color = Color(0xFFAAAAAA),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.5f)
        )

        // Download Indicator
        if (isDownloading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = themeAccent, strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(10.dp))
        } else if (isDownloaded) {
            Icon(Icons.Default.CheckCircle, contentDescription = "Downloaded", tint = themeAccent, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
        }

        // Like / Favorite Icon
        IconButton(
            onClick = onToggleLike,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Save track",
                tint = if (isLiked) themeAccent else Color.Gray,
                modifier = Modifier.size(18.dp)
            )
        }

        // Duration text
        val m = song.durationSeconds / 60
        val s = song.durationSeconds % 60
        Text(
            text = "%d:%02d".format(m, s),
            color = Color.Gray,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(44.dp)
        )

        // 3-dots Menu Button
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = "More",
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
                        Icon(Icons.Default.SkipNext, contentDescription = null, tint = themeAccent)
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
                    text = { Text(if (isDownloaded) "Remove Download" else "Download Offline", color = Color.White) },
                    leadingIcon = {
                        Icon(Icons.Default.ArrowCircleDown, contentDescription = null, tint = themeAccent)
                    },
                    onClick = {
                        showMenu = false
                        onToggleDownload()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Share Track", color = Color.White) },
                    leadingIcon = {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
                    },
                    onClick = {
                        showMenu = false
                        onShareSong()
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
                            text = { Text("   ↳ ${pl.name}", color = themeAccent, fontSize = 13.sp) },
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

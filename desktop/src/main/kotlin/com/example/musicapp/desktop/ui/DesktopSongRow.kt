package com.example.musicapp.desktop.ui

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
    playlists: List<DesktopPlaylist>,
    onPlay: () -> Unit,
    onToggleLike: () -> Unit,
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
            .background(if (isCurrent) Color(0xFF1F3827) else Color.Transparent)
            .clickable(onClick = onPlay)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track number or playing equalizer indicator
        if (isCurrent) {
            Icon(
                imageVector = Icons.Default.Equalizer,
                contentDescription = "Playing",
                tint = Color(0xFF1ED760),
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
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Title and Album
        Column(modifier = Modifier.weight(2f)) {
            Text(
                text = song.title,
                color = if (isCurrent) Color(0xFF1ED760) else Color.White,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 15.sp
            )
            Text(
                text = song.album.ifBlank { song.artist },
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp
            )
        }

        // Artist
        Text(
            text = song.artist,
            color = Color(0xFFAAAAAA),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.5f),
            fontSize = 14.sp
        )

        // Duration
        val m = song.durationSeconds / 60
        val s = song.durationSeconds % 60
        Text(
            text = "%d:%02d".format(m, s),
            color = Color.Gray,
            modifier = Modifier.width(60.dp),
            style = MaterialTheme.typography.bodySmall
        )

        // Like button
        IconButton(
            onClick = onToggleLike,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Like",
                tint = if (isLiked) Color(0xFF1ED760) else Color.Gray,
                modifier = Modifier.size(18.dp)
            )
        }

        // 3-dots Menu Button
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = Color.Gray,
                    modifier = Modifier.size(18.dp)
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
                    leadingIcon = { Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color(0xFF1ED760)) },
                    onClick = {
                        showMenu = false
                        onPlayNext()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to Queue", color = Color.White) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = Color.White) },
                    onClick = {
                        showMenu = false
                        onAddToQueue()
                    }
                )
                DropdownMenuItem(
                    text = { Text(if (isLiked) "Remove from Liked" else "Add to Liked Songs", color = Color.White) },
                    leadingIcon = { Icon(if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null, tint = Color(0xFF1ED760)) },
                    onClick = {
                        showMenu = false
                        onToggleLike()
                    }
                )
                HorizontalDivider(color = Color(0xFF383838))
                DropdownMenuItem(
                    text = { Text("Add to Playlist...", color = Color.White) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = Color.White) },
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

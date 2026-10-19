package com.example.musicapp.ui.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.playlist.PlaylistManager
import com.example.musicapp.data.auth.UserManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerOptionsSheet(
    song: SongInfo,
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit = {},
    onViewArtist: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val playlistManager = remember { PlaylistManager.getInstance(context) }
    val userManager = remember { UserManager.getInstance(context) }
    val currentUser by userManager.currentUser.collectAsStateWithLifecycle()
    val playlists by playlistManager.playlists.collectAsStateWithLifecycle()
    var isLiked by remember(song.videoId) { mutableStateOf(playlistManager.isLiked(song.videoId)) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showCreditsDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF282828),
        contentColor = Color.White,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF555555))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Song Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = song.albumArtUrl,
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFA0A0A0),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = {
                        playlistManager.toggleLike(song)
                        isLiked = !isLiked
                    }
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) Color(0xFF1ED760) else Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFF383838), thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // Action Items
            OptionItem(
                icon = Icons.Default.PlaylistAdd,
                title = "Add to playlist",
                onClick = {
                    if (currentUser.isGuest) {
                        Toast.makeText(context, "Sign in to create and edit playlists", Toast.LENGTH_SHORT).show()
                    } else {
                        showPlaylistPicker = true
                    }
                }
            )

            OptionItem(
                icon = Icons.Default.Person,
                title = "View artist",
                onClick = {
                    onDismiss()
                    onViewArtist(song.artist)
                }
            )

            OptionItem(
                icon = Icons.Default.Album,
                title = "View album",
                onClick = {
                    Toast.makeText(context, "Album: ${song.title} Single", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            )

            OptionItem(
                icon = Icons.Default.Share,
                title = "Share",
                onClick = {
                    val link = Uri.Builder().scheme("lumn").authority("song").appendPath(song.videoId)
                        .appendQueryParameter("title", song.title)
                        .appendQueryParameter("artist", song.artist)
                        .appendQueryParameter("art", song.albumArtUrl)
                        .appendQueryParameter("album", song.albumName)
                        .appendQueryParameter("duration", song.durationSeconds.toString())
                        .build()
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, song.title)
                        putExtra(Intent.EXTRA_TEXT, "Listen to \"${song.title}\" by ${song.artist} on Lumn:\n$link")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share song via"))
                    onDismiss()
                }
            )

            OptionItem(
                icon = Icons.Default.Timer,
                title = "Sleep timer",
                onClick = { showSleepTimerDialog = true }
            )

            OptionItem(
                icon = Icons.Default.Info,
                title = "Show credits",
                onClick = { showCreditsDialog = true }
            )
        }
    }

    if (showPlaylistPicker) {
        AlertDialog(
            onDismissRequest = { showPlaylistPicker = false },
            containerColor = Color(0xFF202A36),
            title = { Text("Add to playlist", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                if (playlists.isEmpty()) {
                    Text("You don’t have any playlists yet. Create one in Your Library, then add this song.", color = Color(0xFFCBD5E1))
                } else {
                    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                        playlists.forEach { playlist ->
                            TextButton(
                                onClick = {
                                    playlistManager.addSongToPlaylist(playlist.id, song)
                                    Toast.makeText(context, "Added to ${playlist.name}", Toast.LENGTH_SHORT).show()
                                    showPlaylistPicker = false
                                    onDismiss()
                                    onAddToPlaylist()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(playlist.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${playlist.songs.size} songs", color = Color(0xFF9AA8B8), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistPicker = false }) { Text("Done", color = Color(0xFF00F59B)) }
            }
        )
    }

    if (showCreditsDialog) {
        AlertDialog(
            onDismissRequest = { showCreditsDialog = false },
            containerColor = Color(0xFF282828),
            title = {
                Text(
                    text = "Song Credits",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(text = song.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    CreditRow(label = "Performed by", value = song.artist)
                    CreditRow(label = "Written by", value = song.artist.split(",").firstOrNull() ?: song.artist)
                    CreditRow(label = "Produced by", value = "High-Fidelity Studio")
                    CreditRow(label = "Source", value = "YouTube Music / Saavn Catalog")
                }
            },
            confirmButton = {
                TextButton(onClick = { showCreditsDialog = false }) {
                    Text("Close", color = Color(0xFF1ED760), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showSleepTimerDialog) {
        val timerOptions = listOf("5 minutes", "15 minutes", "30 minutes", "45 minutes", "1 hour", "End of track")
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            containerColor = Color(0xFF282828),
            title = {
                Text(
                    text = "Stop audio in",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    timerOptions.forEach { opt ->
                        Text(
                            text = opt,
                            color = Color.White,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Toast.makeText(context, "Sleep timer set for $opt", Toast.LENGTH_SHORT).show()
                                    showSleepTimerDialog = false
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSleepTimerDialog = false }) {
                    Text("Cancel", color = Color(0xFFA0A0A0))
                }
            }
        )
    }
}

@Composable
private fun OptionItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = Color.White
        )
    }
}

@Composable
private fun CreditRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, color = Color(0xFFA0A0A0), fontSize = 12.sp)
        Text(text = value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

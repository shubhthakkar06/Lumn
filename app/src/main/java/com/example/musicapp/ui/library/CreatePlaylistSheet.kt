package com.example.musicapp.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlaylistSheet(
    onCreatePlaylist: () -> Unit,
    onImportSpotifyPlaylist: () -> Unit,
    onCreateBlend: () -> Unit,
    onDismiss: () -> Unit
) {
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
            Text(
                text = "Create",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            CreateOptionItem(
                icon = Icons.Default.MusicNote,
                title = "Playlist",
                subtitle = "Build a playlist with your favorite songs",
                onClick = {
                    onDismiss()
                    onCreatePlaylist()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            CreateOptionItem(
                icon = Icons.Default.MusicNote,
                title = "Import Spotify playlist",
                subtitle = "Bring a playlist you own into Lumn",
                onClick = {
                    onDismiss()
                    onImportSpotifyPlaylist()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            CreateOptionItem(
                icon = Icons.Default.Group,
                title = "Blend",
                subtitle = "Combine tastes in a shared playlist with friends",
                onClick = {
                    onDismiss()
                    onCreateBlend()
                }
            )
        }
    }
}

@Composable
private fun CreateOptionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF383838)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFA0A0A0)
            )
        }
    }
}

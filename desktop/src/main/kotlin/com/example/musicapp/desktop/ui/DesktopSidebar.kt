package com.example.musicapp.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicapp.desktop.model.DesktopPlaylist

@Composable
fun DesktopSidebar(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    playlists: List<DesktopPlaylist>,
    themeAccent: Color,
    onCreatePlaylist: (String) -> Unit,
    onOpenImportPlaylist: () -> Unit,
    onOpenSoundCapsule: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenSettings: () -> Unit,
    onDialogStateChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    LaunchedEffect(showCreateDialog) {
        onDialogStateChanged(showCreateDialog)
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Playlist", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("My Playlist #1", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = themeAccent,
                        unfocusedBorderColor = Color(0xFF3E3E3E),
                        focusedContainerColor = Color(0xFF1E1E1E),
                        unfocusedContainerColor = Color(0xFF1E1E1E)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            onCreatePlaylist(newPlaylistName)
                            newPlaylistName = ""
                            showCreateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = themeAccent,
                        contentColor = Color.Black
                    )
                ) {
                    Text("Create", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF242424),
            shape = RoundedCornerShape(16.dp)
        )
    }

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(240.dp),
        color = Color(0xFF121212)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Brand Logo & Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 24.dp, start = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(themeAccent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Rivara",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Rivara",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = Color.White
                    )
                }

                // Primary Navigation Items
                SidebarItem(
                    icon = Icons.Default.Home,
                    label = "Home",
                    isSelected = selectedTab == "Home",
                    themeAccent = themeAccent,
                    onClick = { onTabSelected("Home") }
                )
                SidebarItem(
                    icon = Icons.Default.Search,
                    label = "Search",
                    isSelected = selectedTab == "Search",
                    themeAccent = themeAccent,
                    onClick = { onTabSelected("Search") }
                )
                SidebarItem(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    label = "Your Library",
                    isSelected = selectedTab == "Library",
                    themeAccent = themeAccent,
                    onClick = { onTabSelected("Library") }
                )
                SidebarItem(
                    icon = Icons.Default.Favorite,
                    label = "Liked Songs",
                    isSelected = selectedTab == "Liked",
                    themeAccent = themeAccent,
                    onClick = { onTabSelected("Liked") }
                )
                SidebarItem(
                    icon = Icons.Default.ArrowCircleDown,
                    label = "Downloaded",
                    isSelected = selectedTab == "Downloaded",
                    themeAccent = themeAccent,
                    onClick = { onTabSelected("Downloaded") }
                )
                SidebarItem(
                    icon = Icons.Default.AutoAwesome,
                    label = "Sound Capsule",
                    isSelected = selectedTab == "SoundCapsule",
                    themeAccent = themeAccent,
                    onClick = onOpenSoundCapsule
                )
                SidebarItem(
                    icon = Icons.Default.Sync,
                    label = "Sync Devices",
                    isSelected = false,
                    themeAccent = themeAccent,
                    onClick = onOpenSync
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF282828), thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // Playlists Header with Import & Create buttons
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PLAYLISTS",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                        color = Color.Gray
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onOpenImportPlaylist,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = "Import Playlist",
                                tint = Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { showCreateDialog = true },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Create Playlist",
                                tint = Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Playlist list
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        val isSelected = selectedTab == "Playlist_${playlist.id}"
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isSelected) themeAccent else Color(0xFFCCCCCC),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) Color(0xFF242424) else Color.Transparent)
                                .clickable { onTabSelected("Playlist_${playlist.id}") }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Bottom Settings Item
            Column {
                HorizontalDivider(color = Color(0xFF282828), thickness = 1.dp)
                Spacer(modifier = Modifier.height(8.dp))
                SidebarItem(
                    icon = Icons.Default.Settings,
                    label = "Settings",
                    isSelected = false,
                    themeAccent = themeAccent,
                    onClick = onOpenSettings
                )
            }
        }
    }
}

@Composable
private fun SidebarItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    themeAccent: Color,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) Color(0xFF282828) else Color.Transparent
    val contentColor = if (isSelected) themeAccent else Color(0xFFB3B3B3)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            color = if (isSelected) Color.White else contentColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}

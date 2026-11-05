package com.example.musicapp.desktop.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicapp.desktop.data.DesktopUser
import com.example.musicapp.desktop.model.DesktopPlaylist

@Composable
fun DesktopSidebar(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    playlists: List<DesktopPlaylist>,
    themeAccent: Color,
    currentUser: DesktopUser? = null,
    onOpenAuth: () -> Unit = {},
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
            containerColor = Color(0xFF1E2430),
            shape = RoundedCornerShape(16.dp)
        )
    }

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(250.dp)
            .drawBehind {
                drawLine(
                    color = Color(0x1AFFFFFF),
                    start = Offset(size.width, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1f
                )
            },
        color = Color(0xFF0B0F17)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0F1522), Color(0xFF090D14), Color(0xFF080B10))
                    )
                )
                .padding(horizontal = 14.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Official Phone Logo & Aesthetic Brand Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Image(
                        painter = painterResource("assets/rivara_launcher_icon.png"),
                        contentDescription = "Rivara Logo",
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "RIVARA",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 2.5.sp
                                ),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(themeAccent.copy(alpha = 0.2f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text("MAC", fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold, color = themeAccent)
                            }
                        }
                        Text(
                            text = "STUDIO AUDIO",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.2.sp,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFF7A889B)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // DISCOVER SECTION
                SectionHeader("DISCOVER")
                Spacer(modifier = Modifier.height(4.dp))

                SidebarPillItem(
                    icon = Icons.Default.Home,
                    label = "Home",
                    isSelected = selectedTab == "Home",
                    themeAccent = themeAccent,
                    onClick = { onTabSelected("Home") }
                )
                SidebarPillItem(
                    icon = Icons.Default.Search,
                    label = "Search",
                    isSelected = selectedTab == "Search",
                    themeAccent = themeAccent,
                    onClick = { onTabSelected("Search") }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // COLLECTION SECTION
                SectionHeader("YOUR COLLECTION")
                Spacer(modifier = Modifier.height(4.dp))

                SidebarPillItem(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    label = "Your Library",
                    isSelected = selectedTab == "Library",
                    themeAccent = themeAccent,
                    onClick = { onTabSelected("Library") }
                )
                SidebarPillItem(
                    icon = Icons.Default.Favorite,
                    label = "Liked Songs",
                    isSelected = selectedTab == "Liked",
                    themeAccent = themeAccent,
                    onClick = { onTabSelected("Liked") }
                )
                SidebarPillItem(
                    icon = Icons.Default.ArrowCircleDown,
                    label = "Downloaded",
                    isSelected = selectedTab == "Downloaded",
                    themeAccent = themeAccent,
                    onClick = { onTabSelected("Downloaded") }
                )
                SidebarPillItem(
                    icon = Icons.Default.AutoAwesome,
                    label = "Sound Capsule",
                    isSelected = selectedTab == "SoundCapsule",
                    themeAccent = themeAccent,
                    onClick = onOpenSoundCapsule
                )
                SidebarPillItem(
                    icon = Icons.Default.Sync,
                    label = "Cross-Device Sync",
                    isSelected = false,
                    themeAccent = themeAccent,
                    hasPulseBadge = true,
                    onClick = onOpenSync
                )

                Spacer(modifier = Modifier.height(14.dp))

                // PLAYLISTS SECTION
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader("PLAYLISTS")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onOpenImportPlaylist,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = "Import Playlist",
                                tint = Color(0xFF8895A7),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { showCreateDialog = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Create Playlist",
                                tint = Color(0xFF8895A7),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Scrollable Playlist list
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        val isSelected = selectedTab == "Playlist_${playlist.id}"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) themeAccent.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable { onTabSelected("Playlist_${playlist.id}") }
                                .padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) themeAccent else Color(0xFF1B2230)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.Black else Color(0xFF8C9BB0),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                ),
                                color = if (isSelected) Color.White else Color(0xFFBDC7D4),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (playlist.songCount > 0) {
                                Text(
                                    text = "${playlist.songCount}",
                                    color = Color(0xFF6B788A),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Profile / Settings Card
            Column {
                HorizontalDivider(color = Color(0x1AFFFFFF), thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF121824))
                        .clickable(onClick = onOpenAuth)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(themeAccent, Color(0xFF4285F4))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentUser?.avatarLetter ?: "G",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentUser?.name ?: "Sign In",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1ED760))
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Cloud Synced",
                                color = Color(0xFF8E9BB0),
                                fontSize = 10.5.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color(0xFF8895A7),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(
            letterSpacing = 1.4.sp,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        ),
        color = Color(0xFF6B7B92),
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun SidebarPillItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    themeAccent: Color,
    hasPulseBadge: Boolean = false,
    onClick: () -> Unit
) {
    val bgModifier = if (isSelected) {
        Modifier
            .background(
                Brush.horizontalGradient(
                    listOf(themeAccent.copy(alpha = 0.22f), themeAccent.copy(alpha = 0.05f))
                )
            )
            .border(0.75.dp, themeAccent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
    } else {
        Modifier.background(Color.Transparent)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(bgModifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .clip(CircleShape)
                    .background(themeAccent)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) themeAccent else Color(0xFF9AA7B8),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            color = if (isSelected) Color.White else Color(0xFFC0CAD6),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.5.sp,
            modifier = Modifier.weight(1f)
        )

        if (hasPulseBadge) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1ED760))
            )
        }
    }
}

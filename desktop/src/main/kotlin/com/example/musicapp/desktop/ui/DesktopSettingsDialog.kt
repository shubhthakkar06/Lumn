package com.example.musicapp.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.musicapp.desktop.data.DesktopDownloadManager
import com.example.musicapp.desktop.data.DesktopUser
import com.example.musicapp.desktop.sync.DesktopSyncService
import com.example.musicapp.desktop.theme.DesktopAppTheme
import com.example.musicapp.desktop.theme.DesktopThemeManager

@Composable
fun DesktopSettingsDialog(
    themeAccent: Color,
    currentUser: DesktopUser?,
    downloadManager: DesktopDownloadManager,
    onOpenEqualizer: () -> Unit,
    onOpenDeviceSelector: () -> Unit,
    onOpenSoundCapsule: () -> Unit,
    onOpenAuth: () -> Unit,
    onOpenSync: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val currentTheme by DesktopThemeManager.currentTheme.collectAsState()
    val isOfflineOnly by downloadManager.isOfflineOnly.collectAsState()
    var streamQuality by remember { mutableStateOf("320 kbps (High Quality)") }
    var cacheClearedToast by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(540.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E1E1E),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(themeAccent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = themeAccent, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Settings", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            Text("Themes, Audio Quality & Device Controls", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Profile & Cloud Sync Card
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF252528)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(themeAccent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(currentUser?.avatarLetter ?: "U", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(currentUser?.name ?: "Guest Listener", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            val isGoogle = currentUser?.email?.isNotBlank() == true
                            Text(
                                text = if (isGoogle) "${currentUser?.email} • Google Connected" else "Guest mode • Local only",
                                color = if (isGoogle) Color(0xFF1ED760) else Color.Gray,
                                fontSize = 12.sp
                            )
                            if (isGoogle) {
                                Text(
                                    text = "Cross-Device Sync Active on port ${DesktopSyncService.SYNC_PORT}",
                                    color = Color(0xFF4285F4),
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Button(
                                onClick = {
                                    onDismiss()
                                    onOpenSync()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = themeAccent.copy(alpha = 0.2f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, themeAccent),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, tint = themeAccent, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sync Hub", color = themeAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            TextButton(
                                onClick = {
                                    onDismiss()
                                    onOpenAuth()
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Accounts", color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 1. Theme Selection
                Text("APP ACCENT THEME", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DesktopAppTheme.entries.forEach { theme ->
                        val isSelected = theme == currentTheme
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF282828))
                                .border(
                                    2.dp,
                                    if (isSelected) theme.primaryColor else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { DesktopThemeManager.setTheme(theme) },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(theme.primaryColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = theme.displayName.split(" ").last(),
                                    color = if (isSelected) Color.White else Color.Gray,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 2. Audio Streaming Quality
                Text("STREAMING QUALITY", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf("320 kbps (High Quality)", "160 kbps (Normal)", "96 kbps (Data Saver)").forEach { q ->
                        val isSel = q == streamQuality
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) themeAccent.copy(alpha = 0.15f) else Color(0xFF252525))
                                .border(1.dp, if (isSel) themeAccent else Color(0xFF333333), RoundedCornerShape(10.dp))
                                .clickable { streamQuality = q }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = q.split(" ").first(),
                                color = if (isSel) themeAccent else Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 3. Audio Equalizer & Output Device Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            onDismiss()
                            onOpenEqualizer()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Equalizer, contentDescription = null, tint = themeAccent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Equalizer", color = Color.White, fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            onDismiss()
                            onOpenDeviceSelector()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = themeAccent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Output Device", color = Color.White, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 4. Offline Mode Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF252525))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Offline Mode Only", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Only play songs downloaded to this Mac", color = Color.Gray, fontSize = 12.sp)
                    }
                    Switch(
                        checked = isOfflineOnly,
                        onCheckedChange = { downloadManager.toggleOfflineOnly() },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = themeAccent)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 5. Cache Clear & Account Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        downloadManager.clearAllDownloads()
                        cacheClearedToast = true
                    }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (cacheClearedToast) "Cache Cleared!" else "Clear Audio Cache", color = if (cacheClearedToast) themeAccent else Color(0xFFFF5252), fontSize = 12.5.sp)
                    }

                    TextButton(onClick = {
                        onDismiss()
                        onOpenAuth()
                    }) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = themeAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(currentUser?.name ?: "Switch Account", color = Color.White, fontSize = 12.5.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = themeAccent, contentColor = Color.Black),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

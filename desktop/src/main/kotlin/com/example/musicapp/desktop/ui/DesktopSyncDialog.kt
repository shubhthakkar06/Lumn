package com.example.musicapp.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.musicapp.desktop.data.DesktopLibraryManager
import com.example.musicapp.desktop.data.DesktopUserManager
import com.example.musicapp.desktop.sync.DesktopSyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DesktopSyncDialog(
    libraryManager: DesktopLibraryManager,
    userManager: DesktopUserManager,
    themeAccent: Color,
    onOpenAuth: () -> Unit,
    onDismiss: () -> Unit
) {
    val currentUser by userManager.currentUser.collectAsState()
    val playlists by libraryManager.playlists.collectAsState()
    val likedSongs by libraryManager.likedSongs.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var adbStatus by remember { mutableStateOf<String?>(null) }
    var pingStatus by remember { mutableStateOf<String?>(null) }
    var isCheckingPing by remember { mutableStateOf(false) }

    val localIp = remember { DesktopSyncService.getLocalIpAddress() }
    val port = DesktopSyncService.SYNC_PORT
    val fullUrl = "http://$localIp:$port"

    val lastSyncFormatted = remember(DesktopSyncService.lastSyncTimestampMs) {
        val ts = DesktopSyncService.lastSyncTimestampMs
        if (ts <= 0L) {
            "No sync completed yet this session"
        } else {
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            "Today at ${sdf.format(Date(ts))} (${DesktopSyncService.lastSyncDeviceName})"
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(480.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF161A22),
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
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync",
                                tint = themeAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Cross-Device Sync Hub",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = "Synchronize phone, desktop, and watch",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFA0AAB8)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Server Status Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2633)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1ED760))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Mac Sync Server Active",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Text(
                                text = "Port $port",
                                color = Color(0xFF8F9CAE),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // URL with 1-click Copy
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF151922))
                                .border(1.dp, Color(0xFF2C3545), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Wi-Fi Sync URL", color = Color.Gray, fontSize = 10.5.sp)
                                Text(fullUrl, color = themeAccent, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            }
                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(localIp))
                                    pingStatus = "Copied IP to clipboard!"
                                },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3545), contentColor = Color.White),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy IP", fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Account & Library Snapshot Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2633)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF4285F4)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("G", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = currentUser?.name ?: "Guest",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp
                                )
                            }
                            TextButton(
                                onClick = {
                                    onDismiss()
                                    onOpenAuth()
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Switch Account", color = Color(0xFF4285F4), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (currentUser?.email?.isNotBlank() == true) {
                            Text(
                                text = currentUser?.email.orEmpty(),
                                color = Color(0xFF8F9CAE),
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Color(0xFF2C3545), thickness = 0.8.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("PLAYLISTS ON MAC", color = Color.Gray, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                Text("${playlists.size} playlists", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Column {
                                Text("LIKED SONGS", color = Color.Gray, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                Text("${likedSongs.size} tracks", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Column {
                                Text("LAST SYNC", color = Color.Gray, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                Text(lastSyncFormatted, color = Color(0xFFA0AAB8), fontSize = 11.5.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // How to Sync Instructions
                Text("HOW TO SYNC WITH YOUR PHONE", color = Color(0xFF8E99A8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF151922))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("1. Wi-Fi Independent: Seamless cloud sync works across any Wi-Fi, cellular, or hotspot.", color = Color.White, fontSize = 12.sp)
                    Text("2. Playlists and liked songs auto-sync seamlessly in the background every 15s.", color = Color.White, fontSize = 12.sp)
                    Text("3. On phone, tap the Sync icon in Your Library to manually sync anytime.", color = Color.White, fontSize = 12.sp)
                    Text("4. Local Wi-Fi and USB cable links are active as automatic instant fallbacks.", color = Color(0xFF1ED760), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                if (adbStatus != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(adbStatus!!, color = Color(0xFF1ED760), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }

                if (pingStatus != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(pingStatus!!, color = Color(0xFF4285F4), fontSize = 11.5.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions: ADB Reverse & Cloud Sync Now
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                withContext(Dispatchers.IO) {
                                    try {
                                        val adbCandidates = listOf(
                                            File(System.getProperty("user.home"), "Library/Android/sdk/platform-tools/adb"),
                                            File("/usr/local/bin/adb"),
                                            File("/opt/homebrew/bin/adb")
                                        )
                                        val adb = adbCandidates.firstOrNull { it.canExecute() }
                                        if (adb != null) {
                                            val p = ProcessBuilder(adb.absolutePath, "reverse", "tcp:$port", "tcp:$port").start()
                                            p.waitFor()
                                            withContext(Dispatchers.Main) {
                                                adbStatus = "✓ USB Cable Sync Ready (adb reverse tcp:$port active)"
                                            }
                                        } else {
                                            withContext(Dispatchers.Main) {
                                                adbStatus = "ADB not found. Cloud & Wi-Fi sync are active."
                                            }
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            adbStatus = "ADB error: ${e.message}"
                                        }
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C3545)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Usb, contentDescription = "USB", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Connect via USB", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            isCheckingPing = true
                            coroutineScope.launch {
                                val res = DesktopSyncService.syncCloud()
                                isCheckingPing = false
                                pingStatus = if (res.isSuccess) {
                                    "✓ Cloud Sync Complete! (${res.getOrNull()} playlists synchronized)"
                                } else {
                                    "Cloud sync error: ${res.exceptionOrNull()?.message}"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = themeAccent, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        if (isCheckingPing) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.Black, strokeWidth = 2.dp)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sync Now (Cloud)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

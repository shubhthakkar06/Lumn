package com.example.musicapp.ui.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicapp.data.auth.UserManager
import com.example.musicapp.data.settings.SettingsManager
import com.example.musicapp.ui.auth.AuthDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val userManager = remember { UserManager.getInstance(context) }

    val dataSaver by settingsManager.dataSaverMode.collectAsStateWithLifecycle()
    val isOffline by settingsManager.offlineMode.collectAsStateWithLifecycle()

    val gapless by settingsManager.gaplessPlayback.collectAsStateWithLifecycle()
    val automix by settingsManager.automix.collectAsStateWithLifecycle()
    val crossfade by settingsManager.crossfadeSeconds.collectAsStateWithLifecycle()
    val streamingQuality by settingsManager.streamingQuality.collectAsStateWithLifecycle()
    val downloadQuality by settingsManager.downloadQuality.collectAsStateWithLifecycle()
    val canvasVideos by settingsManager.canvasVideos.collectAsStateWithLifecycle()
    val pushNotif by settingsManager.pushNotifications.collectAsStateWithLifecycle()

    val currentUser by userManager.currentUser.collectAsStateWithLifecycle()

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Dialog & Sheet States
    var activeCategorySheet by remember { mutableStateOf<String?>(null) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showAuthDialog by remember { mutableStateOf(false) }
    var cacheSizeMb by remember {
        mutableStateOf(String.format("%.1f MB", settingsManager.getCacheSizeBytes() / (1024.0 * 1024.0)))
    }

    if (showAuthDialog) {
        AuthDialog(onDismiss = { showAuthDialog = false })
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E14))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 44.dp, bottom = 120.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    IconButton(onClick = { isSearchActive = !isSearchActive }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (isSearchActive) Color(0xFF1ED760) else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // In-Settings Search
            item {
                AnimatedVisibility(visible = isSearchActive) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search settings", color = Color(0xFFA0A0A0)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00F59B),
                            unfocusedBorderColor = Color(0xFF2E384D),
                            focusedContainerColor = Color(0xFF131A26),
                            unfocusedContainerColor = Color(0xFF131A26)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // TOP 3 QUICK TOGGLE CARDS
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Card 1: Data Saver mode
                    val isDataSaverActive = dataSaver != "Off"
                    SettingQuickCard(
                        icon = Icons.Default.BarChart,
                        title = "Data Saver mode",
                        status = dataSaver,
                        isActive = isDataSaverActive,
                        onClick = {
                            val next = settingsManager.cycleDataSaverMode()
                            Toast.makeText(context, "Data Saver set to $next", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // Card 2: Offline mode
                    SettingQuickCard(
                        icon = Icons.Default.CloudOff,
                        title = "Offline mode",
                        status = if (isOffline) "On" else "Off",
                        isActive = isOffline,
                        onClick = {
                            val next = settingsManager.toggleOfflineMode()
                            Toast.makeText(context, if (next) "Offline mode on — downloaded songs work with mobile data still on" else "Offline mode disabled", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 8 SETTINGS CATEGORIES
            item {
                SettingRow(
                    icon = Icons.Default.Person,
                    title = "Account",
                    subtitle = "${currentUser.name} • Active Session",
                    onClick = { activeCategorySheet = "Account" }
                )
                SettingRow(
                    icon = Icons.Default.MusicNote,
                    title = "Content and display",
                    subtitle = "Music styling • Gen Z aesthetics",
                    onClick = { activeCategorySheet = "Content" }
                )
                SettingRow(
                    icon = Icons.Default.VolumeUp,
                    title = "Playback",
                    subtitle = "Gapless playback • Crossfade engine",
                    onClick = { activeCategorySheet = "Playback" }
                )
                SettingRow(
                    icon = Icons.Default.Notifications,
                    title = "Notifications",
                    subtitle = "Push • Media playback controls",
                    onClick = { activeCategorySheet = "Notifications" }
                )
                SettingRow(
                    icon = Icons.Default.Devices,
                    title = "Apps and devices",
                    subtitle = "Audio routing • Buds & Speaker Switcher",
                    onClick = { activeCategorySheet = "Apps" }
                )
                SettingRow(
                    icon = Icons.Default.ArrowCircleDown,
                    title = "Data-saving and offline",
                    subtitle = "Data Saver mode • Offline mode • $cacheSizeMb cache",
                    onClick = { activeCategorySheet = "Storage" }
                )
                SettingRow(
                    icon = Icons.Default.GraphicEq,
                    title = "Media quality",
                    subtitle = "Wi-Fi: $streamingQuality • Download: $downloadQuality",
                    onClick = { activeCategorySheet = "Quality" }
                )

                Spacer(modifier = Modifier.height(36.dp))

                // Log out button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B))
                        .clickable { showLogoutConfirm = true }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Log out",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        // Logout Confirmation Dialog
        if (showLogoutConfirm) {
            AlertDialog(
                onDismissRequest = { showLogoutConfirm = false },
                containerColor = Color(0xFF131A26),
                title = { Text("Log out?", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = "You will need to log back in to access your downloaded songs and personalized recommendations.",
                        color = Color(0xFFA0A0A0)
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showLogoutConfirm = false
                        userManager.logout()
                        Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
                        onBack()
                    }) {
                        Text("Log out", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutConfirm = false }) {
                        Text("Cancel", color = Color(0xFFA0A0A0))
                    }
                }
            )
        }

        // Dynamic Category Bottom Sheet
        if (activeCategorySheet != null) {
            ModalBottomSheet(
                onDismissRequest = { activeCategorySheet = null },
                containerColor = Color(0xFF131A26),
                contentColor = Color.White,
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(top = 10.dp, bottom = 12.dp)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2E384D))
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 36.dp)
                ) {
                    when (activeCategorySheet) {
                        "Account" -> {
                            Text("Account Details", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Username: ${currentUser.name}", color = Color.White, fontSize = 16.sp)
                            Text("Email: ${currentUser.email}", color = Color(0xFFA0A0A0), fontSize = 14.sp)
                            Text("Tier: Personal Edition (100% Free & Uncapped)", color = Color(0xFF00F59B), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = {
                                    activeCategorySheet = null
                                    showAuthDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                            ) {
                                Text("Switch account", color = Color.White)
                            }
                        }
                        "Playback" -> {
                            Text("Playback Preferences", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            Spacer(modifier = Modifier.height(16.dp))
                            SwitchRow("Gapless playback", gapless) { settingsManager.setGaplessPlayback(it) }
                            SwitchRow("Automix", automix) { settingsManager.setAutomix(it) }
                            Text("Crossfade: ${crossfade}s", color = Color.White, modifier = Modifier.padding(top = 12.dp))
                            Slider(
                                value = crossfade.toFloat(),
                                onValueChange = { settingsManager.setCrossfadeSeconds(it.toInt()) },
                                valueRange = 0f..12f,
                                colors = SliderDefaults.colors(thumbColor = Color(0xFF00F59B), activeTrackColor = Color(0xFF00F59B))
                            )
                        }
                        "Content" -> {
                            Text("Content and Display", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            Spacer(modifier = Modifier.height(16.dp))
                            SwitchRow("Canvas video loops", canvasVideos) { settingsManager.setCanvasVideos(it) }
                            SwitchRow("Show live visualizers", true) { }
                        }
                        "Notifications" -> {
                            Text("Notifications", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            Spacer(modifier = Modifier.height(16.dp))
                            SwitchRow("Push notifications", pushNotif) { settingsManager.setPushNotifications(it) }
                            SwitchRow("System media controls (Next / Prev)", true) { }
                        }
                        "Storage" -> {
                            Text("Data-saving & Offline Storage", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Current Cache: $cacheSizeMb", color = Color(0xFFA0A0A0), fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    val freed = settingsManager.clearCache()
                                    cacheSizeMb = "0.0 MB"
                                    Toast.makeText(context, "Cleared ${String.format("%.1f MB", freed / (1024.0 * 1024.0))} of cache!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F59B), contentColor = Color(0xFF0A0E14))
                            ) {
                                Text("Clear cache", fontWeight = FontWeight.Bold)
                            }
                        }
                        "Quality" -> {
                            Text("Media Quality", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Streaming Quality: $streamingQuality", color = Color.White, fontWeight = FontWeight.SemiBold)
                            listOf("Low", "Normal", "High", "Ultra 320kbps").forEach { q ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { settingsManager.setStreamingQuality(q) }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = streamingQuality == q,
                                        onClick = { settingsManager.setStreamingQuality(q) },
                                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF00F59B))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(q, color = Color.White)
                                }
                            }
                        }
                        else -> {
                            Text("Connected Audio Devices", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Adaptive Sound Enhancer: Active", color = Color(0xFF00F59B))
                            Text("Hardware Audio Device Routing: Enabled", color = Color(0xFFA0A0A0))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF0A0E14), checkedTrackColor = Color(0xFF00F59B))
        )
    }
}

@Composable
private fun SettingQuickCard(
    icon: ImageVector,
    title: String,
    status: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = Color(0xFF00F59B)
    val inactiveColor = Color(0xFFA0A0A0)
    val tint = if (isActive) activeColor else inactiveColor

    Box(
        modifier = modifier
            .height(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF131A26))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = title, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text(text = status, style = MaterialTheme.typography.labelSmall, color = if (isActive) Color(0xFF00D2FF) else Color(0xFFA0A0A0), fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = Color.White)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFFA0A0A0))
        }
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color(0xFF666666), modifier = Modifier.size(16.dp))
    }
}

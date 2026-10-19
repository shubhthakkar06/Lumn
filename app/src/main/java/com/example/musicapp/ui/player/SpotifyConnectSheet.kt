package com.example.musicapp.ui.player

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicapp.data.network.SongInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyConnectSheet(
    song: SongInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val audioDeviceManager = remember { com.example.musicapp.ui.util.AudioDeviceManager.getInstance(context) }
    val devices by audioDeviceManager.devices.collectAsState()
    val activeDeviceId by audioDeviceManager.activeDeviceId.collectAsState()
    val activeDevice = remember(devices, activeDeviceId) { audioDeviceManager.getActiveDevice() }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat().coerceAtLeast(1f) }
    var currentVol by remember {
        mutableStateOf((audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVolume).coerceIn(0f, 1f))
    }

    var showJamDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F1722),
        contentColor = Color.White,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF334155))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Text(
                text = "Connect to a device",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                ),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Current Active Device Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF131F2E))
                    .border(1.dp, Color(0x3300F59B), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = activeDevice.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Quality badge "Lossless"
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x2200F59B))
                                    .border(0.5.dp, Color(0x5500F59B), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Lossless",
                                    color = Color(0xFF00F59B),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }

                        // Mint Device Icon with mint line
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = activeDevice.icon,
                                contentDescription = null,
                                tint = Color(0xFF00F59B),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Box(
                                modifier = Modifier
                                    .width(28.dp)
                                    .height(2.dp)
                                    .background(Color(0xFF00F59B))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Mint equalizer bars + active track artist marquee
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = Color(0xFF00F59B),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = Color(0xFF00F59B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Hardware Volume Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeDown,
                            contentDescription = "Volume Down",
                            tint = Color(0xFFA0A0A0),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Slider(
                            value = currentVol.coerceIn(0f, 1f),
                            onValueChange = {
                                currentVol = it
                                audioManager.setStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    (it * maxVolume).toInt(),
                                    0
                                )
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00F59B),
                                activeTrackColor = Color(0xFF00F59B),
                                inactiveTrackColor = Color(0x3300F59B)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Volume Up",
                            tint = Color(0xFFA0A0A0),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Dynamic Devices List
            Text(
                text = "Select a device",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFFA0A0A0)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                devices.forEach { dev ->
                    val isSelected = dev.id == activeDeviceId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Color(0xFF131F2E) else Color.Transparent)
                            .border(
                                width = if (isSelected) 1.dp else 0.dp,
                                color = if (isSelected) Color(0x4400F59B) else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                audioDeviceManager.selectDevice(dev.id)
                                Toast.makeText(context, "Audio routed to ${dev.name}", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 12.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = dev.icon,
                            contentDescription = null,
                            tint = if (isSelected) Color(0xFF00F59B) else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = dev.name,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isSelected) Color(0xFF00F59B) else Color.White
                            )
                            Text(
                                text = if (isSelected) "Currently active" else "Tap to switch playback",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFA0A0A0)
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Active",
                                tint = Color(0xFF00F59B),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Matched actions: same height, padding, icon scale, and single-line label.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Start a Jam
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF383838))
                        .clickable { showJamDialog = true }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CastConnected,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Start a Jam",
                            color = Color.White,
                            maxLines = 1,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                // Find Bluetooth devices
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF383838))
                        .clickable {
                            try {
                                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Bluetooth settings unavailable", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Find devices",
                            color = Color.White,
                            maxLines = 1,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }

    if (showJamDialog) {
        AlertDialog(
            onDismissRequest = { showJamDialog = false },
            containerColor = Color(0xFF131F2E),
            title = {
                Text(
                    text = "Live Session Jam",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Invite friends to open this song in Lumn. Live synchronized playback needs a shared session server, which is not configured yet.",
                        color = Color(0xFFD0D0D0),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F1722))
                            .border(1.dp, Color(0x3300F59B), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                    Text(
                        text = "${song.title} • ${song.artist}",
                            color = Color(0xFF00F59B),
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val link = android.net.Uri.Builder().scheme("lumn").authority("song").appendPath(song.videoId)
                            .appendQueryParameter("title", song.title)
                            .appendQueryParameter("artist", song.artist)
                            .appendQueryParameter("art", song.albumArtUrl)
                            .appendQueryParameter("album", song.albumName)
                            .appendQueryParameter("duration", song.durationSeconds.toString())
                            .build()
                        val invite = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Lumn Jam: ${song.title}")
                            putExtra(Intent.EXTRA_TEXT, "Join me for ${song.title} by ${song.artist} on Lumn:\n$link")
                        }
                        context.startActivity(Intent.createChooser(invite, "Invite friends to Lumn"))
                        showJamDialog = false
                    }
                ) {
                    Text("Share invite", color = Color(0xFF00F59B), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showJamDialog = false }) {
                    Text("Close", color = Color(0xFFA0A0A0))
                }
            }
        )
    }
}

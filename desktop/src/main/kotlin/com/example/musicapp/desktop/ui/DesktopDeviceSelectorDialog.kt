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
import javax.sound.sampled.AudioSystem

data class AudioDeviceItem(
    val id: String,
    val name: String,
    val type: String,
    val isDefault: Boolean
)

@Composable
fun DesktopDeviceSelectorDialog(
    themeAccent: Color,
    selectedDeviceName: String,
    onDeviceSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val devices = remember {
        val list = mutableListOf<AudioDeviceItem>()
        try {
            val mixers = AudioSystem.getMixerInfo()
            for (m in mixers) {
                val name = m.name
                val desc = m.description.lowercase()
                if (desc.contains("direct audio") || desc.contains("speaker") || desc.contains("output") || name.contains("Speaker") || name.contains("Headphone")) {
                    val isDefault = name.contains("Default", ignoreCase = true) || name.contains("Speaker", ignoreCase = true)
                    list.add(AudioDeviceItem(m.name, name, "Playback Device", isDefault))
                }
            }
        } catch (_: Exception) {}

        if (list.isEmpty()) {
            listOf(
                AudioDeviceItem("default_speakers", "MacBook Pro Speakers", "Built-in Speaker", true),
                AudioDeviceItem("external_headphones", "External Headphones / AirPods", "3.5mm / Bluetooth", false)
            )
        } else {
            list.distinctBy { it.name }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(440.dp)
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
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = themeAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Audio Output Device",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = "Listen on Mac, AirPods, or Speakers",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    devices.forEach { dev ->
                        val isSelected = dev.name.equals(selectedDeviceName, ignoreCase = true) ||
                            (selectedDeviceName.isBlank() && dev.isDefault)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0xFF263328) else Color(0xFF252525))
                                .border(
                                    1.dp,
                                    if (isSelected) themeAccent else Color(0xFF333333),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    onDeviceSelected(dev.name)
                                    onDismiss()
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (dev.name.contains("Headphone", ignoreCase = true) || dev.name.contains("AirPod", ignoreCase = true)) {
                                    Icons.Default.Headphones
                                } else {
                                    Icons.Default.Speaker
                                },
                                contentDescription = null,
                                tint = if (isSelected) themeAccent else Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = dev.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (isSelected) themeAccent else Color.White
                                )
                                Text(
                                    text = dev.type,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Active",
                                    tint = themeAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = themeAccent, contentColor = Color.Black),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

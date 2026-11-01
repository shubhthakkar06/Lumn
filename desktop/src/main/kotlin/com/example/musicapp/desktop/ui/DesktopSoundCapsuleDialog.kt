package com.example.musicapp.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.example.musicapp.desktop.data.DesktopUser
import com.example.musicapp.desktop.model.DesktopSong

@Composable
fun DesktopSoundCapsuleDialog(
    currentUser: DesktopUser?,
    themeAccent: Color,
    onPlaySong: (DesktopSong) -> Unit,
    onDismiss: () -> Unit
) {
    var copiedToClipboard by remember { mutableStateOf(false) }

    val topDiscourse = DesktopSong(
        id = "baps_ep_aksharatitdas",
        title = "વચનામૃત ગઢડા પ્રથમ ૫૮: સત્પુરુષનો પ્રસંગ | પૂજ્ય અક્ષરાતીતદાસ સ્વામી",
        artist = "પૂજ્ય અક્ષરાતીતદાસ સ્વામી",
        albumArtUrl = "assets/aksharatitdas_episode.png",
        audioUrl = "https://p.scdn.co/mp3-preview/fa88a13eafcd9aa77ca98e20ceb435c786e229c6.mp3",
        durationSeconds = 3600L,
        isEpisode = true
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(620.dp)
                .height(640.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0F1015),
            tonalElevation = 12.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                themeAccent.copy(alpha = 0.22f),
                                Color(0xFF1E1430).copy(alpha = 0.4f),
                                Color(0xFF0C0D12)
                            ),
                            radius = 900f
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp)
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
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(themeAccent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black, modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Sound Capsule • 2026",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Text(
                                    text = "Your personalized listening journey • ${currentUser?.name ?: "Listener"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFAAAAAA)
                                )
                            }
                        }

                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Stat 1: Total Listening Time Card
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1D26)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(20.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("MINUTES LISTENED", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp), color = themeAccent)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("2,480 Minutes", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                        Text("That's 41 hours of inspiring audio, kathas & music", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(themeAccent.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Timer, contentDescription = null, tint = themeAccent, modifier = Modifier.size(28.dp))
                                    }
                                }
                            }
                        }

                        // Stat 2: Top Artist Spotlight
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1D26)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(20.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        painter = painterResource("assets/sadhus_of_baps.png"),
                                        contentDescription = "Sadhus of BAPS",
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("TOP ARTIST & CREATOR", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp), color = Color(0xFFFF9100))
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("Sadhus of BAPS", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                        Text("You spent 1,120 minutes immersed in authentic spiritual discourses", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    }
                                }
                            }
                        }

                        // Stat 3: Top Track / Episode
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1D26)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text("TOP RECORDING OF 2026", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp), color = themeAccent)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Image(
                                            painter = painterResource("assets/aksharatitdas_episode.png"),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                topDiscourse.title,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(topDiscourse.artist, color = Color.Gray, fontSize = 12.sp)
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(themeAccent)
                                                .clickable { onPlaySong(topDiscourse) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.Black, modifier = Modifier.size(24.dp))
                                        }
                                    }
                                }
                            }
                        }

                        // Stat 4: Audio Aura / Music Personality
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1D26)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text("YOUR AUDIO AURA", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp), color = Color(0xFFBA68C8))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Serene & Soulful Seeker", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Your music reflects calm introspection, soulful Gujarati discourses, and timeless acoustic melodies that uplift the spirit.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFCCCCCC)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Bottom Share / Close Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                val summary = "✨ My Rivara 2026 Sound Capsule ✨\n🎵 2,480 Minutes Listened\n👑 Top Artist: Sadhus of BAPS\n🌟 Audio Aura: Serene & Soulful Seeker\nListen on Rivara: https://github.com/shubhthakkar06/MusicApp"
                                val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                clipboard.setContents(java.awt.datatransfer.StringSelection(summary), null)
                                copiedToClipboard = true
                            },
                            border = androidx.compose.foundation.BorderStroke(1.dp, themeAccent),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = themeAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (copiedToClipboard) "Copied to Clipboard!" else "Share Capsule", color = Color.White, fontSize = 13.sp)
                        }

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
}

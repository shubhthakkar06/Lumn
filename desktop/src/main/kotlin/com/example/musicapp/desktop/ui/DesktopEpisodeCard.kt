package com.example.musicapp.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.musicapp.desktop.model.DesktopPlaylist
import com.example.musicapp.desktop.model.DesktopSong

@Composable
fun DesktopEpisodeCard(
    episode: DesktopSong,
    isPlayingThis: Boolean,
    isLiked: Boolean,
    playlists: List<DesktopPlaylist>,
    onPlay: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleLike: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showPlaylistSubmenu by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isPlayingThis) Color(0xFF142419) else Color(0xFF131A15))
            .border(
                width = 1.dp,
                color = if (isPlayingThis) Color(0xFF1ED760).copy(alpha = 0.5f) else Color(0xFF223026),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Left: High quality Episode Artwork matching Image 1 (click to play)
            val localAsset = when {
                episode.id == "baps_ep_aksharatitdas" || episode.title.contains("અક્ષરાતીતદાસ") -> "assets/aksharatitdas_episode.png"
                episode.id == "baps_ep_uttamcharan" || episode.title.contains("ઉત્તમયોગીદાસ") -> "assets/uttamyogidas_episode.png"
                episode.id == "baps_ep_atmatruptdas" || episode.title.contains("આત્મતૃપ્તદાસ") || episode.title.contains("Atmatruptdas") -> "assets/atmatruptdas_episode.png"
                episode.id == "baps_ep_1" || episode.title.contains("Somprakashdas") || episode.title.contains("સોમપ્રકાશદાસ") -> "assets/somprakashdas_episode.png"
                episode.albumArtUrl.startsWith("assets/") -> episode.albumArtUrl
                else -> null
            }

            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(102.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF222622))
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center
            ) {
                if (localAsset != null) {
                    Image(
                        painter = painterResource(localAsset),
                        contentDescription = episode.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (episode.albumArtUrl.isNotBlank()) {
                    AsyncImage(
                        model = episode.albumArtUrl,
                        contentDescription = episode.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Podcasts,
                        contentDescription = null,
                        tint = Color(0xFFE88A34),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Right: Episode Info & Action Buttons matching Image 1
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onPlay)
            ) {
                // Line 1: Title (Full 2-line title matching Spotify screenshot)
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        lineHeight = 22.sp
                    ),
                    color = if (isPlayingThis) Color(0xFF1ED760) else Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                // Line 2: Subtitle "Episode • BAPS Satsang Gujarati"
                Text(
                    text = episode.album.ifBlank { "Episode • ${episode.artist}" },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color(0xFFA7A7A7),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Line 3: Description in Gujarati (3 lines matching Spotify screenshot)
                val desc = episode.description.ifBlank {
                    "આપણું જીવન સુખ અને દુઃખ બંનેથી ભરેલું છે. જ્યારે મુશ્કેલીઓ આવે છે, ત્યારે આપણે ઘણીવાર નિરાશ થઈ જઈએ છીએ અથવા ખોટી જગ્યાએ ઉકેલ શોધીએ છીએ. પૂજ્ય સોમપ્રકાશદાસ સ્વામી આ કથામાં સમજાવે છે કે સુખ અને દુઃખ એ..."
                }
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    ),
                    color = Color(0xFFA0A5A0),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Line 4: Date & Duration (e.g. "Oct 12 • 47 min 17 sec")
                val minutes = episode.durationSeconds / 60
                val seconds = episode.durationSeconds % 60
                val durationStr = if (minutes >= 60) {
                    val hrs = minutes / 60
                    val remMins = minutes % 60
                    "$hrs hr $remMins min"
                } else {
                    "$minutes min $seconds sec"
                }
                val dateStr = episode.releaseDate.ifBlank { "Oct 12" }

                Text(
                    text = "$dateStr • $durationStr",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.5.sp
                    ),
                    color = Color(0xFF8E948E)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Line 5: Spotify-style Action Row:
                // Left: Green Checkmark (saved) + 3-dots | Right: White Circle Play Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Green Checkmark Badge (Spotify Saved / Liked indicator)
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (isLiked) Color(0xFF1ED760) else Color.Transparent)
                            .border(
                                width = if (isLiked) 0.dp else 1.5.dp,
                                color = if (isLiked) Color.Transparent else Color.Gray,
                                shape = CircleShape
                            )
                            .clickable(onClick = onToggleLike),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = if (isLiked) "Saved to Your Library" else "Save to Your Library",
                            tint = if (isLiked) Color.Black else Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // 3-dots Menu Button
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreHoriz,
                                contentDescription = "Episode options",
                                tint = Color(0xFFAAAAAA),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = {
                                showMenu = false
                                showPlaylistSubmenu = false
                            },
                            modifier = Modifier.background(Color(0xFF242424))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Play Next", color = Color.White) },
                                leadingIcon = {
                                    Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color(0xFF1ED760))
                                },
                                onClick = {
                                    showMenu = false
                                    onPlayNext()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add to Queue", color = Color.White) },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = Color.White)
                                },
                                onClick = {
                                    showMenu = false
                                    onAddToQueue()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isLiked) "Remove from Your Library" else "Save to Your Library", color = Color.White) },
                                leadingIcon = {
                                    Icon(if (isLiked) Icons.Default.Check else Icons.Default.Add, contentDescription = null, tint = Color(0xFF1ED760))
                                },
                                onClick = {
                                    showMenu = false
                                    onToggleLike()
                                }
                            )
                            HorizontalDivider(color = Color(0xFF383838))
                            DropdownMenuItem(
                                text = { Text("Add to Playlist...", color = Color.White) },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = Color.White)
                                },
                                onClick = {
                                    showPlaylistSubmenu = true
                                }
                            )

                            if (showPlaylistSubmenu) {
                                playlists.forEach { pl ->
                                    DropdownMenuItem(
                                        text = { Text("   ↳ ${pl.name}", color = Color(0xFF1ED760), fontSize = 13.sp) },
                                        onClick = {
                                            showMenu = false
                                            showPlaylistSubmenu = false
                                            onAddToPlaylist(pl.id)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Spotify iconic White Circle Play Button on the far right
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable {
                                if (isPlayingThis) onTogglePlayPause() else onPlay()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlayingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlayingThis) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}

package com.example.musicapp.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicapp.desktop.model.DesktopSong

@Composable
fun DesktopMainContent(
    currentSong: DesktopSong?,
    songs: List<DesktopSong>,
    onPlaySong: (DesktopSong) -> Unit,
    onToggleLike: (DesktopSong) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = remember {
        listOf("All", "Pop", "Bollywood", "Lo-Fi", "Hip-Hop", "Punjabi", "Podcasts & Satsang")
    }

    val filteredSongs = remember(searchQuery, selectedCategory, songs) {
        songs.filter { song ->
            val matchesQuery = searchQuery.isBlank() ||
                song.title.contains(searchQuery, ignoreCase = true) ||
                song.artist.contains(searchQuery, ignoreCase = true)
            val matchesCategory = when (selectedCategory) {
                "All" -> true
                "Pop" -> song.artist.contains("Coldplay", ignoreCase = true) || song.artist.contains("Dua Lipa", ignoreCase = true)
                "Podcasts & Satsang" -> song.artist.contains("Satsang", ignoreCase = true) || song.title.contains("Refuge", ignoreCase = true)
                else -> true
            }
            matchesQuery && matchesCategory
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1E2A22), Color(0xFF101010), Color(0xFF0D0D0D))
                )
            )
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Header: Search Field & User Profile Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("What do you want to play? (Songs, Artists, Podcasts)", color = Color.Gray) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray)
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF1ED760),
                        unfocusedBorderColor = Color(0xFF2E2E2E),
                        focusedContainerColor = Color(0xFF1E1E1E),
                        unfocusedContainerColor = Color(0xFF1E1E1E)
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.width(420.dp).height(50.dp)
                )

                // User profile pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1E1E1E))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1ED760)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("S", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Shubh", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Category Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { category ->
                    val isSelected = category == selectedCategory
                    AssistChip(
                        onClick = { selectedCategory = category },
                        label = {
                            Text(
                                text = category,
                                color = if (isSelected) Color.Black else Color.White,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (isSelected) Color(0xFF1ED760) else Color(0xFF282828)
                        ),
                        border = null,
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Song Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("#", color = Color.Gray, modifier = Modifier.width(36.dp), style = MaterialTheme.typography.labelMedium)
                Text("TITLE", color = Color.Gray, modifier = Modifier.weight(2f), style = MaterialTheme.typography.labelMedium)
                Text("ARTIST", color = Color.Gray, modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.labelMedium)
                Text("DURATION", color = Color.Gray, modifier = Modifier.width(80.dp), style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.width(48.dp))
            }

            HorizontalDivider(color = Color(0xFF282828), thickness = 1.dp)

            // Song Rows
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                itemsIndexed(filteredSongs, key = { _, s -> s.id }) { index, song ->
                    val isCurrent = song.id == currentSong?.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCurrent) Color(0xFF1F3827) else Color.Transparent)
                            .clickable { onPlaySong(song) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = if (isCurrent) Color(0xFF1ED760) else Color.Gray,
                            modifier = Modifier.width(36.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Text(
                            text = song.title,
                            color = if (isCurrent) Color(0xFF1ED760) else Color.White,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(2f)
                        )

                        Text(
                            text = song.artist,
                            color = Color(0xFFAAAAAA),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1.5f)
                        )

                        val m = song.durationSeconds / 60
                        val s = song.durationSeconds % 60
                        Text(
                            text = "%d:%02d".format(m, s),
                            color = Color.Gray,
                            modifier = Modifier.width(80.dp),
                            style = MaterialTheme.typography.bodySmall
                        )

                        IconButton(
                            onClick = { onToggleLike(song) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (song.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Like",
                                tint = if (song.isLiked) Color(0xFF1ED760) else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

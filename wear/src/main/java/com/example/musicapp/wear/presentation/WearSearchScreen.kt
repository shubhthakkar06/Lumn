package com.example.musicapp.wear.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.*
import com.example.musicapp.wear.model.WearSongItem
import com.example.musicapp.wear.theme.WearPrimary

@Composable
fun WearSearchScreen(
    onSongSelected: (WearSongItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    // Pure music catalog for Galaxy Watch (strictly pure music, zero podcasts/episodes)
    val localCatalog = remember {
        listOf(
            WearSongItem("1", "Yellow", "Coldplay", 269L),
            WearSongItem("2", "Starboy", "The Weeknd", 230L),
            WearSongItem("3", "Levitating", "Dua Lipa", 203L),
            WearSongItem("4", "Kesariya", "Arijit Singh", 268L),
            WearSongItem("5", "Tum Hi Ho", "Arijit Singh", 262L),
            WearSongItem("6", "Blinding Lights", "The Weeknd", 200L),
            WearSongItem("7", "Shape of You", "Ed Sheeran", 233L),
            WearSongItem("8", "Believer", "Imagine Dragons", 204L),
            WearSongItem("9", "Apna Bana Le", "Arijit Singh", 261L),
            WearSongItem("10", "Channa Mereya", "Arijit Singh", 289L),
            WearSongItem("11", "Viva La Vida", "Coldplay", 242L),
            WearSongItem("12", "Something Just Like This", "The Chainsmokers & Coldplay", 247L),
            WearSongItem("13", "Closer", "The Chainsmokers", 245L),
            WearSongItem("14", "As It Was", "Harry Styles", 167L),
            WearSongItem("15", "O Rangrez", "Aamir Mir", 220L)
        )
    }

    val searchResults = remember(query) {
        if (query.isBlank()) localCatalog.take(5)
        else {
            val q = query.trim().lowercase()
            localCatalog.filter {
                it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Search Music",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Watch Typing Search Bar with Keyboard & Voice Input
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(Color(0xFF1E1E1E))
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = WearPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "Type song or artist…",
                                    color = Color.Gray,
                                    fontSize = 11.5.sp
                                )
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(WearPrimary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (query.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = Color.Gray,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { query = "" }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (searchResults.isEmpty()) {
                item {
                    Text(
                        text = "No songs found for \"$query\"",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(searchResults, key = { it.id }) { song ->
                    Card(
                        onClick = { onSongSelected(song) },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF181818)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(WearPrimary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = WearPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
                                    color = Color.Gray,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = WearPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }
}

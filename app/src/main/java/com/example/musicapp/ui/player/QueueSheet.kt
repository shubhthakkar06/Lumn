package com.example.musicapp.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.musicapp.data.network.SongInfo

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun QueueSheet(
    currentSong: SongInfo,
    queue: List<SongInfo>,
    onSongSelected: (SongInfo) -> Unit,
    onMoveItem: (fromIndex: Int, toIndex: Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        contentColor = Color.White,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF555555))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Queue",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                ),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Now Playing Section
            Text(
                text = "Now playing",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFFA0A0A0)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF282828))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = currentSong.albumArtUrl,
                    contentDescription = currentSong.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentSong.title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF1ED760),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentSong.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFA0A0A0),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Playing",
                    tint = Color(0xFF1ED760),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Up Next Section
            Text(
                text = "Next in queue (${queue.size})",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFFA0A0A0)
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No more tracks in queue",
                        color = Color(0xFF707070),
                        fontSize = 14.sp
                    )
                }
            } else {
                val itemKeys = remember(queue) {
                    val occurrences = mutableMapOf<String, Int>()
                    queue.map { song ->
                        val occurrence = occurrences.getOrDefault(song.videoId, 0)
                        occurrences[song.videoId] = occurrence + 1
                        "${song.videoId}#$occurrence"
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                ) {
                    itemsIndexed(queue, key = { index, _ -> itemKeys[index] }) { index, song ->
                        Row(
                            modifier = Modifier
                                .animateItem()
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    onSongSelected(song)
                                    onDismiss()
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = song.albumArtUrl,
                                contentDescription = song.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFA0A0A0),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Color(0xFF555555),
                                modifier = Modifier.size(18.dp).padding(end = 0.dp)
                            )
                            Column {
                                IconButton(onClick = { onMoveItem(index, index - 1) }, enabled = index > 0, modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move ${song.title} up", tint = if (index > 0) Color.White else Color(0xFF555555))
                                }
                                IconButton(onClick = { onMoveItem(index, index + 1) }, enabled = index < queue.lastIndex, modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move ${song.title} down", tint = if (index < queue.lastIndex) Color.White else Color(0xFF555555))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.example.musicapp.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Close
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
import com.example.musicapp.desktop.data.DesktopLibraryManager
import com.example.musicapp.desktop.data.DesktopMusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DesktopPlaylistImportDialog(
    themeAccent: Color,
    libraryManager: DesktopLibraryManager,
    musicRepository: DesktopMusicRepository,
    onDismiss: () -> Unit
) {
    var playlistTitle by remember { mutableStateOf("") }
    var importInput by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(480.dp)
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
                                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = null,
                                tint = themeAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Import Playlist",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = "Paste Spotify / YouTube link or song list",
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

                OutlinedTextField(
                    value = playlistTitle,
                    onValueChange = { playlistTitle = it },
                    label = { Text("Playlist Name") },
                    placeholder = { Text("e.g. My Favorites 2026") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = themeAccent,
                        unfocusedBorderColor = Color(0xFF383838),
                        focusedContainerColor = Color(0xFF252525),
                        unfocusedContainerColor = Color(0xFF252525)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = importInput,
                    onValueChange = { importInput = it },
                    label = { Text("Link or Song List (one per line)") },
                    placeholder = { Text("https://open.spotify.com/playlist/...\nOR\nYellow - Coldplay\nTum Hi Ho - Arijit Singh") },
                    minLines = 4,
                    maxLines = 6,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = themeAccent,
                        unfocusedBorderColor = Color(0xFF383838),
                        focusedContainerColor = Color(0xFF252525),
                        unfocusedContainerColor = Color(0xFF252525)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (statusMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = statusMessage,
                        color = if (statusMessage.startsWith("Success")) themeAccent else Color(0xFFFFB74D),
                        fontSize = 12.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, enabled = !isImporting) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            val trimmedInput = importInput.trim()
                            if (trimmedInput.isBlank()) {
                                statusMessage = "Please enter songs or a playlist link"
                                return@Button
                            }

                            isImporting = true
                            statusMessage = "Analyzing playlist..."

                            scope.launch(Dispatchers.IO) {
                                try {
                                    if (com.example.musicapp.desktop.data.DesktopSpotifyImporter.isSpotifyUrl(trimmedInput)) {
                                        withContext(Dispatchers.Main) {
                                            statusMessage = "Connecting to Spotify Pathfinder..."
                                        }
                                        val spotifyResult = com.example.musicapp.desktop.data.DesktopSpotifyImporter.importPlaylist(
                                            url = trimmedInput,
                                            musicRepository = musicRepository,
                                            onProgress = { cur, tot ->
                                                scope.launch(Dispatchers.Main) {
                                                    statusMessage = "Loaded $cur of $tot tracks from Spotify..."
                                                }
                                            }
                                        )

                                        if (spotifyResult != null && spotifyResult.songs.isNotEmpty()) {
                                            val finalName = playlistTitle.ifBlank { spotifyResult.name }
                                            val createdPl = libraryManager.createPlaylist(
                                                name = finalName,
                                                coverUrl = spotifyResult.coverUrl
                                            )
                                            for (song in spotifyResult.songs) {
                                                libraryManager.addSongToPlaylist(createdPl.id, song)
                                            }

                                            withContext(Dispatchers.Main) {
                                                isImporting = false
                                                statusMessage = "Success! Imported ${spotifyResult.songs.size} tracks into \"$finalName\""
                                                kotlinx.coroutines.delay(1200)
                                                onDismiss()
                                            }
                                            return@launch
                                        }
                                    }

                                    // Fallback: line-by-line search import
                                    val name = playlistTitle.ifBlank { "Imported Playlist" }
                                    val lines = importInput.lines().map { it.trim() }.filter { it.isNotBlank() }
                                    val playlist = libraryManager.createPlaylist(name)
                                    var addedCount = 0

                                    for ((idx, line) in lines.withIndex()) {
                                        val query = line.replace(Regex("https?://\\S+"), "").trim()
                                        val searchQuery = if (query.isNotBlank()) query else line
                                        withContext(Dispatchers.Main) {
                                            statusMessage = "Matching song ${idx + 1}/${lines.size}: $searchQuery"
                                        }
                                        val hits = musicRepository.searchSongs(searchQuery)
                                        val songToAdd = hits.firstOrNull()
                                        if (songToAdd != null) {
                                            libraryManager.addSongToPlaylist(playlist.id, songToAdd)
                                            addedCount++
                                        }
                                    }

                                    withContext(Dispatchers.Main) {
                                        isImporting = false
                                        statusMessage = "Success! Added $addedCount songs to \"$name\""
                                        kotlinx.coroutines.delay(1000)
                                        onDismiss()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isImporting = false
                                        statusMessage = "Import error: ${e.message}"
                                    }
                                }
                            }
                        },
                        enabled = !isImporting && importInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = themeAccent, contentColor = Color.Black),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Importing...", fontWeight = FontWeight.Bold)
                        } else {
                            Text("Import Now", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

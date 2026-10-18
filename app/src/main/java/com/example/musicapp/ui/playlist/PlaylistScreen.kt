package com.example.musicapp.ui.playlist

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.musicapp.data.download.DownloadStatus
import com.example.musicapp.data.download.MusicDownloadManager
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.playlist.PlaylistManager
import com.example.musicapp.ui.main.MainScreenViewModel
import com.example.musicapp.ui.util.rememberDynamicGradient

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistScreen(
    playlistId: String = "",
    title: String,
    coverUrl: String,
    creator: String = "Shubh Thakkar",
    savesAndDuration: String = "774 saves • 3h 52min",
    viewModel: MainScreenViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val playlistManager = remember { PlaylistManager.getInstance(context) }
    val downloadManager = remember { MusicDownloadManager.getInstance(context) }

    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isShuffle by viewModel.isShuffle.collectAsStateWithLifecycle()
    val loadingSongVideoId by viewModel.loadingSongVideoId.collectAsStateWithLifecycle()

    val likedSongs by playlistManager.likedSongs.collectAsStateWithLifecycle()
    val userPlaylists by playlistManager.playlists.collectAsStateWithLifecycle()
    val downloadStates by downloadManager.downloadStates.collectAsStateWithLifecycle()

    val dynamicColors = rememberDynamicGradient(coverUrl)
    val isLikedSongsPlaylist = playlistId == "liked" || title.equals("Liked Songs", ignoreCase = true)

    var playlistSearchQuery by remember { mutableStateOf("") }
    var isPlaylistSearchOpen by remember { mutableStateOf(false) }
    val vibeFilters = remember { listOf("All", "Energetic", "Chill", "Electronic", "Focus", "Vocal") }
    var selectedVibe by remember { mutableStateOf("All") }

    val playlistSongs: List<SongInfo> = remember(playlistId, title, likedSongs, userPlaylists) {
        when {
            playlistId == "liked" || title.contains("Liked Songs", ignoreCase = true) -> likedSongs
            else -> {
                val found = userPlaylists.find { it.id == playlistId }
                if (found != null) found.songs
                else com.example.musicapp.data.MusicCatalog.getPlaylistSongs(playlistId, title)
            }
        }
    }

    val filteredSongs: List<SongInfo> = remember(playlistSongs, selectedVibe, playlistSearchQuery) {
        var list = playlistSongs
        if (selectedVibe != "All") {
            list = list.filter { song ->
                when (selectedVibe) {
                    "Energetic" -> song.title.contains("remix", true) || song.title.contains("dance", true) || song.title.contains("club", true) || song.title.contains("beat", true) || song.artist.contains("Walker", true) || song.artist.contains("Garrix", true) || song.artist.contains("Avicii", true)
                    "Chill" -> song.title.contains("chill", true) || song.title.contains("slow", true) || song.title.contains("acoustic", true) || song.title.contains("lofi", true) || song.title.contains("night", true) || song.title.contains("faded", true)
                    "Electronic" -> song.artist.contains("Walker", true) || song.artist.contains("DJ", true) || song.title.contains("mix", true) || song.title.contains("electronic", true) || song.title.contains("spectre", true)
                    "Focus" -> !song.title.contains("remix", true) && !song.title.contains("live", true)
                    "Vocal" -> song.title.contains("feat", true) || song.title.contains("ft", true) || song.artist.contains("Arijit", true) || song.artist.contains("Taylor", true)
                    else -> true
                }
            }.ifEmpty {
                if (playlistSongs.size > 2) playlistSongs.take((playlistSongs.size / 2) + 1) else playlistSongs
            }
        }
        if (playlistSearchQuery.isNotBlank()) {
            list = list.filter {
                it.title.contains(playlistSearchQuery, ignoreCase = true) ||
                it.artist.contains(playlistSearchQuery, ignoreCase = true)
            }
        }
        list
    }
    val filteredSongKeys = remember(filteredSongs) {
        val occurrences = mutableMapOf<String, Int>()
        filteredSongs.map { song ->
            val occurrence = occurrences.getOrDefault(song.videoId, 0)
            occurrences[song.videoId] = occurrence + 1
            "${song.videoId}#$occurrence"
        }
    }

    var playBounced by remember { mutableStateOf(false) }
    val playScale by animateFloatAsState(
        targetValue = if (playBounced) 1.15f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        finishedListener = { playBounced = false },
        label = "playScale"
    )

    val currentPlaylist = remember(playlistId, userPlaylists) {
        userPlaylists.find { it.id == playlistId }
    }
    val isUserPlaylist = currentPlaylist != null

    var showPlaylistMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(title) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    var selectedSongForMenu by remember { mutableStateOf<SongInfo?>(null) }
    var selectedSongIndex by remember { mutableIntStateOf(-1) }
    var showSongMenu by remember { mutableStateOf(false) }

    // Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = Color(0xFF131A26),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFCBD5E1),
            title = { Text("Rename Playlist", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00F59B),
                        unfocusedBorderColor = Color(0xFF2E384D),
                        focusedContainerColor = Color(0xFF0A0E14),
                        unfocusedContainerColor = Color(0xFF0A0E14)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            playlistManager.renamePlaylist(playlistId, renameText)
                            Toast.makeText(context, "Playlist renamed", Toast.LENGTH_SHORT).show()
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text("Save", color = Color(0xFF00F59B), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = Color(0xFFA0A0A0))
                }
            }
        )
    }

    // Delete Playlist Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = Color(0xFF131A26),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFCBD5E1),
            title = { Text("Delete Playlist?", fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently delete \"${currentPlaylist?.name ?: title}\".") },
            confirmButton = {
                TextButton(
                    onClick = {
                        playlistManager.deletePlaylist(playlistId)
                        Toast.makeText(context, "Playlist deleted", Toast.LENGTH_SHORT).show()
                        showDeleteConfirmDialog = false
                        onBack()
                    }
                ) {
                    Text("Delete", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = Color(0xFFA0A0A0))
                }
            }
        )
    }

    // Song Options BottomSheet / Dialog
    if (showSongMenu && selectedSongForMenu != null) {
        val song = selectedSongForMenu!!
        val isDl = downloadStates[song.videoId] is DownloadStatus.Downloaded
        AlertDialog(
            onDismissRequest = { showSongMenu = false },
            containerColor = Color(0xFF131A26),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = {
                Text(
                    text = song.title,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isUserPlaylist) {
                        if (selectedSongIndex > 0) {
                            TextButton(
                                onClick = {
                                    playlistManager.moveSong(playlistId, selectedSongIndex, selectedSongIndex - 1)
                                    showSongMenu = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("⬆️  Move Up in Playlist", color = Color.White)
                                }
                            }
                        }
                        if (selectedSongIndex < playlistSongs.size - 1) {
                            TextButton(
                                onClick = {
                                    playlistManager.moveSong(playlistId, selectedSongIndex, selectedSongIndex + 1)
                                    showSongMenu = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("⬇️  Move Down in Playlist", color = Color.White)
                                }
                            }
                        }
                        TextButton(
                            onClick = {
                                playlistManager.removeSongFromPlaylist(playlistId, song.videoId)
                                Toast.makeText(context, "Removed from playlist", Toast.LENGTH_SHORT).show()
                                showSongMenu = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("🗑️  Remove from this Playlist", color = Color(0xFFFF5252))
                            }
                        }
                    }

                    if (isDl) {
                        TextButton(
                            onClick = {
                                downloadManager.deleteDownload(song.videoId)
                                showSongMenu = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("❌  Delete Download", color = Color(0xFFFF5252))
                            }
                        }
                    } else {
                        TextButton(
                            onClick = {
                                downloadManager.downloadSong(song) { target ->
                                    val repo = com.example.musicapp.data.DefaultDataRepository(context)
                                    repo.getStreamUrl(target.videoId, target.title, target.artist)
                                }
                                showSongMenu = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("⬇️  Download Track", color = Color(0xFF00F59B))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSongMenu = false }) {
                    Text("Close", color = Color(0xFFA0A0A0))
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        dynamicColors.firstOrNull() ?: Color(0xFF0F1E36),
                        Color(0xFF0A0E14),
                        Color(0xFF0A0E14)
                    ),
                    startY = 0f,
                    endY = 1200f
                )
            ),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 44.dp, start = 16.dp, end = 16.dp),
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
                IconButton(onClick = { isPlaylistSearchOpen = !isPlaylistSearchOpen }) {
                    Icon(
                        imageVector = if (isPlaylistSearchOpen) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = "Search Playlist",
                        tint = if (isPlaylistSearchOpen) Color(0xFF00F59B) else Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            AnimatedVisibility(visible = isPlaylistSearchOpen) {
                OutlinedTextField(
                    value = playlistSearchQuery,
                    onValueChange = { playlistSearchQuery = it },
                    placeholder = { Text("Search songs in playlist...", color = Color(0xFFA0A0A0)) },
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
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }

            // Cover Art
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isLikedSongsPlaylist) {
                    Box(
                        modifier = Modifier.size(220.dp).clip(RoundedCornerShape(8.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF7344E8), Color(0xFF2448B8), Color(0xFF122043)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = "Liked Songs", tint = Color.White, modifier = Modifier.size(76.dp))
                    }
                } else if (coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = "Playlist Cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(220.dp).clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier.size(220.dp).clip(RoundedCornerShape(8.dp))
                            .background(Brush.linearGradient(dynamicColors)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(64.dp))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Playlist Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Creator Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF5722)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("S", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = creator,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                currentPlaylist?.sourceUrl?.takeIf { it.isNotBlank() }?.let { spotifyUrl ->
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(spotifyUrl)))
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                    ) {
                        Text("Imported from Spotify · Open original playlist", color = Color(0xFF00D2FF), style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Stats row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Public,
                        contentDescription = null,
                        tint = Color(0xFFA0A0A0),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${filteredSongs.size} tracks • 3h 52min",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFA0A0A0)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Added",
                            tint = Color(0xFF00F59B),
                            modifier = Modifier.size(24.dp)
                        )
                        // Download whole playlist button
                        IconButton(
                            onClick = {
                                val songsToDownload = filteredSongs.ifEmpty { playlistSongs }
                                Toast.makeText(context, "Downloading ${songsToDownload.size} tracks...", Toast.LENGTH_SHORT).show()
                                songsToDownload.forEach { s ->
                                    downloadManager.downloadSong(s) { target ->
                                        val repo = com.example.musicapp.data.DefaultDataRepository(context)
                                        repo.getStreamUrl(target.videoId, target.title, target.artist)
                                    }
                                }
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download Playlist",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { showPlaylistMenu = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More",
                                    tint = Color(0xFFA0A0A0),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showPlaylistMenu,
                                onDismissRequest = { showPlaylistMenu = false },
                                modifier = Modifier.background(Color(0xFF161F2E))
                            ) {
                                if (isUserPlaylist) {
                                    DropdownMenuItem(
                                        text = { Text("Rename Playlist", color = Color.White) },
                                        onClick = {
                                            showPlaylistMenu = false
                                            renameText = currentPlaylist?.name ?: title
                                            showRenameDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete Playlist", color = Color(0xFFFF5252)) },
                                        onClick = {
                                            showPlaylistMenu = false
                                            showDeleteConfirmDialog = true
                                        }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Save to Your Library", color = Color.White) },
                                        onClick = {
                                            showPlaylistMenu = false
                                            val created = playlistManager.createPlaylist(title, creator, coverUrl)
                                            filteredSongs.forEach { playlistManager.addSongToPlaylist(created.id, it) }
                                            Toast.makeText(context, "Saved as custom playlist!", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isShuffle) Color(0xFF00F59B) else Color(0xFFA0A0A0),
                            modifier = Modifier.size(28.dp).clickable { viewModel.toggleShuffle() }
                        )

                        // Glowing Cyber Gradient Play Button
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .scale(playScale)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF00F59B), Color(0xFF00D2FF))
                                    )
                                )
                                .clickable {
                                    playBounced = true
                                    if (currentSong != null) {
                                        viewModel.togglePlayPause()
                                    } else if (filteredSongs.isNotEmpty()) {
                                        viewModel.playSong(filteredSongs.first(), filteredSongs)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color(0xFF0A0E14),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Mood / Vibe Filter Pills
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(vibeFilters) { vibe ->
                    val isSelected = selectedVibe == vibe
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSelected) Color(0xFF00F59B)
                                else Color(0xFF161F2E)
                            )
                            .clickable {
                                selectedVibe = vibe
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = vibe,
                            color = if (isSelected) Color(0xFF0A0E14) else Color(0xFFCBD5E1),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Song Items
        itemsIndexed(filteredSongs, key = { index, _ -> filteredSongKeys[index] }) { index, song ->
            val isCurrent = currentSong?.videoId == song.videoId
            val isLoading = loadingSongVideoId == song.videoId
            val dlStatus = downloadStates[song.videoId] ?: DownloadStatus.NotDownloaded

            Row(
                modifier = Modifier
                    .animateItem()
                    .fillMaxWidth()
                    .clickable { viewModel.playSong(song, filteredSongs) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = song.albumArtUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = if (isCurrent) Color(0xFF00F59B) else Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (dlStatus is DownloadStatus.Downloaded) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = Color(0xFF00F59B),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFA0A0A0),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color(0xFF00F59B),
                        strokeWidth = 2.dp
                    )
                } else if (dlStatus is DownloadStatus.Downloaded) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF00F59B),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                IconButton(
                    onClick = {
                        selectedSongForMenu = song
                        selectedSongIndex = index
                        showSongMenu = true
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Song options",
                        tint = Color(0xFFA0A0A0),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

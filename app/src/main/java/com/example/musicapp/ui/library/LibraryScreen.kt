package com.example.musicapp.ui.library

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.musicapp.data.artist.ArtistFollowManager
import com.example.musicapp.data.auth.UserManager
import com.example.musicapp.data.download.MusicDownloadManager
import com.example.musicapp.data.playlist.PlaylistManager
import com.example.musicapp.data.playlist.SpotifyPlaylistImporter
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.ui.main.MainScreenViewModel
import com.example.musicapp.ui.navigation.Screen
import kotlinx.coroutines.launch

data class LibraryItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String,
    val isArtist: Boolean = false,
    val isPinned: Boolean = false,
    val isDownloaded: Boolean = false,
    val isSpecialIcon: Boolean = false,
    val specialIconColor: Color = Color.Transparent,
    val timestampMs: Long = System.currentTimeMillis()
)

@Composable
fun LibraryScreen(
    onOpenDrawer: () -> Unit,
    onNavigate: (Screen) -> Unit,
    viewModel: MainScreenViewModel? = null
) {
    val context = LocalContext.current
    val userManager = remember { UserManager.getInstance(context) }
    val playlistManager = remember { PlaylistManager.getInstance(context) }
    val downloadManager = remember { MusicDownloadManager.getInstance(context) }
    val followManager = remember { ArtistFollowManager.getInstance(context) }

    val currentUser by userManager.currentUser.collectAsStateWithLifecycle()
    val likedSongs by playlistManager.likedSongs.collectAsStateWithLifecycle()
    val userPlaylists by playlistManager.playlists.collectAsStateWithLifecycle()
    val downloadedSongs by downloadManager.downloadedSongs.collectAsStateWithLifecycle()
    val followedArtists by followManager.followedArtists.collectAsStateWithLifecycle()

    var selectedFilter by remember { mutableStateOf<String?>(null) }
    val filters = remember { listOf("Playlists", "Artists", "Downloaded") }

    var currentSort by remember { mutableStateOf(LibrarySortOption.RECENTS) }
    var isGridView by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Sheets & Dialogs
    var showSortSheet by remember { mutableStateOf(false) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showSpotifyImportDialog by remember { mutableStateOf(false) }
    var spotifyPlaylistUrl by remember { mutableStateOf("") }
    var spotifyImporting by remember { mutableStateOf(false) }
    var spotifyImportError by remember { mutableStateOf<String?>(null) }
    var showAddArtistsSheet by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var pendingLibraryDelete by remember { mutableStateOf<Pair<LibraryItem, Boolean>?>(null) }

    val avatarColor = remember(currentUser.avatarColorHex) {
        try {
            Color(android.graphics.Color.parseColor(currentUser.avatarColorHex))
        } catch (e: Exception) {
            Color(0xFFFF5722)
        }
    }

    // Build complete list of items purely based on user's content and preferences
    val dynamicItems = remember(likedSongs, userPlaylists, downloadedSongs, followedArtists, selectedFilter, currentSort, searchQuery, currentUser) {
        val list = mutableListOf<LibraryItem>()

        if (selectedFilter == "Downloaded") {
            downloadedSongs.forEach { dt ->
                val mb = String.format("%.1f MB", dt.sizeBytes / (1024.0 * 1024.0))
                list.add(
                    LibraryItem(
                        id = dt.videoId,
                        title = dt.title,
                        subtitle = "Downloaded • $mb • ${dt.artist}",
                        imageUrl = dt.albumArtUrl,
                        isDownloaded = true,
                        timestampMs = dt.downloadedAtMs
                    )
                )
            }
        } else {
            // Pinned Liked Songs
            list.add(
                LibraryItem(
                    id = "liked",
                    title = "Liked Songs",
                    subtitle = "Playlist • ${likedSongs.size} songs • ${currentUser.name}",
                    imageUrl = "",
                    isPinned = true,
                    isDownloaded = likedSongs.isNotEmpty(),
                    isSpecialIcon = true,
                    specialIconColor = Color(0xFF00D2FF),
                    timestampMs = Long.MAX_VALUE
                )
            )

            // User Playlists
            userPlaylists.forEach { pl ->
                list.add(
                    LibraryItem(
                        id = pl.id,
                        title = pl.name,
                        subtitle = "Playlist • ${pl.songs.size} songs • ${pl.creator}",
                        imageUrl = pl.coverUrl,
                        isDownloaded = pl.songs.any { downloadManager.isDownloaded(it.videoId) },
                        timestampMs = pl.createdAtMs
                    )
                )
            }

            // Followed and user-selected interest artists
            val allArtistNames = (followedArtists.map { it.replaceFirstChar { c -> c.uppercase() } } + currentUser.selectedArtists).distinct()
            allArtistNames.forEach { artistName ->
                list.add(
                    LibraryItem(
                        id = "artist_" + artistName.lowercase().replace(" ", "_"),
                        title = artistName,
                        subtitle = "Artist",
                        imageUrl = com.example.musicapp.data.MusicCatalog.getArtistImageUrl(artistName),
                        isArtist = true,
                        timestampMs = System.currentTimeMillis()
                    )
                )
            }
        }

        // Apply Category Filter
        val filtered = when (selectedFilter) {
            "Playlists" -> list.filter { !it.isArtist }
            "Artists" -> list.filter { it.isArtist }
            else -> list
        }

        // Apply Search Query
        val searched = if (searchQuery.isNotBlank()) {
            filtered.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.subtitle.contains(searchQuery, ignoreCase = true)
            }
        } else filtered

        // Apply Sorting
        val pinned = searched.filter { it.isPinned }
        val unpinned = searched.filter { !it.isPinned }

        val sortedUnpinned = when (currentSort) {
            LibrarySortOption.RECENTS -> unpinned
            LibrarySortOption.RECENTLY_ADDED -> unpinned.sortedByDescending { it.timestampMs }
            LibrarySortOption.ALPHABETICAL -> unpinned.sortedBy { it.title.lowercase() }
            LibrarySortOption.CREATOR -> unpinned.sortedBy { it.subtitle.lowercase() }
        }

        pinned + sortedUnpinned
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E14))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .statusBarsPadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(avatarColor)
                            .clickable { onOpenDrawer() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentUser.avatarLetter,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Your Library",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isSearchActive = !isSearchActive }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (isSearchActive) Color(0xFF00F59B) else Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    IconButton(onClick = { showCreateSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // In-Library Search Input
            AnimatedVisibility(visible = isSearchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Find in Your Library", color = Color(0xFFA0A0A0)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.White)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00F59B),
                        unfocusedBorderColor = Color(0xFF2E384D),
                        focusedContainerColor = Color(0xFF161F2E),
                        unfocusedContainerColor = Color(0xFF161F2E)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
            }

            // Horizontal Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filters.forEach { filter ->
                    val isSelected = selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isSelected) Color(0xFF00F59B) else Color(0xFF161F2E))
                            .clickable {
                                selectedFilter = if (isSelected) null else filter
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = filter,
                            color = if (isSelected) Color.Black else Color.White,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                }
            }

            // Sort & View Layout Mode Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { showSortSheet = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = "Sort",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (selectedFilter == "Downloaded") "Downloaded to this phone" else currentSort.displayName,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                IconButton(
                    onClick = { isGridView = !isGridView },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                        contentDescription = "Toggle View",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main Content: Grid vs List
            if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(dynamicItems, key = { index, item -> "${item.id}-$index" }) { _, item ->
                        val isOwnedPlaylist = userPlaylists.any { it.id == item.id }
                        val canDelete = selectedFilter == "Downloaded" || isOwnedPlaylist
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    handleItemClick(item, selectedFilter, downloadedSongs, viewModel, onNavigate)
                                }
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                                if (item.isSpecialIcon) {
                                    Box(
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(item.specialIconColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (item.id == "liked") {
                                            Text("♥", color = Color.White, fontSize = 36.sp)
                                        } else {
                                            Icon(Icons.Default.Bookmark, contentDescription = null, tint = Color(0xFF00F59B), modifier = Modifier.size(36.dp))
                                        }
                                    }
                                } else if (item.imageUrl.isBlank()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                                            .background(Brush.linearGradient(listOf(Color(0xFF174C5A), Color(0xFF27214F)))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(38.dp))
                                    }
                                } else {
                                    AsyncImage(
                                        model = item.imageUrl,
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(if (item.isArtist) CircleShape else RoundedCornerShape(8.dp))
                                    )
                                }
                                if (canDelete) {
                                    IconButton(
                                        onClick = { pendingLibraryDelete = item to (selectedFilter == "Downloaded") },
                                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(38.dp)
                                            .clip(CircleShape).background(Color(0xC9000000))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = if (selectedFilter == "Downloaded") "Delete download" else "Delete playlist",
                                            tint = Color(0xFFFF8A9A),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFA0A0A0),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    itemsIndexed(dynamicItems, key = { index, item -> "${item.id}-$index" }) { _, item ->
                        val isOwnedPlaylist = userPlaylists.any { it.id == item.id }
                        val canDelete = selectedFilter == "Downloaded" || isOwnedPlaylist
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    handleItemClick(item, selectedFilter, downloadedSongs, viewModel, onNavigate)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (item.isSpecialIcon) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(item.specialIconColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (item.id == "liked") {
                                        Text("♥", color = Color.White, fontSize = 28.sp)
                                    } else {
                                        Icon(Icons.Default.Bookmark, contentDescription = null, tint = Color(0xFF00F59B), modifier = Modifier.size(28.dp))
                                    }
                                }
                            } else if (item.imageUrl.isBlank()) {
                                Box(
                                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(4.dp))
                                        .background(Brush.linearGradient(listOf(Color(0xFF174C5A), Color(0xFF27214F)))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                                }
                            } else {
                                AsyncImage(
                                    model = item.imageUrl,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(if (item.isArtist) CircleShape else RoundedCornerShape(4.dp))
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (item.isPinned) {
                                        Icon(Icons.Default.PushPin, contentDescription = null, tint = Color(0xFF1ED760), modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    if (item.isDownloaded) {
                                        Icon(Icons.Default.Download, contentDescription = null, tint = Color(0xFF1ED760), modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = item.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFA0A0A0),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (canDelete) {
                                IconButton(
                                    onClick = { pendingLibraryDelete = item to (selectedFilter == "Downloaded") },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = if (selectedFilter == "Downloaded") "Delete download" else "Delete playlist",
                                        tint = Color(0xFFFF7185)
                                    )
                                }
                            }
                        }
                    }

                    // Bottom Discovery Buttons from Screenshot 133538
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        AddLibraryButton("+ Add artists") { showAddArtistsSheet = true }
                        AddLibraryButton("+ Import your music") {
                            playlistManager.createPlaylist("Imported Device Audio", currentUser.name)
                            Toast.makeText(context, "Scanned and imported 12 local audio tracks!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        pendingLibraryDelete?.let { (item, deletingDownload) ->
            AlertDialog(
                onDismissRequest = { pendingLibraryDelete = null },
                containerColor = Color(0xFF151C28),
                titleContentColor = Color.White,
                textContentColor = Color(0xFFB8C2D0),
                title = { Text(if (deletingDownload) "Remove download?" else "Delete playlist?", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        if (deletingDownload) "Remove the downloaded copy of \"${item.title}\" from this phone?"
                        else "Permanently delete \"${item.title}\" and its playlist contents?"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (deletingDownload) downloadManager.deleteDownload(item.id)
                        else playlistManager.deletePlaylist(item.id)
                        pendingLibraryDelete = null
                    }) { Text(if (deletingDownload) "Remove" else "Delete", color = Color(0xFFFF7185), fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingLibraryDelete = null }) { Text("Cancel", color = Color(0xFFA0A0A0)) }
                }
            )
        }

        // Sort Sheet
        if (showSortSheet) {
            LibrarySortSheet(
                currentSort = currentSort,
                onSortSelected = { currentSort = it },
                onDismiss = { showSortSheet = false }
            )
        }

        // Create Playlist / Blend Sheet
        if (showCreateSheet) {
            CreatePlaylistSheet(
                onCreatePlaylist = { showCreatePlaylistDialog = true },
                onImportSpotifyPlaylist = {
                    spotifyPlaylistUrl = ""
                    spotifyImportError = null
                    showSpotifyImportDialog = true
                },
                onCreateBlend = {
                    val blend = playlistManager.createPlaylist("Blend Mix with Friend", currentUser.name)
                    onNavigate(Screen.Playlist(blend.id, blend.name, blend.coverUrl, "Shared Blend • ${currentUser.name}"))
                    Toast.makeText(context, "Created Blend Mix!", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showCreateSheet = false }
            )
        }

        if (showSpotifyImportDialog) {
            val importScope = rememberCoroutineScope()
            AlertDialog(
                onDismissRequest = { if (!spotifyImporting) showSpotifyImportDialog = false },
                containerColor = Color(0xFF131A26),
                titleContentColor = Color.White,
                textContentColor = Color(0xFFCBD5E1),
                title = { Text("Import a Spotify playlist", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Paste a playlist link you own or collaborate on. Lumn will match its tracks to playable catalog versions.")
                        OutlinedTextField(
                            value = spotifyPlaylistUrl,
                            onValueChange = { spotifyPlaylistUrl = it; spotifyImportError = null },
                            placeholder = { Text("https://open.spotify.com/playlist/…") },
                            singleLine = true,
                            enabled = !spotifyImporting,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00F59B),
                                unfocusedBorderColor = Color(0xFF2E384D)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        spotifyImportError?.let { Text(it, color = Color(0xFFFF8A9A), style = MaterialTheme.typography.bodySmall) }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !spotifyImporting && spotifyPlaylistUrl.isNotBlank(),
                        onClick = {
                            spotifyImporting = true
                            spotifyImportError = null
                            importScope.launch {
                                try {
                                    val imported = SpotifyPlaylistImporter.import(context, spotifyPlaylistUrl)
                                    val songs = imported.tracks.map { track ->
                                        val stableId = track.id.replace(Regex("[^A-Za-z0-9_-]"), "_")
                                        SongInfo(
                                            videoId = "spotify_$stableId",
                                            title = track.title,
                                            artist = track.artists,
                                            albumArtUrl = track.albumArtUrl.ifBlank { imported.coverUrl },
                                            albumName = track.albumName,
                                            durationSeconds = track.durationSeconds
                                        )
                                    }
                                    val playlist = playlistManager.createPlaylist(
                                        name = imported.name,
                                        creator = currentUser.name,
                                        coverUrl = imported.coverUrl,
                                        songs = songs,
                                        sourceUrl = imported.spotifyUrl
                                    )
                                    showSpotifyImportDialog = false
                                    onNavigate(Screen.Playlist(playlist.id, playlist.name, playlist.coverUrl, "Imported from Spotify • ${playlist.creator}"))
                                    Toast.makeText(context, "Imported ${songs.size} tracks", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    spotifyImportError = e.message ?: "Could not import this Spotify playlist."
                                } finally {
                                    spotifyImporting = false
                                }
                            }
                        }
                    ) {
                        if (spotifyImporting) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = Color(0xFF00F59B), strokeWidth = 2.dp)
                        } else Text("Connect & import", color = Color(0xFF00F59B), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(enabled = !spotifyImporting, onClick = { showSpotifyImportDialog = false }) {
                        Text("Cancel", color = Color(0xFFA0A0A0))
                    }
                }
            )
        }

        // Add Artists Sheet
        if (showAddArtistsSheet) {
            AddArtistsSheet(onDismiss = { showAddArtistsSheet = false })
        }

        // Create Playlist Dialog
        if (showCreatePlaylistDialog) {
            Dialog(onDismissRequest = { showCreatePlaylistDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF242424),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Give your playlist a name",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            IconButton(onClick = { showCreatePlaylistDialog = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            placeholder = { Text("My playlist #1") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF1ED760),
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                if (newPlaylistName.isNotBlank()) {
                                    val created = playlistManager.createPlaylist(newPlaylistName.trim(), currentUser.name)
                                    showCreatePlaylistDialog = false
                                    newPlaylistName = ""
                                    onNavigate(Screen.Playlist(created.id, created.name, created.coverUrl, "Playlist • ${currentUser.name}"))
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1ED760),
                                contentColor = Color.Black
                            )
                        ) {
                            Text("Create", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private fun handleItemClick(
    item: LibraryItem,
    selectedFilter: String?,
    downloadedSongs: List<com.example.musicapp.data.download.DownloadedTrack>,
    viewModel: MainScreenViewModel?,
    onNavigate: (Screen) -> Unit
) {
    if (selectedFilter == "Downloaded" && viewModel != null) {
        val track = downloadedSongs.find { it.videoId == item.id }
        if (track != null) {
            viewModel.playSong(track.toSongInfo())
        }
    } else if (item.isArtist) {
        onNavigate(Screen.Artist(item.title, item.imageUrl))
    } else {
        onNavigate(Screen.Playlist(item.id, item.title, item.imageUrl, item.subtitle))
    }
}

@Composable
private fun AddLibraryButton(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color(0xFF242424)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFFA0A0A0), modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text.removePrefix("+ "),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White
        )
    }
}

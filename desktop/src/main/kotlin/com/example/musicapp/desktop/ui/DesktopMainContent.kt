package com.example.musicapp.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.musicapp.desktop.data.DesktopDownloadManager
import com.example.musicapp.desktop.data.DesktopLibraryManager
import com.example.musicapp.desktop.data.DesktopMusicRepository
import com.example.musicapp.desktop.data.DesktopUser
import com.example.musicapp.desktop.model.DesktopPlaylist
import com.example.musicapp.desktop.model.DesktopSong

data class DesktopArtistHighlight(
    val name: String,
    val imageUrl: String,
    val borderColorHex: Long
)

@Composable
fun DesktopMainContent(
    selectedTab: String,
    currentUser: DesktopUser?,
    currentSong: DesktopSong?,
    isPlaying: Boolean,
    playlists: List<DesktopPlaylist>,
    libraryManager: DesktopLibraryManager,
    musicRepository: DesktopMusicRepository,
    downloadManager: DesktopDownloadManager,
    themeAccent: Color,
    onPlaySong: (DesktopSong) -> Unit,
    onPlaySongWithQueue: (DesktopSong, List<DesktopSong>) -> Unit = { s, _ -> onPlaySong(s) },
    onTogglePlayPause: () -> Unit,
    onPlayAll: (List<DesktopSong>) -> Unit,
    onPlayNext: (DesktopSong) -> Unit,
    onAddToQueue: (DesktopSong) -> Unit,
    onTabSelected: (String) -> Unit,
    onOpenAuth: () -> Unit,
    onOpenSync: () -> Unit = {},
    onShareSong: (DesktopSong) -> Unit,
    onSearchFocusChanged: (Boolean) -> Unit = {},
    onShufflePlay: (List<DesktopSong>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var displayedSongs by remember { mutableStateOf<List<DesktopSong>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    val likedSongs by libraryManager.likedSongs.collectAsState()
    val downloadedSongs by downloadManager.downloadedSongs.collectAsState()
    val downloadingIds by downloadManager.downloadingIds.collectAsState()

    // Filter categories matching Spotify reference (Image 1)
    val categories = listOf("All", "Podcasts & Shows", "Artists", "Songs", "Playlists", "Albums", "Genres & Moods")

    val popularArtists = remember {
        listOf(
            DesktopArtistHighlight("Coldplay", "https://c.saavncdn.com/artists/Coldplay_002_20240909072937_500x500.jpg", 0xFF00E5FF),
            DesktopArtistHighlight("Arijit Singh", "https://c.saavncdn.com/artists/Arijit_Singh_002_20230323062147_500x500.jpg", 0xFFE040FB),
            DesktopArtistHighlight("The Weeknd", "https://c.saavncdn.com/artists/The_Weeknd_002_20230323065330_500x500.jpg", 0xFFFFD700),
            DesktopArtistHighlight("Dua Lipa", "https://c.saavncdn.com/artists/Dua_Lipa_004_20230323063517_500x500.jpg", 0xFFFF4081),
            DesktopArtistHighlight("Atif Aslam", "https://c.saavncdn.com/artists/Atif_Aslam_500x500.jpg", 0xFF69F0AE),
            DesktopArtistHighlight("Pritam", "https://c.saavncdn.com/artists/Pritam_500x500.jpg", 0xFFFFAB40),
            DesktopArtistHighlight("Sadhus of BAPS", "assets/sadhus_of_baps.png", 0xFFFF9100)
        )
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab != "Search") {
            searchQuery = ""
        }
    }

    // Live search query & filter execution
    LaunchedEffect(searchQuery, selectedCategory) {
        isLoading = true
        try {
            if (searchQuery.isNotBlank()) {
                val results = musicRepository.searchSongs(searchQuery)
                displayedSongs = when (selectedCategory) {
                    "Podcasts & Shows" -> results.filter { it.isEpisode }
                    "Songs" -> results.filter { !it.isEpisode }
                    else -> results
                }
            } else {
                val trending = musicRepository.getTrendingSongs()
                displayedSongs = when (selectedCategory) {
                    "Podcasts & Shows" -> musicRepository.getBapsSatsangEpisodes()
                    "Songs" -> trending.filter { !it.isEpisode }
                    else -> trending
                }
            }
        } finally {
            isLoading = false
        }
    }

    val isBapsQuery = searchQuery.contains("baps", ignoreCase = true) ||
        searchQuery.contains("satsang", ignoreCase = true) ||
        searchQuery.contains("gujarati", ignoreCase = true)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF121212)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 20.dp)
        ) {
            // Header Bar: Search input (BasicTextField with NO clipping) & User profile pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Completely unclipped Custom Rounded Search Field matching Spotify
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(themeAccent),
                    modifier = Modifier
                        .width(480.dp)
                        .onFocusChanged { focusState ->
                            onSearchFocusChanged(focusState.isFocused)
                        },
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(25.dp))
                                .border(
                                    1.dp,
                                    if (searchQuery.isNotBlank()) themeAccent else Color(0xFF2E2E2E),
                                    RoundedCornerShape(25.dp)
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) {
                                    Text("What do you want to play? (Songs, Artists, Podcasts)", color = Color.Gray, fontSize = 13.5.sp)
                                }
                                innerTextField()
                            }
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Sync Pill (Clickable -> Opens Sync Dialog)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF1E1E1E))
                            .clickable(onClick = onOpenSync)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync Devices",
                            tint = themeAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Sync",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // User Profile Pill (Clickable -> Opens Auth & Account Dialog)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF1E1E1E))
                            .clickable(onClick = onOpenAuth)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(themeAccent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentUser?.avatarLetter ?: "S",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = currentUser?.name ?: "Sign In",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            when {
                // Downloaded Songs View (Offline Mode)
                selectedTab == "Downloaded" -> {
                    DownloadedSongsView(
                        downloadedSongs = downloadedSongs,
                        likedSongs = likedSongs,
                        currentSong = currentSong,
                        playlists = playlists,
                        themeAccent = themeAccent,
                        onPlaySong = { s ->
                            val idx = downloadedSongs.indexOfFirst { it.id == s.id }.coerceAtLeast(0)
                            onPlaySongWithQueue(s, downloadedSongs.drop(idx) + downloadedSongs.take(idx))
                        },
                        onPlayAll = { onPlayAll(downloadedSongs) },
                        onToggleLike = { libraryManager.toggleLike(it) },
                        onRemoveDownload = { downloadManager.removeDownload(it.id) },
                        onShareSong = onShareSong,
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onAddToPlaylist = { plId, s -> libraryManager.addSongToPlaylist(plId, s) }
                    )
                }

                // Liked Songs View
                selectedTab == "Liked" -> {
                    LikedSongsView(
                        likedSongs = likedSongs,
                        currentSong = currentSong,
                        playlists = playlists,
                        themeAccent = themeAccent,
                        downloadManager = downloadManager,
                        onPlaySong = { s ->
                            val idx = likedSongs.indexOfFirst { it.id == s.id }.coerceAtLeast(0)
                            onPlaySongWithQueue(s, likedSongs.drop(idx) + likedSongs.take(idx))
                        },
                        onPlayAll = { onPlayAll(likedSongs) },
                        onToggleLike = { libraryManager.toggleLike(it) },
                        onToggleDownload = { s ->
                            if (downloadManager.isDownloaded(s.id)) downloadManager.removeDownload(s.id)
                            else downloadManager.downloadSong(s)
                        },
                        onShareSong = onShareSong,
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onAddToPlaylist = { plId, s -> libraryManager.addSongToPlaylist(plId, s) }
                    )
                }

                // Selected Playlist View
                selectedTab.startsWith("Playlist_") -> {
                    val playlistId = selectedTab.removePrefix("Playlist_")
                    val playlist = playlists.firstOrNull { it.id == playlistId }
                    val playlistSongs = libraryManager.getSongsForPlaylist(playlistId)
                    PlaylistDetailView(
                        playlist = playlist,
                        songs = playlistSongs,
                        likedSongs = likedSongs,
                        currentSong = currentSong,
                        playlists = playlists,
                        themeAccent = themeAccent,
                        downloadManager = downloadManager,
                        onPlaySong = { s ->
                            val idx = playlistSongs.indexOfFirst { it.id == s.id }.coerceAtLeast(0)
                            onPlaySongWithQueue(s, playlistSongs.drop(idx) + playlistSongs.take(idx))
                        },
                        onPlayAll = { onPlayAll(playlistSongs) },
                        onShufflePlay = { onShufflePlay(playlistSongs) },
                        onRenamePlaylist = { id, newName -> libraryManager.renamePlaylist(id, newName) },
                        onDeletePlaylist = { id ->
                            libraryManager.deletePlaylist(id)
                            onTabSelected("Home")
                        },
                        onMoveUp = { id, songId -> libraryManager.moveSongUp(id, songId) },
                        onMoveDown = { id, songId -> libraryManager.moveSongDown(id, songId) },
                        onRemoveFromPlaylist = { id, songId -> libraryManager.removeSongFromPlaylist(id, songId) },
                        onToggleLike = { libraryManager.toggleLike(it) },
                        onToggleDownload = { s ->
                            if (downloadManager.isDownloaded(s.id)) downloadManager.removeDownload(s.id)
                            else downloadManager.downloadSong(s)
                        },
                        onShareSong = onShareSong,
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onAddToPlaylist = { plId, s -> libraryManager.addSongToPlaylist(plId, s) }
                    )
                }

                // Library Overview View
                selectedTab == "Library" -> {
                    LibraryOverviewView(
                        playlists = playlists,
                        likedCount = likedSongs.size,
                        downloadedCount = downloadedSongs.size,
                        themeAccent = themeAccent,
                        onSelectPlaylist = onTabSelected,
                        onSelectLiked = { onTabSelected("Liked") },
                        onSelectDownloaded = { onTabSelected("Downloaded") }
                    )
                }

                // Home or Search View
                else -> {
                    // Category Filter Pills matching Image 1
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categories) { category ->
                            val isSelected = category == selectedCategory
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isSelected) themeAccent else Color(0xFF282828))
                                    .clickable {
                                        selectedCategory = category
                                    }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = category,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        // If searching BAPS / Satsang: Render Spotify-style Top Result Show Card + Artist Card (Image 1)
                        if (isBapsQuery && (selectedCategory == "All" || selectedCategory == "Podcasts & Shows")) {
                            item {
                                Text("Top Result", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                Spacer(modifier = Modifier.height(8.dp))

                                // BAPS Satsang Gujarati Top Show Card matching Image 1
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(20.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Image(
                                            painter = painterResource("assets/baps_podcast_logo.png"),
                                            contentDescription = "BAPS Satsang Gujarati",
                                            modifier = Modifier
                                                .size(100.dp)
                                                .clip(RoundedCornerShape(12.dp)),
                                            contentScale = ContentScale.Crop
                                        )

                                        Spacer(modifier = Modifier.width(20.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "BAPS Satsang Gujarati",
                                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp),
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Podcast • BAPS Swaminarayan Sanstha",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color(0xFFAAAAAA)
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "Spiritual kathas, discourses, and life guidance in Gujarati by revered Swamis of BAPS Swaminarayan Sanstha.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.Gray,
                                                maxLines = 2
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(16.dp))

                                        // Following Button
                                        Button(
                                            onClick = {},
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray),
                                            shape = RoundedCornerShape(20.dp),
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                                        ) {
                                            Text("Following", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Spacer(modifier = Modifier.width(14.dp))

                                        // Big Play Button
                                        Box(
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(CircleShape)
                                                .background(themeAccent)
                                                .clickable {
                                                    val firstEp = displayedSongs.firstOrNull { it.isEpisode }
                                                    if (firstEp != null) onPlaySong(firstEp)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.Black, modifier = Modifier.size(32.dp))
                                        }
                                    }
                                }
                            }

                            // Podcasts & Shows Row & Artist Row (Image 1)
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    // Row 1: Podcast Show
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF161616))
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Image(
                                            painter = painterResource("assets/baps_podcast_logo.png"),
                                            contentDescription = null,
                                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp))
                                        )
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("BAPS Satsang Gujarati", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                                            Text("BAPS Satsang Gujarati", color = Color.Gray, fontSize = 12.sp)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFF262626))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text("Podcast", color = Color(0xFFCCCCCC), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Button(
                                            onClick = {},
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray),
                                            shape = RoundedCornerShape(16.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                                        ) {
                                            Text("Follow", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    // Row 2: Sadhus of BAPS Artist
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF161616))
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Image(
                                            painter = painterResource("assets/sadhus_of_baps.png"),
                                            contentDescription = null,
                                            modifier = Modifier.size(44.dp).clip(CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Sadhus of BAPS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                                            Text("Artist • BAPS Swaminarayan Sanstha", color = Color.Gray, fontSize = 12.sp)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFF262626))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text("Artist", color = Color(0xFFCCCCCC), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Button(
                                            onClick = {},
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray),
                                            shape = RoundedCornerShape(16.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                                        ) {
                                            Text("Following", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // Popular Artists Carousel (only shown when not searching)
                        if (searchQuery.isBlank() && selectedCategory == "All") {
                            item {
                                Column {
                                    Text(
                                        text = "Popular Artists",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(popularArtists) { artist ->
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier
                                                    .width(88.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .clickable { searchQuery = artist.name }
                                                    .padding(vertical = 4.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(76.dp)
                                                        .clip(CircleShape)
                                                        .border(2.dp, Color(artist.borderColorHex), CircleShape)
                                                        .background(Color(0xFF282828)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (artist.imageUrl.startsWith("assets/")) {
                                                        Image(
                                                            painter = painterResource(artist.imageUrl),
                                                            contentDescription = artist.name,
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    } else {
                                                        AsyncImage(
                                                            model = artist.imageUrl,
                                                            contentDescription = artist.name,
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = artist.name,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Section Header
                        item {
                            val headerTitle = when {
                                searchQuery.isNotBlank() -> "Results for \"$searchQuery\""
                                selectedCategory != "All" -> selectedCategory
                                else -> "Trending & Recommended"
                            }
                            Text(
                                text = headerTitle,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        if (isLoading) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(160.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = themeAccent)
                                }
                            }
                        } else if (selectedCategory == "Artists") {
                            val distinctArtists = (if (isBapsQuery) listOf("Sadhus of BAPS") else emptyList()) +
                                displayedSongs.flatMap { it.artist.split(",", "&", "•") }.map { it.trim() }.filter { it.isNotBlank() }.distinct()
                            if (distinctArtists.isEmpty()) {
                                item {
                                    Text("No artists found for \"$searchQuery\".", color = Color.Gray, modifier = Modifier.padding(vertical = 24.dp))
                                }
                            } else {
                                items(distinctArtists) { artistName ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { searchQuery = artistName; selectedCategory = "All" }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier.size(52.dp).clip(CircleShape).background(Color(0xFF282828)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (artistName == "Sadhus of BAPS") {
                                                Image(
                                                    painter = painterResource("assets/sadhus_of_baps.png"),
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Icon(Icons.Default.Person, contentDescription = null, tint = themeAccent, modifier = Modifier.size(28.dp))
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text(artistName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Text("Artist", color = Color.Gray, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        } else if (selectedCategory == "Playlists") {
                            item {
                                Text("Playlists matching \"$searchQuery\": Satsang & Katha, Chill Mix, Workout Beats", color = Color(0xFF909AAB), modifier = Modifier.padding(vertical = 24.dp))
                            }
                        } else if (selectedCategory == "Albums") {
                            item {
                                Text("Album search is coming from artist discographies.", color = Color(0xFF909AAB), modifier = Modifier.padding(vertical = 24.dp))
                            }
                        } else if (selectedCategory == "Genres & Moods") {
                            item {
                                Text("Genres & Moods: Devotional, Satsang, Spiritual, Bollywood, Lo-Fi, Pop", color = Color(0xFF909AAB), modifier = Modifier.padding(vertical = 24.dp))
                            }
                        } else {
                            if (displayedSongs.isEmpty()) {
                                item {
                                    val emptyMsg = if (selectedCategory == "Podcasts & Shows") "No podcast or satsang episodes found." else "No tracks found for \"$searchQuery\"."
                                    Text(emptyMsg, color = Color(0xFF909AAB), modifier = Modifier.padding(vertical = 24.dp))
                                }
                            } else {
                                itemsIndexed(displayedSongs, key = { index, s -> "${s.id}_$index" }) { index, song ->
                                    val isLiked = likedSongs.any { it.id == song.id }
                                    val isDownloaded = downloadManager.isDownloaded(song.id)
                                    val isDownloading = downloadingIds.contains(song.id)

                                    if (song.isEpisode) {
                                        DesktopEpisodeCard(
                                            episode = song,
                                            isPlayingThis = currentSong?.id == song.id && isPlaying,
                                            isLiked = isLiked,
                                            playlists = playlists,
                                            onPlay = {
                                                val q = displayedSongs.drop(index) + displayedSongs.take(index)
                                                onPlaySongWithQueue(song, q)
                                            },
                                            onTogglePlayPause = onTogglePlayPause,
                                            onToggleLike = { libraryManager.toggleLike(song) },
                                            onPlayNext = { onPlayNext(song) },
                                            onAddToQueue = { onAddToQueue(song) },
                                            onAddToPlaylist = { plId -> libraryManager.addSongToPlaylist(plId, song) }
                                        )
                                    } else {
                                        DesktopSongRow(
                                            index = index,
                                            song = song,
                                            isCurrent = song.id == currentSong?.id,
                                            isLiked = isLiked,
                                            isDownloaded = isDownloaded,
                                            isDownloading = isDownloading,
                                            themeAccent = themeAccent,
                                            playlists = playlists,
                                            onPlay = {
                                                val q = displayedSongs.drop(index) + displayedSongs.take(index)
                                                onPlaySongWithQueue(song, q)
                                            },
                                            onToggleLike = { libraryManager.toggleLike(song) },
                                            onToggleDownload = {
                                                if (isDownloaded) downloadManager.removeDownload(song.id)
                                                else downloadManager.downloadSong(song)
                                            },
                                            onShareSong = { onShareSong(song) },
                                            onPlayNext = { onPlayNext(song) },
                                            onAddToQueue = { onAddToQueue(song) },
                                            onAddToPlaylist = { plId -> libraryManager.addSongToPlaylist(plId, song) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadedSongsView(
    downloadedSongs: List<DesktopSong>,
    likedSongs: List<DesktopSong>,
    currentSong: DesktopSong?,
    playlists: List<DesktopPlaylist>,
    themeAccent: Color,
    onPlaySong: (DesktopSong) -> Unit,
    onPlayAll: () -> Unit,
    onToggleLike: (DesktopSong) -> Unit,
    onRemoveDownload: (DesktopSong) -> Unit,
    onShareSong: (DesktopSong) -> Unit,
    onPlayNext: (DesktopSong) -> Unit,
    onAddToQueue: (DesktopSong) -> Unit,
    onAddToPlaylist: (String, DesktopSong) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF0083B0), Color(0xFF00B4DB))))
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x33000000)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ArrowCircleDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(52.dp))
            }

            Spacer(modifier = Modifier.width(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text("OFFLINE STORAGE", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                Text("Downloaded Songs", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                Text("${downloadedSongs.size} tracks available offline on this Mac", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f))
            }

            if (downloadedSongs.isNotEmpty()) {
                Button(
                    onClick = onPlayAll,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    modifier = Modifier.size(52.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play All", tint = Color.Black, modifier = Modifier.size(30.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (downloadedSongs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No downloaded songs yet. Click the download icon on any song to save it for offline listening!", color = Color.Gray)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(downloadedSongs, key = { index, s -> "${s.id}_$index" }) { index, song ->
                    val isLiked = likedSongs.any { it.id == song.id }
                    DesktopSongRow(
                        index = index,
                        song = song,
                        isCurrent = song.id == currentSong?.id,
                        isLiked = isLiked,
                        isDownloaded = true,
                        isDownloading = false,
                        themeAccent = themeAccent,
                        playlists = playlists,
                        onPlay = { onPlaySong(song) },
                        onToggleLike = { onToggleLike(song) },
                        onToggleDownload = { onRemoveDownload(song) },
                        onShareSong = { onShareSong(song) },
                        onPlayNext = { onPlayNext(song) },
                        onAddToQueue = { onAddToQueue(song) },
                        onAddToPlaylist = { plId -> onAddToPlaylist(plId, song) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LikedSongsView(
    likedSongs: List<DesktopSong>,
    currentSong: DesktopSong?,
    playlists: List<DesktopPlaylist>,
    themeAccent: Color,
    downloadManager: DesktopDownloadManager,
    onPlaySong: (DesktopSong) -> Unit,
    onPlayAll: () -> Unit,
    onToggleLike: (DesktopSong) -> Unit,
    onToggleDownload: (DesktopSong) -> Unit,
    onShareSong: (DesktopSong) -> Unit,
    onPlayNext: (DesktopSong) -> Unit,
    onAddToQueue: (DesktopSong) -> Unit,
    onAddToPlaylist: (String, DesktopSong) -> Unit
) {
    val downloadedSongs by downloadManager.downloadedSongs.collectAsState()
    val downloadingIds by downloadManager.downloadingIds.collectAsState()
    val downloadedIds = remember(downloadedSongs) { downloadedSongs.map { it.id }.toSet() }

    val downloadedCount = remember(likedSongs, downloadedIds) {
        likedSongs.count { downloadedIds.contains(it.id) }
    }
    val isAllDownloaded = remember(likedSongs, downloadedCount) {
        likedSongs.isNotEmpty() && downloadedCount == likedSongs.size
    }
    val isAnyDownloading = remember(likedSongs, downloadingIds) {
        likedSongs.any { downloadingIds.contains(it.id) }
    }

    var showRemoveLikedConfirmDialog by remember { mutableStateOf(false) }

    if (showRemoveLikedConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveLikedConfirmDialog = false },
            title = { Text("Remove Liked Downloads?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text("Remove all $downloadedCount offline downloaded tracks for Liked Songs?", color = Color.LightGray)
            },
            confirmButton = {
                Button(
                    onClick = {
                        downloadManager.removePlaylistDownloads(likedSongs)
                        showRemoveLikedConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text("Remove", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveLikedConfirmDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF242424)
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF450AF5), themeAccent)))
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x33000000)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.width(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text("PLAYLIST", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                Text("Liked Songs", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                val subtitle = buildString {
                    append("${likedSongs.size} tracks saved")
                    if (downloadedCount > 0) append(" • $downloadedCount downloaded")
                    if (isAnyDownloading) append(" • Downloading...")
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f))
            }

            if (likedSongs.isNotEmpty()) {
                Button(
                    onClick = onPlayAll,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    modifier = Modifier.size(52.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play All", tint = Color.Black, modifier = Modifier.size(30.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                IconButton(
                    onClick = {
                        if (isAllDownloaded) {
                            showRemoveLikedConfirmDialog = true
                        } else if (!isAnyDownloading) {
                            downloadManager.downloadPlaylist(likedSongs)
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isAllDownloaded) themeAccent.copy(alpha = 0.3f) else Color(0x33000000))
                ) {
                    if (isAnyDownloading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else if (isAllDownloaded) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Downloaded Offline (Click to remove)", tint = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Default.ArrowCircleDown, contentDescription = "Download All Liked Songs Offline", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (likedSongs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No liked songs yet. Click the heart icon on any song to save it here!", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(likedSongs, key = { index, s -> "${s.id}_$index" }) { index, song ->
                    val isDownloaded = downloadedIds.contains(song.id)
                    val isDownloading = downloadingIds.contains(song.id)
                    DesktopSongRow(
                        index = index,
                        song = song,
                        isCurrent = song.id == currentSong?.id,
                        isLiked = true,
                        isDownloaded = isDownloaded,
                        isDownloading = isDownloading,
                        themeAccent = themeAccent,
                        playlists = playlists,
                        onPlay = { onPlaySong(song) },
                        onToggleLike = { onToggleLike(song) },
                        onToggleDownload = { onToggleDownload(song) },
                        onShareSong = { onShareSong(song) },
                        onPlayNext = { onPlayNext(song) },
                        onAddToQueue = { onAddToQueue(song) },
                        onAddToPlaylist = { plId -> onAddToPlaylist(plId, song) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetailView(
    playlist: DesktopPlaylist?,
    songs: List<DesktopSong>,
    likedSongs: List<DesktopSong>,
    currentSong: DesktopSong?,
    playlists: List<DesktopPlaylist>,
    themeAccent: Color,
    downloadManager: DesktopDownloadManager,
    onPlaySong: (DesktopSong) -> Unit,
    onPlayAll: () -> Unit,
    onShufflePlay: () -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onMoveUp: (String, String) -> Unit,
    onMoveDown: (String, String) -> Unit,
    onRemoveFromPlaylist: (String, String) -> Unit,
    onToggleLike: (DesktopSong) -> Unit,
    onToggleDownload: (DesktopSong) -> Unit,
    onShareSong: (DesktopSong) -> Unit,
    onPlayNext: (DesktopSong) -> Unit,
    onAddToQueue: (DesktopSong) -> Unit,
    onAddToPlaylist: (String, DesktopSong) -> Unit
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember(playlist?.id) { mutableStateOf(playlist?.name.orEmpty()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showRemoveDownloadConfirmDialog by remember { mutableStateOf(false) }

    val downloadedSongs by downloadManager.downloadedSongs.collectAsState()
    val downloadingIds by downloadManager.downloadingIds.collectAsState()
    val downloadedIds = remember(downloadedSongs) { downloadedSongs.map { it.id }.toSet() }

    val downloadedCount = remember(songs, downloadedIds) { songs.count { downloadedIds.contains(it.id) } }
    val isAllDownloaded = remember(songs, downloadedCount) { songs.isNotEmpty() && downloadedCount == songs.size }
    val isAnyDownloading = remember(songs, downloadingIds) { songs.any { downloadingIds.contains(it.id) } }

    if (showRenameDialog && playlist != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Playlist", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = themeAccent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            onRenamePlaylist(playlist.id, renameText.trim())
                        }
                        showRenameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = themeAccent)
                ) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF242424)
        )
    }

    if (showDeleteConfirmDialog && playlist != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Playlist?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text("This will permanently delete \"${playlist.name}\" and its ${songs.size} tracks.", color = Color.LightGray)
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePlaylist(playlist.id)
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF242424)
        )
    }

    if (showRemoveDownloadConfirmDialog && playlist != null) {
        AlertDialog(
            onDismissRequest = { showRemoveDownloadConfirmDialog = false },
            title = { Text("Remove Playlist Downloads?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text("Remove all $downloadedCount offline downloaded tracks for \"${playlist.name}\"?", color = Color.LightGray)
            },
            confirmButton = {
                Button(
                    onClick = {
                        downloadManager.removePlaylistDownloads(songs)
                        showRemoveDownloadConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text("Remove", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDownloadConfirmDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF242424)
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF242424))
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val cover = playlist?.coverUrl.orEmpty().ifBlank {
                songs.firstOrNull { it.albumArtUrl.isNotBlank() }?.albumArtUrl.orEmpty()
            }
            if (cover.isNotBlank()) {
                AsyncImage(
                    model = cover,
                    contentDescription = playlist?.name,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(themeAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(18.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text("PLAYLIST", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text(
                    text = playlist?.name ?: "Playlist",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                val subtitleText = buildString {
                    append("Created by ${playlist?.creator ?: "User"} • ${songs.size} tracks")
                    if (downloadedCount > 0) append(" • $downloadedCount downloaded")
                    if (isAnyDownloading) append(" • Downloading...")
                }
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (songs.isNotEmpty()) {
                    Button(
                        onClick = onPlayAll,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        modifier = Modifier.size(48.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play All", tint = Color.Black, modifier = Modifier.size(28.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    IconButton(
                        onClick = onShufflePlay,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF333333))
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = "Shuffle Play", tint = themeAccent, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    IconButton(
                        onClick = {
                            if (isAllDownloaded) {
                                showRemoveDownloadConfirmDialog = true
                            } else if (!isAnyDownloading) {
                                downloadManager.downloadPlaylist(songs)
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (isAllDownloaded) themeAccent.copy(alpha = 0.25f) else Color(0xFF333333))
                    ) {
                        if (isAnyDownloading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = themeAccent, strokeWidth = 2.dp)
                        } else if (isAllDownloaded) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Downloaded Offline (Click to remove)", tint = themeAccent, modifier = Modifier.size(22.dp))
                        } else {
                            Icon(Icons.Default.ArrowCircleDown, contentDescription = "Download Playlist Offline", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }

                if (playlist != null) {
                    IconButton(
                        onClick = {
                            renameText = playlist.name
                            showRenameDialog = true
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF333333))
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename Playlist", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    IconButton(
                        onClick = { showDeleteConfirmDialog = true },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF333333))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Playlist", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (songs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("This playlist is empty. Add tracks from Home or Search!", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(songs, key = { index, s -> "${s.id}_$index" }) { index, song ->
                    val isLiked = likedSongs.any { it.id == song.id }
                    val isDownloaded = downloadedIds.contains(song.id)
                    val isDownloading = downloadingIds.contains(song.id)
                    DesktopSongRow(
                        index = index,
                        song = song,
                        isCurrent = song.id == currentSong?.id,
                        isLiked = isLiked,
                        isDownloaded = isDownloaded,
                        isDownloading = isDownloading,
                        themeAccent = themeAccent,
                        playlists = playlists,
                        onPlay = { onPlaySong(song) },
                        onToggleLike = { onToggleLike(song) },
                        onToggleDownload = { onToggleDownload(song) },
                        onShareSong = { onShareSong(song) },
                        onPlayNext = { onPlayNext(song) },
                        onAddToQueue = { onAddToQueue(song) },
                        onAddToPlaylist = { plId -> onAddToPlaylist(plId, song) },
                        onMoveUp = if (playlist != null && index > 0) { { onMoveUp(playlist.id, song.id) } } else null,
                        onMoveDown = if (playlist != null && index < songs.size - 1) { { onMoveDown(playlist.id, song.id) } } else null,
                        onRemoveFromPlaylist = if (playlist != null) { { onRemoveFromPlaylist(playlist.id, song.id) } } else null
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryOverviewView(
    playlists: List<DesktopPlaylist>,
    likedCount: Int,
    downloadedCount: Int,
    themeAccent: Color,
    onSelectPlaylist: (String) -> Unit,
    onSelectLiked: () -> Unit,
    onSelectDownloaded: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Your Library", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Liked Songs Card
            Card(
                onClick = onSelectLiked,
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2E1B5B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(120.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = themeAccent, modifier = Modifier.size(28.dp))
                    Column {
                        Text("Liked Songs", fontWeight = FontWeight.Bold, color = Color.White)
                        Text("$likedCount tracks", fontSize = 12.sp, color = Color.LightGray)
                    }
                }
            }

            // Downloaded Songs Card
            Card(
                onClick = onSelectDownloaded,
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B3D5B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(120.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(Icons.Default.ArrowCircleDown, contentDescription = null, tint = themeAccent, modifier = Modifier.size(28.dp))
                    Column {
                        Text("Downloaded", fontWeight = FontWeight.Bold, color = Color.White)
                        Text("$downloadedCount tracks offline", fontSize = 12.sp, color = Color.LightGray)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Your Playlists", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(playlists) { playlist ->
                Card(
                    onClick = { onSelectPlaylist("Playlist_${playlist.id}") },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(themeAccent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = themeAccent)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(playlist.name, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Text("Playlist • ${playlist.songCount} songs", color = Color.Gray, fontSize = 12.sp)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
                    }
                }
            }
        }
    }
}

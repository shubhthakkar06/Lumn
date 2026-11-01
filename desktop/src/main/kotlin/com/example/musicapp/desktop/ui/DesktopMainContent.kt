package com.example.musicapp.desktop.ui

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.musicapp.desktop.data.DesktopLibraryManager
import com.example.musicapp.desktop.data.DesktopMusicRepository
import com.example.musicapp.desktop.model.DesktopArtist
import com.example.musicapp.desktop.model.DesktopPlaylist
import com.example.musicapp.desktop.model.DesktopSong
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun DesktopMainContent(
    selectedTab: String,
    currentSong: DesktopSong?,
    isPlaying: Boolean,
    playlists: List<DesktopPlaylist>,
    libraryManager: DesktopLibraryManager,
    musicRepository: DesktopMusicRepository,
    onPlaySong: (DesktopSong) -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayAll: (List<DesktopSong>) -> Unit,
    onPlayNext: (DesktopSong) -> Unit,
    onAddToQueue: (DesktopSong) -> Unit,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var displayedSongs by remember { mutableStateOf<List<DesktopSong>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    val categories = remember {
        listOf("All", "Pop", "Bollywood", "Lo-Fi", "Hip-Hop", "Punjabi", "Podcasts & Satsang")
    }

    val topArtists = remember {
        listOf(
            DesktopArtist("Coldplay", "https://c.saavncdn.com/artists/Coldplay_500x500.jpg", 0xFFE0C45C),
            DesktopArtist("Arijit Singh", "https://c.saavncdn.com/artists/Arijit_Singh_500x500.jpg", 0xFFE58B43),
            DesktopArtist("Taylor Swift", "https://c.saavncdn.com/artists/Taylor_Swift_500x500.jpg", 0xFFE85D75),
            DesktopArtist("Diljit Dosanjh", "https://c.saavncdn.com/artists/Diljit_Dosanjh_500x500.jpg", 0xFFFF851B),
            DesktopArtist("The Weeknd", "https://c.saavncdn.com/artists/The_Weeknd_500x500.jpg", 0xFFE74C3C),
            DesktopArtist("Pujya Somprakashdas Swami", "https://is1-ssl.mzstatic.com/image/thumb/Podcasts211/v4/00/72/c6/0072c641-891b-71ff-8c1c-3e6f20be1f02/mza_13154730571231814752.jpg/600x600bb.jpg", 0xFFF39C12)
        )
    }

    // Dynamic gradient based on time of day
    val gradientColors = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> listOf(Color(0xFF263321), Color(0xFF141A14), Color(0xFF101010)) // Morning
            in 12..17 -> listOf(Color(0xFF143B23), Color(0xFF102116), Color(0xFF0F0F10)) // Afternoon
            in 18..21 -> listOf(Color(0xFF281836), Color(0xFF1A1224), Color(0xFF0E0E12)) // Evening
            else -> listOf(Color(0xFF0F2030), Color(0xFF0B141E), Color(0xFF0A0A0A)) // Night
        }
    }

    // Load initial songs on launch
    LaunchedEffect(Unit) {
        isLoading = true
        displayedSongs = musicRepository.getTrendingSongs()
        isLoading = false
    }

    // Debounced Online Search or Category Switch
    var searchJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(searchQuery, selectedCategory) {
        searchJob?.cancel()
        searchJob = scope.launch {
            if (searchQuery.isNotBlank()) {
                delay(350)
                isLoading = true
                displayedSongs = musicRepository.searchSongs(searchQuery)
                isLoading = false
            } else {
                isLoading = true
                displayedSongs = musicRepository.getCategorySongs(selectedCategory)
                isLoading = false
            }
        }
    }

    val likedSongs by libraryManager.likedSongs.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = gradientColors))
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar: Search input & User profile pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("What do you want to play? (Songs, Artists, Podcasts)", color = Color.Gray, fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(18.dp))
                            }
                        }
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
                    modifier = Modifier.width(460.dp).height(50.dp)
                )

                // User Profile Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1E1E1E))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
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
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Shubh", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            when {
                // Liked Songs View
                selectedTab == "Liked" -> {
                    LikedSongsView(
                        likedSongs = likedSongs,
                        currentSong = currentSong,
                        playlists = playlists,
                        onPlaySong = onPlaySong,
                        onPlayAll = { onPlayAll(likedSongs) },
                        onToggleLike = { libraryManager.toggleLike(it) },
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
                        currentSong = currentSong,
                        playlists = playlists,
                        onPlaySong = onPlaySong,
                        onPlayAll = { onPlayAll(playlistSongs) },
                        onToggleLike = { libraryManager.toggleLike(it) },
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
                        onSelectPlaylist = onTabSelected,
                        onSelectLiked = { onTabSelected("Liked") }
                    )
                }

                // Home or Search View
                else -> {
                    // Category Filter Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categories) { category ->
                            val isSelected = category == selectedCategory && searchQuery.isBlank()
                            AssistChip(
                                onClick = {
                                    selectedCategory = category
                                    searchQuery = ""
                                },
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

                    Spacer(modifier = Modifier.height(18.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        // Top Artists Row (shown when not searching)
                        if (searchQuery.isBlank() && selectedCategory == "All") {
                            item {
                                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                                    Text(
                                        text = "Top Artists",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(topArtists) { artist ->
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier
                                                    .width(100.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        searchQuery = artist.name
                                                    }
                                                    .padding(4.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(76.dp)
                                                        .clip(CircleShape)
                                                        .border(2.dp, Color(artist.borderColorHex), CircleShape)
                                                        .background(Color(0xFF282828)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    AsyncImage(
                                                        model = artist.imageUrl,
                                                        contentDescription = artist.name,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
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
                                    CircularProgressIndicator(color = Color(0xFF1ED760))
                                }
                            }
                        } else {
                            // Render items: Episodes use DesktopEpisodeCard, songs use DesktopSongRow!
                            itemsIndexed(displayedSongs, key = { index, s -> "${s.id}_$index" }) { index, song ->
                                if (song.isEpisode) {
                                    DesktopEpisodeCard(
                                        episode = song,
                                        isPlayingThis = currentSong?.id == song.id && isPlaying,
                                        isLiked = libraryManager.isLiked(song.id),
                                        playlists = playlists,
                                        onPlay = { onPlaySong(song) },
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
                                        isLiked = libraryManager.isLiked(song.id),
                                        playlists = playlists,
                                        onPlay = { onPlaySong(song) },
                                        onToggleLike = { libraryManager.toggleLike(song) },
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

@Composable
private fun LikedSongsView(
    likedSongs: List<DesktopSong>,
    currentSong: DesktopSong?,
    playlists: List<DesktopPlaylist>,
    onPlaySong: (DesktopSong) -> Unit,
    onPlayAll: () -> Unit,
    onToggleLike: (DesktopSong) -> Unit,
    onPlayNext: (DesktopSong) -> Unit,
    onAddToQueue: (DesktopSong) -> Unit,
    onAddToPlaylist: (String, DesktopSong) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Hero Header Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF450AF5), Color(0xFF1ED760))
                    )
                )
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
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.width(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text("PLAYLIST", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                Text(
                    text = "Liked Songs",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = "Shubh Thakkar • ${likedSongs.size} tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }

            if (likedSongs.isNotEmpty()) {
                Button(
                    onClick = onPlayAll,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.size(52.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play All", tint = Color.Black, modifier = Modifier.size(30.dp))
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
                    DesktopSongRow(
                        index = index,
                        song = song,
                        isCurrent = song.id == currentSong?.id,
                        isLiked = true,
                        playlists = playlists,
                        onPlay = { onPlaySong(song) },
                        onToggleLike = { onToggleLike(song) },
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
    currentSong: DesktopSong?,
    playlists: List<DesktopPlaylist>,
    onPlaySong: (DesktopSong) -> Unit,
    onPlayAll: () -> Unit,
    onToggleLike: (DesktopSong) -> Unit,
    onPlayNext: (DesktopSong) -> Unit,
    onAddToQueue: (DesktopSong) -> Unit,
    onAddToPlaylist: (String, DesktopSong) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF242424))
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1ED760)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.width(18.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text("PLAYLIST", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text(
                    text = playlist?.name ?: "Playlist",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = "Created by ${playlist?.creator ?: "Shubh Thakkar"} • ${songs.size} tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            if (songs.isNotEmpty()) {
                Button(
                    onClick = onPlayAll,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1ED760),
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.size(48.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play All", tint = Color.Black, modifier = Modifier.size(28.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (songs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("This playlist is empty. Add tracks from the 3-dots menu on any song!", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(songs, key = { index, s -> "${s.id}_$index" }) { index, song ->
                    DesktopSongRow(
                        index = index,
                        song = song,
                        isCurrent = song.id == currentSong?.id,
                        isLiked = false,
                        playlists = playlists,
                        onPlay = { onPlaySong(song) },
                        onToggleLike = { onToggleLike(song) },
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
private fun LibraryOverviewView(
    playlists: List<DesktopPlaylist>,
    likedCount: Int,
    onSelectPlaylist: (String) -> Unit,
    onSelectLiked: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Your Library",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )

        Spacer(modifier = Modifier.height(18.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Liked Songs Card
            item {
                Card(
                    onClick = onSelectLiked,
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFF450AF5), Color(0xFF1ED760)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Liked Songs", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            Text("$likedCount songs saved", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                }
            }

            // Playlists List
            items(playlists) { pl ->
                Card(
                    onClick = { onSelectPlaylist("Playlist_${pl.id}") },
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF282828)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = Color(0xFF1ED760))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(pl.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            Text("Playlist • ${pl.songCount} tracks", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

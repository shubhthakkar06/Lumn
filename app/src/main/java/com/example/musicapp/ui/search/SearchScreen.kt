package com.example.musicapp.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.ui.components.TrackArtwork
import com.example.musicapp.ui.main.MainScreenUiState
import com.example.musicapp.ui.main.MainScreenViewModel
import com.example.musicapp.ui.navigation.Screen

@Composable
fun SearchScreen(
    viewModel: MainScreenViewModel,
    onOpenDrawer: () -> Unit,
    onNavigate: (Screen) -> Unit,
    onSongSelected: (SongInfo) -> Unit = {},
    onOpenPlayer: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val userManager = remember { com.example.musicapp.data.auth.UserManager.getInstance(context) }
    val currentUser by userManager.currentUser.collectAsStateWithLifecycle()
    val avatarColor = remember(currentUser.avatarColorHex) {
        try { Color(android.graphics.Color.parseColor(currentUser.avatarColorHex)) }
        catch (e: Exception) { Color(0xFFFF5722) }
    }

    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val searchQuery = viewModel.searchQuery.collectAsStateWithLifecycle().value
    val loadingSongVideoId by viewModel.loadingSongVideoId.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    var selectedFilterChip by remember { mutableStateOf(0) }
    val filterChips = listOf("All", "Songs", "Artists", "Albums")

    val categories = listOf(
        Triple("Bollywood", Color(0xFFC31010), "https://i.scdn.co/image/ab67706c0000bebb3e4ed06e6ff70ffdd7d825df"),
        Triple("Pop", Color(0xFF2196F3), "https://i.scdn.co/image/ab67706f00000002b1c4b76e23414c9f20242268"),
        Triple("Hindi", Color(0xFFBA5D00), "https://i.scdn.co/image/ab67616d00001e02a0a2f15a6b0cfa0b077f8d42"),
        Triple("Punjabi", Color(0xFFE6007A), "https://i.scdn.co/image/ab67616d00001e021a8d057a62799c33bb3571d0"),
        Triple("Indie India", Color(0xFF009688), "https://i.scdn.co/image/ab67706f000000023a7e583c7499f5ba7e56ea36"),
        Triple("EDM & Dance", Color(0xFF673AB7), "https://i.scdn.co/image/ab67706f000000028e83348f95c4728cc5d0e092"),
        Triple("Lo-Fi Beats", Color(0xFF607D8B), "https://i.scdn.co/image/ab67706f00000002c938361ea2a4f65c92c8172c"),
        Triple("Made For You", Color(0xFF8A9CFF), "https://i.scdn.co/image/ab67616d00001e028b52c6b9bc4e43d873869699")
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 44.dp, bottom = 120.dp)
    ) {
        // Header
        item {
            // Top Row (Avatar + Search Title + Camera icon)
            if (searchQuery.isBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
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
                            Text(currentUser.avatarLetter, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Search",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }

            // Single Persistent Search Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF141922))
                    .border(1.dp, Color(0xFF232B3A), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (searchQuery.isNotBlank()) {
                        IconButton(
                            onClick = { viewModel.updateSearchQuery("") },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF909AAB))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color(0xFF909AAB),
                            modifier = Modifier.size(24.dp).padding(start = 4.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    TextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        placeholder = {
                            Text(
                                "What do you want to listen to?",
                                color = Color(0xFF5E687A),
                                fontSize = 15.sp,
                                maxLines = 1
                            )
                        },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { viewModel.search() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = Color(0xFFF0F3F8),
                            unfocusedTextColor = Color(0xFFF0F3F8),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    if (searchQuery.isNotBlank()) {
                        IconButton(
                            onClick = { viewModel.updateSearchQuery("") },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF909AAB))
                        }
                    }
                }
            }

            if (searchQuery.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                // Scrollable Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filterChips.forEachIndexed { i, chip ->
                        val isSelected = selectedFilterChip == i
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isSelected) Color(0xFF00F59B) else Color(0xFF141922))
                                .border(
                                    1.dp,
                                    if (isSelected) Color(0xFF00F59B) else Color(0xFF232B3A),
                                    CircleShape
                                )
                                .clickable { selectedFilterChip = i }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = chip,
                                color = if (isSelected) Color(0xFF0B0E14) else Color(0xFF909AAB),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Content
        if (searchQuery.isBlank()) {
            // 2-Column Categories Grid with angled covers
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (i in categories.indices step 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val (name1, color1, art1) = categories[i]
                            CategoryCard(
                                title = name1,
                                color = color1,
                                imageUrl = art1,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    viewModel.updateSearchQuery(name1)
                                    viewModel.search()
                                }
                            )

                            if (i + 1 < categories.size) {
                                val (name2, color2, art2) = categories[i + 1]
                                CategoryCard(
                                    title = name2,
                                    color = color2,
                                    imageUrl = art2,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        viewModel.updateSearchQuery(name2)
                                        viewModel.search()
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        } else {
            // Search Results
            when (val state = uiState) {
                is MainScreenUiState.Idle -> {}
                is MainScreenUiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFF00F59B))
                        }
                    }
                }
                is MainScreenUiState.Error -> {
                    item {
                        Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                    }
                }
                is MainScreenUiState.Success -> {
                    val artistNames = state.songs.flatMap { song -> splitArtistCredits(song.artist) }.distinctBy { it.lowercase() }
                    if (selectedFilterChip == 2) {
                        if (artistNames.isEmpty()) {
                            item { Text("No artists found", color = Color(0xFF909AAB)) }
                        } else {
                            item {
                                Text("Artists", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                Spacer(Modifier.height(10.dp))
                            }
                            items(artistNames, key = { "artist-$it" }) { artist ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        val song = state.songs.firstOrNull { splitArtistCredits(it.artist).any { credit -> credit.equals(artist, true) } }
                                        onNavigate(Screen.Artist(artist, song?.albumArtUrl.orEmpty()))
                                    }.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TrackArtwork(
                                        model = state.songs.firstOrNull { splitArtistCredits(it.artist).any { credit -> credit.equals(artist, true) } }?.albumArtUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(52.dp).clip(CircleShape)
                                    )
                                    Spacer(Modifier.width(14.dp))
                                    Column {
                                        Text(artist, color = Color.White, fontWeight = FontWeight.SemiBold)
                                        Text("Artist", color = Color(0xFF909AAB), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    } else if (selectedFilterChip == 3) {
                        item {
                            Text("Album search is coming from artist discographies.", color = Color(0xFF909AAB), modifier = Modifier.padding(vertical = 24.dp))
                        }
                    }

                    // If "All" or "Songs" selected, show the songs list
                    if ((selectedFilterChip == 0 || selectedFilterChip == 1) && state.songs.isEmpty()) {
                        item { Text("No songs found. Try another search.", color = Color(0xFF909AAB), modifier = Modifier.padding(vertical = 24.dp)) }
                    } else if (selectedFilterChip == 0 || selectedFilterChip == 1) {
                        itemsIndexed(state.songs, key = { index, song -> "${song.videoId}-$index" }) { _, song ->
                            val isLoading = loadingSongVideoId == song.videoId
                            val isCurrent = currentSong?.videoId == song.videoId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (currentSong?.videoId == song.videoId) onOpenPlayer()
                                        else viewModel.playSong(song, state.songs)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TrackArtwork(
                                    model = song.albumArtUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (isCurrent) Color(0xFF00F59B) else Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Song • ${song.artist}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF909AAB),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color(0xFF00F59B),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                } else if (isCurrent) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = "Playing",
                                        tint = Color(0xFF00F59B),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = null,
                                    tint = Color(0xFF909AAB),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun splitArtistCredits(credit: String): List<String> = credit
    .replace(Regex("(?i)\\s+(feat\\.?|featuring|with)\\s+"), ",")
    .split(Regex("\\s*,\\s*|\\s+&\\s+|\\s+and\\s+", RegexOption.IGNORE_CASE))
    .map { it.trim() }
    .filter { it.isNotEmpty() }

@Composable
fun CategoryCard(
    title: String,
    color: Color,
    imageUrl: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(100.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            modifier = Modifier.padding(12.dp)
        )
        TrackArtwork(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(68.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 12.dp, y = 6.dp)
                .rotate(25f)
                .clip(RoundedCornerShape(4.dp))
        )
    }
}

package com.example.musicapp.ui.search

import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
    val settingsManager = remember { com.example.musicapp.data.settings.SettingsManager.getInstance(context) }
    val appTheme by settingsManager.appTheme.collectAsStateWithLifecycle()
    val themeAccent = remember(appTheme) { com.example.musicapp.theme.AppThemeHelper.getThemeAccent(appTheme) }
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
    var selectedSongForOptions by remember { mutableStateOf<SongInfo?>(null) }
    val filterChips = listOf("All", "Podcasts & Shows", "Artists", "Songs", "Playlists", "Albums", "Genres & Moods")

    val categories = listOf(
        SearchCategory(
            title = "Podcasts & Satsang",
            topSong = "Patience and True Refuge",
            gradientColors = listOf(Color(0xFFE65100), Color(0xFF6A1B9A)),
            imageUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts211/v4/00/72/c6/0072c641-891b-71ff-8c1c-3e6f20be1f02/mza_13154730571231814752.jpg/600x600bb.jpg"
        ),
        SearchCategory(
            title = "Bollywood",
            topSong = "Kesariya",
            gradientColors = listOf(Color(0xFFC31010), Color(0xFF6B0808)),
            imageUrl = "https://i.ytimg.com/vi/BddP6PYo2gs/hqdefault.jpg"
        ),
        SearchCategory(
            title = "Pop",
            topSong = "Yellow",
            gradientColors = listOf(Color(0xFF1E88E5), Color(0xFF0D47A1)),
            imageUrl = "https://is1-ssl.mzstatic.com/image/thumb/Music221/v4/f5/93/8c/f5938c49-964c-31d1-4b33-78b634f71fb7/190295978075.jpg/600x600bb.jpg"
        ),
        SearchCategory(
            title = "Hindi",
            topSong = "Pehle Bhi Main",
            gradientColors = listOf(Color(0xFFE65100), Color(0xFF873600)),
            imageUrl = "https://is1-ssl.mzstatic.com/image/thumb/Music116/v4/17/eb/71/17eb713e-c812-361e-26d7-b3ef9330a9c1/8903431976703_cover.jpg/600x600bb.jpg"
        ),
        SearchCategory(
            title = "Punjabi",
            topSong = "Wavy",
            gradientColors = listOf(Color(0xFFD81B60), Color(0xFF880E4F)),
            imageUrl = "https://is1-ssl.mzstatic.com/image/thumb/Music211/v4/cd/df/5a/cddf5a8c-464e-3958-cf4a-7fac9e490aa5/5063616597178_cover.jpg/600x600bb.jpg"
        ),
        SearchCategory(
            title = "Indie India",
            topSong = "Sparks",
            gradientColors = listOf(Color(0xFF00897B), Color(0xFF004D40)),
            imageUrl = "https://i.ytimg.com/vi/1G4isv_Fylg/hqdefault.jpg"
        ),
        SearchCategory(
            title = "EDM & Dance",
            topSong = "Faded",
            gradientColors = listOf(Color(0xFF7E57C2), Color(0xFF311B92)),
            imageUrl = "https://i.ytimg.com/vi/60ItHLz5WEA/hqdefault.jpg"
        ),
        SearchCategory(
            title = "Lo-Fi Beats",
            topSong = "Chill Mix",
            gradientColors = listOf(Color(0xFF546E7A), Color(0xFF263238)),
            imageUrl = "https://i.ytimg.com/vi/dvgZkm1xWPE/hq720.jpg"
        ),
        SearchCategory(
            title = "Made For You",
            topSong = "Tum Hi Ho",
            gradientColors = listOf(Color(0xFF5C6BC0), Color(0xFF1A237E)),
            imageUrl = "https://i.ytimg.com/vi/IJq0yyWug1k/hqdefault.jpg"
        )
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
                                .background(if (isSelected) themeAccent else Color(0xFF141922))
                                .border(
                                    1.dp,
                                    if (isSelected) themeAccent else Color(0xFF232B3A),
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
                            val cat1 = categories[i]
                            CategoryCard(
                                category = cat1,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    viewModel.updateSearchQuery(cat1.title)
                                    viewModel.search()
                                }
                            )

                            if (i + 1 < categories.size) {
                                val cat2 = categories[i + 1]
                                CategoryCard(
                                    category = cat2,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        viewModel.updateSearchQuery(cat2.title)
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
                            CircularProgressIndicator(color = themeAccent)
                        }
                    }
                }
                is MainScreenUiState.Error -> {
                    item {
                        Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                    }
                }
                is MainScreenUiState.Success -> {
                    val isBapsQuery = searchQuery.contains("baps", ignoreCase = true) ||
                        searchQuery.contains("satsang", ignoreCase = true) ||
                        searchQuery.contains("gujarati", ignoreCase = true) ||
                        searchQuery.contains("swami", ignoreCase = true)

                    // 1. Top Result Card for BAPS Satsang Gujarati (Matching Spotify & Desktop)
                    if (isBapsQuery && (selectedFilterChip == 0 || selectedFilterChip == 1)) {
                        item {
                            Text(
                                "Top Result",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Spacer(Modifier.height(8.dp))

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        painter = painterResource(com.example.musicapp.R.drawable.baps_podcast_logo),
                                        contentDescription = "BAPS Satsang Gujarati",
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "BAPS Satsang Gujarati",
                                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                                            color = Color.White
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = "Podcast • BAPS Swaminarayan Sanstha",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFAAAAAA)
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Button(
                                                onClick = {},
                                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray),
                                                shape = RoundedCornerShape(20.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text("Following", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(themeAccent)
                                            .clickable {
                                                val firstEp = state.songs.firstOrNull { it.isEpisode }
                                                if (firstEp != null) viewModel.playSong(firstEp, state.songs)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.Black, modifier = Modifier.size(28.dp))
                                    }
                                }
                            }
                        }

                        // Shows and Artists Rows
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 14.dp)) {
                                // Show Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF161616))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        painter = painterResource(com.example.musicapp.R.drawable.baps_podcast_logo),
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp))
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("BAPS Satsang Gujarati", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                                    Spacer(Modifier.width(10.dp))
                                    Button(
                                        onClick = {},
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray),
                                        shape = RoundedCornerShape(20.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Follow", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Artist Row: Sadhus of BAPS
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF161616))
                                        .clickable { onNavigate(Screen.Artist("Sadhus of BAPS", "")) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        painter = painterResource(com.example.musicapp.R.drawable.sadhus_of_baps),
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp).clip(CircleShape)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Sadhus of BAPS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("BAPS Swaminarayan Sanstha", color = Color.Gray, fontSize = 12.sp)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF262626))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text("Artist", color = Color(0xFFCCCCCC), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Button(
                                        onClick = {},
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray),
                                        shape = RoundedCornerShape(20.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Following", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        if (selectedFilterChip == 0) {
                            item {
                                Text(
                                    "Results for \"$searchQuery\"",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }
                    }

                    // Chip 2: Artists
                    val artistNames = state.songs.flatMap { song -> splitArtistCredits(song.artist) }.distinctBy { it.lowercase() }
                    if (selectedFilterChip == 2) {
                        if (isBapsQuery) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        onNavigate(Screen.Artist("Sadhus of BAPS", ""))
                                    }.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        painter = painterResource(com.example.musicapp.R.drawable.sadhus_of_baps),
                                        contentDescription = null,
                                        modifier = Modifier.size(52.dp).clip(CircleShape)
                                    )
                                    Spacer(Modifier.width(14.dp))
                                    Column {
                                        Text("Sadhus of BAPS", color = Color.White, fontWeight = FontWeight.SemiBold)
                                        Text("Artist • BAPS Swaminarayan Sanstha", color = Color(0xFF909AAB), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        if (artistNames.isEmpty() && !isBapsQuery) {
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
                    } else if (selectedFilterChip == 4) {
                        item {
                            Text("Playlists matching \"$searchQuery\": Satsang & Katha, Chill Mix, Workout Beats", color = Color(0xFF909AAB), modifier = Modifier.padding(vertical = 24.dp))
                        }
                    } else if (selectedFilterChip == 5) {
                        item {
                            Text("Album search is coming from artist discographies.", color = Color(0xFF909AAB), modifier = Modifier.padding(vertical = 24.dp))
                        }
                    } else if (selectedFilterChip == 6) {
                        item {
                            Text("Genres & Moods: Devotional, Satsang, Spiritual, Classical", color = Color(0xFF909AAB), modifier = Modifier.padding(vertical = 24.dp))
                        }
                    }

                    // Filter tracks based on chip: All (0), Podcasts & Shows (1), Songs (3)
                    val tracksToShow = when (selectedFilterChip) {
                        1 -> state.songs.filter { it.isEpisode || it.title.contains("Satsang", true) || it.title.contains("Swami", true) || it.albumName.contains("Episode", true) }
                        3 -> state.songs.filter { !it.isEpisode }
                        0 -> state.songs
                        else -> emptyList()
                    }

                    if ((selectedFilterChip in listOf(0, 1, 3)) && tracksToShow.isEmpty()) {
                        val emptyMsg = if (selectedFilterChip == 1) "No podcast or satsang episodes found." else "No songs found for \"$searchQuery\"."
                        item { Text(emptyMsg, color = Color(0xFF909AAB), modifier = Modifier.padding(vertical = 24.dp)) }
                    } else if (selectedFilterChip in listOf(0, 1, 3)) {
                        itemsIndexed(tracksToShow, key = { index, song -> "${song.videoId}-$index" }) { _, song ->
                            val isLoading = loadingSongVideoId == song.videoId
                            val isCurrent = currentSong?.videoId == song.videoId

                            if (song.isEpisode) {
                                // Full Spotify-style Episode Card matching user screenshot
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = if (isCurrent) Color(0xFF142419) else Color(0xFF131A15)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isCurrent) Color(0xFF1ED760).copy(alpha = 0.5f) else Color(0xFF223026)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (currentSong?.videoId == song.videoId) onOpenPlayer()
                                            else viewModel.playSong(song, tracksToShow)
                                        }
                                        .padding(vertical = 6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        // Left: Wide Thumbnail (16:9)
                                        val localDrawableRes = when {
                                            song.videoId == "baps_ep_aksharatitdas" || song.title.contains("અક્ષરાતીતદાસ") -> com.example.musicapp.R.drawable.aksharatitdas_episode
                                            song.videoId == "baps_ep_uttamcharan" || song.title.contains("ઉત્તમયોગીદાસ") -> com.example.musicapp.R.drawable.uttamyogidas_episode
                                            song.videoId == "baps_ep_atmatruptdas" || song.title.contains("આત્મતૃપ્તદાસ") || song.title.contains("Atmatruptdas") -> com.example.musicapp.R.drawable.atmatruptdas_episode
                                            song.videoId == "baps_ep_1" || song.title.contains("Somprakashdas", ignoreCase = true) || song.title.contains("સોમપ્રકાશદાસ") -> com.example.musicapp.R.drawable.somprakashdas_episode
                                            else -> null
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(width = 118.dp, height = 76.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF222622)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (localDrawableRes != null) {
                                                androidx.compose.foundation.Image(
                                                    painter = androidx.compose.ui.res.painterResource(localDrawableRes),
                                                    contentDescription = song.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                TrackArtwork(
                                                    model = song.albumArtUrl,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            // Line 1: 2-line Title
                                            Text(
                                                text = song.title,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.5.sp,
                                                    lineHeight = 19.sp
                                                ),
                                                color = if (isCurrent) Color(0xFF1ED760) else Color.White,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(3.dp))
                                            // Line 2: Subtitle
                                            Text(
                                                text = song.albumName.ifBlank { "Episode • ${song.artist}" },
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                                color = Color(0xFFA7A7A7),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            // Line 3: Description in Gujarati
                                            val desc = song.description.ifBlank {
                                                "આપણું જીવન સુખ અને દુઃખ બંનેથી ભરેલું છે. જ્યારે મુશ્કેલીઓ આવે છે, ત્યારે આપણે ઘણીવાર નિરાશ થઈ જઈએ છીએ અથવા ખોટી જગ્યાએ ઉકેલ શોધીએ છીએ. પૂજ્ય સોમપ્રકાશદાસ સ્વામી આ કથામાં સમજાવે છે કે સુખ અને..."
                                            }
                                            Text(
                                                text = desc,
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
                                                color = Color(0xFFA0A5A0),
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            // Line 4: Date & Duration
                                            val m = song.durationSeconds / 60
                                            val s = song.durationSeconds % 60
                                            val durationText = if (m >= 60) "${m / 60} hr ${m % 60} min" else "$m min $s sec"
                                            val dateText = song.releaseDate.ifBlank { "Oct 12" }
                                            Text(
                                                text = "$dateText • $durationText",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                color = Color(0xFF8E948E)
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))

                                            // Line 5: Spotify-style Action Row: Green Checkmark + 3-dots on left, White Circle Play Button on right
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Green Checkmark Badge
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF1ED760)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Saved",
                                                        tint = Color.Black,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(14.dp))

                                                IconButton(
                                                    onClick = { selectedSongForOptions = song },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.MoreVert,
                                                        contentDescription = "Options",
                                                        tint = Color(0xFFAAAAAA),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.weight(1f))

                                                // White Circular Play Button
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.White)
                                                        .clickable {
                                                            if (currentSong?.videoId == song.videoId) onOpenPlayer()
                                                            else viewModel.playSong(song, tracksToShow)
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = if (isCurrent) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                        contentDescription = "Play",
                                                        tint = Color.Black,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            if (currentSong?.videoId == song.videoId) onOpenPlayer()
                                            else viewModel.playSong(song, tracksToShow)
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
                                            color = if (isCurrent) themeAccent else Color.White,
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
                                            color = themeAccent,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    } else if (isCurrent) {
                                        Icon(
                                            imageVector = Icons.Default.GraphicEq,
                                            contentDescription = "Playing",
                                            tint = themeAccent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    IconButton(
                                        onClick = { selectedSongForOptions = song },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription = "Song options",
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
    }

    if (selectedSongForOptions != null) {
        val targetSong = selectedSongForOptions!!
        com.example.musicapp.ui.player.PlayerOptionsSheet(
            song = targetSong,
            onDismiss = { selectedSongForOptions = null },
            onPlayNext = {
                viewModel.playNext(targetSong)
                android.widget.Toast.makeText(context, "Playing next: ${targetSong.title}", android.widget.Toast.LENGTH_SHORT).show()
                selectedSongForOptions = null
            },
            onAddToQueue = {
                viewModel.addToQueue(targetSong)
                android.widget.Toast.makeText(context, "Added to queue: ${targetSong.title}", android.widget.Toast.LENGTH_SHORT).show()
                selectedSongForOptions = null
            },
            onViewArtist = { artistName ->
                selectedSongForOptions = null
                onNavigate(Screen.Artist(artistName))
            }
        )
    }
}

private fun splitArtistCredits(credit: String): List<String> = credit
    .replace(Regex("(?i)\\s+(feat\\.?|featuring|with)\\s+"), ",")
    .split(Regex("\\s*,\\s*|\\s+&\\s+|\\s+and\\s+", RegexOption.IGNORE_CASE))
    .map { it.trim() }
    .filter { it.isNotEmpty() }

data class SearchCategory(
    val title: String,
    val topSong: String,
    val gradientColors: List<Color>,
    val imageUrl: String
)

@Composable
fun CategoryCard(
    category: SearchCategory,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(108.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(category.gradientColors)
            )
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            Text(
                text = category.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0x33000000))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Top: ${category.topSong}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                    color = Color(0xFFE2E8F0),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        TrackArtwork(
            model = category.imageUrl,
            contentDescription = category.topSong,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(68.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 10.dp, y = 6.dp)
                .rotate(22f)
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(6.dp))
        )
    }
}

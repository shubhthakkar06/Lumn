package com.example.musicapp.ui.artist

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.musicapp.data.MusicCatalog
import com.example.musicapp.data.artist.ArtistFollowManager
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.playlist.PlaylistManager
import com.example.musicapp.ui.main.MainScreenViewModel
import com.example.musicapp.ui.navigation.Screen
import com.example.musicapp.ui.player.PlayerOptionsSheet

@Composable
fun ArtistScreen(
    artistName: String,
    imageUrl: String,
    monthlyListeners: String = "94.9M monthly listeners",
    viewModel: MainScreenViewModel,
    onBack: () -> Unit,
    onNavigate: ((Screen) -> Unit)? = null
) {
    val context = LocalContext.current
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isShuffle by viewModel.isShuffle.collectAsStateWithLifecycle()
    val loadingSongVideoId by viewModel.loadingSongVideoId.collectAsStateWithLifecycle()

    val playlistManager = remember { PlaylistManager.getInstance(context) }
    val likedSongs by playlistManager.likedSongs.collectAsStateWithLifecycle()

    val followManager = remember { ArtistFollowManager.getInstance(context) }
    val followedArtists by followManager.followedArtists.collectAsStateWithLifecycle()
    val isFollowing = remember(artistName, followedArtists) {
        followManager.isFollowing(artistName)
    }

    val artistData = remember(artistName) {
        MusicCatalog.getArtistData(artistName)
    }

    val artistLikedSongs = remember(likedSongs, artistName) {
        likedSongs.filter { it.artist.contains(artistName, ignoreCase = true) }
    }

    var isPopularExpanded by remember { mutableStateOf(false) }

    // Dialog & Sheet States
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showAboutSheet by remember { mutableStateOf(false) }
    var showDiscographySheet by remember { mutableStateOf(false) }
    var showLikedSongsSheet by remember { mutableStateOf(false) }
    var trackForOptionsSheet by remember { mutableStateOf<SongInfo?>(null) }
    var selectedAlbum by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    var popularTracks by remember(artistName) { mutableStateOf(artistData.topTracks) }
    var dynamicAvatar by remember(artistName, imageUrl) {
        mutableStateOf(
            if (artistName.contains("Coldplay", ignoreCase = true)) MusicCatalog.getArtistImageUrl(artistName)
            else imageUrl.ifEmpty { MusicCatalog.getArtistImageUrl(artistName) }
        )
    }
    var dynamicAlbums by remember(artistName) { mutableStateOf(artistData.albums) }

    val effectiveArtistHeroUrl = remember(artistName, dynamicAvatar, popularTracks, artistData) {
        val nonColdplayHero = artistData.heroUrl.takeIf { !it.contains("dvgZkm1xWPE") || artistName.contains("Coldplay", ignoreCase = true) }
        val nonColdplayAvatar = artistData.avatarUrl.takeIf { !it.contains("dvgZkm1xWPE") || artistName.contains("Coldplay", ignoreCase = true) }
        val candidate = dynamicAvatar.ifEmpty {
            nonColdplayHero ?: nonColdplayAvatar ?: popularTracks.firstOrNull()?.albumArtUrl?.ifEmpty { null } ?: MusicCatalog.getArtistImageUrl(artistName)
        }
        if (!artistName.contains("Coldplay", ignoreCase = true) && candidate.contains("dvgZkm1xWPE")) {
            MusicCatalog.getArtistImageUrl(artistName)
        } else {
            candidate
        }
    }

    LaunchedEffect(artistName) {
        try {
            val repo = com.example.musicapp.data.DefaultDataRepository(context)
            val searchResults = repo.searchSongs(artistName)
            val matchingTracks = searchResults
                .filter { it.matchesArtist(artistName) }
                .distinctBy { it.title.trim().lowercase() }
            if (matchingTracks.isNotEmpty()) {
                popularTracks = matchingTracks.take(10)
            }
            val api = com.example.musicapp.data.network.MusicApiService()
            val albums = api.fetchDynamicArtistAlbums(artistName)
            if (albums.isNotEmpty()) {
                dynamicAlbums = albums
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val displayedTracks = if (isPopularExpanded) popularTracks else popularTracks.take(5)

    val listState = rememberLazyListState()
    val showStickyBar by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 250 }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E14))
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // 1. Hero Header Section
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                ) {
                    AsyncImage(
                        model = dynamicAvatar,
                        contentDescription = artistName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Smooth Spotify Scrim Gradient
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x55000000),
                                        Color.Transparent,
                                        Color(0x99121212),
                                        Color(0xFF0A0E14)
                                    ),
                                    startY = 0f
                                )
                            )
                    )

                    // Top Back Button
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(start = 16.dp, top = 8.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0x66000000))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    // Artist Title & Verified Badge
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 8.dp)
                    ) {
                        Text(
                            text = artistName,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 44.sp,
                                letterSpacing = (-0.5).sp
                            ),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF00F59B),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Verified Artist",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // 2. Action Controls & Monthly Listeners
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = monthlyListeners.ifEmpty { artistData.monthlyListeners },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFA0A0A0)
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Artist Avatar, Follow Pill, Three Dots Menu
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            AsyncImage(
                                model = dynamicAvatar,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Crop
                            )

                            // Follow / Following Pill Button
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .border(
                                        width = 1.dp,
                                        color = if (isFollowing) Color(0xFF888888) else Color.White,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        val newState = followManager.toggleFollow(artistName)
                                        Toast.makeText(
                                            context,
                                            if (newState) "Followed $artistName" else "Unfollowed $artistName",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    .padding(horizontal = 18.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (isFollowing) "Following" else "Follow",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            IconButton(
                                onClick = { showOptionsMenu = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More",
                                    tint = Color(0xFFA0A0A0)
                                )
                            }
                        }

                        // Right: Shuffle Toggle & Big Green Play Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            IconButton(
                                onClick = { viewModel.toggleShuffle() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = "Shuffle",
                                    tint = if (isShuffle) Color(0xFF00F59B) else Color(0xFFA0A0A0),
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            val isThisArtistPlaying = isPlaying && currentSong?.artist?.contains(artistName, ignoreCase = true) == true
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00F59B))
                                    .clickable {
                                        if (isThisArtistPlaying) {
                                            viewModel.togglePlayPause()
                                        } else if (popularTracks.isNotEmpty()) {
                                            viewModel.playSong(popularTracks.first(), popularTracks)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isThisArtistPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color.Black,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            // Liked Songs Row
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showLikedSongsSheet = true }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(48.dp)) {
                            if (artistLikedSongs.isNotEmpty()) {
                                AsyncImage(
                                    model = artistLikedSongs.first().albumArtUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(48.dp).clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier.size(48.dp).clip(CircleShape)
                                        .background(Brush.linearGradient(listOf(Color(0xFF7F36D6), Color(0xFF2238A8)))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.FavoriteBorder, contentDescription = null, tint = Color.White)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00F59B))
                                    .align(Alignment.BottomEnd),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Liked songs",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = "${artistLikedSongs.size} songs • $artistName",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFA0A0A0)
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Color(0xFFA0A0A0)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Popular Section Header
                item {
                    Text(
                        text = "Popular",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                // Popular Tracks List (Top 5 or Expanded)
                itemsIndexed(displayedTracks, key = { index, track -> "${track.videoId}-$index" }) { index, track ->
                    val isCurrent = currentSong?.videoId == track.videoId
                    val isTrackLiked = playlistManager.isLiked(track.videoId)
                    val isLoading = loadingSongVideoId == track.videoId
                    val hasVideo = artistData.videoBadges.getOrElse(index) { false }
                    val playCount = artistData.playCounts.getOrElse(index) { "2,500,000,000" }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.playSong(track, popularTracks) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = if (isCurrent) Color(0xFF00F59B) else Color(0xFFA0A0A0),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.width(24.dp)
                        )
                        AsyncImage(
                            model = track.albumArtUrl,
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = if (isCurrent) Color(0xFF00F59B) else Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (hasVideo) "▶ Video • $playCount" else playCount,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFA0A0A0),
                                maxLines = 1
                            )
                        }

                        // Like Toggle Icon
                        IconButton(
                            onClick = { playlistManager.toggleLike(track) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            if (isTrackLiked) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Liked",
                                    tint = Color(0xFF00F59B),
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.FavoriteBorder,
                                    contentDescription = "Like",
                                    tint = Color(0xFF666666),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color(0xFF00F59B),
                                strokeWidth = 2.dp
                            )
                        }

                        // Three Dots Menu
                        IconButton(
                            onClick = { trackForOptionsSheet = track },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = Color(0xFFA0A0A0),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // "See more" / "See less" Pill Button
                item {
                    if (popularTracks.size > 5) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .border(1.dp, Color(0xFF666666), CircleShape)
                                    .clickable { isPopularExpanded = !isPopularExpanded }
                                    .padding(horizontal = 24.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = if (isPopularExpanded) "See less" else "See more",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                }
                }

                // Artist Pick Spotlight Card (From Real Spotify Experience)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "Artist Pick",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val spotlightSong = popularTracks.firstOrNull()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF161F2E))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                                .clickable {
                                    spotlightSong?.let { viewModel.playSong(it, popularTracks) }
                                }
                        ) {
                            AsyncImage(
                                model = spotlightSong?.albumArtUrl ?: effectiveArtistHeroUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Ambient Scrim
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0x77000000),
                                                Color.Transparent,
                                                Color(0xEE0A0E14)
                                            )
                                        )
                                    )
                            )
                            // Top Artist Quote Pill
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(14.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xEEFFFFFF))
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = dynamicAvatar,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Latest track out now!",
                                    color = Color.Black,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            // Bottom Track Info
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = spotlightSong?.albumArtUrl ?: effectiveArtistHeroUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = spotlightSong?.title ?: "Featured Hit",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Single • Top Pick",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF00F59B)
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Popular Releases (Discography) Section
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Popular releases",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = "Show all",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFA0A0A0),
                                modifier = Modifier.clickable { showDiscographySheet = true }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        val albumDisplayList = if (dynamicAlbums.isNotEmpty()) {
                            dynamicAlbums.take(4).map { Triple(it.first, it.second, it.third) }
                        } else {
                            artistData.discography.take(4).map { Triple(it.title, it.typeAndYear, it.coverUrl) }
                        }

                        albumDisplayList.forEach { (title, subtitle, cover) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedAlbum = Triple(title, subtitle, cover)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = cover.ifEmpty { dynamicAvatar },
                                    contentDescription = title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFA0A0A0)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .clip(CircleShape)
                                .border(1.dp, Color(0xFF666666), CircleShape)
                                .clickable { showDiscographySheet = true }
                                .padding(horizontal = 24.dp, vertical = 9.dp)
                        ) {
                            Text(
                                text = "See discography",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }



                // 7. About Card Section
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = "About",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF242424))
                                .clickable { showAboutSheet = true }
                        ) {
                            Column {
                                AsyncImage(
                                    model = effectiveArtistHeroUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                )
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = artistData.globalRank,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFFA0A0A0)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = artistName,
                                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                                color = Color.White
                                            )
                                            if (artistData.verified) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = Color(0xFF00F59B),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .border(
                                                    1.dp,
                                                    if (isFollowing) Color(0xFF888888) else Color.White,
                                                    CircleShape
                                                )
                                                .clickable {
                                                    val newState = followManager.toggleFollow(artistName)
                                                    Toast.makeText(
                                                        context,
                                                        if (newState) "Followed $artistName" else "Unfollowed $artistName",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = if (isFollowing) "Following" else "Follow",
                                                color = Color.White,
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = monthlyListeners.ifEmpty { artistData.monthlyListeners },
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "${artistData.bio.take(130)} ... see more",
                                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                        color = Color(0xFFA0A0A0),
                                        maxLines = 3
                                    )
                                }
                            }
                        }
                    }
                }

                // 8. Artist Playlists
                if (artistData.artistPlaylists.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        ) {
                            Text(
                                text = "Artist Playlists",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(artistData.artistPlaylists) { pl ->
                                    Column(
                                        modifier = Modifier
                                            .width(140.dp)
                                            .clickable {
                                                Toast.makeText(context, "Opening ${pl.title}", Toast.LENGTH_SHORT).show()
                                            }
                                    ) {
                                        AsyncImage(
                                            model = pl.coverUrl.ifEmpty { artistData.avatarUrl },
                                            contentDescription = pl.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(140.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = pl.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = pl.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFA0A0A0)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 9. Fans Also Like Section
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = "Fans also like",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            itemsIndexed(artistData.similarArtists, key = { index, item -> "${item.first}-$index" }) { _, (name, art) ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(130.dp)
                                        .clickable {
                                            onNavigate?.invoke(Screen.Artist(name, art))
                                        }
                                ) {
                                    AsyncImage(
                                        model = art.ifEmpty { artistData.avatarUrl },
                                        contentDescription = name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(130.dp)
                                            .clip(CircleShape)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

        }

        // Sticky Collapsing Header Bar
        AnimatedVisibility(
            visible = showStickyBar,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = Color(0xF2121212),
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Text(
                            text = artistName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val isThisArtistPlaying = isPlaying && currentSong?.artist?.contains(artistName, ignoreCase = true) == true
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00F59B))
                            .clickable {
                                if (isThisArtistPlaying) {
                                    viewModel.togglePlayPause()
                                } else if (popularTracks.isNotEmpty()) {
                                    viewModel.playSong(popularTracks.first(), popularTracks)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isThisArtistPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Options Bottom Sheet
        if (showOptionsMenu) {
            ArtistOptionsMenuSheet(
                artistName = artistName,
                imageUrl = dynamicAvatar,
                onDismiss = { showOptionsMenu = false }
            )
        }

        // About Bottom Sheet
        if (showAboutSheet) {
            ArtistAboutSheet(
                artistData = artistData.copy(heroUrl = effectiveArtistHeroUrl, avatarUrl = effectiveArtistHeroUrl),
                isFollowing = isFollowing,
                onToggleFollow = {
                    val newState = followManager.toggleFollow(artistName)
                    Toast.makeText(
                        context,
                        if (newState) "Followed $artistName" else "Unfollowed $artistName",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onDismiss = { showAboutSheet = false }
            )
        }

        // Discography Bottom Sheet
        if (showDiscographySheet) {
            ArtistDiscographySheet(
                artistName = artistName,
                discography = artistData.discography,
                onSelectAlbum = { item ->
                    showDiscographySheet = false
                    selectedAlbum = Triple(item.title, item.typeAndYear, item.coverUrl)
                },
                onDismiss = { showDiscographySheet = false }
            )
        }

        selectedAlbum?.let { album ->
            ArtistAlbumTracksSheet(
                artistName = artistName,
                album = album,
                viewModel = viewModel,
                onDismiss = { selectedAlbum = null }
            )
        }

        // Liked Songs Bottom Sheet
        if (showLikedSongsSheet) {
            ArtistLikedSongsSheet(
                artistName = artistName,
                likedSongs = artistLikedSongs,
                currentSongId = currentSong?.videoId,
                onPlaySong = { song, queue -> viewModel.playSong(song, queue) },
                onToggleLike = { song -> playlistManager.toggleLike(song) },
                onDismiss = { showLikedSongsSheet = false }
            )
        }

        // Track Options Sheet
        if (trackForOptionsSheet != null) {
            val song = trackForOptionsSheet!!
            PlayerOptionsSheet(
                song = song,
                onDismiss = { trackForOptionsSheet = null },
                onPlayNext = {
                    viewModel.playNext(song)
                    android.widget.Toast.makeText(context, "Playing next: ${song.title}", android.widget.Toast.LENGTH_SHORT).show()
                    trackForOptionsSheet = null
                },
                onAddToQueue = {
                    viewModel.addToQueue(song)
                    android.widget.Toast.makeText(context, "Added to queue: ${song.title}", android.widget.Toast.LENGTH_SHORT).show()
                    trackForOptionsSheet = null
                },
                onViewArtist = { trackArtist ->
                    trackForOptionsSheet = null
                    onNavigate?.invoke(Screen.Artist(trackArtist, MusicCatalog.getArtistImageUrl(trackArtist)))
                }
            )
        }
    }
}

private fun SongInfo.matchesArtist(name: String): Boolean {
    val expected = name.lowercase().replace(Regex("[^a-z0-9]"), "")
    return artist.split(Regex("(?i)\\s*,\\s*|\\s*&\\s*|\\s+and\\s+|\\s+feat\\.?\\s+|\\s+with\\s+"))
        .any { credit ->
            val normalized = credit.replace(Regex("(?i)\\s*-\\s*topic$"), "")
                .lowercase().replace(Regex("[^a-z0-9]"), "")
            normalized == expected
        }
}

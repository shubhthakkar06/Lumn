package com.example.musicapp.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicapp.data.auth.UserManager
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.playlist.PlaylistManager
import com.example.musicapp.data.settings.SettingsManager
import com.example.musicapp.theme.AppThemeHelper
import com.example.musicapp.ui.navigation.Screen
import com.example.musicapp.ui.components.TrackArtwork
import com.example.musicapp.ui.util.rememberDynamicGradient

data class HeroVibeTrack(val title: String, val artist: String, val cover: String, val videoId: String)

@Composable
fun HomeScreen(
    onOpenDrawer: () -> Unit,
    onNavigate: (Screen) -> Unit,
    viewModel: com.example.musicapp.ui.main.MainScreenViewModel? = null
) {
    val context = LocalContext.current
    val userManager = remember { UserManager.getInstance(context) }
    val playlistManager = remember { PlaylistManager.getInstance(context) }
    val currentUser by userManager.currentUser.collectAsStateWithLifecycle()
    val userPlaylists by playlistManager.playlists.collectAsStateWithLifecycle()
    val likedSongs by playlistManager.likedSongs.collectAsStateWithLifecycle()

    val avatarColor = remember(currentUser.avatarColorHex) {
        try {
            Color(android.graphics.Color.parseColor(currentUser.avatarColorHex))
        } catch (e: Exception) {
            Color(0xFF00F59B)
        }
    }

    val playHistoryManager = remember { com.example.musicapp.data.history.PlayHistoryManager.getInstance(context) }
    val playHistory by playHistoryManager.history.collectAsStateWithLifecycle()
    val userHistory = remember(playHistory, currentUser) {
        playHistoryManager.getUserHistory(currentUser.id)
    }

    val settingsManager = remember { SettingsManager.getInstance(context) }
    val appTheme by settingsManager.appTheme.collectAsStateWithLifecycle()
    val themeGradientColors = remember(appTheme) { AppThemeHelper.getThemeGradient(appTheme) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = themeGradientColors
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .statusBarsPadding(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 120.dp)
        ) {
            // 1. Top Cyber Bar: Avatar + Dynamic Greeting + Vibe Status
            item {
                val greeting = remember {
                    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                    when (hour) {
                        in 5..11 -> "Good morning"
                        in 12..16 -> "Good afternoon"
                        in 17..21 -> "Good evening"
                        else -> "Late night flow"
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "YOUR MUSIC, YOUR MOMENT",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp),
                            color = Color(0xFF60E6D0)
                        )
                        Text(
                            text = greeting,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 26.sp),
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                    Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, Brush.sweepGradient(listOf(Color(0xFF00F59B), Color(0xFF00D2FF), Color(0xFF00F59B))), CircleShape)
                                .background(avatarColor)
                                .clickable { onOpenDrawer() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                currentUser.avatarLetter,
                                color = Color.Black,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                        }
                    }
                }

            // 2. Featured Hero Card (Stage)
            item {
                val heroRecord = userHistory.firstOrNull()
                val (heroTitle, heroArtist, heroCover, heroVideoId) = if (heroRecord != null) {
                    HeroVibeTrack(heroRecord.title, heroRecord.artist, heroRecord.albumArtUrl, heroRecord.videoId)
                } else {
                    val fallback = com.example.musicapp.data.MusicCatalog.mostFavSongs.first()
                    HeroVibeTrack(fallback.title, fallback.artist, fallback.albumArtUrl, fallback.videoId)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(185.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF12364A), Color(0xFF281B3C), Color(0xFF111A29))
                            )
                        )
                        .border(
                            1.dp,
                            Brush.sweepGradient(listOf(Color(0xFF00F59B), Color(0xFF00D2FF), Color(0xFFB56BFF), Color(0xFF00F59B))),
                            RoundedCornerShape(22.dp)
                        )
                        .clickable {
                            viewModel?.playSong(SongInfo(heroVideoId, heroTitle, heroArtist, heroCover))
                        }
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TrackArtwork(
                            model = heroCover,
                            contentDescription = heroTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(136.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(16.dp))
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color(0x3300D2FF))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "FEATURED VIBE",
                                    color = Color(0xFF00D2FF),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = heroTitle,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = heroArtist,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFA0AEC0),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00F59B)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.Black, modifier = Modifier.size(24.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "Play Flow",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // 4. Quick Flow Grid (Dynamic User Playlists & Mixes)
            item {
                val userFavArtists = currentUser.selectedArtists
                val userFavGenres = currentUser.selectedGenres

                val gridItems = remember(userPlaylists, userFavArtists, userFavGenres) {
                    val list = mutableListOf<Triple<String, String, () -> Unit>>()
                    list.add(Triple("Liked Songs", "") {
                        onNavigate(Screen.Playlist("liked", "Liked Songs", "", "Playlist • ${currentUser.name}"))
                    })
                    userPlaylists.forEach { pl ->
                        list.add(Triple(pl.name, pl.coverUrl) {
                            onNavigate(Screen.Playlist(pl.id, pl.name, pl.coverUrl, "Playlist • ${pl.creator}"))
                        })
                    }
                    userFavArtists.forEach { artist ->
                        if (list.size < 6) {
                            val artUrl = com.example.musicapp.data.MusicCatalog.getArtistImageUrl(artist)
                            list.add(Triple("$artist Mix", artUrl) {
                                onNavigate(Screen.Artist(artist, artUrl))
                            })
                        }
                    }
                    userFavGenres.forEach { genre ->
                        if (list.size < 6) {
                            val cover = when (genre.lowercase()) {
                                "bollywood" -> "https://i.ytimg.com/vi/BddP6PYo2gs/hqdefault.jpg"
                                "pop" -> "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&auto=format&fit=crop"
                                "punjabi" -> "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=600&auto=format&fit=crop"
                                "indie", "indie india" -> "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=600&auto=format&fit=crop"
                                "edm", "edm & dance" -> "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&auto=format&fit=crop"
                                else -> "https://i.ytimg.com/vi/60ItHLz5WEA/hqdefault.jpg"
                            }
                            list.add(Triple("$genre Mix", cover) {
                                onNavigate(Screen.Playlist("genre_${genre.lowercase()}", "$genre Mix", cover, "Curated for ${currentUser.name}"))
                            })
                        }
                    }
                    val defaultMixes = listOf(
                        Triple("Daily Flow", "https://c.saavncdn.com/423/Arijit-Singh-All-Time-Best-Hindi-Hit-Love-Songs-Collection-Hindi-2026-20261008160834-500x500.jpg") {
                            onNavigate(Screen.Playlist("daily_flow", "Daily Flow", "https://c.saavncdn.com/423/Arijit-Singh-All-Time-Best-Hindi-Hit-Love-Songs-Collection-Hindi-2026-20261008160834-500x500.jpg", "Curated Flow"))
                        },
                        Triple("Arijit Singh Mix", "https://c.saavncdn.com/artists/Arijit_Singh_004_20241118063717_500x500.jpg") {
                            onNavigate(Screen.Artist("Arijit Singh", "https://c.saavncdn.com/artists/Arijit_Singh_004_20241118063717_500x500.jpg"))
                        },
                        Triple("Pritam Mix", "https://c.saavncdn.com/artists/Pritam_Chakraborty-20170711073326_500x500.jpg") {
                            onNavigate(Screen.Artist("Pritam", "https://c.saavncdn.com/artists/Pritam_Chakraborty-20170711073326_500x500.jpg"))
                        },
                        Triple("Coldplay Mix", "https://c.saavncdn.com/artists/Coldplay_002_20241003070447_500x500.jpg") {
                            onNavigate(Screen.Artist("Coldplay", "https://c.saavncdn.com/artists/Coldplay_002_20241003070447_500x500.jpg"))
                        },
                        Triple("Late Night Chill", "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=600&auto=format&fit=crop") {
                            onNavigate(Screen.Playlist("late_night", "Late Night Chill", "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=600&auto=format&fit=crop", "Midnight Waves"))
                        }
                    )
                    for (mix in defaultMixes) {
                        if (list.size < 6 && list.none { it.first == mix.first }) {
                            list.add(mix)
                        }
                    }
                    list.take(6)
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (rowIndex in 0 until (gridItems.size + 1) / 2) {
                        val idx1 = rowIndex * 2
                        val idx2 = idx1 + 1
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val item1 = gridItems.getOrNull(idx1)
                            if (item1 != null) {
                                GenZRecentCard(
                                    title = item1.first,
                                    imageUrl = item1.second,
                                    isLikedSongs = item1.first == "Liked Songs",
                                    modifier = Modifier.weight(1f),
                                    onClick = item1.third
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }

                            val item2 = gridItems.getOrNull(idx2)
                            if (item2 != null) {
                                GenZRecentCard(
                                    title = item2.first,
                                    imageUrl = item2.second,
                                    isLikedSongs = false,
                                    modifier = Modifier.weight(1f),
                                    onClick = item2.third
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }

            // 5. Jump back in (Play History)
            if (userHistory.isNotEmpty()) {
                item {
                    val jumpBackItems = userHistory.distinctBy { it.videoId }.take(6).map { rec ->
                        ShelfItem(rec.title, rec.artist, rec.albumArtUrl)
                    }
                    ShelfSection(
                        title = "Jump Back In",
                        items = jumpBackItems,
                        onItemClick = { item ->
                            val rec = userHistory.find { it.title == item.title }
                            if (rec != null) {
                                viewModel?.playSong(SongInfo(rec.videoId, rec.title, rec.artist, rec.albumArtUrl))
                            }
                        }
                    )
                }
            }

            // 6. Favourite Artists Orbit
            if (currentUser.selectedArtists.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Your Top Artists",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = "See All",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF00F59B)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            val artists = currentUser.selectedArtists.map { name ->
                                Pair(name, com.example.musicapp.data.MusicCatalog.getArtistImageUrl(name))
                            }
                            items(artists.size, key = { "${artists[it].first}-$it" }) { i ->
                                val (name, img) = artists[i]
                                ArtistCircleCard(
                                    name = name,
                                    img = img,
                                    onClick = { onNavigate(Screen.Artist(name, img)) }
                                )
                            }
                        }
                    }
                }
            }

            // 7. Made For You Mixes
            if (currentUser.selectedArtists.isNotEmpty() || currentUser.selectedGenres.isNotEmpty()) {
                item {
                    val mixItems = (currentUser.selectedArtists.map { artist ->
                        ShelfItem("$artist Radio", "Artist Flow", com.example.musicapp.data.MusicCatalog.getArtistImageUrl(artist))
                    } + currentUser.selectedGenres.map { genre ->
                        val cover = when (genre.lowercase()) {
                            "bollywood" -> "https://i.ytimg.com/vi/BddP6PYo2gs/hqdefault.jpg"
                            "pop" -> "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&auto=format&fit=crop"
                            "punjabi" -> "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=600&auto=format&fit=crop"
                            "indie", "indie india" -> "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=600&auto=format&fit=crop"
                            "edm", "edm & dance" -> "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&auto=format&fit=crop"
                            else -> "https://i.ytimg.com/vi/60ItHLz5WEA/hqdefault.jpg"
                        }
                        ShelfItem("$genre Mix", "Curated Flow", cover)
                    }).take(6)

                    ShelfSection(
                        title = "Made For You",
                        items = mixItems,
                        onItemClick = { item ->
                            if (item.title.endsWith("Radio")) {
                                val art = item.title.removeSuffix(" Radio")
                                onNavigate(Screen.Artist(art, item.imageUrl))
                            } else {
                                onNavigate(Screen.Playlist(item.title, item.title, item.imageUrl, item.subtitle))
                            }
                        }
                    )
                }
            }

            // 8. Liked Songs shelf if available
            if (likedSongs.isNotEmpty()) {
                item {
                    val likedShelves = likedSongs.take(6).map { song ->
                        ShelfItem(song.title, song.artist, song.albumArtUrl)
                    }
                    ShelfSection(
                        title = "Songs You Liked",
                        items = likedShelves,
                        onItemClick = { item ->
                            val s = likedSongs.find { it.title == item.title }
                            if (s != null) viewModel?.playSong(s, likedSongs)
                        }
                    )
                }
            }
        }
    }
}

data class ShelfItem(val title: String, val subtitle: String, val imageUrl: String)

@Composable
fun ShelfSection(
    title: String,
    items: List<ShelfItem>,
    onItemClick: (ShelfItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
        Spacer(modifier = Modifier.height(14.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(items.size, key = { "${items[it].title}_$it" }) { i ->
                val item = items[i]
                Column(
                    modifier = Modifier
                        .width(140.dp)
                        .clickable { onItemClick(item) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(16.dp))
                    ) {
                        TrackArtwork(
                            model = item.imageUrl,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
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
                        color = Color(0xFFA0AEC0),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun GenZRecentCard(
    title: String,
    imageUrl: String,
    modifier: Modifier = Modifier,
    isLikedSongs: Boolean = false,
    onClick: () -> Unit = {}
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "cardBounce"
    )

    Row(
        modifier = modifier
            .scale(scale)
            .height(60.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(Color(0xFF131D2A), Color(0xFF0F1522))
                )
            )
            .border(1.dp, Color(0x2200F59B), RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() }
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLikedSongs) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFFFF416C), Color(0xFFFF4B2B))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = "Liked",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        } else {
            TrackArtwork(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(60.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 8.dp)
        )
    }
}

@Composable
private fun ArtistCircleCard(
    name: String,
    img: String,
    onClick: () -> Unit
) {
    val dynamicColors = rememberDynamicGradient(img)
    val ringColor = remember(dynamicColors) { dynamicColors.firstOrNull() ?: Color(0xFF00F59B) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(110.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .border(
                    2.5.dp,
                    Brush.sweepGradient(listOf(ringColor, ringColor.copy(alpha = 0.5f), ringColor)),
                    CircleShape
                )
        ) {
            TrackArtwork(
                model = img,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Verified",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFA0AEC0)
        )
    }
}

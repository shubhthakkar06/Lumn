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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.musicapp.data.auth.UserManager
import com.example.musicapp.data.history.PlayHistoryManager
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.playlist.PlaylistManager
import com.example.musicapp.data.settings.SettingsManager
import com.example.musicapp.theme.AppThemeHelper
import com.example.musicapp.ui.components.MediaCard
import com.example.musicapp.ui.components.Shelf
import com.example.musicapp.ui.components.ShelfPlaceholder
import com.example.musicapp.ui.components.TrackArtwork
import com.example.musicapp.ui.components.rememberArtistImage
import com.example.musicapp.ui.navigation.Screen

@Composable
fun HomeScreen(
    onOpenDrawer: () -> Unit,
    onNavigate: (Screen) -> Unit,
    viewModel: com.example.musicapp.ui.main.MainScreenViewModel? = null,
    homeViewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val userManager = remember { UserManager.getInstance(context) }
    val playlistManager = remember { PlaylistManager.getInstance(context) }
    val currentUser by userManager.currentUser.collectAsStateWithLifecycle()
    val userPlaylists by playlistManager.playlists.collectAsStateWithLifecycle()
    val history by PlayHistoryManager.getInstance(context).history.collectAsStateWithLifecycle()
    val userHistory = remember(history, currentUser.id) {
        PlayHistoryManager.getInstance(context).getUserHistory(currentUser.id).distinctBy { it.videoId }
    }
    val home by homeViewModel.state.collectAsStateWithLifecycle()

    val settingsManager = remember { SettingsManager.getInstance(context) }
    val appTheme by settingsManager.appTheme.collectAsStateWithLifecycle()
    val themeGradientColors = remember(appTheme) { AppThemeHelper.getThemeGradient(appTheme) }
    val themeAccent = remember(appTheme) { AppThemeHelper.getThemeAccent(appTheme) }
    val avatarColor = remember(currentUser.avatarColorHex) {
        runCatching { Color(android.graphics.Color.parseColor(currentUser.avatarColorHex)) }.getOrDefault(Color(0xFF00F59B))
    }

    LaunchedEffect(Unit) { homeViewModel.refresh() }

    // Newest episodes from followed podcasts and YouTube channels.
    val followedShows by remember { com.example.musicapp.data.shows.ShowFollowManager.getInstance(context) }.shows.collectAsStateWithLifecycle()
    var newEpisodes by remember { mutableStateOf<List<com.example.musicapp.data.network.SongInfo>>(emptyList()) }
    LaunchedEffect(followedShows.map { it.id }) {
        newEpisodes = if (followedShows.isEmpty()) emptyList() else runCatching {
            com.example.musicapp.data.catalog.Shows.newEpisodes(followedShows.map(com.example.musicapp.data.catalog.ShowRef::of))
        }.getOrDefault(emptyList())
    }

    fun openMix(id: String, title: String, subtitle: String, cover: String) = onNavigate(Screen.Mix(id, title, subtitle, cover))

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(themeGradientColors))) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).statusBarsPadding(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 140.dp)
        ) {
            item(key = "header") {
                val greeting = remember {
                    when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
                        in 5..11 -> "Good morning"
                        in 12..16 -> "Good afternoon"
                        in 17..21 -> "Good evening"
                        else -> "Late night flow"
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(greeting, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 26.sp), color = Color.White)
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(avatarColor).clickable { onOpenDrawer() },
                        contentAlignment = Alignment.Center
                    ) { com.example.musicapp.ui.components.AvatarContent(currentUser, 18.sp) }
                }
            }

            // Quick access grid: Liked Songs, your playlists, then your mixes.
            item(key = "grid") {
                val tiles = buildList<Triple<String, String, () -> Unit>> {
                    add(Triple("Liked Songs", "") { onNavigate(Screen.Playlist("liked", "Liked Songs", "", "Playlist • ${currentUser.name}")) })
                    userPlaylists.take(5).forEach { pl ->
                        add(Triple(pl.name, pl.coverUrl) { onNavigate(Screen.Playlist(pl.id, pl.name, pl.coverUrl, "Playlist • ${pl.creator}")) })
                    }
                    home.madeForYou.orEmpty().forEach { mix ->
                        add(Triple(mix.title, mix.imageUrl) { openMix(mix.id, mix.title, mix.subtitle, mix.imageUrl) })
                    }
                }.take(6)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                    tiles.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { (title, image, onClick) ->
                                GenZRecentCard(title, image, Modifier.weight(1f), isLikedSongs = title == "Liked Songs", onClick = onClick)
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            userHistory.firstOrNull()?.let { last ->
                item(key = "resume") {
                    ResumeCard(
                        title = last.title,
                        artist = last.artist,
                        cover = last.albumArtUrl,
                        accent = themeAccent,
                        onPlay = { viewModel?.playSong(SongInfo(last.videoId, last.title, last.artist, last.albumArtUrl)) },
                        onRadio = {
                            val seed = SongInfo(last.videoId, last.title, last.artist, last.albumArtUrl)
                            openMix("radio:song:" + kotlinx.serialization.json.Json.encodeToString(SongInfo.serializer(), seed), "${last.title} Radio", "Based on ${last.title}", last.albumArtUrl)
                        }
                    )
                }
            }

            item(key = "made") {
                val mixes = home.madeForYou
                if (mixes == null) ShelfPlaceholder("Made for you")
                else Shelf("Made for you", mixes, key = { it.id }, subtitle = "Updated from what you play, skip and like") { mix ->
                    MediaCard(mix.title, mix.subtitle, mix.imageUrl, onClick = { openMix(mix.id, mix.title, mix.subtitle, mix.imageUrl) })
                }
            }

            if (newEpisodes.isNotEmpty()) {
                item(key = "episodes") {
                    Shelf("New episodes", newEpisodes, key = { it.videoId }, subtitle = "From shows you follow") { ep ->
                        MediaCard(ep.title, listOf(ep.artist, ep.releaseDate).filter { it.isNotBlank() }.joinToString(" • "), ep.albumArtUrl, onClick = { viewModel?.playSong(ep, newEpisodes) }, size = 176.dp)
                    }
                }
            }

            home.becauseSeed?.let { seed ->
                item(key = "because") {
                    Shelf("Because you listened to ${seed.title}", home.becauseSongs, key = { it.videoId }) { song ->
                        MediaCard(song.title, song.artist, song.albumArtUrl, onClick = { viewModel?.playSong(song, home.becauseSongs) })
                    }
                }
            }

            if (userHistory.isNotEmpty()) {
                item(key = "jump") {
                    Shelf("Jump back in", userHistory.take(10), key = { it.videoId }) { rec ->
                        MediaCard(rec.title, rec.artist, rec.albumArtUrl, onClick = {
                            viewModel?.playSong(SongInfo(rec.videoId, rec.title, rec.artist, rec.albumArtUrl))
                        })
                    }
                }
            }

            val artists = (home.topArtists.map { it.replaceFirstChar(Char::titlecase) } + currentUser.selectedArtists).distinctBy { it.lowercase() }.take(10)
            if (artists.isNotEmpty()) {
                item(key = "artists") {
                    Shelf("Your top artists", artists, key = { it.lowercase() }) { name ->
                        val image = rememberArtistImage(name)
                        MediaCard(name, "Artist", image, onClick = { onNavigate(Screen.Artist(name, image)) }, circle = true, size = 120.dp)
                    }
                }
            }

            item(key = "charts") {
                val charts = home.charts
                if (charts == null) ShelfPlaceholder("Charts")
                else Shelf("Charts", charts, key = { it.id }) { pl ->
                    MediaCard(pl.title, pl.subtitle, pl.imageUrl, onClick = { openMix("playlist:${pl.id}", pl.title, pl.subtitle, pl.imageUrl) })
                }
            }

            item(key = "new") {
                val albums = home.newReleases
                if (albums == null) ShelfPlaceholder("New releases")
                else Shelf("New releases", albums, key = { it.id }) { album ->
                    MediaCard(album.title, album.subtitle, album.imageUrl, onClick = {
                        album.id.toLongOrNull()?.let { onNavigate(Screen.Album(it, album.title, album.subtitle, album.imageUrl)) }
                    })
                }
            }

            item(key = "featured") {
                Shelf("Popular playlists", home.featured.orEmpty(), key = { it.id }) { pl ->
                    MediaCard(pl.title, pl.subtitle, pl.imageUrl, onClick = { openMix("playlist:${pl.id}", pl.title, pl.subtitle, pl.imageUrl) })
                }
            }

            item(key = "moods") {
                Shelf("Moods and genres", home.moods.orEmpty(), key = { it.id }) { ch ->
                    MediaCard(ch.title, ch.kind.replaceFirstChar(Char::titlecase), ch.imageUrl, onClick = { openMix("channel:${ch.id}", ch.title, "", ch.imageUrl) })
                }
            }
        }
    }
}

@Composable
private fun ResumeCard(title: String, artist: String, cover: String, accent: Color, onPlay: () -> Unit, onRadio: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 28.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF12364A), Color(0xFF281B3C), Color(0xFF111A29))))
            .clickable(onClick = onPlay)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackArtwork(cover, title, Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("PICK UP WHERE YOU LEFT OFF", color = Color(0xFF00D2FF), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artist, color = Color(0xFFA0AEC0), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.Black)
                }
                Spacer(Modifier.width(10.dp))
                TextButton(onClick = onRadio) { Text("Song radio", color = Color.White, fontWeight = FontWeight.SemiBold) }
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
            .background(Brush.horizontalGradient(listOf(Color(0xFF131D2A), Color(0xFF0F1522))))
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
                Modifier.size(60.dp).background(Brush.linearGradient(listOf(Color(0xFFFF416C), Color(0xFFFF4B2B)))),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp)) }
        } else {
            TrackArtwork(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(60.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 8.dp)
        )
    }
}

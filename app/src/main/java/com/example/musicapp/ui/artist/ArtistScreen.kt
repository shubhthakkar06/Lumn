package com.example.musicapp.ui.artist

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicapp.data.artist.ArtistFollowManager
import com.example.musicapp.data.catalog.ArtistPage
import com.example.musicapp.data.catalog.ArtistRef
import com.example.musicapp.data.catalog.DeezerArtists
import com.example.musicapp.data.catalog.SaavnCatalog
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.recommend.RecommendationEngine
import com.example.musicapp.data.settings.SettingsManager
import com.example.musicapp.theme.AppThemeHelper
import com.example.musicapp.ui.components.AddToPlaylistDialog
import com.example.musicapp.ui.components.MediaCard
import com.example.musicapp.ui.components.Shelf
import com.example.musicapp.ui.components.SongRow
import com.example.musicapp.ui.components.TrackArtwork
import com.example.musicapp.ui.main.MainScreenViewModel
import com.example.musicapp.ui.navigation.Screen
import com.example.musicapp.ui.player.PlayerOptionsSheet
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

private sealed interface ArtistLoad {
    object Loading : ArtistLoad
    object NotFound : ArtistLoad
    data class Ready(val page: ArtistPage, val similar: List<ArtistRef>) : ArtistLoad
}

/** Artist page built from the real catalog: no made-up listener counts or tour dates. */
@Composable
fun ArtistScreen(
    artistName: String,
    imageUrl: String = "",
    monthlyListeners: String = "",
    viewModel: MainScreenViewModel,
    onBack: () -> Unit,
    onNavigate: ((Screen) -> Unit)? = null
) {
    val context = LocalContext.current
    val theme by SettingsManager.getInstance(context).appTheme.collectAsStateWithLifecycle()
    val accent = remember(theme) { AppThemeHelper.getThemeAccent(theme) }
    val follow = remember { ArtistFollowManager.getInstance(context) }
    val followed by follow.followedArtists.collectAsStateWithLifecycle()
    val isFollowing = remember(followed, artistName) { follow.isFollowing(artistName) }
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val loadingId by viewModel.loadingSongVideoId.collectAsStateWithLifecycle()

    var load by remember(artistName) { mutableStateOf<ArtistLoad>(ArtistLoad.Loading) }
    var showAllSongs by remember(artistName) { mutableStateOf(false) }
    var showBio by remember(artistName) { mutableStateOf(false) }
    var optionsFor by remember { mutableStateOf<SongInfo?>(null) }
    var addToPlaylistFor by remember { mutableStateOf<SongInfo?>(null) }

    LaunchedEffect(artistName) {
        val ref = runCatching { RecommendationEngine.getInstance(context).artist(artistName) }.getOrNull()
        val page = runCatching { com.example.musicapp.data.catalog.ArtistCatalog.page(artistName, ref?.id, imageUrl) }.getOrNull()
        if (page == null) {
            load = ArtistLoad.NotFound
            return@LaunchedEffect
        }
        val similar = page.similar.ifEmpty {
            val names = runCatching { DeezerArtists.relatedArtistNames(page.name, 8) }.getOrDefault(emptyList())
            coroutineScope { names.map { async { runCatching { SaavnCatalog.findArtist(it) }.getOrNull() } }.awaitAll().filterNotNull() }
        }.distinctBy { it.id }.filter { it.id != page.id }
        load = ArtistLoad.Ready(page, similar)
    }

    val navigate = onNavigate ?: {}
    Box(Modifier.fillMaxSize().background(Color(0xFF0A0E14))) {
        when (val state = load) {
            ArtistLoad.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = accent) }
            ArtistLoad.NotFound -> Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
                Spacer(Modifier.height(40.dp))
                Text("Couldn't load $artistName right now.", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Check your connection and try again.", color = Color(0xFFA0AEC0))
            }
            is ArtistLoad.Ready -> {
                val page = state.page
                val songs = page.topSongs
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 140.dp)) {
                    item(key = "hero") {
                        Box(Modifier.fillMaxWidth().height(320.dp)) {
                            TrackArtwork(page.imageUrl.ifBlank { imageUrl }, page.name, Modifier.fillMaxSize())
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66000000), Color.Transparent, Color(0xFF0A0E14)))))
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.statusBarsPadding().padding(8.dp).clip(CircleShape).background(Color(0x66000000))
                            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
                            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                                if (page.verified) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF3D91F4), modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Verified artist", color = Color.White, fontSize = 13.sp)
                                    }
                                }
                                Text(page.name, color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2)
                                if (page.followerCount > 0) {
                                    Text("${formatCount(page.followerCount)} followers", color = Color(0xFFCBD5E1), fontSize = 14.sp)
                                }
                            }
                        }
                    }
                    item(key = "actions") {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { follow.toggleFollow(page.name) }) {
                                Text(if (isFollowing) "Following" else "Follow", color = Color.White)
                            }
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = {
                                navigate(Screen.Mix("radio:artist:${page.name}", "${page.name} Radio", "With similar artists", page.imageUrl))
                            }) { Icon(Icons.Default.Radio, contentDescription = "Artist radio", tint = Color.White) }
                            Spacer(Modifier.weight(1f))
                            IconButton(enabled = songs.isNotEmpty(), onClick = {
                                if (!viewModel.isShuffle.value) viewModel.toggleShuffle()
                                viewModel.playSong(songs.random(), songs)
                            }) { Icon(Icons.Default.Shuffle, contentDescription = "Shuffle play", tint = Color.White) }
                            Box(Modifier.size(54.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                                IconButton(enabled = songs.isNotEmpty(), onClick = { viewModel.playSong(songs.first(), songs) }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.Black, modifier = Modifier.size(32.dp))
                                }
                            }
                        }
                    }
                    item(key = "popular-title") {
                        Text("Popular", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                    val visible = if (showAllSongs) songs else songs.take(5)
                    itemsIndexed(visible, key = { i, s -> "song-${s.videoId}-$i" }) { i, song ->
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            SongRow(
                                song = song,
                                isCurrent = currentSong?.videoId == song.videoId,
                                isLoading = loadingId == song.videoId,
                                onClick = { viewModel.playSong(song, songs) },
                                onMore = { optionsFor = song },
                                accent = accent,
                                index = i + 1
                            )
                        }
                    }
                    if (songs.size > 5) {
                        item(key = "more") {
                            TextButton(onClick = { showAllSongs = !showAllSongs }, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text(if (showAllSongs) "Show less" else "See more", color = Color(0xFFCBD5E1))
                            }
                        }
                    }
                    item(key = "albums") {
                        Box(Modifier.padding(start = 16.dp, top = 16.dp)) {
                            Shelf("Discography", page.albums, key = { it.id }) { album ->
                                MediaCard(album.title, album.year, album.imageUrl, onClick = {
                                    album.id.toLongOrNull()?.let { navigate(Screen.Album(it, album.title, page.name, album.imageUrl)) }
                                })
                            }
                        }
                    }
                    item(key = "similar") {
                        Box(Modifier.padding(start = 16.dp)) {
                            Shelf("Fans also like", state.similar, key = { it.id }) { a ->
                                MediaCard(a.name, "Artist", a.imageUrl, onClick = { navigate(Screen.Artist(a.name, a.imageUrl)) }, circle = true, size = 120.dp)
                            }
                        }
                    }
                    if (page.bio.isNotBlank()) {
                        item(key = "bio") {
                            Column(
                                Modifier
                                    .padding(16.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF151B26))
                                    .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(14.dp))
                                    .clickable { showBio = !showBio }
                                    .padding(16.dp)
                            ) {
                                Text("About", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    page.bio,
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 14.sp,
                                    maxLines = if (showBio) Int.MAX_VALUE else 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    optionsFor?.let { song ->
        PlayerOptionsSheet(
            song = song,
            onDismiss = { optionsFor = null },
            onPlayNext = { viewModel.playNext(song); optionsFor = null },
            onAddToQueue = { viewModel.addToQueue(song); optionsFor = null },
            onAddToPlaylist = { addToPlaylistFor = song; optionsFor = null },
            onViewArtist = { name -> optionsFor = null; navigate(Screen.Artist(name.split(",").first().trim())) }
        )
    }
    addToPlaylistFor?.let { song ->
        AddToPlaylistDialog(song, onDismiss = { addToPlaylistFor = null }) { name ->
            addToPlaylistFor = null
            Toast.makeText(context, "Added to $name", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun formatCount(n: Long): String = when {
    n >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", n / 1_000_000.0)
    n >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", n / 1_000.0)
    else -> n.toString()
}

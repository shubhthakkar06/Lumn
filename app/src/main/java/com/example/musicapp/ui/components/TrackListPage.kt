package com.example.musicapp.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.playlist.PlaylistManager
import com.example.musicapp.data.settings.SettingsManager
import com.example.musicapp.theme.AppThemeHelper
import com.example.musicapp.ui.main.MainScreenViewModel
import com.example.musicapp.ui.navigation.Screen
import com.example.musicapp.ui.player.PlayerOptionsSheet
import com.example.musicapp.ui.util.rememberDynamicGradient

/**
 * Album / mix / chart page: cover, title, actions and the track list.
 * [songs] null means still loading.
 */
@Composable
fun TrackListPage(
    title: String,
    subtitle: String,
    coverUrl: String,
    songs: List<SongInfo>?,
    viewModel: MainScreenViewModel,
    onBack: () -> Unit,
    onNavigate: (Screen) -> Unit,
    reason: String = "",
    emptyMessage: String = "Nothing to play here right now. Check your connection and try again.",
    numbered: Boolean = false
) {
    val context = LocalContext.current
    val settings = remember { SettingsManager.getInstance(context) }
    val theme by settings.appTheme.collectAsStateWithLifecycle()
    val accent = remember(theme) { AppThemeHelper.getThemeAccent(theme) }
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val loadingId by viewModel.loadingSongVideoId.collectAsStateWithLifecycle()
    val gradient = rememberDynamicGradient(coverUrl.ifBlank { songs?.firstOrNull()?.albumArtUrl.orEmpty() })
    var optionsFor by remember { mutableStateOf<SongInfo?>(null) }
    var addToPlaylistFor by remember { mutableStateOf<SongInfo?>(null) }
    val cover = coverUrl.ifBlank { songs?.firstOrNull()?.albumArtUrl.orEmpty() }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(gradient + listOf(Color(0xFF0A0E14))))) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp)
        ) {
            item {
                Column(Modifier.fillMaxWidth().statusBarsPadding()) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TrackArtwork(cover, title, Modifier.size(220.dp).clip(RoundedCornerShape(8.dp)))
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    if (subtitle.isNotBlank()) Text(subtitle, color = Color(0xFFCBD5E1), fontSize = 14.sp)
                    if (reason.isNotBlank()) Text(reason, color = Color(0xFF94A3B8), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(14.dp))
                    val list = songs.orEmpty()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            enabled = list.isNotEmpty(),
                            onClick = {
                                val saved = PlaylistManager.getInstance(context).createPlaylist(title, songs = list)
                                if (saved != null) Toast.makeText(context, "Saved \"$title\" to Your Library", Toast.LENGTH_SHORT).show()
                            }
                        ) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Save to Your Library", tint = Color.White) }
                        IconButton(enabled = list.isNotEmpty(), onClick = { viewModel.downloadAlbum(list) }) {
                            Icon(Icons.Default.Download, contentDescription = "Download all", tint = Color.White)
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            enabled = list.isNotEmpty(),
                            onClick = {
                                if (!viewModel.isShuffle.value) viewModel.toggleShuffle()
                                viewModel.playSong(list.random(), list)
                            }
                        ) { Icon(Icons.Default.Shuffle, contentDescription = "Shuffle play", tint = Color.White) }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.size(54.dp).clip(CircleShape).background(if (list.isEmpty()) Color.Gray else accent),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(enabled = list.isNotEmpty(), onClick = { viewModel.playSong(list.first(), list) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.Black, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            when {
                songs == null -> item {
                    Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accent)
                    }
                }
                songs.isEmpty() -> item {
                    Text(emptyMessage, color = Color(0xFFA0AEC0), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 40.dp))
                }
                else -> itemsIndexed(songs, key = { i, s -> "${s.videoId}#$i" }) { i, song ->
                    SongRow(
                        song = song,
                        isCurrent = currentSong?.videoId == song.videoId,
                        isLoading = loadingId == song.videoId,
                        onClick = { viewModel.playSong(song, songs) },
                        onMore = { optionsFor = song },
                        accent = accent,
                        index = if (numbered) i + 1 else null
                    )
                    if (song.isEpisode && song.description.isNotBlank()) {
                        Text(song.description, color = Color(0xFF94A3B8), fontSize = 12.sp, maxLines = 3, modifier = Modifier.padding(bottom = 8.dp))
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
            onViewArtist = { name -> optionsFor = null; onNavigate(Screen.Artist(name.split(",").first().trim())) }
        )
    }
    addToPlaylistFor?.let { song ->
        AddToPlaylistDialog(song, onDismiss = { addToPlaylistFor = null }) { name ->
            addToPlaylistFor = null
            Toast.makeText(context, "Added to $name", Toast.LENGTH_SHORT).show()
        }
    }
}

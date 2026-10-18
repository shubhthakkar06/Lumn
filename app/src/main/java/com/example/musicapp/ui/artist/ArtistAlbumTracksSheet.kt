package com.example.musicapp.ui.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicapp.data.network.MusicApiService
import com.example.musicapp.data.auth.UserManager
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.provider.SearchResultDeduplicator
import com.example.musicapp.ui.components.TrackArtwork
import com.example.musicapp.ui.main.MainScreenViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArtistAlbumTracksSheet(
    artistName: String,
    album: Triple<String, String, String>,
    viewModel: MainScreenViewModel,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentUser by remember { UserManager.getInstance(context).currentUser }.collectAsStateWithLifecycle()
    var albumSongs by remember(album.first, artistName) { mutableStateOf<List<SongInfo>>(emptyList()) }
    var loading by remember(album.first, artistName) { mutableStateOf(true) }
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentTrackInAlbum = currentSong?.let { active -> albumSongs.any { it.videoId == active.videoId } } == true
    val isAlbumPlaying = isPlaying && currentTrackInAlbum

    LaunchedEffect(album.first, artistName) {
        loading = true
        val results = runCatching {
            MusicApiService().searchSongs("${album.first} $artistName")
        }.getOrDefault(emptyList())
        val artistMatches = results.filter { track ->
            track.artist.split(Regex("[,;&]|\\bfeat(?:uring)?\\.?\\b", RegexOption.IGNORE_CASE))
                .any { it.trim().contains(artistName, ignoreCase = true) }
        }
        val exactAlbumMatches = artistMatches.filter {
            it.albumName.isBlank() || it.albumName.contains(album.first, ignoreCase = true)
        }
        val exactIds = (exactAlbumMatches.ifEmpty { artistMatches }).distinctBy { it.videoId }
        albumSongs = SearchResultDeduplicator.deduplicate(exactIds)
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF10151F),
        contentColor = Color.White,
        dragHandle = null,
        modifier = Modifier.fillMaxHeight(0.9f)
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxWidth().height(245.dp).background(
                    Brush.verticalGradient(listOf(Color(0xFF285B73), Color(0xFF10151F)))
                )
            ) {
                TrackArtwork(
                    model = album.third,
                    contentDescription = album.first,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 22.dp, top = 46.dp).size(152.dp).clip(RoundedCornerShape(18.dp))
                )
                Row(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(album.second, color = Color(0xFFC5D6DF), style = MaterialTheme.typography.labelMedium)
                        Text(album.first, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(artistName, color = Color(0xFFD3DCE2), style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(
                        onClick = { viewModel.downloadAlbum(albumSongs) },
                        enabled = albumSongs.isNotEmpty() && !currentUser.isGuest,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Download album", tint = Color.White)
                    }
                    FilledIconButton(
                        onClick = {
                            if (currentTrackInAlbum) viewModel.togglePlayPause()
                            else albumSongs.firstOrNull()?.let { viewModel.playSong(it, albumSongs) }
                        },
                        enabled = albumSongs.isNotEmpty(),
                        modifier = Modifier.size(54.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF48E6A0), contentColor = Color(0xFF07120D))
                    ) {
                        Icon(
                            if (isAlbumPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isAlbumPlaying) "Pause album" else "Play album",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }
            when {
                loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF48E6A0))
                }
                albumSongs.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f).padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Tracks for this release could not be loaded right now.", color = Color(0xFFABB5C4))
                }
                else -> LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(albumSongs, key = { index, song -> "${song.videoId}-$index" }) { index, track ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { viewModel.playSong(track, albumSongs) }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${index + 1}", Modifier.width(30.dp), color = if (currentSong?.videoId == track.videoId) Color(0xFF48E6A0) else Color(0xFF8D98A8))
                            TrackArtwork(
                                model = track.albumArtUrl.ifBlank { album.third },
                                contentDescription = null,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(track.title, color = if (currentSong?.videoId == track.videoId) Color(0xFF48E6A0) else Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.artist, color = Color(0xFF9FAABB), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.example.musicapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.playlist.PlaylistManager
import com.example.musicapp.data.recommend.RecommendationEngine
import java.util.concurrent.ConcurrentHashMap

/** A square (or round, for artists) card with a caption, used in horizontal shelves. */
@Composable
fun MediaCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    circle: Boolean = false,
    size: Dp = 148.dp
) {
    Column(
        modifier = modifier
            .width(size)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp),
        horizontalAlignment = if (circle) Alignment.CenterHorizontally else Alignment.Start
    ) {
        TrackArtwork(
            model = imageUrl,
            contentDescription = title,
            modifier = Modifier
                .size(size)
                .clip(if (circle) CircleShape else RoundedCornerShape(10.dp))
        )
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = if (circle) 1 else 2,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle.isNotBlank()) {
            Text(subtitle, color = Color(0xFFA0AEC0), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Section title plus a horizontally scrolling row. Renders nothing for an empty list. */
@Composable
fun <T> Shelf(
    title: String,
    items: List<T>,
    key: (T) -> Any,
    subtitle: String? = null,
    content: @Composable (T) -> Unit
) {
    if (items.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
        if (subtitle != null) Text(subtitle, color = Color(0xFFA0AEC0), fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(items, key = key) { content(it) }
        }
    }
}

/** Placeholder row of grey cards while a shelf loads. */
@Composable
fun ShelfPlaceholder(title: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(3) {
                Box(Modifier.size(148.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x1FFFFFFF)))
            }
        }
    }
}

/** A song in a vertical list. */
@Composable
fun SongRow(
    song: SongInfo,
    isCurrent: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
    accent: Color,
    index: Int? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (index != null) {
            Text("$index", color = Color(0xFFA0AEC0), fontSize = 14.sp, modifier = Modifier.width(28.dp))
        }
        TrackArtwork(song.albumArtUrl, song.title, Modifier.size(50.dp).clip(RoundedCornerShape(6.dp)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (isCurrent) accent else Color.White,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (song.isEpisode) song.releaseDate.ifBlank { song.artist } else song.artist,
                color = Color(0xFFA0AEC0),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isLoading) {
            CircularProgressIndicator(Modifier.size(18.dp), color = accent, strokeWidth = 2.dp)
        }
        IconButton(onClick = onMore) {
            Icon(Icons.Default.MoreVert, contentDescription = "More options for ${song.title}", tint = Color(0xFFA0AEC0))
        }
    }
}

private val artistImageCache = ConcurrentHashMap<String, String>()

/** Real artist photo from the catalog, looked up once per artist name. */
@Composable
fun rememberArtistImage(artistName: String, fallback: String = ""): String {
    val context = LocalContext.current
    val key = RecommendationEngine.primaryArtist(artistName)
    var url by remember(key) { mutableStateOf(artistImageCache[key] ?: fallback) }
    LaunchedEffect(key) {
        if (artistImageCache[key] == null && key.isNotBlank()) {
            val found = runCatching { RecommendationEngine.getInstance(context).artist(artistName)?.imageUrl }.getOrNull()
            if (!found.isNullOrBlank()) {
                artistImageCache[key] = found
                url = found
            }
        }
    }
    return url
}

/** Lets the user pick one of their playlists (or make a new one) for [song]. */
@Composable
fun AddToPlaylistDialog(song: SongInfo, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val context = LocalContext.current
    val manager = remember { PlaylistManager.getInstance(context) }
    val playlists by manager.playlists.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF1A2130)) {
            Column(Modifier.padding(20.dp).heightIn(max = 520.dp)) {
                Text("Add to playlist", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text("New playlist name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    IconButton(
                        enabled = newName.isNotBlank(),
                        onClick = {
                            manager.createPlaylist(newName.trim(), songs = listOf(song))?.let { onDone(it.name) }
                        }
                    ) { Icon(Icons.Default.Add, contentDescription = "Create playlist", tint = Color.White) }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn {
                    items(playlists, key = { it.id }) { pl ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    manager.addSongToPlaylist(pl.id, song)
                                    onDone(pl.name)
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TrackArtwork(pl.coverUrl, pl.name, Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)))
                            Spacer(Modifier.width(12.dp))
                            Text(pl.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

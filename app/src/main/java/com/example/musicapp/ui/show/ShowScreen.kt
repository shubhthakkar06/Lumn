package com.example.musicapp.ui.show

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import com.example.musicapp.data.catalog.ShowDetail
import com.example.musicapp.data.catalog.ShowRef
import com.example.musicapp.data.catalog.Shows
import com.example.musicapp.data.download.MusicDownloadManager
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.shows.ShowFollowManager
import com.example.musicapp.ui.components.TrackArtwork
import com.example.musicapp.ui.main.MainScreenViewModel
import com.example.musicapp.ui.navigation.Screen
import com.example.musicapp.ui.util.rememberDynamicGradient

/** A podcast or YouTube channel: follow it, browse and play its episodes (newest first). */
@Composable
fun ShowScreen(
    screen: Screen.Show,
    viewModel: MainScreenViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val show = remember(screen) { ShowRef(screen.showId, screen.title, screen.author, screen.imageUrl) }
    val follows = remember { ShowFollowManager.getInstance(context) }
    val followed by follows.shows.collectAsStateWithLifecycle()
    val isFollowing = followed.any { it.id == show.id }
    val accent = MaterialTheme.colorScheme.primary
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val loadingId by viewModel.loadingSongVideoId.collectAsStateWithLifecycle()
    val downloads = remember { MusicDownloadManager.getInstance(context) }

    var detail by remember(show.id) { mutableStateOf<ShowDetail?>(null) }
    var failed by remember(show.id) { mutableStateOf(false) }
    var episodes by remember(show.id) { mutableStateOf<List<SongInfo>>(emptyList()) }
    var loadingMore by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    LaunchedEffect(show.id) {
        val d = runCatching { Shows.detail(show) }.getOrNull()
        detail = d
        episodes = d?.episodes.orEmpty()
        failed = d == null
    }
    val header = detail?.show ?: show
    val gradient = rememberDynamicGradient(header.imageUrl)
    val listState = rememberLazyListState()

    // Load older episodes when the list nears its end.
    val nearEnd by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= listState.layoutInfo.totalItemsCount - 4 } == true } }
    LaunchedEffect(nearEnd, episodes.size) {
        if (nearEnd && !loadingMore && detail != null && Shows.hasMore(header)) {
            loadingMore = true
            val more = runCatching { Shows.moreEpisodes(header) }.getOrDefault(emptyList())
            episodes = (episodes + more).distinctBy { it.videoId }
            loadingMore = false
        }
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(gradient + listOf(Color(0xFF0A0E14))))) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp)) {
            item(key = "header") {
                Column(Modifier.fillMaxWidth().statusBarsPadding()) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TrackArtwork(header.imageUrl, header.title, Modifier.size(132.dp).clip(RoundedCornerShape(14.dp)))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(header.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Text(header.subtitle.ifBlank { header.author }, color = Color(0xFFCBD5E1), fontSize = 13.sp, maxLines = 2)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = {
                                val now = follows.toggle(header.toFollowed())
                                Toast.makeText(context, if (now) "Following ${header.title}" else "Unfollowed", Toast.LENGTH_SHORT).show()
                            },
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isFollowing) accent else Color(0xFF94A3B8))
                        ) { Text(if (isFollowing) "Following" else "Follow", color = if (isFollowing) accent else Color.White, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.weight(1f))
                        if (episodes.isNotEmpty()) {
                            Box(Modifier.size(54.dp).clip(CircleShape).background(accent).clickable { viewModel.playSong(episodes.first(), episodes) }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play latest", tint = Color.Black, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                    val about = detail?.description.orEmpty()
                    if (about.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            about, color = Color(0xFFA0AEC0), fontSize = 13.sp,
                            maxLines = if (showAbout) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { showAbout = !showAbout }
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("All episodes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(Modifier.height(4.dp))
                }
            }
            when {
                detail == null && !failed -> item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = accent) } }
                failed -> item { Text("Couldn't load this show. Check your connection and try again.", color = Color(0xFFA0AEC0), modifier = Modifier.padding(vertical = 24.dp)) }
                else -> items(episodes, key = { it.videoId }) { ep ->
                    EpisodeRow(
                        episode = ep,
                        isCurrent = currentSong?.videoId == ep.videoId,
                        isPlaying = isPlaying && currentSong?.videoId == ep.videoId,
                        isLoading = loadingId == ep.videoId,
                        accent = accent,
                        onPlay = { if (currentSong?.videoId == ep.videoId) viewModel.togglePlayPause() else viewModel.playSong(ep, episodes) },
                        onQueue = { viewModel.addToQueue(ep); Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show() },
                        onDownload = { downloads.downloadSong(ep); Toast.makeText(context, "Downloading episode", Toast.LENGTH_SHORT).show() }
                    )
                }
            }
            if (loadingMore) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), color = accent, strokeWidth = 2.dp) } }
        }
    }
}

@Composable
fun EpisodeRow(
    episode: SongInfo,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isLoading: Boolean,
    accent: Color,
    onPlay: () -> Unit,
    onQueue: () -> Unit,
    onDownload: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onPlay).padding(vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            TrackArtwork(episode.albumArtUrl, episode.title, Modifier.size(width = 96.dp, height = 54.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(episode.title, color = if (isCurrent) accent else Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        if (episode.description.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(episode.description, color = Color(0xFF94A3B8), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOf(episode.releaseDate, formatEpisodeLength(episode.durationSeconds)).filter { it.isNotBlank() }.joinToString(" • "),
                color = Color(0xFFA0AEC0), fontSize = 12.sp, modifier = Modifier.weight(1f)
            )
            if (!episode.videoId.startsWith("pod_")) {
                Icon(Icons.Default.OndemandVideo, contentDescription = "Has video", tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
            }
            IconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Download, contentDescription = "Download episode", tint = Color(0xFFCBD5E1)) }
            IconButton(onClick = onQueue, modifier = Modifier.size(36.dp)) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to queue", tint = Color(0xFFCBD5E1)) }
            Box(Modifier.size(34.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                if (isLoading) CircularProgressIndicator(Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                else Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (isPlaying) "Pause" else "Play", tint = Color.Black, modifier = Modifier.size(20.dp))
            }
        }
    }
    HorizontalDivider(color = Color(0x14FFFFFF))
}

/** "1 hr 2 min", "34 min". */
fun formatEpisodeLength(seconds: Long): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "$h hr $m min" else "${maxOf(1, m)} min"
}

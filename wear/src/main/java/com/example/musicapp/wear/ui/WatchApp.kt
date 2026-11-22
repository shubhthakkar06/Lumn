package com.example.musicapp.wear.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Text
import coil3.compose.AsyncImage
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.provider.ProviderRouter
import com.example.musicapp.wear.data.WatchDownloads
import com.example.musicapp.wear.data.WatchLibrary
import com.example.musicapp.wear.data.WatchSyncService
import com.example.musicapp.wear.playback.WatchPlayer
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Accent = Color(0xFF1ED760)

/**
 * Pages, left to right: Player → Playlists → Search (swipe left to move on).
 * On the player, swipe down for the queue and up for the clock.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WatchApp(player: WatchPlayer) {
    val pages = rememberPagerState(pageCount = { 3 })
    var opened by remember { mutableStateOf<Pair<String, List<SongInfo>>?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pages, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> PlayerArea(player)
                1 -> PlaylistsPage(onOpen = { title, songs -> opened = title to songs })
                else -> SearchPage(player)
            }
        }
        opened?.let { (title, songs) ->
            BackHandler { opened = null }
            PlaylistDetail(title, songs, player, onClose = { opened = null }) { opened = null }
        }
    }
}

// ---------------------------------------------------------------------- player

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerArea(player: WatchPlayer) {
    val pages = rememberPagerState(initialPage = 1, pageCount = { 3 })
    VerticalPager(state = pages, modifier = Modifier.fillMaxSize()) { page ->
        when (page) {
            0 -> QueuePage(player)
            1 -> PlayerPage(player)
            else -> ClockPage(player)
        }
    }
}

@Composable
private fun PlayerPage(player: WatchPlayer) {
    val context = LocalContext.current
    val song by player.current.collectAsStateWithLifecycle()
    val playing by player.isPlaying.collectAsStateWithLifecycle()
    val progress by player.progress.collectAsStateWithLifecycle()
    val library by WatchLibrary.get(context).payload.collectAsStateWithLifecycle()
    val ring by animateFloatAsState(progress, label = "ring")
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var volumeShownAt by remember { mutableLongStateOf(0L) }
    var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun changeVolume(up: Boolean) {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
        volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        volumeShownAt = System.currentTimeMillis()
    }

    Box(
        Modifier
            .fillMaxSize()
            // Turning the bezel or crown changes the volume.
            .onRotaryScrollEvent { changeVolume(it.verticalScrollPixels > 0); true }
            .focusRequester(focus)
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(song?.albumArtUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.66f)))

        if (song == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text("Rivara", color = Accent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (library.email.isBlank()) "Open Rivara on your phone to sync your library" else "Swipe left for your playlists",
                    color = Color(0xFFCBD5E1), fontSize = 12.sp, textAlign = TextAlign.Center
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp)) {
                Text(song!!.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Text(song!!.artist, color = Color(0xFFB8C2D0), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RoundIcon(Icons.Default.SkipPrevious, "Previous", 44.dp) { player.previous() }
                    // Play / pause with a ring that fills as the song plays.
                    Box(Modifier.size(64.dp).clip(CircleShape).clickable { player.toggle() }, contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val stroke = 4.dp.toPx()
                            val inset = stroke / 2
                            drawArc(Color.White.copy(alpha = 0.18f), 0f, 360f, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
                            drawArc(Accent, -90f, 360f * ring, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
                        }
                        Box(Modifier.size(50.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pause" else "Play", Color.Black, 30.dp)
                        }
                    }
                    RoundIcon(Icons.Default.SkipNext, "Next", 44.dp) { player.next() }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    val liked = library.liked.any { it.videoId == song!!.videoId }
                    RoundIcon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Like", 32.dp, if (liked) Accent else Color.White) {
                        WatchSyncService.sendLike(context, WatchLibrary.json.encodeToString(SongInfo.serializer(), song!!))
                    }
                    RoundIcon(Icons.Default.VolumeDown, "Volume down", 32.dp) { changeVolume(false) }
                    RoundIcon(Icons.Default.VolumeUp, "Volume up", 32.dp) { changeVolume(true) }
                }
            }
        }

        // Volume arc along the right edge while it's being changed.
        var showVolume by remember { mutableStateOf(false) }
        LaunchedEffect(volumeShownAt) {
            if (volumeShownAt == 0L) return@LaunchedEffect
            showVolume = true
            delay(1_500)
            showVolume = false
        }
        if (showVolume) {
            Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                val stroke = 5.dp.toPx()
                drawArc(Color.White.copy(alpha = 0.2f), -40f, 80f, false, style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(Accent, 40f - 80f * volume, 80f * volume, false, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
private fun QueuePage(player: WatchPlayer) {
    val queue by player.queue.collectAsStateWithLifecycle()
    val index by player.index.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().background(Color(0xFF0B0F14)), contentPadding = PaddingValues(top = 28.dp, bottom = 40.dp, start = 18.dp, end = 18.dp)) {
        item { Text("Queue", color = Accent, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
        if (queue.isEmpty()) item { Text("Nothing queued", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(top = 16.dp), textAlign = TextAlign.Center) }
        itemsIndexed(queue) { i, s -> SongChip(s, highlighted = i == index, dimmed = i < index) { player.jumpTo(i) } }
    }
}

@Composable
private fun ClockPage(player: WatchPlayer) {
    val song by player.current.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1_000) } }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AsyncImage(song?.albumArtUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.55f)))))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                SimpleDateFormat("h:mm", Locale.getDefault()).format(now),
                style = TextStyle(
                    fontSize = 58.sp, fontWeight = FontWeight.Thin, letterSpacing = (-2).sp,
                    brush = Brush.verticalGradient(listOf(Color.White, Accent.copy(alpha = 0.9f))),
                    shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 4f), 16f)
                )
            )
            Text(SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(now).uppercase(), color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, letterSpacing = 3.sp)
            song?.let {
                Spacer(Modifier.height(10.dp))
                Text(it.title, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 36.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------- library

@Composable
private fun PlaylistsPage(onOpen: (String, List<SongInfo>) -> Unit) {
    val context = LocalContext.current
    val library by WatchLibrary.get(context).payload.collectAsStateWithLifecycle()
    val downloaded by WatchDownloads.get(context).downloaded.collectAsStateWithLifecycle()
    val allSongs = remember(library) { (library.liked + library.playlists.flatMap { it.songs }).distinctBy { it.videoId } }
    LazyColumn(Modifier.fillMaxSize().background(Color(0xFF0B0F14)), contentPadding = PaddingValues(top = 26.dp, bottom = 40.dp, start = 16.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Your Library", color = Accent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(if (library.email.isBlank()) "Open Rivara on your phone" else library.email, color = Color.Gray, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item { LibraryChip(Icons.Default.Favorite, "Liked Songs", "${library.liked.size} songs") { onOpen("Liked Songs", library.liked) } }
        item {
            LibraryChip(Icons.Default.DownloadDone, "Downloads", "${downloaded.size} on watch") {
                onOpen("Downloads", allSongs.filter { it.videoId in downloaded })
            }
        }
        items(library.playlists, key = { it.id }) { p ->
            LibraryChip(Icons.Default.QueueMusic, p.name, "${p.songs.size} songs", p.coverUrl.ifBlank { p.songs.firstOrNull()?.albumArtUrl.orEmpty() }) { onOpen(p.name, p.songs) }
        }
    }
}

@Composable
private fun PlaylistDetail(title: String, songs: List<SongInfo>, player: WatchPlayer, onClose: () -> Unit, onPlayed: () -> Unit) {
    val context = LocalContext.current
    val downloads = remember { WatchDownloads.get(context) }
    val downloaded by downloads.downloaded.collectAsStateWithLifecycle()
    val pending by downloads.pending.collectAsStateWithLifecycle()
    val saved = songs.count { it.videoId in downloaded }
    LazyColumn(Modifier.fillMaxSize().background(Color(0xFF0B0F14)), contentPadding = PaddingValues(top = 24.dp, bottom = 40.dp, start = 16.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, textAlign = TextAlign.Center)
                Text("${songs.size} songs • $saved on watch", color = Color.Gray, fontSize = 10.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RoundIcon(Icons.Default.PlayArrow, "Play", 40.dp, Color.Black, Accent) { if (songs.isNotEmpty()) { player.play(songs); onPlayed() } }
                    RoundIcon(Icons.Default.Shuffle, "Shuffle", 40.dp) { if (songs.isNotEmpty()) { player.play(songs.shuffled()); onPlayed() } }
                    RoundIcon(
                        if (saved == songs.size && songs.isNotEmpty()) Icons.Default.DownloadDone else Icons.Default.Download,
                        "Download all", 40.dp, if (pending.isNotEmpty()) Accent else Color.White
                    ) { downloads.enqueue(songs) }
                    RoundIcon(Icons.Default.Close, "Close", 40.dp) { onClose() }
                }
            }
        }
        itemsIndexed(songs, key = { i, s -> "${s.videoId}#$i" }) { i, s ->
            SongChip(s, badge = when (s.videoId) { in downloaded -> "✓"; in pending -> "…"; else -> null }) { player.play(songs, i); onPlayed() }
        }
    }
}

// ---------------------------------------------------------------------- search

@Composable
private fun SearchPage(player: WatchPlayer) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SongInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val downloads = remember { WatchDownloads.get(context) }
    val downloaded by downloads.downloaded.collectAsStateWithLifecycle()
    LaunchedEffect(submitted) {
        if (submitted.isBlank()) return@LaunchedEffect
        loading = true
        results = runCatching { ProviderRouter.shared.search(submitted).take(20) }.getOrDefault(emptyList())
        loading = false
    }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { query = it; submitted = it }
        }
    }
    LazyColumn(Modifier.fillMaxSize().background(Color(0xFF0B0F14)), contentPadding = PaddingValues(top = 26.dp, bottom = 40.dp, start = 16.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text("Search", color = Accent, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF1C2330)).padding(start = 12.dp)) {
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp), cursorBrush = SolidColor(Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submitted = query.trim() }),
                    modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                    decorationBox = { inner -> if (query.isEmpty()) Text("Songs, artists", color = Color.Gray, fontSize = 13.sp); inner() }
                )
                RoundIcon(Icons.Default.Mic, "Voice search", 36.dp) {
                    runCatching {
                        voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))
                    }
                }
            }
        }
        if (loading) item { Text("Searching…", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
        if (!loading && submitted.isNotBlank() && results.isEmpty()) item { Text("No results", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
        itemsIndexed(results, key = { i, s -> "${s.videoId}#$i" }) { i, s ->
            // Tap to play; long-press to save it on the watch.
            SongChip(s, badge = if (s.videoId in downloaded) "✓" else null, onLongClick = { downloads.enqueue(listOf(s)) }) { player.play(results, i) }
        }
    }
}

// ---------------------------------------------------------------------- pieces

@Composable
private fun Icon(vector: ImageVector, description: String, tint: Color, size: androidx.compose.ui.unit.Dp) {
    Image(vector, description, colorFilter = ColorFilter.tint(tint), modifier = Modifier.size(size))
}

@Composable
private fun RoundIcon(
    vector: ImageVector, description: String, size: androidx.compose.ui.unit.Dp,
    tint: Color = Color.White, background: Color = Color.White.copy(alpha = 0.14f), onClick: () -> Unit
) {
    Box(Modifier.size(size).clip(CircleShape).background(background).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(vector, description, tint, size * 0.55f)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongChip(
    song: SongInfo, highlighted: Boolean = false, dimmed: Boolean = false, badge: String? = null,
    onLongClick: (() -> Unit)? = null, onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(if (highlighted) Accent.copy(alpha = 0.22f) else Color(0xFF161C26))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(song.albumArtUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(30.dp).clip(CircleShape).background(Color(0xFF243042)))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = if (dimmed) Color.Gray else Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = Color.Gray, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        badge?.let { Text(it, color = Accent, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp)) }
    }
}

@Composable
private fun LibraryChip(vector: ImageVector, title: String, subtitle: String, image: String = "", onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF161C26)).clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF243042)), contentAlignment = Alignment.Center) {
            if (image.isNotBlank()) AsyncImage(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(vector, title, Accent, 18.dp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Color.Gray, fontSize = 10.sp)
        }
    }
}

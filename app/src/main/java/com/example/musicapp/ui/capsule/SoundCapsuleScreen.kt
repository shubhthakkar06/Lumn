package com.example.musicapp.ui.capsule

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.musicapp.data.MusicCatalog
import com.example.musicapp.data.auth.UserManager
import com.example.musicapp.data.history.PlayHistoryManager
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.ui.main.MainScreenViewModel
import com.example.musicapp.ui.navigation.Screen
import java.util.Calendar

@Composable
fun SoundCapsuleScreen(
    onBack: () -> Unit,
    viewModel: MainScreenViewModel? = null,
    onNavigate: ((Screen) -> Unit)? = null
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    var showInfoDialog by remember { mutableStateOf(false) }

    val currentUser by UserManager.getInstance(context).currentUser.collectAsState()
    val historyManager = remember { PlayHistoryManager.getInstance(context) }
    val rawHistory by historyManager.history.collectAsState()

    val userRecords = remember(rawHistory, currentUser) {
        historyManager.getUserHistory(currentUser.id)
    }

    // October 2026 stats
    val octStats = remember(userRecords, currentUser) {
        historyManager.getMonthlyStats(currentUser.id, Calendar.OCTOBER, 2026)
    }

    // September 2026 stats
    val septStats = remember(userRecords, currentUser) {
        historyManager.getMonthlyStats(currentUser.id, Calendar.SEPTEMBER, 2026)
    }

    // Dynamic Top Artists & Songs for October
    val topArtistOct = remember(octStats, currentUser) {
        if (octStats.topArtist != "Your Top Artist" && octStats.playCount > 0) {
            octStats.topArtist
        } else {
            currentUser.selectedArtists.firstOrNull() ?: "Pritam"
        }
    }
    val topArtistOctImage = remember(topArtistOct) {
        MusicCatalog.getArtistImageUrl(topArtistOct)
    }

    val topSongOct = remember(octStats, userRecords, topArtistOct) {
        val matchingRecord = userRecords.find { it.title == octStats.topSongTitle }
        if (matchingRecord != null) {
            SongInfo(matchingRecord.videoId, matchingRecord.title, matchingRecord.artist, matchingRecord.albumArtUrl)
        } else {
            val artistData = MusicCatalog.getArtistData(topArtistOct)
            artistData.topTracks.firstOrNull() ?: SongInfo("yKNxeF4PgnY", "Yellow", "Coldplay", "https://i.ytimg.com/vi/yKNxeF4PgnY/hqdefault.jpg")
        }
    }

    // Dynamic Top Artists & Songs for September
    val topArtistSept = remember(septStats, currentUser) {
        if (septStats.topArtist != "Your Top Artist" && septStats.playCount > 0) {
            septStats.topArtist
        } else {
            currentUser.selectedArtists.getOrNull(1) ?: currentUser.selectedArtists.firstOrNull() ?: "Alan Walker"
        }
    }
    val topArtistSeptImage = remember(topArtistSept) {
        MusicCatalog.getArtistImageUrl(topArtistSept)
    }

    val topSongSept = remember(septStats, userRecords, topArtistSept) {
        val matchingRecord = userRecords.find { it.title == septStats.topSongTitle }
        if (matchingRecord != null) {
            SongInfo(matchingRecord.videoId, matchingRecord.title, matchingRecord.artist, matchingRecord.albumArtUrl)
        } else {
            val artistData = MusicCatalog.getArtistData(topArtistSept)
            artistData.topTracks.firstOrNull() ?: SongInfo("dvgZkm1xWPE", "Viva La Vida", "Coldplay", "https://i.ytimg.com/vi/dvgZkm1xWPE/hqdefault.jpg")
        }
    }

    // Dynamic Combo Songs (Story 1)
    val comboSong1 = remember(userRecords, topSongOct) {
        userRecords.getOrNull(0)?.let { SongInfo(it.videoId, it.title, it.artist, it.albumArtUrl) } ?: topSongOct
    }
    val comboSong2 = remember(userRecords, topSongSept, comboSong1) {
        userRecords.firstOrNull { it.videoId != comboSong1.videoId }?.let { SongInfo(it.videoId, it.title, it.artist, it.albumArtUrl) }
            ?: topSongSept
    }

    // Dynamic Throwback Song (Story 2)
    val throwbackSong = remember(userRecords, comboSong1, comboSong2) {
        userRecords.firstOrNull { it.videoId != comboSong1.videoId && it.videoId != comboSong2.videoId }?.let {
            SongInfo(it.videoId, it.title, it.artist, it.albumArtUrl)
        } ?: MusicCatalog.mostFavSongs.first()
    }

    // Dynamic Repeat Song (Story 3)
    val repeatRecord = remember(userRecords) {
        userRecords.groupingBy { it.videoId }.eachCount().maxByOrNull { it.value }?.key?.let { vId ->
            userRecords.firstOrNull { it.videoId == vId }
        }
    }
    val repeatCount = remember(userRecords, repeatRecord) {
        if (repeatRecord != null) {
            userRecords.count { it.videoId == repeatRecord.videoId }.coerceAtLeast(3)
        } else {
            10
        }
    }
    val repeatSong = remember(repeatRecord, comboSong1) {
        repeatRecord?.let { SongInfo(it.videoId, it.title, it.artist, it.albumArtUrl) } ?: comboSong1
    }

    // Dynamic Streak Song (Story 4)
    val streakSong = remember(userRecords, topSongSept) {
        userRecords.lastOrNull()?.let { SongInfo(it.videoId, it.title, it.artist, it.albumArtUrl) } ?: topSongSept
    }

    fun shareCapsuleStory(title: String, body: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "$title\n$body\n\nTracked in my Lumn Sound Capsule ✨")
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Sound Capsule via"))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 44.dp, bottom = 120.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Your Sound Capsule",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }

            // OCTOBER 2026 SECTION (Dynamic)
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showInfoDialog = true }
                        ) {
                            Text(
                                text = "October 2026",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.Default.HelpOutline,
                                contentDescription = "Info",
                                tint = Color(0xFFA0A0A0),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = {
                            shareCapsuleStory(
                                "October 2026 Sound Capsule",
                                "Time listened: ${octStats.totalMinutes} minutes. Top Artist: $topArtistOct. Top Song: ${topSongOct.title}."
                            )
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Time Listened Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF222222))
                            .clickable {
                                Toast.makeText(context, "Detailed listening breakdown for October (${octStats.playCount} songs played)", Toast.LENGTH_SHORT).show()
                            }
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Time listened", style = MaterialTheme.typography.labelSmall, color = Color(0xFFA0A0A0))
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFFA0A0A0))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${octStats.totalMinutes} minutes",
                                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF1ED760)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Top Artist & Top Song 2-Card Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Top Artist
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF222222))
                                .clickable {
                                    onNavigate?.invoke(Screen.Artist(topArtistOct, topArtistOctImage))
                                }
                                .padding(16.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Top artist", style = MaterialTheme.typography.labelSmall, color = Color(0xFFA0A0A0))
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFFA0A0A0), modifier = Modifier.size(16.dp))
                                }
                                Text(
                                    text = topArtistOct,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF5390EC),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                AsyncImage(
                                    model = topArtistOctImage,
                                    contentDescription = topArtistOct,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(92.dp)
                                        .clip(CircleShape)
                                        .align(Alignment.CenterHorizontally)
                                )
                            }
                        }

                        // Top Song
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF222222))
                                .clickable {
                                    viewModel?.playSong(topSongOct)
                                }
                                .padding(16.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Top song", style = MaterialTheme.typography.labelSmall, color = Color(0xFFA0A0A0))
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFFA0A0A0), modifier = Modifier.size(16.dp))
                                }
                                Text(
                                    text = topSongOct.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFFFD166),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                AsyncImage(
                                    model = topSongOct.albumArtUrl,
                                    contentDescription = topSongOct.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(92.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .align(Alignment.CenterHorizontally)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // STORY CARD 1: "A combo as unique as you"
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF222222))
                            .padding(16.dp)
                    ) {
                        Column {
                            // Overlapping Covers
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = comboSong2.albumArtUrl,
                                    contentDescription = comboSong2.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .offset(x = 40.dp, y = 10.dp)
                                        .size(140.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                AsyncImage(
                                    model = comboSong1.albumArtUrl,
                                    contentDescription = comboSong1.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .offset(x = (-40).dp, y = (-10).dp)
                                        .size(140.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "A combo as unique as you",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "You listened to ${comboSong1.title} by ${comboSong1.artist} and ${comboSong2.title} by ${comboSong2.artist} in the same week. Tap to explore.",
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                                color = Color(0xFFA0A0A0)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Track 1
                            CapsuleSongRow(
                                song = comboSong1,
                                onPlay = { viewModel?.playSong(comboSong1) }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Track 2
                            CapsuleSongRow(
                                song = comboSong2,
                                onPlay = { viewModel?.playSong(comboSong2) }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Sept 28 - Oct 4, 2026",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFA0A0A0)
                                )
                                IconButton(onClick = {
                                    shareCapsuleStory(
                                        "A combo as unique as you",
                                        "You listened to ${comboSong1.title} and ${comboSong2.title} in the same week."
                                    )
                                }) {
                                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // SEPTEMBER 2026 SECTION (Dynamic)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "September 2026",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        IconButton(onClick = {
                            shareCapsuleStory(
                                "September 2026 Sound Capsule",
                                "Time listened: ${septStats.totalMinutes} minutes. Top Artist: $topArtistSept."
                            )
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // September Time Listened
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF222222))
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Time listened", style = MaterialTheme.typography.labelSmall, color = Color(0xFFA0A0A0))
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFFA0A0A0))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${septStats.totalMinutes} minutes",
                                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF1ED760)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // September Top Artist & Top Song
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF222222))
                                .clickable {
                                    onNavigate?.invoke(Screen.Artist(topArtistSept, topArtistSeptImage))
                                }
                                .padding(16.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Top artist", style = MaterialTheme.typography.labelSmall, color = Color(0xFFA0A0A0))
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFFA0A0A0), modifier = Modifier.size(16.dp))
                                }
                                Text(
                                    text = topArtistSept,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF5390EC),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                AsyncImage(
                                    model = topArtistSeptImage,
                                    contentDescription = topArtistSept,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(92.dp)
                                        .clip(CircleShape)
                                        .align(Alignment.CenterHorizontally)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF222222))
                                .clickable {
                                    viewModel?.playSong(topSongSept)
                                }
                                .padding(16.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Top song", style = MaterialTheme.typography.labelSmall, color = Color(0xFFA0A0A0))
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFFA0A0A0), modifier = Modifier.size(16.dp))
                                }
                                Text(
                                    text = topSongSept.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFFFD166),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                AsyncImage(
                                    model = topSongSept.albumArtUrl,
                                    contentDescription = topSongSept.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(92.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .align(Alignment.CenterHorizontally)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // STORY CARD 2: "Throwback listening"
                    CapsuleInsightCard(
                        artworkUrl = throwbackSong.albumArtUrl,
                        headline = "Throwback to your favorites",
                        story = "You enjoyed listening to ${throwbackSong.title} by ${throwbackSong.artist}.",
                        song = throwbackSong,
                        dateRange = "Sept 2026",
                        onPlay = { viewModel?.playSong(throwbackSong) },
                        onShare = { shareCapsuleStory("Throwback favorites", "Loved listening to ${throwbackSong.title} by ${throwbackSong.artist}.") }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // STORY CARD 3: "You hit plays on repeat"
                    CapsuleInsightCard(
                        artworkUrl = repeatSong.albumArtUrl,
                        headline = "You hit $repeatCount plays",
                        story = "You listened to ${repeatSong.title} by ${repeatSong.artist} on repeat. Such a dedicated fan.",
                        song = repeatSong,
                        dateRange = "Sept 21 - 27, 2026",
                        onPlay = { viewModel?.playSong(repeatSong) },
                        onShare = { shareCapsuleStory("Top Track", "You listened to ${repeatSong.title} by ${repeatSong.artist} $repeatCount times!") }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // STORY CARD 4: "Listening streak"
                    CapsuleInsightCard(
                        artworkUrl = streakSong.albumArtUrl,
                        headline = "You kept the music going",
                        story = "You played ${streakSong.title} by ${streakSong.artist} day after day.",
                        song = streakSong,
                        dateRange = "Sept 14 - 20, 2026",
                        onPlay = { viewModel?.playSong(streakSong) },
                        onShare = { shareCapsuleStory("Listening streak", "You played ${streakSong.title} day after day.") }
                    )
                }
            }
        }

        // Floating Month Scrubber Pill on Edge
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xDD303030))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (listState.firstVisibleItemScrollOffset > 300) "Sept, 2026" else "Oct, 2026",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.UnfoldMore, contentDescription = null, tint = Color(0xFFA0A0A0), modifier = Modifier.size(14.dp))
            }
        }

        // Help Info Dialog
        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                containerColor = Color(0xFF282828),
                title = { Text("About Your Sound Capsule", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = "Your Sound Capsule is your personalized, monthly listening time capsule. It automatically tracks your stream minutes, top artists, songs on repeat, and surprising combinations.",
                        color = Color(0xFFA0A0A0),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showInfoDialog = false }) {
                        Text("Got it", color = Color(0xFF1ED760), fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

@Composable
private fun CapsuleSongRow(
    song: SongInfo,
    onPlay: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onPlay)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.albumArtUrl,
            contentDescription = song.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFA0A0A0),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "Options",
            tint = Color(0xFFA0A0A0),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun CapsuleInsightCard(
    artworkUrl: String,
    headline: String,
    story: String,
    song: SongInfo,
    dateRange: String,
    onPlay: () -> Unit,
    onShare: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF222222))
            .padding(16.dp)
    ) {
        Column {
            AsyncImage(
                model = artworkUrl,
                contentDescription = headline,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = headline,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = story,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                color = Color(0xFFA0A0A0)
            )
            Spacer(modifier = Modifier.height(14.dp))
            CapsuleSongRow(song = song, onPlay = onPlay)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateRange,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFA0A0A0)
                )
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

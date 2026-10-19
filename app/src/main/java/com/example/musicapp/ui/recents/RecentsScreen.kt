package com.example.musicapp.ui.recents

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import android.widget.Toast
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.musicapp.data.auth.UserManager
import com.example.musicapp.data.history.PlayHistoryManager
import com.example.musicapp.data.history.PlayedSongRecord
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.ui.main.MainScreenViewModel
import com.example.musicapp.ui.navigation.Screen
import com.example.musicapp.ui.player.PlayerOptionsSheet
import java.util.Calendar

@Composable
fun RecentsScreen(
    onBack: () -> Unit,
    viewModel: MainScreenViewModel? = null,
    onNavigate: ((Screen) -> Unit)? = null
) {
    val context = LocalContext.current
    val userManager = remember { UserManager.getInstance(context) }
    val currentUser by userManager.currentUser.collectAsStateWithLifecycle()
    val historyManager = remember { PlayHistoryManager.getInstance(context) }
    val history by historyManager.history.collectAsStateWithLifecycle()
    var selectedSongForOptions by remember { mutableStateOf<SongInfo?>(null) }

    val userHistory = remember(history, currentUser.id) {
        history.filter { it.userId == currentUser.id || it.userId.isEmpty() }
    }

    val (todayList, yesterdayList, earlierList) = remember(userHistory) {
        val today = mutableListOf<PlayedSongRecord>()
        val yesterday = mutableListOf<PlayedSongRecord>()
        val earlier = mutableListOf<PlayedSongRecord>()

        val now = Calendar.getInstance()
        val nowDay = now.get(Calendar.DAY_OF_YEAR)
        val nowYear = now.get(Calendar.YEAR)

        val cal = Calendar.getInstance()

        userHistory.forEach { record ->
            cal.timeInMillis = record.playedAtMs
            val recDay = cal.get(Calendar.DAY_OF_YEAR)
            val recYear = cal.get(Calendar.YEAR)

            if (recYear == nowYear && recDay == nowDay) {
                today.add(record)
            } else if (recYear == nowYear && recDay == nowDay - 1) {
                yesterday.add(record)
            } else {
                earlier.add(record)
            }
        }

        Triple(today, yesterday, earlier)
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF121212))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Recently played",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        if (userHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color(0xFF1ED760),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No listening history yet",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Songs you listen to will show up right here in real time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                if (todayList.isNotEmpty()) {
                    item {
                        SectionHeader("Today")
                    }
                    items(todayList, key = { it.id }) { rec ->
                        val song = SongInfo(rec.videoId, rec.title, rec.artist, rec.albumArtUrl)
                        RecentTrackRow(
                            record = rec,
                            onPlay = { viewModel?.playSong(song) },
                            onOptions = { selectedSongForOptions = song }
                        )
                    }
                }

                if (yesterdayList.isNotEmpty()) {
                    item {
                        SectionHeader("Yesterday")
                    }
                    items(yesterdayList, key = { it.id }) { rec ->
                        val song = SongInfo(rec.videoId, rec.title, rec.artist, rec.albumArtUrl)
                        RecentTrackRow(
                            record = rec,
                            onPlay = { viewModel?.playSong(song) },
                            onOptions = { selectedSongForOptions = song }
                        )
                    }
                }

                if (earlierList.isNotEmpty()) {
                    item {
                        SectionHeader("Earlier")
                    }
                    items(earlierList, key = { it.id }) { rec ->
                        val song = SongInfo(rec.videoId, rec.title, rec.artist, rec.albumArtUrl)
                        RecentTrackRow(
                            record = rec,
                            onPlay = { viewModel?.playSong(song) },
                            onOptions = { selectedSongForOptions = song }
                        )
                    }
                }
            }
        }

        if (selectedSongForOptions != null) {
            val target = selectedSongForOptions!!
            PlayerOptionsSheet(
                song = target,
                onDismiss = { selectedSongForOptions = null },
                onPlayNext = {
                    viewModel?.playNext(target)
                    Toast.makeText(context, "Playing next: ${target.title}", Toast.LENGTH_SHORT).show()
                    selectedSongForOptions = null
                },
                onAddToQueue = {
                    viewModel?.addToQueue(target)
                    Toast.makeText(context, "Added to queue: ${target.title}", Toast.LENGTH_SHORT).show()
                    selectedSongForOptions = null
                },
                onViewArtist = { artistName ->
                    selectedSongForOptions = null
                    onNavigate?.invoke(Screen.Artist(artistName))
                }
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = Color.White,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun RecentTrackRow(
    record: PlayedSongRecord,
    onPlay: () -> Unit,
    onOptions: () -> Unit
) {
    val relativeTime = remember(record.playedAtMs) {
        DateUtils.getRelativeTimeSpanString(
            record.playedAtMs,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = record.albumArtUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF282828))
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${record.artist} • $relativeTime",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFA0A0A0),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onPlay) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF222222)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        IconButton(
            onClick = onOptions,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Song options",
                tint = Color(0xFFA0AEC0),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

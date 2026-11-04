package com.example.musicapp.desktop.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.musicapp.desktop.model.DesktopSong
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun DesktopPlayerBar(
    currentSong: DesktopSong?,
    isPlaying: Boolean,
    progressMs: Long,
    volume: Float,
    isShuffle: Boolean,
    isRepeat: Boolean,
    isLyricsOpen: Boolean,
    isQueueOpen: Boolean,
    isLiked: Boolean = false,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    themeAccent: Color,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleLyrics: () -> Unit,
    onToggleQueue: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleDownload: () -> Unit,
    onOpenEqualizer: () -> Unit = {},
    onOpenDeviceSelector: () -> Unit = {},
    onShareSong: () -> Unit,
    onOpenFullscreen: () -> Unit,
    playbackSpeed: Float = 1.0f,
    onSpeedChange: (Float) -> Unit = {},
    sleepTimerRemainingSeconds: Long? = null,
    sleepTimerLabel: String? = null,
    onSetSleepTimerMinutes: (Int, String) -> Unit = { _, _ -> },
    onSetSleepTimerEndOfTrack: () -> Unit = {},
    onCancelSleepTimer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showSleepTimerMenu by remember { mutableStateOf(false) }
    val durationSeconds = currentSong?.durationSeconds ?: 180L
    val totalMs = durationSeconds * 1000L
    val progressFraction = if (totalMs > 0) (progressMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f) else 0f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(92.dp),
        color = Color(0xFF181818),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Section: Album Art & Track Info (Clickable -> Opens Fullscreen Player)
            Row(
                modifier = Modifier
                    .width(300.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenFullscreen),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF282828)),
                    contentAlignment = Alignment.Center
                ) {
                    val localAsset = when {
                        currentSong?.id == "baps_ep_aksharatitdas" || currentSong?.title?.contains("અક્ષરાતીતદાસ") == true -> "assets/aksharatitdas_episode.png"
                        currentSong?.id == "baps_ep_uttamcharan" || currentSong?.title?.contains("ઉત્તમયોગીદાસ") == true -> "assets/uttamyogidas_episode.png"
                        currentSong?.id == "baps_ep_atmatruptdas" || currentSong?.title?.contains("આત્મતૃપ્તદાસ") == true || currentSong?.title?.contains("Atmatruptdas") == true -> "assets/atmatruptdas_episode.png"
                        currentSong?.id == "baps_ep_1" || currentSong?.title?.contains("Somprakashdas") == true || currentSong?.title?.contains("સોમપ્રકાશદાસ") == true -> "assets/somprakashdas_episode.png"
                        currentSong?.albumArtUrl?.startsWith("assets/") == true -> currentSong.albumArtUrl
                        else -> null
                    }

                    if (localAsset != null) {
                        Image(
                            painter = painterResource(localAsset),
                            contentDescription = currentSong?.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (currentSong != null && currentSong.albumArtUrl.isNotBlank()) {
                        AsyncImage(
                            model = currentSong.albumArtUrl,
                            contentDescription = currentSong.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentSong?.title ?: "Select a track to play",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentSong?.artist ?: "Rivara Desktop",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFAAAAAA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (currentSong != null) {
                    IconButton(onClick = onToggleLike, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (isLiked) themeAccent else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(onClick = onToggleDownload, modifier = Modifier.size(32.dp)) {
                        if (isDownloading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = themeAccent, strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = if (isDownloaded) Icons.Default.CheckCircle else Icons.Default.ArrowCircleDown,
                                contentDescription = if (isDownloaded) "Downloaded" else "Download",
                                tint = if (isDownloaded) themeAccent else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Center Section: Transport Controls & Animated Wavy Slider
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Controls Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    IconButton(onClick = onToggleShuffle, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isShuffle) themeAccent else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onSkipPrev, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(24.dp))
                    }

                    // Play/Pause circular button
                    Button(
                        onClick = onTogglePlayPause,
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    IconButton(onClick = onSkipNext, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    IconButton(onClick = onToggleRepeat, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Repeat,
                            contentDescription = "Repeat",
                            tint = if (isRepeat) themeAccent else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Wavy Scrubber Bar Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(progressMs / 1000L),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = Color.Gray,
                        modifier = Modifier.width(42.dp)
                    )

                    DesktopWavySlider(
                        progressFraction = progressFraction,
                        isPlaying = isPlaying,
                        themeAccent = themeAccent,
                        onSeekFraction = { frac ->
                            onSeek((frac * totalMs).toLong())
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp)
                            .padding(horizontal = 8.dp)
                    )

                    Text(
                        text = formatTime(durationSeconds),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = Color.Gray,
                        modifier = Modifier.width(42.dp)
                    )
                }
            }

            // Right Section: Speed, Sleep Timer, Share, Lyrics, Queue, Equalizer, Volume Slider, and Fullscreen Expand
            Row(
                modifier = Modifier.width(440.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                // Playback Speed Selector Pill
                Box {
                    TextButton(
                        onClick = { showSpeedMenu = true },
                        modifier = Modifier.height(30.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = if (playbackSpeed == 1.0f) "1x" else "${playbackSpeed}x",
                            color = if (playbackSpeed != 1.0f) themeAccent else Color.Gray,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    DropdownMenu(
                        expanded = showSpeedMenu,
                        onDismissRequest = { showSpeedMenu = false },
                        modifier = Modifier.background(Color(0xFF242424))
                    ) {
                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (spd == 1.0f) "1.0x (Normal)" else "${spd}x",
                                        color = if (playbackSpeed == spd) themeAccent else Color.White,
                                        fontWeight = if (playbackSpeed == spd) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    showSpeedMenu = false
                                    onSpeedChange(spd)
                                }
                            )
                        }
                    }
                }

                // Sleep Timer Button
                Box {
                    IconButton(onClick = { showSleepTimerMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = "Sleep Timer",
                            tint = if (sleepTimerLabel != null) themeAccent else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showSleepTimerMenu,
                        onDismissRequest = { showSleepTimerMenu = false },
                        modifier = Modifier.background(Color(0xFF242424))
                    ) {
                        DropdownMenuItem(
                            text = { Text("SLEEP TIMER", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            onClick = {},
                            enabled = false
                        )
                        val timerOpts = listOf(
                            5 to "5 minutes",
                            15 to "15 minutes",
                            30 to "30 minutes",
                            45 to "45 minutes",
                            60 to "1 hour"
                        )
                        timerOpts.forEach { (mins, label) ->
                            DropdownMenuItem(
                                text = { Text(label, color = Color.White) },
                                onClick = {
                                    showSleepTimerMenu = false
                                    onSetSleepTimerMinutes(mins, label)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("End of track", color = Color.White) },
                            onClick = {
                                showSleepTimerMenu = false
                                onSetSleepTimerEndOfTrack()
                            }
                        )
                        if (sleepTimerLabel != null) {
                            HorizontalDivider(color = Color(0xFF383838))
                            DropdownMenuItem(
                                text = {
                                    val rem = sleepTimerRemainingSeconds?.let {
                                        " (${it / 60}:${"%02d".format(it % 60)})"
                                    }.orEmpty()
                                    Text("Turn Off Timer$rem", color = Color(0xFFFF5252))
                                },
                                onClick = {
                                    showSleepTimerMenu = false
                                    onCancelSleepTimer()
                                }
                            )
                        }
                    }
                }

                IconButton(onClick = onShareSong, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share Track",
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(onClick = onOpenEqualizer, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Equalizer,
                        contentDescription = "Equalizer",
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(onClick = onToggleLyrics, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Lyrics,
                        contentDescription = "Lyrics",
                        tint = if (isLyricsOpen) themeAccent else Color.Gray,
                        modifier = Modifier.size(19.dp)
                    )
                }

                IconButton(onClick = onToggleQueue, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Queue",
                        tint = if (isQueueOpen) themeAccent else Color.Gray,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                IconButton(onClick = onOpenDeviceSelector, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Audio Output Device",
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    modifier = Modifier.width(76.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = themeAccent,
                        inactiveTrackColor = Color(0xFF404040)
                    )
                )

                Spacer(modifier = Modifier.width(2.dp))

                IconButton(onClick = onOpenFullscreen, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Fullscreen Player",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopWavySlider(
    progressFraction: Float,
    isPlaying: Boolean,
    themeAccent: Color,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "WavyPhase")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePhaseAnim"
    )

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeekFraction(frac)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val frac = (change.position.x / size.width).coerceIn(0f, 1f)
                    onSeekFraction(frac)
                }
            }
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val playedWidth = width * progressFraction

        // 1. Inactive background line
        drawLine(
            color = Color(0xFF404040),
            start = Offset(playedWidth, centerY),
            end = Offset(width, centerY),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )

        // 2. Active wavy squiggly path
        if (playedWidth > 0f) {
            val wavePath = Path()
            val waveAmplitude = if (isPlaying) 3.5.dp.toPx() else 0f
            val waveLength = 28.dp.toPx()

            var x = 0f
            wavePath.moveTo(0f, centerY)
            while (x <= playedWidth) {
                val y = centerY + waveAmplitude * sin((2 * PI * (x / waveLength) - wavePhase).toFloat())
                wavePath.lineTo(x, y)
                x += 2f
            }

            drawPath(
                path = wavePath,
                color = themeAccent,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )

            // Scrubber knob
            drawCircle(
                color = Color.White,
                radius = 6.dp.toPx(),
                center = Offset(playedWidth, centerY)
            )
        }
    }
}

private fun formatTime(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

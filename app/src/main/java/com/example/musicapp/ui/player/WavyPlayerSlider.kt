package com.example.musicapp.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Android 13/14 inspired wavy/squiggling interactive audio slider.
 * When playing, a sinusoidal ripple flows along the played portion of the track.
 * When paused, the wave flattens smoothly into a calm straight line.
 * Supports tap-to-seek and smooth drag scrubbing with real-time feedback.
 */
@Composable
fun WavyPlayerSlider(
    currentPositionMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color(0xFFB565F5),
    inactiveColor: Color = Color(0x33FFFFFF)
) {
    val density = LocalDensity.current
    var isDragging by remember { mutableStateOf(false) }
    var dragProgressFraction by remember { mutableFloatStateOf(0f) }

    val actualProgressFraction = if (totalDurationMs > 0) {
        (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val displayProgressFraction = if (isDragging) dragProgressFraction else actualProgressFraction
    val displayPositionMs = (displayProgressFraction * totalDurationMs).toLong().coerceIn(0L, totalDurationMs.coerceAtLeast(0L))

    // Continuous wave animation when playing
    val infiniteTransition = rememberInfiniteTransition(label = "wavySeekbarTransition")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI.toFloat()),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    // Smoothly flatten the wave when playback pauses
    val targetAmplitude = if (isPlaying) with(density) { 4.5.dp.toPx() } else 0f
    val animatedAmplitude by animateFloatAsState(
        targetValue = targetAmplitude,
        animationSpec = tween(durationMillis = 350),
        label = "animatedWaveAmplitude"
    )

    val thumbRadius = with(density) { (if (isDragging) 8.dp else 6.dp).toPx() }
    val glowRadius = with(density) { 14.dp.toPx() }
    val strokeWidth = with(density) { 3.5.dp.toPx() }
    val waveLengthPx = with(density) { 36.dp.toPx() }
    val taperDistancePx = with(density) { 24.dp.toPx() }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .pointerInput(totalDurationMs) {
                    detectTapGestures { offset ->
                        if (size.width > 0 && totalDurationMs > 0) {
                            val tappedFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            onSeek((tappedFraction * totalDurationMs).toLong())
                        }
                    }
                }
                .pointerInput(totalDurationMs) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (size.width > 0) {
                                isDragging = true
                                dragProgressFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            if (size.width > 0) {
                                dragProgressFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            }
                        },
                        onDragEnd = {
                            if (totalDurationMs > 0) {
                                onSeek((dragProgressFraction * totalDurationMs).toLong())
                            }
                            isDragging = false
                        },
                        onDragCancel = {
                            isDragging = false
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(32.dp)) {
                val width = size.width
                val centerY = size.height / 2f
                val progressX = (width * displayProgressFraction).coerceIn(0f, width)

                // 1. Draw unplayed portion (straight sleek line from progressX to width)
                if (progressX < width) {
                    drawLine(
                        color = inactiveColor,
                        start = Offset(progressX, centerY),
                        end = Offset(width, centerY),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }

                // 2. Draw played portion with sinusoidal wave
                if (progressX > 0f) {
                    if (animatedAmplitude > 0.1f) {
                        val path = Path()
                        path.moveTo(0f, centerY)

                        var currentX = 0f
                        val step = 2.5f // Pixel step for smooth rendering
                        while (currentX <= progressX) {
                            // Taper amplitude down near the thumb so it connects smoothly to center
                            val distToThumb = progressX - currentX
                            val taper = (distToThumb / taperDistancePx).coerceIn(0f, 1f)
                            val amp = animatedAmplitude * taper
                            val angle = (currentX / waveLengthPx) * 2f * PI.toFloat() - wavePhase
                            val currentY = centerY + amp * sin(angle)
                            path.lineTo(currentX, currentY)
                            currentX += step
                        }
                        path.lineTo(progressX, centerY)

                        drawPath(
                            path = path,
                            color = activeColor,
                            style = Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    } else {
                        // Flat line when paused or near zero amplitude
                        drawLine(
                            color = activeColor,
                            start = Offset(0f, centerY),
                            end = Offset(progressX, centerY),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // 3. Draw Scrubber Thumb with aesthetic outer glow
                val thumbCenter = Offset(progressX, centerY)
                if (isDragging) {
                    drawCircle(
                        color = activeColor.copy(alpha = 0.28f),
                        radius = glowRadius,
                        center = thumbCenter
                    )
                }
                drawCircle(
                    color = Color.White,
                    radius = thumbRadius,
                    center = thumbCenter
                )
                drawCircle(
                    color = activeColor,
                    radius = thumbRadius - with(density) { 2.dp.toPx() },
                    center = thumbCenter
                )
            }
        }

        // Timestamp row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(displayPositionMs),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFA0A0A0)
            )
            Text(
                text = formatDuration(totalDurationMs),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFA0A0A0)
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

package com.example.musicapp.desktop.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DesktopLaunchSplash(modifier: Modifier = Modifier) {
    val motion = rememberInfiniteTransition(label = "rivaraLaunchMotion")
    val logoScale by motion.animateFloat(
        initialValue = 0.94f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), repeatMode = RepeatMode.Reverse),
        label = "rivaraLogoPulse"
    )

    Box(
        modifier = modifier.background(
            Brush.linearGradient(listOf(Color(0xFF111B5B), Color(0xFF171038), Color(0xFF31104A)))
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource("assets/rivara_launcher_icon.png"),
                contentDescription = "Rivara",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(132.dp)
                    .scale(logoScale)
                    .clip(RoundedCornerShape(30.dp))
            )
            Spacer(Modifier.height(26.dp))
            Text(
                text = "RIVARA",
                color = Color.White,
                fontSize = 24.sp,
                letterSpacing = 7.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(18.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    Color(0xFF55C7FF),
                    Color(0xFF9A62F6),
                    Color(0xFFE34DAC),
                    Color(0xFFFF806C),
                    Color(0xFF56D8BF)
                ).forEachIndexed { index, color ->
                    SplashBeatBar(index = index, color = color, motion = motion)
                }
            }
        }
    }
}

@Composable
private fun SplashBeatBar(
    index: Int,
    color: Color,
    motion: InfiniteTransition
) {
    val height by motion.animateFloat(
        initialValue = 7f,
        targetValue = 19f + (index % 3) * 5f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 460 + index * 55, delayMillis = index * 70),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splashBeat$index"
    )
    Box(
        Modifier
            .width(4.dp)
            .height(height.dp)
            .clip(CircleShape)
            .background(color)
    )
}

package com.example.musicapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/** Artwork that never leaves a blank tile when a remote cover is missing or unavailable. */
@Composable
fun TrackArtwork(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    var failed by remember(model) { mutableStateOf(model == null || model.toString().isBlank()) }
    Box(
        modifier = modifier.background(
            Brush.linearGradient(listOf(Color(0xFF173B55), Color(0xFF35204F), Color(0xFF151A2A)))
        ),
        contentAlignment = Alignment.Center
    ) {
        if (failed) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = contentDescription,
                tint = Color(0xFF7DEBD2),
                modifier = Modifier.fillMaxSize(0.42f)
            )
        } else {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                onError = { failed = true }
            )
        }
    }
}

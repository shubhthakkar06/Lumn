package com.example.musicapp.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

/** One search result row: a show, playlist, album or artist. */
@Composable
fun DesktopResultRow(
    title: String,
    subtitle: String,
    imageUrl: String,
    accent: Color,
    circle: Boolean = false,
    following: Boolean? = null,
    onToggleFollow: () -> Unit = {},
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).background(Color(0xFF131925)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(if (circle) CircleShape else RoundedCornerShape(8.dp)).background(Color(0xFF1E2633))
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Color(0xFF94A3B8), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (following != null) {
            OutlinedButton(onClick = onToggleFollow, border = androidx.compose.foundation.BorderStroke(1.dp, if (following) accent else Color.Gray)) {
                Text(if (following) "Following" else "Follow", color = if (following) accent else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

package com.example.musicapp.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import coil3.compose.AsyncImage
import com.example.musicapp.data.auth.UserProfile

/**
 * Inside of a round avatar: the profile photo when there is one, otherwise the initial.
 * The caller supplies the circle (size, clip, background, click).
 */
@Composable
fun AvatarContent(user: UserProfile, fontSize: TextUnit, letterColor: Color = Color.Black) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(user.avatarLetter, color = letterColor, fontWeight = FontWeight.ExtraBold, fontSize = fontSize)
        if (user.photoUrl.isNotBlank()) {
            AsyncImage(
                model = if (user.photoUrl.startsWith("/")) java.io.File(user.photoUrl) else user.photoUrl,
                contentDescription = "Profile photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

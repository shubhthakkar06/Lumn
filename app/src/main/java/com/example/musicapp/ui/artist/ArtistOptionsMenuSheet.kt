package com.example.musicapp.ui.artist

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.musicapp.data.artist.ArtistFollowManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistOptionsMenuSheet(
    artistName: String,
    imageUrl: String,
    onDismiss: () -> Unit,
    onFollowToggled: () -> Unit = {}
) {
    val context = LocalContext.current
    val followManager = remember { ArtistFollowManager.getInstance(context) }
    var isFollowing by remember(artistName) { mutableStateOf(followManager.isFollowing(artistName)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF282828),
        contentColor = Color.White,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF555555))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Artist Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = imageUrl.ifEmpty { "https://i.scdn.co/image/ab6761610000e5eb989ed050d1036b72b0365774" },
                    contentDescription = artistName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = artistName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Artist",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFA0A0A0)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFF383838), thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // Action Items
            ArtistOptionItem(
                icon = if (isFollowing) Icons.Default.Check else Icons.Default.PersonAdd,
                title = if (isFollowing) "Following" else "Follow",
                onClick = {
                    val newState = followManager.toggleFollow(artistName)
                    isFollowing = newState
                    onFollowToggled()
                    Toast.makeText(
                        context,
                        if (newState) "Followed $artistName" else "Unfollowed $artistName",
                        Toast.LENGTH_SHORT
                    ).show()
                    onDismiss()
                }
            )

            ArtistOptionItem(
                icon = Icons.Default.Share,
                title = "Share",
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, artistName)
                        val safeSlug = artistName.lowercase().replace(" ", "-")
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Discover $artistName on Lumn"
                        )
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share artist via"))
                    onDismiss()
                }
            )

            ArtistOptionItem(
                icon = Icons.Default.Block,
                title = "Don't play this artist",
                onClick = {
                    Toast.makeText(
                        context,
                        "We won't play songs by $artistName again",
                        Toast.LENGTH_SHORT
                    ).show()
                    onDismiss()
                }
            )

            ArtistOptionItem(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                title = "Go to artist radio",
                onClick = {
                    Toast.makeText(context, "Opening $artistName Radio", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            )

            ArtistOptionItem(
                icon = Icons.Default.ReportProblem,
                title = "Report",
                onClick = {
                    Toast.makeText(context, "Feedback reported for $artistName", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun ArtistOptionItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = Color.White
        )
    }
}

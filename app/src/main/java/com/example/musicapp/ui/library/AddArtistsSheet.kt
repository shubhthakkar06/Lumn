package com.example.musicapp.ui.library

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.musicapp.data.artist.ArtistFollowManager

data class SuggestedArtist(
    val name: String,
    val imageUrl: String,
    val monthlyListeners: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddArtistsSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val followManager = remember { ArtistFollowManager.getInstance(context) }
    val followedArtists by followManager.followedArtists.collectAsStateWithLifecycle()

    val suggestedArtists = remember {
        listOf(
            SuggestedArtist("Coldplay", "https://i.ytimg.com/vi/dvgZkm1xWPE/hq720.jpg", "94.9M monthly listeners"),
            SuggestedArtist("Arijit Singh", "https://i.ytimg.com/vi/Oextk-If8HQ/hqdefault.jpg", "45.8M monthly listeners"),
            SuggestedArtist("Vishal Mishra", "https://i.ytimg.com/vi/kuhh3JVms4w/hqdefault.jpg", "24.1M monthly listeners"),
            SuggestedArtist("Darshan Raval", "https://i.ytimg.com/vi/V7LwfY5U5WI/hqdefault.jpg", "18.4M monthly listeners"),
            SuggestedArtist("Billie Eilish", "https://i.ytimg.com/vi/viimfQi_pUw/hqdefault.jpg", "102.5M monthly listeners"),
            SuggestedArtist("Maroon 5", "https://i.ytimg.com/vi/09R8_2nJtjg/hqdefault.jpg", "62.3M monthly listeners"),
            SuggestedArtist("Jordana Bryant", "https://is1-ssl.mzstatic.com/image/thumb/Music122/v4/9b/ab/3b/9bab3bf7-d876-10ef-7e41-0a5efee154c1/196925002585.jpg/600x600bb.jpg", "890K monthly listeners")
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0E141E),
        contentColor = Color.White,
        dragHandle = null,
        modifier = Modifier.fillMaxHeight(0.85f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Add artists",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2A2A2A))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            Text(
                text = "Choose artists you like to personalize your recommendations.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFA0A0A0),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            HorizontalDivider(color = Color(0xFF2E2E2E), thickness = 1.dp, modifier = Modifier.padding(top = 12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(suggestedArtists, key = { it.name }) { artist ->
                    val isFollowing = followManager.isFollowing(artist.name)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = artist.imageUrl,
                            contentDescription = artist.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = artist.name,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = artist.monthlyListeners,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFA0A0A0)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .border(
                                    1.dp,
                                    if (isFollowing) Color(0xFF888888) else Color.White,
                                    CircleShape
                                )
                                .clickable {
                                    val nowFollowing = followManager.toggleFollow(artist.name)
                                    Toast.makeText(
                                        context,
                                        if (nowFollowing) "Added ${artist.name} to Your Library" else "Removed ${artist.name}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (isFollowing) "Following" else "Follow",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}

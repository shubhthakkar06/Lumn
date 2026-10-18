package com.example.musicapp.ui.auth

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.musicapp.data.auth.UserManager

data class MusicInterestItem(
    val id: String,
    val name: String,
    val isArtist: Boolean,
    val imageUrl: String,
    val accentColor: Color
)

@Composable
fun MusicInterestsScreen(
    onInterestsCompleted: () -> Unit
) {
    val context = LocalContext.current
    val userManager = remember { UserManager.getInstance(context) }

    val allInterests = remember {
        listOf(
            // Artists
            MusicInterestItem("arijit", "Arijit Singh", true, "https://c.saavncdn.com/artists/Arijit_Singh_004_20241118063717_500x500.jpg", Color(0xFFE91E63)),
            MusicInterestItem("coldplay", "Coldplay", true, "https://c.saavncdn.com/artists/Coldplay_002_20241003070447_500x500.jpg", Color(0xFF3F51B5)),
            MusicInterestItem("pritam", "Pritam", true, "https://c.saavncdn.com/artists/Pritam_Chakraborty-20170711073326_500x500.jpg", Color(0xFFFF9800)),
            MusicInterestItem("alan_walker", "Alan Walker", true, "https://i.ytimg.com/vi/60ItHLz5WEA/hqdefault.jpg", Color(0xFF00BCD4)),
            MusicInterestItem("diljit", "Diljit Dosanjh", true, "https://c.saavncdn.com/artists/Diljit_Dosanjh_004_20231024072818_500x500.jpg", Color(0xFFFF5722)),
            MusicInterestItem("taylor", "Taylor Swift", true, "https://c.saavncdn.com/artists/Taylor_Swift_003_20200226074119_500x500.jpg", Color(0xFF9C27B0)),
            MusicInterestItem("weeknd", "The Weeknd", true, "https://i.ytimg.com/vi/4NRXx6U8ABQ/hqdefault.jpg", Color(0xFFF44336)),
            MusicInterestItem("shreya", "Shreya Ghoshal", true, "https://i.ytimg.com/vi/inEu2qQuGZ8/hqdefault.jpg", Color(0xFF4CAF50)),
            // Genres
            MusicInterestItem("bollywood", "Bollywood", false, "https://i.ytimg.com/vi/BddP6PYo2gs/hqdefault.jpg", Color(0xFFE040FB)),
            MusicInterestItem("pop", "Pop", false, "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&auto=format&fit=crop", Color(0xFF2196F3)),
            MusicInterestItem("punjabi", "Punjabi", false, "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=600&auto=format&fit=crop", Color(0xFFFFC107)),
            MusicInterestItem("indie", "Indie India", false, "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=600&auto=format&fit=crop", Color(0xFF009688)),
            MusicInterestItem("edm", "EDM & Dance", false, "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&auto=format&fit=crop", Color(0xFF673AB7)),
            MusicInterestItem("lofi", "Lo-Fi Beats", false, "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=600&auto=format&fit=crop", Color(0xFF607D8B)),
            MusicInterestItem("rock", "Rock", false, "https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?w=600&auto=format&fit=crop", Color(0xFFD32F2F)),
            MusicInterestItem("hiphop", "Hip-Hop", false, "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600&auto=format&fit=crop", Color(0xFFFF6D00))
        )
    }

    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredInterests = remember(searchQuery, allInterests) {
        if (searchQuery.isBlank()) allInterests
        else allInterests.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
    }

    val canProceed = selectedIds.size >= 3

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E14))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "What music do you like?",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Choose 3 or more artists or genres you want to hear.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFA0A0A0)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Search Filter
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search artists or genres", color = Color.Gray, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF242424),
                    unfocusedContainerColor = Color(0xFF242424),
                    focusedBorderColor = Color(0xFF1ED760),
                    unfocusedBorderColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Grid of selections
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(filteredInterests, key = { it.id }) { item ->
                    val isSelected = selectedIds.contains(item.id)
                    val borderColor by animateColorAsState(
                        targetValue = if (isSelected) Color(0xFF1ED760) else Color.Transparent,
                        label = "interestBorder"
                    )
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.05f else 1.0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "interestScale"
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .scale(scale)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                selectedIds = if (isSelected) {
                                    selectedIds - item.id
                                } else {
                                    selectedIds + item.id
                                }
                            }
                            .padding(4.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(90.dp)
                                .clip(if (item.isArtist) CircleShape else RoundedCornerShape(12.dp))
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = borderColor,
                                    shape = if (item.isArtist) CircleShape else RoundedCornerShape(12.dp)
                                )
                                .background(Color(0xFF282828))
                        ) {
                            AsyncImage(
                                model = item.imageUrl,
                                contentDescription = item.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0x66000000)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF00F59B)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isSelected) Color(0xFF00F59B) else Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }
            }
        }

        // Floating Bottom Continue Action
        Surface(
            color = Color(0xFF0A0E14).copy(alpha = 0.95f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Button(
                onClick = {
                    if (canProceed) {
                        val chosenItems = allInterests.filter { selectedIds.contains(it.id) }
                        val genres = chosenItems.filter { !it.isArtist }.map { it.name }
                        val artists = chosenItems.filter { it.isArtist }.map { it.name }
                        userManager.updateInterests(genres, artists)
                        onInterestsCompleted()
                    }
                },
                enabled = canProceed,
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00F59B),
                    contentColor = Color(0xFF0A0E14),
                    disabledContainerColor = Color(0xFF1E2638),
                    disabledContentColor = Color.Gray
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = if (canProceed) "Done" else "Choose ${3 - selectedIds.size} more",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

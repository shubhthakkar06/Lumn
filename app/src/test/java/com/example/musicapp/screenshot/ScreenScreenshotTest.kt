package com.example.musicapp.screenshot

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.ui.main.FloatingCapsulePlayer
import com.example.musicapp.ui.main.MainScreenUiState
import com.example.musicapp.ui.player.FullScreenPlayerSheet
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel6, sdk = [34])
class ScreenScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val sampleSong = SongInfo(
        videoId = "sample_1",
        title = "Midnight Horizon",
        artist = "Obsidian Waves",
        albumArtUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=500",
        audioStreamUrl = "http://example.com/audio.mp3"
    )

    private val sampleSong2 = SongInfo(
        videoId = "sample_2",
        title = "Electric Rain",
        artist = "Synthetic Souls",
        albumArtUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=500",
        audioStreamUrl = "http://example.com/audio2.mp3"
    )

    // ==========================================
    // 1. MINI PLAYER (FloatingCapsulePlayer)
    // ==========================================

    @Test
    fun miniPlayer_playing() {
        composeTestRule.setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0B0E14)),
                contentAlignment = Alignment.BottomCenter
            ) {
                FloatingCapsulePlayer(
                    song = sampleSong,
                    isPlaying = true,
                    onClick = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/mini_player_playing.png")
    }

    @Test
    fun miniPlayer_paused() {
        composeTestRule.setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0B0E14)),
                contentAlignment = Alignment.BottomCenter
            ) {
                FloatingCapsulePlayer(
                    song = sampleSong,
                    isPlaying = false,
                    onClick = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/mini_player_paused.png")
    }

    @Test
    fun miniPlayer_loading() {
        composeTestRule.setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0B0E14)),
                contentAlignment = Alignment.BottomCenter
            ) {
                FloatingCapsulePlayer(
                    song = sampleSong.copy(title = "Buffering Stream..."),
                    isPlaying = false,
                    onClick = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/mini_player_loading.png")
    }

    // ==========================================
    // 2. SEARCH SCREEN STATES
    // ==========================================

    @Test
    fun searchScreen_emptyBrowse() {
        composeTestRule.setContent {
            MockSearchScreenLayout(
                query = "",
                uiState = MainScreenUiState.Idle
            )
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/search_empty_browse.png")
    }

    @Test
    fun searchScreen_loading() {
        composeTestRule.setContent {
            MockSearchScreenLayout(
                query = "The Weeknd",
                uiState = MainScreenUiState.Loading
            )
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/search_loading.png")
    }

    @Test
    fun searchScreen_populated() {
        composeTestRule.setContent {
            MockSearchScreenLayout(
                query = "The Weeknd",
                uiState = MainScreenUiState.Success(listOf(sampleSong, sampleSong2))
            )
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/search_populated.png")
    }

    @Test
    fun searchScreen_error() {
        composeTestRule.setContent {
            MockSearchScreenLayout(
                query = "Unknown Track",
                uiState = MainScreenUiState.Error("Network offline. Provider unreachable.")
            )
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/search_error.png")
    }

    // ==========================================
    // 3. HOME SCREEN STATES
    // ==========================================

    @Test
    fun homeScreen_populated() {
        composeTestRule.setContent {
            MockHomeScreenLayout(isLoading = false, isEmpty = false)
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/home_populated.png")
    }

    @Test
    fun homeScreen_loading() {
        composeTestRule.setContent {
            MockHomeScreenLayout(isLoading = true, isEmpty = false)
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/home_loading.png")
    }

    @Test
    fun homeScreen_empty() {
        composeTestRule.setContent {
            MockHomeScreenLayout(isLoading = false, isEmpty = true)
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/home_empty.png")
    }

    // ==========================================
    // 4. ARTIST SANCTUARY SCREEN STATES
    // ==========================================

    @Test
    fun artistScreen_populated() {
        composeTestRule.setContent {
            MockArtistScreenLayout(isLoading = false, isError = false)
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/artist_populated.png")
    }

    @Test
    fun artistScreen_loading() {
        composeTestRule.setContent {
            MockArtistScreenLayout(isLoading = true, isError = false)
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/artist_loading.png")
    }

    @Test
    fun artistScreen_error() {
        composeTestRule.setContent {
            MockArtistScreenLayout(isLoading = false, isError = true)
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/artist_error.png")
    }

    // ==========================================
    // 5. VAULT / LIBRARY SCREEN STATES
    // ==========================================

    @Test
    fun libraryVault_empty() {
        composeTestRule.setContent {
            MockVaultScreenLayout(isEmpty = true)
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/library_vault_empty.png")
    }

    @Test
    fun libraryVault_populated() {
        composeTestRule.setContent {
            MockVaultScreenLayout(isEmpty = false)
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/library_vault_populated.png")
    }

    // ==========================================
    // 6. SETTINGS SCREEN
    // ==========================================

    @Test
    fun settingsScreen_populated() {
        composeTestRule.setContent {
            MockSettingsScreenLayout()
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/settings_populated.png")
    }

    // ==========================================
    // COMPOSABLE MOCK LAYOUT HARNESSES
    // ==========================================

    @Composable
    private fun MockSearchScreenLayout(query: String, uiState: MainScreenUiState) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0E14))
                .padding(16.dp)
        ) {
            Spacer(modifier = Modifier.height(28.dp))
            // Clean Search Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF141922))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF909AAB), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (query.isBlank()) "What do you want to listen to?" else query,
                        color = if (query.isBlank()) Color(0xFF5E687A) else Color(0xFFF0F3F8),
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (uiState) {
                is MainScreenUiState.Idle -> {
                    Text("Browse All", color = Color(0xFFF0F3F8), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(80.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E2532))
                                .padding(12.dp)
                        ) {
                            Text("Bollywood", color = Color(0xFFF0F3F8), fontWeight = FontWeight.SemiBold)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(80.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E2532))
                                .padding(12.dp)
                        ) {
                            Text("International", color = Color(0xFFF0F3F8), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                is MainScreenUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF00F59B), modifier = Modifier.size(36.dp))
                    }
                }
                is MainScreenUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(uiState.message, color = Color(0xFFFF5252), fontSize = 14.sp)
                    }
                }
                is MainScreenUiState.Success -> {
                    uiState.songs.forEach { song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E2532))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(song.title, color = Color(0xFFF0F3F8), fontWeight = FontWeight.SemiBold)
                                Text(song.artist, color = Color(0xFF909AAB), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun MockHomeScreenLayout(isLoading: Boolean, isEmpty: Boolean) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0E14))
                .padding(16.dp)
        ) {
            Spacer(modifier = Modifier.height(28.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Cyber-Obsidian", color = Color(0xFFF0F3F8), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00F59B)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("S", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF00F59B))
                }
            } else if (isEmpty) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Your feed is ready. Search to explore music.", color = Color(0xFF909AAB))
                }
            } else {
                Text("Trending Now", color = Color(0xFFF0F3F8), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(2) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(160.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF141922))
                                .padding(12.dp)
                        ) {
                            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                                Text("Synth Wave", color = Color(0xFFF0F3F8), fontWeight = FontWeight.Bold)
                                Text("Popular Mix", color = Color(0xFF909AAB), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun MockArtistScreenLayout(isLoading: Boolean, isError: Boolean) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0E14))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(Color(0xFF141922))
            ) {
                IconButton(onClick = {}, modifier = Modifier.padding(top = 28.dp, start = 8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                    Text("Arijit Singh", color = Color(0xFFF0F3F8), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("94.9M monthly listeners", color = Color(0xFF909AAB), fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF00F59B))
                }
            } else if (isError) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Could not load artist discography.", color = Color(0xFFFF5252))
                }
            } else {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text("Top Tracks", color = Color(0xFFF0F3F8), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    listOf("Kesariya", "Tum Hi Ho", "Channa Mereya").forEachIndexed { idx, track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${idx + 1}", color = Color(0xFF909AAB), modifier = Modifier.width(24.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(track, color = Color(0xFFF0F3F8), fontWeight = FontWeight.SemiBold)
                                Text("1.2B plays", color = Color(0xFF5E687A), fontSize = 12.sp)
                            }
                            Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color(0xFF909AAB))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun MockVaultScreenLayout(isEmpty: Boolean) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0E14))
                .padding(16.dp)
        ) {
            Spacer(modifier = Modifier.height(28.dp))
            Text("The Vault", color = Color(0xFFF0F3F8), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            if (isEmpty) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Your library is empty. Star songs to save them.", color = Color(0xFF909AAB))
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF141922))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF00F59B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.Black)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text("Liked Songs", color = Color(0xFFF0F3F8), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("24 tracks • Offline ready", color = Color(0xFF909AAB), fontSize = 13.sp)
                    }
                }
            }
        }
    }

    @Composable
    private fun MockSettingsScreenLayout() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0E14))
                .padding(16.dp)
        ) {
            Spacer(modifier = Modifier.height(28.dp))
            Text("Settings", color = Color(0xFFF0F3F8), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(20.dp))

            listOf(
                "Streaming Audio Quality" to "Master (320 kbps)",
                "Hardware Equalizer" to "Electronic / Punchy Bass",
                "Crossfade Duration" to "4 seconds",
                "Storage Location" to "Internal Cache (84 MB used)"
            ).forEach { (title, subtitle) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF141922))
                        .padding(14.dp)
                ) {
                    Text(title, color = Color(0xFFF0F3F8), fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(subtitle, color = Color(0xFF00F59B), fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

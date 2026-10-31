package com.example.musicapp.data.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.musicapp.data.artist.ArtistFollowManager
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.playlist.PlaylistManager
import com.example.musicapp.theme.AppThemeHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountIsolationTest {

    private lateinit var context: Context
    private lateinit var userManager: UserManager
    private lateinit var playlistManager: PlaylistManager
    private lateinit var followManager: ArtistFollowManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        userManager = UserManager.getInstance(context)
        playlistManager = PlaylistManager.getInstance(context)
        followManager = ArtistFollowManager.getInstance(context)
    }

    @Test
    fun testPlaylistAccountIsolation() {
        // User A logs in with Google
        val alice = userManager.loginWithGoogle("alice_uid_123", "alice@test.com", "Alice")
        assertEquals(alice.id, userManager.currentUser.value.id)

        val alicePl = playlistManager.createPlaylist(
            name = "Alice Workout Vibes",
            creator = "Alice",
            songs = listOf(
                SongInfo(videoId = "song_1", title = "Levitating", artist = "Dua Lipa", albumArtUrl = "")
            )
        )
        assertNotNull(alicePl)
        assertTrue(playlistManager.playlists.value.any { it.name == "Alice Workout Vibes" })

        // Like song for Alice
        val songToLike = SongInfo(videoId = "liked_1", title = "Yellow", artist = "Coldplay", albumArtUrl = "")
        playlistManager.toggleLike(songToLike)
        assertTrue(playlistManager.isLiked("liked_1"))

        // Switch to User B (Bob logs in)
        val bob = userManager.loginWithGoogle("bob_uid_456", "bob@test.com", "Bob")
        playlistManager.switchUser(bob.id)
        assertEquals(bob.id, userManager.currentUser.value.id)

        assertFalse("Bob should not see Alice's playlist", playlistManager.playlists.value.any { it.name == "Alice Workout Vibes" })
        assertFalse("Bob should not have Alice's liked song", playlistManager.isLiked("liked_1"))

        // Create Bob's playlist
        val bobPl = playlistManager.createPlaylist(
            name = "Bob Chill Beats",
            creator = "Bob",
            songs = listOf(
                SongInfo(videoId = "song_2", title = "Starboy", artist = "The Weeknd", albumArtUrl = "")
            )
        )
        assertNotNull(bobPl)
        assertTrue(playlistManager.playlists.value.any { it.name == "Bob Chill Beats" })

        // Switch back to Alice
        userManager.switchUser(alice.id)
        playlistManager.switchUser(alice.id)
        assertEquals(alice.id, userManager.currentUser.value.id)

        assertTrue("Alice should retain her playlist", playlistManager.playlists.value.any { it.name == "Alice Workout Vibes" })
        assertFalse("Alice should not see Bob's playlist", playlistManager.playlists.value.any { it.name == "Bob Chill Beats" })
        assertTrue("Alice's liked songs must persist", playlistManager.isLiked("liked_1"))
    }

    @Test
    fun testArtistFollowAccountIsolation() {
        // Charlie logs in
        val charlie = userManager.loginWithGoogle("charlie_uid_789", "charlie@test.com", "Charlie")
        followManager.follow("Coldplay")
        assertTrue(followManager.isFollowing("Coldplay"))

        // Diana logs in
        val diana = userManager.loginWithGoogle("diana_uid_101", "diana@test.com", "Diana")
        assertFalse("Diana should not automatically follow Charlie's artists", followManager.isFollowing("Coldplay"))

        // Diana follows Taylor Swift
        followManager.follow("Taylor Swift")
        assertTrue(followManager.isFollowing("Taylor Swift"))

        // Switch back to Charlie
        userManager.switchUser(charlie.id)
        assertTrue("Charlie should still follow Coldplay", followManager.isFollowing("Coldplay"))
        assertFalse("Charlie should not follow Diana's artists", followManager.isFollowing("Taylor Swift"))
    }

    @Test
    fun testThemePresetsIntegrity() {
        // Check that theme presets exist and have valid gradients and accents
        assertTrue(AppThemeHelper.presets.isNotEmpty())
        AppThemeHelper.presets.forEach { preset ->
            assertNotNull(preset.id)
            assertNotNull(preset.name)
            assertTrue(preset.gradientColors.size >= 2)
            val accent = AppThemeHelper.getThemeAccent(preset.id)
            assertNotNull(accent)
            val gradient = AppThemeHelper.getThemeGradient(preset.id)
            assertEquals(preset.gradientColors.size, gradient.size)
        }

        // Test Dynamic Time
        val dynamicGradient = AppThemeHelper.getThemeGradient("Dynamic Time")
        assertTrue(dynamicGradient.isNotEmpty())
        val dynamicAccent = AppThemeHelper.getThemeAccent("Dynamic Time")
        assertNotNull(dynamicAccent)
    }
}

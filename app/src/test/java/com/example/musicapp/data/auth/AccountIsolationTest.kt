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
        UserManager.resetForTesting(context)
        PlaylistManager.resetForTesting(context)
        ArtistFollowManager.resetForTesting(context)
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
    fun testMultipleAccountsSignOutSwitchesToRemainingAccount() {
        // Alice logs in
        val alice = userManager.loginWithGoogle("alice_multi_1", "alice_multi@test.com", "Alice Multi")
        // Bob logs in
        val bob = userManager.loginWithGoogle("bob_multi_2", "bob_multi@test.com", "Bob Multi")

        assertEquals(2, userManager.allUsers.value.size)
        assertEquals(bob.id, userManager.currentUser.value.id)
        assertTrue(userManager.isLoggedIn.value)

        // Bob creates a playlist
        val bobPl = playlistManager.createPlaylist("Bob Secret Tracks", creator = "Bob Multi")
        assertNotNull(bobPl)

        // Bob logs out while Alice is also signed in
        userManager.logout()

        // Bob must be removed from allUsers
        assertEquals(1, userManager.allUsers.value.size)
        assertFalse("Bob must not be in allUsers", userManager.allUsers.value.any { it.id == bob.id })
        assertTrue("Alice must remain in allUsers", userManager.allUsers.value.any { it.id == alice.id })

        // The app must stay logged in and seamlessly switch to Alice
        assertTrue("App must stay logged in when another account remains", userManager.isLoggedIn.value)
        assertEquals("Active user must switch to Alice", alice.id, userManager.currentUser.value.id)
        assertFalse("Alice must not see Bob's playlist", playlistManager.playlists.value.any { it.name == "Bob Secret Tracks" })
    }

    @Test
    fun testSignOutLastAccountAndGuestModeZeroGhostAccounts() {
        // Single user logs in
        val singleUser = userManager.loginWithGoogle("single_user_1", "single@test.com", "Single User")
        assertEquals(singleUser.id, userManager.currentUser.value.id)

        // Log out the only account
        userManager.logout()

        // App must be in logged out state
        assertFalse("App must not be logged in", userManager.isLoggedIn.value)
        assertTrue("Active user must be guest", userManager.currentUser.value.isGuest)
        assertTrue("allUsers must be empty after logging out only account", userManager.allUsers.value.isEmpty())

        // User enters Guest mode
        val guest = userManager.loginAsGuest()
        assertTrue(userManager.isLoggedIn.value)
        assertTrue(guest.isGuest)
        // Verify allUsers has 0 ghost accounts!
        assertTrue("Guest mode must show zero accounts in allUsers", userManager.allUsers.value.isEmpty())
        assertTrue("Guest mode library must have empty playlists", playlistManager.playlists.value.isEmpty())
    }

    @Test
    fun testSignOutSpecificBackgroundAccount() {
        val user1 = userManager.loginWithGoogle("bg_user_1", "bg1@test.com", "Background 1")
        val user2 = userManager.loginWithGoogle("bg_user_2", "bg2@test.com", "Background 2")

        assertEquals(user2.id, userManager.currentUser.value.id)
        assertEquals(2, userManager.allUsers.value.size)

        // Sign out user 1 (non-active) from account manager
        userManager.logout(user1.id)

        assertEquals(1, userManager.allUsers.value.size)
        assertFalse("User 1 should be removed", userManager.allUsers.value.any { it.id == user1.id })
        assertEquals("Active user 2 must remain unchanged", user2.id, userManager.currentUser.value.id)
        assertTrue(userManager.isLoggedIn.value)
    }

    @Test
    fun testLegacyDuplicatePlaylistSanitization() {
        val alice = userManager.loginWithGoogle("alice_primary", "alice@test.com", "Alice")
        val bob = userManager.loginWithGoogle("bob_secondary", "bob@test.com", "Bob")

        // Alice creates a playlist
        userManager.switchUser(alice.id)
        playlistManager.switchUser(alice.id)
        val alicePl = playlistManager.createPlaylist("Alice Personal Hits", creator = "Alice")
        assertNotNull(alicePl)

        // Simulate an accidental duplicate in Bob's file on disk (from legacy bug)
        val bobFile = java.io.File(context.filesDir, "user_playlists_${bob.id}.json")
        val duplicateJson = kotlinx.serialization.json.Json.encodeToString(
            listOf(
                com.example.musicapp.data.playlist.UserPlaylist(
                    id = "pl_alice_legacy",
                    name = "Alice Personal Hits",
                    creator = "Alice",
                    userId = "" // Legacy unassigned
                ),
                com.example.musicapp.data.playlist.UserPlaylist(
                    id = "pl_bob_legit",
                    name = "Bob Own Beats",
                    creator = "Bob",
                    userId = bob.id
                )
            )
        )
        bobFile.writeText(duplicateJson)

        // Switch to Bob and load data
        userManager.switchUser(bob.id)
        playlistManager.switchUser(bob.id)

        // Bob's playlists should have sanitized out Alice's legacy playlist while keeping Bob's own playlist
        assertFalse("Bob should not have Alice's legacy playlist", playlistManager.playlists.value.any { it.name == "Alice Personal Hits" })
        assertTrue("Bob should keep his own playlist", playlistManager.playlists.value.any { it.name == "Bob Own Beats" })
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

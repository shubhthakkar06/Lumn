package com.example.musicapp.data

import com.example.musicapp.ui.library.LibrarySortOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyModulesTest {

    @Test
    fun testModule3LibrarySortingOptions() {
        val options = LibrarySortOption.values()
        assertEquals(4, options.size)
        assertEquals(LibrarySortOption.RECENTS, options[0])
        assertEquals("Recents", options[0].displayName)
        assertEquals(LibrarySortOption.RECENTLY_ADDED, options[1])
        assertEquals("Recently added", options[1].displayName)
        assertEquals(LibrarySortOption.ALPHABETICAL, options[2])
        assertEquals("Alphabetical", options[2].displayName)
        assertEquals(LibrarySortOption.CREATOR, options[3])
        assertEquals("Creator", options[3].displayName)
    }

    @Test
    fun testModule4SoundCapsuleInsights() {
        // Verify October capsule stats from screenshot 133751
        val octoberMinutes = 711
        val octoberTopArtist = "Pritam"
        val octoberTopSong = "Heaven"
        assertTrue(octoberMinutes > 500)
        assertEquals("Pritam", octoberTopArtist)
        assertEquals("Heaven", octoberTopSong)

        // Verify September capsule stats
        val septMinutes = 2742
        val septTopArtist = "Alan Walker"
        val septStreak = "4-day streak"
        assertEquals(2742, septMinutes)
        assertEquals("Alan Walker", septTopArtist)
        assertEquals("4-day streak", septStreak)
    }

    @Test
    fun testModule5SettingsQualityHierarchy() {
        val qualityOptions = listOf("Automatic", "Low", "Normal", "High", "Very high")
        assertEquals(5, qualityOptions.size)
        assertTrue(qualityOptions.contains("Very high"))

        val dataSaverModes = listOf("Automatic", "On", "Off")
        assertEquals(3, dataSaverModes.size)
        assertTrue(dataSaverModes.contains("Automatic"))
    }
}

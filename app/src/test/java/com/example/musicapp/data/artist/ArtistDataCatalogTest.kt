package com.example.musicapp.data.artist

import com.example.musicapp.data.MusicCatalog
import org.junit.Assert.*
import org.junit.Test

class ArtistDataCatalogTest {

    @Test
    fun testColdplayArtistDataMatchesScreenshot() {
        val artist = MusicCatalog.getArtistData("Coldplay")
        assertEquals("Coldplay", artist.name)
        assertTrue(artist.verified)
        assertTrue(artist.monthlyListeners.contains("94.9M"))
        assertEquals("#7 in Top Artists", artist.globalRank)

        // Verify top tracks match screenshot
        assertTrue(artist.topTracks.size >= 6)
        assertEquals("Yellow", artist.topTracks[0].title)
        assertEquals("4,026,414,846", artist.playCounts[0])
        assertTrue(artist.videoBadges[0])

        assertEquals("Viva La Vida", artist.topTracks[1].title)
        assertEquals("Sparks", artist.topTracks[2].title)

        // Verify discography
        assertTrue(artist.discography.any { it.title == "Parachutes" && it.typeAndYear.contains("2000") })
        assertTrue(artist.discography.any { it.title.contains("Rush of Blood") && it.typeAndYear.contains("2002") })

        // Verify featuring
        assertTrue(artist.featuring.any { it.title.contains("THIS IS Coldplay") })
        assertTrue(artist.featuring.any { it.title.contains("Coldplay Radio") })

        // Verify music videos
        assertTrue(artist.musicVideos.any { it.title.contains("ALL MY LOVE") })

        // Verify similar artists
        assertTrue(artist.similarArtists.any { it.first == "Keane" })
        assertTrue(artist.similarArtists.any { it.first == "Maroon 5" })

        // Verify top cities
        assertTrue(artist.topCities.any { it.city == "London" })
        assertTrue(artist.topCities.any { it.city == "Jakarta" })
    }

    @Test
    fun testUnknownArtistFallbackGeneratesValidData() {
        val artist = MusicCatalog.getArtistData("Random Indie Band")
        assertEquals("Random Indie Band", artist.name)
        assertTrue(artist.topTracks.isNotEmpty())
        assertTrue(artist.discography.isNotEmpty())
        assertTrue(artist.bio.isNotBlank())
        assertTrue(artist.topCities.isNotEmpty())
    }
}

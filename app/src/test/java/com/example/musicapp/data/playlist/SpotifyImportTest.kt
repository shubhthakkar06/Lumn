package com.example.musicapp.data.playlist

import com.example.musicapp.data.network.SongInfo
import org.junit.Assert.*
import org.junit.Test

class SpotifyImportTest {

    @Test
    fun testTrackMatcherMatchesAccurately() {
        val imported = SpotifyImportedTrack(
            id = "t1",
            title = "Yellow",
            artists = "Coldplay",
            durationSeconds = 266L
        )

        val candidate1 = SongInfo(
            videoId = "saavn_1",
            title = "Yellow",
            artist = "Coldplay",
            albumArtUrl = "https://example.com/art.jpg",
            durationSeconds = 269L,
            audioStreamUrl = "https://example.com/audio.mp4"
        )

        val candidate2 = SongInfo(
            videoId = "saavn_2",
            title = "Something Else",
            artist = "Coldplay",
            albumArtUrl = "https://example.com/art.jpg",
            durationSeconds = 200L
        )

        val best = SpotifyTrackMatcher.bestMatch(imported, listOf(candidate1, candidate2))
        assertNotNull(best)
        assertEquals("saavn_1", best?.videoId)
        assertEquals("Yellow", best?.title)
    }

    @Test
    fun testTrackMatcherRejectsUnrelatedSongs() {
        val imported = SpotifyImportedTrack(
            id = "t2",
            title = "Starboy",
            artists = "The Weeknd",
            durationSeconds = 230L
        )

        val unrelated = SongInfo(
            videoId = "saavn_unrelated",
            title = "Channa Mereya",
            artist = "Arijit Singh",
            albumArtUrl = "https://example.com/art.jpg",
            durationSeconds = 289L
        )

        val best = SpotifyTrackMatcher.bestMatch(imported, listOf(unrelated))
        assertNull(best)
    }
}

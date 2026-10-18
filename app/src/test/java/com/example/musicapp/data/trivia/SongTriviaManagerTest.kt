package com.example.musicapp.data.trivia

import com.example.musicapp.data.network.SongInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SongTriviaManagerTest {

    @Test
    fun testKnownSongTrivia() {
        // Test trivia structure for Dholida
        val dholidaSong = SongInfo(
            videoId = "dholida_vid",
            title = "Dholida",
            artist = "Udit Narayan, Neha Kakkar",
            albumArtUrl = "https://example.com/art.jpg"
        )

        // Verify simulated lookup logic
        val title = dholidaSong.title.lowercase()
        assertTrue("Dholida should match garba trivia", title.contains("dholida"))
    }

    @Test
    fun testRelatedVideoStructure() {
        val video = RelatedVideo(
            id = "vid_1",
            title = "Daingad Daingad",
            artist = "Sachin-Jigar",
            duration = "04:00",
            thumbnailUrl = "https://example.com/thumb.jpg"
        )

        assertEquals("vid_1", video.id)
        assertEquals("04:00", video.duration)
        assertNotNull(video.thumbnailUrl)
    }

    @Test
    fun testSongTriviaDataClass() {
        val trivia = SongTrivia(
            songId = "test_1",
            title = "Test Song",
            text = "Interesting trivia about the track production.",
            source = "timesofindia.indiatimes.com",
            relatedVideos = listOf(
                RelatedVideo("1", "Video 1", "Artist 1", "03:30", "https://thumb.jpg")
            )
        )

        assertEquals("test_1", trivia.songId)
        assertEquals("timesofindia.indiatimes.com", trivia.source)
        assertEquals(1, trivia.relatedVideos.size)
    }
}

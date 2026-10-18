package com.example.musicapp.data.provider

import com.example.musicapp.data.network.YouTubeVideoIdParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeVideoIdParserTest {
    @Test
    fun extractsOnlyTheVideoIdWhenPlaylistParametersFollow() {
        assertEquals("dvgZkm1xWPE", YouTubeVideoIdParser.parse("https://music.youtube.com/watch?v=dvgZkm1xWPE&list=RDdvgZkm1xWPE"))
    }

    @Test
    fun supportsShortAndEmbedUrls() {
        assertEquals("1G4isv_Fylg", YouTubeVideoIdParser.parse("https://youtu.be/1G4isv_Fylg?t=12"))
        assertEquals("1G4isv_Fylg", YouTubeVideoIdParser.parse("https://www.youtube.com/embed/1G4isv_Fylg"))
    }

    @Test
    fun rejectsMalformedIdsInsteadOfCreatingUnplayableSearchRows() {
        assertNull(YouTubeVideoIdParser.parse("https://www.youtube.com/watch?v=short"))
        assertNull(YouTubeVideoIdParser.parse("https://www.youtube.com/watch"))
    }
}

package com.example.musicapp.data.provider

import com.example.musicapp.data.network.SongInfo
import org.junit.Assert.assertTrue
import org.junit.Test

class RelevanceTest {
    private val router = ProviderRouter(providers = listOf(object : MusicProvider {
        override val name = "Fake"
        override val isOfficial = true
        override suspend fun search(query: String) = emptyList<SongInfo>()
        override suspend fun resolveStream(trackRef: String, title: String?, artist: String?, durationSeconds: Long, quality: StreamQuality) =
            StreamResult.Failure("n/a")
    }))

    private fun song(title: String, artist: String) = SongInfo("id_$title", title, artist, "")

    @Test
    fun originalBeatsCoversWhenArtistIsNamed() {
        val q = "never gonna give you up rick astley"
        val original = router.relevance(q, song("Never Gonna Give You Up", "Rick Astley"))
        val cover = router.relevance(q, song("Never Gonna Give You Up (Rick Astley Cover)", "Kamileon"))
        val piano = router.relevance(q, song("Never Gonna Give You Up (Pianoforte)", "Rick Astley"))
        assertTrue("original $original vs cover $cover", original > cover)
        assertTrue("original $original vs piano $piano", original > piano)
    }

    @Test
    fun exactTitleBeatsSimilarTitle() {
        val q = "tum hi ho"
        assertTrue(router.relevance(q, song("Tum Hi Ho", "Arijit Singh")) > router.relevance(q, song("Tum Hi Aana", "Jubin Nautiyal")))
    }

    @Test
    fun versionsRankWhenAskedFor() {
        val q = "kesariya lofi"
        assertTrue(router.relevance(q, song("Kesariya (Lofi Flip)", "VIBIE")) > router.relevance(q, song("Kesariya Rangu", "Sid Sriram")))
    }

    @Test
    fun wordsInsideOtherWordsAreNotVersions() {
        val q = "deliver"
        assertTrue(router.relevance(q, song("Deliver", "Lake Street Dive")) >= 100)
    }
}

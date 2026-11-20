package com.example.musicapp.data.recommend

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.musicapp.data.auth.UserManager
import com.example.musicapp.data.catalog.PodcastShow
import com.example.musicapp.data.catalog.Podcasts
import com.example.musicapp.data.catalog.SaavnCatalog
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.playlist.PlaylistManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiscoveryTest {
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        UserManager.resetForTesting(context)
        PlaylistManager.resetForTesting(context)
    }

    private fun song(id: String, artist: String) = SongInfo("saavn_$id", "Song $id", artist, "")

    @Test
    fun rankingSpreadsArtistsOut() {
        val engine = RecommendationEngine.getInstance(context)
        val sameArtist = (1..10).map { song("a$it", "Artist A") }
        val others = (1..5).map { song("o$it", "Other $it") }
        val ranked = engine.rank(sameArtist + others, limit = 10)
        assertEquals(10, ranked.size)
        assertTrue("no more than 2 of one artist before others run out", ranked.take(7).count { it.artist == "Artist A" } <= 2)
    }

    @Test
    fun rankingPenalisesSkippedAndRecentSongs() {
        val user = UserManager.getInstance(context).loginWithGoogle("rank_uid", "rank@test.com", "Rank")
        val log = ListeningLog.getInstance(context)
        val now = System.currentTimeMillis()
        repeat(2) {
            log.record(ListenEvent(user.id, "saavn_skip", "Song skip", "X", atMs = now, listenedMs = 4_000, durationMs = 200_000, completed = false, skipped = true))
        }
        val engine = RecommendationEngine.getInstance(context)
        engine.invalidateTaste()
        val ranked = engine.rank(listOf(song("skip", "X"), song("fresh1", "Y"), song("fresh2", "Z")), limit = 3)
        assertEquals("a song skipped twice ranks last", "saavn_skip", ranked.last().videoId)
    }

    @Test
    fun parsesSaavnSongs() {
        val obj = Json.parseToJsonElement(
            """{"id":"aRZbUYD7","song":"Tum Hi Ho","primary_artists":"Mithoon, Arijit Singh","album":"Aashiqui 2",
               "image":"https://c.saavncdn.com/430/Aashiqui-2-Hindi-2013-150x150.jpg","duration":"262","language":"hindi","play_count":"1000"}"""
        ) as JsonObject
        val parsed = SaavnCatalog.parseSong(obj) { "" }!!
        assertEquals("saavn_aRZbUYD7", parsed.videoId)
        assertEquals(262L, parsed.durationSeconds)
        assertEquals("hindi", parsed.language)
        assertTrue(parsed.albumArtUrl.endsWith("500x500.jpg"))
    }

    @Test
    fun parsesPodcastFeeds() {
        val show = PodcastShow("Show", "Host", "https://example.com/feed", "https://example.com/art.jpg")
        val xml = """<rss><channel><item><title>Episode 1</title><guid>g1</guid>
            <enclosure url="https://example.com/ep1.mp3" type="audio/mpeg"/><itunes:duration>1:02:03</itunes:duration>
            <pubDate>Mon, 03 Aug 2026 10:00:00 GMT</pubDate></item></channel></rss>"""
        val episodes = Podcasts.parseFeed(xml, show)
        assertEquals(1, episodes.size)
        assertTrue(episodes[0].isEpisode)
        assertEquals("https://example.com/ep1.mp3", episodes[0].audioStreamUrl)
        assertEquals(3723L, episodes[0].durationSeconds)
        assertFalse(episodes[0].videoId.isBlank())
    }
}

package com.example.musicapp.data.catalog

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubeMusicParseTest {
    private fun run(text: String, pageType: String? = null) = buildString {
        append("""{"text":"$text"""")
        if (pageType != null) append(""","navigationEndpoint":{"browseEndpoint":{"browseId":"X","browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"$pageType"}}}}""")
        append("}")
    }

    @Test
    fun keepsEveryCreditedArtist() {
        val artist = "MUSIC_PAGE_TYPE_ARTIST"
        val row = """
        {"playlistItemData":{"videoId":"cE4atI_v-Z0"},
         "flexColumns":[
          {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[${run("Main Rang Sharbaton Ka")}]}}},
          {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[${run("Pritam", artist)},${run(", ")},${run("Atif Aslam", artist)},${run(" & ")},${run("Chinmayi Sripaada", artist)},${run(" • ")},${run("Phata Poster Nikhla Hero", "MUSIC_PAGE_TYPE_ALBUM")},${run(" • ")},${run("4:24")}]}}},
          {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[${run("69M plays")}]}}}
         ]}
        """
        val song = YouTubeMusic.parseSong(Json.parseToJsonElement(row).jsonObject)!!
        assertEquals("Pritam, Atif Aslam, Chinmayi Sripaada", song.artist)
        assertEquals("Phata Poster Nikhla Hero", song.albumName)
        assertEquals(264L, song.durationSeconds)
        assertEquals(69_000_000L, song.popularityScore)
    }

    @Test
    fun parsesCounts() {
        assertEquals(1_200_000_000L, YouTubeMusic.parsePlays("1.2B plays"))
        assertEquals(305_000L, YouTubeMusic.parsePlays("305K views"))
        assertEquals(3725L, YouTubeMusic.parseDuration("1:02:05"))
        assertEquals(57_200.0, Shows.subscribers("YouTube channel • @x • 57.2K subscribers"), 0.01)
    }
}

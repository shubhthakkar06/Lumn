package com.example.musicapp.data.playlist

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyPagingTest {
    @Test
    fun `json null next ends pagination`() {
        assertNull(parseSpotifyNextPageUrl(Json.parseToJsonElement("""{"next":null}""") as kotlinx.serialization.json.JsonObject))
    }

    @Test
    fun `valid spotify playlist page is preserved`() {
        val url = "https://api.spotify.com/v1/playlists/abc/items?offset=100&limit=100"
        assertEquals(url, parseSpotifyNextPageUrl(Json.parseToJsonElement("""{"next":"$url"}""") as kotlinx.serialization.json.JsonObject))
    }

    @Test
    fun `non spotify paging url is rejected`() {
        val url = "https://evil.example/v1/playlists/abc/items"
        assertNull(parseSpotifyNextPageUrl(Json.parseToJsonElement("""{"next":"$url"}""") as kotlinx.serialization.json.JsonObject))
    }

    @Test
    fun `string null and invalid schemes are rejected`() {
        assertNull(parseSpotifyNextPageUrl(Json.parseToJsonElement("""{"next":"null"}""") as kotlinx.serialization.json.JsonObject))
        assertNull(parseSpotifyNextPageUrl(Json.parseToJsonElement("""{"next":"http://api.spotify.com/v1/playlists/abc/items"}""") as kotlinx.serialization.json.JsonObject))
    }
}

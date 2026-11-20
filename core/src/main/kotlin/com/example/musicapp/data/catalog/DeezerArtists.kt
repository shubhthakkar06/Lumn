package com.example.musicapp.data.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Deezer's public "related artists" graph (no key needed). Used for "Fans also like" and to
 * widen mixes when JioSaavn has no similar artists for someone.
 */
object DeezerArtists {
    private val http = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<String, List<String>>()

    private fun get(url: String): JsonObject? = runCatching {
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (r.isSuccessful) r.body?.string()?.let { json.parseToJsonElement(it) as? JsonObject } else null
        }
    }.getOrNull()

    /** Names of artists similar to [artistName], most similar first. */
    suspend fun relatedArtistNames(artistName: String, limit: Int = 10): List<String> = withContext(Dispatchers.IO) {
        val key = artistName.lowercase().trim()
        cache[key]?.let { return@withContext it.take(limit) }
        val searchUrl = "https://api.deezer.com/search/artist".toHttpUrl().newBuilder()
            .addQueryParameter("q", artistName).addQueryParameter("limit", "1").build().toString()
        val id = ((get(searchUrl)?.get("data") as? JsonArray)?.firstOrNull() as? JsonObject)
            ?.get("id")?.let { (it as? JsonPrimitive)?.content } ?: return@withContext emptyList()
        val related = (get("https://api.deezer.com/artist/$id/related?limit=20")?.get("data") as? JsonArray)
            ?.mapNotNull { ((it as? JsonObject)?.get("name") as? JsonPrimitive)?.content }
            .orEmpty()
        cache[key] = related
        related.take(limit)
    }
}

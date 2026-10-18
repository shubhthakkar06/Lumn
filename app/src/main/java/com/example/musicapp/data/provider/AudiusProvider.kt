package com.example.musicapp.data.provider

import com.example.musicapp.data.network.SongInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

/**
 * Official Audius Music Provider.
 * Free, legal streaming of full-length tracks with verified audio/mpeg endpoints.
 */
class AudiusProvider(
    private val httpClient: OkHttpClient = OkHttpClient()
) : MusicProvider {

    override val name: String = "Audius"
    override val isOfficial: Boolean = true

    private val json = Json { ignoreUnknownKeys = true }
    private val appName = "CYBER_OBSIDIAN"

    override suspend fun search(query: String): List<SongInfo> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SongInfo>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.audius.co/v1/tracks/search?query=$encoded&app_name=$appName"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "CyberObsidianMusic/1.0 (Android)")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
                val jsonStr = response.body!!.string()
                val root = json.parseToJsonElement(jsonStr) as? JsonObject
                val array = root?.get("data") as? JsonArray
                array?.forEach { element ->
                    val obj = element as? JsonObject ?: return@forEach
                    val id = obj["id"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                    val title = obj["title"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                    val userObj = obj["user"] as? JsonObject
                    val artistName = userObj?.get("name")?.let { (it as? JsonPrimitive)?.content } ?: "Audius Artist"
                    val artworkObj = obj["artwork"] as? JsonObject
                    val artUrl = artworkObj?.get("480x480")?.let { (it as? JsonPrimitive)?.content }
                        ?: artworkObj?.get("150x150")?.let { (it as? JsonPrimitive)?.content }
                        ?: "https://audius.co/assets/img/imagePlaceholder.png"

                    if (id.isNotBlank() && title.isNotBlank()) {
                        val streamUrl = "https://api.audius.co/v1/tracks/$id/stream?app_name=$appName"
                        results.add(
                            SongInfo(
                                videoId = "audius_$id",
                                title = title,
                                artist = artistName,
                                albumArtUrl = artUrl,
                                audioStreamUrl = streamUrl
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        results
    }

    override suspend fun resolveStream(
        trackRef: String,
        title: String?,
        artist: String?
    ): StreamResult = withContext(Dispatchers.IO) {
        if (trackRef.startsWith("audius_")) {
            val id = trackRef.removePrefix("audius_")
            val streamUrl = "https://api.audius.co/v1/tracks/$id/stream?app_name=$appName"
            return@withContext StreamResult.Success(streamUrl = streamUrl, providerName = name, bitrateKbps = 320)
        }

        // Search by query if trackRef is not an Audius ID
        val query = listOfNotNull(title, artist).joinToString(" ")
        if (query.isNotBlank()) {
            val hits = search(query)
            val best = hits.firstOrNull()
            if (best != null && best.audioStreamUrl != null) {
                return@withContext StreamResult.Success(
                    streamUrl = best.audioStreamUrl,
                    providerName = name,
                    bitrateKbps = 320
                )
            }
        }

        StreamResult.Failure("No Audius track found for $trackRef", canRetryWithOtherProvider = true)
    }
}

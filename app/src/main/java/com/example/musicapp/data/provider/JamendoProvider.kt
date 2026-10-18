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
 * Jamendo Creative Commons Music Provider.
 */
class JamendoProvider(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val clientId: String = "b6747d04"
) : MusicProvider {

    override val name: String = "Jamendo"
    override val isOfficial: Boolean = true

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(query: String): List<SongInfo> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SongInfo>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.jamendo.com/v3.0/tracks/?client_id=$clientId&format=json&limit=15&search=$encoded&audioformat=mp32"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "CyberObsidianMusic/1.0 (Android)")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
                val jsonStr = response.body!!.string()
                val root = json.parseToJsonElement(jsonStr) as? JsonObject
                val array = root?.get("results") as? JsonArray
                array?.forEach { element ->
                    val obj = element as? JsonObject ?: return@forEach
                    val id = obj["id"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                    val name = obj["name"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                    val artistName = obj["artist_name"]?.let { (it as? JsonPrimitive)?.content } ?: "Jamendo Artist"
                    val image = obj["image"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                    val audioUrl = obj["audio"]?.let { (it as? JsonPrimitive)?.content } ?: ""

                    if (id.isNotBlank() && name.isNotBlank()) {
                        results.add(
                            SongInfo(
                                videoId = "jamendo_$id",
                                title = name,
                                artist = artistName,
                                albumArtUrl = image,
                                audioStreamUrl = audioUrl.ifBlank { null }
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
        if (trackRef.startsWith("jamendo_")) {
            val id = trackRef.removePrefix("jamendo_")
            try {
                val url = "https://api.jamendo.com/v3.0/tracks/?client_id=$clientId&format=json&id=$id&audioformat=mp32"
                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val root = json.parseToJsonElement(response.body!!.string()) as? JsonObject
                    val array = root?.get("results") as? JsonArray
                    val first = array?.firstOrNull() as? JsonObject
                    val audioUrl = first?.get("audio")?.let { (it as? JsonPrimitive)?.content }
                    if (!audioUrl.isNullOrBlank()) {
                        return@withContext StreamResult.Success(streamUrl = audioUrl, providerName = name, bitrateKbps = 192)
                    }
                }
            } catch (e: Exception) {
                // fall through
            }
        }

        val query = listOfNotNull(title, artist).joinToString(" ")
        if (query.isNotBlank()) {
            val hits = search(query)
            val best = hits.firstOrNull { !it.audioStreamUrl.isNullOrBlank() }
            if (best != null && best.audioStreamUrl != null) {
                return@withContext StreamResult.Success(streamUrl = best.audioStreamUrl, providerName = name, bitrateKbps = 192)
            }
        }

        StreamResult.Failure("No stream found on Jamendo for $trackRef", canRetryWithOtherProvider = true)
    }
}

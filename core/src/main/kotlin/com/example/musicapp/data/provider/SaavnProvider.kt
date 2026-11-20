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
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Unofficial JioSaavn Music Provider (Best-effort).
 * Direct 320kbps Akamai CDN stream extraction.
 */
class SaavnProvider(
    private val httpClient: OkHttpClient = OkHttpClient()
) : MusicProvider {

    override val name: String = "Saavn"
    override val isOfficial: Boolean = false

    private val json = Json { ignoreUnknownKeys = true }
    private val memoryStreamCache = ConcurrentHashMap<String, String>()

    @Suppress("unused")
    private fun unescapeHtml(text: String): String {
        return text.replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#039;", "'")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
    }

    fun decryptSaavnUrl(encryptedMediaUrl: String): String {
        return try {
            val key = "38346591".toByteArray(Charsets.UTF_8)
            val spec = SecretKeySpec(key, "DES")
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, spec)
            // okio's decoder works on every Android version and on the desktop JVM.
            val decoded = okio.ByteString.Companion.run { encryptedMediaUrl.trim().decodeBase64() }?.toByteArray()
                ?: return ""
            val decrypted = cipher.doFinal(decoded)
            val rawUrl = String(decrypted, Charsets.UTF_8)
            rawUrl.replace("_96.mp4", "_320.mp4")
                .replace("_160.mp4", "_320.mp4")
                .replace("_48.mp4", "_320.mp4")
        } catch (e: Exception) {
            ""
        }
    }

    override suspend fun search(query: String): List<SongInfo> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SongInfo>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.jiosaavn.com/api.php?__call=search.getResults&q=$encoded&_format=json&_marker=0&ctx=android&p=1&n=20"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile)")
                .build()
            val jsonStr = httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
            if (jsonStr != null) {
                val root = json.parseToJsonElement(jsonStr) as? JsonObject
                (root?.get("results") as? JsonArray)?.forEach { element ->
                    val song = (element as? JsonObject)?.let { com.example.musicapp.data.catalog.SaavnCatalog.parseSong(it, ::decryptSaavnUrl) }
                        ?: return@forEach
                    song.audioStreamUrl?.let { stream ->
                        memoryStreamCache[song.videoId] = stream
                        memoryStreamCache["${song.title.lowercase()}_${song.artist.lowercase()}"] = stream
                    }
                    results.add(song)
                }
            }
        } catch (e: Exception) {
            System.err.println("SaavnProvider search failed: ${e.message}")
        }
        results
    }

    override suspend fun resolveStream(
        trackRef: String,
        title: String?,
        artist: String?,
        durationSeconds: Long,
        quality: StreamQuality
    ): StreamResult = when (val result = resolveBestQuality(trackRef, title, artist, durationSeconds)) {
        is StreamResult.Success -> result.copy(
            streamUrl = quality.applyToSaavnUrl(result.streamUrl),
            bitrateKbps = quality.kbps
        )
        is StreamResult.Failure -> result
    }

    private suspend fun resolveBestQuality(
        trackRef: String,
        title: String?,
        artist: String?,
        durationSeconds: Long
    ): StreamResult = withContext(Dispatchers.IO) {
        // 1. Check in-memory stream cache
        memoryStreamCache[trackRef]?.let {
            return@withContext StreamResult.Success(streamUrl = it, providerName = name)
        }
        if (!title.isNullOrBlank() && !artist.isNullOrBlank()) {
            memoryStreamCache["${title.lowercase()}_${artist.lowercase()}"]?.let {
                return@withContext StreamResult.Success(streamUrl = it, providerName = name)
            }
        }

        // 2. Query song details if trackRef starts with saavn_
        if (trackRef.startsWith("saavn_")) {
            val pid = trackRef.removePrefix("saavn_")
            try {
                val url = "https://www.jiosaavn.com/api.php?__call=song.getDetails&pids=$pid&_format=json&_marker=0&ctx=android"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile)")
                    .build()
                val jsonStr = httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.string() else null
                }
                if (jsonStr != null) {
                    val root = json.parseToJsonElement(jsonStr) as? JsonObject
                    val songObj = root?.get(pid) as? JsonObject
                        ?: root?.values?.firstOrNull { it is JsonObject } as? JsonObject
                    val encUrl = songObj?.get("encrypted_media_url")?.let { (it as? JsonPrimitive)?.content }
                    if (!encUrl.isNullOrBlank()) {
                        val stream = decryptSaavnUrl(encUrl)
                        if (stream.isNotBlank()) {
                            memoryStreamCache[trackRef] = stream
                            return@withContext StreamResult.Success(streamUrl = stream, providerName = name)
                        }
                    }
                }
            } catch (e: Exception) {
                // pass to title search
            }
        }

        // 3. Fallback search by title and artist
        val searchQueries = listOfNotNull(
            if (!title.isNullOrBlank() && !artist.isNullOrBlank()) "$title $artist" else null,
            title
        )
        for (q in searchQueries) {
            try {
                val hit = TrackMatching.bestPlayable(title, artist, durationSeconds, search(q))
                if (hit != null && hit.audioStreamUrl != null) {
                    memoryStreamCache[trackRef] = hit.audioStreamUrl
                    return@withContext StreamResult.Success(streamUrl = hit.audioStreamUrl, providerName = name)
                }
            } catch (e: Exception) {
                // continue to next query
            }
        }

        StreamResult.Failure("No stream found for track $trackRef", canRetryWithOtherProvider = true)
    }

    override fun invalidateStreamCache(trackRef: String, title: String?, artist: String?) {
        memoryStreamCache.remove(trackRef)
        if (!title.isNullOrBlank() && !artist.isNullOrBlank()) {
            memoryStreamCache.remove("${title.lowercase()}_${artist.lowercase()}")
        }
    }
}

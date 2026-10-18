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
            val decoded = try {
                Base64.getDecoder().decode(encryptedMediaUrl.trim())
            } catch (e: Exception) {
                // Fallback for Android legacy base64 if needed
                android.util.Base64.decode(encryptedMediaUrl.trim(), android.util.Base64.DEFAULT)
            }
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

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
                val jsonStr = response.body!!.string()
                val root = json.parseToJsonElement(jsonStr) as? JsonObject
                val array = root?.get("results") as? JsonArray
                array?.forEach { element ->
                    val obj = element as? JsonObject ?: return@forEach
                    val id = obj["id"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                    val songName = obj["song"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                    val primaryArtists = obj["primary_artists"]?.let { (it as? JsonPrimitive)?.content }
                        ?: obj["singers"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                    val moreInfo = obj["more_info"] as? JsonObject
                    val albumName = (obj["album"] ?: moreInfo?.get("album"))
                        ?.let { (it as? JsonPrimitive)?.content }.orEmpty()
                    val durationSeconds = (obj["duration"] ?: moreInfo?.get("duration"))
                        ?.let { (it as? JsonPrimitive)?.content?.toLongOrNull() } ?: 0L
                    val rawImg = obj["image"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                    val highResImg = rawImg.replace("150x150.jpg", "500x500.jpg")
                        .replace("150x150.webp", "500x500.jpg")
                        .replace("50x50.jpg", "500x500.jpg")
                    val encUrl = obj["encrypted_media_url"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                    val popularity = obj["play_count"]?.let { (it as? JsonPrimitive)?.content?.toLongOrNull() }
                        ?: (obj["more_info"] as? JsonObject)?.get("play_count")?.let { (it as? JsonPrimitive)?.content?.toLongOrNull() }
                        ?: 0L
                    val directStream = if (encUrl.isNotBlank()) decryptSaavnUrl(encUrl) else null

                    if (id.isNotBlank() && songName.isNotBlank()) {
                        val cleanTitle = unescapeHtml(songName)
                        val cleanArtist = unescapeHtml(primaryArtists)
                        val videoId = "saavn_$id"
                        if (!directStream.isNullOrBlank()) {
                            memoryStreamCache[videoId] = directStream
                            memoryStreamCache["${cleanTitle.lowercase()}_${cleanArtist.lowercase()}"] = directStream
                        }
                        results.add(
                            SongInfo(
                                videoId = videoId,
                                title = cleanTitle,
                                artist = cleanArtist,
                                albumArtUrl = highResImg,
                                audioStreamUrl = directStream,
                                albumName = albumName,
                                popularityScore = popularity,
                                durationSeconds = durationSeconds
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
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val jsonStr = response.body!!.string()
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
                val items = search(q)
                val hit = items
                    .asSequence()
                    .filter { !it.audioStreamUrl.isNullOrBlank() }
                    .map { it to matchConfidence(title.orEmpty(), artist.orEmpty(), durationSeconds, it) }
                    .filter { (_, score) -> score >= 0.76 }
                    .maxByOrNull { (candidate, score) -> score + kotlin.math.ln1p(candidate.popularityScore.toDouble()) * 0.001 }
                    ?.first
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

    /** Reject near-by catalog hits with a different singer or recording. */
    private fun matchConfidence(queryTitle: String, queryArtist: String, queryDuration: Long, candidate: SongInfo): Double {
        val wantedTitle = normalize(queryTitle)
        val actualTitle = normalize(candidate.title)
        val wantedArtist = normalize(queryArtist)
        val actualArtist = normalize(candidate.artist)
        if (wantedTitle.isBlank() || actualTitle.isBlank()) return 0.0

        val titleScore = when {
            actualTitle == wantedTitle -> 1.0
            actualTitle.contains(wantedTitle) || wantedTitle.contains(actualTitle) -> 0.86
            else -> similarity(wantedTitle, actualTitle)
        }
        val artistScore = when {
            wantedArtist.isBlank() -> 0.72
            actualArtist == wantedArtist -> 1.0
            actualArtist.contains(wantedArtist) || wantedArtist.contains(actualArtist) -> 0.9
            else -> tokenOverlap(wantedArtist, actualArtist)
        }
        if (artistScore < 0.45) return 0.0

        // Version words carry meaning (especially live/remix/slowed/garba edits). Ignore
        // upload labels, but reject a candidate whose recording type conflicts with Spotify.
        val versionWords = setOf("live", "remix", "slowed", "reverb", "acoustic", "instrumental", "karaoke", "cover", "sped", "lofi", "lo fi", "garba", "unplugged")
        val wantedVersions = versionWords.filter { wantedTitle.contains(it) }
        val candidateVersions = versionWords.filter { actualTitle.contains(it) }
        if (candidateVersions.any { it !in wantedVersions }) return 0.0
        if (wantedVersions.any { it !in candidateVersions }) return 0.0

        val candidateDuration = candidate.durationSeconds
        val durationScore = if (queryDuration > 0 && candidateDuration > 0) {
            val difference = kotlin.math.abs(queryDuration - candidateDuration)
            // Allow small provider edits/rounding, while excluding alternate cuts.
            if (difference > maxOf(12L, (queryDuration * 0.12).toLong())) return 0.0
            1.0 - (difference.toDouble() / maxOf(queryDuration, candidateDuration).toDouble())
        } else 0.72
        return titleScore * 0.62 + artistScore * 0.28 + durationScore * 0.10
    }

    private fun normalize(value: String): String = value.lowercase()
        .replace(Regex("\\b(official|audio|video|lyrics?|lyric video|visuali[sz]er|hd|hq|4k)\\b", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("\\([^)]*\\)|\\[[^]]*]")) { match ->
            val label = match.value.lowercase()
            if (listOf("live", "remix", "slowed", "reverb", "acoustic", "instrumental", "karaoke", "cover", "sped", "lofi", "garba", "unplugged").any(label::contains)) label else " "
        }
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private fun tokenOverlap(a: String, b: String): Double {
        val wanted = a.split(Regex("\\s+")).filter { it.length > 1 }.toSet()
        val found = b.split(Regex("\\s+")).filter { it.length > 1 }.toSet()
        if (wanted.isEmpty() || found.isEmpty()) return 0.0
        return wanted.count { token -> found.any { it == token || it.startsWith(token) || token.startsWith(it) } }.toDouble() / wanted.size
    }

    private fun similarity(a: String, b: String): Double {
        if (a.isBlank() || b.isBlank()) return 0.0
        val previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            var diagonal = previous[0]
            previous[0] = i + 1
            for (j in b.indices) {
                val old = previous[j + 1]
                previous[j + 1] = minOf(previous[j + 1] + 1, previous[j] + 1, diagonal + if (a[i] == b[j]) 0 else 1)
                diagonal = old
            }
        }
        return 1.0 - previous[b.length].toDouble() / maxOf(a.length, b.length)
    }

    override fun invalidateStreamCache(trackRef: String, title: String?, artist: String?) {
        memoryStreamCache.remove(trackRef)
        if (!title.isNullOrBlank() && !artist.isNullOrBlank()) {
            memoryStreamCache.remove("${title.lowercase()}_${artist.lowercase()}")
        }
    }
}

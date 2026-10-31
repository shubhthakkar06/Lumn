package com.example.musicapp.data.lyrics

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class LyricLine(val timestampMs: Long, val text: String)

internal fun parseLrcContent(lrcText: String): List<LyricLine> {
    val timestamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    return lrcText.lineSequence().flatMap { line ->
        val stamps = timestamp.findAll(line).toList()
        if (stamps.isEmpty()) return@flatMap emptySequence()
        val text = line.substring(stamps.last().range.last + 1).trim()
        if (text.isBlank()) return@flatMap emptySequence()
        stamps.asSequence().map { match ->
            val minutes = match.groupValues[1].toLongOrNull() ?: 0L
            val seconds = match.groupValues[2].toLongOrNull() ?: 0L
            val fractionText = match.groupValues[3]
            val fraction = when (fractionText.length) {
                1 -> (fractionText.toLongOrNull() ?: 0L) * 100L
                2 -> (fractionText.toLongOrNull() ?: 0L) * 10L
                3 -> fractionText.toLongOrNull() ?: 0L
                else -> 0L
            }
            LyricLine((minutes * 60L + seconds) * 1_000L + fraction, text)
        }
    }.sortedBy { it.timestampMs }.toList()
}

/** Loads synchronized lyrics only when LRCLIB's track identity agrees with the playing song. */
class LyricsManager private constructor(context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .callTimeout(9, TimeUnit.SECONDS)
        .build()
    private val memoryCache = ConcurrentHashMap<String, List<LyricLine>>()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getLyrics(
        title: String,
        artist: String,
        album: String = "",
        durationSeconds: Long = 0L
    ): List<LyricLine> = withContext(Dispatchers.IO) {
        val key = listOf(title, artist, album, durationSeconds).joinToString("::").lowercase()
        memoryCache[key]?.let { return@withContext it }
        val lyrics = runCatching { fetchFromLrclib(title, artist, album, durationSeconds) }
            .getOrDefault(emptyList())
        memoryCache[key] = lyrics
        lyrics
    }

    private fun fetchFromLrclib(title: String, artist: String, album: String, durationSeconds: Long): List<LyricLine> {
        val cleanTitle = clean(title)
        val cleanArtist = clean(artist)
        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return emptyList()

        // The exact endpoint uses duration as part of the identity and reduces collisions between
        // alternate edits, live recordings and similarly named tracks.
        if (durationSeconds > 0L) {
            val exactUrl = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
                .addQueryParameter("track_name", cleanTitle)
                .addQueryParameter("artist_name", artist)
                .addQueryParameter("album_name", album.takeIf { it.isNotBlank() })
                .addQueryParameter("duration", durationSeconds.toString())
                .build()
            (request(exactUrl.toString()) as? JsonObject)?.let { item ->
                if (isSameTrack(item, cleanTitle, cleanArtist, durationSeconds)) {
                    lyrics(item)?.takeIf { it.isNotEmpty() }?.let { return it }
                }
            }
        }

        val searchUrl = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", "$cleanTitle $artist")
            .build()
        val matches = request(searchUrl.toString()) as? JsonArray ?: return emptyList()
        return matches.asSequence()
            .filterIsInstance<JsonObject>()
            .filter { isSameTrack(it, cleanTitle, cleanArtist, durationSeconds) }
            .mapNotNull(::lyrics)
            .firstOrNull { it.isNotEmpty() }
            ?: emptyList()
    }

    private fun request(url: String): kotlinx.serialization.json.JsonElement? {
        val req = Request.Builder().url(url).header("User-Agent", "RivaraMusic/2.7.1 (Android)").build()
        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            return json.parseToJsonElement(body)
        }
    }

    private fun isSameTrack(item: JsonObject, targetTitle: String, targetArtist: String, duration: Long): Boolean {
        val candidateTitle = clean(item.string("trackName"))
        val candidateArtist = clean(item.string("artistName"))
        if (candidateTitle != targetTitle || candidateArtist.isBlank()) return false
        val expectedArtists = targetArtist.split(' ').filter { it.length > 1 }.toSet()
        val actualArtists = candidateArtist.split(' ').filter { it.length > 1 }.toSet()
        val overlap = expectedArtists.intersect(actualArtists).size.toDouble() / expectedArtists.size.coerceAtLeast(1)
        if (overlap < 0.6) return false
        val candidateDuration = item.string("duration").toDoubleOrNull()?.toLong() ?: 0L
        return duration <= 0L || candidateDuration <= 0L || kotlin.math.abs(candidateDuration - duration) <= 3L
    }

    private fun lyrics(item: JsonObject): List<LyricLine>? =
        item.string("syncedLyrics").takeIf(String::isNotBlank)?.let(::parseLrc)

    private fun clean(value: String): String = value
        .replace(Regex("(?i)\\b(official\\s+(audio|video|lyrics)|audio|lyrics)\\b"), " ")
        .replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim().lowercase()

    private fun JsonObject.string(name: String): String =
        (this[name] as? JsonPrimitive)?.contentOrNull.orEmpty()

    /** Supports multiple timestamps per line and both hundredth and millisecond fractions. */
    fun parseLrc(lrcText: String): List<LyricLine> = parseLrcContent(lrcText)

    companion object {
        @Volatile private var instance: LyricsManager? = null
        fun getInstance(context: Context): LyricsManager = instance ?: synchronized(this) {
            instance ?: LyricsManager(context.applicationContext).also { instance = it }
        }
    }
}

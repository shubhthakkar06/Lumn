package com.example.musicapp.data.catalog

import com.example.musicapp.data.network.SongInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * YouTube Music's own search (the "WEB_REMIX" InnerTube client). Unlike the generic YouTube
 * extractor it returns every credited artist ("Pritam, Atif Aslam & Chinmayi"), the album,
 * the duration and the play count. It covers artists JioSaavn cannot license in India.
 */
object YouTubeMusic {
    private const val SEARCH_URL = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false"
    private const val CLIENT_VERSION = "1.20241127.01.00"
    /** Search filter "Songs". */
    private const val SONGS_PARAMS = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    data class Credit(val name: String, val browseId: String)

    /** Songs credited to [artistName], most played first. */
    suspend fun artistSongs(artistName: String, limit: Int = 25): List<SongInfo> {
        val wanted = artistName.trim().lowercase()
        if (wanted.isBlank()) return emptyList()
        return searchSongs(artistName, 40)
            .filter { song -> song.artist.split(",", "&").any { it.trim().lowercase() == wanted } }
            .sortedByDescending { it.popularityScore }
            .distinctBy { it.title.lowercase().replace(Regex("\\s*\\(.*?\\)"), "") }
            .take(limit)
    }

    /** Search filter "Videos" (official music videos, live performances). */
    private const val VIDEOS_PARAMS = "EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D"

    suspend fun searchSongs(query: String, limit: Int = 20): List<SongInfo> = search(query, SONGS_PARAMS, limit)

    suspend fun searchVideos(query: String, limit: Int = 10): List<SongInfo> = search(query, VIDEOS_PARAMS, limit)

    private suspend fun search(query: String, params: String, limit: Int): List<SongInfo> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val body = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", CLIENT_VERSION)
                    put("hl", "en")
                    put("gl", "IN")
                }
            }
            put("query", query)
            put("params", java.net.URLDecoder.decode(params, "UTF-8"))
        }.toString()
        val request = Request.Builder()
            .url(SEARCH_URL)
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Safari/537.36")
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        val text = http.newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null }
            ?: return@withContext emptyList()
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return@withContext emptyList()
        val rows = mutableListOf<JsonObject>()
        collect(root, "musicResponsiveListItemRenderer", rows)
        rows.mapNotNull(::parseSong).distinctBy { it.videoId }.take(limit)
    }

    /** Parses one song row. Visible for tests. */
    fun parseSong(row: JsonObject): SongInfo? {
        val columns = (row["flexColumns"] as? JsonArray)?.mapNotNull { col ->
            ((col as? JsonObject)?.get("musicResponsiveListItemFlexColumnRenderer") as? JsonObject)
                ?.obj("text")?.get("runs") as? JsonArray
        } ?: return null
        if (columns.isEmpty()) return null
        val titleRuns = columns[0]
        val title = titleRuns.joinToString("") { (it as? JsonObject)?.str("text").orEmpty() }.trim()
        val videoId = row.obj("playlistItemData")?.str("videoId")
            ?: titleRuns.firstNotNullOfOrNull { (it as? JsonObject)?.obj("navigationEndpoint")?.obj("watchEndpoint")?.str("videoId") }
            ?: return null
        if (title.isBlank()) return null

        val detail = columns.getOrNull(1) ?: JsonArray(emptyList())
        val artists = mutableListOf<Credit>()
        var album = ""
        var duration = 0L
        // Runs look like: Artist, Artist & Artist • Album • 4:24. Artist and album runs carry a
        // browse endpoint whose page type says which is which.
        var segment = 0
        detail.forEach { element ->
            val run = element as? JsonObject ?: return@forEach
            val textValue = run.str("text").orEmpty()
            val browse = run.obj("navigationEndpoint")?.obj("browseEndpoint")
            val pageType = browse?.obj("browseEndpointContextSupportedConfigs")
                ?.obj("browseEndpointContextMusicConfig")?.str("pageType").orEmpty()
            when {
                textValue.trim() == "•" -> segment++
                pageType == "MUSIC_PAGE_TYPE_ARTIST" || pageType == "MUSIC_PAGE_TYPE_USER_CHANNEL" ->
                    artists += Credit(textValue.trim(), browse?.str("browseId").orEmpty())
                pageType == "MUSIC_PAGE_TYPE_ALBUM" -> album = textValue.trim()
                DURATION.matches(textValue.trim()) -> duration = parseDuration(textValue.trim())
                browse == null && segment == 0 && textValue.isNotBlank() && textValue.trim() !in setOf(",", "&", "and") ->
                    // Artists without an artist page appear as plain text in the first segment.
                    artists += Credit(textValue.trim(), "")
            }
        }
        val plays = columns.drop(2).flatMap { runs -> runs.mapNotNull { (it as? JsonObject)?.str("text") } }
            .firstNotNullOfOrNull { parsePlays(it) } ?: 0L
        val thumb = row.obj("thumbnail")?.obj("musicThumbnailRenderer")?.obj("thumbnail")
            ?.get("thumbnails")?.let { it as? JsonArray }?.lastOrNull()?.let { (it as? JsonObject)?.str("url") }
            .orEmpty()
        val art = when {
            thumb.contains("googleusercontent.com") -> thumb.replace(Regex("=w\\d+-h\\d+.*$"), "=w544-h544-l90-rj")
            thumb.isNotBlank() -> thumb
            else -> "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        }
        return SongInfo(
            videoId = videoId,
            title = title,
            artist = artists.map { it.name }.filter { it.isNotBlank() }.distinct().joinToString(", "),
            albumArtUrl = art,
            albumName = album,
            popularityScore = plays,
            durationSeconds = duration
        )
    }

    private val DURATION = Regex("^(\\d+:)?\\d{1,2}:\\d{2}$")

    internal fun parseDuration(value: String): Long =
        value.split(":").fold(0L) { acc, part -> acc * 60 + (part.toLongOrNull() ?: 0L) }

    internal fun parsePlays(value: String): Long? {
        val match = Regex("^([\\d.,]+)\\s*([KMB]?)\\s+(plays?|views?)$", RegexOption.IGNORE_CASE).find(value.trim()) ?: return null
        val number = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        val scale = when (match.groupValues[2].uppercase()) {
            "K" -> 1_000.0
            "M" -> 1_000_000.0
            "B" -> 1_000_000_000.0
            else -> 1.0
        }
        return (number * scale).toLong()
    }

    private fun collect(element: JsonElement, key: String, out: MutableList<JsonObject>) {
        when (element) {
            is JsonObject -> element.forEach { (k, v) ->
                if (k == key && v is JsonObject) out += v else collect(v, key, out)
            }
            is JsonArray -> element.forEach { collect(it, key, out) }
            else -> Unit
        }
    }

    private fun JsonObject.obj(key: String) = this[key] as? JsonObject
    private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.content
}

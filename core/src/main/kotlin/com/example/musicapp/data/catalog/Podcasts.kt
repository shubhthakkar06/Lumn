package com.example.musicapp.data.catalog

import com.example.musicapp.data.network.SongInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Serializable
data class PodcastShow(val title: String, val author: String, val feedUrl: String, val artworkUrl: String)

/**
 * Real podcasts: shows come from Apple's public podcast directory, and episodes from each
 * show's own RSS feed, so episode audio is the publisher's permanent file.
 */
object Podcasts {
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val episodeCache = ConcurrentHashMap<String, Pair<Long, List<SongInfo>>>()

    suspend fun search(query: String, limit: Int = 15): List<PodcastShow> = withContext(Dispatchers.IO) {
        val url = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("media", "podcast")
            .addQueryParameter("term", query)
            .addQueryParameter("limit", "$limit")
            .addQueryParameter("country", java.util.Locale.getDefault().country.ifBlank { "IN" })
            .build()
        val body = runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { if (it.isSuccessful) it.body?.string() else null }
        }.getOrNull() ?: return@withContext emptyList()
        val results = (runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject)?.get("results") as? JsonArray
        results.orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.content.orEmpty()
            val feed = s("feedUrl")
            if (feed.isBlank()) null else PodcastShow(s("collectionName"), s("artistName"), feed, s("artworkUrl600").ifBlank { s("artworkUrl100") })
        }
    }

    /** Newest episodes of a show, as playable episode items. */
    suspend fun episodes(show: PodcastShow, limit: Int = 50): List<SongInfo> = withContext(Dispatchers.IO) {
        episodeCache[show.feedUrl]?.takeIf { System.currentTimeMillis() - it.first < 30 * 60 * 1000L }?.let { return@withContext it.second }
        val xml = runCatching {
            http.newCall(Request.Builder().url(show.feedUrl).build()).execute().use { if (it.isSuccessful) it.body?.string() else null }
        }.getOrNull() ?: return@withContext emptyList()
        val episodes = parseFeed(xml, show).take(limit)
        episodeCache[show.feedUrl] = System.currentTimeMillis() to episodes
        episodes
    }

    /** Parses an RSS feed with the standard DOM parser (available on Android and the desktop JVM). */
    fun parseFeed(xml: String, show: PodcastShow): List<SongInfo> {
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        val doc = runCatching { factory.newDocumentBuilder().parse(org.xml.sax.InputSource(java.io.StringReader(xml))) }.getOrNull()
            ?: return emptyList()
        val items = doc.getElementsByTagName("item")
        val out = mutableListOf<SongInfo>()
        for (i in 0 until items.length) {
            val item = items.item(i) as? org.w3c.dom.Element ?: continue
            fun text(tag: String) = item.getElementsByTagName(tag).item(0)?.textContent?.trim().orEmpty()
            val enclosure = item.getElementsByTagName("enclosure").item(0) as? org.w3c.dom.Element
            val audio = enclosure?.getAttribute("url").orEmpty()
            val title = text("title")
            if (audio.isBlank() || title.isBlank()) continue
            val guid = text("guid").ifBlank { audio }
            val image = (item.getElementsByTagName("itunes:image").item(0) as? org.w3c.dom.Element)?.getAttribute("href").orEmpty()
            val description = text("description").ifBlank { text("itunes:summary") }.replace(Regex("<[^>]+>"), "").trim()
            out += SongInfo(
                videoId = "pod_" + guid.hashCode().toUInt().toString(16),
                title = title,
                artist = show.title,
                albumArtUrl = image.ifBlank { show.artworkUrl },
                audioStreamUrl = audio,
                albumName = "Episode • ${show.title}",
                durationSeconds = parseDuration(text("itunes:duration")),
                isEpisode = true,
                description = description.take(600),
                releaseDate = text("pubDate").split(" ").take(4).joinToString(" ")
            )
        }
        return out
    }

    private fun parseDuration(text: String): Long {
        val parts = text.trim().split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            1 -> parts[0]
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0L
        }
    }
}

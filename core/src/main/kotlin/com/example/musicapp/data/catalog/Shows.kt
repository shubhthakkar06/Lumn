package com.example.musicapp.data.catalog

import com.example.musicapp.data.cloud.FollowedShow
import com.example.musicapp.data.network.OkHttpDownloader
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.network.YouTubeVideoIdParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
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
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** A show: a podcast (RSS feed) or a YouTube channel that posts talks, episodes or pravachans. */
@Serializable
data class ShowRef(
    /** "rss:<feed url>" or "yt:<channel id>". */
    val id: String,
    val title: String,
    val author: String = "",
    val imageUrl: String = "",
    /** Short line under the title, e.g. "YouTube channel • 120K subscribers". */
    val subtitle: String = ""
) {
    val isYouTube: Boolean get() = id.startsWith("yt:")
    val channelId: String get() = id.removePrefix("yt:")
    val feedUrl: String get() = id.removePrefix("rss:")

    fun toFollowed(now: Long = System.currentTimeMillis()) = FollowedShow(id, title, author, imageUrl, now)

    companion object {
        fun of(f: FollowedShow) = ShowRef(f.id, f.title, f.author, f.imageUrl)
        fun of(p: PodcastShow) = ShowRef("rss:${p.feedUrl}", p.title, p.author, p.artworkUrl, "Podcast • ${p.author}")
    }
}

data class ShowDetail(val show: ShowRef, val description: String, val episodes: List<SongInfo>, val hasMore: Boolean)

/**
 * Shows and episodes from podcasts and YouTube channels. Channel episodes are the channel's
 * videos; they play as audio, and the player can switch to the video.
 */
object Shows {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).callTimeout(14, TimeUnit.SECONDS).build()
    private val nextPages = ConcurrentHashMap<String, Pair<ListLinkHandler, Page>>()
    private val detailCache = ConcurrentHashMap<String, Pair<Long, ShowDetail>>()

    init {
        runCatching { if (NewPipe.getDownloader() == null) NewPipe.init(OkHttpDownloader()) }
    }

    /** Podcasts and YouTube channels matching [query], channels first when they match well. */
    suspend fun search(query: String, limit: Int = 12): List<ShowRef> = coroutineScope {
        val podcasts = async { withTimeoutOrNull(8_000) { runCatching { Podcasts.search(query, limit) }.getOrNull() }.orEmpty().map(ShowRef::of) }
        val channels = async { withTimeoutOrNull(8_000) { runCatching { searchChannels(query, limit) }.getOrNull() }.orEmpty() }
        val q = query.trim().lowercase()
        (channels.await() + podcasts.await())
            .distinctBy { it.id }
            .sortedByDescending { show ->
                val t = show.title.lowercase()
                val match = when {
                    t == q -> 3.0
                    t.startsWith(q) || q.split(" ").all { it in t } -> 2.0
                    t.contains(q) -> 1.0
                    else -> 0.0
                }
                // Audience size breaks near-ties: an official 57K channel beats a 100-subscriber namesake.
                match + kotlin.math.log10(subscribers(show.subtitle) + 1.0) / 2.0
            }
            .take(limit)
    }

    suspend fun searchChannels(query: String, limit: Int = 10): List<ShowRef> = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB")
                    put("clientVersion", "2.20241126.01.00")
                    put("hl", "en")
                    put("gl", "IN")
                }
            }
            put("query", query)
            put("params", "EgIQAg==") // filter: channels
        }.toString()
        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/search?prettyPrint=false")
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Safari/537.36")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        val text = http.newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return@withContext emptyList()
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return@withContext emptyList()
        val renderers = mutableListOf<JsonObject>()
        collect(root, "channelRenderer", renderers)
        renderers.mapNotNull { r ->
            val id = r.str("channelId") ?: return@mapNotNull null
            val title = r.obj("title")?.str("simpleText") ?: return@mapNotNull null
            val handle = r.obj("subscriberCountText")?.str("simpleText").orEmpty()
            val subs = r.obj("videoCountText")?.str("simpleText")
                ?: (r.obj("videoCountText")?.get("runs") as? JsonArray)?.joinToString("") { (it as? JsonObject)?.str("text").orEmpty() }
            val thumb = (r.obj("thumbnail")?.get("thumbnails") as? JsonArray)?.lastOrNull()?.let { (it as? JsonObject)?.str("url") }.orEmpty()
            val image = (if (thumb.startsWith("//")) "https:$thumb" else thumb).replace(Regex("=s\\d+"), "=s400")
            ShowRef(
                id = "yt:$id",
                title = title,
                author = title,
                imageUrl = image,
                subtitle = listOf("YouTube channel", handle.takeIf { it.startsWith("@") }, subs?.takeIf { it.contains("subscriber") })
                    .filterNotNull().filter { it.isNotBlank() }.joinToString(" • ")
            )
        }.take(limit)
    }

    /** Show page: description and newest episodes. */
    suspend fun detail(show: ShowRef, refresh: Boolean = false): ShowDetail? = withContext(Dispatchers.IO) {
        if (!refresh) detailCache[show.id]?.takeIf { System.currentTimeMillis() - it.first < 15 * 60_000L }?.let { return@withContext it.second }
        val detail = if (show.isYouTube) channelDetail(show) else {
            val podcast = PodcastShow(show.title, show.author, show.feedUrl, show.imageUrl)
            ShowDetail(show, "", Podcasts.episodes(podcast, 100), hasMore = false)
        }
        detail?.also { detailCache[show.id] = System.currentTimeMillis() to it }
    }

    /** Next page of a YouTube channel's episodes (podcast feeds arrive in one page). */
    suspend fun moreEpisodes(show: ShowRef): List<SongInfo> = withContext(Dispatchers.IO) {
        val (tab, page) = nextPages[show.id] ?: return@withContext emptyList()
        val more = runCatching { ChannelTabInfo.getMoreItems(ServiceList.YouTube, tab, page) }.getOrNull() ?: return@withContext emptyList()
        val next = more.nextPage
        if (more.hasNextPage() && next != null) nextPages[show.id] = tab to next else nextPages.remove(show.id)
        more.items.filterIsInstance<StreamInfoItem>().mapNotNull { episode(it, show) }
    }

    fun hasMore(show: ShowRef): Boolean = nextPages.containsKey(show.id)

    /** Newest episodes across followed shows, for Home. */
    suspend fun newEpisodes(shows: List<ShowRef>, perShow: Int = 3): List<SongInfo> = coroutineScope {
        shows.take(8).map { s -> async { withTimeoutOrNull(12_000) { runCatching { detail(s)?.episodes?.take(perShow) }.getOrNull() }.orEmpty() } }
            .awaitAll()
            .flatten()
    }

    private fun channelDetail(show: ShowRef): ShowDetail? {
        val info = runCatching { ChannelInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/channel/${show.channelId}") }.getOrNull()
            ?: return null
        val tab = info.tabs.firstOrNull { ChannelTabs.VIDEOS in it.contentFilters } ?: info.tabs.firstOrNull() ?: return null
        val tabInfo = runCatching { ChannelTabInfo.getInfo(ServiceList.YouTube, tab) }.getOrNull() ?: return null
        val next = tabInfo.nextPage
        if (tabInfo.hasNextPage() && next != null) nextPages[show.id] = tab to next else nextPages.remove(show.id)
        val avatar = info.avatars.maxByOrNull { it.height }?.url.orEmpty()
        val subs = info.subscriberCount.takeIf { it > 0 }?.let { "${compact(it)} subscribers" }
        val ref = show.copy(
            title = info.name.ifBlank { show.title },
            author = info.name.ifBlank { show.author },
            imageUrl = avatar.ifBlank { show.imageUrl },
            subtitle = listOfNotNull("YouTube channel", subs).joinToString(" • ")
        )
        val episodes = tabInfo.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull { episode(it, ref) }
        return ShowDetail(ref, info.description.orEmpty(), episodes, hasMore = tabInfo.hasNextPage())
    }

    private fun episode(item: StreamInfoItem, show: ShowRef): SongInfo? {
        val videoId = YouTubeVideoIdParser.parse(item.url) ?: return null
        return SongInfo(
            videoId = videoId,
            title = item.name,
            artist = show.title,
            albumArtUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            albumName = show.title,
            durationSeconds = item.duration.coerceAtLeast(0L),
            isEpisode = true,
            description = item.shortDescription.orEmpty(),
            releaseDate = item.textualUploadDate.orEmpty()
        )
    }

    /** "57.2K subscribers" → 57200. */
    internal fun subscribers(subtitle: String): Double {
        val m = Regex("([\\d.]+)\\s*([KMB]?)\\s+subscribers?", RegexOption.IGNORE_CASE).find(subtitle) ?: return 0.0
        val n = m.groupValues[1].toDoubleOrNull() ?: return 0.0
        return n * when (m.groupValues[2].uppercase()) { "K" -> 1e3; "M" -> 1e6; "B" -> 1e9; else -> 1.0 }
    }

    private fun compact(n: Long): String = when {
        n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0).replace(".0M", "M")
        n >= 1_000 -> "${n / 1_000}K"
        else -> "$n"
    }

    private fun collect(element: JsonElement, key: String, out: MutableList<JsonObject>) {
        when (element) {
            is JsonObject -> element.forEach { (k, v) -> if (k == key && v is JsonObject) out += v else collect(v, key, out) }
            is JsonArray -> element.forEach { collect(it, key, out) }
            else -> Unit
        }
    }

    private fun JsonObject.obj(key: String) = this[key] as? JsonObject
    private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.content
}

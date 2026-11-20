package com.example.musicapp.data.catalog

import com.example.musicapp.data.network.SongInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Serializable
data class ArtistRef(val id: String, val name: String, val imageUrl: String = "")

@Serializable
data class AlbumRef(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val imageUrl: String = "",
    val year: String = "",
    val releaseDate: String = ""
)

@Serializable
data class PlaylistRef(val id: String, val title: String, val subtitle: String = "", val imageUrl: String = "")

@Serializable
data class ChannelRef(val id: String, val title: String, val imageUrl: String = "", val kind: String = "")

data class ArtistPage(
    val id: String,
    val name: String,
    val imageUrl: String,
    val followerCount: Long,
    val bio: String,
    val verified: Boolean,
    val topSongs: List<SongInfo>,
    val albums: List<AlbumRef>,
    val similar: List<ArtistRef>
)

data class AlbumDetail(val ref: AlbumRef, val artist: String, val songs: List<SongInfo>)

data class PlaylistDetail(val ref: PlaylistRef, val songs: List<SongInfo>)

/**
 * JioSaavn's public web API: search, artist pages, albums, playlists, charts, new releases,
 * mood channels, song recommendations and radio stations. Responses are cached in memory
 * for a while so screens and mixes don't refetch on every visit.
 */
object SaavnCatalog {
    private const val BASE = "https://www.jiosaavn.com/api.php"
    private const val CACHE_TTL_MS = 30 * 60 * 1000L

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    private data class Cached(val element: JsonElement, val atMs: Long)
    private val cache = ConcurrentHashMap<String, Cached>()

    private suspend fun call(params: Map<String, String>, ctx: String = "android"): JsonElement? = withContext(Dispatchers.IO) {
        val url = BASE.toHttpUrl().newBuilder().apply {
            addQueryParameter("_format", "json")
            addQueryParameter("_marker", "0")
            addQueryParameter("ctx", ctx)
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        val key = url.toString()
        cache[key]?.takeIf { System.currentTimeMillis() - it.atMs < CACHE_TTL_MS }?.let { return@withContext it.element }
        val body = runCatching {
            http.newCall(
                Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile)").build()
            ).execute().use { if (it.isSuccessful) it.body?.string() else null }
        }.getOrNull() ?: return@withContext null
        val element = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return@withContext null
        cache[key] = Cached(element, System.currentTimeMillis())
        element
    }

    // ---------------------------------------------------------------- parsing helpers

    private fun JsonElement?.str(): String = (this as? JsonPrimitive)?.content?.takeIf { it != "null" }.orEmpty()
    private fun JsonObject.s(key: String): String = this[key].str()
    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.arr(): JsonArray? = this as? JsonArray

    fun unescape(text: String): String = text.replace("&quot;", "\"").replace("&amp;", "&")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&#039;", "'").replace("&apos;", "'").replace("&#39;", "'")

    /** JioSaavn images come at 50/150px; the CDN also serves 500px. */
    fun hiRes(url: String): String = url.replace(Regex("(50x50|150x150)(\\.(jpg|webp|png))"), "500x500.jpg")

    /** Parses a song object in JioSaavn's classic (non-v4) format. */
    fun parseSong(obj: JsonObject, decrypt: (String) -> String = ::decryptMediaUrl): SongInfo? {
        val id = obj.s("id")
        val title = unescape(obj.s("song").ifBlank { obj.s("title") })
        if (id.isBlank() || title.isBlank()) return null
        val artists = unescape(obj.s("primary_artists").ifBlank { obj.s("singers") }.ifBlank { obj.s("music") })
        val enc = obj.s("encrypted_media_url")
        return SongInfo(
            videoId = "saavn_$id",
            title = title,
            artist = artists,
            albumArtUrl = hiRes(obj.s("image")),
            audioStreamUrl = enc.takeIf { it.isNotBlank() }?.let(decrypt)?.takeIf { it.isNotBlank() },
            albumName = unescape(obj.s("album")),
            popularityScore = obj.s("play_count").toLongOrNull() ?: 0L,
            durationSeconds = obj.s("duration").toLongOrNull() ?: 0L,
            language = obj.s("language"),
            releaseDate = obj.s("release_date").ifBlank { obj.s("year") }
        )
    }

    fun decryptMediaUrl(encrypted: String): String = runCatching {
        val cipher = javax.crypto.Cipher.getInstance("DES/ECB/PKCS5Padding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, javax.crypto.spec.SecretKeySpec("38346591".toByteArray(), "DES"))
        val bytes = okio.ByteString.Companion.run { encrypted.trim().decodeBase64() }?.toByteArray() ?: return ""
        val raw = String(cipher.doFinal(bytes), Charsets.UTF_8)
        raw.replace(Regex("_(48|96|160)\\.mp4"), "_320.mp4")
    }.getOrDefault("")

    private fun songsFrom(element: JsonElement?): List<SongInfo> = when (element) {
        is JsonArray -> element.mapNotNull { (it.obj()?.get("song").obj() ?: it.obj())?.let(::parseSong) }
        is JsonObject -> element.entries.filter { it.key.all(Char::isDigit) }
            .sortedBy { it.key.toInt() }
            .mapNotNull { (_, v) -> (v.obj()?.get("song").obj() ?: v.obj())?.let(::parseSong) }
        else -> emptyList()
    }

    private fun albumFrom(obj: JsonObject): AlbumRef? {
        val id = obj.s("albumid").ifBlank { obj.s("id") }
        val title = unescape(obj.s("album").ifBlank { obj.s("title") }.ifBlank { obj.s("name") })
        if (id.isBlank() || title.isBlank()) return null
        val artist = unescape(obj.s("primary_artists").ifBlank { obj.s("music") }.ifBlank { obj.s("subtitle") })
        return AlbumRef(id, title, artist, hiRes(obj.s("image")), obj.s("year"), obj.s("release_date"))
    }

    private fun playlistFrom(obj: JsonObject): PlaylistRef? {
        val id = obj.s("listid").ifBlank { obj.s("id") }
        val title = unescape(obj.s("listname").ifBlank { obj.s("title") })
        if (id.isBlank() || title.isBlank()) return null
        val subtitle = obj.s("subtitle").ifBlank { obj.s("count").takeIf { it.isNotBlank() }?.let { "$it songs" }.orEmpty() }
        return PlaylistRef(id, title, unescape(subtitle), hiRes(obj.s("image")))
    }

    // ---------------------------------------------------------------- search

    suspend fun searchArtists(query: String, limit: Int = 10): List<ArtistRef> =
        call(mapOf("__call" to "search.getArtistResults", "q" to query, "n" to "$limit", "p" to "1"))
            .obj()?.get("results").arr().orEmpty().mapNotNull { el ->
                val o = el.obj() ?: return@mapNotNull null
                val id = o.s("id")
                val name = unescape(o.s("name"))
                if (id.isBlank() || name.isBlank()) null else ArtistRef(id, name, hiRes(o.s("image")))
            }

    suspend fun searchAlbums(query: String, limit: Int = 10): List<AlbumRef> =
        call(mapOf("__call" to "search.getAlbumResults", "q" to query, "n" to "$limit", "p" to "1"))
            .obj()?.get("results").arr().orEmpty().mapNotNull { it.obj()?.let(::albumFrom) }

    suspend fun searchPlaylists(query: String, limit: Int = 10): List<PlaylistRef> =
        call(mapOf("__call" to "search.getPlaylistResults", "q" to query, "n" to "$limit", "p" to "1"))
            .obj()?.get("results").arr().orEmpty().mapNotNull { it.obj()?.let(::playlistFrom) }

    /** Best artist match for a display name ("Arijit Singh, Pritam" → Arijit Singh). */
    suspend fun findArtist(name: String): ArtistRef? {
        val primary = name.split(",", "&", " feat", " ft.").first().trim()
        if (primary.isBlank()) return null
        val results = searchArtists(primary, 5)
        return results.firstOrNull { it.name.equals(primary, ignoreCase = true) } ?: results.firstOrNull()
    }

    // ---------------------------------------------------------------- pages

    suspend fun artistPage(artistId: String, latestFirst: Boolean = false): ArtistPage? {
        val params = mutableMapOf(
            "__call" to "artist.getArtistPageDetails", "artistId" to artistId,
            "n_song" to "20", "n_album" to "20", "page" to "0"
        )
        if (latestFirst) {
            params["category"] = "latest"
            params["sort_order"] = "desc"
        }
        val d = call(params).obj() ?: return null
        val name = unescape(d.s("name")).ifBlank { return null }
        val songs = songsFrom(d["topSongs"].obj()?.get("songs") ?: d["topSongs"])
        val albums = (d["topAlbums"].obj()?.get("albums") ?: d["topAlbums"]).arr().orEmpty().mapNotNull { it.obj()?.let(::albumFrom) }
        val similar = d["similarArtists"].arr().orEmpty().mapNotNull { el ->
            val o = el.obj() ?: return@mapNotNull null
            val id = o.s("id").ifBlank { o.s("artistid") }
            val n = unescape(o.s("name"))
            if (id.isBlank() || n.isBlank()) null else ArtistRef(id, n, hiRes(o.s("image_url").ifBlank { o.s("image") }))
        }
        val bio = runCatching {
            json.parseToJsonElement(d.s("bio")).arr()?.joinToString("\n\n") { it.obj()?.s("text").orEmpty() }.orEmpty()
        }.getOrDefault("")
        return ArtistPage(
            id = artistId,
            name = name,
            imageUrl = hiRes(d.s("image")),
            followerCount = d.s("follower_count").toLongOrNull() ?: 0L,
            bio = unescape(bio),
            verified = d.s("isVerified").equals("true", true),
            topSongs = songs.distinctBy { it.title.lowercase() },
            albums = albums,
            similar = similar
        )
    }

    suspend fun album(albumId: String): AlbumDetail? {
        val d = call(mapOf("__call" to "content.getAlbumDetails", "albumid" to albumId)).obj() ?: return null
        val ref = albumFrom(d) ?: return null
        return AlbumDetail(ref, unescape(d.s("primary_artists")), songsFrom(d["songs"]))
    }

    suspend fun playlist(listId: String): PlaylistDetail? {
        val d = call(mapOf("__call" to "playlist.getDetails", "listid" to listId), ctx = "web6dot0").obj() ?: return null
        val ref = playlistFrom(d) ?: PlaylistRef(listId, "Playlist")
        return PlaylistDetail(ref, songsFrom(d["songs"]))
    }

    // ---------------------------------------------------------------- discovery feeds

    suspend fun charts(): List<PlaylistRef> =
        call(mapOf("__call" to "content.getCharts")).arr().orEmpty().mapNotNull { it.obj()?.let(::playlistFrom) }

    suspend fun featuredPlaylists(limit: Int = 12): List<PlaylistRef> =
        call(mapOf("__call" to "content.getFeaturedPlaylists", "n" to "$limit", "p" to "1"))
            .arr().orEmpty().mapNotNull { it.obj()?.let(::playlistFrom) }

    suspend fun newAlbums(limit: Int = 20): List<AlbumRef> =
        call(mapOf("__call" to "content.getAlbums", "n" to "$limit", "p" to "1"))
            .arr().orEmpty().mapNotNull { it.obj()?.let(::albumFrom) }

    /** Mood and genre channels (Romance, Workout, The 1990s, Devotional…). */
    suspend fun moodChannels(): List<ChannelRef> =
        call(mapOf("__call" to "webapi.getLaunchData")).obj()?.get("browse_discover").arr().orEmpty().mapNotNull { el ->
            val o = el.obj() ?: return@mapNotNull null
            val id = o.s("id")
            val title = unescape(o.s("title"))
            if (id.isBlank() || title.isBlank() || o.s("type") != "channel") null
            else ChannelRef(id, title, hiRes(o.s("image")), o["more_info"].obj()?.s("sub_type").orEmpty())
        }

    suspend fun channelPlaylists(channelId: String): List<PlaylistRef> =
        call(mapOf("__call" to "channel.getDetails", "channel_id" to channelId, "n" to "10", "p" to "1", "api_version" to "4"), ctx = "web6dot0")
            .obj()?.get("top_playlists").arr().orEmpty().mapNotNull { it.obj()?.let(::playlistFrom) }

    /** "More like this" for one JioSaavn song id (without the `saavn_` prefix). */
    suspend fun recommendations(pid: String): List<SongInfo> {
        val d = call(mapOf("__call" to "reco.getreco", "pid" to pid)) ?: return emptyList()
        return when (d) {
            is JsonObject -> d.values.flatMap { songsFrom(it) }
            else -> songsFrom(d)
        }
    }

    /** A radio station seeded by up to five songs; returns its first [count] songs. */
    suspend fun station(seedPids: List<String>, count: Int = 20): List<SongInfo> {
        if (seedPids.isEmpty()) return emptyList()
        val ids = seedPids.take(5).joinToString(",", "[", "]") { "\"$it\"" }
        val created = call(mapOf("__call" to "webradio.createEntityStation", "entity_id" to ids, "entity_type" to "queue")).obj()
        val stationId = created?.s("stationid")?.takeIf { it.isNotBlank() } ?: return emptyList()
        // Station pages must not come from the cache: each call advances the station.
        cache.keys.removeIf { it.contains("webradio.getSong") }
        return songsFrom(call(mapOf("__call" to "webradio.getSong", "stationid" to stationId, "k" to "$count")))
    }
}

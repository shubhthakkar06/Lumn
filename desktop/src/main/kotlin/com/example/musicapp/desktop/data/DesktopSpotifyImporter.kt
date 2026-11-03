package com.example.musicapp.desktop.data

import com.example.musicapp.desktop.model.DesktopSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class DesktopImportedPlaylist(
    val id: String,
    val name: String,
    val coverUrl: String,
    val songs: List<DesktopSong>
)

object DesktopSpotifyImporter {
    private const val PATHFINDER_FETCH_PLAYLIST_HASH = "7982b11e21535cd2594badc40030b745671b61a1fa66766e569d45e6364f3422"

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun isSpotifyUrl(url: String): Boolean {
        val trimmed = url.trim()
        return trimmed.contains("open.spotify.com/playlist") ||
                trimmed.contains("open.spotify.com/album") ||
                trimmed.contains("spotify.link")
    }

    suspend fun importPlaylist(
        url: String,
        musicRepository: DesktopMusicRepository,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): DesktopImportedPlaylist? = withContext(Dispatchers.IO) {
        var targetUrl = url.trim()
        if (targetUrl.contains("spotify.link")) {
            try {
                val req = Request.Builder().url(targetUrl).head().build()
                http.newCall(req).execute().use { resp ->
                    targetUrl = resp.request.url.toString()
                }
            } catch (_: Exception) {}
        }

        val match = Regex("""(playlist|album)[:/]([A-Za-z0-9]{10,64})""").find(targetUrl) ?: return@withContext null
        val kind = match.groupValues[1]
        val entityId = match.groupValues[2]

        val embedUrl = "https://open.spotify.com/embed/$kind/$entityId"
        val req = Request.Builder()
            .url(embedUrl)
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        val html = try {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                resp.body?.string().orEmpty()
            }
        } catch (_: Exception) {
            return@withContext null
        }

        val scriptRegex = Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""")
        val scriptMatch = scriptRegex.find(html) ?: return@withContext null
        val root = try {
            json.parseToJsonElement(scriptMatch.groupValues[1]) as? JsonObject
        } catch (_: Exception) { null } ?: return@withContext null

        val pageProps = (root["props"] as? JsonObject)?.get("pageProps") as? JsonObject
        val state = pageProps?.get("state") as? JsonObject
        val settings = state?.get("settings") as? JsonObject
        val session = settings?.get("session") as? JsonObject
        val accessToken = (session?.get("accessToken") as? JsonPrimitive)?.content.orEmpty()

        if (kind == "playlist" && accessToken.isNotBlank()) {
            val pathfinderResult = fetchPathfinderPlaylist(entityId, accessToken, onProgress)
            if (pathfinderResult != null && pathfinderResult.songs.isNotEmpty()) {
                return@withContext pathfinderResult
            }
        }

        return@withContext null
    }

    private fun fetchPathfinderPlaylist(
        playlistId: String,
        accessToken: String,
        onProgress: (current: Int, total: Int) -> Unit
    ): DesktopImportedPlaylist? {
        return try {
            val songs = mutableListOf<DesktopSong>()
            var playlistName = ""
            var playlistCoverUrl = ""
            var offset = 0
            val limit = 100
            var totalCount: Int? = null
            val maxFetch = 600

            while (offset < (totalCount ?: Int.MAX_VALUE) && songs.size < maxFetch) {
                val variables = """{"uri":"spotify:playlist:$playlistId","offset":$offset,"limit":$limit,"enableWatchFeedEntrypoint":false}"""
                val extensions = """{"persistedQuery":{"version":1,"sha256Hash":"$PATHFINDER_FETCH_PLAYLIST_HASH"}}"""

                val encodedVariables = URLEncoder.encode(variables, StandardCharsets.UTF_8.toString())
                val encodedExtensions = URLEncoder.encode(extensions, StandardCharsets.UTF_8.toString())
                val queryUrl = "https://api-partner.spotify.com/pathfinder/v1/query?operationName=fetchPlaylist&variables=$encodedVariables&extensions=$encodedExtensions"

                val req = Request.Builder()
                    .url(queryUrl)
                    .header("Authorization", "Bearer $accessToken")
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()

                val resp = http.newCall(req).execute()
                if (!resp.isSuccessful) break
                val bodyStr = resp.body?.string().orEmpty()
                val root = json.parseToJsonElement(bodyStr) as? JsonObject ?: break

                val plData = (root["data"] as? JsonObject)?.get("playlistV2") as? JsonObject ?: break

                if (playlistName.isBlank()) {
                    playlistName = (plData["name"] as? JsonPrimitive)?.content.orEmpty()
                }

                if (playlistCoverUrl.isBlank()) {
                    val imagesObj = plData["images"] as? JsonObject
                    val imageItems = imagesObj?.get("items") as? JsonArray
                    val firstImg = imageItems?.firstOrNull() as? JsonObject
                    val sources = firstImg?.get("sources") as? JsonArray
                    playlistCoverUrl = (sources?.firstOrNull() as? JsonObject)?.get("url")?.let { (it as? JsonPrimitive)?.content }.orEmpty()
                }

                val content = plData["content"] as? JsonObject ?: break
                if (totalCount == null) {
                    totalCount = (content["totalCount"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
                }

                val items = content["items"] as? JsonArray ?: break
                if (items.isEmpty()) break

                for (itemEl in items) {
                    val itemObj = itemEl as? JsonObject ?: continue
                    val itemV2 = itemObj["itemV2"] as? JsonObject ?: continue
                    val trData = itemV2["data"] as? JsonObject ?: continue
                    val uri = (trData["uri"] as? JsonPrimitive)?.content.orEmpty()
                    val title = (trData["name"] as? JsonPrimitive)?.content?.trim().orEmpty()
                    if (title.isBlank()) continue

                    val artistsObj = trData["artists"] as? JsonObject
                    val artistsItems = artistsObj?.get("items") as? JsonArray
                    val artistsList = mutableListOf<String>()
                    artistsItems?.forEach { aEl ->
                        val profile = (aEl as? JsonObject)?.get("profile") as? JsonObject
                        val aName = (profile?.get("name") as? JsonPrimitive)?.content?.trim()
                        if (!aName.isNullOrBlank()) artistsList.add(aName)
                    }
                    val artists = if (artistsList.isNotEmpty()) artistsList.joinToString(", ") else "Unknown Artist"

                    val albumObj = trData["albumOfTrack"] as? JsonObject
                    val albumName = (albumObj?.get("name") as? JsonPrimitive)?.content.orEmpty()
                    val artSources = (albumObj?.get("coverArt") as? JsonObject)?.get("sources") as? JsonArray
                    var trackCoverUrl = ""
                    if (artSources != null && artSources.isNotEmpty()) {
                        val preferred = artSources.firstOrNull { src ->
                            (src as? JsonObject)?.get("width")?.let { (it as? JsonPrimitive)?.content?.toIntOrNull() == 300 } == true
                        } ?: artSources.firstOrNull()
                        trackCoverUrl = (preferred as? JsonObject)?.get("url")?.let { (it as? JsonPrimitive)?.content }.orEmpty()
                    }

                    val durationMs = (trData["trackDuration"] as? JsonObject)?.get("totalMilliseconds")?.let {
                        (it as? JsonPrimitive)?.content?.toLongOrNull()
                    } ?: 0L

                    val trackId = uri.substringAfterLast(':').ifBlank { "${title}_${artists}".lowercase().replace(" ", "_") }

                    songs.add(
                        DesktopSong(
                            id = trackId,
                            title = title,
                            artist = artists,
                            album = albumName.ifBlank { playlistName },
                            durationSeconds = (durationMs / 1000L).coerceAtLeast(0L),
                            albumArtUrl = trackCoverUrl.ifBlank { playlistCoverUrl }
                        )
                    )
                }

                offset += items.size
                onProgress(songs.size, totalCount)
                if (offset >= totalCount) break
            }

            if (songs.isEmpty()) return null

            if (playlistCoverUrl.isBlank()) {
                playlistCoverUrl = songs.firstOrNull { it.albumArtUrl.isNotBlank() }?.albumArtUrl.orEmpty()
            }

            DesktopImportedPlaylist(
                id = playlistId,
                name = playlistName.ifBlank { "Spotify Playlist" },
                coverUrl = playlistCoverUrl,
                songs = songs
            )
        } catch (_: Exception) {
            null
        }
    }
}

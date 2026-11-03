package com.example.musicapp.data.playlist

import android.content.Context
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.network.YouTubeVideoIdParser
import com.example.musicapp.data.provider.ProviderRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.TimeUnit

data class SpotifyImportedTrack(
    val id: String,
    val title: String,
    val artists: String,
    val albumName: String = "",
    val albumArtUrl: String = "",
    val durationSeconds: Long = 0L,
    val resolvedSong: SongInfo? = null
)

data class SpotifyImportedPlaylist(
    val id: String,
    val name: String,
    val spotifyUrl: String,
    val tracks: List<SpotifyImportedTrack>,
    val coverUrl: String = ""
)

/**
 * Universal Playlist Importer for Rivara Mobile.
 *
 * Imports Spotify playlists/albums, YouTube playlists, and multi-line song lists
 * with zero browser popups, zero Spotify Developer OAuth hurdles, and crash-proof
 * on-demand track resolution.
 */
object SpotifyPlaylistImporter {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun import(
        context: Context,
        playlistUrl: String,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): SpotifyImportedPlaylist = withContext(Dispatchers.IO) {
        val trimmed = playlistUrl.trim()
        require(trimmed.isNotBlank()) { "Please paste a playlist link or song list." }

        val playlist = extractSpotify(trimmed)
            ?: extractYouTube(trimmed)
            ?: extractTextList(trimmed)
            ?: throw IllegalArgumentException("Could not read that playlist. Please enter a valid Spotify or YouTube link, or paste song titles.")

        onProgress(1, playlist.tracks.size)

        // Pre-match top 3 tracks with a fast timeout so initial playback is instantaneous
        val router = ProviderRouter()
        val preMatchCount = minOf(3, playlist.tracks.size)
        val matchedTopTracks = (0 until preMatchCount).map { i ->
            val track = playlist.tracks[i]
            async {
                runCatching {
                    withTimeoutOrNull(3000L) {
                        val primary = router.search("${track.title} ${track.artists}")
                        SpotifyTrackMatcher.bestMatch(track, primary)
                    }
                }.getOrNull()
            }
        }.awaitAll()

        val enrichedTracks = playlist.tracks.mapIndexed { i, track ->
            val resolved = if (i < preMatchCount) matchedTopTracks[i] else null
            track.copy(resolvedSong = resolved)
        }

        onProgress(playlist.tracks.size, playlist.tracks.size)

        playlist.copy(tracks = enrichedTracks)
    }

    private fun extractSpotify(input: String): SpotifyImportedPlaylist? {
        var targetUrl = input.trim()
        if (targetUrl.contains("spotify.link")) {
            try {
                val req = Request.Builder().url(targetUrl).head().build()
                http.newCall(req).execute().use { resp ->
                    targetUrl = resp.request.url.toString()
                }
            } catch (_: Exception) {}
        }

        val match = Regex("""(playlist|album)[:/]([A-Za-z0-9]{10,64})""").find(targetUrl) ?: return null
        val kind = match.groupValues[1]
        val entityId = match.groupValues[2]

        val embedUrl = "https://open.spotify.com/embed/$kind/$entityId"
        val req = Request.Builder()
            .url(embedUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        val response = http.newCall(req).execute()
        val html = response.body?.string().orEmpty()
        if (!response.isSuccessful || html.isBlank()) {
            throw IllegalStateException("Spotify playlist could not be loaded (${response.code}).")
        }

        val scriptRegex = Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""")
        val scriptMatch = scriptRegex.find(html)
            ?: throw IllegalStateException("Spotify playlist format changed or playlist is private.")

        val jsonStr = scriptMatch.groupValues[1]
        val root = json.parseToJsonElement(jsonStr) as? JsonObject
            ?: throw IllegalStateException("Failed to parse Spotify data.")

        val pageProps = (root["props"] as? JsonObject)?.get("pageProps") as? JsonObject
        val state = pageProps?.get("state") as? JsonObject
        val data = state?.get("data") as? JsonObject
        val entity = data?.get("entity") as? JsonObject
            ?: throw IllegalStateException("Spotify playlist not found or set to private.")

        val name = (entity["name"] as? JsonPrimitive)?.content
            ?: (entity["title"] as? JsonPrimitive)?.content
            ?: "Spotify Playlist"

        var coverUrl = ""
        val vis = entity["visualIdentity"] as? JsonObject
        val images = vis?.get("image") as? JsonArray
        val lastImg = images?.lastOrNull() as? JsonObject
        coverUrl = (lastImg?.get("url") as? JsonPrimitive)?.content.orEmpty()
        if (coverUrl.isBlank()) {
            val coverArt = entity["coverArt"] as? JsonObject
            val sources = coverArt?.get("sources") as? JsonArray
            val firstSource = sources?.firstOrNull() as? JsonObject
            coverUrl = (firstSource?.get("url") as? JsonPrimitive)?.content.orEmpty()
        }

        val trackList = entity["trackList"] as? JsonArray ?: JsonArray(emptyList())
        val tracks = mutableListOf<SpotifyImportedTrack>()

        for (itemEl in trackList) {
            val item = itemEl as? JsonObject ?: continue
            val tId = (item["id"] as? JsonPrimitive)?.content
                ?: (item["uri"] as? JsonPrimitive)?.content?.substringAfterLast(':').orEmpty()
            val title = (item["title"] as? JsonPrimitive)?.content?.trim().orEmpty()
            val subtitle = (item["subtitle"] as? JsonPrimitive)?.content?.trim().orEmpty()
            val durationMs = (item["duration"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L

            if (title.isNotBlank()) {
                tracks += SpotifyImportedTrack(
                    id = tId.ifBlank { "${title}_${subtitle}" },
                    title = title,
                    artists = subtitle.ifBlank { "Unknown Artist" },
                    albumName = name,
                    albumArtUrl = coverUrl,
                    durationSeconds = (durationMs / 1000L).coerceAtLeast(0L)
                )
            }
        }

        if (tracks.isEmpty()) {
            throw IllegalStateException("No playable tracks were found in that Spotify playlist.")
        }

        return SpotifyImportedPlaylist(
            id = entityId,
            name = name,
            spotifyUrl = "https://open.spotify.com/$kind/$entityId",
            tracks = tracks,
            coverUrl = coverUrl
        )
    }

    private fun extractYouTube(input: String): SpotifyImportedPlaylist? {
        val trimmed = input.trim()
        val isYt = trimmed.contains("youtube.com/playlist") ||
            trimmed.contains("music.youtube.com/playlist") ||
            (trimmed.contains("youtube.com") && trimmed.contains("list="))

        if (!isYt) return null

        return try {
            val extractor = ServiceList.YouTube.getPlaylistExtractor(trimmed)
            extractor.fetchPage()
            val name = extractor.name.ifBlank { "YouTube Playlist" }
            val cover = extractor.thumbnails.maxByOrNull { it.height * it.width }?.url.orEmpty()
            val tracks = extractor.initialPage.items.filterIsInstance<StreamInfoItem>().map { item ->
                val vid = YouTubeVideoIdParser.parse(item.url) ?: item.url
                SpotifyImportedTrack(
                    id = vid,
                    title = item.name.orEmpty(),
                    artists = item.uploaderName.orEmpty(),
                    albumArtUrl = item.thumbnails.maxByOrNull { it.height * it.width }?.url.orEmpty(),
                    durationSeconds = item.duration.coerceAtLeast(0L)
                )
            }
            if (tracks.isEmpty()) null else {
                SpotifyImportedPlaylist(
                    id = "yt_" + System.currentTimeMillis(),
                    name = name,
                    spotifyUrl = trimmed,
                    tracks = tracks,
                    coverUrl = cover
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractTextList(input: String): SpotifyImportedPlaylist? {
        val lines = input.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") }
        // Only treat as song list if there's at least one non-URL line
        if (lines.isEmpty() || lines.all { it.startsWith("http://") || it.startsWith("https://") }) return null

        val tracks = lines.mapIndexed { index, line ->
            val parts = when {
                line.contains(" - ") -> line.split(" - ", limit = 2)
                line.contains(" by ", ignoreCase = true) -> line.split(Regex(" by ", RegexOption.IGNORE_CASE), limit = 2)
                line.contains(" | ") -> line.split(" | ", limit = 2)
                else -> listOf(line, "")
            }
            val title = parts[0].trim().replace(Regex("""^\d+[\.\)]\s*"""), "")
            val artist = parts.getOrNull(1)?.trim().orEmpty()
            SpotifyImportedTrack(
                id = "track_${index}_${System.currentTimeMillis()}",
                title = title,
                artists = artist.ifBlank { "Various Artists" },
                albumArtUrl = "",
                durationSeconds = 0L
            )
        }

        return SpotifyImportedPlaylist(
            id = "list_" + System.currentTimeMillis(),
            name = "Imported Mix",
            spotifyUrl = "",
            tracks = tracks,
            coverUrl = ""
        )
    }
}

/** Match identity to Rivara's catalog without requiring identical artist spelling. */
internal object SpotifyTrackMatcher {
    private val distinctVersionTerms = setOf(
        "remix", "live", "acoustic", "instrumental", "karaoke", "cover", "sped", "slowed", "garba", "unplugged", "remaster", "edit", "demo"
    )

    fun bestMatch(track: SpotifyImportedTrack, candidates: List<SongInfo>): SongInfo? = candidates
        .mapNotNull { candidate -> score(track, candidate)?.let { candidate to it } }
        .maxWithOrNull(compareBy<Pair<SongInfo, Double>> { it.second }
            .thenBy { !it.first.audioStreamUrl.isNullOrBlank() })
        ?.first

    private fun score(source: SpotifyImportedTrack, candidate: SongInfo): Double? {
        val sourceTitle = normalize(source.title)
        val candidateTitle = normalize(candidate.title)
        if (sourceTitle.isBlank() || candidateTitle.isBlank()) return null

        val sourceVersions = versionTerms(sourceTitle)
        val candidateVersions = versionTerms(candidateTitle)
        if (candidateVersions.any { it !in sourceVersions }) return null

        val titleEdit = similarity(sourceTitle, candidateTitle)
        val sourceTokens = sourceTitle.split(' ').filter(String::isNotBlank).toSet()
        val candidateTokens = candidateTitle.split(' ').filter(String::isNotBlank).toSet()
        val tokenCoverage = sourceTokens.intersect(candidateTokens).size.toDouble() / sourceTokens.size.coerceAtLeast(1)
        val titleScore = titleEdit * 0.6 + tokenCoverage * 0.4
        val artistTokens = normalize(source.artists).split(' ').filter { it.length > 1 }.toSet()
        val candidateArtistTokens = normalize(candidate.artist).split(' ').filter { it.length > 1 }.toSet()
        val artistOverlap = artistTokens.intersect(candidateArtistTokens).size.toDouble() / artistTokens.size.coerceAtLeast(1)
        val durationDelta = if (source.durationSeconds > 0 && candidate.durationSeconds > 0) {
            kotlin.math.abs(source.durationSeconds - candidate.durationSeconds)
        } else null
        val durationClose = durationDelta != null && durationDelta <= maxOf(8L, source.durationSeconds / 30L)

        if (titleScore < 0.75) return null
        if (artistOverlap < 0.15 && titleScore < 0.95) return null

        val durationScore = when {
            durationDelta == null -> 0.5
            durationClose -> 1.0 - durationDelta.toDouble() / maxOf(8L, source.durationSeconds / 30L)
            else -> (1.0 - durationDelta.toDouble() / maxOf(18L, source.durationSeconds / 8L)).coerceAtLeast(0.0)
        }
        return titleScore * 0.72 + durationScore * 0.18 + artistOverlap * 0.10
    }

    private fun normalize(value: String): String = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .replace(Regex("\\b(feat|featuring|ft)\\b.*$"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private fun versionTerms(title: String): Set<String> = distinctVersionTerms.filterTo(mutableSetOf()) { term ->
        Regex("\\b${Regex.escape(term)}\\b").containsMatchIn(title)
    }

    private fun similarity(a: String, b: String): Double {
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
        return 1.0 - previous[b.length].toDouble() / maxOf(a.length, b.length).coerceAtLeast(1)
    }
}

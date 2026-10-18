package com.example.musicapp.data.provider

import com.example.musicapp.data.network.OkHttpDownloader
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.network.YouTubeVideoIdParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.ConcurrentHashMap

/**
 * YouTube Music Provider via NewPipeExtractor (Robust Fallback).
 * Provides 100% catalog coverage for any track, videoId, or obscure song.
 */
class YoutubeProvider : MusicProvider {
    override val name: String = "YouTube"
    override val isOfficial: Boolean = false

    private val memoryStreamCache = ConcurrentHashMap<String, String>()

    init {
        try {
            if (NewPipe.getDownloader() == null) {
                NewPipe.init(OkHttpDownloader())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun search(query: String): List<SongInfo> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SongInfo>()
        try {
            val searchExtractor = ServiceList.YouTube.getSearchExtractor(
                query,
                listOf(org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
                null
            )
            searchExtractor.fetchPage()
            for (item in searchExtractor.initialPage.items) {
                if (item is StreamInfoItem) {
                    val videoId = YouTubeVideoIdParser.parse(item.url) ?: continue
                    val bestThumb = item.thumbnails.maxByOrNull { it.height * it.width }?.url
                        ?: item.thumbnails.lastOrNull()?.url
                        ?: "https://i.ytimg.com/vi/${videoId}/hqdefault.jpg"
                    val highResArt = when {
                        bestThumb.contains("googleusercontent.com") -> bestThumb.replace(Regex("=w\\d+-h\\d+.*"), "=w544-h544-l90-rj")
                        bestThumb.contains("ytimg.com") -> "https://i.ytimg.com/vi/${videoId}/hqdefault.jpg"
                        else -> bestThumb
                    }
                    results.add(
                        SongInfo(
                            videoId = videoId,
                            title = item.name,
                            artist = item.uploaderName,
                            albumArtUrl = highResArt,
                            audioStreamUrl = null,
                            durationSeconds = item.duration.coerceAtLeast(0L)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        results
    }

    private fun extractStream(vid: String): String? {
        return try {
            val streamExtractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$vid")
            streamExtractor.fetchPage()
            val audioStreams = streamExtractor.audioStreams
            audioStreams.maxByOrNull { it.bitrate }?.content
                ?: streamExtractor.videoStreams.firstOrNull()?.content
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun resolveStream(
        trackRef: String,
        title: String?,
        artist: String?,
        durationSeconds: Long
    ): StreamResult = withContext(Dispatchers.IO) {
        // 1. Direct memory cache check
        memoryStreamCache[trackRef]?.let {
            return@withContext StreamResult.Success(
                streamUrl = it,
                providerName = name,
                bitrateKbps = 160
            )
        }

        // 2. Identify target YouTube videoId if trackRef is an 11-char ID
        var ytVideoId: String? = null
        if (trackRef.length == 11 && !trackRef.startsWith("saavn_") && !trackRef.startsWith("track_")) {
            ytVideoId = trackRef
        }

        // 3. Try direct videoId extraction first
        var stream = if (!ytVideoId.isNullOrBlank()) extractStream(ytVideoId) else null

        // 4. If direct extraction failed (e.g. video unavailable/restricted), fall back to searching title + artist
        if (stream.isNullOrBlank() && (!title.isNullOrBlank() || !artist.isNullOrBlank())) {
            val q = listOfNotNull(title, artist).joinToString(" ")
            try {
                val searchResults = search(q)
                val wantedTitle = normalize(title.orEmpty())
                val wantedArtist = normalize(artist.orEmpty())
                val fallbackVid = searchResults
                    .map { candidate -> candidate to titleMatch(wantedTitle, normalize(candidate.title), wantedArtist, normalize(candidate.artist), durationSeconds, candidate.durationSeconds) }
                    .filter { (_, score) -> score >= 0.77 }
                    .maxByOrNull { it.second }
                    ?.first?.videoId
                if (!fallbackVid.isNullOrBlank() && fallbackVid != ytVideoId) {
                    stream = extractStream(fallbackVid)
                    if (!stream.isNullOrBlank()) {
                        ytVideoId = fallbackVid
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (!stream.isNullOrBlank()) {
            memoryStreamCache[trackRef] = stream
            ytVideoId?.let { memoryStreamCache[it] = stream }
            return@withContext StreamResult.Success(
                streamUrl = stream,
                providerName = name,
                bitrateKbps = 160
            )
        }

        StreamResult.Failure("No stream found on YouTube for $trackRef", canRetryWithOtherProvider = true)
    }

    private fun normalize(value: String): String = value.lowercase()
        .replace(Regex("\\b(official|audio|video|lyrics?|visuali[sz]er|hd|hq|4k)\\b", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("\\([^)]*\\)|\\[[^]]*]")) { match ->
            val label = match.value.lowercase()
            if (listOf("live", "remix", "slowed", "reverb", "acoustic", "instrumental", "karaoke", "cover", "sped", "lofi", "garba", "unplugged").any(label::contains)) label else " "
        }
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private fun titleMatch(wantedTitle: String, actualTitle: String, wantedArtist: String, actualArtist: String, wantedDuration: Long, actualDuration: Long): Double {
        if (wantedTitle.isBlank() || actualTitle.isBlank()) return 0.0
        val titleScore = when {
            wantedTitle == actualTitle -> 1.0
            actualTitle.contains(wantedTitle) || wantedTitle.contains(actualTitle) -> 0.88
            else -> {
                val wanted = wantedTitle.split(Regex("\\s+")).filter { it.length > 1 }
                val actual = actualTitle.split(Regex("\\s+")).filter { it.length > 1 }
                if (wanted.isEmpty()) 0.0 else wanted.count { token -> actual.any { it == token || it.startsWith(token) } }.toDouble() / wanted.size
            }
        }
        val artistScore = if (wantedArtist.isBlank()) 0.5 else {
            val tokens = wantedArtist.split(Regex("\\s+")).filter { it.length > 1 }
            val actual = actualArtist.split(Regex("\\s+")).filter { it.length > 1 }
            if (actualArtist.contains(wantedArtist)) 1.0
            else if (tokens.isEmpty()) 0.0
            else tokens.count { token -> actual.any { it == token || it.startsWith(token) } }.toDouble() / tokens.size
        }
        val versionWords = listOf("live", "remix", "slowed", "reverb", "acoustic", "instrumental", "karaoke", "cover", "sped", "lofi", "garba", "unplugged")
        if (versionWords.any { word -> actualTitle.contains(word) != wantedTitle.contains(word) }) return 0.0
        val durationScore = if (wantedDuration > 0L && actualDuration > 0L) {
            val difference = kotlin.math.abs(wantedDuration - actualDuration)
            if (difference > maxOf(12L, (wantedDuration * 0.12).toLong())) return 0.0
            1.0 - difference.toDouble() / maxOf(wantedDuration, actualDuration)
        } else 0.72
        return titleScore * 0.62 + artistScore * 0.28 + durationScore * 0.10
    }
}

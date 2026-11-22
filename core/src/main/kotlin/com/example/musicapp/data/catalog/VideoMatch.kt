package com.example.musicapp.data.catalog

import com.example.musicapp.data.network.OkHttpDownloader
import com.example.musicapp.data.network.SongInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import java.util.concurrent.ConcurrentHashMap

/**
 * Music videos for the player's Song / Video switch. Episodes from YouTube channels are their
 * own video. For songs, the official music video is looked up on YouTube Music and accepted
 * only when title and artist clearly match.
 */
object VideoMatch {
    private val cache = ConcurrentHashMap<String, String>()
    private const val NONE = "-"

    init {
        runCatching { if (NewPipe.getDownloader() == null) NewPipe.init(OkHttpDownloader()) }
    }

    fun isYouTubeId(id: String): Boolean = id.length == 11 && Regex("[A-Za-z0-9_-]{11}").matches(id)

    /** YouTube video id to show for [song], or null when there's no video. */
    suspend fun find(song: SongInfo): String? {
        if (song.isEpisode) return song.videoId.takeIf(::isYouTubeId)
        cache[song.videoId]?.let { return it.takeIf { v -> v != NONE } }
        val primaryArtist = song.artist.split(",", "&").map { it.trim() }.filter { it.isNotBlank() }
        val candidates = runCatching { YouTubeMusic.searchVideos("${song.title} ${primaryArtist.take(2).joinToString(" ")}", 8) }.getOrDefault(emptyList())
        val wantedTitle = normalize(song.title)
        val best = candidates.map { it to score(wantedTitle, primaryArtist, song.durationSeconds, it) }
            .filter { it.second >= 0.62 }
            .maxByOrNull { it.second }?.first
        val id = best?.videoId
        cache[song.videoId] = id ?: NONE
        return id
    }

    private fun score(wantedTitle: String, artists: List<String>, duration: Long, candidate: SongInfo): Double {
        val title = normalize(candidate.title)
        if (wantedTitle.isBlank() || title.isBlank()) return 0.0
        val versionWords = listOf("cover", "karaoke", "remix", "lofi", "slowed", "reverb", "instrumental", "8d", "reaction")
        if (versionWords.any { title.contains(it) && !wantedTitle.contains(it) }) return 0.0
        val titleScore = when {
            title == wantedTitle -> 1.0
            title.contains(wantedTitle) -> 0.9
            else -> {
                val words = wantedTitle.split(" ").filter { it.length > 1 }
                if (words.isEmpty()) 0.0 else words.count { title.contains(it) }.toDouble() / words.size
            }
        }
        val candidateArtist = (candidate.artist + " " + candidate.title).lowercase()
        // Share of credited artists the video names: the Atif Aslam video beats the Arijit Singh
        // reprise even though both credit Pritam.
        val artistScore = if (artists.isEmpty()) 0.5 else artists.count { a ->
            a.lowercase().split(" ").filter { it.length > 1 }.all { candidateArtist.contains(it) }
        }.toDouble() / artists.size
        // Music videos often run longer than the song (intros, outros), so duration is a soft signal.
        val durationScore = if (duration > 0 && candidate.durationSeconds > 0) {
            val diff = kotlin.math.abs(candidate.durationSeconds - duration)
            when {
                diff <= 20 -> 1.0
                diff <= 90 -> 0.6
                else -> 0.1
            }
        } else 0.6
        return titleScore * 0.55 + artistScore * 0.3 + durationScore * 0.15
    }

    private fun normalize(s: String) = s.lowercase()
        .replace(Regex("\\((?:from|official|lyric|full|video|audio)[^)]*\\)|\\[[^]]*]"), " ")
        .replace(Regex("\\b(official|music|video|lyrical|lyrics?|full|song|hd|4k|audio)\\b"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    /**
     * Video-only stream (MP4/H.264, so every phone decodes it in hardware) no taller than
     * [maxHeight]. Audio comes from the same video separately, so they stay in sync.
     */
    suspend fun videoStreamUrl(videoId: String, maxHeight: Int): String? = withContext(Dispatchers.IO) {
        runCatching {
            val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
            extractor.fetchPage()
            val videoOnly = extractor.videoOnlyStreams
                .filter { it.format == MediaFormat.MPEG_4 && it.height in 1..maxHeight && it.codec?.startsWith("avc") != false }
                .maxByOrNull { it.height }
            videoOnly?.content ?: extractor.videoStreams.filter { it.height <= maxHeight }.maxByOrNull { it.height }?.content
        }.getOrNull()
    }
}

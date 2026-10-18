package com.example.musicapp.data.provider

import com.example.musicapp.data.network.SongInfo
import java.text.Normalizer

/**
 * Merges provider copies only when metadata strongly indicates the same recording.
 * Duration is a hard gate so unknown-length songs and alternate edits are preserved.
 */
internal object SearchResultDeduplicator {
    private val artistNoise = setOf("topic", "official", "vevo", "records", "music")

    // These words identify a materially different performance/arrangement and must match
    // on both results before two tracks can be collapsed, including Garba pace/style tags.
    private val versionMarkers = listOf(
        Regex("\\bslow(?:ed)?(?:[ -]?(?:version|mix|reverb|garba))?\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(?:fast[ -]?(?:version|mix|garba)|speed[ -]?up|sped[ -]?up|high[ -]?speed)\\b", RegexOption.IGNORE_CASE),
        Regex("\\bacoustic\\b|\\bunplugged\\b|\\blive\\b|\\bremix\\b|\\breverb\\b|\\blo[ -]?fi\\b|\\binstrumental\\b|\\bkaraoke\\b|\\bcover\\b|\\bmashup\\b|\\breprise\\b|\\bextended(?:[ -]?mix)?\\b|\\bradio[ -]?edit\\b|\\bgarba\\b|\\bdandiya\\b|\\bdodhiya\\b", RegexOption.IGNORE_CASE),
        Regex("ધીમું|ધીમા|ધીમી|ઝડપી|ઝડપથી|ગરબા|દાંડિયા")
    )

    fun deduplicate(songs: List<SongInfo>): List<SongInfo> {
        val ranked = songs.sortedWith(
            compareByDescending<SongInfo> { it.popularityScore }
                .thenByDescending { !it.audioStreamUrl.isNullOrBlank() }
        )
        val kept = mutableListOf<SongInfo>()
        for (song in ranked) {
            if (kept.any { sameRecording(it, song) }) continue
            kept += song
        }
        return kept
    }

    internal fun sameRecording(first: SongInfo, second: SongInfo): Boolean {
        // Do not guess when a provider omitted length, and allow only small provider rounding.
        if (first.durationSeconds <= 0L || second.durationSeconds <= 0L) return false
        if (kotlin.math.abs(first.durationSeconds - second.durationSeconds) > 2L) return false

        val firstVariants = variantMarkers(first)
        val secondVariants = variantMarkers(second)
        if (firstVariants != secondVariants) return false

        val titleScore = similarity(canonicalTitle(first.title), canonicalTitle(second.title))
        if (titleScore < 0.90) return false
        return artistSimilarity(first.artist, second.artist) >= 0.85
    }

    private fun variantMarkers(song: SongInfo): Set<String> {
        val value = "${song.title} ${song.albumName}"
        return versionMarkers.flatMap { regex ->
            regex.findAll(value).map { it.value.lowercase() }.toList()
        }.toSet()
    }

    private fun canonicalTitle(value: String): String {
        val plain = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase()
            .replace(Regex("\\b(?:official|audio|video|lyrics?|visualizer|hd|4k)\\b"), " ")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
        return plain
    }

    private fun artistSimilarity(first: String, second: String): Double {
        val firstTokens = artistTokens(first)
        val secondTokens = artistTokens(second)
        if (firstTokens.isEmpty() || secondTokens.isEmpty()) return 0.0
        val shared = firstTokens.intersect(secondTokens).size
        return shared.toDouble() / maxOf(firstTokens.size, secondTokens.size)
    }

    private fun artistTokens(value: String): Set<String> = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase()
        .replace(Regex("\\b(?:feat(?:uring)?|ft|with)\\b"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .split(Regex("\\s+"))
        .filter { it.length > 1 && it !in artistNoise }
        .toSet()

    private fun similarity(first: String, second: String): Double {
        if (first.isBlank() || second.isBlank()) return 0.0
        if (first == second) return 1.0
        val previous = IntArray(second.length + 1) { it }
        for (i in first.indices) {
            var diagonal = previous[0]
            previous[0] = i + 1
            for (j in second.indices) {
                val old = previous[j + 1]
                previous[j + 1] = minOf(
                    previous[j + 1] + 1,
                    previous[j] + 1,
                    diagonal + if (first[i] == second[j]) 0 else 1
                )
                diagonal = old
            }
        }
        return 1.0 - previous[second.length].toDouble() / maxOf(first.length, second.length)
    }
}

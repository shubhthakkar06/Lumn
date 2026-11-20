package com.example.musicapp.data.provider

import com.example.musicapp.data.network.SongInfo

/**
 * Shared "is this the same recording?" check used before any provider plays a search hit in
 * place of the requested song. Every fallback must pass it, so a missing song fails visibly
 * instead of quietly playing something unrelated.
 */
internal object TrackMatching {
    /** Minimum [confidence] for a search hit to stand in for the requested track. */
    const val MIN_CONFIDENCE = 0.76

    private val versionWords = listOf(
        "live", "remix", "slowed", "reverb", "acoustic", "instrumental", "karaoke",
        "cover", "sped", "lofi", "lo fi", "garba", "unplugged"
    )

    fun confidence(queryTitle: String, queryArtist: String, queryDurationSeconds: Long, candidate: SongInfo): Double {
        val wantedTitle = normalize(queryTitle)
        val actualTitle = normalize(candidate.title)
        val wantedArtist = normalize(queryArtist)
        val actualArtist = normalize(candidate.artist)
        if (wantedTitle.isBlank() || actualTitle.isBlank()) return 0.0

        val titleScore = when {
            actualTitle == wantedTitle -> 1.0
            actualTitle.contains(wantedTitle) || wantedTitle.contains(actualTitle) -> 0.86
            else -> similarity(wantedTitle, actualTitle)
        }
        val artistScore = when {
            wantedArtist.isBlank() -> 0.72
            actualArtist == wantedArtist -> 1.0
            actualArtist.contains(wantedArtist) || wantedArtist.contains(actualArtist) -> 0.9
            else -> tokenOverlap(wantedArtist, actualArtist)
        }
        if (artistScore < 0.45) return 0.0

        // Version words carry meaning (live/remix/slowed/garba edits): both sides must agree.
        val wantedVersions = versionWords.filter { wantedTitle.contains(it) }
        val candidateVersions = versionWords.filter { actualTitle.contains(it) }
        if (candidateVersions.any { it !in wantedVersions }) return 0.0
        if (wantedVersions.any { it !in candidateVersions }) return 0.0

        val candidateDuration = candidate.durationSeconds
        val durationScore = if (queryDurationSeconds > 0 && candidateDuration > 0) {
            val difference = kotlin.math.abs(queryDurationSeconds - candidateDuration)
            if (difference > maxOf(12L, (queryDurationSeconds * 0.12).toLong())) return 0.0
            1.0 - (difference.toDouble() / maxOf(queryDurationSeconds, candidateDuration).toDouble())
        } else 0.72
        return titleScore * 0.62 + artistScore * 0.28 + durationScore * 0.10
    }

    /** Best playable hit that passes [MIN_CONFIDENCE], or null. */
    fun bestPlayable(title: String?, artist: String?, durationSeconds: Long, hits: List<SongInfo>): SongInfo? =
        hits.asSequence()
            .filter { !it.audioStreamUrl.isNullOrBlank() }
            .map { it to confidence(title.orEmpty(), artist.orEmpty(), durationSeconds, it) }
            .filter { (_, score) -> score >= MIN_CONFIDENCE }
            .maxByOrNull { (candidate, score) -> score + kotlin.math.ln1p(candidate.popularityScore.toDouble()) * 0.001 }
            ?.first

    fun normalize(value: String): String = value.lowercase()
        .replace(Regex("\\b(official|audio|video|lyrics?|lyric video|visuali[sz]er|hd|hq|4k)\\b", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("\\([^)]*\\)|\\[[^]]*]")) { match ->
            val label = match.value.lowercase()
            if (versionWords.any(label::contains)) label else " "
        }
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private fun tokenOverlap(a: String, b: String): Double {
        val wanted = a.split(Regex("\\s+")).filter { it.length > 1 }.toSet()
        val found = b.split(Regex("\\s+")).filter { it.length > 1 }.toSet()
        if (wanted.isEmpty() || found.isEmpty()) return 0.0
        return wanted.count { token -> found.any { it == token || it.startsWith(token) || token.startsWith(it) } }.toDouble() / wanted.size
    }

    private fun similarity(a: String, b: String): Double {
        if (a.isBlank() || b.isBlank()) return 0.0
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
        return 1.0 - previous[b.length].toDouble() / maxOf(a.length, b.length)
    }
}

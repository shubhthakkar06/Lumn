package com.example.musicapp.desktop.data

import com.example.musicapp.desktop.model.DesktopSong
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max

object DesktopTrackMatcher {
    private val distinctVersionTerms = setOf(
        "remix", "live", "acoustic", "instrumental", "karaoke", "cover",
        "sped", "slowed", "garba", "unplugged", "remaster", "edit", "demo", "reverb"
    )

    fun cleanTitleForSearch(title: String): String {
        return title
            .replace(Regex("\\(with [^)]+\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(feat\\.?[^)]+\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[feat\\.?[^]]+\\]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(from [^)]+\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[from [^]]+\\]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(official[^)]*\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[official[^]]*\\]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(audio[^)]*\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(video[^)]*\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("- remastered.*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("- original.*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun cleanArtistForSearch(artist: String): String {
        return artist
            .split(",", "&", "feat.", "ft.", "•", "/")
            .firstOrNull()
            ?.trim()
            ?.ifBlank { artist } ?: artist
    }

    fun bestMatch(target: DesktopSong, candidates: List<DesktopSong>): DesktopSong? {
        if (candidates.isEmpty()) return null

        val scored = candidates.mapNotNull { candidate ->
            val s = score(target, candidate)
            if (s != null && s >= 0.65) candidate to s else null
        }

        return scored.maxWithOrNull(
            compareBy<Pair<DesktopSong, Double>> { it.second }
                .thenBy { !it.first.audioUrl.isNullOrBlank() }
        )?.first
    }

    private fun score(target: DesktopSong, candidate: DesktopSong): Double? {
        val targetTitle = normalize(target.title)
        val candidateTitle = normalize(candidate.title)
        if (targetTitle.isBlank() || candidateTitle.isBlank()) return null

        val targetVersions = versionTerms(targetTitle)
        val candidateVersions = versionTerms(candidateTitle)

        // If candidate is a karaoke/cover/remix but the target song is NOT, reject!
        val unwantedVersions = candidateVersions.filter { it !in targetVersions }
        if (unwantedVersions.isNotEmpty()) {
            if ("karaoke" in unwantedVersions || "cover" in unwantedVersions || "instrumental" in unwantedVersions) {
                return null
            }
            if ("remix" in unwantedVersions && "remix" !in targetVersions) {
                return null
            }
        }

        // Title edit distance and token coverage
        val titleEdit = similarity(targetTitle, candidateTitle)
        val targetTokens = targetTitle.split(' ').filter { it.isNotBlank() && it.length > 1 }.toSet()
        val candidateTokens = candidateTitle.split(' ').filter { it.isNotBlank() && it.length > 1 }.toSet()
        val tokenCoverage = if (targetTokens.isNotEmpty()) {
            targetTokens.intersect(candidateTokens).size.toDouble() / targetTokens.size.toDouble()
        } else 0.5
        val titleScore = titleEdit * 0.6 + tokenCoverage * 0.4

        // Artist overlap
        val targetArtistTokens = normalize(target.artist).split(' ').filter { it.length > 2 }.toSet()
        val candidateArtistTokens = normalize(candidate.artist).split(' ').filter { it.length > 2 }.toSet()
        val artistOverlap = if (targetArtistTokens.isNotEmpty() && candidateArtistTokens.isNotEmpty()) {
            targetArtistTokens.intersect(candidateArtistTokens).size.toDouble() / targetArtistTokens.size.toDouble()
        } else 0.5

        // Strict rejection rules:
        // 1. If artist has zero overlap and title score is below 0.88, reject.
        if (targetArtistTokens.isNotEmpty() && artistOverlap < 0.15 && titleScore < 0.88) {
            return null
        }
        // 2. If title similarity is too low, reject.
        if (titleScore < 0.60) {
            return null
        }

        // Duration comparison (if target has known duration > 0)
        val durationScore = if (target.durationSeconds > 0 && candidate.durationSeconds > 0) {
            val delta = abs(target.durationSeconds - candidate.durationSeconds)
            val maxTolerance = max(15L, (target.durationSeconds * 0.15).toLong())
            if (delta <= maxTolerance) {
                1.0 - (delta.toDouble() / maxTolerance.toDouble()) * 0.3
            } else if (delta > max(35L, (target.durationSeconds * 0.35).toLong()) && titleScore < 0.90) {
                // Duration is drastically different and title isn't an exact match -> likely wrong song!
                return null
            } else {
                0.4
            }
        } else {
            0.7
        }

        return titleScore * 0.60 + artistOverlap * 0.25 + durationScore * 0.15
    }

    private fun normalize(value: String): String {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("\\b(feat|featuring|ft|with|from)\\b.*$"), " ")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
    }

    private fun versionTerms(title: String): Set<String> {
        return distinctVersionTerms.filterTo(mutableSetOf()) { term ->
            Regex("\\b${Regex.escape(term)}\\b").containsMatchIn(title)
        }
    }

    private fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
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
        return 1.0 - previous[b.length].toDouble() / max(a.length, b.length).coerceAtLeast(1)
    }
}

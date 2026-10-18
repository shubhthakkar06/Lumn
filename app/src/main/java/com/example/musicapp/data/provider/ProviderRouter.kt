package com.example.musicapp.data.provider

import com.example.musicapp.data.network.SongInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

class ProviderRouter(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build(),
    providers: List<MusicProvider> = emptyList()
) {
    private val saavnProvider = SaavnProvider(httpClient)
    private val youtubeProvider = YoutubeProvider()
    private val audiusProvider = AudiusProvider(httpClient)
    private val jamendoProvider = JamendoProvider(httpClient)

    private val allProviders: List<MusicProvider> = if (providers.isNotEmpty()) providers else listOf(
        saavnProvider,
        youtubeProvider,
        audiusProvider,
        jamendoProvider
    )

    // Sliding window of last 20 outcomes per provider: true = success, false = failure
    private val providerHistory = ConcurrentHashMap<String, ConcurrentLinkedQueue<Boolean>>()

    // Cached stream URLs with expiry timestamp (Epoch ms)
    data class CachedStream(
        val url: String,
        val providerName: String,
        val expiresAtMs: Long
    )
    private val streamCache = ConcurrentHashMap<String, CachedStream>()

    init {
        allProviders.forEach { p ->
            providerHistory[p.name] = ConcurrentLinkedQueue()
        }
    }

    private fun recordOutcome(providerName: String, success: Boolean) {
        val history = providerHistory.getOrPut(providerName) { ConcurrentLinkedQueue() }
        history.add(success)
        while (history.size > 20) {
            history.poll()
        }
    }

    fun getFailureRate(providerName: String): Float {
        val history = providerHistory[providerName] ?: return 0f
        if (history.isEmpty()) return 0f
        val failures = history.count { !it }
        return failures.toFloat() / history.size.toFloat()
    }

    /**
     * Orders providers: healthy providers first (<50% failure rate),
     * demoted providers (>50% failure rate) placed at the end.
     */
    private fun getPrioritizedProviders(): List<MusicProvider> {
        return allProviders.sortedBy { provider ->
            val rate = getFailureRate(provider.name)
            if (rate > 0.5f) 1 else 0
        }
    }

    fun normalize(text: String?): String {
        if (text == null) return ""
        return text.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
    }

    private fun editSimilarity(a: String, b: String): Double {
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

    private fun relevance(query: String, song: SongInfo): Int {
        val q = normalize(query)
        val title = normalize(song.title)
        val artist = normalize(song.artist)
        if (q.isBlank()) return 0
        var score = when {
            title == q -> 100
            title.startsWith(q) -> 85
            title.contains(q) -> 75
            artist == q -> 68
            artist.contains(q) -> 58
            else -> 0
        }
        val queryTokens = q.split(Regex("\\s+")).filter { it.length > 1 }
        val titleTokens = title.split(Regex("\\s+")).filter { it.length > 1 }
        val artistTokens = artist.split(Regex("\\s+")).filter { it.length > 1 }
        if (queryTokens.isNotEmpty()) {
            val matched = queryTokens.count { token ->
                (titleTokens + artistTokens).any { candidate ->
                    candidate == token || candidate.startsWith(token) || token.startsWith(candidate) || editSimilarity(token, candidate) >= 0.78
                }
            }
            score += (matched * 60) / queryTokens.size
            // Whole-title edit distance catches spelling variants such as Shubhaarambh.
            val bestTitleToken = queryTokens.maxOfOrNull { token ->
                titleTokens.maxOfOrNull { candidate -> editSimilarity(token, candidate) } ?: 0.0
            } ?: 0.0
            score += (bestTitleToken * 12).toInt()
        }
        return score
    }

    suspend fun search(query: String): List<SongInfo> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        // Light spelling normalization helps provider searches that distinguish repeated
        // vowels (for example, Shubharambh / Shubhaarambh) while retaining original query.
        val normalizedVowels = query.replace(Regex("([aeiou])\\1+", RegexOption.IGNORE_CASE), "$1")
        val queryVariants = listOf(query, normalizedVowels).distinct()
        val resultsByProvider = coroutineScope {
            getPrioritizedProviders().map { provider ->
                async {
                    val timeoutMs = if (provider.name == "YouTube") 5_000L else 3_000L
                    val results = queryVariants.map { variant ->
                        try {
                            withTimeoutOrNull(timeoutMs) { provider.search(variant) }.orEmpty()
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }.flatten()
                    recordOutcome(provider.name, results.isNotEmpty())
                    provider to results
                }
            }.awaitAll()
        }

        // Keep provider order as a tie-breaker, but prefer the most relevant title/artist
        // matches. Providers often return overlapping catalogs and duplicate editions.
        val ranked = resultsByProvider.flatMap { it.second }
            .sortedWith(
                compareByDescending<SongInfo> { relevance(query, it) }
                    .thenByDescending { it.popularityScore }
                    // When two providers return the same recording, keep a directly
                    // playable catalog entry ahead of a URL that still needs resolving.
                    .thenByDescending { !it.audioStreamUrl.isNullOrBlank() }
            )
        val uniqueIds = LinkedHashMap<String, SongInfo>()
        ranked.forEach { song -> uniqueIds.putIfAbsent(song.videoId, song) }
        SearchResultDeduplicator.deduplicate(uniqueIds.values.toList()).take(40)
    }

    suspend fun resolveStream(
        trackRef: String,
        title: String? = null,
        artist: String? = null,
        durationSeconds: Long = 0L,
        excludedProviders: Set<String> = emptySet()
    ): StreamResult = withContext(Dispatchers.IO) {
        // Keep stream caches ID-specific. Two recordings can share title/artist text
        // while mapping to different language versions or remasters.
        val cacheKey = "$trackRef:${normalize(title)}:${normalize(artist)}"

        // 1. Check valid cache
        streamCache[cacheKey]?.let { cached ->
            // A retry may explicitly exclude the provider that supplied this cached URL.
            // Returning it here defeats fallback and replays the same broken stream.
            if (System.currentTimeMillis() < cached.expiresAtMs && cached.providerName !in excludedProviders) {
                return@withContext StreamResult.Success(
                    streamUrl = cached.url,
                    providerName = cached.providerName
                )
            } else {
                streamCache.remove(cacheKey)
            }
        }

        // 2. Try prioritized providers with adaptive timeout (6s for YouTube, 3s for CDN)
        val prioritized = getPrioritizedProviders()
        val sourceOrdered = when {
            trackRef.startsWith("saavn_") -> prioritized.sortedBy { if (it.name == "Saavn") 0 else 1 }
            trackRef.length == 11 && !trackRef.startsWith("track_") -> prioritized.sortedBy { if (it.name == "YouTube") 0 else 1 }
            else -> prioritized
        }
        val providers = sourceOrdered.filterNot { it.name in excludedProviders }
        var lastFailureReason = "No provider could resolve track $trackRef"

        for (provider in providers) {
            try {
                val timeoutMs = if (provider.name == "YouTube") 6000L else 3000L
                val result = withTimeoutOrNull(timeoutMs) {
                    provider.resolveStream(trackRef, title, artist, durationSeconds)
                }

                if (result is StreamResult.Success) {
                    recordOutcome(provider.name, true)
                    // Cache stream for 30 minutes
                    val expiry = System.currentTimeMillis() + (30 * 60 * 1000L)
                    streamCache[cacheKey] = CachedStream(
                        url = result.streamUrl,
                        providerName = provider.name,
                        expiresAtMs = expiry
                    )
                    return@withContext result
                } else if (result is StreamResult.Failure) {
                    recordOutcome(provider.name, false)
                    lastFailureReason = "${provider.name}: ${result.reason}"
                } else {
                    // Timed out (null)
                    recordOutcome(provider.name, false)
                    lastFailureReason = "${provider.name} timed out (>${timeoutMs}ms)"
                }
            } catch (e: Exception) {
                recordOutcome(provider.name, false)
                lastFailureReason = "${provider.name} exception: ${e.message}"
            }
        }

        StreamResult.Failure(lastFailureReason, canRetryWithOtherProvider = false)
    }

    fun invalidateCache(trackRef: String, title: String? = null, artist: String? = null) {
        val cacheKey = "$trackRef:${normalize(title)}:${normalize(artist)}"
        streamCache.remove(cacheKey)
        allProviders.forEach { it.invalidateStreamCache(trackRef, title, artist) }
    }
}

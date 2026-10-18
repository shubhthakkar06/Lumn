package com.example.musicapp.contract

import com.example.musicapp.data.provider.AudiusProvider
import com.example.musicapp.data.provider.ProviderRouter
import com.example.musicapp.data.provider.SaavnProvider
import com.example.musicapp.data.provider.StreamResult
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class MusicSourceContractTest {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val router = ProviderRouter(httpClient)
    private val saavn = SaavnProvider(httpClient)
    private val audius = AudiusProvider(httpClient)

    data class TestQuery(
        val category: String,
        val title: String,
        val artist: String? = null
    )

    private val sampleQueries = listOf(
        // 10 Bollywood
        TestQuery("Bollywood", "Tum Hi Ho", "Arijit Singh"),
        TestQuery("Bollywood", "Kesariya", "Pritam"),
        TestQuery("Bollywood", "Apna Bana Le", "Arijit Singh"),
        TestQuery("Bollywood", "Channa Mereya", "Arijit Singh"),
        TestQuery("Bollywood", "Kalank", "Arijit Singh"),
        TestQuery("Bollywood", "Pasoori", "Ali Sethi"),
        TestQuery("Bollywood", "Raataan Lambiyan", "Jubin Nautiyal"),
        TestQuery("Bollywood", "Agar Tum Saath Ho", "Alka Yagnik"),
        TestQuery("Bollywood", "Naina", "Diljit Dosanjh"),
        TestQuery("Bollywood", "Shayad", "Pritam"),

        // 10 International
        TestQuery("International", "Blinding Lights", "The Weeknd"),
        TestQuery("International", "Shape of You", "Ed Sheeran"),
        TestQuery("International", "Starboy", "The Weeknd"),
        TestQuery("International", "Hymn for the Weekend", "Coldplay"),
        TestQuery("International", "Viva La Vida", "Coldplay"),
        TestQuery("International", "Levitating", "Dua Lipa"),
        TestQuery("International", "As It Was", "Harry Styles"),
        TestQuery("International", "Stay", "The Kid LAROI"),
        TestQuery("International", "Believer", "Imagine Dragons"),
        TestQuery("International", "Bad Guy", "Billie Eilish"),

        // 10 Indie / Electronic / Free Stream
        TestQuery("Indie", "Summer Breeze", null),
        TestQuery("Indie", "Chillhop", null),
        TestQuery("Indie", "Cyberpunk", null),
        TestQuery("Indie", "Acoustic Sunset", null),
        TestQuery("Indie", "Lo-Fi Beats", null),
        TestQuery("Indie", "Midnight Drive", null),
        TestQuery("Indie", "Neon Dreams", null),
        TestQuery("Indie", "Solar", null),
        TestQuery("Indie", "Echoes", null),
        TestQuery("Indie", "Orbit", null)
    )

    data class ContractResult(
        val category: String,
        val query: String,
        val provider: String,
        val httpCode: Int,
        val contentType: String,
        val lengthBytes: Long,
        val passed: Boolean,
        val failureReason: String = ""
    )

    @Test
    fun test30MusicSourceContracts() = runBlocking {
        val results = mutableListOf<ContractResult>()

        for (q in sampleQueries) {
            val queryStr = listOfNotNull(q.title, q.artist).joinToString(" ")
            var resolvedResult: StreamResult = StreamResult.Failure("Init")
            var streamUrl: String? = null
            var providerName = "None"

            // 1. Resolve via ProviderRouter
            val searchResults = router.search(queryStr)
            val first = searchResults.firstOrNull()

            if (first != null) {
                resolvedResult = router.resolveStream(first.videoId, first.title, first.artist)
            } else {
                resolvedResult = router.resolveStream("track_${q.title}", q.title, q.artist)
            }

            if (resolvedResult is StreamResult.Success) {
                streamUrl = resolvedResult.streamUrl
                providerName = resolvedResult.providerName
            }

            if (streamUrl.isNullOrBlank()) {
                results.add(
                    ContractResult(
                        category = q.category,
                        query = queryStr,
                        provider = providerName,
                        httpCode = 0,
                        contentType = "none",
                        lengthBytes = 0,
                        passed = false,
                        failureReason = (resolvedResult as? StreamResult.Failure)?.reason ?: "Unresolved"
                    )
                )
                continue
            }

            // 2. Validate real HTTP stream
            var httpCode = 0
            var contentType = "unknown"
            var lengthBytes = 0L
            var passed = false
            var failureReason = ""

            try {
                val req = Request.Builder()
                    .url(streamUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile)")
                    .header("Range", "bytes=0-1023")
                    .build()

                httpClient.newCall(req).execute().use { response ->
                    httpCode = response.code
                    contentType = response.header("Content-Type") ?: "unknown"
                    lengthBytes = response.body?.contentLength() ?: 0L

                    val isAudio = contentType.contains("audio") ||
                            contentType.contains("video/mp4") ||
                            contentType.contains("application/octet-stream")

                    if ((httpCode in 200..206) && isAudio && (lengthBytes != 0L || response.isSuccessful)) {
                        passed = true
                    } else {
                        failureReason = "HTTP $httpCode, Content-Type: $contentType, Length: $lengthBytes"
                    }
                }
            } catch (e: Exception) {
                failureReason = e.message ?: "Exception"
            }

            results.add(
                ContractResult(
                    category = q.category,
                    query = queryStr,
                    provider = providerName,
                    httpCode = httpCode,
                    contentType = contentType,
                    lengthBytes = lengthBytes,
                    passed = passed,
                    failureReason = failureReason
                )
            )
        }

        // Print ASCII summary table
        println("\n" + "=".repeat(92))
        println(String.format("%-14s | %-26s | %-8s | %-4s | %-16s | %-6s", "CATEGORY", "QUERY", "PROVIDER", "HTTP", "CONTENT-TYPE", "STATUS"))
        println("-".repeat(92))
        for (r in results) {
            val shortQuery = if (r.query.length > 25) r.query.take(23) + ".." else r.query
            val shortType = if (r.contentType.length > 15) r.contentType.take(13) + ".." else r.contentType
            val status = if (r.passed) "PASS" else "FAIL"
            println(String.format("%-14s | %-26s | %-8s | %-4d | %-16s | %-6s", r.category, shortQuery, r.provider, r.httpCode, shortType, status))
        }
        println("=".repeat(92))

        val passedCount = results.count { it.passed }
        val totalCount = results.size
        val passRate = (passedCount.toFloat() / totalCount.toFloat()) * 100f
        println(String.format("MUSIC SOURCE CONTRACT SUMMARY: %d/%d passed (%.1f%%)", passedCount, totalCount, passRate))

        // Contract test requirement: high overall pass rate across multi-provider router
        assertTrue("Expected pass rate >= 70%, was $passRate%", passRate >= 70f)
    }

    @Test
    fun testDirectCatalogYoutubeResolution() = runBlocking {
        val ytProvider = com.example.musicapp.data.provider.YoutubeProvider()
        val testTracks = listOf(
            Triple("1G4isv_Fylg", "Sparks", "Coldplay"),
            Triple("dvgZkm1xWPE", "Viva La Vida", "Coldplay")
        )

        for ((vid, title, artist) in testTracks) {
            val res = ytProvider.resolveStream(vid, title, artist)
            if (res is StreamResult.Failure) {
                println("Failed to resolve $vid ($title): ${res.reason}")
            }
            assertTrue("Expected YouTube stream to resolve for $vid ($title). Reason: ${(res as? StreamResult.Failure)?.reason}", res is StreamResult.Success)
            val success = res as StreamResult.Success
            assertTrue("Expected non-blank stream URL", success.streamUrl.isNotBlank())
            println("Verified YouTube Stream for $title ($vid) -> ${success.streamUrl.take(60)}...")
        }
    }
}


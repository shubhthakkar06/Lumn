package com.example.musicapp.data.provider

import com.example.musicapp.data.network.SongInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Unofficial JioSaavn Music Provider (Best-effort).
 * Direct 320kbps Akamai CDN stream extraction.
 */
class SaavnProvider(
    private val httpClient: OkHttpClient = OkHttpClient()
) : MusicProvider {

    override val name: String = "Saavn"
    override val isOfficial: Boolean = false

    private val json = Json { ignoreUnknownKeys = true }
    private val memoryStreamCache = ConcurrentHashMap<String, String>()

    private fun unescapeHtml(text: String): String {
        return text.replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#039;", "'")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
    }

    fun decryptSaavnUrl(encryptedMediaUrl: String): String {
        return try {
            val key = "38346591".toByteArray(Charsets.UTF_8)
            val spec = SecretKeySpec(key, "DES")
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, spec)
            val decoded = try {
                Base64.getDecoder().decode(encryptedMediaUrl.trim())
            } catch (e: Exception) {
                // Fallback for Android legacy base64 if needed
                android.util.Base64.decode(encryptedMediaUrl.trim(), android.util.Base64.DEFAULT)
            }
            val decrypted = cipher.doFinal(decoded)
            val rawUrl = String(decrypted, Charsets.UTF_8)
            rawUrl.replace("_96.mp4", "_320.mp4")
                .replace("_160.mp4", "_320.mp4")
                .replace("_48.mp4", "_320.mp4")
        } catch (e: Exception) {
            ""
        }
    }

    override suspend fun search(query: String): List<SongInfo> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SongInfo>()
        val trimmed = query.trim()
        val isSatsangQuery = trimmed.contains("satsang", ignoreCase = true) ||
            trimmed.contains("baps", ignoreCase = true) ||
            trimmed.contains("swami", ignoreCase = true) ||
            trimmed.contains("katha", ignoreCase = true) ||
            trimmed.contains("patience", ignoreCase = true) ||
            trimmed.contains("episode", ignoreCase = true) ||
            trimmed.contains("podcast", ignoreCase = true)

        if (isSatsangQuery) {
            results.addAll(getBapsSatsangEpisodes().filter {
                it.title.contains(trimmed, ignoreCase = true) ||
                it.artist.contains(trimmed, ignoreCase = true) ||
                it.description.contains(trimmed, ignoreCase = true)
            })
        }
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.jiosaavn.com/api.php?__call=search.getResults&q=$encoded&_format=json&_marker=0&ctx=android&p=1&n=20"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile)")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
                val jsonStr = response.body!!.string()
                val root = json.parseToJsonElement(jsonStr) as? JsonObject
                val array = root?.get("results") as? JsonArray
                array?.forEach { element ->
                    val obj = element as? JsonObject ?: return@forEach
                    val id = obj["id"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                    val songName = obj["song"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                    val primaryArtists = obj["primary_artists"]?.let { (it as? JsonPrimitive)?.content }
                        ?: obj["singers"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                    val moreInfo = obj["more_info"] as? JsonObject
                    val albumName = (obj["album"] ?: moreInfo?.get("album"))
                        ?.let { (it as? JsonPrimitive)?.content }.orEmpty()
                    val durationSeconds = (obj["duration"] ?: moreInfo?.get("duration"))
                        ?.let { (it as? JsonPrimitive)?.content?.toLongOrNull() } ?: 0L
                    val rawImg = obj["image"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                    val highResImg = rawImg.replace("150x150.jpg", "500x500.jpg")
                        .replace("150x150.webp", "500x500.jpg")
                        .replace("50x50.jpg", "500x500.jpg")
                    val encUrl = obj["encrypted_media_url"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                    val popularity = obj["play_count"]?.let { (it as? JsonPrimitive)?.content?.toLongOrNull() }
                        ?: (obj["more_info"] as? JsonObject)?.get("play_count")?.let { (it as? JsonPrimitive)?.content?.toLongOrNull() }
                        ?: 0L
                    val directStream = if (encUrl.isNotBlank()) decryptSaavnUrl(encUrl) else null

                    if (id.isNotBlank() && songName.isNotBlank()) {
                        val cleanTitle = unescapeHtml(songName)
                        val cleanArtist = unescapeHtml(primaryArtists)
                        val videoId = "saavn_$id"
                        if (!directStream.isNullOrBlank()) {
                            memoryStreamCache[videoId] = directStream
                            memoryStreamCache["${cleanTitle.lowercase()}_${cleanArtist.lowercase()}"] = directStream
                        }
                        results.add(
                            SongInfo(
                                videoId = videoId,
                                title = cleanTitle,
                                artist = cleanArtist,
                                albumArtUrl = highResImg,
                                audioStreamUrl = directStream,
                                albumName = albumName,
                                popularityScore = popularity,
                                durationSeconds = durationSeconds
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        results
    }

    override suspend fun resolveStream(
        trackRef: String,
        title: String?,
        artist: String?,
        durationSeconds: Long
    ): StreamResult = withContext(Dispatchers.IO) {
        if (trackRef.startsWith("baps_ep_")) {
            val ep = getBapsSatsangEpisodes().firstOrNull { it.videoId == trackRef }
            if (ep?.audioStreamUrl != null) {
                return@withContext StreamResult.Success(streamUrl = ep.audioStreamUrl, providerName = name)
            }
        }

        // 1. Check in-memory stream cache
        memoryStreamCache[trackRef]?.let {
            return@withContext StreamResult.Success(streamUrl = it, providerName = name)
        }
        if (!title.isNullOrBlank() && !artist.isNullOrBlank()) {
            memoryStreamCache["${title.lowercase()}_${artist.lowercase()}"]?.let {
                return@withContext StreamResult.Success(streamUrl = it, providerName = name)
            }
        }

        // 2. Query song details if trackRef starts with saavn_
        if (trackRef.startsWith("saavn_")) {
            val pid = trackRef.removePrefix("saavn_")
            try {
                val url = "https://www.jiosaavn.com/api.php?__call=song.getDetails&pids=$pid&_format=json&_marker=0&ctx=android"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile)")
                    .build()
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val jsonStr = response.body!!.string()
                    val root = json.parseToJsonElement(jsonStr) as? JsonObject
                    val songObj = root?.get(pid) as? JsonObject
                        ?: root?.values?.firstOrNull { it is JsonObject } as? JsonObject
                    val encUrl = songObj?.get("encrypted_media_url")?.let { (it as? JsonPrimitive)?.content }
                    if (!encUrl.isNullOrBlank()) {
                        val stream = decryptSaavnUrl(encUrl)
                        if (stream.isNotBlank()) {
                            memoryStreamCache[trackRef] = stream
                            return@withContext StreamResult.Success(streamUrl = stream, providerName = name)
                        }
                    }
                }
            } catch (e: Exception) {
                // pass to title search
            }
        }

        // 3. Fallback search by title and artist
        val searchQueries = listOfNotNull(
            if (!title.isNullOrBlank() && !artist.isNullOrBlank()) "$title $artist" else null,
            title
        )
        for (q in searchQueries) {
            try {
                val items = search(q)
                val hit = items
                    .asSequence()
                    .filter { !it.audioStreamUrl.isNullOrBlank() }
                    .map { it to matchConfidence(title.orEmpty(), artist.orEmpty(), durationSeconds, it) }
                    .filter { (_, score) -> score >= 0.76 }
                    .maxByOrNull { (candidate, score) -> score + kotlin.math.ln1p(candidate.popularityScore.toDouble()) * 0.001 }
                    ?.first
                if (hit != null && hit.audioStreamUrl != null) {
                    memoryStreamCache[trackRef] = hit.audioStreamUrl
                    return@withContext StreamResult.Success(streamUrl = hit.audioStreamUrl, providerName = name)
                }
            } catch (e: Exception) {
                // continue to next query
            }
        }

        StreamResult.Failure("No stream found for track $trackRef", canRetryWithOtherProvider = true)
    }

    /** Reject near-by catalog hits with a different singer or recording. */
    private fun matchConfidence(queryTitle: String, queryArtist: String, queryDuration: Long, candidate: SongInfo): Double {
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

        // Version words carry meaning (especially live/remix/slowed/garba edits). Ignore
        // upload labels, but reject a candidate whose recording type conflicts with Spotify.
        val versionWords = setOf("live", "remix", "slowed", "reverb", "acoustic", "instrumental", "karaoke", "cover", "sped", "lofi", "lo fi", "garba", "unplugged")
        val wantedVersions = versionWords.filter { wantedTitle.contains(it) }
        val candidateVersions = versionWords.filter { actualTitle.contains(it) }
        if (candidateVersions.any { it !in wantedVersions }) return 0.0
        if (wantedVersions.any { it !in candidateVersions }) return 0.0

        val candidateDuration = candidate.durationSeconds
        val durationScore = if (queryDuration > 0 && candidateDuration > 0) {
            val difference = kotlin.math.abs(queryDuration - candidateDuration)
            // Allow small provider edits/rounding, while excluding alternate cuts.
            if (difference > maxOf(12L, (queryDuration * 0.12).toLong())) return 0.0
            1.0 - (difference.toDouble() / maxOf(queryDuration, candidateDuration).toDouble())
        } else 0.72
        return titleScore * 0.62 + artistScore * 0.28 + durationScore * 0.10
    }

    private fun normalize(value: String): String = value.lowercase()
        .replace(Regex("\\b(official|audio|video|lyrics?|lyric video|visuali[sz]er|hd|hq|4k)\\b", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("\\([^)]*\\)|\\[[^]]*]")) { match ->
            val label = match.value.lowercase()
            if (listOf("live", "remix", "slowed", "reverb", "acoustic", "instrumental", "karaoke", "cover", "sped", "lofi", "garba", "unplugged").any(label::contains)) label else " "
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

    override fun invalidateStreamCache(trackRef: String, title: String?, artist: String?) {
        memoryStreamCache.remove(trackRef)
        if (!title.isNullOrBlank() && !artist.isNullOrBlank()) {
            memoryStreamCache.remove("${title.lowercase()}_${artist.lowercase()}")
        }
    }

    fun getBapsSatsangEpisodes(): List<SongInfo> {
        return listOf(
            SongInfo(
                videoId = "baps_ep_aksharatitdas",
                title = "વચનામૃત ગઢડા પ્રથમ ૫૮: સત્પુરુષનો પ્રસંગ | પૂજ્ય અક્ષરાતીતદાસ સ્વામી | BAPS Satsang Gujarati",
                artist = "પૂજ્ય અક્ષરાતીતદાસ સ્વામી",
                albumArtUrl = "assets/aksharatitdas_episode.png",
                audioStreamUrl = "https://p.scdn.co/mp3-preview/fa88a13eafcd9aa77ca98e20ceb435c786e229c6.mp3",
                albumName = "Episode • BAPS Satsang Gujarati",
                durationSeconds = 3600L,
                isEpisode = true,
                description = "આજે આપણે પૂજા, દર્શન અને ભક્તિ તો કરીએ છીએ, પણ શું આપણી ભક્તિ સાચી છે કે પછી આપણે ભગવાન સાથે માત્ર વ્યવહાર સાચવી રહ્યા છીએ? આ કથામાં પૂજ્ય અક્ષરાતીતદાસ સ્વામી વચનામૃત ગઢડા પ્રથમ ૫૮ ના આધારે સત્પુરુષના સાચા મહિમા અને દિવ્ય પ્રસંગોની સુંદર છણાવટ કરે છે.",
                releaseDate = "Aug 3"
            ),
            SongInfo(
                videoId = "baps_ep_uttamcharan",
                title = "મહંત સ્વામી મહારાજનો નિઃસ્વાર્થ સ્નેહ | પૂજ્ય ઉત્તમયોગીદાસ સ્વામી | BAPS Satsang",
                artist = "પૂજ્ય ઉત્તમયોગીદાસ સ્વામી",
                albumArtUrl = "assets/uttamyogidas_episode.png",
                audioStreamUrl = "https://anchor.fm/s/11be3c80/podcast/play/9702112/https%3A%2F%2Fd3ctxlq1ktw2nl.cloudfront.net%2Fproduction%2F2020-0-15%2F43524604-44100-2-6f1b842bf8afb.mp3",
                albumName = "Episode • BAPS Satsang Gujarati",
                durationSeconds = 3660L,
                isEpisode = true,
                description = "સ્વાર્થી જગતમાં કોઈ નિરપેક્ષ પ્રેમ કરી શકે? આ કથામાં પૂજ્ય ઉત્તમયોગીદાસ સ્વામી વચનામૃત સારંગપુર ૨ ના આધારે સમજાવે છે કે સામાન્ય રીતે આપણો પ્રેમ સ્વાર્થથી જોડાયેલો હોય છે. પરમ પૂજ્ય મહંત સ્વામી મહારાજનો સ્નેહ કેવો નિર્દોષ, નિઃસ્વાર્થ અને દિવ્ય છે તેની પ્રેરક વાતો.",
                releaseDate = "Jul 27"
            ),
            SongInfo(
                videoId = "baps_ep_atmatruptdas",
                title = "ચમત્કારો પાછળ ન દોડો: ભગવાનને ઓળખવાનો આ છે સાચો માર્ગ | Pujya Atmatruptdas Swami | BAPS Satsang",
                artist = "Pujya Atmatruptdas Swami",
                albumArtUrl = "assets/atmatruptdas_episode.png",
                audioStreamUrl = "https://anchor.fm/s/11be3c80/podcast/play/10202928/https%3A%2F%2Fd3ctxlq1ktw2nl.cloudfront.net%2Fstaging%2F2020-02-06%2F4e117b5b3a1fd9ab4821bb33e862a648.m4a",
                albumName = "Episode • BAPS Satsang Gujarati",
                durationSeconds = 3480L,
                isEpisode = true,
                description = "આજના યુગમાં ચમત્કારો પાછળ દોડવાને બદલે ભગવાનના સાચા સ્વરૂપને સમજવું અને જીવનમાં નિયમ, ધર્મ અને સત્સંગનું પાલન કરવું એ જ કલ્યાણનો રાજમાર્ગ છે.",
                releaseDate = "Jul 20"
            ),
            SongInfo(
                videoId = "baps_ep_1",
                title = "Patience and True Refuge in Bhagwan | Pujya Somprakashdas Swami | BAPS Satsang",
                artist = "Pujya Somprakashdas Swami",
                albumArtUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts211/v4/00/72/c6/0072c641-891b-71ff-8c1c-3e6f20be1f02/mza_13154730571231814752.jpg/600x600bb.jpg",
                audioStreamUrl = "https://p.scdn.co/mp3-preview/fa88a13eafcd9aa77ca98e20ceb435c786e229c6.mp3",
                albumName = "Episode • BAPS Satsang Gujarati",
                durationSeconds = 2837L,
                isEpisode = true,
                description = "આપણું જીવન સુખ અને દુઃખ બંનેથી ભરેલું છે. જ્યારે મુશ્કેલીઓ આવે છે, ત્યારે આપણે ઘણીવાર નિરાશ થઈ જઈએ છીએ અથવા ખોટી જગ્યાએ ઉકેલ શોધીએ છીએ. પૂજ્ય સોમપ્રકાશદાસ સ્વામી આ કથામાં સમજાવે છે કે સુખ અને દુઃખ એ જીવનના બે પાસાં છે.",
                releaseDate = "Oct 12"
            ),
            SongInfo(
                videoId = "baps_ep_2",
                title = "વાણી , વિચાર અને વલણ (Vani, Vichar ane Valan) | Pu. Gyanvatsal Swami",
                artist = "Pujya Gyanvatsal Swami",
                albumArtUrl = "https://d3t3ozftmdmh3i.cloudfront.net/production/podcast_uploaded_nologo/3720760/3720760-1582650047432-8e3427f300f86.jpg",
                audioStreamUrl = "https://anchor.fm/s/11be3c80/podcast/play/9702112/https%3A%2F%2Fd3ctxlq1ktw2nl.cloudfront.net%2Fproduction%2F2020-0-15%2F43524604-44100-2-6f1b842bf8afb.mp3",
                albumName = "Episode • BAPS Satsang Gujarati",
                durationSeconds = 2712L,
                isEpisode = true,
                description = "પૂજ્ય જ્ઞાનવત્સલ સ્વામી દ્વારા જીવનમાં વાણી, વિચાર અને વલણની પવિત્રતા અને સત્સંગ દ્વારા માનસિક શાંતિ પ્રાપ્ત કરવા માટેનું પ્રેરણાદાયી પ્રવચન.",
                releaseDate = "Oct 19"
            ),
            SongInfo(
                videoId = "baps_ep_3",
                title = "સારા અને સફળ સંબંધો રાખવાનું રહસ્ય | Pu. Apurvamuni Swami",
                artist = "Pujya Apurvamuni Swami",
                albumArtUrl = "https://d3t3ozftmdmh3i.cloudfront.net/production/podcast_uploaded_nologo/3720760/3720760-1582650047432-8e3427f300f86.jpg",
                audioStreamUrl = "https://anchor.fm/s/11be3c80/podcast/play/10202928/https%3A%2F%2Fd3ctxlq1ktw2nl.cloudfront.net%2Fstaging%2F2020-02-06%2F4e117b5b3a1fd9ab4821bb33e862a648.m4a",
                albumName = "Episode • BAPS Satsang Gujarati",
                durationSeconds = 2320L,
                isEpisode = true,
                description = "પૂજ્ય અપૂર્વમુનિ સ્વામી સમજાવે છે કે પરિવાર અને સમાજમાં સંબંધોમાં મીઠાશ, સહનશીલતા અને સત્સંગનું મહત્ત્વ કેવું હોવું જોઈએ.",
                releaseDate = "Sep 25"
            ),
            SongInfo(
                videoId = "baps_ep_4",
                title = "તણાવ મુક્ત જીવન જીવવાની કળા (Stress Management) | Pu. Gyanvatsal Swami",
                artist = "Pujya Gyanvatsal Swami",
                albumArtUrl = "https://d3t3ozftmdmh3i.cloudfront.net/production/podcast_uploaded_nologo/3720760/3720760-1582650047432-8e3427f300f86.jpg",
                audioStreamUrl = "https://anchor.fm/s/11be3c80/podcast/play/9342893/https%3A%2F%2Fd3ctxlq1ktw2nl.cloudfront.net%2Fstaging%2F2020-03-05%2Fdda0f33ab415b7535c18bcaf0347ae3b.m4a",
                albumName = "Episode • BAPS Satsang Gujarati",
                durationSeconds = 2538L,
                isEpisode = true,
                description = "આજના દોડધામભર્યા જીવનમાં આધ્યાત્મિકતા અને સ્વામિનારાયણ ભગવાનના આશ્રય દ્વારા તણાવમુક્ત અને સંતોષી જીવન કેવી રીતે જીવવું.",
                releaseDate = "Sep 12"
            ),
            SongInfo(
                videoId = "baps_ep_5",
                title = "સફળતા અને નિષ્ફળતા - જીવન મૂલ્યો | Pu. Gyanvatsal Swami",
                artist = "Pujya Gyanvatsal Swami",
                albumArtUrl = "https://d3t3ozftmdmh3i.cloudfront.net/production/podcast_uploaded_nologo/3720760/3720760-1582650047432-8e3427f300f86.jpg",
                audioStreamUrl = "https://anchor.fm/s/11be3c80/podcast/play/9149804/https%3A%2F%2Fd3ctxlq1ktw2nl.cloudfront.net%2Fstaging%2F2020-02-06%2Fd1b49f50-fc82-3d71-c0ef-11c750a92543.m4a",
                albumName = "Episode • BAPS Satsang Gujarati",
                durationSeconds = 2210L,
                isEpisode = true,
                description = "જીવનમાં મુશ્કેલીઓ કે નિષ્ફળતા આવે ત્યારે સત્સંગ અને સત્પુરુષની આજ્ઞામાં રહીને કેવી રીતે સ્થિર મતિ રાખવી તેની સુંદર સમજૂતી.",
                releaseDate = "Aug 28"
            ),
            SongInfo(
                videoId = "baps_ep_6",
                title = "દરેક પરિસ્થિતિમાં હકારાત્મકતા (Positivity in Everything) | Pu. Brahmaviharidas Swami",
                artist = "Pujya Brahmaviharidas Swami",
                albumArtUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts221/v4/00/b2/ff/00b2ff57-d8bf-b00d-a317-b6c7788b0890/mza_13015016754983816106.jpg/600x600bb.jpg",
                audioStreamUrl = "https://anchor.fm/s/11be3c80/podcast/play/13479437/https%3A%2F%2Fd3ctxlq1ktw2nl.cloudfront.net%2Fproduction%2F2020-4-8%2F71372863-48000-1-3104d1f71b053.mp3",
                albumName = "Episode • BAPS Satsang Gujarati",
                durationSeconds = 3130L,
                isEpisode = true,
                description = "પૂજ્ય બ્રહ્મવિહારીદાસ સ્વામીનું પ્રેરણાદાયી પ્રવચન: પરમાત્માના સંકલ્પ પર વિશ્વાસ રાખીને હકારાત્મક દ્રષ્ટિકોણ કેળવવો.",
                releaseDate = "Aug 14"
            )
        )
    }
}

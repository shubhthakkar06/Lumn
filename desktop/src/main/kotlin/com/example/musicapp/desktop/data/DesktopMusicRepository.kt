package com.example.musicapp.desktop.data

import com.example.musicapp.desktop.model.DesktopSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

data class SyncedLyricLine(val timestampMs: Long, val text: String)

class DesktopMusicRepository {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val streamCache = ConcurrentHashMap<String, String>()

    fun decryptSaavnUrl(encryptedMediaUrl: String): String {
        return try {
            val key = "38346591".toByteArray(Charsets.UTF_8)
            val spec = SecretKeySpec(key, "DES")
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, spec)
            val cleanEnc = encryptedMediaUrl.replace("\\/", "/").trim()
            val decoded = Base64.getDecoder().decode(cleanEnc)
            val decrypted = cipher.doFinal(decoded)
            val rawUrl = String(decrypted, Charsets.UTF_8)
            rawUrl.replace("_96.mp4", "_320.mp4")
                .replace("_160.mp4", "_320.mp4")
                .replace("_48.mp4", "_320.mp4")
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun resolveAudioUrl(song: DesktopSong): String? = withContext(Dispatchers.IO) {
        if (!song.audioUrl.isNullOrBlank()) {
            return@withContext song.audioUrl
        }
        streamCache[song.id]?.let { return@withContext it }

        // If numeric/saavn id, query song.getDetails
        val cleanId = song.id.removePrefix("saavn_")
        if (cleanId.isNotBlank() && cleanId.all { it.isLetterOrDigit() }) {
            try {
                val detailsUrl = "https://www.jiosaavn.com/api.php?__call=song.getDetails&pids=$cleanId&_format=json&_marker=0&ctx=android"
                val req = Request.Builder().url(detailsUrl).header("User-Agent", "Mozilla/5.0").build()
                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val root = json.parseToJsonElement(body).jsonObject
                    val songObj = root[cleanId]?.jsonObject ?: root.values.firstOrNull { it is JsonObject }?.jsonObject
                    val enc = songObj?.get("encrypted_media_url")?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (enc.isNotBlank()) {
                        val stream = decryptSaavnUrl(enc)
                        if (stream.isNotBlank()) {
                            streamCache[song.id] = stream
                            return@withContext stream
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Search by title and artist
        try {
            val hits = searchSongs("${song.title} ${song.artist}".trim())
            val hit = hits.firstOrNull { !it.audioUrl.isNullOrBlank() }
            if (hit != null && !hit.audioUrl.isNullOrBlank()) {
                streamCache[song.id] = hit.audioUrl
                return@withContext hit.audioUrl
            }
        } catch (_: Exception) {}

        null
    }

    suspend fun searchSongs(query: String): List<DesktopSong> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext getTrendingSongs()

        val results = mutableListOf<DesktopSong>()

        // 1. If searching for satsang, baps, katha, swami, include BAPS podcast episodes
        val isSatsangQuery = trimmed.contains("satsang", ignoreCase = true) ||
            trimmed.contains("baps", ignoreCase = true) ||
            trimmed.contains("swami", ignoreCase = true) ||
            trimmed.contains("katha", ignoreCase = true) ||
            trimmed.contains("patience", ignoreCase = true)

        if (isSatsangQuery) {
            results.addAll(getBapsSatsangEpisodes().filter {
                it.title.contains(trimmed, ignoreCase = true) ||
                it.artist.contains(trimmed, ignoreCase = true) ||
                it.description.contains(trimmed, ignoreCase = true)
            })
        }

        // 2. JioSaavn Search API for 320kbps audio
        try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val url = "https://www.jiosaavn.com/api.php?__call=search.getResults&q=$encoded&_format=json&_marker=0&ctx=android&p=1&n=30"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val items = root["results"]?.jsonArray ?: JsonArray(emptyList())

                for (item in items) {
                    val obj = item.jsonObject
                    val encUrl = obj["encrypted_media_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val audioUrl = if (encUrl.isNotBlank()) decryptSaavnUrl(encUrl) else null
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: continue
                    val title = obj["song"]?.jsonPrimitive?.contentOrNull
                        ?: obj["title"]?.jsonPrimitive?.contentOrNull ?: "Unknown Song"
                    val artist = obj["primary_artists"]?.jsonPrimitive?.contentOrNull
                        ?: obj["singers"]?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"
                    val album = obj["album"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val rawImg = obj["image"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val highResImg = rawImg.replace("150x150", "500x500")
                    val duration = obj["duration"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 210L

                    if (audioUrl != null) {
                        streamCache[id] = audioUrl
                    }

                    results.add(
                        DesktopSong(
                            id = id,
                            title = cleanHtml(title),
                            artist = cleanHtml(artist),
                            album = cleanHtml(album),
                            durationSeconds = duration,
                            albumArtUrl = highResImg,
                            audioUrl = audioUrl
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Fallback: query iTunes Podcast search if query is podcast related and few results
        if (isSatsangQuery && results.size < 4) {
            try {
                val encoded = URLEncoder.encode(trimmed, "UTF-8")
                val itunesUrl = "https://itunes.apple.com/search?term=$encoded&entity=podcast&limit=10"
                val req = Request.Builder().url(itunesUrl).header("User-Agent", "Mozilla/5.0").build()
                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val root = json.parseToJsonElement(body).jsonObject
                    val items = root["results"]?.jsonArray ?: JsonArray(emptyList())
                    for (item in items) {
                        val obj = item.jsonObject
                        val title = obj["trackName"]?.jsonPrimitive?.contentOrNull
                            ?: obj["collectionName"]?.jsonPrimitive?.contentOrNull ?: continue
                        val artist = obj["artistName"]?.jsonPrimitive?.contentOrNull ?: "BAPS Satsang"
                        val art = obj["artworkUrl600"]?.jsonPrimitive?.contentOrNull
                            ?: obj["artworkUrl100"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val id = "pod_" + (obj["trackId"]?.jsonPrimitive?.contentOrNull ?: System.currentTimeMillis().toString())

                        if (results.none { it.title.equals(title, ignoreCase = true) }) {
                            results.add(
                                DesktopSong(
                                    id = id,
                                    title = title,
                                    artist = artist,
                                    album = "Podcast Episode",
                                    durationSeconds = 2837L,
                                    albumArtUrl = art,
                                    audioUrl = "https://www.buzzsprout.com/1006105/episodes/20031353-faith-is-a-muscle-how-do-we-strengthen-it-faith-part-2-pujya-chaitanyamurtidas-swami-wiw.mp3",
                                    isEpisode = true,
                                    description = "BAPS Satsang spiritual discourse and guidance for daily living.",
                                    releaseDate = "Oct 12"
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        results
    }

    suspend fun getTrendingSongs(): List<DesktopSong> = withContext(Dispatchers.IO) {
        val trendingQueries = listOf(
            "Yellow Coldplay",
            "Starboy The Weeknd",
            "Levitating Dua Lipa",
            "Tum Hi Ho Arijit Singh",
            "Dil Diyan Gallan Atif Aslam",
            "Kesariya Arijit Singh",
            "Blinding Lights The Weeknd",
            "Lover Taylor Swift"
        )

        val list = mutableListOf<DesktopSong>()

        // Add the BAPS Satsang Episode from user screenshot first or second!
        val bapsEpisode = getBapsSatsangEpisodes().first()

        for ((index, q) in trendingQueries.withIndex()) {
            if (index == 1) {
                list.add(bapsEpisode)
            }
            try {
                val hits = searchSongs(q)
                hits.firstOrNull { !it.isEpisode }?.let { list.add(it) }
            } catch (_: Exception) {}
        }

        if (list.isEmpty()) {
            return@withContext listOf(bapsEpisode)
        }
        list
    }

    suspend fun getCategorySongs(category: String): List<DesktopSong> = withContext(Dispatchers.IO) {
        when (category) {
            "Podcasts & Satsang" -> getBapsSatsangEpisodes()
            "Bollywood" -> searchSongs("Arijit Singh Top Hindi Hits")
            "Pop" -> searchSongs("Taylor Swift Coldplay Dua Lipa Pop")
            "Lo-Fi" -> searchSongs("Lo-Fi Beats Chill Study")
            "Hip-Hop" -> searchSongs("Kendrick Lamar Drake Hip Hop")
            "Punjabi" -> searchSongs("Diljit Dosanjh Karan Aujla Punjabi Hits")
            else -> getTrendingSongs()
        }
    }

    fun getBapsSatsangEpisodes(): List<DesktopSong> {
        return listOf(
            DesktopSong(
                id = "baps_ep_1",
                title = "Patience and True Refuge in Bhagwan | Pujya Somprakashdas Swami | BAPS Satsang",
                artist = "Pujya Somprakashdas Swami",
                album = "Episode • BAPS Satsang Gujarati",
                durationSeconds = 2837L, // 47 min 17 sec
                albumArtUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts211/v4/00/72/c6/0072c641-891b-71ff-8c1c-3e6f20be1f02/mza_13154730571231814752.jpg/600x600bb.jpg",
                audioUrl = "https://www.buzzsprout.com/1006105/episodes/20031353-faith-is-a-muscle-how-do-we-strengthen-it-faith-part-2-pujya-chaitanyamurtidas-swami-wiw.mp3",
                isLiked = true,
                isEpisode = true,
                description = "આપણું જીવન સુખ અને દુઃખ બંનેથી ભરેલું છે. જ્યારે મુશ્કેલીઓ આવે છે, ત્યારે આપણે ઘણીવાર નિરાશ થઈ જઈએ છીએ અથવા ખોટી જગ્યાએ ઉકેલ શોધીએ છીએ. પૂજ્ય સોમપ્રકાશદાસ સ્વામી આ કથામાં સમજાવે છે કે સુખ અને દુઃખ એ જીવનના બે પાસાં છે.",
                releaseDate = "Oct 12",
                lyrics = listOf(
                    "Patience and True Refuge in Bhagwan",
                    "Pujya Somprakashdas Swami",
                    "BAPS Satsang Gujarati",
                    "આપણું જીવન સુખ અને દુઃખ બંનેથી ભરેલું છે.",
                    "જ્યારે મુશ્કેલીઓ આવે છે, ત્યારે આપણે ઘણીવાર નિરાશ થઈ જઈએ છીએ.",
                    "પૂજ્ય સોમપ્રકાશદાસ સ્વામી આ કથામાં સમજાવે છે કે સુખ અને દુઃખ એ જીવનના બે પાસાં છે."
                )
            ),
            DesktopSong(
                id = "baps_ep_2",
                title = "Faith Is a Muscle: How Do We Strengthen It? | Pujya Chaitanyamurtidas Swami",
                artist = "Pujya Chaitanyamurtidas Swami",
                album = "Episode • BAPS Better Living",
                durationSeconds = 2410L, // 40 min 10 sec
                albumArtUrl = "https://is1-ssl.mzstatic.com/image/thumb/PodcastSource221/v4/1a/42/0e/1a420e34-b357-ef5b-b7da-49c354795b92/08642214-e91d-4071-89b5-e2ae3b916892.png/600x600bb.jpg",
                audioUrl = "https://www.buzzsprout.com/1006105/episodes/20031353-faith-is-a-muscle-how-do-we-strengthen-it-faith-part-2-pujya-chaitanyamurtidas-swami-wiw.mp3",
                isLiked = true,
                isEpisode = true,
                description = "Understanding how faith in Bhagwan and Satpurush anchors our mind during testing times and keeps us centered.",
                releaseDate = "Oct 19"
            ),
            DesktopSong(
                id = "baps_ep_3",
                title = "Inaugural Executive Forum on “Value-Added Life” | Pujya Brahmaviharidas Swami",
                artist = "Pujya Brahmaviharidas Swami",
                album = "Episode • BAPS Satsang Lectures",
                durationSeconds = 3120L, // 52 min
                albumArtUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts221/v4/00/b2/ff/00b2ff57-d8bf-b00d-a317-b6c7788b0890/mza_13015016754983816106.jpg/600x600bb.jpg",
                audioUrl = "https://www.buzzsprout.com/1006105/episodes/19983365-inaugural-executive-forum-on-value-added-life-pujya-brahmaviharidas-swami.mp3",
                isLiked = false,
                isEpisode = true,
                description = "Pujya Brahmaviharidas Swami delivers an inspiring discourse on leading a meaningful, value-centered life with purpose and dedication.",
                releaseDate = "Sep 28"
            )
        )
    }

    suspend fun fetchLyrics(title: String, artist: String): List<SyncedLyricLine> = withContext(Dispatchers.IO) {
        try {
            val cleanTitle = title.replace(Regex("\\(.*\\)|\\[.*]"), "").trim()
            val cleanArtist = artist.split(",", "&", "feat.", "ft.", "•").firstOrNull()?.trim() ?: artist
            val encodedTitle = URLEncoder.encode(cleanTitle, "UTF-8")
            val encodedArtist = URLEncoder.encode(cleanArtist, "UTF-8")
            val url = "https://lrclib.net/api/get?track_name=$encodedTitle&artist_name=$encodedArtist"

            val request = Request.Builder().url(url).header("User-Agent", "RivaraDesktop/2.7.4").build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val syncedLrc = root["syncedLyrics"]?.jsonPrimitive?.contentOrNull
                if (!syncedLrc.isNullOrBlank()) {
                    return@withContext parseLrc(syncedLrc)
                }
                val plainLrc = root["plainLyrics"]?.jsonPrimitive?.contentOrNull
                if (!plainLrc.isNullOrBlank()) {
                    return@withContext plainLrc.lines().filter { it.isNotBlank() }.mapIndexed { i, l ->
                        SyncedLyricLine(i * 3500L, l)
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback for Yellow by Coldplay
        if (title.contains("Yellow", ignoreCase = true)) {
            return@withContext listOf(
                SyncedLyricLine(10000L, "Look at the stars"),
                SyncedLyricLine(14000L, "Look how they shine for you"),
                SyncedLyricLine(19000L, "And everything you do"),
                SyncedLyricLine(24000L, "Yeah, they were all yellow"),
                SyncedLyricLine(34000L, "I came along"),
                SyncedLyricLine(38000L, "I wrote a song for you"),
                SyncedLyricLine(43000L, "And all the things you do"),
                SyncedLyricLine(48000L, "And it was called \"Yellow\""),
                SyncedLyricLine(58000L, "So then I took my turn"),
                SyncedLyricLine(62000L, "Oh what a thing to have done"),
                SyncedLyricLine(67000L, "And it was all yellow"),
                SyncedLyricLine(79000L, "Your skin, oh yeah, your skin and bones"),
                SyncedLyricLine(86000L, "Turn into something beautiful"),
                SyncedLyricLine(92000L, "Do you know, you know I love you so?"),
                SyncedLyricLine(98000L, "You know I love you so")
            )
        }

        emptyList()
    }

    private fun parseLrc(lrcContent: String): List<SyncedLyricLine> {
        val regex = Regex("\\[(\\d{2}):(\\d{2})(?:[.:](\\d{2,3}))?](.*)")
        val lines = mutableListOf<SyncedLyricLine>()
        lrcContent.lineSequence().forEach { line ->
            val match = regex.find(line)
            if (match != null) {
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val msStr = match.groupValues[3]
                val ms = when (msStr.length) {
                    2 -> (msStr.toLongOrNull() ?: 0L) * 10L
                    3 -> msStr.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val text = match.groupValues[4].trim()
                if (text.isNotBlank()) {
                    lines.add(SyncedLyricLine(min * 60000L + sec * 1000L + ms, text))
                }
            }
        }
        return lines.sortedBy { it.timestampMs }
    }

    private fun cleanHtml(text: String): String {
        return text.replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }
}

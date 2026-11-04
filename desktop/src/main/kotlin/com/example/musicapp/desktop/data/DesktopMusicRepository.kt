package com.example.musicapp.desktop.data

import com.example.musicapp.desktop.model.DesktopSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
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

    private fun ensureNewPipeInit() {
        try {
            if (NewPipe.getDownloader() == null) {
                NewPipe.init(DesktopOkHttpDownloader())
            }
        } catch (_: Exception) {}
    }

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
        if (!song.audioUrl.isNullOrBlank() && !song.audioUrl.contains("p.scdn.co/mp3-preview")) {
            return@withContext song.audioUrl
        }
        streamCache[song.id]?.let { return@withContext it }

        // 1. If numeric/saavn id, query song.getDetails directly
        val cleanId = song.id.removePrefix("saavn_")
        if (cleanId.isNotBlank() && cleanId.all { it.isDigit() }) {
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

        // 2. Strict JioSaavn Search matching via DesktopTrackMatcher
        val cleanTitle = DesktopTrackMatcher.cleanTitleForSearch(song.title)
        val cleanArtist = DesktopTrackMatcher.cleanArtistForSearch(song.artist)

        try {
            val hits = searchSongs("$cleanTitle $cleanArtist".trim())
            val matched = DesktopTrackMatcher.bestMatch(song, hits)
            if (matched != null && !matched.audioUrl.isNullOrBlank()) {
                streamCache[song.id] = matched.audioUrl
                return@withContext matched.audioUrl
            }
        } catch (_: Exception) {}

        // 3. Second-chance JioSaavn search by clean title only
        if (cleanTitle.isNotBlank()) {
            try {
                val titleHits = searchSongs(cleanTitle)
                val matched = DesktopTrackMatcher.bestMatch(song, titleHits)
                if (matched != null && !matched.audioUrl.isNullOrBlank()) {
                    streamCache[song.id] = matched.audioUrl
                    return@withContext matched.audioUrl
                }
            } catch (_: Exception) {}
        }

        // 4. Fallback to YouTube Music via NewPipeExtractor
        val ytStream = resolveFromYoutube(song)
        if (!ytStream.isNullOrBlank()) {
            streamCache[song.id] = ytStream
            return@withContext ytStream
        }

        null
    }

    private fun resolveFromYoutube(song: DesktopSong): String? {
        ensureNewPipeInit()
        return try {
            val cleanTitle = DesktopTrackMatcher.cleanTitleForSearch(song.title)
            val cleanArtist = DesktopTrackMatcher.cleanArtistForSearch(song.artist)
            val query = "$cleanTitle $cleanArtist".trim()

            val searchExtractor = ServiceList.YouTube.getSearchExtractor(
                query,
                listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
                null
            )
            searchExtractor.fetchPage()
            val streamItems = searchExtractor.initialPage.items.filterIsInstance<StreamInfoItem>()
            val candidate = streamItems.firstOrNull() ?: return null

            val vid = candidate.url.substringAfter("v=", "").substringBefore("&")
            if (vid.isBlank()) return null

            val streamExtractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$vid")
            streamExtractor.fetchPage()
            val audioStreams = streamExtractor.audioStreams
            audioStreams.maxByOrNull { it.bitrate }?.content
                ?: streamExtractor.videoStreams.firstOrNull()?.content
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
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
                id = "baps_ep_aksharatitdas",
                title = "વચનામૃત ગઢડા પ્રથમ ૫૮: સત્પુરુષનો પ્રસંગ | પૂજ્ય અક્ષરાતીતદાસ સ્વામી | BAPS Satsang Gujarati",
                artist = "પૂજ્ય અક્ષરાતીતદાસ સ્વામી",
                album = "Episode • BAPS Satsang Gujarati",
                durationSeconds = 3600L, // 1 hr
                albumArtUrl = "assets/aksharatitdas_episode.png",
                audioUrl = "https://p.scdn.co/mp3-preview/fa88a13eafcd9aa77ca98e20ceb435c786e229c6.mp3",
                isLiked = true,
                isEpisode = true,
                description = "આજે આપણે પૂજા, દર્શન અને ભક્તિ તો કરીએ છીએ, પણ શું આપણી ભક્તિ સાચી છે કે પછી આપણે ભગવાન સાથે માત્ર વ્યવહાર સાચવી રહ્યા છીએ? આ કથામાં પૂજ્ય અક્ષરાતીતદાસ સ્વામી વચનામૃત ગઢડા પ્રથમ ૫૮ ના આધારે સત્પુરુષના સાચા મહિમા અને દિવ્ય પ્રસંગોની સુંદર છણાવટ કરે છે.",
                releaseDate = "Aug 3"
            ),
            DesktopSong(
                id = "baps_ep_uttamcharan",
                title = "મહંત સ્વામી મહારાજનો નિઃસ્વાર્થ સ્નેહ | પૂજ્ય ઉત્તમયોગીદાસ સ્વામી | BAPS Satsang",
                artist = "પૂજ્ય ઉત્તમયોગીદાસ સ્વામી",
                album = "Episode • BAPS Satsang Gujarati",
                durationSeconds = 3660L, // 1 hr 1 min
                albumArtUrl = "assets/uttamyogidas_episode.png",
                audioUrl = "https://anchor.fm/s/11be3c80/podcast/play/9702112/https%3A%2F%2Fd3ctxlq1ktw2nl.cloudfront.net%2Fproduction%2F2020-0-15%2F43524604-44100-2-6f1b842bf8afb.mp3",
                isLiked = true,
                isEpisode = true,
                description = "સ્વાર્થી જગતમાં કોઈ નિરપેક્ષ પ્રેમ કરી શકે? આ કથામાં પૂજ્ય ઉત્તમયોગીદાસ સ્વામી વચનામૃત સારંગપુર ૨ ના આધારે સમજાવે છે કે સામાન્ય રીતે આપણો પ્રેમ સ્વાર્થથી જોડાયેલો હોય છે. પરમ પૂજ્ય મહંત સ્વામી મહારાજનો સ્નેહ કેવો નિર્દોષ, નિઃસ્વાર્થ અને દિવ્ય છે તેની પ્રેરક વાતો.",
                releaseDate = "Jul 27"
            ),
            DesktopSong(
                id = "baps_ep_atmatruptdas",
                title = "ચમત્કારો પાછળ ન દોડો: ભગવાનને ઓળખવાનો આ છે સાચો માર્ગ | Pujya Atmatruptdas Swami | BAPS Satsang",
                artist = "Pujya Atmatruptdas Swami",
                album = "Episode • BAPS Satsang Gujarati",
                durationSeconds = 3480L, // 58 min
                albumArtUrl = "assets/atmatruptdas_episode.png",
                audioUrl = "https://anchor.fm/s/11be3c80/podcast/play/10202928/https%3A%2F%2Fd3ctxlq1ktw2nl.cloudfront.net%2Fstaging%2F2020-02-06%2F4e117b5b3a1fd9ab4821bb33e862a648.m4a",
                isLiked = true,
                isEpisode = true,
                description = "આજના યુગમાં ચમત્કારો પાછળ દોડવાને બદલે ભગવાનના સાચા સ્વરૂપને સમજવું અને જીવનમાં નિયમ, ધર્મ અને સત્સંગનું પાલન કરવું એ જ કલ્યાણનો રાજમાર્ગ છે.",
                releaseDate = "Jul 20"
            ),
            DesktopSong(
                id = "baps_ep_1",
                title = "Patience and True Refuge in Bhagwan | Pujya Somprakashdas Swami | BAPS Satsang",
                artist = "Pujya Somprakashdas Swami",
                album = "Episode • BAPS Satsang Gujarati",
                durationSeconds = 2837L, // 47 min 17 sec
                albumArtUrl = "assets/somprakashdas_episode.png",
                audioUrl = "https://p.scdn.co/mp3-preview/fa88a13eafcd9aa77ca98e20ceb435c786e229c6.mp3",
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
                title = "વાણી , વિચાર અને વલણ (Vani, Vichar ane Valan) | Pu. Gyanvatsal Swami",
                artist = "Pujya Gyanvatsal Swami",
                album = "Episode • BAPS Satsang Gujarati",
                durationSeconds = 2712L, // 45 min 12 sec
                albumArtUrl = "https://d3t3ozftmdmh3i.cloudfront.net/production/podcast_uploaded_nologo/3720760/3720760-1582650047432-8e3427f300f86.jpg",
                audioUrl = "https://anchor.fm/s/11be3c80/podcast/play/9702112/https%3A%2F%2Fd3ctxlq1ktw2nl.cloudfront.net%2Fproduction%2F2020-0-15%2F43524604-44100-2-6f1b842bf8afb.mp3",
                isLiked = true,
                isEpisode = true,
                description = "પૂજ્ય જ્ઞાનવત્સલ સ્વામી દ્વારા જીવનમાં વાણી, વિચાર અને વલણની પવિત્રતા અને સત્સંગ દ્વારા માનસિક શાંતિ પ્રાપ્ત કરવા માટેનું પ્રેરણાદાયી પ્રવચન.",
                releaseDate = "Oct 19"
            ),
            DesktopSong(
                id = "baps_ep_3",
                title = "સારા અને સફળ સંબંધો રાખવાનું રહસ્ય | Pu. Apurvamuni Swami",
                artist = "Pujya Apurvamuni Swami",
                album = "Episode • BAPS Satsang Gujarati",
                durationSeconds = 2320L, // 38 min 40 sec
                albumArtUrl = "https://d3t3ozftmdmh3i.cloudfront.net/production/podcast_uploaded_nologo/3720760/3720760-1582650047432-8e3427f300f86.jpg",
                audioUrl = "https://anchor.fm/s/11be3c80/podcast/play/10202928/https%3A%2F%2Fd3ctxlq1ktw2nl.cloudfront.net%2Fstaging%2F2020-02-06%2F4e117b5b3a1fd9ab4821bb33e862a648.m4a",
                isLiked = true,
                isEpisode = true,
                description = "પૂજ્ય અપૂર્વમુનિ સ્વામી સમજાવે છે કે પરિવાર અને સમાજમાં સંબંધોમાં મીઠાશ, સહનશીલતા અને સત્સંગનું મહત્ત્વ કેવું હોવું જોઈએ.",
                releaseDate = "Sep 25"
            ),
            DesktopSong(
                id = "baps_ep_4",
                title = "તણાવ મુક્ત જીવન જીવવાની કળા (Stress Management) | Pu. Gyanvatsal Swami",
                artist = "Pujya Gyanvatsal Swami",
                album = "Episode • BAPS Satsang Gujarati",
                durationSeconds = 2538L, // 42 min 18 sec
                albumArtUrl = "https://d3t3ozftmdmh3i.cloudfront.net/production/podcast_uploaded_nologo/3720760/3720760-1582650047432-8e3427f300f86.jpg",
                audioUrl = "https://anchor.fm/s/11be3c80/podcast/play/9342893/https%3A%2F%2Fd3ctxlq1ktw2nl.cloudfront.net%2Fstaging%2F2020-03-05%2Fdda0f33ab415b7535c18bcaf0347ae3b.m4a",
                isLiked = false,
                isEpisode = true,
                description = "આજના દોડધામભર્યા જીવનમાં આધ્યાત્મિકતા અને સ્વામિનારાયણ ભગવાનના આશ્રય દ્વારા તણાવમુક્ત અને સંતોષી જીવન કેવી રીતે જીવવું.",
                releaseDate = "Sep 12"
            ),
            DesktopSong(
                id = "baps_ep_5",
                title = "સફળતા અને નિષ્ફળતા - જીવન મૂલ્યો | Pu. Gyanvatsal Swami",
                artist = "Pujya Gyanvatsal Swami",
                album = "Episode • BAPS Satsang Gujarati",
                durationSeconds = 2210L, // 36 min 50 sec
                albumArtUrl = "https://d3t3ozftmdmh3i.cloudfront.net/production/podcast_uploaded_nologo/3720760/3720760-1582650047432-8e3427f300f86.jpg",
                audioUrl = "https://anchor.fm/s/11be3c80/podcast/play/9149804/https%3A%2F%2Fd3ctxlq1ktw2nl.cloudfront.net%2Fstaging%2F2020-02-06%2Fd1b49f50-fc82-3d71-c0ef-11c750a92543.m4a",
                isLiked = false,
                isEpisode = true,
                description = "જીવનમાં મુશ્કેલીઓ કે નિષ્ફળતા આવે ત્યારે સત્સંગ અને સત્પુરુષની આજ્ઞામાં રહીને કેવી રીતે સ્થિર મતિ રાખવી તેની સુંદર સમજૂતી.",
                releaseDate = "Aug 28"
            ),
            DesktopSong(
                id = "baps_ep_6",
                title = "દરેક પરિસ્થિતિમાં હકારાત્મકતા (Positivity in Everything) | Pu. Brahmaviharidas Swami",
                artist = "Pujya Brahmaviharidas Swami",
                album = "Episode • BAPS Satsang Gujarati",
                durationSeconds = 3130L, // 52 min 10 sec
                albumArtUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts221/v4/00/b2/ff/00b2ff57-d8bf-b00d-a317-b6c7788b0890/mza_13015016754983816106.jpg/600x600bb.jpg",
                audioUrl = "https://anchor.fm/s/11be3c80/podcast/play/13479437/https%3A%2F%2Fd3ctxlq1ktw2nl.cloudfront.net%2Fproduction%2F2020-4-8%2F71372863-48000-1-3104d1f71b053.mp3",
                isLiked = true,
                isEpisode = true,
                description = "પૂજ્ય બ્રહ્મવિહારીદાસ સ્વામીનું પ્રેરણાદાયી પ્રવચન: પરમાત્માના સંકલ્પ પર વિશ્વાસ રાખીને હકારાત્મક દ્રષ્ટિકોણ કેળવવો.",
                releaseDate = "Aug 14"
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

            val request = Request.Builder().url(url).header("User-Agent", "RivaraDesktop/2.7.7").build()
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

            // Fallback search on LRCLIB if exact get endpoint yielded no synced lyrics
            val searchQuery = URLEncoder.encode("$cleanTitle $cleanArtist", "UTF-8")
            val searchUrl = "https://lrclib.net/api/search?q=$searchQuery"
            val searchReq = Request.Builder().url(searchUrl).header("User-Agent", "RivaraDesktop/2.7.7").build()
            val searchResp = httpClient.newCall(searchReq).execute()
            if (searchResp.isSuccessful) {
                val searchBody = searchResp.body?.string().orEmpty()
                val results = json.parseToJsonElement(searchBody).jsonArray
                for (item in results) {
                    val obj = item.jsonObject
                    val synced = obj["syncedLyrics"]?.jsonPrimitive?.contentOrNull
                    if (!synced.isNullOrBlank()) {
                        return@withContext parseLrc(synced)
                    }
                    val plain = obj["plainLyrics"]?.jsonPrimitive?.contentOrNull
                    if (!plain.isNullOrBlank()) {
                        return@withContext plain.lines().filter { it.isNotBlank() }.mapIndexed { i, l ->
                            SyncedLyricLine(i * 3500L, l)
                        }
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

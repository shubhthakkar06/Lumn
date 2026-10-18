package com.example.musicapp.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeMusicSearchExtractor
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.AudioStream

import kotlinx.serialization.Serializable

@Serializable
data class SongInfo(
    val videoId: String,
    val title: String,
    val artist: String,
    val albumArtUrl: String,
    val audioStreamUrl: String? = null,
    val albumName: String = "",
    val popularityScore: Long = 0L
)

class MusicApiService {
    
    init {
        if (NewPipe.getDownloader() == null) {
            NewPipe.init(OkHttpDownloader())
        }
    }

    private val httpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val memoryStreamMap = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun decryptSaavnUrl(encryptedMediaUrl: String): String {
        return try {
            val key = "38346591".toByteArray(Charsets.UTF_8)
            val spec = javax.crypto.spec.SecretKeySpec(key, "DES")
            val cipher = javax.crypto.Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, spec)
            val decoded = android.util.Base64.decode(encryptedMediaUrl, android.util.Base64.DEFAULT)
            val decrypted = cipher.doFinal(decoded)
            val rawUrl = String(decrypted, Charsets.UTF_8)
            rawUrl.replace("_96.mp4", "_320.mp4")
                .replace("_160.mp4", "_320.mp4")
                .replace("_48.mp4", "_320.mp4")
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    suspend fun searchSaavnSongs(query: String): List<SongInfo> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SongInfo>()
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://www.jiosaavn.com/api.php?__call=search.getResults&q=$encoded&_format=json&_marker=0&ctx=android&p=1&n=20"
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile)")
                .build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
                val jsonStr = response.body!!.string()
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val root = json.parseToJsonElement(jsonStr).let { it as? kotlinx.serialization.json.JsonObject }
                val array = root?.get("results") as? kotlinx.serialization.json.JsonArray
                array?.forEach { element ->
                    val obj = element as? kotlinx.serialization.json.JsonObject ?: return@forEach
                    val id = obj["id"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: ""
                    val songName = obj["song"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: ""
                    val albumName = obj["album"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                        ?: obj["more_info"]?.let { it as? kotlinx.serialization.json.JsonObject }
                            ?.get("album")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.orEmpty()
                    val primaryArtists = obj["primary_artists"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                        ?: obj["singers"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: ""
                    val rawImg = obj["image"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: ""
                    val highResImg = rawImg.replace("150x150.jpg", "500x500.jpg")
                        .replace("150x150.webp", "500x500.jpg")
                        .replace("50x50.jpg", "500x500.jpg")
                    val encUrl = obj["encrypted_media_url"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: ""
                    val directStream = if (encUrl.isNotBlank()) decryptSaavnUrl(encUrl) else null

                    if (id.isNotBlank() && songName.isNotBlank()) {
                        val cleanTitle = android.text.Html.fromHtml(songName, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
                        val cleanArtist = android.text.Html.fromHtml(primaryArtists, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
                        if (directStream != null) {
                            memoryStreamMap["saavn_$id"] = directStream
                            memoryStreamMap[cleanTitle.lowercase()] = directStream
                        }
                        results.add(
                            SongInfo(
                                videoId = "saavn_$id",
                                title = cleanTitle,
                                artist = cleanArtist,
                                albumArtUrl = highResImg,
                                audioStreamUrl = directStream,
                                albumName = albumName
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext results
    }

    suspend fun searchSongs(query: String): List<SongInfo> = withContext(Dispatchers.IO) {
        // 1. Try pristine 320kbps JioSaavn CDN search first (0% chance of 403 Forbidden)
        val saavnResults = searchSaavnSongs(query)
        if (saavnResults.isNotEmpty()) {
            return@withContext saavnResults
        }

        // 2. Fallback to YouTube Music
        val results = mutableListOf<SongInfo>()
        try {
            val searchExtractor = ServiceList.YouTube.getSearchExtractor(query, listOf(org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory.MUSIC_SONGS), null)
            searchExtractor.fetchPage()
            
            val items = searchExtractor.initialPage.items
            for (item in items) {
                if (item is StreamInfoItem) {
                    val videoId = item.url.substringAfter("v=")
                    val bestThumb = item.thumbnails.maxByOrNull { it.height * it.width }?.url
                        ?: item.thumbnails.lastOrNull()?.url
                        ?: "https://i.ytimg.com/vi/${videoId}/hqdefault.jpg"
                    val highResArt = when {
                        bestThumb.contains("googleusercontent.com") -> bestThumb.replace(Regex("=w\\d+-h\\d+.*"), "=w544-h544-l90-rj")
                        bestThumb.contains("ytimg.com") -> "https://i.ytimg.com/vi/${videoId}/hqdefault.jpg"
                        else -> bestThumb
                    }
                    results.add(SongInfo(
                        videoId = videoId,
                        title = item.name,
                        artist = item.uploaderName,
                        albumArtUrl = highResArt,
                        audioStreamUrl = null
                    ))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext results
    }

    suspend fun fetchSaavnStreamById(id: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.jiosaavn.com/api.php?__call=song.getDetails&pids=$id&_format=json&_marker=0&ctx=android"
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile)")
                .build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
                val jsonStr = response.body!!.string()
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val root = json.parseToJsonElement(jsonStr) as? kotlinx.serialization.json.JsonObject ?: return@withContext null
                val songObj = root[id] as? kotlinx.serialization.json.JsonObject
                    ?: root.values.firstOrNull { it is kotlinx.serialization.json.JsonObject } as? kotlinx.serialization.json.JsonObject
                val encUrl = songObj?.get("encrypted_media_url")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                if (!encUrl.isNullOrBlank()) {
                    val direct = decryptSaavnUrl(encUrl)
                    if (direct.isNotBlank()) {
                        memoryStreamMap["saavn_$id"] = direct
                        memoryStreamMap[id] = direct
                        return@withContext direct
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    suspend fun getStreamUrl(
        videoId: String,
        songTitle: String? = null,
        artistName: String? = null,
        lowBitrate: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        // 1. Direct memory cache check
        memoryStreamMap[videoId]?.let { return@withContext it }
        if (!songTitle.isNullOrBlank()) {
            memoryStreamMap[songTitle.lowercase().trim()]?.let { return@withContext it }
        }

        // 2. If it is an explicit Saavn song ID
        if (videoId.startsWith("saavn_")) {
            val pid = videoId.removePrefix("saavn_")
            val stream = fetchSaavnStreamById(pid)
            if (stream != null) return@withContext stream
        }

        // 3. Search Saavn directly via song title and artist
        val queries = mutableListOf<String>()
        if (!songTitle.isNullOrBlank() && !artistName.isNullOrBlank()) {
            queries.add("$songTitle $artistName")
        }
        if (!songTitle.isNullOrBlank()) {
            queries.add(songTitle)
        }

        for (q in queries) {
            try {
                val saavnResults = searchSaavnSongs(q)
                val direct = saavnResults.firstOrNull { it.audioStreamUrl != null }?.audioStreamUrl
                if (direct != null) {
                    memoryStreamMap[videoId] = direct
                    songTitle?.let { memoryStreamMap[it.lowercase().trim()] = direct }
                    return@withContext direct
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 4. If videoId is an 8-character Saavn ID (not YouTube)
        if (videoId.length in 6..12 && !videoId.contains("-") && !videoId.contains("_")) {
            val direct = fetchSaavnStreamById(videoId)
            if (direct != null) return@withContext direct
        }

        // 5. Fallback: YouTube NewPipe stream extractor
        try {
            val streamExtractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=${videoId}")
            streamExtractor.fetchPage()
            val audioStreams = streamExtractor.audioStreams
            
            if (audioStreams.isNotEmpty()) {
                val url = if (lowBitrate) {
                    audioStreams.minByOrNull { it.bitrate }?.content
                } else {
                    audioStreams.maxByOrNull { it.bitrate }?.content
                }
                if (url != null) return@withContext url
            }
            
            val videoStreams = streamExtractor.videoStreams
            if (videoStreams.isNotEmpty()) {
                return@withContext videoStreams.minByOrNull { it.resolution }?.content
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    suspend fun fetchDynamicArtistAlbums(artistName: String): List<Triple<String, String, String>> = withContext(Dispatchers.IO) {
        val albums = mutableListOf<Triple<String, String, String>>()
        try {
            val encoded = java.net.URLEncoder.encode(artistName, "UTF-8")
            val url = "https://itunes.apple.com/search?term=$encoded&entity=album&limit=8"
            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
                val jsonStr = response.body!!.string()
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val root = json.parseToJsonElement(jsonStr).let { it as kotlinx.serialization.json.JsonObject }
                val results = root["results"] as? kotlinx.serialization.json.JsonArray
                results?.forEach { element ->
                    val obj = element as kotlinx.serialization.json.JsonObject
                    val collectionName = obj["collectionName"]?.let { (it as kotlinx.serialization.json.JsonPrimitive).content } ?: ""
                    val collectionArtist = obj["artistName"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.orEmpty()
                    val releaseDate = obj["releaseDate"]?.let { (it as kotlinx.serialization.json.JsonPrimitive).content }?.take(4) ?: "Album"
                    val art = obj["artworkUrl100"]?.let { (it as kotlinx.serialization.json.JsonPrimitive).content }
                        ?.replace("100x100bb.jpg", "600x600bb.jpg") ?: ""
                    if (collectionName.isNotBlank() && art.isNotBlank() && collectionArtist.contains(artistName, ignoreCase = true)) {
                        albums.add(Triple(collectionName, "Album • $releaseDate", art))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext albums
    }
}

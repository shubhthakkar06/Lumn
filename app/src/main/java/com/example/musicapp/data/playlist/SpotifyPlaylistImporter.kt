package com.example.musicapp.data.playlist

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.example.musicapp.data.DefaultDataRepository
import com.example.musicapp.data.network.SongInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

data class SpotifyImportedTrack(
    val id: String,
    val title: String,
    val artists: String,
    val albumName: String = "",
    val albumArtUrl: String = "",
    val durationSeconds: Long = 0L,
    val resolvedSong: SongInfo? = null
)

data class SpotifyImportedPlaylist(
    val id: String,
    val name: String,
    val spotifyUrl: String,
    val tracks: List<SpotifyImportedTrack>,
    val coverUrl: String = ""
)

/**
 * Reads playlist metadata from Spotify using Authorization Code + PKCE.
 * No Spotify client secret is embedded in the mobile app.
 */
object SpotifyPlaylistImporter {
    const val CLIENT_ID = "f1671ccb41f146f78d85f11898de74ef"
    const val REDIRECT_URI = "http://127.0.0.1:8888/callback"
    private const val CALLBACK_PORT = 8888
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun import(context: Context, playlistUrl: String): SpotifyImportedPlaylist = withContext(Dispatchers.IO) {
        require(CLIENT_ID.isNotBlank()) { "Spotify is not configured yet." }
        val playlistId = extractPlaylistId(playlistUrl)
            ?: throw IllegalArgumentException("Paste a Spotify playlist link, such as open.spotify.com/playlist/…")
        val token = authorize(context)
        val playlist = apiGet("https://api.spotify.com/v1/playlists/$playlistId?market=US", token)
        val name = (playlist["name"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: "Spotify Playlist"
        val playlistCover = playlistImages(playlist)

        val tracks = mutableListOf<SpotifyImportedTrack>()
        var nextUrl: String? = "https://api.spotify.com/v1/playlists/$playlistId/items?limit=100&market=US"
        var pages = 0
        while (nextUrl != null && pages < 20) {
            val page = apiGet(nextUrl, token)
            val entries = page["items"] as? JsonArray ?: JsonArray(emptyList())
            entries.forEach { entry ->
                val obj = entry as? JsonObject ?: return@forEach
                val item = (obj["item"] as? JsonObject) ?: (obj["track"] as? JsonObject) ?: return@forEach
                val title = (item["name"] as? JsonPrimitive)?.content?.trim().orEmpty()
                val artists = (item["artists"] as? JsonArray)?.mapNotNull { artist ->
                    (artist as? JsonObject)?.get("name")?.let { (it as? JsonPrimitive)?.content }
                }?.filter { it.isNotBlank() }?.distinct()?.joinToString(", ").orEmpty()
                val id = (item["id"] as? JsonPrimitive)?.content.orEmpty()
                val album = item["album"] as? JsonObject
                val albumName = (album?.get("name") as? JsonPrimitive)?.content.orEmpty()
                val albumCover = album?.let(::playlistImages).orEmpty()
                val durationMs = (item["duration_ms"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
                if (title.isNotBlank() && artists.isNotBlank()) {
                    tracks += SpotifyImportedTrack(
                        id.ifBlank { "${title}_$artists" }, title, artists, albumName, albumCover,
                        (durationMs / 1000L).takeIf { it > 0L } ?: 0L
                    )
                }
            }
            nextUrl = parseSpotifyNextPageUrl(page)
            pages++
        }
        if (tracks.isEmpty()) throw IllegalStateException("No playable tracks were found in that Spotify playlist.")
        val repository = DefaultDataRepository(context.applicationContext)
        val semaphore = Semaphore(3)
        val matchedTracks = coroutineScope {
            tracks.distinctBy { it.id }.map { track ->
                async {
                    semaphore.withPermit {
                        val primary = runCatching {
                            repository.searchSongs("${track.title} ${track.artists}")
                        }.getOrDefault(emptyList())
                        val firstMatch = SpotifyTrackMatcher.bestMatch(track, primary)
                        val result = if (firstMatch != null) firstMatch else {
                            val titleOnly = runCatching { repository.searchSongs(track.title) }.getOrDefault(emptyList())
                            SpotifyTrackMatcher.bestMatch(track, titleOnly)
                        }
                        track.copy(resolvedSong = result)
                    }
                }
            }.awaitAll()
        }
        SpotifyImportedPlaylist(
            id = playlistId,
            name = name,
            spotifyUrl = "https://open.spotify.com/playlist/$playlistId",
            tracks = matchedTracks,
            coverUrl = playlistCover
        )
    }

    private fun playlistImages(obj: JsonObject): String =
        ((obj["images"] as? JsonArray)?.firstOrNull() as? JsonObject)
            ?.get("url")?.let { (it as? JsonPrimitive)?.content }.orEmpty()

    private fun extractPlaylistId(input: String): String? {
        val value = input.trim()
        val uri = runCatching { Uri.parse(value) }.getOrNull()
        val path = uri?.pathSegments.orEmpty()
        val fromUrl = path.windowed(2).firstOrNull { it[0] == "playlist" }?.get(1)
        val fromSpotifyUri = value.substringAfter("spotify:playlist:", "").substringBefore('?')
        val candidate = fromUrl ?: fromSpotifyUri.takeIf { it.isNotBlank() }
        return candidate?.takeIf { it.matches(Regex("[A-Za-z0-9]{10,64}")) }
    }

    private suspend fun authorize(context: Context): String {
        val server = withContext(Dispatchers.IO) {
            ServerSocket().apply {
                reuseAddress = true
                soTimeout = 180_000
                bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), CALLBACK_PORT), 1)
            }
        }
        try {
            val verifier = randomUrlToken(64)
            val challenge = Base64.encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
            val state = randomUrlToken(24)
            val authUri = Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
                .appendQueryParameter("client_id", CLIENT_ID)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", REDIRECT_URI)
                .appendQueryParameter("scope", "playlist-read-private playlist-read-collaborative")
                .appendQueryParameter("state", state)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("code_challenge", challenge)
                .build()

            withContext(Dispatchers.Main) {
                context.startActivity(Intent(Intent.ACTION_VIEW, authUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }

            val code = withContext(Dispatchers.IO) {
                server.use { socket ->
                    socket.accept().use { client ->
                        client.soTimeout = 15_000
                        val reader = client.getInputStream().bufferedReader(Charsets.US_ASCII)
                        val requestLine = reader.readLine().orEmpty()
                        // Drain request headers before sending the browser response.
                        while (!reader.readLine().isNullOrEmpty()) Unit
                        val requestTarget = requestLine.split(' ').getOrNull(1)
                            ?: throw IllegalStateException("Spotify returned an invalid sign-in response.")
                        val callback = Uri.parse("http://127.0.0.1$requestTarget")
                        val html = "<html><body style='font-family:sans-serif;background:#10151f;color:white;padding:32px'><h2>Lumn connected</h2><p>You can return to Lumn to finish importing your playlist.</p></body></html>"
                        val body = html.toByteArray(Charsets.UTF_8)
                        val output = client.getOutputStream()
                        output.write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                        output.write(body)
                        output.flush()

                        if (callback.getQueryParameter("state") != state) {
                            throw SecurityException("Spotify sign-in could not be verified. Please try again.")
                        }
                        callback.getQueryParameter("error")?.let {
                            throw IllegalStateException("Spotify authorization was cancelled.")
                        }
                        callback.getQueryParameter("code")
                            ?: throw IllegalStateException("Spotify did not return an authorization code.")
                    }
                }
            }

            return exchangeCode(code, verifier)
        } catch (e: java.net.SocketTimeoutException) {
            throw IllegalStateException("Spotify sign-in timed out. Please try again.", e)
        } finally {
            runCatching { server.close() }
        }
    }

    private fun exchangeCode(code: String, verifier: String): String {
        val body = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", REDIRECT_URI)
            .add("code_verifier", verifier)
            .build()
        val request = Request.Builder().url("https://accounts.spotify.com/api/token").post(body).build()
        http.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Spotify sign-in failed (${response.code}). Please check the registered redirect URI.")
            }
            val accessToken = (json.parseToJsonElement(responseBody) as? JsonObject)
                ?.get("access_token")?.let { (it as? JsonPrimitive)?.content }
            return accessToken?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Spotify did not provide an access token.")
        }
    }

    private fun apiGet(url: String, token: String): JsonObject {
        val safeUrl = url.toHttpUrlOrNull()
        require(safeUrl?.scheme == "https" && safeUrl.host == "api.spotify.com" &&
            safeUrl.encodedPath.startsWith("/v1/playlists/")) {
            "Spotify returned an invalid playlist page URL."
        }
        val request = Request.Builder().url(safeUrl).header("Authorization", "Bearer $token").build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = when (response.code) {
                    401 -> "Spotify sign-in expired. Please try again."
                    403 -> "Spotify only allows importing playlists you own or collaborate on."
                    404 -> "That Spotify playlist could not be found."
                    429 -> "Spotify is rate limiting requests. Please wait and retry."
                    else -> "Spotify playlist request failed (${response.code})."
                }
                throw IllegalStateException(message)
            }
            return json.parseToJsonElement(body) as? JsonObject
                ?: throw IllegalStateException("Spotify returned an unreadable playlist.")
        }
    }

    private fun randomUrlToken(bytes: Int): String {
        val random = ByteArray(bytes).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(random, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}

/** Match Spotify's identity to Lumn's catalog without requiring identical artist spelling. */
internal object SpotifyTrackMatcher {
    private val distinctVersionTerms = setOf(
        "remix", "live", "acoustic", "instrumental", "karaoke", "cover", "sped", "slowed", "garba", "unplugged"
    )

    fun bestMatch(track: SpotifyImportedTrack, candidates: List<SongInfo>): SongInfo? = candidates
        .mapNotNull { candidate -> score(track, candidate)?.let { candidate to it } }
        .maxWithOrNull(compareBy<Pair<SongInfo, Double>> { it.second }
            .thenBy { !it.first.audioStreamUrl.isNullOrBlank() })
        ?.first

    private fun score(source: SpotifyImportedTrack, candidate: SongInfo): Double? {
        val sourceTitle = normalize(source.title)
        val candidateTitle = normalize(candidate.title)
        if (sourceTitle.isBlank() || candidateTitle.isBlank()) return null

        val sourceVersions = versionTerms(sourceTitle)
        val candidateVersions = versionTerms(candidateTitle)
        if (candidateVersions.any { it !in sourceVersions }) return null

        val titleEdit = similarity(sourceTitle, candidateTitle)
        val sourceTokens = sourceTitle.split(' ').filter(String::isNotBlank).toSet()
        val candidateTokens = candidateTitle.split(' ').filter(String::isNotBlank).toSet()
        val tokenCoverage = sourceTokens.intersect(candidateTokens).size.toDouble() / sourceTokens.size.coerceAtLeast(1)
        val titleScore = titleEdit * 0.6 + tokenCoverage * 0.4
        val artistTokens = normalize(source.artists).split(' ').filter { it.length > 1 }.toSet()
        val candidateArtistTokens = normalize(candidate.artist).split(' ').filter { it.length > 1 }.toSet()
        val artistOverlap = artistTokens.intersect(candidateArtistTokens).size.toDouble() / artistTokens.size.coerceAtLeast(1)
        val durationDelta = if (source.durationSeconds > 0 && candidate.durationSeconds > 0) {
            kotlin.math.abs(source.durationSeconds - candidate.durationSeconds)
        } else null
        val durationClose = durationDelta != null && durationDelta <= maxOf(8L, source.durationSeconds / 30L)

        // Exact duration and title identity outweigh provider artist-credit variations.
        if (titleScore < 0.78) return null
        if (!durationClose && artistOverlap < 0.25 && titleScore < 0.96) return null
        if (durationDelta != null && durationDelta > maxOf(18L, source.durationSeconds / 8L) && titleScore < 0.97) return null

        val durationScore = when {
            durationDelta == null -> 0.5
            durationClose -> 1.0 - durationDelta.toDouble() / maxOf(8L, source.durationSeconds / 30L)
            else -> (1.0 - durationDelta.toDouble() / maxOf(18L, source.durationSeconds / 8L)).coerceAtLeast(0.0)
        }
        return titleScore * 0.72 + durationScore * 0.18 + artistOverlap * 0.10
    }

    private fun normalize(value: String): String = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .replace(Regex("\\b(feat|featuring|ft)\\b.*$"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private fun versionTerms(title: String): Set<String> = distinctVersionTerms.filterTo(mutableSetOf()) { term ->
        Regex("\\b${Regex.escape(term)}\\b").containsMatchIn(title)
    }

    private fun similarity(a: String, b: String): Double {
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
        return 1.0 - previous[b.length].toDouble() / maxOf(a.length, b.length).coerceAtLeast(1)
    }
}

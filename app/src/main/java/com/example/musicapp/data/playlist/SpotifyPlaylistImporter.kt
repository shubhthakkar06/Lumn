package com.example.musicapp.data.playlist

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
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

data class SpotifyImportedTrack(val id: String, val title: String, val artists: String)

data class SpotifyImportedPlaylist(
    val id: String,
    val name: String,
    val spotifyUrl: String,
    val tracks: List<SpotifyImportedTrack>
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
                if (title.isNotBlank() && artists.isNotBlank()) {
                    tracks += SpotifyImportedTrack(id.ifBlank { "${title}_$artists" }, title, artists)
                }
            }
            nextUrl = parseSpotifyNextPageUrl(page)
            pages++
        }
        if (tracks.isEmpty()) throw IllegalStateException("No playable tracks were found in that Spotify playlist.")
        SpotifyImportedPlaylist(
            id = playlistId,
            name = name,
            spotifyUrl = "https://open.spotify.com/playlist/$playlistId",
            tracks = tracks.distinctBy { it.id }
        )
    }

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

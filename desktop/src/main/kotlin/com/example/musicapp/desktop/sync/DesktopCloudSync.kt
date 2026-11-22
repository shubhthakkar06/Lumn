package com.example.musicapp.desktop.sync

import com.example.musicapp.data.cloud.CloudJson
import com.example.musicapp.data.cloud.CloudPaths
import com.example.musicapp.data.cloud.CloudPlaylist
import com.example.musicapp.data.cloud.LibraryMerge
import com.example.musicapp.data.cloud.LibrarySnapshot
import com.example.musicapp.data.cloud.RemoteItem
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.desktop.data.DesktopLibraryManager
import com.example.musicapp.desktop.data.DesktopUserManager
import com.example.musicapp.desktop.model.DesktopPlaylist
import com.example.musicapp.desktop.model.DesktopSong
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Live library sync on the Mac, through the same Firebase Realtime Database as the phone.
 *
 * The Mac signs in anonymously, shows a 6-digit code, and the phone (signed in with Google)
 * grants that Mac access to its library. From then on likes and playlists stream both ways
 * within a second or two. Uses Firebase's REST and streaming APIs, so no Google SDK is needed.
 */
class DesktopCloudSync(
    private val library: DesktopLibraryManager,
    private val users: DesktopUserManager
) {
    sealed interface State {
        data object NotLinked : State
        data class ShowingCode(val code: String) : State
        data class Live(val email: String) : State
        data class Error(val message: String) : State
    }

    @Serializable
    private data class Saved(
        val deviceUid: String = "",
        val refreshToken: String = "",
        val accountUid: String = "",
        val email: String = "",
        val name: String = ""
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val dir = File(System.getProperty("user.home"), ".rivara").apply { mkdirs() }
    private val savedFile = File(dir, "cloud.json")
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    private val streamHttp = http.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> println("Cloud sync: ${e.message}") })
    private val mutex = kotlinx.coroutines.sync.Mutex()

    private val _state = MutableStateFlow<State>(State.NotLinked)
    val state: StateFlow<State> = _state.asStateFlow()

    private var saved = runCatching { json.decodeFromString(Saved.serializer(), savedFile.readText()) }.getOrDefault(Saved())
    private var idToken = ""
    private var tokenExpiresAt = 0L
    private var remoteTree: JsonElement = JsonNull
    private var streamJob: Job? = null
    private var linkJob: Job? = null

    /** Last cloud versions, so fields the Mac doesn't model are never lost when it writes back. */
    private val playlistCache = HashMap<String, CloudPlaylist>()
    private val songCache = HashMap<String, SongInfo>()

    fun start() {
        if (saved.accountUid.isNotBlank()) connect()
        // Local edits go up after a short pause.
        scope.launch {
            combine(library.playlists, library.playlistSongs, library.likedSongs, com.example.musicapp.desktop.data.DesktopShowFollows.shows) { _, _, _, _ -> Unit }
                .drop(1)
                .debounce(800)
                .collect { if (_state.value is State.Live) syncNow() }
        }
    }

    // ---------------------------------------------------------------- linking

    /** Shows a code to enter on the phone, then waits for the phone to approve it. */
    fun beginLink() {
        linkJob?.cancel()
        linkJob = scope.launch {
            runCatching {
                ensureDeviceAccount()
                val code = (100000..999999).random().toString()
                val body = buildJsonObject {
                    put("device", saved.deviceUid)
                    put("name", "Mac (${runCatching { InetAddress.getLocalHost().hostName.substringBefore('.') }.getOrDefault("Rivara")})")
                    put("at", buildJsonObject { put(".sv", "timestamp") })
                }
                request("PUT", "links/$code", body)
                _state.value = State.ShowingCode(code)
                val deadline = System.currentTimeMillis() + 15 * 60_000L
                while (isActive && System.currentTimeMillis() < deadline) {
                    delay(2_000)
                    val link = request("GET", "links/$code") as? JsonObject ?: continue
                    val uid = link["uid"]?.jsonPrimitive?.contentOrNull ?: continue
                    saved = saved.copy(
                        accountUid = uid,
                        email = link["email"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        name = link["displayName"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    )
                    persist()
                    runCatching { request("DELETE", "links/$code") }
                    withContext(Dispatchers.Main) {
                        users.signIn(saved.name.ifBlank { saved.email.substringBefore('@') }, saved.email)
                    }
                    connect()
                    return@launch
                }
                _state.value = State.Error("The code expired. Try again.")
            }.onFailure { _state.value = State.Error(friendly(it)) }
        }
    }

    fun cancelLink() {
        linkJob?.cancel()
        if (_state.value is State.ShowingCode) _state.value = State.NotLinked
    }

    fun unlink() {
        streamJob?.cancel()
        saved = Saved(deviceUid = saved.deviceUid, refreshToken = saved.refreshToken)
        persist()
        _state.value = State.NotLinked
    }

    // ---------------------------------------------------------------- live stream

    private fun connect() {
        streamJob?.cancel()
        streamJob = scope.launch {
            var backoff = 2_000L
            while (isActive) {
                runCatching { stream() }.onFailure {
                    if (it is CancellationException) throw it
                    _state.value = State.Error(friendly(it))
                }
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(60_000L)
            }
        }
    }

    /** Server-sent events: the full library first, then every change as it happens. */
    private suspend fun stream() {
        val token = token()
        val req = Request.Builder()
            .url("${DB_URL}/users/${saved.accountUid}/data.json?auth=$token")
            .header("Accept", "text/event-stream")
            .build()
        streamHttp.newCall(req).execute().use { response ->
            if (response.code == 401) error("This Mac's access was removed. Link it again from your phone.")
            if (!response.isSuccessful) error("Cloud library unavailable (${response.code})")
            val source = response.body?.source() ?: return
            var event = ""
            while (currentCoroutineContext().isActive) {
                val line = source.readUtf8Line() ?: break
                when {
                    line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                    line.startsWith("data:") -> {
                        val data = line.removePrefix("data:").trim()
                        when (event) {
                            "put", "patch" -> {
                                val msg = json.parseToJsonElement(data).jsonObject
                                val path = msg["path"]?.jsonPrimitive?.content.orEmpty()
                                val value = msg["data"] ?: JsonNull
                                remoteTree = if (event == "put") setAt(remoteTree, segments(path), value) else patchAt(remoteTree, segments(path), value)
                                _state.value = State.Live(saved.email)
                                syncNow()
                            }
                            "auth_revoked" -> return // token expired: reconnect with a fresh one
                            "cancel" -> error("This Mac no longer has access. Link it again from your phone.")
                        }
                    }
                }
                if (System.currentTimeMillis() > tokenExpiresAt - 60_000L) return
            }
        }
    }

    // ---------------------------------------------------------------- merge

    private suspend fun syncNow(): Unit = mutex.withLock {
        if (saved.accountUid.isBlank()) return@withLock
        val remote = parseRemote(remoteTree)
        remote[CloudPaths.PLAYLISTS]?.values?.forEach { item -> CloudJson.playlistOrNull(item.json)?.let { playlistCache[it.id] = it; it.songs.forEach { s -> songCache[s.videoId] = s } } }
        remote[CloudPaths.LIKED]?.values?.forEach { item -> CloudJson.songOrNull(item.json)?.let { songCache[it.videoId] = it } }
        val base = loadBase()
        val local = withContext(Dispatchers.Main) { localSnapshot(base) }
        val result = LibraryMerge.merge(base, local, remote, System.currentTimeMillis())
        if (result.localChanged) withContext(Dispatchers.Main) { applyLocal(result.local) }
        saveBase(result.base)
        if (result.writes.isNotEmpty()) {
            val body = buildJsonObject {
                result.writes.forEach { (path, item) ->
                    if (item == null) put(path, JsonNull)
                    else put(path, buildJsonObject { put("json", item.json); put("at", item.at) })
                }
            }
            request("PATCH", "users/${saved.accountUid}/data", body)
        }
    }

    private fun localSnapshot(base: LibrarySnapshot): LibrarySnapshot {
        val songsById = library.playlistSongs.value
        val playlists = library.playlists.value.map { p ->
            val songs = songsById[p.id].orEmpty().map(::toSongInfo)
            val cached = playlistCache[p.id]
            CloudPlaylist(
                id = p.id, name = p.name, creator = cached?.creator ?: p.creator, coverUrl = p.coverUrl,
                songs = songs, isPinned = cached?.isPinned ?: false,
                createdAtMs = cached?.createdAtMs ?: p.id.removePrefix("pl_").toLongOrNull() ?: 0L,
                sourceUrl = cached?.sourceUrl.orEmpty()
            )
        }
        return LibrarySnapshot.of(
            CloudPaths.PLAYLISTS to CloudJson.playlistSnapshot(playlists),
            CloudPaths.LIKED to CloudJson.likedSnapshot(library.likedSongs.value.map(::toSongInfo)),
            // The Mac doesn't manage followed artists: reporting the base leaves the cloud's copy as is.
            CloudPaths.ARTISTS to base.section(CloudPaths.ARTISTS),
            CloudPaths.SHOWS to CloudJson.showSnapshot(com.example.musicapp.desktop.data.DesktopShowFollows.shows.value)
        )
    }

    private fun applyLocal(merged: LibrarySnapshot) {
        val playlists = merged.section(CloudPaths.PLAYLISTS).values.mapNotNull(CloudJson::playlistOrNull)
        playlists.forEach { playlistCache[it.id] = it }
        val liked = merged.section(CloudPaths.LIKED).values.mapNotNull(CloudJson::songOrNull)
        com.example.musicapp.desktop.data.DesktopShowFollows.replace(merged.section(CloudPaths.SHOWS).values.mapNotNull(CloudJson::showOrNull))
        library.replaceFromCloud(
            playlists = playlists.map { DesktopPlaylist(it.id, it.name, it.creator, it.songs.size, it.coverUrl) },
            songs = playlists.associate { p -> p.id to p.songs.map(::toDesktop) },
            liked = liked.map { toDesktop(it).copy(isLiked = true) }
        )
    }

    /** A Mac song as cloud JSON; untouched songs keep every field the phone stored. */
    private fun toSongInfo(s: DesktopSong): SongInfo {
        songCache[s.id]?.let { cached -> if (toDesktop(cached).sameAs(s)) return cached }
        return SongInfo(
            videoId = s.id, title = s.title, artist = s.artist, albumArtUrl = s.albumArtUrl,
            audioStreamUrl = s.audioUrl?.takeIf { s.isEpisode }, albumName = s.album, durationSeconds = s.durationSeconds,
            isEpisode = s.isEpisode, description = s.description, releaseDate = s.releaseDate
        )
    }

    private fun toDesktop(s: SongInfo) = DesktopSong(
        id = s.videoId, title = s.title, artist = s.artist, album = s.albumName, durationSeconds = s.durationSeconds,
        albumArtUrl = s.albumArtUrl, audioUrl = s.audioStreamUrl?.takeIf { s.isEpisode }, isEpisode = s.isEpisode,
        description = s.description, releaseDate = s.releaseDate
    )

    private fun DesktopSong.sameAs(o: DesktopSong) = id == o.id && title == o.title && artist == o.artist &&
        album == o.album && durationSeconds == o.durationSeconds && albumArtUrl == o.albumArtUrl

    private fun parseRemote(tree: JsonElement): Map<String, Map<String, RemoteItem>> {
        val root = tree as? JsonObject ?: return emptyMap()
        return CloudPaths.SECTIONS.associateWith { section ->
            (root[section] as? JsonObject).orEmpty().mapNotNull { (key, v) ->
                val o = v as? JsonObject ?: return@mapNotNull null
                val text = o["json"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                key to RemoteItem(text, o["at"]?.jsonPrimitive?.longOrNull ?: 0L)
            }.toMap()
        }
    }

    private fun baseFile() = File(dir, "cloud_base_${saved.accountUid.replace(Regex("[^A-Za-z0-9_-]"), "_")}.json")
    private fun loadBase() = runCatching { json.decodeFromString(LibrarySnapshot.serializer(), baseFile().readText()) }.getOrDefault(LibrarySnapshot())
    private fun saveBase(base: LibrarySnapshot) {
        runCatching { baseFile().writeText(json.encodeToString(LibrarySnapshot.serializer(), base)) }
    }

    // ---------------------------------------------------------------- Firebase REST

    private fun ensureDeviceAccount() {
        if (saved.deviceUid.isNotBlank() && saved.refreshToken.isNotBlank()) return
        val req = Request.Builder()
            .url("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$API_KEY")
            .post("""{"returnSecureToken":true}""".toRequestBody("application/json".toMediaType()))
            .build()
        val body = http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) error(if (text.contains("ADMIN_ONLY_OPERATION") || text.contains("OPERATION_NOT_ALLOWED")) "Turn on Anonymous sign-in in the Firebase console (Authentication → Sign-in method)." else "Couldn't reach Firebase (${r.code})")
            json.parseToJsonElement(text).jsonObject
        }
        saved = saved.copy(deviceUid = body.str("localId"), refreshToken = body.str("refreshToken"))
        idToken = body.str("idToken")
        tokenExpiresAt = System.currentTimeMillis() + (body.str("expiresIn").toLongOrNull() ?: 3600) * 1000
        persist()
    }

    private fun token(): String {
        if (idToken.isNotBlank() && System.currentTimeMillis() < tokenExpiresAt - 60_000L) return idToken
        ensureDeviceAccount()
        if (idToken.isNotBlank() && System.currentTimeMillis() < tokenExpiresAt - 60_000L) return idToken
        val req = Request.Builder()
            .url("https://securetoken.googleapis.com/v1/token?key=$API_KEY")
            .post(FormBody.Builder().add("grant_type", "refresh_token").add("refresh_token", saved.refreshToken).build())
            .build()
        val body = http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("This Mac's sign-in expired. Link it again from your phone.")
            json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject
        }
        idToken = body.str("id_token")
        tokenExpiresAt = System.currentTimeMillis() + (body.str("expires_in").toLongOrNull() ?: 3600) * 1000
        body.str("refresh_token").takeIf { it.isNotBlank() }?.let { saved = saved.copy(refreshToken = it); persist() }
        return idToken
    }

    private fun request(method: String, path: String, body: JsonElement? = null): JsonElement? {
        val url = "$DB_URL/$path.json?auth=${token()}"
        val rb = body?.toString()?.toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(url).method(method, rb).build()
        return http.newCall(req).execute().use { r ->
            if (r.code == 401) error("Permission denied by the cloud database (check its rules).")
            if (r.code == 404) error("The cloud database doesn't exist yet. Create the Realtime Database in the Firebase console.")
            if (!r.isSuccessful) error("Cloud request failed (${r.code})")
            r.body?.string()?.takeIf { it.isNotBlank() }?.let { json.parseToJsonElement(it) }
        }
    }

    private fun persist() {
        runCatching { savedFile.writeText(json.encodeToString(Saved.serializer(), saved)) }
    }

    private fun friendly(e: Throwable): String = e.message ?: "Cloud sync failed"

    private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.contentOrNull.orEmpty()

    // ---------------------------------------------------------------- JSON tree helpers

    private fun segments(path: String) = path.split('/').filter { it.isNotBlank() }

    private fun setAt(node: JsonElement, path: List<String>, value: JsonElement): JsonElement {
        if (path.isEmpty()) return value
        val obj = (node as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        val child = setAt(obj[path[0]] ?: JsonNull, path.drop(1), value)
        if (child is JsonNull) obj.remove(path[0]) else obj[path[0]] = child
        return JsonObject(obj)
    }

    private fun patchAt(node: JsonElement, path: List<String>, value: JsonElement): JsonElement {
        var result = node
        (value as? JsonObject)?.forEach { (k, v) -> result = setAt(result, path + segments(k), v) }
        return result
    }

    companion object {
        /** Firebase project config (the same public values the Android app ships in google-services.json). */
        private val config: Map<String, String> = runCatching {
            File(System.getProperty("user.home"), ".rivara/firebase.properties").readLines()
                .mapNotNull { line -> line.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }.toMap()
        }.getOrDefault(emptyMap())
        val API_KEY: String = config["apiKey"] ?: "AIzaSyCBFXfcc7hZGnMytYkRBs1ax6RDidCDUUs"
        val DB_URL: String = (config["databaseUrl"] ?: "https://lumn-24ba5-default-rtdb.firebaseio.com").trimEnd('/')
    }
}

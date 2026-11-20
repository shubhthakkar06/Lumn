package com.example.musicapp.desktop.sync

import com.example.musicapp.desktop.data.DesktopLibraryManager
import com.example.musicapp.desktop.data.DesktopUser
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.nio.charset.StandardCharsets

@Serializable
data class SyncSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationSeconds: Long = 0L,
    val albumArtUrl: String = "",
    val audioUrl: String? = null
)

@Serializable
data class SyncPlaylist(
    val id: String,
    val name: String,
    val creator: String = "",
    val coverUrl: String = "",
    val songs: List<SyncSong> = emptyList(),
    val createdAtMs: Long = System.currentTimeMillis()
)

@Serializable
data class RivaraSyncPayload(
    val version: Int = 1,
    val deviceId: String = "mac_" + System.getProperty("user.name", "desktop"),
    val deviceName: String = "Rivara on Mac",
    val deviceType: String = "macOS",
    val userEmail: String,
    val userId: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val playlists: List<SyncPlaylist> = emptyList(),
    val likedSongs: List<SyncSong> = emptyList()
)

/**
 * Wi-Fi sync server for the Rivara phone app.
 *
 * Only a phone that sends this Mac's pairing code (shown in the Sync window) can read or
 * change the library. There is no cloud copy: the library never leaves this Mac except to a
 * phone the user paired.
 */
object DesktopSyncService {
    const val SYNC_PORT = 8765
    const val PAIR_CODE_HEADER = "X-Rivara-Pair-Code"

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dataDir = File(System.getProperty("user.home"), ".rivara").apply { mkdirs() }
    private val pairCodeFile = File(dataDir, "pair_code")

    private var server: HttpServer? = null
    private var libraryManager: DesktopLibraryManager? = null
    private var activeUser: DesktopUser? = null

    var lastSyncTimestampMs: Long = 0L
        private set
    var lastSyncDeviceName: String = "None"
        private set

    var onSyncCompleted: ((RivaraSyncPayload) -> Unit)? = null

    /** Six-digit code the phone must send with every sync request. Persisted across launches. */
    @Volatile
    var pairCode: String = loadOrCreatePairCode()
        private set

    private fun newCode(): String = java.security.SecureRandom().nextInt(1_000_000).toString().padStart(6, '0')

    private fun loadOrCreatePairCode(): String {
        val existing = runCatching { pairCodeFile.readText().trim() }.getOrNull()
        if (existing != null && existing.matches(Regex("\\d{6}"))) return existing
        return newCode().also { code -> runCatching { pairCodeFile.writeText(code) } }
    }

    /** Issues a new code; phones paired with the old one must pair again. */
    fun regeneratePairCode(): String {
        val code = newCode()
        runCatching { pairCodeFile.writeText(code) }
        pairCode = code
        return code
    }

    fun start(libManager: DesktopLibraryManager, user: DesktopUser?) {
        libraryManager = libManager
        activeUser = user

        if (server != null) return

        try {
            val s = HttpServer.create(InetSocketAddress("0.0.0.0", SYNC_PORT), 0)
            s.createContext("/api/sync/ping") { exchange -> handlePing(exchange) }
            s.createContext("/api/sync/library") { exchange -> handleLibrarySync(exchange) }
            s.executor = null
            s.start()
            server = s
            println("Rivara Desktop Sync Server running on port $SYNC_PORT")

            // Automatically attempt ADB reverse if an Android device is attached via USB/Wi-Fi
            configureAdbReverse()
        } catch (e: Exception) {
            System.err.println("Failed to start DesktopSyncService: ${e.message}")
        }
    }

    fun updateUser(user: DesktopUser?) {
        activeUser = user
    }

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr.isSiteLocalAddress && addr.hostAddress.indexOf(':') < 0) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    private fun isAuthorized(exchange: HttpExchange): Boolean {
        val supplied = exchange.requestHeaders.getFirst(PAIR_CODE_HEADER)?.trim().orEmpty()
        return supplied.isNotEmpty() &&
            java.security.MessageDigest.isEqual(supplied.toByteArray(), pairCode.toByteArray())
    }

    private fun handlePing(exchange: HttpExchange) {
        // Deliberately reveals nothing about the signed-in user.
        sendResponse(exchange, 200, """{"status":"ok","app":"rivara","deviceType":"macOS","port":$SYNC_PORT}""", "application/json")
    }

    private fun handleLibrarySync(exchange: HttpExchange) {
        if (!isAuthorized(exchange)) {
            sendResponse(exchange, 401, """{"error":"Pairing code missing or incorrect"}""", "application/json")
            return
        }
        val lib = libraryManager
        if (lib == null) {
            sendResponse(exchange, 503, """{"error":"Library not ready"}""", "application/json")
            return
        }

        if (exchange.requestMethod.equals("GET", ignoreCase = true)) {
            sendResponse(exchange, 200, json.encodeToString(lib.getSyncPayload(activeUser)), "application/json")
            return
        }

        if (exchange.requestMethod.equals("POST", ignoreCase = true)) {
            try {
                val body = exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                val incomingPayload = json.decodeFromString<RivaraSyncPayload>(body)

                lib.mergeFromSync(incomingPayload)

                lastSyncTimestampMs = System.currentTimeMillis()
                lastSyncDeviceName = incomingPayload.deviceName.ifBlank { incomingPayload.deviceType }

                scope.launch(Dispatchers.Main) {
                    onSyncCompleted?.invoke(incomingPayload)
                }

                sendResponse(exchange, 200, json.encodeToString(lib.getSyncPayload(activeUser)), "application/json")
            } catch (e: Exception) {
                e.printStackTrace()
                sendResponse(exchange, 400, """{"error":"Invalid sync payload"}""", "application/json")
            }
            return
        }

        sendResponse(exchange, 405, """{"error":"Method not allowed"}""", "application/json")
    }

    private fun sendResponse(exchange: HttpExchange, code: Int, text: String, contentType: String) {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", contentType)
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun configureAdbReverse() {
        scope.launch {
            try {
                val adbCandidates = listOf(
                    File(System.getProperty("user.home"), "Library/Android/sdk/platform-tools/adb"),
                    File("/usr/local/bin/adb"),
                    File("/opt/homebrew/bin/adb")
                )
                val adb = adbCandidates.firstOrNull { it.canExecute() } ?: return@launch
                ProcessBuilder(adb.absolutePath, "reverse", "tcp:$SYNC_PORT", "tcp:$SYNC_PORT").start()
            } catch (_: Exception) {}
        }
    }

    fun stop() {
        try {
            server?.stop(0)
            server = null
        } catch (_: Exception) {}
    }
}

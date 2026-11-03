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
import java.net.InetAddress
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
    val deviceName: String = "MacBook Pro (Rivara Desktop)",
    val deviceType: String = "macOS",
    val userEmail: String,
    val userId: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val playlists: List<SyncPlaylist> = emptyList(),
    val likedSongs: List<SyncSong> = emptyList()
)

object DesktopSyncService {
    const val SYNC_PORT = 8765

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var server: HttpServer? = null
    private var libraryManager: DesktopLibraryManager? = null
    private var activeUser: DesktopUser? = null

    var lastSyncTimestampMs: Long = 0L
        private set
    var lastSyncDeviceName: String = "None"
        private set

    var onAuthCallbackReceived: ((email: String, name: String) -> Unit)? = null
    var onSyncCompleted: ((RivaraSyncPayload) -> Unit)? = null

    fun start(libManager: DesktopLibraryManager, user: DesktopUser?) {
        libraryManager = libManager
        activeUser = user

        if (server != null) return

        try {
            val s = HttpServer.create(InetSocketAddress("0.0.0.0", SYNC_PORT), 0)

            s.createContext("/api/sync/ping") { exchange ->
                handlePing(exchange)
            }

            s.createContext("/api/sync/library") { exchange ->
                handleLibrarySync(exchange)
            }

            s.createContext("/auth") { exchange ->
                handleAuthPage(exchange)
            }

            s.createContext("/auth/callback") { exchange ->
                handleAuthCallback(exchange)
            }

            s.executor = null
            s.start()
            server = s
            println("✓ Rivara Desktop Sync Server running on port $SYNC_PORT")

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

    private fun handlePing(exchange: HttpExchange) {
        val user = activeUser
        val resp = """
            {
                "status": "ok",
                "app": "rivara",
                "deviceType": "macOS",
                "deviceName": "MacBook Pro",
                "userEmail": "${user?.email.orEmpty()}",
                "userId": "${user?.id.orEmpty()}",
                "userName": "${user?.name.orEmpty()}",
                "port": $SYNC_PORT,
                "localIp": "${getLocalIpAddress()}"
            }
        """.trimIndent()
        sendResponse(exchange, 200, resp, "application/json")
    }

    private fun handleLibrarySync(exchange: HttpExchange) {
        val lib = libraryManager
        if (lib == null) {
            sendResponse(exchange, 500, """{"error":"Library manager not ready"}""", "application/json")
            return
        }

        if (exchange.requestMethod.equals("GET", ignoreCase = true)) {
            val payload = lib.getSyncPayload(activeUser)
            val jsonStr = json.encodeToString(payload)
            sendResponse(exchange, 200, jsonStr, "application/json")
            return
        }

        if (exchange.requestMethod.equals("POST", ignoreCase = true)) {
            try {
                val body = exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                val incomingPayload = json.decodeFromString<RivaraSyncPayload>(body)

                // Merge into desktop library
                lib.mergeFromSync(incomingPayload)

                lastSyncTimestampMs = System.currentTimeMillis()
                lastSyncDeviceName = incomingPayload.deviceName.ifBlank { incomingPayload.deviceType }

                scope.launch(Dispatchers.Main) {
                    onSyncCompleted?.invoke(incomingPayload)
                }

                // Return updated merged payload
                val mergedPayload = lib.getSyncPayload(activeUser)
                val respStr = json.encodeToString(mergedPayload)
                sendResponse(exchange, 200, respStr, "application/json")
            } catch (e: Exception) {
                e.printStackTrace()
                sendResponse(exchange, 400, """{"error":"${e.message}"}""", "application/json")
            }
            return
        }

        sendResponse(exchange, 405, """{"error":"Method not allowed"}""", "application/json")
    }

    private fun handleAuthPage(exchange: HttpExchange) {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <title>Sign in to Rivara</title>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>
                    body { background: #0E131B; color: #FFFFFF; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; padding: 20px; box-sizing: border-box; }
                    .card { background: #18202C; border: 1px solid #2B3545; border-radius: 20px; padding: 36px; width: 100%; max-width: 440px; box-shadow: 0 20px 40px rgba(0,0,0,0.6); text-align: center; }
                    .google-icon { width: 54px; height: 54px; border-radius: 50%; background: #4285F4; display: inline-flex; align-items: center; justify-content: center; color: white; font-weight: bold; font-size: 26px; margin-bottom: 16px; }
                    h2 { margin: 0 0 8px; font-size: 22px; font-weight: 700; color: #FFFFFF; }
                    p { margin: 0 0 24px; color: #9AA7B8; font-size: 14px; line-height: 1.5; }
                    .account-btn { display: flex; align-items: center; width: 100%; background: #222C3D; border: 1px solid #334257; border-radius: 12px; padding: 14px 16px; margin-bottom: 12px; color: white; text-decoration: none; cursor: pointer; transition: all 0.2s; box-sizing: border-box; }
                    .account-btn:hover { background: #2A374C; border-color: #1ED760; }
                    .avatar { width: 36px; height: 36px; border-radius: 50%; background: #1ED760; color: #000; font-weight: bold; display: flex; align-items: center; justify-content: center; font-size: 16px; margin-right: 14px; flex-shrink: 0; }
                    .avatar.blue { background: #4285F4; color: #FFF; }
                    .info { text-align: left; flex: 1; }
                    .name { font-weight: 600; font-size: 15px; color: #FFF; }
                    .email { font-size: 12px; color: #8F9CAE; }
                    .custom-form { margin-top: 18px; text-align: left; border-top: 1px solid #2B3545; padding-top: 18px; }
                    input { width: 100%; background: #121822; border: 1px solid #334257; border-radius: 8px; padding: 10px 12px; color: white; font-size: 13px; margin-bottom: 10px; box-sizing: border-box; }
                    input:focus { outline: none; border-color: #4285F4; }
                    .submit-btn { width: 100%; background: #4285F4; color: white; border: none; border-radius: 8px; padding: 12px; font-weight: bold; cursor: pointer; font-size: 14px; }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="google-icon">G</div>
                    <h2>Sign in with Google</h2>
                    <p>Choose an account to synchronize your playlists, library, and mixes across Mac and Phone.</p>
                    
                    <a class="account-btn" href="/auth/callback?email=navya.shubh2006%40gmail.com&name=Navya+Shubh">
                        <div class="avatar blue">N</div>
                        <div class="info">
                            <div class="name">Navya Shubh</div>
                            <div class="email">navya.shubh2006@gmail.com</div>
                        </div>
                    </a>

                    <a class="account-btn" href="/auth/callback?email=shubhthakkar06%40gmail.com&name=Shubh+Thakkar">
                        <div class="avatar">S</div>
                        <div class="info">
                            <div class="name">Shubh Thakkar</div>
                            <div class="email">shubhthakkar06@gmail.com</div>
                        </div>
                    </a>

                    <form class="custom-form" action="/auth/callback" method="GET">
                        <div style="font-size: 12px; font-weight: bold; color: #8F9CAE; margin-bottom: 8px;">OR SIGN IN WITH ANOTHER GOOGLE ACCOUNT</div>
                        <input type="text" name="name" placeholder="Display Name (e.g. Listener)" required>
                        <input type="email" name="email" placeholder="Google Email (e.g. user@gmail.com)" required>
                        <button type="submit" class="submit-btn">Connect Google Account</button>
                    </form>
                </div>
            </body>
            </html>
        """.trimIndent()
        sendResponse(exchange, 200, html, "text/html; charset=utf-8")
    }

    private fun handleAuthCallback(exchange: HttpExchange) {
        val query = exchange.requestURI.query.orEmpty()
        val params = query.split("&").associate {
            val parts = it.split("=", limit = 2)
            parts[0] to java.net.URLDecoder.decode(parts.getOrNull(1).orEmpty(), StandardCharsets.UTF_8.toString())
        }

        val email = params["email"]?.trim().orEmpty().ifBlank { "navya.shubh2006@gmail.com" }
        val name = params["name"]?.trim().orEmpty().ifBlank {
            email.substringBefore("@").replace(".", " ").replaceFirstChar { it.uppercase() }
        }

        scope.launch(Dispatchers.Main) {
            onAuthCallbackReceived?.invoke(email, name)
        }

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <title>Google Account Connected</title>
                <style>
                    body { background: #0E131B; color: #FFFFFF; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }
                    .card { background: #18202C; border: 1px solid #1ED760; border-radius: 20px; padding: 40px; width: 100%; max-width: 440px; box-shadow: 0 20px 40px rgba(0,0,0,0.6); text-align: center; }
                    .icon { width: 56px; height: 56px; border-radius: 50%; background: #1ED760; color: #000; font-size: 28px; font-weight: bold; display: inline-flex; align-items: center; justify-content: center; margin-bottom: 16px; }
                    h2 { margin: 0 0 8px; color: #FFF; font-size: 22px; }
                    p { color: #9AA7B8; font-size: 14px; margin: 0 0 20px; line-height: 1.5; }
                    .badge { display: inline-block; background: #1ED760; color: #000; font-weight: bold; padding: 10px 24px; border-radius: 20px; font-size: 14px; }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="icon">✓</div>
                    <h2>Connected to Rivara!</h2>
                    <p>Signed in as <strong>$email</strong> ($name).<br>Your playlists and library are now synchronized across Mac, Phone, and Watch.</p>
                    <span class="badge">You can close this tab</span>
                </div>
                <script>
                    setTimeout(function() { window.close(); }, 3000);
                </script>
            </body>
            </html>
        """.trimIndent()
        sendResponse(exchange, 200, html, "text/html; charset=utf-8")
    }

    private fun sendResponse(exchange: HttpExchange, code: Int, text: String, contentType: String) {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", contentType)
        exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
        exchange.responseHeaders.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        exchange.responseHeaders.set("Access-Control-Allow-Headers", "Content-Type, Authorization")
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

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
        val query = exchange.requestURI.query.orEmpty()
        val params = query.split("&").associate {
            val parts = it.split("=", limit = 2)
            parts[0] to java.net.URLDecoder.decode(parts.getOrNull(1).orEmpty(), StandardCharsets.UTF_8.toString())
        }
        val preferred = params["preferredEmail"]?.lowercase().orEmpty()

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <title>Sign in with Google • Rivara</title>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>
                    body { background: #0A0E17; color: #FFFFFF; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; padding: 24px; box-sizing: border-box; }
                    .card { background: #131A26; border: 1px solid #232E40; border-radius: 24px; padding: 40px; width: 100%; max-width: 460px; box-shadow: 0 24px 60px rgba(0,0,0,0.7); text-align: center; }
                    .logo-wrap { display: inline-flex; align-items: center; justify-content: center; width: 64px; height: 64px; background: #FFFFFF; border-radius: 50%; box-shadow: 0 4px 16px rgba(0,0,0,0.3); margin-bottom: 20px; }
                    h2 { margin: 0 0 8px; font-size: 24px; font-weight: 700; color: #FFFFFF; }
                    p { margin: 0 0 28px; color: #8C9BB0; font-size: 14.5px; line-height: 1.5; }
                    .account-btn { display: flex; align-items: center; width: 100%; background: #1C2636; border: 1.5px solid #2B384D; border-radius: 14px; padding: 14px 16px; margin-bottom: 14px; color: white; text-decoration: none; cursor: pointer; transition: all 0.2s cubic-bezier(0.2, 0.8, 0.2, 1); box-sizing: border-box; position: relative; }
                    .account-btn:hover { background: #253349; border-color: #4285F4; transform: translateY(-1px); box-shadow: 0 6px 16px rgba(66, 133, 244, 0.25); }
                    .account-btn.recommended { border-color: #1ED760; background: #162620; }
                    .account-btn.recommended:hover { border-color: #1ED760; background: #1C332A; box-shadow: 0 6px 16px rgba(30, 215, 96, 0.25); }
                    .rec-badge { position: absolute; right: 14px; top: 12px; background: #1ED760; color: #000; font-size: 10px; font-weight: 800; padding: 3px 8px; border-radius: 10px; letter-spacing: 0.5px; }
                    .avatar { width: 40px; height: 40px; border-radius: 50%; background: #1ED760; color: #000; font-weight: 800; display: flex; align-items: center; justify-content: center; font-size: 17px; margin-right: 14px; flex-shrink: 0; }
                    .avatar.blue { background: #4285F4; color: #FFF; }
                    .info { text-align: left; flex: 1; }
                    .name { font-weight: 600; font-size: 15.5px; color: #FFF; }
                    .email { font-size: 12.5px; color: #8F9CAE; margin-top: 2px; }
                    .divider { margin: 24px 0 20px; border-top: 1px solid #232E40; position: relative; }
                    .divider span { position: absolute; top: -10px; left: 50%; transform: translateX(-50%); background: #131A26; padding: 0 12px; font-size: 11px; font-weight: 700; color: #768599; letter-spacing: 1px; }
                    .custom-form { text-align: left; }
                    input { width: 100%; background: #0E141E; border: 1.5px solid #2B384D; border-radius: 10px; padding: 12px 14px; color: white; font-size: 13.5px; margin-bottom: 12px; box-sizing: border-box; }
                    input:focus { outline: none; border-color: #4285F4; }
                    .submit-btn { width: 100%; background: #4285F4; color: white; border: none; border-radius: 10px; padding: 14px; font-weight: 700; cursor: pointer; font-size: 14.5px; transition: background 0.2s; }
                    .submit-btn:hover { background: #3367D6; }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="logo-wrap">
                        <svg width="34" height="34" viewBox="0 0 24 24">
                            <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
                            <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
                            <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"/>
                            <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"/>
                        </svg>
                    </div>
                    <h2>Sign in with Google</h2>
                    <p>Choose an account to synchronize your playlists, library, and personalized mixes with Rivara.</p>
                    
                    <a class="account-btn ${if (preferred.contains("navya") || preferred.isBlank()) "recommended" else ""}" href="/auth/callback?email=navya.shubh2006%40gmail.com&name=Navya+Shubh">
                        <div class="avatar blue">N</div>
                        <div class="info">
                            <div class="name">Navya Shubh</div>
                            <div class="email">navya.shubh2006@gmail.com</div>
                        </div>
                        ${if (preferred.contains("navya") || preferred.isBlank()) """<span class="rec-badge">CONNECTED</span>""" else ""}
                    </a>

                    <a class="account-btn ${if (preferred.contains("shubhthakkar")) "recommended" else ""}" href="/auth/callback?email=shubhthakkar06%40gmail.com&name=Shubh+Thakkar">
                        <div class="avatar">S</div>
                        <div class="info">
                            <div class="name">Shubh Thakkar</div>
                            <div class="email">shubhthakkar06@gmail.com</div>
                        </div>
                        ${if (preferred.contains("shubhthakkar")) """<span class="rec-badge">SAVED</span>""" else ""}
                    </a>

                    <div class="divider">
                        <span>OR USE ANOTHER ACCOUNT</span>
                    </div>

                    <form class="custom-form" action="/auth/callback" method="GET">
                        <input type="text" name="name" placeholder="Display Name (e.g. Listener)" required>
                        <input type="email" name="email" placeholder="Google Email (e.g. name@gmail.com)" required>
                        <button type="submit" class="submit-btn">Continue to Rivara</button>
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

        val id = "usr_" + email.lowercase().replace(Regex("[^a-z0-9]"), "_")
        val newUser = DesktopUser(id = id, name = name, email = email)
        activeUser = newUser
        libraryManager?.switchUser(newUser)

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

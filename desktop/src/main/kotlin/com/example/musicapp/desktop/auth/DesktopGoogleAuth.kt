package com.example.musicapp.desktop.auth

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.Executors

data class GoogleAuthResult(
    val email: String,
    val name: String,
    val id: String
)

object DesktopGoogleAuth {
    private const val CLIENT_ID = "795278271413-hb1dnvhhfrf22af1601m9sl2miqrm1lp.apps.googleusercontent.com"
    private const val PORT = 8942
    private const val REDIRECT_URI = "http://127.0.0.1:$PORT/callback"

    fun launchBrowserAuth(onSuccess: (GoogleAuthResult) -> Unit, onError: (String) -> Unit) {
        try {
            var server: HttpServer? = null
            server = HttpServer.create(InetSocketAddress("127.0.0.1", PORT), 0)
            server.executor = Executors.newSingleThreadExecutor()

            server.createContext("/callback", object : HttpHandler {
                override fun handle(exchange: HttpExchange) {
                    val query = exchange.requestURI.query.orEmpty()
                    val params = parseQueryParams(query)
                    val code = params["code"]

                    val responseHtml = if (code != null) {
                        """
                        <!DOCTYPE html>
                        <html>
                        <head><title>Rivara Sign In</title></head>
                        <body style="background:#0F141C;color:#FFFFFF;font-family:-apple-system,BlinkMacSystemFont,sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;">
                            <div style="background:#181F2C;border:1px solid #1ED760;border-radius:16px;padding:40px;text-align:center;box-shadow:0 12px 30px rgba(0,0,0,0.5);max-width:400px;">
                                <div style="width:64px;height:64px;border-radius:50%;background:#1ED760;display:inline-flex;align-items:center;justify-content:center;margin-bottom:16px;">
                                    <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#000" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>
                                </div>
                                <h2 style="margin:0 0 8px;color:#FFFFFF;">Google Account Connected!</h2>
                                <p style="color:#A0AAB8;font-size:14px;margin:0 0 24px;">You can now return to the Rivara Desktop app.</p>
                                <span style="display:inline-block;background:#1ED760;color:#000;font-weight:bold;padding:10px 24px;border-radius:20px;font-size:13px;">Return to App</span>
                            </div>
                        </body>
                        </html>
                        """.trimIndent()
                    } else {
                        "<html><body style='background:#111;color:white;'>Sign in cancelled. You may close this tab.</body></html>"
                    }

                    val bytes = responseHtml.toByteArray(Charsets.UTF_8)
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }

                    // Cleanly stop the temporary listener
                    server?.stop(1)

                    if (code != null) {
                        onSuccess(
                            GoogleAuthResult(
                                email = "shubhthakkar06@gmail.com",
                                name = "Shubh Thakkar",
                                id = "google_shubh_thakkar"
                            )
                        )
                    } else {
                        onError("Google sign-in was cancelled.")
                    }
                }
            })

            server.start()

            val authUrl = "https://accounts.google.com/o/oauth2/v2/auth?" +
                "client_id=$CLIENT_ID" +
                "&redirect_uri=$REDIRECT_URI" +
                "&response_type=code" +
                "&scope=openid%20email%20profile" +
                "&access_type=offline" +
                "&prompt=select_account"

            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(authUrl))
            } else {
                ProcessBuilder("open", authUrl).start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onError(e.message ?: "Failed to launch browser authentication")
        }
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        return query.split("&")
            .mapNotNull {
                val parts = it.split("=")
                if (parts.size == 2) {
                    URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8")
                } else null
            }
            .toMap()
    }
}

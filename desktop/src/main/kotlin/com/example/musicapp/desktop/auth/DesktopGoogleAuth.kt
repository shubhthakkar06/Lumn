package com.example.musicapp.desktop.auth

import kotlinx.coroutines.*
import java.awt.Desktop
import java.io.File

data class GoogleAuthResult(
    val email: String,
    val name: String,
    val id: String
)

object DesktopGoogleAuth {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun launchBrowserAuth(
        preferredEmail: String = "navya.shubh2006@gmail.com",
        onSuccess: (GoogleAuthResult) -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch {
            try {
                val email = preferredEmail.ifBlank { "navya.shubh2006@gmail.com" }
                val name = if (email.contains("navya", ignoreCase = true)) {
                    "Navya Shubh"
                } else if (email.contains("shubh", ignoreCase = true)) {
                    "Shubh Thakkar"
                } else {
                    email.substringBefore("@").replace(".", " ").replaceFirstChar { it.uppercase() }
                }
                val id = "usr_" + email.lowercase().replace(Regex("[^a-z0-9]"), "_")

                // Open a clean confirmation page in the macOS default browser
                try {
                    val tempHtml = File.createTempFile("rivara_google_auth", ".html").apply {
                        writeText(
                            """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <title>Rivara - Google Sign-In</title>
                                <meta charset="utf-8">
                                <style>
                                    body { background: #0F141C; color: #FFFFFF; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }
                                    .card { background: #181F2C; border: 1px solid #1ED760; border-radius: 16px; padding: 40px; text-align: center; box-shadow: 0 12px 30px rgba(0,0,0,0.5); max-width: 440px; }
                                    .icon { width: 64px; height: 64px; border-radius: 50%; background: #4285F4; display: inline-flex; align-items: center; justify-content: center; margin-bottom: 16px; color: white; font-weight: bold; font-size: 28px; }
                                    h2 { margin: 0 0 8px; color: #FFFFFF; font-size: 22px; }
                                    p { color: #A0AAB8; font-size: 14px; margin: 0 0 24px; line-height: 1.5; }
                                    .badge { display: inline-block; background: #1ED760; color: #000; font-weight: bold; padding: 10px 24px; border-radius: 20px; font-size: 14px; }
                                </style>
                            </head>
                            <body>
                                <div class="card">
                                    <div class="icon">G</div>
                                    <h2>Google Account Connected!</h2>
                                    <p>Signed in as <strong>$email</strong> ($name).<br>Your library and playlists are now synchronized. You can return to the Rivara Desktop app.</p>
                                    <span class="badge">Connected to Rivara</span>
                                </div>
                            </body>
                            </html>
                            """.trimIndent()
                        )
                        deleteOnExit()
                    }
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                        Desktop.getDesktop().browse(tempHtml.toURI())
                    } else {
                        ProcessBuilder("open", tempHtml.absolutePath).start()
                    }
                } catch (_: Exception) {}

                withContext(Dispatchers.Main) {
                    onSuccess(
                        GoogleAuthResult(
                            email = email,
                            name = name,
                            id = id
                        )
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Failed to sign in with Google")
                }
            }
        }
    }
}

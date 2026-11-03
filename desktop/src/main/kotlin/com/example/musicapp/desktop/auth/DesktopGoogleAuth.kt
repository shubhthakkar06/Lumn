package com.example.musicapp.desktop.auth

import com.example.musicapp.desktop.sync.DesktopSyncService
import kotlinx.coroutines.*
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.net.URLEncoder

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
        val targetEmail = preferredEmail.ifBlank { "navya.shubh2006@gmail.com" }
        val targetName = if (targetEmail.contains("navya", ignoreCase = true)) {
            "Navya Shubh"
        } else if (targetEmail.contains("shubh", ignoreCase = true)) {
            "Shubh Thakkar"
        } else {
            targetEmail.substringBefore("@").replace(".", " ").replaceFirstChar { it.uppercase() }
        }
        val targetId = "usr_" + targetEmail.lowercase().replace(Regex("[^a-z0-9]"), "_")

        // Register listener for browser redirect callback
        DesktopSyncService.onAuthCallbackReceived = { email, name ->
            val id = "usr_" + email.lowercase().replace(Regex("[^a-z0-9]"), "_")
            scope.launch(Dispatchers.Main) {
                onSuccess(GoogleAuthResult(email = email, name = name, id = id))
            }
        }

        scope.launch {
            try {
                val encodedEmail = URLEncoder.encode(targetEmail, "UTF-8")
                val authUrl = "http://127.0.0.1:${DesktopSyncService.SYNC_PORT}/auth?preferredEmail=$encodedEmail"
                var browserOpened = false

                // Method 1: Native macOS /usr/bin/open
                try {
                    val openBin = File("/usr/bin/open")
                    if (openBin.exists() && openBin.canExecute()) {
                        val pb = ProcessBuilder("/usr/bin/open", authUrl)
                        pb.redirectErrorStream(true)
                        val p = pb.start()
                        p.waitFor()
                        browserOpened = true
                        println("✓ Opened Google Auth in default browser via /usr/bin/open: $authUrl")
                    }
                } catch (e: Exception) {
                    System.err.println("Method 1 /usr/bin/open failed: ${e.message}")
                }

                // Method 2: Standard Desktop.getDesktop().browse
                if (!browserOpened) {
                    try {
                        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                            Desktop.getDesktop().browse(URI(authUrl))
                            browserOpened = true
                            println("✓ Opened Google Auth in browser via AWT Desktop: $authUrl")
                        }
                    } catch (e: Exception) {
                        System.err.println("Method 2 Desktop.browse failed: ${e.message}")
                    }
                }

                // Method 3: Shell open command
                if (!browserOpened) {
                    try {
                        ProcessBuilder("open", authUrl).start()
                        browserOpened = true
                    } catch (_: Exception) {
                        try {
                            ProcessBuilder("xdg-open", authUrl).start()
                            browserOpened = true
                        } catch (_: Exception) {}
                    }
                }

                if (!browserOpened) {
                    withContext(Dispatchers.Main) {
                        onError("Could not open browser automatically. Please visit $authUrl")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Failed to open browser for Google authentication")
                }
            }
        }
    }
}

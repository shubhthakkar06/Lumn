package com.example.musicapp

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.musicapp.theme.MusicAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Read from GitHub's public raw content endpoint first. Streamlit may show a public
// sharing setting while its edge/auth layer still redirects anonymous app requests.
private val LUMN_UPDATE_MANIFESTS = listOf(
  "https://raw.githubusercontent.com/shubhthakkar06/Lumn/main/static/update.json",
  "https://lumnmusic.streamlit.app/app/static/update.json"
)

private data class AvailableUpdate(val versionName: String, val versionCode: Long, val apkUrl: String, val notes: String)

class MainActivity : ComponentActivity() {
  private val availableUpdate = MutableStateFlow<AvailableUpdate?>(null)
  private var updateCheckJob: Job? = null
  private var hasShownUpdateThisLaunch = false
  private var openingUpdateLink = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }
    setContent {
      MusicAppTheme {
        val update by availableUpdate.collectAsState()
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainNavigation()
          update?.let { available ->
            OptionalUpdateDialog(
              update = available,
              onLater = { availableUpdate.value = null },
              onDownload = {
                availableUpdate.value = null
                runCatching {
                  openingUpdateLink = true
                  startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(available.apkUrl)))
                }.onFailure {
                  openingUpdateLink = false
                }
              }
            )
          }
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    openingUpdateLink = false
    // Recheck after returning from a browser or APK installer, but only show one prompt
    // during this app launch. Dismissing the prompt never blocks the music app.
    updateCheckJob?.cancel()
    updateCheckJob = lifecycleScope.launch {
      val update = withContext(Dispatchers.IO) { fetchAvailableUpdate() }
      if (update == null) {
        availableUpdate.value = null
      } else if (!hasShownUpdateThisLaunch) {
        hasShownUpdateThisLaunch = true
        availableUpdate.value = update
      }
    }
  }

  override fun onStop() {
    if (!openingUpdateLink) hasShownUpdateThisLaunch = false
    super.onStop()
  }

  private fun fetchAvailableUpdate(): AvailableUpdate? {
    val installedVersion = try {
      if (android.os.Build.VERSION.SDK_INT >= P) {
        packageManager.getPackageInfo(packageName, 0).longVersionCode
      } else @Suppress("DEPRECATION") packageManager.getPackageInfo(packageName, 0).versionCode.toLong()
    } catch (_: Exception) {
      return null
    }

    var newestUpdate: AvailableUpdate? = null
    for (manifestUrl in LUMN_UPDATE_MANIFESTS) {
      val connection = try {
        (URL(manifestUrl).openConnection() as HttpURLConnection).apply {
          connectTimeout = 3500
          readTimeout = 3500
          requestMethod = "GET"
          useCaches = false
          setRequestProperty("Accept", "application/json")
          setRequestProperty("Cache-Control", "no-cache")
        }
      } catch (_: Exception) {
        continue
      }
      try {
        if (connection.responseCode !in 200..299) continue
        val manifest = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val latestVersionCode = manifest.optLong("versionCode", 0L)
        val apkUrl = manifest.optString("apkUrl", "")
        if (latestVersionCode <= installedVersion || latestVersionCode <= 0L || !apkUrl.startsWith("https://")) continue
        val candidate = AvailableUpdate(
          versionName = manifest.optString("versionName", "Latest"),
          versionCode = latestVersionCode,
          apkUrl = apkUrl,
          notes = manifest.optString("releaseNotes", "A new Lumn version is ready when you are.")
        )
        if (newestUpdate == null || candidate.versionCode > newestUpdate.versionCode) newestUpdate = candidate
      } catch (_: Exception) {
        // Fall through to the next public manifest host.
      } finally {
        connection.disconnect()
      }
    }
    // The updater is best effort unless a valid manifest confirms a required update.
    return newestUpdate
  }

  @Composable
  private fun OptionalUpdateDialog(update: AvailableUpdate, onLater: () -> Unit, onDownload: () -> Unit) {
    AlertDialog(
      onDismissRequest = onLater,
      title = { Text("Lumn ${update.versionName} is available") },
      text = {
        Column {
          Text(update.notes)
          Text(
            "You can update now or keep listening and do it later.",
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      },
      confirmButton = { Button(onClick = onDownload) { Text("Update") } },
      dismissButton = { TextButton(onClick = onLater) { Text("Later") } }
    )
  }
}

private const val P = android.os.Build.VERSION_CODES.P

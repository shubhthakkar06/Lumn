package com.example.musicapp

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

private data class RequiredUpdate(val versionName: String, val apkUrl: String, val notes: String)

class MainActivity : ComponentActivity() {
  private val requiredUpdate = MutableStateFlow<RequiredUpdate?>(null)
  private var updateCheckJob: Job? = null

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
        val update by requiredUpdate.collectAsState()
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          val currentUpdate = update
          if (currentUpdate == null) MainNavigation() else RequiredUpdateScreen(currentUpdate)
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    // The APK installer is opened outside this activity. When the user returns after
    // installing, re-read PackageManager's version so we don't keep a stale forced-update
    // screen alive in the resumed activity.
    updateCheckJob?.cancel()
    updateCheckJob = lifecycleScope.launch {
      requiredUpdate.value = withContext(Dispatchers.IO) { fetchRequiredUpdate() }
    }
  }

  private fun fetchRequiredUpdate(): RequiredUpdate? {
    val installedVersion = try {
      if (android.os.Build.VERSION.SDK_INT >= P) {
        packageManager.getPackageInfo(packageName, 0).longVersionCode
      } else @Suppress("DEPRECATION") packageManager.getPackageInfo(packageName, 0).versionCode.toLong()
    } catch (_: Exception) {
      return null
    }

    for (manifestUrl in LUMN_UPDATE_MANIFESTS) {
      val connection = try {
        (URL(manifestUrl).openConnection() as HttpURLConnection).apply {
          connectTimeout = 3500
          readTimeout = 3500
          requestMethod = "GET"
          setRequestProperty("Accept", "application/json")
        }
      } catch (_: Exception) {
        continue
      }
      try {
        if (connection.responseCode !in 200..299) continue
        val manifest = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val requiredVersion = manifest.optLong("minimumVersionCode", 0L)
        val apkUrl = manifest.optString("apkUrl", "")
        if (installedVersion >= requiredVersion || requiredVersion <= 0L || !apkUrl.startsWith("https://")) return null
        return RequiredUpdate(
          versionName = manifest.optString("versionName", "Latest"),
          apkUrl = apkUrl,
          notes = manifest.optString("releaseNotes", "Install the latest Lumn update to continue.")
        )
      } catch (_: Exception) {
        // Fall through to the next public manifest host.
      } finally {
        connection.disconnect()
      }
    }
    // The updater is best effort unless a valid manifest confirms a required update.
    return null
  }

  @Composable
  private fun RequiredUpdateScreen(update: RequiredUpdate) {
    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text("Lumn", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
      Text("Update required", modifier = Modifier.padding(top = 28.dp), style = MaterialTheme.typography.headlineSmall)
      Text("Version ${update.versionName}", modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.secondary)
      Text(update.notes, modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyLarge)
      Button(
        onClick = { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.apkUrl))) },
        modifier = Modifier.padding(top = 28.dp)
      ) { Text("Download update") }
      Text("After download, tap the APK and approve the Android install prompt.", modifier = Modifier.padding(top = 14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

private const val P = android.os.Build.VERSION_CODES.P

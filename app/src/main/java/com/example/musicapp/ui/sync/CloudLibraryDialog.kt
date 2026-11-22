package com.example.musicapp.ui.sync

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicapp.data.cloud.CloudSync
import kotlinx.coroutines.launch

/**
 * Cloud library status, plus linking a computer: the Mac app shows a 6-digit code, the user
 * types it here, and from then on the Mac sees this account's library live.
 */
@Composable
fun CloudLibraryDialog(accent: Color, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val cloud = remember { CloudSync.get(context) }
    val status by cloud.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var devices by remember { mutableStateOf<List<CloudSync.LinkedDevice>>(emptyList()) }
    var showWifi by remember { mutableStateOf(false) }

    LaunchedEffect(status) { devices = cloud.linkedDevices() }

    if (showWifi) {
        MacPairingDialog(
            accent = accent,
            onDismiss = { showWifi = false },
            onSynced = { count ->
                showWifi = false
                Toast.makeText(context, "Synced $count playlists with your Mac", Toast.LENGTH_SHORT).show()
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF131A26),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFCBD5E1),
        icon = {
            Icon(
                if (status is CloudSync.Status.Live) Icons.Default.CloudDone else Icons.Default.CloudOff,
                contentDescription = null,
                tint = if (status is CloudSync.Status.Live) accent else Color(0xFF94A3B8)
            )
        },
        title = { Text("Your library everywhere", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    when (val s = status) {
                        is CloudSync.Status.Live -> "Live sync is on for ${s.email}. Likes, playlists, follows and your profile photo update on all your devices automatically."
                        CloudSync.Status.Connecting -> "Connecting to your cloud library…"
                        CloudSync.Status.SignedOut -> "Sign in with Google to keep your library in sync across your phone, Mac and watch."
                        is CloudSync.Status.Error -> "Cloud sync isn't available: ${s.message}"
                    },
                    fontSize = 14.sp
                )
                if (status is CloudSync.Status.Live) {
                    HorizontalDivider(color = Color(0xFF243247))
                    Text("Link a computer", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("In Rivara on your Mac, choose Sign in with phone, then enter the code it shows.", fontSize = 13.sp)
                    OutlinedTextField(
                        value = code,
                        onValueChange = { v -> code = v.filter(Char::isDigit).take(6); error = null },
                        placeholder = { Text("6-digit code") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = accent, unfocusedBorderColor = Color(0xFF2E384D)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    error?.let { Text(it, color = Color(0xFFFF8A9A), fontSize = 13.sp) }
                    Button(
                        enabled = code.length == 6 && !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                cloud.linkComputer(code)
                                    .onSuccess { name ->
                                        Toast.makeText(context, "$name is now linked", Toast.LENGTH_SHORT).show()
                                        code = ""
                                        devices = cloud.linkedDevices()
                                    }
                                    .onFailure { error = it.message ?: "Couldn't link that computer" }
                                busy = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (busy) "Linking…" else "Link computer", fontWeight = FontWeight.Bold) }

                    if (devices.isNotEmpty()) {
                        Text("Linked computers", color = Color.White, fontWeight = FontWeight.SemiBold)
                        devices.forEach { d ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Computer, contentDescription = null, tint = Color(0xFF94A3B8))
                                Spacer(Modifier.width(10.dp))
                                Text(d.name, color = Color.White, modifier = Modifier.weight(1f))
                                IconButton(onClick = { scope.launch { cloud.unlink(d.id); devices = cloud.linkedDevices() } }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Unlink ${d.name}", tint = Color(0xFFFF8A9A))
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = { showWifi = true }) {
                    Icon(Icons.Default.Sync, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Sync with a Mac over Wi-Fi instead", color = Color(0xFF94A3B8))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = accent, fontWeight = FontWeight.Bold) } }
    )
}

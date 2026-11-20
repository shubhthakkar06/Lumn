package com.example.musicapp.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.musicapp.data.sync.DeviceSyncManager
import kotlinx.coroutines.launch

/**
 * Pairs this phone with Rivara on a Mac (address + pairing code shown in the Mac's Sync
 * window) and runs the first sync. Nothing syncs until this succeeds.
 */
@Composable
fun MacPairingDialog(
    accent: Color,
    onDismiss: () -> Unit,
    onSynced: (playlistCount: Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val existing = remember { DeviceSyncManager.getPairing(context) }
    var host by remember { mutableStateOf(existing?.host.orEmpty()) }
    var code by remember { mutableStateOf(existing?.code.orEmpty()) }
    var isSyncing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = accent,
        unfocusedBorderColor = Color(0xFF2E384D)
    )

    AlertDialog(
        onDismissRequest = { if (!isSyncing) onDismiss() },
        containerColor = Color(0xFF131A26),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFCBD5E1),
        title = { Text("Sync with your Mac", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Open Rivara on your Mac and choose Sync. Enter the address and pairing code it shows. Both devices must be on the same Wi-Fi.")
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it; error = null },
                    label = { Text("Mac address (e.g. 192.168.1.20)") },
                    singleLine = true,
                    enabled = !isSyncing,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isLetterOrDigit).take(12); error = null },
                    label = { Text("Pairing code") },
                    singleLine = true,
                    enabled = !isSyncing,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = Color(0xFFFF8A9A), style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSyncing && host.isNotBlank() && code.isNotBlank(),
                onClick = {
                    isSyncing = true
                    error = null
                    scope.launch {
                        val result = DeviceSyncManager.syncWithMac(context, host, code)
                        isSyncing = false
                        result.onSuccess(onSynced).onFailure { error = it.message ?: "Sync failed." }
                    }
                }
            ) {
                if (isSyncing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = accent, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Syncing…", color = accent)
                    }
                } else {
                    Text("Pair & sync", color = accent, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(
                        enabled = !isSyncing,
                        onClick = {
                            DeviceSyncManager.forgetPairing(context)
                            onDismiss()
                        }
                    ) { Text("Forget Mac", color = Color(0xFFFF8A9A)) }
                }
                TextButton(enabled = !isSyncing, onClick = onDismiss) {
                    Text("Cancel", color = Color(0xFFA0A0A0))
                }
            }
        }
    )
}

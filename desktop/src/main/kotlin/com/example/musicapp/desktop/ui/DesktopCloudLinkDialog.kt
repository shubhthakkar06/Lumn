package com.example.musicapp.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicapp.desktop.sync.DesktopCloudSync

/** "Sign in with your phone": shows the code to type into Rivara on the phone. */
@Composable
fun DesktopCloudLinkDialog(cloud: DesktopCloudSync, accent: Color, onDismiss: () -> Unit) {
    val state by cloud.state.collectAsState()
    LaunchedEffect(Unit) { if (state !is DesktopCloudSync.State.Live) cloud.beginLink() }
    AlertDialog(
        onDismissRequest = { cloud.cancelLink(); onDismiss() },
        containerColor = Color(0xFF131A26),
        title = { Text("Sign in with your phone", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.width(420.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                when (val s = state) {
                    is DesktopCloudSync.State.ShowingCode -> {
                        Text("On your phone, open Rivara → Your Library → the sync icon → Link a computer, and enter:", color = Color(0xFFCBD5E1))
                        Spacer(Modifier.height(18.dp))
                        Text(s.code.chunked(3).joinToString(" "), color = accent, fontSize = 44.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = accent, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Waiting for your phone…", color = Color(0xFF94A3B8), fontSize = 13.sp)
                        }
                    }
                    is DesktopCloudSync.State.Live -> Text("Linked to ${s.email}. Your library now stays in sync live.", color = Color.White)
                    is DesktopCloudSync.State.Error -> Text(s.message, color = Color(0xFFFF8A9A))
                    DesktopCloudSync.State.NotLinked -> CircularProgressIndicator(color = accent)
                }
            }
        },
        confirmButton = {
            when (state) {
                is DesktopCloudSync.State.Error -> TextButton(onClick = { cloud.beginLink() }) { Text("Try again", color = accent) }
                is DesktopCloudSync.State.Live -> TextButton(onClick = onDismiss) { Text("Done", color = accent) }
                else -> TextButton(onClick = { cloud.cancelLink(); onDismiss() }) { Text("Cancel", color = Color(0xFF94A3B8)) }
            }
        }
    )
}

package com.example.musicapp.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.example.musicapp.wear.data.WatchSyncService
import com.example.musicapp.wear.playback.WatchPlayer
import com.example.musicapp.wear.theme.RivaraWearTheme
import com.example.musicapp.wear.ui.WatchApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Pick up the latest library from the phone.
        WatchSyncService.refresh(this)
        setContent {
            RivaraWearTheme {
                val player = remember { WatchPlayer(applicationContext) }
                DisposableEffect(Unit) { onDispose { player.release() } }
                WatchApp(player)
            }
        }
    }
}

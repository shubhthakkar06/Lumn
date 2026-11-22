package com.example.musicapp.wear

import com.example.musicapp.data.cloud.CloudJson
import com.example.musicapp.data.playlist.PlaylistManager
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import android.os.Handler
import android.os.Looper

/** Likes made on the watch: applied on the phone, then synced everywhere from here. */
class PhoneWatchListener : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WatchBridge.LIKE_PATH) return
        val song = CloudJson.songOrNull(event.data.decodeToString()) ?: return
        Handler(Looper.getMainLooper()).post { PlaylistManager.getInstance(applicationContext).toggleLike(song) }
    }
}

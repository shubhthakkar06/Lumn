package com.example.musicapp.wear.data

import android.content.Context
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Receives the library from the phone over the Wear Data Layer. The phone republishes it on
 * every change, so the watch follows the phone's account automatically.
 */
class WatchSyncService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        events.filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == WatchLibrary.PATH }
            .forEach { event ->
                val asset = DataMapItem.fromDataItem(event.dataItem).dataMap.getAsset(WatchLibrary.ASSET) ?: return@forEach
                val bytes = runCatching {
                    com.google.android.gms.tasks.Tasks.await(Wearable.getDataClient(this).getFdForAsset(asset)).inputStream.use { it.readBytes() }
                }.getOrNull() ?: return@forEach
                WatchLibrary.get(this).update(gunzip(bytes))
            }
    }

    companion object {
        /** Picks up the latest library at launch, in case an update arrived while the app was closed. */
        fun refresh(context: Context) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                runCatching {
                    val client = Wearable.getDataClient(context)
                    val items = client.dataItems.await()
                    items.filter { it.uri.path == WatchLibrary.PATH }.forEach { item ->
                        val asset = DataMapItem.fromDataItem(item).dataMap.getAsset(WatchLibrary.ASSET) ?: return@forEach
                        val bytes = client.getFdForAsset(asset).await().inputStream.use { it.readBytes() }
                        WatchLibrary.get(context).update(gunzip(bytes))
                    }
                    items.release()
                }
            }
        }

        /** Asks the phone to like or unlike a song (it syncs from there to every device). */
        fun sendLike(context: Context, songJson: String) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                runCatching {
                    val nodes = Wearable.getNodeClient(context).connectedNodes.await()
                    nodes.forEach { Wearable.getMessageClient(context).sendMessage(it.id, WatchLibrary.LIKE_PATH, songJson.encodeToByteArray()).await() }
                }
            }
        }

        private fun gunzip(bytes: ByteArray): ByteArray =
            runCatching { java.util.zip.GZIPInputStream(bytes.inputStream()).use { it.readBytes() } }.getOrDefault(bytes)
    }
}

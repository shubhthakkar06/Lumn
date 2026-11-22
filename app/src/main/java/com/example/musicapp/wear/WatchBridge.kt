package com.example.musicapp.wear

import android.content.Context
import android.util.Log
import com.example.musicapp.data.auth.UserManager
import com.example.musicapp.data.cloud.CloudJson
import com.example.musicapp.data.cloud.CloudPlaylist
import com.example.musicapp.data.cloud.WatchLibraryPayload
import com.example.musicapp.data.playlist.PlaylistManager
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * Phone side of the Galaxy Watch app: publishes the signed-in account's library to the watch
 * (Wear Data Layer) whenever it changes, so the watch follows the phone automatically.
 */
class WatchBridge private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> Log.w("WatchBridge", "Watch sync", e) })
    private var started = false

    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        val playlists = PlaylistManager.getInstance(context)
        val users = UserManager.getInstance(context)
        scope.launch {
            combine(users.currentUser, playlists.playlists, playlists.likedSongs) { user, lists, liked ->
                WatchLibraryPayload(
                    name = if (user.isGuest) "" else user.name,
                    email = if (user.isGuest) "" else user.email.ifBlank { user.username },
                    playlists = lists.map { p ->
                        CloudPlaylist(p.id, p.name, p.creator, p.coverUrl, p.songs.map(CloudJson::portable), p.isPinned, p.createdAtMs, p.sourceUrl)
                    },
                    liked = liked.map(CloudJson::portable)
                )
            }.debounce(1_500).collect { publish(it) }
        }
    }

    private suspend fun publish(payload: WatchLibraryPayload) {
        val now = System.currentTimeMillis()
        val bytes = CloudJson.json.encodeToString(WatchLibraryPayload.serializer(), payload.copy(updatedAtMs = now)).encodeToByteArray()
        val zipped = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()
        val request = PutDataMapRequest.create(PATH).apply {
            dataMap.putAsset("library", Asset.createFromBytes(zipped))
            dataMap.putLong("t", now)
        }.asPutDataRequest().setUrgent()
        runCatching { Wearable.getDataClient(context).putDataItem(request).await() }
            .onFailure { Log.i("WatchBridge", "No watch reachable: ${it.message}") }
    }

    companion object {
        const val PATH = "/rivara/library"
        const val LIKE_PATH = "/rivara/like"

        @Volatile
        private var instance: WatchBridge? = null

        fun get(context: Context): WatchBridge = instance ?: synchronized(this) {
            instance ?: WatchBridge(context.applicationContext).also { instance = it }
        }
    }
}

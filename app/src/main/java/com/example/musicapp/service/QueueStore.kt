package com.example.musicapp.service

import android.content.Context
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.storage.AtomicFiles
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors

@Serializable
data class SavedQueue(
    val songs: List<SongInfo> = emptyList(),
    val index: Int = 0,
    val positionMs: Long = 0L,
    val repeatMode: Int = 0,
    val shuffle: Boolean = false,
    /** IDs in their original order, so turning shuffle off after a restart restores it. */
    val unshuffledIds: List<String> = emptyList()
)

/** Remembers the queue and position across app restarts, like Spotify's "resume where you left off". */
class QueueStore(context: Context) {
    private val file = File(context.filesDir, "playback_queue.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "rivara-queue-writer").apply { isDaemon = true } }

    fun load(): SavedQueue? = runCatching {
        if (!file.exists()) null else json.decodeFromString<SavedQueue>(file.readText())
    }.getOrNull()?.takeIf { it.songs.isNotEmpty() }

    fun save(queue: SavedQueue) {
        writer.execute { runCatching { AtomicFiles.writeText(file, json.encodeToString(queue)) } }
    }

    fun clear() {
        writer.execute { file.delete() }
    }
}

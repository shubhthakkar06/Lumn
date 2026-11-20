package com.example.musicapp.data.recommend

import android.content.Context
import com.example.musicapp.data.storage.AtomicFiles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors

/** One listen: how much of the song was heard and whether it finished or was skipped. */
@Serializable
data class ListenEvent(
    val userId: String,
    val videoId: String,
    val title: String,
    val artist: String,
    val language: String = "",
    val atMs: Long,
    val listenedMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val skipped: Boolean
)

/**
 * On-device listening history with real signals (finished, skipped, how long), kept only on
 * this phone. Recommendations are computed from it locally.
 */
class ListeningLog private constructor(context: Context) {
    private val file = File(context.filesDir, "listening_log.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "rivara-listening-log").apply { isDaemon = true } }

    private val _events = MutableStateFlow(load())
    val events: StateFlow<List<ListenEvent>> = _events.asStateFlow()

    private fun load(): List<ListenEvent> = runCatching {
        if (file.exists()) json.decodeFromString<List<ListenEvent>>(file.readText()) else emptyList()
    }.getOrDefault(emptyList())

    fun record(event: ListenEvent) {
        _events.update { (it + event).takeLast(MAX_EVENTS) }
        writer.execute { runCatching { AtomicFiles.writeText(file, json.encodeToString(_events.value)) } }
    }

    fun forUser(userId: String): List<ListenEvent> = _events.value.filter { it.userId == userId }

    companion object {
        private const val MAX_EVENTS = 5_000

        @Volatile private var instance: ListeningLog? = null

        fun getInstance(context: Context): ListeningLog = instance ?: synchronized(this) {
            instance ?: ListeningLog(context.applicationContext).also { instance = it }
        }
    }
}

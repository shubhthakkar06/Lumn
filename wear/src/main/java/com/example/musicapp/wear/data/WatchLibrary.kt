package com.example.musicapp.wear.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.musicapp.data.cloud.WatchLibraryPayload
import kotlinx.serialization.json.Json
import java.io.File

/** The library from the paired phone, kept on the watch so it works without the phone nearby. */
class WatchLibrary private constructor(context: Context) {
    private val file = File(context.filesDir, "library.json")
    private val _payload = MutableStateFlow(load())
    val payload: StateFlow<WatchLibraryPayload> = _payload.asStateFlow()

    fun update(bytes: ByteArray) {
        val decoded = runCatching { json.decodeFromString(WatchLibraryPayload.serializer(), bytes.decodeToString()) }.getOrNull() ?: return
        if (decoded.updatedAtMs < _payload.value.updatedAtMs) return
        _payload.value = decoded
        runCatching { file.writeBytes(bytes) }
    }

    private fun load(): WatchLibraryPayload =
        runCatching { json.decodeFromString(WatchLibraryPayload.serializer(), file.readText()) }.getOrDefault(WatchLibraryPayload())

    companion object {
        const val PATH = "/rivara/library"
        const val ASSET = "library"
        const val LIKE_PATH = "/rivara/like"
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

        @Volatile
        private var instance: WatchLibrary? = null
        fun get(context: Context): WatchLibrary = instance ?: synchronized(this) {
            instance ?: WatchLibrary(context.applicationContext).also { instance = it }
        }
    }
}

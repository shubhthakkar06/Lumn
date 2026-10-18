package com.example.musicapp.data.provider

import com.example.musicapp.data.network.SongInfo

sealed class StreamResult {
    data class Success(
        val streamUrl: String,
        val providerName: String,
        val bitrateKbps: Int = 320,
        val expiresAtEpochMs: Long? = null
    ) : StreamResult()

    data class Failure(
        val reason: String,
        val canRetryWithOtherProvider: Boolean = true
    ) : StreamResult()
}

interface MusicProvider {
    val name: String
    val isOfficial: Boolean
    suspend fun search(query: String): List<SongInfo>
    suspend fun resolveStream(trackRef: String, title: String? = null, artist: String? = null): StreamResult
    fun invalidateStreamCache(trackRef: String, title: String? = null, artist: String? = null) = Unit
}

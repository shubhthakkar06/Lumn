package com.example.musicapp.data.network

import kotlinx.serialization.Serializable

@Serializable
data class SongInfo(
    val videoId: String,
    val title: String,
    val artist: String,
    val albumArtUrl: String,
    val audioStreamUrl: String? = null,
    val albumName: String = "",
    val popularityScore: Long = 0L,
    /** Provider duration in whole seconds; zero means the provider did not report it. */
    val durationSeconds: Long = 0L,
    /** Null for ordinary catalog tracks; false marks a Spotify import with no confident Rivara match. */
    val importMatchFound: Boolean? = null,
    val isEpisode: Boolean = false,
    val description: String = "",
    val releaseDate: String = "",
    /** Song language as reported by the catalog ("hindi", "english", "gujarati"…), if known. */
    val language: String = ""
)

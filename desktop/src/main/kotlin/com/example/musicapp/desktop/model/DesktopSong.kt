package com.example.musicapp.desktop.model

import kotlinx.serialization.Serializable

@Serializable
data class DesktopSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationSeconds: Long = 0L,
    val albumArtUrl: String = "",
    val audioUrl: String? = null,
    val isLiked: Boolean = false,
    val isEpisode: Boolean = false,
    val description: String = "",
    val releaseDate: String = "",
    val lyrics: List<String> = emptyList()
)

@Serializable
data class DesktopPlaylist(
    val id: String,
    val name: String,
    val creator: String = "Shubh Thakkar",
    val songCount: Int = 0
)

data class DesktopArtist(
    val name: String,
    val imageUrl: String,
    val borderColorHex: Long = 0xFF1ED760
)

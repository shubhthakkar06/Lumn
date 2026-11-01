package com.example.musicapp.wear.model

import kotlinx.serialization.Serializable

@Serializable
data class WearSongItem(
    val id: String,
    val title: String,
    val artist: String,
    val durationSeconds: Long = 0L,
    val albumArtUrl: String = ""
)

@Serializable
data class WearPlaybackState(
    val title: String = "No track playing",
    val artist: String = "Rivara Music",
    val isPlaying: Boolean = false,
    val progressMs: Long = 0L,
    val durationMs: Long = 180_000L,
    val volume: Float = 0.7f,
    val isPhoneConnected: Boolean = true,
    val queue: List<WearSongItem> = listOf(
        WearSongItem("1", "Yellow", "Coldplay", 269L),
        WearSongItem("2", "Starboy", "The Weeknd", 230L),
        WearSongItem("3", "Levitating", "Dua Lipa", 203L),
        WearSongItem("4", "Kesariya", "Arijit Singh", 268L),
        WearSongItem("5", "Tum Hi Ho", "Arijit Singh", 262L)
    )
)

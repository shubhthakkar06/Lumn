package com.example.musicapp.wear.model

import kotlinx.serialization.Serializable

@Serializable
data class WearSongItem(
    val id: String,
    val title: String,
    val artist: String,
    val durationSeconds: Long = 0L,
    val albumArtUrl: String = "",
    val audioUrl: String = ""
)

@Serializable
data class WearPlaybackState(
    val title: String = "Hymn for the Weekend",
    val artist: String = "Coldplay",
    val albumArtUrl: String = "https://c.saavncdn.com/141/A-Head-Full-of-Dreams-English-2015-20201104170723-500x500.jpg",
    val isPlaying: Boolean = false,
    val progressMs: Long = 0L,
    val durationMs: Long = 258_000L,
    val volume: Float = 0.8f,
    val userProfileName: String = "Shubh Thakkar",
    val userProfileEmail: String = "shubhthakkar06@gmail.com",
    val isPhoneConnected: Boolean = true,
    val queue: List<WearSongItem> = defaultWearMusicCatalog
)

val defaultWearMusicCatalog = listOf(
    WearSongItem(
        id = "j5-V3BSp",
        title = "Hymn for the Weekend",
        artist = "Coldplay",
        durationSeconds = 258L,
        albumArtUrl = "https://c.saavncdn.com/141/A-Head-Full-of-Dreams-English-2015-20201104170723-500x500.jpg",
        audioUrl = ""
    ),
    WearSongItem(
        id = "DYBuG4Ko",
        title = "Yellow",
        artist = "Coldplay",
        durationSeconds = 269L,
        albumArtUrl = "https://c.saavncdn.com/750/Yellow-English-2003-20201104171351-500x500.jpg",
        audioUrl = ""
    ),
    WearSongItem(
        id = "starboy_weeknd",
        title = "Starboy",
        artist = "The Weeknd",
        durationSeconds = 230L,
        albumArtUrl = "https://c.saavncdn.com/396/The-Highlights-English-2021-20240207045714-500x500.jpg",
        audioUrl = ""
    ),
    WearSongItem(
        id = "levitating_dualipa",
        title = "Levitating",
        artist = "Dua Lipa",
        durationSeconds = 203L,
        albumArtUrl = "https://c.saavncdn.com/665/Future-Nostalgia-English-2020-20260306223201-500x500.jpg",
        audioUrl = ""
    ),
    WearSongItem(
        id = "kesariya_arijit",
        title = "Kesariya",
        artist = "Arijit Singh",
        durationSeconds = 268L,
        albumArtUrl = "https://c.saavncdn.com/423/Arijit-Singh-All-Time-Best-Hindi-Hit-Love-Songs-Collection-Hindi-2026-20261008160834-500x500.jpg",
        audioUrl = ""
    ),
    WearSongItem(
        id = "tum_hi_ho_arijit",
        title = "Tum Hi Ho",
        artist = "Arijit Singh",
        durationSeconds = 262L,
        albumArtUrl = "https://c.saavncdn.com/430/Aashiqui-2-Hindi-2013-500x500.jpg",
        audioUrl = ""
    ),
    WearSongItem(
        id = "something_just_like_this",
        title = "Something Just Like This",
        artist = "The Chainsmokers & Coldplay",
        durationSeconds = 247L,
        albumArtUrl = "https://c.saavncdn.com/141/A-Head-Full-of-Dreams-English-2015-20201104170723-500x500.jpg",
        audioUrl = ""
    )
)

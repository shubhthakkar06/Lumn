package com.example.musicapp.ui.navigation

sealed interface Screen {
    object Home : Screen
    object Search : Screen
    object Library : Screen
    data class Playlist(
        val playlistId: String,
        val title: String,
        val coverUrl: String,
        val subtitle: String,
        val saves: String = "774 saves • 3h 52min"
    ) : Screen
    data class Artist(
        val artistName: String,
        val imageUrl: String = "",
        val listeners: String = "94.9M monthly listeners"
    ) : Screen
    object Settings : Screen
    object Recents : Screen
    object SoundCapsule : Screen
}

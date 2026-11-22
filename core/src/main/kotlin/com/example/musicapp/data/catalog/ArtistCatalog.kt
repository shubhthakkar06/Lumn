package com.example.musicapp.data.catalog

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Artist pages from JioSaavn, filled in from YouTube Music. JioSaavn cannot carry some
 * artists in India (many Pakistani singers, for example), so a thin or missing JioSaavn page
 * is completed with the artist's most played YouTube Music songs.
 */
object ArtistCatalog {
    private const val MIN_SONGS = 10

    suspend fun page(artistName: String, saavnId: String?, fallbackImage: String = ""): ArtistPage? = coroutineScope {
        val name = artistName.trim()
        val saavnJob = async { saavnId?.let { runCatching { SaavnCatalog.artistPage(it) }.getOrNull() } }
        val youtubeJob = async { runCatching { YouTubeMusic.artistSongs(name) }.getOrDefault(emptyList()) }
        val saavn = saavnJob.await()
        val youtube = youtubeJob.await()
        // A JioSaavn search can land on a different artist with a similar name.
        val sameArtist = saavn?.takeIf { it.name.equals(name, ignoreCase = true) }
        when {
            sameArtist != null && sameArtist.topSongs.size >= MIN_SONGS -> sameArtist
            sameArtist != null -> {
                val known = sameArtist.topSongs.map { it.title.lowercase() }.toSet()
                sameArtist.copy(topSongs = sameArtist.topSongs + youtube.filter { it.title.lowercase() !in known })
            }
            youtube.isNotEmpty() -> ArtistPage(
                id = "yt:$name",
                name = name,
                imageUrl = fallbackImage.ifBlank { youtube.first().albumArtUrl },
                followerCount = 0,
                bio = "",
                verified = false,
                topSongs = youtube,
                albums = emptyList(),
                similar = emptyList()
            )
            else -> saavn
        }
    }
}

package com.example.musicapp.data.playlist

import com.example.musicapp.data.network.SongInfo
import org.junit.Assert.*
import org.junit.Test

class SpotifyImportTest {

    @Test
    fun testTrackMatcherMatchesAccurately() {
        val imported = SpotifyImportedTrack(
            id = "t1",
            title = "Yellow",
            artists = "Coldplay",
            durationSeconds = 266L
        )

        val candidate1 = SongInfo(
            videoId = "saavn_1",
            title = "Yellow",
            artist = "Coldplay",
            albumArtUrl = "https://example.com/art.jpg",
            durationSeconds = 269L,
            audioStreamUrl = "https://example.com/audio.mp4"
        )

        val candidate2 = SongInfo(
            videoId = "saavn_2",
            title = "Something Else",
            artist = "Coldplay",
            albumArtUrl = "https://example.com/art.jpg",
            durationSeconds = 200L
        )

        val best = SpotifyTrackMatcher.bestMatch(imported, listOf(candidate1, candidate2))
        assertNotNull(best)
        assertEquals("saavn_1", best?.videoId)
        assertEquals("Yellow", best?.title)
    }

    @Test
    fun testTrackMatcherRejectsUnrelatedSongs() {
        val imported = SpotifyImportedTrack(
            id = "t2",
            title = "Starboy",
            artists = "The Weeknd",
            durationSeconds = 230L
        )

        val unrelated = SongInfo(
            videoId = "saavn_unrelated",
            title = "Channa Mereya",
            artist = "Arijit Singh",
            albumArtUrl = "https://example.com/art.jpg",
            durationSeconds = 289L
        )

        val best = SpotifyTrackMatcher.bestMatch(imported, listOf(unrelated))
        assertNull(best)
    }

    @Test
    fun testLargePlaylistPreservesAllTracksAndThumbnails() {
        val tracks = (1..170).map { i ->
            SpotifyImportedTrack(
                id = "track_$i",
                title = "Song $i",
                artists = "Artist $i",
                albumName = "Album $i",
                albumArtUrl = "https://i.scdn.co/image/art_$i.jpg",
                durationSeconds = 180L + i
            )
        }

        val playlist = SpotifyImportedPlaylist(
            id = "pl_170",
            name = "My 170 Songs",
            spotifyUrl = "https://open.spotify.com/playlist/pl_170",
            tracks = tracks,
            coverUrl = "https://i.scdn.co/image/playlist_cover.jpg"
        )

        assertEquals(170, playlist.tracks.size)
        assertEquals("https://i.scdn.co/image/playlist_cover.jpg", playlist.coverUrl)
        assertEquals("https://i.scdn.co/image/art_101.jpg", playlist.tracks[100].albumArtUrl)
        assertEquals("Song 101", playlist.tracks[100].title)
        assertEquals("https://i.scdn.co/image/art_170.jpg", playlist.tracks[169].albumArtUrl)
        assertEquals("Song 170", playlist.tracks[169].title)
    }

    @Test
    fun testTrackThumbnailFallbackToPlaylistCover() {
        val cover = "https://i.scdn.co/image/playlist_cover.jpg"
        val trackWithoutArt = SpotifyImportedTrack(
            id = "t_no_art",
            title = "Acoustic Tune",
            artists = "Singer",
            albumArtUrl = ""
        )

        val resolvedArt = trackWithoutArt.albumArtUrl.ifBlank { cover }
        assertEquals(cover, resolvedArt)
    }
}

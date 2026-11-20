package com.example.musicapp.data

import com.example.musicapp.data.auth.UserManager
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.playlist.PlaylistImportManager
import com.example.musicapp.data.playlist.SpotifyImportedPlaylist
import com.example.musicapp.data.playlist.SpotifyImportedTrack
import com.example.musicapp.data.provider.TrackMatching
import com.example.musicapp.data.storage.AtomicFiles
import com.example.musicapp.data.sync.DeviceSyncManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Pure-JVM checks for the Phase 1 stability and security fixes. */
class Phase1RegressionTest {

    @Test
    fun saltedPasswordHashesVerifyAndDiffer() {
        val first = UserManager.createPasswordHash("correct horse")
        val second = UserManager.createPasswordHash("correct horse")
        assertNotEquals("Each hash must use its own salt", first, second)
        assertTrue(UserManager.verifyPassword("correct horse", first))
        assertTrue(UserManager.verifyPassword("correct horse", second))
        assertFalse(UserManager.verifyPassword("wrong", first))
        assertFalse(UserManager.isLegacyPasswordHash(first))
    }

    @Test
    fun legacySha256HashesStillVerifySoOldAccountsCanUpgrade() {
        val legacy = UserManager.hashPassword("oldpass1")
        assertTrue(UserManager.isLegacyPasswordHash(legacy))
        assertTrue(UserManager.verifyPassword("oldpass1", legacy))
        assertFalse(UserManager.verifyPassword("oldpass2", legacy))
    }

    @Test
    fun emptyStoredHashNeverVerifies() {
        assertFalse(UserManager.verifyPassword("anything", ""))
    }

    private fun song(title: String, artist: String, duration: Long = 200, url: String? = "https://cdn/x.mp4") =
        SongInfo(videoId = "id_$title", title = title, artist = artist, albumArtUrl = "", audioStreamUrl = url, durationSeconds = duration)

    @Test
    fun fallbackRefusesUnrelatedSongs() {
        val hits = listOf(song("Some Other Track", "Unknown Indie Band"), song("Random Beat", "Producer X"))
        assertNull(TrackMatching.bestPlayable("Tum Hi Ho", "Arijit Singh", 262, hits))
    }

    @Test
    fun fallbackAcceptsTheSameRecording() {
        val hits = listOf(song("Random Beat", "Producer X"), song("Tum Hi Ho", "Arijit Singh", 261))
        assertEquals("Tum Hi Ho", TrackMatching.bestPlayable("Tum Hi Ho", "Arijit Singh", 262, hits)?.title)
    }

    @Test
    fun fallbackRejectsDifferentVersionsAndLengths() {
        assertNull(TrackMatching.bestPlayable("Kesariya", "Arijit Singh", 268, listOf(song("Kesariya (Slowed + Reverb)", "Arijit Singh", 300))))
        assertNull(TrackMatching.bestPlayable("Kesariya", "Arijit Singh", 268, listOf(song("Kesariya", "Arijit Singh", 520))))
    }

    @Test
    fun fallbackIgnoresHitsWithoutAudio() {
        assertNull(TrackMatching.bestPlayable("Tum Hi Ho", "Arijit Singh", 262, listOf(song("Tum Hi Ho", "Arijit Singh", 262, url = null))))
    }

    @Test
    fun importMarksOnlySearchedTracksAsUnmatched() {
        val matched = song("Yellow", "Coldplay").copy(videoId = "saavn_abc")
        val playlist = SpotifyImportedPlaylist(
            id = "p", name = "Mix", spotifyUrl = "",
            tracks = listOf(
                SpotifyImportedTrack("t1", "Yellow", "Coldplay", resolvedSong = matched, matchAttempted = true),
                SpotifyImportedTrack("t2", "Rare B-side", "Someone", matchAttempted = true),
                SpotifyImportedTrack("t3", "Later Song", "Someone")
            )
        )
        val songs = PlaylistImportManager.toSongs(playlist)
        assertEquals(true, songs[0].importMatchFound)
        assertEquals("saavn_abc", songs[0].videoId)
        assertEquals(false, songs[1].importMatchFound)
        assertNull("Tracks never searched are matched at play time, not flagged", songs[2].importMatchFound)
        assertTrue("Imported songs never carry stream URLs", songs.all { it.audioStreamUrl == null })
    }

    @Test
    fun macAddressInputIsNormalized() {
        assertEquals("192.168.1.20", DeviceSyncManager.normalizeHost(" http://192.168.1.20:8765/ "))
        assertEquals("192.168.1.20", DeviceSyncManager.normalizeHost("192.168.1.20"))
        assertEquals("mac.local", DeviceSyncManager.normalizeHost("mac.local:8765"))
    }

    @Test
    fun atomicWriteReplacesContentWithoutLeavingTempFiles() {
        val dir = Files.createTempDirectory("atomic").toFile()
        val target = File(dir, "data.json")
        AtomicFiles.writeText(target, "[1]")
        AtomicFiles.writeText(target, "[1,2]")
        assertEquals("[1,2]", target.readText())
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".tmp") })
        dir.deleteRecursively()
    }
}

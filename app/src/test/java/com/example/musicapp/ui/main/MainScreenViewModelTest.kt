package com.example.musicapp.ui.main

import com.example.musicapp.data.DataRepository
import com.example.musicapp.data.network.SongInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class MainScreenViewModelTest {
    @Test
    fun testFakeDataRepository_search() {
        val repo = FakeDataRepository()
        assertEquals("test_id", repo.songs.first().videoId)
        assertEquals("Test Title", repo.songs.first().title)
    }
}

private class FakeDataRepository : DataRepository {
    val songs = listOf(SongInfo("test_id", "Test Title", "Test Artist", ""))
    override suspend fun searchSongs(query: String): List<SongInfo> = songs
    override suspend fun getStreamUrl(videoId: String, songTitle: String?, artist: String?, durationSeconds: Long): String? = "http://example.com/audio.mp3"
    override fun prefetchStreamUrls(songs: List<SongInfo>) {}
}

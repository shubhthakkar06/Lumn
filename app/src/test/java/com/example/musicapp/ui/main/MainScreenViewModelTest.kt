package com.example.musicapp.ui.main

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.musicapp.data.DataRepository
import com.example.musicapp.data.network.SongInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainScreenViewModelTest {

    private class FakeDataRepository : DataRepository {
        val songs = listOf(SongInfo("test_id", "Test Title", "Test Artist", ""))
        val prefetched = mutableListOf<SongInfo>()
        override suspend fun searchSongs(query: String): List<SongInfo> = songs
        override suspend fun getStreamUrl(videoId: String, songTitle: String?, artist: String?, durationSeconds: Long): String? = "http://example.com/audio.mp3"
        override fun prefetchStreamUrls(songs: List<SongInfo>) {
            prefetched.addAll(songs)
        }
    }

    @Test
    fun testFakeDataRepository_search() {
        val repo = FakeDataRepository()
        assertEquals("test_id", repo.songs.first().videoId)
        assertEquals("Test Title", repo.songs.first().title)
    }

    @Test
    fun testPlayNext_insertsSongImmediatelyAfterCurrent() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = FakeDataRepository()
        val viewModel = MainScreenViewModel(repo, app)

        val songA = SongInfo("a", "Song A", "Artist A", "")
        val songB = SongInfo("b", "Song B", "Artist B", "")
        val songC = SongInfo("c", "Song C", "Artist C", "")

        // Initial play of songA with queue [songA, songC]
        viewModel.playSong(songA, listOf(songA, songC))
        assertEquals(2, viewModel.currentQueue.value.size)
        assertEquals("a", viewModel.currentQueue.value[0].videoId)
        assertEquals("c", viewModel.currentQueue.value[1].videoId)

        // Play Next song B
        viewModel.playNext(songB)

        assertEquals(3, viewModel.currentQueue.value.size)
        assertEquals("a", viewModel.currentQueue.value[0].videoId)
        assertEquals("b", viewModel.currentQueue.value[1].videoId)
        assertEquals("c", viewModel.currentQueue.value[2].videoId)
        assertFalse(viewModel.isShuffle.value)
    }

    @Test
    fun testAddToQueue_appendsSongToEnd() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = FakeDataRepository()
        val viewModel = MainScreenViewModel(repo, app)

        val songA = SongInfo("a", "Song A", "Artist A", "")
        val songB = SongInfo("b", "Song B", "Artist B", "")
        val songC = SongInfo("c", "Song C", "Artist C", "")

        viewModel.playSong(songA, listOf(songA, songB))
        viewModel.addToQueue(songC)

        assertEquals(3, viewModel.currentQueue.value.size)
        assertEquals("a", viewModel.currentQueue.value[0].videoId)
        assertEquals("b", viewModel.currentQueue.value[1].videoId)
        assertEquals("c", viewModel.currentQueue.value[2].videoId)
    }
}

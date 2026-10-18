package com.example.musicapp.playback

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.musicapp.data.DefaultDataRepository
import com.example.musicapp.data.DataRepository
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.settings.SettingsManager
import com.example.musicapp.ui.main.MainScreenViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class PlaybackAppFlowInstrumentedTest {

    @Test
    fun offlineModeUsesLocalTrackEvenWhenNetworkAndRemoteSongUrlAreAvailable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val settings = SettingsManager.getInstance(context)
        val wasOffline = settings.offlineMode.value
        if (!wasOffline) settings.toggleOfflineMode()

        val localTrack = File(context.cacheDir, "offline_mode_stays_online.wav").apply {
            writeBytes(testWaveBytes())
        }
        val lookups = AtomicInteger(0)
        val localOnlyRepository = object : DataRepository {
            override suspend fun searchSongs(query: String) = emptyList<SongInfo>()
            override suspend fun getStreamUrl(videoId: String, songTitle: String?, artist: String?): String? {
                lookups.incrementAndGet()
                return localTrack.toURI().toString()
            }
            override suspend fun getFallbackStreamUrl(videoId: String, songTitle: String?, artist: String?): String? = null
            override fun prefetchStreamUrls(songs: List<SongInfo>) = Unit
        }
        val store = ViewModelStore()
        val viewModel = ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                MainScreenViewModel(localOnlyRepository, context) as T
        })[MainScreenViewModel::class.java]

        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                viewModel.playSong(
                    SongInfo(
                        videoId = "offline-mode-track",
                        title = "Local track",
                        artist = "Test artist",
                        albumArtUrl = "",
                        audioStreamUrl = "https://example.invalid/remote-stream.m4a"
                    )
                )
            }
            withTimeout(10_000L) {
                while (!viewModel.isPlaying.value || viewModel.currentPosition.value < 250L) delay(100L)
            }
            assertTrue("Offline playback should ask for the local file instead of using the remote URL", lookups.get() > 0)
            assertTrue("Offline playback should advance while mobile data remains enabled", viewModel.currentPosition.value >= 250L)
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            if (!wasOffline) settings.toggleOfflineMode()
            localTrack.delete()
        }
    }

    @Test
    fun alanWalkerFadedPlaysThroughTheAppPlaybackService() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = ViewModelStore()
        val viewModel = ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                MainScreenViewModel(DefaultDataRepository(context), context) as T
        })[MainScreenViewModel::class.java]

        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                viewModel.playSong(
                    SongInfo(
                        videoId = "60ItHLz5WEA",
                        title = "Faded",
                        artist = "Alan Walker",
                        albumArtUrl = "https://i.ytimg.com/vi/60ItHLz5WEA/hqdefault.jpg"
                    )
                )
            }

            withTimeout(60_000L) {
                while (!viewModel.isPlaying.value || viewModel.currentPosition.value < 500L) delay(250L)
            }
            assertTrue("Alan Walker Faded did not advance in the app playback service", viewModel.currentPosition.value >= 500L)
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
        }
    }

    @Test
    fun somethingJustLikeThisPlaysThroughTheAppPlaybackService() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = ViewModelStore()
        val viewModel = ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                MainScreenViewModel(DefaultDataRepository(context), context) as T
        })[MainScreenViewModel::class.java]

        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                viewModel.playSong(
                    SongInfo(
                        videoId = "FM7MFYoylPA",
                        title = "Something Just Like This",
                        artist = "The Chainsmokers, Coldplay",
                        albumArtUrl = "https://i.ytimg.com/vi/FM7MFYoylPA/hqdefault.jpg"
                    )
                )
            }

            withTimeout(90_000L) {
                while (!viewModel.isPlaying.value || viewModel.currentPosition.value < 500L) delay(250L)
            }
            assertTrue("Something Just Like This did not advance in the app playback service", viewModel.currentPosition.value >= 500L)
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
        }
    }

    @Test
    fun coldplayTrackPlaysThroughTheAppPlaybackService() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = ViewModelStore()
        val viewModel = ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                MainScreenViewModel(DefaultDataRepository(context), context) as T
        })[MainScreenViewModel::class.java]

        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                viewModel.playSong(
                    SongInfo(
                        videoId = "dvgZkm1xWPE",
                        title = "Viva La Vida",
                        artist = "Coldplay",
                        albumArtUrl = "https://i.ytimg.com/vi/dvgZkm1xWPE/hqdefault.jpg"
                    )
                )
            }

            withTimeout(60_000L) {
                while (!viewModel.isPlaying.value || viewModel.currentPosition.value < 500L) {
                    delay(250L)
                }
            }
            assertTrue("The app playback service did not advance the Coldplay track", viewModel.currentPosition.value >= 500L)
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
        }
    }

    private fun testWaveBytes(): ByteArray {
        val sampleRate = 44_100
        val dataSize = sampleRate * 2
        return ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII)); putInt(36 + dataSize)
            put("WAVE".toByteArray(Charsets.US_ASCII)); put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16); putShort(1); putShort(1); putInt(sampleRate); putInt(sampleRate * 2)
            putShort(2); putShort(16); put("data".toByteArray(Charsets.US_ASCII)); putInt(dataSize)
            repeat(dataSize) { put(0) }
        }.array()
    }
}

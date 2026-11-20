package com.example.musicapp.data.history

import android.content.Context
import com.example.musicapp.data.network.SongInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Calendar

@Serializable
data class PlayedSongRecord(
    val id: String,
    val userId: String,
    val videoId: String,
    val title: String,
    val artist: String,
    val albumArtUrl: String,
    val playedAtMs: Long = System.currentTimeMillis()
)

data class MonthlyCapsuleStats(
    val monthName: String,
    val year: Int,
    val totalMinutes: Int,
    val topArtist: String,
    val topSongTitle: String,
    val topSongArtist: String,
    val topSongUrl: String,
    val playCount: Int,
    val uniqueArtistsCount: Int
)

class PlayHistoryManager private constructor(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val historyFile = File(context.filesDir, "play_history.json")
    private val historyBackupFile = File(context.filesDir, "play_history.backup.json")
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "rivara-history-writer").apply { isDaemon = true }
    }

    private val _history = MutableStateFlow<List<PlayedSongRecord>>(emptyList())
    val history: StateFlow<List<PlayedSongRecord>> = _history.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        fun read(file: File): List<PlayedSongRecord>? =
            if (!file.exists()) null
            else runCatching { json.decodeFromString<List<PlayedSongRecord>>(file.readText()) }.getOrNull()
        _history.value = read(historyFile) ?: read(historyBackupFile) ?: emptyList()
    }

    private fun saveHistory() {
        writer.execute {
            try {
                val content = json.encodeToString(_history.value)
                com.example.musicapp.data.storage.AtomicFiles.writeText(historyFile, content)
                com.example.musicapp.data.storage.AtomicFiles.writeText(historyBackupFile, content)
            } catch (e: Exception) {
                android.util.Log.e("PlayHistoryManager", "Could not save play history", e)
            }
        }
    }

    fun recordPlay(song: SongInfo, userId: String) {
        val record = PlayedSongRecord(
            id = "rec_${System.currentTimeMillis()}_${(1000..9999).random()}",
            userId = userId,
            videoId = song.videoId,
            title = song.title,
            artist = song.artist,
            albumArtUrl = song.albumArtUrl,
            playedAtMs = System.currentTimeMillis()
        )
        // Keep newest at front, cap at 500 items
        _history.update { (listOf(record) + it).take(500) }
        saveHistory()
    }

    fun getUserHistory(userId: String): List<PlayedSongRecord> {
        return _history.value.filter { it.userId == userId || it.userId.isEmpty() }
    }

    fun getMonthlyStats(userId: String, targetMonth: Int, targetYear: Int): MonthlyCapsuleStats {
        val userRecords = getUserHistory(userId)
        val cal = Calendar.getInstance()

        val matching = userRecords.filter { record ->
            cal.timeInMillis = record.playedAtMs
            val m = cal.get(Calendar.MONTH) // 0-based
            val y = cal.get(Calendar.YEAR)
            m == targetMonth && y == targetYear
        }

        val totalPlays = matching.size
        // Estimate ~3.5 minutes per play
        val totalMinutes = if (totalPlays > 0) totalPlays * 3 + (totalPlays % 3) else 45

        val artistCounts = matching.groupingBy { it.artist }.eachCount()
        val topArtist = artistCounts.maxByOrNull { it.value }?.key ?: "Your Top Artist"

        val songCounts = matching.groupingBy { it.videoId }.eachCount()
        val topSongId = songCounts.maxByOrNull { it.value }?.key
        val topRecord = matching.find { it.videoId == topSongId } ?: matching.firstOrNull()

        val monthName = when (targetMonth) {
            Calendar.JANUARY -> "January"
            Calendar.FEBRUARY -> "February"
            Calendar.MARCH -> "March"
            Calendar.APRIL -> "April"
            Calendar.MAY -> "May"
            Calendar.JUNE -> "June"
            Calendar.JULY -> "July"
            Calendar.AUGUST -> "August"
            Calendar.SEPTEMBER -> "September"
            Calendar.OCTOBER -> "October"
            Calendar.NOVEMBER -> "November"
            Calendar.DECEMBER -> "December"
            else -> "October"
        }

        return MonthlyCapsuleStats(
            monthName = monthName,
            year = targetYear,
            totalMinutes = totalMinutes,
            topArtist = topArtist,
            topSongTitle = topRecord?.title ?: "Top Song",
            topSongArtist = topRecord?.artist ?: topArtist,
            topSongUrl = topRecord?.albumArtUrl ?: "",
            playCount = totalPlays,
            uniqueArtistsCount = artistCounts.size.coerceAtLeast(1)
        )
    }

    companion object {
        @Volatile
        private var instance: PlayHistoryManager? = null

        fun getInstance(context: Context): PlayHistoryManager {
            return instance ?: synchronized(this) {
                instance ?: PlayHistoryManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

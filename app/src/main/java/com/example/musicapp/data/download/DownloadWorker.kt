package com.example.musicapp.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.musicapp.data.network.SongInfo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Downloads one song through WorkManager, so downloads keep going when the app is closed,
 * resume after the app is killed, and wait for a network connection.
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val song = inputData.getString(KEY_SONG)
            ?.let { runCatching { json.decodeFromString<SongInfo>(it) }.getOrNull() }
            ?: return Result.failure()
        val notify = inputData.getBoolean(KEY_NOTIFY, true)

        runCatching { setForeground(foregroundInfo(song.title)) }

        val manager = MusicDownloadManager.getInstance(applicationContext)
        return when (manager.performDownload(song, notify = notify && runAttemptCount >= MAX_ATTEMPTS - 1)) {
            MusicDownloadManager.Outcome.SUCCESS -> Result.success()
            MusicDownloadManager.Outcome.ALREADY_DONE -> Result.success()
            MusicDownloadManager.Outcome.FAILED -> if (runAttemptCount < MAX_ATTEMPTS - 1) Result.retry() else {
                manager.markFailed(song, notify)
                Result.failure()
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo("music")

    private fun foregroundInfo(title: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW))
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading")
            .setContentText(title)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(0, 0, true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_SONG = "song"
        const val KEY_NOTIFY = "notify"
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 4107
        private const val MAX_ATTEMPTS = 3
        private val json = Json { ignoreUnknownKeys = true }

        fun encode(song: SongInfo): String = json.encodeToString(song.copy(audioStreamUrl = song.audioStreamUrl?.takeIf { song.isEpisode }))
    }
}

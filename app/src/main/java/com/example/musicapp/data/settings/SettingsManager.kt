package com.example.musicapp.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class SettingsManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("spotify_settings_prefs", Context.MODE_PRIVATE)

    private val _dataSaverMode = MutableStateFlow(prefs.getString(KEY_DATA_SAVER, "Automatic") ?: "Automatic")
    val dataSaverMode: StateFlow<String> = _dataSaverMode.asStateFlow()

    private val _offlineMode = MutableStateFlow(prefs.getBoolean(KEY_OFFLINE, false))
    val offlineMode: StateFlow<Boolean> = _offlineMode.asStateFlow()

    private val _gaplessPlayback = MutableStateFlow(prefs.getBoolean(KEY_GAPLESS, true))
    val gaplessPlayback: StateFlow<Boolean> = _gaplessPlayback.asStateFlow()

    private val _automix = MutableStateFlow(prefs.getBoolean(KEY_AUTOMIX, true))
    val automix: StateFlow<Boolean> = _automix.asStateFlow()

    private val _autoplay = MutableStateFlow(prefs.getBoolean(KEY_AUTOPLAY, true))
    val autoplay: StateFlow<Boolean> = _autoplay.asStateFlow()

    private val _crossfadeSeconds = MutableStateFlow(prefs.getInt(KEY_CROSSFADE, 0))
    val crossfadeSeconds: StateFlow<Int> = _crossfadeSeconds.asStateFlow()

    private val _streamingQuality = MutableStateFlow(prefs.getString(KEY_STREAMING_QUALITY, "Very high") ?: "Very high")
    val streamingQuality: StateFlow<String> = _streamingQuality.asStateFlow()

    private val _downloadQuality = MutableStateFlow(prefs.getString(KEY_DOWNLOAD_QUALITY, "Very high") ?: "Very high")
    val downloadQuality: StateFlow<String> = _downloadQuality.asStateFlow()

    private val _canvasVideos = MutableStateFlow(prefs.getBoolean(KEY_CANVAS, true))
    val canvasVideos: StateFlow<Boolean> = _canvasVideos.asStateFlow()

    private val _pushNotifications = MutableStateFlow(prefs.getBoolean(KEY_PUSH_NOTIF, true))
    val pushNotifications: StateFlow<Boolean> = _pushNotifications.asStateFlow()

    fun cycleDataSaverMode(): String {
        val next = when (_dataSaverMode.value) {
            "Automatic" -> "On"
            "On" -> "Off"
            else -> "Automatic"
        }
        _dataSaverMode.value = next
        prefs.edit().putString(KEY_DATA_SAVER, next).apply()
        return next
    }

    fun toggleOfflineMode(): Boolean {
        val next = !_offlineMode.value
        _offlineMode.value = next
        prefs.edit().putBoolean(KEY_OFFLINE, next).apply()
        return next
    }

    fun setGaplessPlayback(enabled: Boolean) {
        _gaplessPlayback.value = enabled
        prefs.edit().putBoolean(KEY_GAPLESS, enabled).apply()
    }

    fun setAutomix(enabled: Boolean) {
        _automix.value = enabled
        prefs.edit().putBoolean(KEY_AUTOMIX, enabled).apply()
    }

    fun setAutoplay(enabled: Boolean) {
        _autoplay.value = enabled
        prefs.edit().putBoolean(KEY_AUTOPLAY, enabled).apply()
    }

    fun setCrossfadeSeconds(sec: Int) {
        _crossfadeSeconds.value = sec
        prefs.edit().putInt(KEY_CROSSFADE, sec).apply()
    }

    fun setStreamingQuality(quality: String) {
        _streamingQuality.value = quality
        prefs.edit().putString(KEY_STREAMING_QUALITY, quality).apply()
    }

    fun setDownloadQuality(quality: String) {
        _downloadQuality.value = quality
        prefs.edit().putString(KEY_DOWNLOAD_QUALITY, quality).apply()
    }

    fun setCanvasVideos(enabled: Boolean) {
        _canvasVideos.value = enabled
        prefs.edit().putBoolean(KEY_CANVAS, enabled).apply()
    }

    fun setPushNotifications(enabled: Boolean) {
        _pushNotifications.value = enabled
        prefs.edit().putBoolean(KEY_PUSH_NOTIF, enabled).apply()
    }

    fun getCacheSizeBytes(): Long {
        return getFolderSize(context.cacheDir) + getFolderSize(File(context.filesDir, "image_cache"))
    }

    fun clearCache(): Long {
        val freed = getCacheSizeBytes()
        try {
            context.cacheDir.deleteRecursively()
            File(context.filesDir, "image_cache").deleteRecursively()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return freed
    }

    private fun getFolderSize(dir: File): Long {
        var size: Long = 0
        if (dir.exists()) {
            dir.listFiles()?.forEach { file ->
                size += if (file.isDirectory) getFolderSize(file) else file.length()
            }
        }
        return size
    }

    companion object {
        private const val KEY_DATA_SAVER = "key_data_saver"
        private const val KEY_OFFLINE = "key_offline"
        private const val KEY_GAPLESS = "key_gapless"
        private const val KEY_AUTOMIX = "key_automix"
        private const val KEY_AUTOPLAY = "key_autoplay"
        private const val KEY_CROSSFADE = "key_crossfade"
        private const val KEY_STREAMING_QUALITY = "key_streaming_quality"
        private const val KEY_DOWNLOAD_QUALITY = "key_download_quality"
        private const val KEY_CANVAS = "key_canvas"
        private const val KEY_PUSH_NOTIF = "key_push_notif"

        @Volatile
        private var instance: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

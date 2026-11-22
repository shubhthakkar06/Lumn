package com.example.musicapp.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.media3.common.util.UnstableApi
import com.example.musicapp.ui.util.AudioDeviceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * Owns the sound settings and feeds them, tuned to the current output, to the player's
 * [CinematicAudioProcessor]. Settings persist across restarts.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class SoundEngine private constructor(private val context: Context) {
    data class Output(val kind: OutputKind, val name: String)

    private val prefs = context.getSharedPreferences("sound_engine", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<SoundSettings> = _settings.asStateFlow()

    private val _output = MutableStateFlow(detectOutput())
    val output: StateFlow<Output> = _output.asStateFlow()

    /** Processor of the live player; replaced when the playback service restarts. */
    @Volatile
    private var processor: CinematicAudioProcessor? = null

    init {
        // The device picker knows the real media route (including a device the user pinned).
        AudioDeviceManager.getInstance(context).onRouteChanged = { refreshOutput() }
    }

    fun newProcessor(): CinematicAudioProcessor = CinematicAudioProcessor().also {
        processor = it
        push()
    }

    fun selectMode(mode: SoundMode) {
        val adaptive = _settings.value.adaptive
        update(if (mode == SoundMode.CUSTOM) _settings.value.copy(mode = SoundMode.CUSTOM) else SoundSettings.preset(mode).copy(adaptive = adaptive && mode != SoundMode.STUDIO))
    }

    /** Applies a slider change; any manual tweak turns the mode into Custom. */
    fun tweak(change: (SoundSettings) -> SoundSettings) {
        update(change(_settings.value).copy(mode = SoundMode.CUSTOM))
    }

    fun update(settings: SoundSettings) {
        _settings.value = settings
        prefs.edit().putString(KEY, json.encodeToString(SoundSettings.serializer(), settings)).apply()
        push()
    }

    fun refreshOutput() {
        _output.value = detectOutput()
        push()
    }

    private fun push() {
        processor?.setParams(_settings.value.effective(_output.value.kind))
    }

    private fun load(): SoundSettings = prefs.getString(KEY, null)
        ?.let { runCatching { json.decodeFromString(SoundSettings.serializer(), it) }.getOrNull() }
        ?: SoundSettings.preset(SoundMode.CINEMATIC)

    private fun detectOutput(): Output {
        val active = AudioDeviceManager.getInstance(context).getActiveDevice()
        val name = active.name
        val lower = name.lowercase()
        val kind = when (active.type) {
            com.example.musicapp.ui.util.AudioDeviceType.PHONE_SPEAKER -> {
                val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                if (outputs.any { it.type in EXTERNAL_TYPES }) OutputKind.EXTERNAL_SPEAKER else OutputKind.SPEAKER
            }
            com.example.musicapp.ui.util.AudioDeviceType.BLUETOOTH -> when {
                listOf("car", "auto", "sync", "uconnect", "mylink", "hands-free", "carplay").any { lower.contains(it) } -> OutputKind.CAR
                listOf("buds", "airpod", "earbud", "tws", "freebuds", "nothing ear", "ear (", "liberty", "elite").any { lower.contains(it) } -> OutputKind.EARBUDS
                listOf("speaker", "soundbar", "boom", "flip", "charge", "soundlink", "home", "echo", "nest").any { lower.contains(it) } -> OutputKind.EXTERNAL_SPEAKER
                else -> OutputKind.HEADPHONES
            }
            else -> OutputKind.HEADPHONES
        }
        return Output(kind, name)
    }

    companion object {
        private const val KEY = "settings"
        private val EXTERNAL_TYPES = setOf(
            AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL, AudioDeviceInfo.TYPE_AUX_LINE
        )

        @Volatile
        private var instance: SoundEngine? = null

        fun get(context: Context): SoundEngine = instance ?: synchronized(this) {
            instance ?: SoundEngine(context.applicationContext).also { instance = it }
        }
    }
}

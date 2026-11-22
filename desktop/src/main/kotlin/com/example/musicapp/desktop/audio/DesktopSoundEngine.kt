package com.example.musicapp.desktop.audio

import com.example.musicapp.audio.CinematicDsp
import com.example.musicapp.audio.OutputKind
import com.example.musicapp.audio.SoundMode
import com.example.musicapp.audio.SoundSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Rivara Sound on the Mac: the same engine as the phone (3D space, surround, width, tone,
 * punch, limiter), run on the decoded 48 kHz stereo audio before it reaches CoreAudio.
 */
object DesktopSoundEngine {
    private val file = File(System.getProperty("user.home"), ".rivara/sound.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val dsp = CinematicDsp(48_000)
    private var floats = FloatArray(0)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<SoundSettings> = _settings.asStateFlow()

    private val _output = MutableStateFlow(OutputKind.SPEAKER to "Mac speakers")
    val output: StateFlow<Pair<OutputKind, String>> = _output.asStateFlow()

    init { push() }

    fun selectMode(mode: SoundMode) {
        val adaptive = _settings.value.adaptive
        update(if (mode == SoundMode.CUSTOM) _settings.value.copy(mode = mode) else SoundSettings.preset(mode).copy(adaptive = adaptive && mode != SoundMode.STUDIO))
    }

    fun tweak(change: (SoundSettings) -> SoundSettings) = update(change(_settings.value).copy(mode = SoundMode.CUSTOM))

    fun update(settings: SoundSettings) {
        _settings.value = settings
        runCatching { file.parentFile.mkdirs(); file.writeText(json.encodeToString(SoundSettings.serializer(), settings)) }
        push()
    }

    /** Called with the CoreAudio device name whenever the output changes (null = system default). */
    fun setOutputDevice(name: String?) {
        val label = name ?: "Mac speakers"
        val lower = label.lowercase()
        val kind = when {
            listOf("airpods", "buds", "earbud", "nothing ear", "freebuds").any { lower.contains(it) } -> OutputKind.EARBUDS
            listOf("headphone", "wh-", "bose", "sennheiser", "beats", "jabra", "headset", "external headphones").any { lower.contains(it) } -> OutputKind.HEADPHONES
            name == null || lower.contains("macbook") || lower.contains("built-in") || lower.contains("imac") -> OutputKind.SPEAKER
            else -> OutputKind.EXTERNAL_SPEAKER
        }
        _output.value = kind to label
        push()
    }

    fun reset() = dsp.reset()

    /** Processes interleaved 16-bit little-endian stereo PCM in place ([length] bytes, whole frames). */
    fun processPcm(buffer: ByteArray, length: Int) {
        val frames = length / 4
        if (frames == 0) return
        if (floats.size < frames * 2) floats = FloatArray(frames * 2)
        var b = 0
        for (i in 0 until frames * 2) {
            val sample = ((buffer[b].toInt() and 0xFF) or (buffer[b + 1].toInt() shl 8)).toShort()
            floats[i] = sample / 32768f
            b += 2
        }
        dsp.process(floats, frames)
        b = 0
        for (i in 0 until frames * 2) {
            val v = (floats[i].coerceIn(-1f, 1f) * 32767f).toInt()
            buffer[b] = (v and 0xFF).toByte()
            buffer[b + 1] = ((v shr 8) and 0xFF).toByte()
            b += 2
        }
    }

    private fun push() = dsp.setParams(_settings.value.effective(_output.value.first))

    private fun load(): SoundSettings = runCatching { json.decodeFromString(SoundSettings.serializer(), file.readText()) }
        .getOrDefault(SoundSettings.preset(SoundMode.CINEMATIC))
}

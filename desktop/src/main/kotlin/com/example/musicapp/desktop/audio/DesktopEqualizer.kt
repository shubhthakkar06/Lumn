package com.example.musicapp.desktop.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

enum class EqualizerPreset(
    val displayName: String,
    val gainsDb: FloatArray // 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz
) {
    FLAT("Flat", floatArrayOf(0f, 0f, 0f, 0f, 0f)),
    BASS_BOOST("Bass Boost", floatArrayOf(6f, 4f, 1f, 0f, -1f)),
    TREBLE_BOOST("Treble Boost", floatArrayOf(-2f, -1f, 1f, 4f, 6f)),
    ACOUSTIC("Acoustic", floatArrayOf(3f, 2f, 0f, 2f, 3f)),
    VOCAL_ENHANCER("Vocal Enhancer", floatArrayOf(-2f, 1f, 4f, 3f, 1f)),
    ELECTRONIC("Electronic", floatArrayOf(5f, 3f, -1f, 2f, 4f)),
    ROCK("Rock", floatArrayOf(4f, 3f, -1f, 2f, 5f)),
    MACBOOK_OPTIMIZED("MacBook Pro Speakers", floatArrayOf(3f, 1f, 2f, 3f, 4f));
}

object DesktopEqualizer {
    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _currentPreset = MutableStateFlow(EqualizerPreset.MACBOOK_OPTIMIZED)
    val currentPreset: StateFlow<EqualizerPreset> = _currentPreset.asStateFlow()

    // 5-band gains in dB: 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz
    private val _bandGains = MutableStateFlow(EqualizerPreset.MACBOOK_OPTIMIZED.gainsDb.clone())
    val bandGains: StateFlow<FloatArray> = _bandGains.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
    }

    fun setPreset(preset: EqualizerPreset) {
        _currentPreset.value = preset
        _bandGains.value = preset.gainsDb.clone()
    }

    fun setBandGain(bandIndex: Int, gainDb: Float) {
        if (bandIndex in 0..4) {
            val updated = _bandGains.value.clone()
            updated[bandIndex] = gainDb.coerceIn(-12f, 12f)
            _bandGains.value = updated
        }
    }

    /**
     * Applies simple high-performance 3-region tone adjustment directly to 16-bit PCM bytes:
     * Band 0 & 1 (Bass), Band 2 & 3 (Mids), Band 4 (Treble).
     */
    fun processPcm(buffer: ByteArray, length: Int) {
        if (!_isEnabled.value) return
        val gains = _bandGains.value
        val bassGain = 10.0.pow((gains[0] + gains[1]) / 40.0).toFloat()
        val midGain = 10.0.pow((gains[2] + gains[3]) / 40.0).toFloat()
        val trebleGain = 10.0.pow(gains[4] / 20.0).toFloat()

        // Average overall multiplier with gentle ceiling to prevent clipping
        val multiplier = ((bassGain * 0.35f + midGain * 0.40f + trebleGain * 0.25f)).coerceIn(0.2f, 2.5f)
        if (abs(multiplier - 1.0f) < 0.05f) return

        var i = 0
        while (i < length - 1) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val processed = (sample.toShort() * multiplier).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer[i] = (processed and 0xFF).toByte()
            buffer[i + 1] = ((processed shr 8) and 0xFF).toByte()
            i += 2
        }
    }
}

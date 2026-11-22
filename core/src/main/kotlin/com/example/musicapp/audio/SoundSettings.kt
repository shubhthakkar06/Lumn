package com.example.musicapp.audio

import kotlinx.serialization.Serializable

/** Where the sound is going; the engine tunes itself to it. */
enum class OutputKind(val label: String) {
    HEADPHONES("Headphones"),
    EARBUDS("Earbuds"),
    SPEAKER("Phone speaker"),
    CAR("Car"),
    EXTERNAL_SPEAKER("Speaker")
}

enum class SoundMode(val label: String, val blurb: String) {
    CINEMATIC("Cinematic 3D", "Wide, out-of-head sound with a real room around it"),
    CONCERT("Concert Hall", "Big hall ambience, like hearing it live"),
    DEEP_BASS("Deep Bass", "Heavy, tight low end without muddying vocals"),
    VOCAL("Vocal Focus", "Voices forward and clear, gentle ambience"),
    STUDIO("Studio", "Exactly as mastered: no processing"),
    CUSTOM("Custom", "Your own sliders")
}

/**
 * User-facing sound settings. Values are in natural units so the UI and the DSP agree:
 * dB for tone and loudness, 0..1 for amounts.
 */
@Serializable
data class SoundSettings(
    val mode: SoundMode = SoundMode.CINEMATIC,
    val bassDb: Float = 4f,
    val trebleDb: Float = 2.5f,
    val presenceDb: Float = 1f,
    /** 0 = original stereo, 1 = very wide. */
    val width: Float = 0.45f,
    /** 0 = dry, 1 = large room. */
    val space: Float = 0.4f,
    /** Headphone crossfeed / externalization, 0..1. */
    val surround: Float = 0.6f,
    /** Glue compression, 0..1. */
    val punch: Float = 0.45f,
    val loudnessDb: Float = 1.5f,
    /** Tune automatically to the connected output (speaker, buds, car…). */
    val adaptive: Boolean = true
) {
    companion object {
        fun preset(mode: SoundMode): SoundSettings = when (mode) {
            SoundMode.CINEMATIC -> SoundSettings(mode, 4f, 2.5f, 1f, 0.45f, 0.4f, 0.65f, 0.45f, 1.5f)
            SoundMode.CONCERT -> SoundSettings(mode, 2f, 1.5f, 0.5f, 0.35f, 0.85f, 0.55f, 0.3f, 1f)
            SoundMode.DEEP_BASS -> SoundSettings(mode, 8f, 1.5f, 0f, 0.25f, 0.15f, 0.4f, 0.6f, 1f)
            SoundMode.VOCAL -> SoundSettings(mode, -1f, 2f, 4f, 0.1f, 0.12f, 0.35f, 0.35f, 1f)
            SoundMode.STUDIO -> SoundSettings(mode, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, adaptive = false)
            SoundMode.CUSTOM -> SoundSettings(mode)
        }
    }

    /** What the DSP actually runs, after adapting to the output. */
    fun effective(output: OutputKind): DspParams {
        if (mode == SoundMode.STUDIO) return DspParams.BYPASS
        var bass = bassDb
        var treble = trebleDb
        var presence = presenceDb
        var w = width
        var room = space
        var crossfeed = surround
        if (adaptive) when (output) {
            OutputKind.SPEAKER -> {
                // Tiny speakers distort on deep bass and sit centimetres apart.
                bass = minOf(bass, 2.5f)
                presence += 1.5f
                w = minOf(w, 0.25f)
                room *= 0.6f
                crossfeed = 0f
            }
            OutputKind.EARBUDS -> {
                treble -= 0.5f
                crossfeed = minOf(1f, crossfeed * 1.1f)
            }
            OutputKind.HEADPHONES -> Unit
            OutputKind.CAR -> {
                bass += 1f
                presence += 1f
                room *= 0.35f
                crossfeed = 0f
            }
            OutputKind.EXTERNAL_SPEAKER -> {
                crossfeed = 0f
                room *= 0.7f
            }
        } else if (output == OutputKind.SPEAKER || output == OutputKind.CAR || output == OutputKind.EXTERNAL_SPEAKER) {
            // Crossfeed only makes sense when each ear hears only one channel.
            crossfeed = 0f
        }
        return DspParams(
            enabled = true,
            bassDb = bass.coerceIn(-8f, 12f),
            trebleDb = treble.coerceIn(-8f, 10f),
            presenceDb = presence.coerceIn(-6f, 8f),
            width = w.coerceIn(0f, 1f),
            space = room.coerceIn(0f, 1f),
            crossfeed = crossfeed.coerceIn(0f, 1f),
            punch = punch.coerceIn(0f, 1f),
            loudnessDb = loudnessDb.coerceIn(0f, 8f)
        )
    }
}

data class DspParams(
    val enabled: Boolean,
    val bassDb: Float,
    val trebleDb: Float,
    val presenceDb: Float,
    val width: Float,
    val space: Float,
    val crossfeed: Float,
    val punch: Float,
    val loudnessDb: Float
) {
    companion object {
        val BYPASS = DspParams(false, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    }
}

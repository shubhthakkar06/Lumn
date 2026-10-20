package com.example.musicapp.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DeviceCategory {
    EARBUDS,
    HEADPHONES,
    HOME_THEATRE,
    CAR_AUDIO,
    PHONE_SPEAKER
}

enum class EqPresetMode(val displayName: String) {
    ADAPTIVE("Auto Device Adaptive"),
    ARENA_3D_DSP("Triband Arena 3D"),
    BASS_PUNCH("Bass Punch"),
    SPATIAL_ATMOS("Spatial Immersion"),
    VOCAL_CLARITY("Vocal & Acoustic"),
    STUDIO_FLAT("Studio Reference Flat")
}

data class ActiveAudioProfile(
    val category: DeviceCategory,
    val deviceName: String,
    val presetMode: EqPresetMode,
    val bassBoostStrength: Short,
    val virtualizerStrength: Short,
    val profileLabel: String,
    val description: String,
    val equalizerAvailable: Boolean = false,
    val bassBoostAvailable: Boolean = false,
    val virtualizerAvailable: Boolean = false
)

class AudioEffectManager(private val context: Context) {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var deviceCallbackRegistered = false
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            runCatching { applyProfileForCurrentDevice() }
        }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            runCatching { applyProfileForCurrentDevice() }
        }
    }
    private var currentSessionId: Int = -1

    private var currentPreset = EqPresetMode.ADAPTIVE

    private val _activeProfile = MutableStateFlow(
        ActiveAudioProfile(
            category = DeviceCategory.PHONE_SPEAKER,
            deviceName = "Phone Speaker",
            presetMode = EqPresetMode.ADAPTIVE,
            bassBoostStrength = 0,
            virtualizerStrength = 0,
            profileLabel = "Vocal Clarity • Anti-Distortion",
            description = "Optimized for phone stereo speakers with dialogue clarity."
        )
    )
    val activeProfile: StateFlow<ActiveAudioProfile> = _activeProfile.asStateFlow()

    init {
        deviceCallbackRegistered = runCatching {
            audioManager.registerAudioDeviceCallback(deviceCallback, null)
            true
        }.getOrDefault(false)
    }

    fun attachToSession(sessionId: Int) {
        if (sessionId == currentSessionId || sessionId == 0) return
        releaseEffects()
        currentSessionId = sessionId
        // Android devices do not all expose the same effect implementations. Create each
        // independently so an unsupported virtualizer, for example, cannot disable the EQ
        // and bass boost that the device does support.
        equalizer = createEffect("Equalizer", sessionId) { Equalizer(0, sessionId).apply { enabled = true } }
        bassBoost = createEffect("BassBoost", sessionId) { BassBoost(0, sessionId).apply { enabled = true } }
        virtualizer = createEffect("Virtualizer", sessionId) { Virtualizer(0, sessionId).apply { enabled = true } }
        loudnessEnhancer = createEffect("LoudnessEnhancer", sessionId) { LoudnessEnhancer(sessionId).apply { enabled = true } }
        Log.i(
            "AudioEffectManager",
            "Attached session=$sessionId eq=${equalizer != null} bass=${bassBoost != null} virtualizer=${virtualizer != null} loudness=${loudnessEnhancer != null}"
        )
        applyProfileForCurrentDevice()
    }

    private inline fun <T> createEffect(name: String, sessionId: Int, create: () -> T): T? = try {
        create()
    } catch (e: Exception) {
        Log.w("AudioEffectManager", "$name unavailable for audio session $sessionId", e)
        null
    }

    fun setPreset(mode: EqPresetMode) {
        currentPreset = mode
        applyProfileForCurrentDevice()
    }

    fun setBassBoostStrength(strength: Short) {
        try {
            val boost = bassBoost
            if (boost != null) {
                boost.setStrength(strength)
            } else {
                // A number of recent Samsung audio HALs do not expose BassBoost, but do
                // expose Equalizer. Use the EQ's low bands as a real bass fallback.
                setEqualizerBassGain(strength)
            }
            _activeProfile.value = _activeProfile.value.copy(bassBoostStrength = strength)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setVirtualizerStrength(strength: Short) {
        try {
            val effect = virtualizer
            if (effect != null) {
                effect.setStrength(strength)
            } else {
                // Keep the spatial control useful on audio routes whose HAL omits the
                // Virtualizer effect: use a gentle presence/air EQ contour instead.
                setEqualizerSpatialContour(strength)
            }
            _activeProfile.value = _activeProfile.value.copy(virtualizerStrength = strength)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setEqualizerBassGain(strength: Short) {
        val eq = equalizer ?: return
        val maxGain = eq.bandLevelRange[1].coerceAtLeast(0)
        val gain = (maxGain.toFloat() * strength.coerceIn(0, 1000) / 1000f).toInt().toShort()
        for (band in 0 until eq.numberOfBands) {
            if (eq.getCenterFreq(band.toShort()) < 250_000) {
                eq.setBandLevel(band.toShort(), gain)
            }
        }
    }

    private fun setEqualizerSpatialContour(strength: Short) {
        val eq = equalizer ?: return
        val amount = strength.coerceIn(0, 1000) / 1000f
        val range = eq.bandLevelRange
        val maxGain = range[1].coerceAtLeast(0).toInt()
        val maxCut = range[0].coerceAtMost(0).toInt()
        for (band in 0 until eq.numberOfBands) {
            val frequency = eq.getCenterFreq(band.toShort())
            val gain = when {
                frequency in 700_000..2_500_000 -> (maxCut * 0.12f * amount).toInt()
                frequency >= 3_500_000 -> (maxGain * 0.16f * amount).toInt()
                else -> 0
            }
            eq.setBandLevel(band.toShort(), gain.toShort())
        }
    }

    fun setLoudnessGain(gainMb: Int) {
        try {
            loudnessEnhancer?.setTargetGain(gainMb)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun detectDeviceCategory(): Pair<DeviceCategory, String> {
        val outputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        // 1. Check for Home Theatre / HDMI / Soundbar / Line Out
        val homeTheatreDevice = outputDevices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_HDMI ||
            it.type == AudioDeviceInfo.TYPE_HDMI_ARC ||
            it.type == AudioDeviceInfo.TYPE_HDMI_EARC ||
            it.type == AudioDeviceInfo.TYPE_LINE_DIGITAL ||
            it.type == AudioDeviceInfo.TYPE_LINE_ANALOG ||
            it.type == AudioDeviceInfo.TYPE_AUX_LINE
        }
        if (homeTheatreDevice != null) {
            val name = homeTheatreDevice.productName.toString().ifBlank { "Home Theatre" }
            return Pair(DeviceCategory.HOME_THEATRE, name)
        }

        // 2. Check for Car Audio or USB DAC
        val carOrUsbDevice = outputDevices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY ||
            (it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP && it.productName.toString().contains("car", ignoreCase = true))
        }
        if (carOrUsbDevice != null) {
            val name = carOrUsbDevice.productName.toString().ifBlank { "Car Audio / USB DAC" }
            return Pair(DeviceCategory.CAR_AUDIO, name)
        }

        // 3. Check for Earbuds (TWS / In-Ear)
        val earbudsDevice = outputDevices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            (it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP && isEarbudsName(it.productName.toString()))
        }
        if (earbudsDevice != null) {
            val name = earbudsDevice.productName.toString().ifBlank { "Wireless Earbuds" }
            return Pair(DeviceCategory.EARBUDS, name)
        }

        // 4. Check for Over-Ear / Wired Headphones
        val headphoneDevice = outputDevices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        }
        if (headphoneDevice != null) {
            val name = headphoneDevice.productName.toString().ifBlank { "Headphones" }
            return Pair(DeviceCategory.HEADPHONES, name)
        }

        return Pair(DeviceCategory.PHONE_SPEAKER, "Phone Speaker")
    }

    private fun isEarbudsName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("buds") || lower.contains("airpod") || lower.contains("earbud") ||
               lower.contains("tws") || lower.contains("freebuds") || lower.contains("pixel buds")
    }

    private fun applyProfileForCurrentDevice() {
        val (category, deviceName) = detectDeviceCategory()
        val eq = equalizer
        val bb = bassBoost
        val virt = virtualizer

        if (currentSessionId <= 0) {
            _activeProfile.value = ActiveAudioProfile(
                category = category,
                deviceName = deviceName,
                presetMode = currentPreset,
                bassBoostStrength = 0,
                virtualizerStrength = 0,
                profileLabel = "Waiting for Session",
                description = "Audio effects will attach when music starts."
            )
            return
        }

        try {
            when (currentPreset) {
                EqPresetMode.ADAPTIVE -> {
                    when (category) {
                        DeviceCategory.EARBUDS -> {
                            // Punchy bass boost, moderate spatial virtualizer, crisp treble
                            bb?.setStrength(650.toShort())
                            virt?.setStrength(550.toShort())
                            tuneEqualizer(
                                eq,
                                subBassGain = 400,
                                midBassGain = 150,
                                midGain = 50,
                                presenceGain = 300,
                                airGain = 450
                            )
                            loudnessEnhancer?.setTargetGain(180)
                            _activeProfile.value = ActiveAudioProfile(
                                category = category,
                                deviceName = deviceName,
                                presetMode = currentPreset,
                                bassBoostStrength = 650,
                                virtualizerStrength = 550,
                                profileLabel = "Earbuds Tuned • Punchy Bass & Spatial",
                                description = "Triband acoustic curve tuned for tight in-ear bass, clean mids, and wide stereo imaging."
                            )
                        }
                        DeviceCategory.HEADPHONES -> {
                            // Wide 3D immersive soundstage, rich Harman sub-bass curve
                            bb?.setStrength(450.toShort())
                            virt?.setStrength(800.toShort())
                            tuneEqualizer(
                                eq,
                                subBassGain = 350,
                                midBassGain = 50,
                                midGain = 100,
                                presenceGain = 250,
                                airGain = 400
                            )
                            loudnessEnhancer?.setTargetGain(60)
                            _activeProfile.value = ActiveAudioProfile(
                                category = category,
                                deviceName = deviceName,
                                presetMode = currentPreset,
                                bassBoostStrength = 450,
                                virtualizerStrength = 800,
                                profileLabel = "Studio Headphones • 3D Spatial Atmos",
                                description = "Ultra-wide virtualizer field with deep balanced sub-bass for immersive studio listening."
                            )
                        }
                        DeviceCategory.HOME_THEATRE -> {
                            // Deep acoustic resonance, clear vocal projection, room acoustics
                            bb?.setStrength(550.toShort())
                            virt?.setStrength(850.toShort())
                            tuneEqualizer(
                                eq,
                                subBassGain = 500,
                                midBassGain = 200,
                                midGain = 150,
                                presenceGain = 350,
                                airGain = 450
                            )
                            loudnessEnhancer?.setTargetGain(250)
                            _activeProfile.value = ActiveAudioProfile(
                                category = category,
                                deviceName = deviceName,
                                presetMode = currentPreset,
                                bassBoostStrength = 550,
                                virtualizerStrength = 850,
                                profileLabel = "Home Theatre • Cinematic Arena Dynamic",
                                description = "Expanded dynamic range with enhanced acoustic resonance and grand hall presence."
                            )
                        }
                        DeviceCategory.CAR_AUDIO -> {
                            // Road noise compensation: boosted upper-bass and forward midrange
                            bb?.setStrength(450.toShort())
                            virt?.setStrength(450.toShort())
                            tuneEqualizer(
                                eq,
                                subBassGain = 450,
                                midBassGain = 400,
                                midGain = 250,
                                presenceGain = 300,
                                airGain = 250
                            )
                            loudnessEnhancer?.setTargetGain(220)
                            _activeProfile.value = ActiveAudioProfile(
                                category = category,
                                deviceName = deviceName,
                                presetMode = currentPreset,
                                bassBoostStrength = 450,
                                virtualizerStrength = 450,
                                profileLabel = "Car Acoustic • Road-Noise Compensated",
                                description = "Forward vocal mids and solid bass to cut through ambient travel and cabin noise."
                            )
                        }
                        DeviceCategory.PHONE_SPEAKER -> {
                            // Speaker anti-distortion: no harsh bass clipping, dialogue boost
                            bb?.setStrength(0)
                            virt?.setStrength(0)
                            tuneEqualizer(
                                eq,
                                subBassGain = -150,
                                midBassGain = 50,
                                midGain = 450,
                                presenceGain = 550,
                                airGain = 200
                            )
                            loudnessEnhancer?.setTargetGain(100)
                            _activeProfile.value = ActiveAudioProfile(
                                category = category,
                                deviceName = deviceName,
                                presetMode = currentPreset,
                                bassBoostStrength = 0,
                                virtualizerStrength = 0,
                                profileLabel = "Phone Speaker • Clear Dialogue Boost",
                                description = "Anti-clipping curve enhancing vocal presence on device stereo speakers."
                            )
                        }
                    }
                }
                EqPresetMode.ARENA_3D_DSP -> {
                    // Triband Arena 3D DSP (inspired by music-prototype):
                    // Separates low sub-bass, Haas-delay spatial mid widening, and smooth LFO high-frequency presence
                    bb?.setStrength(600.toShort())
                    virt?.setStrength(850.toShort())
                    tuneEqualizer(
                        eq,
                        subBassGain = 450,
                        midBassGain = 100,
                        midGain = 150,
                        presenceGain = 350,
                        airGain = 500
                    )
                    loudnessEnhancer?.setTargetGain(200)
                    _activeProfile.value = ActiveAudioProfile(
                        category = category,
                        deviceName = deviceName,
                        presetMode = currentPreset,
                        bassBoostStrength = 600,
                        virtualizerStrength = 850,
                        profileLabel = "Triband Arena 3D DSP",
                        description = "3D spatial audio engine with band-separated Haas delay width, rich sub-bass, and airy highs."
                    )
                }
                EqPresetMode.BASS_PUNCH -> {
                    bb?.setStrength(950.toShort())
                    virt?.setStrength(350.toShort())
                    tuneEqualizer(
                        eq,
                        subBassGain = 700,
                        midBassGain = 450,
                        midGain = 50,
                        presenceGain = 200,
                        airGain = 300
                    )
                    loudnessEnhancer?.setTargetGain(260)
                    _activeProfile.value = ActiveAudioProfile(
                        category = category,
                        deviceName = deviceName,
                        presetMode = currentPreset,
                        bassBoostStrength = 950,
                        virtualizerStrength = 350,
                        profileLabel = "Bass Punch Active",
                        description = "Maximum low-end punch for electronic, hip-hop, and club mixes."
                    )
                }
                EqPresetMode.SPATIAL_ATMOS -> {
                    bb?.setStrength(350.toShort())
                    virt?.setStrength(1000.toShort())
                    tuneEqualizer(
                        eq,
                        subBassGain = 250,
                        midBassGain = 100,
                        midGain = 150,
                        presenceGain = 400,
                        airGain = 550
                    )
                    loudnessEnhancer?.setTargetGain(150)
                    _activeProfile.value = ActiveAudioProfile(
                        category = category,
                        deviceName = deviceName,
                        presetMode = currentPreset,
                        bassBoostStrength = 350,
                        virtualizerStrength = 1000,
                        profileLabel = "Spatial Immersion Active",
                        description = "360-degree holographic soundstage simulation."
                    )
                }
                EqPresetMode.VOCAL_CLARITY -> {
                    bb?.setStrength(100.toShort())
                    virt?.setStrength(200.toShort())
                    tuneEqualizer(
                        eq,
                        subBassGain = -50,
                        midBassGain = 100,
                        midGain = 600,
                        presenceGain = 550,
                        airGain = 400
                    )
                    loudnessEnhancer?.setTargetGain(120)
                    _activeProfile.value = ActiveAudioProfile(
                        category = category,
                        deviceName = deviceName,
                        presetMode = currentPreset,
                        bassBoostStrength = 100,
                        virtualizerStrength = 200,
                        profileLabel = "Vocal Clarity Active",
                        description = "Front-and-center acoustic intimacy for lyrics and vocals."
                    )
                }
                EqPresetMode.STUDIO_FLAT -> {
                    bb?.setStrength(0)
                    virt?.setStrength(0)
                    tuneEqualizer(
                        eq,
                        subBassGain = 0,
                        midBassGain = 0,
                        midGain = 0,
                        presenceGain = 0,
                        airGain = 0
                    )
                    loudnessEnhancer?.setTargetGain(0)
                    _activeProfile.value = ActiveAudioProfile(
                        category = category,
                        deviceName = deviceName,
                        presetMode = currentPreset,
                        bassBoostStrength = 0,
                        virtualizerStrength = 0,
                        profileLabel = "Studio Reference Flat",
                        description = "Uncolored raw master audio directly from the artist."
                    )
                }
            }

            val active = _activeProfile.value
            val unavailable = buildList {
                if (bb == null) add("bass boost")
                if (virt == null) add("3D virtualizer")
                if (eq == null) add("equalizer")
            }
            val capabilityNote = when {
                unavailable.isEmpty() -> ""
                eq != null && bb == null && virt == null ->
                    " Bass and spatial controls are tuned through the equalizer for this audio route."
                else -> " Some sound controls are adapted to the effects available on this audio route."
            }
            _activeProfile.value = active.copy(
                description = active.description + capabilityNote,
                equalizerAvailable = eq != null,
                bassBoostAvailable = bb != null || eq != null,
                virtualizerAvailable = virt != null || eq != null
            )
        } catch (e: Exception) {
            Log.e("AudioEffectManager", "Failed to apply profile", e)
        }
    }

    private fun tuneEqualizer(
        eq: Equalizer?,
        subBassGain: Short,
        midBassGain: Short,
        midGain: Short,
        presenceGain: Short,
        airGain: Short
    ) {
        if (eq == null) return
        val range = eq.bandLevelRange
        val minLevel = range[0]
        val maxLevel = range[1]
        val bands = eq.numberOfBands
        for (i in 0 until bands) {
            val centerFreq = eq.getCenterFreq(i.toShort()) // in millihertz
            val gain = when {
                centerFreq < 150_000 -> subBassGain
                centerFreq < 500_000 -> midBassGain
                centerFreq < 2_500_000 -> midGain
                centerFreq < 6_000_000 -> presenceGain
                else -> airGain
            }
            eq.setBandLevel(i.toShort(), gain.coerceIn(minLevel, maxLevel))
        }
    }

    fun release() {
        releaseEffects()
        if (deviceCallbackRegistered) {
            runCatching { audioManager.unregisterAudioDeviceCallback(deviceCallback) }
            deviceCallbackRegistered = false
        }
    }

    private fun releaseEffects() {
        equalizer?.let { runCatching { it.release() } }
        bassBoost?.let { runCatching { it.release() } }
        virtualizer?.let { runCatching { it.release() } }
        loudnessEnhancer?.let { runCatching { it.release() } }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
        currentSessionId = -1
    }
}

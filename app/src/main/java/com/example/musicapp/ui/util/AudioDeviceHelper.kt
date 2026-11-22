package com.example.musicapp.ui.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AudioDeviceType {
    PHONE_SPEAKER,
    WIRED_HEADSET,
    BLUETOOTH,
    USB_AUDIO
}

data class ConnectedAudioDevice(
    val id: String,
    val name: String,
    val type: AudioDeviceType,
    val isDefaultOrActive: Boolean
) {
    val icon: ImageVector
        get() = when (type) {
            AudioDeviceType.PHONE_SPEAKER -> Icons.Default.PhoneAndroid
            AudioDeviceType.WIRED_HEADSET -> Icons.Default.Headphones
            AudioDeviceType.BLUETOOTH -> Icons.Default.Bluetooth
            AudioDeviceType.USB_AUDIO -> Icons.Default.Speaker
        }
}

/**
 * Media output picker. Each physical device appears once: a Bluetooth headset exposes a
 * music link (A2DP or LE Audio) and a phone-call link (SCO), and only the music link can
 * play music, so call links are never listed. Choosing a device pins the player to exactly
 * that output; "Automatic" hands routing back to Android.
 */
class AudioDeviceManager private constructor(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _devices = MutableStateFlow<List<ConnectedAudioDevice>>(emptyList())
    val devices: StateFlow<List<ConnectedAudioDevice>> = _devices.asStateFlow()

    private val _activeDeviceId = MutableStateFlow(SPEAKER_ID)
    val activeDeviceId: StateFlow<String> = _activeDeviceId.asStateFlow()

    /** Called after the media output changes (the sound engine retunes itself). */
    @Volatile
    var onRouteChanged: (() -> Unit)? = null

    /** Output the user picked, or null to follow Android's routing. */
    @Volatile
    private var pinnedId: String? = null

    init {
        refreshDevices()
        audioManager.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refreshDevices()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refreshDevices()
        }, Handler(Looper.getMainLooper()))
    }

    fun refreshDevices() {
        val outputs = mediaOutputs()
        val list = outputs.map { (id, info) ->
            ConnectedAudioDevice(id, displayName(info), typeOf(info), isDefaultOrActive = false)
        }.sortedBy { order(it.type) }

        // A pinned device that disconnected releases the pin, like Android itself does.
        if (pinnedId != null && list.none { it.id == pinnedId }) {
            pinnedId = null
            applyToPlayer()
        }
        val active = pinnedId ?: systemMediaRoute(outputs) ?: list.firstOrNull()?.id ?: SPEAKER_ID
        _devices.value = list.map { it.copy(isDefaultOrActive = it.id == active) }
        _activeDeviceId.value = active
        onRouteChanged?.invoke()
    }

    fun selectDevice(deviceId: String) {
        val outputs = mediaOutputs()
        // Picking what Android already routes to means "automatic"; no need to pin.
        pinnedId = if (deviceId == systemMediaRoute(outputs)) null else deviceId
        applyToPlayer()
        refreshDevices()
    }

    /** The exact output to pin the player to, or null for automatic. Read by PlaybackService. */
    fun preferredOutput(): AudioDeviceInfo? {
        val id = pinnedId ?: return null
        return mediaOutputs()[id]
    }

    fun getActiveDevice(): ConnectedAudioDevice {
        val currentId = _activeDeviceId.value
        return _devices.value.find { it.id == currentId }
            ?: _devices.value.firstOrNull()
            ?: ConnectedAudioDevice(SPEAKER_ID, "This phone", AudioDeviceType.PHONE_SPEAKER, true)
    }

    private fun applyToPlayer() {
        com.example.musicapp.service.PlaybackService.setPreferredAudioDevice(preferredOutput())
    }

    /** Media-capable outputs keyed by a stable id, one entry per physical device. */
    private fun mediaOutputs(): LinkedHashMap<String, AudioDeviceInfo> {
        val result = LinkedHashMap<String, AudioDeviceInfo>()
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type in MEDIA_TYPES && it.isSink }
            .sortedBy { preference(it.type) }
            .forEach { info ->
                val id = idFor(info)
                result.putIfAbsent(id, info)
            }
        return result
    }

    /** Where Android currently sends media, when it can tell us (Android 13+). */
    private fun systemMediaRoute(outputs: Map<String, AudioDeviceInfo>): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Older Android routes media to the most recently connected headset.
            return outputs.keys.minByOrNull { order(typeOf(outputs.getValue(it))) }
        }
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val routed = runCatching { audioManager.getAudioDevicesForAttributes(attributes) }.getOrNull().orEmpty()
        return routed.firstNotNullOfOrNull { attr ->
            outputs.entries.firstOrNull { (_, info) ->
                info.type == attr.type && (attr.address.isBlank() || info.address == attr.address)
            }?.key
        }
    }

    private fun idFor(info: AudioDeviceInfo): String = when (typeOf(info)) {
        AudioDeviceType.PHONE_SPEAKER -> SPEAKER_ID
        // A2DP and LE Audio links of one headset share its Bluetooth address.
        AudioDeviceType.BLUETOOTH -> "bt_" + info.address.ifBlank { info.productName.toString() }
        AudioDeviceType.WIRED_HEADSET -> "wired_" + info.productName
        AudioDeviceType.USB_AUDIO -> "usb_" + info.productName
    }

    private fun displayName(info: AudioDeviceInfo): String {
        val product = info.productName?.toString().orEmpty().trim()
        val generic = product.isBlank() || product == Build.MODEL || product.equals("Android", true)
        return when (typeOf(info)) {
            AudioDeviceType.PHONE_SPEAKER -> "This phone"
            AudioDeviceType.BLUETOOTH -> if (generic) "Bluetooth headphones" else product
            AudioDeviceType.WIRED_HEADSET -> if (generic) "Wired headphones" else product
            AudioDeviceType.USB_AUDIO -> if (generic) "USB audio" else product
        }
    }

    private fun typeOf(info: AudioDeviceInfo): AudioDeviceType = when (info.type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioDeviceType.PHONE_SPEAKER
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> AudioDeviceType.WIRED_HEADSET
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> AudioDeviceType.USB_AUDIO
        else -> AudioDeviceType.BLUETOOTH
    }

    /** Within one physical device, prefer the classic music link over LE Audio. */
    private fun preference(type: Int): Int = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> 0
        else -> 1
    }

    private fun order(type: AudioDeviceType): Int = when (type) {
        AudioDeviceType.BLUETOOTH -> 0
        AudioDeviceType.WIRED_HEADSET -> 1
        AudioDeviceType.USB_AUDIO -> 2
        AudioDeviceType.PHONE_SPEAKER -> 3
    }

    companion object {
        const val SPEAKER_ID = "phone_speaker"

        private val MEDIA_TYPES: Set<Int> = buildSet {
            add(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
            add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
            add(AudioDeviceInfo.TYPE_USB_HEADSET)
            add(AudioDeviceInfo.TYPE_USB_DEVICE)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(AudioDeviceInfo.TYPE_BLE_HEADSET)
                add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
            }
            add(AudioDeviceInfo.TYPE_HEARING_AID)
            // TYPE_BLUETOOTH_SCO is the phone-call link: it cannot play music.
        }

        @Volatile
        private var instance: AudioDeviceManager? = null

        fun getInstance(context: Context): AudioDeviceManager {
            return instance ?: synchronized(this) {
                instance ?: AudioDeviceManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

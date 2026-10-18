package com.example.musicapp.ui.util

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
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

class AudioDeviceManager private constructor(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _devices = MutableStateFlow<List<ConnectedAudioDevice>>(emptyList())
    val devices: StateFlow<List<ConnectedAudioDevice>> = _devices.asStateFlow()

    private val _activeDeviceId = MutableStateFlow("phone_speaker")
    val activeDeviceId: StateFlow<String> = _activeDeviceId.asStateFlow()

    init {
        refreshDevices()
        registerDeviceCallback()
    }

    fun refreshDevices() {
        val result = mutableListOf<ConnectedAudioDevice>()
        val outputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        var hasHeadphones = false
        var hasBluetooth = false

        for (device in outputDevices) {
            when (device.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_USB_HEADSET -> {
                    hasHeadphones = true
                    val deviceName = if (device.productName.isNotBlank() && device.productName != "Android") {
                        device.productName.toString()
                    } else {
                        "Wired Headphones"
                    }
                    result.add(
                        ConnectedAudioDevice(
                            id = "wired_${device.id}",
                            name = deviceName,
                            type = AudioDeviceType.WIRED_HEADSET,
                            isDefaultOrActive = true
                        )
                    )
                }
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET -> {
                    hasBluetooth = true
                    val deviceName = if (device.productName.isNotBlank() && device.productName != "Android") {
                        device.productName.toString()
                    } else {
                        "Bluetooth Audio"
                    }
                    result.add(
                        ConnectedAudioDevice(
                            id = "bt_${device.id}",
                            name = deviceName,
                            type = AudioDeviceType.BLUETOOTH,
                            isDefaultOrActive = !hasHeadphones
                        )
                    )
                }
            }
        }

        // Always include Phone Speaker as primary/fallback option
        result.add(
            ConnectedAudioDevice(
                id = "phone_speaker",
                name = "This Phone (Speaker)",
                type = AudioDeviceType.PHONE_SPEAKER,
                isDefaultOrActive = !hasHeadphones && !hasBluetooth
            )
        )

        _devices.value = result

        // If previously selected device is no longer present, fallback
        if (result.none { it.id == _activeDeviceId.value }) {
            _activeDeviceId.value = result.firstOrNull { it.isDefaultOrActive }?.id ?: "phone_speaker"
        }
    }

    fun selectDevice(deviceId: String) {
        _activeDeviceId.value = deviceId
        try {
            val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            if (deviceId == "phone_speaker") {
                val speaker = outputs.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                com.example.musicapp.service.PlaybackService.setPreferredAudioDevice(speaker)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val speakerComm = audioManager.availableCommunicationDevices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    if (speakerComm != null) {
                        audioManager.setCommunicationDevice(speakerComm)
                    }
                }
            } else if (deviceId.startsWith("bt_")) {
                val rawId = deviceId.removePrefix("bt_").toIntOrNull()
                val btDevice = outputs.find {
                    it.id == rawId ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
                com.example.musicapp.service.PlaybackService.setPreferredAudioDevice(btDevice)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    audioManager.clearCommunicationDevice()
                }
            } else if (deviceId.startsWith("wired_")) {
                val rawId = deviceId.removePrefix("wired_").toIntOrNull()
                val wiredDevice = outputs.find {
                    it.id == rawId ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                }
                com.example.musicapp.service.PlaybackService.setPreferredAudioDevice(wiredDevice)
            } else {
                com.example.musicapp.service.PlaybackService.setPreferredAudioDevice(null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getActiveDevice(): ConnectedAudioDevice {
        val currentId = _activeDeviceId.value
        return _devices.value.find { it.id == currentId }
            ?: _devices.value.firstOrNull()
            ?: ConnectedAudioDevice("phone_speaker", "This Phone (Speaker)", AudioDeviceType.PHONE_SPEAKER, true)
    }

    private fun registerDeviceCallback() {
        audioManager.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                refreshDevices()
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                refreshDevices()
            }
        }, null)
    }

    companion object {
        @Volatile
        private var instance: AudioDeviceManager? = null

        fun getInstance(context: Context): AudioDeviceManager {
            return instance ?: synchronized(this) {
                instance ?: AudioDeviceManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

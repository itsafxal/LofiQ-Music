package com.lofiq.core.common

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class AudioOutputRouteType {
    PHONE_SPEAKER,
    BLUETOOTH,
    WIRED_HEADPHONES,
    USB_AUDIO,
    OTHER,
    UNKNOWN
}

enum class AudioDeviceCategory {
    THIS_PHONE,
    WIRED_HEADSET,
    BLUETOOTH,
    USB,
    OTHER
}

data class AudioOutputDevice(
    val id: String,
    val name: String,
    val subtitle: String,
    val routeType: AudioOutputRouteType,
    val category: AudioDeviceCategory = when (routeType) {
        AudioOutputRouteType.PHONE_SPEAKER -> AudioDeviceCategory.THIS_PHONE
        AudioOutputRouteType.BLUETOOTH -> AudioDeviceCategory.BLUETOOTH
        AudioOutputRouteType.WIRED_HEADPHONES -> AudioDeviceCategory.WIRED_HEADSET
        AudioOutputRouteType.USB_AUDIO -> AudioDeviceCategory.USB
        else -> AudioDeviceCategory.OTHER
    },
    val isCurrent: Boolean = false,
    val icon: ImageVector = when (routeType) {
        AudioOutputRouteType.PHONE_SPEAKER -> Icons.Rounded.PhoneAndroid
        AudioOutputRouteType.BLUETOOTH -> Icons.Rounded.BluetoothAudio
        AudioOutputRouteType.WIRED_HEADPHONES -> Icons.Rounded.Headphones
        AudioOutputRouteType.USB_AUDIO -> Icons.Rounded.Headphones
        AudioOutputRouteType.OTHER -> Icons.Rounded.Speaker
        AudioOutputRouteType.UNKNOWN -> Icons.Rounded.GraphicEq
    },
    val rawDeviceInfo: AudioDeviceInfo? = null
) {
    val isBluetooth: Boolean get() = routeType == AudioOutputRouteType.BLUETOOTH
    val isHeadphones: Boolean get() = routeType == AudioOutputRouteType.WIRED_HEADPHONES || routeType == AudioOutputRouteType.USB_AUDIO
    val isSpeaker: Boolean get() = routeType == AudioOutputRouteType.PHONE_SPEAKER
}

/**
 * Authoritative, singleton audio routing manager for LofiQ.
 *
 * Responsibilities:
 * 1. Accurately detect the real active media playback route (STREAM_MUSIC / USAGE_MEDIA).
 * 2. Prevent false positives (never reporting Bluetooth when audio routes through Phone Speaker).
 * 3. Handle device switching smoothly without telephony mode hacks or stream reload.
 * 4. Maintain volume integrity by avoiding AudioAttributes.USAGE_VOICE_COMMUNICATION misuse.
 * 5. Provide observable real-time StateFlow updates across player UI and sheets.
 */
@Singleton
class AudioRoutingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exoPlayer: ExoPlayer,
    private val dispatchers: DispatcherProvider
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val managerScope = CoroutineScope(SupervisorJob() + dispatchers.main)

    private val _currentRoute = MutableStateFlow(createDefaultSpeakerDevice())
    val currentRoute: StateFlow<AudioOutputDevice> = _currentRoute.asStateFlow()

    private val _availableRoutes = MutableStateFlow<List<AudioOutputDevice>>(emptyList())
    val availableRoutes: StateFlow<List<AudioOutputDevice>> = _availableRoutes.asStateFlow()

    private var preferredAudioDeviceInfo: AudioDeviceInfo? = null
    private var isListenersRegistered = false

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            managerScope.launch {
                LofiQLogger.d(LofiQLogger.Category.PLAYBACK, "[ROUTING] Audio devices added: ${addedDevices?.size ?: 0}")
                refreshRoutes()
                delay(300)
                refreshRoutes()
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            managerScope.launch {
                LofiQLogger.d(LofiQLogger.Category.PLAYBACK, "[ROUTING] Audio devices removed: ${removedDevices?.size ?: 0}")
                // If preferred device was removed, reset preferred audio device
                if (preferredAudioDeviceInfo != null && removedDevices != null) {
                    if (removedDevices.any { it.id == preferredAudioDeviceInfo?.id }) {
                        preferredAudioDeviceInfo = null
                        exoPlayer.setPreferredAudioDevice(null)
                    }
                }
                refreshRoutes()
                delay(300)
                refreshRoutes()
            }
        }
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            managerScope.launch {
                LofiQLogger.d(LofiQLogger.Category.PLAYBACK, "[ROUTING] Broadcast event: $action")
                refreshRoutes()
                delay(300)
                refreshRoutes()
            }
        }
    }

    init {
        registerListeners()
        managerScope.launch(dispatchers.io) {
            refreshRoutes()
        }
    }

    private fun registerListeners() {
        if (isListenersRegistered) return
        try {
            val mainHandler = Handler(Looper.getMainLooper())
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)

            val filter = IntentFilter().apply {
                addAction(AudioManager.ACTION_HEADSET_PLUG)
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(broadcastReceiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(broadcastReceiver, filter)
                }
            } catch (secEx: SecurityException) {
                LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[ROUTING] Full bluetooth filter failed ($secEx). Falling back to safe audio intents.")
                val safeFilter = IntentFilter().apply {
                    addAction(AudioManager.ACTION_HEADSET_PLUG)
                    addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(broadcastReceiver, safeFilter, Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(broadcastReceiver, safeFilter)
                }
            }

            // Register MediaRouter2 callback on Android 11+ (API 30+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val mr2 = android.media.MediaRouter2.getInstance(context)
                    val routeCallback = object : android.media.MediaRouter2.RouteCallback() {
                        override fun onRoutesUpdated(routes: List<android.media.MediaRoute2Info>) {
                            managerScope.launch {
                                refreshRoutes()
                            }
                        }
                    }
                    mr2.registerRouteCallback(context.mainExecutor, routeCallback, android.media.RouteDiscoveryPreference.Builder(emptyList(), true).build())
                } catch (mrEx: Throwable) {
                    LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[ROUTING] MediaRouter2 callback registration error: ${mrEx.message}")
                }
            }

            isListenersRegistered = true
        } catch (t: Throwable) {
            LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[ROUTING] Listener registration failed: ${t.message}")
        }
    }

    /**
     * Re-evaluates all available output routes and determines the active media playback device.
     */
    fun refreshRoutes() {
        try {
            val outputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.toList() ?: emptyList()

            val available = mutableListOf<AudioOutputDevice>()

            // 1. Phone Speaker (Always available)
            val speakerRaw = outputDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            val speakerDevice = AudioOutputDevice(
                id = "this_phone",
                name = "Phone Speaker",
                subtitle = "Internal Speaker",
                routeType = AudioOutputRouteType.PHONE_SPEAKER,
                rawDeviceInfo = speakerRaw
            )
            available.add(speakerDevice)

            // 2. Wired Headphones / 3.5mm Headsets
            val wiredDevices = outputDevices.filter {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
            }
            for (device in wiredDevices) {
                val prodName = runCatching { device.productName?.toString()?.trim() }.getOrNull()
                val name = if (!prodName.isNullOrBlank() && prodName != "null") prodName else "Wired Headphones"
                available.add(
                    AudioOutputDevice(
                        id = "wired_${device.id}",
                        name = name,
                        subtitle = "Connected via 3.5mm Jack",
                        routeType = AudioOutputRouteType.WIRED_HEADPHONES,
                        rawDeviceInfo = device
                    )
                )
            }

            // 3. USB Audio Devices
            val usbDevices = outputDevices.filter {
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY)
            }
            for (device in usbDevices) {
                val prodName = runCatching { device.productName?.toString()?.trim() }.getOrNull()
                val name = if (!prodName.isNullOrBlank() && prodName != "null") prodName else "USB Audio"
                available.add(
                    AudioOutputDevice(
                        id = "usb_${device.id}",
                        name = name,
                        subtitle = "Connected via USB-C",
                        routeType = AudioOutputRouteType.USB_AUDIO,
                        rawDeviceInfo = device
                    )
                )
            }

            // 4. Bluetooth Audio Devices
            val btDevices = outputDevices.filter { dev ->
                when (dev.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_HEARING_AID -> true
                    else -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                            dev.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                            dev.type == AudioDeviceInfo.TYPE_BLE_BROADCAST
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                            dev.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                        } else false
                    }
                }
            }
            for (device in btDevices) {
                val prodName = runCatching { device.productName?.toString()?.trim() }.getOrNull()
                val name = if (!prodName.isNullOrBlank() && prodName != "null") prodName else "Bluetooth Audio"
                val subtitle = when (device.type) {
                    AudioDeviceInfo.TYPE_HEARING_AID -> "Hearing Aid"
                    else -> "Connected via Bluetooth"
                }
                available.add(
                    AudioOutputDevice(
                        id = "bt_${device.id}",
                        name = name,
                        subtitle = subtitle,
                        routeType = AudioOutputRouteType.BLUETOOTH,
                        rawDeviceInfo = device
                    )
                )
            }

            // 5. External / Line Out / HDMI
            val otherDevices = outputDevices.filter { dev ->
                dev.type == AudioDeviceInfo.TYPE_LINE_ANALOG ||
                dev.type == AudioDeviceInfo.TYPE_LINE_DIGITAL ||
                dev.type == AudioDeviceInfo.TYPE_HDMI ||
                dev.type == AudioDeviceInfo.TYPE_DOCK
            }
            for (device in otherDevices) {
                val prodName = runCatching { device.productName?.toString()?.trim() }.getOrNull()
                val name = if (!prodName.isNullOrBlank() && prodName != "null") prodName else "External Audio"
                available.add(
                    AudioOutputDevice(
                        id = "other_${device.id}",
                        name = name,
                        subtitle = "External Audio Out",
                        routeType = AudioOutputRouteType.OTHER,
                        rawDeviceInfo = device
                    )
                )
            }

            // Determine real active route
            val activeDevice = determineActiveDevice(available, outputDevices)

            val finalizedList = available.map { dev ->
                dev.copy(isCurrent = dev.id == activeDevice.id)
            }

            _availableRoutes.value = finalizedList
            _currentRoute.value = activeDevice.copy(isCurrent = true)

            LofiQLogger.i(
                LofiQLogger.Category.PLAYBACK,
                "[ROUTING] Active route: ${activeDevice.name} [type=${activeDevice.routeType}], availableCount=${available.size}"
            )
        } catch (t: Throwable) {
            LofiQLogger.e(LofiQLogger.Category.PLAYBACK, "[ROUTING] refreshRoutes error: ${t.message}", t)
            val fallback = createDefaultSpeakerDevice()
            _availableRoutes.value = listOf(fallback)
            _currentRoute.value = fallback
        }
    }

    /**
     * Authoritatively determines which device is currently handling media audio output.
     */
    private fun determineActiveDevice(
        available: List<AudioOutputDevice>,
        outputDevices: List<AudioDeviceInfo>
    ): AudioOutputDevice {
        // 1. Explicit ExoPlayer Preferred Device override
        if (preferredAudioDeviceInfo != null) {
            val matched = available.firstOrNull { it.rawDeviceInfo?.id == preferredAudioDeviceInfo?.id }
            if (matched != null) {
                return matched
            } else {
                preferredAudioDeviceInfo = null
                exoPlayer.setPreferredAudioDevice(null)
            }
        }

        // 2. Android 14+ (API 34+) MediaRouter2 authoritative system selection
        // Note: MediaRoute2Info.getType() and MediaRoute2Info.TYPE_BUILTIN_SPEAKER were added in API 34.
        // Calling selected.type on Android 11..13 will crash with NoSuchMethodError.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val mr2 = android.media.MediaRouter2.getInstance(context)
                val selected = mr2.systemController.selectedRoutes.firstOrNull()
                if (selected != null) {
                    val selName = selected.name?.toString() ?: ""
                    when (selected.type) {
                        android.media.MediaRoute2Info.TYPE_WIRED_HEADSET,
                        android.media.MediaRoute2Info.TYPE_WIRED_HEADPHONES -> {
                            val wired = available.firstOrNull { it.routeType == AudioOutputRouteType.WIRED_HEADPHONES }
                            if (wired != null) return wired
                        }
                        android.media.MediaRoute2Info.TYPE_USB_DEVICE,
                        android.media.MediaRoute2Info.TYPE_USB_HEADSET -> {
                            val usb = available.firstOrNull { it.routeType == AudioOutputRouteType.USB_AUDIO }
                            if (usb != null) return usb
                        }
                        android.media.MediaRoute2Info.TYPE_BLUETOOTH_A2DP,
                        android.media.MediaRoute2Info.TYPE_BLE_HEADSET,
                        android.media.MediaRoute2Info.TYPE_HEARING_AID -> {
                            val bt = available.firstOrNull {
                                it.routeType == AudioOutputRouteType.BLUETOOTH &&
                                (it.name.equals(selName, ignoreCase = true) || selName.contains(it.name, ignoreCase = true))
                            } ?: available.firstOrNull { it.routeType == AudioOutputRouteType.BLUETOOTH }
                            if (bt != null) return bt
                        }
                        android.media.MediaRoute2Info.TYPE_BUILTIN_SPEAKER -> {
                            val hasExternal = available.any { it.routeType != AudioOutputRouteType.PHONE_SPEAKER }
                            if (!hasExternal) {
                                val speaker = available.firstOrNull { it.routeType == AudioOutputRouteType.PHONE_SPEAKER }
                                if (speaker != null) return speaker
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[ROUTING] MediaRouter2 query error: ${t.message}")
            }
        }

        // 3. Fallback / Standard Android media audio routing hierarchy:
        // Priority A: Wired headset / 3.5mm
        val wired = available.firstOrNull { it.routeType == AudioOutputRouteType.WIRED_HEADPHONES }
        val isWiredOn = runCatching { audioManager.isWiredHeadsetOn }.getOrDefault(true)
        if (wired != null && isWiredOn) {
            return wired
        }

        // Priority B: USB Audio
        val usb = available.firstOrNull { it.routeType == AudioOutputRouteType.USB_AUDIO }
        if (usb != null) {
            return usb
        }

        // Priority C: Bluetooth A2DP / BLE
        val btDevices = available.filter { it.routeType == AudioOutputRouteType.BLUETOOTH }
        if (btDevices.isNotEmpty()) {
            val isBtA2dpExplicitlyFalse = runCatching { !audioManager.isBluetoothA2dpOn }.getOrDefault(false)
            if (!isBtA2dpExplicitlyFalse || runCatching { audioManager.isBluetoothA2dpOn }.getOrDefault(true)) {
                return btDevices.first()
            }
        }

        // Priority D: Phone Speaker
        return available.firstOrNull { it.routeType == AudioOutputRouteType.PHONE_SPEAKER }
            ?: createDefaultSpeakerDevice()
    }

    /**
     * Seamlessly routes media audio playback to target device without stopping playback,
     * restarting the player, or modifying volume.
     */
    fun switchToDevice(target: AudioOutputDevice) {
        try {
            if (target.routeType == AudioOutputRouteType.PHONE_SPEAKER) {
                val speaker = target.rawDeviceInfo ?: audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speaker != null) {
                    preferredAudioDeviceInfo = speaker
                    exoPlayer.setPreferredAudioDevice(speaker)
                } else {
                    preferredAudioDeviceInfo = null
                    exoPlayer.setPreferredAudioDevice(null)
                }
            } else if (target.rawDeviceInfo != null) {
                preferredAudioDeviceInfo = target.rawDeviceInfo
                exoPlayer.setPreferredAudioDevice(target.rawDeviceInfo)
            } else {
                preferredAudioDeviceInfo = null
                exoPlayer.setPreferredAudioDevice(null)
            }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[ROUTING] setPreferredAudioDevice failed: ${e.message}")
        }
        refreshRoutes()
    }

    /**
     * Opens the native Android 11+ System Media Output Switcher panel (Settings.Panel.ACTION_MEDIA_OUTPUT)
     * if supported, allowing the user to select system-wide output targets.
     */
    fun launchSystemOutputSwitcher(activityContext: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent("com.android.settings.panel.action.MEDIA_OUTPUT").apply {
                    putExtra("com.android.settings.panel.extra.PACKAGE_NAME", activityContext.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                activityContext.startActivity(intent)
            } catch (e: Exception) {
                LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[ROUTING] Launch system media output panel failed: ${e.message}")
            }
        }
    }

    companion object {
        fun createDefaultSpeakerDevice(): AudioOutputDevice {
            return AudioOutputDevice(
                id = "this_phone",
                name = "Phone Speaker",
                subtitle = "Internal Speaker",
                routeType = AudioOutputRouteType.PHONE_SPEAKER,
                isCurrent = true
            )
        }
    }
}

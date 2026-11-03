package com.lofiq.core.common

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lofiq.ui.haptics.Haptic
import com.lofiq.ui.haptics.rememberHaptics
import com.lofiq.ui.theme.AccentWhite
import com.lofiq.ui.theme.BrandTeal
import com.lofiq.ui.theme.TextPrimary
import com.lofiq.ui.theme.TextSecondary

data class AudioOutputManager(
    val currentDevice: AudioOutputDevice,
    val availableDevices: List<AudioOutputDevice>,
    val switchToDevice: (AudioOutputDevice) -> Unit,
    val openSystemSwitcher: (() -> Unit)? = null
)

@Composable
fun rememberCurrentAudioDevice(
    routingManager: AudioRoutingManager? = null
): Pair<AudioOutputDevice, () -> Unit> {
    val context = LocalContext.current
    var showSwitcherSheet by remember { mutableStateOf(false) }

    if (routingManager != null) {
        val currentRoute by routingManager.currentRoute.collectAsState()
        val availableRoutes by routingManager.availableRoutes.collectAsState()

        if (showSwitcherSheet) {
            AudioOutputSwitcherSheet(
                currentDevice = currentRoute,
                availableDevices = availableRoutes,
                onSelectDevice = { target ->
                    routingManager.switchToDevice(target)
                },
                onOpenSystemSwitcher = {
                    routingManager.launchSystemOutputSwitcher(context)
                },
                onDismiss = { showSwitcherSheet = false }
            )
        }

        return Pair(currentRoute) {
            showSwitcherSheet = true
        }
    } else {
        val audioOutputManager = rememberAudioOutputManager()
        if (showSwitcherSheet) {
            AudioOutputSwitcherSheet(
                manager = audioOutputManager,
                onDismiss = { showSwitcherSheet = false }
            )
        }

        return Pair(audioOutputManager.currentDevice) {
            showSwitcherSheet = true
        }
    }
}

@Composable
fun rememberAudioOutputManager(): AudioOutputManager {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var triggerUpdate by remember { mutableIntStateOf(0) }

    val (currentDevice, availableDevices) = remember(triggerUpdate) {
        getFallbackAudioOutputDevices(context, audioManager)
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                triggerUpdate++
            }
        }

        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        } catch (_: Throwable) {}

        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                triggerUpdate++
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                triggerUpdate++
            }
        }
        audioManager.registerAudioDeviceCallback(callback, null)

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
            try {
                audioManager.unregisterAudioDeviceCallback(callback)
            } catch (_: Exception) {}
        }
    }

    val switchDevice: (AudioOutputDevice) -> Unit = { target ->
        // No telephony hacks here; safely refresh local state
        triggerUpdate++
    }

    return AudioOutputManager(
        currentDevice = currentDevice,
        availableDevices = availableDevices,
        switchToDevice = switchDevice
    )
}

private fun getFallbackAudioOutputDevices(
    context: Context,
    audioManager: AudioManager
): Pair<AudioOutputDevice, List<AudioOutputDevice>> {
    val thisPhoneFallback = AudioOutputDevice(
        id = "this_phone",
        name = "Phone Speaker",
        subtitle = "Internal Speaker",
        routeType = AudioOutputRouteType.PHONE_SPEAKER,
        rawDeviceInfo = null,
        isCurrent = true
    )

    return try {
        val devices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList() }
                .getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val available = mutableListOf<AudioOutputDevice>()

        // 1. Phone Speaker
        val speakerRaw = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        val thisPhoneDevice = AudioOutputDevice(
            id = "this_phone",
            name = "Phone Speaker",
            subtitle = "Internal Speaker",
            routeType = AudioOutputRouteType.PHONE_SPEAKER,
            rawDeviceInfo = speakerRaw
        )
        available.add(thisPhoneDevice)

        // 2. Wired Headphones
        val wiredDevices = devices.filter {
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

        // 3. USB Audio
        val usbDevices = devices.filter {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
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

        // 4. Bluetooth Devices
        val btDevices = devices.filter { dev ->
            when (dev.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_HEARING_AID -> true
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET || dev.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                    } else false
                }
            }
        }
        for (device in btDevices) {
            val prodName = runCatching { device.productName?.toString()?.trim() }.getOrNull()
            val name = if (!prodName.isNullOrBlank() && prodName != "null") prodName else "Bluetooth Audio"
            available.add(
                AudioOutputDevice(
                    id = "bt_${device.id}",
                    name = name,
                    subtitle = "Connected via Bluetooth",
                    routeType = AudioOutputRouteType.BLUETOOTH,
                    rawDeviceInfo = device
                )
            )
        }

        // Determine current active device
        val wired = available.firstOrNull { it.routeType == AudioOutputRouteType.WIRED_HEADPHONES }
        val usb = available.firstOrNull { it.routeType == AudioOutputRouteType.USB_AUDIO }
        val bt = available.firstOrNull { it.routeType == AudioOutputRouteType.BLUETOOTH }

        val isWiredOn = runCatching { audioManager.isWiredHeadsetOn }.getOrDefault(true)
        val isBtA2dpExplicitlyFalse = runCatching { !audioManager.isBluetoothA2dpOn }.getOrDefault(false)

        val active = when {
            wired != null && isWiredOn -> wired
            usb != null -> usb
            bt != null && (!isBtA2dpExplicitlyFalse || runCatching { audioManager.isBluetoothA2dpOn }.getOrDefault(true)) -> bt
            else -> thisPhoneDevice
        }

        val finalizedList = available.map { dev ->
            dev.copy(isCurrent = dev.id == active.id)
        }

        Pair(active.copy(isCurrent = true), finalizedList)
    } catch (_: Throwable) {
        Pair(thisPhoneFallback, listOf(thisPhoneFallback))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioOutputSwitcherSheet(
    manager: AudioOutputManager,
    onDismiss: () -> Unit
) {
    AudioOutputSwitcherSheet(
        currentDevice = manager.currentDevice,
        availableDevices = manager.availableDevices,
        onSelectDevice = manager.switchToDevice,
        onOpenSystemSwitcher = manager.openSystemSwitcher,
        onDismiss = onDismiss
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioOutputSwitcherSheet(
    currentDevice: AudioOutputDevice,
    availableDevices: List<AudioOutputDevice>,
    onSelectDevice: (AudioOutputDevice) -> Unit,
    onOpenSystemSwitcher: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val haptics = rememberHaptics()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F0F14),
        contentColor = AccentWhite,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.35f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Audio Output",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = AccentWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    )
                    Text(
                        text = "Select where to route playback audio",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = AccentWhite.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Device list
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF16161D))
                    .border(1.dp, Color(0xFF262633), RoundedCornerShape(16.dp))
            ) {
                availableDevices.forEachIndexed { index, device ->
                    val isSelected = device.id == currentDevice.id || device.isCurrent

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptics.play(Haptic.Select)
                                onSelectDevice(device)
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Device Icon with circular background
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) BrandTeal.copy(alpha = 0.18f)
                                    else Color.White.copy(alpha = 0.06f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = device.icon,
                                contentDescription = null,
                                tint = if (isSelected) BrandTeal else AccentWhite.copy(alpha = 0.85f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Device Details
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = device.name,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = if (isSelected) AccentWhite else TextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    fontSize = 15.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = device.subtitle,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isSelected) BrandTeal.copy(alpha = 0.9f) else TextSecondary,
                                    fontSize = 12.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Selected indicator
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(BrandTeal),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Active output",
                                    tint = Color(0xFF0F0F14),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    if (index < availableDevices.size - 1) {
                        HorizontalDivider(
                            color = Color(0xFF262633),
                            thickness = 0.8.dp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }

            // Optional Android 11+ System Media Output Switcher launcher
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && onOpenSystemSwitcher != null) {
                OutlinedButton(
                    onClick = {
                        haptics.play(Haptic.Tap)
                        onOpenSystemSwitcher()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AccentWhite
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.20f))
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Devices,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Android System Output Switcher",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    )
                }
            }
        }
    }
}

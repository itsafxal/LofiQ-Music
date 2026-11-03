package com.lofiq.core

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.media3.exoplayer.ExoPlayer
import com.lofiq.core.common.*
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AudioRoutingManagerTest {

    private val context: Context = mockk(relaxed = true)
    private val audioManager: AudioManager = mockk(relaxed = true)
    private val exoPlayer: ExoPlayer = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testDispatchers: DispatcherProvider = mockk {
        every { main } returns testDispatcher
        every { io } returns testDispatcher
        every { default } returns testDispatcher
        every { unconfined } returns testDispatcher
    }

    @Before
    fun setUp() {
        every { context.getSystemService(Context.AUDIO_SERVICE) } returns audioManager
        every { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) } returns emptyArray()
    }

    @Test
    fun `test initial route is Phone Speaker`() = runTest {
        val manager = AudioRoutingManager(context, exoPlayer, testDispatchers)
        val route = manager.currentRoute.value

        assertEquals(AudioOutputRouteType.PHONE_SPEAKER, route.routeType)
        assertEquals("Phone Speaker", route.name)
        assertTrue(route.isSpeaker)
        assertFalse(route.isBluetooth)
    }

    @Test
    fun `test route type properties and flags`() {
        val speaker = AudioOutputDevice(
            id = "speaker",
            name = "Phone Speaker",
            subtitle = "Internal",
            routeType = AudioOutputRouteType.PHONE_SPEAKER
        )
        assertTrue(speaker.isSpeaker)
        assertFalse(speaker.isBluetooth)
        assertFalse(speaker.isHeadphones)

        val bt = AudioOutputDevice(
            id = "bt_1",
            name = "Sony WH-1000XM4",
            subtitle = "Bluetooth",
            routeType = AudioOutputRouteType.BLUETOOTH
        )
        assertFalse(bt.isSpeaker)
        assertTrue(bt.isBluetooth)
        assertFalse(bt.isHeadphones)

        val wired = AudioOutputDevice(
            id = "wired_1",
            name = "Wired Headphones",
            subtitle = "3.5mm",
            routeType = AudioOutputRouteType.WIRED_HEADPHONES
        )
        assertFalse(wired.isSpeaker)
        assertFalse(wired.isBluetooth)
        assertTrue(wired.isHeadphones)

        val usb = AudioOutputDevice(
            id = "usb_1",
            name = "USB DAC",
            subtitle = "USB-C",
            routeType = AudioOutputRouteType.USB_AUDIO
        )
        assertTrue(usb.isHeadphones)
        assertFalse(usb.isBluetooth)
    }

    @Test
    fun `test switching to Bluetooth device invokes setPreferredAudioDevice without stopping playback`() = runTest {
        val manager = AudioRoutingManager(context, exoPlayer, testDispatchers)
        val mockBtInfo = mockk<AudioDeviceInfo>(relaxed = true) {
            every { id } returns 42
            every { type } returns AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            every { productName } returns "Bose QC45"
        }

        val btTarget = AudioOutputDevice(
            id = "bt_42",
            name = "Bose QC45",
            subtitle = "Connected via Bluetooth",
            routeType = AudioOutputRouteType.BLUETOOTH,
            rawDeviceInfo = mockBtInfo
        )

        manager.switchToDevice(btTarget)

        // ExoPlayer must be routed to the Bluetooth AudioDeviceInfo directly
        verify { exoPlayer.setPreferredAudioDevice(mockBtInfo) }
        // Verify playback was never stopped or paused
        verify(exactly = 0) { exoPlayer.stop() }
        verify(exactly = 0) { exoPlayer.pause() }
    }

    @Test
    fun `test switching to Phone Speaker sets preferred speaker device`() = runTest {
        val manager = AudioRoutingManager(context, exoPlayer, testDispatchers)
        val mockSpeakerInfo = mockk<AudioDeviceInfo>(relaxed = true) {
            every { id } returns 1
            every { type } returns AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            every { productName } returns "Phone Speaker"
        }

        val speakerTarget = AudioOutputDevice(
            id = "this_phone",
            name = "Phone Speaker",
            subtitle = "Internal Speaker",
            routeType = AudioOutputRouteType.PHONE_SPEAKER,
            rawDeviceInfo = mockSpeakerInfo
        )

        manager.switchToDevice(speakerTarget)

        verify { exoPlayer.setPreferredAudioDevice(mockSpeakerInfo) }
        verify(exactly = 0) { exoPlayer.stop() }
    }

    @Test
    fun `test false positive prevention - Bluetooth connected but not A2DP active yields Phone Speaker`() = runTest {
        val mockSpeaker = mockk<AudioDeviceInfo>(relaxed = true) {
            every { id } returns 1
            every { type } returns AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            every { productName } returns "Phone Speaker"
        }
        val mockBtDevice = mockk<AudioDeviceInfo>(relaxed = true) {
            every { id } returns 2
            every { type } returns AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            every { productName } returns "Smart Watch"
        }

        every { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) } returns arrayOf(mockSpeaker, mockBtDevice)
        // A2DP media audio is NOT active (e.g., watch only connected for notifications/calls)
        every { audioManager.isBluetoothA2dpOn } returns false
        every { audioManager.isWiredHeadsetOn } returns false

        val manager = AudioRoutingManager(context, exoPlayer, testDispatchers)
        manager.refreshRoutes()

        val route = manager.currentRoute.value
        assertEquals(AudioOutputRouteType.PHONE_SPEAKER, route.routeType)
        assertEquals("Phone Speaker", route.name)
    }

    @Test
    fun `test Bluetooth active - routes to Bluetooth device with correct name`() = runTest {
        val mockSpeaker = mockk<AudioDeviceInfo>(relaxed = true) {
            every { id } returns 1
            every { type } returns AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            every { productName } returns "Phone Speaker"
        }
        val mockBtDevice = mockk<AudioDeviceInfo>(relaxed = true) {
            every { id } returns 10
            every { type } returns AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            every { productName } returns "AirPods Pro"
        }

        every { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) } returns arrayOf(mockSpeaker, mockBtDevice)
        every { audioManager.isBluetoothA2dpOn } returns true
        every { audioManager.isWiredHeadsetOn } returns false

        val manager = AudioRoutingManager(context, exoPlayer, testDispatchers)
        manager.refreshRoutes()

        val route = manager.currentRoute.value
        assertEquals(AudioOutputRouteType.BLUETOOTH, route.routeType)
        assertEquals("AirPods Pro", route.name)
        assertTrue(route.isBluetooth)
    }

    @Test
    fun `test Wired headphones insertion overrides phone speaker`() = runTest {
        val mockSpeaker = mockk<AudioDeviceInfo>(relaxed = true) {
            every { id } returns 1
            every { type } returns AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            every { productName } returns "Phone Speaker"
        }
        val mockWired = mockk<AudioDeviceInfo>(relaxed = true) {
            every { id } returns 5
            every { type } returns AudioDeviceInfo.TYPE_WIRED_HEADPHONES
            every { productName } returns "3.5mm Earphones"
        }

        every { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) } returns arrayOf(mockSpeaker, mockWired)
        every { audioManager.isWiredHeadsetOn } returns true
        every { audioManager.isBluetoothA2dpOn } returns false

        val manager = AudioRoutingManager(context, exoPlayer, testDispatchers)
        manager.refreshRoutes()

        val route = manager.currentRoute.value
        assertEquals(AudioOutputRouteType.WIRED_HEADPHONES, route.routeType)
        assertEquals("3.5mm Earphones", route.name)
        assertTrue(route.isHeadphones)
    }

    @Test
    fun `test initialization handles unexpected runtime exception or error gracefully without crashing`() = runTest {
        every { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) } throws RuntimeException("Hardware audio subsystem error")

        // Must not throw or crash the caller/startup thread
        val manager = AudioRoutingManager(context, exoPlayer, testDispatchers)
        manager.refreshRoutes()

        val route = manager.currentRoute.value
        assertEquals(AudioOutputRouteType.PHONE_SPEAKER, route.routeType)
        assertEquals("Phone Speaker", route.name)
        assertTrue(route.isSpeaker)
    }
}

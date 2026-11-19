package com.lofiq.playback.engine

import android.content.Context
import android.media.AudioManager
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import com.lofiq.core.common.AudioOutputDevice
import com.lofiq.core.common.AudioOutputRouteType
import com.lofiq.core.common.AudioRoutingManager
import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.model.PlaybackState
import com.lofiq.core.model.Song
import com.lofiq.feature.equalizer.AudioEffectsController
import io.mockk.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AudioPipelineMonitorTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private lateinit var mockContext: Context
    private lateinit var mockAudioManager: AudioManager
    private lateinit var mockPlaybackEngine: PlaybackEngine
    private lateinit var mockAudioRoutingManager: AudioRoutingManager
    private lateinit var mockAudioEffectsController: AudioEffectsController

    private val activePlayerFlow = MutableStateFlow<ExoPlayer>(mockk(relaxed = true))
    private val playerStateFlow = MutableStateFlow(PlaybackState())
    private val audioRouteFlow = MutableStateFlow(
        AudioOutputDevice(
            id = "phone_speaker",
            name = "Phone Speaker",
            subtitle = "Built-in Speaker",
            routeType = AudioOutputRouteType.PHONE_SPEAKER
        )
    )

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockAudioManager = mockk(relaxed = true)
        every { mockContext.getSystemService(Context.AUDIO_SERVICE) } returns mockAudioManager
        every { mockAudioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE) } returns "48000"

        mockPlaybackEngine = mockk(relaxed = true)
        every { mockPlaybackEngine.activePlayerFlow } returns activePlayerFlow
        every { mockPlaybackEngine.playerState } returns playerStateFlow

        mockAudioRoutingManager = mockk(relaxed = true)
        every { mockAudioRoutingManager.currentRoute } returns audioRouteFlow

        mockAudioEffectsController = mockk(relaxed = true)
        every { mockAudioEffectsController.isEqualizerEnabled() } returns true
        every { mockAudioEffectsController.isBassBoostEnabled() } returns false
        every { mockAudioEffectsController.isVirtualizerEnabled() } returns false
        every { mockAudioEffectsController.getCurrentPresetName() } returns "Flat"
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `test initial state binds to active player and creates default pipeline info`() = runTest(testDispatcher) {
        val player1: ExoPlayer = mockk(relaxed = true)
        activePlayerFlow.value = player1

        val monitor = AudioPipelineMonitor(
            context = mockContext,
            playbackEngine = mockPlaybackEngine,
            audioRoutingManager = mockAudioRoutingManager,
            audioEffectsController = mockAudioEffectsController,
            dispatchers = testDispatchers
        )
        runCurrent()

        verify { player1.addListener(any()) }
        verify { player1.addAnalyticsListener(any()) }

        val info = monitor.pipelineInfo.value
        assertNotNull(info)
        assertEquals("Phone Speaker", info.output.deviceName)
    }

    @Test
    fun `test dual-player handover unbinds old player and binds new player`() = runTest(testDispatcher) {
        val player1: ExoPlayer = mockk(relaxed = true)
        val player2: ExoPlayer = mockk(relaxed = true)
        activePlayerFlow.value = player1

        val monitor = AudioPipelineMonitor(
            context = mockContext,
            playbackEngine = mockPlaybackEngine,
            audioRoutingManager = mockAudioRoutingManager,
            audioEffectsController = mockAudioEffectsController,
            dispatchers = testDispatchers
        )
        runCurrent()

        // Handover to second player
        activePlayerFlow.value = player2
        runCurrent()

        // Verify unbind from player 1
        verify { player1.removeListener(any()) }
        verify { player1.removeAnalyticsListener(any()) }

        // Verify bind to player 2
        verify { player2.addListener(any()) }
        verify { player2.addAnalyticsListener(any()) }
    }

    @Test
    fun `test track info updates with song and provider metadata`() = runTest(testDispatcher) {
        val player1: ExoPlayer = mockk(relaxed = true)
        activePlayerFlow.value = player1

        val monitor = AudioPipelineMonitor(
            context = mockContext,
            playbackEngine = mockPlaybackEngine,
            audioRoutingManager = mockAudioRoutingManager,
            audioEffectsController = mockAudioEffectsController,
            dispatchers = testDispatchers
        )
        runCurrent()

        val testSong = Song(
            id = "saavn_12345",
            title = "Midnight Lofi Keys",
            artist = "Lofi Artist",
            providerId = "saavn",
            durationMs = 180000L
        )

        playerStateFlow.value = PlaybackState(
            currentSong = testSong,
            isPlaying = true,
            activeProviderId = "saavn"
        )
        runCurrent()

        val info = monitor.pipelineInfo.value
        assertEquals("Midnight Lofi Keys", info.trackTitle)
        assertEquals("Lofi Artist", info.trackArtist)
        assertEquals("HQ", info.trackInfo.qualityBadge)
        assertEquals("JioSaavn Official Stream", info.trackInfo.sourceProvider)
        assertTrue(info.isLiveActive)
    }

    @Test
    fun `test hardware resampling detection between 44_1kHz and 48kHz`() = runTest(testDispatcher) {
        val player1: ExoPlayer = mockk(relaxed = true)
        activePlayerFlow.value = player1

        val monitor = AudioPipelineMonitor(
            context = mockContext,
            playbackEngine = mockPlaybackEngine,
            audioRoutingManager = mockAudioRoutingManager,
            audioEffectsController = mockAudioEffectsController,
            dispatchers = testDispatchers
        )
        runCurrent()

        // When input rate is 44100 and output hardware is 48000, resampler must report active resampling
        val testFormat = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setSampleRate(44100)
            .setChannelCount(2)
            .setAverageBitrate(320000)
            .build()

        monitor.javaClass.getDeclaredMethod(
            "handleAudioFormatChanged",
            ExoPlayer::class.java,
            Format::class.java
        ).apply {
            isAccessible = true
            invoke(monitor, player1, testFormat)
        }

        val info = monitor.pipelineInfo.value
        assertEquals(44100, info.trackInfo.sampleRateHz)
        assertEquals(320000, info.trackInfo.bitrateBps)
        assertEquals(2, info.trackInfo.channelCount)
        assertTrue(info.resampler.isResamplingRequired)
    }

    @Test
    fun `test stale callbacks from discarded player are ignored`() = runTest(testDispatcher) {
        val player1: ExoPlayer = mockk(relaxed = true)
        val player2: ExoPlayer = mockk(relaxed = true)
        activePlayerFlow.value = player1

        val monitor = AudioPipelineMonitor(
            context = mockContext,
            playbackEngine = mockPlaybackEngine,
            audioRoutingManager = mockAudioRoutingManager,
            audioEffectsController = mockAudioEffectsController,
            dispatchers = testDispatchers
        )
        runCurrent()

        // Promote player 2
        activePlayerFlow.value = player2
        runCurrent()

        // Now player 1 fires a late callback with old format
        val staleFormat = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_OPUS)
            .setSampleRate(48000)
            .setChannelCount(2)
            .setAverageBitrate(128000)
            .build()

        monitor.javaClass.getDeclaredMethod(
            "handleAudioFormatChanged",
            ExoPlayer::class.java,
            Format::class.java
        ).apply {
            isAccessible = true
            invoke(monitor, player1, staleFormat)
        }

        // Must NOT overwrite with stale player 1 data
        assertNotEquals(128000, monitor.pipelineInfo.value.trackInfo.bitrateBps)
    }

    @Test
    fun `test unknown bitrate fallback does not fabricate fake lossless or 320kbps claim`() = runTest(testDispatcher) {
        val player1: ExoPlayer = mockk(relaxed = true)
        activePlayerFlow.value = player1

        val monitor = AudioPipelineMonitor(
            context = mockContext,
            playbackEngine = mockPlaybackEngine,
            audioRoutingManager = mockAudioRoutingManager,
            audioEffectsController = mockAudioEffectsController,
            dispatchers = testDispatchers
        )
        runCurrent()

        // Format with Format.NO_VALUE (-1) bitrate
        val vbrFormat = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setSampleRate(44100)
            .setChannelCount(2)
            .setAverageBitrate(Format.NO_VALUE)
            .build()

        monitor.javaClass.getDeclaredMethod(
            "handleAudioFormatChanged",
            ExoPlayer::class.java,
            Format::class.java
        ).apply {
            isAccessible = true
            invoke(monitor, player1, vbrFormat)
        }

        val info = monitor.pipelineInfo.value
        assertEquals("Unknown (VBR)", info.trackInfo.formattedBitrate)
        assertFalse("Must never falsely claim lossless for unknown bitrate AAC", info.trackInfo.isLossless == true)
    }
}

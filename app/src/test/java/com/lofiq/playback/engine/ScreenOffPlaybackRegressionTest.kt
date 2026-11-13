package com.lofiq.playback.engine

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.lofiq.core.common.AudioRoutingManager
import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.common.NetworkQualityMonitor
import com.lofiq.core.database.dao.FavoriteDao
import com.lofiq.core.database.dao.PlaybackHistoryDao
import com.lofiq.core.database.dao.QueueSnapshotDao
import com.lofiq.core.database.dao.SongDao
import com.lofiq.core.datastore.LofiQPreferences
import com.lofiq.core.model.AudioQuality
import com.lofiq.core.model.PlaybackRepeatMode
import com.lofiq.core.model.Song
import com.lofiq.resolver.cache.ResolutionCache
import com.lofiq.resolver.core.ResolveRequest
import com.lofiq.resolver.core.ResolutionResult
import com.lofiq.resolver.manager.ResolverManager
import com.lofiq.resolver.prefetch.PrefetchManager
import io.mockk.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Regression test suite verifying that LofiQ's Seamless Gapless Playback and Equal-Power Crossfade
 * operate reliably when the phone screen is OFF.
 *
 * Specifically tests:
 * 1. Synchronous MediaSession promotion before old player cleanup (preventing dropped foreground).
 * 2. Unbroken system AudioFocus across transitions.
 * 3. Short-lived WakeLock protection during crossfade & preload execution.
 * 4. Multiple (5+) consecutive track transitions without pause or gap.
 * 5. Audio focus interruption (transient loss, recovery, ducking).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScreenOffPlaybackRegressionTest {

    private class FakeSlot(val name: String, val sessionId: Int) {
        var listener: Player.Listener? = null
        val items = mutableListOf<MediaItem>()
        var volume: Float = 1f
        var playWhenReady: Boolean = false
        var playbackState: Int = Player.STATE_IDLE
        var isPlaying: Boolean = false
        var positionMs: Long = 0L
        var durationMs: Long = 180_000L
        var bufferedMs: Long = 30_000L
        var repeatMode: Int = Player.REPEAT_MODE_OFF
        val events = mutableListOf<String>()
    }

    private fun createMockExoPlayer(state: FakeSlot): ExoPlayer {
        val player = mockk<ExoPlayer>(relaxed = true)
        every { player.audioSessionId } returns state.sessionId
        every { player.addListener(any()) } answers { state.listener = firstArg() }
        every { player.volume } answers { state.volume }
        every { player.volume = any() } answers {
            state.volume = firstArg()
            state.events.add("${state.name}:volume=${state.volume}")
        }
        every { player.playWhenReady } answers { state.playWhenReady }
        every { player.playWhenReady = any() } answers { state.playWhenReady = firstArg() }
        every { player.playbackState } answers { state.playbackState }
        every { player.isPlaying } answers { state.isPlaying }
        every { player.currentPosition } answers { state.positionMs }
        every { player.duration } answers { state.durationMs }
        every { player.bufferedPosition } answers { state.bufferedMs }
        every { player.mediaItemCount } answers { state.items.size }
        every { player.currentMediaItem } answers { state.items.firstOrNull() }
        every { player.repeatMode } answers { state.repeatMode }
        every { player.repeatMode = any() } answers { state.repeatMode = firstArg() }

        every { player.setMediaItem(any(), any<Boolean>()) } answers {
            state.items.clear()
            state.items.add(firstArg())
            state.events.add("${state.name}:setMediaItem")
        }
        every { player.setMediaItem(any(), any<Long>()) } answers {
            state.items.clear()
            state.items.add(firstArg())
            state.positionMs = secondArg()
            state.events.add("${state.name}:setMediaItem(pos=${state.positionMs})")
        }
        every { player.prepare() } answers {
            state.playbackState = Player.STATE_READY
            state.events.add("${state.name}:prepare")
            state.listener?.onPlaybackStateChanged(Player.STATE_READY)
        }
        every { player.play() } answers {
            state.playWhenReady = true
            state.isPlaying = true
            state.playbackState = Player.STATE_READY
            state.events.add("${state.name}:play")
            state.listener?.onIsPlayingChanged(true)
        }
        every { player.pause() } answers {
            state.playWhenReady = false
            state.isPlaying = false
            state.events.add("${state.name}:pause")
            state.listener?.onIsPlayingChanged(false)
        }
        every { player.stop() } answers {
            state.playWhenReady = false
            state.isPlaying = false
            state.playbackState = Player.STATE_IDLE
            state.events.add("${state.name}:stop")
            state.listener?.onPlaybackStateChanged(Player.STATE_IDLE)
        }
        every { player.clearMediaItems() } answers {
            state.items.clear()
            state.events.add("${state.name}:clearMediaItems")
        }
        every { player.seekTo(any<Long>()) } answers {
            state.positionMs = firstArg()
            state.events.add("${state.name}:seekTo=${state.positionMs}")
        }
        return player
    }

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private lateinit var tempDir: File
    private lateinit var slotA: FakeSlot
    private lateinit var slotB: FakeSlot
    private lateinit var playerSlotA: ExoPlayer
    private lateinit var playerSlotB: ExoPlayer

    private lateinit var audioFocusManager: PlaybackAudioFocusManager
    private lateinit var wakeLockManager: PlaybackWakeLockManager
    private lateinit var screenStateMonitor: ScreenStateMonitor
    private lateinit var diagnosticLogger: PlaybackDiagnosticLogger

    private lateinit var engine: PlaybackEngine

    private fun song(id: String, durationSec: Long = 180L) = Song(
        id = id,
        title = "Track $id",
        artist = "Artist $id",
        album = "Album $id",
        durationSeconds = durationSec,
        artworkUrl = "",
        highResArtworkUrl = "",
        sourceUrl = "",
        providerId = "jiosaavn"
    )

    @Before
    fun setup() {
        tempDir = Files.createTempDirectory("lofiq_screen_off_test").toFile()
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)
        mockkStatic(Environment::class)
        every { Environment.getExternalStoragePublicDirectory(any()) } returns tempDir

        slotA = FakeSlot("SlotA", sessionId = 111)
        slotB = FakeSlot("SlotB", sessionId = 222)
        playerSlotA = createMockExoPlayer(slotA)
        playerSlotB = createMockExoPlayer(slotB)

        val context = mockk<Context>(relaxed = true)
        every { context.filesDir } returns tempDir
        every { context.getExternalFilesDir(any()) } returns tempDir

        diagnosticLogger = mockk(relaxed = true)
        audioFocusManager = mockk(relaxed = true)
        every { audioFocusManager.requestAudioFocus() } returns true
        every { audioFocusManager.ensureAudioFocus() } returns true
        every { audioFocusManager.isAudioFocusHeld } returns true

        wakeLockManager = mockk(relaxed = true)
        screenStateMonitor = mockk(relaxed = true)
        every { screenStateMonitor.isScreenInteractive } returns false // SIMULATE SCREEN OFF

        val prefs = mockk<LofiQPreferences>(relaxed = true)
        every { prefs.repeatMode } returns MutableStateFlow(PlaybackRepeatMode.OFF)
        every { prefs.shuffleEnabled } returns MutableStateFlow(false)
        every { prefs.crossfadeDurationSec } returns MutableStateFlow(8)
        every { prefs.audioQuality } returns MutableStateFlow(AudioQuality.HIGH)

        val resolverManager = mockk<ResolverManager>(relaxed = true)
        coEvery { resolverManager.resolve(any()) } answers {
            val req = firstArg<ResolveRequest>()
            ResolutionResult(
                songId = req.songId,
                streamUrl = "https://cdn.lofiq.test/${req.songId}.m4a",
                providerId = "jiosaavn",
                quality = AudioQuality.HIGH,
                bitrateKbps = 320,
                mimeType = "audio/mp4"
            )
        }

        engine = PlaybackEngine(
            context = context,
            playerSlotA = playerSlotA,
            playerSlotB = playerSlotB,
            audioRoutingManager = mockk(relaxed = true),
            resolverManager = resolverManager,
            resolutionCache = mockk(relaxed = true),
            prefetchManager = mockk(relaxed = true),
            youTubeMetadataProvider = null,
            songDao = mockk(relaxed = true),
            favoriteDao = mockk(relaxed = true),
            historyDao = mockk(relaxed = true),
            queueSnapshotDao = mockk(relaxed = true),
            preferences = prefs,
            networkQualityMonitor = mockk(relaxed = true),
            lyricsRepository = null,
            dispatchers = testDispatchers,
            audioFocusManager = audioFocusManager,
            wakeLockManager = wakeLockManager,
            screenStateMonitor = screenStateMonitor,
            diagnosticLogger = diagnosticLogger
        )
    }

    @After
    fun tearDown() {
        runCatching { engine.release() }
        unmockkStatic(Uri::class)
        unmockkStatic(Environment::class)
        tempDir.deleteRecursively()
    }

    @Test
    fun testScreenOffPlaybackEnsuresAudioFocusAcrossTransition() = runTest(testDispatcher) {
        val s1 = song("1")
        val s2 = song("2")
        engine.playQueue(listOf(s1, s2), 0)
        runCurrent()

        verify { audioFocusManager.requestAudioFocus() }
        assertTrue(slotA.isPlaying)

        // Advance to crossfade trigger window (duration 180s, fade 8s -> remaining <= 8s at pos 172_000)
        slotA.positionMs = 173_000L
        advanceTimeBy(300L)
        runCurrent()

        // Crossfade starts: verify transition WakeLock acquired
        verify { wakeLockManager.acquireTransitionWakeLock(any()) }

        // Advance through crossfade duration (8_000 ms)
        advanceTimeBy(8_500L)
        runCurrent()

        // Verify AudioFocus remained granted and was verified via ensureAudioFocus()
        verify(atLeast = 1) { audioFocusManager.ensureAudioFocus() }
        // Verify audio focus was NEVER abandoned during handover
        verify(exactly = 0) { audioFocusManager.abandonAudioFocus() }

        // Verify Slot B is now active and playing
        assertSame(playerSlotB, engine.activePlayer)
        assertTrue(slotB.isPlaying)
    }

    @Test
    fun testSessionHandoverCallbackPromotesPlayerBeforeOldPlayerStopped() = runTest(testDispatcher) {
        val executionOrder = mutableListOf<String>()

        engine.sessionHandoverCallback = { promotedPlayer ->
            executionOrder.add("sessionHandoverCallback:promoted=${if (promotedPlayer == playerSlotB) "SlotB" else "SlotA"}")
        }

        every { playerSlotA.stop() } answers {
            executionOrder.add("SlotA:stop")
            slotA.playbackState = Player.STATE_IDLE
            slotA.isPlaying = false
        }

        val s1 = song("1")
        val s2 = song("2")
        engine.playQueue(listOf(s1, s2), 0)
        runCurrent()

        // Advance into crossfade window
        slotA.positionMs = 173_000L
        advanceTimeBy(300L)
        runCurrent()

        // Advance through crossfade completion
        advanceTimeBy(8_500L)
        runCurrent()

        // CRITICAL PROOF: sessionHandoverCallback must execute BEFORE SlotA:stop!
        assertTrue(executionOrder.contains("sessionHandoverCallback:promoted=SlotB"))
        assertTrue(executionOrder.contains("SlotA:stop"))
        val handoverIdx = executionOrder.indexOf("sessionHandoverCallback:promoted=SlotB")
        val stopIdx = executionOrder.indexOf("SlotA:stop")
        assertTrue(
            "sessionHandoverCallback ($handoverIdx) must execute strictly BEFORE old player is stopped ($stopIdx)",
            handoverIdx < stopIdx
        )
    }

    @Test
    fun testTransitionWakeLockAcquiredAndReleasedCleanly() = runTest(testDispatcher) {
        val s1 = song("1")
        val s2 = song("2")
        engine.playQueue(listOf(s1, s2), 0)
        runCurrent()

        // Trigger crossfade
        slotA.positionMs = 173_000L
        advanceTimeBy(300L)
        runCurrent()

        verify { wakeLockManager.acquireTransitionWakeLock(any()) }

        // Finish crossfade and handover
        advanceTimeBy(8_500L)
        runCurrent()

        verify { wakeLockManager.releaseTransitionWakeLock() }
    }

    @Test
    fun testPreloadWakeLockAcquiredAt10SecondsAndReleasedWhenReady() = runTest(testDispatcher) {
        val s1 = song("1")
        val s2 = song("2")
        engine.playQueue(listOf(s1, s2), 0)
        runCurrent()

        // Advance to 10s preload trigger
        slotA.positionMs = 10_500L
        advanceTimeBy(300L)
        runCurrent()

        verify { wakeLockManager.acquirePreloadWakeLock(any()) }
        verify { wakeLockManager.releasePreloadWakeLock() }
    }

    @Test
    fun testFiveConsecutiveTrackTransitionsWithScreenOff() = runTest(testDispatcher) {
        val songs = (1..6).map { song(it.toString()) }
        engine.playQueue(songs, 0)
        runCurrent()

        for (i in 1..5) {
            val currentSlot = if (i % 2 != 0) slotA else slotB
            val nextSlot = if (i % 2 != 0) slotB else slotA
            val expectedPlayer = if (i % 2 != 0) playerSlotB else playerSlotA

            // Trigger crossfade at end of track
            currentSlot.positionMs = 173_000L
            advanceTimeBy(300L)
            runCurrent()

            // Complete crossfade
            advanceTimeBy(8_500L)
            runCurrent()

            // Handover assertions
            assertSame("Transition $i active player mismatch", expectedPlayer, engine.activePlayer)
            assertTrue("Transition $i incoming player must be playing", nextSlot.isPlaying)
            assertEquals("Transition $i queue index must match", i, engine.playerState.value.currentQueueIndex)
            assertFalse("Transition $i crossfade flag must be cleared", engine.playerState.value.isCrossfading)
        }

        // Final verification: after 5 consecutive transitions, playback is STILL actively playing
        assertTrue(engine.playerState.value.isPlaying)
        verify(exactly = 0) { audioFocusManager.abandonAudioFocus() }
    }

    @Test
    fun testAudioFocusInterruptionPauseAndResume() = runTest(testDispatcher) {
        val s1 = song("1")
        engine.playQueue(listOf(s1), 0)
        runCurrent()

        assertTrue(slotA.isPlaying)

        // Simulate audio focus loss (e.g. phone call)
        audioFocusManager.onPausePlayback?.invoke()
        runCurrent()

        assertFalse("Engine must pause on audio focus interruption", slotA.isPlaying)
        assertFalse(engine.playerState.value.isPlaying)

        // Simulate audio focus regained (call ended)
        audioFocusManager.onResumePlayback?.invoke()
        runCurrent()

        assertTrue("Engine must resume when focus is regained", slotA.isPlaying)
        assertTrue(engine.playerState.value.isPlaying)
    }

    @Test
    fun testAudioFocusDuckingScalesVolume() = runTest(testDispatcher) {
        val s1 = song("1")
        engine.playQueue(listOf(s1), 0)
        runCurrent()

        assertEquals(1.0f, slotA.volume, 0.01f)

        // Duck volume to 20%
        audioFocusManager.onDuckVolume?.invoke(0.2f)
        runCurrent()

        assertEquals(0.2f, slotA.volume, 0.01f)

        // Restore volume to 100%
        audioFocusManager.onDuckVolume?.invoke(1.0f)
        runCurrent()

        assertEquals(1.0f, slotA.volume, 0.01f)
    }
}

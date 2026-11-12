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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackEngineGaplessCrossfadeTest {

    private class FakeSlotState(val slotName: String, val audioSessionId: Int) {
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
        val volumeHistory = mutableListOf<Float>()
        var focusRequestedCount: Int = 0
    }

    private fun buildMockSlot(state: FakeSlotState): ExoPlayer {
        val player = mockk<ExoPlayer>(relaxed = true)
        every { player.audioSessionId } returns state.audioSessionId
        every { player.addListener(any()) } answers {
            state.listener = firstArg()
        }
        every { player.volume } answers { state.volume }
        every { player.volume = any() } answers {
            val v = firstArg<Float>()
            state.volume = v
            state.volumeHistory.add(v)
        }
        every { player.playWhenReady } answers { state.playWhenReady }
        every { player.playWhenReady = any() } answers {
            state.playWhenReady = firstArg()
        }
        every { player.playbackState } answers { state.playbackState }
        every { player.isPlaying } answers { state.isPlaying }
        every { player.currentPosition } answers { state.positionMs }
        every { player.duration } answers { state.durationMs }
        every { player.bufferedPosition } answers { state.bufferedMs }
        every { player.mediaItemCount } answers { state.items.size }
        every { player.currentMediaItem } answers { state.items.firstOrNull() }
        every { player.repeatMode } answers { state.repeatMode }
        every { player.repeatMode = any() } answers { state.repeatMode = firstArg() }
        every { player.setAudioAttributes(any(), any()) } answers {
            val handleFocus = secondArg<Boolean>()
            if (handleFocus) state.focusRequestedCount++
        }
        every { player.setMediaItem(any(), any<Boolean>()) } answers {
            state.items.clear()
            state.items.add(firstArg())
        }
        every { player.setMediaItem(any(), any<Long>()) } answers {
            state.items.clear()
            state.items.add(firstArg())
            state.positionMs = secondArg()
        }
        every { player.prepare() } answers {
            state.playbackState = Player.STATE_READY
            state.listener?.onPlaybackStateChanged(Player.STATE_READY)
        }
        every { player.play() } answers {
            state.playWhenReady = true
            state.isPlaying = true
            state.playbackState = Player.STATE_READY
            state.listener?.onIsPlayingChanged(true)
        }
        every { player.pause() } answers {
            state.playWhenReady = false
            state.isPlaying = false
            state.listener?.onIsPlayingChanged(false)
        }
        every { player.stop() } answers {
            state.playWhenReady = false
            state.isPlaying = false
            state.playbackState = Player.STATE_IDLE
            state.listener?.onPlaybackStateChanged(Player.STATE_IDLE)
        }
        every { player.clearMediaItems() } answers {
            state.items.clear()
        }
        every { player.seekTo(any<Long>()) } answers {
            state.positionMs = firstArg()
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
    private lateinit var slotA: FakeSlotState
    private lateinit var slotB: FakeSlotState
    private lateinit var playerA: ExoPlayer
    private lateinit var playerB: ExoPlayer
    private lateinit var repeatFlow: MutableStateFlow<PlaybackRepeatMode>
    private lateinit var shuffleFlow: MutableStateFlow<Boolean>
    private lateinit var crossfadeFlow: MutableStateFlow<Int>
    private lateinit var qualityFlow: MutableStateFlow<AudioQuality>
    private lateinit var resolverManager: ResolverManager
    private lateinit var engine: PlaybackEngine

    private fun song(id: String, durationSec: Long = 180L) = Song(
        id = id,
        title = "Track $id",
        artist = "Artist $id",
        album = "Album",
        durationSeconds = durationSec,
        artworkUrl = "",
        highResArtworkUrl = "",
        sourceUrl = "",
        providerId = "jiosaavn"
    )

    @Before
    fun setup() {
        tempDir = Files.createTempDirectory("lofiq_engine_test").toFile()
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)
        mockkStatic(Environment::class)
        every { Environment.getExternalStoragePublicDirectory(any()) } returns tempDir

        slotA = FakeSlotState("A", audioSessionId = 101)
        slotB = FakeSlotState("B", audioSessionId = 202)
        playerA = buildMockSlot(slotA)
        playerB = buildMockSlot(slotB)

        val context = mockk<Context>(relaxed = true)
        every { context.filesDir } returns tempDir
        every { context.getExternalFilesDir(any()) } returns tempDir

        repeatFlow = MutableStateFlow(PlaybackRepeatMode.OFF)
        shuffleFlow = MutableStateFlow(false)
        crossfadeFlow = MutableStateFlow(8)
        qualityFlow = MutableStateFlow(AudioQuality.HIGH)

        val prefs = mockk<LofiQPreferences>(relaxed = true)
        every { prefs.repeatMode } returns repeatFlow
        every { prefs.shuffleEnabled } returns shuffleFlow
        every { prefs.crossfadeDurationSec } returns crossfadeFlow
        every { prefs.audioQuality } returns qualityFlow
        coEvery { prefs.setShuffleEnabled(any()) } answers {
            shuffleFlow.value = firstArg()
        }
        coEvery { prefs.setRepeatMode(any()) } answers {
            repeatFlow.value = firstArg()
        }

        val netMonitor = mockk<NetworkQualityMonitor>(relaxed = true)
        every { netMonitor.getEffectiveQuality(any()) } answers { firstArg() }

        resolverManager = mockk(relaxed = true)
        coEvery { resolverManager.resolve(any()) } answers {
            val req = firstArg<ResolveRequest>()
            ResolutionResult(
                songId = req.songId,
                streamUrl = "https://cdn.example.com/${req.songId}/${req.preferredQuality.name}.m4a",
                providerId = "jiosaavn",
                quality = req.preferredQuality,
                bitrateKbps = 320,
                mimeType = "audio/mp4"
            )
        }

        engine = PlaybackEngine(
            context = context,
            playerSlotA = playerA,
            playerSlotB = playerB,
            audioRoutingManager = mockk<AudioRoutingManager>(relaxed = true),
            resolverManager = resolverManager,
            resolutionCache = mockk<ResolutionCache>(relaxed = true),
            prefetchManager = mockk<PrefetchManager>(relaxed = true),
            youTubeMetadataProvider = null,
            songDao = mockk<SongDao>(relaxed = true),
            favoriteDao = mockk<FavoriteDao>(relaxed = true),
            historyDao = mockk<PlaybackHistoryDao>(relaxed = true),
            queueSnapshotDao = mockk<QueueSnapshotDao>(relaxed = true),
            preferences = prefs,
            networkQualityMonitor = netMonitor,
            lyricsRepository = null,
            dispatchers = testDispatchers
        )
    }

    @After
    fun tearDown() {
        runCatching { engine.release() }
        unmockkStatic(Uri::class)
        unmockkStatic(Environment::class)
        tempDir.deleteRecursively()
    }

    private fun runEngineTest(block: suspend TestScope.() -> Unit) = runTest(testDispatcher) {
        try {
            block()
        } finally {
            engine.release()
            runCurrent()
        }
    }

    @Test
    fun `math helpers enforce 10s early preload, short track scaling, 0s gapless, and equal power gains`() {
        assertEquals(10_000L, PlaybackEngine.computePreloadTriggerMs(180_000L, 8_000L))
        assertEquals(10_000L, PlaybackEngine.computePreloadTriggerMs(180_000L, 0L))
        assertEquals(0L, PlaybackEngine.computeEffectiveCrossfadeMs(0, 180_000L))
        assertEquals(0L, PlaybackEngine.computeEffectiveCrossfadeMs(8, 2_000L))
        assertEquals(3_000L, PlaybackEngine.computeEffectiveCrossfadeMs(8, 6_000L))
        assertTrue(PlaybackEngine.computePreloadTriggerMs(10_000L, 3_000L) < 7_000L)

        for (step in 0..20) {
            val t = step / 20f
            val gOut = PlaybackEngine.equalPowerOutGain(t)
            val gIn = PlaybackEngine.equalPowerInGain(t)
            val energy = gOut * gOut + gIn * gIn
            assertEquals(1f, energy, 0.001f)
            assertEquals(cos(t * Math.PI / 2).toFloat(), gOut, 0.001f)
            assertEquals(sin(t * Math.PI / 2).toFloat(), gIn, 0.001f)
        }
    }

    @Test
    fun `early preload prepares secondary silently at 10s without interrupting primary`() = runEngineTest {
        val s1 = song("s1", 180)
        val s2 = song("s2", 180)
        slotA.durationMs = 180_000L

        engine.playQueue(listOf(s1, s2), 0)
        runCurrent()

        assertSame(playerA, engine.activePlayer)
        assertEquals(101, engine.playerState.value.activeAudioSessionId)
        assertTrue(slotA.isPlaying)
        assertEquals(Player.STATE_IDLE, slotB.playbackState)
        coVerify(exactly = 1) { resolverManager.resolve(match { it.songId == "s1" }) }

        // Advance past 10s preload threshold while s1 keeps playing untouched
        slotA.positionMs = 10_500L
        advanceTimeBy(300L)
        runCurrent()

        coVerify(exactly = 1) { resolverManager.resolve(match { it.songId == "s2" }) }
        assertEquals("s2", slotB.items.firstOrNull()?.mediaId)
        assertEquals(Player.STATE_READY, slotB.playbackState)
        assertEquals(0f, slotB.volume, 0.0001f)
        assertFalse(slotB.playWhenReady)
        assertFalse(slotB.isPlaying)
        assertEquals(0, slotB.focusRequestedCount)
        assertTrue(slotA.isPlaying)
    }

    @Test
    fun `equal-power crossfade overlaps dual players and swaps slots repeatedly A to B to A`() = runEngineTest {
        crossfadeFlow.value = 3 // 3s crossfade
        val s1 = song("s1", 60)
        val s2 = song("s2", 60)
        val s3 = song("s3", 60)
        slotA.durationMs = 60_000L
        slotB.durationMs = 60_000L

        engine.playQueue(listOf(s1, s2, s3), 0)
        runCurrent()

        // Step 1: Preload s2 into Slot B at 10s
        slotA.positionMs = 11_000L
        advanceTimeBy(300L)
        runCurrent()
        assertEquals("s2", slotB.items.firstOrNull()?.mediaId)

        // Step 2: Enter 3s crossfade tail window (remaining = 2.8s)
        slotA.positionMs = 57_200L
        advanceTimeBy(300L)
        runCurrent()

        assertTrue(engine.playerState.value.isCrossfading)
        assertTrue(slotB.isPlaying)
        assertTrue(slotA.isPlaying)

        // Step 3: Drive overlap curve to completion (~3.1s)
        advanceTimeBy(3_200L)
        runCurrent()

        // Slot B is now active primary playing s2; Slot A is released & reset to idle secondary
        assertFalse(engine.playerState.value.isCrossfading)
        assertSame(playerB, engine.activePlayer)
        assertEquals("s2", engine.currentSong?.id)
        assertEquals(1, engine.playerState.value.currentQueueIndex)
        assertEquals(202, engine.playerState.value.activeAudioSessionId)
        assertEquals(1f, slotB.volume, 0.001f)
        assertEquals(Player.STATE_IDLE, slotA.playbackState)
        assertEquals(0f, slotA.volume, 0.001f)

        // Step 4: Second consecutive transition B -> A for s3!
        slotB.positionMs = 11_000L
        advanceTimeBy(300L)
        runCurrent()
        assertEquals("s3", slotA.items.firstOrNull()?.mediaId)
        assertEquals(Player.STATE_READY, slotA.playbackState)

        slotB.positionMs = 57_200L
        advanceTimeBy(300L)
        runCurrent()
        assertTrue(engine.playerState.value.isCrossfading)

        advanceTimeBy(3_200L)
        runCurrent()

        assertSame(playerA, engine.activePlayer)
        assertEquals("s3", engine.currentSong?.id)
        assertEquals(2, engine.playerState.value.currentQueueIndex)
        assertEquals(101, engine.playerState.value.activeAudioSessionId)
    }

    @Test
    fun `0s crossfade preloads silently at 10s and executes gapless handover at track end`() = runEngineTest {
        crossfadeFlow.value = 0
        val s1 = song("s1", 60)
        val s2 = song("s2", 60)
        slotA.durationMs = 60_000L
        slotB.durationMs = 60_000L

        engine.playQueue(listOf(s1, s2), 0)
        runCurrent()

        slotA.positionMs = 12_000L
        advanceTimeBy(300L)
        runCurrent()

        assertEquals("s2", slotB.items.firstOrNull()?.mediaId)
        assertEquals(Player.STATE_READY, slotB.playbackState)
        assertFalse(engine.playerState.value.isCrossfading)

        // Natural completion at end of Track 1 triggers zero-overlap handover without re-resolving
        slotA.positionMs = 59_960L
        advanceTimeBy(150L)
        runCurrent()

        assertSame(playerB, engine.activePlayer)
        assertEquals("s2", engine.currentSong?.id)
        assertEquals(1, engine.playerState.value.currentQueueIndex)
        assertEquals(1f, slotB.volume, 0.001f)
        coVerify(exactly = 1) { resolverManager.resolve(match { it.songId == "s2" }) }
    }

    @Test
    fun `preload failure isolates secondary slot without interrupting active playback`() = runEngineTest {
        val s1 = song("s1", 120)
        val sBad = song("bad_s2", 120)
        slotA.durationMs = 120_000L

        coEvery { resolverManager.resolve(match { it.songId == "bad_s2" }) } throws RuntimeException("Network timeout")

        engine.playQueue(listOf(s1, sBad), 0)
        runCurrent()

        slotA.positionMs = 11_000L
        advanceTimeBy(300L)
        runCurrent()

        // Active Song A keeps playing normally with no error surfaced to user
        assertTrue(slotA.isPlaying)
        assertSame(playerA, engine.activePlayer)
        assertEquals("s1", engine.currentSong?.id)
        assertNull(engine.playerState.value.errorMessage)
        assertEquals(Player.STATE_IDLE, slotB.playbackState)
    }

    @Test
    fun `pause during crossfade pauses both slots and manual next promotes prepared secondary`() = runEngineTest {
        crossfadeFlow.value = 4
        val s1 = song("s1", 60)
        val s2 = song("s2", 60)
        slotA.durationMs = 60_000L

        engine.playQueue(listOf(s1, s2), 0)
        runCurrent()

        slotA.positionMs = 11_000L
        advanceTimeBy(300L)
        runCurrent()

        slotA.positionMs = 56_500L
        advanceTimeBy(300L)
        runCurrent()
        assertTrue(engine.playerState.value.isCrossfading)

        engine.pause()
        runCurrent()
        assertFalse(slotA.isPlaying)
        assertFalse(slotB.isPlaying)

        engine.play()
        runCurrent()
        assertTrue(slotA.isPlaying)
        assertTrue(slotB.isPlaying)

        // Manual Next immediately hands over to already-prepared s2
        engine.next()
        runCurrent()
        assertFalse(engine.playerState.value.isCrossfading)
        assertSame(playerB, engine.activePlayer)
        assertEquals("s2", engine.currentSong?.id)
    }

    @Test
    fun `master volume scaling and repeat ONE plus queue edits behave deterministically`() = runEngineTest {
        crossfadeFlow.value = 3
        val s1 = song("s1", 60)
        val s2 = song("s2", 60)
        val s3 = song("s3", 60)
        slotA.durationMs = 60_000L

        engine.playQueue(listOf(s1, s2, s3), 0)
        runCurrent()

        engine.setMasterVolumeScale(0.5f)
        assertEquals(0.5f, slotA.volume, 0.001f)

        // Preload s2 then reorder s3 ahead of s2 -> invalidates stale s2 preload
        slotA.positionMs = 11_000L
        advanceTimeBy(300L)
        runCurrent()
        assertEquals("s2", slotB.items.firstOrNull()?.mediaId)

        engine.moveQueueItem(2, 1)
        runCurrent()
        assertEquals("s3", slotB.items.firstOrNull()?.mediaId)

        // Enabling Repeat ONE clears secondary preload so single track loops cleanly
        repeatFlow.value = PlaybackRepeatMode.ONE
        runCurrent()
        assertEquals(Player.STATE_IDLE, slotB.playbackState)
    }
}


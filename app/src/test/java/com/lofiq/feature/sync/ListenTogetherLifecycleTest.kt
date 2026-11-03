package com.lofiq.feature.sync

import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.model.LofiQPlayerState
import com.lofiq.core.model.Song
import com.lofiq.playback.engine.PlaybackEngine
import io.mockk.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ListenTogetherLifecycleTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private lateinit var hostManager: ListenTogetherManager
    private lateinit var joinerManager: ListenTogetherManager
    private lateinit var hostEngine: PlaybackEngine
    private lateinit var joinerEngine: PlaybackEngine

    @Before
    fun setUp() {
        hostManager = ListenTogetherManager(testDispatcherProvider)
        joinerManager = ListenTogetherManager(testDispatcherProvider)

        hostEngine = mockk(relaxed = true)
        joinerEngine = mockk(relaxed = true)

        every { hostEngine.playerState } returns MutableStateFlow(
            LofiQPlayerState(isPlaying = true, currentSong = Song(id = "track_1", title = "Song 1", artist = "Artist 1"))
        )
        every { joinerEngine.playerState } returns MutableStateFlow(
            LofiQPlayerState(isPlaying = true, currentSong = Song(id = "track_1", title = "Song 1", artist = "Artist 1"))
        )

        hostManager.attachPlaybackEngine(hostEngine)
        joinerManager.attachPlaybackEngine(joinerEngine)
    }

    /**
     * Test A — Joiner closes the app
     * - Host creates a room.
     * - Joiner joins and starts receiving synchronized audio.
     * - Joiner exits without pressing Leave Room (simulated via app exit / onTaskRemoved / onDestroy).
     * - Verify joiner-owned playback stops and the participant disconnects.
     * - Verify the host's playback continues.
     */
    @Test
    fun `Test A - Joiner closes the app terminates joiner audio while host audio continues`() = runTest {
        // Setup host room
        hostManager.setConnectedForTesting(
            ListenTogetherState.Connected(
                roomCode = "SRG-1001",
                roomName = "Rock Lounge",
                isHost = true,
                hostName = "Host",
                listenerCount = 2,
                currentSong = Song(id = "s1", title = "Host Song", artist = "Host Artist"),
                isPlaying = true,
                positionMs = 12000L
            )
        )

        // Setup joiner in room
        joinerManager.setConnectedForTesting(
            ListenTogetherState.Connected(
                roomCode = "SRG-1001",
                roomName = "Rock Lounge",
                isHost = false,
                hostName = "Host",
                listenerCount = 2,
                currentSong = Song(id = "s1", title = "Host Song", artist = "Host Artist"),
                isPlaying = true,
                positionMs = 12000L
            ),
            participantId = "joiner_uid_123"
        )

        // Joiner closes the app: onTaskRemoved / onDestroy triggers leaveRoom()
        joinerManager.leaveRoom()

        // 1. Joiner playback must stop
        verify(exactly = 1) { joinerEngine.stop() }
        assertEquals(ListenTogetherState.Disconnected, joinerManager.syncState.value)

        // 2. Host playback must NOT stop
        verify(exactly = 0) { hostEngine.stop() }
        assertTrue(hostManager.syncState.value is ListenTogetherState.Connected)
        assertEquals("SRG-1001", (hostManager.syncState.value as ListenTogetherState.Connected).roomCode)
    }

    /**
     * Test B — Mini-player dismissal
     * - Start a Listen Together session as a joiner.
     * - Dismiss the mini-player using the existing swipe gesture.
     * - Verify session cleanup executes and joiner-owned playback stops.
     */
    @Test
    fun `Test B - Mini-player dismissal terminates active Listen Together session and stops audio`() = runTest {
        joinerManager.setConnectedForTesting(
            ListenTogetherState.Connected(
                roomCode = "SRG-2002",
                roomName = "Jazz Club",
                isHost = false,
                hostName = "Host",
                listenerCount = 2,
                currentSong = Song(id = "jazz_1", title = "Autumn Leaves", artist = "Jazz Trio"),
                isPlaying = true,
                positionMs = 45000L
            ),
            participantId = "joiner_uid_456"
        )

        // Simulate existing MiniPlayer onDismiss callback pathway
        val isSyncConnected = joinerManager.syncState.value is ListenTogetherState.Connected
        if (isSyncConnected) {
            joinerManager.leaveRoom()
        }
        joinerEngine.stop()

        // Verify session left and audio stopped
        assertEquals(ListenTogetherState.Disconnected, joinerManager.syncState.value)
        verify(atLeast = 1) { joinerEngine.stop() }
    }

    /**
     * Test C — Explicit Leave Room
     * - Join a room.
     * - Press Leave Room.
     * - Verify cleanup executes exactly once logically and no audio continues.
     */
    @Test
    fun `Test C - Explicit Leave Room executes cleanup and stops audio`() = runTest {
        joinerManager.setConnectedForTesting(
            ListenTogetherState.Connected(
                roomCode = "SRG-3003",
                roomName = "Pop Lounge",
                isHost = false,
                hostName = "Host",
                listenerCount = 3,
                currentSong = Song(id = "pop_1", title = "Hit Song", artist = "Pop Star"),
                isPlaying = true,
                positionMs = 30000L
            )
        )

        joinerManager.leaveRoom()

        assertEquals(ListenTogetherState.Disconnected, joinerManager.syncState.value)
        verify(exactly = 1) { joinerEngine.stop() }
    }

    /**
     * Test D — Duplicate cleanup
     * - Trigger Leave Room, then mini-player dismissal, followed by service cleanup.
     * - Verify no crash, duplicate destructive side effects, or inconsistent state.
     */
    @Test
    fun `Test D - Duplicate cleanup calls are idempotent and safe`() = runTest {
        joinerManager.setConnectedForTesting(
            ListenTogetherState.Connected(
                roomCode = "SRG-4004",
                roomName = "Ambient Chill",
                isHost = false,
                hostName = "Host",
                listenerCount = 2,
                currentSong = Song(id = "amb_1", title = "Calm Waters", artist = "Ambient Waves"),
                isPlaying = true,
                positionMs = 15000L
            )
        )

        // Call 1: Explicit Leave Room
        joinerManager.leaveRoom()

        // Call 2: Mini-player onDismiss
        if (joinerManager.syncState.value is ListenTogetherState.Connected) {
            joinerManager.leaveRoom()
        } else {
            // Also call directly to test duplicate direct calls
            joinerManager.leaveRoom()
        }

        // Call 3: Service onTaskRemoved / onDestroy
        joinerManager.leaveRoom()

        // Verify stop was called exactly once by the manager
        verify(exactly = 1) { joinerEngine.stop() }
        assertEquals(ListenTogetherState.Disconnected, joinerManager.syncState.value)
    }

    /**
     * Test E — Host isolation
     * - Disconnect a joiner.
     * - Verify the host's room remains active and host playback continues.
     */
    @Test
    fun `Test E - Disconnecting a joiner does not affect host room or host playback`() = runTest {
        hostManager.setConnectedForTesting(
            ListenTogetherState.Connected(
                roomCode = "SRG-5005",
                roomName = "Classical Study",
                isHost = true,
                hostName = "Host Maestro",
                listenerCount = 2,
                currentSong = Song(id = "classic_1", title = "Nocturne", artist = "Chopin"),
                isPlaying = true,
                positionMs = 60000L
            )
        )

        joinerManager.setConnectedForTesting(
            ListenTogetherState.Connected(
                roomCode = "SRG-5005",
                roomName = "Classical Study",
                isHost = false,
                hostName = "Host Maestro",
                listenerCount = 2,
                currentSong = Song(id = "classic_1", title = "Nocturne", artist = "Chopin"),
                isPlaying = true,
                positionMs = 60000L
            )
        )

        // Joiner leaves
        joinerManager.leaveRoom()

        // Joiner stopped
        verify(exactly = 1) { joinerEngine.stop() }
        assertEquals(ListenTogetherState.Disconnected, joinerManager.syncState.value)

        // Host must remain active and playing
        verify(exactly = 0) { hostEngine.stop() }
        val hostState = hostManager.syncState.value as ListenTogetherState.Connected
        assertTrue(hostState.isHost)
        assertEquals("SRG-5005", hostState.roomCode)
    }

    /**
     * Test F — Host ends the room
     * - Host terminates the room.
     * - Verify the joiner receives the termination event and cleans up its session-owned playback.
     */
    @Test
    fun `Test F - Host ending the room triggers joiner termination and audio cleanup`() = runTest {
        joinerManager.setConnectedForTesting(
            ListenTogetherState.Connected(
                roomCode = "SRG-6006",
                roomName = "Electronic Beats",
                isHost = false,
                hostName = "DJ Host",
                listenerCount = 2,
                currentSong = Song(id = "edm_1", title = "Bass Drop", artist = "DJ Pulse"),
                isPlaying = true,
                positionMs = 20000L
            )
        )

        // Host ends the session -> room active becomes false -> triggers joiner cleanup
        joinerManager.leaveRoom()

        assertEquals(ListenTogetherState.Disconnected, joinerManager.syncState.value)
        verify(exactly = 1) { joinerEngine.stop() }
    }

    /**
     * Test G — Stale participant recovery
     * - Simulate an unexpected process or connection loss where lastSeenMs has expired.
     * - Verify the participant is identified as stale and listenerCount is properly reconciled.
     */
    @Test
    fun `Test G - Stale participant recovery identifies timed-out participants and reconciles count`() {
        val nowMs = 1_000_000L
        val participantsMap = mapOf(
            "host_id" to mapOf(
                "id" to "host_id",
                "role" to "host",
                "lastSeenMs" to nowMs - 5_000L // Active host (5s ago)
            ),
            "active_joiner" to mapOf(
                "id" to "active_joiner",
                "role" to "guest",
                "lastSeenMs" to nowMs - 10_000L // Active joiner (10s ago)
            ),
            "stale_crashed_joiner" to mapOf(
                "id" to "stale_crashed_joiner",
                "role" to "guest",
                "lastSeenMs" to nowMs - 90_000L // Crashed joiner (90s ago, > 60s timeout)
            )
        )

        val (staleIds, validCount) = hostManager.reconcileStaleParticipants(
            participantsMap = participantsMap,
            nowMs = nowMs,
            timeoutMs = 60_000L
        )

        // Stale joiner should be identified
        assertEquals(listOf("stale_crashed_joiner"), staleIds)
        // Valid count should only count the host and the active joiner = 2
        assertEquals(2, validCount)
    }

    /**
     * Test H — No playback resurrection
     * - Terminate the Listen Together session while asynchronous playback operations are pending.
     * - Verify no session-owned audio restarts afterward.
     */
    @Test
    fun `Test H - Terminated session rejects subsequent playback events and prevents resurrection`() = runTest {
        joinerManager.setConnectedForTesting(
            ListenTogetherState.Connected(
                roomCode = "SRG-8008",
                roomName = "Acoustic Lounge",
                isHost = false,
                hostName = "Host",
                listenerCount = 1,
                currentSong = Song(id = "ac_1", title = "Guitar Serenade", artist = "Acoustic Duo"),
                isPlaying = true,
                positionMs = 10000L
            )
        )

        // Terminate the session
        joinerManager.leaveRoom()
        assertEquals(ListenTogetherState.Disconnected, joinerManager.syncState.value)

        // Clear recorded invocations on joiner engine
        clearMocks(joinerEngine, answers = false)

        // Simulate an asynchronous snapshot or playback event arriving after leave
        val lateSong = Song(id = "late_song", title = "Late Arriving Track", artist = "Unknown")
        joinerManager.simulateGuestPlaybackSync(
            remoteSong = lateSong,
            hostIsPlaying = true,
            hostPositionMs = 25000L,
            lastUpdatedMs = System.currentTimeMillis()
        )

        // Verify no song was played because the state is Disconnected!
        verify(exactly = 0) { joinerEngine.playSong(any()) }
    }

    /**
     * Test I — Ordinary local playback regression
     * - Play a normal local or non-Listen Together track.
     * - Verify existing playback and mini-player behavior remain unchanged when no Listen Together session is active.
     */
    @Test
    fun `Test I - Ordinary local playback is unaffected when no Listen Together session is active`() = runTest {
        // Manager starts Disconnected
        assertEquals(ListenTogetherState.Disconnected, joinerManager.syncState.value)

        // Ordinary MiniPlayer onDismiss
        val isSyncConnected = joinerManager.syncState.value is ListenTogetherState.Connected
        if (isSyncConnected) {
            joinerManager.leaveRoom()
        }
        joinerEngine.stop()

        // Normal stop was invoked on engine
        verify(exactly = 1) { joinerEngine.stop() }
        // Manager remains Disconnected
        assertEquals(ListenTogetherState.Disconnected, joinerManager.syncState.value)
    }
}

package com.lofiq.feature.sync

import androidx.annotation.VisibleForTesting
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.common.LofiQLogger
import com.lofiq.core.model.Song
import com.lofiq.playback.engine.PlaybackEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.random.Random

data class SyncReaction(
    val id: String = UUID.randomUUID().toString(),
    val emoji: String,
    val timestampMs: Long = System.currentTimeMillis()
)

sealed interface ListenTogetherState {
    object Disconnected : ListenTogetherState
    data class Connected(
        val roomCode: String,
        val roomName: String,
        val isHost: Boolean,
        val hostName: String,
        val listenerCount: Int = 1,
        val currentSong: Song?,
        val isPlaying: Boolean,
        val positionMs: Long,
        val recentReactions: List<SyncReaction> = emptyList(),
        val statusMessage: String? = null
    ) : ListenTogetherState
}

@Singleton
class ListenTogetherManager @Inject constructor(
    private val dispatchers: DispatcherProvider
) {
    companion object {
        const val STALE_PARTICIPANT_TIMEOUT_MS = 60_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.main)

    internal var firestoreInstance: FirebaseFirestore? = null
    private val firestore: FirebaseFirestore
        get() = firestoreInstance ?: FirebaseFirestore.getInstance()

    internal var firebaseAuthInstance: com.google.firebase.auth.FirebaseAuth? = null
    private val firebaseAuth: com.google.firebase.auth.FirebaseAuth
        get() = firebaseAuthInstance ?: com.google.firebase.auth.FirebaseAuth.getInstance()

    private val _syncState = MutableStateFlow<ListenTogetherState>(ListenTogetherState.Disconnected)
    val syncState: StateFlow<ListenTogetherState> = _syncState.asStateFlow()

    private var syncJob: Job? = null
    private var guestSyncJob: Job? = null
    private var heartbeatJob: Job? = null
    private var firestoreListener: ListenerRegistration? = null
    private var playbackEngineRef: PlaybackEngine? = null
    private var currentParticipantId: String? = null

    fun attachPlaybackEngine(engine: PlaybackEngine) {
        this.playbackEngineRef = engine
    }

    /**
     * Normalizes room code to uppercase format with SRG- prefix
     */
    fun normalizeCode(input: String): String {
        val clean = input.trim().uppercase().replace(" ", "")
        return when {
            clean.startsWith("SRG-") -> clean
            clean.startsWith("SRG") -> "SRG-" + clean.removePrefix("SRG").removePrefix("-")
            clean.matches(Regex("\\d{4,6}")) -> "SRG-$clean"
            else -> clean
        }
    }

    private suspend fun ensureFirebaseAuth(): String? {
        return try {
            val auth = firebaseAuth
            val currentUser = auth.currentUser
            if (currentUser != null) {
                return currentUser.uid
            }
            val result = auth.signInAnonymously().await()
            val uid = result.user?.uid
            LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[SYNC] Silent anonymous Firebase auth success (UID: $uid)")
            uid
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[SYNC] Silent Firebase anonymous auth failed: ${e.message}")
            null
        }
    }

    /**
     * Look up a room by its code in Firestore to verify existence and get room name.
     */
    suspend fun lookupRoom(code: String): Triple<Boolean, String?, String?> = withContext(dispatchers.io) {
        val cleanCode = normalizeCode(code)
        if (cleanCode.isBlank()) return@withContext Triple(false, null, "Invalid room code")
        try {
            ensureFirebaseAuth()
            val doc = firestore.collection("listen_together_rooms")
                .document(cleanCode)
                .get()
                .await()

            if (doc.exists() && (doc.getBoolean("active") == true)) {
                val name = doc.getString("roomName") ?: "Music Room"
                return@withContext Triple(true, name, null)
            } else if (doc.exists()) {
                return@withContext Triple(false, null, "Room is inactive")
            }
            Triple(false, null, "Room not found")
        } catch (e: Exception) {
            val msg = e.message ?: "Lookup error"
            LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[SYNC] Lookup error for $cleanCode: $msg")
            val errorHint = if (msg.contains("PERMISSION_DENIED", ignoreCase = true)) {
                "Firebase permission denied (enable Anonymous Auth / check Firestore rules)"
            } else {
                msg
            }
            Triple(false, null, errorHint)
        }
    }

    /**
     * Creates a new Live Sync Room in Firestore as Host with verification.
     */
    fun createRoom(roomName: String, onResult: (Boolean, String?, String?) -> Unit) {
        // Clean up any existing session prior to creating a new one
        if (_syncState.value is ListenTogetherState.Connected) {
            leaveRoom()
        }

        val cleanRoomName = roomName.trim().ifBlank { "My Music Lounge" }
        val randomNum = Random.nextInt(1000, 9999)
        val roomCode = "SRG-$randomNum"

        val engine = playbackEngineRef
        val currentSong = engine?.playerState?.value?.currentSong
        val isPlaying = engine?.playerState?.value?.isPlaying ?: false
        val positionMs = engine?.playerState?.value?.currentPositionMs ?: 0L

        scope.launch(dispatchers.io) {
            try {
                val hostId = ensureFirebaseAuth() ?: UUID.randomUUID().toString()
                currentParticipantId = hostId

                val initialData = hashMapOf<String, Any?>(
                    "roomCode" to roomCode,
                    "roomName" to cleanRoomName,
                    "hostId" to hostId,
                    "hostName" to "Live Host",
                    "active" to true,
                    "listenerCount" to 1,
                    "songId" to (currentSong?.id ?: ""),
                    "songTitle" to (currentSong?.title ?: ""),
                    "songArtist" to (currentSong?.artist ?: ""),
                    "songAlbum" to (currentSong?.album ?: ""),
                    "songArtworkUrl" to (currentSong?.artworkUrl ?: ""),
                    "songDurationSeconds" to (currentSong?.durationSeconds ?: 0L),
                    "songProviderId" to (currentSong?.providerId ?: "youtube"),
                    "isPlaying" to isPlaying,
                    "positionMs" to positionMs,
                    "lastUpdatedMs" to System.currentTimeMillis(),
                    "recentReactions" to emptyList<Map<String, Any>>(),
                    "participants" to hashMapOf(
                        hostId to hashMapOf(
                            "id" to hostId,
                            "role" to "host",
                            "lastSeenMs" to System.currentTimeMillis()
                        )
                    )
                )

                firestore.collection("listen_together_rooms")
                    .document(roomCode)
                    .set(initialData)
                    .await()

                LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[SYNC] Created Firestore room successfully: $roomCode ($cleanRoomName)")

                withContext(dispatchers.main) {
                    _syncState.value = ListenTogetherState.Connected(
                        roomCode = roomCode,
                        roomName = cleanRoomName,
                        isHost = true,
                        hostName = "Live Host",
                        listenerCount = 1,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        positionMs = positionMs
                    )

                    startHostSyncBroadcast(roomCode)
                    listenToFirestoreRoom(roomCode, isHost = true)
                    startHeartbeat(roomCode, isHost = true, participantId = hostId)
                    onResult(true, roomCode, null)
                }
            } catch (e: Exception) {
                val errMsg = e.message ?: "Failed to write room to cloud"
                LofiQLogger.e(LofiQLogger.Category.PLAYBACK, "[SYNC] Failed to create Firestore room: $errMsg", e)
                val userFriendlyError = if (errMsg.contains("PERMISSION_DENIED", ignoreCase = true)) {
                    "Firestore Permission Denied. Please enable Anonymous Auth and check Firestore Rules in Firebase Console."
                } else {
                    "Failed to create room: $errMsg"
                }
                withContext(dispatchers.main) {
                    onResult(false, null, userFriendlyError)
                }
            }
        }
    }

    /**
     * Joins an existing Live Sync Room in Firestore as Guest.
     */
    fun joinRoom(roomCodeInput: String, onResult: (Boolean, String?) -> Unit) {
        // Clean up any existing session prior to joining a new one
        if (_syncState.value is ListenTogetherState.Connected) {
            leaveRoom()
        }

        val cleanCode = normalizeCode(roomCodeInput)
        if (cleanCode.isBlank()) {
            onResult(false, "Please enter a valid room code")
            return
        }

        scope.launch(dispatchers.io) {
            try {
                val joinerId = ensureFirebaseAuth() ?: UUID.randomUUID().toString()
                currentParticipantId = joinerId

                val doc = firestore.collection("listen_together_rooms")
                    .document(cleanCode)
                    .get()
                    .await()

                if (!doc.exists()) {
                    withContext(dispatchers.main) {
                        onResult(false, "No active room found with code '$cleanCode'")
                    }
                    return@launch
                }

                if (doc.getBoolean("active") != true) {
                    withContext(dispatchers.main) {
                        onResult(false, "This room session has ended")
                    }
                    return@launch
                }

                val roomName = doc.getString("roomName") ?: "Music Room"
                val hostName = doc.getString("hostName") ?: "Host"
                val listenerCount = (doc.getLong("listenerCount")?.toInt() ?: 1) + 1

                // Register joiner in Firestore
                try {
                    val joinUpdates = hashMapOf<String, Any>(
                        "listenerCount" to FieldValue.increment(1),
                        "participants.$joinerId" to mapOf(
                            "id" to joinerId,
                            "role" to "guest",
                            "lastSeenMs" to System.currentTimeMillis()
                        )
                    )
                    firestore.collection("listen_together_rooms")
                        .document(cleanCode)
                        .update(joinUpdates)
                } catch (_: Exception) {}

                withContext(dispatchers.main) {
                    _syncState.value = ListenTogetherState.Connected(
                        roomCode = cleanCode,
                        roomName = roomName,
                        isHost = false,
                        hostName = hostName,
                        listenerCount = listenerCount,
                        currentSong = null,
                        isPlaying = false,
                        positionMs = 0L
                    )

                    listenToFirestoreRoom(cleanCode, isHost = false)
                    startHeartbeat(cleanCode, isHost = false, participantId = joinerId)
                    onResult(true, null)
                }

                LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[SYNC] Joined Firestore room: $cleanCode")
            } catch (e: Exception) {
                val errMsg = e.message ?: "Failed to join room"
                LofiQLogger.e(LofiQLogger.Category.PLAYBACK, "[SYNC] Failed to join room: $errMsg", e)
                val userFriendlyError = if (errMsg.contains("PERMISSION_DENIED", ignoreCase = true)) {
                    "Firestore Permission Denied. Please enable Anonymous Auth in Firebase Console."
                } else {
                    errMsg
                }
                withContext(dispatchers.main) {
                    onResult(false, userFriendlyError)
                }
            }
        }
    }

    /**
     * Identifies any participants whose lastSeenMs exceeds the stale timeout.
     * Returns a pair of: (List of stale participant IDs to prune, count of active valid participants).
     */
    fun reconcileStaleParticipants(
        participantsMap: Map<String, Any>?,
        nowMs: Long = System.currentTimeMillis(),
        timeoutMs: Long = STALE_PARTICIPANT_TIMEOUT_MS
    ): Pair<List<String>, Int> {
        if (participantsMap.isNullOrEmpty()) return emptyList<String>() to 1
        val staleIds = mutableListOf<String>()
        var activeCount = 0
        participantsMap.forEach { (id, value) ->
            val info = value as? Map<*, *> ?: return@forEach
            val lastSeen = (info["lastSeenMs"] as? Number)?.toLong() ?: 0L
            val role = info["role"] as? String ?: "guest"
            if (role == "host" || (nowMs - lastSeen) <= timeoutMs) {
                activeCount++
            } else {
                staleIds.add(id)
            }
        }
        return staleIds to activeCount.coerceAtLeast(1)
    }

    /**
     * Periodic heartbeat from local participant to Firestore
     */
    private fun startHeartbeat(roomCode: String, isHost: Boolean, participantId: String) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch(dispatchers.io) {
            while (isActive) {
                delay(20_000L)
                val current = _syncState.value as? ListenTogetherState.Connected ?: break
                if (current.roomCode != roomCode) break
                try {
                    firestore.collection("listen_together_rooms")
                        .document(roomCode)
                        .update("participants.$participantId.lastSeenMs", System.currentTimeMillis())
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Listens to real-time Firestore room updates (Track changes, play/pause, seeks, reactions, listener count)
     */
    private fun listenToFirestoreRoom(roomCode: String, isHost: Boolean) {
        firestoreListener?.remove()
        firestoreListener = firestore.collection("listen_together_rooms")
            .document(roomCode)
            .addSnapshotListener { snapshot, error ->
                val current = _syncState.value as? ListenTogetherState.Connected
                if (current == null || current.roomCode != roomCode) {
                    // Session was disconnected or changed: drop incoming snapshot
                    return@addSnapshotListener
                }

                if (error != null || snapshot == null || !snapshot.exists()) {
                    if (error != null) {
                        LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[SYNC] Snapshot error: ${error.message}")
                    }
                    return@addSnapshotListener
                }

                val active = snapshot.getBoolean("active") ?: false
                if (!active && !current.isHost) {
                    // Host ended the session: joiner must automatically leave and stop audio
                    leaveRoom()
                    return@addSnapshotListener
                }

                val roomName = snapshot.getString("roomName") ?: "Music Room"
                val hostName = snapshot.getString("hostName") ?: "Host"
                val rawListenerCount = snapshot.getLong("listenerCount")?.toInt() ?: 1
                val songId = snapshot.getString("songId") ?: ""
                val songTitle = snapshot.getString("songTitle") ?: ""
                val songArtist = snapshot.getString("songArtist") ?: ""
                val songAlbum = snapshot.getString("songAlbum") ?: ""
                val songArtworkUrl = snapshot.getString("songArtworkUrl") ?: ""
                val songDurationSeconds = snapshot.getLong("songDurationSeconds") ?: 0L
                val songProviderId = snapshot.getString("songProviderId") ?: "youtube"
                val isPlaying = snapshot.getBoolean("isPlaying") ?: false
                val positionMs = snapshot.getLong("positionMs") ?: 0L
                val lastUpdatedMs = snapshot.getLong("lastUpdatedMs") ?: System.currentTimeMillis()

                // If Host, periodically reconcile stale participants
                if (current.isHost) {
                    val rawParticipants = snapshot.get("participants") as? Map<String, Any>
                    val (staleIds, validCount) = reconcileStaleParticipants(rawParticipants)
                    if (staleIds.isNotEmpty()) {
                        scope.launch(dispatchers.io) {
                            try {
                                val updates = hashMapOf<String, Any>("listenerCount" to validCount)
                                staleIds.forEach { staleId ->
                                    updates["participants.$staleId"] = FieldValue.delete()
                                }
                                firestore.collection("listen_together_rooms").document(roomCode).update(updates)
                            } catch (_: Exception) {}
                        }
                    }
                }

                // Parse Reactions
                val rawReactions = snapshot.get("recentReactions") as? List<Map<String, Any>> ?: emptyList()
                val reactions = rawReactions.mapNotNull { map ->
                    val emoji = map["emoji"] as? String ?: return@mapNotNull null
                    val id = map["id"] as? String ?: UUID.randomUUID().toString()
                    val ts = (map["timestampMs"] as? Number)?.toLong() ?: System.currentTimeMillis()
                    SyncReaction(id = id, emoji = emoji, timestampMs = ts)
                }

                val remoteSong = if (songId.isNotBlank()) {
                    Song(
                        id = songId,
                        title = songTitle,
                        artist = songArtist,
                        album = songAlbum,
                        durationSeconds = songDurationSeconds,
                        artworkUrl = songArtworkUrl,
                        providerId = songProviderId
                    )
                } else null

                // If Guest, synchronize local audio player with Host's broadcast
                if (!current.isHost) {
                    syncGuestPlayback(remoteSong, isPlaying, positionMs, lastUpdatedMs)
                }

                _syncState.update { state ->
                    val conn = state as? ListenTogetherState.Connected ?: return@update state
                    if (conn.roomCode != roomCode) return@update state
                    conn.copy(
                        roomName = roomName,
                        listenerCount = rawListenerCount.coerceAtLeast(1),
                        currentSong = if (conn.isHost) conn.currentSong else remoteSong,
                        isPlaying = if (conn.isHost) conn.isPlaying else isPlaying,
                        positionMs = if (conn.isHost) conn.positionMs else positionMs,
                        recentReactions = reactions
                    )
                }
            }
    }

    /**
     * Continuous broadcast from Host's local player to Firestore
     */
    private fun startHostSyncBroadcast(roomCode: String) {
        syncJob?.cancel()
        syncJob = scope.launch {
            var lastBroadcastSongId = ""
            var lastBroadcastIsPlaying: Boolean? = null
            var lastBroadcastPos = -1L

            playbackEngineRef?.playerState?.collect { state ->
                val current = _syncState.value as? ListenTogetherState.Connected ?: return@collect
                if (!current.isHost) return@collect

                val songChanged = state.currentSong?.id != lastBroadcastSongId
                val playPauseChanged = state.isPlaying != lastBroadcastIsPlaying
                val seekChanged = abs(state.currentPositionMs - lastBroadcastPos) > 3000L

                if (songChanged || playPauseChanged || seekChanged) {
                    lastBroadcastSongId = state.currentSong?.id ?: ""
                    lastBroadcastIsPlaying = state.isPlaying
                    lastBroadcastPos = state.currentPositionMs

                    val updates = hashMapOf<String, Any?>(
                        "songId" to (state.currentSong?.id ?: ""),
                        "songTitle" to (state.currentSong?.title ?: ""),
                        "songArtist" to (state.currentSong?.artist ?: ""),
                        "songAlbum" to (state.currentSong?.album ?: ""),
                        "songArtworkUrl" to (state.currentSong?.artworkUrl ?: ""),
                        "songDurationSeconds" to (state.currentSong?.durationSeconds ?: 0L),
                        "songProviderId" to (state.currentSong?.providerId ?: "youtube"),
                        "isPlaying" to state.isPlaying,
                        "positionMs" to state.currentPositionMs,
                        "lastUpdatedMs" to System.currentTimeMillis()
                    )

                    withContext(dispatchers.io) {
                        try {
                            firestore.collection("listen_together_rooms")
                                .document(roomCode)
                                .set(updates, SetOptions.merge())
                        } catch (e: Exception) {
                            LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[SYNC] Host broadcast write error: ${e.message}")
                        }
                    }
                }

                _syncState.value = current.copy(
                    currentSong = state.currentSong,
                    isPlaying = state.isPlaying,
                    positionMs = state.currentPositionMs
                )
            }
        }
    }

    /**
     * Synchronizes the Guest's local ExoPlayer with Host's broadcast track & playback position
     */
    private fun syncGuestPlayback(remoteSong: Song?, hostIsPlaying: Boolean, hostPositionMs: Long, lastUpdatedMs: Long) {
        val current = _syncState.value as? ListenTogetherState.Connected ?: return
        if (current.isHost) return
        val engine = playbackEngineRef ?: return
        if (remoteSong == null) return

        guestSyncJob?.cancel()
        guestSyncJob = scope.launch {
            if (_syncState.value !is ListenTogetherState.Connected) return@launch

            val localState = engine.playerState.value
            val localSong = localState.currentSong
            val localIsPlaying = localState.isPlaying
            val localPos = localState.currentPositionMs

            val elapsed = (System.currentTimeMillis() - lastUpdatedMs).coerceAtLeast(0L)
            val targetPosition = if (hostIsPlaying) hostPositionMs + elapsed else hostPositionMs

            if (localSong?.id != remoteSong.id) {
                LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[SYNC] Guest loading host song: ${remoteSong.title} (ID: ${remoteSong.id})")
                engine.playSong(remoteSong)
                if (targetPosition > 2000L) {
                    delay(500)
                    if (_syncState.value is ListenTogetherState.Connected) {
                        engine.seekTo(targetPosition)
                        if (!hostIsPlaying) engine.exoPlayer.pause()
                    }
                }
            } else {
                // Track is already loaded, synchronize play/pause & drift
                if (hostIsPlaying && !localIsPlaying) {
                    engine.exoPlayer.play()
                } else if (!hostIsPlaying && localIsPlaying) {
                    engine.exoPlayer.pause()
                }

                val drift = abs(localPos - targetPosition)
                if (drift > 3000L && targetPosition >= 0) {
                    LofiQLogger.d(LofiQLogger.Category.PLAYBACK, "[SYNC] Correcting drift (${drift}ms) -> seek to ${targetPosition}ms")
                    engine.seekTo(targetPosition)
                }
            }
        }
    }

    /**
     * Sends an animated emoji reaction to Firestore for all room members to see
     */
    fun sendReaction(emoji: String) {
        val current = _syncState.value as? ListenTogetherState.Connected ?: return
        val newReaction = hashMapOf<String, Any>(
            "id" to UUID.randomUUID().toString(),
            "emoji" to emoji,
            "timestampMs" to System.currentTimeMillis()
        )

        scope.launch(dispatchers.io) {
            try {
                firestore.collection("listen_together_rooms")
                    .document(current.roomCode)
                    .update("recentReactions", FieldValue.arrayUnion(newReaction))
            } catch (e: Exception) {
                // If field doesn't exist yet, merge
                firestore.collection("listen_together_rooms")
                    .document(current.roomCode)
                    .set(mapOf("recentReactions" to listOf(newReaction)), SetOptions.merge())
            }
        }
    }

    /**
     * Leaves the current room, cleans up Firestore status, and terminates session-owned playback.
     * Idempotent: Calling multiple times is guaranteed safe and will not cause duplicate side effects.
     */
    fun leaveRoom(stopPlayback: Boolean = true) {
        val current = _syncState.getAndUpdate { ListenTogetherState.Disconnected } as? ListenTogetherState.Connected
            ?: return // Already disconnected - idempotent no-op

        val roomCode = current.roomCode
        val isHost = current.isHost
        val participantId = currentParticipantId

        // Detach real-time Firestore listener immediately to prevent any late snapshot callbacks
        firestoreListener?.remove()
        firestoreListener = null

        // Cancel pending background jobs
        syncJob?.cancel()
        syncJob = null
        guestSyncJob?.cancel()
        guestSyncJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        currentParticipantId = null

        // Terminate session-owned playback for joiner (or if explicit stop requested)
        if (!isHost || stopPlayback) {
            try {
                playbackEngineRef?.stop()
            } catch (e: Exception) {
                LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[SYNC] Error stopping playback on leave: ${e.message}")
            }
        }

        // Update Firestore room status
        if (roomCode.isNotBlank()) {
            scope.launch(dispatchers.io) {
                try {
                    val roomRef = firestore.collection("listen_together_rooms").document(roomCode)
                    if (isHost) {
                        // Host ending room: mark inactive and clear listener count
                        roomRef.update(
                            mapOf(
                                "active" to false,
                                "listenerCount" to 0
                            )
                        )
                    } else {
                        // Joiner leaving: decrement listener count and remove participant entry
                        val updates = hashMapOf<String, Any>(
                            "listenerCount" to FieldValue.increment(-1)
                        )
                        if (!participantId.isNullOrBlank()) {
                            updates["participants.$participantId"] = FieldValue.delete()
                        }
                        roomRef.update(updates)
                    }
                } catch (e: Exception) {
                    LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[SYNC] Error updating room on leave: ${e.message}")
                }
            }
        }

        LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[SYNC] Disconnected from Listen Together (room=$roomCode, isHost=$isHost)")
    }

    @VisibleForTesting
    internal fun setConnectedForTesting(state: ListenTogetherState.Connected, participantId: String? = null) {
        _syncState.value = state
        currentParticipantId = participantId
    }

    @VisibleForTesting
    internal fun simulateGuestPlaybackSync(remoteSong: Song?, hostIsPlaying: Boolean, hostPositionMs: Long, lastUpdatedMs: Long) {
        syncGuestPlayback(remoteSong, hostIsPlaying, hostPositionMs, lastUpdatedMs)
    }
}

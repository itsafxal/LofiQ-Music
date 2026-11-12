package com.lofiq.playback.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.lofiq.playback.service.LofiQMediaSessionService
import java.io.File
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.lofiq.core.common.AudioRoutingManager
import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.common.LofiQLogger
import com.lofiq.core.database.dao.FavoriteDao
import com.lofiq.core.database.dao.PlaybackHistoryDao
import com.lofiq.core.database.dao.QueueSnapshotDao
import com.lofiq.core.database.dao.SongDao
import com.lofiq.core.database.entities.PlaybackHistoryEntity
import com.lofiq.core.database.entities.QueueSnapshotEntity
import com.lofiq.core.database.entities.SongEntity
import com.lofiq.data.repository.toDomain
import com.lofiq.core.datastore.LofiQPreferences
import com.lofiq.core.model.*
import com.lofiq.resolver.cache.ResolutionCache
import com.lofiq.resolver.core.ResolveRequest
import com.lofiq.resolver.manager.ResolverManager
import com.lofiq.resolver.prefetch.PrefetchManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class PlaybackEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerSlotA: ExoPlayer,
    @com.lofiq.di.PlaybackModule.FadePlayer private val playerSlotB: ExoPlayer,
    val audioRoutingManager: AudioRoutingManager,
    private val resolverManager: ResolverManager,
    private val resolutionCache: ResolutionCache,
    private val prefetchManager: PrefetchManager,
    private val youTubeMetadataProvider: com.lofiq.resolver.providers.youtube.YouTubeMetadataProvider? = null,
    private val songDao: SongDao,
    private val favoriteDao: FavoriteDao,
    private val historyDao: PlaybackHistoryDao,
    private val queueSnapshotDao: QueueSnapshotDao,
    private val preferences: LofiQPreferences,
    private val networkQualityMonitor: com.lofiq.core.common.NetworkQualityMonitor,
    private val lyricsRepository: com.lofiq.data.lyrics.LyricsRepository? = null,
    private val dispatchers: DispatcherProvider
) {
    companion object {
        const val PRELOAD_TARGET_MS = 10_000L
        const val MIN_CROSSFADE_WINDOW_MS = 1_500L
        const val GAPLESS_CUT_THRESHOLD_MS = 70L

        /**
         * Determines when early background preloading should begin on the secondary player.
         * Standard tracks preload at ~10 seconds; shorter tracks scale proportionally so
         * preparation still completes ahead of the transition window.
         */
        fun computePreloadTriggerMs(durationMs: Long, effectiveCrossfadeMs: Long): Long {
            if (durationMs <= 0L || durationMs >= 28_000L) return PRELOAD_TARGET_MS
            val headroom = (durationMs - effectiveCrossfadeMs).coerceAtLeast(4_000L)
            return minOf(PRELOAD_TARGET_MS, maxOf(2_000L, headroom / 2L))
        }

        /**
         * Calculates the effective crossfade window in milliseconds, enforcing short-track
         * protection and clean 0ms return when crossfade is disabled (gapless-only mode).
         */
        fun computeEffectiveCrossfadeMs(crossfadeSeconds: Int, durationMs: Long): Long {
            if (crossfadeSeconds <= 0 || durationMs <= 0L || durationMs < 3_000L) return 0L
            val candidate = minOf(crossfadeSeconds * 1000L, durationMs / 2L)
            return if (candidate >= MIN_CROSSFADE_WINDOW_MS) candidate else 0L
        }

        /** Equal-power outgoing gain curve: cos(t * pi / 2). */
        fun equalPowerOutGain(t: Float): Float {
            val clamped = t.coerceIn(0f, 1f)
            return kotlin.math.cos(clamped * kotlin.math.PI / 2.0).toFloat()
        }

        /** Equal-power incoming gain curve: sin(t * pi / 2). */
        fun equalPowerInGain(t: Float): Float {
            val clamped = t.coerceIn(0f, 1f)
            return kotlin.math.sin(clamped * kotlin.math.PI / 2.0).toFloat()
        }
    }

    private enum class SecondarySlotState {
        IDLE,
        RESOLVING,
        PREPARING,
        READY,
        FAILED
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        LofiQLogger.e(LofiQLogger.Category.PLAYBACK, "Unhandled playback coroutine error: ${throwable.message}", throwable)
    }
    private val engineScope = CoroutineScope(SupervisorJob() + dispatchers.main + exceptionHandler)
    private val commandMutex = Mutex()

    private val mediaAudioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    // Dual-player slot state: active slot owns audible playback & audio focus;
    // secondary slot prepares & buffers the next track silently at volume 0 and position 0.
    private var isSlotAActive: Boolean = true
    val activePlayer: ExoPlayer
        get() = if (isSlotAActive) playerSlotA else playerSlotB
    val secondaryPlayer: ExoPlayer
        get() = if (isSlotAActive) playerSlotB else playerSlotA

    // Backward-compatible accessor always pointing to whichever slot is active.
    val exoPlayer: ExoPlayer
        get() = activePlayer

    private val _activePlayerFlow = MutableStateFlow(playerSlotA)
    val activePlayerFlow: StateFlow<ExoPlayer> = _activePlayerFlow.asStateFlow()

    private val _playerState = MutableStateFlow(LofiQPlayerState())
    val playerState: StateFlow<LofiQPlayerState> = _playerState.asStateFlow()

    private val _smartRecommendations = MutableStateFlow<List<Song>>(emptyList())
    val smartRecommendations: StateFlow<List<Song>> = _smartRecommendations.asStateFlow()

    private val _isSmartAutoplay = MutableStateFlow(true)
    val isSmartAutoplay: StateFlow<Boolean> = _isSmartAutoplay.asStateFlow()

    private val _currentVibe = MutableStateFlow<VibeClassification?>(null)
    val currentVibe: StateFlow<VibeClassification?> = _currentVibe.asStateFlow()

    private val _currentLyrics = MutableStateFlow<com.lofiq.data.lyrics.model.Lyrics?>(null)
    val currentLyrics: StateFlow<com.lofiq.data.lyrics.model.Lyrics?> = _currentLyrics.asStateFlow()

    private val _isLyricsLoading = MutableStateFlow(false)
    val isLyricsLoading: StateFlow<Boolean> = _isLyricsLoading.asStateFlow()

    private val currentQueue = mutableListOf<Song>()
    private var currentIndex = -1
    private var isExplicitQueue = false
    private var priorityQueueOffset = 0
    private var positionJob: Job? = null
    private var lyricsJob: Job? = null
    private var playbackJob: Job? = null
    private var preloadJob: Job? = null
    private var volumeFadeJob: Job? = null

    private var activePlaybackId: String = ""
    private var consecutiveFailures = 0
    private var currentSongRetryCount = 0
    private var crossfadeSeconds: Int = 8
    private var userAudioQuality: AudioQuality = AudioQuality.AUTO
    private var masterVolumeScale: Float = 1.0f

    // Early Preload & Transition Coordination State
    private var preloadEpoch: Long = 0L
    private var secondarySlotState: SecondarySlotState = SecondarySlotState.IDLE
    private var secondarySong: Song? = null
    private var secondarySongId: String? = null
    private var secondaryPreparedMediaItem: MediaItem? = null
    private var secondaryArtworkBytes: ByteArray? = null
    private var inFlightPreloadSongId: String? = null
    private val failedPreloadForTrackIds = mutableSetOf<String>()
    private var lastBufferingWaitLogSongId: String? = null
    private var isTransitioning: Boolean = false
    private var isTransitionPaused: Boolean = false
    private var transitionTargetSongId: String? = null
    private var currentCrossfadeT: Float = 0f

    fun getMasterVolumeScale(): Float = masterVolumeScale

    fun setMasterVolumeScale(scale: Float) {
        masterVolumeScale = scale.coerceIn(0f, 1f)
        if (isTransitioning) {
            activePlayer.volume = (equalPowerOutGain(currentCrossfadeT) * masterVolumeScale).coerceIn(0f, 1f)
            secondaryPlayer.volume = (equalPowerInGain(currentCrossfadeT) * masterVolumeScale).coerceIn(0f, 1f)
        } else {
            activePlayer.volume = masterVolumeScale
        }
    }

    private fun invalidateSecondaryPreload(reason: String) {
        preloadEpoch++
        preloadJob?.cancel()
        preloadJob = null
        inFlightPreloadSongId = null
        if (secondarySongId != null || secondarySlotState != SecondarySlotState.IDLE) {
            LofiQLogger.i(LofiQLogger.Category.PREFETCH, "[PRELOAD] Invalidating secondary preload ($reason)")
        }
        secondarySlotState = SecondarySlotState.IDLE
        secondarySong = null
        secondarySongId = null
        secondaryPreparedMediaItem = null
        secondaryArtworkBytes = null
        lastBufferingWaitLogSongId = null
        runCatching {
            val sec = secondaryPlayer
            sec.volume = 0f
            sec.playWhenReady = false
            if (sec.playbackState != Player.STATE_IDLE || sec.mediaItemCount > 0) {
                sec.stop()
                sec.clearMediaItems()
            }
        }
    }

    private fun cancelFadeAndRestoreVolume(clearPreload: Boolean = false) {
        val hadActiveFade = volumeFadeJob?.isActive == true || isTransitioning
        volumeFadeJob?.cancel()
        volumeFadeJob = null
        isTransitioning = false
        isTransitionPaused = false
        transitionTargetSongId = null
        currentCrossfadeT = 0f

        if (hadActiveFade || clearPreload) {
            invalidateSecondaryPreload(if (clearPreload) "clearPreload" else "cancelledTransition")
        }

        if (activePlayer.volume != masterVolumeScale) {
            activePlayer.volume = masterVolumeScale
        }
        _playerState.update { it.copy(isCrossfading = false) }
    }

    private fun validateSecondaryPreloadTarget() {
        val nextSong = getNextSong()
        val stalePreloaded = secondarySongId != null && secondarySongId != nextSong?.id
        val staleInFlight = inFlightPreloadSongId != null && inFlightPreloadSongId != nextSong?.id
        if (stalePreloaded || staleInFlight) {
            if (isTransitioning && transitionTargetSongId != nextSong?.id) {
                cancelFadeAndRestoreVolume(clearPreload = true)
            } else {
                invalidateSecondaryPreload("queueTargetChanged")
            }
        }
    }

    private fun maybePreloadAfterQueueChange() {
        validateSecondaryPreloadTarget()
        val active = activePlayer
        val nextSong = getNextSong() ?: return
        if (!active.isPlaying || isTransitioning) return
        val rawDur = active.duration.coerceAtLeast(0L)
        val effectiveDur = if (rawDur > 0L) rawDur else (currentSong?.durationMs ?: 0L)
        val effFade = computeEffectiveCrossfadeMs(crossfadeSeconds, effectiveDur)
        val triggerMs = computePreloadTriggerMs(effectiveDur, effFade)
        if (active.currentPosition >= triggerMs &&
            secondarySongId != nextSong.id &&
            inFlightPreloadSongId != nextSong.id &&
            !failedPreloadForTrackIds.contains(nextSong.id)
        ) {
            preloadNextTrack()
        }
    }

    init {
        attachSlotListener(playerSlotA, isSlotA = true)
        attachSlotListener(playerSlotB, isSlotA = false)
        observePreferences()
        startPositionTracker()
    }

    private fun attachSlotListener(slotPlayer: ExoPlayer, isSlotA: Boolean) {
        slotPlayer.addListener(object : Player.Listener {
            private fun isFromActiveSlot(): Boolean = isSlotA == isSlotAActive

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isFromActiveSlot()) {
                    LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[MEDIA3][id=$activePlaybackId][slot=${if (isSlotA) "A" else "B"}] IS_PLAYING=$isPlaying")
                    updateState()
                } else if (isPlaying && !isTransitioning) {
                    // Safety invariant: secondary player must never output audio before transition begins
                    LofiQLogger.w(LofiQLogger.Category.PREFETCH, "[SECONDARY_PLAYER] Guard triggered: pausing premature secondary playback")
                    runCatching {
                        slotPlayer.volume = 0f
                        slotPlayer.playWhenReady = false
                        slotPlayer.pause()
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN"
                }

                if (!isFromActiveSlot()) {
                    // Secondary slot readiness tracking
                    if (secondarySongId != null) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                if (secondarySlotState != SecondarySlotState.READY) {
                                    secondarySlotState = SecondarySlotState.PREPARING
                                }
                                LofiQLogger.d(
                                    LofiQLogger.Category.PREFETCH,
                                    "[SECONDARY_PLAYER] Buffering incoming '${secondarySong?.title}' buffered=${slotPlayer.bufferedPosition}ms"
                                )
                            }
                            Player.STATE_READY -> {
                                secondarySlotState = SecondarySlotState.READY
                                // Ensure incoming stays silent and paused at 0 until transition starts
                                if (!isTransitioning) {
                                    slotPlayer.volume = 0f
                                    slotPlayer.playWhenReady = false
                                    if (slotPlayer.isPlaying) slotPlayer.pause()
                                    if (slotPlayer.currentPosition > 50L) slotPlayer.seekTo(0L)
                                }
                                LofiQLogger.i(
                                    LofiQLogger.Category.PREFETCH,
                                    "[SECONDARY_PLAYER] READY: '${secondarySong?.title}' buffered=${slotPlayer.bufferedPosition}ms pos=${slotPlayer.currentPosition}ms vol=0%"
                                )
                            }
                            Player.STATE_ENDED -> {
                                if (!isTransitioning) {
                                    LofiQLogger.w(LofiQLogger.Category.PREFETCH, "[SECONDARY_PLAYER] Ended unexpectedly during preload for '${secondarySong?.title}'")
                                }
                            }
                            Player.STATE_IDLE -> Unit
                        }
                    }
                    return
                }

                // Active primary slot state handling
                LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[MEDIA3][id=$activePlaybackId][slot=${if (isSlotA) "A" else "B"}] STATE=$stateName")
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        _playerState.update { it.copy(isBuffering = true) }
                    }
                    Player.STATE_READY -> {
                        consecutiveFailures = 0
                        currentSongRetryCount = 0
                        _playerState.update {
                            it.copy(
                                isBuffering = false,
                                isReady = true,
                                errorMessage = null,
                                activeAudioSessionId = slotPlayer.audioSessionId
                            )
                        }
                    }
                    Player.STATE_ENDED -> {
                        _playerState.update { it.copy(isBuffering = true, isPlaying = true) }
                        handleTrackEnded()
                    }
                    Player.STATE_IDLE -> {
                        _playerState.update { it.copy(isBuffering = false) }
                    }
                }
                updateState()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val errorCodeName = error.errorCodeName
                val errorCode = error.errorCode
                val causeMsg = error.cause?.message ?: "Unknown cause"
                val errorDetails = error.message ?: "Unknown error"

                if (!isFromActiveSlot()) {
                    val failedTitle = secondarySong?.title ?: "unknown"
                    val failedId = secondarySongId
                    LofiQLogger.w(
                        LofiQLogger.Category.PLAYBACK,
                        "[PLAYBACK_RECOVERY] [SECONDARY_PLAYER] Incoming preload decode/network error for '$failedTitle' (code=$errorCode $errorCodeName). Active track remains uninterrupted."
                    )
                    if (failedId != null) {
                        failedPreloadForTrackIds.add(failedId)
                    }
                    if (isTransitioning) {
                        cancelFadeAndRestoreVolume(clearPreload = true)
                    } else {
                        invalidateSecondaryPreload("secondaryPlayerError:$errorCodeName")
                    }
                    return
                }

                LofiQLogger.e(
                    LofiQLogger.Category.PLAYBACK,
                    "[PLAYBACK_RECOVERY] [MEDIA3][id=$activePlaybackId] ERROR: code=$errorCode ($errorCodeName), msg=$errorDetails, cause=$causeMsg",
                    error
                )

                // 1. AudioTrack sink failure (Bluetooth connection / routing / codec renegotiation)
                if (errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
                    errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED) {
                    val curPos = slotPlayer.currentPosition.coerceAtLeast(0L)
                    LofiQLogger.w(
                        LofiQLogger.Category.PLAYBACK,
                        "[PLAYBACK_RECOVERY] AudioTrack output error (Bluetooth/routing). Re-preparing active player at ${curPos}ms."
                    )
                    slotPlayer.prepare()
                    if (curPos > 0) {
                        slotPlayer.seekTo(curPos)
                    }
                    slotPlayer.play()
                    return
                }

                val current = currentSong
                if (current != null) {
                    engineScope.launch(dispatchers.io) {
                        resolutionCache.invalidate("${current.id}_${current.quality.name}")
                    }
                }

                if (current != null && (current.providerId == "local" || current.id.startsWith("content://") || current.sourceUrl.startsWith("content://"))) {
                    engineScope.launch {
                        commandMutex.withLock {
                            if (currentIndex < currentQueue.size - 1) {
                                currentIndex++
                                LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[PLAYBACK_RECOVERY] Advancing to next local track after error on '${current.title}'")
                                startPlaybackInternal(currentQueue[currentIndex], retryCount = 0)
                            } else {
                                _playerState.update {
                                    it.copy(
                                        isBuffering = false,
                                        isPlaying = false,
                                        isReady = false,
                                        errorMessage = "Playback failed for local file '${current.title}'"
                                    )
                                }
                            }
                        }
                    }
                    return
                }

                // 2. First attempt recovery by resolving alternative stream for the SAME track
                if (currentSongRetryCount < 2 && current != null) {
                    currentSongRetryCount++
                    LofiQLogger.w(
                        LofiQLogger.Category.PLAYBACK,
                        "[PLAYBACK_RECOVERY] Retrying same track '${current.title}' with fallback stream (attempt=${currentSongRetryCount + 1})"
                    )
                    startPlayback(current, retryCount = currentSongRetryCount)
                    return
                }

                // 3. If current track retries are exhausted, advance to next track
                engineScope.launch {
                    commandMutex.withLock {
                        if (currentIndex < currentQueue.size - 1 && consecutiveFailures < 3) {
                            currentIndex++
                            LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[PLAYBACK_RECOVERY] Advancing to next track after player error on '${current?.title}'")
                            startPlaybackInternal(currentQueue[currentIndex], retryCount = 0)
                            checkAndReplenishQueue()
                        } else if (_isSmartAutoplay.value && consecutiveFailures < 3) {
                            LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[PLAYBACK_RECOVERY] Fetching infinite track after player error")
                            fetchAndPlayNextInfiniteTrack(retryCount = 0)
                        } else {
                            _playerState.update {
                                it.copy(
                                    isBuffering = false,
                                    isPlaying = false,
                                    isReady = false,
                                    errorMessage = "Playback failed ($errorCodeName): $causeMsg"
                                )
                            }
                        }
                    }
                }
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                if (!isFromActiveSlot()) return
                val audioTracks = tracks.groups.filter { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO }
                for (group in audioTracks) {
                    for (i in 0 until group.length) {
                        if (group.isTrackSelected(i)) {
                            val format = group.getTrackFormat(i)
                            LofiQLogger.i(
                                LofiQLogger.Category.PLAYBACK,
                                "[MEDIA3][id=$activePlaybackId] TRACK_SELECTED: mime=${format.sampleMimeType}, bitrate=${format.bitrate}, sampleRate=${format.sampleRate}, channels=${format.channelCount}"
                            )
                        }
                    }
                }
            }
        })
    }

    private fun observePreferences() {
        engineScope.launch {
            preferences.repeatMode.collect { mode ->
                _playerState.update { it.copy(repeatMode = mode) }
                // Both dual-player slots operate with single-item REPEAT_MODE_OFF internally;
                // PlaybackEngine controls queue wrapping & Repeat ONE deterministically across handovers.
                playerSlotA.repeatMode = Player.REPEAT_MODE_OFF
                playerSlotB.repeatMode = Player.REPEAT_MODE_OFF
                when (mode) {
                    PlaybackRepeatMode.OFF, PlaybackRepeatMode.ALL -> {
                        validateSecondaryPreloadTarget()
                        if (activePlayer.isPlaying && activePlayer.currentPosition >= PRELOAD_TARGET_MS) {
                            preloadNextTrack()
                        }
                    }
                    PlaybackRepeatMode.ONE -> {
                        if (isTransitioning) {
                            cancelFadeAndRestoreVolume(clearPreload = true)
                        } else {
                            invalidateSecondaryPreload("repeatOneEnabled")
                        }
                    }
                }
            }
        }
        engineScope.launch {
            preferences.shuffleEnabled.collect { isShuffle ->
                _playerState.update { it.copy(isShuffleEnabled = isShuffle) }
            }
        }
        engineScope.launch {
            preferences.crossfadeDurationSec.collect { sec ->
                val clamped = sec.coerceIn(0, 12)
                if (crossfadeSeconds != clamped) {
                    crossfadeSeconds = clamped
                    LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[CROSSFADE] Updated crossfade duration to ${clamped}s")
                }
            }
        }
        engineScope.launch {
            preferences.audioQuality.collect { quality ->
                val previous = userAudioQuality
                userAudioQuality = quality
                if (previous != quality) {
                    failedPreloadForTrackIds.clear()
                    invalidateSecondaryPreload("audioQualityChanged:$quality")
                    if (currentSong != null && (activePlayer.isPlaying || activePlayer.playWhenReady)) {
                        handleDynamicQualityChange(quality)
                    }
                }
            }
        }
    }

    private fun handleDynamicQualityChange(newQuality: AudioQuality) {
        val song = currentSong ?: return
        val isLocalDeviceTrack = song.providerId == "local" ||
                song.id.startsWith("content://") ||
                song.id.startsWith("file://") ||
                song.sourceUrl.startsWith("content://") ||
                song.sourceUrl.startsWith("file://")
        if (isLocalDeviceTrack) return

        val currentPos = activePlayer.currentPosition.coerceAtLeast(0L)
        val wasPlaying = activePlayer.isPlaying
        val effectiveQuality = networkQualityMonitor.getEffectiveQuality(newQuality)

        LofiQLogger.i(
            LofiQLogger.Category.PLAYBACK,
            "[QUALITY_SWITCH] Switching active playback stream to $newQuality (effective=$effectiveQuality) at ${currentPos}ms"
        )

        cancelFadeAndRestoreVolume(clearPreload = true)

        engineScope.launch {
            try {
                val (mediaItem, provider) = resolveMediaItemForSong(
                    song = song,
                    qualityOverride = effectiveQuality
                )
                withContext(dispatchers.main) {
                    if (currentSong?.id == song.id) {
                        val active = activePlayer
                        active.setMediaItem(mediaItem, currentPos)
                        active.prepare()
                        active.volume = masterVolumeScale
                        if (wasPlaying) {
                            active.play()
                        }
                        _playerState.update {
                            it.copy(
                                currentPositionMs = currentPos,
                                activeProviderId = provider,
                                activeAudioSessionId = active.audioSessionId
                            )
                        }
                    }
                }
                if (currentPos >= PRELOAD_TARGET_MS) {
                    preloadNextTrack()
                }
            } catch (e: Exception) {
                LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[QUALITY_SWITCH] Failed to reload stream in new quality: ${e.message}")
            }
        }
    }

    private fun startPositionTracker() {
        positionJob?.cancel()
        positionJob = engineScope.launch {
            var lastPrefetchPos = 0L
            while (isActive) {
                var trackerDelay = 200L
                runCatching {
                    val active = activePlayer
                    if (active.isPlaying) {
                        val pos = active.currentPosition.coerceAtLeast(0L)
                        val rawExoDur = active.duration.coerceAtLeast(0L)
                        val songDur = currentSong?.durationMs ?: 0L
                        val effectiveDur = when {
                            rawExoDur > 0L -> rawExoDur
                            songDur > 0L -> songDur
                            else -> 0L
                        }
                        val buffered = active.bufferedPosition.coerceAtLeast(0L)
                        _playerState.update {
                            it.copy(
                                currentPositionMs = pos,
                                durationMs = if (effectiveDur > 0) effectiveDur else it.durationMs,
                                bufferedPositionMs = buffered,
                                activeAudioSessionId = active.audioSessionId
                            )
                        }

                        val effectiveCrossfadeMs = computeEffectiveCrossfadeMs(crossfadeSeconds, effectiveDur)
                        val preloadTriggerMs = computePreloadTriggerMs(effectiveDur, effectiveCrossfadeMs)

                        // Stage 1: Early Preloading at ~10 seconds into active playback (both Crossfade & Gapless-Only)
                        if (!isTransitioning && pos >= preloadTriggerMs) {
                            val nextSong = getNextSong()
                            if (nextSong != null &&
                                secondarySongId != nextSong.id &&
                                inFlightPreloadSongId != nextSong.id &&
                                !failedPreloadForTrackIds.contains(nextSong.id)
                            ) {
                                LofiQLogger.i(
                                    LofiQLogger.Category.PREFETCH,
                                    "[PRELOAD] Reached ${pos}ms (trigger=${preloadTriggerMs}ms) -> preparing next track '${nextSong.title}' asynchronously on secondary slot"
                                )
                                preloadNextTrack()
                            }
                        }

                        // Replenish queue / warm upcoming cache asynchronously
                        if (pos >= PRELOAD_TARGET_MS && (pos - lastPrefetchPos > 30_000L || (effectiveDur > 0L && effectiveDur - pos < 45_000L))) {
                            lastPrefetchPos = pos
                            triggerUpcomingPrefetch()
                            checkAndReplenishQueue()
                        }

                        // Stage 2: Crossfade or Gapless Zero-Overlap Transition Window
                        if (!isTransitioning && effectiveDur > 0L && pos >= 2_000L) {
                            val remainingMs = (effectiveDur - pos).coerceAtLeast(0L)
                            val nextSong = getNextSong()
                            val isNextPrepared = nextSong != null &&
                                secondarySongId == nextSong.id &&
                                (secondaryPlayer.playbackState == Player.STATE_READY || secondarySlotState == SecondarySlotState.READY)

                            if (effectiveCrossfadeMs > 0L) {
                                // High-precision polling as we approach the crossfade start boundary
                                if (remainingMs in (effectiveCrossfadeMs - 400L)..(effectiveCrossfadeMs + 2_500L)) {
                                    trackerDelay = 40L
                                }

                                if (remainingMs <= effectiveCrossfadeMs && nextSong != null) {
                                    if (isNextPrepared) {
                                        startDualPlayerCrossfade(
                                            fadeDurationMs = effectiveCrossfadeMs,
                                            targetSong = nextSong
                                        )
                                    } else {
                                        // Secondary player is still resolving or buffering; keep Song A uninterrupted
                                        if (lastBufferingWaitLogSongId != nextSong.id) {
                                            lastBufferingWaitLogSongId = nextSong.id
                                            LofiQLogger.w(
                                                LofiQLogger.Category.PLAYBACK,
                                                "[CROSSFADE] Waiting for incoming '${nextSong.title}' buffer readiness (state=$secondarySlotState, rem=${remainingMs}ms) without interrupting '${currentSong?.title}'"
                                            )
                                        }
                                        if (secondarySongId != nextSong.id && inFlightPreloadSongId != nextSong.id && !failedPreloadForTrackIds.contains(nextSong.id)) {
                                            preloadNextTrack()
                                        }
                                    }
                                }
                            } else {
                                // Crossfade == 0: True Gapless Zero-Overlap Playback
                                if (remainingMs <= 1_000L) {
                                    trackerDelay = 35L
                                }
                                if (remainingMs <= GAPLESS_CUT_THRESHOLD_MS && isNextPrepared && nextSong != null) {
                                    triggerGaplessZeroOverlapCut(nextSong)
                                }
                            }
                        }

                        if (!isTransitioning && active.volume != masterVolumeScale) {
                            active.volume = masterVolumeScale
                        }
                    }
                }
                delay(trackerDelay)
            }
        }
    }

    private fun startDualPlayerCrossfade(fadeDurationMs: Long, targetSong: Song) {
        if (isTransitioning) return
        val outgoing = activePlayer
        val incoming = secondaryPlayer
        if (secondarySongId != targetSong.id || incoming.mediaItemCount == 0 || incoming.playbackState != Player.STATE_READY) {
            return
        }

        isTransitioning = true
        isTransitionPaused = false
        transitionTargetSongId = targetSong.id
        currentCrossfadeT = 0f
        _playerState.update { it.copy(isCrossfading = true) }

        val outgoingTitle = currentSong?.title ?: "Unknown"
        LofiQLogger.i(
            LofiQLogger.Category.PLAYBACK,
            "[CROSSFADE] Starting equal-power dual-player overlap: out='$outgoingTitle' (slot=${if (isSlotAActive) "A" else "B"}, pos=${outgoing.currentPosition}ms) -> in='${targetSong.title}' (slot=${if (isSlotAActive) "B" else "A"}, buffered=${incoming.bufferedPosition}ms, fade=${fadeDurationMs}ms)"
        )

        volumeFadeJob?.cancel()
        volumeFadeJob = engineScope.launch {
            try {
                // Start incoming player silently from position 0 while leaving outgoing untouched
                if (incoming.currentPosition > 50L) {
                    incoming.seekTo(0L)
                }
                incoming.volume = 0.0f
                incoming.playWhenReady = true
                incoming.play()

                val tickMs = 40L
                var elapsedMs = 0L
                var acceleratedFade = false

                while (isActive && elapsedMs < fadeDurationMs) {
                    delay(tickMs)

                    // Respect pause/resume smoothly mid-crossfade
                    if (isTransitionPaused || (!outgoing.isPlaying && !incoming.isPlaying && !outgoing.playWhenReady)) {
                        continue
                    }

                    // If outgoing reached natural end slightly before fade finished, finish incoming ramp smoothly over ~120ms
                    if (!acceleratedFade && (outgoing.playbackState == Player.STATE_ENDED || outgoing.playbackState == Player.STATE_IDLE)) {
                        acceleratedFade = true
                        elapsedMs = maxOf(elapsedMs, fadeDurationMs - 120L)
                    } else {
                        elapsedMs += tickMs
                    }

                    val t = (elapsedMs.toFloat() / fadeDurationMs.toFloat()).coerceIn(0.0f, 1.0f)
                    currentCrossfadeT = t

                    val outVol = (equalPowerOutGain(t) * masterVolumeScale).coerceIn(0f, 1f)
                    val inVol = (equalPowerInGain(t) * masterVolumeScale).coerceIn(0f, 1f)

                    if (outgoing.playbackState != Player.STATE_IDLE) {
                        outgoing.volume = outVol
                    }
                    incoming.volume = inVol
                }

                // Ensure incoming is genuinely playing before handover
                if (!incoming.isPlaying && incoming.playbackState == Player.STATE_READY) {
                    incoming.playWhenReady = true
                    incoming.play()
                }
                incoming.volume = masterVolumeScale
                outgoing.volume = 0f

                LofiQLogger.i(
                    LofiQLogger.Category.PLAYBACK,
                    "[CROSSFADE] Volume ramp reached 100% for '${targetSong.title}' -> performing atomic handover"
                )
                performPlayerHandover(expectedIncomingSong = targetSong, isGapless = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LofiQLogger.e(LofiQLogger.Category.PLAYBACK, "[PLAYBACK_RECOVERY] [CROSSFADE] Transition error: ${e.message}", e)
                cancelFadeAndRestoreVolume(clearPreload = false)
            }
        }
    }

    private fun triggerGaplessZeroOverlapCut(targetSong: Song) {
        if (isTransitioning) return
        val incoming = secondaryPlayer
        if (secondarySongId != targetSong.id || incoming.mediaItemCount == 0 || incoming.playbackState != Player.STATE_READY) {
            return
        }

        isTransitioning = true
        transitionTargetSongId = targetSong.id
        LofiQLogger.i(
            LofiQLogger.Category.PLAYBACK,
            "[GAPLESS] Zero-overlap handover triggered: '${currentSong?.title}' -> '${targetSong.title}' (buffered=${incoming.bufferedPosition}ms)"
        )

        volumeFadeJob?.cancel()
        volumeFadeJob = engineScope.launch {
            if (incoming.currentPosition > 50L) {
                incoming.seekTo(0L)
            }
            incoming.volume = masterVolumeScale
            incoming.playWhenReady = true
            incoming.play()
            performPlayerHandover(expectedIncomingSong = targetSong, isGapless = true)
        }
    }

    private suspend fun performPlayerHandover(expectedIncomingSong: Song, isGapless: Boolean) {
        commandMutex.withLock {
            performPlayerHandoverLocked(expectedIncomingSong, isGapless)
        }
    }

    private suspend fun performPlayerHandoverLocked(expectedIncomingSong: Song, isGapless: Boolean) {
        val oldPrimary = activePlayer
        val newPrimary = secondaryPlayer

        if (secondarySongId != expectedIncomingSong.id || newPrimary.mediaItemCount == 0) {
            LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[PLAYBACK_RECOVERY] [PLAYER_HANDOVER] Handover aborted: incoming mismatch for '${expectedIncomingSong.title}'")
            cancelFadeAndRestoreVolume(clearPreload = true)
            return
        }

        val incomingArtwork = secondaryArtworkBytes
        val incomingMediaItem = secondaryPreparedMediaItem

        // 1. Ensure incoming player owns audible output at full volume and starts/stays playing
        newPrimary.volume = masterVolumeScale
        newPrimary.playWhenReady = true
        if (!newPrimary.isPlaying && newPrimary.playbackState == Player.STATE_READY) {
            newPrimary.play()
        }

        // 2. Transfer audio focus ownership cleanly without creating a second MediaSession
        oldPrimary.setAudioAttributes(mediaAudioAttributes, false)
        newPrimary.setAudioAttributes(mediaAudioAttributes, true)

        // 3. Atomic slot promotion
        isSlotAActive = !isSlotAActive
        _activePlayerFlow.value = newPrimary

        // 4. Silence and release old primary media resources so it becomes clean secondary slot
        runCatching {
            oldPrimary.volume = 0f
            oldPrimary.playWhenReady = false
            oldPrimary.pause()
            oldPrimary.stop()
            oldPrimary.clearMediaItems()
        }

        // 5. Update queue pointer & track metadata
        val targetIdx = findTargetIndexForSong(expectedIncomingSong.id)
        val updatedSong: Song
        if (targetIdx != -1) {
            if (!isExplicitQueue && targetIdx > 0) {
                val remaining = currentQueue.drop(targetIdx)
                currentQueue.clear()
                currentQueue.addAll(remaining)
                currentIndex = 0
            } else {
                currentIndex = targetIdx
            }
            updatedSong = currentQueue[currentIndex]
        } else {
            updatedSong = expectedIncomingSong
        }
        if (priorityQueueOffset > 0) priorityQueueOffset--

        activePlaybackId = java.util.UUID.randomUUID().toString().take(8)
        currentSongRetryCount = 0
        consecutiveFailures = 0

        // 6. Reset secondary & transition state for the next cycle (Song C will preload at 10s!)
        preloadEpoch++
        preloadJob?.cancel()
        preloadJob = null
        inFlightPreloadSongId = null
        secondarySlotState = SecondarySlotState.IDLE
        secondarySong = null
        secondarySongId = null
        secondaryPreparedMediaItem = null
        secondaryArtworkBytes = null
        failedPreloadForTrackIds.clear()
        lastBufferingWaitLogSongId = null
        isTransitioning = false
        isTransitionPaused = false
        transitionTargetSongId = null
        currentCrossfadeT = 0f

        // 7. Apply cached artwork onto MediaSession playlistMetadata if ready
        if (incomingArtwork != null && incomingMediaItem != null) {
            runCatching {
                val updatedMeta = incomingMediaItem.mediaMetadata.buildUpon()
                    .setArtworkData(incomingArtwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    .build()
                newPrimary.playlistMetadata = updatedMeta
            }
        }

        val actualPosMs = newPrimary.currentPosition.coerceAtLeast(0L)
        val actualDurMs = if (newPrimary.duration > 0L) newPrimary.duration else updatedSong.durationMs
        val sessionId = newPrimary.audioSessionId

        _playerState.update {
            it.copy(
                currentSong = updatedSong,
                activeProviderId = updatedSong.providerId,
                isPlaying = true,
                isBuffering = newPrimary.playbackState == Player.STATE_BUFFERING,
                isReady = newPrimary.playbackState == Player.STATE_READY,
                isCrossfading = false,
                errorMessage = null,
                currentPositionMs = actualPosMs,
                durationMs = actualDurMs,
                queue = currentQueue.toList(),
                currentQueueIndex = currentIndex,
                activeAudioSessionId = sessionId
            )
        }
        updateState()

        LofiQLogger.i(
            LofiQLogger.Category.PLAYBACK,
            "[PLAYER_HANDOVER] ${if (isGapless) "GAPLESS" else "CROSSFADE"} handover complete -> activeSlot=${if (isSlotAActive) "A" else "B"}, track='${updatedSong.title}', pos=${actualPosMs}ms, sessionId=$sessionId"
        )

        fetchLyricsForSong(updatedSong)
        if (updatedSong.providerId != "local" && updatedSong.providerId != "local_download") {
            fetchSmartRecommendations(updatedSong, autoAppend = !isExplicitQueue, targetCount = 10)
        }
        engineScope.launch(dispatchers.io) {
            runCatching {
                songDao.incrementPlayCount(updatedSong.id)
                historyDao.recordHistory(
                    PlaybackHistoryEntity(
                        songId = updatedSong.id,
                        playedAt = System.currentTimeMillis(),
                        durationPlayedSeconds = 0
                    )
                )
                persistQueue()
            }
        }
        checkAndReplenishQueue()
    }

    private fun findTargetIndexForSong(songId: String): Int {
        if (currentIndex in 0 until currentQueue.size - 1 && currentQueue[currentIndex + 1].id == songId) {
            return currentIndex + 1
        }
        if (_playerState.value.repeatMode == PlaybackRepeatMode.ALL && currentQueue.isNotEmpty() && currentQueue[0].id == songId) {
            return 0
        }
        return currentQueue.indexOfFirst { it.id == songId }
    }

    fun playSong(song: Song) {
        engineScope.launch {
            commandMutex.withLock {
                consecutiveFailures = 0
                priorityQueueOffset = 0
                isExplicitQueue = false
                if (currentSong?.id == song.id &&
                    !isTransitioning &&
                    activePlayer.playbackState != Player.STATE_IDLE &&
                    activePlayer.playbackState != Player.STATE_ENDED
                ) {
                    activePlayer.volume = masterVolumeScale
                    activePlayer.playWhenReady = true
                    activePlayer.play()
                    _playerState.update { it.copy(isPlaying = true, isBuffering = false) }
                    updateState()
                    return@withLock
                }
                cancelFadeAndRestoreVolume(clearPreload = true)
                failedPreloadForTrackIds.clear()
                currentQueue.clear()
                currentQueue.add(song)
                currentIndex = 0
                startPlaybackInternal(song)
                fetchSmartRecommendations(song, autoAppend = true, targetCount = 10)
            }
        }
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        engineScope.launch {
            commandMutex.withLock {
                consecutiveFailures = 0
                priorityQueueOffset = 0
                isExplicitQueue = songs.size > 1
                val targetIndex = startIndex.coerceIn(0, songs.size - 1)
                val targetSong = songs[targetIndex]

                if (currentSong?.id == targetSong.id &&
                    !isTransitioning &&
                    currentQueue.size == songs.size &&
                    activePlayer.playbackState != Player.STATE_IDLE &&
                    activePlayer.playbackState != Player.STATE_ENDED
                ) {
                    activePlayer.volume = masterVolumeScale
                    activePlayer.playWhenReady = true
                    activePlayer.play()
                    _playerState.update { it.copy(isPlaying = true, isBuffering = false) }
                    updateState()
                    return@withLock
                }

                cancelFadeAndRestoreVolume(clearPreload = true)
                failedPreloadForTrackIds.clear()
                currentQueue.clear()
                currentQueue.addAll(songs)
                currentIndex = targetIndex
                startPlaybackInternal(currentQueue[currentIndex])
                triggerUpcomingPrefetch()
                persistQueue()
            }
        }
    }

    fun playQueueItem(targetIndex: Int) {
        engineScope.launch {
            commandMutex.withLock {
                if (targetIndex !in currentQueue.indices) return@withLock
                consecutiveFailures = 0
                priorityQueueOffset = 0
                cancelFadeAndRestoreVolume(clearPreload = true)
                failedPreloadForTrackIds.clear()
                if (!isExplicitQueue) {
                    // In single-track auto mode, drop preceding tracks so chosen song is at 0,
                    // and top up to 10 smart recommendations based on the newly selected track!
                    val targetSong = currentQueue[targetIndex]
                    val remaining = currentQueue.drop(targetIndex)
                    currentQueue.clear()
                    currentQueue.addAll(remaining)
                    currentIndex = 0
                    startPlaybackInternal(targetSong)
                    fetchSmartRecommendations(targetSong, autoAppend = true, targetCount = 10)
                } else {
                    currentIndex = targetIndex
                    startPlaybackInternal(currentQueue[currentIndex])
                }
                updateState()
                triggerUpcomingPrefetch()
                persistQueue()
            }
        }
    }

    suspend fun restoreAndPlayLastTrack(): Boolean = withContext(dispatchers.io) {
        try {
            val persisted = queueSnapshotDao.getPersistedQueue()
            if (persisted.isNotEmpty()) {
                val domainSongs = persisted.map { it.toDomain() }
                withContext(dispatchers.main) {
                    playQueue(domainSongs, startIndex = 0)
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[PLAYBACK] Could not restore saved queue: ${e.message}")
            false
        }
    }

    suspend fun getSavedQueueOrEmpty(): List<Song> = withContext(dispatchers.io) {
        try {
            queueSnapshotDao.getPersistedQueue().map { it.toDomain() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addToQueue(song: Song) {
        engineScope.launch {
            commandMutex.withLock {
                currentQueue.add(song)
                updateState()
                triggerUpcomingPrefetch()
                maybePreloadAfterQueueChange()
                persistQueue()
            }
        }
    }

    fun enqueuePriorityNext(fromIndex: Int) {
        engineScope.launch {
            commandMutex.withLock {
                if (fromIndex !in currentQueue.indices || fromIndex <= currentIndex) return@withLock
                val song = currentQueue.removeAt(fromIndex)
                val targetPos = (currentIndex + 1 + priorityQueueOffset).coerceIn(currentIndex + 1, currentQueue.size)
                currentQueue.add(targetPos, song)
                priorityQueueOffset++
                updateState()
                triggerUpcomingPrefetch()
                maybePreloadAfterQueueChange()
                persistQueue()
            }
        }
    }

    fun playNextInQueue(song: Song) {
        engineScope.launch {
            commandMutex.withLock {
                val insertPos = (currentIndex + 1 + priorityQueueOffset).coerceIn(0, currentQueue.size)
                currentQueue.add(insertPos, song)
                priorityQueueOffset++
                updateState()
                triggerUpcomingPrefetch()
                maybePreloadAfterQueueChange()
                persistQueue()
            }
        }
    }

    fun togglePlayPause() {
        engineScope.launch {
            commandMutex.withLock {
                val active = activePlayer
                val secondary = secondaryPlayer
                if (active.isPlaying) {
                    LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[PLAYBACK][id=$activePlaybackId] PAUSE_REQUESTED")
                    if (isTransitioning) {
                        isTransitionPaused = true
                        runCatching { if (secondary.isPlaying) secondary.pause() }
                    }
                    active.pause()
                } else {
                    if ((active.playbackState == Player.STATE_IDLE || active.playbackState == Player.STATE_ENDED) && currentSong != null) {
                        startPlaybackInternal(currentSong!!)
                    } else {
                        LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[PLAYBACK][id=$activePlaybackId] PLAY_REQUESTED")
                        isTransitionPaused = false
                        active.playWhenReady = true
                        active.play()
                        if (isTransitioning && secondary.mediaItemCount > 0 &&
                            (secondary.playbackState == Player.STATE_READY || secondary.playbackState == Player.STATE_BUFFERING)
                        ) {
                            runCatching {
                                secondary.playWhenReady = true
                                secondary.play()
                            }
                        }
                    }
                }
                updateState()
            }
        }
    }

    fun play() {
        engineScope.launch {
            commandMutex.withLock {
                val active = activePlayer
                val secondary = secondaryPlayer
                if ((active.playbackState == Player.STATE_IDLE || active.playbackState == Player.STATE_ENDED) && currentSong != null) {
                    startPlaybackInternal(currentSong!!)
                } else {
                    LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[PLAYBACK][id=$activePlaybackId] PLAY_REQUESTED")
                    isTransitionPaused = false
                    active.playWhenReady = true
                    active.play()
                    if (isTransitioning && secondary.mediaItemCount > 0 &&
                        (secondary.playbackState == Player.STATE_READY || secondary.playbackState == Player.STATE_BUFFERING)
                    ) {
                        runCatching {
                            secondary.playWhenReady = true
                            secondary.play()
                        }
                    }
                }
                updateState()
            }
        }
    }

    fun pause() {
        engineScope.launch {
            commandMutex.withLock {
                LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[PLAYBACK][id=$activePlaybackId] PAUSE_REQUESTED")
                if (isTransitioning) {
                    isTransitionPaused = true
                    runCatching { if (secondaryPlayer.isPlaying) secondaryPlayer.pause() }
                }
                activePlayer.pause()
                updateState()
            }
        }
    }

    fun stop() {
        engineScope.launch {
            commandMutex.withLock {
                LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[PLAYBACK][id=$activePlaybackId] STOP_REQUESTED")
                cancelFadeAndRestoreVolume(clearPreload = true)
                playbackJob?.cancel()
                playbackJob = null
                failedPreloadForTrackIds.clear()
                runCatching {
                    playerSlotA.playWhenReady = false
                    playerSlotA.stop()
                    playerSlotA.clearMediaItems()
                }
                runCatching {
                    playerSlotB.playWhenReady = false
                    playerSlotB.stop()
                    playerSlotB.clearMediaItems()
                }
                currentQueue.clear()
                currentIndex = -1
                _playerState.value = LofiQPlayerState()
            }
        }
    }

    fun next() {
        engineScope.launch {
            commandMutex.withLock {
                if (currentQueue.isEmpty()) return@withLock
                consecutiveFailures = 0
                if (priorityQueueOffset > 0) priorityQueueOffset--

                val nextSong = getNextSong()
                val sec = secondaryPlayer
                if (nextSong != null &&
                    secondarySongId == nextSong.id &&
                    sec.mediaItemCount > 0 &&
                    sec.playbackState == Player.STATE_READY
                ) {
                    LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[GAPLESS] Manual skip promoting prepared secondary track: '${nextSong.title}'")
                    val wasAlreadyFadingSame = isTransitioning && transitionTargetSongId == nextSong.id
                    volumeFadeJob?.cancel()
                    volumeFadeJob = null
                    isTransitioning = false
                    isTransitionPaused = false
                    if (!wasAlreadyFadingSame && sec.currentPosition > 50L) {
                        sec.seekTo(0L)
                    }
                    sec.volume = masterVolumeScale
                    sec.playWhenReady = true
                    sec.play()
                    performPlayerHandoverLocked(expectedIncomingSong = nextSong, isGapless = true)
                    return@withLock
                }

                cancelFadeAndRestoreVolume(clearPreload = true)
                failedPreloadForTrackIds.clear()

                if (currentIndex < currentQueue.size - 1) {
                    currentIndex++
                    startPlaybackInternal(currentQueue[currentIndex])
                    checkAndReplenishQueue()
                } else if (_playerState.value.repeatMode == PlaybackRepeatMode.ALL) {
                    currentIndex = 0
                    startPlaybackInternal(currentQueue[currentIndex])
                } else {
                    val currentSongIsLocal = currentSong?.providerId == "local" || (currentSong?.id?.startsWith("content://") == true)
                    if (currentSongIsLocal) {
                        _playerState.update { it.copy(isPlaying = false, isBuffering = false) }
                    } else {
                        fetchAndPlayNextInfiniteTrack()
                    }
                }
            }
        }
    }

    fun previous() {
        engineScope.launch {
            commandMutex.withLock {
                if (currentQueue.isEmpty()) return@withLock
                consecutiveFailures = 0
                val active = activePlayer
                cancelFadeAndRestoreVolume(clearPreload = true)
                failedPreloadForTrackIds.clear()
                if (active.currentPosition > 3000L) {
                    active.seekTo(0L)
                    active.playWhenReady = true
                    active.play()
                } else if (currentIndex > 0) {
                    currentIndex--
                    startPlaybackInternal(currentQueue[currentIndex])
                } else if (_playerState.value.repeatMode == PlaybackRepeatMode.ALL) {
                    currentIndex = currentQueue.size - 1
                    startPlaybackInternal(currentQueue[currentIndex])
                } else {
                    active.seekTo(0L)
                }
                updateState()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        engineScope.launch {
            commandMutex.withLock {
                if (isTransitioning) {
                    cancelFadeAndRestoreVolume(clearPreload = true)
                }
                LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[PLAYBACK][id=$activePlaybackId] SEEK_REQUESTED to ${positionMs}ms")
                activePlayer.seekTo(positionMs)
                _playerState.update { it.copy(currentPositionMs = positionMs) }
            }
        }
    }

    fun toggleRepeat() {
        engineScope.launch {
            val nextMode = when (_playerState.value.repeatMode) {
                PlaybackRepeatMode.OFF -> PlaybackRepeatMode.ALL
                PlaybackRepeatMode.ALL -> PlaybackRepeatMode.ONE
                PlaybackRepeatMode.ONE -> PlaybackRepeatMode.OFF
            }
            preferences.setRepeatMode(nextMode)
        }
    }

    fun toggleShuffle() {
        engineScope.launch {
            commandMutex.withLock {
                val nextShuffle = !_playerState.value.isShuffleEnabled
                if (nextShuffle && currentQueue.size > 2 && currentIndex in 0 until currentQueue.size - 1) {
                    val keepPrefix = currentQueue.subList(0, currentIndex + 1).toList()
                    val upcoming = currentQueue.subList(currentIndex + 1, currentQueue.size).toMutableList()
                    upcoming.shuffle()
                    currentQueue.clear()
                    currentQueue.addAll(keepPrefix)
                    currentQueue.addAll(upcoming)
                    maybePreloadAfterQueueChange()
                    persistQueue()
                    updateState()
                }
                preferences.setShuffleEnabled(nextShuffle)
            }
        }
    }

    fun setSmartAutoplay(enabled: Boolean) {
        _isSmartAutoplay.value = enabled
        if (enabled) {
            val remainingTracks = currentQueue.size - 1 - currentIndex
            if (remainingTracks <= 3) {
                val seed = currentSong ?: currentQueue.lastOrNull()
                if (seed != null) {
                    fetchSmartRecommendations(seed)
                }
            }
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        engineScope.launch {
            commandMutex.withLock {
                if (fromIndex in currentQueue.indices && toIndex in currentQueue.indices && fromIndex != toIndex) {
                    val item = currentQueue.removeAt(fromIndex)
                    currentQueue.add(toIndex, item)
                    if (currentIndex == fromIndex) {
                        currentIndex = toIndex
                    } else if (fromIndex < currentIndex && toIndex >= currentIndex) {
                        currentIndex--
                    } else if (fromIndex > currentIndex && toIndex <= currentIndex) {
                        currentIndex++
                    }
                    updateState()
                    triggerUpcomingPrefetch()
                    maybePreloadAfterQueueChange()
                    persistQueue()
                }
            }
        }
    }

    fun reorderUpcomingQueue(newUpcomingList: List<Song>) {
        engineScope.launch {
            commandMutex.withLock {
                if (currentIndex in currentQueue.indices) {
                    val current = currentQueue[currentIndex]
                    val history = if (currentIndex > 0) currentQueue.subList(0, currentIndex) else emptyList()
                    val newFullQueue = ArrayList<Song>(history.size + 1 + newUpcomingList.size).apply {
                        addAll(history)
                        add(current)
                        addAll(newUpcomingList)
                    }
                    currentQueue.clear()
                    currentQueue.addAll(newFullQueue)
                    updateState()
                    triggerUpcomingPrefetch()
                    maybePreloadAfterQueueChange()
                    persistQueue()
                } else if (currentQueue.isEmpty()) {
                    currentQueue.addAll(newUpcomingList)
                    currentIndex = 0
                    updateState()
                    persistQueue()
                }
            }
        }
    }

    fun clearUpcomingQueue() {
        engineScope.launch {
            commandMutex.withLock {
                if (currentIndex in currentQueue.indices) {
                    val current = currentQueue[currentIndex]
                    currentQueue.clear()
                    currentQueue.add(current)
                    currentIndex = 0
                    if (isTransitioning) {
                        cancelFadeAndRestoreVolume(clearPreload = true)
                    } else {
                        invalidateSecondaryPreload("clearUpcomingQueue")
                    }
                    updateState()
                    persistQueue()
                }
            }
        }
    }

    fun removeQueueItem(index: Int) {
        engineScope.launch {
            commandMutex.withLock {
                if (index in currentQueue.indices) {
                    currentQueue.removeAt(index)
                    if (index < currentIndex) {
                        currentIndex--
                        validateSecondaryPreloadTarget()
                    } else if (index == currentIndex) {
                        cancelFadeAndRestoreVolume(clearPreload = true)
                        failedPreloadForTrackIds.clear()
                        if (currentIndex < currentQueue.size) {
                            startPlaybackInternal(currentQueue[currentIndex])
                        } else if (currentQueue.isNotEmpty()) {
                            currentIndex = 0
                            startPlaybackInternal(currentQueue[0])
                        } else {
                            currentIndex = -1
                            activePlayer.stop()
                            activePlayer.clearMediaItems()
                        }
                    } else {
                        maybePreloadAfterQueueChange()
                    }
                    updateState()
                    triggerUpcomingPrefetch()
                    persistQueue()
                }
            }
        }
    }

    private var isReplenishingQueue = false

    private suspend fun getEffectiveYouTubeVideoId(song: Song): String? {
        if (song.providerId == "youtube" && song.id.length == 11 && song.id.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
            return song.id
        }
        val provider = youTubeMetadataProvider ?: return null
        return try {
            val query = "${song.title} ${song.artist}".trim()
            val hits = provider.searchSongs(query, limit = 3)
            hits.firstOrNull()?.id
        } catch (e: Exception) {
            null
        }
    }

    private fun checkAndReplenishQueue() {
        if (isReplenishingQueue) return
        val currentSongIsLocal = currentSong?.providerId == "local" || (currentSong?.id?.startsWith("content://") == true)
        if (currentSongIsLocal) return // Local tracks stay strictly within the local user queue

        if (isExplicitQueue) {
            // Explicit playlist: only replenish when user reaches the very last track of the playlist
            if (currentIndex >= currentQueue.size - 1) {
                val seedSong = if (currentIndex in currentQueue.indices) currentQueue[currentIndex] else currentQueue.lastOrNull()
                if (seedSong != null) {
                    replenishInfiniteQueue(seedSong, count = 10)
                }
            }
        } else {
            // Single-track auto queue: maintain 10 upcoming tracks
            val upcomingCount = currentQueue.size - 1 - currentIndex
            if (upcomingCount < 10) {
                val seedSong = if (currentIndex in currentQueue.indices) currentQueue[currentIndex] else currentQueue.lastOrNull()
                if (seedSong != null) {
                    replenishInfiniteQueue(seedSong, count = 10 - upcomingCount)
                }
            }
        }
    }

    private fun replenishInfiniteQueue(seedSong: Song, count: Int = 10) {
        if (isReplenishingQueue) return
        isReplenishingQueue = true
        engineScope.launch(dispatchers.io) {
            try {
                val vibe = MusicVibeEngine.classify(seedSong)
                _currentVibe.value = vibe

                val allCandidates = mutableListOf<Song>()

                // 1. Same artist's and co-artists' greatest hits
                val individualArtists = seedSong.artist
                    .replace(Regex("""(?i)\b(feat\.|ft\.|featuring)\b.*"""), "")
                    .split(Regex("""(?i),\s*|\s+&\s+|\s*/\s*|;\s*|\s+and\s+|\s+with\s+"""))
                    .map { it.replace(Regex("""(?i)\b(official|audio|video|topic|singer|music)\b.*"""), "").trim() }
                    .filter { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) && !it.equals("Various Artists", ignoreCase = true) }
                    .distinct()

                for (artist in individualArtists.take(3)) {
                    val artistHits = youTubeMetadataProvider?.searchSongs("$artist Top Songs", limit = 4) ?: emptyList()
                    allCandidates.addAll(artistHits)
                }

                // 2. Fetch YouTube Music native Radio tracks via /next with resolved YouTube videoId
                val effectiveId = getEffectiveYouTubeVideoId(seedSong)
                if (effectiveId != null) {
                    val radioTracks = youTubeMetadataProvider?.fetchRadioSongs(effectiveId, limit = 20) ?: emptyList()
                    allCandidates.addAll(radioTracks)
                }

                // 3. Fetch contextual queries if needed
                if (allCandidates.size < 15 && youTubeMetadataProvider != null) {
                    val contextualQueries = MusicVibeEngine.getContextualSearchQueries(seedSong, vibe)
                    for (q in contextualQueries) {
                        if (allCandidates.size >= 25) break
                        val batch = youTubeMetadataProvider.searchSongs(q, limit = 10)
                        allCandidates.addAll(batch)
                    }
                }

                val ranked = MusicVibeEngine.rankAndFilterRecommendations(allCandidates, seedSong, vibe, limit = 15)
                val distinctNew = ranked.filter { candidate ->
                    candidate.id != seedSong.id &&
                    currentQueue.none {
                        it.id == candidate.id ||
                        (it.title.equals(candidate.title, ignoreCase = true) && it.artist.equals(candidate.artist, ignoreCase = true))
                    }
                }.take(count)

                if (distinctNew.isNotEmpty()) {
                    commandMutex.withLock {
                        currentQueue.addAll(distinctNew)
                        updateState()
                        triggerUpcomingPrefetch()
                        persistQueue()
                    }
                    LofiQLogger.d(LofiQLogger.Category.PLAYBACK, "Infinite Queue replenished +${distinctNew.size} tracks for '${seedSong.title}' (${vibe.displayTag})")
                }
            } catch (e: Exception) {
                LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "Infinite Queue replenish failed: ${e.message}")
            } finally {
                isReplenishingQueue = false
            }
        }
    }

    private suspend fun fetchAndPlayNextInfiniteTrack(retryCount: Int = 0) {
        val seedSong = currentSong ?: currentQueue.lastOrNull()
        val candidates = withContext(dispatchers.io) {
            try {
                // 1. Check pre-fetched smart recommendations first
                val cachedRecs = _smartRecommendations.value.filter { candidate ->
                    currentQueue.none {
                        it.id == candidate.id ||
                        (it.title.equals(candidate.title, ignoreCase = true) && it.artist.equals(candidate.artist, ignoreCase = true))
                    }
                }
                if (cachedRecs.isNotEmpty()) {
                    return@withContext cachedRecs
                }

                // 2. Query radio or contextual suggestions
                if (seedSong != null) {
                    val vibe = MusicVibeEngine.classify(seedSong)
                    _currentVibe.value = vibe
                    val effectiveId = getEffectiveYouTubeVideoId(seedSong)
                    val radio = if (effectiveId != null) {
                        youTubeMetadataProvider?.fetchRadioSongs(effectiveId, limit = 15) ?: emptyList()
                    } else emptyList()
                    val pool = if (radio.isNotEmpty()) radio else {
                        val q = MusicVibeEngine.getContextualSearchQueries(seedSong, vibe).firstOrNull() ?: "${seedSong.artist} Top Hits"
                        youTubeMetadataProvider?.searchSongs(q, limit = 15) ?: emptyList()
                    }
                    val ranked = MusicVibeEngine.rankAndFilterRecommendations(pool, seedSong, vibe, limit = 15)
                        .filter { candidate -> currentQueue.none { it.id == candidate.id } }
                    if (ranked.isNotEmpty()) {
                        ranked
                    } else {
                        // Strict fallback: Query curated language hits instead of returning unfiltered radio
                        val fallbackQuery = when (vibe.language) {
                            MusicLanguage.MALAYALAM -> "Malayalam Top Hits"
                            MusicLanguage.TAMIL -> "Tamil Top Hits"
                            MusicLanguage.HINDI -> "Top Bollywood Hits"
                            MusicLanguage.TELUGU -> "Top Telugu Hits"
                            MusicLanguage.PUNJABI -> "Punjabi Top Hits"
                            else -> "Top Trending Global Hits"
                        }
                        val fallbackBatch = youTubeMetadataProvider?.searchSongs(fallbackQuery, limit = 15) ?: emptyList()
                        MusicVibeEngine.rankAndFilterRecommendations(fallbackBatch, seedSong, vibe, limit = 15)
                            .filter { candidate -> currentQueue.none { it.id == candidate.id } }
                    }
                } else {
                    val lang = _currentVibe.value?.language
                    val genericQuery = when (lang) {
                        MusicLanguage.MALAYALAM -> "Malayalam Top Hits"
                        MusicLanguage.TAMIL -> "Tamil Top Hits"
                        MusicLanguage.HINDI -> "Top Bollywood Hits"
                        else -> "Top Trending Hits"
                    }
                    youTubeMetadataProvider?.searchSongs(genericQuery, limit = 15)
                        ?.filter { candidate -> currentQueue.none { it.id == candidate.id } }
                        ?: emptyList()
                }
            } catch (e: Exception) {
                LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "fetchAndPlayNextInfiniteTrack error: ${e.message}")
                emptyList()
            }
        }
        if (candidates.isNotEmpty()) {
            currentQueue.addAll(candidates)
            currentIndex++
            startPlayback(currentQueue[currentIndex], retryCount)
            triggerUpcomingPrefetch()
            persistQueue()
        } else if (currentQueue.isNotEmpty()) {
            // Never stop silently: repeat queue from start
            currentIndex = 0
            startPlayback(currentQueue[0], retryCount)
        }
    }

    private fun fetchSmartRecommendations(song: Song, autoAppend: Boolean = false, targetCount: Int = 10) {
        if (youTubeMetadataProvider == null) return
        engineScope.launch(dispatchers.io) {
            try {
                val vibe = MusicVibeEngine.classify(song)
                _currentVibe.value = vibe
                LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "Classified '${song.title}' as vibe: ${vibe.displayTag}")

                val accumulatedCandidates = mutableListOf<Song>()

                // 1. Same artist's and co-artists' greatest hits
                val individualArtists = song.artist
                    .replace(Regex("""(?i)\b(feat\.|ft\.|featuring)\b.*"""), "")
                    .split(Regex("""(?i),\s*|\s+&\s+|\s*/\s*|;\s*|\s+and\s+|\s+with\s+"""))
                    .map { it.replace(Regex("""(?i)\b(official|audio|video|topic|singer|music)\b.*"""), "").trim() }
                    .filter { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) && !it.equals("Various Artists", ignoreCase = true) }
                    .distinct()

                for (artist in individualArtists.take(3)) {
                    val artistHits = youTubeMetadataProvider.searchSongs("$artist Top Songs", limit = 4)
                    accumulatedCandidates.addAll(artistHits)
                }

                // 2. Fetch YouTube Music native Radio tracks via /next
                val effectiveId = getEffectiveYouTubeVideoId(song)
                if (effectiveId != null) {
                    val radioResults = youTubeMetadataProvider.fetchRadioSongs(effectiveId, limit = 20)
                    accumulatedCandidates.addAll(radioResults)
                }

                // 3. Supplement with contextual searches matching language and vibe
                val contextualQueries = MusicVibeEngine.getContextualSearchQueries(song, vibe)
                for (query in contextualQueries) {
                    if (accumulatedCandidates.size >= 25) break
                    val batch = youTubeMetadataProvider.searchSongs(query, limit = 10)
                    accumulatedCandidates.addAll(batch)
                }

                // 4. Intelligently score, rank, and filter with vibe and language affinity
                val ranked = MusicVibeEngine.rankAndFilterRecommendations(accumulatedCandidates, song, vibe, limit = 15)
                _smartRecommendations.value = ranked

                commandMutex.withLock {
                    if (autoAppend && !isExplicitQueue) {
                        val upcomingCount = (currentQueue.size - 1 - currentIndex).coerceAtLeast(0)
                        val needed = (targetCount - upcomingCount).coerceAtLeast(0)
                        if (needed > 0) {
                            val toAdd = ranked.filter { candidate ->
                                candidate.id != song.id &&
                                currentQueue.none {
                                    it.id == candidate.id ||
                                    (it.title.equals(candidate.title, ignoreCase = true) && it.artist.equals(candidate.artist, ignoreCase = true))
                                }
                            }.take(needed)
                            if (toAdd.isNotEmpty()) {
                                currentQueue.addAll(toAdd)
                                updateState()
                                triggerUpcomingPrefetch()
                                persistQueue()
                                LofiQLogger.d(LofiQLogger.Category.PLAYBACK, "Auto-populated ${toAdd.size} tracks into UP NEXT queue for '${song.title}'")
                            }
                        }
                    } else if (isExplicitQueue && currentIndex >= currentQueue.size - 1) {
                        val toAdd = ranked.filter { candidate ->
                            candidate.id != song.id && currentQueue.none { it.id == candidate.id }
                        }.take(targetCount)
                        if (toAdd.isNotEmpty()) {
                            currentQueue.addAll(toAdd)
                            updateState()
                            triggerUpcomingPrefetch()
                            persistQueue()
                            LofiQLogger.d(LofiQLogger.Category.PLAYBACK, "Appended ${toAdd.size} tracks at end of playlist for '${song.title}'")
                        }
                    }
                }
            } catch (e: Exception) {
                LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "Smart queue fetch failed: ${e.message}")
            }
        }
    }

    private fun fetchLyricsForSong(song: Song) {
        if (lyricsRepository == null) return
        lyricsJob?.cancel()
        lyricsJob = engineScope.launch(dispatchers.io) {
            _isLyricsLoading.value = true
            _currentLyrics.value = null
            try {
                val lyrics = lyricsRepository.getLyrics(song)
                if (currentSong?.id == song.id) {
                    _currentLyrics.value = lyrics
                }
            } catch (e: Exception) {
                LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "Lyrics fetch error: ${e.message}")
            } finally {
                _isLyricsLoading.value = false
            }
        }
    }

    fun refreshLyrics() {
        val song = currentSong ?: return
        lyricsRepository?.clearCache()
        fetchLyricsForSong(song)
    }

    private fun triggerUpcomingPrefetch() {
        if (currentIndex < 0 || currentIndex >= currentQueue.size) return
        val upcoming = currentQueue.drop(currentIndex + 1).take(3).filter {
            it.providerId != "local" && it.providerId != "local_download" && !it.id.startsWith("content://")
        }
        if (upcoming.isNotEmpty()) {
            val quality = networkQualityMonitor.getEffectiveQuality(userAudioQuality)
            prefetchManager.prefetchNextTracks(upcoming, quality)

            // Warm up Coil image cache in background for smooth instant metadata rendering
            engineScope.launch(dispatchers.io) {
                for (nextSong in upcoming) {
                    val artworkUrl = nextSong.highResArtworkUrl.ifEmpty { nextSong.artworkUrl }
                    if (artworkUrl.isNotBlank()) {
                        runCatching {
                            val req = coil.request.ImageRequest.Builder(context)
                                .data(artworkUrl)
                                .build()
                            coil.Coil.imageLoader(context).enqueue(req)
                        }
                    }
                }
            }
        }
    }

    private fun getNextSong(): Song? {
        if (currentQueue.isEmpty()) return null
        if (_playerState.value.repeatMode == PlaybackRepeatMode.ONE) return null
        if (currentIndex < currentQueue.size - 1) {
            return currentQueue[currentIndex + 1]
        }
        if (_playerState.value.repeatMode == PlaybackRepeatMode.ALL) {
            return currentQueue.firstOrNull()
        }
        return null
    }

    private suspend fun resolveMediaItemForSong(
        song: Song,
        reqId: String = java.util.UUID.randomUUID().toString().take(8),
        retryCount: Int = 0,
        qualityOverride: AudioQuality? = null
    ): Pair<MediaItem, String> = withContext(dispatchers.io) {
        val isLocalDeviceTrack = song.providerId == "local" ||
                song.id.startsWith("content://") ||
                song.id.startsWith("file://") ||
                song.sourceUrl.startsWith("content://") ||
                song.sourceUrl.startsWith("file://") ||
                (song.providerId != "local_download" && song.sourceUrl.isNotBlank() && File(song.sourceUrl).exists())

        val lofiqDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "LofiQ")
        val cleanTitle = song.title.replace(Regex("""[\\/:*?"<>|]"""), "_").replace(Regex("""\s+"""), " ").trim()
        val cleanArtist = song.artist.replace(Regex("""[\\/:*?"<>|]"""), "_").replace(Regex("""\s+"""), " ").trim()
        val lofiqFile = File(lofiqDir, "$cleanTitle - $cleanArtist.m4a")

        val downloadDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "downloads")
        val downloadFile = File(downloadDir, "${song.id.replace(Regex("""[^a-zA-Z0-9_-]"""), "_")}.m4a")

        val isOfflineDownload = song.providerId == "local_download" ||
                (song.sourceUrl.isNotBlank() && (song.sourceUrl.startsWith("content://") || (File(song.sourceUrl).exists() && File(song.sourceUrl).length() > 10_000L))) ||
                (lofiqFile.exists() && lofiqFile.length() > 10_000L) ||
                (downloadFile.exists() && downloadFile.length() > 10_000L)

        val playUri: Uri
        val streamMimeType: String?
        val resolvedProvider: String

        if (isLocalDeviceTrack) {
            LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[PLAYBACK][id=$reqId] Resolving LOCAL DEVICE track: '${song.title}'")
            val candidateUri = when {
                song.id.startsWith("content://") -> song.id
                song.sourceUrl.startsWith("content://") -> song.sourceUrl
                song.sourceUrl.startsWith("file://") -> song.sourceUrl
                song.id.startsWith("file://") -> song.id
                song.sourceUrl.isNotBlank() && File(song.sourceUrl).exists() -> Uri.fromFile(File(song.sourceUrl)).toString()
                else -> song.sourceUrl.ifBlank { song.id }
            }
            playUri = if (candidateUri.startsWith("content://") || candidateUri.startsWith("file://")) {
                Uri.parse(candidateUri)
            } else {
                Uri.fromFile(File(candidateUri))
            }
            streamMimeType = null
            resolvedProvider = "local"
        } else if (isOfflineDownload) {
            playUri = when {
                song.sourceUrl.startsWith("content://") || song.sourceUrl.startsWith("file://") -> {
                    Uri.parse(song.sourceUrl)
                }
                song.sourceUrl.isNotBlank() && File(song.sourceUrl).exists() && File(song.sourceUrl).length() > 10_000L -> {
                    Uri.fromFile(File(song.sourceUrl))
                }
                lofiqFile.exists() && lofiqFile.length() > 10_000L -> {
                    Uri.fromFile(lofiqFile)
                }
                else -> {
                    Uri.fromFile(downloadFile)
                }
            }
            LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[PLAYBACK][id=$reqId] Resolving OFFLINE DOWNLOAD track: '${song.title}' uri=$playUri")
            streamMimeType = androidx.media3.common.MimeTypes.AUDIO_MP4
            resolvedProvider = "local_download"
        } else {
            LofiQLogger.i(LofiQLogger.Category.RESOLVER, "[RESOLVER][id=$reqId] Resolving stream for '${song.title}' [id: ${song.id}]")
            val cleanArtistName = song.artist
                .replace(Regex("""\d{1,2}:\d{2}(:\d{2})?$"""), "")
                .replace(Regex("""(?i)\(Original Motion Picture Soundtrack\)|\[Original Motion Picture Soundtrack\]"""), "")
                .replace(Regex("""(?i)\(From ".*?"\)|\(From '.*?'\)"""), "")
                .trim()
                .ifBlank { song.artist }

            val effectiveQuality = qualityOverride ?: networkQualityMonitor.getEffectiveQuality(userAudioQuality)
            val request = ResolveRequest(
                songId = song.id,
                songTitle = song.title,
                artistName = cleanArtistName,
                albumName = song.album,
                durationSeconds = song.durationSeconds,
                preferredQuality = effectiveQuality,
                providerHint = if (retryCount > 0) "youtube" else null
            )
            val resolution = resolverManager.resolve(request)
            LofiQLogger.i(
                LofiQLogger.Category.RESOLVER,
                "[RESOLVER][id=$reqId] SUCCESS: mimeType=${resolution.mimeType}, bitrate=${resolution.bitrateKbps}kbps, provider=${resolution.providerId}"
            )
            playUri = Uri.parse(resolution.streamUrl)
            streamMimeType = when {
                resolution.mimeType.contains("webm", ignoreCase = true) -> androidx.media3.common.MimeTypes.AUDIO_WEBM
                resolution.mimeType.contains("ogg", ignoreCase = true) || resolution.mimeType.contains("opus", ignoreCase = true) -> androidx.media3.common.MimeTypes.AUDIO_OGG
                else -> null
            }
            resolvedProvider = resolution.providerId
        }

        val artworkUrl = song.highResArtworkUrl.ifEmpty { song.artworkUrl }
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(song.title)
            .setDisplayTitle(song.title)
            .setArtist(song.artist)
            .setSubtitle(song.artist)
            .setDescription(song.album)
            .setAlbumTitle(song.album)
            .setAlbumArtist(song.artist)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setIsPlayable(true)

        if (artworkUrl.isNotEmpty()) {
            metadataBuilder.setArtworkUri(Uri.parse(artworkUrl))
        }
        val mediaMetadata = metadataBuilder.build()

        val mediaItemBuilder = MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(playUri)
            .setMediaMetadata(mediaMetadata)

        if (streamMimeType != null) {
            mediaItemBuilder.setMimeType(streamMimeType)
        }

        Pair(mediaItemBuilder.build(), resolvedProvider)
    }

    private fun preloadNextTrack() {
        val nextSong = getNextSong() ?: return
        if (isTransitioning || failedPreloadForTrackIds.contains(nextSong.id)) {
            return
        }
        if (secondarySongId == nextSong.id &&
            (secondarySlotState == SecondarySlotState.READY || secondarySlotState == SecondarySlotState.PREPARING)
        ) {
            return
        }
        if (inFlightPreloadSongId == nextSong.id) {
            return
        }

        preloadJob?.cancel()
        val targetEpoch = ++preloadEpoch
        inFlightPreloadSongId = nextSong.id
        secondarySlotState = SecondarySlotState.RESOLVING
        secondarySong = nextSong
        secondarySongId = nextSong.id
        secondaryPreparedMediaItem = null
        secondaryArtworkBytes = null

        preloadJob = engineScope.launch(dispatchers.io) {
            try {
                LofiQLogger.i(
                    LofiQLogger.Category.PREFETCH,
                    "[PRELOAD] Resolving next track '${nextSong.title}' [id=${nextSong.id}] for secondary slot (epoch=$targetEpoch)"
                )
                val (mediaItem, resolvedProvider) = resolveMediaItemForSong(
                    song = nextSong,
                    reqId = "pre-${nextSong.id.take(6)}"
                )

                var cachedBytes: ByteArray? = null
                val artworkUrl = com.lofiq.core.common.ArtworkUtils.getOptimalArtwork(nextSong)
                if (artworkUrl.isNotBlank()) {
                    runCatching {
                        val loader = coil.Coil.imageLoader(context)
                        val req = coil.request.ImageRequest.Builder(context)
                            .data(artworkUrl)
                            .size(500, 500)
                            .allowHardware(false)
                            .build()
                        val result = loader.execute(req)
                        val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                        if (bitmap != null) {
                            val stream = java.io.ByteArrayOutputStream()
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, stream)
                            cachedBytes = stream.toByteArray()
                        }
                    }
                }

                withContext(dispatchers.main) {
                    if (!isActive || targetEpoch != preloadEpoch || isTransitioning || getNextSong()?.id != nextSong.id) {
                        return@withContext
                    }
                    val nextSongIndex = findTargetIndexForSong(nextSong.id)
                    if (nextSongIndex in currentQueue.indices && currentQueue[nextSongIndex].id == nextSong.id) {
                        currentQueue[nextSongIndex] = currentQueue[nextSongIndex].copy(providerId = resolvedProvider)
                    }

                    val sec = secondaryPlayer
                    secondaryArtworkBytes = cachedBytes
                    secondaryPreparedMediaItem = mediaItem
                    secondarySlotState = SecondarySlotState.PREPARING
                    inFlightPreloadSongId = null

                    // Strict silent secondary preparation: volume=0%, paused at pos 0, never request audio focus
                    sec.volume = 0f
                    sec.playWhenReady = false
                    sec.setAudioAttributes(mediaAudioAttributes, false)
                    sec.repeatMode = Player.REPEAT_MODE_OFF
                    sec.setMediaItem(mediaItem, /* startPositionMs = */ 0L)
                    sec.prepare()
                    if (sec.playbackState == Player.STATE_READY) {
                        secondarySlotState = SecondarySlotState.READY
                    }
                    LofiQLogger.i(
                        LofiQLogger.Category.PREFETCH,
                        "[SECONDARY_PLAYER] Loaded '${nextSong.title}' into slot ${if (isSlotAActive) "B" else "A"} (state=$secondarySlotState, vol=0%, playWhenReady=false)"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(dispatchers.main) {
                    if (targetEpoch == preloadEpoch) {
                        failedPreloadForTrackIds.add(nextSong.id)
                        invalidateSecondaryPreload("preloadFailed:${e.message}")
                    }
                }
                LofiQLogger.w(
                    LofiQLogger.Category.PLAYBACK,
                    "[PLAYBACK_RECOVERY] [PRELOAD] Isolated failure preloading '${nextSong.title}': ${e.message}. Active track undisturbed."
                )
            }
        }
    }

    private fun startPlayback(song: Song, retryCount: Int = 0) {
        startPlaybackInternal(song, retryCount)
    }

    private fun startPlaybackInternal(song: Song, retryCount: Int = 0) {
        cancelFadeAndRestoreVolume(clearPreload = true)
        playbackJob?.cancel()
        failedPreloadForTrackIds.clear()
        playbackJob = engineScope.launch {
            loadAndPlaySong(song, retryCount)
        }
    }

    private suspend fun loadAndPlaySong(song: Song, retryCount: Int = 0) {
        val reqId = java.util.UUID.randomUUID().toString().take(8)
        activePlaybackId = reqId

        LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[PLAYBACK][id=$reqId] PLAY_REQUEST for '${song.title}' [id: ${song.id}] (attempt=${retryCount + 1})")

        val isLocalDeviceTrack = song.providerId == "local" ||
                song.id.startsWith("content://") ||
                song.id.startsWith("file://") ||
                song.sourceUrl.startsWith("content://") ||
                song.sourceUrl.startsWith("file://") ||
                (song.providerId != "local_download" && song.sourceUrl.isNotBlank() && File(song.sourceUrl).exists())

        if (!isLocalDeviceTrack) {
            fetchSmartRecommendations(song)
            fetchLyricsForSong(song)
            triggerUpcomingPrefetch()
        }

        _playerState.update {
            it.copy(
                currentSong = song,
                activeProviderId = song.providerId,
                isBuffering = true,
                isPlaying = true,
                isCrossfading = false,
                errorMessage = null,
                currentPositionMs = 0L,
                durationMs = song.durationMs
            )
        }
        updateState()

        try {
            val (mediaItem, activeProvider) = resolveMediaItemForSong(song, reqId, retryCount)

            if (currentIndex in currentQueue.indices) {
                currentQueue[currentIndex] = song.copy(providerId = activeProvider)
            }

            _playerState.update {
                it.copy(
                    activeProviderId = activeProvider,
                    currentSong = (it.currentSong ?: song).copy(providerId = activeProvider)
                )
            }

            withContext(dispatchers.main) {
                runCatching {
                    val serviceIntent = Intent(context, LofiQMediaSessionService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
                val active = activePlayer
                LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[MEDIA3][id=$reqId][slot=${if (isSlotAActive) "A" else "B"}] PREPARE & PLAY")
                active.volume = masterVolumeScale
                active.setAudioAttributes(mediaAudioAttributes, true)
                active.repeatMode = Player.REPEAT_MODE_OFF
                active.playWhenReady = true
                active.setMediaItem(mediaItem, /* resetPosition = */ true)
                active.prepare()
                active.play()
            }

            val artworkUrl = song.highResArtworkUrl.ifEmpty { song.artworkUrl }
            if (artworkUrl.isNotEmpty()) {
                engineScope.launch(dispatchers.io) {
                    runCatching {
                        val loader = coil.Coil.imageLoader(context)
                        val req = coil.request.ImageRequest.Builder(context)
                            .data(artworkUrl)
                            .allowHardware(false)
                            .build()
                        val result = loader.execute(req)
                        val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                        if (bitmap != null && activePlaybackId == reqId) {
                            val stream = java.io.ByteArrayOutputStream()
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, stream)
                            val bytes = stream.toByteArray()
                            withContext(dispatchers.main) {
                                runCatching {
                                    val active = activePlayer
                                    if (activePlaybackId == reqId && active.mediaItemCount > 0 && active.currentMediaItem?.mediaId == song.id) {
                                        val updatedMetadata = mediaItem.mediaMetadata.buildUpon()
                                            .setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                                            .build()
                                        active.playlistMetadata = updatedMetadata
                                    }
                                }
                            }
                        }
                    }
                }
            }

            withContext(dispatchers.io) {
                val lofiqDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "LofiQ")
                val cleanTitle = song.title.replace(Regex("""[\\/:*?"<>|]"""), "_").replace(Regex("""\s+"""), " ").trim()
                val cleanArtist = song.artist.replace(Regex("""[\\/:*?"<>|]"""), "_").replace(Regex("""\s+"""), " ").trim()
                val lofiqFile = File(lofiqDir, "$cleanTitle - $cleanArtist.m4a")
                val downloadDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "downloads")
                val downloadFile = File(downloadDir, "${song.id.replace(Regex("""[^a-zA-Z0-9_-]"""), "_")}.m4a")

                val existing = songDao.getById(song.id)
                val isOfflineDownload = song.providerId == "local_download" ||
                        existing?.providerId == "local_download" ||
                        (song.sourceUrl.isNotBlank() && (song.sourceUrl.startsWith("content://") || (File(song.sourceUrl).exists() && File(song.sourceUrl).length() > 10_000L))) ||
                        (lofiqFile.exists() && lofiqFile.length() > 10_000L) ||
                        (downloadFile.exists() && downloadFile.length() > 10_000L)

                val effectiveProviderId = if (isOfflineDownload) "local_download" else song.providerId
                val effectiveSourceUrl = when {
                    isOfflineDownload && lofiqFile.exists() && lofiqFile.length() > 10_000L -> lofiqFile.absolutePath
                    isOfflineDownload && downloadFile.exists() && downloadFile.length() > 10_000L -> downloadFile.absolutePath
                    isOfflineDownload && existing?.sourceUrl?.isNotBlank() == true -> existing.sourceUrl
                    isOfflineDownload && song.sourceUrl.isNotBlank() -> song.sourceUrl
                    isOfflineDownload || isLocalDeviceTrack -> (song.sourceUrl.ifBlank { mediaItem.localConfiguration?.uri.toString() })
                    else -> mediaItem.localConfiguration?.uri.toString()
                }

                songDao.insertOrUpdate(
                    SongEntity(
                        id = song.id,
                        title = song.title,
                        artist = song.artist,
                        album = song.album,
                        durationSeconds = song.durationSeconds,
                        artworkUrl = song.artworkUrl.ifBlank { existing?.artworkUrl.orEmpty() },
                        highResArtworkUrl = song.highResArtworkUrl.ifBlank { existing?.highResArtworkUrl.orEmpty() },
                        sourceUrl = effectiveSourceUrl,
                        providerId = effectiveProviderId,
                        quality = song.quality.name,
                        addedAt = existing?.addedAt ?: System.currentTimeMillis()
                    )
                )
                songDao.incrementPlayCount(song.id)
                historyDao.recordHistory(
                    PlaybackHistoryEntity(
                        songId = song.id,
                        playedAt = System.currentTimeMillis(),
                        durationPlayedSeconds = 0
                    )
                )
            }

            consecutiveFailures = 0
            triggerUpcomingPrefetch()

        } catch (e: CancellationException) {
            LofiQLogger.d(LofiQLogger.Category.PLAYBACK, "[PLAYBACK][id=$reqId] Playback job cancelled for '${song.title}'")
            throw e
        } catch (e: Exception) {
            LofiQLogger.e(LofiQLogger.Category.PLAYBACK, "[PLAYBACK][id=$reqId] FAILED: ${e.message}", e)
            consecutiveFailures++

            engineScope.launch(dispatchers.io) {
                resolutionCache.invalidate("${song.id}_${song.quality.name}")
            }

            if (retryCount < 3 && currentIndex < currentQueue.size - 1) {
                LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[AUTO-SKIP] Skipping unplayable track '${song.title}' to next track")
                currentIndex++
                loadAndPlaySong(currentQueue[currentIndex], retryCount + 1)
                checkAndReplenishQueue()
                return
            } else if (retryCount < 3 && _isSmartAutoplay.value) {
                LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[AUTO-SKIP] Skipping unplayable track '${song.title}' to infinite track")
                fetchAndPlayNextInfiniteTrack(retryCount + 1)
                return
            }

            _playerState.update {
                it.copy(
                    isBuffering = false,
                    isPlaying = false,
                    isReady = false,
                    errorMessage = "Unable to play '${song.title}': ${e.localizedMessage ?: "Source unavailable"}"
                )
            }
        }
        updateState()
    }

    private fun handleTrackEnded() {
        engineScope.launch {
            commandMutex.withLock {
                if (isTransitioning) {
                    // Crossfade coroutine finishes and hands over smoothly; avoid double advance
                    return@withLock
                }
                val active = activePlayer
                val pos = active.currentPosition.coerceAtLeast(0L)
                val curSong = currentSong
                val expectedDur = curSong?.durationMs ?: 0L
                val isPrematureEnd = expectedDur > 30_000L && (pos < 20_000L || (pos < expectedDur - 15_000L && pos < (expectedDur * 0.85f).toLong()))

                if (isPrematureEnd && curSong != null && currentSongRetryCount < 2) {
                    currentSongRetryCount++
                    LofiQLogger.w(
                        LofiQLogger.Category.PLAYBACK,
                        "[PLAYBACK_RECOVERY] Premature EOF detected at ${pos}ms for '${curSong.title}' (expected ${expectedDur}ms). Recovering active player at current pos (attempt $currentSongRetryCount/2)."
                    )
                    active.prepare()
                    if (pos > 0) {
                        active.seekTo(pos)
                    }
                    active.play()
                    return@withLock
                }

                LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[TRACK_ENDED] Finished current track [index=$currentIndex / size=${currentQueue.size}] at ${pos}ms")
                if (_playerState.value.repeatMode == PlaybackRepeatMode.ONE) {
                    active.seekTo(0L)
                    active.volume = masterVolumeScale
                    active.playWhenReady = true
                    active.prepare()
                    active.play()
                    return@withLock
                }

                val nextSong = getNextSong()
                val sec = secondaryPlayer
                if (nextSong != null &&
                    secondarySongId == nextSong.id &&
                    sec.mediaItemCount > 0 &&
                    sec.playbackState == Player.STATE_READY
                ) {
                    LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[GAPLESS] Track ended -> immediate gapless handover to preloaded '${nextSong.title}'")
                    if (sec.currentPosition > 50L) {
                        sec.seekTo(0L)
                    }
                    sec.volume = masterVolumeScale
                    sec.playWhenReady = true
                    sec.play()
                    performPlayerHandoverLocked(expectedIncomingSong = nextSong, isGapless = true)
                    return@withLock
                }

                if (priorityQueueOffset > 0) priorityQueueOffset--
                if (currentIndex < currentQueue.size - 1) {
                    if (!isExplicitQueue) {
                        val upcoming = currentQueue[currentIndex + 1]
                        val remaining = currentQueue.drop(currentIndex + 1)
                        currentQueue.clear()
                        currentQueue.addAll(remaining)
                        currentIndex = 0
                        startPlaybackInternal(upcoming)
                        checkAndReplenishQueue()
                    } else {
                        currentIndex++
                        startPlaybackInternal(currentQueue[currentIndex])
                        checkAndReplenishQueue()
                    }
                } else if (_playerState.value.repeatMode == PlaybackRepeatMode.ALL && currentQueue.isNotEmpty()) {
                    currentIndex = 0
                    startPlaybackInternal(currentQueue[0])
                } else {
                    val currentSongIsLocal = currentSong?.providerId == "local" || (currentSong?.id?.startsWith("content://") == true)
                    if (currentSongIsLocal) {
                        _playerState.update { it.copy(isPlaying = false, isBuffering = false) }
                    } else {
                        fetchAndPlayNextInfiniteTrack()
                    }
                }
            }
        }
    }

    private fun updateState() {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            updateStateDirect()
        } else {
            engineScope.launch(dispatchers.main) {
                updateStateDirect()
            }
        }
    }

    private fun updateStateDirect() {
        val active = activePlayer
        val isExoPlaying = active.isPlaying
        val isBuffering = active.playbackState == Player.STATE_BUFFERING || playbackJob?.isActive == true
        val isReady = active.playbackState == Player.STATE_READY
        val current = if (currentIndex in currentQueue.indices) currentQueue[currentIndex] else null
        val isCurrentInPlayer = active.currentMediaItem?.mediaId == current?.id
        val pos = if (isCurrentInPlayer) active.currentPosition.coerceAtLeast(0L) else 0L
        val dur = if (isCurrentInPlayer && active.duration > 0) active.duration else current?.durationMs ?: 0L
        val sessionId = active.audioSessionId

        _playerState.update {
            val effectivePlaying = if (playbackJob?.isActive == true) true else isExoPlaying
            it.copy(
                currentSong = current ?: it.currentSong,
                isPlaying = effectivePlaying,
                isBuffering = isBuffering,
                isReady = isReady,
                isCrossfading = isTransitioning,
                currentPositionMs = pos,
                durationMs = if (dur > 0) dur else current?.durationMs ?: it.durationMs,
                queue = currentQueue.toList(),
                currentQueueIndex = currentIndex,
                activeAudioSessionId = if (sessionId != 0) sessionId else it.activeAudioSessionId
            )
        }
        runCatching {
            com.lofiq.widget.MusicWidgetManager.updateAllWidgets(context)
        }
    }

    private suspend fun persistQueue() = withContext(dispatchers.io) {
        val snapshot = currentQueue.mapIndexed { index, song ->
            QueueSnapshotEntity(
                songId = song.id,
                orderIndex = index,
                isCurrent = index == currentIndex
            )
        }
        queueSnapshotDao.replaceQueue(snapshot)
    }

    val currentSong: Song?
        get() = if (currentIndex in currentQueue.indices) currentQueue[currentIndex] else null

    fun release() {
        positionJob?.cancel()
        playbackJob?.cancel()
        preloadJob?.cancel()
        volumeFadeJob?.cancel()
        lyricsJob?.cancel()
        engineScope.cancel()
    }
}

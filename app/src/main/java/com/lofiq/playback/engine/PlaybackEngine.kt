package com.lofiq.playback.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.lofiq.playback.service.LofiQMediaSessionService
import java.io.File
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
    val exoPlayer: ExoPlayer,
    @com.lofiq.di.PlaybackModule.FadePlayer private val fadePlayer: ExoPlayer,
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
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        LofiQLogger.e(LofiQLogger.Category.PLAYBACK, "Unhandled playback coroutine error: ${throwable.message}", throwable)
    }
    private val engineScope = CoroutineScope(SupervisorJob() + dispatchers.main + exceptionHandler)
    private val commandMutex = Mutex()

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
    private var preloadedSongId: String? = null
    private var activePlaybackId: String = ""
    private var consecutiveFailures = 0
    private var currentSongRetryCount = 0
    private var crossfadeSeconds: Int = 8
    private var volumeFadeJob: Job? = null
    private var userAudioQuality: AudioQuality = AudioQuality.AUTO
    private var fadePlayerPreparedSongId: String? = null
    private var crossfadeActiveForSongId: String? = null

    private fun cancelFadeAndRestoreVolume() {
        volumeFadeJob?.cancel()
        volumeFadeJob = null
        fadePlayerPreparedSongId = null
        crossfadeActiveForSongId = null
        runCatching {
            if (fadePlayer.isPlaying || fadePlayer.playbackState != Player.STATE_IDLE) {
                fadePlayer.stop()
                fadePlayer.clearMediaItems()
            }
        }
        if (exoPlayer.volume != 1f) {
            exoPlayer.volume = 1f
        }
        if (exoPlayer.mediaItemCount > 1 && exoPlayer.currentMediaItemIndex == 1) {
            exoPlayer.removeMediaItem(0)
        }
    }

    init {
        setupPlayerListener()
        observePreferences()
        startPositionTracker()
    }

    private fun setupPlayerListener() {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[MEDIA3][id=$activePlaybackId] IS_PLAYING=$isPlaying")
                updateState()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN"
                }
                LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[MEDIA3][id=$activePlaybackId] STATE=$stateName")

                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        _playerState.update { it.copy(isBuffering = true) }
                    }
                    Player.STATE_READY -> {
                        consecutiveFailures = 0
                        currentSongRetryCount = 0
                        _playerState.update { it.copy(isBuffering = false, isReady = true, errorMessage = null) }
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

                LofiQLogger.e(
                    LofiQLogger.Category.PLAYBACK,
                    "[MEDIA3][id=$activePlaybackId] ERROR: code=$errorCode ($errorCodeName), msg=$errorDetails, cause=$causeMsg",
                    error
                )

                // 1. AudioTrack sink failure (common during Bluetooth connection or codec renegotiation)
                if (errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
                    errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED) {
                    val curPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                    LofiQLogger.w(
                        LofiQLogger.Category.PLAYBACK,
                        "[MEDIA3] AudioTrack output error (Bluetooth/routing). Re-preparing player at position ${curPos}ms."
                    )
                    exoPlayer.prepare()
                    if (curPos > 0) {
                        exoPlayer.seekTo(curPos)
                    }
                    exoPlayer.play()
                    return
                }

                // 2. Protect active playback: if a preloaded background item at index 1 failed while track at index 0 is playing
                if (exoPlayer.mediaItemCount > 1 && exoPlayer.currentMediaItemIndex == 0 && exoPlayer.currentPosition > 2_000L) {
                    LofiQLogger.w(
                        LofiQLogger.Category.PLAYBACK,
                        "[PRELOAD] Error during background track preloading. Removing preloaded item to prevent skipping active track '${currentSong?.title}'."
                    )
                    runCatching {
                        exoPlayer.removeMediaItem(1)
                        preloadedSongId = null
                    }
                    val curPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                    exoPlayer.prepare()
                    if (curPos > 0) {
                        exoPlayer.seekTo(curPos)
                    }
                    exoPlayer.play()
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
                                LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[LOCAL-PLAYER] Advancing to next track after error on '${current.title}'")
                                startPlayback(currentQueue[currentIndex], retryCount = 0)
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

                // 3. First attempt recovery by resolving alternative stream for the SAME track
                if (currentSongRetryCount < 2 && current != null) {
                    currentSongRetryCount++
                    LofiQLogger.w(
                        LofiQLogger.Category.PLAYBACK,
                        "[AUTO-RECOVERY] Retrying same track '${current.title}' with fallback stream (attempt=${currentSongRetryCount + 1})"
                    )
                    startPlayback(current, retryCount = currentSongRetryCount)
                    return
                }

                // 4. If current track retries are exhausted, advance to next track
                engineScope.launch {
                    commandMutex.withLock {
                        if (currentIndex < currentQueue.size - 1 && consecutiveFailures < 3) {
                            currentIndex++
                            LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[AUTO-RECOVERY] Advancing to next track after player error on '${current?.title}'")
                            startPlayback(currentQueue[currentIndex], retryCount = 0)
                            checkAndReplenishQueue()
                        } else if (_isSmartAutoplay.value && consecutiveFailures < 3) {
                            LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[AUTO-RECOVERY] Fetching infinite track after player error")
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

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val reasonName = when (reason) {
                    Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "AUTO"
                    Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "SEEK"
                    Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "REPEAT"
                    Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "PLAYLIST_CHANGED"
                    else -> "UNKNOWN"
                }
                LofiQLogger.i(
                    LofiQLogger.Category.PLAYBACK,
                    "[MEDIA3][id=$activePlaybackId] MEDIA_ITEM_TRANSITION: mediaId=${mediaItem?.mediaId}, reason=$reasonName"
                )
                if (mediaItem != null) {
                    handleMediaItemTransition(mediaItem, reason)
                }
            }
        })

        fadePlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                LofiQLogger.w(
                    LofiQLogger.Category.PLAYBACK,
                    "[CROSSFADE] FadePlayer encountered error: ${error.message}"
                )
                runCatching {
                    fadePlayer.stop()
                    fadePlayer.clearMediaItems()
                }
                fadePlayerPreparedSongId = null
            }
        })
    }

    private fun observePreferences() {
        engineScope.launch {
            preferences.repeatMode.collect { mode ->
                _playerState.update { it.copy(repeatMode = mode) }
                when (mode) {
                    PlaybackRepeatMode.OFF -> {
                        exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
                        preloadNextTrack()
                    }
                    PlaybackRepeatMode.ALL -> {
                        exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
                        preloadNextTrack()
                    }
                    PlaybackRepeatMode.ONE -> {
                        exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
                        val currentPlayingIndex = exoPlayer.currentMediaItemIndex
                        while (exoPlayer.mediaItemCount > currentPlayingIndex + 1) {
                            exoPlayer.removeMediaItem(exoPlayer.mediaItemCount - 1)
                        }
                        preloadedSongId = null
                    }
                }
            }
        }
        engineScope.launch {
            preferences.shuffleEnabled.collect { isShuffle ->
                _playerState.update { it.copy(isShuffleEnabled = isShuffle) }
                exoPlayer.shuffleModeEnabled = isShuffle
            }
        }
        engineScope.launch {
            preferences.crossfadeDurationSec.collect { sec ->
                crossfadeSeconds = sec.coerceIn(0, 12)
            }
        }
        engineScope.launch {
            preferences.audioQuality.collect { quality ->
                val previous = userAudioQuality
                userAudioQuality = quality
                if (previous != quality && currentSong != null && (exoPlayer.isPlaying || exoPlayer.playWhenReady)) {
                    handleDynamicQualityChange(quality)
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

        val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
        val wasPlaying = exoPlayer.isPlaying
        val effectiveQuality = networkQualityMonitor.getEffectiveQuality(newQuality)

        LofiQLogger.i(
            LofiQLogger.Category.PLAYBACK,
            "[QUALITY_SWITCH] Switching active playback stream to $newQuality (effective=$effectiveQuality) at ${currentPos}ms"
        )

        engineScope.launch {
            withContext(dispatchers.main) {
                val currentPlayingIndex = exoPlayer.currentMediaItemIndex
                while (exoPlayer.mediaItemCount > currentPlayingIndex + 1) {
                    exoPlayer.removeMediaItem(exoPlayer.mediaItemCount - 1)
                }
                preloadedSongId = null
            }

            try {
                val (mediaItem, provider) = resolveMediaItemForSong(
                    song = song,
                    qualityOverride = effectiveQuality
                )
                withContext(dispatchers.main) {
                    if (currentSong?.id == song.id) {
                        exoPlayer.setMediaItem(mediaItem, currentPos)
                        exoPlayer.prepare()
                        if (wasPlaying) {
                            exoPlayer.play()
                        }
                        _playerState.update {
                            it.copy(
                                currentPositionMs = currentPos,
                                activeProviderId = provider
                            )
                        }
                    }
                }
                preloadNextTrack()
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
                    if (exoPlayer.isPlaying) {
                        val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                        val rawExoDur = exoPlayer.duration.coerceAtLeast(0L)
                        val songDur = currentSong?.durationMs ?: 0L
                        val effectiveDur = when {
                            rawExoDur > 30_000L -> rawExoDur
                            songDur > 30_000L -> songDur
                            else -> 0L
                        }
                        val buffered = exoPlayer.bufferedPosition.coerceAtLeast(0L)
                        _playerState.update {
                            it.copy(
                                currentPositionMs = pos,
                                durationMs = if (effectiveDur > 0) effectiveDur else (if (rawExoDur > 0) rawExoDur else songDur),
                                bufferedPositionMs = buffered
                            )
                        }

                        // Proactive Seamless Play (Simultaneous Crossfade)
                        // STRICT GUARDS to prevent premature song skipping:
                        // 1. crossfadeSeconds must be > 0
                        // 2. effectiveDur must be > 45s (crossfadeMs * 3)
                        // 3. pos MUST be at least 20 seconds into the song
                        // 4. pos MUST be at least 70% into the song
                        // 5. remainingMs must be within 0..crossfadeMs
                        val crossfadeMs = crossfadeSeconds * 1000L
                        val remainingMs = effectiveDur - pos
                        val canCrossfade = crossfadeSeconds > 0 &&
                                effectiveDur > (crossfadeMs * 3L).coerceAtLeast(45_000L) &&
                                pos >= 20_000L &&
                                pos >= (effectiveDur * 0.70f).toLong()

                        if (canCrossfade) {
                            // Sub-frame polling near transition boundary (within 2s) to hit handover with millisecond accuracy
                            if (remainingMs in (crossfadeMs - 500L)..(crossfadeMs + 2500L)) {
                                trackerDelay = 40L
                            }

                            // Stage 1: Pre-buffer the tail of the current song on fadePlayer (~3-10s before crossfade starts)
                            if (remainingMs in (crossfadeMs + 1000L)..(crossfadeMs + 10_000L)) {
                                val curId = currentSong?.id
                                if (curId != null && fadePlayerPreparedSongId != curId) {
                                    val currentItem = exoPlayer.currentMediaItem
                                    if (currentItem != null) {
                                        fadePlayerPreparedSongId = curId
                                        fadePlayer.stop()
                                        fadePlayer.clearMediaItems()
                                        fadePlayer.setMediaItem(currentItem)
                                        fadePlayer.seekTo((effectiveDur - crossfadeMs).coerceAtLeast(0L))
                                        fadePlayer.prepare()
                                        fadePlayer.playWhenReady = false
                                        LofiQLogger.i(
                                            LofiQLogger.Category.PLAYBACK,
                                            "[CROSSFADE] Pre-buffering fade-out tail for '${currentSong?.title}' at ${effectiveDur - crossfadeMs}ms"
                                        )
                                    }
                                }
                            }

                            // Stage 2: Trigger simultaneous crossfade right when remainingMs <= crossfadeMs
                            if (remainingMs in 0L..crossfadeMs && exoPlayer.mediaItemCount > 1 && exoPlayer.currentMediaItemIndex == 0) {
                                val curId = currentSong?.id
                                if (curId != null && crossfadeActiveForSongId != curId) {
                                    val isFadePlayerReady = fadePlayer.playbackState == Player.STATE_READY
                                    val isFadePlayerPrepared = fadePlayerPreparedSongId == curId && fadePlayer.mediaItemCount > 0

                                    // If fade player is still buffering, give it a moment to finish buffering before triggering
                                    if (isFadePlayerPrepared && !isFadePlayerReady && remainingMs > 1200L) {
                                        // Wait for next position tracker tick to let fadePlayer reach STATE_READY
                                    } else {
                                        crossfadeActiveForSongId = curId
                                        triggerSimultaneousCrossfade(
                                            crossfadeMs = crossfadeMs,
                                            useFadeTail = isFadePlayerReady && isFadePlayerPrepared
                                        )
                                    }
                                }
                            } else if (volumeFadeJob == null || !volumeFadeJob!!.isActive) {
                                if (exoPlayer.volume < 1f) {
                                    exoPlayer.volume = 1f
                                }
                            }
                        } else if (volumeFadeJob == null || !volumeFadeJob!!.isActive) {
                            if (exoPlayer.volume < 1f) {
                                exoPlayer.volume = 1f
                            }
                        }

                        // Proactively ensure upcoming tracks are prefetched when reaching 10s or every 30s
                        if (pos > 10_000L && (pos - lastPrefetchPos > 30_000L || (effectiveDur > 0 && effectiveDur - pos < 45_000L))) {
                            lastPrefetchPos = pos
                            triggerUpcomingPrefetch()
                            checkAndReplenishQueue()
                            preloadNextTrack()
                        }
                    }
                }
                delay(trackerDelay)
            }
        }
    }

    private fun triggerSimultaneousCrossfade(crossfadeMs: Long, useFadeTail: Boolean = true) {
        volumeFadeJob?.cancel()
        volumeFadeJob = engineScope.launch {
            LofiQLogger.i(
                LofiQLogger.Category.PLAYBACK,
                "[CROSSFADE] Starting seamless simultaneous crossfade (${crossfadeMs / 1000}s overlap, useFadeTail=$useFadeTail)"
            )

            // 1. Hand over outgoing track tail to fadePlayer seamlessly
            var hasActiveFadeTail = false
            if (useFadeTail && fadePlayer.mediaItemCount > 0 && fadePlayer.playbackState == Player.STATE_READY) {
                hasActiveFadeTail = true
                val curPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                if (curPos > 0) {
                    fadePlayer.seekTo(curPos)
                }
                fadePlayer.volume = 1.0f
                fadePlayer.playWhenReady = true
                fadePlayer.play()
            }

            // 2. Advance primary exoPlayer immediately to preloaded incoming track (index 1)
            // If fadePlayer is active, start incoming track at volume 0 and fade up.
            // If fadePlayer is not active, avoid abrupt silence by starting gently and ramping smoothly.
            exoPlayer.volume = if (hasActiveFadeTail) 0.0f else 0.15f
            withContext(dispatchers.main) {
                exoPlayer.seekToNextMediaItem()
                exoPlayer.playWhenReady = true
                exoPlayer.play()
            }

            // 3. Smooth dual-player simultaneous volume crossfade
            // Outgoing track fades from 1.0 -> 0.0 on fadePlayer
            // Incoming track fades from 0.0 -> 1.0 on exoPlayer
            val steps = (crossfadeMs / 50L).coerceIn(20L, 80L).toInt()
            val stepDelay = crossfadeMs / steps
            for (i in 1..steps) {
                delay(stepDelay)
                val inRatio = (i.toFloat() / steps.toFloat()).coerceIn(0.0f, 1.0f)
                val outRatio = (1.0f - inRatio).coerceIn(0.0f, 1.0f)

                exoPlayer.volume = inRatio
                if (hasActiveFadeTail) {
                    runCatching { fadePlayer.volume = outRatio }
                }
            }

            // 4. Crossfade complete: clean up fade player and ensure full primary volume
            exoPlayer.volume = 1.0f
            runCatching {
                fadePlayer.stop()
                fadePlayer.clearMediaItems()
            }
            fadePlayerPreparedSongId = null
            crossfadeActiveForSongId = null

            withContext(dispatchers.main) {
                while (exoPlayer.currentMediaItemIndex > 0) {
                    exoPlayer.removeMediaItem(0)
                }
            }
            LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[CROSSFADE] Seamless crossfade completed successfully.")
        }
    }

    fun playSong(song: Song) {
        cancelFadeAndRestoreVolume()
        engineScope.launch {
            commandMutex.withLock {
                consecutiveFailures = 0
                priorityQueueOffset = 0
                isExplicitQueue = false
                if (currentSong?.id == song.id && exoPlayer.playbackState != Player.STATE_IDLE && exoPlayer.playbackState != Player.STATE_ENDED) {
                    exoPlayer.playWhenReady = true
                    exoPlayer.play()
                    _playerState.update { it.copy(isPlaying = true, isBuffering = false) }
                    updateState()
                    return@withLock
                }
                currentQueue.clear()
                currentQueue.add(song)
                currentIndex = 0
                startPlayback(song)
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

                if (currentSong?.id == targetSong.id && currentQueue.size == songs.size && exoPlayer.playbackState != Player.STATE_IDLE && exoPlayer.playbackState != Player.STATE_ENDED) {
                    exoPlayer.playWhenReady = true
                    exoPlayer.play()
                    _playerState.update { it.copy(isPlaying = true, isBuffering = false) }
                    updateState()
                    return@withLock
                }

                currentQueue.clear()
                currentQueue.addAll(songs)
                currentIndex = targetIndex
                startPlayback(currentQueue[currentIndex])
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
                if (!isExplicitQueue) {
                    // In single-track auto mode, drop preceding tracks so chosen song is at 0,
                    // and top up to 10 smart recommendations based on the newly selected track!
                    val targetSong = currentQueue[targetIndex]
                    val remaining = currentQueue.drop(targetIndex)
                    currentQueue.clear()
                    currentQueue.addAll(remaining)
                    currentIndex = 0
                    startPlayback(targetSong)
                    fetchSmartRecommendations(targetSong, autoAppend = true, targetCount = 10)
                } else {
                    currentIndex = targetIndex
                    startPlayback(currentQueue[currentIndex])
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
                preloadNextTrack()
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
                preloadNextTrack()
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
                preloadNextTrack()
                persistQueue()
            }
        }
    }

    fun togglePlayPause() {
        engineScope.launch {
            commandMutex.withLock {
                if (exoPlayer.isPlaying) {
                    LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[PLAYBACK][id=$activePlaybackId] PAUSE_REQUESTED")
                    exoPlayer.pause()
                    runCatching { if (fadePlayer.isPlaying) fadePlayer.pause() }
                } else {
                    if ((exoPlayer.playbackState == Player.STATE_IDLE || exoPlayer.playbackState == Player.STATE_ENDED) && currentSong != null) {
                        startPlayback(currentSong!!)
                    } else {
                        LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[PLAYBACK][id=$activePlaybackId] PLAY_REQUESTED")
                        exoPlayer.playWhenReady = true
                        exoPlayer.play()
                        runCatching {
                            if (volumeFadeJob?.isActive == true && (fadePlayer.playbackState == Player.STATE_READY || fadePlayer.playbackState == Player.STATE_BUFFERING)) {
                                fadePlayer.play()
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
                if ((exoPlayer.playbackState == Player.STATE_IDLE || exoPlayer.playbackState == Player.STATE_ENDED) && currentSong != null) {
                    startPlayback(currentSong!!)
                } else {
                    LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[PLAYBACK][id=$activePlaybackId] PLAY_REQUESTED")
                    exoPlayer.playWhenReady = true
                    exoPlayer.play()
                    runCatching {
                        if (volumeFadeJob?.isActive == true && (fadePlayer.playbackState == Player.STATE_READY || fadePlayer.playbackState == Player.STATE_BUFFERING)) {
                            fadePlayer.play()
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
                exoPlayer.pause()
                runCatching { if (fadePlayer.isPlaying) fadePlayer.pause() }
                updateState()
            }
        }
    }

    fun stop() {
        cancelFadeAndRestoreVolume()
        engineScope.launch {
            commandMutex.withLock {
                LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[PLAYBACK][id=$activePlaybackId] STOP_REQUESTED")
                playbackJob?.cancel()
                playbackJob = null
                preloadJob?.cancel()
                preloadJob = null
                preloadedSongId = null
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                runCatching {
                    fadePlayer.stop()
                    fadePlayer.clearMediaItems()
                }
                currentQueue.clear()
                currentIndex = -1
                _playerState.value = LofiQPlayerState()
            }
        }
    }

    fun next() {
        cancelFadeAndRestoreVolume()
        engineScope.launch {
            commandMutex.withLock {
                if (currentQueue.isEmpty()) return@withLock
                consecutiveFailures = 0
                if (priorityQueueOffset > 0) priorityQueueOffset--

                val nextSong = getNextSong()
                if (nextSong != null && exoPlayer.mediaItemCount > 1 && exoPlayer.getMediaItemAt(1).mediaId == nextSong.id) {
                    LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[NEXT] Fast-seeking to already preloaded track: '${nextSong.title}'")
                    exoPlayer.seekToNextMediaItem()
                    exoPlayer.playWhenReady = true
                    exoPlayer.play()
                    return@withLock
                }

                if (currentIndex < currentQueue.size - 1) {
                    currentIndex++
                    startPlayback(currentQueue[currentIndex])
                    checkAndReplenishQueue()
                } else if (_playerState.value.repeatMode == PlaybackRepeatMode.ALL) {
                    currentIndex = 0
                    startPlayback(currentQueue[currentIndex])
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
        cancelFadeAndRestoreVolume()
        engineScope.launch {
            commandMutex.withLock {
                if (currentQueue.isEmpty()) return@withLock
                consecutiveFailures = 0
                if (exoPlayer.currentPosition > 3000L) {
                    exoPlayer.seekTo(0L)
                    exoPlayer.play()
                } else if (currentIndex > 0) {
                    currentIndex--
                    startPlayback(currentQueue[currentIndex])
                } else if (_playerState.value.repeatMode == PlaybackRepeatMode.ALL) {
                    currentIndex = currentQueue.size - 1
                    startPlayback(currentQueue[currentIndex])
                } else {
                    exoPlayer.seekTo(0L)
                }
                updateState()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        cancelFadeAndRestoreVolume()
        engineScope.launch {
            commandMutex.withLock {
                LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[PLAYBACK][id=$activePlaybackId] SEEK_REQUESTED to ${positionMs}ms")
                exoPlayer.seekTo(positionMs)
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
            val nextShuffle = !_playerState.value.isShuffleEnabled
            preferences.setShuffleEnabled(nextShuffle)
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
                    preloadNextTrack()
                    persistQueue()
                }
            }
        }
    }

    fun reorderUpcomingQueue(newUpcomingList: List<Song>) {
        engineScope.launch {
            commandMutex.withLock {
                if (currentIndex in currentQueue.indices) {
                    val currentSong = currentQueue[currentIndex]
                    val history = if (currentIndex > 0) currentQueue.subList(0, currentIndex) else emptyList()
                    val newFullQueue = ArrayList<Song>(history.size + 1 + newUpcomingList.size).apply {
                        addAll(history)
                        add(currentSong)
                        addAll(newUpcomingList)
                    }
                    currentQueue.clear()
                    currentQueue.addAll(newFullQueue)
                    updateState()
                    triggerUpcomingPrefetch()
                    preloadNextTrack()
                    persistQueue()
                } else if (currentQueue.isEmpty()) {
                    currentQueue.addAll(newUpcomingList)
                    currentIndex = 0
                    updateState()
                    preloadNextTrack()
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
                    updateState()
                    val currentPlayingIndex = exoPlayer.currentMediaItemIndex
                    while (exoPlayer.mediaItemCount > currentPlayingIndex + 1) {
                        exoPlayer.removeMediaItem(exoPlayer.mediaItemCount - 1)
                    }
                    preloadedSongId = null
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
                    } else if (index == currentIndex) {
                        if (currentIndex < currentQueue.size) {
                            startPlayback(currentQueue[currentIndex])
                        } else if (currentQueue.isNotEmpty()) {
                            currentIndex = 0
                            startPlayback(currentQueue[0])
                        } else {
                            currentIndex = -1
                            exoPlayer.stop()
                        }
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
        if (nextSong.id == preloadedSongId && exoPlayer.mediaItemCount > 1) {
            return
        }
        preloadJob?.cancel()
        preloadJob = engineScope.launch(dispatchers.io) {
            try {
                LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[PRELOAD] Starting preload for next track: '${nextSong.title}' [id: ${nextSong.id}]")
                val (mediaItem, resolvedProvider) = resolveMediaItemForSong(nextSong)
                withContext(dispatchers.main) {
                    val nextSongIndex = if (currentIndex in 0 until currentQueue.size - 1) currentIndex + 1 else -1
                    if (nextSongIndex in currentQueue.indices && currentQueue[nextSongIndex].id == nextSong.id) {
                        currentQueue[nextSongIndex] = currentQueue[nextSongIndex].copy(providerId = resolvedProvider)
                    }
                    if (getNextSong()?.id == nextSong.id) {
                        val currentPlayingIndex = exoPlayer.currentMediaItemIndex
                        while (exoPlayer.mediaItemCount > currentPlayingIndex + 1) {
                            exoPlayer.removeMediaItem(exoPlayer.mediaItemCount - 1)
                        }
                        exoPlayer.addMediaItem(mediaItem)
                        preloadedSongId = nextSong.id
                        LofiQLogger.i(
                            LofiQLogger.Category.PLAYBACK,
                            "[PRELOAD] Enqueued next track into ExoPlayer: '${nextSong.title}' [mediaItemCount=${exoPlayer.mediaItemCount}]"
                        )
                    }
                }

                val artworkUrl = com.lofiq.core.common.ArtworkUtils.getOptimalArtwork(nextSong)
                if (artworkUrl.isNotBlank()) {
                    runCatching {
                        val req = coil.request.ImageRequest.Builder(context)
                            .data(artworkUrl)
                            .size(500, 500)
                            .build()
                        coil.Coil.imageLoader(context).enqueue(req)
                    }
                }
            } catch (e: CancellationException) {
                // Cancelled normally
            } catch (e: Exception) {
                LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[PRELOAD] Failed to preload next track '${nextSong.title}': ${e.message}")
            }
        }
    }

    private fun handleMediaItemTransition(mediaItem: MediaItem, reason: Int) {
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
            return
        }
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
            _playerState.update { it.copy(currentPositionMs = 0L) }
            return
        }
        val mediaId = mediaItem.mediaId
        val targetIndex = when {
            currentIndex in 0 until currentQueue.size - 1 && currentQueue[currentIndex + 1].id == mediaId -> currentIndex + 1
            _playerState.value.repeatMode == PlaybackRepeatMode.ALL && currentQueue.isNotEmpty() && currentQueue[0].id == mediaId -> 0
            else -> currentQueue.indexOfFirst { it.id == mediaId }
        }

        if (targetIndex != -1 && targetIndex != currentIndex) {
            LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[TRANSITION] Transitioned to track '$mediaId' at queue index $targetIndex (reason=$reason)")
            val newSong: Song
            if (!isExplicitQueue && targetIndex > 0) {
                val remaining = currentQueue.drop(targetIndex)
                currentQueue.clear()
                currentQueue.addAll(remaining)
                currentIndex = 0
                newSong = currentQueue[0]
            } else {
                currentIndex = targetIndex
                newSong = currentQueue[currentIndex]
            }
            activePlaybackId = java.util.UUID.randomUUID().toString().take(8)
            preloadedSongId = null
            if (volumeFadeJob?.isActive != true) {
                fadePlayerPreparedSongId = null
                crossfadeActiveForSongId = null
            }
            currentSongRetryCount = 0

            // Clean up completed items in ExoPlayer so currently playing track sits cleanly at index 0,
            // but ONLY if simultaneous crossfade isn't actively running
            if (volumeFadeJob?.isActive != true && exoPlayer.mediaItemCount > 1 && exoPlayer.currentMediaItemIndex == 1) {
                exoPlayer.removeMediaItem(0)
            }

            _playerState.update {
                it.copy(
                    currentSong = newSong,
                    activeProviderId = newSong.providerId,
                    isPlaying = true,
                    isBuffering = false,
                    isReady = true,
                    errorMessage = null,
                    currentPositionMs = 0L,
                    durationMs = newSong.durationMs,
                    currentQueueIndex = currentIndex
                )
            }
            updateState()

            fetchLyricsForSong(newSong)
            fetchSmartRecommendations(newSong, autoAppend = !isExplicitQueue, targetCount = 10)
            engineScope.launch(dispatchers.io) {
                songDao.incrementPlayCount(newSong.id)
                historyDao.recordHistory(
                    PlaybackHistoryEntity(
                        songId = newSong.id,
                        playedAt = System.currentTimeMillis(),
                        durationPlayedSeconds = 0
                    )
                )
            }

            if (volumeFadeJob?.isActive != true) {
                if (crossfadeSeconds > 0) {
                    volumeFadeJob = engineScope.launch {
                        exoPlayer.volume = 0.05f
                        val fadeInDurationMs = (crossfadeSeconds * 400L).coerceIn(1000L, 3500L)
                        val steps = 20
                        val stepDelay = fadeInDurationMs / steps
                        for (i in 1..steps) {
                            delay(stepDelay)
                            val v = 0.05f + (0.95f * (i.toFloat() / steps))
                            exoPlayer.volume = v.coerceIn(0.05f, 1f)
                        }
                        exoPlayer.volume = 1f
                    }
                } else {
                    cancelFadeAndRestoreVolume()
                }
            }

            checkAndReplenishQueue()
            preloadNextTrack()
        }
    }

    private fun startPlayback(song: Song, retryCount: Int = 0) {
        cancelFadeAndRestoreVolume()
        playbackJob?.cancel()
        preloadJob?.cancel()
        preloadJob = null
        preloadedSongId = null
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
                LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[MEDIA3][id=$reqId] PREPARE & PLAY")
                exoPlayer.playWhenReady = true
                exoPlayer.setMediaItem(mediaItem, /* resetPosition = */ true)
                exoPlayer.prepare()
                exoPlayer.play()
            }

            preloadNextTrack()

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
                                    if (activePlaybackId == reqId && exoPlayer.mediaItemCount > 0 && exoPlayer.currentMediaItem?.mediaId == song.id) {
                                        val updatedMetadata = mediaItem.mediaMetadata.buildUpon()
                                            .setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                                            .build()
                                        exoPlayer.playlistMetadata = updatedMetadata
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
                val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                val curSong = currentSong
                val expectedDur = curSong?.durationMs ?: 0L
                val isPrematureEnd = expectedDur > 30_000L && (pos < 20_000L || (pos < expectedDur - 15_000L && pos < (expectedDur * 0.85f).toLong()))

                if (isPrematureEnd && curSong != null && currentSongRetryCount < 2) {
                    currentSongRetryCount++
                    LofiQLogger.w(
                        LofiQLogger.Category.PLAYBACK,
                        "[TRACK_ENDED] Premature EOF detected at ${pos}ms for '${curSong.title}' (expected ${expectedDur}ms). Auto-recovering current track from position (attempt $currentSongRetryCount/2)."
                    )
                    exoPlayer.prepare()
                    if (pos > 0) {
                        exoPlayer.seekTo(pos)
                    }
                    exoPlayer.play()
                    return@withLock
                }

                LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[TRACK_ENDED] Finished current track [index=$currentIndex / size=${currentQueue.size}] at ${pos}ms")
                if (priorityQueueOffset > 0) priorityQueueOffset--
                if (_playerState.value.repeatMode == PlaybackRepeatMode.ONE) {
                    exoPlayer.seekTo(0L)
                    exoPlayer.playWhenReady = true
                    exoPlayer.prepare()
                    exoPlayer.play()
                } else if (currentIndex < currentQueue.size - 1) {
                    if (!isExplicitQueue) {
                        val nextSong = currentQueue[currentIndex + 1]
                        val remaining = currentQueue.drop(currentIndex + 1)
                        currentQueue.clear()
                        currentQueue.addAll(remaining)
                        currentIndex = 0
                        startPlayback(nextSong)
                        checkAndReplenishQueue()
                    } else {
                        currentIndex++
                        startPlayback(currentQueue[currentIndex])
                        checkAndReplenishQueue()
                    }
                } else if (_playerState.value.repeatMode == PlaybackRepeatMode.ALL && currentQueue.isNotEmpty()) {
                    currentIndex = 0
                    startPlayback(currentQueue[0])
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
        val isExoPlaying = exoPlayer.isPlaying
        val isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING || playbackJob?.isActive == true
        val isReady = exoPlayer.playbackState == Player.STATE_READY
        val current = if (currentIndex in currentQueue.indices) currentQueue[currentIndex] else null
        val isCurrentInPlayer = exoPlayer.currentMediaItem?.mediaId == current?.id
        val pos = if (isCurrentInPlayer) exoPlayer.currentPosition.coerceAtLeast(0L) else 0L
        val dur = if (isCurrentInPlayer && exoPlayer.duration > 0) exoPlayer.duration else current?.durationMs ?: 0L

        _playerState.update {
            val effectivePlaying = if (playbackJob?.isActive == true) true else isExoPlaying
            it.copy(
                currentSong = current ?: it.currentSong,
                isPlaying = effectivePlaying,
                isBuffering = isBuffering,
                isReady = isReady,
                currentPositionMs = pos,
                durationMs = if (dur > 0) dur else current?.durationMs ?: it.durationMs,
                queue = currentQueue.toList(),
                currentQueueIndex = currentIndex
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
}

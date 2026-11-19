package com.lofiq.playback.engine

import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages targeted, short-lived partial WakeLocks to ensure CPU execution remains active
 * during critical asynchronous operations when the display is OFF:
 * 1. Dual-player crossfade overlap & 40ms volume ramp loops (transitionWakeLock)
 * 2. Background stream URL resolution & ExoPlayer secondary buffer preparation (preloadWakeLock)
 * 3. Primary stream URL resolution & initial track buffer preparation (playbackWakeLock)
 * 4. Atomic player promotion and MediaSession handover
 *
 * Prevents premature kernel CPU suspension during screen-off transitions and playback starts
 * without holding permanent or indefinite wake locks.
 */
@Singleton
class PlaybackWakeLockManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val diagnosticLogger: PlaybackDiagnosticLogger
) {
    private val powerManager by lazy {
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    }

    private var transitionWakeLock: PowerManager.WakeLock? = null
    private var preloadWakeLock: PowerManager.WakeLock? = null
    private var playbackWakeLock: PowerManager.WakeLock? = null
    private val lock = Any()

    fun acquireTransitionWakeLock(durationMs: Long = 20_000L) {
        synchronized(lock) {
            runCatching {
                if (transitionWakeLock?.isHeld == true) {
                    transitionWakeLock?.release()
                }
                val pm = powerManager ?: return@runCatching
                transitionWakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "LofiQ:TransitionWakeLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire(durationMs.coerceIn(5_000L, 45_000L))
                }
                diagnosticLogger.logWakeLock("ACQUIRED", "TransitionWakeLock", durationMs)
            }.onFailure {
                diagnosticLogger.logError("WAKELOCK_ACQUIRE_FAILED", "TransitionWakeLock: ${it.message}", it)
            }
        }
    }

    fun releaseTransitionWakeLock() {
        synchronized(lock) {
            runCatching {
                if (transitionWakeLock?.isHeld == true) {
                    transitionWakeLock?.release()
                    diagnosticLogger.logWakeLock("RELEASED", "TransitionWakeLock")
                }
                transitionWakeLock = null
            }
        }
    }

    fun acquirePreloadWakeLock(timeoutMs: Long = 25_000L) {
        synchronized(lock) {
            runCatching {
                if (preloadWakeLock?.isHeld == true) {
                    preloadWakeLock?.release()
                }
                val pm = powerManager ?: return@runCatching
                preloadWakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "LofiQ:PreloadWakeLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire(timeoutMs.coerceIn(3_000L, 35_000L))
                }
                diagnosticLogger.logWakeLock("ACQUIRED", "PreloadWakeLock", timeoutMs)
            }.onFailure {
                diagnosticLogger.logError("WAKELOCK_ACQUIRE_FAILED", "PreloadWakeLock: ${it.message}", it)
            }
        }
    }

    fun releasePreloadWakeLock() {
        synchronized(lock) {
            runCatching {
                if (preloadWakeLock?.isHeld == true) {
                    preloadWakeLock?.release()
                    diagnosticLogger.logWakeLock("RELEASED", "PreloadWakeLock")
                }
                preloadWakeLock = null
            }
        }
    }

    fun acquirePlaybackWakeLock(timeoutMs: Long = 25_000L) {
        synchronized(lock) {
            runCatching {
                if (playbackWakeLock?.isHeld == true) {
                    playbackWakeLock?.release()
                }
                val pm = powerManager ?: return@runCatching
                playbackWakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "LofiQ:PlaybackWakeLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire(timeoutMs.coerceIn(5_000L, 35_000L))
                }
                diagnosticLogger.logWakeLock("ACQUIRED", "PlaybackWakeLock", timeoutMs)
            }.onFailure {
                diagnosticLogger.logError("WAKELOCK_ACQUIRE_FAILED", "PlaybackWakeLock: ${it.message}", it)
            }
        }
    }

    fun releasePlaybackWakeLock() {
        synchronized(lock) {
            runCatching {
                if (playbackWakeLock?.isHeld == true) {
                    playbackWakeLock?.release()
                    diagnosticLogger.logWakeLock("RELEASED", "PlaybackWakeLock")
                }
                playbackWakeLock = null
            }
        }
    }

    fun releaseAll() {
        releaseTransitionWakeLock()
        releasePreloadWakeLock()
        releasePlaybackWakeLock()
    }
}

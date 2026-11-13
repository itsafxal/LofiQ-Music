package com.lofiq.playback.engine

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.lofiq.core.common.LofiQLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Diagnostic logger specifically for background playback, screen-off transitions,
 * audio focus lifecycle, WakeLocks, and dual-player handover.
 *
 * Uses dedicated TAG "LofiQBackgroundPlayback" for easy adb logcat filtering:
 *   adb logcat -s LofiQBackgroundPlayback
 */
@Singleton
class PlaybackDiagnosticLogger @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val TAG = "LofiQBackgroundPlayback"
    }

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val powerManager by lazy {
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    }

    val isDisplayInteractive: Boolean
        get() = powerManager?.isInteractive ?: true

    private fun screenTag(): String = if (isDisplayInteractive) "[SCREEN_ON]" else "[SCREEN_OFF]"

    private fun log(level: Int, message: String, throwable: Throwable? = null) {
        val timestamp = timeFormat.format(Date())
        val fullMsg = "[$timestamp]${screenTag()} $message"
        when (level) {
            Log.DEBUG -> {
                Log.d(TAG, fullMsg)
                LofiQLogger.d(LofiQLogger.Category.PLAYBACK, "$TAG: $fullMsg")
            }
            Log.INFO -> {
                Log.i(TAG, fullMsg)
                LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "$TAG: $fullMsg")
            }
            Log.WARN -> {
                Log.w(TAG, fullMsg, throwable)
                LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "$TAG: $fullMsg", throwable)
            }
            Log.ERROR -> {
                Log.e(TAG, fullMsg, throwable)
                LofiQLogger.e(LofiQLogger.Category.PLAYBACK, "$TAG: $fullMsg", throwable)
            }
        }
    }

    fun logScreenState(isInteractive: Boolean) {
        val stateStr = if (isInteractive) "DISPLAY_TURNED_ON" else "DISPLAY_TURNED_OFF"
        log(Log.INFO, "[SCREEN_STATE] $stateStr")
    }

    fun logServiceLifecycle(event: String, details: String = "") {
        val extra = if (details.isNotBlank()) " | $details" else ""
        log(Log.INFO, "[SERVICE_LIFECYCLE] $event$extra")
    }

    fun logPlayerState(
        slot: String,
        state: String,
        isPlaying: Boolean,
        playWhenReady: Boolean,
        posMs: Long,
        durMs: Long,
        reason: String
    ) {
        log(
            Log.INFO,
            "[PLAYER_STATE] slot=$slot state=$state isPlaying=$isPlaying pwr=$playWhenReady pos=${posMs}ms dur=${durMs}ms reason='$reason'"
        )
    }

    fun logPreload(event: String, trackTitle: String, trackId: String, epoch: Long, details: String = "") {
        val extra = if (details.isNotBlank()) " | $details" else ""
        log(Log.INFO, "[PRELOAD] event=$event track='$trackTitle' id=$trackId epoch=$epoch$extra")
    }

    fun logCrossfade(
        event: String,
        outTrack: String,
        inTrack: String,
        progressPercent: Int = 0,
        details: String = ""
    ) {
        val progress = if (progressPercent in 1..99) " progress=$progressPercent%" else ""
        val extra = if (details.isNotBlank()) " | $details" else ""
        log(Log.INFO, "[CROSSFADE] event=$event out='$outTrack' -> in='$inTrack'$progress$extra")
    }

    fun logHandover(event: String, promotedSlot: String, incomingTrack: String, details: String = "") {
        val extra = if (details.isNotBlank()) " | $details" else ""
        log(Log.INFO, "[HANDOVER] event=$event promotedSlot=$promotedSlot incoming='$incomingTrack'$extra")
    }

    fun logAudioFocus(event: String, focusState: String, details: String = "") {
        val extra = if (details.isNotBlank()) " | $details" else ""
        log(Log.INFO, "[AUDIO_FOCUS] event=$event state=$focusState$extra")
    }

    fun logWakeLock(event: String, tag: String, timeoutMs: Long = 0) {
        val timeoutStr = if (timeoutMs > 0) " timeout=${timeoutMs}ms" else ""
        log(Log.INFO, "[WAKELOCK] event=$event tag='$tag'$timeoutStr")
    }

    fun logError(event: String, message: String, throwable: Throwable? = null) {
        log(Log.ERROR, "[ERROR] event=$event msg='$message'", throwable)
    }
}

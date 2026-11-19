package com.lofiq.playback.engine

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.lofiq.core.common.LofiQLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Diagnostic event captured in the bounded ring buffer and persisted to disk
 * across process crashes and restarts.
 */
data class DiagnosticEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val formattedTime: String,
    val isScreenInteractive: Boolean,
    val category: String,
    val message: String,
    val trackId: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

/**
 * Diagnostic logger specifically for background playback, screen-off transitions,
 * audio focus lifecycle, WakeLocks, dual-player handover, and process restoration.
 *
 * Maintains a thread-safe circular ring buffer (max 100 entries) that persists
 * to disk in `filesDir/playback_diagnostic_events.json` so crash/termination history
 * survives process restarts.
 *
 * Dedicated TAG "LofiQBackgroundPlayback" for easy adb logcat filtering:
 *   adb logcat -s LofiQBackgroundPlayback
 */
@Singleton
class PlaybackDiagnosticLogger @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val TAG = "LofiQBackgroundPlayback"
        private const val MAX_BUFFER_SIZE = 100
        private const val PERSIST_FILE_NAME = "playback_diagnostic_events.json"

        // Canonical termination categories
        const val CATEGORY_USER_STOP = "USER_STOP"
        const val CATEGORY_USER_PAUSE = "USER_PAUSE"
        const val CATEGORY_AUDIO_FOCUS_LOSS = "AUDIO_FOCUS_LOSS"
        const val CATEGORY_STREAM_ERROR = "STREAM_ERROR"
        const val CATEGORY_PREMATURE_EOF = "PREMATURE_EOF"
        const val CATEGORY_SERVICE_DESTROYED = "SERVICE_DESTROYED"
        const val CATEGORY_PROCESS_RESTART = "PROCESS_RESTART"
        const val CATEGORY_HANDOVER_FAILURE = "HANDOVER_FAILURE"
        const val CATEGORY_WAKELOCK_EVENT = "WAKELOCK_EVENT"
        const val CATEGORY_NOTIFICATION_UPDATE = "NOTIFICATION_UPDATE"
        const val CATEGORY_UNKNOWN_TERMINATION = "UNKNOWN_TERMINATION"
    }

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val powerManager by lazy {
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    }

    private val bufferLock = Any()
    private val ringBuffer = ArrayDeque<DiagnosticEvent>(MAX_BUFFER_SIZE)

    init {
        loadPersistedEvents()
        recordEvent(
            category = CATEGORY_PROCESS_RESTART,
            message = "PlaybackDiagnosticLogger initialized / process start"
        )
    }

    val isDisplayInteractive: Boolean
        get() = powerManager?.isInteractive ?: true

    private fun screenTag(): String = if (isDisplayInteractive) "[SCREEN_ON]" else "[SCREEN_OFF]"

    private fun recordEvent(
        category: String,
        message: String,
        trackId: String? = null,
        positionMs: Long = 0L,
        durationMs: Long = 0L
    ) {
        val now = System.currentTimeMillis()
        val timeStr = timeFormat.format(Date(now))
        val event = DiagnosticEvent(
            timestamp = now,
            formattedTime = timeStr,
            isScreenInteractive = isDisplayInteractive,
            category = category,
            message = message,
            trackId = trackId,
            positionMs = positionMs,
            durationMs = durationMs
        )

        synchronized(bufferLock) {
            if (ringBuffer.size >= MAX_BUFFER_SIZE) {
                ringBuffer.removeFirst()
            }
            ringBuffer.addLast(event)
        }

        // Persist when critical lifecycle, error, or termination events occur
        if (category in listOf(
                CATEGORY_USER_STOP,
                CATEGORY_USER_PAUSE,
                CATEGORY_SERVICE_DESTROYED,
                CATEGORY_STREAM_ERROR,
                CATEGORY_PREMATURE_EOF,
                CATEGORY_HANDOVER_FAILURE,
                CATEGORY_PROCESS_RESTART,
                CATEGORY_UNKNOWN_TERMINATION
            )
        ) {
            persistBufferToDisk()
        }
    }

    private fun persistBufferToDisk() {
        runCatching {
            val file = File(context.filesDir, PERSIST_FILE_NAME)
            val jsonArray = JSONArray()
            val eventsSnapshot = synchronized(bufferLock) { ringBuffer.toList() }

            for (ev in eventsSnapshot) {
                val obj = JSONObject().apply {
                    put("timestamp", ev.timestamp)
                    put("formattedTime", ev.formattedTime)
                    put("screenOn", ev.isScreenInteractive)
                    put("category", ev.category)
                    put("message", ev.message)
                    if (ev.trackId != null) put("trackId", ev.trackId)
                    if (ev.positionMs > 0) put("posMs", ev.positionMs)
                    if (ev.durationMs > 0) put("durMs", ev.durationMs)
                }
                jsonArray.put(obj)
            }

            file.writeText(jsonArray.toString())
        }
    }

    private fun loadPersistedEvents() {
        runCatching {
            val file = File(context.filesDir, PERSIST_FILE_NAME)
            if (!file.exists()) return@runCatching

            val content = file.readText()
            if (content.isBlank()) return@runCatching

            val jsonArray = JSONArray(content)
            synchronized(bufferLock) {
                ringBuffer.clear()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val ev = DiagnosticEvent(
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        formattedTime = obj.optString("formattedTime", ""),
                        isScreenInteractive = obj.optBoolean("screenOn", true),
                        category = obj.optString("category", "UNKNOWN"),
                        message = obj.optString("message", ""),
                        trackId = if (obj.has("trackId")) obj.getString("trackId") else null,
                        positionMs = obj.optLong("posMs", 0L),
                        durationMs = obj.optLong("durMs", 0L)
                    )
                    ringBuffer.addLast(ev)
                }
            }
        }
    }

    fun getRecentEvents(): List<DiagnosticEvent> {
        return synchronized(bufferLock) { ringBuffer.toList() }
    }

    private fun log(
        level: Int,
        category: String,
        message: String,
        trackId: String? = null,
        positionMs: Long = 0L,
        durationMs: Long = 0L,
        throwable: Throwable? = null
    ) {
        val timestamp = timeFormat.format(Date())
        val fullMsg = "[$timestamp]${screenTag()} [$category] $message"
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

        recordEvent(
            category = category,
            message = message,
            trackId = trackId,
            positionMs = positionMs,
            durationMs = durationMs
        )
    }

    fun logTermination(
        category: String,
        details: String,
        trackId: String? = null,
        posMs: Long = 0L,
        durMs: Long = 0L
    ) {
        log(
            level = Log.WARN,
            category = category,
            message = details,
            trackId = trackId,
            positionMs = posMs,
            durationMs = durMs
        )
    }

    fun logScreenState(isInteractive: Boolean) {
        val stateStr = if (isInteractive) "DISPLAY_TURNED_ON" else "DISPLAY_TURNED_OFF"
        log(Log.INFO, "SCREEN_STATE", stateStr)
    }

    fun logServiceLifecycle(event: String, details: String = "") {
        val extra = if (details.isNotBlank()) " | $details" else ""
        log(Log.INFO, "SERVICE_LIFECYCLE", "$event$extra")
    }

    fun logNotificationUpdate(event: String, details: String = "") {
        val extra = if (details.isNotBlank()) " | $details" else ""
        log(Log.INFO, CATEGORY_NOTIFICATION_UPDATE, "$event$extra")
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
            level = Log.INFO,
            category = "PLAYER_STATE",
            message = "slot=$slot state=$state isPlaying=$isPlaying pwr=$playWhenReady pos=${posMs}ms dur=${durMs}ms reason='$reason'",
            positionMs = posMs,
            durationMs = durMs
        )
    }

    fun logPreload(event: String, trackTitle: String, trackId: String, epoch: Long, details: String = "") {
        val extra = if (details.isNotBlank()) " | $details" else ""
        log(
            level = Log.INFO,
            category = "PRELOAD",
            message = "event=$event track='$trackTitle' epoch=$epoch$extra",
            trackId = trackId
        )
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
        log(Log.INFO, "CROSSFADE", "event=$event out='$outTrack' -> in='$inTrack'$progress$extra")
    }

    fun logHandover(event: String, promotedSlot: String, incomingTrack: String, details: String = "") {
        val extra = if (details.isNotBlank()) " | $details" else ""
        log(Log.INFO, "HANDOVER", "event=$event promotedSlot=$promotedSlot incoming='$incomingTrack'$extra")
    }

    fun logAudioFocus(event: String, focusState: String, details: String = "") {
        val extra = if (details.isNotBlank()) " | $details" else ""
        log(
            level = Log.INFO,
            category = if (focusState.contains("LOSS")) CATEGORY_AUDIO_FOCUS_LOSS else "AUDIO_FOCUS",
            message = "event=$event state=$focusState$extra"
        )
    }

    fun logWakeLock(event: String, tag: String, timeoutMs: Long = 0) {
        val timeoutStr = if (timeoutMs > 0) " timeout=${timeoutMs}ms" else ""
        log(Log.INFO, CATEGORY_WAKELOCK_EVENT, "event=$event tag='$tag'$timeoutStr")
    }

    fun logError(event: String, message: String, throwable: Throwable? = null) {
        val cat = when {
            event.contains("EOF", ignoreCase = true) -> CATEGORY_PREMATURE_EOF
            event.contains("STREAM", ignoreCase = true) -> CATEGORY_STREAM_ERROR
            event.contains("HANDOVER", ignoreCase = true) -> CATEGORY_HANDOVER_FAILURE
            else -> "ERROR"
        }
        log(Log.ERROR, cat, "event=$event msg='$message'", throwable = throwable)
    }
}

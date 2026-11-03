package com.lofiq.playback.timer

import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.common.LofiQLogger
import com.lofiq.playback.engine.PlaybackEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class SleepTimerState(
    val isActive: Boolean = false,
    val remainingSeconds: Long = 0L,
    val totalSeconds: Long = 0L,
    val isEndOfTrack: Boolean = false
)

@OptIn(UnstableApi::class)
@Singleton
class SleepTimerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackEngine: PlaybackEngine,
    private val dispatchers: DispatcherProvider
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.main)
    private var timerJob: Job? = null

    private val _timerState = MutableStateFlow(SleepTimerState())
    val timerState: StateFlow<SleepTimerState> = _timerState.asStateFlow()

    fun startTimer(minutes: Int) {
        cancelTimer()
        if (minutes <= 0) return

        val totalSeconds = minutes * 60L
        _timerState.value = SleepTimerState(
            isActive = true,
            remainingSeconds = totalSeconds,
            totalSeconds = totalSeconds,
            isEndOfTrack = false
        )

        LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[SleepTimer] Started for $minutes minutes ($totalSeconds s)")

        timerJob = scope.launch {
            var remaining = totalSeconds
            while (remaining > 0 && isActive) {
                delay(1000L)
                remaining--
                _timerState.update { it.copy(remainingSeconds = remaining) }
            }

            if (remaining <= 0) {
                triggerSleepStop()
            }
        }
    }

    fun startEndOfTrackTimer() {
        cancelTimer()
        _timerState.value = SleepTimerState(
            isActive = true,
            remainingSeconds = 0L,
            totalSeconds = 0L,
            isEndOfTrack = true
        )
        LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[SleepTimer] Set to stop at end of current track")
    }

    fun onTrackCompleted() {
        if (_timerState.value.isActive && _timerState.value.isEndOfTrack) {
            LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[SleepTimer] End of track reached, stopping playback")
            triggerSleepStop()
        }
    }

    fun addMinutes(extraMinutes: Int) {
        if (!_timerState.value.isActive) {
            startTimer(extraMinutes)
            return
        }
        val extraSeconds = extraMinutes * 60L
        val newRemaining = _timerState.value.remainingSeconds + extraSeconds
        val newTotal = _timerState.value.totalSeconds + extraSeconds

        _timerState.update {
            it.copy(
                remainingSeconds = newRemaining,
                totalSeconds = newTotal,
                isEndOfTrack = false
            )
        }
    }

    fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        _timerState.value = SleepTimerState(isActive = false)
        LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[SleepTimer] Timer cancelled")
    }

    private fun triggerSleepStop() {
        scope.launch {
            // Smooth volume fade-out over 2.5 seconds
            val initialVolume = playbackEngine.exoPlayer.volume
            val steps = 15
            for (i in 1..steps) {
                delay(150L)
                val newVol = initialVolume * (1f - (i.toFloat() / steps))
                playbackEngine.exoPlayer.volume = newVol.coerceAtLeast(0f)
            }

            playbackEngine.pause()
            // Reset volume back to standard
            playbackEngine.exoPlayer.volume = initialVolume
            _timerState.value = SleepTimerState(isActive = false)
            LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[SleepTimer] Playback stopped and timer ended")
        }
    }
}

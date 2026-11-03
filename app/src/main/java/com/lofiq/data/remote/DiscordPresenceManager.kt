package com.lofiq.data.remote

import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.common.LofiQLogger
import com.lofiq.core.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiscordPresenceManager @Inject constructor(
    private val dispatchers: DispatcherProvider
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)

    private val _currentActivity = MutableStateFlow<DiscordActivity?>(null)
    val currentActivity: StateFlow<DiscordActivity?> = _currentActivity.asStateFlow()

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        if (!enabled) {
            _currentActivity.value = null
        }
    }

    fun updatePlayback(song: Song?, isPlaying: Boolean, currentPositionMs: Long) {
        if (!_isEnabled.value || song == null || !isPlaying) {
            _currentActivity.value = null
            return
        }

        scope.launch {
            val durationMs = song.durationMs
            val now = System.currentTimeMillis()
            val startMs = now - currentPositionMs
            val endMs = if (durationMs > 0) startMs + durationMs else 0L

            val activity = DiscordActivity(
                details = song.title,
                state = "by ${song.artist}",
                largeImageKey = song.artworkUrl,
                largeImageText = song.album.ifBlank { "LofiQ" },
                startTimestamp = startMs,
                endTimestamp = endMs
            )
            _currentActivity.value = activity
            LofiQLogger.d(LofiQLogger.Category.PLAYBACK, "[DISCORD] Updated presence for '${song.title}'")
        }
    }
}

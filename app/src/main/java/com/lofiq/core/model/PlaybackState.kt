package com.lofiq.core.model

data class LofiQPlayerState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val repeatMode: PlaybackRepeatMode = PlaybackRepeatMode.OFF,
    val isShuffleEnabled: Boolean = false,
    val queue: List<Song> = emptyList(),
    val queueItems: List<QueueItem> = emptyList(),
    val currentQueueIndex: Int = -1,
    val playbackSpeed: Float = 1.0f,
    val errorMessage: String? = null,
    val isReady: Boolean = false,
    val activeProviderId: String? = null,
    val isCrossfading: Boolean = false,
    val activeAudioSessionId: Int = 0
) {
    val progressFraction: Float
        get() = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val hasNext: Boolean
        get() = if (queue.isEmpty()) false else currentQueueIndex < queue.size - 1 || repeatMode == PlaybackRepeatMode.ALL

    val hasPrevious: Boolean
        get() = if (queue.isEmpty()) false else currentQueueIndex > 0 || currentPositionMs > 3000L
}

sealed interface LofiQResult<out T> {
    data class Success<T>(val data: T) : LofiQResult<T>
    data class Error(val message: String, val cause: Throwable? = null) : LofiQResult<Nothing>
    data object Loading : LofiQResult<Nothing>
}

package com.lofiq.core.common

import java.util.Locale
import java.util.concurrent.TimeUnit

object TimeFormatUtils {
    fun formatMs(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }

    fun formatSeconds(seconds: Long): String {
        if (seconds <= 0) return "0:00"
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format(Locale.US, "%d:%02d", minutes, remainingSeconds)
    }
}

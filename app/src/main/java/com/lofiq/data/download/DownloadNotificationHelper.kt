package com.lofiq.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.lofiq.core.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Native Android Download Progress Notification Manager.
 *
 * Provides real-time percentage progress bars in the Android system notification drawer
 * for active song downloads. Automatically dismisses notifications when downloads complete,
 * fail, or are cancelled.
 */
@Singleton
class DownloadNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "lofiq_track_downloads"
        const val CHANNEL_NAME = "Song Downloads"
        const val BASE_NOTIFICATION_ID = 20000
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    private val lastUpdateTimes = ConcurrentHashMap<String, Long>()
    private val lastUpdateProgress = ConcurrentHashMap<String, Int>()

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time progress for downloading songs"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun getNotificationId(songId: String): Int {
        return BASE_NOTIFICATION_ID + (songId.hashCode() and 0x7FFFFFFF) % 50000
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Updates or creates the progress notification for an ongoing track download.
     * Throttled to avoid overwhelming NotificationManager.
     */
    fun updateProgress(song: Song, progress: Float, force: Boolean = false) {
        if (!hasNotificationPermission()) return

        val percent = (progress * 100).toInt().coerceIn(0, 100)
        val now = System.currentTimeMillis()
        val lastTime = lastUpdateTimes[song.id] ?: 0L
        val lastPercent = lastUpdateProgress[song.id] ?: -1

        // Throttle updates: minimum 2% delta or >= 250ms elapsed, unless force=true
        if (!force && lastPercent >= 0 && (percent - lastPercent < 2) && (now - lastTime < 250)) {
            return
        }

        lastUpdateTimes[song.id] = now
        lastUpdateProgress[song.id] = percent

        val notificationId = getNotificationId(song.id)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("LofiQ — Downloading")
            .setContentText("${song.title} • $percent%")
            .setSubText("Downloading")
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setShowWhen(false)

        try {
            notificationManager?.notify(notificationId, builder.build())
        } catch (_: Exception) {
            // Guard against system or security limits
        }
    }

    /**
     * Cancels the download notification immediately.
     * Called when a download completes, fails, or is cancelled.
     */
    fun cancelNotification(songId: String) {
        lastUpdateTimes.remove(songId)
        lastUpdateProgress.remove(songId)
        try {
            notificationManager?.cancel(getNotificationId(songId))
        } catch (_: Exception) {
            // Guard
        }
    }
}

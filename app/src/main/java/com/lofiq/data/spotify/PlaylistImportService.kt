package com.lofiq.data.spotify
 
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.lofiq.MainActivity
import com.lofiq.R
import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.common.LofiQLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class PlaylistImportService : Service() {

    @Inject
    lateinit var spotifyImportHelper: SpotifyImportHelper

    @Inject
    lateinit var dispatchers: DispatcherProvider

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var importJob: Job? = null

    companion object {
        const val CHANNEL_ID = "lofiq_playlist_import"
        const val NOTIFICATION_ID = 3001
        const val COMPLETE_NOTIFICATION_ID = 3002

        const val ACTION_START_IMPORT = "com.lofiq.action.START_IMPORT"
        const val ACTION_CANCEL_IMPORT = "com.lofiq.action.CANCEL_IMPORT"
        const val EXTRA_URL = "extra_playlist_url"

        fun startImport(context: Context, url: String) {
            val intent = Intent(context, PlaylistImportService::class.java).apply {
                action = ACTION_START_IMPORT
                putExtra(EXTRA_URL, url)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancelImport(context: Context) {
            val intent = Intent(context, PlaylistImportService::class.java).apply {
                action = ACTION_CANCEL_IMPORT
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL_IMPORT -> {
                LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "Cancelling playlist import service")
                cancelCurrentImport()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(NOTIFICATION_ID)
                stopSelf()
            }
            ACTION_START_IMPORT -> {
                val url = intent.getStringExtra(EXTRA_URL)
                if (!url.isNullOrBlank()) {
                    startForegroundWithInitialNotification(url)
                    executeImport(url)
                } else {
                    stopSelf()
                }
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun cancelCurrentImport() {
        importJob?.cancel()
        importJob = null
        spotifyImportHelper.cancelActiveImport()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Playlist Import",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live progress when importing playlists from Spotify or YouTube Music"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithInitialNotification(url: String) {
        val isYouTube = url.contains("youtube.com", ignoreCase = true) ||
                url.contains("youtu.be", ignoreCase = true) ||
                url.contains("list=", ignoreCase = true)

        val notification = buildProgressNotification(
            title = if (isYouTube) "Importing YouTube Playlist..." else "Importing Spotify Playlist...",
            content = "Fetching tracklist from link...",
            progress = 0,
            max = 0,
            indeterminate = true
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            LofiQLogger.e(LofiQLogger.Category.PLAYBACK, "Failed to start foreground service: ${e.message}")
        }
    }

    private fun executeImport(url: String) {
        importJob?.cancel()
        importJob = serviceScope.launch {
            var lastUpdateTime = 0L
            val isYouTube = url.contains("youtube.com", ignoreCase = true) ||
                    url.contains("youtu.be", ignoreCase = true) ||
                    url.contains("list=", ignoreCase = true)

            spotifyImportHelper.importSpotifyPlaylist(url) { status ->
                when (status) {
                    SpotifyImportStatus.Idle -> {}
                    SpotifyImportStatus.FetchingMetadata -> {
                        val text = if (isYouTube) "Fetching tracks from YouTube Music..." else "Fetching tracks from Spotify..."
                        updateNotification(
                            buildProgressNotification(
                                title = if (isYouTube) "Importing YouTube Playlist..." else "Importing Spotify Playlist...",
                                content = text,
                                progress = 0,
                                max = 0,
                                indeterminate = true
                            )
                        )
                    }
                    is SpotifyImportStatus.MatchingTracks -> {
                        val now = System.currentTimeMillis()
                        if (now - lastUpdateTime > 250 || status.current == 1 || status.current == status.total) {
                            lastUpdateTime = now
                            val title = if (status.playlistTitle.isNotBlank()) {
                                "Importing '${status.playlistTitle}'"
                            } else {
                                if (isYouTube) "Importing YouTube Playlist" else "Importing Spotify Playlist"
                            }
                            val text = "${status.current} of ${status.total} tracks: ${status.trackTitle}"
                            updateNotification(
                                buildProgressNotification(
                                    title = title,
                                    content = text,
                                    progress = status.current,
                                    max = status.total,
                                    indeterminate = false
                                )
                            )
                        }
                    }
                    is SpotifyImportStatus.Success -> {
                        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                        } else {
                            @Suppress("DEPRECATION")
                            stopForeground(true)
                        }
                        manager.cancel(NOTIFICATION_ID)
                        val completeNotif = buildCompletionNotification(
                            title = "Playlist Imported! 🎉",
                            content = "Successfully imported ${status.importedCount} tracks into '${status.playlistName}'"
                        )
                        manager.notify(COMPLETE_NOTIFICATION_ID, completeNotif)
                        stopSelf()
                    }
                    is SpotifyImportStatus.Error -> {
                        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                        } else {
                            @Suppress("DEPRECATION")
                            stopForeground(true)
                        }
                        manager.cancel(NOTIFICATION_ID)
                        val errorNotif = buildErrorNotification(
                            title = "Playlist Import Failed",
                            content = status.message
                        )
                        manager.notify(COMPLETE_NOTIFICATION_ID, errorNotif)
                        stopSelf()
                    }
                }
            }
        }
    }

    private fun updateNotification(notification: Notification) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildProgressNotification(
        title: String,
        content: String,
        progress: Int,
        max: Int,
        indeterminate: Boolean
    ): Notification {
        val openIntent = (packageManager.getLaunchIntentForPackage(packageName) ?: Intent(this, MainActivity::class.java)).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, PlaylistImportService::class.java).apply {
            action = ACTION_CANCEL_IMPORT
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 1, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setProgress(max, progress, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun buildCompletionNotification(title: String, content: String): Notification {
        val openIntent = (packageManager.getLaunchIntentForPackage(packageName) ?: Intent(this, MainActivity::class.java)).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    private fun buildErrorNotification(title: String, content: String): Notification {
        val openIntent = (packageManager.getLaunchIntentForPackage(packageName) ?: Intent(this, MainActivity::class.java)).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(content)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    override fun onDestroy() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.cancel(NOTIFICATION_ID)
        serviceScope.cancel()
        super.onDestroy()
    }
}

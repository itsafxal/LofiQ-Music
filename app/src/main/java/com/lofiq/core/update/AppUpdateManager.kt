package com.lofiq.core.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import android.graphics.BitmapFactory
import com.lofiq.BuildConfig
import com.lofiq.MainActivity
import com.lofiq.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val releaseDate: String,
    val apkUrl: String,
    val apkSize: String,
    val releaseNotes: List<String>,
    val hasUpdate: Boolean,
    val checksum: String? = null
)

@Singleton
class AppUpdateManager @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "AppUpdateManager"
        const val UPDATE_CHANNEL_ID = "lofiq_app_updates"
        const val UPDATE_NOTIFICATION_ID = 4001
        const val EXTRA_SHOW_UPDATE = "extra_show_update"
        const val EXTRA_START_DOWNLOAD = "extra_start_download"
        const val WORK_NAME_PERIODIC_UPDATE = "lofiq_periodic_update_check"
        private const val REQUEST_CODE_UPDATE = 4001
        private const val REQUEST_CODE_DOWNLOAD = 4002

        // Primary (Vercel) and Resilient Fallback (Netlify) Update Endpoints
        const val DEFAULT_UPDATE_ENDPOINT = "https://lofiq-web.vercel.app/version.json"
        const val NETLIFY_FALLBACK_ENDPOINT = "https://mybackuplofiq.netlify.app/version.json"
    }

    /**
     * Check if a newer version of LofiQ is available.
     * Compares remote versionCode with current BuildConfig.VERSION_CODE.
     */
    suspend fun checkForUpdate(
        customEndpoint: String? = null
    ): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val endpoints = listOfNotNull(
                customEndpoint?.takeIf { it.isNotBlank() },
                DEFAULT_UPDATE_ENDPOINT,
                NETLIFY_FALLBACK_ENDPOINT
            )

            var jsonString: String? = null
            var resolvedEndpoint = ""

            for (endpoint in endpoints) {
                try {
                    val request = Request.Builder()
                        .url(endpoint)
                        .header("User-Agent", "LofiQ-Android/${BuildConfig.VERSION_NAME}")
                        .header("Cache-Control", "no-cache")
                        .build()

                    val response = okHttpClient.newCall(request).execute()
                    if (response.isSuccessful && response.body != null) {
                        jsonString = response.body?.string()
                        resolvedEndpoint = endpoint
                        break
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed fetching update from $endpoint: ${e.message}")
                }
            }

            if (jsonString == null) {
                return@withContext Result.failure(Exception("Could not connect to update server"))
            }

            val json = JSONObject(jsonString)
            val remoteVersionCode = json.optInt("versionCode", 0)
            val remoteVersionName = json.optString("versionName", "")
            val releaseDate = json.optString("releaseDate", "")
            var rawApkUrl = json.optString("apkUrl", "")
            val apkSize = json.optString("apkSize", "")
            val checksum = json.optString("checksum", "").ifBlank {
                json.optString("sha256", "").ifBlank { null }
            }

            val notesArray = json.optJSONArray("releaseNotes")
            val releaseNotes = mutableListOf<String>()
            if (notesArray != null) {
                for (i in 0 until notesArray.length()) {
                    val note = notesArray.optString(i)
                    if (note.isNotBlank()) {
                        releaseNotes.add(note)
                    }
                    if (releaseNotes.size >= 7) break
                }
            }

            // Resolve relative APK URLs against endpoint domain
            val fullApkUrl = if (rawApkUrl.startsWith("http://") || rawApkUrl.startsWith("https://")) {
                rawApkUrl
            } else if (rawApkUrl.isNotBlank()) {
                val baseDomain = resolvedEndpoint.substringBefore("/version.json")
                if (rawApkUrl.startsWith("/")) {
                    "$baseDomain$rawApkUrl"
                } else {
                    "$baseDomain/$rawApkUrl"
                }
            } else {
                resolvedEndpoint.substringBefore("/version.json") + "/downloads/LofiQ.apk"
            }

            val currentVersionCode = BuildConfig.VERSION_CODE
            val currentVersionName = BuildConfig.VERSION_NAME

            val hasUpdate = isUpdateAvailable(
                currentVersionCode = currentVersionCode,
                currentVersionName = currentVersionName,
                remoteVersionCode = remoteVersionCode,
                remoteVersionName = remoteVersionName
            )

            Log.i(
                TAG,
                "Update check evaluated: current=$currentVersionName($currentVersionCode), remote=$remoteVersionName($remoteVersionCode), hasUpdate=$hasUpdate"
            )

            val info = UpdateInfo(
                versionCode = remoteVersionCode,
                versionName = remoteVersionName,
                releaseDate = releaseDate,
                apkUrl = fullApkUrl,
                apkSize = apkSize,
                releaseNotes = releaseNotes,
                hasUpdate = hasUpdate,
                checksum = checksum
            )

            Result.success(info)
        } catch (e: Exception) {
            Log.e(TAG, "Check for update failed", e)
            Result.failure(e)
        }
    }

    /**
     * Determines whether [remoteVersionName] or [remoteVersionCode] represents
     * a genuinely newer release than the currently running build.
     * Prevents the infinite prompt loop when the user is already on the latest version.
     */
    fun isUpdateAvailable(
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
        currentVersionName: String = BuildConfig.VERSION_NAME,
        remoteVersionCode: Int,
        remoteVersionName: String
    ): Boolean {
        val cleanCurrentName = currentVersionName.trim().removePrefix("v").removePrefix("V")
        val cleanRemoteName = remoteVersionName.trim().removePrefix("v").removePrefix("V")

        // 1. If remote version name is blank, fall back to version code check
        if (cleanRemoteName.isBlank() || cleanCurrentName.isBlank()) {
            return remoteVersionCode > currentVersionCode
        }

        val semVerDiff = compareSemanticVersions(cleanRemoteName, cleanCurrentName)

        return when {
            // Remote version name is strictly newer (e.g. 2.0.0 > 1.2.0 or 1.2.1 > 1.2.0)
            semVerDiff > 0 -> true

            // Remote version name is older than currently installed (e.g. 1.1.1 < 1.2.0)
            semVerDiff < 0 -> false

            // Same semantic version name (e.g. both are 2.3.2):
            // Fall back to numeric version code comparison (e.g. build 53 > build 52)
            else -> remoteVersionCode > currentVersionCode
        }
    }

    /**
     * Compares two semantic version strings (e.g., "1.2.0" and "1.1.1").
     * Returns > 0 if v1 > v2, < 0 if v1 < v2, and 0 if equal.
     */
    fun compareSemanticVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(Regex("[^0-9]+")).filter { it.isNotBlank() }.mapNotNull { it.toIntOrNull() }
        val parts2 = v2.split(Regex("[^0-9]+")).filter { it.isNotBlank() }.mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) {
                return p1.compareTo(p2)
            }
        }
        return 0
    }

    /**
     * Determines whether to automatically display the launch update popup.
     * Always prompts on app launch if a newer version is available.
     */
    fun shouldPromptAutomaticUpdate(context: Context, info: UpdateInfo): Boolean {
        return info.hasUpdate
    }

    /**
     * Records that the user chose "Later" for [versionName].
     */
    fun markUpdateDismissed(context: Context, versionName: String) {
        val prefs = context.getSharedPreferences("lofiq_update_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("dismissed_version", versionName)
            .putLong("dismissed_timestamp", System.currentTimeMillis())
            .apply()
    }

    /**
     * Determines whether we should post an update notification right now in background.
     * 1. Brand new version released that user hasn't been notified of -> TRUE immediately on the spot!
     * 2. Already notified earlier -> TRUE only if 12-hour reminder interval has elapsed.
     */
    fun shouldNotifyUpdate(context: Context, info: UpdateInfo): Boolean {
        if (!info.hasUpdate) return false
        val prefs = context.getSharedPreferences("lofiq_update_prefs", Context.MODE_PRIVATE)
        val lastNotifiedVersion = prefs.getString("last_notified_version", null)
        val lastNotifiedTime = prefs.getLong("last_notified_timestamp", 0L)
        val now = System.currentTimeMillis()

        // Brand new version detected! Post notification on the spot without delay
        if (lastNotifiedVersion != info.versionName) {
            return true
        }

        // Same version already notified: only remind periodically every 12 hours
        return (now - lastNotifiedTime) >= TimeUnit.HOURS.toMillis(12)
    }

    /**
     * Records that an update notification was posted for [info].
     */
    fun markNotificationShown(context: Context, info: UpdateInfo) {
        val prefs = context.getSharedPreferences("lofiq_update_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("last_notified_version", info.versionName)
            .putInt("last_notified_version_code", info.versionCode)
            .putLong("last_notified_timestamp", System.currentTimeMillis())
            .apply()
    }

    /**
     * Performs a lightweight (~2KB) background check for updates and immediately posts
     * a system notification on the lock screen and notification panel if a newer version is available.
     */
    suspend fun checkForUpdateAndNotify(context: Context): Result<UpdateInfo> {
        val result = checkForUpdate()
        result.onSuccess { info ->
            if (info.hasUpdate) {
                // Post system notification when app is running in background or closed
                // When app is in foreground, the in-app popup dialog alerts the user directly
                val isForeground = com.lofiq.LofiQApp.isAppInForeground
                if (!isForeground && shouldNotifyUpdate(context, info)) {
                    showUpdateAvailableNotification(context, info)
                    markNotificationShown(context, info)
                }
            } else {
                dismissUpdateNotification(context)
                clearDismissedUpdate(context)
            }
        }
        return result
    }

    /**
     * Clears dismissed update record upon successful installation or update.
     */
    fun clearDismissedUpdate(context: Context) {
        val prefs = context.getSharedPreferences("lofiq_update_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    /**
     * Download the update APK to the app's cache directory with progress reporting.
     */
    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        expectedChecksum: String? = null,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val candidateUrls = linkedSetOf(apkUrl)
        if (apkUrl.contains("lofiq-web.vercel.app")) {
            candidateUrls.add(apkUrl.replace("lofiq-web.vercel.app", "mybackuplofiq.netlify.app"))
        } else if (!apkUrl.contains("mybackuplofiq.netlify.app")) {
            candidateUrls.add("https://mybackuplofiq.netlify.app/downloads/LofiQ.apk")
        }

        var lastException: Exception? = null
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val destinationFile = File(updatesDir, "LofiQ-latest.apk")

        for (url in candidateUrls) {
            try {
                Log.i(TAG, "Attempting to download update APK from: $url")
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "LofiQ-Android/${BuildConfig.VERSION_NAME}")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful || response.body == null) {
                    throw Exception("HTTP error ${response.code} downloading from $url")
                }

                val body = response.body!!
                val contentLength = body.contentLength()

                body.byteStream().use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        var totalRead = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (contentLength > 0) {
                                val progress = (totalRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                                onProgress(progress)
                            }
                        }
                        output.flush()
                    }
                }

                Log.i(TAG, "Successfully downloaded update APK (${destinationFile.length()} bytes) from $url")
                if (!expectedChecksum.isNullOrBlank()) {
                    val computedChecksum = calculateSha256(destinationFile)
                    if (!computedChecksum.equals(expectedChecksum.trim(), ignoreCase = true)) {
                        destinationFile.delete()
                        throw SecurityException("APK integrity check failed! Expected: $expectedChecksum, computed: $computedChecksum")
                    }
                    Log.i(TAG, "APK SHA-256 integrity verified successfully: $computedChecksum")
                }
                return@withContext Result.success(destinationFile)
            } catch (e: Exception) {
                Log.w(TAG, "Download attempt failed for $url: ${e.message}")
                lastException = e
            }
        }

        Result.failure(lastException ?: Exception("Failed downloading update APK from all available sources"))
    }

    private fun calculateSha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Creates the notification channel for app update notifications on Android O+.
     */
    fun createUpdateNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                UPDATE_CHANNEL_ID,
                "App Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when a newer version of LofiQ is available to install"
                setShowBadge(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                enableLights(true)
                lightColor = android.graphics.Color.WHITE
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * Posts a notification in the system notification shade indicating a new update is available.
     * Includes release notes and an "Update Now" action button.
     */
    fun showUpdateAvailableNotification(context: Context, info: UpdateInfo) {
        if (!info.hasUpdate) return

        createUpdateNotificationChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "Cannot show update notification: POST_NOTIFICATIONS permission not granted")
                return
            }
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        // Tap on notification: Opens app and shows the update prompt
        val openIntent = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent(context, MainActivity::class.java)).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SHOW_UPDATE, true)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_UPDATE,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action button "Update Now": Opens app and starts downloading update immediately
        val downloadIntent = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent(context, MainActivity::class.java)).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SHOW_UPDATE, true)
            putExtra(EXTRA_START_DOWNLOAD, true)
        }
        val downloadPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_DOWNLOAD,
            downloadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "LofiQ v${info.versionName} Available! 🚀"
        val contentText = if (info.releaseNotes.isNotEmpty()) {
            info.releaseNotes.first().trimStart { it == '•' || it == '-' || it == '*' || it.isWhitespace() }.take(100)
        } else {
            "A new version of LofiQ is available to install."
        }

        val bigText = buildString {
            append("A new update (v${info.versionName}) is ready to download.")
            if (info.releaseNotes.isNotEmpty()) {
                append("\n\nWhat's new:\n")
                info.releaseNotes.take(4).forEach { note ->
                    val cleanNote = note.trimStart { it == '•' || it == '-' || it == '*' || it.isWhitespace() }
                    append("• $cleanNote\n")
                }
            }
            if (info.apkSize.isNotBlank()) {
                append("\nSize: ${info.apkSize}")
            }
            append("\nTap to install the latest version.")
        }.trim()

        val largeIcon = runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.app_logo_transparent)
        }.getOrNull()

        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .apply {
                if (largeIcon != null) {
                    setLargeIcon(largeIcon)
                }
            }
            .setContentTitle("New LofiQ v${info.versionName} is Available! 🚀")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setColor(0xFF8B5CF6.toInt())
            .addAction(
                android.R.drawable.stat_sys_download,
                "Update Now",
                downloadPendingIntent
            )
            .build()

        try {
            notificationManager.notify(UPDATE_NOTIFICATION_ID, notification)
            Log.i(TAG, "Update notification displayed for version ${info.versionName}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed posting update notification: ${e.message}")
        }
    }

    /**
     * Dismisses the update notification from the notification panel.
     */
    fun dismissUpdateNotification(context: Context) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(UPDATE_NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to dismiss update notification: ${e.message}")
        }
    }

    /**
     * Schedules periodic background update checks via WorkManager.
     * Checks version metadata using negligible data (<3 KB) and alerts user if update is available.
     */
    fun schedulePeriodicUpdateCheck(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // Periodic background check every 15 minutes (minimum allowable by WorkManager)
            val periodicWork = PeriodicWorkRequestBuilder<AppUpdateWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC_UPDATE,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWork
            )
            Log.i(TAG, "Periodic background update check scheduled via WorkManager (15m interval)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed scheduling background update check: ${e.message}")
        }
    }

    /**
     * Launch the system PackageInstaller via FileProvider.
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            dismissUpdateNotification(context)
            val authority = "${context.packageName}.fileprovider"
            val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed launching APK installation intent", e)
        }
    }
}

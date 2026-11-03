package com.lofiq.core.update

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Background WorkManager worker that periodically checks for new LofiQ releases
 * even when the application is not actively open.
 *
 * Runs strictly with CONNECTED network constraint and consumes negligible data (~2.4 KB per check).
 * When a newer build is discovered, posts an actionable system notification with release highlights
 * and a direct "Update Now" trigger.
 */
class AppUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "AppUpdateWorker"
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AppUpdateWorkerEntryPoint {
        fun appUpdateManager(): AppUpdateManager
    }

    override suspend fun doWork(): Result {
        return try {
            if (com.lofiq.LofiQApp.isAppInForeground) {
                Log.d(TAG, "App is actively in foreground, skipping background update notification")
                return Result.success()
            }

            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                AppUpdateWorkerEntryPoint::class.java
            )
            val appUpdateManager = entryPoint.appUpdateManager()

            val result = appUpdateManager.checkForUpdateAndNotify(applicationContext)
            result.fold(
                onSuccess = { info ->
                    if (info.hasUpdate) {
                        Log.i(TAG, "New LofiQ update evaluated in background: v${info.versionName}")
                    } else {
                        Log.d(TAG, "Background update check completed: app is up to date.")
                    }
                    Result.success()
                },
                onFailure = { error ->
                    Log.w(TAG, "Background update check encountered an error: ${error.message}")
                    Result.retry()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in AppUpdateWorker", e)
            Result.retry()
        }
    }
}

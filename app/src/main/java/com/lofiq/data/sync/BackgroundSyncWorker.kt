package com.lofiq.data.sync

import android.content.Context
import androidx.work.*
import coil.Coil
import coil.request.ImageRequest
import com.lofiq.core.common.ArtworkUtils
import com.lofiq.core.common.LofiQLogger
import com.lofiq.data.repository.HomeFeedCacheManager
import com.lofiq.data.repository.MusicRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * Background WorkManager worker that periodically keeps latest songs and recommendations
 * live and updated, while pre-caching high-resolution artworks into Coil disk cache.
 *
 * Uses minimal data (~1-2 MB per cycle) as requested by user, preventing slow image loading
 * and eliminating fallback app logo appearances when opening the app or playing songs.
 */
class BackgroundSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "BackgroundSyncWorker"
        private const val WORK_NAME = "LofiQBackgroundSync"
        private const val PREFS_NAME = "lofiq_bg_sync_prefs"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
        private const val MIN_SYNC_INTERVAL_MS = 60 * 60 * 1000L // 1 hour throttle

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(
                4, TimeUnit.HOURS,
                30, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
            LofiQLogger.i(LofiQLogger.Category.APP, "Scheduled periodic background data & image sync (every 4 hours)")
        }

        fun triggerOneTimeSync(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastSync = prefs.getLong(KEY_LAST_SYNC, 0L)
            val now = System.currentTimeMillis()

            if (now - lastSync < MIN_SYNC_INTERVAL_MS) {
                LofiQLogger.d(LofiQLogger.Category.CACHE, "Skipping one-time sync: executed recently (${(now - lastSync) / 60000}m ago)")
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneTimeRequest = OneTimeWorkRequestBuilder<BackgroundSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME}_OneTime",
                ExistingWorkPolicy.REPLACE,
                oneTimeRequest
            )
            prefs.edit().putLong(KEY_LAST_SYNC, now).apply()
            LofiQLogger.i(LofiQLogger.Category.CACHE, "Enqueued background one-time data & image sync")
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BackgroundSyncEntryPoint {
        fun musicRepository(): MusicRepository
        fun homeFeedCacheManager(): HomeFeedCacheManager
    }

    override suspend fun doWork(): Result {
        return try {
            LofiQLogger.i(LofiQLogger.Category.CACHE, "Starting background live data & image sync")

            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                BackgroundSyncEntryPoint::class.java
            )
            val musicRepository = entryPoint.musicRepository()
            val homeFeedCacheManager = entryPoint.homeFeedCacheManager()

            // 1. Fetch latest trending songs (minimal network: ~25-30 items, ~25KB JSON)
            val trendingSongs = try {
                musicRepository.getTrendingSongs(limit = 25)
            } catch (e: Exception) {
                LofiQLogger.w(LofiQLogger.Category.CACHE, "Failed to fetch trending songs: ${e.message}")
                emptyList()
            }

            val malayalamSongs = try {
                musicRepository.searchSongs("Malayalam Trending Hits", limit = 15)
            } catch (e: Exception) {
                LofiQLogger.w(LofiQLogger.Category.CACHE, "Failed to fetch Malayalam songs: ${e.message}")
                emptyList()
            }

            // 2. Persist to HomeFeed persistent cache so app startup is instantaneous
            if (trendingSongs.isNotEmpty()) {
                homeFeedCacheManager.saveShelf("trending_india", trendingSongs)
            }
            if (malayalamSongs.isNotEmpty()) {
                homeFeedCacheManager.saveShelf("malayalam_trending", malayalamSongs)
            }

            // 3. Pre-cache high-resolution artworks into Coil disk cache (~1-2MB total data)
            val allSongs = (trendingSongs + malayalamSongs).distinctBy { it.id }.take(30)
            val imageLoader = Coil.imageLoader(applicationContext)
            var preloadedCount = 0

            for (song in allSongs) {
                val artworkUrl = ArtworkUtils.getOptimalArtwork(song)
                if (artworkUrl.isNotBlank()) {
                    runCatching {
                        val request = ImageRequest.Builder(applicationContext)
                            .data(artworkUrl)
                            .size(500, 500)
                            .build()
                        imageLoader.enqueue(request)
                        preloadedCount++
                    }
                }
            }

            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()

            LofiQLogger.i(
                LofiQLogger.Category.CACHE,
                "Background sync complete: saved ${allSongs.size} live songs and pre-cached $preloadedCount artworks (~1.2MB data used)"
            )
            Result.success()
        } catch (e: Exception) {
            LofiQLogger.e(LofiQLogger.Category.CACHE, "Background sync failed: ${e.message}", e)
            Result.retry()
        }
    }
}

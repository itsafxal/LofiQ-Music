package com.lofiq

import android.app.Activity
import android.app.Application
import android.os.Bundle
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.lofiq.core.common.DevicePerformanceManager
import com.lofiq.core.common.LocalAudioArtworkFetcher
import com.lofiq.core.common.LofiQLogger
import com.lofiq.core.update.AppUpdateManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LofiQApp : Application(), ImageLoaderFactory {

    @Inject
    lateinit var appUpdateManager: AppUpdateManager

    @Inject
    lateinit var playbackEngine: com.lofiq.playback.engine.PlaybackEngine

    @Inject
    lateinit var libraryRepository: com.lofiq.data.repository.LibraryRepository

    companion object {
        @Volatile
        var isAppInForeground: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase App Check with Play Integrity (Production) & Debug Provider (Development)
        runCatching {
            FirebaseApp.initializeApp(this)
            val firebaseAppCheck = FirebaseAppCheck.getInstance()
            val providerFactory: AppCheckProviderFactory = if (BuildConfig.DEBUG) {
                try {
                    val debugClass = Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
                    val method = debugClass.getMethod("getInstance")
                    method.invoke(null) as AppCheckProviderFactory
                } catch (_: Throwable) {
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                }
            } else {
                PlayIntegrityAppCheckProviderFactory.getInstance()
            }
            firebaseAppCheck.installAppCheckProviderFactory(providerFactory)
            LofiQLogger.i(LofiQLogger.Category.APP, "Firebase App Check initialized successfully")
        }.onFailure { e ->
            LofiQLogger.w(LofiQLogger.Category.APP, "Firebase App Check initialization deferred: ${e.message}")
        }
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("CRASH_LOFIQ", "FATAL CRASH in thread ${thread.name}: ${throwable.message}", throwable)
            try {
                val prefs = getSharedPreferences("lofiq_crash_state", android.content.Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("last_error", throwable.message ?: throwable.javaClass.simpleName)
                    .putString("last_stack_trace", android.util.Log.getStackTraceString(throwable))
                    .putLong("crash_timestamp", System.currentTimeMillis())
                    .commit()
            } catch (_: Throwable) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var startedActivities = 0
            private var isConfigurationChanging = false

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {
                if (++startedActivities == 1 && !isConfigurationChanging) {
                    isAppInForeground = true
                }
            }
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {
                isConfigurationChanging = activity.isChangingConfigurations
                if (--startedActivities == 0 && !isConfigurationChanging) {
                    isAppInForeground = false
                    runCatching {
                        com.lofiq.data.sync.BackgroundSyncWorker.triggerOneTimeSync(this@LofiQApp)
                    }
                }
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        // Ensure background periodic update check is always active via WorkManager
        runCatching {
            appUpdateManager.schedulePeriodicUpdateCheck(this)
        }

        // Ensure periodic live data & image prefetch is active in background (1-2MB data)
        runCatching {
            com.lofiq.data.sync.BackgroundSyncWorker.schedulePeriodicSync(this)
        }

        // Initialize Home Screen Music Widgets
        runCatching {
            com.lofiq.widget.MusicWidgetManager.init(
                context = this,
                playbackEngine = playbackEngine,
                libraryRepository = libraryRepository
            )
        }

        LofiQLogger.i(LofiQLogger.Category.APP, "LofiQ Application initialized")
    }

    override fun newImageLoader(): ImageLoader {
        val isLowRam = DevicePerformanceManager.isLowRamDevice(this)
        return ImageLoader.Builder(this)
            .components {
                add(LocalAudioArtworkFetcher.Factory(this@LofiQApp))
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(if (isLowRam) 0.15 else 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes((if (isLowRam) 50L else 250L) * 1024 * 1024)
                    .build()
            }
            .allowRgb565(isLowRam)
            .crossfade(!isLowRam)
            .build()
    }
}

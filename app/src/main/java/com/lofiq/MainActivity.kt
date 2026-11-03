package com.lofiq

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import com.lofiq.playback.engine.PlaybackEngine
import com.lofiq.playback.service.LofiQMediaSessionService
import com.lofiq.ui.navigation.MainScreen
import com.lofiq.ui.performance.resolvePerformanceRefreshRate
import com.lofiq.ui.performance.supportedPerformanceRefreshRates
import com.lofiq.ui.theme.LofiQTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var playbackEngine: PlaybackEngine

    @Inject
    lateinit var sleepTimerManager: com.lofiq.playback.timer.SleepTimerManager

    @Inject
    lateinit var downloadManager: com.lofiq.data.download.TrackDownloadManager

    @Inject
    lateinit var libraryRepository: com.lofiq.data.repository.LibraryRepository

    @Inject
    lateinit var musicRepository: com.lofiq.data.repository.MusicRepository

    @Inject
    lateinit var appUpdateManager: com.lofiq.core.update.AppUpdateManager

    @Inject
    lateinit var homeFeedCacheManager: com.lofiq.data.repository.HomeFeedCacheManager

    @Inject
    lateinit var listenTogetherManager: com.lofiq.feature.sync.ListenTogetherManager

    @Inject
    lateinit var spotifyImportHelper: com.lofiq.data.spotify.SpotifyImportHelper

    @Inject
    lateinit var appIconManager: com.lofiq.core.common.AppIconManager

    private var pendingUpdatePrompt by mutableStateOf(false)
    private var autoStartDownload by mutableStateOf(false)

    private val notificationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Permission handled; background worker will handle update alerts when app is closed/in background
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_LofiQ)
        window.setBackgroundDrawableResource(android.R.color.black)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        handleUpdateIntent(intent)

        // Pre-warm caches, ensure launcher alias validity, schedule background update checks, and silent auth
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { appIconManager.ensureValidLauncherAlias() }
            runCatching { homeFeedCacheManager.loadFromDisk() }
            runCatching { downloadManager.ensureLofiQDirectoryExists() }
            runCatching { appUpdateManager.schedulePeriodicUpdateCheck(applicationContext) }
            runCatching {
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                if (auth.currentUser == null) {
                    auth.signInAnonymously()
                }
            }
        }

        // Apply peak display refresh rate for fluid 90Hz / 120Hz animations safely
        runCatching {
            val display = window.decorView.display
            val maxRefreshRate = display?.supportedPerformanceRefreshRates()?.maxOrNull() ?: 60
            applyPerformanceMode(enabled = true, refreshRate = maxRefreshRate, composeView = window.decorView)
        }

        setContent {
            LofiQTheme {
                MainScreen(
                    playbackEngine = playbackEngine,
                    sleepTimerManager = sleepTimerManager,
                    downloadManager = downloadManager,
                    libraryRepository = libraryRepository,
                    musicRepository = musicRepository,
                    syncManager = listenTogetherManager,
                    appUpdateManager = appUpdateManager,
                    spotifyImportHelper = spotifyImportHelper,
                    pendingUpdatePrompt = pendingUpdatePrompt,
                    autoStartDownload = autoStartDownload,
                    onClearUpdateIntent = {
                        pendingUpdatePrompt = false
                        autoStartDownload = false
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUpdateIntent(intent)
    }

    private fun handleUpdateIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(com.lofiq.core.update.AppUpdateManager.EXTRA_SHOW_UPDATE, false) == true) {
            pendingUpdatePrompt = true
            if (intent.getBooleanExtra(com.lofiq.core.update.AppUpdateManager.EXTRA_START_DOWNLOAD, false)) {
                autoStartDownload = true
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            val syncState = listenTogetherManager.syncState.value
            if (syncState is com.lofiq.feature.sync.ListenTogetherState.Connected && !syncState.isHost) {
                com.lofiq.core.common.LofiQLogger.i(
                    com.lofiq.core.common.LofiQLogger.Category.PLAYBACK,
                    "[ACTIVITY] onDestroy while joiner connected -> leaving room"
                )
                listenTogetherManager.leaveRoom()
            }
        }
        super.onDestroy()
    }

    private fun applyPerformanceMode(enabled: Boolean, refreshRate: Int, composeView: View) {
        runCatching {
            val display = composeView.display
            val supportedRefreshRate = display?.resolvePerformanceRefreshRate(refreshRate) ?: refreshRate
            window.attributes = window.attributes.apply {
                preferredRefreshRate = if (enabled) supportedRefreshRate.toFloat() else 0f
            }
            if (Build.VERSION.SDK_INT >= 35) {
                Api35PerformanceHelper.applyFrameRate(window, composeView, enabled, supportedRefreshRate.toFloat())
            }
        }
    }

    @androidx.annotation.RequiresApi(35)
    private object Api35PerformanceHelper {
        fun applyFrameRate(window: android.view.Window, composeView: View, enabled: Boolean, rate: Float) {
            runCatching {
                window.setFrameRatePowerSavingsBalanced(!enabled)
                composeView.requestedFrameRate = if (enabled) rate else View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT
            }
        }
    }
}

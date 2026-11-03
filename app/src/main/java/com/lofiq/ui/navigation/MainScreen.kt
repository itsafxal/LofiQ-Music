package com.lofiq.ui.navigation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lofiq.auth.AuthRepository
import com.lofiq.auth.UserAccount
import com.lofiq.feature.equalizer.AudioEffectsController
import com.lofiq.feature.equalizer.EqualizerScreen
import com.lofiq.feature.explore.ExploreScreen
import com.lofiq.feature.home.HomeScreen
import com.lofiq.feature.library.LibraryScreen
import com.lofiq.feature.local.LocalMusicScreen
import com.lofiq.feature.miniplayer.MiniPlayer
import com.lofiq.feature.player.FullPlayerSheet
import com.lofiq.feature.profile.ProfileScreen
import com.lofiq.feature.profile.ProfileViewModel
import com.lofiq.feature.search.SearchScreen
import com.lofiq.feature.developer.DeveloperScreen
import com.lofiq.feature.settings.AppearanceThemeScreen
import com.lofiq.feature.settings.AppIconStudioScreen
import com.lofiq.feature.settings.SeekbarStyleScreen
import com.lofiq.feature.settings.SettingsScreen
import com.lofiq.ui.components.AmbientAppAtmosphere
import com.lofiq.playback.engine.PlaybackEngine
import com.lofiq.feature.sync.ListenTogetherBottomSheet
import com.lofiq.feature.sync.ListenTogetherMode
import com.lofiq.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    playbackEngine: PlaybackEngine,
    sleepTimerManager: com.lofiq.playback.timer.SleepTimerManager? = null,
    downloadManager: com.lofiq.data.download.TrackDownloadManager? = null,
    libraryRepository: com.lofiq.data.repository.LibraryRepository? = null,
    musicRepository: com.lofiq.data.repository.MusicRepository? = null,
    syncManager: com.lofiq.feature.sync.ListenTogetherManager? = null,
    appUpdateManager: com.lofiq.core.update.AppUpdateManager? = null,
    spotifyImportHelper: com.lofiq.data.spotify.SpotifyImportHelper? = null,
    pendingUpdatePrompt: Boolean = false,
    autoStartDownload: Boolean = false,
    onClearUpdateIntent: () -> Unit = {},
    profileViewModel: ProfileViewModel = hiltViewModel(),
    settingsViewModel: com.lofiq.feature.settings.SettingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: NavScreen.HOME.route

    val completedImport by (spotifyImportHelper?.lastCompletedImport ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsState()
    val playerState by playbackEngine.playerState.collectAsState()
    val userAccount by profileViewModel.authRepository.userAccount.collectAsStateWithLifecycle(initialValue = UserAccount.GUEST)
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    var isFullPlayerVisible by remember { mutableStateOf(false) }
    var showListenTogetherSheet by remember { mutableStateOf(false) }
    var listenTogetherInitialMode by remember { mutableStateOf(ListenTogetherMode.CHOICE) }
    val effectsController = remember { AudioEffectsController(context.applicationContext) }
    val activeSyncManager = syncManager ?: remember { com.lofiq.feature.sync.ListenTogetherManager(com.lofiq.core.common.DefaultDispatcherProvider()) }

    // Automatic In-App Update Checking State on Launch
    var updateInfo by remember { mutableStateOf<com.lofiq.core.update.UpdateInfo?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var updateDownloadProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        if (appUpdateManager != null) {
            val result = appUpdateManager.checkForUpdate()
            result.onSuccess { info ->
                if (info.hasUpdate && appUpdateManager.shouldPromptAutomaticUpdate(context, info)) {
                    updateInfo = info
                    appUpdateManager.dismissUpdateNotification(context)
                }
            }
        }
    }

    LaunchedEffect(pendingUpdatePrompt, autoStartDownload) {
        if (pendingUpdatePrompt && appUpdateManager != null) {
            val currentInfo = updateInfo
            if (currentInfo != null && currentInfo.hasUpdate) {
                if (autoStartDownload && !isDownloadingUpdate) {
                    isDownloadingUpdate = true
                    updateDownloadProgress = 0f
                    coroutineScope.launch {
                        val result = appUpdateManager.downloadApk(context, currentInfo.apkUrl, currentInfo.checksum) { progress ->
                            updateDownloadProgress = progress
                        }
                        result.onSuccess { file ->
                            isDownloadingUpdate = false
                            updateInfo = null
                            appUpdateManager.dismissUpdateNotification(context)
                            appUpdateManager.clearDismissedUpdate(context)
                            appUpdateManager.installApk(context, file)
                        }.onFailure {
                            isDownloadingUpdate = false
                            Toast.makeText(context, "Download failed: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                val result = appUpdateManager.checkForUpdate()
                result.onSuccess { info ->
                    if (info.hasUpdate) {
                        updateInfo = info
                        if (autoStartDownload && !isDownloadingUpdate) {
                            isDownloadingUpdate = true
                            updateDownloadProgress = 0f
                            coroutineScope.launch {
                                val downloadResult = appUpdateManager.downloadApk(context, info.apkUrl, info.checksum) { progress ->
                                    updateDownloadProgress = progress
                                }
                                downloadResult.onSuccess { file ->
                                    isDownloadingUpdate = false
                                    updateInfo = null
                                    appUpdateManager.dismissUpdateNotification(context)
                                    appUpdateManager.clearDismissedUpdate(context)
                                    appUpdateManager.installApk(context, file)
                                }.onFailure {
                                    isDownloadingUpdate = false
                                    Toast.makeText(context, "Download failed: ${it.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }
            }
            onClearUpdateIntent()
        }
    }

    LaunchedEffect(playbackEngine, activeSyncManager) {
        activeSyncManager.attachPlaybackEngine(playbackEngine)
    }

    BackHandler(enabled = isFullPlayerVisible) {
        isFullPlayerVisible = false
    }

    LaunchedEffect(playerState.isReady) {
        val sessionId = playbackEngine.exoPlayer.audioSessionId
        if (sessionId != 0) {
            effectsController.attachSession(sessionId)
        }
    }

    val isLowSpecDevice = remember { com.lofiq.core.common.DevicePerformanceManager.isLowSpecDevice(context) }
    val effectivePerformanceMode = settingsState.performanceModeEnabled || isLowSpecDevice
    val effectiveLiquidGlass = !settingsState.performanceModeEnabled && settingsState.liquidGlassEnabled && !isLowSpecDevice

    CompositionLocalProvider(
        LocalLiquidGlassEnabled provides effectiveLiquidGlass,
        LocalFullScreenArtworkEnabled provides settingsState.fullScreenArtworkEnabled,
        LocalAppIconTheme provides settingsState.appIconTheme,
        LocalBrandColor provides AccentWhite,
        LocalSeekbarStyle provides settingsState.seekbarStyle,
        LocalPerformanceModeEnabled provides effectivePerformanceMode
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(BackgroundBlack)
        ) {
        // Dynamic Atmospheric Song Theme Backdrop
        AmbientAppAtmosphere(
            currentSong = playerState.currentSong
        )

        // Fullscreen edge-to-edge content: screens scroll seamlessly behind floating controls
        NavHost(
            navController = navController,
            startDestination = NavScreen.HOME.route,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                val fromRoute = initialState.destination.route
                val toRoute = targetState.destination.route
                val tabOrder = listOf(NavScreen.HOME.route, NavScreen.LIBRARY.route, NavScreen.SEARCH.route, NavScreen.PROFILE.route)
                val fromIdx = tabOrder.indexOf(fromRoute)
                val toIdx = tabOrder.indexOf(toRoute)

                if (effectivePerformanceMode) {
                    fadeIn(animationSpec = tween(120))
                } else if (fromIdx != -1 && toIdx != -1) {
                    if (toIdx > fromIdx) {
                        fadeIn(animationSpec = tween(220, easing = LinearOutSlowInEasing)) +
                            slideInHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) { (it * 0.08f).toInt() }
                    } else {
                        fadeIn(animationSpec = tween(220, easing = LinearOutSlowInEasing)) +
                            slideInHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) { (-it * 0.08f).toInt() }
                    }
                } else {
                    fadeIn(animationSpec = tween(200)) +
                        slideInVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) { (it * 0.06f).toInt() }
                }
            },
            exitTransition = {
                val fromRoute = initialState.destination.route
                val toRoute = targetState.destination.route
                val tabOrder = listOf(NavScreen.HOME.route, NavScreen.LIBRARY.route, NavScreen.SEARCH.route, NavScreen.PROFILE.route)
                val fromIdx = tabOrder.indexOf(fromRoute)
                val toIdx = tabOrder.indexOf(toRoute)

                if (effectivePerformanceMode) {
                    fadeOut(animationSpec = tween(100))
                } else if (fromIdx != -1 && toIdx != -1) {
                    if (toIdx > fromIdx) {
                        fadeOut(animationSpec = tween(180, easing = FastOutLinearInEasing)) +
                            slideOutHorizontally(animationSpec = tween(180, easing = FastOutLinearInEasing)) { (-it * 0.08f).toInt() }
                    } else {
                        fadeOut(animationSpec = tween(180, easing = FastOutLinearInEasing)) +
                            slideOutHorizontally(animationSpec = tween(180, easing = FastOutLinearInEasing)) { (it * 0.08f).toInt() }
                    }
                } else {
                    fadeOut(animationSpec = tween(160)) +
                        slideOutVertically(animationSpec = tween(160)) { (-it * 0.06f).toInt() }
                }
            },
            popEnterTransition = {
                if (effectivePerformanceMode) {
                    fadeIn(animationSpec = tween(120))
                } else {
                    fadeIn(animationSpec = tween(200)) +
                        slideInVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) { (-it * 0.06f).toInt() }
                }
            },
            popExitTransition = {
                if (effectivePerformanceMode) {
                    fadeOut(animationSpec = tween(100))
                } else {
                    fadeOut(animationSpec = tween(160)) +
                        slideOutVertically(animationSpec = tween(160)) { (it * 0.06f).toInt() }
                }
            }
        ) {
            composable(NavScreen.HOME.route) {
                HomeScreen(
                    onNavigateToSearch = {
                        navController.navigate(NavScreen.SEARCH.route)
                    },
                    onNavigateToSettings = {
                        navController.navigate(NavScreen.PROFILE.route)
                    },
                    onOpenListenTogether = {
                        listenTogetherInitialMode = ListenTogetherMode.JOIN
                        showListenTogetherSheet = true
                    }
                )
            }
            composable(NavScreen.LIBRARY.route) {
                LibraryScreen(
                    onNavigateToLocalMusic = {
                        navController.navigate("local_music")
                    },
                    onNavigateToSettings = {
                        navController.navigate(NavScreen.PROFILE.route)
                    }
                )
            }
            composable(NavScreen.SEARCH.route) {
                SearchScreen(
                    onSongPlay = { isFullPlayerVisible = true }
                )
            }
            composable(NavScreen.PROFILE.route) {
                ProfileScreen(
                    onNavigateBack = {
                        val popped = navController.popBackStack(NavScreen.HOME.route, inclusive = false)
                        if (!popped) {
                            navController.popBackStack()
                        }
                    },
                    onNavigateToEqualizer = { navController.navigate("equalizer") },
                    onNavigateToDiagnostics = { navController.navigate("resolver_diagnostics") },
                    onNavigateToLocalMusic = { navController.navigate("local_music") },
                    onNavigateToAppearance = { navController.navigate("appearance_theme") },
                    onNavigateToDeveloper = { navController.navigate("developer") },
                    hasActiveSong = playerState.currentSong != null
                )
            }
            composable("explore") {
                ExploreScreen(
                    onNavigateToSettings = {
                        navController.navigate(NavScreen.PROFILE.route)
                    }
                )
            }
            composable(
                route = "search_query/{query}",
                arguments = listOf(navArgument("query") { type = NavType.StringType })
            ) { backStackEntry ->
                val query = backStackEntry.arguments?.getString("query").orEmpty()
                SearchScreen(
                    initialQuery = query,
                    onSongPlay = { isFullPlayerVisible = true }
                )
            }
            composable("settings") {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEqualizer = { navController.navigate("equalizer") },
                    onNavigateToLocalMusic = { navController.navigate("local_music") },
                    onNavigateToDiagnostics = { navController.navigate("resolver_diagnostics") },
                    onNavigateToAppearance = { navController.navigate("appearance_theme") },
                    onNavigateToSeekbarStyle = { navController.navigate("seekbar_style") },
                    onNavigateToDeveloper = { navController.navigate("developer") },
                    hasActiveSong = playerState.currentSong != null
                )
            }
            composable("developer") {
                DeveloperScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDiagnostics = { navController.navigate("resolver_diagnostics") },
                    hasActiveSong = playerState.currentSong != null
                )
            }
            composable("appearance_theme") {
                AppearanceThemeScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAppIconTheme = { navController.navigate("app_icon_theme") },
                    onNavigateToSeekbarStyle = { navController.navigate("seekbar_style") },
                    viewModel = settingsViewModel
                )
            }
            composable("seekbar_style") {
                SeekbarStyleScreen(
                    onNavigateBack = { navController.popBackStack() },
                    viewModel = settingsViewModel
                )
            }
            composable("app_icon_theme") {
                AppIconStudioScreen(
                    onNavigateBack = { navController.popBackStack() },
                    hasActiveSong = playerState.currentSong != null,
                    viewModel = settingsViewModel
                )
            }
            composable("equalizer") {
                EqualizerScreen(
                    effectsController = effectsController,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("local_music") {
                LocalMusicScreen(
                    playbackEngine = playbackEngine,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("resolver_diagnostics") {
                androidx.media3.common.util.UnstableApi::class.java.let {
                    com.lofiq.feature.debug.ResolverDiagnosticsScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }

        // Floating Bottom Controls Overlay (MiniPlayer + Frosted Capsule BottomNavBar)
        val isBottomBarRoute = currentRoute in listOf(
            NavScreen.HOME.route,
            NavScreen.LIBRARY.route,
            NavScreen.SEARCH.route,
            NavScreen.PROFILE.route,
            "explore"
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            // Persistent Mini Player above Bottom Nav Bar with fluid shared-element entrance/exit
            AnimatedVisibility(
                visible = playerState.currentSong != null && !isFullPlayerVisible,
                enter = if (effectivePerformanceMode) {
                    slideInVertically(initialOffsetY = { it }) + fadeIn(tween(150))
                } else {
                    slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)
                    ) + fadeIn(tween(220))
                },
                exit = if (effectivePerformanceMode) {
                    slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(120))
                } else {
                    slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(180)
                    ) + fadeOut(tween(180))
                }
            ) {
                MiniPlayer(
                    playerState = playerState,
                    onPlayPauseClick = { playbackEngine.togglePlayPause() },
                    onNextClick = { playbackEngine.next() },
                    onClick = { isFullPlayerVisible = true },
                    onDismiss = {
                        val isSyncConnected = activeSyncManager.syncState.value is com.lofiq.feature.sync.ListenTogetherState.Connected
                        if (isSyncConnected) {
                            activeSyncManager.leaveRoom()
                        }
                        playbackEngine.stop()
                    },
                    modifier = if (!isBottomBarRoute) {
                        Modifier
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp)
                    } else {
                        Modifier
                    }
                )
            }

            // Bottom Navigation Bar (Home, Library, Search, Profile)
            if (isBottomBarRoute) {
                BottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = { screen ->
                        if (currentRoute != screen.route) {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    userAvatarUrl = userAccount.avatarUrl,
                    isLoggedIn = userAccount.isLoggedIn
                )
            }
        }

        // Animated Fullscreen Player Overlay with fluid spring physics & shared-element feel
        AnimatedVisibility(
            visible = isFullPlayerVisible && playerState.currentSong != null,
            enter = if (effectivePerformanceMode) {
                slideInVertically(initialOffsetY = { it }, animationSpec = tween(240)) + fadeIn(tween(200))
            } else {
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)
                ) + fadeIn(tween(260))
            },
            exit = if (effectivePerformanceMode) {
                slideOutVertically(targetOffsetY = { it }, animationSpec = tween(180)) + fadeOut(tween(150))
            } else {
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = spring(dampingRatio = 0.95f, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium)
                ) + fadeOut(tween(220))
            }
        ) {
            FullPlayerSheet(
                playbackEngine = playbackEngine,
                sleepTimerManager = sleepTimerManager,
                effectsController = effectsController,
                downloadManager = downloadManager,
                libraryRepository = libraryRepository,
                musicRepository = musicRepository,
                syncManager = activeSyncManager,
                seekbarStyle = settingsState.seekbarStyle,
                onSeekbarStyleChange = { settingsViewModel.setSeekbarStyle(it) },
                onOpenEqualizer = {
                    isFullPlayerVisible = false
                    navController.navigate("equalizer")
                },
                onDismiss = { isFullPlayerVisible = false }
            )
        }

        // Listen Together Bottom Sheet (Triggered from Quick Pill or Other Entry Points)
        if (showListenTogetherSheet) {
            ListenTogetherBottomSheet(
                syncManager = activeSyncManager,
                initialMode = listenTogetherInitialMode,
                onDismiss = { showListenTogetherSheet = false }
            )
        }

        // Automated In-App Update Prompt Dialog on App Launch
        if (updateInfo != null) {
            val update = updateInfo!!
            AlertDialog(
                onDismissRequest = {
                    if (!isDownloadingUpdate) {
                        updateInfo = null
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.CloudDownload,
                            contentDescription = null,
                            tint = settingsState.activeBrandColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "New Update Available!",
                            color = AccentWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "v${update.versionName} (${update.apkSize})",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            if (update.releaseDate.isNotBlank()) {
                                Text(
                                    text = update.releaseDate,
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "What's New:",
                            color = AccentWhite,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            update.releaseNotes.forEach { note ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "•",
                                        color = settingsState.activeBrandColor,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(
                                        text = note,
                                        color = TextSecondary,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }

                        if (isDownloadingUpdate) {
                            Spacer(modifier = Modifier.height(16.dp))
                            LinearProgressIndicator(
                                progress = { updateDownloadProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = settingsState.activeBrandColor,
                                trackColor = SurfaceElevated
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Downloading update... ${(updateDownloadProgress * 100).toInt()}%",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    val confirmContentColor = if (settingsState.activeBrandColor.luminance() > 0.5f) Color.Black else Color.White
                    Button(
                        onClick = {
                            if (appUpdateManager != null) {
                                isDownloadingUpdate = true
                                updateDownloadProgress = 0f
                                coroutineScope.launch {
                                    val result = appUpdateManager.downloadApk(context, update.apkUrl, update.checksum) { progress ->
                                        updateDownloadProgress = progress
                                    }
                                    result.onSuccess { file ->
                                        isDownloadingUpdate = false
                                        updateInfo = null
                                        appUpdateManager.dismissUpdateNotification(context)
                                        appUpdateManager.clearDismissedUpdate(context)
                                        appUpdateManager.installApk(context, file)
                                    }.onFailure {
                                        isDownloadingUpdate = false
                                        Toast.makeText(context, "Download failed: ${it.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        enabled = !isDownloadingUpdate,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = settingsState.activeBrandColor,
                            contentColor = confirmContentColor,
                            disabledContainerColor = settingsState.activeBrandColor.copy(alpha = 0.5f),
                            disabledContentColor = confirmContentColor.copy(alpha = 0.7f)
                        ),
                        modifier = Modifier.height(42.dp)
                    ) {
                        if (isDownloadingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = confirmContentColor
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = confirmContentColor
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isDownloadingUpdate) "Downloading..." else "Update Now",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = confirmContentColor
                        )
                    }
                },
                dismissButton = {
                    if (!isDownloadingUpdate) {
                        OutlinedButton(
                            onClick = {
                                appUpdateManager?.markUpdateDismissed(context, update.versionName)
                                updateInfo = null
                            },
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, SurfaceBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = TextSecondary
                            ),
                            modifier = Modifier.height(42.dp)
                        ) {
                            Text(
                                text = "Later",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp),
                containerColor = Color(0xFF141416)
            )
        }

        // Global Celebration Pop-up when Background Playlist Import Completes
        if (completedImport != null) {
            val success = completedImport!!
            com.lofiq.feature.spotify.PlaylistImportSuccessDialog(
                playlistName = success.playlistName,
                importedCount = success.importedCount,
                onDismiss = {
                    spotifyImportHelper?.clearCompletedImport()
                    spotifyImportHelper?.resetImportStatus()
                },
                onOpenPlaylist = {
                    spotifyImportHelper?.clearCompletedImport()
                    spotifyImportHelper?.resetImportStatus()
                    navController.navigate(NavScreen.LIBRARY.route) {
                        popUpTo(NavScreen.HOME.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}
}

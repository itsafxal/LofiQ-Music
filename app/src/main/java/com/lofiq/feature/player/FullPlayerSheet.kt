package com.lofiq.feature.player

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.Coil
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lofiq.core.common.TimeFormatUtils
import com.lofiq.core.common.rememberCurrentAudioDevice
import com.lofiq.core.model.PlaybackRepeatMode
import com.lofiq.data.lyrics.model.LyricsSyncType
import com.lofiq.feature.lyrics.LyricsSheet
import com.lofiq.feature.queue.QueueSheet
import com.lofiq.playback.engine.PlaybackEngine
import com.lofiq.ui.components.AnimatedWaveformSeekbar
import com.lofiq.ui.components.seekbar.CustomPlayerSeekbar
import com.lofiq.ui.components.DynamicArtworkBackground
import com.lofiq.ui.components.ThinSlider
import com.lofiq.ui.haptics.Haptic
import com.lofiq.ui.haptics.rememberHaptics
import com.lofiq.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import com.lofiq.ui.animation.LiquidPlayPauseButton
import com.lofiq.ui.animation.liquidPressEffect
import com.lofiq.ui.theme.isLiquidGlassActive
import com.lofiq.ui.theme.LocalPerformanceModeEnabled

/**
 * Production LofiQ Unified Artwork Player:
 *
 * 1. FULL-SCREEN DYNAMIC BACKDROP: Extracted artwork palette mesh & soft blurred photographic atmosphere
 * 2. SHARP HERO ARTWORK: Edge-to-edge at top, with progressive DstIn multi-stop dissolution
 * 3. FLOATING TOP BAR: Translucent collapse chevron, "PLAYING FROM", and Queue action
 * 4. METADATA & ACTIONS: Prominent title, artist, favorite chip, and more options menu
 * 5. LYRICS / STATUS LINE: Subtle status caption above progress bar
 * 6. RESPONSIVE SEEKBAR: Hairline ThinSlider with elapsed and remaining (-X:XX) timestamps
 * 7. TRANSPORT CONTROLS: Shuffle, Previous, Dominant Circular Play/Pause, Next, Repeat
 * 8. VOLUME SLIDER ROW: System volume ThinSlider with volume icons
 * 9. SECONDARY NAVIGATION: Lyrics icon, Frosted Capsule (Headphones / Artist), and Queue button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerSheet(
    playbackEngine: PlaybackEngine,
    sleepTimerManager: com.lofiq.playback.timer.SleepTimerManager? = null,
    effectsController: com.lofiq.feature.equalizer.AudioEffectsController? = null,
    downloadManager: com.lofiq.data.download.TrackDownloadManager? = null,
    libraryRepository: com.lofiq.data.repository.LibraryRepository? = null,
    musicRepository: com.lofiq.data.repository.MusicRepository? = null,
    syncManager: com.lofiq.feature.sync.ListenTogetherManager? = null,
    seekbarStyle: com.lofiq.core.model.SeekbarStyle = com.lofiq.ui.theme.LocalSeekbarStyle.current,
    onSeekbarStyleChange: ((com.lofiq.core.model.SeekbarStyle) -> Unit)? = null,
    onOpenEqualizer: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val playerState by playbackEngine.playerState.collectAsState()
    val currentLyrics by playbackEngine.currentLyrics.collectAsState()
    val isLyricsLoading by playbackEngine.isLyricsLoading.collectAsState()
    val timerState by (sleepTimerManager?.timerState ?: MutableStateFlow(com.lofiq.playback.timer.SleepTimerState())).collectAsState()
    val downloadMap by (downloadManager?.downloads ?: MutableStateFlow(emptyMap())).collectAsState()

    val (audioDevice, openAudioPicker) = rememberCurrentAudioDevice(playbackEngine.audioRoutingManager)

    // Automatically sync active audio output device in real-time while FullPlayerSheet is open
    LaunchedEffect(Unit) {
        while (isActive) {
            playbackEngine.audioRoutingManager.refreshRoutes()
            kotlinx.coroutines.delay(1500)
        }
    }

    val currentSong = playerState.currentSong ?: return

    val isFavoriteFlow = remember(currentSong.id) {
        libraryRepository?.isFavorite(currentSong.id) ?: kotlinx.coroutines.flow.flowOf(currentSong.isFavorite)
    }
    val isFavorite by isFavoriteFlow.collectAsState(initial = currentSong.isFavorite)

    val downloadInfo = downloadMap[currentSong.id] ?: com.lofiq.data.download.TrackDownloadInfo(currentSong.id)
    val isDownloaded = downloadInfo.state == com.lofiq.data.download.DownloadState.COMPLETED || (downloadManager?.isDownloaded(currentSong) == true)
    val isDownloading = downloadInfo.state == com.lofiq.data.download.DownloadState.DOWNLOADING
    val downloadProgress = downloadInfo.progress

    var showQueueSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    var showActionsSheet by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showAlbumSheet by remember { mutableStateOf(false) }
    var showListenTogetherSheet by remember { mutableStateOf(false) }
    var showSeekbarStyleSheet by remember { mutableStateOf(false) }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            downloadManager?.downloadSong(currentSong, saveToPublicStorage = true)
            Toast.makeText(
                context,
                "Downloading '${currentSong.title}' to Music/LofiQ...",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            // Permission denied: save inside app storage as requested
            downloadManager?.downloadSong(currentSong, saveToPublicStorage = false)
            Toast.makeText(
                context,
                "Storage permission denied: saving '${currentSong.title}' to in-app storage",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    BackHandler {
        if (showQueueSheet) {
            showQueueSheet = false
        } else if (showSeekbarStyleSheet) {
            showSeekbarStyleSheet = false
        } else if (showLyricsSheet) {
            showLyricsSheet = false
        } else if (showActionsSheet) {
            showActionsSheet = false
        } else if (showEqualizerSheet) {
            showEqualizerSheet = false
        } else if (showSleepTimerSheet) {
            showSleepTimerSheet = false
        } else if (showAddToPlaylistDialog) {
            showAddToPlaylistDialog = false
        } else if (showAlbumSheet) {
            showAlbumSheet = false
        } else if (showListenTogetherSheet) {
            showListenTogetherSheet = false
        } else {
            onDismiss()
        }
    }

    // User seeking state
    var isUserSeeking by remember { mutableStateOf(false) }
    var seekPositionMs by remember { mutableFloatStateOf(0f) }

    // Gesture drag tracking
    var swipeOffsetX by remember { mutableFloatStateOf(0f) }
    var dismissDragY by remember { mutableFloatStateOf(0f) }
    var totalDragX by remember { mutableFloatStateOf(0f) }
    var totalDragY by remember { mutableFloatStateOf(0f) }

    val optimalArtworkUrl = remember(currentSong.artworkUrl, currentSong.highResArtworkUrl, currentSong.id) {
        com.lofiq.core.common.ArtworkUtils.getOptimalArtwork(currentSong)
    }

    val fallbackArtworkUrl = remember(currentSong.id, optimalArtworkUrl) {
        com.lofiq.core.common.ArtworkUtils.getFallbackArtwork(currentSong)
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    var extractedPalette by remember { mutableStateOf<ArtworkAtmospherePalette?>(null) }

    LaunchedEffect(optimalArtworkUrl) {
        if (optimalArtworkUrl.isBlank()) {
            extractedPalette = null
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val loader = Coil.imageLoader(context)
                val req = ImageRequest.Builder(context)
                    .data(optimalArtworkUrl)
                    .size(128, 128)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(req)
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    extractedPalette = ArtworkColorUtils.extractAtmospherePalette(bitmap)
                } else {
                    extractedPalette = null
                }
            } catch (_: Exception) {
                extractedPalette = null
            }
        }
    }

    val isFullScreenArtwork = LocalFullScreenArtworkEnabled.current
    val isLiquidGlass = isLiquidGlassActive()
    val isPerfMode = LocalPerformanceModeEnabled.current
    val brandColor = LocalBrandColor.current
    val activeGlowColor = extractedPalette?.vibrant ?: brandColor

    val animatedSwipeX by animateFloatAsState(
        targetValue = swipeOffsetX,
        animationSpec = if (isPerfMode) spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessHigh)
        else spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
        label = "fullPlayerSwipeX"
    )

    val animatedDismissY by animateFloatAsState(
        targetValue = dismissDragY,
        animationSpec = spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
        label = "fullPlayerDismissY"
    )

    val playingAuraScale by animateFloatAsState(
        targetValue = if (playerState.isPlaying && !isPerfMode) 1.0f else 0.95f,
        animationSpec = spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
        label = "playingAuraScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .offset { IntOffset(0, animatedDismissY.roundToInt()) }
            .graphicsLayer {
                val progress = (animatedDismissY / 1000f).coerceIn(0f, 0.12f)
                scaleX = 1f - progress
                scaleY = 1f - progress
                if (animatedDismissY > 5f) {
                    clip = true
                    shape = RoundedCornerShape((progress * 260f).dp)
                }
            }
            .background(BackgroundBlack)
            .pointerInput(currentSong.id) {
                detectDragGestures(
                    onDragStart = {
                        swipeOffsetX = 0f
                        dismissDragY = 0f
                        totalDragX = 0f
                        totalDragY = 0f
                    },
                    onDragEnd = {
                        if (abs(totalDragX) > abs(totalDragY)) {
                            // Horizontal Swipe -> Change Song
                            if (swipeOffsetX < -70f) {
                                haptics.play(Haptic.SkipNext)
                                playbackEngine.next()
                            } else if (swipeOffsetX > 70f) {
                                haptics.play(Haptic.SkipPrevious)
                                playbackEngine.previous()
                            }
                        } else {
                            // Vertical Swipe -> Dismiss or Open Queue from anywhere on player screen
                            if (totalDragY > 100f) {
                                onDismiss()
                            } else if (totalDragY < -45f) {
                                haptics.play(Haptic.Expand)
                                showQueueSheet = true
                            }
                        }
                        swipeOffsetX = 0f
                        dismissDragY = 0f
                        totalDragX = 0f
                        totalDragY = 0f
                    },
                    onDragCancel = {
                        swipeOffsetX = 0f
                        dismissDragY = 0f
                        totalDragX = 0f
                        totalDragY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y
                        if (abs(totalDragX) > abs(totalDragY)) {
                            change.consume()
                            swipeOffsetX += dragAmount.x * 0.85f
                        } else {
                            change.consume()
                            if (totalDragY > 0f) {
                                dismissDragY = totalDragY
                            }
                        }
                    }
                )
            }
    ) {
        // =========================================================================
        // LAYER 1: Full-Screen Artwork-Driven Atmosphere Backdrop
        // =========================================================================
        DynamicArtworkBackground(
            artworkUrl = optimalArtworkUrl,
            palette = extractedPalette
        )

        // =========================================================================
        // LAYER 2: Sharp Hero Artwork (Fixed Edge-to-Edge at Top with Progressive Dissolution)
        // =========================================================================
        if (isFullScreenArtwork && optimalArtworkUrl.isNotBlank()) {
            Crossfade(
                targetState = optimalArtworkUrl,
                animationSpec = tween(
                    durationMillis = 500,
                    easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
                ),
                label = "SharpHeroArtworkCrossfade"
            ) { targetUrl ->
                if (targetUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(targetUrl)
                            .crossfade(250)
                            .build(),
                        contentDescription = "Artwork for ${currentSong.title}",
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .fillMaxHeight(0.50f)
                            .graphicsLayer {
                                // Scale up 1.34x to eliminate any 4:3 or 16:9 pillarbox side borders baked into thumbnails
                                scaleX = 1.34f
                                scaleY = 1.34f
                                transformOrigin = TransformOrigin(0.5f, 0.35f)
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithContent {
                                drawContent()

                                // LofiQ HERO_FADE_FRACTION (0.42f) progressive dissolution
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color.Black, Color.Transparent),
                                        startY = size.height * 0.58f,
                                        endY = size.height
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                    )
                }
            }
        }

        // =========================================================================
        // LAYER 3: Top Status Bar Scrim (Ensures Status Bar Legibility)
        // =========================================================================
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(statusBarTop + 44.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.45f),
                            Color.Transparent
                        )
                    )
                )
        )

        // =========================================================================
        // LAYER 4: Player UI Content Deck
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Header Bar: Drag Pill, Collapse, Origin Caption & More Options (Matching Reference)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Top drag/collapse pill indicator
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.45f))
                        .clickable { onDismiss() }
                )

                if (!isFullScreenArtwork) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Collapse Player",
                                tint = AccentWhite,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = "NOW PLAYING",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.SansSerif,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                )
                            )
                            Text(
                                text = if (currentSong.album.isNotBlank()) currentSong.album else "Search",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = FontFamily.SansSerif,
                                    color = Color.White.copy(alpha = 0.60f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = { showActionsSheet = true },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "More Options",
                                tint = AccentWhite,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            if (isFullScreenArtwork) {
                // Flexible space letting sharp hero artwork shine in upper half
                Spacer(modifier = Modifier.weight(1f))
            } else {
                // Floating Rounded Square Poster Card over blurred dynamic atmosphere
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .graphicsLayer {
                            translationX = if (!isPerfMode) animatedSwipeX else 0f
                            rotationZ = if (!isPerfMode) (animatedSwipeX / 42f).coerceIn(-7f, 7f) else 0f
                            alpha = if (!isPerfMode) 1f - (abs(animatedSwipeX) / 800f).coerceIn(0f, 0.35f) else 1f
                            scaleX = playingAuraScale
                            scaleY = playingAuraScale
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Crossfade(
                        targetState = optimalArtworkUrl,
                        animationSpec = tween(
                            durationMillis = 400,
                            easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
                        ),
                        label = "PosterCardCrossfade"
                    ) { targetUrl ->
                        if (isLiquidGlass) {
                            // Premium Liquid Glass Frosted Glassmorphism Chassis with Ambient Halo & Specular Border
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.88f)
                                    .aspectRatio(1f)
                                    .shadow(
                                        elevation = 28.dp,
                                        shape = RoundedCornerShape(28.dp),
                                        spotColor = activeGlowColor.copy(alpha = 0.50f),
                                        ambientColor = Color.Black.copy(alpha = 0.70f)
                                    )
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(Color(0xFF14141E).copy(alpha = 0.62f))
                                    .border(
                                        width = 1.5.dp,
                                        brush = Brush.verticalGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.55f),
                                                Color.White.copy(alpha = 0.22f),
                                                Color.White.copy(alpha = 0.08f)
                                            )
                                        ),
                                        shape = RoundedCornerShape(28.dp)
                                    )
                                    .padding(6.dp)
                            ) {
                                // Inner Artwork Container with Optical Specular Sheen & Inner Glass Rim
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(22.dp))
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(targetUrl)
                                            .crossfade(250)
                                            .build(),
                                        contentDescription = "Artwork for ${currentSong.title}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    // Optical Specular Glass Glare / Sheen Overlay
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.linearGradient(
                                                    0.0f to Color.White.copy(alpha = 0.20f),
                                                    0.35f to Color.White.copy(alpha = 0.06f),
                                                    0.65f to Color.Transparent,
                                                    1.0f to Color.Black.copy(alpha = 0.12f)
                                                )
                                            )
                                    )

                                    // Inner Glass Border
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .border(
                                                width = 1.dp,
                                                brush = Brush.verticalGradient(
                                                    listOf(
                                                        Color.White.copy(alpha = 0.35f),
                                                        Color.White.copy(alpha = 0.08f),
                                                        Color.White.copy(alpha = 0.18f)
                                                    )
                                                ),
                                                shape = RoundedCornerShape(22.dp)
                                            )
                                    )
                                }
                            }
                        } else {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth(0.88f)
                                    .aspectRatio(1f),
                                shape = RoundedCornerShape(26.dp),
                                shadowElevation = 24.dp,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                                color = Color.Black.copy(alpha = 0.30f)
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(targetUrl.ifBlank { com.lofiq.R.drawable.app_logo })
                                        .crossfade(true)
                                        .error(com.lofiq.R.drawable.app_logo)
                                        .fallback(com.lofiq.R.drawable.app_logo)
                                        .build(),
                                    contentDescription = "Artwork for ${currentSong.title}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(26.dp))
                                )
                            }
                        }
                    }
                }
            }

            // 2. Lower Content Deck (Metadata, Progress, Transport Controls, Volume, Bottom Navigation)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Track Information & Action Row
                var showMoreMenu by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 2.dp, end = 2.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Song title & artist
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = currentSong.title,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                                color = AccentWhite,
                                fontSize = 22.sp,
                                letterSpacing = (-0.3).sp,
                                lineHeight = 26.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = currentSong.artist,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.SansSerif,
                                color = Color.White.copy(alpha = 0.70f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Right: Favorite/Heart toggle + Contextual Action button (More Options in FullScreen, Lyrics in Card mode)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                haptics.play(Haptic.Select)
                                coroutineScope.launch {
                                    libraryRepository?.toggleFavorite(currentSong)
                                }
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.16f))
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                                tint = if (isFavorite) Color(0xFFE91E63) else AccentWhite,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        if (isFullScreenArtwork) {
                            // In Full Screen Artwork mode: Three-dot (More Options) button replaces Lyrics button
                            IconButton(
                                onClick = {
                                    haptics.play(Haptic.Tap)
                                    showActionsSheet = true
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.16f))
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "More Options",
                                    tint = AccentWhite,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        } else {
                            // In Standard Card Artwork mode: Lyrics toggle button (Three-dot is at top right)
                            IconButton(
                                onClick = {
                                    haptics.play(Haptic.Tap)
                                    showLyricsSheet = true
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (currentLyrics != null) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.16f))
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FormatQuote,
                                    contentDescription = "View lyrics",
                                    tint = AccentWhite,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                // 3. Lyrics / Status Line (Dynamic Live Synced Lyrics)
                val activeLyricText = remember(currentLyrics, playerState.currentPositionMs, isLyricsLoading) {
                    val lines = currentLyrics?.lines
                    if (!lines.isNullOrEmpty() && currentLyrics?.syncType == LyricsSyncType.LINE_SYNCED) {
                        var foundText: String? = null
                        for (i in lines.indices.reversed()) {
                            if (playerState.currentPositionMs >= lines[i].timeMs) {
                                foundText = lines[i].text
                                break
                            }
                        }
                        foundText ?: lines.firstOrNull()?.text ?: "Lyrics available"
                    } else if (isLyricsLoading) {
                        "Fetching synced lyrics..."
                    } else if (!lines.isNullOrEmpty()) {
                        lines.firstOrNull()?.text ?: "View lyrics"
                    } else {
                        "Tap for lyrics"
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showLyricsSheet = true }
                            .padding(vertical = 2.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FormatQuote,
                            contentDescription = null,
                            tint = if (currentLyrics?.syncType == LyricsSyncType.LINE_SYNCED) Color(0xFFC084FC) else Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = activeLyricText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.SansSerif,
                                color = if (currentLyrics?.syncType == LyricsSyncType.LINE_SYNCED) AccentWhite else Color.White.copy(alpha = 0.70f),
                                fontSize = 13.sp,
                                fontWeight = if (currentLyrics?.syncType == LyricsSyncType.LINE_SYNCED) FontWeight.Medium else FontWeight.Normal
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    val activeProvider = (playerState.activeProviderId ?: currentSong.providerId).lowercase()
                    val isOffline = activeProvider.contains("local") ||
                            activeProvider.contains("download") ||
                            activeProvider.contains("offline") ||
                            currentSong.id.startsWith("local_") ||
                            currentSong.id.startsWith("content://") ||
                            currentSong.id.startsWith("file://") ||
                            currentSong.sourceUrl.startsWith("content://") ||
                            currentSong.sourceUrl.startsWith("file://") ||
                            isDownloaded

                    val badgeLabel = when {
                        isOffline -> "Offline"
                        activeProvider.contains("saavn") || currentSong.id.startsWith("saavn_") || currentSong.id.startsWith("jio_") -> "HQ"
                        activeProvider.contains("piped") || activeProvider.contains("direct_http") -> "Pulse"
                        else -> "Hi-Fi"
                    }

                    val badgeColor = when (badgeLabel) {
                        "Offline" -> Color(0xFF34D399) // Emerald Green
                        "HQ" -> Color(0xFFFFD54F)      // JioSaavn Gold
                        "Pulse" -> Color(0xFFA855F7)   // Piped Pulse Neon Purple
                        else -> Color(0xFF38BDF8)      // YouTube Hi-Fi Sky Blue
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeColor.copy(alpha = 0.12f))
                            .border(1.dp, badgeColor.copy(alpha = 0.28f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 0.6.sp,
                                color = badgeColor
                            )
                        )
                    }
                }

                // 4. Progress Slider & Timestamps
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp)
                ) {
                    val currentPos = if (isUserSeeking) seekPositionMs.toLong() else playerState.currentPositionMs
                    val duration = playerState.durationMs.coerceAtLeast(1L)
                    val progressFraction = (currentPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    val remainingMs = (duration - currentPos).coerceAtLeast(0L)

                    CustomPlayerSeekbar(
                        progress = progressFraction,
                        duration = duration,
                        currentPosition = currentPos,
                        isPlaying = playerState.isPlaying,
                        style = seekbarStyle,
                        primaryColor = extractedPalette?.vibrant?.takeIf { !extractedPalette!!.isGrayscale }
                            ?: extractedPalette?.dominant?.takeIf { !extractedPalette!!.isGrayscale },
                        secondaryColor = extractedPalette?.secondary?.takeIf { !extractedPalette!!.isGrayscale }
                            ?: extractedPalette?.dominant?.takeIf { !extractedPalette!!.isGrayscale },
                        onSeek = { targetMs ->
                            playbackEngine.seekTo(targetMs)
                            isUserSeeking = false
                        },
                        onSeeking = { isSeeking, fraction ->
                            isUserSeeking = isSeeking
                            seekPositionMs = fraction * duration
                        },
                        onLongPress = {
                            showSeekbarStyleSheet = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = TimeFormatUtils.formatMs(currentPos),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White.copy(alpha = 0.70f),
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        )
                        Text(
                            text = "-${TimeFormatUtils.formatMs(remainingMs)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White.copy(alpha = 0.70f),
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                // 5. Liquid Glass Transparency Box with Playback Controls (Previous, Play/Pause, Next)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.16f),
                                    Color.White.copy(alpha = 0.07f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.30f),
                                    Color.White.copy(alpha = 0.08f)
                                )
                            ),
                            shape = RoundedCornerShape(32.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous Button with tactile liquid press
                        IconButton(
                            onClick = {
                                haptics.play(Haptic.SkipPrevious)
                                playbackEngine.previous()
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .liquidPressEffect(onClick = {
                                    haptics.play(Haptic.SkipPrevious)
                                    playbackEngine.previous()
                                })
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = "Previous Track",
                                tint = AccentWhite,
                                modifier = Modifier.size(42.dp)
                            )
                        }

                        // Dominant Liquid Play/Pause button with tactile bounce & icon morphing
                        LiquidPlayPauseButton(
                            isPlaying = playerState.isPlaying,
                            isBuffering = playerState.isBuffering,
                            onClick = { playbackEngine.togglePlayPause() },
                            size = 68.dp,
                            iconSize = 38.dp
                        )

                        // Next Button with tactile liquid press
                        IconButton(
                            onClick = {
                                haptics.play(Haptic.SkipNext)
                                playbackEngine.next()
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .liquidPressEffect(onClick = {
                                    haptics.play(Haptic.SkipNext)
                                    playbackEngine.next()
                                })
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "Next Track",
                                tint = AccentWhite,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }
                }

                // 7. Centered Dual-Control Frosted Glass Capsule (Listen Together / Jam + Audio Output Device)
                val syncState by (syncManager?.syncState ?: MutableStateFlow(com.lofiq.feature.sync.ListenTogetherState.Disconnected)).collectAsState()
                val isJamConnected = syncState is com.lofiq.feature.sync.ListenTogetherState.Connected

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .height(42.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.20f),
                                        Color.White.copy(alpha = 0.08f)
                                    )
                                )
                            )
                            .border(
                                width = 1.dp,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.35f),
                                        Color.White.copy(alpha = 0.12f)
                                    )
                                ),
                                shape = RoundedCornerShape(24.dp)
                            )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            // Segment 1: Listen Together / Jam
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable {
                                        haptics.play(Haptic.Select)
                                        showListenTogetherSheet = true
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Groups,
                                    contentDescription = "Listen Together",
                                    tint = if (isJamConnected) Color(0xFF00E5FF) else AccentWhite,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (isJamConnected) "Jam" else "Listen Together",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.SansSerif,
                                        color = if (isJamConnected) Color(0xFF00E5FF) else AccentWhite,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    maxLines = 1
                                )
                            }

                            // Subtle Glass Divider
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(18.dp)
                                    .background(Color.White.copy(alpha = 0.22f))
                            )

                            // Segment 2: Audio Device Route (This Phone / Wired / Bluetooth)
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable {
                                        haptics.play(Haptic.Tap)
                                        openAudioPicker()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = audioDevice.icon,
                                    contentDescription = "Audio Route: ${audioDevice.name}",
                                    tint = if (audioDevice.isBluetooth) Color(0xFF60A5FA) else AccentWhite,
                                    modifier = Modifier.size(17.dp)
                                )
                                Text(
                                    text = audioDevice.name,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.SansSerif,
                                        color = AccentWhite,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Dedicated Bottom Liquid Glass Q (Queue) Button & Swipe-Up Affordance
                val queueTracks = playerState.queue
                val queueSize = queueTracks.size
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .pointerInput(Unit) {
                                var swipeUpDrag = 0f
                                detectVerticalDragGestures(
                                    onDragStart = { swipeUpDrag = 0f },
                                    onDragEnd = { swipeUpDrag = 0f },
                                    onDragCancel = { swipeUpDrag = 0f },
                                    onVerticalDrag = { change, dragAmount ->
                                        swipeUpDrag += dragAmount
                                        if (swipeUpDrag < -25f) {
                                            change.consume()
                                            haptics.play(Haptic.Expand)
                                            showQueueSheet = true
                                            swipeUpDrag = 0f
                                        }
                                    }
                                )
                            }
                            .clip(RoundedCornerShape(20.dp))
                            .liquidPressEffect(onClick = {
                                haptics.play(Haptic.Select)
                                showQueueSheet = true
                            })
                            .then(
                                if (isLiquidGlass) {
                                    Modifier
                                        .shadow(8.dp, RoundedCornerShape(20.dp), spotColor = activeGlowColor.copy(alpha = 0.35f))
                                        .background(Color(0xFF14141E).copy(alpha = 0.55f))
                                        .border(1.dp, LiquidGlassTokens.specularBorder(0.35f), RoundedCornerShape(20.dp))
                                } else {
                                    Modifier
                                        .background(Color.White.copy(alpha = 0.10f))
                                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(20.dp))
                                }
                            ),
                        color = Color.Transparent,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            // Glowing Q Emblem Badge
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isLiquidGlass) activeGlowColor.copy(alpha = 0.25f)
                                        else Color.White.copy(alpha = 0.20f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isLiquidGlass) activeGlowColor.copy(alpha = 0.50f)
                                        else Color.White.copy(alpha = 0.35f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Q",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Black,
                                        color = if (isLiquidGlass) activeGlowColor else AccentWhite,
                                        fontSize = 11.sp
                                    )
                                )
                            }

                            Text(
                                text = if (queueSize > 1) "Queue • $queueSize tracks" else "Queue",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = AccentWhite,
                                    fontSize = 12.sp
                                )
                            )

                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowUp,
                                contentDescription = "Swipe up for Queue",
                                tint = Color.White.copy(alpha = 0.70f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Queue Modal Bottom Sheet
        if (showQueueSheet) {
            QueueSheet(
                playbackEngine = playbackEngine,
                onDismiss = { showQueueSheet = false }
            )
        }

        // Lyrics Modal Bottom Sheet
        // Lyrics Modal Bottom Sheet
        if (showLyricsSheet) {
            LyricsSheet(
                playbackEngine = playbackEngine,
                onDismiss = { showLyricsSheet = false }
            )
        }

        // Player Actions (3-Dots) Options Bottom Sheet
        if (showActionsSheet) {
            val haptics = com.lofiq.ui.haptics.rememberHaptics()
            val neonCyan = Color(0xFF00E5FF)
            val neonPurple = Color(0xFF8C52FF)
            val cardBg = Color(0xFF141419)
            val cardBorder = Color(0xFF26262F)

            ModalBottomSheet(
                onDismissRequest = { showActionsSheet = false },
                containerColor = Color(0xFF0F0F14),
                contentColor = AccentWhite,
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.35f))
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Current Song Header Preview
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(cardBg)
                            .border(1.dp, cardBorder, RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(currentSong.artworkUrl.ifEmpty { currentSong.highResArtworkUrl })
                                .crossfade(true)
                                .build(),
                            contentDescription = currentSong.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentSong.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = AccentWhite,
                                    fontSize = 15.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currentSong.artist,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(neonCyan.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = currentSong.quality.name,
                                color = neonCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Action Items List
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(cardBg)
                            .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                    ) {
                        // 1. Favorite / Liked Song Action
                        PlayerActionRow(
                            icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            iconTint = if (isFavorite) Color(0xFFE91E63) else AccentWhite,
                            title = if (isFavorite) "Liked Track" else "Like Track",
                            subtitle = if (isFavorite) "Saved in your Liked Songs library" else "Add to your Liked Songs library",
                            onClick = {
                                haptics.play(Haptic.Select)
                                coroutineScope.launch {
                                    libraryRepository?.toggleFavorite(currentSong)
                                }
                            }
                        )

                        HorizontalDivider(color = cardBorder, thickness = 0.8.dp)

                        // 2. Download Action (with circular progress & public device storage)
                        val downloadSubtitle = when {
                            isDownloading -> "Downloading ${(downloadProgress * 100).toInt()}%..."
                            isDownloaded -> "Saved with cover art (playable in external players)"
                            else -> "Save to Music/LofiQ with album cover art"
                        }
                        PlayerActionRow(
                            icon = if (isDownloaded) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
                            iconTint = if (isDownloaded) Color(0xFF34C759) else if (isDownloading) neonPurple else neonCyan,
                            title = if (isDownloaded) "Downloaded to Device" else if (isDownloading) "Downloading..." else "Download Track",
                            subtitle = downloadSubtitle,
                            isDownloading = isDownloading,
                            downloadProgress = downloadProgress,
                            onClick = {
                                if (isDownloaded) {
                                    haptics.play(Haptic.Select)
                                    val localPath = downloadInfo.localFilePath.orEmpty()
                                    val destMsg = if (localPath.contains("Music/LofiQ")) {
                                        "Saved in Music/LofiQ on device storage"
                                    } else {
                                        "Saved in in-app storage"
                                    }
                                    Toast.makeText(
                                        context,
                                        destMsg,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else if (!isDownloading) {
                                    haptics.play(Haptic.Select)
                                    val needsStoragePermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                                        ) != PackageManager.PERMISSION_GRANTED

                                    if (needsStoragePermission) {
                                        storagePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                    } else {
                                        downloadManager?.downloadSong(currentSong, saveToPublicStorage = true)
                                        Toast.makeText(
                                            context,
                                            "Downloading '${currentSong.title}' to Music/LofiQ...",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        )

                        HorizontalDivider(color = cardBorder, thickness = 0.8.dp)

                        // 2. Equalizer & Audio DSP Action (Opens in-place EqualizerBottomSheet)
                        PlayerActionRow(
                            icon = Icons.Rounded.Tune,
                            iconTint = neonCyan,
                            title = "Equalizer & Audio DSP",
                            subtitle = "Multi-band EQ, bass boost & 3D surround",
                            onClick = {
                                haptics.play(Haptic.Select)
                                showActionsSheet = false
                                if (effectsController != null) {
                                    showEqualizerSheet = true
                                } else {
                                    onOpenEqualizer?.invoke()
                                }
                            }
                        )

                        HorizontalDivider(color = cardBorder, thickness = 0.8.dp)

                        // 3. Sleep Timer Action
                        val timerSubtitle = if (timerState.isActive) {
                            if (timerState.isEndOfTrack) "⏳ Stops at end of this track"
                            else String.format("⏳ %02d:%02d remaining", timerState.remainingSeconds / 60, timerState.remainingSeconds % 60)
                        } else {
                            "Auto-stop music with rotary dial"
                        }
                        PlayerActionRow(
                            icon = Icons.Rounded.Timer,
                            iconTint = neonPurple,
                            title = "Sleep Timer",
                            subtitle = timerSubtitle,
                            onClick = {
                                haptics.play(Haptic.Select)
                                showActionsSheet = false
                                showSleepTimerSheet = true
                            }
                        )

                        HorizontalDivider(color = cardBorder, thickness = 0.8.dp)

                        // 4. Add to Playlist (Opens real playlist picker & creator)
                        PlayerActionRow(
                            icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                            iconTint = Color(0xFFFF9500),
                            title = "Add to Playlist",
                            subtitle = "Save track to custom playlists",
                            onClick = {
                                haptics.play(Haptic.Select)
                                showActionsSheet = false
                                showAddToPlaylistDialog = true
                            }
                        )

                        HorizontalDivider(color = cardBorder, thickness = 0.8.dp)

                        // 5. View Album (Opens album songs sheet)
                        PlayerActionRow(
                            icon = Icons.Rounded.Album,
                            iconTint = TextSecondary,
                            title = "View Album",
                            subtitle = currentSong.album.ifEmpty { "Single Release" },
                            onClick = {
                                haptics.play(Haptic.Tap)
                                showActionsSheet = false
                                showAlbumSheet = true
                            }
                        )

                        if (syncManager != null) {
                            HorizontalDivider(color = cardBorder, thickness = 0.8.dp)

                            // 6. Listen Together Action
                            PlayerActionRow(
                                icon = Icons.Rounded.GroupWork,
                                iconTint = Color(0xFF00E5FF),
                                title = "Listen Together",
                                subtitle = "Join or host a live sync room with friends",
                                onClick = {
                                    haptics.play(Haptic.Select)
                                    showActionsSheet = false
                                    showListenTogetherSheet = true
                                }
                            )
                        }
                    }
                }
            }
        }

        // In-Place Equalizer Modal Bottom Sheet
        if (showEqualizerSheet && effectsController != null) {
            com.lofiq.feature.equalizer.EqualizerBottomSheet(
                effectsController = effectsController,
                onDismiss = { showEqualizerSheet = false }
            )
        }

        // Sleep Timer Sheet (Rotary clock picker / countdown)
        if (showSleepTimerSheet && sleepTimerManager != null) {
            SleepTimerSheet(
                sleepTimerManager = sleepTimerManager,
                onDismiss = { showSleepTimerSheet = false }
            )
        }

        // Add to Playlist Dialog
        if (showAddToPlaylistDialog && libraryRepository != null) {
            com.lofiq.feature.playlist.AddToPlaylistDialog(
                song = currentSong,
                libraryRepository = libraryRepository,
                onDismiss = { showAddToPlaylistDialog = false }
            )
        }

        // View Album Tracks Sheet
        if (showAlbumSheet && musicRepository != null) {
            AlbumSongsSheet(
                albumName = currentSong.album,
                artistName = currentSong.artist,
                artworkUrl = currentSong.artworkUrl.ifEmpty { currentSong.highResArtworkUrl },
                musicRepository = musicRepository,
                playbackEngine = playbackEngine,
                onDismiss = { showAlbumSheet = false }
            )
        }

        // Listen Together Live Room Sheet
        if (showListenTogetherSheet && syncManager != null) {
            com.lofiq.feature.sync.ListenTogetherBottomSheet(
                syncManager = syncManager,
                initialMode = com.lofiq.feature.sync.ListenTogetherMode.CHOICE,
                onDismiss = { showListenTogetherSheet = false }
            )
        }

        if (showSeekbarStyleSheet) {
            com.lofiq.feature.settings.SeekbarStyleBottomSheet(
                currentStyle = seekbarStyle,
                onSelectStyle = { newStyle ->
                    onSeekbarStyleChange?.invoke(newStyle)
                    showSeekbarStyleSheet = false
                },
                onDismiss = { showSeekbarStyleSheet = false }
            )
        }
    }
}

@Composable
private fun PlayerActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    isDownloading: Boolean = false,
    downloadProgress: Float = 0f,
    onClick: () -> Unit
) {
    val neonCyan = Color(0xFF00E5FF)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            if (isDownloading) {
                CircularProgressIndicator(
                    progress = { downloadProgress },
                    color = neonCyan,
                    strokeWidth = 2.5.dp,
                    trackColor = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = AccentWhite,
                    fontSize = 14.sp
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (isDownloading) neonCyan else TextSecondary,
                    fontSize = 12.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isDownloading) {
            Text(
                text = "${(downloadProgress * 100).toInt()}%",
                color = neonCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}


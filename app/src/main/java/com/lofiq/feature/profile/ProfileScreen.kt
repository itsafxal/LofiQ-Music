package com.lofiq.feature.profile

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.lofiq.R
import com.lofiq.auth.LoginDialog
import com.lofiq.auth.UserAccount
import com.lofiq.core.model.AudioQuality
import com.lofiq.feature.settings.SeamlessPlayDialog
import com.lofiq.feature.settings.SettingsViewModel
import com.lofiq.ui.components.LofiQLogo
import com.lofiq.ui.haptics.Haptic
import com.lofiq.ui.haptics.rememberHaptics
import com.lofiq.ui.theme.*
import kotlinx.coroutines.launch

private val MonochromeCard = Color(0xFF0F0F0F)
private val MonochromeSubtle = Color(0xFF181818)
private val MonochromeBorder = Color.White.copy(alpha = 0.08f)
private val GrayscaleFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToEqualizer: () -> Unit = {},
    onNavigateToDiagnostics: () -> Unit = {},
    onNavigateToLocalMusic: () -> Unit = {},
    onNavigateToAppearance: () -> Unit = {},
    onNavigateToDeveloper: () -> Unit = {},
    hasActiveSong: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()

    androidx.activity.compose.BackHandler {
        onNavigateBack()
    }

    val userAccount by profileViewModel.authRepository.userAccount.collectAsStateWithLifecycle(initialValue = UserAccount.GUEST)
    val isYouTubeSyncing by profileViewModel.isYouTubeSyncing.collectAsStateWithLifecycle()
    val isYouTubeBannerPermanentlyDismissed by profileViewModel.isYouTubeBannerPermanentlyDismissed.collectAsStateWithLifecycle()
    val settingsState by viewModel.uiState.collectAsStateWithLifecycle()

    var showYouTubeMusicDialog by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showCrossfadeDialog by remember { mutableStateOf(false) }

    val bottomScrollPadding = if (hasActiveSong) 220.dp else 140.dp

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = bottomScrollPadding)
        ) {
            // Screen Header: Back Button + Title on Left, Monochrome Logo on Right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = {
                        haptics.play(Haptic.Tap)
                        onNavigateBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = AccentWhite,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentWhite,
                            letterSpacing = (-0.5).sp
                        )
                    )
                }

                Image(
                    painter = painterResource(id = R.drawable.app_logo_transparent),
                    contentDescription = "LofiQ Logo",
                    modifier = Modifier.size(34.dp)
                )
            }

            // ==========================================
            // 1. CONNECTED ACCOUNTS & INTEGRATIONS (YouTube Music Only)
            // ==========================================
            MonoSectionHeader("ACCOUNTS & INTEGRATIONS")

            // YouTube Music Clean Item
            MonoMenuItem(
                iconDrawable = R.drawable.ic_youtube_music,
                iconTint = Color.Unspecified,
                title = "YouTube Music",
                subtitle = if (userAccount.isLoggedIn) {
                    "Connected as ${userAccount.name.ifBlank { userAccount.email ?: "Account linked" }}"
                } else {
                    "Tap to connect YouTube Music account"
                },
                trailingBadge = if (userAccount.isLoggedIn) "Connected" else null,
                isBadgeSuccess = userAccount.isLoggedIn,
                onClick = {
                    haptics.play(Haptic.Tap)
                    showYouTubeMusicDialog = true
                }
            )

            if (!userAccount.isLoggedIn && isYouTubeBannerPermanentlyDismissed) {
                MonoMenuItem(
                    icon = Icons.Rounded.Visibility,
                    title = "Show Banner on Home",
                    subtitle = "Restore the YouTube Music sign-in card on Home",
                    onClick = {
                        haptics.play(Haptic.Tap)
                        profileViewModel.resetYouTubeBanner()
                        Toast.makeText(context, "YouTube Music card restored on Home", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // APPEARANCE & THEME
            // ==========================================
            MonoSectionHeader("APPEARANCE & THEME")

            MonoMenuItem(
                icon = Icons.Rounded.Palette,
                title = "Appearance & Themes",
                subtitle = "App icon & branding, player poster & Liquid Glass UI",
                onClick = {
                    haptics.play(Haptic.Tap)
                    onNavigateToAppearance()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // 2. PLAYBACK & AUDIO
            // ==========================================
            MonoSectionHeader("PLAYBACK & AUDIO")

            MonoMenuItem(
                icon = Icons.Rounded.HighQuality,
                title = "Streaming Quality",
                subtitle = settingsState.audioQuality.label,
                onClick = {
                    haptics.play(Haptic.Tap)
                    showQualityDialog = true
                }
            )

            MonoMenuItem(
                icon = Icons.Rounded.GraphicEq,
                title = "Equalizer & Audio DSP",
                subtitle = "Bass boost, 3D spatializer, frequency presets",
                onClick = {
                    haptics.play(Haptic.Tap)
                    onNavigateToEqualizer()
                }
            )

            MonoMenuItem(
                icon = Icons.Rounded.AutoAwesome,
                title = "Seamless Play",
                subtitle = if (settingsState.crossfadeSeconds > 0) "${settingsState.crossfadeSeconds}s crossfade between tracks" else "Disabled (standard playback)",
                onClick = {
                    haptics.play(Haptic.Tap)
                    showCrossfadeDialog = true
                }
            )

            MonoMenuItem(
                icon = Icons.Rounded.FolderCopy,
                title = "Local Music Library",
                subtitle = "Browse and play audio files stored on device",
                onClick = {
                    haptics.play(Haptic.Tap)
                    onNavigateToLocalMusic()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // 3. STORAGE & CACHE
            // ==========================================
            MonoSectionHeader("STORAGE & CACHE")

            MonoMenuItem(
                icon = Icons.Rounded.CleaningServices,
                title = "Clear Resolution Cache",
                subtitle = "Free cached stream links and temporary metadata",
                onClick = {
                    haptics.play(Haptic.Tap)
                    viewModel.clearCache()
                    Toast.makeText(context, "Stream resolution cache cleared", Toast.LENGTH_SHORT).show()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // 4. ABOUT LOFIQ
            // ==========================================
            MonoSectionHeader("ABOUT LOFIQ")

            // App Card with Monochrome Logo (Tapping 7 times unlocks Admin Resolver Diagnostics)
            val isLiquidGlass = LocalLiquidGlassEnabled.current
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(18.dp),
                color = if (isLiquidGlass) LiquidGlassTokens.CardGlassFill else MonochromeCard,
                border = if (isLiquidGlass) LiquidGlassTokens.specularBorderStroke() else BorderStroke(1.dp, MonochromeBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo_transparent),
                        contentDescription = "LofiQ Logo",
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "LofiQ V${com.lofiq.BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = AccentWhite
                            )
                        )
                        Text(
                            text = "Music without limits",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }
            }

            MonoMenuItem(
                icon = Icons.Rounded.SystemUpdate,
                title = "Check for Updates",
                subtitle = if (settingsState.isCheckingUpdate) "Checking for newer releases..." else "Get latest LofiQ features & fixes",
                onClick = {
                    haptics.play(Haptic.Tap)
                    viewModel.checkForUpdates()
                }
            )

            // Developer Screen Entry Card
            MonoMenuItem(
                icon = Icons.Rounded.Person,
                title = "Developer",
                subtitle = "Muhammed Afsal • itsafxal.vercel.app",
                onClick = {
                    haptics.play(Haptic.Tap)
                    onNavigateToDeveloper()
                }
            )
        }

        // Update Notification Dialog (Black & White style)
        if (settingsState.updateInfo != null) {
            val update = settingsState.updateInfo!!
            AlertDialog(
                onDismissRequest = {
                    if (!settingsState.isDownloadingUpdate) {
                        viewModel.dismissUpdateDialog()
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.RocketLaunch,
                            contentDescription = null,
                            tint = AccentWhite,
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
                                text = "LofiQ V${update.versionName} (${update.apkSize})",
                                color = AccentWhite,
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
                                        color = AccentWhite,
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

                        if (settingsState.isDownloadingUpdate) {
                            Spacer(modifier = Modifier.height(16.dp))
                            LinearProgressIndicator(
                                progress = { settingsState.downloadProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = Color.White,
                                trackColor = MonochromeSubtle
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Downloading update... ${(settingsState.downloadProgress * 100).toInt()}%",
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
                            viewModel.downloadAndInstallUpdate(context, update)
                        },
                        enabled = !settingsState.isDownloadingUpdate,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = settingsState.activeBrandColor,
                            contentColor = confirmContentColor,
                            disabledContainerColor = settingsState.activeBrandColor.copy(alpha = 0.5f),
                            disabledContentColor = confirmContentColor.copy(alpha = 0.7f)
                        ),
                        modifier = Modifier.height(42.dp)
                    ) {
                        if (settingsState.isDownloadingUpdate) {
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
                            text = if (settingsState.isDownloadingUpdate) "Downloading..." else "Update Now",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = confirmContentColor
                        )
                    }
                },
                dismissButton = {
                    if (!settingsState.isDownloadingUpdate) {
                        OutlinedButton(
                            onClick = { viewModel.dismissUpdateDialog() },
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
                containerColor = MonochromeCard,
                shape = RoundedCornerShape(24.dp)
            )
        }

        // Show Toast feedback when checking updates
        LaunchedEffect(settingsState.isUpToDate) {
            if (settingsState.isUpToDate) {
                Toast.makeText(context, "LofiQ is up to date!", Toast.LENGTH_SHORT).show()
                viewModel.dismissUpdateDialog()
            }
        }

        LaunchedEffect(settingsState.updateError) {
            settingsState.updateError?.let { error ->
                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            }
        }

        // Audio Quality Picker Dialog (Black & White style)
        if (showQualityDialog) {
            AlertDialog(
                onDismissRequest = { showQualityDialog = false },
                title = { Text("Select Streaming Quality", color = AccentWhite, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AudioQuality.entries.forEach { quality ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        viewModel.setAudioQuality(quality)
                                        showQualityDialog = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = settingsState.audioQuality == quality,
                                    onClick = {
                                        viewModel.setAudioQuality(quality)
                                        showQualityDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color.White,
                                        unselectedColor = TextSecondary
                                    )
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = quality.label,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = if (settingsState.audioQuality == quality) AccentWhite else TextSecondary,
                                                fontWeight = if (settingsState.audioQuality == quality) FontWeight.Bold else FontWeight.Medium
                                            )
                                        )
                                        if (quality == AudioQuality.AUTO) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Recommended",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = Color(0xFF10B981),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = quality.subtitle,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = TextSecondary.copy(alpha = 0.8f),
                                            fontSize = 11.5.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showQualityDialog = false }) {
                        Text("Close", color = AccentWhite)
                    }
                },
                containerColor = MonochromeCard,
                shape = RoundedCornerShape(24.dp)
            )
        }

        // Seamless Play Dialog
        if (showCrossfadeDialog) {
            SeamlessPlayDialog(
                currentSeconds = settingsState.crossfadeSeconds,
                onDismiss = { showCrossfadeDialog = false },
                onSave = { viewModel.setCrossfadeSeconds(it) }
            )
        }

        // YouTube Music Management Popup Dialog
        if (showYouTubeMusicDialog) {
            AlertDialog(
                onDismissRequest = { showYouTubeMusicDialog = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF0000).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_youtube_music),
                                    contentDescription = "YouTube Music",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "YouTube Music",
                                    color = AccentWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                                Text(
                                    text = if (userAccount.isLoggedIn) "Account Connected" else "Not Connected",
                                    color = if (userAccount.isLoggedIn) Color(0xFF34D399) else TextSecondary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        IconButton(
                            onClick = { showYouTubeMusicDialog = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (userAccount.isLoggedIn) {
                            // Account Profile Header Card
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MonochromeSubtle,
                                border = BorderStroke(1.dp, MonochromeBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!userAccount.avatarUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = userAccount.avatarUrl,
                                                contentDescription = "Avatar",
                                                colorFilter = GrayscaleFilter,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.Person,
                                                contentDescription = null,
                                                tint = AccentWhite,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = userAccount.name.ifBlank { "YouTube Music User" },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = AccentWhite,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = userAccount.email ?: "Account connected",
                                            fontSize = 12.sp,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f))
                                    ) {
                                        Text(
                                            text = "Connected",
                                            color = Color(0xFF34D399),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            // 1. Sync Music Button
                            Button(
                                onClick = {
                                    haptics.play(Haptic.Tap)
                                    profileViewModel.syncYouTubeLibrary { liked, playlists ->
                                        Toast.makeText(context, "YouTube Music synced ($liked songs, $playlists playlists)", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = !isYouTubeSyncing,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(22.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black,
                                    disabledContainerColor = Color(0xFF262626),
                                    disabledContentColor = TextTertiary
                                )
                            ) {
                                if (isYouTubeSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.Black
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Syncing...", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Sync,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Sync Music", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                            }

                            // 2. Disconnect / Log Out Button
                            OutlinedButton(
                                onClick = {
                                    haptics.play(Haptic.Tap)
                                    showYouTubeMusicDialog = false
                                    showSignOutConfirm = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(22.dp),
                                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.45f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color(0xFFEF4444).copy(alpha = 0.08f),
                                    contentColor = Color(0xFFF87171)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                                    contentDescription = null,
                                    tint = Color(0xFFF87171),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Log Out of YouTube Music", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFF87171))
                            }
                        } else {
                            // Not Logged In State
                            Text(
                                text = "Connect your YouTube Music account to automatically sync your playlists, liked songs, and personalized favorites into LofiQ.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                )
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Button(
                                onClick = {
                                    haptics.play(Haptic.Tap)
                                    showYouTubeMusicDialog = false
                                    showLoginDialog = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(22.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Login,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Connect YouTube Music", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Black)
                            }
                        }
                    }
                },
                confirmButton = {},
                containerColor = MonochromeCard,
                shape = RoundedCornerShape(24.dp)
            )
        }

        // YouTube Music Login Dialog Sheet
        if (showLoginDialog) {
            LoginDialog(
                onDismiss = { showLoginDialog = false },
                onAuthSuccess = { cookies ->
                    profileViewModel.onYouTubeCookiesCaptured(cookies) { success, account ->
                        showLoginDialog = false
                        if (success) {
                            Toast.makeText(context, "Connected as ${account.name} & library synced!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "YouTube Music connected, syncing...", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }

        // YouTube Music Logout Confirmation Alert
        if (showSignOutConfirm) {
            AlertDialog(
                onDismissRequest = { showSignOutConfirm = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = null,
                            tint = Color(0xFFF87171),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Log Out of YouTube Music?", color = AccentWhite, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                },
                text = {
                    Text(
                        "Are you sure you want to log out of your YouTube Music account? You will be switched to guest mode. Your local and downloaded music will remain safe.",
                        color = TextSecondary,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showSignOutConfirm = false
                            scope.launch {
                                profileViewModel.authRepository.signOut()
                                Toast.makeText(context, "Logged out of YouTube Music", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Log Out", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSignOutConfirm = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = MonochromeCard,
                shape = RoundedCornerShape(24.dp)
            )
        }
    }
}

@Composable
private fun MonoSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(
            color = Color(0xFFA0A0A0),
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.2.sp
        ),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun MonoMenuItem(
    icon: ImageVector? = null,
    iconDrawable: Int? = null,
    iconTint: Color = AccentWhite,
    title: String,
    subtitle: String,
    trailingBadge: String? = null,
    isBadgeSuccess: Boolean = false,
    onClick: () -> Unit
) {
    val isLiquidGlass = LocalLiquidGlassEnabled.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        color = if (isLiquidGlass) LiquidGlassTokens.CardGlassFill else MonochromeCard,
        shape = RoundedCornerShape(18.dp),
        border = if (isLiquidGlass) LiquidGlassTokens.specularBorderStroke() else BorderStroke(1.dp, MonochromeBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isLiquidGlass) LiquidGlassTokens.PillGlassFill else MonochromeSubtle),
                contentAlignment = Alignment.Center
            ) {
                if (iconDrawable != null) {
                    Image(
                        painter = painterResource(id = iconDrawable),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                } else if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = AccentWhite,
                        fontSize = 15.sp
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (trailingBadge != null) {
                if (isBadgeSuccess) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.16f),
                        border = BorderStroke(0.8.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF34D399))
                            )
                            Text(
                                text = trailingBadge,
                                color = Color(0xFF34D399),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text(
                            text = trailingBadge,
                            color = AccentWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF666666),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun MonoSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val isLiquidGlass = LocalLiquidGlassEnabled.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable { onCheckedChange(!checked) },
        color = if (isLiquidGlass) LiquidGlassTokens.CardGlassFill else MonochromeCard,
        shape = RoundedCornerShape(18.dp),
        border = if (isLiquidGlass) LiquidGlassTokens.specularBorderStroke() else BorderStroke(1.dp, MonochromeBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isLiquidGlass) LiquidGlassTokens.PillGlassFill else MonochromeSubtle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AccentWhite,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = AccentWhite,
                        fontSize = 15.sp
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF2563EB),
                    uncheckedThumbColor = Color(0xFFAAAAAA),
                    uncheckedTrackColor = Color(0xFF222226),
                    uncheckedBorderColor = Color.White.copy(alpha = 0.15f)
                )
            )
        }
    }
}

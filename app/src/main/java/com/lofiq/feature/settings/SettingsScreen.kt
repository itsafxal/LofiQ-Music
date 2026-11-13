package com.lofiq.feature.settings

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
import com.lofiq.feature.profile.ProfileViewModel
import com.lofiq.ui.components.LofiQLogo
import com.lofiq.ui.components.glass.*
import com.lofiq.ui.haptics.Haptic
import com.lofiq.ui.haptics.rememberHaptics
import com.lofiq.ui.theme.*
import kotlinx.coroutines.launch

private val MonochromeCard = Color(0xFF0F0F0F)
private val MonochromeSubtle = Color(0xFF181818)
private val MonochromeBorder = Color.White.copy(alpha = 0.08f)
private val GrayscaleFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

@Composable
fun SettingsScreen(
    onNavigateBack: (() -> Unit)? = null,
    onNavigateToEqualizer: () -> Unit = {},
    onNavigateToLocalMusic: () -> Unit = {},
    onNavigateToDiagnostics: () -> Unit = {},
    onNavigateToAppearance: () -> Unit = {},
    onNavigateToSeekbarStyle: () -> Unit = {},
    onNavigateToDeveloper: () -> Unit = {},
    hasActiveSong: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val userAccount by profileViewModel.authRepository.userAccount.collectAsStateWithLifecycle(initialValue = UserAccount.GUEST)
    val isYouTubeSyncing by profileViewModel.isYouTubeSyncing.collectAsStateWithLifecycle()
    
    var showQualityDialog by remember { mutableStateOf(false) }
    var showCrossfadeDialog by remember { mutableStateOf(false) }
    var showYouTubeMusicDialog by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }

    val isLiquidGlass = LocalLiquidGlassEnabled.current
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
            // Screen Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = if (onNavigateBack != null) 8.dp else 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onNavigateBack != null) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBackIosNew,
                            contentDescription = "Back",
                            tint = AccentWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentWhite
                    )
                )
            }

            // Accounts & Integrations Section
            SettingsSectionHeader(title = "ACCOUNTS & INTEGRATIONS")

            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                preset = GlassPreset.DARK,
                shape = RoundedCornerShape(18.dp),
                padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                onClick = {
                    haptics.play(Haptic.Tap)
                    showYouTubeMusicDialog = true
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(LiquidGlassTokens.PillGlassFill),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_youtube_music),
                            contentDescription = "YouTube Music Logo",
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "YouTube Music",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = AccentWhite,
                                fontSize = 15.sp
                            )
                        )
                        Text(
                            text = if (userAccount.isLoggedIn) {
                                "Connected as ${userAccount.name.ifBlank { userAccount.email ?: "Account linked" }}"
                            } else {
                                "Tap to connect YouTube Music account"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary,
                                fontSize = 12.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (userAccount.isLoggedIn) {
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
                                    text = "Connected",
                                    color = Color(0xFF34D399),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
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

            Spacer(modifier = Modifier.height(16.dp))

            // Appearance & UI Theme Section
            SettingsSectionHeader(title = "APPEARANCE & THEME")

            SettingsItem(
                icon = Icons.Rounded.Palette,
                title = "Appearance & Themes",
                subtitle = "App icon & branding, player poster & Liquid Glass UI",
                onClick = { onNavigateToAppearance() }
            )

            SettingsItem(
                icon = Icons.Rounded.LinearScale,
                title = "Seekbar Style",
                subtitle = "${uiState.seekbarStyle.label} • ${uiState.seekbarStyle.description}",
                onClick = { onNavigateToSeekbarStyle() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Audio & Playback Section
            SettingsSectionHeader(title = "PLAYBACK & AUDIO")
            
            SettingsItem(
                icon = Icons.Rounded.HighQuality,
                title = "Streaming Quality",
                subtitle = uiState.audioQuality.label,
                onClick = { showQualityDialog = true }
            )

            SettingsItem(
                icon = Icons.Rounded.GraphicEq,
                title = "Equalizer & Audio DSP",
                subtitle = "Bass boost, 3D spatializer, multi-band frequency presets",
                onClick = onNavigateToEqualizer
            )

            SettingsItem(
                icon = Icons.Rounded.AutoAwesome,
                title = "Seamless Play",
                subtitle = if (uiState.crossfadeSeconds > 0) "${uiState.crossfadeSeconds}s crossfade between tracks" else "Disabled (standard playback)",
                onClick = {
                    haptics.play(Haptic.Tap)
                    showCrossfadeDialog = true
                }
            )

            SettingsItem(
                icon = Icons.Rounded.FolderCopy,
                title = "Local Music Library",
                subtitle = "Browse and play audio files stored on device",
                onClick = onNavigateToLocalMusic
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Storage & Cache Section
            SettingsSectionHeader(title = "STORAGE & CACHE")

            SettingsItem(
                icon = Icons.Rounded.CleaningServices,
                title = "Clear Resolution Cache",
                subtitle = "Free cached stream links and metadata",
                onClick = {
                    viewModel.clearCache()
                    Toast.makeText(context, "Stream resolution cache cleared", Toast.LENGTH_SHORT).show()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // About Section
            SettingsSectionHeader(title = "ABOUT LOFIQ")

            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                preset = GlassPreset.DARK,
                shape = RoundedCornerShape(18.dp),
                padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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

                SettingsItem(
                    icon = Icons.Rounded.SystemUpdate,
                    title = "Check for Updates",
                    subtitle = if (uiState.isCheckingUpdate) "Checking for newer releases..." else "Get latest LofiQ features & fixes",
                    onClick = { viewModel.checkForUpdates() }
                )

                // Developer Screen Entry Card
                SettingsItem(
                    icon = Icons.Rounded.Person,
                    title = "Developer",
                    subtitle = "Muhammed Afsal • itsafxal.vercel.app",
                    onClick = onNavigateToDeveloper
                )
        }

        // Update Notification Dialog (Liquid Glass Modal)
        if (uiState.updateInfo != null) {
            val update = uiState.updateInfo!!
            GlassDialog(
                onDismissRequest = {
                    if (!uiState.isDownloadingUpdate) {
                        viewModel.dismissUpdateDialog()
                    }
                },
                icon = Icons.Rounded.RocketLaunch,
                title = "New Update Available!",
                confirmButton = {
                    val confirmContentColor = if (uiState.activeBrandColor.luminance() > 0.5f) Color.Black else Color.White
                    GlassButton(
                        onClick = {
                            viewModel.downloadAndInstallUpdate(context, update)
                        },
                        enabled = !uiState.isDownloadingUpdate,
                        isPrimary = true,
                        accentColor = uiState.activeBrandColor,
                        modifier = Modifier.height(42.dp)
                    ) {
                        if (uiState.isDownloadingUpdate) {
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
                            text = if (uiState.isDownloadingUpdate) "Downloading..." else "Update Now",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = confirmContentColor
                        )
                    }
                },
                dismissButton = if (!uiState.isDownloadingUpdate) {
                    {
                        GlassButton(
                            onClick = { viewModel.dismissUpdateDialog() },
                            preset = GlassPreset.SUBTLE,
                            modifier = Modifier.height(42.dp)
                        ) {
                            Text(
                                text = "Later",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp,
                                color = TextSecondary
                            )
                        }
                    }
                } else null
            ) {
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
                                    color = BrandTangerine,
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

                    if (uiState.isDownloadingUpdate) {
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { uiState.downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = BrandTangerine,
                            trackColor = Color.White.copy(alpha = 0.12f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Downloading update... ${(uiState.downloadProgress * 100).toInt()}%",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Show Toast feedback when checking updates
        LaunchedEffect(uiState.isUpToDate) {
            if (uiState.isUpToDate) {
                Toast.makeText(context, "LofiQ is up to date!", Toast.LENGTH_SHORT).show()
                viewModel.dismissUpdateDialog()
            }
        }

        LaunchedEffect(uiState.updateError) {
            uiState.updateError?.let { error ->
                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            }
        }

        // Audio Quality Picker Dialog (Liquid Glass Modal)
        if (showQualityDialog) {
            GlassDialog(
                onDismissRequest = { showQualityDialog = false },
                icon = Icons.Rounded.HighQuality,
                title = "Select Audio Quality",
                confirmButton = {
                    GlassButton(
                        onClick = { showQualityDialog = false },
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text("Close", color = AccentWhite, fontWeight = FontWeight.SemiBold)
                    }
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioQuality.entries.forEach { quality ->
                        val isSelected = uiState.audioQuality == quality
                        GlassCard(
                            preset = if (isSelected) GlassPreset.ELEVATED else GlassPreset.SUBTLE,
                            padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            onClick = {
                                viewModel.setAudioQuality(quality)
                                showQualityDialog = false
                            },
                            hasGlow = isSelected,
                            glowColor = BrandTangerine
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.setAudioQuality(quality)
                                        showQualityDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = BrandTangerine,
                                        unselectedColor = TextSecondary
                                    )
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = quality.label,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = if (isSelected) AccentWhite else TextSecondary,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
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
                }
            }
        }

        // Seamless Play Dialog
        if (showCrossfadeDialog) {
            SeamlessPlayDialog(
                currentSeconds = uiState.crossfadeSeconds,
                onDismiss = { showCrossfadeDialog = false },
                onSave = { viewModel.setCrossfadeSeconds(it) }
            )
        }

        // YouTube Music Management Popup Dialog (Liquid Glass Modal)
        if (showYouTubeMusicDialog) {
            GlassDialog(
                onDismissRequest = { showYouTubeMusicDialog = false },
                icon = null,
                title = null,
                confirmButton = {
                    GlassButton(
                        onClick = { showYouTubeMusicDialog = false },
                        preset = GlassPreset.SUBTLE,
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text("Close", color = TextSecondary, fontWeight = FontWeight.SemiBold)
                    }
                }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
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
                    }

                    if (userAccount.isLoggedIn) {
                        GlassCard(
                            preset = GlassPreset.SUBTLE,
                            padding = PaddingValues(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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

                        GlassButton(
                            onClick = {
                                haptics.play(Haptic.Tap)
                                profileViewModel.syncYouTubeLibrary { liked, playlists ->
                                    Toast.makeText(context, "YouTube Music synced ($liked songs, $playlists playlists)", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !isYouTubeSyncing,
                            isPrimary = true,
                            accentColor = BrandTangerine,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(22.dp)
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

                        GlassButton(
                            onClick = {
                                haptics.play(Haptic.Tap)
                                showYouTubeMusicDialog = false
                                showSignOutConfirm = true
                            },
                            preset = GlassPreset.SUBTLE,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(22.dp)
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
                        Text(
                            text = "Connect your YouTube Music account to automatically sync your playlists, liked songs, and personalized favorites into LofiQ.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = TextSecondary,
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        GlassButton(
                            onClick = {
                                haptics.play(Haptic.Tap)
                                showYouTubeMusicDialog = false
                                showLoginDialog = true
                            },
                            isPrimary = true,
                            accentColor = BrandTangerine,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(22.dp)
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
            }
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

        // YouTube Music Logout Confirmation Alert (Liquid Glass Modal)
        if (showSignOutConfirm) {
            GlassDialog(
                onDismissRequest = { showSignOutConfirm = false },
                icon = Icons.AutoMirrored.Rounded.Logout,
                title = "Log Out of YouTube Music?",
                confirmButton = {
                    GlassButton(
                        onClick = {
                            showSignOutConfirm = false
                            scope.launch {
                                profileViewModel.authRepository.signOut()
                                Toast.makeText(context, "Logged out of YouTube Music", Toast.LENGTH_SHORT).show()
                            }
                        },
                        isPrimary = true,
                        accentColor = Color(0xFFEF4444),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Log Out", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                dismissButton = {
                    GlassButton(
                        onClick = { showSignOutConfirm = false },
                        preset = GlassPreset.SUBTLE
                    ) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            ) {
                Text(
                    "Are you sure you want to log out of your YouTube Music account? You will be switched to guest mode. Your local and downloaded music will remain safe.",
                    color = TextSecondary,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
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
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        preset = GlassPreset.DARK,
        shape = RoundedCornerShape(18.dp),
        padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(LiquidGlassTokens.PillGlassFill),
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
                    )
                )
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
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        preset = GlassPreset.DARK,
        shape = RoundedCornerShape(18.dp),
        padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        onClick = { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(LiquidGlassTokens.PillGlassFill),
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
                    )
                )
            }

            GlassSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                activeColor = BrandTangerine
            )
        }
    }
}


package com.lofiq.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lofiq.core.common.AppIconTheme
import com.lofiq.ui.components.glass.*
import com.lofiq.ui.haptics.Haptic
import com.lofiq.ui.haptics.rememberHaptics
import com.lofiq.ui.theme.*

private val ScreenBackground = Color(0xFF0A0A0E)
private val SurfaceCardColor = Color(0xFF131318)
private val SurfaceSubtleColor = Color(0xFF1C1C23)
private val SubtleBorderColor = Color.White.copy(alpha = 0.08f)

/**
 * Clean, modern Appearance & Theme screen.
 * Contains:
 * 1. "App Icon & Themes" navigation card leading to dedicated Color Studio window.
 * 2. Player & display experience customization toggles (Artwork, Liquid Glass, Dynamic Glow).
 */
@Composable
fun AppearanceThemeScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAppIconTheme: () -> Unit,
    onNavigateToSeekbarStyle: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isLiquidGlass = LocalLiquidGlassEnabled.current
    val haptics = rememberHaptics()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scrollState = rememberScrollState()

    val launcherIconColor = uiState.appIconTheme.primaryColor

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isLiquidGlass) Color.Transparent else ScreenBackground)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // ==========================================
            // TOP BAR: BACK BUTTON & TITLE
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    GlassIconButton(
                        onClick = {
                            haptics.play(Haptic.Tap)
                            onNavigateBack()
                        },
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = AccentWhite,
                        size = 42.dp,
                        iconSize = 20.dp
                    )

                    Column {
                        Text(
                            text = "Appearance & Theme",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                color = AccentWhite,
                                letterSpacing = (-0.5).sp
                            )
                        )
                        Text(
                            text = "Branding, Display Atmosphere & Styling",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ==========================================
            // SECTION 1: APP ICON & THEMES ENTRY CARD
            // ==========================================
            SectionHeader(
                title = "BRANDING & ICON THEMES",
                subtitle = "Choose from the Photoshop-style color wheel or curated palettes"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Dedicated Option Card for App Icon & Themes
            GlassCard(
                preset = GlassPreset.DARK,
                shape = RoundedCornerShape(22.dp),
                padding = PaddingValues(20.dp),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    haptics.play(Haptic.Tap)
                    onNavigateToAppIconTheme()
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(LiquidGlassTokens.PillGlassFill)
                                .border(1.dp, SubtleBorderColor, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ColorLens,
                                contentDescription = null,
                                tint = AccentWhite,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "App Icon & Themes",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.5.sp,
                                    color = AccentWhite
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Color wheel studio & launcher preview",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.5.sp,
                                    color = TextSecondary
                                )
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Round Swatch for Active Launcher Icon Color
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .shadow(8.dp, CircleShape, spotColor = launcherIconColor)
                                .clip(CircleShape)
                                .background(launcherIconColor)
                                .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                        )

                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                            contentDescription = "Open Studio",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dedicated Option Card for Seekbar Style
            GlassCard(
                preset = GlassPreset.DARK,
                shape = RoundedCornerShape(22.dp),
                padding = PaddingValues(20.dp),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    haptics.play(Haptic.Tap)
                    onNavigateToSeekbarStyle()
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(LiquidGlassTokens.PillGlassFill)
                                .border(1.dp, SubtleBorderColor, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LinearScale,
                                contentDescription = null,
                                tint = AccentWhite,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Seekbar Style",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.5.sp,
                                    color = AccentWhite
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${uiState.seekbarStyle.label} • 10 custom styles & animations",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.5.sp,
                                    color = TextSecondary
                                )
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                        contentDescription = "Open Seekbar Styles",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // ==========================================
            // SECTION 2: DISPLAY & PLAYER EXPERIENCE
            // ==========================================
            SectionHeader(
                title = "PLAYER & DISPLAY EXPERIENCE",
                subtitle = "Fine-tune full-bleed artwork, glassmorphism and dynamic atmosphere"
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Toggle 1: Full Screen Artwork
            ThemeToggleCard(
                icon = Icons.Rounded.FitScreen,
                title = "Full Screen Artwork",
                subtitle = if (uiState.fullScreenArtworkEnabled) {
                    "Enabled • Immersive edge-to-edge player poster (Recommended)"
                } else {
                    "Disabled • Floating card over blurred ambient atmosphere"
                },
                checked = uiState.fullScreenArtworkEnabled,
                onCheckedChange = {
                    haptics.play(Haptic.Tap)
                    viewModel.setFullScreenArtworkEnabled(it)
                },
                isLiquidGlass = isLiquidGlass
            )

            Spacer(modifier = Modifier.height(10.dp))

            val isLowSpec = remember { com.lofiq.core.common.DevicePerformanceManager.isLowSpecDevice(context) }
            val hardwareSummary = remember { com.lofiq.core.common.DevicePerformanceManager.getHardwareSummary(context) }

            // Toggle 2: Liquid Glass UI
            ThemeToggleCard(
                icon = Icons.Rounded.BlurOn,
                title = "Liquid Glass UI",
                subtitle = when {
                    uiState.performanceModeEnabled -> "Disabled • Performance Mode is active to preserve maximum frame rate and battery."
                    isLiquidGlass -> "Active • Frosted glassmorphism, translucent surfaces & fluid spring animations"
                    else -> "Inactive • High-contrast deep obsidian panels"
                },
                checked = isLiquidGlass,
                onCheckedChange = {
                    haptics.play(Haptic.Tap)
                    viewModel.setLiquidGlassEnabled(it)
                },
                isLiquidGlass = isLiquidGlass
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Toggle 3: Dynamic Atmosphere Glow
            ThemeToggleCard(
                icon = Icons.Rounded.AutoAwesome,
                title = "Dynamic Atmosphere Glow",
                subtitle = if (uiState.dynamicBackground) {
                    "Enabled • 360° audio-reactive color spectrum matching song artwork"
                } else {
                    "Disabled • Deep static obsidian backdrop"
                },
                checked = uiState.dynamicBackground,
                onCheckedChange = {
                    haptics.play(Haptic.Tap)
                    viewModel.setDynamicBackground(it)
                },
                isLiquidGlass = isLiquidGlass
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Toggle 4: Performance Mode (Low-RAM Optimization)
            ThemeToggleCard(
                icon = Icons.Rounded.Speed,
                title = "Performance Mode (Low-RAM)",
                subtitle = when {
                    uiState.performanceModeEnabled -> "Active • Solid surfaces & snappy transitions (Optimized for $hardwareSummary)"
                    isLowSpec -> "Inactive (Safe Guard Active) • Low-spec detected ($hardwareSummary). Heavy GPU effects are safely constrained."
                    else -> "Inactive • Liquid glass UI, fluid spring physics & atmospheric blurs enabled ($hardwareSummary)"
                },
                checked = uiState.performanceModeEnabled,
                onCheckedChange = {
                    haptics.play(Haptic.Tap)
                    viewModel.setPerformanceModeEnabled(it)
                },
                isLiquidGlass = isLiquidGlass
            )

            // Bottom clearance for floating mini-player and system navigation bar
            Spacer(modifier = Modifier.height(120.dp))
        }
    }
}

/**
 * Reusable Section Header with glowing typography.
 */
@Composable
private fun SectionHeader(
    title: String,
    subtitle: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = TextSecondary,
                letterSpacing = 1.2.sp
            )
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                color = TextTertiary
            )
        )
    }
}

/**
 * Reusable High-Fidelity Toggle Card for Settings.
 */
@Composable
private fun ThemeToggleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isLiquidGlass: Boolean
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        preset = if (checked) GlassPreset.DARK else GlassPreset.SUBTLE,
        shape = RoundedCornerShape(18.dp),
        padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        onClick = { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (checked) BrandTangerine.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f))
                        .border(
                            1.dp,
                            if (checked) BrandTangerine.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (checked) BrandTangerine else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = AccentWhite
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            color = TextSecondary,
                            lineHeight = 15.sp
                        )
                    )
                }
            }

            GlassSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                activeColor = BrandTangerine
            )
        }
    }
}

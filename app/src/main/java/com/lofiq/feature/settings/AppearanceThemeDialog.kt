package com.lofiq.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lofiq.R
import com.lofiq.core.common.AppIconTheme
import com.lofiq.ui.components.glass.*
import com.lofiq.ui.haptics.Haptic
import com.lofiq.ui.haptics.rememberHaptics
import com.lofiq.ui.theme.*

private val MonochromeCard = Color(0xFF0F0F0F)
private val MonochromeSubtle = Color(0xFF181818)
private val MonochromeBorder = Color.White.copy(alpha = 0.08f)

/**
 * Dedicated Appearance & Themes modal window.
 * Allows users to configure:
 * 1. App Icon & Logo Theme (Launcher icon & in-app branding background color)
 * 2. Full Screen Artwork (ON by default: full-bleed dissolve; OFF: floating poster card on blurred atmosphere)
 * 3. Liquid Glass UI (iOS style frosted glass & specular reflections, OFF by default)
 * 4. Dynamic Background Atmosphere Glow (ON by default)
 */
@Composable
fun AppearanceThemeDialog(
    fullScreenArtworkEnabled: Boolean,
    onToggleFullScreenArtwork: (Boolean) -> Unit,
    liquidGlassEnabled: Boolean,
    onToggleLiquidGlass: (Boolean) -> Unit,
    dynamicBackgroundEnabled: Boolean,
    onToggleDynamicBackground: (Boolean) -> Unit,
    selectedAppIcon: AppIconTheme = AppIconTheme.PURPLE,
    onSelectAppIcon: (AppIconTheme) -> Unit = {},
    onDismiss: () -> Unit
) {
    val isLiquidGlass = LocalLiquidGlassEnabled.current
    val haptics = rememberHaptics()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            preset = GlassPreset.MODAL,
            shape = RoundedCornerShape(26.dp),
            hasGlow = true
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Bar: Icon, Title & Dismiss Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(LiquidGlassTokens.PillGlassFill),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Palette,
                                contentDescription = null,
                                tint = AccentWhite,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Appearance & Theme",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 19.sp,
                                    color = AccentWhite
                                )
                            )
                            Text(
                                text = "Visuals, app icon & atmosphere",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = AccentWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Section 1: App Icon & Logo Theme Selector Card
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    preset = GlassPreset.DARK,
                    shape = RoundedCornerShape(18.dp),
                    padding = PaddingValues(14.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        // Section Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isLiquidGlass) LiquidGlassTokens.PillGlassFill else MonochromeSubtle),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ColorLens,
                                    contentDescription = null,
                                    tint = AccentWhite,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "App Icon & Logo Theme",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = AccentWhite,
                                        fontSize = 14.5.sp
                                    )
                                )
                                Text(
                                    text = "Launcher icon & app branding background",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextSecondary,
                                        fontSize = 11.5.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Active Icon Preview Banner
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Squircle Icon Preview with Dynamic Background Gradient
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        Brush.linearGradient(
                                            colors = selectedAppIcon.gradientColors
                                        )
                                    )
                                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.app_logo_transparent),
                                    contentDescription = "App Icon Preview",
                                    modifier = Modifier.size(30.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = selectedAppIcon.displayName,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = AccentWhite,
                                            fontSize = 14.sp
                                        )
                                    )
                                    if (selectedAppIcon.isDefault) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = selectedAppIcon.primaryColor.copy(alpha = 0.35f),
                                            border = BorderStroke(1.dp, selectedAppIcon.secondaryColor.copy(alpha = 0.5f))
                                        ) {
                                            Text(
                                                text = "DEFAULT",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 9.sp,
                                                    color = Color(0xFFD8B4FE)
                                                )
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Phone launcher & app manager icon",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Horizontal Color Swatches Carousel
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconTheme.entries.forEach { theme ->
                                val isSelected = theme == selectedAppIcon
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            haptics.play(Haptic.Tap)
                                            onSelectAppIcon(theme)
                                        }
                                        .padding(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    colors = theme.gradientColors
                                                )
                                            )
                                            .border(
                                                width = if (isSelected) 2.5.dp else 1.dp,
                                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(12.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.app_logo_transparent),
                                            contentDescription = theme.displayName,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color.Black.copy(alpha = 0.35f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Check,
                                                    contentDescription = "Selected",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = if (theme.isDefault) "Purple" else theme.displayName.replace("Crimson ", "").replace("Sapphire ", "").replace("Emerald ", "").replace("Sunset ", "").replace("Midnight ", "").replace("Neon ", "").replace("Golden ", "").replace("Electric ", ""),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) AccentWhite else TextSecondary
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Option 1: Full Screen Artwork (Default: ON)
                ThemeToggleRow(
                    icon = Icons.Rounded.FitScreen,
                    title = "Full Screen Artwork",
                    subtitle = if (fullScreenArtworkEnabled) {
                        "Enabled • Edge-to-edge player poster (Default)"
                    } else {
                        "Disabled • Floating card on blurred atmosphere"
                    },
                    checked = fullScreenArtworkEnabled,
                    onCheckedChange = onToggleFullScreenArtwork,
                    isLiquidGlass = isLiquidGlass
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Option 2: Liquid Glass UI (Default: ON when Performance Mode is OFF)
                ThemeToggleRow(
                    icon = Icons.Rounded.BlurOn,
                    title = "Liquid Glass UI",
                    subtitle = if (liquidGlassEnabled) {
                        "Enabled • Translucent glass & specular borders (Default)"
                    } else {
                        "Disabled • Classic solid dark theme"
                    },
                    checked = liquidGlassEnabled,
                    onCheckedChange = onToggleLiquidGlass,
                    isLiquidGlass = isLiquidGlass
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Option 3: Dynamic Background Glow (Default: ON)
                ThemeToggleRow(
                    icon = Icons.Rounded.AutoAwesome,
                    title = "Dynamic Atmosphere Glow",
                    subtitle = if (dynamicBackgroundEnabled) {
                        "Enabled • 360° reactive color spectrum (Default)"
                    } else {
                        "Disabled • Deep obsidian backdrop"
                    },
                    checked = dynamicBackgroundEnabled,
                    onCheckedChange = onToggleDynamicBackground,
                    isLiquidGlass = isLiquidGlass
                )

                Spacer(modifier = Modifier.height(22.dp))

                // Done Button (Liquid Glass Primary Button)
                GlassButton(
                    onClick = onDismiss,
                    isPrimary = true,
                    accentColor = BrandTangerine,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "Done",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.Black
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeToggleRow(
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
        shape = RoundedCornerShape(16.dp),
        padding = PaddingValues(horizontal = 14.dp, vertical = 13.dp),
        onClick = { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (checked) BrandTangerine.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f))
                    .border(
                        1.dp,
                        if (checked) BrandTangerine.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked) BrandTangerine else TextSecondary,
                    modifier = Modifier.size(19.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = AccentWhite,
                        fontSize = 14.5.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp
                    )
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            GlassSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                activeColor = BrandTangerine
            )
        }
    }
}

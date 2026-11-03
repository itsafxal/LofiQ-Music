package com.lofiq.feature.settings

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lofiq.R
import com.lofiq.core.common.AppIconManager
import com.lofiq.core.common.AppIconTheme
import com.lofiq.ui.components.LofiQLogo
import com.lofiq.ui.haptics.Haptic
import com.lofiq.ui.haptics.rememberHaptics
import com.lofiq.ui.theme.*
import kotlin.math.*

private val ScreenBackground = Color(0xFF0A0A0E)
private val SurfaceCardColor = Color(0xFF131318)
private val SurfaceSubtleColor = Color(0xFF1C1C23)
private val SubtleBorderColor = Color.White.copy(alpha = 0.08f)


/**
 * Dedicated App Icon & Themes studio window requested by user.
 * Features:
 * 1. Live dual preview of Phone Launcher Icon & In-App Header Branding
 * 2. "Current Color" round swatch with dynamic hex indicator
 * 3. Photoshop-style Circular Hue Color Wheel / Spectrum Slider
 * 4. Saturation and Brightness tone adjustment sliders
 * 5. Quick aesthetic preset chips
 * 6. "Apply & Restart" launcher restart and "Apply In-App Color" instant action
 */
@Composable
fun AppIconStudioScreen(
    onNavigateBack: () -> Unit,
    hasActiveSong: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isLiquidGlass = LocalLiquidGlassEnabled.current
    val haptics = rememberHaptics()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    val initialColor = remember(uiState.customBrandColorHex, uiState.appIconTheme) {
        if (!uiState.customBrandColorHex.isNullOrBlank()) {
            try {
                Color(android.graphics.Color.parseColor(uiState.customBrandColorHex))
            } catch (_: Exception) {
                uiState.appIconTheme.primaryColor
            }
        } else {
            uiState.appIconTheme.primaryColor
        }
    }
    var currentColor by remember(initialColor) { mutableStateOf(initialColor) }
    var hue by remember(initialColor) {
        val hsv = FloatArray(3)
        AndroidColor.RGBToHSV(
            (initialColor.red * 255).toInt(),
            (initialColor.green * 255).toInt(),
            (initialColor.blue * 255).toInt(),
            hsv
        )
        mutableFloatStateOf(hsv[0])
    }

    val currentHex = remember(currentColor) {
        val r = (currentColor.red * 255).toInt().coerceIn(0, 255)
        val g = (currentColor.green * 255).toInt().coerceIn(0, 255)
        val b = (currentColor.blue * 255).toInt().coerceIn(0, 255)
        String.format("#%02X%02X%02X", r, g, b)
    }

    val secondaryGradientColor = remember(currentColor) {
        AppIconTheme.deriveGradientSecondary(currentColor)
    }

    var showRestartDialog by remember { mutableStateOf(false) }

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
            // TOP BAR: BACK & TITLE
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                IconButton(
                    onClick = {
                        haptics.play(Haptic.Tap)
                        onNavigateBack()
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (isLiquidGlass) LiquidGlassTokens.PillGlassFill else SurfaceSubtleColor)
                        .border(1.dp, SubtleBorderColor, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = AccentWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "App Icon Studio",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 21.sp,
                            color = AccentWhite,
                            letterSpacing = (-0.5).sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Customize phone launcher icon & pick color",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ==========================================
            // LAUNCHER ICON SHOWCASE PREVIEW
            // ==========================================
            LauncherIconShowcaseCard(
                primaryColor = currentColor,
                hex = currentHex,
                isLiquidGlass = isLiquidGlass
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ==========================================
            // CURRENT COLOR ROUND SWATCH & STATUS
            // ==========================================
            CurrentColorCard(
                color = currentColor,
                hex = currentHex,
                isLiquidGlass = isLiquidGlass
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ==========================================
            // PHOTOSHOP-STYLE COLOR PICKER (CIRCLE / WHEEL)
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "COLOR WHEEL & SPECTRUM",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        letterSpacing = 1.2.sp
                    )
                )

                if (currentColor != AppIconTheme.PURPLE.primaryColor) {
                    Text(
                        text = "Reset Default",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp,
                            color = AccentWhite.copy(alpha = 0.75f)
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                haptics.play(Haptic.Tap)
                                hue = 270f
                                currentColor = AppIconTheme.PURPLE.primaryColor
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Photoshop Color Wheel Card (Vibrance & Brightness auto-set to pure vivid color)
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = if (isLiquidGlass) LiquidGlassTokens.CardGlassFill else SurfaceCardColor,
                border = if (isLiquidGlass) LiquidGlassTokens.specularBorderStroke() else BorderStroke(1.dp, SubtleBorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Circular Hue Wheel with live dynamic center swatch
                    PhotoshopHueWheel(
                        currentHue = hue,
                        currentColor = currentColor,
                        onHueChanged = { newHue ->
                            hue = newHue
                            val argb = AndroidColor.HSVToColor(floatArrayOf(newHue, 1f, 1f))
                            currentColor = Color(argb)
                            haptics.play(Haptic.Tick)
                        },
                        modifier = Modifier
                            .size(210.dp)
                            .padding(4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==========================================
            // ACTION BUTTON: APPLY COLOR & RESTART LAUNCHER
            // ==========================================
            val buttonContentColor = if (currentColor.luminance() > 0.5f) Color.Black else Color.White
            Button(
                onClick = {
                    haptics.play(Haptic.Tap)
                    showRestartDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = currentColor,
                    contentColor = buttonContentColor
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    contentDescription = null,
                    tint = buttonContentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Apply Color & Restart Launcher",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = buttonContentColor
                    )
                )
            }

            // Generous bottom clearance to ensure MiniPlayer never covers the button or content when scrolling
            val bottomClearance = if (hasActiveSong) 140.dp else 40.dp
            Spacer(modifier = Modifier.height(bottomClearance))
        }

        // ==========================================
        // CONFIRMATION DIALOG (SUV-MUSIC STYLE)
        // ==========================================
        if (showRestartDialog) {
            AlertDialog(
                onDismissRequest = { showRestartDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(currentColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.app_logo_transparent),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(Color.White),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Text(
                            text = "Apply Color & Restart?",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = AccentWhite
                            )
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "LofiQ will update your phone launcher icon to $currentHex.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = AccentWhite
                            )
                        )
                        Text(
                            text = "Android requires closing the app so your home screen launcher can refresh. Tap the LofiQ icon from your home screen after that to open.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary,
                                lineHeight = 18.sp
                            )
                        )
                    }
                },
                confirmButton = {
                    val dialogButtonContentColor = if (currentColor.luminance() > 0.5f) Color.Black else Color.White
                    Button(
                        onClick = {
                            showRestartDialog = false
                            android.widget.Toast.makeText(
                                context,
                                "Applying custom icon ($currentHex)…",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            viewModel.applyCustomColorAndRestart(currentColor)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = currentColor,
                            contentColor = dialogButtonContentColor
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Apply & Restart", color = dialogButtonContentColor, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRestartDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = SurfaceCardColor,
                shape = RoundedCornerShape(22.dp)
            )
        }
    }
}

/**
 * Focused preview card for Phone Launcher Icon.
 */
@Composable
private fun LauncherIconShowcaseCard(
    primaryColor: Color,
    hex: String,
    isLiquidGlass: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = if (isLiquidGlass) LiquidGlassTokens.CardGlassFill else SurfaceCardColor,
        border = if (isLiquidGlass) LiquidGlassTokens.specularBorderStroke() else BorderStroke(1.dp, SubtleBorderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .shadow(
                        elevation = 18.dp,
                        shape = RoundedCornerShape(22.dp),
                        ambientColor = primaryColor,
                        spotColor = primaryColor.copy(alpha = 0.65f)
                    )
                    .size(80.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(primaryColor)
                    .border(1.5.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.White.copy(alpha = 0.16f),
                                0.45f to Color.Transparent,
                                1.0f to Color.Black.copy(alpha = 0.10f)
                            )
                        )
                )
                Image(
                    painter = painterResource(id = R.drawable.app_logo_transparent),
                    contentDescription = "Launcher Icon Preview",
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier.size(50.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Launcher App Icon Preview",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = AccentWhite
                )
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Applied to your phone's home screen & app drawer",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.5.sp,
                    color = TextSecondary
                )
            )
        }
    }
}

/**
 * Prominent Current Color card showing a round swatch with the chosen color and hex code.
 */
@Composable
private fun CurrentColorCard(
    color: Color,
    hex: String,
    isLiquidGlass: Boolean
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isLiquidGlass) LiquidGlassTokens.CardGlassFill else SurfaceCardColor,
        border = BorderStroke(1.dp, SubtleBorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Round Circle for Current Color
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .shadow(10.dp, CircleShape, spotColor = color)
                        .clip(CircleShape)
                        .background(color)
                        .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                )

                Column {
                    Text(
                        text = "Current Color",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = AccentWhite
                        )
                    )
                    Text(
                        text = "Custom Palette Active",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = color.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
            ) {
                Text(
                    text = hex,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = AccentWhite
                    )
                )
            }
        }
    }
}

/**
 * Interactive Photoshop-style Circular Hue Wheel in Compose.
 */
@Composable
private fun PhotoshopHueWheel(
    currentHue: Float,
    currentColor: Color,
    onHueChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val angle = calculateAngle(center, offset)
                        onHueChanged(angle)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val angle = calculateAngle(center, change.position)
                        onHueChanged(angle)
                    }
                }
        ) {
            val strokeWidth = 30.dp.toPx()
            val radius = (minOf(size.width, size.height) - strokeWidth) / 2f

            // Sweep rainbow hue gradient starting at 0° (Red at 3 o'clock)
            val rainbowColors = listOf(
                Color.Red,      // 0°
                Color.Yellow,   // 60°
                Color.Green,    // 120°
                Color.Cyan,     // 180°
                Color.Blue,     // 240°
                Color.Magenta,  // 300°
                Color.Red       // 360°
            )
            val brush = Brush.sweepGradient(
                colors = rainbowColors,
                center = center
            )

            drawCircle(
                brush = brush,
                radius = radius,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Current Hue Indicator Thumb on the circle
            val angleRad = Math.toRadians(currentHue.toDouble())
            val thumbX = center.x + radius * cos(angleRad).toFloat()
            val thumbY = center.y + radius * sin(angleRad).toFloat()

            // Outer white thumb glow & ring
            drawCircle(
                color = Color.Black.copy(alpha = 0.5f),
                radius = 16.dp.toPx(),
                center = Offset(thumbX, thumbY)
            )
            drawCircle(
                color = Color.White,
                radius = 14.dp.toPx(),
                center = Offset(thumbX, thumbY)
            )
            val thumbArgb = AndroidColor.HSVToColor(floatArrayOf(currentHue, 1f, 1f))
            drawCircle(
                color = Color(thumbArgb),
                radius = 10.dp.toPx(),
                center = Offset(thumbX, thumbY)
            )
        }

        // Inner center dynamic circle swatch showing live color!
        Box(
            modifier = Modifier
                .size(72.dp)
                .shadow(12.dp, CircleShape, spotColor = currentColor)
                .clip(CircleShape)
                .background(currentColor)
                .border(2.5.dp, Color.White.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.ColorLens,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

private fun calculateAngle(center: Offset, point: Offset): Float {
    val dx = point.x - center.x
    val dy = point.y - center.y
    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    if (angle < 0f) angle += 360f
    return angle % 360f
}


package com.lofiq.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lofiq.core.model.SeekbarStyle
import com.lofiq.ui.components.seekbar.CustomPlayerSeekbar
import com.lofiq.ui.haptics.Haptic
import com.lofiq.ui.haptics.rememberHaptics
import com.lofiq.ui.theme.*
import kotlin.math.sin

private val ScreenBackground = Color(0xFF0A0A0E)
private val SurfaceCardColor = Color(0xFF131318)
private val SurfaceSubtleColor = Color(0xFF1C1C23)
private val SubtleBorderColor = Color.White.copy(alpha = 0.08f)

/**
 * Dedicated Seekbar Style Customization Screen.
 * Lets users pick from 10 distinct, animated seekbar designs.
 */
@Composable
fun SeekbarStyleScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isLiquidGlass = LocalLiquidGlassEnabled.current
    val haptics = rememberHaptics()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    var selectedStyle by remember(uiState.seekbarStyle) { mutableStateOf(uiState.seekbarStyle) }
    val activeBrandColor = uiState.activeBrandColor

    // Demo Player State for live interactive testing
    var demoPlaying by remember { mutableStateOf(true) }
    var demoPositionMs by remember { mutableLongStateOf(84_000L) }
    val demoDurationMs = 228_000L

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
            // TOP BAR: BACK BUTTON & HEADER
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
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

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = "Seekbar Style",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = AccentWhite,
                            letterSpacing = (-0.5).sp
                        )
                    )
                    Text(
                        text = "Customize player seekbar & playback animations",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // LIVE INTERACTIVE DEMO CARD
            // ==========================================
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isLiquidGlass) LiquidGlassTokens.CardGlassFill else SurfaceCardColor
                ),
                border = if (isLiquidGlass) {
                    LiquidGlassTokens.specularBorderStroke()
                } else {
                    androidx.compose.foundation.BorderStroke(1.dp, SubtleBorderColor)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(activeBrandColor.copy(alpha = 0.20f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = null,
                                    tint = activeBrandColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Live Interactive Preview",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = AccentWhite
                                    )
                                )
                                Text(
                                    text = selectedStyle.label,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        color = activeBrandColor,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }

                        // Play / Pause Demo Button
                        IconButton(
                            onClick = {
                                haptics.play(Haptic.Tap)
                                demoPlaying = !demoPlaying
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(activeBrandColor)
                        ) {
                            Icon(
                                imageVector = if (demoPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (demoPlaying) "Pause" else "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Live Interactive Custom Seekbar
                    CustomPlayerSeekbar(
                        progress = (demoPositionMs.toFloat() / demoDurationMs.toFloat()).coerceIn(0f, 1f),
                        duration = demoDurationMs,
                        currentPosition = demoPositionMs,
                        isPlaying = demoPlaying,
                        style = selectedStyle,
                        primaryColor = activeBrandColor,
                        onSeek = { targetMs ->
                            demoPositionMs = targetMs
                        },
                        onSeeking = { isSeeking, frac ->
                            if (isSeeking) {
                                demoPositionMs = (frac * demoDurationMs).toLong()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "01:24",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        )
                        Text(
                            text = "-02:24",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==========================================
            // STYLE SELECTION SECTION
            // ==========================================
            Text(
                text = "AVAILABLE STYLES",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                ),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SeekbarStyle.entries.forEach { style ->
                    val isSelected = style == selectedStyle
                    SeekbarStyleItemCard(
                        style = style,
                        isSelected = isSelected,
                        activeColor = activeBrandColor,
                        isLiquidGlass = isLiquidGlass,
                        onClick = {
                            haptics.play(Haptic.Select)
                            selectedStyle = style
                            viewModel.setSeekbarStyle(style)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Helpful footer hint
            Text(
                text = "💡 Tip: You can also quickly switch seekbar styles anytime by long-pressing directly on the seekbar in the Now Playing screen.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                ),
                modifier = Modifier.padding(horizontal = 6.dp)
            )

            // Bottom clearance for floating mini-player and system navigation bar
            Spacer(modifier = Modifier.height(120.dp))
        }
    }
}

/**
 * Bottom Sheet for quick in-player seekbar style switching on long-press.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeekbarStyleBottomSheet(
    currentStyle: SeekbarStyle,
    onSelectStyle: (SeekbarStyle) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = rememberHaptics()
    val brandColor = LocalBrandColor.current
    val isLiquidGlass = LocalLiquidGlassEnabled.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (isLiquidGlass) LiquidGlassTokens.CardGlassFill else Color(0xFF131318),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = Color.White.copy(alpha = 0.35f)
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Seekbar Style",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = AccentWhite
                        )
                    )
                    Text(
                        text = "Choose your preferred player seekbar",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SeekbarStyle.entries.forEach { style ->
                    SeekbarStyleItemCard(
                        style = style,
                        isSelected = style == currentStyle,
                        activeColor = brandColor,
                        isLiquidGlass = isLiquidGlass,
                        onClick = {
                            haptics.play(Haptic.Select)
                            onSelectStyle(style)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Individual Seekbar Style Card with live mini Canvas preview.
 */
@Composable
fun SeekbarStyleItemCard(
    style: SeekbarStyle,
    isSelected: Boolean,
    activeColor: Color,
    isLiquidGlass: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) activeColor else SubtleBorderColor,
        animationSpec = tween(300),
        label = "borderColor"
    )

    val cardBg = if (isSelected) {
        activeColor.copy(alpha = 0.12f)
    } else if (isLiquidGlass) {
        LiquidGlassTokens.CardGlassFill
    } else {
        SurfaceCardColor
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = borderColor
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mini Preview Canvas
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.40f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                MiniSeekbarCanvasPreview(
                    style = style,
                    activeColor = activeColor
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Style Title & Description
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = style.label,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = if (isSelected) AccentWhite else AccentWhite.copy(alpha = 0.90f)
                        )
                    )

                    if (style == SeekbarStyle.LOFIQ_WAVE) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(activeColor.copy(alpha = 0.20f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "DEFAULT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = activeColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = style.description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.5.sp,
                        color = TextSecondary,
                        lineHeight = 15.sp
                    )
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Selection Radio / Check Pill
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) activeColor else SurfaceSubtleColor)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) activeColor else SubtleBorderColor,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Mini vector preview canvas for individual seekbar style cards.
 */
@Composable
private fun MiniSeekbarCanvasPreview(
    style: SeekbarStyle,
    activeColor: Color
) {
    val inactiveColor = Color.White.copy(alpha = 0.25f)
    val progress = 0.62f

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val progressX = progress * width

        when (style) {
            SeekbarStyle.LOFIQ_WAVE -> {
                // Multi-layer sine waves
                val path1 = Path().apply {
                    moveTo(0f, centerY)
                    for (x in 0..width.toInt()) {
                        val y = centerY + sin(x * 0.12f) * (height * 0.22f)
                        lineTo(x.toFloat(), y)
                    }
                }
                val path2 = Path().apply {
                    moveTo(0f, centerY)
                    for (x in 0..progressX.toInt()) {
                        val y = centerY + sin(x * 0.18f + 1.2f) * (height * 0.32f)
                        lineTo(x.toFloat(), y)
                    }
                }
                drawPath(path1, inactiveColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                drawPath(path2, activeColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(activeColor, radius = 4.dp.toPx(), center = Offset(progressX, centerY))
            }
            SeekbarStyle.WAVEFORM -> {
                val barCount = 12
                val barW = width / barCount
                for (i in 0 until barCount) {
                    val x = i * barW + barW / 2f
                    val isPast = x <= progressX
                    val h = (sin(i * 0.8) * 0.4 + 0.6).toFloat() * (height * 0.65f)
                    val c = if (isPast) activeColor else inactiveColor
                    drawRoundRect(
                        color = c,
                        topLeft = Offset(x - 1.5.dp.toPx(), centerY - h / 2f),
                        size = Size(3.dp.toPx(), h),
                        cornerRadius = CornerRadius(1.5.dp.toPx())
                    )
                }
            }
            SeekbarStyle.WAVE_LINE -> {
                val wavePath = Path().apply {
                    moveTo(0f, centerY)
                    var x = 0f
                    while (x <= progressX) {
                        val y = centerY + sin(x * 0.15f) * (height * 0.25f)
                        lineTo(x, y)
                        x += 2f
                    }
                }
                drawLine(inactiveColor, Offset(progressX, centerY), Offset(width, centerY), strokeWidth = 2.5.dp.toPx())
                drawPath(wavePath, activeColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(activeColor, radius = 3.5.dp.toPx(), center = Offset(progressX, centerY))
            }
            SeekbarStyle.CLASSIC -> {
                val trackH = 3.5.dp.toPx()
                drawRoundRect(
                    color = inactiveColor,
                    topLeft = Offset(0f, centerY - trackH / 2f),
                    size = Size(width, trackH),
                    cornerRadius = CornerRadius(trackH / 2f)
                )
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(0f, centerY - trackH / 2f),
                    size = Size(progressX, trackH),
                    cornerRadius = CornerRadius(trackH / 2f)
                )
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(progressX - 1.5.dp.toPx(), centerY - 6.dp.toPx()),
                    size = Size(3.dp.toPx(), 12.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
                )
            }
            SeekbarStyle.DOTS -> {
                val count = 8
                val spacing = width / count
                for (i in 0 until count) {
                    val x = i * spacing + spacing / 2f
                    val isPast = x <= progressX
                    drawCircle(
                        color = if (isPast) activeColor else inactiveColor,
                        radius = if (isPast) 2.8.dp.toPx() else 2.dp.toPx(),
                        center = Offset(x, centerY)
                    )
                }
                drawCircle(activeColor, radius = 4.5.dp.toPx(), center = Offset(progressX, centerY))
            }
            SeekbarStyle.GRADIENT_BAR -> {
                val trackH = 4.dp.toPx()
                drawRoundRect(
                    color = inactiveColor,
                    topLeft = Offset(0f, centerY - trackH / 2f),
                    size = Size(width, trackH),
                    cornerRadius = CornerRadius(trackH / 2f)
                )
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(activeColor.copy(alpha = 0.5f), activeColor, Color.White),
                        startX = 0f,
                        endX = progressX
                    ),
                    topLeft = Offset(0f, centerY - trackH / 2f),
                    size = Size(progressX, trackH),
                    cornerRadius = CornerRadius(trackH / 2f)
                )
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(progressX - 2.dp.toPx(), centerY - 6.dp.toPx()),
                    size = Size(4.dp.toPx(), 12.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }
            SeekbarStyle.NEON -> {
                drawLine(inactiveColor, Offset(0f, centerY), Offset(width, centerY), strokeWidth = 2.5.dp.toPx())
                drawLine(activeColor.copy(alpha = 0.4f), Offset(0f, centerY), Offset(progressX, centerY), strokeWidth = 6.dp.toPx())
                drawLine(activeColor, Offset(0f, centerY), Offset(progressX, centerY), strokeWidth = 2.5.dp.toPx())
                drawCircle(activeColor.copy(alpha = 0.5f), radius = 6.dp.toPx(), center = Offset(progressX, centerY))
                drawCircle(Color.White, radius = 3.dp.toPx(), center = Offset(progressX, centerY))
            }
            SeekbarStyle.BLOCKS -> {
                val count = 8
                val bw = width / count
                for (i in 0 until count) {
                    val x = i * bw + 2.dp.toPx()
                    val isPast = (x + bw) <= progressX
                    drawRoundRect(
                        color = if (isPast) activeColor else inactiveColor,
                        topLeft = Offset(x, centerY - 4.dp.toPx()),
                        size = Size(bw - 4.dp.toPx(), 8.dp.toPx()),
                        cornerRadius = CornerRadius(1.5.dp.toPx())
                    )
                }
            }
            SeekbarStyle.MATERIAL -> {
                val trackH = 3.5.dp.toPx()
                drawLine(inactiveColor, Offset(0f, centerY), Offset(width, centerY), strokeWidth = trackH, cap = StrokeCap.Round)
                drawLine(activeColor, Offset(0f, centerY), Offset(progressX, centerY), strokeWidth = trackH, cap = StrokeCap.Round)
                drawCircle(activeColor, radius = 5.dp.toPx(), center = Offset(progressX, centerY))
            }
            SeekbarStyle.M3E_WAVY -> {
                val wavePath = Path().apply {
                    moveTo(0f, centerY)
                    for (x in 0..width.toInt()) {
                        val y = centerY + sin(x * 0.16f) * (height * 0.25f)
                        lineTo(x.toFloat(), y)
                    }
                }
                drawPath(wavePath, inactiveColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                val activeWave = Path().apply {
                    moveTo(0f, centerY)
                    for (x in 0..progressX.toInt()) {
                        val y = centerY + sin(x * 0.16f) * (height * 0.25f)
                        lineTo(x.toFloat(), y)
                    }
                }
                drawPath(activeWave, activeColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                val thumbY = centerY + sin(progressX * 0.16f) * (height * 0.25f)
                drawCircle(activeColor, radius = 4.5.dp.toPx(), center = Offset(progressX, thumbY))
            }
        }
    }
}

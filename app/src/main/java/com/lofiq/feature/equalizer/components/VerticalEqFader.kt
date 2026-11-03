package com.lofiq.feature.equalizer.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lofiq.ui.haptics.Haptic
import com.lofiq.ui.haptics.rememberHaptics
import com.lofiq.ui.theme.*
import kotlin.math.abs

/**
 * Professional Studio Vertical Equalizer Fader with center 0dB detent,
 * bidirectional gain fill, tactile thumb handle, and frequency badge.
 */
@Composable
fun VerticalEqFader(
    value: Float, // 0.0f (minLevel) to 1.0f (maxLevel), 0.5f is 0 dB
    onValueChange: (Float) -> Unit,
    freqLabel: String,
    gainDb: Int,
    modifier: Modifier = Modifier,
    height: Dp = 190.dp,
    enabled: Boolean = true,
    activeColor: Color = Color(0xFF00E5FF),
    secondaryColor: Color = Color(0xFF8C52FF)
) {
    val haptics = rememberHaptics()
    var wasAtZero by remember { mutableStateOf(gainDb == 0) }

    val animatedProgress by animateFloatAsState(
        targetValue = value.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 60),
        label = "verticalFaderProgress"
    )

    // Check zero crossing for haptic feedback
    LaunchedEffect(gainDb) {
        val isZero = gainDb == 0
        if (isZero != wasAtZero) {
            wasAtZero = isZero
            if (isZero) haptics.play(Haptic.Select)
        }
    }

    Column(
        modifier = modifier.width(52.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. Live dB Badge at the top
        val gainText = when {
            gainDb > 0 -> "+$gainDb dB"
            gainDb < 0 -> "$gainDb dB"
            else -> "0 dB"
        }
        val isNonZero = gainDb != 0 && enabled

        Box(
            modifier = Modifier
                .height(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (isNonZero) activeColor.copy(alpha = 0.15f)
                    else Color.White.copy(alpha = 0.05f)
                )
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = gainText,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = when {
                        !enabled -> TextTertiary
                        isNonZero -> activeColor
                        else -> TextSecondary
                    },
                    fontWeight = if (isNonZero) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 10.sp
                )
            )
        }

        // 2. Vertical Fader Track Canvas
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(height)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    val trackHeight = this.size.height.toFloat()
                    detectTapGestures { offset ->
                        val fraction = (1f - (offset.y / trackHeight)).coerceIn(0f, 1f)
                        haptics.play(Haptic.Tick)
                        onValueChange(fraction)
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    val trackHeight = this.size.height.toFloat()
                    detectDragGestures(
                        onDragStart = { offset ->
                            haptics.play(Haptic.Tick)
                            val fraction = (1f - (offset.y / trackHeight)).coerceIn(0f, 1f)
                            onValueChange(fraction)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val fraction = (1f - (change.position.y / trackHeight)).coerceIn(0f, 1f)
                            onValueChange(fraction)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val centerX = canvasWidth / 2f
                val trackWidth = 6.dp.toPx()
                val handleHeight = 26.dp.toPx()
                val handleWidth = 36.dp.toPx()

                val usableHeight = canvasHeight - handleHeight
                val handleCenterY = (1f - animatedProgress) * usableHeight + (handleHeight / 2f)
                val zeroCenterY = 0.5f * usableHeight + (handleHeight / 2f)

                val trackAlpha = if (enabled) 1f else 0.35f

                // Draw background track slot
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.10f * trackAlpha),
                    topLeft = Offset(centerX - trackWidth / 2f, handleHeight / 2f),
                    size = Size(trackWidth, usableHeight),
                    cornerRadius = CornerRadius(trackWidth / 2f)
                )

                // Draw 0 dB center reference line tick
                drawLine(
                    color = Color.White.copy(alpha = 0.35f * trackAlpha),
                    start = Offset(centerX - 10.dp.toPx(), zeroCenterY),
                    end = Offset(centerX + 10.dp.toPx(), zeroCenterY),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Draw active bidirectional fill from 0 dB center point to handle
                if (enabled && abs(handleCenterY - zeroCenterY) > 1f) {
                    val fillTop = minOf(handleCenterY, zeroCenterY)
                    val fillHeight = abs(handleCenterY - zeroCenterY)
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(activeColor, secondaryColor),
                            startY = 0f,
                            endY = canvasHeight
                        ),
                        topLeft = Offset(centerX - trackWidth / 2f, fillTop),
                        size = Size(trackWidth, fillHeight),
                        cornerRadius = CornerRadius(trackWidth / 2f)
                    )
                }

                // Draw Studio Fader Handle / Slider Knob
                val handleLeft = centerX - handleWidth / 2f
                val handleTop = handleCenterY - handleHeight / 2f

                // Handle Shadow / Ambient Glow
                if (enabled && gainDb != 0) {
                    drawRoundRect(
                        color = activeColor.copy(alpha = 0.20f),
                        topLeft = Offset(handleLeft - 2.dp.toPx(), handleTop - 2.dp.toPx()),
                        size = Size(handleWidth + 4.dp.toPx(), handleHeight + 4.dp.toPx()),
                        cornerRadius = CornerRadius(8.dp.toPx())
                    )
                }

                // Handle Body (Brushed dark metal)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF2C2C32),
                            Color(0xFF1E1E22),
                            Color(0xFF141417)
                        ),
                        startY = handleTop,
                        endY = handleTop + handleHeight
                    ),
                    topLeft = Offset(handleLeft, handleTop),
                    size = Size(handleWidth, handleHeight),
                    cornerRadius = CornerRadius(6.dp.toPx())
                )

                // Handle Border / Bevel
                drawRoundRect(
                    color = if (enabled) Color.White.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.08f),
                    topLeft = Offset(handleLeft, handleTop),
                    size = Size(handleWidth, handleHeight),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx())
                )

                // Center Grip Line on Handle
                val gripLineColor = if (enabled) {
                    if (gainDb != 0) activeColor else Color.White.copy(alpha = 0.8f)
                } else {
                    Color.White.copy(alpha = 0.3f)
                }
                drawLine(
                    color = gripLineColor,
                    start = Offset(handleLeft + 6.dp.toPx(), handleCenterY),
                    end = Offset(handleLeft + handleWidth - 6.dp.toPx(), handleCenterY),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        // 3. Frequency Label Badge at the bottom
        Text(
            text = freqLabel,
            style = MaterialTheme.typography.labelMedium.copy(
                color = if (enabled) AccentWhite else TextTertiary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp
            )
        )
    }
}

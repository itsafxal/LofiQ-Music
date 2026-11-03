package com.lofiq.feature.equalizer.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lofiq.ui.haptics.Haptic
import com.lofiq.ui.haptics.rememberHaptics
import com.lofiq.ui.theme.*
import kotlin.math.*

/**
 * Audiophile Studio Rotary Knob with 270° sweep, glowing gradient arc,
 * tick markers, brushed metal finish, and instant tactile haptic feedback.
 */
@Composable
fun RotaryKnob(
    value: Float, // 0.0f to 1.0f
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String = "${(value * 100).toInt()}%",
    icon: ImageVector? = null,
    size: Dp = 125.dp,
    enabled: Boolean = true,
    activeColor: Color = Color(0xFF00E5FF),
    secondaryColor: Color = Color(0xFF8C52FF)
) {
    val haptics = rememberHaptics()
    var lastHapticStep by remember { mutableIntStateOf((value * 25).toInt()) }

    val animatedValue by animateFloatAsState(
        targetValue = value.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 30),
        label = "rotaryKnobValue"
    )

    val startAngle = 135f
    val sweepAngle = 270f

    fun calculateValueFromAngle(touchX: Float, touchY: Float, centerX: Float, centerY: Float): Float {
        val angleRad = atan2(touchY - centerY, touchX - centerX)
        var angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()
        if (angleDeg < 0) angleDeg += 360f

        val relativeAngle = (angleDeg - startAngle + 360f) % 360f
        return when {
            relativeAngle <= sweepAngle -> (relativeAngle / sweepAngle).coerceIn(0f, 1f)
            relativeAngle > sweepAngle && relativeAngle < sweepAngle + 45f -> 1f
            else -> 0f
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    val widthPx = this.size.width.toFloat()
                    val heightPx = this.size.height.toFloat()
                    val center = Offset(widthPx / 2f, heightPx / 2f)

                    detectTapGestures { offset ->
                        val newVal = calculateValueFromAngle(offset.x, offset.y, center.x, center.y)
                        haptics.play(Haptic.Select)
                        onValueChange(newVal)
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    val widthPx = this.size.width.toFloat()
                    val heightPx = this.size.height.toFloat()
                    val center = Offset(widthPx / 2f, heightPx / 2f)

                    detectDragGestures(
                        onDragStart = { offset ->
                            haptics.play(Haptic.Tick)
                            val newVal = calculateValueFromAngle(offset.x, offset.y, center.x, center.y)
                            onValueChange(newVal)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val newVal = calculateValueFromAngle(change.position.x, change.position.y, center.x, center.y)
                            val step = (newVal * 25).toInt()
                            if (step != lastHapticStep) {
                                lastHapticStep = step
                                haptics.play(Haptic.Tick)
                            }
                            onValueChange(newVal)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = this.size.width
                val canvasHeight = this.size.height
                val center = Offset(canvasWidth / 2f, canvasHeight / 2f)
                val strokeWidth = 7.dp.toPx()
                val radius = (min(canvasWidth, canvasHeight) - strokeWidth * 3) / 2f

                val arcAlpha = if (enabled) 1f else 0.35f
                val effectiveTrackColor = Color.White.copy(alpha = 0.10f * arcAlpha)

                // 1. Draw outer tick marks
                val totalTicks = 24
                for (i in 0..totalTicks) {
                    val tickFraction = i.toFloat() / totalTicks
                    val tickAngle = startAngle + tickFraction * sweepAngle
                    val tickRad = Math.toRadians(tickAngle.toDouble())
                    val isTickActive = tickFraction <= animatedValue && enabled

                    val outerTickRadius = radius + strokeWidth + 5.dp.toPx()
                    val innerTickRadius = outerTickRadius - if (i % 6 == 0) 4.5.dp.toPx() else 2.5.dp.toPx()

                    val startX = center.x + innerTickRadius * cos(tickRad).toFloat()
                    val startY = center.y + innerTickRadius * sin(tickRad).toFloat()
                    val endX = center.x + outerTickRadius * cos(tickRad).toFloat()
                    val endY = center.y + outerTickRadius * sin(tickRad).toFloat()

                    val tickColor = when {
                        !enabled -> Color.White.copy(alpha = 0.08f)
                        isTickActive -> activeColor.copy(alpha = 0.85f)
                        else -> Color.White.copy(alpha = 0.20f)
                    }

                    drawLine(
                        color = tickColor,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = if (i % 6 == 0) 1.8.dp.toPx() else 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // 2. Inactive Background Arc Track
                drawArc(
                    color = effectiveTrackColor,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // 3. Active Glowing Arc Track
                if (animatedValue > 0.005f && enabled) {
                    val activeSweep = sweepAngle * animatedValue
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(activeColor, secondaryColor, activeColor),
                            center = center
                        ),
                        startAngle = startAngle,
                        sweepAngle = activeSweep,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                // 4. Inner Dark Metallic Disc
                val innerKnobRadius = radius - strokeWidth - 2.dp.toPx()
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF222228),
                            Color(0xFF141418),
                            Color(0xFF0C0C0F)
                        ),
                        center = center,
                        radius = innerKnobRadius
                    ),
                    radius = innerKnobRadius,
                    center = center
                )

                // Disc Bevel Border
                drawCircle(
                    color = if (enabled) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f),
                    radius = innerKnobRadius,
                    center = center,
                    style = Stroke(width = 1.2.dp.toPx())
                )

                // 5. Indicator Dot
                val currentAngle = startAngle + animatedValue * sweepAngle
                val currentRad = Math.toRadians(currentAngle.toDouble())
                val indicatorDistance = innerKnobRadius - 7.dp.toPx()
                val indicatorX = center.x + indicatorDistance * cos(currentRad).toFloat()
                val indicatorY = center.y + indicatorDistance * sin(currentRad).toFloat()

                if (enabled) {
                    drawCircle(
                        color = activeColor.copy(alpha = 0.35f),
                        radius = 5.dp.toPx(),
                        center = Offset(indicatorX, indicatorY)
                    )
                    drawCircle(
                        color = activeColor,
                        radius = 3.dp.toPx(),
                        center = Offset(indicatorX, indicatorY)
                    )
                } else {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.25f),
                        radius = 2.5.dp.toPx(),
                        center = Offset(indicatorX, indicatorY)
                    )
                }
            }

            // Center Text inside Knob
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = if (enabled) AccentWhite else TextTertiary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                )
            }
        }

        // Title underneath Knob
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
                color = if (enabled) TextSecondary else TextTertiary,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 0.8.sp
            )
        )
    }
}

package com.lofiq.feature.player

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lofiq.playback.timer.SleepTimerManager
import com.lofiq.playback.timer.SleepTimerState
import com.lofiq.ui.haptics.Haptic
import com.lofiq.ui.haptics.rememberHaptics
import com.lofiq.ui.theme.*
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    sleepTimerManager: SleepTimerManager,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHaptics()
    val timerState by sleepTimerManager.timerState.collectAsState()
    var selectedMinutes by remember { mutableIntStateOf(30) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val neonCyan = Color(0xFF00E5FF)
    val neonPurple = Color(0xFF8C52FF)
    val cardBg = Color(0xFF131318)
    val cardBorder = Color(0xFF26262F)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(neonCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Timer,
                            contentDescription = null,
                            tint = neonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Sleep Timer",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = AccentWhite,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = if (timerState.isActive) "Timer active" else "Stop music automatically",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = AccentWhite,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (timerState.isActive) {
                // ACTIVE TIMER VIEW (Countdown + Controls)
                val remainingSec = timerState.remainingSeconds
                val mins = remainingSec / 60
                val secs = remainingSec % 60
                val timeFormatted = if (timerState.isEndOfTrack) {
                    "End of Track"
                } else {
                    String.format("%02d:%02d", mins, secs)
                }

                val progress = if (timerState.totalSeconds > 0) {
                    (timerState.remainingSeconds.toFloat() / timerState.totalSeconds.toFloat()).coerceIn(0f, 1f)
                } else 1f

                // Large Glowing Countdown Ring
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = 8.dp.toPx()
                        val radius = (size.minDimension - stroke) / 2f
                        val center = Offset(size.width / 2f, size.height / 2f)

                        // Track
                        drawCircle(
                            color = Color.White.copy(alpha = 0.10f),
                            radius = radius,
                            center = center,
                            style = Stroke(width = stroke)
                        )

                        // Glowing Arc
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(neonCyan, neonPurple, neonCyan),
                                center = center
                            ),
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = timeFormatted,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = AccentWhite,
                                fontSize = if (timerState.isEndOfTrack) 20.sp else 32.sp
                            )
                        )
                        if (!timerState.isEndOfTrack) {
                            Text(
                                text = "REMAINING",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = neonCyan,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }

                // Extension Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(5, 15, 30).forEach { extra ->
                        OutlinedButton(
                            onClick = {
                                haptics.play(Haptic.Select)
                                sleepTimerManager.addMinutes(extra)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = AccentWhite
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("+$extra m", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Stop / Cancel Timer Button
                Button(
                    onClick = {
                        haptics.play(Haptic.ToggleOff)
                        sleepTimerManager.cancelTimer()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF3B30).copy(alpha = 0.20f),
                        contentColor = Color(0xFFFF453A)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(imageVector = Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cancel Timer", fontWeight = FontWeight.Bold)
                }

            } else {
                // TIMER PICKER VIEW (Rotary Time Dial + Steppers + Presets)

                RotaryTimePicker(
                    minutes = selectedMinutes,
                    onMinutesChange = { newMins ->
                        selectedMinutes = newMins
                    },
                    modifier = Modifier.padding(vertical = 2.dp)
                )

                // Stepper Buttons (-5m, -1m, +1m, +5m)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                haptics.play(Haptic.Tick)
                                selectedMinutes = (selectedMinutes - 5).coerceIn(1, 120)
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(cardBg)
                                .border(1.dp, cardBorder, CircleShape)
                        ) {
                            Text("-5", color = AccentWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        IconButton(
                            onClick = {
                                haptics.play(Haptic.Tick)
                                selectedMinutes = (selectedMinutes - 1).coerceIn(1, 120)
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(cardBg)
                                .border(1.dp, cardBorder, CircleShape)
                        ) {
                            Text("-1", color = AccentWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        IconButton(
                            onClick = {
                                haptics.play(Haptic.Tick)
                                selectedMinutes = (selectedMinutes + 1).coerceIn(1, 120)
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(cardBg)
                                .border(1.dp, cardBorder, CircleShape)
                        ) {
                            Text("+1", color = AccentWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        IconButton(
                            onClick = {
                                haptics.play(Haptic.Tick)
                                selectedMinutes = (selectedMinutes + 5).coerceIn(1, 120)
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(cardBg)
                                .border(1.dp, cardBorder, CircleShape)
                        ) {
                            Text("+5", color = AccentWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Quick Preset Chips Row
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "PRESETS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontSize = 10.sp
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(5, 10, 15, 30).forEach { mins ->
                            val isSelected = selectedMinutes == mins
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) neonCyan.copy(alpha = 0.16f) else cardBg)
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) neonCyan else cardBorder,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        haptics.play(Haptic.Select)
                                        selectedMinutes = mins
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$mins m",
                                    color = if (isSelected) AccentWhite else TextSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(45, 60, 90).forEach { mins ->
                            val isSelected = selectedMinutes == mins
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) neonCyan.copy(alpha = 0.16f) else cardBg)
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) neonCyan else cardBorder,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        haptics.play(Haptic.Select)
                                        selectedMinutes = mins
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$mins m",
                                    color = if (isSelected) AccentWhite else TextSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // End of Track Chip
                        Box(
                            modifier = Modifier
                                .weight(1.4f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(cardBg)
                                .border(1.dp, cardBorder, RoundedCornerShape(10.dp))
                                .clickable {
                                    haptics.play(Haptic.ToggleOn)
                                    sleepTimerManager.startEndOfTrackTimer()
                                    onDismiss()
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "End of track",
                                color = neonPurple,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Start Timer CTA Button
                Button(
                    onClick = {
                        haptics.play(Haptic.ToggleOn)
                        sleepTimerManager.startTimer(selectedMinutes)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = neonCyan,
                        contentColor = BackgroundBlack
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Start Timer ($selectedMinutes min)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Rotary Clock / Time Picker with smooth 360° circular drag gesture and minute steps
 */
@Composable
fun RotaryTimePicker(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp
) {
    val haptics = rememberHaptics()
    var lastHapticMinute by remember { mutableIntStateOf(minutes) }
    val maxMinutes = 120f

    val progress = (minutes.toFloat() / maxMinutes).coerceIn(0.01f, 1f)
    val neonCyan = Color(0xFF00E5FF)
    val neonPurple = Color(0xFF8C52FF)

    fun calculateMinsFromOffset(touchX: Float, touchY: Float, centerX: Float, centerY: Float): Int {
        val touchAngleRad = atan2(touchY - centerY, touchX - centerX)
        var angleDeg = Math.toDegrees(touchAngleRad.toDouble()).toFloat() + 90f
        if (angleDeg < 0) angleDeg += 360f
        return ((angleDeg / 360f) * maxMinutes).roundToInt().coerceIn(1, 120)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .pointerInput(Unit) {
                val widthPx = this.size.width.toFloat()
                val heightPx = this.size.height.toFloat()
                val center = Offset(widthPx / 2f, heightPx / 2f)

                detectTapGestures { offset ->
                    val newMins = calculateMinsFromOffset(offset.x, offset.y, center.x, center.y)
                    haptics.play(Haptic.Select)
                    onMinutesChange(newMins)
                }
            }
            .pointerInput(Unit) {
                val widthPx = this.size.width.toFloat()
                val heightPx = this.size.height.toFloat()
                val center = Offset(widthPx / 2f, heightPx / 2f)

                detectDragGestures(
                    onDragStart = { offset ->
                        val newMins = calculateMinsFromOffset(offset.x, offset.y, center.x, center.y)
                        haptics.play(Haptic.Tick)
                        onMinutesChange(newMins)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val newMins = calculateMinsFromOffset(change.position.x, change.position.y, center.x, center.y)
                        if (newMins != lastHapticMinute && abs(newMins - lastHapticMinute) >= 1) {
                            lastHapticMinute = newMins
                            haptics.play(Haptic.Tick)
                        }
                        onMinutesChange(newMins)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            val radius = (this.size.minDimension - stroke * 3) / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)

            // Outer Tick Markers
            val ticks = 24
            for (i in 0 until ticks) {
                val fraction = i.toFloat() / ticks
                val angleDeg = fraction * 360f - 90f
                val angleRad = Math.toRadians(angleDeg.toDouble())

                val outerR = radius + stroke + 5.dp.toPx()
                val innerR = outerR - if (i % 6 == 0) 4.5.dp.toPx() else 2.5.dp.toPx()

                val p1 = Offset(center.x + innerR * cos(angleRad).toFloat(), center.y + innerR * sin(angleRad).toFloat())
                val p2 = Offset(center.x + outerR * cos(angleRad).toFloat(), center.y + outerR * sin(angleRad).toFloat())

                drawLine(
                    color = if (fraction <= progress) neonCyan.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.15f),
                    start = p1,
                    end = p2,
                    strokeWidth = if (i % 6 == 0) 2.dp.toPx() else 1.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Inactive Track Ring
            drawCircle(
                color = Color.White.copy(alpha = 0.10f),
                radius = radius,
                center = center,
                style = Stroke(width = stroke)
            )

            // Active Glowing Arc
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(neonCyan, neonPurple, neonCyan),
                    center = center
                ),
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )

            // Center Dark Disc
            val innerR = radius - stroke - 2.dp.toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF222228), Color(0xFF141418), Color(0xFF0C0C0F)),
                    center = center,
                    radius = innerR
                ),
                radius = innerR,
                center = center
            )

            // Indicator Handle
            val indicatorAngle = -90f + progress * 360f
            val indRad = Math.toRadians(indicatorAngle.toDouble())
            val indPos = Offset(center.x + radius * cos(indRad).toFloat(), center.y + radius * sin(indRad).toFloat())

            drawCircle(
                color = neonCyan.copy(alpha = 0.35f),
                radius = 7.dp.toPx(),
                center = indPos
            )
            drawCircle(
                color = neonCyan,
                radius = 4.5.dp.toPx(),
                center = indPos
            )
        }

        // Center Minute Text
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$minutes",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = AccentWhite,
                    fontSize = 30.sp
                )
            )
            Text(
                text = "MINUTES",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = neonCyan,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontSize = 10.sp
                )
            )
        }
    }
}

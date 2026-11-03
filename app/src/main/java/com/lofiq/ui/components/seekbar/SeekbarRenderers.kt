package com.lofiq.ui.components.seekbar

import android.graphics.Paint as AndroidPaint
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Pre-generated stable amplitudes for visualizer and waveform styles.
 */
object SeekbarVisualizerData {
    val defaultAmplitudes: List<Float> by lazy {
        listOf(
            0.35f, 0.48f, 0.62f, 0.78f, 0.55f, 0.42f, 0.68f, 0.85f, 0.92f, 0.74f,
            0.58f, 0.65f, 0.80f, 0.95f, 0.88f, 0.60f, 0.45f, 0.70f, 0.82f, 0.75f,
            0.50f, 0.62f, 0.85f, 0.98f, 0.72f, 0.56f, 0.40f, 0.65f, 0.84f, 0.90f,
            0.78f, 0.60f, 0.48f, 0.72f, 0.88f, 0.64f, 0.52f, 0.68f, 0.82f, 0.70f,
            0.45f, 0.58f, 0.75f, 0.86f, 0.60f, 0.42f, 0.55f, 0.70f, 0.62f, 0.40f
        )
    }
}

/**
 * 1. Waveform Style: Animated vertical bars simulating studio audio spectrum.
 */
object WaveformRenderer {
    fun DrawScope.draw(
        progress: Float,
        isPlaying: Boolean,
        phase: Float,
        amplitudes: List<Float>,
        activeColor: Color,
        inactiveColor: Color,
        isDragging: Boolean
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val barCount = amplitudes.size.coerceAtLeast(1)
        val barWidth = width / barCount
        val maxBarHeight = height * 0.70f
        val progressX = progress * width

        amplitudes.forEachIndexed { index, amp ->
            val x = index * barWidth + barWidth / 2f
            val isPast = x <= progressX

            val animatedAmp = if (isPlaying && isPast) {
                val cycle = (phase + index * 0.35f) % (2f * PI.toFloat())
                val bounce = sin(cycle)
                amp * (0.88f + bounce * 0.16f)
            } else {
                amp
            }

            val barHeight = (animatedAmp * maxBarHeight).coerceAtLeast(4.dp.toPx())
            val topY = centerY - barHeight / 2f
            val barThickness = (barWidth * 0.55f).coerceAtLeast(2.dp.toPx())
            val corner = CornerRadius(barThickness / 2f)

            if (isPast) {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            activeColor.copy(alpha = 0.85f),
                            activeColor,
                            activeColor.copy(alpha = 0.85f)
                        ),
                        startY = topY,
                        endY = topY + barHeight
                    ),
                    topLeft = Offset(x - barThickness / 2f, topY),
                    size = Size(barThickness, barHeight),
                    cornerRadius = corner
                )
            } else {
                drawRoundRect(
                    color = inactiveColor.copy(alpha = 0.32f),
                    topLeft = Offset(x - barThickness / 2f, topY),
                    size = Size(barThickness, barHeight),
                    cornerRadius = corner
                )
            }
        }

        // Thumb: vertical rounded pill with aura glow
        val thumbW = (if (isDragging) 8.dp else 6.dp).toPx()
        val thumbH = maxBarHeight * 1.15f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(activeColor.copy(alpha = 0.45f), Color.Transparent),
                center = Offset(progressX, centerY),
                radius = thumbH * 0.85f
            ),
            radius = thumbH * 0.85f,
            center = Offset(progressX, centerY)
        )

        drawRoundRect(
            color = activeColor,
            topLeft = Offset(progressX - thumbW / 2f, centerY - thumbH / 2f),
            size = Size(thumbW, thumbH),
            cornerRadius = CornerRadius(thumbW / 2f)
        )

        drawRoundRect(
            color = Color.White.copy(alpha = 0.70f),
            topLeft = Offset(progressX - (thumbW * 0.3f) / 2f, centerY - (thumbH * 0.5f) / 2f),
            size = Size(thumbW * 0.3f, thumbH * 0.5f),
            cornerRadius = CornerRadius(thumbW * 0.15f)
        )
    }
}

/**
 * 2. Wave Line Style: Smooth flowing sinusoidal wave line with riding orb.
 */
object WaveLineRenderer {
    fun DrawScope.draw(
        progress: Float,
        isPlaying: Boolean,
        phase: Float,
        activeColor: Color,
        inactiveColor: Color,
        isDragging: Boolean
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val progressX = progress * width
        val trackHeight = (if (isDragging) 6.dp else 4.5.dp).toPx()
        val amplitude = height * 0.22f
        val frequency = 0.045f

        // Unplayed straight baseline
        drawLine(
            color = inactiveColor.copy(alpha = 0.28f),
            start = Offset(progressX, centerY),
            end = Offset(width, centerY),
            strokeWidth = trackHeight,
            cap = StrokeCap.Round
        )

        // Played sine wave
        val playedPath = Path().apply {
            moveTo(0f, centerY)
            var x = 0f
            while (x <= progressX) {
                val amp = if (isPlaying) amplitude else amplitude * 0.25f
                val y = centerY + sin(x * frequency + (if (isPlaying) phase else 0f)) * amp
                lineTo(x, y)
                x += 2.5f
            }
        }

        drawPath(
            path = playedPath,
            color = activeColor,
            style = Stroke(width = trackHeight, cap = StrokeCap.Round)
        )

        // Glowing orb thumb riding the sine wave
        val thumbAmp = if (isPlaying) amplitude else amplitude * 0.25f
        val currentWaveY = centerY + sin(progressX * frequency + (if (isPlaying) phase else 0f)) * thumbAmp
        val thumbRadius = (if (isDragging) 8.dp else 6.5.dp).toPx()

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(activeColor.copy(alpha = 0.55f), Color.Transparent),
                center = Offset(progressX, currentWaveY),
                radius = thumbRadius * 3.2f
            ),
            radius = thumbRadius * 3.2f,
            center = Offset(progressX, currentWaveY)
        )

        drawCircle(
            color = activeColor,
            radius = thumbRadius,
            center = Offset(progressX, currentWaveY)
        )

        drawCircle(
            color = Color.White,
            radius = thumbRadius * 0.45f,
            center = Offset(progressX, currentWaveY)
        )
    }
}

/**
 * 3. Classic Style: Minimalist sleek track with pill thumb, track gap, and stop dot.
 */
object ClassicRenderer {
    fun DrawScope.draw(
        progress: Float,
        activeColor: Color,
        inactiveColor: Color,
        isDragging: Boolean
    ) {
        val width = size.width
        val centerY = size.height / 2f
        val p = progress.coerceIn(0f, 1f)
        val trackHeight = (if (isDragging) 10.dp else 7.dp).toPx()
        val thumbWidth = (if (isDragging) 4.5.dp else 4.dp).toPx()
        val thumbHeight = trackHeight * 2.5f
        val gap = 4.dp.toPx()
        val thumbX = (p * width).coerceIn(thumbWidth / 2f, width - thumbWidth / 2f)
        val outer = CornerRadius(trackHeight / 2f)
        val inner = CornerRadius(2.dp.toPx())

        val activeEnd = thumbX - thumbWidth / 2f - gap
        if (activeEnd > 0f) {
            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = centerY - trackHeight / 2f,
                        right = activeEnd,
                        bottom = centerY + trackHeight / 2f,
                        topLeftCornerRadius = outer,
                        bottomLeftCornerRadius = outer,
                        topRightCornerRadius = inner,
                        bottomRightCornerRadius = inner
                    )
                )
            }
            drawPath(path = path, color = activeColor)
        }

        val inactiveStart = thumbX + thumbWidth / 2f + gap
        if (inactiveStart < width) {
            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = inactiveStart,
                        top = centerY - trackHeight / 2f,
                        right = width,
                        bottom = centerY + trackHeight / 2f,
                        topLeftCornerRadius = inner,
                        bottomLeftCornerRadius = inner,
                        topRightCornerRadius = outer,
                        bottomRightCornerRadius = outer
                    )
                )
            }
            drawPath(path = path, color = inactiveColor.copy(alpha = 0.28f))

            // End dot indicator
            val stopRadius = 2.dp.toPx()
            if (width - inactiveStart > trackHeight) {
                drawCircle(
                    color = activeColor,
                    radius = stopRadius,
                    center = Offset(width - trackHeight / 2f, centerY)
                )
            }
        }

        // Thumb Pill
        drawRoundRect(
            color = activeColor,
            topLeft = Offset(thumbX - thumbWidth / 2f, centerY - thumbHeight / 2f),
            size = Size(thumbWidth, thumbHeight),
            cornerRadius = CornerRadius(thumbWidth / 2f)
        )
    }
}

/**
 * 4. Dots Style: Spaced beads/dots trail that pulses with music playback.
 */
object DotsRenderer {
    fun DrawScope.draw(
        progress: Float,
        isPlaying: Boolean,
        phase: Float,
        activeColor: Color,
        inactiveColor: Color,
        isDragging: Boolean
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val progressX = progress * width
        val dotCount = 28
        val dotSpacing = width / dotCount
        val trackHeight = 3.dp.toPx()

        // Unplayed straight connector line
        drawLine(
            color = inactiveColor.copy(alpha = 0.22f),
            start = Offset(progressX, centerY),
            end = Offset(width, centerY),
            strokeWidth = trackHeight,
            cap = StrokeCap.Round
        )

        for (i in 0 until dotCount) {
            val x = i * dotSpacing + dotSpacing / 2f
            val isPast = x <= progressX

            if (isPast) {
                val baseRadius = 3.2.dp.toPx()
                val animatedRadius = if (isPlaying) {
                    val cycle = (phase + i * 0.42f) % (2f * PI.toFloat())
                    val wave = sin(cycle)
                    baseRadius * (1f + wave * 0.35f)
                } else {
                    baseRadius
                }

                drawCircle(
                    color = activeColor,
                    radius = animatedRadius,
                    center = Offset(x, centerY)
                )
            } else {
                drawCircle(
                    color = inactiveColor.copy(alpha = 0.35f),
                    radius = 2.2.dp.toPx(),
                    center = Offset(x, centerY)
                )
            }
        }

        // Glowing thumb dot
        val thumbRadius = (if (isDragging) 8.dp else 6.5.dp).toPx()

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(activeColor.copy(alpha = 0.50f), Color.Transparent),
                center = Offset(progressX, centerY),
                radius = thumbRadius * 3f
            ),
            radius = thumbRadius * 3f,
            center = Offset(progressX, centerY)
        )

        drawCircle(
            color = activeColor,
            radius = thumbRadius,
            center = Offset(progressX, centerY)
        )

        drawCircle(
            color = Color.White,
            radius = thumbRadius * 0.45f,
            center = Offset(progressX, centerY)
        )
    }
}

/**
 * 5. Gradient Bar Style: Lush multi-stop glowing gradient with outer glow layer.
 */
object GradientBarRenderer {
    fun DrawScope.draw(
        progress: Float,
        activeColor: Color,
        inactiveColor: Color,
        secondaryColor: Color?,
        isDragging: Boolean
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val progressX = (progress * width).coerceIn(0f, width)
        val trackHeight = (if (isDragging) 10.dp else 7.dp).toPx()

        val gradStart = secondaryColor ?: activeColor.copy(alpha = 0.65f)
        val gradMid = activeColor
        val gradEnd = Color.White.copy(alpha = 0.90f)

        // Background track
        drawRoundRect(
            color = inactiveColor.copy(alpha = 0.25f),
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = Size(width, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f)
        )

        // Outer glow
        if (progressX > trackHeight) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        gradStart.copy(alpha = 0.25f),
                        gradMid.copy(alpha = 0.30f),
                        gradEnd.copy(alpha = 0.20f)
                    ),
                    startX = 0f,
                    endX = progressX
                ),
                topLeft = Offset(0f, centerY - trackHeight * 1.2f),
                size = Size(progressX, trackHeight * 2.4f),
                cornerRadius = CornerRadius(trackHeight * 1.2f)
            )

            // Progress bar
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(gradStart, gradMid, gradEnd),
                    startX = 0f,
                    endX = progressX
                ),
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = Size(progressX, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f)
            )
        }

        // Thumb
        val thumbW = (if (isDragging) 7.dp else 5.5.dp).toPx()
        val thumbH = trackHeight * 2.5f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(activeColor.copy(alpha = 0.45f), Color.Transparent),
                center = Offset(progressX, centerY),
                radius = thumbH
            ),
            radius = thumbH,
            center = Offset(progressX, centerY)
        )

        drawRoundRect(
            color = activeColor,
            topLeft = Offset(progressX - thumbW / 2f, centerY - thumbH / 2f),
            size = Size(thumbW, thumbH),
            cornerRadius = CornerRadius(thumbW / 2f)
        )

        drawLine(
            color = Color.White.copy(alpha = 0.85f),
            start = Offset(progressX, centerY - thumbH * 0.30f),
            end = Offset(progressX, centerY + thumbH * 0.30f),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

/**
 * 6. Neon Glow Style: Cyberpunk luminous neon laser beam with native shadow layer.
 */
object NeonRenderer {
    fun DrawScope.draw(
        progress: Float,
        activeColor: Color,
        inactiveColor: Color,
        isDragging: Boolean
    ) {
        val width = size.width
        val centerY = size.height / 2f
        val progressX = (progress * width).coerceIn(0f, width)
        val trackHeight = (if (isDragging) 6.dp else 4.5.dp).toPx()

        // Inactive line
        drawLine(
            color = inactiveColor.copy(alpha = 0.25f),
            start = Offset(0f, centerY),
            end = Offset(width, centerY),
            strokeWidth = trackHeight,
            cap = StrokeCap.Round
        )

        if (progressX > 0f) {
            // Native canvas glow bloom
            drawIntoCanvas { canvas ->
                val glowPaint = AndroidPaint().apply {
                    color = activeColor.toArgb()
                    isAntiAlias = true
                    strokeWidth = trackHeight * 2.2f
                    strokeCap = AndroidPaint.Cap.ROUND
                    setShadowLayer(22f, 0f, 0f, activeColor.toArgb())
                }
                canvas.nativeCanvas.drawLine(0f, centerY, progressX, centerY, glowPaint)
            }

            // Crisp active line
            drawLine(
                color = activeColor,
                start = Offset(0f, centerY),
                end = Offset(progressX, centerY),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round
            )
        }

        // Radiant neon thumb
        val thumbRadius = (if (isDragging) 9.dp else 7.dp).toPx()
        drawIntoCanvas { canvas ->
            val thumbGlow = AndroidPaint().apply {
                color = Color.White.toArgb()
                isAntiAlias = true
                setShadowLayer(24f, 0f, 0f, activeColor.toArgb())
            }
            canvas.nativeCanvas.drawCircle(progressX, centerY, thumbRadius, thumbGlow)
        }

        drawCircle(
            color = Color.White,
            radius = thumbRadius * 0.60f,
            center = Offset(progressX, centerY)
        )
    }
}

/**
 * 7. Blocks Style: Segmented matrix digital blocks illuminating with progress.
 */
object BlocksRenderer {
    fun DrawScope.draw(
        progress: Float,
        activeColor: Color,
        inactiveColor: Color,
        isDragging: Boolean
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val progressX = progress * width
        val blockCount = 26
        val blockWidth = width / blockCount
        val padding = 3.dp.toPx()
        val blockH = (if (isDragging) height * 0.55f else height * 0.42f)

        for (i in 0 until blockCount) {
            val startX = i * blockWidth + padding / 2f
            val blockProgress = (i + 1).toFloat() / blockCount
            val isLit = progress >= blockProgress || (i == 0 && progress > 0f)

            val color = if (isLit) activeColor else inactiveColor.copy(alpha = 0.25f)

            drawRoundRect(
                color = color,
                topLeft = Offset(startX, centerY - blockH / 2f),
                size = Size((blockWidth - padding).coerceAtLeast(2f), blockH),
                cornerRadius = CornerRadius(2.5.dp.toPx())
            )
        }

        // Thumb cursor
        val thumbW = blockWidth * 1.3f
        val thumbH = blockH * 1.35f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(activeColor.copy(alpha = 0.40f), Color.Transparent),
                center = Offset(progressX, centerY),
                radius = thumbH * 1.1f
            ),
            radius = thumbH * 1.1f,
            center = Offset(progressX, centerY)
        )

        drawRoundRect(
            color = activeColor,
            topLeft = Offset(progressX - thumbW / 2f, centerY - thumbH / 2f),
            size = Size(thumbW, thumbH),
            cornerRadius = CornerRadius(2.5.dp.toPx())
        )

        drawRoundRect(
            color = Color.White.copy(alpha = 0.70f),
            topLeft = Offset(progressX - (thumbW * 0.4f) / 2f, centerY - (thumbH * 0.6f) / 2f),
            size = Size(thumbW * 0.4f, thumbH * 0.6f),
            cornerRadius = CornerRadius(1.5.dp.toPx())
        )
    }
}

/**
 * 8. M3 Expressive Wavy Style: Fluid squircle sinusoidal progress track.
 */
object M3EWavyRenderer {
    fun DrawScope.draw(
        progress: Float,
        isPlaying: Boolean,
        phase: Float,
        activeColor: Color,
        inactiveColor: Color,
        isDragging: Boolean
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val progressX = (progress * width).coerceIn(0f, width)
        val strokePx = (if (isDragging) 6.dp else 4.5.dp).toPx()
        val amplitude = height * 0.22f
        val frequency = 0.055f

        // Full inactive wavy track
        val inactivePath = Path().apply {
            moveTo(0f, centerY)
            var x = 0f
            while (x <= width) {
                val y = centerY + sin(x * frequency + (if (isPlaying) phase else 0f)) * amplitude
                lineTo(x, y)
                x += 2.5f
            }
        }
        drawPath(
            path = inactivePath,
            color = inactiveColor.copy(alpha = 0.28f),
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )

        // Active progress wavy track
        if (progressX > 0f) {
            val activePath = Path().apply {
                moveTo(0f, centerY)
                var x = 0f
                while (x <= progressX) {
                    val y = centerY + sin(x * frequency + (if (isPlaying) phase else 0f)) * amplitude
                    lineTo(x, y)
                    x += 2.5f
                }
            }
            drawPath(
                path = activePath,
                color = activeColor,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }

        // Expressive pill thumb
        val currentWaveY = centerY + sin(progressX * frequency + (if (isPlaying) phase else 0f)) * amplitude
        val thumbRadius = (if (isDragging) 8.dp else 6.5.dp).toPx()

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(activeColor.copy(alpha = 0.45f), Color.Transparent),
                center = Offset(progressX, currentWaveY),
                radius = thumbRadius * 2.8f
            ),
            radius = thumbRadius * 2.8f,
            center = Offset(progressX, currentWaveY)
        )

        drawCircle(
            color = activeColor,
            radius = thumbRadius,
            center = Offset(progressX, currentWaveY)
        )

        drawCircle(
            color = Color.White,
            radius = thumbRadius * 0.45f,
            center = Offset(progressX, currentWaveY)
        )
    }
}

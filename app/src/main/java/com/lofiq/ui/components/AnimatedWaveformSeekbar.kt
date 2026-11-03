package com.lofiq.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lofiq.ui.haptics.Haptic
import com.lofiq.ui.haptics.rememberHaptics
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * Data model for individual sine-wave ribbon layers.
 */
internal data class WaveformLayer(
    val color: Color,
    val strokeWidth: Dp,
    val amplitude: Dp,
    val cycles: Float,
    val speed: Float,
    val phaseOffset: Float,
    val harmonicFactor: Float = 0.22f,
)

/**
 * Pure Kotlin RGB to HSL converter avoiding Android framework mock issues in unit tests.
 */
internal fun rgbToHsl(r: Float, g: Float, b: Float): FloatArray {
    val max = maxOf(r, maxOf(g, b))
    val min = minOf(r, minOf(g, b))
    val d = max - min
    val l = (max + min) / 2f
    var h = 0f
    var s = 0f

    if (max != min && d > 0.0001f) {
        s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
        h = when (max) {
            r -> (g - b) / d + (if (g < b) 6f else 0f)
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } * 60f
    }
    return floatArrayOf((h + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
}

/**
 * Pure Kotlin HSL to Compose Color converter.
 */
internal fun hslToColor(h: Float, s: Float, l: Float, alpha: Float = 1f): Color {
    val normalizedH = (h % 360f + 360f) % 360f
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val x = c * (1f - kotlin.math.abs((normalizedH / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r1, g1, b1) = when ((normalizedH / 60f).toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
        alpha = alpha
    )
}

/**
 * Dynamic waveform color palette generated from song artwork or fallback theme.
 */
data class DynamicWaveformPalette(
    val primaryWave: Color,
    val secondaryWave: Color,
    val tertiaryWave: Color,
    val quaternaryWave: Color,
    val playedTrackStart: Color,
    val playedTrackEnd: Color,
    val unplayedTrack: Color,
    val thumbCore: Color,
    val thumbHighlight: Color,
) {
    companion object {
        val Default = DynamicWaveformPalette(
            quaternaryWave = Color(0xFF868C56).copy(alpha = 0.65f), // Olive
            tertiaryWave = Color(0xFFA11C34).copy(alpha = 0.78f),   // Crimson
            secondaryWave = Color(0xFFD8B26E).copy(alpha = 0.85f),  // Champagne Gold
            primaryWave = Color(0xFFFF3860),                         // Coral Red
            playedTrackStart = Color(0xFFFF4D6D),
            playedTrackEnd = Color(0xFFE11D48),
            unplayedTrack = Color(0xFF353027),
            thumbCore = Color(0xFFDC2626),
            thumbHighlight = Color(0xFFFF859B).copy(alpha = 0.45f)
        )

        fun fromArtworkColor(primary: Color, secondary: Color? = null): DynamicWaveformPalette {
            val hsl = rgbToHsl(primary.red, primary.green, primary.blue)
            val h = hsl[0]
            val s = hsl[1].coerceIn(0.40f, 0.95f)

            // Primary foreground wave: vibrant & bright
            val primaryWave = hslToColor(h, s, 0.58f)
            // Secondary wave: warm analogous tone shifted +30 degrees
            val secondaryHue = (h + 30f) % 360f
            val secondaryWave = hslToColor(secondaryHue, (s * 0.9f).coerceIn(0.35f, 0.85f), 0.68f, alpha = 0.85f)
            // Tertiary wave: deep rich core shade
            val tertiaryWave = hslToColor(h, s, 0.35f, alpha = 0.78f)
            // Quaternary wave: ambient muted backdrop shifted -25 degrees
            val quatHue = (h - 25f + 360f) % 360f
            val quaternaryWave = hslToColor(quatHue, (s * 0.55f).coerceIn(0.2f, 0.5f), 0.38f, alpha = 0.65f)

            // Played track gradient
            val playedStart = secondaryWave.copy(alpha = 1f)
            val playedEnd = primaryWave
            // Unplayed track: subtle dark shade tinted with primary hue
            val unplayed = hslToColor(h, (s * 0.25f).coerceIn(0.08f, 0.25f), 0.16f)
            // Glowing thumb core and highlight
            val thumb = primaryWave
            val thumbHighlight = hslToColor(h, 0.45f, 0.85f, alpha = 0.5f)

            return DynamicWaveformPalette(
                primaryWave = primaryWave,
                secondaryWave = secondaryWave,
                tertiaryWave = tertiaryWave,
                quaternaryWave = quaternaryWave,
                playedTrackStart = playedStart,
                playedTrackEnd = playedEnd,
                unplayedTrack = unplayed,
                thumbCore = thumb,
                thumbHighlight = thumbHighlight
            )
        }
    }
}

/**
 * Curated wave layers reproducing the multi-layer waveform seekbar design:
 * - Olive/Khaki tertiary layer (deepest, subtle backdrop)
 * - Deep Crimson secondary layer (mid-depth)
 * - Muted Gold/Champagne primary contrast layer (warm golden glow)
 * - Vibrant Coral Pink-Red primary layer (prominent foreground)
 */
internal val DefaultWaveLayers = listOf(
    WaveformLayer(
        color = Color(0xFF868C56).copy(alpha = 0.65f),
        strokeWidth = 1.6.dp,
        amplitude = 15.dp,
        cycles = 3.3f,
        speed = 1.15f,
        phaseOffset = 3.2f,
        harmonicFactor = 0.18f,
    ),
    WaveformLayer(
        color = Color(0xFFA11C34).copy(alpha = 0.78f),
        strokeWidth = 1.8.dp,
        amplitude = 17.dp,
        cycles = 2.6f,
        speed = 0.70f,
        phaseOffset = 4.7f,
        harmonicFactor = 0.24f,
    ),
    WaveformLayer(
        color = Color(0xFFD8B26E).copy(alpha = 0.85f),
        strokeWidth = 1.8.dp,
        amplitude = 22.dp,
        cycles = 2.3f,
        speed = 0.82f,
        phaseOffset = 1.6f,
        harmonicFactor = 0.28f,
    ),
    WaveformLayer(
        color = Color(0xFFFF3860),
        strokeWidth = 2.2.dp,
        amplitude = 19.dp,
        cycles = 2.8f,
        speed = 1.0f,
        phaseOffset = 0f,
        harmonicFactor = 0.22f,
    ),
)

/**
 * Smooth window envelope function that tapers to 0 at both endpoints (u = 0 and u = 1),
 * ensuring the wave curves emerge seamlessly from the track origin and converge directly
 * into the center of the glowing progress thumb without discontinuities.
 */
internal fun waveformEnvelope(u: Float): Float {
    if (u <= 0f || u >= 1f) return 0f
    val sinVal = sin(PI.toFloat() * u)
    return if (sinVal > 0f) sinVal.pow(0.85f) else 0f
}

/**
 * Calculates normalized wave height (0.0 to 1.0) above the baseline for a given position and layer.
 */
internal fun calculateWaveHeight(
    u: Float,
    layer: WaveformLayer,
    phase: Float,
): Float {
    val env = waveformEnvelope(u)
    if (env <= 0f) return 0f
    val layerPhase = phase * layer.speed + layer.phaseOffset
    val theta = 2f * PI.toFloat() * layer.cycles * u + layerPhase
    val rawSin = sin(theta) + layer.harmonicFactor * sin(2f * theta + 0.5f)
    val normSin = (rawSin + (1f + layer.harmonicFactor)) / (2f * (1f + layer.harmonicFactor))
    return env * normSin
}

/**
 * Animated Multi-Layer Waveform Seekbar with Glowing Progress Thumb.
 *
 * Visual characteristics:
 * - Full-width horizontal audio progress track with dark olive/near-black unplayed bar.
 * - Vibrant pink-to-crimson red played progress bar with rounded cap.
 * - Multiple smooth, overlapping sinusoidal wave ribbons above the played track.
 * - Prominent pink/red primary waveform alongside muted gold, olive, and dark red layers.
 * - Circular crimson progress thumb with soft multi-layered outer glow that gently pulses when playing.
 * - Interactive seeking via tapping or horizontal dragging with haptic feedback.
 * - Lifecycle-conscious frame-driven phase animation that pauses when audio is paused.
 */
@Composable
fun AnimatedWaveformSeekbar(
    progress: Float,
    duration: Long,
    currentPosition: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    primaryColor: Color? = null,
    secondaryColor: Color? = null,
    onSeeking: ((Boolean, Float) -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    var isDragging by remember { mutableStateOf(false) }
    var internalFraction by remember(progress) { mutableFloatStateOf(progress.coerceIn(0f, 1f)) }

    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnSeeking by rememberUpdatedState(onSeeking)
    val currentDuration by rememberUpdatedState(duration)

    // Continuously animated phase for smooth wave movement and glowing thumb pulsing
    var phase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var lastTime = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { frameTime ->
                val dtSeconds = ((frameTime - lastTime) / 1_000_000_000f).coerceIn(0f, 0.05f)
                lastTime = frameTime
                val delta = dtSeconds * 1.95f
                phase = (phase + delta) % (2f * PI.toFloat())
            }
        }
    }

    // Dynamic Artwork Palette Resolution & Smooth Color Interpolation
    val targetPalette = remember(primaryColor, secondaryColor) {
        if (primaryColor != null) {
            DynamicWaveformPalette.fromArtworkColor(primaryColor, secondaryColor)
        } else {
            DynamicWaveformPalette.Default
        }
    }

    val animPrimaryWave by animateColorAsState(targetPalette.primaryWave, tween(600), label = "primaryWave")
    val animSecondaryWave by animateColorAsState(targetPalette.secondaryWave, tween(600), label = "secondaryWave")
    val animTertiaryWave by animateColorAsState(targetPalette.tertiaryWave, tween(600), label = "tertiaryWave")
    val animQuaternaryWave by animateColorAsState(targetPalette.quaternaryWave, tween(600), label = "quaternaryWave")
    val animPlayedStart by animateColorAsState(targetPalette.playedTrackStart, tween(600), label = "playedStart")
    val animPlayedEnd by animateColorAsState(targetPalette.playedTrackEnd, tween(600), label = "playedEnd")
    val animUnplayed by animateColorAsState(targetPalette.unplayedTrack, tween(600), label = "unplayed")
    val animThumbCore by animateColorAsState(targetPalette.thumbCore, tween(600), label = "thumbCore")
    val animThumbHighlight by animateColorAsState(targetPalette.thumbHighlight, tween(600), label = "thumbHighlight")

    val activeWaveLayers = remember(animQuaternaryWave, animTertiaryWave, animSecondaryWave, animPrimaryWave) {
        listOf(
            DefaultWaveLayers[0].copy(color = animQuaternaryWave),
            DefaultWaveLayers[1].copy(color = animTertiaryWave),
            DefaultWaveLayers[2].copy(color = animSecondaryWave),
            DefaultWaveLayers[3].copy(color = animPrimaryWave),
        )
    }

    // Allocate Path objects once and reuse them across frames to eliminate GC allocations
    val layerPaths = remember { List(DefaultWaveLayers.size) { Path() } }

    val displayFraction = if (isDragging) internalFraction else progress.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(currentDuration) {
                detectTapGestures(
                    onPress = { offset ->
                        val horizontalPaddingPx = 16.dp.toPx()
                        val trackStartX = horizontalPaddingPx
                        val trackEndX = size.width - horizontalPaddingPx
                        val trackWidth = (trackEndX - trackStartX).coerceAtLeast(1f)
                        val frac = ((offset.x - trackStartX) / trackWidth).coerceIn(0f, 1f)

                        isDragging = true
                        internalFraction = frac
                        haptics.play(Haptic.Tick)
                        currentOnSeeking?.invoke(true, frac)

                        val released = tryAwaitRelease()
                        isDragging = false
                        if (released) {
                            val dur = currentDuration.coerceAtLeast(1L)
                            val targetMs = (frac * dur).toLong().coerceIn(0L, dur)
                            currentOnSeek(targetMs)
                            haptics.play(Haptic.Select)
                        }
                        currentOnSeeking?.invoke(false, frac)
                    }
                )
            }
            .pointerInput(currentDuration) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val horizontalPaddingPx = 16.dp.toPx()
                        val trackStartX = horizontalPaddingPx
                        val trackEndX = size.width - horizontalPaddingPx
                        val trackWidth = (trackEndX - trackStartX).coerceAtLeast(1f)
                        val frac = ((offset.x - trackStartX) / trackWidth).coerceIn(0f, 1f)

                        isDragging = true
                        internalFraction = frac
                        haptics.play(Haptic.Tick)
                        currentOnSeeking?.invoke(true, frac)
                    },
                    onDragEnd = {
                        isDragging = false
                        val dur = currentDuration.coerceAtLeast(1L)
                        val targetMs = (internalFraction * dur).toLong().coerceIn(0L, dur)
                        currentOnSeek(targetMs)
                        haptics.play(Haptic.Select)
                        currentOnSeeking?.invoke(false, internalFraction)
                    },
                    onDragCancel = {
                        isDragging = false
                        currentOnSeeking?.invoke(false, progress)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val horizontalPaddingPx = 16.dp.toPx()
                        val trackStartX = horizontalPaddingPx
                        val trackEndX = size.width - horizontalPaddingPx
                        val trackWidth = (trackEndX - trackStartX).coerceAtLeast(1f)
                        val frac = ((change.position.x - trackStartX) / trackWidth).coerceIn(0f, 1f)
                        internalFraction = frac
                        currentOnSeeking?.invoke(true, frac)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val horizontalPaddingPx = 16.dp.toPx()
            val trackStartX = horizontalPaddingPx
            val trackEndX = size.width - horizontalPaddingPx
            val trackWidth = (trackEndX - trackStartX).coerceAtLeast(1f)

            // Baseline is positioned near the bottom to give ample headroom for wave crests
            val baselineY = size.height - 18.dp.toPx()
            val trackThicknessPx = 4.5.dp.toPx()
            val thumbRadiusPx = 7.5.dp.toPx()

            val thumbX = trackStartX + displayFraction * trackWidth

            // 1. Draw Unplayed Track (subtle dark tint derived from song theme)
            drawLine(
                color = animUnplayed,
                start = Offset(trackStartX, baselineY),
                end = Offset(trackEndX, baselineY),
                strokeWidth = trackThicknessPx,
                cap = StrokeCap.Round,
            )

            // 2. Draw Active/Played Track (dynamic artwork gradient)
            if (thumbX > trackStartX + 0.5f) {
                val playedBrush = Brush.horizontalGradient(
                    colors = listOf(animPlayedStart, animPlayedEnd),
                    startX = trackStartX,
                    endX = thumbX.coerceAtLeast(trackStartX + 1f),
                )
                drawLine(
                    brush = playedBrush,
                    start = Offset(trackStartX, baselineY),
                    end = Offset(thumbX, baselineY),
                    strokeWidth = trackThicknessPx,
                    cap = StrokeCap.Round,
                )
            }

            // 3. Rhythm Beat Pulse: Organic musical breathing modulation
            val beatFactor = if (isPlaying) {
                1.0f + 0.16f * (sin(phase * 2.2f) * sin(phase * 1.1f + 0.35f))
            } else 1.0f

            // 4. Draw Animated Multi-Layer Waveforms above the played track
            val activeWidth = thumbX - trackStartX
            if (activeWidth > 8.dp.toPx()) {
                val stepPx = 3.dp.toPx().coerceAtLeast(4f)
                val steps = (activeWidth / stepPx).toInt().coerceIn(16, 120)

                activeWaveLayers.forEachIndexed { index, layer ->
                    val path = layerPaths[index]
                    path.reset()
                    path.moveTo(trackStartX, baselineY)

                    val ampPx = layer.amplitude.toPx() * beatFactor
                    var prevX = trackStartX
                    var prevY = baselineY

                    for (j in 1..steps) {
                        val u = j.toFloat() / steps.toFloat()
                        val curX = trackStartX + u * activeWidth
                        val heightFraction = calculateWaveHeight(u, layer, phase)
                        val curY = baselineY - ampPx * heightFraction

                        val midX = (prevX + curX) / 2f
                        val midY = (prevY + curY) / 2f
                        path.quadraticTo(prevX, prevY, midX, midY)
                        prevX = curX
                        prevY = curY
                    }
                    path.lineTo(thumbX, baselineY)

                    drawPath(
                        path = path,
                        color = layer.color,
                        style = Stroke(
                            width = layer.strokeWidth.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
            }

            // 5. Draw Glowing Progress Thumb at (thumbX, baselineY) with dynamic rhythmic throb
            val pulse = if (isPlaying) ((beatFactor - 1.0f) / 0.16f).coerceIn(-1f, 1f) else 0f
            val baseGlowRadius = 15.5.dp.toPx()
            val glowRadius = baseGlowRadius * (1f + pulse * 0.22f)
            val glowAlpha = if (isPlaying) {
                (0.32f + pulse * 0.14f).coerceIn(0.20f, 0.55f)
            } else 0.30f

            // Outer soft blooming halo
            drawCircle(
                color = animThumbCore.copy(alpha = glowAlpha * 0.35f),
                radius = glowRadius * 1.30f,
                center = Offset(thumbX, baselineY),
            )

            // Primary glowing halo
            drawCircle(
                color = animThumbCore.copy(alpha = glowAlpha),
                radius = glowRadius,
                center = Offset(thumbX, baselineY),
            )

            // Solid progress thumb core
            drawCircle(
                color = animThumbCore,
                radius = thumbRadiusPx,
                center = Offset(thumbX, baselineY),
            )

            // Subtle 3D pearl highlight on the upper-left of the thumb
            drawCircle(
                color = animThumbHighlight,
                radius = thumbRadiusPx * 0.5f,
                center = Offset(thumbX - 1.dp.toPx(), baselineY - 1.dp.toPx()),
            )
        }
    }
}

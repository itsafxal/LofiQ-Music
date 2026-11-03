package com.lofiq.ui.components.seekbar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.lofiq.core.model.SeekbarStyle
import com.lofiq.ui.components.AnimatedWaveformSeekbar
import com.lofiq.ui.haptics.Haptic
import com.lofiq.ui.haptics.rememberHaptics
import com.lofiq.ui.theme.LocalBrandColor
import kotlinx.coroutines.isActive
import kotlin.math.PI

/**
 * Unified player seekbar supporting all 10 customizable seekbar styles.
 */
@Composable
fun CustomPlayerSeekbar(
    progress: Float,
    duration: Long,
    currentPosition: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    style: SeekbarStyle = SeekbarStyle.LOFIQ_WAVE,
    primaryColor: Color? = null,
    secondaryColor: Color? = null,
    onSeeking: ((Boolean, Float) -> Unit)? = null,
    onLongPress: (() -> Unit)? = null
) {
    // 1. If LofiQ Waveform is selected, delegate to our signature AnimatedWaveformSeekbar
    if (style == SeekbarStyle.LOFIQ_WAVE) {
        Box(
            modifier = modifier.pointerInput(onLongPress) {
                if (onLongPress != null) {
                    detectTapGestures(
                        onLongPress = { onLongPress() }
                    )
                }
            }
        ) {
            AnimatedWaveformSeekbar(
                progress = progress,
                duration = duration,
                currentPosition = currentPosition,
                isPlaying = isPlaying,
                primaryColor = primaryColor,
                secondaryColor = secondaryColor,
                onSeek = onSeek,
                onSeeking = onSeeking,
                modifier = Modifier.fillMaxWidth()
            )
        }
        return
    }

    val haptics = rememberHaptics()
    val brandColor = LocalBrandColor.current
    val effectiveActiveColor = primaryColor ?: brandColor
    val effectiveInactiveColor = Color.White.copy(alpha = 0.28f)

    val animActiveColor by animateColorAsState(effectiveActiveColor, tween(400), label = "activeColor")
    val animInactiveColor by animateColorAsState(effectiveInactiveColor, tween(400), label = "inactiveColor")

    var isDragging by remember { mutableStateOf(false) }
    var internalFraction by remember(progress) { mutableFloatStateOf(progress.coerceIn(0f, 1f)) }

    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnSeeking by rememberUpdatedState(onSeeking)
    val currentDuration by rememberUpdatedState(duration)
    val currentOnLongPress by rememberUpdatedState(onLongPress)

    // 2. Material 3 Standard Slider
    if (style == SeekbarStyle.MATERIAL) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 4.dp)
                .pointerInput(Unit) {
                    if (currentOnLongPress != null) {
                        detectTapGestures(
                            onLongPress = {
                                haptics.play(Haptic.Select)
                                currentOnLongPress?.invoke()
                            }
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Slider(
                value = if (isDragging) internalFraction else progress.coerceIn(0f, 1f),
                onValueChange = { frac ->
                    isDragging = true
                    internalFraction = frac
                    currentOnSeeking?.invoke(true, frac)
                },
                onValueChangeFinished = {
                    isDragging = false
                    val dur = currentDuration.coerceAtLeast(1L)
                    val targetMs = (internalFraction * dur).toLong().coerceIn(0L, dur)
                    currentOnSeek(targetMs)
                    currentOnSeeking?.invoke(false, internalFraction)
                    haptics.play(Haptic.Select)
                },
                colors = SliderDefaults.colors(
                    thumbColor = animActiveColor,
                    activeTrackColor = animActiveColor,
                    inactiveTrackColor = animInactiveColor
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
        return
    }

    // 3. Canvas-based dynamic styles (WAVEFORM, WAVE_LINE, CLASSIC, DOTS, GRADIENT_BAR, NEON, BLOCKS, M3E_WAVY)
    val shouldAnimatePhase = isPlaying && (
        style == SeekbarStyle.WAVEFORM ||
            style == SeekbarStyle.WAVE_LINE ||
            style == SeekbarStyle.DOTS ||
            style == SeekbarStyle.M3E_WAVY
    )

    var phase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying, shouldAnimatePhase) {
        if (!shouldAnimatePhase) return@LaunchedEffect
        var lastTime = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { frameTime ->
                val dtSeconds = ((frameTime - lastTime) / 1_000_000_000f).coerceIn(0f, 0.05f)
                lastTime = frameTime
                val delta = dtSeconds * 3.5f
                phase = (phase + delta) % (2f * PI.toFloat())
            }
        }
    }

    val displayFraction = if (isDragging) internalFraction else progress.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(currentDuration) {
                detectTapGestures(
                    onPress = { offset ->
                        val horizontalPaddingPx = 14.dp.toPx()
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
                    },
                    onLongPress = {
                        haptics.play(Haptic.Select)
                        currentOnLongPress?.invoke()
                    }
                )
            }
            .pointerInput(currentDuration) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val horizontalPaddingPx = 14.dp.toPx()
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
                        currentOnSeeking?.invoke(false, internalFraction)
                        haptics.play(Haptic.Select)
                    },
                    onDragCancel = {
                        isDragging = false
                        currentOnSeeking?.invoke(false, internalFraction)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val horizontalPaddingPx = 14.dp.toPx()
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
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 14.dp)
        ) {
            when (style) {
                SeekbarStyle.WAVEFORM -> with(WaveformRenderer) {
                    draw(
                        progress = displayFraction,
                        isPlaying = isPlaying,
                        phase = phase,
                        amplitudes = SeekbarVisualizerData.defaultAmplitudes,
                        activeColor = animActiveColor,
                        inactiveColor = animInactiveColor,
                        isDragging = isDragging
                    )
                }
                SeekbarStyle.WAVE_LINE -> with(WaveLineRenderer) {
                    draw(
                        progress = displayFraction,
                        isPlaying = isPlaying,
                        phase = phase,
                        activeColor = animActiveColor,
                        inactiveColor = animInactiveColor,
                        isDragging = isDragging
                    )
                }
                SeekbarStyle.CLASSIC -> with(ClassicRenderer) {
                    draw(
                        progress = displayFraction,
                        activeColor = animActiveColor,
                        inactiveColor = animInactiveColor,
                        isDragging = isDragging
                    )
                }
                SeekbarStyle.DOTS -> with(DotsRenderer) {
                    draw(
                        progress = displayFraction,
                        isPlaying = isPlaying,
                        phase = phase,
                        activeColor = animActiveColor,
                        inactiveColor = animInactiveColor,
                        isDragging = isDragging
                    )
                }
                SeekbarStyle.GRADIENT_BAR -> with(GradientBarRenderer) {
                    draw(
                        progress = displayFraction,
                        activeColor = animActiveColor,
                        inactiveColor = animInactiveColor,
                        secondaryColor = secondaryColor,
                        isDragging = isDragging
                    )
                }
                SeekbarStyle.NEON -> with(NeonRenderer) {
                    draw(
                        progress = displayFraction,
                        activeColor = animActiveColor,
                        inactiveColor = animInactiveColor,
                        isDragging = isDragging
                    )
                }
                SeekbarStyle.BLOCKS -> with(BlocksRenderer) {
                    draw(
                        progress = displayFraction,
                        activeColor = animActiveColor,
                        inactiveColor = animInactiveColor,
                        isDragging = isDragging
                    )
                }
                SeekbarStyle.M3E_WAVY -> with(M3EWavyRenderer) {
                    draw(
                        progress = displayFraction,
                        isPlaying = isPlaying,
                        phase = phase,
                        activeColor = animActiveColor,
                        inactiveColor = animInactiveColor,
                        isDragging = isDragging
                    )
                }
                SeekbarStyle.LOFIQ_WAVE, SeekbarStyle.MATERIAL -> {
                    // Handled above
                }
            }
        }
    }
}

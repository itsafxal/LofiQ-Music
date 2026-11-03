package com.lofiq.ui.animation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lofiq.ui.haptics.Haptic
import com.lofiq.ui.haptics.rememberHaptics
import com.lofiq.ui.theme.*

/**
 * Centralized Animation System for LofiQ Liquid Glass & Fluid UI.
 *
 * Automatically adapts animation parameters, spring physics, and visual complexity
 * depending on whether Performance Mode (Low-RAM mode) is active:
 *
 * - When Performance Mode is OFF: Rich bouncy springs, fluid curves, specular rim highlights,
 *   subtle press scales, and smooth icon morphing.
 * - When Performance Mode is ON: Snappy zero-bounce transitions, flat solid fills, lightweight
 *   static fallbacks, and zero GPU overhead.
 */
object LiquidAnimationTokens {
    val FluidDecelEasing = CubicBezierEasing(0.20f, 0.90f, 0.40f, 1.0f)
    val FluidSpringEasing = CubicBezierEasing(0.34f, 1.30f, 0.64f, 1.0f)
    val SharpEasing = FastOutSlowInEasing

    val FullPlayerEnterDuration = 420
    val FullPlayerExitDuration = 320
    val TabSwitchDuration = 300
    val ArtworkSwipeDuration = 260
}

/**
 * Returns a centralized SpringSpec that provides silky liquid bounce when Performance Mode is OFF,
 * and high-stiffness, no-bounce snappy feedback when Performance Mode is ON.
 */
@Composable
fun <T> rememberLiquidSpring(
    dampingRatio: Float = Spring.DampingRatioLowBouncy,
    stiffness: Float = Spring.StiffnessMediumLow
): SpringSpec<T> {
    val isPerfMode = LocalPerformanceModeEnabled.current
    return remember(isPerfMode, dampingRatio, stiffness) {
        if (isPerfMode) {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
        } else {
            spring(dampingRatio = dampingRatio, stiffness = stiffness)
        }
    }
}

/**
 * Returns a centralized TweenSpec adapting duration and easing to current performance tier.
 */
@Composable
fun <T> rememberLiquidTween(
    durationMillis: Int = 300,
    easing: androidx.compose.animation.core.Easing = LiquidAnimationTokens.FluidDecelEasing
): TweenSpec<T> {
    val isPerfMode = LocalPerformanceModeEnabled.current
    return remember(isPerfMode, durationMillis) {
        tween(
            durationMillis = if (isPerfMode) (durationMillis * 0.65f).toInt() else durationMillis,
            easing = if (isPerfMode) FastOutSlowInEasing else easing
        )
    }
}

/**
 * Interactive tactile modifier applying smooth liquid squish/bounce on press.
 * Automatically disabled in Performance Mode to preserve touch responsiveness on low-end CPUs.
 */
@Composable
fun Modifier.liquidPressEffect(
    enabled: Boolean = true,
    haptic: Haptic? = Haptic.Tap,
    targetScaleOnPress: Float = 0.93f,
    onClick: () -> Unit
): Modifier {
    val isPerfMode = LocalPerformanceModeEnabled.current
    val haptics = rememberHaptics()
    var isPressed by remember { mutableStateOf(false) }

    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed && !isPerfMode) targetScaleOnPress else 1.0f,
        animationSpec = spring(
            dampingRatio = if (isPressed) Spring.DampingRatioNoBouncy else Spring.DampingRatioLowBouncy,
            stiffness = if (isPressed) Spring.StiffnessHigh else Spring.StiffnessMediumLow
        ),
        label = "liquidPressScale"
    )

    return this
        .scale(animatedScale)
        .pointerInput(enabled, isPerfMode) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onPress = {
                    isPressed = true
                    tryAwaitRelease()
                    isPressed = false
                },
                onTap = {
                    if (haptic != null) haptics.play(haptic)
                    onClick()
                }
            )
        }
}

/**
 * Centralized Modern Liquid Play/Pause Button with animated morphing transitions,
 * glowing specular chassis (or solid chassis in Performance Mode), and tactile spring feedback.
 */
@Composable
fun LiquidPlayPauseButton(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    iconSize: Dp = 38.dp,
    tintColor: Color = AccentWhite
) {
    val isLiquidGlass = isLiquidGlassActive()
    val isPerfMode = LocalPerformanceModeEnabled.current
    val haptics = rememberHaptics()

    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && !isPerfMode) 0.88f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "playPausePressScale"
    )

    val backgroundBrush = remember(isLiquidGlass) {
        if (isLiquidGlass) {
            Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.28f),
                    Color(0xFF181824).copy(alpha = 0.75f)
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF262630),
                    Color(0xFF181820)
                )
            )
        }
    }

    val borderStroke = remember(isLiquidGlass) {
        if (isLiquidGlass) {
            LiquidGlassTokens.specularBorderStroke(0.42f)
        } else {
            BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .scale(pressScale)
            .then(
                if (isLiquidGlass) {
                    Modifier.shadow(
                        elevation = 16.dp,
                        shape = CircleShape,
                        spotColor = Color.Black.copy(alpha = 0.6f)
                    )
                } else Modifier
            )
            .clip(CircleShape)
            .background(backgroundBrush)
            .border(borderStroke, CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = {
                        if (isPlaying) {
                            haptics.play(Haptic.Pause)
                        } else {
                            haptics.play(Haptic.Resume)
                        }
                        onClick()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = isBuffering to isPlaying,
            transitionSpec = {
                if (isPerfMode) {
                    fadeIn(animationSpec = tween(100)) togetherWith fadeOut(animationSpec = tween(100))
                } else {
                    (scaleIn(
                        initialScale = 0.70f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                    ) + fadeIn(animationSpec = tween(180))) togetherWith
                            (scaleOut(targetScale = 0.70f, animationSpec = tween(140)) + fadeOut(animationSpec = tween(140)))
                }
            },
            label = "playPauseIconMorph"
        ) { (buffering, playing) ->
            when {
                buffering -> {
                    CircularProgressIndicator(
                        color = tintColor,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(iconSize * 0.78f)
                    )
                }
                playing -> {
                    Icon(
                        imageVector = Icons.Rounded.Pause,
                        contentDescription = "Pause",
                        tint = tintColor,
                        modifier = Modifier.size(iconSize)
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play",
                        tint = tintColor,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
    }
}

/**
 * Modern lightweight Shimmer Box placeholder for loading artwork and song items.
 * Bypasses continuous animation when in Performance Mode to protect CPU/GPU.
 */
@Composable
fun LiquidShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    val isPerfMode = LocalPerformanceModeEnabled.current

    if (isPerfMode) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(Color.White.copy(alpha = 0.08f))
        )
    } else {
        val transition = rememberInfiniteTransition(label = "shimmerTransition")
        val translateAnim by transition.animateFloat(
            initialValue = -300f,
            targetValue = 600f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmerTranslate"
        )

        val shimmerBrush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.06f),
                Color.White.copy(alpha = 0.18f),
                Color.White.copy(alpha = 0.06f)
            ),
            start = androidx.compose.ui.geometry.Offset(translateAnim, translateAnim),
            end = androidx.compose.ui.geometry.Offset(translateAnim + 200f, translateAnim + 200f)
        )

        Box(
            modifier = modifier
                .clip(shape)
                .background(shimmerBrush)
        )
    }
}

/**
 * Modern audio waveform buffering / playing indicator with 3 smooth bouncing liquid bars.
 */
@Composable
fun LiquidBufferingWave(
    modifier: Modifier = Modifier,
    barColor: Color = AccentWhite,
    barWidth: Dp = 3.dp,
    maxHeight: Dp = 18.dp
) {
    val isPerfMode = LocalPerformanceModeEnabled.current

    if (isPerfMode) {
        CircularProgressIndicator(
            color = barColor,
            strokeWidth = 2.dp,
            modifier = modifier.size(maxHeight)
        )
        return
    }

    val transition = rememberInfiniteTransition(label = "waveTransition")

    val bar1Height by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )

    val bar2Height by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 380, delayMillis = 100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )

    val bar3Height by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520, delayMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    Row(
        modifier = modifier.height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(bar1Height, bar2Height, bar3Height).forEach { fraction ->
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(barWidth / 2))
                    .background(barColor)
            )
        }
    }
}


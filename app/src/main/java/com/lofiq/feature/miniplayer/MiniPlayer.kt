package com.lofiq.feature.miniplayer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lofiq.core.model.LofiQPlayerState
import com.lofiq.ui.animation.LiquidPlayPauseButton
import com.lofiq.ui.animation.liquidPressEffect
import com.lofiq.ui.haptics.Haptic
import com.lofiq.ui.haptics.rememberHaptics
import com.lofiq.ui.theme.*
import kotlin.math.roundToInt

/**
 * LofiQ Floating Frosted Glass Mini Player with Haptics and Swipe-Down-To-Stop.
 * Enhanced with Liquid Play/Pause button, artwork playback animations, and specular edges.
 */
@Composable
fun MiniPlayer(
    playerState: LofiQPlayerState,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentSong = playerState.currentSong ?: return
    val haptics = rememberHaptics()
    val isLiquidGlass = isLiquidGlassActive()
    val isPerfMode = LocalPerformanceModeEnabled.current
    val brandColor = LocalBrandColor.current

    val miniPlayerInteractionSource = remember { MutableInteractionSource() }
    val isMiniPlayerFocused by miniPlayerInteractionSource.collectIsFocusedAsState()

    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var isDismissed by remember { mutableStateOf(false) }

    val animatedOffsetY by animateFloatAsState(
        targetValue = if (isDismissed) 200f else dragOffsetY,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "miniPlayerDragOffset",
        finishedListener = {
            if (isDismissed) {
                onDismiss()
            }
        }
    )

    // Artwork Playback Scale Animation (Subtle breathing scale when playing)
    val artworkScale by animateFloatAsState(
        targetValue = if (playerState.isPlaying && !isPerfMode) 1.0f else 0.94f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "miniPlayerArtworkScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .offset { IntOffset(0, animatedOffsetY.roundToInt()) }
            .graphicsLayer {
                alpha = (1f - (animatedOffsetY / 180f)).coerceIn(0f, 1f)
            }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    if (delta > 0 || dragOffsetY > 0) {
                        dragOffsetY = (dragOffsetY + delta).coerceAtLeast(0f)
                    }
                },
                onDragStopped = { velocity ->
                    if (dragOffsetY > 60f || velocity > 400f) {
                        haptics.play(Haptic.ToggleOff)
                        isDismissed = true
                    } else {
                        dragOffsetY = 0f
                    }
                }
            )
            .then(
                if (isLiquidGlass) {
                    Modifier.shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(18.dp),
                        spotColor = Color.Black.copy(alpha = 0.70f)
                    )
                } else {
                    Modifier.shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(18.dp),
                        spotColor = Color.Black.copy(alpha = 0.40f)
                    )
                }
            )
            .border(
                width = if (isMiniPlayerFocused) 2.dp else 1.dp,
                brush = if (isMiniPlayerFocused) Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.90f), Color.White.copy(alpha = 0.70f)))
                else if (isLiquidGlass) LiquidGlassTokens.specularBorder(0.38f)
                else Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.20f),
                        Color.White.copy(alpha = 0.06f)
                    )
                ),
                shape = RoundedCornerShape(18.dp)
            )
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isMiniPlayerFocused) Color(0xFF20202C).copy(alpha = 0.95f)
                else if (isLiquidGlass) Color(0xFF101016).copy(alpha = 0.60f)
                else Color(0xFF16161C).copy(alpha = 0.96f)
            )
            .clickable(
                interactionSource = miniPlayerInteractionSource,
                indication = ripple()
            ) {
                haptics.play(Haptic.Expand)
                onClick()
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // High-Res Artwork with dynamic playing scale & subtle glow
                Box(
                    modifier = Modifier
                        .scale(artworkScale)
                        .then(
                            if (isLiquidGlass && playerState.isPlaying) {
                                Modifier.shadow(
                                    elevation = 8.dp,
                                    shape = RoundedCornerShape(10.dp),
                                    spotColor = brandColor.copy(alpha = 0.50f)
                                )
                            } else Modifier
                        )
                ) {
                    com.lofiq.ui.components.ArtworkImage(
                        artworkUrl = com.lofiq.core.common.ArtworkUtils.getOptimalArtwork(currentSong),
                        contentDescription = currentSong.title,
                        size = 46.dp,
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title & Artist with animated text transitions
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    AnimatedContent(
                        targetState = currentSong.title,
                        transitionSpec = {
                            if (isPerfMode) {
                                fadeIn(tween(100)) togetherWith fadeOut(tween(100))
                            } else {
                                (fadeIn(tween(200)) + scaleIn(initialScale = 0.95f)) togetherWith
                                        (fadeOut(tween(150)) + scaleOut(targetScale = 0.95f))
                            }
                        },
                        label = "miniPlayerTitleAnim"
                    ) { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = AccentWhite,
                                fontSize = 14.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = currentSong.artist,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Smooth Liquid Play / Pause / Buffering Button
                LiquidPlayPauseButton(
                    isPlaying = playerState.isPlaying,
                    isBuffering = playerState.isBuffering,
                    onClick = onPlayPauseClick,
                    size = 42.dp,
                    iconSize = 24.dp
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Skip Next Button with tactile bounce
                IconButton(
                    onClick = {
                        haptics.play(Haptic.SkipNext)
                        onNextClick()
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .liquidPressEffect(onClick = onNextClick)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next Track",
                        tint = AccentWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Hairline Animated Progress Indicator
            val progress = if (playerState.durationMs > 0) {
                (playerState.currentPositionMs.toFloat() / playerState.durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = AccentWhite,
                trackColor = Color.White.copy(alpha = 0.10f)
            )
        }
    }
}

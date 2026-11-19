package com.lofiq.feature.player.pipeline

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Interactive quality badge transforming LofiQ's Now Playing badge into an animated
 * Audio Pipeline entry point inspired by BitChord.
 */
@Composable
fun AudioPipelineBadge(
    badgeLabel: String, // "HQ", "Hi-Fi", "Pulse", "Offline"
    badgeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "BadgePressScale"
    )

    // Gentle live breathing glow effect
    val infiniteTransition = rememberInfiniteTransition(label = "PipelineBadgeGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowAlpha"
    )

    val borderBrush = Brush.horizontalGradient(
        colors = listOf(
            badgeColor.copy(alpha = if (isPlaying) glowAlpha else 0.35f),
            badgeColor.copy(alpha = 0.20f),
            badgeColor.copy(alpha = if (isPlaying) glowAlpha else 0.35f)
        )
    )

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = if (isPlaying) 6.dp else 0.dp,
                shape = RoundedCornerShape(8.dp),
                spotColor = badgeColor.copy(alpha = 0.4f)
            )
            .clip(RoundedCornerShape(8.dp))
            .background(badgeColor.copy(alpha = 0.12f))
            .border(1.dp, borderBrush, RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            )
            .semantics {
                role = Role.Button
                contentDescription = "Audio Pipeline: $badgeLabel. Tap to open live signal path inspector."
            }
            .padding(horizontal = 8.dp, vertical = 3.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Live pulsing audio signal dot / waveform indicator
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .size(5.5.dp)
                        .clip(CircleShape)
                        .background(badgeColor.copy(alpha = glowAlpha + 0.2f))
                )
            }

            Text(
                text = "Audio Pipeline • $badgeLabel",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp,
                    letterSpacing = 0.5.sp,
                    color = badgeColor
                )
            )
        }
    }
}

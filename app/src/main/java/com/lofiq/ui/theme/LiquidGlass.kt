package com.lofiq.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Global CompositionLocal indicating whether iOS-style Liquid Glass UI is enabled.
 * Default is TRUE when Performance Mode is OFF.
 */
val LocalLiquidGlassEnabled = compositionLocalOf { true }

/**
 * Global CompositionLocal indicating whether Full-Screen Artwork is enabled in Now Playing.
 * Default is TRUE (Full-bleed edge-to-edge dissolving hero artwork).
 * When FALSE: Displays floating rounded square poster card on blurred dynamic backdrop.
 */
val LocalFullScreenArtworkEnabled = compositionLocalOf { true }

/**
 * Global CompositionLocal providing the active SeekbarStyle throughout the player UI hierarchy.
 * Default is SeekbarStyle.LOFIQ_WAVE.
 */
val LocalSeekbarStyle = compositionLocalOf { com.lofiq.core.model.SeekbarStyle.LOFIQ_WAVE }

/**
 * Global CompositionLocal indicating whether Performance Mode is enabled.
 * When TRUE: Disables heavy GPU Gaussian blurs, rendering lightweight, hardware-accelerated
 * ambient gradients and solid cards for silky-smooth 60 FPS playback on low-spec devices.
 */
val LocalPerformanceModeEnabled = compositionLocalOf { false }

/**
 * Convenience helper returning whether liquid glass styling should be visually active.
 * Automatically disabled if Performance Mode is enabled.
 */
@Composable
fun isLiquidGlassActive(): Boolean {
    return LocalLiquidGlassEnabled.current && !LocalPerformanceModeEnabled.current
}

/**
 * Design tokens and helpers for iOS Liquid Glass material vs Classic Dark theme.
 */
object LiquidGlassTokens {
    // Glass Fills (Translucent frosted glass with ambient backdrop glow)
    val GlassSurface = Color(0xFF101016).copy(alpha = 0.62f)
    val GlassSurfaceElevated = Color(0xFF181822).copy(alpha = 0.72f)
    val GlassCard = Color(0xFF161620).copy(alpha = 0.58f)
    val GlassCardSubtle = Color.White.copy(alpha = 0.06f)
    val GlassPill = Color.White.copy(alpha = 0.10f)
    val GlassPillActive = Color.White.copy(alpha = 0.22f)
    // Aliases
    val PillGlassFill = GlassPill
    val CardGlassFill = GlassCard
    val CardGlassElevatedFill = GlassSurfaceElevated

    // Classic Fills (Opaque / Solid dark theme for Performance Mode)
    val ClassicSurface = Color(0xFF141418)
    val ClassicSurfaceElevated = Color(0xFF1C1C20)
    val ClassicCard = Color(0xFF16161A)
    val ClassicBorderColor = Color.White.copy(alpha = 0.08f)

    /**
     * Top-down specular reflection border gradient - the signature hallmark of iOS fluid glass.
     */
    fun specularBorder(alpha: Float = 0.28f): Brush {
        return Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = alpha),
                Color.White.copy(alpha = alpha * 0.35f),
                Color.White.copy(alpha = alpha * 0.06f)
            )
        )
    }

    /**
     * Returns a BorderStroke with specular highlight gradient for iOS liquid glass surfaces.
     */
    fun specularBorderStroke(alpha: Float = 0.28f): BorderStroke {
        return BorderStroke(1.dp, specularBorder(alpha))
    }

    /**
     * Standard classic border for non-glass / performance mode.
     */
    fun classicBorder(alpha: Float = 0.08f): BorderStroke {
        return BorderStroke(1.dp, Color.White.copy(alpha = alpha))
    }

    /**
     * Inner specular sheen brush for fluid glass panels.
     */
    fun specularSheen(alpha: Float = 0.10f): Brush {
        return Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = alpha),
                Color.White.copy(alpha = alpha * 0.25f),
                Color.Transparent
            )
        )
    }
}

/**
 * Convenience accessor for adaptive card background color based on active theme mode.
 * Falls back to solid opaque color when Performance Mode is active.
 */
@Composable
fun liquidGlassCardColor(isElevated: Boolean = false): Color {
    val isGlass = isLiquidGlassActive()
    return if (isGlass) {
        if (isElevated) LiquidGlassTokens.GlassSurfaceElevated else LiquidGlassTokens.GlassCard
    } else {
        if (isElevated) LiquidGlassTokens.ClassicSurfaceElevated else LiquidGlassTokens.ClassicCard
    }
}

/**
 * Convenience accessor for adaptive border stroke based on active theme mode.
 */
@Composable
fun liquidGlassBorder(alpha: Float = 0.28f): BorderStroke {
    val isGlass = isLiquidGlassActive()
    return if (isGlass) {
        BorderStroke(1.dp, LiquidGlassTokens.specularBorder(alpha))
    } else {
        BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    }
}

/**
 * Modifier applying iOS-style Liquid Glass when enabled, or classic dark card when disabled.
 */
@Composable
fun Modifier.liquidGlassSurface(
    shape: Shape = RoundedCornerShape(18.dp),
    isElevated: Boolean = false,
    borderAlpha: Float = 0.28f
): Modifier {
    val isGlass = isLiquidGlassActive()
    return if (isGlass) {
        this
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (isElevated) 0.12f else 0.07f),
                        Color(0xFF101018).copy(alpha = if (isElevated) 0.70f else 0.58f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = LiquidGlassTokens.specularBorder(alpha = borderAlpha),
                shape = shape
            )
    } else {
        this
            .clip(shape)
            .background(if (isElevated) LiquidGlassTokens.ClassicSurfaceElevated else LiquidGlassTokens.ClassicCard)
            .border(
                width = 1.dp,
                color = LiquidGlassTokens.ClassicBorderColor,
                shape = shape
            )
    }
}

/**
 * Reusable surface container that seamlessly switches between Liquid Glass (iOS)
 * and Classic Dark theme based on the user's Settings toggle and device capability.
 */
@Composable
fun LiquidGlassContainer(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    isElevated: Boolean = false,
    borderAlpha: Float = 0.28f,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val isGlass = isLiquidGlassActive()
    val surfaceColor = if (isGlass) {
        if (isElevated) LiquidGlassTokens.GlassSurfaceElevated else LiquidGlassTokens.GlassCard
    } else {
        if (isElevated) LiquidGlassTokens.ClassicSurfaceElevated else LiquidGlassTokens.ClassicCard
    }

    val borderStroke = if (isGlass) {
        BorderStroke(1.dp, LiquidGlassTokens.specularBorder(borderAlpha))
    } else {
        BorderStroke(1.dp, LiquidGlassTokens.ClassicBorderColor)
    }

    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier
            .clip(shape)
            .border(borderStroke, shape),
        shape = shape,
        color = surfaceColor
    ) {
        content()
    }
}

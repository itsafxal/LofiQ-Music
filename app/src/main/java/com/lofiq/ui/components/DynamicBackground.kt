package com.lofiq.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lofiq.ui.theme.ArtworkAtmospherePalette
import com.lofiq.ui.theme.BackgroundBlack
import com.lofiq.ui.theme.LocalPerformanceModeEnabled

/**
 * LofiQ Full-Screen Artwork Atmosphere Backdrop:
 *
 * 1. BASE: Deep Foundation
 * 2. ENLARGED + BLURRED ARTWORK: 1.35x scale, 32dp blur filling screen edge-to-edge with no borders
 *    (Bypassed in Performance Mode to save GPU fillrate on budget 2GB-4GB RAM phones)
 * 3. HARDWARE-ACCELERATED MESH/GRADIENT (in Performance Mode): Pure Canvas gradient derived from palette
 * 4. PROGRESSIVE DARK SCRIM: Vertical gradient preserving artwork vibrancy while maintaining control readability
 */
@Composable
fun DynamicArtworkBackground(
    artworkUrl: String,
    modifier: Modifier = Modifier,
    scrimBrush: Brush? = null,
    palette: ArtworkAtmospherePalette? = null
) {
    val isPerformanceMode = LocalPerformanceModeEnabled.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBlack)
    ) {
        // LAYER 1: Full-Screen Enlarged & Blurred Exact Artwork (High-end Mode)
        if (!isPerformanceMode && artworkUrl.isNotBlank()) {
            Crossfade(
                targetState = artworkUrl,
                animationSpec = tween(
                    durationMillis = 500,
                    easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
                ),
                label = "BackdropArtworkCrossfade"
            ) { targetUrl ->
                if (targetUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(targetUrl)
                            .crossfade(250)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = 1.35f
                                scaleY = 1.35f
                                transformOrigin = TransformOrigin(0.5f, 0.5f)
                            }
                            .blur(radius = 32.dp)
                    )
                }
            }
        }

        // LAYER 1b: Performance Mode Hardware-Accelerated Gradient Bloom (Zero GPU Blur Overhead)
        if (isPerformanceMode && palette != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val vibrantColor = palette.vibrant
                val dominantColor = palette.dominant
                drawRect(
                    brush = Brush.verticalGradient(
                        0.00f to dominantColor.copy(alpha = 0.40f),
                        0.40f to vibrantColor.copy(alpha = 0.22f),
                        0.85f to BackgroundBlack
                    )
                )
            }
        }

        // LAYER 2: Progressive Soft Legibility Scrim
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = scrimBrush ?: Brush.verticalGradient(
                    0.00f to Color.Black.copy(alpha = 0.38f),
                    0.35f to Color.Black.copy(alpha = 0.48f),
                    0.70f to Color.Black.copy(alpha = 0.68f),
                    1.00f to Color.Black.copy(alpha = 0.85f)
                )
            )
        }
    }
}

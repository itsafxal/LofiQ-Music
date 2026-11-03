package com.lofiq.ui.components

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import coil.Coil
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lofiq.core.model.Song
import com.lofiq.ui.theme.ArtworkAtmospherePalette
import com.lofiq.ui.theme.ArtworkColorUtils
import com.lofiq.ui.theme.BackgroundBlack
import com.lofiq.ui.theme.LocalPerformanceModeEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val paletteCache = mutableMapOf<String, ArtworkAtmospherePalette>()

/**
 * Global Exact-Color Dynamic Ambient Atmosphere Backdrop
 * 
 * Provides an authentic dynamic background across Home UI, Search, Library, Settings, and Playlists
 * according to the currently playing song's artwork exact colors and textures:
 *
 * 1. Base: Deep Foundation (BackgroundBlack)
 * 2. Exact Blurred Artwork Atmosphere: Enlarged, blurred artwork layer providing spatial color richness
 * 3. Exact Color Theme Mesh: Multi-stop gradient + upper & lower radial blooms with authentic dominant/secondary hues
 * 4. Progressive Dark Scrim: Guarantees high text legibility for search bars, track titles, and cards
 *
 * Fully preserves black/dark artwork (never converts black to brown), brown/earthy artwork
 * (never converts brown to red), and extracts full 360-degree colors across all genres.
 */
@Composable
fun AmbientAppAtmosphere(
    currentSong: Song?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentPalette by remember { mutableStateOf<ArtworkAtmospherePalette?>(null) }

    LaunchedEffect(currentSong?.id) {
        if (currentSong == null) {
            currentPalette = null
            return@LaunchedEffect
        }

        val cached = paletteCache[currentSong.id]
        if (cached != null) {
            currentPalette = cached
            return@LaunchedEffect
        }

        val artUrl = currentSong.highResArtworkUrl
            .ifEmpty { currentSong.artworkUrl }
            .ifEmpty { currentSong.id }

        if (artUrl.isBlank()) {
            currentPalette = null
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            try {
                val loader = Coil.imageLoader(context)
                val req = ImageRequest.Builder(context)
                    .data(artUrl)
                    .size(128, 128)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(req)
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val palette = ArtworkColorUtils.extractAtmospherePalette(bitmap)
                    paletteCache[currentSong.id] = palette
                    currentPalette = palette
                } else {
                    currentPalette = null
                }
            } catch (e: Exception) {
                currentPalette = null
            }
        }
    }

    val atmosphereEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

    val dominantColor = currentPalette?.dominant
    val secondaryColor = currentPalette?.secondary ?: dominantColor

    // Compute deep, tasteful ambient colors spanning 100% from top to bottom
    val topTarget = remember(dominantColor, currentPalette) {
        computeAmbientTone(dominantColor, currentPalette, lightness = 0.16f, saturationScale = 0.70f)
    }
    val midTarget = remember(dominantColor, currentPalette) {
        computeAmbientTone(dominantColor, currentPalette, lightness = 0.11f, saturationScale = 0.55f)
    }
    val bottomTarget = remember(dominantColor, currentPalette) {
        computeAmbientTone(dominantColor, currentPalette, lightness = 0.08f, saturationScale = 0.50f)
    }
    val bloomTarget = remember(dominantColor, currentPalette) {
        computeAmbientTone(dominantColor, currentPalette, lightness = 0.22f, saturationScale = 0.75f)
    }
    val secondaryBloomTarget = remember(secondaryColor, currentPalette) {
        computeAmbientTone(secondaryColor, currentPalette, lightness = 0.18f, saturationScale = 0.70f)
    }

    val animatedTopColor by animateColorAsState(
        targetValue = topTarget,
        animationSpec = tween(durationMillis = 850, easing = atmosphereEasing),
        label = "AmbientTopAtmosphere"
    )

    val animatedMidColor by animateColorAsState(
        targetValue = midTarget,
        animationSpec = tween(durationMillis = 850, easing = atmosphereEasing),
        label = "AmbientMidAtmosphere"
    )

    val animatedBottomColor by animateColorAsState(
        targetValue = bottomTarget,
        animationSpec = tween(durationMillis = 850, easing = atmosphereEasing),
        label = "AmbientBottomAtmosphere"
    )

    val animatedBloomColor by animateColorAsState(
        targetValue = bloomTarget,
        animationSpec = tween(durationMillis = 850, easing = atmosphereEasing),
        label = "AmbientBloomAtmosphere"
    )

    val animatedSecondaryBloomColor by animateColorAsState(
        targetValue = secondaryBloomTarget,
        animationSpec = tween(durationMillis = 850, easing = atmosphereEasing),
        label = "AmbientSecondaryBloomAtmosphere"
    )

    val artUrl = currentSong?.highResArtworkUrl
        ?.ifEmpty { currentSong.artworkUrl }
        .orEmpty()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBlack)
    ) {
        // LAYER 1: Full-Screen Enlarged & Blurred Exact Artwork Atmosphere (Bypassed in Performance Mode to save GPU fillrate on 2GB-4GB RAM)
        val isPerformanceMode = LocalPerformanceModeEnabled.current
        if (!isPerformanceMode && artUrl.isNotBlank()) {
            Crossfade(
                targetState = artUrl,
                animationSpec = tween(
                    durationMillis = 650,
                    easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
                ),
                label = "AmbientBackdropArtworkCrossfade"
            ) { targetUrl ->
                if (targetUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(targetUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = 1.35f
                                scaleY = 1.35f
                                alpha = 0.38f
                            }
                            .blur(radius = 42.dp)
                    )
                }
            }
        }

        // LAYER 2: Exact Artwork Color Canvas Gradient & Multi-Stop Radial Blooms
        if (currentSong != null && currentPalette != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Full vertical gradient spanning 100% top to bottom edge with rich theme color
                drawRect(
                    brush = Brush.verticalGradient(
                        0.00f to animatedTopColor,
                        0.45f to animatedMidColor,
                        1.00f to animatedBottomColor
                    )
                )

                // Atmospheric radial bloom near upper screen (dominant tone)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            animatedBloomColor.copy(alpha = 0.45f),
                            animatedBloomColor.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.20f),
                        radius = size.width * 0.95f
                    )
                )

                // Atmospheric radial bloom near lower screen (secondary accent tone)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            animatedSecondaryBloomColor.copy(alpha = 0.28f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.80f),
                        radius = size.width * 0.95f
                    )
                )
            }
        }

        // LAYER 3: Progressive Soft Legibility Scrim
        // Guarantees pristine contrast and readability for all text, search inputs,
        // shelf carousels, and bottom bar controls across Home UI, Search, and Library.
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    0.00f to Color.Black.copy(alpha = 0.25f),
                    0.35f to Color.Black.copy(alpha = 0.35f),
                    0.70f to Color.Black.copy(alpha = 0.50f),
                    1.00f to Color.Black.copy(alpha = 0.65f)
                )
            )
        }
    }
}

/**
 * Computes deep, legible dark-mode ambient tones adapted directly from the exact artwork color.
 */
private fun computeAmbientTone(
    color: Color?,
    palette: ArtworkAtmospherePalette?,
    lightness: Float,
    saturationScale: Float
): Color {
    if (color == null || palette == null) return BackgroundBlack

    // 1. Black / deep dark artwork preservation (strictly avoids turning black to brown)
    if (palette.isDark) {
        return Color(0xFF090A0D)
    }

    // 2. Grayscale / monochrome artwork preservation (strictly preserves 0 saturation)
    if (palette.isGrayscale) {
        val l = lightness.coerceIn(0.06f, 0.12f)
        return Color(ColorUtils.HSLToColor(floatArrayOf(0f, 0f, l)))
    }

    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    val hue = hsl[0]
    val sat = hsl[1]
    val lit = hsl[2]

    if (sat < 0.08f) {
        return Color(0xFF0E0F14)
    }

    // 3. Brown / earthy artwork preservation (strictly preserves warm hue in 15-45 deg range; never shifts to red)
    val isBrown = hue in 15f..45f && sat in 0.15f..0.65f && lit in 0.10f..0.50f
    if (isBrown) {
        val adaptedBrownHsl = floatArrayOf(
            hue,
            sat.coerceIn(0.25f, 0.50f),
            lightness.coerceIn(0.08f, 0.18f)
        )
        return Color(ColorUtils.HSLToColor(adaptedBrownHsl))
    }

    // 4. Full-spectrum chromatic colors (preserves exact authentic hue across 0..360 deg)
    val adaptedSat = (sat * saturationScale).coerceIn(0.25f, 0.80f)
    val adaptedLit = lightness.coerceIn(0.08f, 0.22f)
    return Color(ColorUtils.HSLToColor(floatArrayOf(hue, adaptedSat, adaptedLit)))
}

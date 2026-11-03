package com.lofiq.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import kotlin.math.abs

/**
 * Production artwork atmosphere palette model.
 */
data class ArtworkAtmospherePalette(
    val dominant: Color,
    val darkMuted: Color,
    val vibrant: Color,
    val isGrayscale: Boolean,
    val isDark: Boolean = false,
    val secondary: Color = dominant
) {
    companion object {
        val DefaultNeutral = ArtworkAtmospherePalette(
            dominant = Color(0xFF1E1E1E),
            darkMuted = Color(0xFF121212),
            vibrant = Color(0xFF282828),
            isGrayscale = true,
            isDark = true,
            secondary = Color(0xFF1E1E1E)
        )
    }
}

/**
 * Production utilities for extracting and adapting artwork color palettes.
 *
 * Implements LofiQ color processing and saturation adaptation rules:
 * - Grayscale artwork preserves 0 saturation (never injects default accent/red hue).
 * - Near-neutral artwork preserves subtle saturation.
 * - Chromatic artwork is boosted to minimum and capped at maximum saturation.
 * - Black artwork preserves true obsidian dark-mode tone (never converts black to brown).
 * - Brown/earthy artwork preserves warm amber/chocolate hue (never converts brown to red).
 * - Full 360-degree hue spectrum extracted via area-weighted pixel histogram clustering.
 */
object ArtworkColorUtils {

    const val DEFAULT_MINIMUM_SATURATION = 0.20f
    const val DEFAULT_MAXIMUM_SATURATION = 0.62f
    const val DEFAULT_NEUTRAL_THRESHOLD = 0.10f

    /**
     * Adapts artwork saturation according to ArtworkPalette rules:
     *
     * 1. Grayscale artwork (saturation == 0) -> Returns 0.0 (preserves neutral grayscale).
     * 2. Near-neutral artwork (0 < saturation < neutralThreshold) -> Preserves subtle saturation.
     * 3. Chromatic artwork (saturation >= neutralThreshold):
     *    - If saturation < minimum -> Boosted to minimum.
     *    - If saturation > maximum -> Clamped to maximum.
     *    - Otherwise -> Preserved as-is.
     */
    fun adaptedArtworkSaturation(
        saturation: Float,
        minimum: Float = DEFAULT_MINIMUM_SATURATION,
        maximum: Float = DEFAULT_MAXIMUM_SATURATION,
        neutralThreshold: Float = DEFAULT_NEUTRAL_THRESHOLD
    ): Float {
        val clampedInput = saturation.coerceIn(0f, 1f)
        val validMin = minimum.coerceIn(0f, 1f)
        val validMax = maximum.coerceIn(validMin, 1f)

        return when {
            // Case 1: Exact zero saturation (Grayscale)
            clampedInput == 0f -> 0f

            // Case 2: Near-neutral artwork below threshold
            clampedInput < neutralThreshold -> clampedInput

            // Case 3: Chromatic artwork boosted to minimum
            clampedInput < validMin -> validMin

            // Case 4: Chromatic artwork capped at maximum
            clampedInput > validMax -> validMax

            // Case 5: Within [minimum, maximum] range
            else -> clampedInput
        }
    }

    /**
     * Adapts a single color's HSL saturation using [adaptedArtworkSaturation].
     */
    fun adaptColorSaturation(
        color: Color,
        minimum: Float = DEFAULT_MINIMUM_SATURATION,
        maximum: Float = DEFAULT_MAXIMUM_SATURATION,
        neutralThreshold: Float = DEFAULT_NEUTRAL_THRESHOLD
    ): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color.toArgb(), hsl)
        val originalSat = hsl[1]
        val adaptedSat = adaptedArtworkSaturation(originalSat, minimum, maximum, neutralThreshold)
        hsl[1] = adaptedSat

        // For grayscale (saturation == 0), keep lightness controlled for dark atmosphere
        val isGrayscale = adaptedSat == 0f
        if (isGrayscale) {
            hsl[2] = hsl[2].coerceIn(0.08f, 0.20f)
        } else {
            hsl[2] = hsl[2].coerceIn(0.12f, 0.45f)
        }

        return Color(ColorUtils.HSLToColor(hsl))
    }

    /**
     * Extracts an exact, authentic color model palette directly from the artwork [Bitmap].
     *
     * Solves previous quantization flaws:
     * - Black / dark artwork remains deep obsidian charcoal (never turns brown).
     * - Brown / earthy artwork preserves warm amber/chocolate hue (never turns red).
     * - Real dominant colors across the 360-degree spectrum are extracted via area-weighted
     *   clustering rather than latching onto random 2% vibrant badges.
     */
    fun extractAtmospherePalette(
        bitmap: Bitmap,
        minimumSaturation: Float = DEFAULT_MINIMUM_SATURATION,
        maximumSaturation: Float = DEFAULT_MAXIMUM_SATURATION
    ): ArtworkAtmospherePalette {
        if (bitmap.width <= 0 || bitmap.height <= 0) {
            return ArtworkAtmospherePalette.DefaultNeutral
        }

        val sampleWidth = 48
        val sampleHeight = 48
        val scaledBitmap = if (bitmap.width > sampleWidth || bitmap.height > sampleHeight) {
            Bitmap.createScaledBitmap(bitmap, sampleWidth, sampleHeight, true)
        } else {
            bitmap
        }

        val totalPixels = scaledBitmap.width * scaledBitmap.height
        val pixels = IntArray(totalPixels)
        scaledBitmap.getPixels(pixels, 0, scaledBitmap.width, 0, 0, scaledBitmap.width, scaledBitmap.height)
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }

        val hsl = FloatArray(3)
        var totalValidPixels = 0
        var darkPixelCount = 0
        var grayscalePixelCount = 0
        var sumSaturation = 0f
        var sumLightness = 0f

        class ColorBin {
            var count = 0
            var sumR = 0L
            var sumG = 0L
            var sumB = 0L
            var sumSat = 0f
            var sumLight = 0f
            var sumHue = 0f
        }

        val bins = mutableMapOf<Int, ColorBin>()

        for (p in pixels) {
            val alpha = (p ushr 24) and 0xFF
            if (alpha < 128) continue

            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = p and 0xFF

            ColorUtils.RGBToHSL(r, g, b, hsl)
            val h = hsl[0]
            val s = hsl[1]
            val l = hsl[2]

            totalValidPixels++
            sumSaturation += s
            sumLightness += l

            if (l < 0.12f) {
                darkPixelCount++
            }
            if (s < 0.10f) {
                grayscalePixelCount++
            }

            // Collect chromatic pixels for clustering (ignoring pure black or blinding white background borders)
            if (l in 0.08f..0.94f && s >= 0.08f) {
                val hBin = (h / 15f).toInt().coerceIn(0, 23)
                val sBin = ((s - 0.08f) / 0.23f).toInt().coerceIn(0, 3)
                val lBin = ((l - 0.08f) / 0.21f).toInt().coerceIn(0, 3)
                val binKey = (hBin shl 4) or (sBin shl 2) or lBin

                val bin = bins.getOrPut(binKey) { ColorBin() }
                bin.count++
                bin.sumR += r
                bin.sumG += g
                bin.sumB += b
                bin.sumSat += s
                bin.sumLight += l
                bin.sumHue += h
            }
        }

        if (totalValidPixels == 0) {
            return ArtworkAtmospherePalette.DefaultNeutral
        }

        val avgSaturation = sumSaturation / totalValidPixels
        val avgLightness = sumLightness / totalValidPixels
        val darkRatio = darkPixelCount.toFloat() / totalValidPixels
        val grayscaleRatio = grayscalePixelCount.toFloat() / totalValidPixels

        // 1. TRUE BLACK / DEEP DARK ARTWORK CHECK
        if (darkRatio >= 0.65f || avgLightness <= 0.12f) {
            val darkObsidian = Color(0xFF0F1014)
            val deepGraphite = Color(0xFF181A20)
            return ArtworkAtmospherePalette(
                dominant = darkObsidian,
                darkMuted = Color(0xFF090A0D),
                vibrant = deepGraphite,
                isGrayscale = true,
                isDark = true,
                secondary = darkObsidian
            )
        }

        // 2. TRUE MONOCHROME / GRAYSCALE ARTWORK CHECK
        if (grayscaleRatio >= 0.75f || avgSaturation <= 0.10f || bins.isEmpty()) {
            val slate = Color(0xFF1C1D24)
            val slateMuted = Color(0xFF121318)
            val slateHighlight = Color(0xFF323642)
            return ArtworkAtmospherePalette(
                dominant = slate,
                darkMuted = slateMuted,
                vibrant = slateHighlight,
                isGrayscale = true,
                isDark = false,
                secondary = slate
            )
        }

        // 3. CHROMATIC DOMINANT COLOR CLUSTERING
        val dominantBin = bins.values.maxByOrNull { it.count }!!
        val domR = (dominantBin.sumR / dominantBin.count).toInt().coerceIn(0, 255)
        val domG = (dominantBin.sumG / dominantBin.count).toInt().coerceIn(0, 255)
        val domB = (dominantBin.sumB / dominantBin.count).toInt().coerceIn(0, 255)
        val domHue = dominantBin.sumHue / dominantBin.count
        val domSat = dominantBin.sumSat / dominantBin.count
        val domLight = dominantBin.sumLight / dominantBin.count

        // Check if this is an authentic brown / earthy tone
        val isBrown = domHue in 15f..45f && domSat in 0.15f..0.65f && domLight in 0.10f..0.48f

        // 4. SECONDARY / ACCENT COLOR
        val secondaryBin = bins.values
            .filter { bin ->
                val bHue = bin.sumHue / bin.count
                val bSat = bin.sumSat / bin.count
                val diff = abs(bHue - domHue).let { if (it > 180f) 360f - it else it }
                (diff > 25f || abs(bSat - domSat) > 0.25f) && (bin.count >= totalValidPixels * 0.03f)
            }
            .maxByOrNull { bin ->
                val bSat = bin.sumSat / bin.count
                bin.count * (bSat + 0.35f)
            }

        val secondaryColor = if (secondaryBin != null) {
            val secR = (secondaryBin.sumR / secondaryBin.count).toInt().coerceIn(0, 255)
            val secG = (secondaryBin.sumG / secondaryBin.count).toInt().coerceIn(0, 255)
            val secB = (secondaryBin.sumB / secondaryBin.count).toInt().coerceIn(0, 255)
            Color(secR, secG, secB)
        } else {
            Color(domR, domG, domB)
        }

        val rawDominantColor = Color(domR, domG, domB)

        // For vibrant: if secondary exists and is rich, use it; otherwise adapt dominant
        val vibrantColor = if (secondaryBin != null) {
            adaptColorSaturation(secondaryColor, minimumSaturation, maximumSaturation)
        } else {
            adaptColorSaturation(rawDominantColor, minimumSaturation, maximumSaturation)
        }

        // For darkMuted: dominant with dark lightness (0.08 - 0.12)
        val darkMutedHsl = floatArrayOf(domHue, domSat * 0.7f, 0.10f)
        val darkMutedColor = Color(ColorUtils.HSLToColor(darkMutedHsl))

        val adaptedDominant = if (isBrown) {
            // Strictly preserve brown hue and earthy saturation
            val brownHsl = floatArrayOf(
                domHue,
                domSat.coerceIn(0.25f, 0.55f),
                domLight.coerceIn(0.12f, 0.30f)
            )
            Color(ColorUtils.HSLToColor(brownHsl))
        } else {
            adaptColorSaturation(rawDominantColor, minimumSaturation, maximumSaturation)
        }

        return ArtworkAtmospherePalette(
            dominant = adaptedDominant,
            darkMuted = darkMutedColor,
            vibrant = vibrantColor,
            isGrayscale = false,
            isDark = false,
            secondary = secondaryColor
        )
    }
}

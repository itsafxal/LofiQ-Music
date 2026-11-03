package com.lofiq.ui

import com.lofiq.ui.theme.ArtworkColorUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkPaletteTest {

    @Test
    fun `grayscale artwork preserves exactly 0 saturation`() {
        val result1 = ArtworkColorUtils.adaptedArtworkSaturation(
            saturation = 0f,
            minimum = 0.20f,
            maximum = 0.62f
        )
        assertEquals(0f, result1, 0.0001f)

        val result2 = ArtworkColorUtils.adaptedArtworkSaturation(
            saturation = 0f,
            minimum = 0.55f,
            maximum = 1f
        )
        assertEquals(0f, result2, 0.0001f)
    }

    @Test
    fun `near-neutral artwork preserves subtle saturation`() {
        val result1 = ArtworkColorUtils.adaptedArtworkSaturation(
            saturation = 0.08f,
            minimum = 0.20f,
            maximum = 0.62f
        )
        assertEquals(0.08f, result1, 0.0001f)

        val result2 = ArtworkColorUtils.adaptedArtworkSaturation(
            saturation = 0.05f,
            minimum = 0.30f,
            maximum = 0.70f
        )
        assertEquals(0.05f, result2, 0.0001f)
    }

    @Test
    fun `chromatic artwork below minimum is boosted to minimum`() {
        val result = ArtworkColorUtils.adaptedArtworkSaturation(
            saturation = 0.25f,
            minimum = 0.55f,
            maximum = 0.80f
        )
        assertEquals(0.55f, result, 0.0001f)
    }

    @Test
    fun `chromatic artwork above maximum is capped at maximum`() {
        val result = ArtworkColorUtils.adaptedArtworkSaturation(
            saturation = 0.90f,
            minimum = 0.20f,
            maximum = 0.62f
        )
        assertEquals(0.62f, result, 0.0001f)
    }

    @Test
    fun `chromatic artwork within range preserves saturation`() {
        val result = ArtworkColorUtils.adaptedArtworkSaturation(
            saturation = 0.40f,
            minimum = 0.20f,
            maximum = 0.62f
        )
        assertEquals(0.40f, result, 0.0001f)
    }

    @Test
    fun `saturation exactly at minimum is preserved`() {
        val result = ArtworkColorUtils.adaptedArtworkSaturation(
            saturation = 0.20f,
            minimum = 0.20f,
            maximum = 0.62f
        )
        assertEquals(0.20f, result, 0.0001f)
    }

    @Test
    fun `saturation exactly at maximum is preserved`() {
        val result = ArtworkColorUtils.adaptedArtworkSaturation(
            saturation = 0.62f,
            minimum = 0.20f,
            maximum = 0.62f
        )
        assertEquals(0.62f, result, 0.0001f)
    }

    @Test
    fun `negative and out-of-range saturation inputs are safely handled`() {
        val negativeResult = ArtworkColorUtils.adaptedArtworkSaturation(
            saturation = -0.5f,
            minimum = 0.20f,
            maximum = 0.62f
        )
        assertEquals(0f, negativeResult, 0.0001f)

        val overflowResult = ArtworkColorUtils.adaptedArtworkSaturation(
            saturation = 1.5f,
            minimum = 0.20f,
            maximum = 0.62f
        )
        assertEquals(0.62f, overflowResult, 0.0001f)
    }
}

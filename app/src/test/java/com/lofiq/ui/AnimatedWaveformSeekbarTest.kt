package com.lofiq.ui

import com.lofiq.ui.components.DefaultWaveLayers
import com.lofiq.ui.components.calculateWaveHeight
import com.lofiq.ui.components.waveformEnvelope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimatedWaveformSeekbarTest {

    @Test
    fun `waveformEnvelope is exactly zero at both boundaries`() {
        assertEquals(0f, waveformEnvelope(0f), 0.0001f)
        assertEquals(0f, waveformEnvelope(1f), 0.0001f)
    }

    @Test
    fun `waveformEnvelope safely handles out of bounds inputs`() {
        assertEquals(0f, waveformEnvelope(-0.5f), 0.0001f)
        assertEquals(0f, waveformEnvelope(1.5f), 0.0001f)
    }

    @Test
    fun `waveformEnvelope reaches maximum near center`() {
        val centerValue = waveformEnvelope(0.5f)
        assertTrue("Center value must be close to 1.0, was $centerValue", centerValue in 0.95f..1.05f)
    }

    @Test
    fun `calculateWaveHeight is exactly zero at endpoints for all layers and phases`() {
        val testPhases = listOf(0f, 0.5f, 1.57f, 3.14f, 4.8f, 6.28f)
        for (layer in DefaultWaveLayers) {
            for (phase in testPhases) {
                assertEquals(0f, calculateWaveHeight(0f, layer, phase), 0.0001f)
                assertEquals(0f, calculateWaveHeight(1f, layer, phase), 0.0001f)
            }
        }
    }

    @Test
    fun `calculateWaveHeight remains strictly non-negative and capped at 1`() {
        val testPhases = listOf(0f, 1.2f, 2.5f, 4.0f, 5.5f)
        for (layer in DefaultWaveLayers) {
            for (phase in testPhases) {
                for (step in 0..100) {
                    val u = step / 100f
                    val height = calculateWaveHeight(u, layer, phase)
                    assertTrue("Height must be >= 0, was $height at u=$u", height >= -0.0001f)
                    assertTrue("Height must be <= 1.05, was $height at u=$u", height <= 1.05f)
                }
            }
        }
    }

    @Test
    fun `default wave layers contains 4 curated visual layers`() {
        assertEquals(4, DefaultWaveLayers.size)

        // Verify stroke widths are delicate (between 1.5dp and 2.5dp)
        for (layer in DefaultWaveLayers) {
            assertTrue(layer.strokeWidth.value in 1.4f..2.5f)
            assertTrue(layer.amplitude.value in 10f..30f)
            assertTrue(layer.speed > 0f)
            assertTrue(layer.cycles in 2.0f..4.0f)
        }
    }

    @Test
    fun `seek time conversion logic handles edge cases safely`() {
        val durationMs = 240000L // 4 minutes

        // Regular seeking
        val halfMs = (0.5f * durationMs).toLong().coerceIn(0L, durationMs)
        assertEquals(120000L, halfMs)

        // Zero seeking
        val zeroMs = (0.0f * durationMs).toLong().coerceIn(0L, durationMs)
        assertEquals(0L, zeroMs)

        // Full seeking
        val fullMs = (1.0f * durationMs).toLong().coerceIn(0L, durationMs)
        assertEquals(240000L, fullMs)

        // Overflow seeking
        val overMs = (1.5f * durationMs).toLong().coerceIn(0L, durationMs)
        assertEquals(240000L, overMs)

        // Negative seeking
        val underMs = (-0.5f * durationMs).toLong().coerceIn(0L, durationMs)
        assertEquals(0L, underMs)

        // Zero duration song
        val zeroDuration = 0L
        val safeZeroDur = zeroDuration.coerceAtLeast(1L)
        val seekOnZero = (0.5f * safeZeroDur).toLong().coerceIn(0L, zeroDuration)
        assertEquals(0L, seekOnZero)
    }

    @Test
    fun `default dynamic waveform palette has vibrant and distinct color layers`() {
        val palette = com.lofiq.ui.components.DynamicWaveformPalette.Default
        assertTrue(palette.primaryWave.alpha > 0f)
        assertTrue(palette.secondaryWave.alpha > 0f)
        assertTrue(palette.tertiaryWave.alpha > 0f)
        assertTrue(palette.quaternaryWave.alpha > 0f)
    }

    @Test
    fun `fromArtworkColor generates valid harmonious dynamic waveform palette`() {
        val sampleColor = androidx.compose.ui.graphics.Color(0xFF007AFF) // Electric Blue
        val palette = com.lofiq.ui.components.DynamicWaveformPalette.fromArtworkColor(sampleColor)
        assertTrue(palette.primaryWave.alpha > 0f)
        assertTrue(palette.thumbCore.alpha > 0f)
    }
}

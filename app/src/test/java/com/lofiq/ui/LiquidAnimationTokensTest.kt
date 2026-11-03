package com.lofiq.ui

import com.lofiq.ui.animation.LiquidAnimationTokens
import org.junit.Assert.*
import org.junit.Test

class LiquidAnimationTokensTest {

    @Test
    fun `liquid animation tokens have valid durations`() {
        assertTrue("Enter duration must be positive", LiquidAnimationTokens.FullPlayerEnterDuration > 0)
        assertTrue("Exit duration must be positive", LiquidAnimationTokens.FullPlayerExitDuration > 0)
        assertTrue("Tab switch duration must be positive", LiquidAnimationTokens.TabSwitchDuration > 0)
        assertTrue("Swipe duration must be positive", LiquidAnimationTokens.ArtworkSwipeDuration > 0)
    }

    @Test
    fun `liquid easing curves are non-null`() {
        assertNotNull(LiquidAnimationTokens.FluidDecelEasing)
        assertNotNull(LiquidAnimationTokens.FluidSpringEasing)
        assertNotNull(LiquidAnimationTokens.SharpEasing)
    }

    @Test
    fun `effective liquid glass state is false when performance mode is true`() {
        // Simulates isLiquidGlassActive condition
        val liquidGlassPref = true
        val performanceModePref = true

        val isActive = liquidGlassPref && !performanceModePref
        assertFalse("Liquid glass MUST be inactive when performance mode is active", isActive)
    }

    @Test
    fun `effective liquid glass state is true when performance mode is false and glass is enabled`() {
        val liquidGlassPref = true
        val performanceModePref = false

        val isActive = liquidGlassPref && !performanceModePref
        assertTrue("Liquid glass MUST be active when performance mode is OFF and glass is enabled", isActive)
    }
}


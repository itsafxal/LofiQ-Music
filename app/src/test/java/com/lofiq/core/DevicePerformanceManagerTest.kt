package com.lofiq.core

import com.lofiq.core.common.DevicePerformanceManager
import org.junit.Assert.*
import org.junit.Test

class DevicePerformanceManagerTest {

    @Test
    fun `performance tiers are defined correctly with appropriate labels`() {
        assertEquals("Low-Spec", DevicePerformanceManager.PerformanceTier.LOW.label)
        assertEquals("Mid-Tier", DevicePerformanceManager.PerformanceTier.MID.label)
        assertEquals("High-Spec Flagship", DevicePerformanceManager.PerformanceTier.HIGH.label)
    }

    @Test
    fun `getCpuCores returns positive number of hardware processors`() {
        val cores = DevicePerformanceManager.getCpuCores()
        assertTrue("Available CPU cores must be at least 1", cores >= 1)
    }

    @Test
    fun `performance tiers cover low mid and high thresholds`() {
        val tiers = DevicePerformanceManager.PerformanceTier.values()
        assertEquals(3, tiers.size)
        assertTrue(tiers.contains(DevicePerformanceManager.PerformanceTier.LOW))
        assertTrue(tiers.contains(DevicePerformanceManager.PerformanceTier.MID))
        assertTrue(tiers.contains(DevicePerformanceManager.PerformanceTier.HIGH))
    }
}

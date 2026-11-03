package com.lofiq.core.common

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * Utility for detecting hardware tier, CPU capabilities, and device memory capacity.
 * Automatically adapts graphic fidelity, blurs, and animation complexity
 * so that budget phones (2GB, 3GB, 4GB RAM, <= 4 cores) run butter-smooth at 60 FPS
 * without overheating, dropping frames, or suffering memory pressure.
 */
object DevicePerformanceManager {

    enum class PerformanceTier(val label: String) {
        LOW("Low-Spec"),
        MID("Mid-Tier"),
        HIGH("High-Spec Flagship")
    }

    private var cachedTotalRamGb: Float? = null
    private var cachedIsLowRam: Boolean? = null
    private var cachedCpuCores: Int? = null

    /**
     * Total physical RAM in gigabytes (e.g. 2.0, 3.8, 7.6, etc.)
     */
    fun getTotalRamGb(context: Context): Float {
        cachedTotalRamGb?.let { return it }
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager?.getMemoryInfo(memInfo)
            val ramGb = (memInfo.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)).toFloat()
            cachedTotalRamGb = ramGb
            ramGb
        } catch (_: Exception) {
            4.0f
        }
    }

    /**
     * Number of available CPU cores.
     */
    fun getCpuCores(): Int {
        cachedCpuCores?.let { return it }
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        cachedCpuCores = cores
        return cores
    }

    /**
     * Returns true if the device is a low-RAM device (<= 4.25 GB physical RAM or Android lowRamDevice flag).
     */
    fun isLowRamDevice(context: Context): Boolean {
        cachedIsLowRam?.let { return it }
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (actManager?.isLowRamDevice == true) {
                cachedIsLowRam = true
                return true
            }
            val totalGb = getTotalRamGb(context)
            val isLow = totalGb in 0.1f..4.25f
            cachedIsLowRam = isLow
            isLow
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Comprehensive hardware capability check:
     * Device is considered low-spec if physical RAM <= 4.25GB, system lowRamDevice is true,
     * available CPU cores <= 4, or Android version is below API 28 (where RenderEffect / GPU blurs are costly).
     */
    fun isLowSpecDevice(context: Context): Boolean {
        if (isLowRamDevice(context)) return true
        if (getCpuCores() <= 4) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return true
        return false
    }

    /**
     * Classifies device into LOW, MID, or HIGH performance tier.
     */
    fun getPerformanceTier(context: Context): PerformanceTier {
        val ram = getTotalRamGb(context)
        val cores = getCpuCores()
        return when {
            ram <= 4.25f || cores <= 4 || isLowRamDevice(context) -> PerformanceTier.LOW
            ram < 7.5f || cores < 8 -> PerformanceTier.MID
            else -> PerformanceTier.HIGH
        }
    }

    /**
     * Determines the automatic default for Performance Mode.
     * True on low-spec phones (2GB, 3GB, 4GB RAM or <= 4 cores); False on 6GB, 8GB, 12GB+ devices.
     */
    fun shouldDefaultToPerformanceMode(context: Context): Boolean {
        return isLowSpecDevice(context)
    }

    /**
     * Determines the automatic default for Liquid Glass UI.
     * Liquid Glass is ON by default whenever Performance Mode is OFF.
     */
    fun shouldDefaultToLiquidGlass(context: Context): Boolean {
        return !shouldDefaultToPerformanceMode(context)
    }

    /**
     * Determines whether heavy GPU blurs and intensive multi-layer blurs are safe to render.
     */
    fun isSafeForHeavyBlur(context: Context): Boolean {
        return !isLowSpecDevice(context)
    }

    /**
     * Human-readable hardware summary for Settings UI.
     */
    fun getHardwareSummary(context: Context): String {
        val ram = String.format(java.util.Locale.US, "%.1f GB", getTotalRamGb(context))
        val cores = "${getCpuCores()} Cores"
        val tier = getPerformanceTier(context).label
        return "$ram • $cores • $tier"
    }
}

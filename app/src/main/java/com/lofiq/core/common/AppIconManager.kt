package com.lofiq.core.common

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.lofiq.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class AppIconTheme(
    val id: String,
    val displayName: String,
    val isDefault: Boolean,
    val aliasName: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val hexCode: String,
    @DrawableRes val iconDrawableRes: Int
) {
    PURPLE(
        id = "purple",
        displayName = "Royal Purple",
        isDefault = true,
        aliasName = "MainActivityDefault",
        primaryColor = Color(0xFF4A00B5),
        secondaryColor = Color(0xFF7C3AED),
        hexCode = "#4A00B5",
        iconDrawableRes = R.mipmap.ic_launcher
    ),
    RED(
        id = "red",
        displayName = "Crimson Red",
        isDefault = false,
        aliasName = "MainActivityRed",
        primaryColor = Color(0xFFD32F2F),
        secondaryColor = Color(0xFFEF4444),
        hexCode = "#D32F2F",
        iconDrawableRes = R.mipmap.ic_launcher_red
    ),
    BLUE(
        id = "blue",
        displayName = "Sapphire Blue",
        isDefault = false,
        aliasName = "MainActivityBlue",
        primaryColor = Color(0xFF1D4ED8),
        secondaryColor = Color(0xFF3B82F6),
        hexCode = "#2563EB",
        iconDrawableRes = R.mipmap.ic_launcher_blue
    ),
    GREEN(
        id = "green",
        displayName = "Emerald Green",
        isDefault = false,
        aliasName = "MainActivityGreen",
        primaryColor = Color(0xFF059669),
        secondaryColor = Color(0xFF10B981),
        hexCode = "#059669",
        iconDrawableRes = R.mipmap.ic_launcher_green
    ),
    ORANGE(
        id = "orange",
        displayName = "Sunset Orange",
        isDefault = false,
        aliasName = "MainActivityOrange",
        primaryColor = Color(0xFFEA580C),
        secondaryColor = Color(0xFFF97316),
        hexCode = "#EA580C",
        iconDrawableRes = R.mipmap.ic_launcher_orange
    ),
    BLACK(
        id = "black",
        displayName = "Midnight Obsidian",
        isDefault = false,
        aliasName = "MainActivityBlack",
        primaryColor = Color(0xFF18181B),
        secondaryColor = Color(0xFF27272A),
        hexCode = "#18181B",
        iconDrawableRes = R.mipmap.ic_launcher_black
    ),
    PINK(
        id = "pink",
        displayName = "Neon Pink",
        isDefault = false,
        aliasName = "MainActivityPink",
        primaryColor = Color(0xFFDB2777),
        secondaryColor = Color(0xFFEC4899),
        hexCode = "#DB2777",
        iconDrawableRes = R.mipmap.ic_launcher_pink
    ),
    AMBER(
        id = "amber",
        displayName = "Golden Amber",
        isDefault = false,
        aliasName = "MainActivityAmber",
        primaryColor = Color(0xFFD97706),
        secondaryColor = Color(0xFFF59E0B),
        hexCode = "#D97706",
        iconDrawableRes = R.mipmap.ic_launcher_amber
    ),
    TEAL(
        id = "teal",
        displayName = "Electric Teal",
        isDefault = false,
        aliasName = "MainActivityTeal",
        primaryColor = Color(0xFF0D9488),
        secondaryColor = Color(0xFF14B8A6),
        hexCode = "#0D9488",
        iconDrawableRes = R.mipmap.ic_launcher_teal
    );

    val gradientColors: List<Color>
        get() = listOf(secondaryColor, primaryColor)

    companion object {
        fun fromId(id: String?): AppIconTheme {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: PURPLE
        }

        fun findNearestAlias(color: Color): AppIconTheme {
            val r = (color.red * 255).toInt().coerceIn(0, 255)
            val g = (color.green * 255).toInt().coerceIn(0, 255)
            val b = (color.blue * 255).toInt().coerceIn(0, 255)

            // Very dark / black detection
            val maxC = maxOf(r, g, b)
            val minC = minOf(r, g, b)
            if (maxC < 45 || (maxC - minC < 20 && maxC < 75)) {
                return BLACK
            }

            val hsv = FloatArray(3)
            android.graphics.Color.RGBToHSV(r, g, b, hsv)
            val hue = hsv[0] // 0..360

            return when {
                hue in 0f..15f || hue in 345f..360f -> RED
                hue in 15f..42f -> ORANGE
                hue in 42f..68f -> AMBER
                hue in 68f..165f -> GREEN
                hue in 165f..195f -> TEAL
                hue in 195f..255f -> BLUE
                hue in 255f..295f -> PURPLE
                hue in 295f..345f -> PINK
                else -> PURPLE
            }
        }

        fun deriveGradientSecondary(primary: Color): Color {
            // Preserves exact primary hue and saturation so app branding remains 100% faithful to the picked color
            return primary
        }
    }
}

@Singleton
class AppIconManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AppIconManager"
    }

    private fun getComponentForTheme(theme: AppIconTheme): ComponentName {
        val pkg = context.packageName
        return ComponentName(pkg, "$pkg.${theme.aliasName}")
    }

    /**
     * Activates the given AppIconTheme launcher alias and deactivates all other launcher aliases.
     * Uses PackageManager.DONT_KILL_APP so all component updates complete safely without premature death.
     * Guarantees that the target alias is enabled BEFORE disabling others, ensuring the launcher always has an active entry point.
     * If killProcessAfter is true, schedules a graceful process exit after delay so the Android launcher can re-bind (matching SuvMusic behavior).
     */
    fun setAppIcon(targetTheme: AppIconTheme, killProcessAfter: Boolean = true): Boolean {
        val pm = context.packageManager
        val targetComponent = getComponentForTheme(targetTheme)
        var anyChanged = false

        // 1. First, enable the target component.
        try {
            val currentState = pm.getComponentEnabledSetting(targetComponent)
            if (currentState != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                pm.setComponentEnabledSetting(
                    targetComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                anyChanged = true
                Log.i(TAG, "Enabled launcher alias: ${targetComponent.className}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable target component ${targetComponent.className}", e)
            return false
        }

        // Verify that target is actually enabled before disabling anything
        val verifyState = try {
            pm.getComponentEnabledSetting(targetComponent)
        } catch (_: Exception) {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }
        if (verifyState != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            Log.e(TAG, "Verification failed for ${targetComponent.className}, aborting disable step to avoid launcher loss")
            return false
        }

        // 2. Only after target is safely enabled, disable all other aliases.
        AppIconTheme.entries.filter { it != targetTheme }.forEach { theme ->
            val otherComponent = getComponentForTheme(theme)
            try {
                val currentState = pm.getComponentEnabledSetting(otherComponent)
                if (currentState != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                    pm.setComponentEnabledSetting(
                        otherComponent,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    anyChanged = true
                    Log.i(TAG, "Disabled launcher alias: ${otherComponent.className}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error disabling alias ${otherComponent.className}", e)
            }
        }

        // 3. If any alias changed and killProcessAfter is true, terminate the process after 600ms
        // so that the Android launcher re-binds cleanly to the new component (matching SuvMusic).
        if (anyChanged && killProcessAfter) {
            Handler(Looper.getMainLooper()).postDelayed({
                Process.killProcess(Process.myPid())
            }, 600L)
        }

        return true
    }

    /**
     * Returns the currently active launcher icon theme based on PackageManager state,
     * defaulting to PURPLE (Default).
     */
    fun getCurrentActiveIconTheme(): AppIconTheme {
        val pm = context.packageManager
        for (theme in AppIconTheme.entries.filter { !it.isDefault }) {
            val component = getComponentForTheme(theme)
            try {
                val state = pm.getComponentEnabledSetting(component)
                if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                    return theme
                }
            } catch (_: Exception) {}
        }
        return AppIconTheme.PURPLE
    }

    /**
     * Ensures that at least one launcher alias is enabled.
     * If all aliases happen to be disabled (e.g. from an earlier broken install),
     * recovers immediately by re-enabling MainActivityDefault.
     */
    fun ensureValidLauncherAlias() {
        val pm = context.packageManager
        var hasEnabledAlias = false

        for (theme in AppIconTheme.entries) {
            val component = getComponentForTheme(theme)
            try {
                val state = pm.getComponentEnabledSetting(component)
                if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                    hasEnabledAlias = true
                    break
                }
                if (theme.isDefault && state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
                    hasEnabledAlias = true
                    break
                }
            } catch (_: Exception) {}
        }

        if (!hasEnabledAlias) {
            Log.w(TAG, "No enabled launcher alias found! Restoring default launcher alias.")
            val defaultComponent = getComponentForTheme(AppIconTheme.PURPLE)
            try {
                pm.setComponentEnabledSetting(
                    defaultComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore default launcher alias", e)
            }
        }
    }
}

package com.lofiq.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.lofiq.core.common.AppIconTheme

val LocalAppIconTheme = compositionLocalOf { AppIconTheme.PURPLE }
val LocalBrandColor = compositionLocalOf { AccentWhite }

private val DarkColorScheme = darkColorScheme(
    primary = AccentWhite,
    onPrimary = BackgroundBlack,
    primaryContainer = SurfaceElevated,
    onPrimaryContainer = TextPrimary,
    secondary = AccentGray,
    onSecondary = TextPrimary,
    background = BackgroundBlack,
    onBackground = TextPrimary,
    surface = SurfaceBlack,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    outline = SurfaceBorder
)

@Composable
fun LofiQTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = BackgroundBlack.toArgb()
            window.navigationBarColor = BackgroundBlack.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

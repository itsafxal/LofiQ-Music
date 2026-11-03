package com.lofiq.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lofiq.R
import com.lofiq.core.common.AppIconTheme
import com.lofiq.ui.theme.LocalAppIconTheme
import com.lofiq.ui.theme.LocalBrandColor

/**
 * LofiQ dynamic branded logo component.
 * Reacts instantly and accurately to the user's custom brand color and [AppIconTheme].
 * Guarantees exact color fidelity with the rest of the application UI.
 */
@Composable
fun LofiQLogo(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    theme: AppIconTheme = LocalAppIconTheme.current,
    customColor: Color? = null,
    asBadge: Boolean = false,
    withGlow: Boolean = false
) {
    if (asBadge) {
        val primary = customColor ?: theme.primaryColor
        val cornerRadius = size * 0.28f
        val boxModifier = if (withGlow) {
            modifier
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(cornerRadius),
                    ambientColor = primary,
                    spotColor = primary.copy(alpha = 0.6f)
                )
        } else {
            modifier
        }

        Box(
            modifier = boxModifier
                .size(size)
                .clip(RoundedCornerShape(cornerRadius))
                .background(primary)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.28f),
                    shape = RoundedCornerShape(cornerRadius)
                ),
            contentAlignment = Alignment.Center
        ) {
            // Subtle top-down specular sheen to give glossy depth without altering the base color hue
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.White.copy(alpha = 0.16f),
                            0.45f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = 0.10f)
                        )
                    )
            )

            Image(
                painter = painterResource(id = R.drawable.app_logo_transparent),
                contentDescription = "LofiQ Logo",
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(size * 0.62f)
            )
        }
    } else {
        // Pure white transparent logo glyph for in-app UI
        Image(
            painter = painterResource(id = R.drawable.app_logo_transparent),
            contentDescription = "LofiQ Logo",
            modifier = modifier.size(size)
        )
    }
}

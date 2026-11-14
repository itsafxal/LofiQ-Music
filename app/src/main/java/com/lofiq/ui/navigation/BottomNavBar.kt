package com.lofiq.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lofiq.ui.haptics.Haptic
import com.lofiq.ui.haptics.rememberHaptics
import com.lofiq.ui.theme.*

enum class NavScreen(val route: String, val title: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Rounded.Home),
    LIBRARY("library", "Library", Icons.AutoMirrored.Rounded.QueueMusic),
    SEARCH("search", "Search", Icons.Rounded.Search),
    PROFILE("profile", "Profile", Icons.Rounded.Person)
}

/**
 * LofiQ Ultra-Smooth Floating Frosted Glass Capsule Navigation Bar.
 *
 * Performance-Optimized Liquid Navigation:
 * 1. Fixed slot-based geometry (zero layout shifts or mutual tab resizing during animation).
 * 2. Draw-phase GPU translation via graphicsLayer (bypasses parent remeasure & relayout loops).
 * 3. Liquid spring sliding pill indicator with specular border and ambient glow.
 * 4. Snappy, zero-overhead execution when Performance Mode is ON.
 */
@Composable
fun BottomNavBar(
    currentRoute: String,
    onNavigate: (NavScreen) -> Unit,
    userAvatarUrl: String? = null,
    isLoggedIn: Boolean = false,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHaptics()
    val isLiquidGlass = isLiquidGlassActive()
    val isPerfMode = LocalPerformanceModeEnabled.current
    val brandColor = LocalBrandColor.current
    val density = LocalDensity.current

    val bottomScreens = remember { listOf(NavScreen.HOME, NavScreen.LIBRARY, NavScreen.SEARCH) }
    val selectedIndex = bottomScreens.indexOfFirst { it.route == currentRoute }.let { if (it >= 0) it else 0 }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .shadow(
                    elevation = if (isLiquidGlass) 24.dp else 16.dp,
                    shape = RoundedCornerShape(36.dp),
                    spotColor = Color.Black.copy(alpha = if (isLiquidGlass) 0.70f else 0.50f)
                )
                .border(
                    width = 1.2.dp,
                    brush = if (isLiquidGlass) LiquidGlassTokens.specularBorder(0.38f)
                    else Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.White.copy(alpha = 0.06f)
                        )
                    ),
                    shape = RoundedCornerShape(36.dp)
                )
                .clip(RoundedCornerShape(36.dp)),
            color = if (isLiquidGlass) Color(0xFF101016).copy(alpha = 0.60f)
            else Color(0xFF141418).copy(alpha = 0.95f)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val totalWidth = maxWidth
                val tabCount = bottomScreens.size
                val slotWidth = totalWidth / tabCount
                val pillWidth = (slotWidth - 8.dp).coerceAtLeast(44.dp)
                val targetXPx = with(density) { (slotWidth * selectedIndex + (slotWidth - pillWidth) / 2).toPx() }

                val animatedXPx by animateFloatAsState(
                    targetValue = targetXPx,
                    animationSpec = if (isPerfMode) {
                        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
                    } else {
                        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                    },
                    label = "liquidTabX"
                )

                // Liquid Sliding Indicator Pill (Draw-Phase GPU Translation: zero recomposition penalty)
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = animatedXPx
                        }
                        .width(pillWidth)
                        .height(48.dp)
                        .then(
                            if (isLiquidGlass) {
                                Modifier.shadow(
                                    elevation = 10.dp,
                                    shape = RoundedCornerShape(24.dp),
                                    spotColor = brandColor.copy(alpha = 0.45f)
                                )
                            } else Modifier
                        )
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            if (isLiquidGlass) brandColor.copy(alpha = 0.22f)
                            else brandColor.copy(alpha = 0.16f)
                        )
                        .border(
                            width = 1.dp,
                            brush = if (isLiquidGlass) LiquidGlassTokens.specularBorder(0.50f)
                            else Brush.verticalGradient(
                                listOf(brandColor.copy(alpha = 0.40f), brandColor.copy(alpha = 0.20f))
                            ),
                            shape = RoundedCornerShape(24.dp)
                        )
                )

                // Row of Equal-Slot Interactive Tabs (Rock-solid layout without measurement thrashing)
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    bottomScreens.forEachIndexed { index, screen ->
                        val isSelected = currentRoute == screen.route

                        val animatedIconTint by animateColorAsState(
                            targetValue = if (isSelected) brandColor else Color.White.copy(alpha = 0.50f),
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "tabTint_$index"
                        )

                        val animatedIconScale by animateFloatAsState(
                            targetValue = if (isSelected && !isPerfMode) 1.10f else 1.0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "tabIconScale_$index"
                        )

                        val tabInteractionSource = remember { MutableInteractionSource() }
                        val isTabFocused by tabInteractionSource.collectIsFocusedAsState()

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .border(
                                    width = if (isTabFocused) 1.5.dp else 0.dp,
                                    color = if (isTabFocused) Color.White.copy(alpha = 0.85f) else Color.Transparent,
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .background(
                                    if (isTabFocused) Color.White.copy(alpha = 0.12f) else Color.Transparent
                                )
                                .clickable(
                                    interactionSource = tabInteractionSource,
                                    indication = null
                                ) {
                                    haptics.play(Haptic.Select)
                                    onNavigate(screen)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (screen == NavScreen.PROFILE && isLoggedIn && !userAvatarUrl.isNullOrBlank()) {
                                    coil.compose.AsyncImage(
                                        model = userAvatarUrl,
                                        contentDescription = screen.title,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .scale(animatedIconScale)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .border(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) brandColor else Color.White.copy(alpha = 0.4f),
                                                shape = androidx.compose.foundation.shape.CircleShape
                                            )
                                    )
                                } else {
                                    Icon(
                                        imageVector = screen.icon,
                                        contentDescription = screen.title,
                                        tint = animatedIconTint,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .scale(animatedIconScale)
                                    )
                                }

                                AnimatedVisibility(
                                    visible = isSelected,
                                    enter = if (isPerfMode) fadeIn(spring(stiffness = Spring.StiffnessHigh))
                                    else fadeIn(tween(180)) + expandHorizontally(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    ),
                                    exit = if (isPerfMode) fadeOut(spring(stiffness = Spring.StiffnessHigh))
                                    else fadeOut(tween(140)) + shrinkHorizontally(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                ) {
                                    Row {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = screen.title,
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = brandColor,
                                                fontSize = 13.sp
                                            ),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

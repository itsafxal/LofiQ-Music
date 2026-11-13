package com.lofiq.ui.components.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lofiq.ui.theme.*

/**
 * Six distinct Liquid Glass Material Presets according to visual hierarchy & elevation.
 */
enum class GlassPreset(
    val blurRadius: Dp,
    val bgAlpha: Float,
    val baseSubstrate: Color,
    val borderAlpha: Float,
    val defaultCornerRadius: Dp,
    val elevation: Dp
) {
    LIGHT(
        blurRadius = 16.dp,
        bgAlpha = 0.45f,
        baseSubstrate = Color(0xFF20202C),
        borderAlpha = 0.35f,
        defaultCornerRadius = 16.dp,
        elevation = 0.dp
    ),
    DARK(
        blurRadius = 20.dp,
        bgAlpha = 0.65f,
        baseSubstrate = Color(0xFF101018),
        borderAlpha = 0.28f,
        defaultCornerRadius = 20.dp,
        elevation = 4.dp
    ),
    FLOATING(
        blurRadius = 24.dp,
        bgAlpha = 0.75f,
        baseSubstrate = Color(0xFF0E0E14),
        borderAlpha = 0.38f,
        defaultCornerRadius = 24.dp,
        elevation = 16.dp
    ),
    ELEVATED(
        blurRadius = 22.dp,
        bgAlpha = 0.72f,
        baseSubstrate = Color(0xFF181824),
        borderAlpha = 0.32f,
        defaultCornerRadius = 20.dp,
        elevation = 8.dp
    ),
    SUBTLE(
        blurRadius = 12.dp,
        bgAlpha = 0.28f,
        baseSubstrate = Color(0xFF1A1A22),
        borderAlpha = 0.18f,
        defaultCornerRadius = 16.dp,
        elevation = 0.dp
    ),
    MODAL(
        blurRadius = 30.dp,
        bgAlpha = 0.88f,
        baseSubstrate = Color(0xFF0B0B10),
        borderAlpha = 0.40f,
        defaultCornerRadius = 28.dp,
        elevation = 24.dp
    )
}

/**
 * Helper generating the directional specular reflection border brush.
 */
fun specularGlassBorder(alpha: Float = 0.28f): Brush {
    return Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = alpha),
            Color.White.copy(alpha = alpha * 0.35f),
            Color.White.copy(alpha = alpha * 0.05f)
        )
    )
}

/**
 * Top-down inner specular sheen brush for glass panels and cards.
 */
fun specularInnerSheen(alpha: Float = 0.08f): Brush {
    return Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = alpha),
            Color.White.copy(alpha = alpha * 0.20f),
            Color.Transparent
        )
    )
}

// =========================================================================
// 1. GlassSurface — The fundamental liquid glass container primitive
// =========================================================================
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    preset: GlassPreset = GlassPreset.DARK,
    shape: Shape = RoundedCornerShape(preset.defaultCornerRadius),
    borderAlpha: Float? = null,
    onClick: (() -> Unit)? = null,
    hasGlow: Boolean = false,
    glowColor: Color = BrandTangerine,
    content: @Composable BoxScope.() -> Unit
) {
    val isGlass = isLiquidGlassActive()
    val effectiveBorderAlpha = borderAlpha ?: preset.borderAlpha

    val shadowModifier = if (preset.elevation > 0.dp) {
        modifier.shadow(
            elevation = if (isGlass) preset.elevation else (preset.elevation * 0.6f),
            shape = shape,
            spotColor = if (hasGlow) glowColor.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.6f),
            ambientColor = if (hasGlow) glowColor.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.3f)
        )
    } else modifier

    val backgroundModifier = if (isGlass) {
        Modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.08f),
                        preset.baseSubstrate.copy(alpha = preset.bgAlpha)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = specularGlassBorder(effectiveBorderAlpha),
                shape = shape
            )
    } else {
        Modifier
            .clip(shape)
            .background(preset.baseSubstrate.copy(alpha = 0.98f))
            .border(
                width = 1.dp,
                color = LiquidGlassTokens.ClassicBorderColor,
                shape = shape
            )
    }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(color = Color.White.copy(alpha = 0.2f)),
            onClick = onClick
        )
    } else Modifier

    Box(
        modifier = shadowModifier
            .then(backgroundModifier)
            .then(clickableModifier)
    ) {
        // Optional subtle inner top sheen for enhanced physical depth
        if (isGlass) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(specularInnerSheen(0.06f))
            )
        }
        content()
    }
}

// =========================================================================
// 2. GlassCard — Standard content and setting card container
// =========================================================================
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    preset: GlassPreset = GlassPreset.DARK,
    shape: Shape = RoundedCornerShape(preset.defaultCornerRadius),
    padding: PaddingValues = PaddingValues(16.dp),
    borderAlpha: Float? = null,
    onClick: (() -> Unit)? = null,
    hasGlow: Boolean = false,
    glowColor: Color = BrandTangerine,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassSurface(
        modifier = modifier,
        preset = preset,
        shape = shape,
        borderAlpha = borderAlpha,
        onClick = onClick,
        hasGlow = hasGlow,
        glowColor = glowColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
        ) {
            content()
        }
    }
}

// =========================================================================
// 3. GlassPanel — Large grouped section container with header slot
// =========================================================================
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
    preset: GlassPreset = GlassPreset.DARK,
    shape: Shape = RoundedCornerShape(24.dp),
    padding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    GlassCard(
        modifier = modifier,
        preset = preset,
        shape = shape,
        padding = padding
    ) {
        if (title != null || icon != null || action != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    if (icon != null) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(BrandTangerine.copy(alpha = 0.18f))
                                .border(1.dp, BrandTangerine.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = BrandTangerine,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column {
                        if (title != null) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    letterSpacing = 0.2.sp
                                )
                            )
                        }
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
                if (action != null) {
                    action()
                }
            }
        }
        content()
    }
}

// =========================================================================
// 4. GlassButton — Interactive button with tactile press animation
// =========================================================================
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPrimary: Boolean = false,
    accentColor: Color = BrandTangerine,
    preset: GlassPreset = if (isPrimary) GlassPreset.LIGHT else GlassPreset.SUBTLE,
    shape: Shape = RoundedCornerShape(16.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "GlassButtonPressScale"
    )

    val isGlass = isLiquidGlassActive()
    val effectiveBg = if (isPrimary) {
        if (isGlass) accentColor.copy(alpha = 0.85f) else accentColor
    } else {
        if (isGlass) preset.baseSubstrate.copy(alpha = preset.bgAlpha) else LiquidGlassTokens.ClassicCard
    }

    val borderStroke = if (isPrimary) {
        BorderStroke(1.dp, Color.White.copy(alpha = 0.45f))
    } else {
        if (isGlass) BorderStroke(1.dp, specularGlassBorder(preset.borderAlpha))
        else BorderStroke(1.dp, LiquidGlassTokens.ClassicBorderColor)
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = effectiveBg,
        border = borderStroke,
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = if (isPrimary) 8.dp else 0.dp,
                shape = shape,
                spotColor = accentColor.copy(alpha = 0.5f)
            ),
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            content()
        }
    }
}

// =========================================================================
// 5. GlassIconButton — Circular or squircle icon button with tactile feedback
// =========================================================================
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    enabled: Boolean = true,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    tint: Color = TextPrimary,
    shape: Shape = CircleShape,
    preset: GlassPreset = GlassPreset.SUBTLE
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "GlassIconButtonPressScale"
    )

    GlassSurface(
        modifier = modifier
            .size(size)
            .scale(scale),
        preset = preset,
        shape = shape,
        onClick = if (enabled) onClick else null
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) tint else tint.copy(alpha = 0.4f),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

// =========================================================================
// 6. GlassNavigationSurface — Floating capsule navigation surface
// =========================================================================
@Composable
fun GlassNavigationSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(36.dp),
    content: @Composable BoxScope.() -> Unit
) {
    GlassSurface(
        modifier = modifier,
        preset = GlassPreset.FLOATING,
        shape = shape,
        borderAlpha = 0.38f,
        hasGlow = true,
        glowColor = Color.Black
    ) {
        content()
    }
}

// =========================================================================
// 7. GlassDialog — Premium modal alert dialog with translucent backdrop
// =========================================================================
@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: ImageVector? = null,
    title: String? = null,
    shape: Shape = RoundedCornerShape(28.dp),
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassSurface(
                modifier = modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp),
                preset = GlassPreset.MODAL,
                shape = shape,
                borderAlpha = 0.42f,
                hasGlow = true,
                glowColor = Color.Black
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    if (icon != null) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(BrandTangerine.copy(alpha = 0.16f))
                                .border(1.dp, BrandTangerine.copy(alpha = 0.35f), CircleShape)
                                .align(Alignment.CenterHorizontally),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = BrandTangerine,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (title != null) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 18.sp
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    content()

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (dismissButton != null) {
                            dismissButton()
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        confirmButton()
                    }
                }
            }
        }
    }
}

// =========================================================================
// 8. GlassBottomSheet — Modal bottom sheet container with specular top rim
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val isGlass = isLiquidGlassActive()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = if (isGlass) GlassPreset.MODAL.baseSubstrate.copy(alpha = GlassPreset.MODAL.bgAlpha) else BackgroundBlack,
        shape = shape,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(44.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (isGlass) Brush.horizontalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.White.copy(alpha = 0.55f),
                                Color.White.copy(alpha = 0.15f)
                            )
                        ) else Brush.horizontalGradient(
                            listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.30f))
                        )
                    )
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            content()
        }
    }
}

// =========================================================================
// 9. GlassSegmentedControl — Multi-segment selector with sliding glass pill
// =========================================================================
@Composable
fun GlassSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isGlass = isLiquidGlassActive()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isGlass) GlassPreset.SUBTLE.baseSubstrate.copy(alpha = 0.40f) else SurfaceCard)
            .border(
                1.dp,
                if (isGlass) specularGlassBorder(0.20f) else SolidColor(LiquidGlassTokens.ClassicBorderColor),
                RoundedCornerShape(14.dp)
            )
            .padding(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, title ->
                val isSelected = index == selectedIndex
                val animatedBg by animateColorAsState(
                    targetValue = if (isSelected) {
                        if (isGlass) Color.White.copy(alpha = 0.18f) else SurfaceElevated
                    } else Color.Transparent,
                    animationSpec = tween(180),
                    label = "SegmentedControlBg"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .background(animatedBg)
                        .then(
                            if (isSelected && isGlass) {
                                Modifier.border(1.dp, specularGlassBorder(0.35f), RoundedCornerShape(10.dp))
                            } else Modifier
                        )
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) TextPrimary else TextSecondary,
                            fontSize = 13.sp
                        )
                    )
                }
            }
        }
    }
}

// =========================================================================
// 10. GlassMenu — Floating contextual dropdown menu surface
// =========================================================================
@Composable
fun GlassMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(GlassPreset.ELEVATED.baseSubstrate.copy(alpha = 0.92f))
            .border(1.dp, specularGlassBorder(0.35f), RoundedCornerShape(18.dp))
            .padding(vertical = 6.dp)
    ) {
        content()
    }
}

// =========================================================================
// 11. GlassTooltip — Compact floating pill tooltip
// =========================================================================
@Composable
fun GlassTooltip(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(GlassPreset.SUBTLE.baseSubstrate.copy(alpha = 0.85f))
            .border(1.dp, specularGlassBorder(0.25f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp
            )
        )
    }
}

// =========================================================================
// 12. GlassInput — Translucent text input field with focused specular border
// =========================================================================
@Composable
fun GlassInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    shape: Shape = RoundedCornerShape(16.dp)
) {
    val isGlass = isLiquidGlassActive()

    TextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = singleLine,
        placeholder = {
            Text(
                text = placeholder,
                color = TextTertiary,
                fontSize = 14.sp
            )
        },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon ?: {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Clear text",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = if (isGlass) GlassPreset.SUBTLE.baseSubstrate.copy(alpha = 0.50f) else SurfaceCard,
            unfocusedContainerColor = if (isGlass) GlassPreset.SUBTLE.baseSubstrate.copy(alpha = 0.35f) else SurfaceCard,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = BrandTangerine
        ),
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(
                width = 1.dp,
                brush = if (isGlass) specularGlassBorder(0.30f) else SolidColor(LiquidGlassTokens.ClassicBorderColor),
                shape = shape
            )
    )
}

// =========================================================================
// 13. GlassSlider — Precision audio slider with glowing Tangerine track
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    accentColor: Color = BrandTangerine
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        enabled = enabled,
        modifier = modifier,
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = accentColor,
            inactiveTrackColor = Color.White.copy(alpha = 0.12f),
            disabledThumbColor = Color.White.copy(alpha = 0.4f),
            disabledActiveTrackColor = accentColor.copy(alpha = 0.4f)
        )
    )
}

// =========================================================================
// 14. GlassSwitch — Smooth toggle switch with frosted glass pill track
// =========================================================================
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color = BrandTangerine
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = activeColor,
            uncheckedThumbColor = TextSecondary,
            uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
            uncheckedBorderColor = Color.White.copy(alpha = 0.18f)
        )
    )
}

package com.lofiq.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lofiq.ui.components.glass.GlassButton
import com.lofiq.ui.components.glass.GlassDialog
import com.lofiq.ui.components.glass.GlassPreset
import com.lofiq.ui.components.glass.GlassSlider
import com.lofiq.ui.components.glass.specularGlassBorder
import com.lofiq.ui.haptics.Haptic
import com.lofiq.ui.haptics.rememberHaptics
import com.lofiq.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun SeamlessPlayDialog(
    currentSeconds: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf(currentSeconds.toFloat().coerceIn(0f, 12f)) }
    val haptics = rememberHaptics()
    val isGlass = isLiquidGlassActive()

    GlassDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Rounded.AutoAwesome,
        title = "Seamless Play & Crossfade",
        confirmButton = {
            GlassButton(
                isPrimary = true,
                accentColor = BrandTangerine,
                onClick = {
                    haptics.play(Haptic.Tap)
                    onSave(sliderValue.roundToInt())
                    onDismiss()
                }
            ) {
                Text(
                    text = "Save",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        },
        dismissButton = {
            GlassButton(
                isPrimary = false,
                onClick = onDismiss
            ) {
                Text(
                    text = "Cancel",
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Allows consecutive songs to smoothly crossfade into each other, eliminating abrupt silence and buffering gaps.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            )

            // Live value badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isGlass) GlassPreset.SUBTLE.baseSubstrate.copy(alpha = 0.5f) else SurfaceCard)
                    .border(1.dp, specularGlassBorder(0.25f), RoundedCornerShape(14.dp))
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                val currentInt = sliderValue.roundToInt()
                Text(
                    text = if (currentInt == 0) "Disabled (Gapless Only)" else "$currentInt Seconds Overlap",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = BrandTangerine,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                )
            }

            // Slider (0s to 12s)
            Column {
                GlassSlider(
                    value = sliderValue,
                    onValueChange = {
                        val rounded = it.roundToInt().toFloat()
                        if (rounded != sliderValue) {
                            haptics.play(Haptic.Tick)
                        }
                        sliderValue = rounded
                    },
                    valueRange = 0f..12f,
                    accentColor = BrandTangerine
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Off", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                    Text("4s", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                    Text("8s (Default)", style = MaterialTheme.typography.labelSmall.copy(color = BrandVanilla, fontWeight = FontWeight.SemiBold))
                    Text("12s", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                }
            }

            // Preset Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    0 to "Off",
                    4 to "4s",
                    8 to "8s",
                    12 to "12s"
                ).forEach { (presetSec, label) ->
                    val isSelected = sliderValue.roundToInt() == presetSec
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) BrandTangerine.copy(alpha = 0.85f)
                                else if (isGlass) GlassPreset.SUBTLE.baseSubstrate.copy(alpha = 0.35f)
                                else SurfaceCard
                            )
                            .border(
                                1.dp,
                                if (isSelected) specularGlassBorder(0.45f) else specularGlassBorder(0.18f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                haptics.play(Haptic.Tap)
                                sliderValue = presetSec.toFloat()
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (isSelected) Color.White else TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }
    }
}

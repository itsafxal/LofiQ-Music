package com.lofiq.feature.equalizer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lofiq.feature.equalizer.components.EqCurveVisualizer
import com.lofiq.feature.equalizer.components.RotaryKnob
import com.lofiq.feature.equalizer.components.VerticalEqFader
import com.lofiq.ui.haptics.Haptic
import com.lofiq.ui.haptics.rememberHaptics
import com.lofiq.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerBottomSheet(
    effectsController: AudioEffectsController,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val presetScrollState = rememberScrollState()
    val haptics = rememberHaptics()

    val neonCyan = Color(0xFF00E5FF)
    val neonPurple = Color(0xFF8C52FF)
    val cardBackground = Color(0xFF131318)
    val cardBorder = Color(0xFF26262F)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0C0C10),
        contentColor = AccentWhite,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.35f))
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with Power Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Equalizer & DSP",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = AccentWhite,
                            fontSize = 19.sp
                        )
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (effectsController.isEnabled) neonCyan else Color.White.copy(alpha = 0.2f)
                            )
                    )
                }

                Switch(
                    checked = effectsController.isEnabled,
                    onCheckedChange = {
                        haptics.play(if (it) Haptic.ToggleOn else Haptic.ToggleOff)
                        effectsController.toggleEnabled(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AccentWhite,
                        checkedTrackColor = neonCyan,
                        uncheckedThumbColor = TextTertiary,
                        uncheckedTrackColor = SurfaceElevated
                    )
                )
            }

            // Presets Horizontal Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(presetScrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                effectsController.presets.forEachIndexed { index, preset ->
                    val isSelected = effectsController.selectedPresetIndex == index
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) neonCyan.copy(alpha = 0.16f)
                                else cardBackground
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) neonCyan else cardBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                haptics.play(Haptic.Select)
                                effectsController.applyPreset(index)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = preset.name,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = if (isSelected) AccentWhite else TextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.5.sp
                            )
                        )
                    }
                }
            }

            // Multi-Band Equalizer Card
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EqCurveVisualizer(
                        bandLevels = effectsController.bandLevels.toList(),
                        minLevel = effectsController.minLevel,
                        maxLevel = effectsController.maxLevel,
                        enabled = effectsController.isEnabled,
                        activeColor = neonCyan,
                        secondaryColor = neonPurple
                    )

                    // Vertical Faders
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Side dB Scale
                        Column(
                            modifier = Modifier
                                .height(175.dp)
                                .padding(vertical = 10.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.End
                        ) {
                            Text("+15", color = TextTertiary, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            Text("0 dB", color = if (effectsController.isEnabled) neonCyan.copy(alpha = 0.8f) else TextTertiary, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            Text("-15", color = TextTertiary, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                        }

                        val bandsCount = effectsController.bandFrequencies.size
                        for (i in 0 until bandsCount) {
                            val freq = effectsController.bandFrequencies[i]
                            val freqLabel = if (freq >= 1000) {
                                val khz = freq / 1000f
                                if (khz % 1f == 0f) "${khz.toInt()}k" else "${khz}k"
                            } else {
                                "$freq"
                            }

                            val minVal = effectsController.minLevel.toFloat()
                            val maxVal = effectsController.maxLevel.toFloat()
                            val currentLevel = effectsController.bandLevels.getOrElse(i) { 0 }
                            val gainDb = (currentLevel / 100)

                            val progress = if (maxVal > minVal) {
                                ((currentLevel - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
                            } else 0.5f

                            VerticalEqFader(
                                value = progress,
                                onValueChange = { fraction ->
                                    val newLevel = (minVal + fraction * (maxVal - minVal)).toInt()
                                    effectsController.setBandLevel(i, newLevel)
                                },
                                freqLabel = freqLabel,
                                gainDb = gainDb,
                                enabled = effectsController.isEnabled,
                                activeColor = neonCyan,
                                secondaryColor = neonPurple,
                                height = 175.dp
                            )
                        }
                    }
                }
            }

            // Dual Rotary Knobs Card
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RotaryKnob(
                        value = effectsController.bassBoostStrength,
                        onValueChange = { effectsController.setBassBoost(it) },
                        title = "Bass Boost",
                        subtitle = "${(effectsController.bassBoostStrength * 100).toInt()}%",
                        size = 115.dp,
                        enabled = effectsController.isEnabled,
                        activeColor = neonCyan,
                        secondaryColor = neonPurple
                    )

                    RotaryKnob(
                        value = effectsController.virtualizerStrength,
                        onValueChange = { effectsController.setVirtualizer(it) },
                        title = "3D Surround",
                        subtitle = "${(effectsController.virtualizerStrength * 100).toInt()}%",
                        size = 115.dp,
                        enabled = effectsController.isEnabled,
                        activeColor = neonPurple,
                        secondaryColor = neonCyan
                    )
                }
            }
        }
    }
}

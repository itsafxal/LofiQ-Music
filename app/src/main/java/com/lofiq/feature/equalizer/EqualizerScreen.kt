package com.lofiq.feature.equalizer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
fun EqualizerScreen(
    effectsController: AudioEffectsController,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val presetScrollState = rememberScrollState()
    val haptics = rememberHaptics()

    val neonCyan = Color(0xFF00E5FF)
    val neonPurple = Color(0xFF8C52FF)
    val cardBackground = Color(0xFF131318)
    val cardBorder = Color(0xFF26262F)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Equalizer & DSP",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = AccentWhite,
                                letterSpacing = 0.2.sp
                            )
                        )

                        // Studio Power Status LED indicator
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (effectsController.isEnabled) neonCyan else Color.White.copy(alpha = 0.2f)
                                )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = AccentWhite
                        )
                    }
                },
                actions = {
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
                    Spacer(modifier = Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundBlack
                )
            )
        },
        containerColor = BackgroundBlack,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 1. Audio Presets Carousel ────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PRESETS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    )
                    if (effectsController.selectedPresetIndex != 0) {
                        Text(
                            text = "Reset",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = neonCyan,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    haptics.play(Haptic.Select)
                                    effectsController.applyPreset(0)
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(presetScrollState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    effectsController.presets.forEachIndexed { index, preset ->
                        val isSelected = effectsController.selectedPresetIndex == index
                        val activeBorder = if (isSelected) neonCyan else Color.Transparent

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
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.GraphicEq,
                                        contentDescription = null,
                                        tint = neonCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = if (isSelected) AccentWhite else TextSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // ── 2. Master Studio Multi-Band Equalizer Rack ───────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Rack Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                tint = if (effectsController.isEnabled) neonCyan else TextTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "STUDIO EQUALIZER",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = AccentWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    letterSpacing = 1.sp
                                )
                            )
                        }

                        Text(
                            text = "${effectsController.bandFrequencies.size} BANDS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (effectsController.isEnabled) neonCyan else TextTertiary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }

                    // Dynamic EQ Curve Visualizer
                    EqCurveVisualizer(
                        bandLevels = effectsController.bandLevels.toList(),
                        minLevel = effectsController.minLevel,
                        maxLevel = effectsController.maxLevel,
                        enabled = effectsController.isEnabled,
                        activeColor = neonCyan,
                        secondaryColor = neonPurple
                    )

                    // Vertical Fader Bank Rack
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Side dB Scale Reference Ticks
                        Column(
                            modifier = Modifier
                                .height(190.dp)
                                .padding(vertical = 12.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "+15",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextTertiary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "0 dB",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (effectsController.isEnabled) neonCyan.copy(alpha = 0.8f) else TextTertiary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "-15",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextTertiary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        // Multi-Band Vertical Faders
                        val bandsCount = effectsController.bandFrequencies.size
                        for (i in 0 until bandsCount) {
                            val freq = effectsController.bandFrequencies[i]
                            val freqLabel = if (freq >= 1000) {
                                val khz = freq / 1000f
                                if (khz % 1f == 0f) "${khz.toInt()} kHz" else "$khz kHz"
                            } else {
                                "$freq Hz"
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
                                height = 190.dp
                            )
                        }
                    }
                }
            }

            // ── 3. Acoustic Enhancements Rotary Knobs ────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SurroundSound,
                            contentDescription = null,
                            tint = if (effectsController.isEnabled) neonPurple else TextTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "ACOUSTIC ENHANCEMENTS",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = AccentWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                letterSpacing = 1.sp
                            )
                        )
                    }

                    // Dual Rotary Dials Side-by-Side
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Bass Booster Rotary Knob
                        RotaryKnob(
                            value = effectsController.bassBoostStrength,
                            onValueChange = { effectsController.setBassBoost(it) },
                            title = "Bass Boost",
                            subtitle = "${(effectsController.bassBoostStrength * 100).toInt()}%",
                            size = 125.dp,
                            enabled = effectsController.isEnabled,
                            activeColor = neonCyan,
                            secondaryColor = neonPurple
                        )

                        // 2. 3D Spatial Virtualizer Rotary Knob
                        RotaryKnob(
                            value = effectsController.virtualizerStrength,
                            onValueChange = { effectsController.setVirtualizer(it) },
                            title = "3D Surround",
                            subtitle = "${(effectsController.virtualizerStrength * 100).toInt()}%",
                            size = 125.dp,
                            enabled = effectsController.isEnabled,
                            activeColor = neonPurple,
                            secondaryColor = neonCyan
                        )
                    }
                }
            }

            // Bottom clearance for floating mini-player and system navigation bar
            Spacer(modifier = Modifier.height(120.dp))
        }
    }
}

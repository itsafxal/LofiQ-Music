package com.lofiq.feature.settings

import androidx.compose.foundation.BorderStroke
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
import com.lofiq.ui.haptics.Haptic
import com.lofiq.ui.haptics.rememberHaptics
import com.lofiq.ui.theme.AccentWhite
import com.lofiq.ui.theme.TextSecondary
import kotlin.math.roundToInt

private val MonochromeCard = Color(0xFF0F0F0F)
private val MonochromeSubtle = Color(0xFF181818)
private val MonochromeBorder = Color.White.copy(alpha = 0.08f)

@Composable
fun SeamlessPlayDialog(
    currentSeconds: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf(currentSeconds.toFloat().coerceIn(0f, 12f)) }
    val haptics = rememberHaptics()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MonochromeSubtle),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "Seamless Play",
                        tint = AccentWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "Seamless Play",
                    color = AccentWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
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
                        .clip(RoundedCornerShape(12.dp))
                        .background(MonochromeSubtle)
                        .border(BorderStroke(1.dp, MonochromeBorder), RoundedCornerShape(12.dp))
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val currentInt = sliderValue.roundToInt()
                    Text(
                        text = if (currentInt == 0) "Disabled (Gapless Only)" else "$currentInt Seconds Overlap",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = AccentWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    )
                }

                // Slider (0s to 12s)
                Column {
                    Slider(
                        value = sliderValue,
                        onValueChange = {
                            val rounded = it.roundToInt().toFloat()
                            if (rounded != sliderValue) {
                                haptics.play(Haptic.Tick)
                            }
                            sliderValue = rounded
                        },
                        valueRange = 0f..12f,
                        steps = 11,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentWhite,
                            activeTrackColor = AccentWhite,
                            inactiveTrackColor = MonochromeSubtle,
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        )
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Off", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                        Text("4s", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                        Text("8s (Default)", style = MaterialTheme.typography.labelSmall.copy(color = AccentWhite, fontWeight = FontWeight.SemiBold))
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
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) AccentWhite else MonochromeSubtle)
                                .clickable {
                                    haptics.play(Haptic.Tap)
                                    sliderValue = presetSec.toFloat()
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isSelected) Color.Black else AccentWhite,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptics.play(Haptic.Tap)
                    onSave(sliderValue.roundToInt())
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentWhite,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "Save",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "Cancel",
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        },
        containerColor = MonochromeCard,
        shape = RoundedCornerShape(24.dp)
    )
}

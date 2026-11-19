package com.lofiq.feature.player.pipeline

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lofiq.core.audio.pipeline.*
import com.lofiq.ui.components.glass.*
import com.lofiq.ui.theme.*

/**
 * Premium Live Audio Pipeline Inspector dialog inspired by BitChord.
 *
 * Visualizes the 5-stage signal path from stream input to hardware audio output:
 * 1. Track Info (Source, Codec, Bitrate, Channels)
 * 2. Decoder (MediaCodec name, Hardware/Software classification, PCM format)
 * 3. Resampler (Input vs Native AudioTrack output rate)
 * 4. DSP & Effects (Equalizer, Bass Boost, Virtualizer, Speed)
 * 5. Audio Output (Active route, Device name, Hardware channels)
 */
@Composable
fun AudioPipelineDialog(
    pipelineInfo: AudioPipelineInfo,
    onDismissRequest: () -> Unit,
    onOpenEqualizer: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Animated luminous pulse moving along the vertical signal path
    val infiniteTransition = rememberInfiniteTransition(label = "PipelinePulseTransition")
    val pulseFraction by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SignalPulseFraction"
    )

    // Pulsing green beacon for live active playback
    val liveBeaconAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LiveBeaconAlpha"
    )

    val neonAccent = Color(0xFF38BDF8) // Sky Blue
    val jadeGreen = Color(0xFF34D399)  // Emerald

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF0F0E17).copy(alpha = 0.94f))
                .border(1.dp, specularGlassBorder(0.35f), RoundedCornerShape(28.dp))
                .shadow(24.dp, RoundedCornerShape(28.dp), spotColor = Color(0xFF8B5CF6).copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // ==========================================
                // 1. DIALOG HEADER & LIVE BEACON
                // ==========================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Audio Pipeline",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 19.sp,
                                    letterSpacing = 0.2.sp
                                )
                            )

                            // Live / Paused status pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (pipelineInfo.isLiveActive) jadeGreen.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f))
                                    .border(1.dp, if (pipelineInfo.isLiveActive) jadeGreen.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (pipelineInfo.isLiveActive) jadeGreen.copy(alpha = liveBeaconAlpha) else Color.Gray)
                                    )
                                    Text(
                                        text = if (pipelineInfo.isLiveActive) "Live • Active" else "Paused",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.5.sp,
                                            color = if (pipelineInfo.isLiveActive) jadeGreen else TextSecondary
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Live signal path, decoder to output",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        )
                    }

                    GlassIconButton(
                        onClick = onDismissRequest,
                        icon = Icons.Rounded.Close,
                        size = 36.dp,
                        iconSize = 18.dp,
                        preset = GlassPreset.SUBTLE
                    )
                }

                // Current Song Quick Banner
                if (pipelineInfo.trackTitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E1B2E).copy(alpha = 0.6f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pipelineInfo.trackTitle,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                        fontSize = 13.sp
                                    ),
                                    maxLines = 1
                                )
                                Text(
                                    text = pipelineInfo.trackArtist.ifBlank { "LofiQ Music Player" },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextSecondary,
                                        fontSize = 11.5.sp
                                    ),
                                    maxLines = 1
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(neonAccent.copy(alpha = 0.15f))
                                    .padding(horizontal = 7.dp, vertical = 2.5.dp)
                            ) {
                                Text(
                                    text = pipelineInfo.trackInfo.qualityBadge,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = neonAccent
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ==========================================
                // 2. VERTICAL SIGNAL-FLOW TIMELINE
                // ==========================================
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        // Left vertical connector line with moving luminous pulse
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize().padding(top = 18.dp, bottom = 18.dp)) {
                                val lineX = size.width / 2f
                                val startY = 0f
                                val endY = size.height

                                // Static background spine
                                drawLine(
                                    color = Color.White.copy(alpha = 0.14f),
                                    start = Offset(lineX, startY),
                                    end = Offset(lineX, endY),
                                    strokeWidth = 2.dp.toPx()
                                )

                                // Moving luminous signal pulse
                                val pulseY = startY + (endY - startY) * pulseFraction
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            neonAccent.copy(alpha = 0.85f),
                                            neonAccent.copy(alpha = 0.35f),
                                            Color.Transparent
                                        ),
                                        center = Offset(lineX, pulseY),
                                        radius = 16.dp.toPx()
                                    ),
                                    radius = 16.dp.toPx(),
                                    center = Offset(lineX, pulseY)
                                )

                                drawCircle(
                                    color = Color.White,
                                    radius = 3.dp.toPx(),
                                    center = Offset(lineX, pulseY)
                                )
                            }
                        }

                        // Right stage cards column
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // STAGE 1: TRACK INFO
                            PipelineStageCard(
                                stageNumber = "1",
                                title = "Track Info",
                                subtitle = pipelineInfo.trackInfo.sourceProvider,
                                icon = Icons.Rounded.Audiotrack,
                                accentColor = neonAccent,
                                items = listOf(
                                    "Codec" to pipelineInfo.trackInfo.codec,
                                    "Bitrate" to pipelineInfo.trackInfo.formattedBitrate,
                                    "Sample Rate" to pipelineInfo.trackInfo.formattedSampleRate,
                                    "Channels" to pipelineInfo.trackInfo.formattedChannels,
                                    "Container" to pipelineInfo.trackInfo.container,
                                    "Type" to pipelineInfo.trackInfo.compressionType
                                )
                            )

                            // STAGE 2: DECODER
                            PipelineStageCard(
                                stageNumber = "2",
                                title = "Decoder",
                                subtitle = pipelineInfo.decoder.decoderType,
                                icon = Icons.Rounded.Memory,
                                accentColor = Color(0xFFA78BFA), // Lavender
                                items = listOf(
                                    "Decoder Name" to pipelineInfo.decoder.decoderName,
                                    "Input MIME" to pipelineInfo.decoder.inputMime,
                                    "PCM Format" to pipelineInfo.decoder.outputPcmEncoding,
                                    "Decoded Rate" to pipelineInfo.decoder.formattedSampleRate,
                                    "Hardware Accel" to when (pipelineInfo.decoder.isHardwareAccelerated) {
                                        true -> "Yes (Hardware MediaCodec)"
                                        false -> "No (Software MediaCodec)"
                                        null -> "Standard Platform"
                                    }
                                )
                            )

                            // STAGE 3: RESAMPLER
                            PipelineStageCard(
                                stageNumber = "3",
                                title = "Resampler",
                                subtitle = pipelineInfo.resampler.statusText,
                                icon = Icons.Rounded.Transform,
                                accentColor = Color(0xFFFBBF24), // Amber
                                items = listOf(
                                    "Conversion" to pipelineInfo.resampler.formattedConversion,
                                    "Resampling Active" to if (pipelineInfo.resampler.isResamplingRequired) "Yes" else "No (Bit-rate Match)",
                                    "Subsystem" to pipelineInfo.resampler.implementationDetails
                                )
                            )

                            // STAGE 4: DSP & EFFECTS
                            PipelineStageCard(
                                stageNumber = "4",
                                title = "DSP & Effects",
                                subtitle = if (pipelineInfo.dsp.hasActiveEffects) "Active Audio Effects" else "Flat / Bypassed",
                                icon = Icons.Rounded.GraphicEq,
                                accentColor = Color(0xFFF472B6), // Pink
                                items = listOf(
                                    "Equalizer" to if (pipelineInfo.dsp.isEqEnabled) "Enabled (${pipelineInfo.dsp.activePresetName ?: "Custom"})" else "Bypassed",
                                    "Bass Boost" to if (pipelineInfo.dsp.bassBoostPercent > 0) "+${pipelineInfo.dsp.bassBoostPercent}%" else "Disabled",
                                    "3D Virtualizer" to if (pipelineInfo.dsp.virtualizerPercent > 0) "+${pipelineInfo.dsp.virtualizerPercent}%" else "Disabled",
                                    "Playback Speed" to "${pipelineInfo.dsp.playbackSpeed}x",
                                    "DSP Summary" to pipelineInfo.dsp.activeEffectsSummary
                                )
                            )

                            // STAGE 5: AUDIO OUTPUT
                            PipelineStageCard(
                                stageNumber = "5",
                                title = "Audio Output",
                                subtitle = pipelineInfo.output.routeType,
                                icon = if (pipelineInfo.output.isBluetooth) Icons.Rounded.BluetoothAudio
                                       else if (pipelineInfo.output.isHeadphones) Icons.Rounded.Headphones
                                       else Icons.Rounded.PhoneAndroid,
                                accentColor = jadeGreen,
                                items = listOf(
                                    "Active Route" to pipelineInfo.output.routeType,
                                    "Device Name" to pipelineInfo.output.deviceName,
                                    "Native HW Rate" to pipelineInfo.output.formattedSampleRate,
                                    "Channel Array" to pipelineInfo.output.channelConfig,
                                    "Hardware Encoding" to pipelineInfo.output.hardwareEncoding
                                )
                            )
                        }
                    }
                }

                // ==========================================
                // 3. DIALOG FOOTER & QUICK DSP NAVIGATION
                // ==========================================
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onOpenEqualizer != null) {
                        GlassButton(
                            onClick = {
                                onDismissRequest()
                                onOpenEqualizer()
                            },
                            preset = GlassPreset.SUBTLE,
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = TextSecondary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "DSP / Equalizer",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }
                    }

                    GlassButton(
                        onClick = onDismissRequest,
                        isPrimary = true,
                        accentColor = neonAccent,
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text(
                            text = "Done",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}

/**
 * Clean glass node card displaying detailed parameters for an individual audio pipeline stage.
 */
@Composable
private fun PipelineStageCard(
    stageNumber: String,
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    items: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF161524).copy(alpha = 0.85f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row: Node icon, Stage number & title, Subtitle pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.18f))
                            .border(1.dp, accentColor.copy(alpha = 0.40f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "$stageNumber. $title",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.5.sp,
                                color = TextSecondary
                            ),
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Technical Parameter Grid
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F0E17).copy(alpha = 0.65f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                items.forEach { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary,
                                fontSize = 11.5.sp
                            )
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (value == "—" || value.startsWith("Unknown")) TextSecondary else TextPrimary,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

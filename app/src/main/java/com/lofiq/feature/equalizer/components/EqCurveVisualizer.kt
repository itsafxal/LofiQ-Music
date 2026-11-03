package com.lofiq.feature.equalizer.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lofiq.ui.theme.AccentWhite
import com.lofiq.ui.theme.TextTertiary

/**
 * Real-time dynamic response curve visualizer for the equalizer.
 * Shows smooth spline interpolation across all frequency bands with gradient fill and studio gridlines.
 */
@Composable
fun EqCurveVisualizer(
    bandLevels: List<Int>, // in millibels or raw levels
    minLevel: Int,
    maxLevel: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = Color(0xFF00E5FF),
    secondaryColor: Color = Color(0xFF7C4DFF)
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0A0A0D))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val midY = height / 2f

            val curveAlpha = if (enabled) 1f else 0.25f

            // 1. Draw Grid Lines (+15 dB, 0 dB, -15 dB)
            val gridColor = Color.White.copy(alpha = 0.06f * curveAlpha)
            val centerGridColor = Color.White.copy(alpha = 0.15f * curveAlpha)

            // Top guide line
            drawLine(
                color = gridColor,
                start = Offset(0f, height * 0.15f),
                end = Offset(width, height * 0.15f),
                strokeWidth = 1.dp.toPx()
            )
            // Center 0 dB guide line
            drawLine(
                color = centerGridColor,
                start = Offset(0f, midY),
                end = Offset(width, midY),
                strokeWidth = 1.2.dp.toPx()
            )
            // Bottom guide line
            drawLine(
                color = gridColor,
                start = Offset(0f, height * 0.85f),
                end = Offset(width, height * 0.85f),
                strokeWidth = 1.dp.toPx()
            )

            // 2. Calculate Control Points for the Spline
            val numBands = bandLevels.size.coerceAtLeast(1)
            val points = mutableListOf<Offset>()

            // Add leading edge point
            val firstLevel = bandLevels.firstOrNull() ?: 0
            val firstNorm = if (maxLevel > minLevel) {
                1f - ((firstLevel - minLevel).toFloat() / (maxLevel - minLevel)).coerceIn(0f, 1f)
            } else 0.5f
            points.add(Offset(0f, firstNorm * (height * 0.7f) + (height * 0.15f)))

            for (i in 0 until numBands) {
                val level = bandLevels.getOrElse(i) { 0 }
                val norm = if (maxLevel > minLevel) {
                    1f - ((level - minLevel).toFloat() / (maxLevel - minLevel)).coerceIn(0f, 1f)
                } else 0.5f
                val x = (i + 0.5f) * (width / numBands)
                val y = norm * (height * 0.7f) + (height * 0.15f)
                points.add(Offset(x, y))
            }

            // Add trailing edge point
            val lastLevel = bandLevels.lastOrNull() ?: 0
            val lastNorm = if (maxLevel > minLevel) {
                1f - ((lastLevel - minLevel).toFloat() / (maxLevel - minLevel)).coerceIn(0f, 1f)
            } else 0.5f
            points.add(Offset(width, lastNorm * (height * 0.7f) + (height * 0.15f)))

            // 3. Build Smooth Cubic Bezier Path
            val path = Path()
            val fillPath = Path()

            if (points.isNotEmpty()) {
                path.moveTo(points.first().x, points.first().y)
                fillPath.moveTo(0f, height)
                fillPath.lineTo(points.first().x, points.first().y)

                for (i in 0 until points.size - 1) {
                    val p0 = if (i > 0) points[i - 1] else points[i]
                    val p1 = points[i]
                    val p2 = points[i + 1]
                    val p3 = if (i + 2 < points.size) points[i + 2] else p2

                    val cp1x = p1.x + (p2.x - p0.x) / 6f
                    val cp1y = p1.y + (p2.y - p0.y) / 6f
                    val cp2x = p2.x - (p3.x - p1.x) / 6f
                    val cp2y = p2.y - (p3.y - p1.y) / 6f

                    path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                    fillPath.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                }

                fillPath.lineTo(width, height)
                fillPath.close()

                // Draw Gradient Area Fill under curve
                if (enabled) {
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                activeColor.copy(alpha = 0.25f),
                                secondaryColor.copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = height
                        )
                    )
                }

                // Draw Stroke Curve
                drawPath(
                    path = path,
                    brush = Brush.horizontalGradient(
                        colors = if (enabled) listOf(activeColor, secondaryColor, activeColor)
                        else listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.25f))
                    ),
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )

                // Draw band node points
                for (i in 1..numBands) {
                    val pt = points[i]
                    drawCircle(
                        color = if (enabled) activeColor else Color.White.copy(alpha = 0.3f),
                        radius = 4.dp.toPx(),
                        center = pt
                    )
                    if (enabled) {
                        drawCircle(
                            color = Color(0xFF0A0A0D),
                            radius = 2.dp.toPx(),
                            center = pt
                        )
                    }
                }
            }
        }

        // Top labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "RESPONSE SPECTRUM",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (enabled) activeColor.copy(alpha = 0.7f) else TextTertiary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp
                )
            )
            Text(
                text = "+15 dB / -15 dB",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = TextTertiary,
                    fontSize = 9.sp
                )
            )
        }
    }
}

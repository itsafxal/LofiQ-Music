package com.lofiq.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * LofiQ hairline scrubber: a sleek capsule with no bulky thumb knob,
 * which dynamically thickens under the finger and smoothly settles back when released.
 * Accurately seeks on tap and horizontal drag.
 */
@Composable
fun ThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    idleHeight: Dp = 5.dp,
    activeHeight: Dp = 9.dp,
    activeColor: Color = Color.White.copy(alpha = 0.95f),
    inactiveColor: Color = Color.White.copy(alpha = 0.25f),
) {
    var dragging by remember { mutableStateOf(false) }
    var internalFraction by remember(value) { mutableFloatStateOf(value.coerceIn(0f, 1f)) }

    val height by animateDpAsState(
        targetValue = if (dragging) activeHeight else idleHeight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "thinSliderHeight",
    )

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    val displayFraction = if (dragging) internalFraction else value.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(activeHeight + 20.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        dragging = true
                        val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        internalFraction = frac
                        currentOnValueChange(frac)
                        val released = tryAwaitRelease()
                        dragging = false
                        if (released) {
                            currentOnValueChangeFinished?.invoke()
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        internalFraction = frac
                        currentOnValueChange(frac)
                    },
                    onDragEnd = {
                        dragging = false
                        currentOnValueChangeFinished?.invoke()
                    },
                    onDragCancel = {
                        dragging = false
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val frac = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        internalFraction = frac
                        currentOnValueChange(frac)
                    }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            val radius = CornerRadius(size.height / 2f)
            drawRoundRect(color = inactiveColor, cornerRadius = radius)

            val filled = size.width * displayFraction
            if (filled > 0f) {
                drawRoundRect(
                    color = activeColor,
                    size = Size(
                        filled.coerceAtLeast(size.height).coerceAtMost(size.width),
                        size.height,
                    ),
                    cornerRadius = radius,
                )
            }
        }
    }
}

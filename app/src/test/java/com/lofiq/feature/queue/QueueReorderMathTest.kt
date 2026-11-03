package com.lofiq.feature.queue

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.roundToInt

class QueueReorderMathTest {

    @Test
    fun testSlotDeltaCalculation() {
        val rowHeight = 200f

        // Small drag within deadband: stays in slot 0
        assertEquals(0, (0f / rowHeight).roundToInt())
        assertEquals(0, (50f / rowHeight).roundToInt())
        assertEquals(0, (99f / rowHeight).roundToInt())
        assertEquals(0, (-50f / rowHeight).roundToInt())
        assertEquals(0, (-99f / rowHeight).roundToInt())

        // Past half-row threshold: moves to next slot
        assertEquals(1, (101f / rowHeight).roundToInt())
        assertEquals(1, (180f / rowHeight).roundToInt())
        assertEquals(2, (310f / rowHeight).roundToInt())

        // Past half-row threshold upwards: moves to previous slot
        assertEquals(-1, (-101f / rowHeight).roundToInt())
        assertEquals(-1, (-180f / rowHeight).roundToInt())
        assertEquals(-2, (-310f / rowHeight).roundToInt())
    }

    @Test
    fun testTargetIndexClamping() {
        val listSize = 5
        val maxIndex = listSize - 1
        val startIndex = 0

        // Dragging above top bounds clamps to 0
        val clampedTop = (startIndex + -3).coerceIn(0, maxIndex)
        assertEquals(0, clampedTop)

        // Dragging below bottom bounds clamps to lastIndex
        val clampedBottom = (startIndex + 10).coerceIn(0, maxIndex)
        assertEquals(maxIndex, clampedBottom)
    }

    @Test
    fun testTranslationContinuityAcrossSwaps() {
        // Visual continuity: Screen position must NOT jump when an item swaps positions in the list
        val rowHeight = 180f
        val startRelIndex = 1

        // User drags 100px down: not yet past halfway (100 < 90 is false, 100/180 = 0.555 -> 1)
        var accumulatedDragY = 80f
        var currentRelIndex = startRelIndex
        var translationY = (startRelIndex - currentRelIndex) * rowHeight + accumulatedDragY
        var screenPosition = currentRelIndex * rowHeight + translationY
        // Before swap
        assertEquals(startRelIndex * rowHeight + accumulatedDragY, screenPosition, 0.001f)

        // User crosses 91px threshold: swaps from index 1 to index 2
        accumulatedDragY = 95f
        val slotDelta = (accumulatedDragY / rowHeight).roundToInt()
        assertEquals(1, slotDelta)
        currentRelIndex = startRelIndex + slotDelta // now 2

        // Visual translation after swap
        translationY = (startRelIndex - currentRelIndex) * rowHeight + accumulatedDragY
        // (1 - 2) * 180 + 95 = -180 + 95 = -85f
        assertEquals(-85f, translationY, 0.001f)

        // Total screen position relative to list origin
        screenPosition = currentRelIndex * rowHeight + translationY
        // 2 * 180 + (-85) = 360 - 85 = 275f
        // Expected: startRelIndex * 180 + 95 = 180 + 95 = 275f!
        assertEquals(275f, screenPosition, 0.001f)
        assertEquals(startRelIndex * rowHeight + accumulatedDragY, screenPosition, 0.001f)
    }

    @Test
    fun testReorderingListPreservesElements() {
        val initialList = listOf("Song A", "Song B", "Song C", "Song D")
        val mutable = initialList.toMutableList()

        // Move "Song A" from 0 to 2
        val item = mutable.removeAt(0)
        mutable.add(2, item)

        assertEquals(listOf("Song B", "Song C", "Song A", "Song D"), mutable)

        // Move "Song A" from 2 back to 1
        val item2 = mutable.removeAt(2)
        mutable.add(1, item2)

        assertEquals(listOf("Song B", "Song A", "Song C", "Song D"), mutable)
    }
}

package com.lofiq.core

import com.lofiq.core.common.TimeFormatUtils
import com.lofiq.core.model.PlaybackRepeatMode
import com.lofiq.core.model.Song
import com.lofiq.core.model.LofiQPlayerState
import org.junit.Assert.*
import org.junit.Test

class PlayerStateTest {

    @Test
    fun `progressFraction computes correctly based on position and duration`() {
        val state = LofiQPlayerState(
            currentPositionMs = 30000L,
            durationMs = 60000L
        )
        assertEquals(0.5f, state.progressFraction, 0.001f)
    }

    @Test
    fun `hasNext returns true when more songs exist in queue`() {
        val song1 = Song(id = "1", title = "A", artist = "A")
        val song2 = Song(id = "2", title = "B", artist = "B")
        val state = LofiQPlayerState(
            queue = listOf(song1, song2),
            currentQueueIndex = 0,
            repeatMode = PlaybackRepeatMode.OFF
        )
        assertTrue(state.hasNext)
    }

    @Test
    fun `hasNext returns true at queue end if repeatMode is ALL`() {
        val song1 = Song(id = "1", title = "A", artist = "A")
        val state = LofiQPlayerState(
            queue = listOf(song1),
            currentQueueIndex = 0,
            repeatMode = PlaybackRepeatMode.ALL
        )
        assertTrue(state.hasNext)
    }

    @Test
    fun `TimeFormatUtils formats milliseconds accurately`() {
        assertEquals("0:00", TimeFormatUtils.formatMs(0L))
        assertEquals("0:45", TimeFormatUtils.formatMs(45000L))
        assertEquals("3:25", TimeFormatUtils.formatMs(205000L))
        assertEquals("12:05", TimeFormatUtils.formatMs(725000L))
    }
}

package com.lofiq.core

import com.lofiq.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueReorderTest {

    @Test
    fun `reordering upcoming songs preserves history and currently playing song`() {
        val song1 = Song(id = "1", title = "Song 1", artist = "Artist 1")
        val currentSong = Song(id = "2", title = "Current Song", artist = "Artist 2")
        val up1 = Song(id = "3", title = "Upcoming 1", artist = "Artist 3")
        val up2 = Song(id = "4", title = "Upcoming 2", artist = "Artist 4")
        val up3 = Song(id = "5", title = "Upcoming 3", artist = "Artist 5")

        val fullQueue = listOf(song1, currentSong, up1, up2, up3)
        val currentIndex = 1

        val history = fullQueue.subList(0, currentIndex)
        assertEquals(listOf(song1), history)
        assertEquals(currentSong, fullQueue[currentIndex])

        // User reorders upcoming tracks: [up3, up1, up2]
        val reorderedUpcoming = listOf(up3, up1, up2)

        val newFullQueue = ArrayList<Song>(history.size + 1 + reorderedUpcoming.size).apply {
            addAll(history)
            add(currentSong)
            addAll(reorderedUpcoming)
        }

        assertEquals(5, newFullQueue.size)
        assertEquals(song1, newFullQueue[0])
        assertEquals(currentSong, newFullQueue[1])
        assertEquals(up3, newFullQueue[2])
        assertEquals(up1, newFullQueue[3])
        assertEquals(up2, newFullQueue[4])
        // Current index remains pointed at currentSong
        assertEquals(currentSong, newFullQueue[currentIndex])
    }

    @Test
    fun `reordering when playing first song preserves first song at index 0`() {
        val currentSong = Song(id = "1", title = "First Song", artist = "Artist 1")
        val up1 = Song(id = "2", title = "Up 1", artist = "Artist 2")
        val up2 = Song(id = "3", title = "Up 2", artist = "Artist 3")

        val currentIndex = 0
        val reorderedUpcoming = listOf(up2, up1)

        val history = emptyList<Song>()
        val newFullQueue = ArrayList<Song>().apply {
            addAll(history)
            add(currentSong)
            addAll(reorderedUpcoming)
        }

        assertEquals(listOf(currentSong, up2, up1), newFullQueue)
        assertEquals(currentSong, newFullQueue[currentIndex])
    }

    @Test
    fun `rapid consecutive reorders maintain integrity without duplicate tracks`() {
        val songs = (1..6).map { Song(id = "$it", title = "Song $it", artist = "Artist") }
        var currentQueue = songs
        val currentIndex = 2 // Song 3 is playing

        var upcoming = currentQueue.subList(currentIndex + 1, currentQueue.size) // [Song 4, Song 5, Song 6]

        // Swap 1: 4 & 5
        upcoming = listOf(upcoming[1], upcoming[0], upcoming[2]) // [Song 5, Song 4, Song 6]

        // Swap 2: 4 & 6
        upcoming = listOf(upcoming[0], upcoming[2], upcoming[1]) // [Song 5, Song 6, Song 4]

        val history = currentQueue.subList(0, currentIndex)
        val current = currentQueue[currentIndex]
        val finalQueue = ArrayList<Song>().apply {
            addAll(history)
            add(current)
            addAll(upcoming)
        }

        assertEquals(6, finalQueue.size)
        assertEquals(finalQueue.size, finalQueue.distinctBy { it.id }.size)
        assertEquals("3", finalQueue[currentIndex].id)
        assertEquals("5", finalQueue[3].id)
        assertEquals("6", finalQueue[4].id)
        assertEquals("4", finalQueue[5].id)
    }
}

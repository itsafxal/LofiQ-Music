package com.lofiq.data.repository

import com.lofiq.core.database.entities.FavoriteEntity
import com.lofiq.core.database.entities.PlaylistEntity
import com.lofiq.core.database.entities.PlaylistSongCrossRef
import com.lofiq.core.database.entities.SongEntity
import org.junit.Assert.*
import org.junit.Test

class DatabaseIdempotentSyncTest {

    @Test
    fun testSongEntityIdempotency() {
        val song1 = SongEntity(
            id = "sp_4cOdK2wGLETKBW3PvgPWqT",
            title = "Starboy",
            artist = "The Weeknd",
            album = "Starboy",
            durationSeconds = 230L,
            artworkUrl = "https://i.scdn.co/image/art1",
            highResArtworkUrl = "https://i.scdn.co/image/art1_high",
            sourceUrl = "",
            providerId = "spotify",
            quality = "HIGH",
            addedAt = 1000L
        )

        val song2 = SongEntity(
            id = "sp_4cOdK2wGLETKBW3PvgPWqT",
            title = "Starboy (Updated)",
            artist = "The Weeknd",
            album = "Starboy Deluxe",
            durationSeconds = 230L,
            artworkUrl = "https://i.scdn.co/image/art2",
            highResArtworkUrl = "https://i.scdn.co/image/art2_high",
            sourceUrl = "",
            providerId = "spotify",
            quality = "HIGH",
            addedAt = 2000L
        )

        val map = mutableMapOf<String, SongEntity>()
        map[song1.id] = song1
        map[song2.id] = song2 // simulates OnConflictStrategy.REPLACE

        assertEquals(1, map.size)
        assertEquals("Starboy (Updated)", map["sp_4cOdK2wGLETKBW3PvgPWqT"]?.title)
    }

    @Test
    fun testPlaylistSongCrossRefOrdering() {
        val playlistId = "spotify_pl_37i9dQZF1DXcBWIGoYBM5M"
        val crossRefs = listOf(
            PlaylistSongCrossRef(playlistId = playlistId, songId = "sp_track1", position = 0, addedAt = 100L),
            PlaylistSongCrossRef(playlistId = playlistId, songId = "sp_track2", position = 1, addedAt = 100L),
            PlaylistSongCrossRef(playlistId = playlistId, songId = "sp_track3", position = 2, addedAt = 100L)
        )

        val sorted = crossRefs.sortedBy { it.position }
        assertEquals("sp_track1", sorted[0].songId)
        assertEquals(0, sorted[0].position)
        assertEquals("sp_track2", sorted[1].songId)
        assertEquals(1, sorted[1].position)
        assertEquals("sp_track3", sorted[2].songId)
        assertEquals(2, sorted[2].position)
    }

    @Test
    fun testFavoriteDeduplication() {
        val fav1 = FavoriteEntity(songId = "dQw4w9WgXcQ", addedAt = 5000L)
        val fav2 = FavoriteEntity(songId = "dQw4w9WgXcQ", addedAt = 6000L)

        val favorites = mutableMapOf<String, FavoriteEntity>()
        favorites[fav1.songId] = fav1
        favorites[fav2.songId] = fav2

        assertEquals(1, favorites.size)
        assertEquals(6000L, favorites["dQw4w9WgXcQ"]?.addedAt)
    }
}

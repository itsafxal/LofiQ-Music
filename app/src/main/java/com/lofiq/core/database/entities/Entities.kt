package com.lofiq.core.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Long,
    val artworkUrl: String,
    val highResArtworkUrl: String,
    val sourceUrl: String,
    val providerId: String,
    val quality: String,
    val addedAt: Long,
    val playCount: Long = 0L,
    val lyrics: String? = null
)

@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val imageUrl: String,
    val songCount: Int,
    val description: String
)

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String,
    val year: String,
    val songCount: Int
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val artworkUrl: String,
    val isSystem: Boolean,
    val createdAt: Long
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    indices = [Index(value = ["songId"])],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PlaylistSongCrossRef(
    val playlistId: String,
    val songId: String,
    val position: Int,
    val addedAt: Long
)

@Entity(
    tableName = "favorites",
    indices = [Index(value = ["addedAt"])]
)
data class FavoriteEntity(
    @PrimaryKey val songId: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playback_history",
    indices = [Index(value = ["playedAt"])]
)
data class PlaybackHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    val playedAt: Long = System.currentTimeMillis(),
    val durationPlayedSeconds: Long = 0
)

@Entity(
    tableName = "queue_snapshot",
    indices = [Index(value = ["orderIndex"])]
)
data class QueueSnapshotEntity(
    @PrimaryKey val songId: String,
    val orderIndex: Int,
    val isCurrent: Boolean
)

@Entity(
    tableName = "cached_metadata",
    indices = [Index(value = ["cachedAt"])]
)
data class CachedMetadataEntity(
    @PrimaryKey val key: String,
    val jsonContent: String,
    val cachedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = 0L
)

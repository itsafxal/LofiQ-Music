package com.lofiq.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lofiq.core.database.dao.*
import com.lofiq.core.database.entities.*

@Database(
    entities = [
        SongEntity::class,
        ArtistEntity::class,
        AlbumEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        FavoriteEntity::class,
        PlaybackHistoryEntity::class,
        QueueSnapshotEntity::class,
        CachedMetadataEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class LofiQDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun historyDao(): PlaybackHistoryDao
    abstract fun queueSnapshotDao(): QueueSnapshotDao
    abstract fun metadataCacheDao(): MetadataCacheDao

    companion object {
        const val DATABASE_NAME = "lofiq_music.db"
    }
}

package com.lofiq.core.database.dao

import androidx.room.*
import com.lofiq.core.database.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun rawInsertOrUpdate(song: SongEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun rawInsertOrUpdateAll(songs: List<SongEntity>)

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getById(id: String): SongEntity?

    @Query("SELECT * FROM songs WHERE id = :id")
    fun observeById(id: String): Flow<SongEntity?>

    @Query("SELECT * FROM songs ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%'")
    suspend fun searchLocal(query: String): List<SongEntity>

    @Query("""
        SELECT * FROM songs 
        WHERE providerId = 'local_download' 
           OR (sourceUrl LIKE '/storage/%' AND sourceUrl NOT LIKE 'http%') 
           OR sourceUrl LIKE 'content://%'
        ORDER BY addedAt DESC
    """)
    fun observeDownloads(): Flow<List<SongEntity>>

    @Query("""
        SELECT * FROM songs 
        WHERE providerId = 'local_download' 
           OR (sourceUrl LIKE '/storage/%' AND sourceUrl NOT LIKE 'http%') 
           OR sourceUrl LIKE 'content://%'
    """)
    suspend fun getDownloadsSync(): List<SongEntity>

    @Query("DELETE FROM songs WHERE id = :id AND (providerId = 'local_download' OR sourceUrl LIKE '/storage/%' OR sourceUrl LIKE 'content://%')")
    suspend fun deleteDownload(id: String)

    @Query("UPDATE songs SET providerId = :fallbackProviderId, sourceUrl = '' WHERE id = :songId")
    suspend fun removeDownloadStatus(songId: String, fallbackProviderId: String = "youtube")

    @Query("UPDATE songs SET playCount = playCount + 1 WHERE id = :songId")
    suspend fun incrementPlayCount(songId: String)

    @Transaction
    suspend fun insertOrUpdate(song: SongEntity) {
        if (song.providerId == "local_download") {
            rawInsertOrUpdate(song)
        } else {
            val existing = getById(song.id)
            if (existing != null && (existing.providerId == "local_download" || (existing.sourceUrl.isNotBlank() && !existing.sourceUrl.startsWith("http")))) {
                rawInsertOrUpdate(
                    song.copy(
                        providerId = "local_download",
                        sourceUrl = existing.sourceUrl,
                        addedAt = existing.addedAt
                    )
                )
            } else {
                rawInsertOrUpdate(song)
            }
        }
    }

    @Transaction
    suspend fun insertOrUpdateAll(songs: List<SongEntity>) {
        if (songs.isEmpty()) return
        val currentDownloads = getDownloadsSync().associateBy { it.id }
        val mergedSongs = songs.map { song ->
            val dl = currentDownloads[song.id]
            if (dl != null && song.providerId != "local_download") {
                song.copy(
                    providerId = "local_download",
                    sourceUrl = dl.sourceUrl,
                    addedAt = dl.addedAt
                )
            } else {
                song
            }
        }
        rawInsertOrUpdateAll(mergedSongs)
    }
}

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorites(favorites: List<FavoriteEntity>)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun removeFavorite(songId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    fun observeIsFavorite(songId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    suspend fun isFavorite(songId: String): Boolean

    @Query("""
        SELECT songs.* FROM songs
        INNER JOIN favorites ON songs.id = favorites.songId
        ORDER BY favorites.addedAt DESC
    """)
    fun observeFavoriteSongs(): Flow<List<SongEntity>>
}

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observeAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: String): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongToPlaylist(crossRef: PlaylistSongCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongsToPlaylist(crossRefs: List<PlaylistSongCrossRef>)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylistSongs(playlistId: String)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: String, songId: String)

    @Query("""
        SELECT songs.* FROM songs
        INNER JOIN playlist_songs ON songs.id = playlist_songs.songId
        WHERE playlist_songs.playlistId = :playlistId
        ORDER BY playlist_songs.position ASC
    """)
    fun observeSongsForPlaylist(playlistId: String): Flow<List<SongEntity>>

    @Query("""
        SELECT songs.* FROM songs
        INNER JOIN playlist_songs ON songs.id = playlist_songs.songId
        WHERE playlist_songs.playlistId = :playlistId
        ORDER BY playlist_songs.position ASC
    """)
    suspend fun getSongsForPlaylistSync(playlistId: String): List<SongEntity>
}

@Dao
interface PlaybackHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordHistory(item: PlaybackHistoryEntity)

    @Query("""
        SELECT songs.* FROM songs
        INNER JOIN playback_history ON songs.id = playback_history.songId
        ORDER BY playback_history.playedAt DESC
        LIMIT :limit
    """)
    fun observeRecentlyPlayed(limit: Int = 50): Flow<List<SongEntity>>

    @Query("DELETE FROM playback_history")
    suspend fun clearHistory()
}

@Dao
interface QueueSnapshotDao {
    @Query("DELETE FROM queue_snapshot")
    suspend fun clearQueue()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueue(items: List<QueueSnapshotEntity>)

    @Transaction
    suspend fun replaceQueue(items: List<QueueSnapshotEntity>) {
        clearQueue()
        insertQueue(items)
    }

    @Query("""
        SELECT songs.* FROM songs
        INNER JOIN queue_snapshot ON songs.id = queue_snapshot.songId
        ORDER BY queue_snapshot.orderIndex ASC
    """)
    suspend fun getPersistedQueue(): List<SongEntity>
}

@Dao
interface MetadataCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(item: CachedMetadataEntity)

    @Query("SELECT * FROM cached_metadata WHERE `key` = :key")
    suspend fun get(key: String): CachedMetadataEntity?

    @Query("DELETE FROM cached_metadata WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM cached_metadata WHERE expiresAt > 0 AND expiresAt < :now")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis())
}

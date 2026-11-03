package com.lofiq.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.common.LofiQLogger
import com.lofiq.core.database.dao.FavoriteDao
import com.lofiq.core.database.dao.PlaylistDao
import com.lofiq.core.database.dao.SongDao
import com.lofiq.core.database.entities.FavoriteEntity
import com.lofiq.core.database.entities.PlaylistEntity
import com.lofiq.core.database.entities.PlaylistSongCrossRef
import com.lofiq.core.model.Playlist
import com.lofiq.core.model.Song
import com.lofiq.data.repository.toEntity
import com.lofiq.data.repository.toDomain
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreSyncRepository @Inject constructor(
    private val songDao: SongDao,
    private val favoriteDao: FavoriteDao,
    private val playlistDao: PlaylistDao,
    private val dispatchers: DispatcherProvider
) {
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private fun getCurrentUid(): String? = auth.currentUser?.uid

    /**
     * Download user's in-app liked songs and custom playlists from Cloud Firestore into Room DB
     */
    suspend fun syncFromCloud(): Boolean = withContext(dispatchers.io) {
        val uid = getCurrentUid() ?: return@withContext false
        try {
            LofiQLogger.i(LofiQLogger.Category.DATABASE, "Starting Firestore Cloud Sync for UID: $uid")

            // 1. Pull user's in-app liked songs from subcollections & user doc
            val favCollections = listOf("favorites", "liked_songs", "likedSongs", "favoriteSongs")
            val songsToInsert = mutableListOf<Song>()
            val favEntities = mutableListOf<FavoriteEntity>()

            for (colName in favCollections) {
                try {
                    val favsSnapshot = firestore.collection("users").document(uid)
                        .collection(colName)
                        .get()
                        .await()

                    for (doc in favsSnapshot.documents) {
                        val songId = doc.getString("songId") ?: doc.getString("id") ?: doc.getString("videoId") ?: doc.id
                        val title = doc.getString("title") ?: doc.getString("name") ?: doc.getString("trackName") ?: ""
                        val artist = doc.getString("artist") ?: doc.getString("artistName") ?: doc.getString("uploader") ?: "Unknown Artist"
                        val album = doc.getString("album") ?: doc.getString("albumName") ?: ""
                        val artworkUrl = doc.getString("artworkUrl") ?: doc.getString("thumbnailUrl") ?: doc.getString("coverUrl") ?: ""
                        val highResArtworkUrl = doc.getString("highResArtworkUrl") ?: artworkUrl
                        val durationSeconds = (doc.get("durationSeconds") as? Number)?.toLong()
                            ?: (doc.get("duration") as? Number)?.toLong()
                            ?: ((doc.get("durationMs") as? Number)?.toLong()?.div(1000L))
                            ?: 0L
                        val providerId = doc.getString("providerId") ?: doc.getString("source") ?: "youtube"
                        val sourceUrl = doc.getString("sourceUrl") ?: ""
                        val addedAt = (doc.get("addedAt") as? Number)?.toLong()
                            ?: (doc.get("timestamp") as? Number)?.toLong()
                            ?: System.currentTimeMillis()

                        if (title.isBlank() && songId.isBlank()) continue

                        val song = Song(
                            id = songId,
                            title = title.ifBlank { "Unknown Title" },
                            artist = artist,
                            album = album,
                            durationSeconds = durationSeconds,
                            artworkUrl = artworkUrl,
                            highResArtworkUrl = highResArtworkUrl,
                            sourceUrl = sourceUrl,
                            providerId = providerId,
                            isFavorite = true,
                            addedAt = addedAt
                        )
                        songsToInsert.add(song)
                        favEntities.add(FavoriteEntity(songId = songId, addedAt = addedAt))
                    }
                } catch (e: Exception) {
                    LofiQLogger.d(LofiQLogger.Category.DATABASE, "Subcollection $colName error: ${e.message}")
                }
            }

            // Check root favorites collection by userId
            try {
                val rootFavs = firestore.collection("favorites")
                    .whereEqualTo("userId", uid)
                    .get()
                    .await()

                for (doc in rootFavs.documents) {
                    val songId = doc.getString("songId") ?: doc.getString("id") ?: doc.id
                    val title = doc.getString("title") ?: doc.getString("name") ?: ""
                    val artist = doc.getString("artist") ?: "Unknown Artist"
                    val album = doc.getString("album") ?: ""
                    val artworkUrl = doc.getString("artworkUrl") ?: ""
                    val durationSeconds = (doc.get("durationSeconds") as? Number)?.toLong() ?: 0L
                    val providerId = doc.getString("providerId") ?: "youtube"
                    val addedAt = (doc.get("addedAt") as? Number)?.toLong() ?: System.currentTimeMillis()

                    if (title.isNotBlank() || songId.isNotBlank()) {
                        val song = Song(
                            id = songId,
                            title = title.ifBlank { "Unknown Title" },
                            artist = artist,
                            album = album,
                            durationSeconds = durationSeconds,
                            artworkUrl = artworkUrl,
                            providerId = providerId,
                            isFavorite = true,
                            addedAt = addedAt
                        )
                        songsToInsert.add(song)
                        favEntities.add(FavoriteEntity(songId = songId, addedAt = addedAt))
                    }
                }
            } catch (_: Exception) {}

            // Check user document fields (array/list format)
            try {
                val userDoc = firestore.collection("users").document(uid).get().await()
                if (userDoc.exists()) {
                    val listFields = listOf("favorites", "liked_songs", "likedSongs", "favoriteSongs")
                    for (field in listFields) {
                        val arrayData = userDoc.get(field) as? List<Map<String, Any>>
                        if (!arrayData.isNullOrEmpty()) {
                            for (item in arrayData) {
                                val sid = item["songId"] as? String ?: item["id"] as? String ?: continue
                                val stitle = item["title"] as? String ?: item["name"] as? String ?: "Unknown"
                                val sartist = item["artist"] as? String ?: "Unknown Artist"
                                val salbum = item["album"] as? String ?: ""
                                val sartwork = item["artworkUrl"] as? String ?: ""
                                val sdur = (item["durationSeconds"] as? Number)?.toLong() ?: 0L
                                val sprovider = item["providerId"] as? String ?: "youtube"
                                val sadded = (item["addedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()

                                val song = Song(
                                    id = sid,
                                    title = stitle,
                                    artist = sartist,
                                    album = salbum,
                                    durationSeconds = sdur,
                                    artworkUrl = sartwork,
                                    providerId = sprovider,
                                    isFavorite = true,
                                    addedAt = sadded
                                )
                                songsToInsert.add(song)
                                favEntities.add(FavoriteEntity(songId = sid, addedAt = sadded))
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            if (songsToInsert.isNotEmpty()) {
                val distinctSongs = songsToInsert.distinctBy { it.id }
                val distinctFavs = favEntities.distinctBy { it.songId }
                val currentDownloads = songDao.observeDownloads().firstOrNull()?.associateBy { it.id } ?: emptyMap()
                val entitiesToInsert = distinctSongs.map { song ->
                    val dl = currentDownloads[song.id]
                    if (dl != null) {
                        song.toEntity().copy(providerId = "local_download", sourceUrl = dl.sourceUrl)
                    } else {
                        song.toEntity()
                    }
                }
                songDao.insertOrUpdateAll(entitiesToInsert)
                favoriteDao.addFavorites(distinctFavs)
                LofiQLogger.i(LofiQLogger.Category.DATABASE, "Synced ${distinctSongs.size} in-app liked songs from Firestore")
            }

            // 2. Pull user's custom in-app playlists
            val playlistCollections = listOf("playlists", "custom_playlists", "user_playlists")
            for (pCol in playlistCollections) {
                try {
                    val playlistsSnapshot = firestore.collection("users").document(uid)
                        .collection(pCol)
                        .get()
                        .await()

                    if (!playlistsSnapshot.isEmpty) {
                        for (doc in playlistsSnapshot.documents) {
                            val plId = doc.getString("id") ?: doc.id
                            val name = doc.getString("name") ?: continue
                            val description = doc.getString("description") ?: ""
                            val artworkUrl = doc.getString("artworkUrl") ?: ""
                            val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()

                            val playlistEntity = PlaylistEntity(
                                id = plId,
                                name = name,
                                description = description,
                                artworkUrl = artworkUrl,
                                isSystem = false,
                                createdAt = createdAt
                            )
                            playlistDao.insertPlaylist(playlistEntity)

                            val songsList = doc.get("songs") as? List<Map<String, Any>>
                            if (!songsList.isNullOrEmpty()) {
                                for ((index, songMap) in songsList.withIndex()) {
                                    val sid = songMap["songId"] as? String ?: songMap["id"] as? String ?: continue
                                    val stitle = songMap["title"] as? String ?: "Unknown"
                                    val sartist = songMap["artist"] as? String ?: "Unknown"
                                    val salbum = songMap["album"] as? String ?: ""
                                    val sartwork = songMap["artworkUrl"] as? String ?: ""
                                    val sduration = (songMap["durationSeconds"] as? Number)?.toLong() ?: 0L
                                    val sprovider = songMap["providerId"] as? String ?: "youtube"

                                    val song = Song(
                                        id = sid,
                                        title = stitle,
                                        artist = sartist,
                                        album = salbum,
                                        durationSeconds = sduration,
                                        artworkUrl = sartwork,
                                        providerId = sprovider
                                    )
                                    val existing = songDao.getById(sid)
                                    val entityToSave = if (existing?.providerId == "local_download") {
                                        song.toEntity().copy(providerId = "local_download", sourceUrl = existing.sourceUrl)
                                    } else {
                                        song.toEntity()
                                    }
                                    songDao.insertOrUpdate(entityToSave)
                                    playlistDao.addSongToPlaylist(
                                        PlaylistSongCrossRef(
                                            playlistId = plId,
                                            songId = sid,
                                            position = index,
                                            addedAt = System.currentTimeMillis()
                                        )
                                    )
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // 3. Upload all local favorites and playlists to Firestore so cloud is always up-to-date
            uploadLocalDataToCloud()

            true
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.DATABASE, "Firestore sync error: ${e.message}")
            false
        }
    }

    /**
     * Uploads local Room database favorites & playlists to Cloud Firestore
     */
    suspend fun uploadLocalDataToCloud() = withContext(dispatchers.io) {
        val uid = getCurrentUid() ?: return@withContext
        try {
            // Upload local favorite songs
            val localFavorites = favoriteDao.observeFavoriteSongs().firstOrNull().orEmpty()
            for (favEntity in localFavorites) {
                val song = favEntity.toDomain(isFavorite = true)
                saveFavoriteToCloud(song)
            }
            if (localFavorites.isNotEmpty()) {
                LofiQLogger.i(LofiQLogger.Category.DATABASE, "Uploaded ${localFavorites.size} local favorites to Firestore for UID: $uid")
            }

            // Upload local custom playlists
            val localPlaylists = playlistDao.observeAllPlaylists().firstOrNull().orEmpty()
            for (plEntity in localPlaylists) {
                if (!plEntity.isSystem) {
                    val songs = playlistDao.getSongsForPlaylistSync(plEntity.id).map { it.toDomain() }
                    savePlaylistToCloud(
                        Playlist(
                            id = plEntity.id,
                            name = plEntity.name,
                            description = plEntity.description,
                            artworkUrl = plEntity.artworkUrl,
                            isSystem = false,
                            createdAt = plEntity.createdAt,
                            songCount = songs.size
                        ),
                        songs
                    )
                }
            }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.DATABASE, "uploadLocalDataToCloud error: ${e.message}")
        }
    }

    /**
     * Save an in-app liked song to Cloud Firestore
     */
    suspend fun saveFavoriteToCloud(song: Song) = withContext(dispatchers.io) {
        val uid = getCurrentUid() ?: return@withContext
        try {
            val favMap = hashMapOf<String, Any>(
                "songId" to song.id,
                "title" to song.title,
                "artist" to song.artist,
                "album" to song.album,
                "artworkUrl" to song.artworkUrl,
                "highResArtworkUrl" to song.highResArtworkUrl,
                "durationSeconds" to song.durationSeconds,
                "providerId" to song.providerId,
                "sourceUrl" to song.sourceUrl,
                "addedAt" to System.currentTimeMillis()
            )
            firestore.collection("users").document(uid)
                .collection("favorites").document(song.id)
                .set(favMap, SetOptions.merge())
                .await()
            LofiQLogger.d(LofiQLogger.Category.DATABASE, "Saved favorite '${song.title}' to Firestore for UID: $uid")
        } catch (e: Exception) {
            LofiQLogger.e(LofiQLogger.Category.DATABASE, "Failed to save favorite to Firestore: ${e.message}", e)
        }
    }

    /**
     * Remove an in-app liked song from Cloud Firestore
     */
    suspend fun removeFavoriteFromCloud(songId: String) = withContext(dispatchers.io) {
        val uid = getCurrentUid() ?: return@withContext
        try {
            firestore.collection("users").document(uid)
                .collection("favorites").document(songId)
                .delete()
                .await()
            LofiQLogger.d(LofiQLogger.Category.DATABASE, "Removed favorite $songId from Firestore")
        } catch (e: Exception) {
            LofiQLogger.e(LofiQLogger.Category.DATABASE, "Failed to remove favorite from Firestore: ${e.message}", e)
        }
    }

    /**
     * Save custom in-app playlist to Cloud Firestore
     */
    suspend fun savePlaylistToCloud(playlist: Playlist, songs: List<Song> = emptyList()) = withContext(dispatchers.io) {
        val uid = getCurrentUid() ?: return@withContext
        try {
            val plData = hashMapOf(
                "id" to playlist.id,
                "name" to playlist.name,
                "description" to playlist.description,
                "artworkUrl" to playlist.artworkUrl,
                "createdAt" to playlist.createdAt,
                "songCount" to (if (songs.isNotEmpty()) songs.size else playlist.songCount),
                "songs" to songs.map { FirestoreFavoriteSong.fromSong(it) }
            )
            firestore.collection("users").document(uid)
                .collection("playlists").document(playlist.id)
                .set(plData, SetOptions.merge())
                .await()
            LofiQLogger.d(LofiQLogger.Category.DATABASE, "Saved playlist '${playlist.name}' to Firestore")
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.DATABASE, "Failed to save playlist to Firestore: ${e.message}")
        }
    }

    /**
     * Delete custom in-app playlist from Cloud Firestore
     */
    suspend fun deletePlaylistFromCloud(playlistId: String) = withContext(dispatchers.io) {
        val uid = getCurrentUid() ?: return@withContext
        try {
            firestore.collection("users").document(uid)
                .collection("playlists").document(playlistId)
                .delete()
                .await()
            LofiQLogger.d(LofiQLogger.Category.DATABASE, "Deleted playlist $playlistId from Firestore")
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.DATABASE, "Failed to delete playlist from Firestore: ${e.message}")
        }
    }

    /**
     * Backup connected accounts credentials
     * DEPRECATED: Cloud synchronization of raw session credentials is removed in favor of
     * device-local DataStore sandbox isolation. This method is a no-op to prevent cloud writes.
     */
    suspend fun backupConnectedCredentials(ytCookies: String?, spotifyCookies: String?) = withContext(dispatchers.io) {
        // No-op: Local DataStore sandbox is the definitive secure storage
        LofiQLogger.d(LofiQLogger.Category.DATABASE, "Cloud credential backup disabled; using local DataStore sandbox")
    }

    /**
     * Restore connected accounts credentials
     * DEPRECATED: Automatic cloud restoration disabled in favor of device-local DataStore.
     */
    suspend fun restoreConnectedCredentials(): Pair<String?, String?> = withContext(dispatchers.io) {
        // Disabled: Never fetch or restore credentials from cloud
        Pair(null, null)
    }
}

package com.lofiq.data.repository

import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.common.LofiQLogger
import com.lofiq.core.database.dao.FavoriteDao
import com.lofiq.core.database.dao.PlaybackHistoryDao
import com.lofiq.core.database.dao.PlaylistDao
import com.lofiq.core.database.dao.SongDao
import com.lofiq.core.database.entities.FavoriteEntity
import com.lofiq.core.database.entities.PlaylistEntity
import com.lofiq.core.database.entities.PlaylistSongCrossRef
import com.lofiq.core.database.entities.SongEntity
import com.lofiq.core.datastore.LofiQPreferences
import com.lofiq.core.model.*
import com.lofiq.resolver.cache.ResolutionCache
import com.lofiq.resolver.core.TrackIdentityMatcher
import com.lofiq.resolver.providers.youtube.YouTubeMetadataProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val youTubeMetadataProvider: YouTubeMetadataProvider,
    private val jioSaavnClient: com.lofiq.data.jiosaavn.JioSaavnClient,
    private val songDao: SongDao,
    private val dispatchers: DispatcherProvider
) {
    suspend fun searchSongs(query: String, limit: Int = 25): List<Song> = withContext(dispatchers.io) {
        if (query.isBlank()) return@withContext emptyList()
        val trimmedQuery = query.trim()
        val userWantsKaraoke = trimmedQuery.contains("karaoke", ignoreCase = true) ||
                trimmedQuery.contains("instrumental", ignoreCase = true)

        val saavnResult: Pair<List<Song>, List<String>> = try {
            jioSaavnClient.getAutocomplete(trimmedQuery)
        } catch (e: Exception) {
            Pair(emptyList(), emptyList())
        }
        val rawSaavnSongs: List<Song> = saavnResult.first
        val rawYtResults: List<Song> = try {
            youTubeMetadataProvider.searchSongs(trimmedQuery, limit = limit)
        } catch (e: Exception) {
            emptyList()
        }

        val saavnSongs = if (userWantsKaraoke) rawSaavnSongs else rawSaavnSongs.filter {
            !TrackIdentityMatcher.isKaraokeOrIncompatibleTrack(it.title, it.artist)
        }
        val ytResults = if (userWantsKaraoke) rawYtResults else rawYtResults.filter {
            !TrackIdentityMatcher.isKaraokeOrIncompatibleTrack(it.title, it.artist)
        }

        val cleanQuery = trimmedQuery.lowercase()
        val topMatchSong: Song? = saavnSongs.firstOrNull { song ->
            val title = song.title.lowercase()
            title == cleanQuery || title.startsWith(cleanQuery)
        } ?: saavnSongs.firstOrNull()

        val finalResults = mutableListOf<Song>()
        if (topMatchSong != null) {
            finalResults.add(topMatchSong)
        }

        for (song in ytResults) {
            val isDuplicate = topMatchSong != null && (
                song.id == topMatchSong.id ||
                (song.title.equals(topMatchSong.title, ignoreCase = true) &&
                 song.artist.split(",").firstOrNull()?.trim()?.equals(topMatchSong.artist.split(",").firstOrNull()?.trim(), ignoreCase = true) == true)
            )
            if (!isDuplicate) {
                finalResults.add(song)
            }
        }

        if (finalResults.size < 5) {
            for (saavn in saavnSongs) {
                if (finalResults.none { it.id == saavn.id || it.title.equals(saavn.title, ignoreCase = true) }) {
                    finalResults.add(saavn)
                }
            }
        }

        if (finalResults.isNotEmpty()) {
            return@withContext finalResults.take(limit)
        }

        songDao.searchLocal(query).map { it.toDomain() }
    }

    suspend fun getSearchSuggestions(query: String): List<String> = withContext(dispatchers.io) {
        if (query.isBlank()) return@withContext emptyList()
        val yt = try { youTubeMetadataProvider.getSearchSuggestions(query) } catch (e: Exception) { emptyList() }
        val saavn = try { jioSaavnClient.getAutocomplete(query).second } catch (e: Exception) { emptyList() }
        (saavn + yt).distinct().take(10)
    }

    suspend fun getAutocompleteSongs(query: String): List<Song> = withContext(dispatchers.io) {
        if (query.isBlank()) return@withContext emptyList()
        val songs = try { jioSaavnClient.getAutocomplete(query).first } catch (e: Exception) { emptyList() }
        val userWantsKaraoke = query.contains("karaoke", ignoreCase = true) || query.contains("instrumental", ignoreCase = true)
        if (userWantsKaraoke) songs else songs.filter { !TrackIdentityMatcher.isKaraokeOrIncompatibleTrack(it.title, it.artist) }
    }

    suspend fun getTrendingSongs(limit: Int = 20): List<Song> = withContext(dispatchers.io) {
        youTubeMetadataProvider.getTrendingSongs(limit = limit)
    }

    suspend fun getSongById(id: String): Song? = withContext(dispatchers.io) {
        songDao.getById(id)?.toDomain()
    }
}

@Singleton
class LibraryRepository @Inject constructor(
    private val favoriteDao: FavoriteDao,
    private val playlistDao: PlaylistDao,
    private val historyDao: PlaybackHistoryDao,
    private val songDao: SongDao,
    private val youTubeMetadataProvider: YouTubeMetadataProvider,
    private val firestoreSyncRepository: com.lofiq.data.firebase.FirestoreSyncRepository,
    private val dispatchers: DispatcherProvider
) {
    private val likedSyncMutex = Mutex()
    private val playlistSyncMutex = Mutex()

    fun observeFavorites(): Flow<List<Song>> {
        return favoriteDao.observeFavoriteSongs().map { list -> list.map { it.toDomain(isFavorite = true) } }
    }

    fun observeDownloads(): Flow<List<Song>> {
        return songDao.observeDownloads().map { list -> list.map { it.toDomain() } }
    }

    suspend fun deleteDownload(songId: String) = withContext(dispatchers.io) {
        songDao.deleteDownload(songId)
    }

    suspend fun toggleFavorite(song: Song) = withContext(dispatchers.io) {
        val existing = songDao.getById(song.id)
        val entity = if (existing?.providerId == "local_download") {
            song.toEntity().copy(
                providerId = "local_download",
                sourceUrl = existing.sourceUrl
            )
        } else {
            song.toEntity()
        }
        songDao.insertOrUpdate(entity)
        if (favoriteDao.isFavorite(song.id)) {
            favoriteDao.removeFavorite(song.id)
            firestoreSyncRepository.removeFavoriteFromCloud(song.id)
        } else {
            favoriteDao.addFavorite(FavoriteEntity(songId = song.id))
            firestoreSyncRepository.saveFavoriteToCloud(song)
        }
    }

    fun isFavorite(songId: String): Flow<Boolean> {
        return favoriteDao.observeIsFavorite(songId)
    }

    fun observePlaylists(): Flow<List<Playlist>> {
        return playlistDao.observeAllPlaylists().map { list ->
            list.map {
                Playlist(
                    id = it.id,
                    name = it.name,
                    description = it.description,
                    artworkUrl = it.artworkUrl,
                    isSystem = it.isSystem,
                    createdAt = it.createdAt
                )
            }
        }
    }

    suspend fun createPlaylist(name: String, description: String = "") = withContext(dispatchers.io) {
        val playlist = PlaylistEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            artworkUrl = "",
            isSystem = false,
            createdAt = System.currentTimeMillis()
        )
        playlistDao.insertPlaylist(playlist)
        firestoreSyncRepository.savePlaylistToCloud(
            Playlist(
                id = playlist.id,
                name = playlist.name,
                description = playlist.description,
                artworkUrl = playlist.artworkUrl,
                isSystem = playlist.isSystem,
                createdAt = playlist.createdAt
            )
        )
    }

    suspend fun deletePlaylist(playlistId: String) = withContext(dispatchers.io) {
        val existing = playlistDao.getPlaylistById(playlistId)
        if (existing != null) {
            playlistDao.deletePlaylist(existing)
            firestoreSyncRepository.deletePlaylistFromCloud(playlistId)
        }
    }

    suspend fun addSongToPlaylist(playlistId: String, song: Song) = withContext(dispatchers.io) {
        val existingSong = songDao.getById(song.id)
        val entity = if (existingSong?.providerId == "local_download") {
            song.toEntity().copy(
                providerId = "local_download",
                sourceUrl = existingSong.sourceUrl
            )
        } else {
            song.toEntity()
        }
        songDao.insertOrUpdate(entity)
        val crossRef = PlaylistSongCrossRef(
            playlistId = playlistId,
            songId = song.id,
            position = (System.currentTimeMillis() % 10000).toInt(),
            addedAt = System.currentTimeMillis()
        )
        playlistDao.addSongToPlaylist(crossRef)
        val existing = playlistDao.getPlaylistById(playlistId)
        if (existing != null) {
            val songs = playlistDao.getSongsForPlaylistSync(playlistId).map { it.toDomain() }
            firestoreSyncRepository.savePlaylistToCloud(
                Playlist(
                    id = existing.id,
                    name = existing.name,
                    description = existing.description,
                    artworkUrl = existing.artworkUrl,
                    isSystem = existing.isSystem,
                    createdAt = existing.createdAt,
                    songCount = songs.size
                ),
                songs = songs
            )
        }
    }

    fun observePlaylistSongs(playlistId: String): Flow<List<Song>> {
        return playlistDao.observeSongsForPlaylist(playlistId).map { list -> list.map { it.toDomain() } }
    }

    fun observeHistory(): Flow<List<Song>> {
        return historyDao.observeRecentlyPlayed(50).map { list -> list.distinctBy { it.id }.map { it.toDomain() } }
    }

    suspend fun clearHistory() = withContext(dispatchers.io) {
        historyDao.clearHistory()
    }

    /**
     * Sync user's remote YouTube Music liked songs into local Room library
     */
    suspend fun syncRemoteLikedSongs(cookies: String): Int = withContext(dispatchers.io) {
        likedSyncMutex.withLock {
            try {
                val likedSongs = youTubeMetadataProvider.fetchBrowseLikedSongs(cookies)
                LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[LIBRARY][YOUTUBE] Liked songs fetched count=${likedSongs.size}")
                if (likedSongs.isNotEmpty()) {
                    val now = System.currentTimeMillis()
                    songDao.insertOrUpdateAll(likedSongs.map { it.toEntity() })
                    val favEntities = likedSongs.mapIndexed { idx, song ->
                        FavoriteEntity(songId = song.id, addedAt = now - idx * 1000L)
                    }
                    favoriteDao.addFavorites(favEntities)
                    LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[LIBRARY][YOUTUBE] Successfully synced ${likedSongs.size} liked songs into Room")
                    return@withLock likedSongs.size
                }
            } catch (e: Exception) {
                LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "syncRemoteLikedSongs failed: ${e.message}")
            }
            0
        }
    }

    /**
     * Sync user's remote YouTube Music playlists and their tracks into local Room library
     */
    suspend fun syncRemotePlaylists(cookies: String): Int = withContext(dispatchers.io) {
        playlistSyncMutex.withLock {
            var syncedCount = 0
            try {
                val remotePlaylists = youTubeMetadataProvider.fetchBrowseUserPlaylists(cookies)
                LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[LIBRARY][YOUTUBE] Playlists fetched count=${remotePlaylists.size}")
                for (pl in remotePlaylists) {
                    val existingPlaylist = playlistDao.getPlaylistById(pl.id)
                    val playlistEntity = PlaylistEntity(
                        id = pl.id,
                        name = pl.title,
                        description = pl.description,
                        artworkUrl = pl.artworkUrl,
                        isSystem = false,
                        createdAt = existingPlaylist?.createdAt ?: System.currentTimeMillis()
                    )
                    playlistDao.insertPlaylist(playlistEntity)

                    // Fetch songs for this playlist
                    val songs = youTubeMetadataProvider.fetchBrowsePlaylistSongs(pl.id, cookies)
                    if (songs.isNotEmpty()) {
                        songDao.insertOrUpdateAll(songs.map { it.toEntity() })
                        val crossRefs = songs.mapIndexed { index, song ->
                            PlaylistSongCrossRef(
                                playlistId = pl.id,
                                songId = song.id,
                                position = index,
                                addedAt = System.currentTimeMillis()
                            )
                        }
                        playlistDao.clearPlaylistSongs(pl.id)
                        playlistDao.addSongsToPlaylist(crossRefs)
                        LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[PLAYLIST][YOUTUBE] Playlist loaded playlistId=${pl.id} tracks=${songs.size}")
                    }
                    syncedCount++
                }
            } catch (e: Exception) {
                LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "syncRemotePlaylists failed: ${e.message}")
            }
            syncedCount
        }
    }
}

@Singleton
class SettingsRepository @Inject constructor(
    private val preferences: LofiQPreferences,
    private val resolutionCache: ResolutionCache
) {
    val audioQuality: Flow<AudioQuality> = preferences.audioQuality
    val dynamicBackground: Flow<Boolean> = preferences.dynamicBackgroundEnabled
    val liquidGlassEnabled: Flow<Boolean> = preferences.liquidGlassEnabled
    val fullScreenArtworkEnabled: Flow<Boolean> = preferences.fullScreenArtworkEnabled
    val appIconTheme: Flow<com.lofiq.core.common.AppIconTheme> = preferences.appIconTheme.map {
        com.lofiq.core.common.AppIconTheme.fromId(it)
    }
    val customBrandColor: Flow<String?> = preferences.customBrandColor
    val seekbarStyle: Flow<com.lofiq.core.model.SeekbarStyle> = preferences.seekbarStyle
    val performanceModeEnabled: Flow<Boolean> = preferences.performanceModeEnabled

    suspend fun setAudioQuality(quality: AudioQuality) {
        preferences.setAudioQuality(quality)
    }

    suspend fun setDynamicBackground(enabled: Boolean) {
        preferences.setDynamicBackgroundEnabled(enabled)
    }

    suspend fun setLiquidGlassEnabled(enabled: Boolean) {
        preferences.setLiquidGlassEnabled(enabled)
    }

    suspend fun setFullScreenArtworkEnabled(enabled: Boolean) {
        preferences.setFullScreenArtworkEnabled(enabled)
    }

    suspend fun setAppIconTheme(theme: com.lofiq.core.common.AppIconTheme) {
        preferences.setAppIconTheme(theme.id)
    }

    suspend fun setCustomBrandColor(hex: String?) {
        preferences.setCustomBrandColor(hex)
    }

    suspend fun setSeekbarStyle(style: com.lofiq.core.model.SeekbarStyle) {
        preferences.setSeekbarStyle(style)
    }

    suspend fun setPerformanceModeEnabled(enabled: Boolean) {
        preferences.setPerformanceModeEnabled(enabled)
    }

    val crossfadeDurationSec: Flow<Int> = preferences.crossfadeDurationSec

    suspend fun setCrossfadeDurationSec(seconds: Int) {
        preferences.setCrossfadeDurationSec(seconds)
    }

    suspend fun clearCache() {
        resolutionCache.clear()
    }
}

// Mappers
fun SongEntity.toDomain(isFavorite: Boolean = false): Song {
    return Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationSeconds = durationSeconds,
        artworkUrl = artworkUrl,
        highResArtworkUrl = highResArtworkUrl,
        sourceUrl = sourceUrl,
        isFavorite = isFavorite,
        providerId = providerId,
        quality = AudioQuality.fromString(quality),
        addedAt = addedAt,
        playCount = playCount,
        lyrics = lyrics
    )
}

fun Song.toEntity(): SongEntity {
    return SongEntity(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationSeconds = durationSeconds,
        artworkUrl = artworkUrl,
        highResArtworkUrl = highResArtworkUrl,
        sourceUrl = sourceUrl,
        providerId = providerId,
        quality = quality.name,
        addedAt = addedAt,
        playCount = playCount,
        lyrics = lyrics
    )
}

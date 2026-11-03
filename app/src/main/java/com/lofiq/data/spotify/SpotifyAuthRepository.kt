package com.lofiq.data.spotify

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.SupervisorJob
import com.lofiq.auth.ProviderSyncStatus
import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.common.LofiQLogger
import com.lofiq.core.database.dao.FavoriteDao
import com.lofiq.core.database.dao.PlaylistDao
import com.lofiq.core.database.dao.SongDao
import com.lofiq.core.database.entities.FavoriteEntity
import com.lofiq.core.database.entities.PlaylistEntity
import com.lofiq.core.database.entities.PlaylistSongCrossRef
import com.lofiq.core.model.Song
import com.lofiq.data.repository.MusicRepository
import com.lofiq.data.repository.toEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class SpotifyAccount(
    val name: String = "Spotify User",
    val email: String? = null,
    val avatarUrl: String? = null,
    val id: String? = null,
    val product: String? = null,
    val isLoggedIn: Boolean = false
) {
    companion object {
        val GUEST = SpotifyAccount(name = "Guest", isLoggedIn = false)
    }
}

@Singleton
class SpotifyAuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
    private val favoriteDao: FavoriteDao,
    private val playlistDao: PlaylistDao,
    private val musicRepository: MusicRepository,
    private val spotifyImportHelper: SpotifyImportHelper,
    val tokenManager: SpotifyTokenManager,
    val spotifyWebClient: SpotifyWebClient,
    private val dispatchers: DispatcherProvider
) {
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(dispatchers.io + SupervisorJob()),
        produceFile = { context.preferencesDataStoreFile("lofiq_spotify_preferences") }
    )

    private val _spotifySyncStatus = MutableStateFlow<ProviderSyncStatus>(ProviderSyncStatus.Idle)
    val spotifySyncStatus: StateFlow<ProviderSyncStatus> = _spotifySyncStatus.asStateFlow()

    private object PreferencesKeys {
        val COOKIES = stringPreferencesKey("spotify_cookies")
        val USER_NAME = stringPreferencesKey("spotify_user_name")
        val USER_EMAIL = stringPreferencesKey("spotify_user_email")
        val USER_AVATAR = stringPreferencesKey("spotify_user_avatar")
        val USER_ID = stringPreferencesKey("spotify_user_id")
        val USER_PRODUCT = stringPreferencesKey("spotify_user_product")
        val IS_LOGGED_IN = booleanPreferencesKey("spotify_is_logged_in")
    }

    @Volatile
    private var inMemoryCookies: String? = null

    init {
        CoroutineScope(dispatchers.io).launch {
            dataStore.data.catch { }.collect { prefs ->
                inMemoryCookies = prefs[PreferencesKeys.COOKIES]
                val isLoggedIn = prefs[PreferencesKeys.IS_LOGGED_IN] ?: false
                if (!isLoggedIn && _spotifySyncStatus.value !is ProviderSyncStatus.Idle) {
                    _spotifySyncStatus.value = ProviderSyncStatus.NotConnected
                }
            }
        }
    }

    val spotifyAccount: Flow<SpotifyAccount> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            val isLoggedIn = prefs[PreferencesKeys.IS_LOGGED_IN] ?: false
            if (!isLoggedIn) {
                SpotifyAccount.GUEST
            } else {
                SpotifyAccount(
                    name = prefs[PreferencesKeys.USER_NAME] ?: "Spotify User",
                    email = prefs[PreferencesKeys.USER_EMAIL],
                    avatarUrl = prefs[PreferencesKeys.USER_AVATAR],
                    id = prefs[PreferencesKeys.USER_ID],
                    product = prefs[PreferencesKeys.USER_PRODUCT],
                    isLoggedIn = true
                )
            }
        }

    suspend fun getCookies(): String? = withContext(dispatchers.io) {
        inMemoryCookies ?: dataStore.data.map { it[PreferencesKeys.COOKIES] }.firstOrNull()
    }

    /**
     * Obtains an active Bearer access token using user session cookies (sp_dc)
     */
    suspend fun getAccessToken(): String? = withContext(dispatchers.io) {
        val rawCookies = getCookies() ?: return@withContext null
        tokenManager.getAccessToken(rawCookies)
    }

    /**
     * Saves captured Spotify web session cookies and fetches user profile.
     */
    suspend fun saveCookiesAndSyncProfile(cookies: String): Result<SpotifyAccount> = withContext(dispatchers.io) {
        try {
            val cleanCookie = tokenManager.extractSpDc(cookies)
            inMemoryCookies = cleanCookie
            _spotifySyncStatus.value = ProviderSyncStatus.LoadingProfile

            dataStore.edit { prefs ->
                prefs[PreferencesKeys.COOKIES] = cleanCookie
                prefs[PreferencesKeys.IS_LOGGED_IN] = true
            }

            val profile = spotifyWebClient.fetchUserProfile(cleanCookie)

            dataStore.edit { prefs ->
                prefs[PreferencesKeys.COOKIES] = cleanCookie
                prefs[PreferencesKeys.USER_NAME] = profile.name
                if (profile.email != null) prefs[PreferencesKeys.USER_EMAIL] = profile.email
                if (profile.avatarUrl != null) prefs[PreferencesKeys.USER_AVATAR] = profile.avatarUrl
                if (profile.id != null) prefs[PreferencesKeys.USER_ID] = profile.id
                if (profile.product != null) prefs[PreferencesKeys.USER_PRODUCT] = profile.product
                prefs[PreferencesKeys.IS_LOGGED_IN] = true
            }

            LofiQLogger.i(LofiQLogger.Category.NETWORK, "[ACCOUNT][SPOTIFY] Auth saved for userId=${profile.id} name=${profile.name}")

            // Automatically sync in background
            CoroutineScope(dispatchers.io).launch {
                syncAllSpotifyData()
            }

            Result.success(profile)
        } catch (e: Exception) {
            _spotifySyncStatus.value = ProviderSyncStatus.Failed(e.message ?: "Spotify auth failed")
            LofiQLogger.e(LofiQLogger.Category.NETWORK, "Spotify auth save failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Synchronizes all Spotify data (Profile -> Liked Songs -> Playlists + Tracks)
     */
    suspend fun syncAllSpotifyData(): Boolean = withContext(dispatchers.io) {
        val cookies = getCookies()
        if (cookies.isNullOrBlank()) {
            _spotifySyncStatus.value = ProviderSyncStatus.NotConnected
            return@withContext false
        }

        try {
            _spotifySyncStatus.value = ProviderSyncStatus.LoadingProfile
            val profile = spotifyWebClient.fetchUserProfile(cookies)
            dataStore.edit { prefs ->
                prefs[PreferencesKeys.USER_NAME] = profile.name
                if (profile.email != null) prefs[PreferencesKeys.USER_EMAIL] = profile.email
                if (profile.avatarUrl != null) prefs[PreferencesKeys.USER_AVATAR] = profile.avatarUrl
                if (profile.id != null) prefs[PreferencesKeys.USER_ID] = profile.id
                if (profile.product != null) prefs[PreferencesKeys.USER_PRODUCT] = profile.product
            }

            _spotifySyncStatus.value = ProviderSyncStatus.SyncingLikedSongs(0, 0)
            val likedCount = syncSpotifyLikedSongs { current, total ->
                _spotifySyncStatus.value = ProviderSyncStatus.SyncingLikedSongs(current, total)
            }

            _spotifySyncStatus.value = ProviderSyncStatus.SyncingPlaylists(0, 0)
            val plCount = syncSpotifyPlaylists { current, total ->
                _spotifySyncStatus.value = ProviderSyncStatus.SyncingPlaylists(current, total)
            }

            _spotifySyncStatus.value = ProviderSyncStatus.Completed(
                likedCount = likedCount,
                playlistCount = plCount,
                timestamp = System.currentTimeMillis()
            )
            true
        } catch (e: Exception) {
            _spotifySyncStatus.value = ProviderSyncStatus.Failed(e.message ?: "Spotify sync failed")
            LofiQLogger.e(LofiQLogger.Category.NETWORK, "syncAllSpotifyData failed: ${e.message}", e)
            false
        }
    }

    /**
     * Gets user's Spotify playlists
     */
    suspend fun getPlaylists(): List<SpotifyPlaylist> = withContext(dispatchers.io) {
        val cookies = getCookies() ?: return@withContext emptyList()
        spotifyWebClient.fetchPlaylists(cookies).first
    }

    /**
     * Gets user's Spotify liked songs
     */
    suspend fun getLikedSongs(offset: Int = 0, limit: Int = 50): Pair<List<SpotifyTrack>, Int> = withContext(dispatchers.io) {
        val cookies = getCookies() ?: return@withContext Pair(emptyList(), 0)
        spotifyWebClient.fetchLikedSongs(cookies, offset, limit)
    }

    /**
     * Gets tracks for any playlist (or liked songs)
     */
    suspend fun getPlaylistTracks(playlistId: String, offset: Int = 0, limit: Int = 100): Pair<List<SpotifyTrack>, Int> = withContext(dispatchers.io) {
        val cookies = getCookies() ?: return@withContext Pair(emptyList(), 0)
        spotifyWebClient.fetchPlaylistTracks(cookies, playlistId, offset, limit)
    }

    /**
     * Syncs user's Spotify Liked Songs directly into the Room database Favorites
     */
    suspend fun syncSpotifyLikedSongs(onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }): Int = withContext(dispatchers.io) {
        val cookies = getCookies() ?: return@withContext 0
        var totalSynced = 0
        try {
            var offset = 0
            val limit = 50
            var totalTracks = 1

            while (offset < totalTracks) {
                val (tracks, count) = spotifyWebClient.fetchLikedSongs(cookies, offset, limit)
                totalTracks = count
                if (tracks.isEmpty()) break

                val songEntities = mutableListOf<com.lofiq.core.database.entities.SongEntity>()
                val favEntities = mutableListOf<FavoriteEntity>()
                val now = System.currentTimeMillis()

                for ((idx, track) in tracks.withIndex()) {
                    val song = track.toSong()
                    songEntities.add(song.toEntity())
                    favEntities.add(FavoriteEntity(songId = song.id, addedAt = now - (offset + idx) * 1000L))
                }

                if (songEntities.isNotEmpty()) {
                    songDao.insertOrUpdateAll(songEntities)
                    favoriteDao.addFavorites(favEntities)
                    totalSynced += songEntities.size
                }

                onProgress(totalSynced, totalTracks)
                offset += tracks.size
            }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.NETWORK, "Spotify liked songs sync error: ${e.message}")
        }
        totalSynced
    }

    /**
     * Syncs user's Spotify Playlists directly into Room database Playlists
     */
    suspend fun syncSpotifyPlaylists(onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }): Int = withContext(dispatchers.io) {
        val cookies = getCookies() ?: return@withContext 0
        var playlistsSynced = 0
        try {
            val (playlists, totalPlaylists) = spotifyWebClient.fetchPlaylists(cookies)
            if (playlists.isEmpty()) return@withContext 0

            for (pl in playlists) {
                val localPlaylistId = "spotify_pl_${pl.id}"
                val existingPlaylist = playlistDao.getPlaylistById(localPlaylistId)
                val playlistEntity = PlaylistEntity(
                    id = localPlaylistId,
                    name = pl.name,
                    description = pl.owner?.let { "Created by $it" }.orEmpty(),
                    artworkUrl = pl.imageUrl.orEmpty(),
                    isSystem = false,
                    createdAt = existingPlaylist?.createdAt ?: System.currentTimeMillis()
                )
                playlistDao.insertPlaylist(playlistEntity)

                try {
                    val (tracks, _) = spotifyWebClient.fetchPlaylistTracks(cookies, pl.id, 0, 100)
                    if (tracks.isNotEmpty()) {
                        val crossRefs = mutableListOf<PlaylistSongCrossRef>()
                        val playlistSongs = mutableListOf<com.lofiq.core.database.entities.SongEntity>()

                        for ((index, track) in tracks.withIndex()) {
                            val song = track.toSong()
                            playlistSongs.add(song.toEntity())
                            crossRefs.add(
                                PlaylistSongCrossRef(
                                    playlistId = localPlaylistId,
                                    songId = song.id,
                                    position = index,
                                    addedAt = System.currentTimeMillis()
                                )
                            )
                        }

                        songDao.insertOrUpdateAll(playlistSongs)
                        playlistDao.clearPlaylistSongs(localPlaylistId)
                        playlistDao.addSongsToPlaylist(crossRefs)
                    }
                } catch (e: Exception) {
                    LofiQLogger.w(LofiQLogger.Category.NETWORK, "Playlist track sync failed for ${pl.name}: ${e.message}")
                }

                playlistsSynced++
                onProgress(playlistsSynced, totalPlaylists)
            }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.NETWORK, "Spotify playlists sync error: ${e.message}")
        }
        playlistsSynced
    }

    /**
     * Disconnects Spotify account and clears stored credentials.
     */
    suspend fun signOut() = withContext(dispatchers.io) {
        dataStore.edit { prefs ->
            prefs.clear()
        }
        inMemoryCookies = null
        _spotifySyncStatus.value = ProviderSyncStatus.NotConnected
        tokenManager.clearSession()
        LofiQLogger.i(LofiQLogger.Category.NETWORK, "Spotify account disconnected")
    }
}

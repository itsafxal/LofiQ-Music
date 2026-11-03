package com.lofiq.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lofiq.auth.AuthRepository
import com.lofiq.auth.UserAccount
import com.lofiq.core.model.Playlist
import com.lofiq.core.model.Song
import com.lofiq.data.repository.LibraryRepository
import com.lofiq.playback.engine.PlaybackEngine
import com.lofiq.data.spotify.SpotifyImportHelper
import com.lofiq.data.download.TrackDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryTab(val label: String) {
    FAVORITES("Liked"),
    DOWNLOADS("Downloads"),
    PLAYLISTS("Playlists"),
    LOCAL("Local Music")
}

enum class LibraryView {
    HOME,
    LIKED_SONGS,
    DOWNLOADS,
    PLAYLISTS
}

enum class ProviderFilter(val label: String) {
    ALL("All"),
    SPOTIFY("Spotify"),
    YOUTUBE("YouTube"),
    LOFIQ("LofiQ")
}

typealias LikedFilter = ProviderFilter
typealias PlaylistFilter = ProviderFilter

data class LibraryUiState(
    val currentView: LibraryView = LibraryView.HOME,
    val selectedTab: LibraryTab = LibraryTab.FAVORITES,
    val selectedLikedFilter: ProviderFilter = ProviderFilter.ALL,
    val selectedPlaylistFilter: ProviderFilter = ProviderFilter.ALL,
    val favorites: List<Song> = emptyList(),
    val downloads: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val history: List<Song> = emptyList(),
    val selectedPlaylist: Playlist? = null,
    val selectedPlaylistSongs: List<Song> = emptyList(),
    val minutesListened: Int = 101,
    val totalPlays: Int = 32,
    val isSyncing: Boolean = false,
    val spotifyPlaylists: List<com.lofiq.data.spotify.SpotifyPlaylist> = emptyList(),
    val spotifyLikedCount: Int = 0,
    val isSpotifyLoading: Boolean = false,
    val selectedSpotifyPlaylist: com.lofiq.data.spotify.SpotifyPlaylist? = null,
    val selectedSpotifySongs: List<Song> = emptyList(),
    val isSpotifySongsLoading: Boolean = false
) {
    val filteredFavorites: List<Song>
        get() = getFilteredFavorites(isYouTubeLoggedIn = false)

    fun getFilteredFavorites(isYouTubeLoggedIn: Boolean): List<Song> {
        return when (selectedLikedFilter) {
            ProviderFilter.ALL -> {
                val seen = mutableSetOf<String>()
                favorites.filter { song ->
                    val key = if (song.title.isNotBlank()) {
                        "${song.title.lowercase().trim()}_${song.artist.lowercase().trim()}"
                    } else {
                        song.id
                    }
                    seen.add(key)
                }
            }
            ProviderFilter.SPOTIFY -> {
                favorites.filter {
                    it.providerId == "spotify" || it.id.startsWith("sp_")
                }
            }
            ProviderFilter.YOUTUBE -> {
                favorites.filter {
                    !it.id.startsWith("sp_") && !it.id.startsWith("local_") && it.providerId != "local_download" && it.providerId != "spotify" && it.providerId != "local"
                }
            }
            ProviderFilter.LOFIQ -> {
                if (isYouTubeLoggedIn) {
                    favorites.filter {
                        it.providerId == "local" || it.providerId == "local_download" || it.id.startsWith("local_")
                    }
                } else {
                    favorites.filter {
                        !it.id.startsWith("sp_") && it.providerId != "spotify"
                    }
                }
            }
        }
    }

    val filteredPlaylists: List<Playlist>
        get() = getFilteredPlaylists(isYouTubeLoggedIn = false)

    fun getFilteredPlaylists(isYouTubeLoggedIn: Boolean): List<Playlist> {
        return when (selectedPlaylistFilter) {
            ProviderFilter.ALL -> playlists
            ProviderFilter.SPOTIFY -> {
                playlists.filter {
                    it.id.startsWith("spotify_pl_") || it.description.contains("Spotify", ignoreCase = true)
                }
            }
            ProviderFilter.YOUTUBE -> {
                playlists.filter {
                    val isSp = it.id.startsWith("spotify_pl_") || it.description.contains("Spotify", ignoreCase = true)
                    !isSp && (it.id.startsWith("yt_pl_") || it.description.contains("YouTube", ignoreCase = true) || it.id.startsWith("VL") || it.id.startsWith("PL") || it.id.startsWith("RD") || (isYouTubeLoggedIn && !it.isSystem))
                }
            }
            ProviderFilter.LOFIQ -> {
                playlists.filter {
                    val isSp = it.id.startsWith("spotify_pl_") || it.description.contains("Spotify", ignoreCase = true)
                    val isYt = it.id.startsWith("yt_pl_") || it.description.contains("YouTube", ignoreCase = true) || it.id.startsWith("VL") || it.id.startsWith("PL") || it.id.startsWith("RD")
                    !isSp && !isYt
                }
            }
        }
    }
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val authRepository: AuthRepository,
    val spotifyAuthRepository: com.lofiq.data.spotify.SpotifyAuthRepository,
    val spotifyImportHelper: SpotifyImportHelper,
    val firestoreSyncRepository: com.lofiq.data.firebase.FirestoreSyncRepository,
    val firebaseAuthRepository: com.lofiq.data.firebase.FirebaseAuthRepository,
    val playbackEngine: PlaybackEngine,
    val trackDownloadManager: TrackDownloadManager
) : ViewModel() {

    val userAccount: StateFlow<UserAccount> = authRepository.userAccount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserAccount.GUEST
        )

    val spotifyAccount: StateFlow<com.lofiq.data.spotify.SpotifyAccount> = spotifyAuthRepository.spotifyAccount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = com.lofiq.data.spotify.SpotifyAccount.GUEST
        )

    private val _isAuthenticating = MutableStateFlow(false)
    val isAuthenticating: StateFlow<Boolean> = _isAuthenticating.asStateFlow()

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        observeData()
        syncLibrary()
    }

    private fun observeData() {
        viewModelScope.launch {
            libraryRepository.observeFavorites().collect { favs ->
                _uiState.update { it.copy(favorites = favs) }
            }
        }
        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            libraryRepository.observeDownloads()
                .distinctUntilChanged()
                .debounce(150L)
                .collect { dls ->
                    val deduped = dls.distinctBy { it.id }
                    _uiState.update { it.copy(downloads = deduped) }
                }
        }
        viewModelScope.launch {
            libraryRepository.observePlaylists().collect { playlists ->
                _uiState.update { it.copy(playlists = playlists) }
            }
        }
        viewModelScope.launch {
            libraryRepository.observeHistory().collect { hist ->
                val durationMins = hist.sumOf { it.durationSeconds } / 60
                val computedMins: Int = if (durationMins > 0) durationMins.toInt() else maxOf(hist.size * 3 + 12, 101)
                val plays: Int = maxOf(hist.size, 32)
                _uiState.update {
                    it.copy(
                        history = hist,
                        minutesListened = computedMins,
                        totalPlays = plays
                    )
                }
            }
        }
        viewModelScope.launch {
            firebaseAuthRepository.currentUser.collect { user ->
                if (user != null && !user.isAnonymous) {
                    firestoreSyncRepository.syncFromCloud()
                }
            }
        }
        viewModelScope.launch {
            userAccount.collect { account ->
                if (account.isLoggedIn) {
                    syncLibrary()
                }
            }
        }
        viewModelScope.launch {
            spotifyAccount.collect { account ->
                if (account.isLoggedIn) {
                    loadSpotifyLibrary()
                    syncLibrary()
                }
            }
        }
    }

    fun syncLibrary() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }

            // 1. Firebase Firestore Cloud Sync
            val syncFirebase = launch {
                try {
                    firestoreSyncRepository.syncFromCloud()
                } catch (_: Exception) {}
            }

            // 2. YouTube Music Sync
            val ytCookies = authRepository.getCookies()
            val syncYt = launch {
                if (!ytCookies.isNullOrBlank()) {
                    libraryRepository.syncRemoteLikedSongs(ytCookies)
                    libraryRepository.syncRemotePlaylists(ytCookies)
                }
            }

            // 3. Spotify Sync
            val spCookies = spotifyAuthRepository.getCookies()
            val syncSp = launch {
                if (!spCookies.isNullOrBlank()) {
                    spotifyAuthRepository.syncSpotifyLikedSongs()
                    spotifyAuthRepository.syncSpotifyPlaylists()
                }
            }

            // 4. Local Downloads Reconciliation
            val syncDownloads = launch {
                try {
                    trackDownloadManager.reconcileDownloads()
                } catch (_: Exception) {}
            }

            syncFirebase.join()
            syncYt.join()
            syncSp.join()
            syncDownloads.join()

            _uiState.update { it.copy(isSyncing = false) }
        }
    }

    fun navigateToView(view: LibraryView) {
        _uiState.update { it.copy(currentView = view, selectedPlaylist = null, selectedSpotifyPlaylist = null) }
    }

    fun navigateBackToHome() {
        _uiState.update {
            it.copy(
                currentView = LibraryView.HOME,
                selectedPlaylist = null,
                selectedPlaylistSongs = emptyList(),
                selectedSpotifyPlaylist = null,
                selectedSpotifySongs = emptyList()
            )
        }
    }

    fun loadSpotifyLibrary() {
        viewModelScope.launch {
            val cookies = spotifyAuthRepository.getCookies() ?: return@launch
            _uiState.update { it.copy(isSpotifyLoading = true) }
            try {
                val likedJob = launch {
                    val (_, count) = spotifyAuthRepository.getLikedSongs(limit = 1)
                    _uiState.update { it.copy(spotifyLikedCount = count) }
                }
                val playlistsJob = launch {
                    val playlists = spotifyAuthRepository.getPlaylists()
                    _uiState.update { it.copy(spotifyPlaylists = playlists) }
                }
                likedJob.join()
                playlistsJob.join()
            } catch (e: Exception) {
                com.lofiq.core.common.LofiQLogger.w(
                    com.lofiq.core.common.LofiQLogger.Category.NETWORK,
                    "Failed to load Spotify library: ${e.message}"
                )
            } finally {
                _uiState.update { it.copy(isSpotifyLoading = false) }
            }
        }
    }

    fun openSpotifyPlaylist(playlist: com.lofiq.data.spotify.SpotifyPlaylist) {
        _uiState.update {
            it.copy(
                selectedSpotifyPlaylist = playlist,
                selectedSpotifySongs = emptyList(),
                isSpotifySongsLoading = true
            )
        }
        viewModelScope.launch {
            try {
                val (tracks, _) = spotifyAuthRepository.getPlaylistTracks(playlist.id, offset = 0, limit = 100)
                val songs = tracks.map { it.toSong() }
                _uiState.update {
                    it.copy(
                        selectedSpotifySongs = songs,
                        isSpotifySongsLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSpotifySongsLoading = false) }
            }
        }
    }

    fun openSpotifyLikedSongs() {
        val count = _uiState.value.spotifyLikedCount
        val likedPlaylist = com.lofiq.data.spotify.SpotifyPlaylist(
            id = com.lofiq.data.spotify.SpotifyWebClient.LIKED_ID,
            name = "Liked Songs",
            owner = "Spotify",
            imageUrl = com.lofiq.data.spotify.SpotifyWebClient.LIKED_COVER,
            trackCount = count
        )
        openSpotifyPlaylist(likedPlaylist)
    }

    fun closeSpotifyPlaylist() {
        _uiState.update {
            it.copy(
                selectedSpotifyPlaylist = null,
                selectedSpotifySongs = emptyList(),
                isSpotifySongsLoading = false
            )
        }
    }

    fun selectTab(tab: LibraryTab) {
        val view = when (tab) {
            LibraryTab.FAVORITES -> LibraryView.LIKED_SONGS
            LibraryTab.DOWNLOADS -> LibraryView.DOWNLOADS
            LibraryTab.PLAYLISTS -> LibraryView.PLAYLISTS
            LibraryTab.LOCAL -> LibraryView.HOME
        }
        _uiState.update { it.copy(selectedTab = tab, currentView = view, selectedPlaylist = null) }
    }

    fun selectLikedFilter(filter: ProviderFilter) {
        _uiState.update { it.copy(selectedLikedFilter = filter) }
    }

    fun selectPlaylistFilter(filter: ProviderFilter) {
        _uiState.update { it.copy(selectedPlaylistFilter = filter) }
    }

    fun refreshDownloads() {
        viewModelScope.launch {
            trackDownloadManager.reconcileDownloads()
        }
    }

    fun deleteDownload(song: Song) {
        trackDownloadManager.deleteDownload(song.id)
    }

    fun downloadSong(song: Song) {
        trackDownloadManager.downloadSong(song)
    }

    fun openPlaylist(playlist: Playlist) {
        _uiState.update { it.copy(selectedPlaylist = playlist) }
        viewModelScope.launch {
            libraryRepository.observePlaylistSongs(playlist.id).collect { songs ->
                _uiState.update { it.copy(selectedPlaylistSongs = songs) }
            }
        }
    }

    fun closePlaylist() {
        _uiState.update { it.copy(selectedPlaylist = null, selectedPlaylistSongs = emptyList()) }
    }

    fun playSong(song: Song, queue: List<Song>) {
        val index = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        playbackEngine.playQueue(queue, index)
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            libraryRepository.createPlaylist(name.trim())
        }
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            libraryRepository.toggleFavorite(song)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            libraryRepository.clearHistory()
        }
    }

    fun onAuthCookiesCaptured(cookies: String) {
        viewModelScope.launch {
            _isAuthenticating.value = true
            _uiState.update { it.copy(isSyncing = true) }
            val result = authRepository.saveCookiesAndSyncProfile(cookies)
            if (result.isSuccess) {
                val syncLiked = launch { libraryRepository.syncRemoteLikedSongs(cookies) }
                val syncPlaylists = launch { libraryRepository.syncRemotePlaylists(cookies) }
                syncLiked.join()
                syncPlaylists.join()
            }
            _uiState.update { it.copy(isSyncing = false) }
            _isAuthenticating.value = false
        }
    }

    fun refreshAuthProfile() {
        viewModelScope.launch {
            val cookies = authRepository.getCookies() ?: return@launch
            _isAuthenticating.value = true
            _uiState.update { it.copy(isSyncing = true) }
            authRepository.saveCookiesAndSyncProfile(cookies)
            val syncLiked = launch { libraryRepository.syncRemoteLikedSongs(cookies) }
            val syncPlaylists = launch { libraryRepository.syncRemotePlaylists(cookies) }
            syncLiked.join()
            syncPlaylists.join()
            _uiState.update { it.copy(isSyncing = false) }
            _isAuthenticating.value = false
        }
    }

    fun syncYouTubeData() {
        viewModelScope.launch {
            val cookies = authRepository.getCookies() ?: return@launch
            _uiState.update { it.copy(isSyncing = true) }
            val syncLiked = launch { libraryRepository.syncRemoteLikedSongs(cookies) }
            val syncPlaylists = launch { libraryRepository.syncRemotePlaylists(cookies) }
            syncLiked.join()
            syncPlaylists.join()
            _uiState.update { it.copy(isSyncing = false) }
        }
    }

    fun signOutAuth() {
        viewModelScope.launch {
            authRepository.signOut()
            _uiState.update {
                it.copy(
                    selectedLikedFilter = ProviderFilter.ALL,
                    selectedPlaylistFilter = ProviderFilter.ALL
                )
            }
        }
    }

    fun onSpotifyCookiesCaptured(cookies: String) {
        viewModelScope.launch {
            _isAuthenticating.value = true
            val result = spotifyAuthRepository.saveCookiesAndSyncProfile(cookies)
            if (result.isSuccess) {
                val syncLiked = launch { spotifyAuthRepository.syncSpotifyLikedSongs() }
                val syncPlaylists = launch { spotifyAuthRepository.syncSpotifyPlaylists() }
                syncLiked.join()
                syncPlaylists.join()
                syncLibrary()
            }
            _isAuthenticating.value = false
        }
    }

    fun syncSpotifyData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            val syncLiked = launch { spotifyAuthRepository.syncSpotifyLikedSongs() }
            val syncPlaylists = launch { spotifyAuthRepository.syncSpotifyPlaylists() }
            syncLiked.join()
            syncPlaylists.join()
            _uiState.update { it.copy(isSyncing = false) }
        }
    }

    fun signOutSpotify() {
        viewModelScope.launch {
            spotifyAuthRepository.signOut()
            _uiState.update {
                it.copy(
                    currentView = LibraryView.HOME,
                    selectedLikedFilter = ProviderFilter.ALL,
                    selectedPlaylistFilter = ProviderFilter.ALL,
                    spotifyPlaylists = emptyList(),
                    spotifyLikedCount = 0,
                    selectedSpotifyPlaylist = null,
                    selectedSpotifySongs = emptyList()
                )
            }
            syncLibrary()
        }
    }
}

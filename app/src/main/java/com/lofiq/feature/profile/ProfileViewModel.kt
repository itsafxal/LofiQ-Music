package com.lofiq.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lofiq.auth.AuthRepository
import com.lofiq.auth.UserAccount
import com.lofiq.core.datastore.LofiQPreferences
import com.lofiq.data.repository.LibraryRepository
import com.lofiq.data.spotify.SpotifyAuthRepository
import com.lofiq.data.spotify.SpotifyImportHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    val authRepository: AuthRepository,
    val libraryRepository: LibraryRepository,
    val spotifyAuthRepository: SpotifyAuthRepository,
    val spotifyImportHelper: SpotifyImportHelper,
    private val preferences: LofiQPreferences
) : ViewModel() {

    val isYouTubeBannerPermanentlyDismissed: StateFlow<Boolean> = preferences.isYouTubeBannerPermanentlyDismissed
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    fun resetYouTubeBanner() {
        viewModelScope.launch {
            preferences.setYouTubeBannerPermanentlyDismissed(false)
        }
    }

    private val _isYouTubeSyncing = MutableStateFlow(false)
    val isYouTubeSyncing: StateFlow<Boolean> = _isYouTubeSyncing.asStateFlow()

    private val _isSpotifySyncing = MutableStateFlow(false)
    val isSpotifySyncing: StateFlow<Boolean> = _isSpotifySyncing.asStateFlow()

    fun onYouTubeCookiesCaptured(cookies: String, onComplete: ((Boolean, UserAccount) -> Unit)? = null) {
        viewModelScope.launch {
            _isYouTubeSyncing.value = true
            val result = authRepository.saveCookiesAndSyncProfile(cookies)
            if (result.isSuccess) {
                libraryRepository.syncRemoteLikedSongs(cookies)
                libraryRepository.syncRemotePlaylists(cookies)
            }
            _isYouTubeSyncing.value = false
            onComplete?.invoke(result.isSuccess, result.getOrDefault(UserAccount.GUEST))
        }
    }

    fun syncYouTubeLibrary(onComplete: (likedCount: Int, playlistCount: Int) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val cookies = authRepository.getCookies()
            if (!cookies.isNullOrBlank()) {
                _isYouTubeSyncing.value = true
                val liked = libraryRepository.syncRemoteLikedSongs(cookies)
                val playlists = libraryRepository.syncRemotePlaylists(cookies)
                _isYouTubeSyncing.value = false
                onComplete(liked, playlists)
            } else {
                onComplete(0, 0)
            }
        }
    }

    fun onSpotifyCookiesCaptured(cookies: String, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            _isSpotifySyncing.value = true
            val result = spotifyAuthRepository.saveCookiesAndSyncProfile(cookies)
            if (result.isSuccess) {
                spotifyAuthRepository.syncSpotifyLikedSongs()
                spotifyAuthRepository.syncSpotifyPlaylists()
            }
            _isSpotifySyncing.value = false
            onComplete?.invoke(result.isSuccess)
        }
    }

    fun syncSpotifyLibrary(onComplete: (likedCount: Int, playlistCount: Int) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _isSpotifySyncing.value = true
            val liked = spotifyAuthRepository.syncSpotifyLikedSongs()
            val playlists = spotifyAuthRepository.syncSpotifyPlaylists()
            _isSpotifySyncing.value = false
            onComplete(liked, playlists)
        }
    }

    fun signOutSpotify() {
        viewModelScope.launch {
            spotifyAuthRepository.signOut()
        }
    }
}

package com.lofiq.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lofiq.auth.AuthRepository
import com.lofiq.auth.UserAccount
import com.lofiq.core.model.Song
import com.lofiq.data.repository.LibraryRepository
import com.lofiq.data.repository.MusicRepository
import com.lofiq.playback.engine.PlaybackEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExploreUiState(
    val selectedMood: MoodItem? = null,
    val moodSongs: List<Song> = emptyList(),
    val isLoadingMoodSongs: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val libraryRepository: LibraryRepository,
    private val authRepository: AuthRepository,
    val playbackEngine: PlaybackEngine
) : ViewModel() {

    val userAccount: StateFlow<UserAccount> = authRepository.userAccount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserAccount.GUEST
        )

    private val _isAuthenticating = MutableStateFlow(false)
    val isAuthenticating: StateFlow<Boolean> = _isAuthenticating.asStateFlow()

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    // Cache mood songs so returning is instant
    private val moodCache = mutableMapOf<String, List<Song>>()

    fun selectMood(mood: MoodItem) {
        _uiState.update { it.copy(selectedMood = mood, isLoadingMoodSongs = true, errorMessage = null) }
        
        val cached = moodCache[mood.id]
        if (cached != null && cached.isNotEmpty()) {
            _uiState.update { it.copy(moodSongs = cached, isLoadingMoodSongs = false) }
            return
        }

        viewModelScope.launch {
            try {
                val songs = musicRepository.searchSongs(mood.searchQuery, limit = 35)
                moodCache[mood.id] = songs
                _uiState.update {
                    it.copy(
                        moodSongs = songs,
                        isLoadingMoodSongs = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingMoodSongs = false,
                        errorMessage = "Could not load songs for ${mood.title}"
                    )
                }
            }
        }
    }

    fun clearSelectedMood() {
        _uiState.update { it.copy(selectedMood = null, moodSongs = emptyList()) }
    }

    fun playSong(song: Song, queue: List<Song>) {
        val index = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        playbackEngine.playQueue(queue, index)
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            libraryRepository.toggleFavorite(song)
        }
    }

    fun refreshProfile() {
        viewModelScope.launch {
            val cookies = authRepository.getCookies() ?: return@launch
            _isAuthenticating.value = true
            authRepository.saveCookiesAndSyncProfile(cookies)
            launch { libraryRepository.syncRemoteLikedSongs(cookies) }
            launch { libraryRepository.syncRemotePlaylists(cookies) }
            _isAuthenticating.value = false
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }

    fun onCookiesCaptured(cookies: String) {
        viewModelScope.launch {
            _isAuthenticating.value = true
            val result = authRepository.saveCookiesAndSyncProfile(cookies)
            if (result.isSuccess) {
                launch { libraryRepository.syncRemoteLikedSongs(cookies) }
                launch { libraryRepository.syncRemotePlaylists(cookies) }
            }
            _isAuthenticating.value = false
        }
    }
}

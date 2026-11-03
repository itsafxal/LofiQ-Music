package com.lofiq.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lofiq.data.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    val userAccount: StateFlow<UserAccount> = authRepository.userAccount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserAccount.GUEST
        )

    private val _isAuthenticating = MutableStateFlow(false)
    val isAuthenticating: StateFlow<Boolean> = _isAuthenticating.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    fun onCookiesCaptured(cookies: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isAuthenticating.value = true
            _authError.value = null
            val result = authRepository.saveCookiesAndSyncProfile(cookies)
            if (result.isSuccess) {
                // Sync remote liked songs and playlists into local library
                launch { libraryRepository.syncRemoteLikedSongs(cookies) }
                launch { libraryRepository.syncRemotePlaylists(cookies) }
                onComplete()
            } else {
                _authError.value = result.exceptionOrNull()?.message ?: "Failed to authenticate"
            }
            _isAuthenticating.value = false
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

    fun clearError() {
        _authError.value = null
    }
}

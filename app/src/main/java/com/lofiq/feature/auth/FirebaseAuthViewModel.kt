package com.lofiq.feature.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.lofiq.auth.AuthRepository
import com.lofiq.core.common.LofiQLogger
import com.lofiq.data.firebase.FirebaseAuthRepository
import com.lofiq.data.firebase.FirebaseUserProfile
import com.lofiq.data.firebase.FirestoreSyncRepository
import com.lofiq.data.spotify.SpotifyAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthMode {
    SIGN_IN,
    SIGN_UP
}

data class AuthUiState(
    val authMode: AuthMode = AuthMode.SIGN_IN,
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false
)

private const val GOOGLE_WEB_CLIENT_ID = "73682493381-vttrmjnn3e44lrugutv5bgarp2r6gcie.apps.googleusercontent.com"

@HiltViewModel
class FirebaseAuthViewModel @Inject constructor(
    val firebaseAuthRepository: FirebaseAuthRepository,
    val firestoreSyncRepository: FirestoreSyncRepository,
    private val authRepository: AuthRepository,
    private val spotifyAuthRepository: SpotifyAuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    val currentUser: StateFlow<FirebaseUserProfile?> = firebaseAuthRepository.currentUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isOnboardingCompleted: StateFlow<Boolean> = firebaseAuthRepository.isOnboardingCompleted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setAuthMode(mode: AuthMode) {
        _uiState.update { it.copy(authMode = mode, errorMessage = null) }
    }

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, errorMessage = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun onDisplayNameChange(name: String) {
        _uiState.update { it.copy(displayName = name, errorMessage = null) }
    }

    fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(GOOGLE_WEB_CLIENT_ID)
            .requestEmail()
            .requestProfile()
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun handleGoogleSignInResult(account: GoogleSignInAccount?, exception: ApiException?) {
        if (account != null) {
            val idToken = account.idToken
            if (!idToken.isNullOrBlank()) {
                viewModelScope.launch {
                    _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                    val result = firebaseAuthRepository.signInWithGoogleToken(idToken)
                    if (result.isSuccess) {
                        onAuthCompletedSuccessfully()
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Google Sign-In failed"
                            )
                        }
                    }
                }
            } else {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Google authentication token not found") }
            }
        } else {
            val statusCode = exception?.statusCode
            val msg = when (statusCode) {
                10 -> "Google Sign-In setup error (Code 10). Add SHA-1 debug fingerprint in Firebase Console."
                12500 -> "Google Sign-In failed (Code 12500). Please check your Google account."
                7 -> "Network error during Google Sign-In. Check your connection."
                16, 12501 -> "Google Sign-In was cancelled."
                null -> "Google Sign-In was cancelled."
                else -> exception.localizedMessage ?: "Google Sign-In failed ($statusCode)"
            }
            _uiState.update { it.copy(isLoading = false, errorMessage = msg) }
        }
    }

    fun submitEmailAuth() {
        val state = _uiState.value
        if (state.email.isBlank() || !state.email.contains("@")) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid email address") }
            return
        }
        if (state.password.length < 6) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 6 characters") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = if (state.authMode == AuthMode.SIGN_IN) {
                firebaseAuthRepository.signInWithEmail(state.email, state.password)
            } else {
                firebaseAuthRepository.signUpWithEmail(state.email, state.password, state.displayName)
            }

            if (result.isSuccess) {
                onAuthCompletedSuccessfully()
            } else {
                val error = result.exceptionOrNull()
                val rawMsg = error?.localizedMessage.orEmpty()
                val friendlyMsg = when {
                    rawMsg.contains("invalid-credential", ignoreCase = true) ||
                    rawMsg.contains("credential is incorrect", ignoreCase = true) -> {
                        if (state.authMode == AuthMode.SIGN_IN) {
                            "Incorrect password, or account doesn't exist yet. Try 'Create Account' tab."
                        } else {
                            "Invalid credentials provided."
                        }
                    }
                    rawMsg.contains("email-already-in-use", ignoreCase = true) ||
                    rawMsg.contains("already in use", ignoreCase = true) ->
                        "An account with this email already exists. Please switch to 'Sign In'."
                    rawMsg.contains("network", ignoreCase = true) ->
                        "Network error. Please check your internet connection."
                    else -> error?.localizedMessage ?: "Authentication failed"
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = friendlyMsg
                    )
                }
            }
        }
    }

    fun continueAsGuest() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = firebaseAuthRepository.continueAsGuest()
            if (result.isSuccess) {
                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
            } else {
                val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Guest login failed"
                _uiState.update { it.copy(isLoading = false, errorMessage = errorMsg) }
            }
        }
    }

    private suspend fun onAuthCompletedSuccessfully() {
        // 1. Sync in-app liked songs & custom playlists from Firestore
        firestoreSyncRepository.syncFromCloud()

        // 2. Connected account sessions (YouTube Music / Spotify) are managed strictly via
        // device-local DataStore sandbox storage, eliminating external cloud session dependency.

        _uiState.update { it.copy(isLoading = false, isSuccess = true) }
    }

    fun syncFromCloud() {
        viewModelScope.launch {
            firestoreSyncRepository.syncFromCloud()
        }
    }

    fun resetAuthState() {
        _uiState.value = AuthUiState()
    }

    fun signOut(context: Context? = null) {
        viewModelScope.launch {
            try {
                if (context != null) {
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(GOOGLE_WEB_CLIENT_ID)
                        .requestEmail()
                        .build()
                    GoogleSignIn.getClient(context, gso).signOut()
                }
            } catch (e: Exception) {
                LofiQLogger.w(LofiQLogger.Category.APP, "GoogleSignIn signOut error: ${e.message}")
            }
            firebaseAuthRepository.signOut()
            resetAuthState()
        }
    }
}

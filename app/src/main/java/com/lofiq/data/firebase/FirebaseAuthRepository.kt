package com.lofiq.data.firebase

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.common.LofiQLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider
) {
    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { context.preferencesDataStoreFile("lofiq_user_onboarding_prefs") }
    )

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("is_onboarding_completed")
    }

    val isOnboardingCompleted: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[Keys.ONBOARDING_COMPLETED] ?: false }

    val currentUser: Flow<FirebaseUserProfile?> = callbackFlow {
        val authStateListener = FirebaseAuth.AuthStateListener { auth ->
            val user = auth.currentUser
            if (user != null) {
                val profile = FirebaseUserProfile(
                    uid = user.uid,
                    displayName = user.displayName ?: if (user.isAnonymous) "Guest Listener" else "LofiQ User",
                    email = user.email,
                    photoUrl = user.photoUrl?.toString(),
                    isAnonymous = user.isAnonymous
                )
                trySend(profile)
            } else {
                trySend(null)
            }
        }
        firebaseAuth.addAuthStateListener(authStateListener)
        awaitClose { firebaseAuth.removeAuthStateListener(authStateListener) }
    }.flowOn(dispatchers.io)

    suspend fun setOnboardingCompleted(completed: Boolean = true) {
        withContext(dispatchers.io) {
            dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
        }
    }

    suspend fun signInWithGoogleToken(idToken: String): Result<FirebaseUserProfile> = withContext(dispatchers.io) {
        try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val user = authResult.user ?: throw IllegalStateException("Firebase user is null after Google sign-in")
            
            val profile = FirebaseUserProfile(
                uid = user.uid,
                displayName = user.displayName ?: "LofiQ User",
                email = user.email,
                photoUrl = user.photoUrl?.toString(),
                isAnonymous = false
            )
            saveUserToFirestore(user)
            setOnboardingCompleted(true)
            LofiQLogger.i(LofiQLogger.Category.APP, "Google Firebase Sign-In successful for: ${profile.displayName}")
            Result.success(profile)
        } catch (e: Exception) {
            LofiQLogger.e(LofiQLogger.Category.APP, "Google Sign-In failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun signInWithEmail(email: String, pass: String): Result<FirebaseUserProfile> = withContext(dispatchers.io) {
        try {
            val authResult = firebaseAuth.signInWithEmailAndPassword(email.trim(), pass).await()
            val user = authResult.user ?: throw IllegalStateException("Firebase user is null after email sign-in")
            val profile = FirebaseUserProfile(
                uid = user.uid,
                displayName = user.displayName ?: email.substringBefore("@"),
                email = user.email,
                photoUrl = user.photoUrl?.toString(),
                isAnonymous = false
            )
            saveUserToFirestore(user)
            setOnboardingCompleted(true)
            LofiQLogger.i(LofiQLogger.Category.APP, "Email Firebase Sign-In successful for: ${profile.displayName}")
            Result.success(profile)
        } catch (e: Exception) {
            LofiQLogger.e(LofiQLogger.Category.APP, "Email Sign-In failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun signUpWithEmail(email: String, pass: String, name: String): Result<FirebaseUserProfile> = withContext(dispatchers.io) {
        try {
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email.trim(), pass).await()
            val user = authResult.user ?: throw IllegalStateException("Firebase user is null after email registration")
            
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(name.ifBlank { email.substringBefore("@") })
                .build()
            user.updateProfile(profileUpdates).await()

            val profile = FirebaseUserProfile(
                uid = user.uid,
                displayName = name.ifBlank { email.substringBefore("@") },
                email = user.email,
                photoUrl = null,
                isAnonymous = false
            )
            saveUserToFirestore(user)
            setOnboardingCompleted(true)
            LofiQLogger.i(LofiQLogger.Category.APP, "Email Firebase Sign-Up successful for: ${profile.displayName}")
            Result.success(profile)
        } catch (e: Exception) {
            LofiQLogger.e(LofiQLogger.Category.APP, "Email Sign-Up failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun continueAsGuest(): Result<FirebaseUserProfile> = withContext(dispatchers.io) {
        try {
            val authResult = firebaseAuth.signInAnonymously().await()
            val user = authResult.user ?: throw IllegalStateException("Firebase anonymous user is null")
            val profile = FirebaseUserProfile(
                uid = user.uid,
                displayName = "Guest Listener",
                email = null,
                photoUrl = null,
                isAnonymous = true
            )
            setOnboardingCompleted(true)
            LofiQLogger.i(LofiQLogger.Category.APP, "Anonymous Guest login successful")
            Result.success(profile)
        } catch (e: Exception) {
            // Even if offline, mark onboarding completed so guest can proceed
            setOnboardingCompleted(true)
            Result.success(FirebaseUserProfile(uid = "guest_${System.currentTimeMillis()}", displayName = "Guest Listener", isAnonymous = true))
        }
    }

    suspend fun signOut() = withContext(dispatchers.io) {
        firebaseAuth.signOut()
        LofiQLogger.i(LofiQLogger.Category.APP, "User signed out from Firebase Auth")
    }

    fun getCurrentUid(): String? = firebaseAuth.currentUser?.uid

    private fun saveUserToFirestore(user: FirebaseUser) {
        val userMap = hashMapOf(
            "uid" to user.uid,
            "displayName" to (user.displayName ?: ""),
            "email" to (user.email ?: ""),
            "photoUrl" to (user.photoUrl?.toString() ?: ""),
            "lastLoginAt" to System.currentTimeMillis()
        )
        firestore.collection("users").document(user.uid)
            .set(userMap, SetOptions.merge())
            .addOnFailureListener { e ->
                LofiQLogger.w(LofiQLogger.Category.APP, "Failed to update user profile in Firestore: ${e.message}")
            }
    }
}

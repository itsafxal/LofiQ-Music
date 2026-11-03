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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    internal var firebaseAuthInstance: FirebaseAuth? = null
    private val firebaseAuth: FirebaseAuth
        get() = firebaseAuthInstance ?: FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val authMutex = Mutex()
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

    suspend fun getOrSignInAnonymous(): Result<String> = withContext(dispatchers.io) {
        val existing = firebaseAuth.currentUser
        if (existing != null) {
            return@withContext Result.success(existing.uid)
        }
        authMutex.withLock {
            val doubleCheck = firebaseAuth.currentUser
            if (doubleCheck != null) {
                return@withContext Result.success(doubleCheck.uid)
            }
            try {
                val authResult = firebaseAuth.signInAnonymously().await()
                val user = authResult.user ?: throw IllegalStateException("Firebase user is null after anonymous sign-in")
                LofiQLogger.i(LofiQLogger.Category.APP, "Anonymous Firebase sign-in successful: ${user.uid}")
                Result.success(user.uid)
            } catch (e: Exception) {
                LofiQLogger.e(LofiQLogger.Category.APP, "Anonymous Firebase sign-in failed: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    suspend fun continueAsGuest(): Result<FirebaseUserProfile> = withContext(dispatchers.io) {
        val authResult = getOrSignInAnonymous()
        if (authResult.isSuccess) {
            val uid = authResult.getOrThrow()
            val user = firebaseAuth.currentUser
            val profile = FirebaseUserProfile(
                uid = uid,
                displayName = user?.displayName ?: "Guest Listener",
                email = user?.email,
                photoUrl = user?.photoUrl?.toString(),
                isAnonymous = user?.isAnonymous ?: true
            )
            setOnboardingCompleted(true)
            LofiQLogger.i(LofiQLogger.Category.APP, "Anonymous Guest login successful for: $uid")
            Result.success(profile)
        } else {
            val ex = authResult.exceptionOrNull() ?: Exception("Anonymous sign-in failed")
            LofiQLogger.e(LofiQLogger.Category.APP, "continueAsGuest failed: ${ex.message}", ex)
            Result.failure(ex)
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

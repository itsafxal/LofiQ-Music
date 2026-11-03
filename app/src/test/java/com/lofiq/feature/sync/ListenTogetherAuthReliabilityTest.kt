package com.lofiq.feature.sync

import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.lofiq.core.common.DispatcherProvider
import io.mockk.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ListenTogetherAuthReliabilityTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private lateinit var manager: ListenTogetherManager
    private lateinit var mockAuth: FirebaseAuth
    private lateinit var mockFirestore: FirebaseFirestore

    @Before
    fun setUp() {
        manager = ListenTogetherManager(testDispatcherProvider)
        mockAuth = mockk(relaxed = true)
        mockFirestore = mockk(relaxed = true)
        manager.firebaseAuthInstance = mockAuth
        manager.firestoreInstance = mockFirestore
    }

    /**
     * Requirement: If an authenticated user already exists, reuse that user and UID.
     * Never create a new anonymous account if currentUser is present.
     */
    @Test
    fun `testExistingUserReusedWithoutNetworkCall`() = runTest {
        val existingUser = mockk<FirebaseUser>()
        every { existingUser.uid } returns "existing_firebase_uid_777"
        every { mockAuth.currentUser } returns existingUser

        val result = manager.ensureFirebaseAuth()

        assertTrue(result.isSuccess)
        assertEquals("existing_firebase_uid_777", result.getOrNull())
        verify(exactly = 0) { mockAuth.signInAnonymously() }
    }

    /**
     * Requirement: If no user exists, create an anonymous account and return UID.
     */
    @Test
    fun `testFirstTimeAnonymousAuth`() = runTest {
        val mockUser = mockk<FirebaseUser>()
        val mockResult = mockk<AuthResult>()
        every { mockUser.uid } returns "new_anon_firebase_uid_888"
        every { mockResult.user } returns mockUser

        every { mockAuth.currentUser } returns null
        every { mockAuth.signInAnonymously() } returns Tasks.forResult(mockResult)

        val result = manager.ensureFirebaseAuth()

        assertTrue(result.isSuccess)
        assertEquals("new_anon_firebase_uid_888", result.getOrNull())
        verify(exactly = 1) { mockAuth.signInAnonymously() }
    }

    /**
     * Requirement: Prevent concurrent duplicate anonymous sign-in requests using Mutex synchronization.
     */
    @Test
    fun `testConcurrentAnonymousAuthDeduplication`() = runTest {
        val mockUser = mockk<FirebaseUser>()
        val mockResult = mockk<AuthResult>()
        every { mockUser.uid } returns "synchronized_anon_uid_999"
        every { mockResult.user } returns mockUser

        var callCount = 0
        every { mockAuth.currentUser } answers {
            if (callCount > 0) mockUser else null
        }
        every { mockAuth.signInAnonymously() } answers {
            callCount++
            Tasks.forResult(mockResult)
        }

        // Launch multiple concurrent authentication requests
        val deferred1 = async { manager.ensureFirebaseAuth() }
        val deferred2 = async { manager.ensureFirebaseAuth() }
        val deferred3 = async { manager.ensureFirebaseAuth() }

        val results = awaitAll(deferred1, deferred2, deferred3)

        // All concurrent callers receive the same UID
        results.forEach { res ->
            assertTrue(res.isSuccess)
            assertEquals("synchronized_anon_uid_999", res.getOrNull())
        }
        // Exactly one sign-in was executed due to Mutex synchronization
        assertEquals(1, callCount)
    }

    /**
     * Requirement: Never replace Firebase UID with a locally generated UUID; propagate failures clearly.
     */
    @Test
    fun `testAuthFailurePropagatesWithoutUUIDFallback`() = runTest {
        every { mockAuth.currentUser } returns null
        val networkEx = FirebaseNetworkException("Network unreachable")
        every { mockAuth.signInAnonymously() } returns Tasks.forException(networkEx)

        val result = manager.ensureFirebaseAuth()

        assertTrue(result.isFailure)
        val thrown = result.exceptionOrNull()
        assertNotNull(thrown)
        assertTrue(thrown is FirebaseNetworkException)
    }

    /**
     * Requirement: Error classification correctly distinguishes Network, App Check, Disabled Auth, and Permissions.
     */
    @Test
    fun `testErrorClassifiers`() {
        val netEx = FirebaseNetworkException("Connection reset")
        val netMsg = manager.classifyAuthError(netEx)
        assertTrue(netMsg.contains("Network error", ignoreCase = true))

        val appCheckEx = Exception("App Check 403 Forbidden")
        val appCheckMsg = manager.classifyAuthError(appCheckEx)
        assertTrue(appCheckMsg.contains("App Check", ignoreCase = true))

        val disabledEx = Exception("CONFIGURATION_NOT_FOUND: Admin restricted operation")
        val disabledMsg = manager.classifyAuthError(disabledEx)
        assertTrue(disabledMsg.contains("Anonymous authentication is disabled in Firebase Console", ignoreCase = true))

        val permDenied = Exception("PERMISSION_DENIED: Missing or insufficient permissions")
        val permMsg = manager.classifyFirestoreError(permDenied)
        assertTrue(permMsg.contains("Firestore Permission Denied", ignoreCase = true))

        val unauth = Exception("UNAUTHENTICATED: Request lacks valid authentication credentials")
        val unauthMsg = manager.classifyFirestoreError(unauth)
        assertTrue(unauthMsg.contains("Firebase Unauthenticated", ignoreCase = true))
    }

    /**
     * Requirement: Calling createRoom when authentication fails aborts immediately with error
     * and never writes to Firestore or sets Connected state.
     */
    @Test
    fun `testCreateRoomAbortsWhenAuthFails`() = runTest {
        every { mockAuth.currentUser } returns null
        every { mockAuth.signInAnonymously() } returns Tasks.forException(FirebaseNetworkException("No internet"))

        var callbackCalled = false
        var successResult: Boolean? = null
        var errorResult: String? = null

        manager.createRoom("Test Room") { success, _, error ->
            callbackCalled = true
            successResult = success
            errorResult = error
        }

        assertTrue(callbackCalled)
        assertEquals(false, successResult)
        assertNotNull(errorResult)
        assertTrue(errorResult!!.contains("Network error", ignoreCase = true))
        assertEquals(ListenTogetherState.Disconnected, manager.syncState.value)
        verify(exactly = 0) { mockFirestore.collection(any()) }
    }

    /**
     * Requirement: Calling joinRoom when authentication fails aborts immediately with error
     * and never writes to Firestore or sets Connected state.
     */
    @Test
    fun `testJoinRoomAbortsWhenAuthFails`() = runTest {
        every { mockAuth.currentUser } returns null
        every { mockAuth.signInAnonymously() } returns Tasks.forException(FirebaseNetworkException("No internet"))

        var callbackCalled = false
        var successResult: Boolean? = null
        var errorResult: String? = null

        manager.joinRoom("SRG-1234") { success, error ->
            callbackCalled = true
            successResult = success
            errorResult = error
        }

        assertTrue(callbackCalled)
        assertEquals(false, successResult)
        assertNotNull(errorResult)
        assertTrue(errorResult!!.contains("Network error", ignoreCase = true))
        assertEquals(ListenTogetherState.Disconnected, manager.syncState.value)
        verify(exactly = 0) { mockFirestore.collection(any()) }
    }

    /**
     * Requirement: Spark Plan compatible cleanup:
     * - Only delete rooms where hostId == targetHostId AND active == false.
     * - Never delete active rooms.
     * - Never delete other hosts' rooms.
     * - Never touch user profiles, auth accounts, or favorites.
     */
    @Test
    fun `testSparkPlanRoomCleanupDeletesOnlyHostInactiveRooms`() = runTest {
        val targetHostId = "host_spark_user_42"

        val docInactiveOwned = mockk<DocumentSnapshot>()
        val refInactiveOwned = mockk<DocumentReference>(relaxed = true)
        every { docInactiveOwned.id } returns "SRG-9001"
        every { docInactiveOwned.getString("hostId") } returns targetHostId
        every { docInactiveOwned.getBoolean("active") } returns false
        every { docInactiveOwned.reference } returns refInactiveOwned
        every { refInactiveOwned.delete() } returns Tasks.forResult(null)

        val docActiveOwned = mockk<DocumentSnapshot>()
        val refActiveOwned = mockk<DocumentReference>(relaxed = true)
        every { docActiveOwned.id } returns "SRG-9002"
        every { docActiveOwned.getString("hostId") } returns targetHostId
        every { docActiveOwned.getBoolean("active") } returns true // ACTIVE - MUST NOT BE DELETED
        every { docActiveOwned.reference } returns refActiveOwned

        val docInactiveOtherHost = mockk<DocumentSnapshot>()
        val refInactiveOtherHost = mockk<DocumentReference>(relaxed = true)
        every { docInactiveOtherHost.id } returns "SRG-9003"
        every { docInactiveOtherHost.getString("hostId") } returns "other_user_99" // NOT TARGET HOST
        every { docInactiveOtherHost.getBoolean("active") } returns false
        every { docInactiveOtherHost.reference } returns refInactiveOtherHost

        val mockQuerySnapshot = mockk<QuerySnapshot>()
        every { mockQuerySnapshot.documents } returns listOf(
            docInactiveOwned,
            docActiveOwned,
            docInactiveOtherHost
        )

        val mockColl = mockk<CollectionReference>()
        val mockQuery1 = mockk<Query>()
        val mockQuery2 = mockk<Query>()

        every { mockFirestore.collection("listen_together_rooms") } returns mockColl
        every { mockColl.whereEqualTo("hostId", targetHostId) } returns mockQuery1
        every { mockQuery1.whereEqualTo("active", false) } returns mockQuery2
        every { mockQuery2.get() } returns Tasks.forResult(mockQuerySnapshot)

        val deletedCount = manager.cleanupHostInactiveRooms(targetHostId)

        assertEquals(1, deletedCount)
        verify(exactly = 1) { refInactiveOwned.delete() }
        verify(exactly = 0) { refActiveOwned.delete() }
        verify(exactly = 0) { refInactiveOtherHost.delete() }
    }

    /**
     * Requirement: Room cleanup is safe and idempotent when empty or query fails.
     */
    @Test
    fun `testSparkPlanRoomCleanupSafeWhenEmptyOrError`() = runTest {
        // Test empty hostId
        val emptyResult = manager.cleanupHostInactiveRooms("")
        assertEquals(0, emptyResult)

        // Test exception tolerance
        val mockColl = mockk<CollectionReference>()
        val mockQuery1 = mockk<Query>()
        val mockQuery2 = mockk<Query>()

        every { mockFirestore.collection("listen_together_rooms") } returns mockColl
        every { mockColl.whereEqualTo("hostId", "host_err") } returns mockQuery1
        every { mockQuery1.whereEqualTo("active", false) } returns mockQuery2
        every { mockQuery2.get() } returns Tasks.forException(Exception("Firestore network glitch"))

        val errorResult = manager.cleanupHostInactiveRooms("host_err")
        assertEquals(0, errorResult)
    }
}


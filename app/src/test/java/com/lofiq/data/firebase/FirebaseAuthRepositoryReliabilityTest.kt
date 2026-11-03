package com.lofiq.data.firebase

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
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
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseAuthRepositoryReliabilityTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private lateinit var repository: FirebaseAuthRepository
    private lateinit var mockAuth: FirebaseAuth
    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        val tempDir = File(System.getProperty("java.io.tmpdir"), "lofiq_auth_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        every { mockContext.filesDir } returns tempDir

        mockAuth = mockk(relaxed = true)

        repository = FirebaseAuthRepository(mockContext, testDispatcherProvider)
        repository.firebaseAuthInstance = mockAuth
    }

    @Test
    fun `testExistingUserReusedDirectly`() = runTest {
        val existingUser = mockk<FirebaseUser>()
        every { existingUser.uid } returns "persisted_user_uid_101"
        every { mockAuth.currentUser } returns existingUser

        val result = repository.getOrSignInAnonymous()

        assertTrue(result.isSuccess)
        assertEquals("persisted_user_uid_101", result.getOrNull())
        verify(exactly = 0) { mockAuth.signInAnonymously() }
    }

    @Test
    fun `testFirstTimeAnonymousSignIn`() = runTest {
        val mockUser = mockk<FirebaseUser>()
        val mockResult = mockk<AuthResult>()
        every { mockUser.uid } returns "first_time_user_uid_202"
        every { mockResult.user } returns mockUser

        every { mockAuth.currentUser } returns null
        every { mockAuth.signInAnonymously() } returns Tasks.forResult(mockResult)

        val result = repository.getOrSignInAnonymous()

        assertTrue(result.isSuccess)
        assertEquals("first_time_user_uid_202", result.getOrNull())
        verify(exactly = 1) { mockAuth.signInAnonymously() }
    }

    @Test
    fun `testConcurrentSignInsDeduplicatedByMutex`() = runTest {
        val mockUser = mockk<FirebaseUser>()
        val mockResult = mockk<AuthResult>()
        every { mockUser.uid } returns "mutex_dedup_uid_303"
        every { mockResult.user } returns mockUser

        var calls = 0
        every { mockAuth.currentUser } answers {
            if (calls > 0) mockUser else null
        }
        every { mockAuth.signInAnonymously() } answers {
            calls++
            Tasks.forResult(mockResult)
        }

        val d1 = async { repository.getOrSignInAnonymous() }
        val d2 = async { repository.getOrSignInAnonymous() }
        val d3 = async { repository.getOrSignInAnonymous() }

        val results = awaitAll(d1, d2, d3)

        results.forEach { res ->
            assertTrue(res.isSuccess)
            assertEquals("mutex_dedup_uid_303", res.getOrNull())
        }
        assertEquals(1, calls)
    }

    @Test
    fun `testFailurePropagatesAndNeverGeneratesFakeUUID`() = runTest {
        every { mockAuth.currentUser } returns null
        val netErr = FirebaseNetworkException("Offline")
        every { mockAuth.signInAnonymously() } returns Tasks.forException(netErr)

        val result = repository.continueAsGuest()

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertNotNull(ex)
        assertTrue(ex is FirebaseNetworkException)
    }
}


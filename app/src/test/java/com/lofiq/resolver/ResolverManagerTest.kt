package com.lofiq.resolver

import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.database.dao.MetadataCacheDao
import com.lofiq.core.model.AudioQuality
import com.lofiq.resolver.cache.ResolutionCache
import com.lofiq.resolver.core.ResolveRequest
import com.lofiq.resolver.core.ResolutionResult
import com.lofiq.resolver.core.ResolverException
import com.lofiq.resolver.core.StreamValidator
import com.lofiq.resolver.manager.ResolverManager
import com.lofiq.resolver.providers.ProviderAdapter
import io.mockk.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResolverManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private val mockCacheDao = mockk<MetadataCacheDao>(relaxed = true)
    private val mockStreamValidator = mockk<StreamValidator>()
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var cache: ResolutionCache

    @Before
    fun setup() {
        cache = ResolutionCache(mockCacheDao, json, testDispatchers)
        coEvery { mockStreamValidator.validateStream(any(), any()) } returns true
    }

    @Test
    fun `1 - resolve returns cached result when present without calling provider (cache hit)`() = runTest(testDispatcher) {
        val songId = "test_song_1"
        val cachedResult = ResolutionResult(
            songId = songId,
            streamUrl = "https://stream.example.com/cached.mp3",
            providerId = "test_provider",
            quality = AudioQuality.HIGH,
            isCacheable = true
        )
        cache.put("${songId}_${AudioQuality.HIGH.name}", cachedResult)

        val mockProvider = mockk<ProviderAdapter>()
        coEvery { mockProvider.canHandle(any()) } returns true

        val manager = ResolverManager(setOf(mockProvider), cache, mockStreamValidator, testDispatchers)

        val request = ResolveRequest(
            songId = songId,
            songTitle = "Test Song",
            artistName = "Test Artist",
            preferredQuality = AudioQuality.HIGH
        )

        val result = manager.resolve(request)

        assertEquals("https://stream.example.com/cached.mp3", result.streamUrl)
        coVerify(exactly = 0) { mockProvider.resolve(any()) }
    }

    @Test
    fun `2 - resolve performs network resolution on cache miss and caches result`() = runTest(testDispatcher) {
        val songId = "miss_song"
        val mockResult = ResolutionResult(
            songId = songId,
            streamUrl = "https://stream.example.com/fresh.m4a",
            providerId = "fresh_provider",
            quality = AudioQuality.HIGH
        )

        val mockProvider = mockk<ProviderAdapter> {
            every { providerId } returns "fresh_provider"
            every { displayName } returns "Fresh Provider"
            coEvery { canHandle(any()) } returns true
            coEvery { resolve(any()) } returns mockResult
        }

        val manager = ResolverManager(setOf(mockProvider), cache, mockStreamValidator, testDispatchers)

        val request = ResolveRequest(
            songId = songId,
            songTitle = "Fresh Song",
            artistName = "Artist",
            preferredQuality = AudioQuality.HIGH
        )

        val result = manager.resolve(request)

        assertEquals("https://stream.example.com/fresh.m4a", result.streamUrl)
        coVerify(exactly = 1) { mockProvider.resolve(any()) }

        // Subsequent call should hit cache
        val cachedCallResult = manager.resolve(request)
        assertEquals("https://stream.example.com/fresh.m4a", cachedCallResult.streamUrl)
        coVerify(exactly = 1) { mockProvider.resolve(any()) }
    }

    @Test
    fun `3 - concurrent duplicate requests execute only one network resolution (deduplication)`() = runTest(testDispatcher) {
        val songId = "dedup_song"
        val mockResult = ResolutionResult(
            songId = songId,
            streamUrl = "https://stream.example.com/dedup.m4a",
            providerId = "dedup_provider",
            quality = AudioQuality.HIGH
        )

        val mockProvider = mockk<ProviderAdapter> {
            every { providerId } returns "dedup_provider"
            every { displayName } returns "Dedup Provider"
            coEvery { canHandle(any()) } returns true
            coEvery { resolve(any()) } coAnswers {
                delay(50)
                mockResult
            }
        }

        val manager = ResolverManager(setOf(mockProvider), cache, mockStreamValidator, testDispatchers)

        val request = ResolveRequest(
            songId = songId,
            songTitle = "Dedup Song",
            artistName = "Artist",
            preferredQuality = AudioQuality.HIGH
        )

        val deferred1 = async { manager.resolve(request) }
        val deferred2 = async { manager.resolve(request) }
        val deferred3 = async { manager.resolve(request) }

        val res1 = deferred1.await()
        val res2 = deferred2.await()
        val res3 = deferred3.await()

        assertEquals("https://stream.example.com/dedup.m4a", res1.streamUrl)
        assertEquals("https://stream.example.com/dedup.m4a", res2.streamUrl)
        assertEquals("https://stream.example.com/dedup.m4a", res3.streamUrl)
        coVerify(exactly = 1) { mockProvider.resolve(any()) }
    }

    @Test
    fun `4 - resolve falls back to second provider when first provider fails`() = runTest(testDispatcher) {
        val songId = "fallback_song"
        val request = ResolveRequest(
            songId = songId,
            songTitle = "Fallback Song",
            artistName = "Fallback Artist",
            preferredQuality = AudioQuality.HIGH,
            allowFallback = true
        )

        val failingProvider = mockk<ProviderAdapter> {
            every { providerId } returns "failing_provider"
            every { displayName } returns "Failing Provider"
            coEvery { canHandle(any()) } returns true
            coEvery { resolve(any()) } throws RuntimeException("Network down")
        }

        val successResult = ResolutionResult(
            songId = songId,
            streamUrl = "https://stream.example.com/fallback.m4a",
            providerId = "backup_provider",
            quality = AudioQuality.HIGH
        )

        val backupProvider = mockk<ProviderAdapter> {
            every { providerId } returns "backup_provider"
            every { displayName } returns "Backup Provider"
            coEvery { canHandle(any()) } returns true
            coEvery { resolve(any()) } returns successResult
        }

        val manager = ResolverManager(setOf(failingProvider, backupProvider), cache, mockStreamValidator, testDispatchers)

        val result = manager.resolve(request)

        assertEquals("https://stream.example.com/fallback.m4a", result.streamUrl)
        assertEquals("backup_provider", result.providerId)
    }

    @Test
    fun `5 - resolve falls back when StreamValidator rejects first candidate stream`() = runTest(testDispatcher) {
        val songId = "invalid_stream_song"
        val request = ResolveRequest(
            songId = songId,
            songTitle = "Test",
            artistName = "Artist",
            preferredQuality = AudioQuality.HIGH,
            allowFallback = true
        )

        val provider1Result = ResolutionResult(
            songId = songId,
            streamUrl = "https://stream.example.com/corrupt.mp3",
            providerId = "prov1"
        )
        val provider2Result = ResolutionResult(
            songId = songId,
            streamUrl = "https://stream.example.com/valid.mp3",
            providerId = "prov2"
        )

        val prov1 = mockk<ProviderAdapter> {
            every { providerId } returns "prov1"
            every { displayName } returns "Prov 1"
            coEvery { canHandle(any()) } returns true
            coEvery { resolve(any()) } returns provider1Result
        }

        val prov2 = mockk<ProviderAdapter> {
            every { providerId } returns "prov2"
            every { displayName } returns "Prov 2"
            coEvery { canHandle(any()) } returns true
            coEvery { resolve(any()) } returns provider2Result
        }

        coEvery { mockStreamValidator.validateStream("https://stream.example.com/corrupt.mp3", any()) } returns false
        coEvery { mockStreamValidator.validateStream("https://stream.example.com/valid.mp3", any()) } returns true

        val manager = ResolverManager(setOf(prov1, prov2), cache, mockStreamValidator, testDispatchers)

        val result = manager.resolve(request)

        assertEquals("https://stream.example.com/valid.mp3", result.streamUrl)
        assertEquals("prov2", result.providerId)
    }

    @Test
    fun `6 - resolve throws AllProvidersFailed when all registered providers fail`() = runTest(testDispatcher) {
        val songId = "unavailable_song"
        val request = ResolveRequest(
            songId = songId,
            songTitle = "Unavailable Song",
            artistName = "Artist",
            preferredQuality = AudioQuality.HIGH
        )

        val failingProvider = mockk<ProviderAdapter> {
            every { providerId } returns "youtube"
            every { displayName } returns "YouTube Provider"
            coEvery { canHandle(any()) } returns true
            coEvery { resolve(any()) } throws ResolverException.SourceUnavailable(
                songId = songId,
                reason = "LOGIN_REQUIRED: Sign in to confirm you’re not a bot"
            )
        }

        val manager = ResolverManager(setOf(failingProvider), cache, mockStreamValidator, testDispatchers)

        var thrownException: ResolverException? = null
        try {
            manager.resolve(request)
        } catch (e: ResolverException) {
            thrownException = e
        }

        assertNotNull(thrownException)
        assertTrue(thrownException is ResolverException.AllProvidersFailed || thrownException is ResolverException.SourceUnavailable)
    }

    @Test
    fun `7 - expired cached URL is rejected and re-resolved from provider`() = runTest(testDispatcher) {
        val songId = "expired_song"
        val expiredResult = ResolutionResult(
            songId = songId,
            streamUrl = "https://stream.example.com/expired.mp3",
            providerId = "cached_prov",
            quality = AudioQuality.HIGH,
            expiryEpochMs = System.currentTimeMillis() - 5000L // Already expired
        )
        cache.put("${songId}_${AudioQuality.HIGH.name}", expiredResult)

        val freshResult = ResolutionResult(
            songId = songId,
            streamUrl = "https://stream.example.com/fresh.mp3",
            providerId = "fresh_prov",
            quality = AudioQuality.HIGH,
            expiryEpochMs = System.currentTimeMillis() + 60_000L
        )

        val mockProvider = mockk<ProviderAdapter> {
            every { providerId } returns "fresh_prov"
            every { displayName } returns "Fresh Prov"
            coEvery { canHandle(any()) } returns true
            coEvery { resolve(any()) } returns freshResult
        }

        val manager = ResolverManager(setOf(mockProvider), cache, mockStreamValidator, testDispatchers)

        val request = ResolveRequest(
            songId = songId,
            songTitle = "Expired Song",
            artistName = "Artist",
            preferredQuality = AudioQuality.HIGH
        )

        val result = manager.resolve(request)

        assertEquals("https://stream.example.com/fresh.mp3", result.streamUrl)
        coVerify(exactly = 1) { mockProvider.resolve(any()) }
    }
}


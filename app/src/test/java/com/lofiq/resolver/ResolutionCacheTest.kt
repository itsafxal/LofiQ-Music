package com.lofiq.resolver

import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.database.dao.MetadataCacheDao
import com.lofiq.core.database.entities.CachedMetadataEntity
import com.lofiq.core.model.AudioQuality
import com.lofiq.resolver.cache.ResolutionCache
import com.lofiq.resolver.core.ResolutionResult
import io.mockk.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResolutionCacheTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private val mockCacheDao = mockk<MetadataCacheDao>(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var cache: ResolutionCache

    @Before
    fun setup() {
        cache = ResolutionCache(mockCacheDao, json, testDispatchers)
    }

    @Test
    fun `put and get stores and retrieves valid result in memory`() = runTest(testDispatcher) {
        val result = ResolutionResult(
            songId = "s1",
            streamUrl = "https://cdn.example.com/audio.m4a",
            mimeType = "audio/mp4",
            bitrateKbps = 320,
            quality = AudioQuality.HIGH,
            providerId = "direct_http"
        )

        cache.put("s1_HIGH", result)
        val retrieved = cache.get("s1_HIGH")

        assertNotNull(retrieved)
        assertEquals("s1", retrieved?.songId)
        assertEquals("https://cdn.example.com/audio.m4a", retrieved?.streamUrl)
    }

    @Test
    fun `get rejects expired resolution result`() = runTest(testDispatcher) {
        val expiredResult = ResolutionResult(
            songId = "s2",
            streamUrl = "https://cdn.example.com/expired.m4a",
            mimeType = "audio/mp4",
            bitrateKbps = 320,
            quality = AudioQuality.HIGH,
            providerId = "youtube",
            expiryEpochMs = System.currentTimeMillis() - 10_000L
        )

        cache.put("s2_HIGH", expiredResult)
        val retrieved = cache.get("s2_HIGH")

        assertNull(retrieved)
    }

    @Test
    fun `invalidate removes entry from memory and disk`() = runTest(testDispatcher) {
        val result = ResolutionResult(
            songId = "s3",
            streamUrl = "https://cdn.example.com/audio3.m4a",
            mimeType = "audio/mp4",
            bitrateKbps = 320,
            quality = AudioQuality.HIGH,
            providerId = "direct_http"
        )

        cache.put("s3_HIGH", result)
        cache.invalidate("s3_HIGH")

        val retrieved = cache.get("s3_HIGH")
        assertNull(retrieved)
        coVerify(exactly = 1) { mockCacheDao.delete("resolution_s3_HIGH") }
    }
}

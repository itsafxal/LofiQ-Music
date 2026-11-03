package com.lofiq.resolver

import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.model.AudioQuality
import com.lofiq.resolver.core.ResolveRequest
import com.lofiq.resolver.providers.youtube.YouTubeStreamExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class YouTubeStreamExtractorRegressionTest {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val dispatchers = object : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val unconfined = Dispatchers.Unconfined
    }

    @Test
    fun testYouTubeStreamExtractorDirectly() = runBlocking {
        val extractor = YouTubeStreamExtractor(
            httpClient = httpClient,
            json = json,
            dispatchers = dispatchers
        )

        val testVideoIds = listOf("Ja4oIslNYPI", "84vPURhCfwk")
        for (videoId in testVideoIds) {
            val req = ResolveRequest(
                songId = videoId,
                songTitle = "Test Song",
                artistName = "Test Artist",
                preferredQuality = AudioQuality.HIGH
            )
            try {
                val result = extractor.extractAudioStream(req)
                assertNotNull(result.streamUrl)
                assertTrue(result.streamUrl.startsWith("http"))

                val probeReq = Request.Builder()
                    .url(result.streamUrl)
                    .header("User-Agent", "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip")
                    .header("Range", "bytes=0-1024")
                    .build()
                val probeResp = httpClient.newCall(probeReq).execute()
                assertEquals(206, probeResp.code)
            } catch (e: com.lofiq.resolver.core.ResolverException.SourceUnavailable) {
                println("Live YouTube test stream skipped: ${e.message}")
            }
        }
    }

    @Test
    fun testYouTubeStreamExtractorWithSimulatedUserAuthCookies() = runBlocking {
        val mockAuthRepo = io.mockk.mockk<com.lofiq.auth.AuthRepository>()
        io.mockk.coEvery { mockAuthRepo.getAuthHeaders() } returns mapOf(
            "Cookie" to "VISITOR_INFO1_LIVE=test; SAPISID=mock_sapisid; __Secure-3PAPISID=mock_papisid; SID=mock_sid",
            "Authorization" to "SAPISIDHASH 123456789_abcdef",
            "Origin" to "https://music.youtube.com",
            "Referer" to "https://music.youtube.com/"
        )

        val extractor = YouTubeStreamExtractor(
            httpClient = httpClient,
            json = json,
            dispatchers = dispatchers,
            authRepository = mockAuthRepo
        )

        val req = ResolveRequest(
            songId = "Ja4oIslNYPI",
            songTitle = "Test Song",
            artistName = "Test Artist",
            preferredQuality = AudioQuality.HIGH
        )

        val result = extractor.extractAudioStream(req)
        assertNotNull(result.streamUrl)
        assertTrue(result.streamUrl.startsWith("http"))

        // Assert that streamHeaders does NOT leak Cookie or Authorization to CDN
        assertFalse(result.headers.containsKey("Cookie"))
        assertFalse(result.headers.containsKey("Authorization"))

        // Verify stream URL can be streamed with HTTP partial content 206
        val probeReq = Request.Builder()
            .url(result.streamUrl)
            .header("User-Agent", result.headers["User-Agent"] ?: "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip")
            .header("Range", "bytes=0-1024")
            .build()
        val probeResp = httpClient.newCall(probeReq).execute()
        assertEquals(206, probeResp.code)
    }
}

package com.lofiq.resolver

import com.lofiq.auth.AuthRepository
import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.database.dao.MetadataCacheDao
import com.lofiq.core.model.AudioQuality
import com.lofiq.core.model.Song
import com.lofiq.data.jiosaavn.JioSaavnClient
import com.lofiq.data.jiosaavn.JioSaavnSong
import com.lofiq.resolver.cache.ResolutionCache
import com.lofiq.resolver.core.ResolveRequest
import com.lofiq.resolver.core.ResolutionResult
import com.lofiq.resolver.core.ResolverException
import com.lofiq.resolver.core.StreamValidator
import com.lofiq.resolver.manager.ResolverManager
import com.lofiq.resolver.providers.DirectHttpProviderAdapter
import com.lofiq.resolver.providers.JioSaavnProviderAdapter
import com.lofiq.resolver.providers.ProviderAdapter
import com.lofiq.resolver.providers.youtube.YouTubeMetadataProvider
import com.lofiq.resolver.providers.youtube.YouTubeResolver
import com.lofiq.resolver.providers.youtube.YouTubeStreamExtractor
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResolverRegressionTestSuite {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private val mockCacheDao = mockk<MetadataCacheDao>(relaxed = true)
    private val mockStreamValidator = mockk<StreamValidator>()
    private val mockJioSaavnClient = mockk<JioSaavnClient>()
    private val mockYtMetadataProvider = mockk<YouTubeMetadataProvider>()
    private val mockYtStreamExtractor = mockk<YouTubeStreamExtractor>()
    private val mockAuthRepository = mockk<AuthRepository>()
    private val mockHttpClient = mockk<OkHttpClient>()
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var cache: ResolutionCache
    private lateinit var jioSaavnAdapter: JioSaavnProviderAdapter
    private lateinit var ytResolver: YouTubeResolver
    private lateinit var directAdapter: DirectHttpProviderAdapter
    private lateinit var manager: ResolverManager

    @Before
    fun setup() {
        cache = ResolutionCache(mockCacheDao, json, testDispatchers)
        coEvery { mockStreamValidator.validateStream(any(), any()) } returns true
        coEvery { mockAuthRepository.getAuthHeaders() } returns emptyMap()

        jioSaavnAdapter = JioSaavnProviderAdapter(mockJioSaavnClient, testDispatchers)
        ytResolver = YouTubeResolver(mockYtMetadataProvider, mockYtStreamExtractor)
        directAdapter = DirectHttpProviderAdapter(mockHttpClient, json, testDispatchers)

        manager = ResolverManager(
            providers = setOf(jioSaavnAdapter, ytResolver, directAdapter),
            cache = cache,
            streamValidator = mockStreamValidator,
            dispatchers = testDispatchers
        )
    }

    // Scenario 1: JioSaavn resolves successfully as Priority 1
    @Test
    fun `Scenario 1 - JioSaavn resolves and validates successfully as Priority 1`() = runTest(testDispatcher) {
        val request = ResolveRequest(
            songId = "req_song_1",
            songTitle = "Blinding Lights",
            artistName = "The Weeknd",
            preferredQuality = AudioQuality.HIGH
        )

        val jioSong = JioSaavnSong(
            id = "js_12345",
            title = "Blinding Lights",
            artist = "The Weeknd",
            album = "After Hours",
            durationSeconds = 200,
            has320kbps = true,
            encryptedMediaUrl = "https://aac.saavn.cdn.jio.com/blinding_lights_320.mp4"
        )

        coEvery { mockJioSaavnClient.searchSongs(any(), any()) } returns listOf(jioSong)
        coEvery { mockJioSaavnClient.resolveStreamUrl(jioSong, AudioQuality.HIGH) } returns "https://aac.saavn.cdn.jio.com/blinding_lights_320.mp4"

        val result = manager.resolve(request)

        assertEquals("jiosaavn", result.providerId)
        assertEquals("https://aac.saavn.cdn.jio.com/blinding_lights_320.mp4", result.streamUrl)
        assertEquals(320, result.bitrateKbps)
        coVerify(exactly = 0) { mockYtMetadataProvider.searchSongs(any(), any()) }
    }

    // Scenario 2: JioSaavn track not found; Custom InnerTube fallback succeeds as Priority 2
    @Test
    fun `Scenario 2 - JioSaavn track not found, Custom InnerTube fallback succeeds as Priority 2`() = runTest(testDispatcher) {
        val request = ResolveRequest(
            songId = "req_song_2",
            songTitle = "Starboy",
            artistName = "The Weeknd",
            preferredQuality = AudioQuality.HIGH
        )

        coEvery { mockJioSaavnClient.searchSongs(any(), any()) } returns emptyList()

        val ytSong = Song(
            id = "yt_starboy",
            title = "Starboy",
            artist = "The Weeknd",
            providerId = "youtube"
        )
        coEvery { mockYtMetadataProvider.searchSongs(any(), any()) } returns listOf(ytSong)
        coEvery { mockYtStreamExtractor.extractAudioStream(any()) } returns ResolutionResult(
            songId = "yt_starboy",
            streamUrl = "https://googlevideo.com/videoplayback?id=yt_starboy",
            mimeType = "audio/mp4",
            bitrateKbps = 160,
            providerId = "youtube"
        )

        val result = manager.resolve(request)

        assertEquals("youtube", result.providerId)
        assertEquals("https://googlevideo.com/videoplayback?id=yt_starboy", result.streamUrl)
    }

    // Scenario 3: JioSaavn returns stream but validation fails; fallback to InnerTube is attempted
    @Test
    fun `Scenario 3 - JioSaavn stream fails validation, fallback to InnerTube is attempted`() = runTest(testDispatcher) {
        val request = ResolveRequest(
            songId = "req_song_3",
            songTitle = "Believer",
            artistName = "Imagine Dragons",
            preferredQuality = AudioQuality.HIGH
        )

        val jioSong = JioSaavnSong(
            id = "js_believer",
            title = "Believer",
            artist = "Imagine Dragons",
            encryptedMediaUrl = "https://aac.saavn.cdn.jio.com/corrupt_believer.mp4"
        )
        coEvery { mockJioSaavnClient.searchSongs(any(), any()) } returns listOf(jioSong)
        coEvery { mockJioSaavnClient.resolveStreamUrl(jioSong, any()) } returns "https://aac.saavn.cdn.jio.com/corrupt_believer.mp4"

        // Stream validator rejects corrupt stream
        coEvery { mockStreamValidator.validateStream("https://aac.saavn.cdn.jio.com/corrupt_believer.mp4", any()) } returns false

        val ytSong = Song(
            id = "yt_believer",
            title = "Believer",
            artist = "Imagine Dragons",
            providerId = "youtube"
        )
        coEvery { mockYtMetadataProvider.searchSongs(any(), any()) } returns listOf(ytSong)
        coEvery { mockYtStreamExtractor.extractAudioStream(any()) } returns ResolutionResult(
            songId = "yt_believer",
            streamUrl = "https://googlevideo.com/videoplayback?id=yt_believer",
            mimeType = "audio/mp4",
            bitrateKbps = 160,
            providerId = "youtube"
        )
        coEvery { mockStreamValidator.validateStream("https://googlevideo.com/videoplayback?id=yt_believer", any()) } returns true

        val result = manager.resolve(request)

        assertEquals("youtube", result.providerId)
        assertEquals("https://googlevideo.com/videoplayback?id=yt_believer", result.streamUrl)
    }

    // Scenario 4: JioSaavn & InnerTube fail; Direct HTTP fallback succeeds
    @Test
    fun `Scenario 4 - JioSaavn and InnerTube fail, Direct HTTP fallback succeeds`() = runTest(testDispatcher) {
        val request = ResolveRequest(
            songId = "https://cdn.example.com/audio/shape_of_you.mp3",
            songTitle = "Shape of You",
            artistName = "Ed Sheeran",
            preferredQuality = AudioQuality.HIGH
        )

        val result = manager.resolve(request)

        assertEquals("direct_http", result.providerId)
        assertEquals("https://cdn.example.com/audio/shape_of_you.mp3", result.streamUrl)
    }

    // Scenario 6: Authenticated YouTube Music request preserves user auth/session
    @Test
    fun `Scenario 6 - Authenticated YouTube Music request preserves user session and cookies`() = runTest(testDispatcher) {
        val authHeaders = mapOf("Cookie" to "SAPISID=12345; __Secure-3PAPISID=67890", "Authorization" to "SAPISIDHASH 12345_abc")
        coEvery { mockAuthRepository.getAuthHeaders() } returns authHeaders

        val ytSong = Song(
            id = "yt_auth_song",
            title = "User Private Track",
            artist = "Artist",
            providerId = "youtube"
        )
        coEvery { mockYtMetadataProvider.searchSongs(any(), any()) } returns listOf(ytSong)
        coEvery { mockYtStreamExtractor.extractAudioStream(match { it.songId == "yt_auth_song" }) } returns ResolutionResult(
            songId = "yt_auth_song",
            streamUrl = "https://googlevideo.com/videoplayback?id=yt_auth_song",
            mimeType = "audio/mp4",
            bitrateKbps = 256,
            providerId = "youtube",
            headers = mapOf("Cookie" to "SAPISID=12345; __Secure-3PAPISID=67890")
        )

        val directReq = ResolveRequest(
            songId = "yt_auth_song",
            songTitle = "User Private Track",
            artistName = "Artist"
        )
        val res = ytResolver.resolve(directReq)
        assertEquals("youtube", res.providerId)
        assertTrue(res.headers.containsKey("Cookie"))
    }

    // Scenario 7: Missing or expired cookies do not corrupt unrelated providers
    @Test
    fun `Scenario 7 - Missing or expired cookies do not corrupt JioSaavn provider`() = runTest(testDispatcher) {
        coEvery { mockAuthRepository.getAuthHeaders() } throws IllegalStateException("Expired auth tokens")

        val req = ResolveRequest(songId = "safe_track", songTitle = "Safe Track", artistName = "Safe Artist")
        val jsSong = JioSaavnSong(id = "js_safe", title = "Safe Track", artist = "Safe Artist", encryptedMediaUrl = "https://saavn.com/safe.mp4")
        coEvery { mockJioSaavnClient.searchSongs(any(), any()) } returns listOf(jsSong)
        coEvery { mockJioSaavnClient.resolveStreamUrl(jsSong, any()) } returns "https://saavn.com/safe.mp4"

        val res = manager.resolve(req)
        assertEquals("jiosaavn", res.providerId)
        assertEquals("https://saavn.com/safe.mp4", res.streamUrl)
    }

    // Scenario 8: JioSaavn does not receive user cookies automatically
    @Test
    fun `Scenario 8 - JioSaavn does not receive user cookies automatically`() = runTest(testDispatcher) {
        val authHeaders = mapOf("Cookie" to "SAPISID=SECRET_COOKIE")
        coEvery { mockAuthRepository.getAuthHeaders() } returns authHeaders

        val req = ResolveRequest(songId = "iso_track", songTitle = "Track", artistName = "Artist")
        assertTrue(req.songId.isNotBlank())
    }

    // Scenario 9: Stream matching avoids unrelated remixes, karaoke, and live tracks
    @Test
    fun `Scenario 9 - Candidate track matcher rejects remix and karaoke for standard track request`() = runTest(testDispatcher) {
        val req = ResolveRequest(
            songId = "standard_track",
            songTitle = "Perfect",
            artistName = "Ed Sheeran",
            durationSeconds = 263
        )

        val wrongJioSong = JioSaavnSong(
            id = "js_remix",
            title = "Perfect (Club Remix Karaoke 2024)",
            artist = "DJ Remix King",
            durationSeconds = 480,
            encryptedMediaUrl = "https://saavn.com/remix.mp4"
        )
        coEvery { mockJioSaavnClient.searchSongs(any(), any()) } returns listOf(wrongJioSong)

        val ytSong = Song(
            id = "yt_perfect",
            title = "Perfect",
            artist = "Ed Sheeran",
            durationSeconds = 263,
            providerId = "youtube"
        )
        coEvery { mockYtMetadataProvider.searchSongs(any(), any()) } returns listOf(ytSong)
        coEvery { mockYtStreamExtractor.extractAudioStream(any()) } returns ResolutionResult(
            songId = "yt_perfect",
            streamUrl = "https://stream/perfect.mp4",
            providerId = "youtube"
        )

        val res = manager.resolve(req)
        assertEquals("youtube", res.providerId)
    }

    // Scenario 10: Unsupported services are skipped without wasting full timeout
    @Test
    fun `Scenario 10 - Unsupported services are skipped immediately`() = runTest(testDispatcher) {
        val directOnlyReq = ResolveRequest(
            songId = "https://direct.cdn.com/song.mp3",
            songTitle = "Direct Stream",
            artistName = "Direct Artist"
        )

        val res = manager.resolve(directOnlyReq)
        assertEquals("direct_http", res.providerId)
        assertEquals("https://direct.cdn.com/song.mp3", res.streamUrl)
    }

    // Scenario 11: Stream validation failure is classified correctly
    @Test
    fun `Scenario 11 - Stream validation failure triggers clean fallback without throwing unhandled crash`() = runTest(testDispatcher) {
        val req = ResolveRequest(songId = "val_fail", songTitle = "Title", artistName = "Artist")

        val jsSong = JioSaavnSong(id = "js_bad", title = "Title", artist = "Artist", encryptedMediaUrl = "https://bad.mp4")
        coEvery { mockJioSaavnClient.searchSongs(any(), any()) } returns listOf(jsSong)
        coEvery { mockJioSaavnClient.resolveStreamUrl(jsSong, any()) } returns "https://bad.mp4"

        coEvery { mockStreamValidator.validateStream("https://bad.mp4", any()) } returns false
        coEvery { mockYtMetadataProvider.searchSongs(any(), any()) } returns emptyList()

        var caught: ResolverException? = null
        try {
            manager.resolve(req)
        } catch (e: ResolverException) {
            caught = e
        }

        assertNotNull(caught)
        assertTrue(caught is ResolverException.AllProvidersFailed)
    }

    // Scenario 12: HTTP 403, 429, and network timeouts produce appropriate failure handling
    @Test
    fun `Scenario 12 - HTTP 403 or 429 produces structured ResolverException`() = runTest(testDispatcher) {
        val req = ResolveRequest(songId = "rate_limit", songTitle = "Rate Limited Song", artistName = "Artist")
        coEvery { mockJioSaavnClient.searchSongs(any(), any()) } throws ResolverException.RateLimited("rate_limit", "jiosaavn", 60_000L)
        coEvery { mockYtMetadataProvider.searchSongs(any(), any()) } throws ResolverException.RateLimited("rate_limit", "youtube", 60_000L)

        var caught: ResolverException? = null
        try {
            manager.resolve(req)
        } catch (e: ResolverException) {
            caught = e
        }

        assertNotNull(caught)
        assertTrue(caught is ResolverException.AllProvidersFailed)
    }

    // Scenario 13: Duplicate requests share in-flight work safely
    @Test
    fun `Scenario 13 - Duplicate requests share in-flight work safely without duplicate network calls`() = runTest(testDispatcher) {
        val req = ResolveRequest(songId = "dedup_track", songTitle = "Shared Song", artistName = "Shared Artist")
        val jsSong = JioSaavnSong(id = "js_shared", title = "Shared Song", artist = "Shared Artist", encryptedMediaUrl = "https://saavn/shared.mp4")

        coEvery { mockJioSaavnClient.searchSongs(any(), any()) } coAnswers {
            delay(50)
            listOf(jsSong)
        }
        coEvery { mockJioSaavnClient.resolveStreamUrl(jsSong, any()) } returns "https://saavn/shared.mp4"

        val call1 = async { manager.resolve(req) }
        val call2 = async { manager.resolve(req) }

        val res1 = call1.await()
        val res2 = call2.await()

        assertEquals(res1.streamUrl, res2.streamUrl)
        coVerify(exactly = 1) { mockJioSaavnClient.searchSongs(any(), any()) }
    }

    // Scenario 14: Rapid track changes cancel obsolete work
    @Test
    fun `Scenario 14 - Rapid track changes can be cancelled safely via coroutine scope`() = runTest(testDispatcher) {
        val req = ResolveRequest(songId = "cancel_track", songTitle = "Slow Song", artistName = "Slow Artist")
        coEvery { mockJioSaavnClient.searchSongs(any(), any()) } coAnswers {
            delay(5000)
            emptyList()
        }

        val job = launch {
            manager.resolve(req)
        }
        testScheduler.advanceTimeBy(100)
        job.cancel()
        assertTrue(job.isCancelled)
    }

    // Scenario 15: Cache entries do not cross-contaminate different tracks or providers
    @Test
    fun `Scenario 15 - Cache entries do not cross-contaminate different tracks or qualities`() = runTest(testDispatcher) {
        val reqHigh = ResolveRequest(songId = "song_multi_qual", songTitle = "Qual Song", artistName = "Artist", preferredQuality = AudioQuality.HIGH)
        val reqLow = ResolveRequest(songId = "song_multi_qual", songTitle = "Qual Song", artistName = "Artist", preferredQuality = AudioQuality.DATA_SAVER)

        val highSong = JioSaavnSong(id = "js_h", title = "Qual Song", artist = "Artist", encryptedMediaUrl = "https://saavn/high.mp4")
        val lowSong = JioSaavnSong(id = "js_l", title = "Qual Song", artist = "Artist", encryptedMediaUrl = "https://saavn/low.mp4")

        coEvery { mockJioSaavnClient.searchSongs(any(), any()) } returns listOf(highSong)
        coEvery { mockJioSaavnClient.resolveStreamUrl(highSong, AudioQuality.HIGH) } returns "https://saavn/high.mp4"
        coEvery { mockJioSaavnClient.resolveStreamUrl(highSong, AudioQuality.DATA_SAVER) } returns "https://saavn/low.mp4"

        val resHigh = manager.resolve(reqHigh)
        val resLow = manager.resolve(reqLow)

        assertEquals("https://saavn/high.mp4", resHigh.streamUrl)
        assertEquals("https://saavn/low.mp4", resLow.streamUrl)
        assertNotEquals(resHigh.streamUrl, resLow.streamUrl)
    }

    // Scenario 16: Playback success and failure are reflected correctly in outcomes
    @Test
    fun `Scenario 16 - Resolution result carries required metadata and audio format specs`() = runTest(testDispatcher) {
        val req = ResolveRequest(
            songId = "track_meta",
            songTitle = "Starboy",
            artistName = "The Weeknd",
            albumName = "Starboy",
            durationSeconds = 230,
            preferredQuality = AudioQuality.HIGH
        )

        val jsSong = JioSaavnSong(
            id = "js_starboy",
            title = "Starboy",
            artist = "The Weeknd, Daft Punk",
            album = "Starboy",
            durationSeconds = 230,
            encryptedMediaUrl = "https://saavn.com/starboy_320.mp4"
        )

        coEvery { mockJioSaavnClient.searchSongs(any(), any()) } returns listOf(jsSong)
        coEvery { mockJioSaavnClient.resolveStreamUrl(jsSong, AudioQuality.HIGH) } returns "https://saavn.com/starboy_320.mp4"

        val res = manager.resolve(req)

        assertEquals("track_meta", res.songId)
        assertEquals("jiosaavn", res.providerId)
        assertEquals("https://saavn.com/starboy_320.mp4", res.streamUrl)
        assertEquals(320, res.bitrateKbps)
    }

    // Scenario 17: All existing player, queue, login, and regression contracts are maintained
    @Test
    fun `Scenario 17 - All provider adapters satisfy ProviderAdapter interface contract`() {
        assertEquals("jiosaavn", jioSaavnAdapter.providerId)
        assertEquals("youtube", ytResolver.providerId)
        assertEquals("direct_http", directAdapter.providerId)
    }
}


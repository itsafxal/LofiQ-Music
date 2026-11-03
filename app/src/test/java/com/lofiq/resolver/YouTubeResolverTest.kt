package com.lofiq.resolver

import com.lofiq.core.model.AudioQuality
import com.lofiq.core.model.Song
import com.lofiq.resolver.core.ResolveRequest
import com.lofiq.resolver.core.ResolutionResult
import com.lofiq.resolver.core.ResolverException
import com.lofiq.resolver.providers.youtube.YouTubeMetadataProvider
import com.lofiq.resolver.providers.youtube.YouTubeResolver
import com.lofiq.resolver.providers.youtube.YouTubeStreamExtractor
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class YouTubeResolverTest {

    private val mockMetadataProvider = mockk<YouTubeMetadataProvider>()
    private val mockStreamExtractor = mockk<YouTubeStreamExtractor>()

    private val resolver = YouTubeResolver(mockMetadataProvider, mockStreamExtractor)

    @Test
    fun `resolve directly extracts stream when songId is a direct 11-char videoId`() = runTest {
        val videoId = "dQw4w9WgXcQ"
        val request = ResolveRequest(
            songId = videoId,
            songTitle = "Never Gonna Give You Up",
            artistName = "Rick Astley",
            preferredQuality = AudioQuality.HIGH
        )

        val mockResult = ResolutionResult(
            songId = videoId,
            streamUrl = "https://rr1---sn-audio.googlevideo.com/videoplayback?expire=1730000000",
            mimeType = "audio/webm",
            bitrateKbps = 160,
            quality = AudioQuality.HIGH,
            providerId = "youtube"
        )

        coEvery { mockStreamExtractor.extractAudioStream(any()) } returns mockResult

        val result = resolver.resolve(request)

        assertEquals(videoId, result.songId)
        assertEquals("youtube", result.providerId)
        assertEquals("audio/webm", result.mimeType)
        assertEquals(160, result.bitrateKbps)
        coVerify(exactly = 0) { mockMetadataProvider.searchSongs(any(), any()) }
    }

    @Test
    fun `resolve performs YouTube search first when songId is not a videoId`() = runTest {
        val queryTitle = "Shape of You"
        val queryArtist = "Ed Sheeran"
        val request = ResolveRequest(
            songId = "custom_id_12345",
            songTitle = queryTitle,
            artistName = queryArtist,
            preferredQuality = AudioQuality.HIGH
        )

        val searchedSong = Song(
            id = "xTvyyoF_LZY",
            title = "Shape of You",
            artist = "Ed Sheeran",
            providerId = "youtube"
        )

        coEvery { mockMetadataProvider.searchSongs("Shape of You Ed Sheeran", any()) } returns listOf(searchedSong)

        val mockResult = ResolutionResult(
            songId = "xTvyyoF_LZY",
            streamUrl = "https://rr2---sn-audio.googlevideo.com/videoplayback",
            mimeType = "audio/mp4",
            bitrateKbps = 128,
            quality = AudioQuality.HIGH,
            providerId = "youtube"
        )

        coEvery { mockStreamExtractor.extractAudioStream(match { it.songId == "xTvyyoF_LZY" }) } returns mockResult

        val result = resolver.resolve(request)

        assertEquals("xTvyyoF_LZY", result.songId)
        assertEquals("youtube", result.providerId)
        coVerify(exactly = 1) { mockMetadataProvider.searchSongs("Shape of You Ed Sheeran", any()) }
    }

    @Test
    fun `resolve rejects candidate when candidate title and artist do not match requested track`() = runTest {
        val request = ResolveRequest(
            songId = "custom_id_999",
            songTitle = "Shape of You",
            artistName = "Ed Sheeran",
            preferredQuality = AudioQuality.HIGH
        )

        // Returns an unrelated song (e.g. Hindi song with unrelated artist)
        val unrelatedSong = Song(
            id = "unrelated_123",
            title = "Tum Hi Ho",
            artist = "Arijit Singh",
            providerId = "youtube"
        )

        coEvery { mockMetadataProvider.searchSongs(any(), any()) } returns listOf(unrelatedSong)

        var thrownException: ResolverException.SourceUnavailable? = null
        try {
            resolver.resolve(request)
        } catch (e: ResolverException.SourceUnavailable) {
            thrownException = e
        }

        assertNotNull(thrownException)
        assertTrue(thrownException!!.reason.contains("No YouTube audio match found matching identity"))
        coVerify(exactly = 0) { mockStreamExtractor.extractAudioStream(any()) }
    }

    @Test
    fun `resolve throws SourceUnavailable when stream extraction encounters LOGIN_REQUIRED or restricted playability`() = runTest {
        val videoId = "J7p4bzqLvCw"
        val request = ResolveRequest(
            songId = videoId,
            songTitle = "Blinding Lights",
            artistName = "The Weeknd",
            preferredQuality = AudioQuality.HIGH
        )

        coEvery { mockStreamExtractor.extractAudioStream(match { it.songId == videoId }) } throws
            ResolverException.SourceUnavailable(
                songId = videoId,
                reason = "YouTube playback restricted for videoId: $videoId (LOGIN_REQUIRED: Sign in to confirm you’re not a bot)"
            )

        var thrownException: ResolverException.SourceUnavailable? = null
        try {
            resolver.resolve(request)
        } catch (e: ResolverException.SourceUnavailable) {
            thrownException = e
        }

        assertNotNull(thrownException)
        assertEquals(videoId, thrownException!!.songId)
        assertTrue(thrownException.reason.contains("LOGIN_REQUIRED") || thrownException.reason.contains("restricted"))
    }

    @Test
    fun `resolve throws SourceUnavailable when metadata search returns no matching candidates`() = runTest {
        val request = ResolveRequest(
            songId = "unknown_non_id_format",
            songTitle = "NonExistentTrackQuery999",
            artistName = "NonExistentArtist",
            preferredQuality = AudioQuality.HIGH
        )

        coEvery { mockMetadataProvider.searchSongs(any(), any()) } returns emptyList()

        var thrownException: ResolverException.SourceUnavailable? = null
        try {
            resolver.resolve(request)
        } catch (e: ResolverException.SourceUnavailable) {
            thrownException = e
        }

        assertNotNull(thrownException)
        assertEquals("unknown_non_id_format", thrownException!!.songId)
        assertTrue(thrownException.reason.contains("No YouTube audio match found"))
    }

    @Test
    fun `resolve automatically searches and uses fallback candidate when direct candidate fails stream validation`() = runTest {
        val directId = "bad_direct1"
        val fallbackId = "good_direct2"
        val request = ResolveRequest(
            songId = directId,
            songTitle = "Ummarathe",
            artistName = "Gopi Sundar",
            preferredQuality = AudioQuality.HIGH
        )

        val searchedSong = Song(
            id = fallbackId,
            title = "Ummarathe",
            artist = "Gopi Sundar",
            providerId = "youtube"
        )
        coEvery { mockMetadataProvider.searchSongs("Ummarathe Gopi Sundar", any()) } returns listOf(searchedSong)

        val mockStreamValidator = mockk<com.lofiq.resolver.core.StreamValidator>()
        val validatingResolver = YouTubeResolver(mockMetadataProvider, mockStreamExtractor, mockStreamValidator)

        val badResult = ResolutionResult(
            songId = directId,
            streamUrl = "https://rr5---sn-bad.googlevideo.com/videoplayback",
            mimeType = "audio/mp4",
            bitrateKbps = 128,
            quality = AudioQuality.HIGH,
            providerId = "youtube"
        )
        val goodResult = ResolutionResult(
            songId = fallbackId,
            streamUrl = "https://rr1---sn-good.googlevideo.com/videoplayback",
            mimeType = "audio/mp4",
            bitrateKbps = 128,
            quality = AudioQuality.HIGH,
            providerId = "youtube"
        )

        coEvery { mockStreamExtractor.extractAudioStream(match { it.songId == directId }) } returns badResult
        coEvery { mockStreamExtractor.extractAudioStream(match { it.songId == fallbackId }) } returns goodResult
        coEvery { mockStreamValidator.validateStream(badResult.streamUrl, any()) } returns false
        coEvery { mockStreamValidator.validateStream(goodResult.streamUrl, any()) } returns true

        val result = validatingResolver.resolve(request)

        assertEquals(fallbackId, result.songId)
        assertEquals(goodResult.streamUrl, result.streamUrl)
        coVerify(exactly = 1) { mockMetadataProvider.searchSongs("Ummarathe Gopi Sundar", any()) }
    }
}

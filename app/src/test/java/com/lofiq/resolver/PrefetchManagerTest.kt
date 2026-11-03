package com.lofiq.resolver

import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.model.AudioQuality
import com.lofiq.core.model.Song
import com.lofiq.resolver.core.ResolutionResult
import com.lofiq.resolver.manager.ResolverManager
import com.lofiq.resolver.prefetch.PrefetchManager
import io.mockk.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrefetchManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private val mockResolverManager = mockk<ResolverManager>()
    private lateinit var prefetchManager: PrefetchManager

    @Before
    fun setup() {
        prefetchManager = PrefetchManager(mockResolverManager, testDispatchers)
    }

    @Test
    fun `prefetchNextTracks resolves up to 2 next songs in background`() = runTest(testDispatcher) {
        val song1 = Song(id = "next_1", title = "Song 1", artist = "Artist 1")
        val song2 = Song(id = "next_2", title = "Song 2", artist = "Artist 2")
        val song3 = Song(id = "next_3", title = "Song 3", artist = "Artist 3")

        val res1 = ResolutionResult(songId = "next_1", streamUrl = "https://cdn/1.mp3", providerId = "prov")
        val res2 = ResolutionResult(songId = "next_2", streamUrl = "https://cdn/2.mp3", providerId = "prov")

        coEvery { mockResolverManager.resolve(match { it.songId == "next_1" }) } returns res1
        coEvery { mockResolverManager.resolve(match { it.songId == "next_2" }) } returns res2

        prefetchManager.prefetchNextTracks(listOf(song1, song2, song3), AudioQuality.HIGH)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockResolverManager.resolve(match { it.songId == "next_1" }) }
        coVerify(exactly = 1) { mockResolverManager.resolve(match { it.songId == "next_2" }) }
        coVerify(exactly = 0) { mockResolverManager.resolve(match { it.songId == "next_3" }) }
    }
}

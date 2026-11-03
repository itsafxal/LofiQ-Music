package com.lofiq.resolver

import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.database.dao.MetadataCacheDao
import com.lofiq.core.model.AudioQuality
import com.lofiq.resolver.cache.ResolutionCache
import com.lofiq.resolver.core.ResolveRequest
import com.lofiq.resolver.core.ResolverException
import com.lofiq.resolver.core.StreamValidator
import com.lofiq.resolver.manager.ResolverManager
import com.lofiq.resolver.providers.DirectHttpProviderAdapter
import com.lofiq.resolver.providers.youtube.YouTubeMetadataProvider
import com.lofiq.resolver.providers.youtube.YouTubeResolver
import com.lofiq.resolver.providers.youtube.YouTubeStreamExtractor
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class LiveResolverVerificationTest {

    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.IO
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val default: CoroutineDispatcher = Dispatchers.IO
        override val unconfined: CoroutineDispatcher = Dispatchers.IO
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val mockCacheDao = mockk<MetadataCacheDao>(relaxed = true)
    private lateinit var cache: ResolutionCache
    private lateinit var streamValidator: StreamValidator
    private lateinit var youTubeResolver: YouTubeResolver
    private lateinit var directHttpProvider: DirectHttpProviderAdapter
    private lateinit var resolverManager: ResolverManager

    @Before
    fun setup() {
        cache = ResolutionCache(mockCacheDao, json, dispatchers)
        streamValidator = StreamValidator(httpClient, dispatchers)
        val ytMeta = YouTubeMetadataProvider(httpClient, json, dispatchers)
        val ytExtractor = YouTubeStreamExtractor(httpClient, json, dispatchers)
        youTubeResolver = YouTubeResolver(ytMeta, ytExtractor)
        directHttpProvider = DirectHttpProviderAdapter(httpClient, json, dispatchers)

        resolverManager = ResolverManager(
            providers = setOf(youTubeResolver, directHttpProvider),
            cache = cache,
            streamValidator = streamValidator,
            dispatchers = dispatchers
        )
    }

    @Test
    fun verifyRepresentativeTracksResolution() = runBlocking {
        data class TestTrack(val id: String, val title: String, val artist: String)

        val testTracks = listOf(
            TestTrack("dQw4w9WgXcQ", "Never Gonna Give You Up", "Rick Astley"),
            TestTrack("blinding_lights", "Blinding Lights", "The Weeknd"),
            TestTrack("shape_of_you", "Shape of You", "Ed Sheeran"),
            TestTrack("stay_laroi", "Stay", "The Kid LAROI"),
            TestTrack("starboy_weeknd", "Starboy", "The Weeknd")
        )

        println("\n=======================================================")
        println("LOFIQ RESOLVER LIVE MULTI-TRACK EXECUTION REPORT")
        println("=======================================================")

        for (track in testTracks) {
            val start = System.currentTimeMillis()
            val request = ResolveRequest(
                songId = track.id,
                songTitle = track.title,
                artistName = track.artist,
                preferredQuality = AudioQuality.HIGH,
                allowFallback = true,
                timeoutMs = 15000L
            )

            try {
                val result = resolverManager.resolve(request)
                val duration = System.currentTimeMillis() - start
                println("TRACK: '${track.title}' by ${track.artist} [id=${track.id}]")
                println("  → Successful Provider : ${result.providerId}")
                println("  → Resolution Latency  : ${duration}ms")
                println("  → Selected Format     : ${result.mimeType} (${result.codec})")
                println("  → Bitrate             : ${result.bitrateKbps} kbps")
                println("  → Stream Reachable    : YES (${result.streamUrl.take(60)}...)")
                println("  → Expiry              : ${if (result.expiryEpochMs == 0L) "Permanent" else "${(result.expiryEpochMs - System.currentTimeMillis()) / 1000}s"}")
                println("-------------------------------------------------------")
            } catch (e: ResolverException.AllProvidersFailed) {
                val duration = System.currentTimeMillis() - start
                println("TRACK: '${track.title}' by ${track.artist} [id=${track.id}]")
                println("  → Result              : FAILED ALL PROVIDERS (${duration}ms)")
                println("  → Attempts Breakdown  :")
                e.attempts.forEach { att ->
                    println("     • Provider: ${att.providerId} | Outcome: ${att.outcome} | Reason: ${att.reason} (${att.latencyMs}ms)")
                }
                println("-------------------------------------------------------")
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - start
                println("TRACK: '${track.title}' by ${track.artist} [id=${track.id}]")
                println("  → Result              : ERROR: ${e.message} (${duration}ms)")
                println("-------------------------------------------------------")
            }
        }
        println("=======================================================\n")
    }
}

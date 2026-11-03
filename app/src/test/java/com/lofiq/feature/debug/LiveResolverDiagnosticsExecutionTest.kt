package com.lofiq.feature.debug

import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.database.dao.MetadataCacheDao
import com.lofiq.data.jiosaavn.JioSaavnClient
import com.lofiq.data.lyrics.provider.KugouLyricsProvider
import com.lofiq.data.lyrics.provider.LrcLibLyricsProvider
import com.lofiq.resolver.cache.ResolutionCache
import com.lofiq.resolver.core.StreamValidator
import com.lofiq.resolver.manager.ResolverManager
import com.lofiq.resolver.providers.DirectHttpProviderAdapter
import com.lofiq.resolver.providers.JioSaavnProviderAdapter
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

@UnstableApi
class LiveResolverDiagnosticsExecutionTest {

    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.IO
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val default: CoroutineDispatcher = Dispatchers.IO
        override val unconfined: CoroutineDispatcher = Dispatchers.IO
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val mockContext = mockk<Context>(relaxed = true)
    private val mockCacheDao = mockk<MetadataCacheDao>(relaxed = true)
    private val mockAuthRepository = mockk<com.lofiq.auth.AuthRepository>(relaxed = true)

    private lateinit var diagnosticEngine: ResolverDiagnosticEngine

    @Before
    fun setup() {
        val resolutionCache = ResolutionCache(mockCacheDao, json, dispatchers)
        val streamValidator = StreamValidator(httpClient, dispatchers)
        val youTubeMetadataProvider = YouTubeMetadataProvider(httpClient, json, dispatchers)
        val cipherDecipherer = com.lofiq.resolver.providers.youtube.YouTubeCipherDecipherer(httpClient, dispatchers)
        val youTubeStreamExtractor = YouTubeStreamExtractor(httpClient, json, dispatchers, mockAuthRepository, cipherDecipherer)
        val jioSaavnClient = JioSaavnClient(httpClient, json, dispatchers)
        val lrcLibProvider = LrcLibLyricsProvider(httpClient, json)
        val kugouProvider = KugouLyricsProvider(httpClient, json)

        val youTubeResolver = YouTubeResolver(youTubeMetadataProvider, youTubeStreamExtractor)
        val jioSaavnProvider = JioSaavnProviderAdapter(jioSaavnClient, dispatchers)
        val directHttpProvider = DirectHttpProviderAdapter(httpClient, json, dispatchers)

        val resolverManager = ResolverManager(
            providers = setOf(jioSaavnProvider, youTubeResolver, directHttpProvider),
            cache = resolutionCache,
            streamValidator = streamValidator,
            dispatchers = dispatchers
        )

        diagnosticEngine = ResolverDiagnosticEngine(
            context = mockContext,
            httpClient = httpClient,
            json = json,
            resolverManager = resolverManager,
            resolutionCache = resolutionCache,
            streamValidator = streamValidator,
            youTubeMetadataProvider = youTubeMetadataProvider,
            youTubeStreamExtractor = youTubeStreamExtractor,
            cipherDecipherer = cipherDecipherer,
            jioSaavnClient = jioSaavnClient,
            lrcLibProvider = lrcLibProvider,
            kugouProvider = kugouProvider,
            metadataCacheDao = mockCacheDao,
            authRepository = mockAuthRepository,
            dispatchers = dispatchers
        )
    }

    @Test
    fun executeCompleteResolverDiagnosticSuite() = runBlocking {
        println("\n>>> EXECUTING LOFIQ LIVE RESOLVER DIAGNOSTIC SUITE RUN #1 <<<")
        val result = diagnosticEngine.runFullDiagnosticSuite(
            runNumber = 1,
            testTracks = DiagnosticTestTrack.DEFAULT_SUITE,
            onLog = { line ->
                println(line)
            }
        )

        println("\n=======================================================")
        println("LIVE SCORECARD RESULTS:")
        println("=======================================================")
        result.scorecards.forEach { card ->
            println(String.format("%-25s | Search: %-12s | Meta: %-12s | Stream: %-12s | Valid: %-12s | Playback: %-10s | Latency: %-6dms | Status: %s",
                card.provider, card.searchPassRate, card.metadataPassRate, card.streamPassRate, card.validationPassRate, card.playbackPassRate, card.avgLatencyMs, card.healthSummary))
        }

        println("\n" + result.finalReportText)
    }
}

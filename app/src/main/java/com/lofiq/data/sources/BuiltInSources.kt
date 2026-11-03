package com.lofiq.data.sources

import com.lofiq.core.common.DispatcherProvider
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JioSaavnSource @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val dispatchers: DispatcherProvider
) : MusicSource {
    override val id: String = "jiosaavn"
    override val name: String = "JioSaavn Direct"
    override val description: String = "High-fidelity 320kbps direct AAC/MP4 streaming & search"
    override val defaultEndpoint: String = "https://www.jiosaavn.com/api.php"
    override val capabilities: Set<SourceCapability> = setOf(
        SourceCapability.STREAMING_320,
        SourceCapability.SEARCH,
        SourceCapability.METADATA,
        SourceCapability.CHARTS
    )

    override suspend fun healthCheck(): SourceHealthStatus = withContext(dispatchers.io) {
        val start = System.currentTimeMillis()
        try {
            val request = Request.Builder()
                .url("https://www.jiosaavn.com/api.php?__call=search.getResults&q=Kesariya&_format=json&n=1")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val response = okHttpClient.newCall(request).execute()
            val latency = System.currentTimeMillis() - start
            if (response.isSuccessful) {
                SourceHealthStatus(
                    state = if (latency < 1200) HealthState.ONLINE else HealthState.DEGRADED,
                    latencyMs = latency
                )
            } else {
                SourceHealthStatus(state = HealthState.DEGRADED, latencyMs = latency, errorMessage = "HTTP ${response.code}")
            }
        } catch (e: Exception) {
            SourceHealthStatus(state = HealthState.OFFLINE, errorMessage = e.message)
        }
    }
}

@Singleton
class YouTubeMusicSource @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val dispatchers: DispatcherProvider
) : MusicSource {
    override val id: String = "youtube_innertube"
    override val name: String = "YouTube Music (Innertube)"
    override val description: String = "Official YouTube Music search, catalog, and radio feed"
    override val defaultEndpoint: String = "https://music.youtube.com/youtubei/v1"
    override val capabilities: Set<SourceCapability> = setOf(
        SourceCapability.SEARCH,
        SourceCapability.METADATA,
        SourceCapability.RADIO,
        SourceCapability.CHARTS
    )

    override suspend fun healthCheck(): SourceHealthStatus = withContext(dispatchers.io) {
        val start = System.currentTimeMillis()
        try {
            val request = Request.Builder()
                .url("https://music.youtube.com/generate_204")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val response = okHttpClient.newCall(request).execute()
            val latency = System.currentTimeMillis() - start
            SourceHealthStatus(
                state = if (response.isSuccessful || response.code == 204) HealthState.ONLINE else HealthState.DEGRADED,
                latencyMs = latency
            )
        } catch (e: Exception) {
            SourceHealthStatus(state = HealthState.OFFLINE, errorMessage = e.message)
        }
    }
}

@Singleton
class LrcLibSource @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val dispatchers: DispatcherProvider
) : MusicSource {
    override val id: String = "lrclib"
    override val name: String = "LRCLIB Lyrics Database"
    override val description: String = "Real-time line-by-line synchronized LRC lyrics provider"
    override val defaultEndpoint: String = "https://lrclib.net/api"
    override val capabilities: Set<SourceCapability> = setOf(SourceCapability.LYRICS)

    override suspend fun healthCheck(): SourceHealthStatus = withContext(dispatchers.io) {
        val start = System.currentTimeMillis()
        try {
            val request = Request.Builder()
                .url("https://lrclib.net/api/search?q=test")
                .header("User-Agent", "LofiQ/1.0")
                .build()
            val response = okHttpClient.newCall(request).execute()
            val latency = System.currentTimeMillis() - start
            SourceHealthStatus(
                state = if (response.isSuccessful) HealthState.ONLINE else HealthState.DEGRADED,
                latencyMs = latency
            )
        } catch (e: Exception) {
            SourceHealthStatus(state = HealthState.OFFLINE, errorMessage = e.message)
        }
    }
}

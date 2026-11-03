package com.lofiq.resolver.providers.youtube

import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.common.LofiQLogger
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class MirrorCapability {
    SEARCH,
    STREAM,
    BOTH
}

data class MirrorHealthRecord(
    val url: String,
    var isSearchHealthy: Boolean = true,
    var isStreamHealthy: Boolean = true,
    var lastCheckEpochMs: Long = 0L,
    var lastFailureEpochMs: Long = 0L,
    var latencyMs: Long = 0L,
    var lastErrorMessage: String? = null
)

@Singleton
class PipedMirrorManager @Inject constructor(
    private val baseHttpClient: OkHttpClient,
    private val json: Json,
    private val dispatchers: DispatcherProvider
) {
    companion object {
        const val FAILURE_COOLDOWN_MS = 3 * 60 * 1000L // 3 minutes cooldown for failed mirrors
        const val CONNECT_TIMEOUT_SEC = 3L
        const val READ_TIMEOUT_SEC = 4L

        val DEFAULT_MIRRORS = listOf(
            "https://api.piped.private.coffee",
            "https://pipedapi.tokhmi.xyz",
            "https://piped-api.lunar.icu",
            "https://api-piped.mha.fi"
        )
    }

    // Dedicated OkHttpClient with IPv4-first DNS sorting and bounded fast timeouts
    val fastMirrorClient: OkHttpClient by lazy {
        baseHttpClient.newBuilder()
            .dns(object : okhttp3.Dns {
                override fun lookup(hostname: String): List<java.net.InetAddress> {
                    return try {
                        val addresses = okhttp3.Dns.SYSTEM.lookup(hostname)
                        // Prefer IPv4 addresses over IPv6 to eliminate hangs on unroutable IPv6 networks
                        addresses.sortedBy { it is java.net.Inet6Address }
                    } catch (e: Exception) {
                        okhttp3.Dns.SYSTEM.lookup(hostname)
                    }
                }
            })
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val mirrorRegistry = ConcurrentHashMap<String, MirrorHealthRecord>()

    init {
        DEFAULT_MIRRORS.forEach { url ->
            mirrorRegistry[url] = MirrorHealthRecord(url = url)
        }
    }

    fun getAllMirrors(): List<String> = DEFAULT_MIRRORS

    fun getHealthySearchMirrors(): List<String> {
        val now = System.currentTimeMillis()
        return DEFAULT_MIRRORS
            .map { url -> mirrorRegistry.getOrPut(url) { MirrorHealthRecord(url) } }
            .filter { record ->
                record.isSearchHealthy || (now - record.lastFailureEpochMs > FAILURE_COOLDOWN_MS)
            }
            .sortedBy { if (it.latencyMs > 0) it.latencyMs else 9999L }
            .map { it.url }
            .ifEmpty { DEFAULT_MIRRORS.take(2) }
    }

    fun getHealthyStreamMirrors(): List<String> {
        val now = System.currentTimeMillis()
        return DEFAULT_MIRRORS
            .map { url -> mirrorRegistry.getOrPut(url) { MirrorHealthRecord(url) } }
            .filter { record ->
                record.isStreamHealthy || (now - record.lastFailureEpochMs > FAILURE_COOLDOWN_MS)
            }
            .sortedBy { if (it.latencyMs > 0) it.latencyMs else 9999L }
            .map { it.url }
            .ifEmpty { DEFAULT_MIRRORS.take(2) }
    }

    fun recordSuccess(url: String, latencyMs: Long, capability: MirrorCapability = MirrorCapability.BOTH) {
        val record = mirrorRegistry.getOrPut(url) { MirrorHealthRecord(url) }
        synchronized(record) {
            if (capability == MirrorCapability.SEARCH || capability == MirrorCapability.BOTH) {
                record.isSearchHealthy = true
            }
            if (capability == MirrorCapability.STREAM || capability == MirrorCapability.BOTH) {
                record.isStreamHealthy = true
            }
            record.latencyMs = latencyMs
            record.lastCheckEpochMs = System.currentTimeMillis()
            record.lastErrorMessage = null
        }
    }

    fun recordFailure(url: String, errorMessage: String?, capability: MirrorCapability = MirrorCapability.BOTH) {
        val record = mirrorRegistry.getOrPut(url) { MirrorHealthRecord(url) }
        synchronized(record) {
            val now = System.currentTimeMillis()
            if (capability == MirrorCapability.SEARCH || capability == MirrorCapability.BOTH) {
                record.isSearchHealthy = false
            }
            if (capability == MirrorCapability.STREAM || capability == MirrorCapability.BOTH) {
                record.isStreamHealthy = false
            }
            record.lastFailureEpochMs = now
            record.lastCheckEpochMs = now
            record.lastErrorMessage = errorMessage
        }
        LofiQLogger.w(
            LofiQLogger.Category.RESOLVER,
            "[PIPED_MIRROR] Marked $url unhealthy ($capability) for ${FAILURE_COOLDOWN_MS / 1000}s: $errorMessage"
        )
    }

    suspend fun probeMirror(url: String, sampleQuery: String = "Kamli"): MirrorHealthRecord = withContext(dispatchers.io) {
        val start = System.currentTimeMillis()
        val encoded = URLEncoder.encode(sampleQuery, StandardCharsets.UTF_8.toString())
        val searchUrl = "$url/search?q=$encoded&filter=music_songs"

        val request = Request.Builder()
            .url(searchUrl)
            .header("User-Agent", "LofiQ/1.0")
            .build()

        try {
            val response = fastMirrorClient.newCall(request).execute()
            val latency = System.currentTimeMillis() - start
            response.use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val root = json.parseToJsonElement(body).jsonObject
                    val items = root["items"]?.jsonArray ?: JsonArray(emptyList())
                    if (items.isNotEmpty()) {
                        recordSuccess(url, latency, MirrorCapability.SEARCH)
                        return@withContext mirrorRegistry[url]!!.copy(latencyMs = latency, isSearchHealthy = true)
                    } else {
                        recordFailure(url, "Empty search items", MirrorCapability.SEARCH)
                        return@withContext mirrorRegistry[url]!!.copy(latencyMs = latency, isSearchHealthy = false, lastErrorMessage = "Empty search items")
                    }
                } else {
                    recordFailure(url, "HTTP ${resp.code}", MirrorCapability.SEARCH)
                    return@withContext mirrorRegistry[url]!!.copy(latencyMs = latency, isSearchHealthy = false, lastErrorMessage = "HTTP ${resp.code}")
                }
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start
            recordFailure(url, e.message ?: "Connection error", MirrorCapability.SEARCH)
            return@withContext mirrorRegistry[url]!!.copy(latencyMs = latency, isSearchHealthy = false, lastErrorMessage = e.message)
        }
    }
}

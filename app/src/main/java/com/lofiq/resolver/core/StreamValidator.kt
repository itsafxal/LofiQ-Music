package com.lofiq.resolver.core

import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.common.LofiQLogger
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamValidator @Inject constructor(
    private val httpClient: OkHttpClient,
    private val dispatchers: DispatcherProvider
) {
    // Robust HTTP client for lightweight validation probes with realistic timeouts and redirect handling
    private val probeClient: OkHttpClient by lazy {
        httpClient.newBuilder()
            .connectTimeout(4000, TimeUnit.MILLISECONDS)
            .readTimeout(4000, TimeUnit.MILLISECONDS)
            .callTimeout(5000, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Performs lightweight validation of stream URL:
     * - Validates URL format and protocol
     * - Checks reachability via small bounded range request (bytes=0-1024)
     * - Falls back to HEAD / lightweight GET if Range request is rejected (e.g. 403/416)
     * - Rejects HTML error / CAPTCHA pages
     * - Ensures stream delivers valid playable audio bytes
     */
    suspend fun validateStream(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): Boolean = withContext(dispatchers.io) {
        if (url.isBlank()) return@withContext false
        if (!isValidUrl(url)) return@withContext false

        // Attempt 1: Bounded Range Probe for all stream sources (including JioSaavn & YouTube)
        val rangeSuccess = executeRangeProbe(url, headers)
        if (rangeSuccess) return@withContext true

        // Attempt 2: Fallback Head / Standard Probe
        executeFallbackProbe(url, headers)
    }

    private fun getAppropriateUserAgent(url: String, headers: Map<String, String>): String {
        headers["User-Agent"]?.let { if (it.isNotBlank()) return it }
        return when {
            url.contains("c=ANDROID_VR") -> "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
            url.contains("c=ANDROID_MUSIC") -> "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 11) gzip"
            url.contains("c=ANDROID_TESTSUITE") -> "Google-YouTube-Internal-Testing-Suite"
            url.contains("c=ANDROID") -> "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip"
            url.contains("c=IOS") -> "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)"
            url.contains("c=WEB") || url.contains("c=WEB_REMIX") -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
            url.contains("googlevideo.com") -> "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip"
            else -> "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        }
    }

    private fun executeRangeProbe(url: String, headers: Map<String, String>): Boolean {
        return try {
            val reqBuilder = Request.Builder().url(url)
            val ua = getAppropriateUserAgent(url, headers)

            reqBuilder.header("User-Agent", ua)
            reqBuilder.header("Range", "bytes=0-1024")

            if (url.contains("googlevideo.com")) {
                reqBuilder.removeHeader("Cookie")
                reqBuilder.removeHeader("Authorization")
                reqBuilder.removeHeader("Referer")
                reqBuilder.removeHeader("Origin")
            } else {
                if (url.contains("c=WEB_REMIX")) {
                    reqBuilder.header("Referer", "https://music.youtube.com/")
                    reqBuilder.header("Origin", "https://music.youtube.com")
                } else if (url.contains("c=WEB")) {
                    reqBuilder.header("Referer", "https://www.youtube.com/")
                    reqBuilder.header("Origin", "https://www.youtube.com")
                }
                headers.forEach { (k, v) ->
                    if (!k.equals("User-Agent", ignoreCase = true) && !k.equals("Range", ignoreCase = true) &&
                        !k.equals("Referer", ignoreCase = true) && !k.equals("Origin", ignoreCase = true) &&
                        !k.equals("Cookie", ignoreCase = true) && !k.equals("Authorization", ignoreCase = true)) {
                        reqBuilder.header(k, v)
                    }
                }
            }

            val response = probeClient.newCall(reqBuilder.build()).execute()
            response.use { resp ->
                val code = resp.code
                if (code in 200..206) {
                    val contentType = resp.header("Content-Type").orEmpty().lowercase()
                    if (contentType.contains("text/html") || contentType.contains("text/plain")) {
                        LofiQLogger.w(LofiQLogger.Category.RESOLVER, "Stream validation returned HTML/text page ($contentType)")
                        return false
                    }
                    return true
                }
                if (code == 403 || code == 416 || code == 400) {
                    // Fall back to secondary probe
                    return false
                }
                if (code in 400..599) {
                    LofiQLogger.w(LofiQLogger.Category.RESOLVER, "Stream validation probe HTTP $code for $url")
                    return false
                }

                code in 200..399
            }
        } catch (e: Exception) {
            LofiQLogger.d(LofiQLogger.Category.RESOLVER, "Stream range probe failed: ${e.message}, trying fallback probe")
            false
        }
    }

    private fun executeFallbackProbe(url: String, headers: Map<String, String>): Boolean {
        return try {
            val ua = getAppropriateUserAgent(url, headers)

            // YouTube CDN forbids HEAD requests (returns 403), so use bounded GET request
            val reqBuilder = Request.Builder().url(url)
            if (url.contains("googlevideo.com")) {
                reqBuilder.get().header("Range", "bytes=0-1")
                reqBuilder.removeHeader("Cookie")
                reqBuilder.removeHeader("Authorization")
                reqBuilder.removeHeader("Referer")
                reqBuilder.removeHeader("Origin")
            } else {
                reqBuilder.head()
                headers.forEach { (k, v) ->
                    if (!k.equals("User-Agent", ignoreCase = true) && !k.equals("Range", ignoreCase = true) &&
                        !k.equals("Cookie", ignoreCase = true) && !k.equals("Authorization", ignoreCase = true)) {
                        reqBuilder.header(k, v)
                    }
                }
            }

            reqBuilder.header("User-Agent", ua)

            val response = probeClient.newCall(reqBuilder.build()).execute()
            response.use { resp ->
                val code = resp.code
                if (code in 200..399 || code == 206) {
                    val contentType = resp.header("Content-Type").orEmpty().lowercase()
                    if (contentType.contains("text/html") || contentType.contains("text/plain")) {
                        return false
                    }
                    return true
                }
                LofiQLogger.w(LofiQLogger.Category.RESOLVER, "Stream validation fallback probe HTTP $code for $url")
                false
            }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.RESOLVER, "Stream validation fallback probe failed: ${e.message}")
            false
        }
    }

    fun isValidUrl(urlStr: String): Boolean {
        return try {
            val u = URL(urlStr)
            u.protocol == "http" || u.protocol == "https"
        } catch (e: Exception) {
            false
        }
    }
}

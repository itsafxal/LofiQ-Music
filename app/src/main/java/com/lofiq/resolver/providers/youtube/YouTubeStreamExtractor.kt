package com.lofiq.resolver.providers.youtube

import com.lofiq.auth.AuthRepository
import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.common.LofiQLogger
import com.lofiq.core.model.AudioQuality
import com.lofiq.resolver.core.ResolveRequest
import com.lofiq.resolver.core.ResolutionResult
import com.lofiq.resolver.core.ResolverException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeStreamExtractor @Inject constructor(
    private val httpClient: OkHttpClient,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
    private val authRepository: AuthRepository? = null,
    private val cipherDecipherer: YouTubeCipherDecipherer? = null
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun extractAudioStream(request: ResolveRequest): ResolutionResult = withContext(dispatchers.io) {
        val videoId = request.songId
        var lastStatusReason: String? = null
        val authHeaders = try {
            authRepository?.getAuthHeaders() ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }

        val isAuth = authHeaders.isNotEmpty()

        // 1. Try Android Client InnerTube Player API (Direct unthrottled signed audio streams - fastest & most reliable)
        try {
            val (androidResult, androidReason) = extractViaInnerTubeAndroid(videoId, request.preferredQuality)
            if (androidResult != null && isValidResolutionResult(androidResult)) {
                return@withContext androidResult
            }
            if (androidReason != null) lastStatusReason = androidReason
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.RESOLVER, "InnerTube Android extraction failed for $videoId: ${e.message}")
        }

        // 2. Try Android VR Client InnerTube Player API (High resilience unthrottled VR stream)
        try {
            val (vrResult, vrReason) = extractViaInnerTubeAndroidVr(videoId, request.preferredQuality)
            if (vrResult != null && isValidResolutionResult(vrResult)) {
                return@withContext vrResult
            }
            if (vrReason != null) lastStatusReason = vrReason
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.RESOLVER, "InnerTube Android VR extraction failed for $videoId: ${e.message}")
        }

        // 3. Try Android Music Client InnerTube Player API (High resilience unthrottled audio stream)
        try {
            val (musicResult, musicReason) = extractViaInnerTubeAndroidMusic(videoId, request.preferredQuality)
            if (musicResult != null && isValidResolutionResult(musicResult)) {
                return@withContext musicResult
            }
            if (musicReason != null) lastStatusReason = musicReason
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.RESOLVER, "InnerTube Android Music extraction failed for $videoId: ${e.message}")
        }

        // 4. Try iOS Client InnerTube Player API (Unthrottled iOS signed stream)
        try {
            val (iosResult, iosReason) = extractViaInnerTubeIos(videoId, request.preferredQuality)
            if (iosResult != null && isValidResolutionResult(iosResult)) {
                return@withContext iosResult
            }
            if (iosReason != null) lastStatusReason = iosReason
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.RESOLVER, "InnerTube iOS extraction failed for $videoId: ${e.message}")
        }

        // 5. Try Android TestSuite Client InnerTube Player API
        try {
            val (testSuiteResult, testSuiteReason) = extractViaInnerTubeAndroidTestSuite(videoId, request.preferredQuality)
            if (testSuiteResult != null && isValidResolutionResult(testSuiteResult)) {
                return@withContext testSuiteResult
            }
            if (testSuiteReason != null) lastStatusReason = testSuiteReason
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.RESOLVER, "InnerTube Android TestSuite extraction failed for $videoId: ${e.message}")
        }

        // 6. Try TV Simply Embedded Player InnerTube API
        try {
            val (tvEmbeddedResult, tvEmbeddedReason) = extractViaInnerTubeTvSimplyEmbedded(videoId, request.preferredQuality)
            if (tvEmbeddedResult != null && isValidResolutionResult(tvEmbeddedResult)) {
                return@withContext tvEmbeddedResult
            }
            if (tvEmbeddedReason != null) lastStatusReason = tvEmbeddedReason
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.RESOLVER, "InnerTube TV Simply Embedded extraction failed for $videoId: ${e.message}")
        }

        // 7. Try TVHTML5 Client InnerTube Player API
        try {
            val (tvResult, tvReason) = extractViaInnerTubeTvHtml5(videoId, request.preferredQuality)
            if (tvResult != null && isValidResolutionResult(tvResult)) {
                return@withContext tvResult
            }
            if (tvReason != null) lastStatusReason = tvReason
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.RESOLVER, "InnerTube TVHTML5 extraction failed for $videoId: ${e.message}")
        }

        // 8. Try Public Piped & Invidious Stream Mirror Fallback
        try {
            val (pipedResult, pipedReason) = extractViaPipedFallback(videoId, request.preferredQuality)
            if (pipedResult != null && isValidResolutionResult(pipedResult)) {
                LofiQLogger.i(LofiQLogger.Category.RESOLVER, "Resolved YouTube audio stream via mirror proxy for $videoId")
                return@withContext pipedResult
            }
            if (pipedReason != null) lastStatusReason = pipedReason
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.RESOLVER, "Mirror stream fallback failed for $videoId: ${e.message}")
        }

        // 9. Fallback to YouTube Music Web Remix client
        try {
            val (webResult, webReason) = extractViaInnerTubeWebRemix(videoId, request.preferredQuality, authHeaders)
            if (webResult != null && isValidResolutionResult(webResult)) {
                return@withContext webResult
            }
            if (webReason != null) lastStatusReason = webReason
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.RESOLVER, "InnerTube Web Remix extraction failed for $videoId: ${e.message}")
        }

        // 10. Fallback to YouTube Web InnerTube endpoint
        try {
            val (ytWebResult, ytWebReason) = extractViaInnerTubeWeb(videoId, request.preferredQuality, authHeaders)
            if (ytWebResult != null && isValidResolutionResult(ytWebResult)) {
                return@withContext ytWebResult
            }
            if (ytWebReason != null) lastStatusReason = ytWebReason
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.RESOLVER, "InnerTube YouTube Web extraction failed for $videoId: ${e.message}")
        }

        val structuredReason = when {
            lastStatusReason != null -> "YouTube playback restricted for videoId: $videoId ($lastStatusReason)"
            else -> "No direct unthrottled audio stream found on YouTube for videoId: $videoId"
        }

        LofiQLogger.w(
            LofiQLogger.Category.RESOLVER,
            "[RESOLVER] videoId=$videoId audioFormats=0 validSources=0 reason=$structuredReason"
        )

        throw ResolverException.SourceUnavailable(
            songId = videoId,
            reason = structuredReason
        )
    }

    private fun getClientCountry(): String = java.util.Locale.getDefault().country.takeIf { it.isNotBlank() } ?: "IN"
    private fun getClientLanguage(): String = java.util.Locale.getDefault().language.takeIf { it.isNotBlank() } ?: "en"

    private suspend fun extractViaInnerTubeAndroidTestSuite(
        videoId: String,
        preferredQuality: AudioQuality
    ): Pair<ResolutionResult?, String?> {
        val url = "https://www.youtube.com/youtubei/v1/player?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8&prettyPrint=false"
        val payload = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "ANDROID_TESTSUITE")
                    put("clientVersion", "1.9")
                    put("androidSdkVersion", 30)
                    put("osName", "Android")
                    put("osVersion", "11")
                    put("hl", getClientLanguage())
                    put("gl", getClientCountry())
                }
            }
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            putJsonObject("playbackContext") {
                putJsonObject("contentPlaybackContext") {
                    put("html5Preference", "HTML5_PREF_WANTS")
                }
            }
        }

        val reqBuilder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .header("User-Agent", "Google-YouTube-Internal-Testing-Suite")
            .header("X-YouTube-Client-Name", "3")
            .header("X-YouTube-Client-Version", "1.9")

        val response = httpClient.newCall(reqBuilder.build()).execute()
        response.use { resp ->
            if (!resp.isSuccessful) return Pair(null, "HTTP ${resp.code}")
            val body = resp.body?.string() ?: return Pair(null, "Empty response body")
            return parseInnerTubePlayerResponse(videoId, body, preferredQuality)
        }
    }

    private suspend fun extractViaInnerTubeTvSimplyEmbedded(
        videoId: String,
        preferredQuality: AudioQuality
    ): Pair<ResolutionResult?, String?> {
        val url = "https://www.youtube.com/youtubei/v1/player?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8&prettyPrint=false"
        val payload = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "TVHTML5_SIMPLY_EMBEDDED_PLAYER")
                    put("clientVersion", "2.0")
                    put("platform", "TV")
                    put("hl", getClientLanguage())
                    put("gl", getClientCountry())
                }
                putJsonObject("thirdParty") {
                    put("embedUrl", "https://www.youtube.com/embed/$videoId")
                }
            }
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            putJsonObject("playbackContext") {
                putJsonObject("contentPlaybackContext") {
                    put("html5Preference", "HTML5_PREF_WANTS")
                }
            }
        }

        val reqBuilder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .header("User-Agent", "Mozilla/5.0 (SMART-TV; Linux; Tizen 5.0) AppleWebKit/538.1 (KHTML, like Gecko) Version/5.0 TV Safari/538.1")
            .header("X-YouTube-Client-Name", "85")
            .header("X-YouTube-Client-Version", "2.0")

        val response = httpClient.newCall(reqBuilder.build()).execute()
        response.use { resp ->
            if (!resp.isSuccessful) return Pair(null, "HTTP ${resp.code}")
            val body = resp.body?.string() ?: return Pair(null, "Empty response body")
            return parseInnerTubePlayerResponse(videoId, body, preferredQuality)
        }
    }

    private val mirrorManager: PipedMirrorManager by lazy {
        PipedMirrorManager(httpClient, json, dispatchers)
    }

    private fun extractViaPipedFallback(
        videoId: String,
        preferredQuality: AudioQuality
    ): Pair<ResolutionResult?, String?> {
        val healthyMirrors = mirrorManager.getHealthyStreamMirrors()
        for (mirror in healthyMirrors) {
            val start = System.currentTimeMillis()
            try {
                val url = "$mirror/streams/$videoId"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()
                val response = mirrorManager.fastMirrorClient.newCall(request).execute()
                val latency = System.currentTimeMillis() - start
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        mirrorManager.recordFailure(mirror, "HTTP ${resp.code}", MirrorCapability.STREAM)
                        return@use
                    }
                    val body = resp.body?.string() ?: return@use
                    val root = json.parseToJsonElement(body).jsonObject
                    val audioStreams = root["audioStreams"]?.jsonArray ?: return@use
                    val extracted = mutableListOf<ExtractedAudioFormat>()
                    for (stream in audioStreams) {
                        val obj = stream.jsonObject
                        val streamUrl = obj["url"]?.jsonPrimitive?.contentOrNull ?: continue
                        if (!isValidUrl(streamUrl)) continue
                        val mime = obj["mimeType"]?.jsonPrimitive?.contentOrNull ?: "audio/mp4"
                        val bitrate = obj["bitrate"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 128000
                        val codec = obj["codec"]?.jsonPrimitive?.contentOrNull ?: if (mime.contains("webm")) "opus" else "aac"
                        val cleanMime = mime.substringBefore(";").trim()
                        extracted.add(
                            ExtractedAudioFormat(
                                url = streamUrl,
                                mimeType = cleanMime,
                                bitrate = bitrate,
                                durationMs = 0L,
                                codec = codec
                            )
                        )
                    }
                    if (extracted.isNotEmpty()) {
                        mirrorManager.recordSuccess(mirror, latency, MirrorCapability.STREAM)
                        val sorted = extracted.sortedByDescending { it.bitrate }
                        val selected = when (preferredQuality) {
                            AudioQuality.DATA_SAVER -> sorted.last()
                            AudioQuality.STANDARD -> sorted.firstOrNull { it.bitrate in 90_000..140_000 } ?: if (sorted.size > 1) sorted[sorted.size / 2] else sorted.first()
                            AudioQuality.HIGH, AudioQuality.AUTO -> sorted.first()
                        }
                        val result = ResolutionResult(
                            songId = videoId,
                            streamUrl = selected.url,
                            mimeType = selected.mimeType,
                            durationMs = selected.durationMs,
                            bitrateKbps = selected.bitrate / 1000,
                            quality = preferredQuality,
                            providerId = "piped_proxy",
                            codec = selected.codec,
                            sampleRate = selected.sampleRate,
                            channels = selected.channels,
                            expiryEpochMs = extractExpiryFromUrl(selected.url),
                            isCacheable = true
                        )
                        return Pair(result, null)
                    } else {
                        mirrorManager.recordFailure(mirror, "No audio streams in response", MirrorCapability.STREAM)
                    }
                }
            } catch (e: Exception) {
                mirrorManager.recordFailure(mirror, e.message, MirrorCapability.STREAM)
                LofiQLogger.d(LofiQLogger.Category.RESOLVER, "Piped mirror $mirror failed for $videoId: ${e.message}")
            }
        }

        // Invidious Fallback Mirrors with fast client and active hosts
        val invidiousMirrors = listOf(
            "https://invidious.nerdvpn.de",
            "https://invidious.drgns.space",
            "https://invidious.fdn.fr"
        )
        for (mirror in invidiousMirrors) {
            try {
                val url = "$mirror/api/v1/videos/$videoId"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()
                val response = mirrorManager.fastMirrorClient.newCall(request).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body?.string() ?: return@use
                    val root = json.parseToJsonElement(body).jsonObject
                    val adaptiveFormats = root["adaptiveFormats"]?.jsonArray ?: return@use
                    val extracted = mutableListOf<ExtractedAudioFormat>()
                    for (stream in adaptiveFormats) {
                        val obj = stream.jsonObject
                        val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: ""
                        if (!type.startsWith("audio/")) continue
                        val streamUrl = obj["url"]?.jsonPrimitive?.contentOrNull ?: continue
                        if (!isValidUrl(streamUrl)) continue
                        val bitrate = obj["bitrate"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 128000
                        val cleanMime = type.substringBefore(";").trim()
                        val codec = if (cleanMime.contains("webm")) "opus" else "aac"
                        extracted.add(
                            ExtractedAudioFormat(
                                url = streamUrl,
                                mimeType = cleanMime,
                                bitrate = bitrate,
                                durationMs = 0L,
                                codec = codec
                            )
                        )
                    }
                    if (extracted.isNotEmpty()) {
                        val sorted = extracted.sortedByDescending { it.bitrate }
                        val selected = when (preferredQuality) {
                            AudioQuality.DATA_SAVER -> sorted.last()
                            AudioQuality.STANDARD -> sorted.firstOrNull { it.bitrate in 90_000..140_000 } ?: if (sorted.size > 1) sorted[sorted.size / 2] else sorted.first()
                            AudioQuality.HIGH, AudioQuality.AUTO -> sorted.first()
                        }
                        val result = ResolutionResult(
                            songId = videoId,
                            streamUrl = selected.url,
                            mimeType = selected.mimeType,
                            durationMs = selected.durationMs,
                            bitrateKbps = selected.bitrate / 1000,
                            quality = preferredQuality,
                            providerId = "invidious_proxy",
                            codec = selected.codec,
                            sampleRate = selected.sampleRate,
                            channels = selected.channels,
                            expiryEpochMs = extractExpiryFromUrl(selected.url),
                            isCacheable = true
                        )
                        return Pair(result, null)
                    }
                }
            } catch (e: Exception) {
                LofiQLogger.d(LofiQLogger.Category.RESOLVER, "Invidious mirror $mirror failed for $videoId: ${e.message}")
            }
        }

        return Pair(null, "All stream mirrors failed or returned no audio streams")
    }

    private suspend fun extractViaInnerTubeWebRemix(
        videoId: String,
        preferredQuality: AudioQuality,
        authHeaders: Map<String, String>
    ): Pair<ResolutionResult?, String?> {
        val url = "https://music.youtube.com/youtubei/v1/player?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8&prettyPrint=false"
        val payload = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", "1.20260320.01.00")
                    put("hl", getClientLanguage())
                    put("gl", getClientCountry())
                }
            }
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            putJsonObject("playbackContext") {
                putJsonObject("contentPlaybackContext") {
                    put("html5Preference", "HTML5_PREF_WANTS")
                    put("signatureTimestamp", 20737)
                }
            }
        }

        val reqBuilder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
            .header("Referer", "https://music.youtube.com/")
            .header("Origin", "https://music.youtube.com")
            .header("X-Origin", "https://music.youtube.com")
            .header("X-YouTube-Client-Name", "67")
            .header("X-YouTube-Client-Version", "1.20260320.01.00")

        authHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }

        val response = httpClient.newCall(reqBuilder.build()).execute()
        response.use { resp ->
            if (!resp.isSuccessful) return Pair(null, "HTTP ${resp.code}")
            val body = resp.body?.string() ?: return Pair(null, "Empty response body")
            return parseInnerTubePlayerResponse(videoId, body, preferredQuality)
        }
    }

    private suspend fun extractViaInnerTubeWeb(
        videoId: String,
        preferredQuality: AudioQuality,
        authHeaders: Map<String, String>
    ): Pair<ResolutionResult?, String?> {
        val url = "https://www.youtube.com/youtubei/v1/player?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8&prettyPrint=false"
        val payload = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB")
                    put("clientVersion", "2.20240813.01.00")
                    put("hl", getClientLanguage())
                    put("gl", getClientCountry())
                }
            }
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            putJsonObject("playbackContext") {
                putJsonObject("contentPlaybackContext") {
                    put("html5Preference", "HTML5_PREF_WANTS")
                    put("signatureTimestamp", 20725)
                }
            }
        }

        val reqBuilder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
            .header("Referer", "https://www.youtube.com/")
            .header("Origin", "https://www.youtube.com")
            .header("X-Origin", "https://www.youtube.com")
            .header("X-YouTube-Client-Name", "1")
            .header("X-YouTube-Client-Version", "2.20240813.01.00")

        if (authHeaders.containsKey("Cookie")) {
            val cookies = authHeaders["Cookie"]!!
            val sapisid = com.lofiq.auth.AuthCryptoUtils.extractSapisid(cookies)
            reqBuilder.header("Cookie", cookies)
            if (!sapisid.isNullOrBlank()) {
                val ytAuth = com.lofiq.auth.AuthCryptoUtils.generateSapisidHash(sapisid, "https://www.youtube.com")
                reqBuilder.header("Authorization", ytAuth)
            }
        }

        val response = httpClient.newCall(reqBuilder.build()).execute()
        response.use { resp ->
            if (!resp.isSuccessful) return Pair(null, "HTTP ${resp.code}")
            val body = resp.body?.string() ?: return Pair(null, "Empty response body")
            return parseInnerTubePlayerResponse(videoId, body, preferredQuality)
        }
    }

    private suspend fun extractViaInnerTubeAndroidVr(
        videoId: String,
        preferredQuality: AudioQuality
    ): Pair<ResolutionResult?, String?> {
        val url = "https://www.youtube.com/youtubei/v1/player?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8&prettyPrint=false"
        val payload = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "ANDROID_VR")
                    put("clientVersion", "1.60.19")
                    put("deviceMake", "Oculus")
                    put("deviceModel", "Quest 3")
                    put("androidSdkVersion", 32)
                    put("osName", "Android")
                    put("osVersion", "12L")
                    put("hl", getClientLanguage())
                    put("gl", getClientCountry())
                }
            }
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            putJsonObject("playbackContext") {
                putJsonObject("contentPlaybackContext") {
                    put("html5Preference", "HTML5_PREF_WANTS")
                }
            }
        }

        val reqBuilder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .header("User-Agent", "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip")
            .header("X-YouTube-Client-Name", "28")

        val response = httpClient.newCall(reqBuilder.build()).execute()
        response.use { resp ->
            if (!resp.isSuccessful) return Pair(null, "HTTP ${resp.code}")
            val body = resp.body?.string() ?: return Pair(null, "Empty response body")
            return parseInnerTubePlayerResponse(videoId, body, preferredQuality)
        }
    }

    private suspend fun extractViaInnerTubeAndroidMusic(
        videoId: String,
        preferredQuality: AudioQuality
    ): Pair<ResolutionResult?, String?> {
        val url = "https://www.youtube.com/youtubei/v1/player?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8&prettyPrint=false"
        val payload = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "ANDROID_MUSIC")
                    put("clientVersion", "7.27.52")
                    put("androidSdkVersion", 30)
                    put("osName", "Android")
                    put("osVersion", "11")
                    put("hl", getClientLanguage())
                    put("gl", getClientCountry())
                }
            }
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            putJsonObject("playbackContext") {
                putJsonObject("contentPlaybackContext") {
                    put("html5Preference", "HTML5_PREF_WANTS")
                }
            }
        }

        val reqBuilder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .header("User-Agent", "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 11) gzip")
            .header("X-YouTube-Client-Name", "21")

        val response = httpClient.newCall(reqBuilder.build()).execute()
        response.use { resp ->
            if (!resp.isSuccessful) return Pair(null, "HTTP ${resp.code}")
            val body = resp.body?.string() ?: return Pair(null, "Empty response body")
            return parseInnerTubePlayerResponse(videoId, body, preferredQuality)
        }
    }

    private suspend fun extractViaInnerTubeAndroid(
        videoId: String,
        preferredQuality: AudioQuality
    ): Pair<ResolutionResult?, String?> {
        val url = "https://www.youtube.com/youtubei/v1/player?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8&prettyPrint=false"
        val payload = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "ANDROID")
                    put("clientVersion", "21.26.364")
                    put("androidSdkVersion", 30)
                    put("osName", "Android")
                    put("osVersion", "11")
                    put("hl", getClientLanguage())
                    put("gl", getClientCountry())
                }
            }
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            putJsonObject("playbackContext") {
                putJsonObject("contentPlaybackContext") {
                    put("html5Preference", "HTML5_PREF_WANTS")
                }
            }
        }

        val reqBuilder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .header("User-Agent", "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip")
            .header("X-YouTube-Client-Name", "3")
            .header("X-YouTube-Client-Version", "21.26.364")

        val response = httpClient.newCall(reqBuilder.build()).execute()
        response.use { resp ->
            if (!resp.isSuccessful) return Pair(null, "HTTP ${resp.code}")
            val body = resp.body?.string() ?: return Pair(null, "Empty response body")
            return parseInnerTubePlayerResponse(videoId, body, preferredQuality)
        }
    }

    private suspend fun extractViaInnerTubeIos(
        videoId: String,
        preferredQuality: AudioQuality
    ): Pair<ResolutionResult?, String?> {
        val url = "https://www.youtube.com/youtubei/v1/player?key=AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc&prettyPrint=false"
        val payload = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "IOS")
                    put("clientVersion", "19.45.4")
                    put("deviceMake", "Apple")
                    put("platform", "MOBILE")
                    put("deviceModel", "iPhone16,2")
                    put("osName", "iPhone")
                    put("osVersion", "18.1.0.22B83")
                    put("hl", getClientLanguage())
                    put("gl", getClientCountry())
                }
            }
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            putJsonObject("playbackContext") {
                putJsonObject("contentPlaybackContext") {
                    put("html5Preference", "HTML5_PREF_WANTS")
                }
            }
        }

        val reqBuilder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .header("User-Agent", "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)")
            .header("X-YouTube-Client-Name", "5")

        val response = httpClient.newCall(reqBuilder.build()).execute()
        response.use { resp ->
            if (!resp.isSuccessful) return Pair(null, "HTTP ${resp.code}")
            val body = resp.body?.string() ?: return Pair(null, "Empty response body")
            return parseInnerTubePlayerResponse(videoId, body, preferredQuality)
        }
    }

    private suspend fun extractViaInnerTubeTvHtml5(
        videoId: String,
        preferredQuality: AudioQuality
    ): Pair<ResolutionResult?, String?> {
        val url = "https://www.youtube.com/youtubei/v1/player?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8&prettyPrint=false"
        val payload = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "TVHTML5")
                    put("clientVersion", "7.20240813.07.00")
                    put("platform", "TV")
                    put("hl", getClientLanguage())
                    put("gl", getClientCountry())
                }
            }
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            putJsonObject("playbackContext") {
                putJsonObject("contentPlaybackContext") {
                    put("html5Preference", "HTML5_PREF_WANTS")
                }
            }
        }

        val reqBuilder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .header("User-Agent", "Mozilla/5.0 (SmartHub; SMART-TV; U; Linux/SmartTV)")
            .header("X-YouTube-Client-Name", "7")

        val response = httpClient.newCall(reqBuilder.build()).execute()
        response.use { resp ->
            if (!resp.isSuccessful) return Pair(null, "HTTP ${resp.code}")
            val body = resp.body?.string() ?: return Pair(null, "Empty response body")
            return parseInnerTubePlayerResponse(videoId, body, preferredQuality)
        }
    }

    private suspend fun parseInnerTubePlayerResponse(
        videoId: String,
        jsonStr: String,
        preferredQuality: AudioQuality
    ): Pair<ResolutionResult?, String?> {
        try {
            val root = json.parseToJsonElement(jsonStr).jsonObject
            val playability = root["playabilityStatus"]?.jsonObject
            val status = playability?.get("status")?.jsonPrimitive?.contentOrNull
            val reason = playability?.get("reason")?.jsonPrimitive?.contentOrNull

            if (status != null && status != "OK") {
                val failureDescription = if (!reason.isNullOrBlank()) "$status: $reason" else status
                LofiQLogger.w(
                    LofiQLogger.Category.RESOLVER,
                    "[RESOLVER] videoId=$videoId playabilityStatus=$status reason=$reason"
                )
                return Pair(null, failureDescription)
            }

            val streamingData = root["streamingData"]?.jsonObject ?: return Pair(null, "No streamingData in response")
            val adaptiveFormats = streamingData["adaptiveFormats"]?.jsonArray ?: JsonArray(emptyList())
            val regularFormats = streamingData["formats"]?.jsonArray ?: JsonArray(emptyList())
            val allFormats = adaptiveFormats + regularFormats

            // Inspect and filter all received formats
            var totalAudioFormats = 0
            val validAudioStreams = mutableListOf<ExtractedAudioFormat>()

            for (item in allFormats) {
                val formatObj = item.jsonObject
                val mimeTypeRaw = formatObj["mimeType"]?.jsonPrimitive?.contentOrNull ?: continue
                val isAudio = mimeTypeRaw.startsWith("audio/") || mimeTypeRaw.startsWith("video/mp4")
                if (!isAudio) continue

                totalAudioFormats++

                // Extract stream URL if legitimately available or decipher signatureCipher
                var streamUrl = formatObj["url"]?.jsonPrimitive?.contentOrNull
                if (streamUrl.isNullOrBlank() || !isValidUrl(streamUrl)) {
                    val cipher = formatObj["signatureCipher"]?.jsonPrimitive?.contentOrNull
                        ?: formatObj["cipher"]?.jsonPrimitive?.contentOrNull
                    if (!cipher.isNullOrBlank() && cipherDecipherer != null) {
                        streamUrl = cipherDecipherer.decipher(cipher)
                    }
                }

                if (streamUrl.isNullOrBlank() || !isValidUrl(streamUrl)) continue

                val bitrate = formatObj["bitrate"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 128000
                val approxDurationMs = formatObj["approxDurationMs"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                val cleanMimeType = mimeTypeRaw.substringBefore(";").trim()
                val codec = if (cleanMimeType.contains("webm") || cleanMimeType.contains("opus")) "opus" else "aac"
                val audioSampleRate = formatObj["audioSampleRate"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 44100
                val audioChannels = formatObj["audioChannels"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 2

                validAudioStreams.add(
                    ExtractedAudioFormat(
                        url = streamUrl,
                        mimeType = cleanMimeType,
                        bitrate = bitrate,
                        durationMs = approxDurationMs,
                        codec = codec,
                        sampleRate = audioSampleRate,
                        channels = audioChannels
                    )
                )
            }

            if (validAudioStreams.isEmpty()) {
                val emptyReason = "No direct URLs in $totalAudioFormats audio formats (requires authentication or cipher)"
                LofiQLogger.d(
                    LofiQLogger.Category.RESOLVER,
                    "[RESOLVER] videoId=$videoId audioFormats=$totalAudioFormats validSources=0 reason=$emptyReason"
                )
                return Pair(null, emptyReason)
            }

            // Select best audio stream based on preferred quality among valid playable streams
            val sortedStreams = validAudioStreams.sortedByDescending { it.bitrate }
            val selected = when (preferredQuality) {
                AudioQuality.DATA_SAVER -> sortedStreams.last()
                AudioQuality.STANDARD -> {
                    sortedStreams.firstOrNull { it.bitrate in 90_000..140_000 }
                        ?: if (sortedStreams.size > 1) sortedStreams[sortedStreams.size / 2] else sortedStreams.first()
                }
                AudioQuality.HIGH, AudioQuality.AUTO -> {
                    sortedStreams.first()
                }
            }

            val expiryEpochMs = extractExpiryFromUrl(selected.url)

            val streamHeaders = when {
                selected.url.contains("c=ANDROID_VR") -> mapOf(
                    "User-Agent" to "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
                )
                selected.url.contains("c=ANDROID_MUSIC") -> mapOf(
                    "User-Agent" to "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 11) gzip"
                )
                selected.url.contains("c=ANDROID") || selected.url.contains("c=ANDROID_TESTSUITE") -> mapOf(
                    "User-Agent" to "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip"
                )
                selected.url.contains("c=IOS") -> mapOf(
                    "User-Agent" to "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)"
                )
                selected.url.contains("c=WEB_REMIX") -> mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                )
                selected.url.contains("c=WEB") -> mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                )
                else -> mapOf(
                    "User-Agent" to "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip"
                )
            }

            val result = ResolutionResult(
                songId = videoId,
                streamUrl = selected.url,
                mimeType = selected.mimeType,
                durationMs = selected.durationMs,
                bitrateKbps = selected.bitrate / 1000,
                quality = preferredQuality,
                providerId = "youtube",
                codec = selected.codec,
                sampleRate = selected.sampleRate,
                channels = selected.channels,
                expiryEpochMs = expiryEpochMs,
                headers = streamHeaders,
                isCacheable = true
            )

            LofiQLogger.i(
                LofiQLogger.Category.RESOLVER,
                "[RESOLVER] videoId=$videoId audioFormats=$totalAudioFormats validSources=${validAudioStreams.size} selected=${result.mimeType} bitrate=${result.bitrateKbps}kbps quality=${preferredQuality.name} expiry=$expiryEpochMs"
            )

            return Pair(result, null)
        } catch (e: Exception) {
            LofiQLogger.e(LofiQLogger.Category.RESOLVER, "Error parsing player response: ${e.message}")
            return Pair(null, "Parsing error: ${e.message}")
        }
    }

    private fun isValidResolutionResult(result: ResolutionResult): Boolean {
        if (result.streamUrl.isBlank()) return false
        if (!isValidUrl(result.streamUrl)) return false
        if (result.mimeType.isBlank() || (!result.mimeType.startsWith("audio/") && result.mimeType != "video/mp4")) return false
        if (result.bitrateKbps <= 0) return false
        return true
    }

    private fun isValidUrl(urlStr: String): Boolean {
        return try {
            val u = URL(urlStr)
            u.protocol == "http" || u.protocol == "https"
        } catch (e: Exception) {
            false
        }
    }

    private fun extractExpiryFromUrl(url: String): Long {
        return try {
            val pattern = java.util.regex.Pattern.compile("expire=(\\d+)")
            val matcher = pattern.matcher(url)
            if (matcher.find()) {
                val expireSec = matcher.group(1)?.toLongOrNull() ?: 0L
                expireSec * 1000L
            } else {
                System.currentTimeMillis() + (6 * 3600 * 1000L) // Default 6h expiry
            }
        } catch (e: Exception) {
            System.currentTimeMillis() + (6 * 3600 * 1000L)
        }
    }

    private data class ExtractedAudioFormat(
        val url: String,
        val mimeType: String,
        val bitrate: Int,
        val durationMs: Long,
        val codec: String = "aac",
        val sampleRate: Int = 44100,
        val channels: Int = 2
    )
}

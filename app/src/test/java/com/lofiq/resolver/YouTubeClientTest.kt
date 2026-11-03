package com.lofiq.resolver

import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Test
import java.util.concurrent.TimeUnit

class YouTubeClientTest {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val testVideoIds = listOf(
        "4NRXx6U8ABQ", // Blinding Lights
        "JGwWNGJdvx8", // Shape of You
        "dQw4w9WgXcQ", // Never Gonna Give You Up
        "kTJczUoc56U", // Stay
        "7wtfhZwyrcc"  // Believer
    )

    @Test
    fun testClients() {
        for (videoId in testVideoIds) {
            println("\n>>> TESTING VIDEO: $videoId <<<")

            testClient(videoId, "ANDROID_TESTSUITE", "1.9", "Google-HTTP-Java-Client/1.43.3 (gzip)", 31)
            testClient(videoId, "ANDROID_VR", "1.61.48", "Mozilla/5.0 (Linux; Android 12; Quest 3) AppleWebKit/537.36", 32)
            testClient(videoId, "TVHTML5_SIMPLY_EMBEDDED_PLAYER", "2.0", "Mozilla/5.0 (SMART-TV; Linux; Tizen 5.0) AppleWebKit/538.1", null)
            testClient(videoId, "WEB_EMBEDDED_PLAYER", "1.20240901.01.00", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36", null)
            testClient(videoId, "IOS", "19.45.4", "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X)", null)
            testClient(videoId, "ANDROID_MUSIC", "7.21.50", "com.google.android.apps.youtube.music/7.21.50 (Linux; U; Android 14)", 34)
        }
    }

    private fun testClient(
        videoId: String,
        clientName: String,
        clientVersion: String,
        userAgent: String,
        androidSdkVersion: Int?
    ) {
        val url = "https://www.youtube.com/youtubei/v1/player"
        val payload = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", clientName)
                    put("clientVersion", clientVersion)
                    if (androidSdkVersion != null) {
                        put("androidSdkVersion", androidSdkVersion)
                        put("osName", "Android")
                        put("osVersion", "14")
                    }
                    put("hl", "en")
                    put("gl", "US")
                }
            }
            put("videoId", videoId)
            putJsonObject("playbackContext") {
                putJsonObject("contentPlaybackContext") {
                    put("html5Preference", "HTML5_PREF_WANTS")
                }
            }
        }

        try {
            val req = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .header("User-Agent", userAgent)
                .header("X-YouTube-Client-Name", when (clientName) {
                    "ANDROID_TESTSUITE" -> "30"
                    "ANDROID_VR" -> "28"
                    "TVHTML5_SIMPLY_EMBEDDED_PLAYER" -> "85"
                    "WEB_EMBEDDED_PLAYER" -> "56"
                    "IOS" -> "5"
                    "ANDROID_MUSIC" -> "26"
                    else -> "1"
                })
                .header("X-YouTube-Client-Version", clientVersion)
                .build()

            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            val root = json.parseToJsonElement(body).jsonObject
            val playability = root["playabilityStatus"]?.jsonObject
            val status = playability?.get("status")?.jsonPrimitive?.contentOrNull
            val reason = playability?.get("reason")?.jsonPrimitive?.contentOrNull

            val streamingData = root["streamingData"]?.jsonObject
            val adaptive = streamingData?.get("adaptiveFormats")?.jsonArray ?: JsonArray(emptyList())
            val formats = streamingData?.get("formats")?.jsonArray ?: JsonArray(emptyList())
            val all = adaptive + formats
            val withDirectUrl = all.count { it.jsonObject["url"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true }

            println("  [$clientName] HTTP ${resp.code} | status=$status | directUrls=$withDirectUrl/${all.size} | reason=$reason")
        } catch (e: Exception) {
            println("  [$clientName] EXCEPTION: ${e.message}")
        }
    }
}

package com.sargamify.feature.debug

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Test
import java.util.concurrent.TimeUnit

class InnerTubeClientProbeTest {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    @Test
    fun testInspectDecipher() {
        val videoId = "60ItHLz5WEA"
        val clientsToTest = listOf(
            "ANDROID_TESTSUITE" to Pair(
                """{"context":{"client":{"clientName":"ANDROID_TESTSUITE","clientVersion":"1.9","androidSdkVersion":30,"osName":"Android","osVersion":"11","hl":"en","gl":"IN"}},"videoId":"$videoId","contentCheckOk":true,"racyCheckOk":true}""",
                "Google-YouTube-Internal-Testing-Suite"
            ),
            "TVHTML5_SIMPLY_EMBEDDED_PLAYER" to Pair(
                """{"context":{"client":{"clientName":"TVHTML5_SIMPLY_EMBEDDED_PLAYER","clientVersion":"2.0","platform":"TV","hl":"en","gl":"IN"},"thirdParty":{"embedUrl":"https://www.youtube.com/embed/$videoId"}},"videoId":"$videoId","contentCheckOk":true,"racyCheckOk":true}""",
                "Mozilla/5.0 (SMART-TV; Linux; Tizen 5.0) AppleWebKit/538.1 (KHTML, like Gecko) Version/5.0 TV Safari/538.1"
            ),
            "WEB_EMBEDDED" to Pair(
                """{"context":{"client":{"clientName":"WEB_EMBEDDED_PLAYER","clientVersion":"1.20240813.01.00","hl":"en","gl":"IN"},"thirdParty":{"embedUrl":"https://www.youtube.com/embed/$videoId"}},"videoId":"$videoId","contentCheckOk":true,"racyCheckOk":true}""",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
            )
        )

        for ((name, pair) in clientsToTest) {
            val req = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8&prettyPrint=false")
                .post(pair.first.toRequestBody(jsonMediaType))
                 .header("User-Agent", pair.second)
                .build()
            val resp = httpClient.newCall(req).execute()
            val code = resp.code
            val body = resp.body?.string().orEmpty()
            val hasStreaming = body.contains("\"streamingData\"")
            val statusMatch = java.util.regex.Pattern.compile("\"status\":\"([^\"]+)\"").matcher(body)
            val status = if (statusMatch.find()) statusMatch.group(1) else "UNKNOWN"
            val reasonMatch = java.util.regex.Pattern.compile("\"reason\":\"([^\"]+)\"").matcher(body)
            val reason = if (reasonMatch.find()) reasonMatch.group(1) else "None"
            val hasCipher = body.contains("signatureCipher")
            val hasDirect = body.contains("\"url\":\"https:")
            println("[$name] HTTP $code | Status: $status | Reason: $reason | streamingData: $hasStreaming | directUrl: $hasDirect | cipher: $hasCipher")
        }
    }
}

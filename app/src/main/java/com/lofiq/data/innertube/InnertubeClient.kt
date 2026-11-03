package com.lofiq.data.innertube

import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.common.LofiQLogger
import com.lofiq.core.model.Song
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InnertubeClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
    private val authRepository: com.lofiq.auth.AuthRepository
) {
    private val endpointUrl = "https://music.youtube.com/youtubei/v1"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun getNextRadio(videoId: String, playlistId: String? = null): InnertubeNextResult = withContext(dispatchers.io) {
        val requestBodyJson = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", "1.20260320.01.00")
                    put("hl", "en")
                    put("gl", "US")
                }
            }
            put("videoId", videoId)
            if (!playlistId.isNullOrBlank()) {
                put("playlistId", playlistId)
                put("isAudioOnly", true)
            }
        }.toString()

        val authHeaders = authRepository.getAuthHeaders()
        val requestBuilder = Request.Builder()
            .url("$endpointUrl/next")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
            .header("Referer", "https://music.youtube.com/")
            .post(requestBodyJson.toRequestBody(jsonMediaType))

        authHeaders.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }

        val request = requestBuilder.build()

        try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext InnertubeNextResult(currentSongId = videoId)
            val body = response.body?.string() ?: return@withContext InnertubeNextResult(currentSongId = videoId)

            val root = json.parseToJsonElement(body).jsonObject
            val tabs = root["contents"]?.jsonObject
                ?.get("singleColumnMusicWatchNextResultsRenderer")?.jsonObject
                ?.get("tabbedRenderer")?.jsonObject
                ?.get("watchNextTabbedResultsRenderer")?.jsonObject
                ?.get("tabs")?.jsonArray

            val queueTab = tabs?.firstOrNull()?.jsonObject?.get("tabRenderer")?.jsonObject
            val tracks = queueTab?.get("content")?.jsonObject
                ?.get("musicQueueRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("playlistPanelRenderer")?.jsonObject
                ?.get("contents")?.jsonArray

            val upcoming = mutableListOf<Song>()
            if (tracks != null) {
                for (t in tracks) {
                    val renderer = t.jsonObject["playlistPanelVideoRenderer"]?.jsonObject ?: continue
                    val id = renderer["videoId"]?.jsonPrimitive?.content ?: continue
                    if (id == videoId) continue

                    val title = renderer["title"]?.jsonObject
                        ?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("text")?.jsonPrimitive?.content ?: "Unknown Track"

                    val bylineRuns = renderer["longBylineText"]?.jsonObject?.get("runs")?.jsonArray
                        ?: renderer["shortBylineText"]?.jsonObject?.get("runs")?.jsonArray
                    val artistTokens = mutableListOf<String>()
                    if (bylineRuns != null) {
                        for (run in bylineRuns) {
                            val text = run.jsonObject["text"]?.jsonPrimitive?.content ?: continue
                            if (text.contains("•") || text.contains("·") || text.contains("|")) {
                                val beforeBullet = text.substringBefore("•").substringBefore("·").substringBefore("|").trim()
                                if (beforeBullet.isNotBlank()) artistTokens.add(beforeBullet)
                                break
                            }
                            artistTokens.add(text)
                        }
                    }
                    val artist = if (artistTokens.isNotEmpty()) artistTokens.joinToString("").trim() else "Unknown Artist"

                    val thumbs = renderer["thumbnail"]?.jsonObject?.get("thumbnails")?.jsonArray
                    val artworkUrl = thumbs?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content ?: ""

                    upcoming.add(
                        Song(
                            id = id,
                            title = title,
                            artist = artist,
                            artworkUrl = artworkUrl,
                            highResArtworkUrl = artworkUrl.replace(Regex("""=w\d+-h\d+.*"""), "=w1200-h1200")
                        )
                    )
                }
            }

            InnertubeNextResult(
                currentSongId = videoId,
                upcomingSongs = upcoming
            )
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "Innertube next fetch failed: ${e.message}")
            InnertubeNextResult(currentSongId = videoId)
        }
    }
}

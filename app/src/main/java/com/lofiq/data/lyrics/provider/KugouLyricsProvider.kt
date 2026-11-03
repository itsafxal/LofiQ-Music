package com.lofiq.data.lyrics.provider

import android.util.Base64
import com.lofiq.data.lyrics.model.LyricsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KugouLyricsProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : LyricsProvider {

    override val name: String = "Kugou"

    override suspend fun getLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Long?
    ): LyricsResult? = withContext(Dispatchers.IO) {
        val keyword = "$artist - $title"
        val durationMs = if (durationSeconds != null && durationSeconds > 0) durationSeconds * 1000L else 0L

        val searchUrl = "http://krcs.kugou.com/search".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("ver", "1")
            ?.addQueryParameter("man", "yes")
            ?.addQueryParameter("client", "mobi")
            ?.addQueryParameter("keyword", keyword)
            ?.apply {
                if (durationMs > 0) addQueryParameter("duration", durationMs.toString())
            }
            ?.build() ?: return@withContext null

        val searchRequest = Request.Builder()
            .url(searchUrl)
            .header("User-Agent", "Mozilla/5.0")
            .get()
            .build()

        try {
            val response = okHttpClient.newCall(searchRequest).execute()
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string() ?: return@withContext null

            val jsonElement = json.parseToJsonElement(body).jsonObject
            val candidates = jsonElement["candidates"]?.jsonArray
            if (candidates.isNullOrEmpty()) return@withContext null

            val firstCandidate = candidates.firstOrNull()?.jsonObject ?: return@withContext null
            val id = firstCandidate["id"]?.jsonPrimitive?.content ?: return@withContext null
            val accessKey = firstCandidate["accesskey"]?.jsonPrimitive?.content ?: return@withContext null

            // Download lyrics content
            val downloadUrl = "http://krcs.kugou.com/download".toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("ver", "1")
                ?.addQueryParameter("client", "mobi")
                ?.addQueryParameter("id", id)
                ?.addQueryParameter("accesskey", accessKey)
                ?.addQueryParameter("fmt", "lrc")
                ?.addQueryParameter("charset", "utf8")
                ?.build() ?: return@withContext null

            val downloadRequest = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()

            val dlResponse = okHttpClient.newCall(downloadRequest).execute()
            if (!dlResponse.isSuccessful) return@withContext null
            val dlBody = dlResponse.body?.string() ?: return@withContext null

            val dlJson = json.parseToJsonElement(dlBody).jsonObject
            val base64Content = dlJson["content"]?.jsonPrimitive?.content ?: return@withContext null

            val decodedBytes = Base64.decode(base64Content, Base64.DEFAULT)
            val lrcText = String(decodedBytes, StandardCharsets.UTF_8)

            if (lrcText.isNotBlank()) {
                LyricsResult(
                    lrcText = lrcText,
                    plainText = null,
                    source = name,
                    isSynced = true
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }
}

package com.lofiq.data.lyrics.provider

import com.lofiq.data.lyrics.model.LyricsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LrcLibLyricsProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : LyricsProvider {

    override val name: String = "LRCLIB"

    override suspend fun getLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Long?
    ): LyricsResult? = withContext(Dispatchers.IO) {
        val cleanTitle = cleanTrackTitle(title)
        val cleanArtist = cleanArtistName(artist)

        // 1. Try exact get endpoint first
        val exactResult = tryExactGet(cleanTitle, cleanArtist, album, durationSeconds)
        if (exactResult != null) return@withContext exactResult

        // 2. Fallback to search endpoint
        return@withContext trySearch(cleanTitle, cleanArtist)
    }

    private fun tryExactGet(
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Long?
    ): LyricsResult? {
        val urlBuilder = "https://lrclib.net/api/get".toHttpUrlOrNull()?.newBuilder() ?: return null
        urlBuilder.addQueryParameter("track_name", title)
        urlBuilder.addQueryParameter("artist_name", artist)
        if (!album.isNullOrBlank()) {
            urlBuilder.addQueryParameter("album_name", album)
        }
        if (durationSeconds != null && durationSeconds > 0) {
            urlBuilder.addQueryParameter("duration", durationSeconds.toString())
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", "LofiQ/1.0.1 (https://github.com/itsafxal/LofiQ-Music)")
            .get()
            .build()

        return try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val bodyString = response.body?.string() ?: return null
            parseLyricsJson(bodyString)
        } catch (e: Exception) {
            null
        }
    }

    private fun trySearch(title: String, artist: String): LyricsResult? {
        val urlBuilder = "https://lrclib.net/api/search".toHttpUrlOrNull()?.newBuilder() ?: return null
        urlBuilder.addQueryParameter("track_name", title)
        urlBuilder.addQueryParameter("artist_name", artist)

        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", "LofiQ/1.0.1 (https://github.com/itsafxal/LofiQ-Music)")
            .get()
            .build()

        return try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val bodyString = response.body?.string() ?: return null
            val jsonElement = json.parseToJsonElement(bodyString)
            if (jsonElement is JsonArray && jsonElement.isNotEmpty()) {
                // Find first result with synced lyrics or plain lyrics
                for (item in jsonElement) {
                    if (item is JsonObject) {
                        val parsed = parseLyricsJsonObject(item)
                        if (parsed != null) return parsed
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun parseLyricsJson(jsonStr: String): LyricsResult? {
        return try {
            val element = json.parseToJsonElement(jsonStr)
            if (element is JsonObject) {
                parseLyricsJsonObject(element)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun parseLyricsJsonObject(obj: JsonObject): LyricsResult? {
        val instrumental = obj["instrumental"]?.jsonPrimitive?.booleanOrNull ?: false
        if (instrumental) {
            return LyricsResult(
                lrcText = "[00:00.00] ♪ Instrumental ♪",
                plainText = "♪ Instrumental ♪",
                source = name,
                isSynced = true
            )
        }

        val synced = obj["syncedLyrics"]?.jsonPrimitive?.content
        val plain = obj["plainLyrics"]?.jsonPrimitive?.content

        if (synced.isNullOrBlank() && plain.isNullOrBlank()) return null

        return LyricsResult(
            lrcText = synced?.takeIf { it.isNotBlank() },
            plainText = plain?.takeIf { it.isNotBlank() },
            source = name,
            isSynced = !synced.isNullOrBlank()
        )
    }

    private fun cleanTrackTitle(title: String): String {
        return title
            .replace(Regex("(?i)\\s*[\\[(](official\\s*(music\\s*)?video|official\\s*audio|lyric\\s*video|lyrics|audio|video|remaster(ed)?(\\s*\\d{4})?|radio\\s*edit|album\\s*version|single\\s*version)[\\])]"), "")
            .replace(Regex("(?i)\\s*[\\[(]from\\s*\"[^\"]+\"[\\])]"), "")
            .replace(Regex("(?i)\\s*[\\[(]feat\\.?\\s*[^)\\]]+[\\])]"), "")
            .replace(Regex("(?i)\\s*[\\[(]ft\\.?\\s*[^)\\]]+[\\])]"), "")
            .trim()
    }

    private fun cleanArtistName(artist: String): String {
        return artist
            .split(",", "&", "feat.", "ft.", "- Topic").firstOrNull()?.trim() ?: artist.trim()
    }
}

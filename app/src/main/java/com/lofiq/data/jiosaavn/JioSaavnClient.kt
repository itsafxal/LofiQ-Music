package com.lofiq.data.jiosaavn

import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.common.LofiQLogger
import com.lofiq.core.model.AudioQuality
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JioSaavnClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val dispatchers: DispatcherProvider
) {
    private val baseUrl = "https://www.jiosaavn.com/api.php"

    suspend fun searchSongs(query: String, limit: Int = 20): List<JioSaavnSong> = withContext(dispatchers.io) {
        val url = baseUrl.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("__call", "search.getResults")
            ?.addQueryParameter("q", query)
            ?.addQueryParameter("_format", "json")
            ?.addQueryParameter("_marker", "0")
            ?.addQueryParameter("api_version", "4")
            ?.addQueryParameter("ctx", "android")
            ?.addQueryParameter("n", limit.toString())
            ?.build() ?: return@withContext emptyList()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .get()
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string() ?: return@withContext emptyList()

            val root = json.parseToJsonElement(body).jsonObject
            val results = root["results"]?.jsonArray ?: return@withContext emptyList()

            val songs = mutableListOf<JioSaavnSong>()
            for (elem in results) {
                if (elem is JsonObject) {
                    val parsed = parseSongObject(elem)
                    if (parsed != null) songs.add(parsed)
                }
            }
            songs
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.RESOLVER, "JioSaavn search failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun getSongDetails(songId: String): JioSaavnSong? = withContext(dispatchers.io) {
        val url = baseUrl.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("__call", "song.getDetails")
            ?.addQueryParameter("pids", songId)
            ?.addQueryParameter("_format", "json")
            ?.addQueryParameter("_marker", "0")
            ?.addQueryParameter("ctx", "android")
            ?.addQueryParameter("api_version", "4")
            ?.build() ?: return@withContext null

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .get()
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string() ?: return@withContext null

            val root = json.parseToJsonElement(body).jsonObject
            val songObj = root[songId]?.jsonObject ?: root.values.firstOrNull()?.jsonObject
            songObj?.let { parseSongObject(it) }
        } catch (e: Exception) {
            null
        }
    }

    fun resolveStreamUrl(song: JioSaavnSong, quality: AudioQuality = AudioQuality.HIGH): String? {
        val effectiveQuality = if ((quality == AudioQuality.HIGH || quality == AudioQuality.AUTO) && !song.has320kbps) AudioQuality.STANDARD else quality
        if (song.encryptedMediaUrl.isNotBlank()) {
            val decrypted = JioSaavnCrypto.decryptMediaUrl(song.encryptedMediaUrl, effectiveQuality)
            if (!decrypted.isNullOrBlank()) return decrypted
        }
        if (song.mediaPreviewUrl.isNotBlank()) {
            val derived = JioSaavnCrypto.deriveStreamFromPreviewUrl(song.mediaPreviewUrl, effectiveQuality)
            if (!derived.isNullOrBlank()) return derived
        }
        return null
    }

    private fun parseSongObject(obj: JsonObject): JioSaavnSong? {
        val id = obj["id"]?.jsonPrimitive?.content ?: return null
        val title = obj["song"]?.jsonPrimitive?.content
            ?: obj["title"]?.jsonPrimitive?.content ?: return null

        val moreInfo = obj["more_info"]?.jsonObject
        val primaryArtists = obj["primary_artists"]?.jsonPrimitive?.contentOrNull
        val singers = moreInfo?.get("singers")?.jsonPrimitive?.contentOrNull
        val music = moreInfo?.get("music")?.jsonPrimitive?.contentOrNull
        val subtitle = obj["subtitle"]?.jsonPrimitive?.contentOrNull

        val artistList = listOfNotNull(primaryArtists, singers, music, subtitle)
            .flatMap { it.split(",", "&", "/", ";") }
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) }
            .distinct()

        val artist = if (artistList.isNotEmpty()) {
            artistList.joinToString(", ")
        } else {
            primaryArtists ?: singers ?: music ?: subtitle ?: "Unknown Artist"
        }

        val album = obj["album"]?.jsonPrimitive?.content
            ?: moreInfo?.get("album")?.jsonPrimitive?.content ?: ""

        val year = obj["year"]?.jsonPrimitive?.content ?: ""
        val durationStr = obj["duration"]?.jsonPrimitive?.content
            ?: moreInfo?.get("duration")?.jsonPrimitive?.content ?: "0"
        val duration = durationStr.toLongOrNull() ?: 0L

        val rawImage = obj["image"]?.jsonPrimitive?.content ?: ""
        val highResImage = com.lofiq.core.common.ArtworkUtils.getHighResArtworkUrl(rawImage)

        val encryptedMediaUrl = moreInfo?.get("encrypted_media_url")?.jsonPrimitive?.content ?: ""
        val mediaPreviewUrl = moreInfo?.get("media_preview_url")?.jsonPrimitive?.content
            ?: obj["media_preview_url"]?.jsonPrimitive?.content ?: ""
        val has320 = moreInfo?.get("320kbps")?.jsonPrimitive?.content == "true"
        val language = obj["language"]?.jsonPrimitive?.contentOrNull
            ?: moreInfo?.get("language")?.jsonPrimitive?.contentOrNull ?: ""
        val label = moreInfo?.get("label")?.jsonPrimitive?.contentOrNull
            ?: moreInfo?.get("copyright_text")?.jsonPrimitive?.contentOrNull
            ?: obj["label"]?.jsonPrimitive?.contentOrNull ?: ""

        return JioSaavnSong(
            id = id,
            title = cleanHtmlEntities(title),
            artist = cleanHtmlEntities(artist),
            album = cleanHtmlEntities(album),
            year = year,
            durationSeconds = duration,
            artworkUrl = highResImage.ifBlank { rawImage },
            highResArtworkUrl = highResImage.ifBlank { rawImage },
            encryptedMediaUrl = encryptedMediaUrl,
            mediaPreviewUrl = mediaPreviewUrl,
            has320kbps = has320,
            language = cleanHtmlEntities(language),
            label = cleanHtmlEntities(label)
        )
    }

    suspend fun getAutocomplete(query: String): Pair<List<com.lofiq.core.model.Song>, List<String>> = withContext(dispatchers.io) {
        if (query.isBlank()) return@withContext Pair(emptyList(), emptyList())
        val url = baseUrl.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("__call", "autocomplete.get")
            ?.addQueryParameter("query", query.trim())
            ?.addQueryParameter("_format", "json")
            ?.addQueryParameter("_marker", "0")
            ?.addQueryParameter("ctx", "android")
            ?.addQueryParameter("api_version", "4")
            ?.build() ?: return@withContext Pair(emptyList(), emptyList())

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .get()
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext Pair(emptyList(), emptyList())
            val body = response.body?.string() ?: return@withContext Pair(emptyList(), emptyList())

            val root = json.parseToJsonElement(body).jsonObject
            val songs = mutableListOf<com.lofiq.core.model.Song>()
            val suggestions = mutableListOf<String>()

            // 1. Process topquery
            val topQueryObj = root["topquery"]?.jsonObject
            val topQueryData = topQueryObj?.get("data")?.jsonArray
            if (topQueryData != null) {
                for (elem in topQueryData) {
                    if (elem is JsonObject) {
                        val rawTitle = elem["title"]?.jsonPrimitive?.contentOrNull ?: continue
                        val cleanTitle = cleanHtmlEntities(rawTitle.replace(Regex("""\s*-\s*\?+.*"""), "").replace(Regex("""\?+"""), "").trim())
                        if (cleanTitle.isNotBlank()) {
                            suggestions.add(cleanTitle)
                        }
                    }
                }
            }

            // 2. Process songs
            val songsObj = root["songs"]?.jsonObject
            val songsData = songsObj?.get("data")?.jsonArray
            if (songsData != null) {
                for (elem in songsData) {
                    if (elem is JsonObject) {
                        val id = elem["id"]?.jsonPrimitive?.contentOrNull ?: continue
                        val rawTitle = elem["title"]?.jsonPrimitive?.contentOrNull ?: continue
                        val cleanTitle = cleanHtmlEntities(rawTitle.replace(Regex("""\s*-\s*\?+.*"""), "").replace(Regex("""\?+"""), "").trim())
                        if (cleanTitle.isBlank()) continue

                        val moreInfo = elem["more_info"]?.jsonObject
                        val rawArtists = moreInfo?.get("primary_artists")?.jsonPrimitive?.contentOrNull
                            ?: elem["description"]?.jsonPrimitive?.contentOrNull
                            ?: "Unknown Artist"
                        val cleanArtist = cleanHtmlEntities(
                            rawArtists.replace(Regex("""^.*?[-—–]\s*"""), "")
                                .replace(Regex("""\?+"""), "")
                                .trim()
                        ).ifBlank { "Unknown Artist" }

                        val rawImage = elem["image"]?.jsonPrimitive?.contentOrNull ?: ""
                        val highRes = com.lofiq.core.common.ArtworkUtils.getHighResArtworkUrl(rawImage)
                        val rawAlbum = elem["album"]?.jsonPrimitive?.contentOrNull ?: ""
                        val cleanAlbum = cleanHtmlEntities(rawAlbum.replace(Regex("""\s*-\s*\?+.*"""), "").trim())

                        songs.add(
                            com.lofiq.core.model.Song(
                                id = id,
                                title = cleanTitle,
                                artist = cleanArtist,
                                album = cleanAlbum,
                                durationSeconds = 0L,
                                artworkUrl = highRes.ifBlank { rawImage },
                                highResArtworkUrl = highRes.ifBlank { rawImage },
                                providerId = "jiosaavn",
                                quality = com.lofiq.core.model.AudioQuality.HIGH
                            )
                        )
                        suggestions.add(cleanTitle)
                    }
                }
            }

            Pair(songs.distinctBy { it.id }, suggestions.distinct().take(10))
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.RESOLVER, "JioSaavn autocomplete failed: ${e.message}")
            Pair(emptyList(), emptyList())
        }
    }

    private fun cleanHtmlEntities(text: String): String {
        return text
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }
}

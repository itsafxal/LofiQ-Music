package com.lofiq.resolver.providers.youtube

import com.lofiq.auth.AuthRepository
import com.lofiq.auth.AuthCryptoUtils
import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.common.LofiQLogger
import com.lofiq.core.model.AudioQuality
import com.lofiq.core.model.Song
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

data class ParsedPlaylist(
    val id: String,
    val title: String,
    val description: String = "",
    val artworkUrl: String = "",
    val songCount: Int = 0
)

@Singleton
class YouTubeMetadataProvider @Inject constructor(
    private val httpClient: OkHttpClient,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
    private val authRepository: AuthRepository? = null
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val mirrorManager: PipedMirrorManager by lazy {
        PipedMirrorManager(httpClient, json, dispatchers)
    }

    private fun getClientCountry(): String = java.util.Locale.getDefault().country.takeIf { it.isNotBlank() } ?: "IN"
    private fun getClientLanguage(): String = java.util.Locale.getDefault().language.takeIf { it.isNotBlank() } ?: "en"

    suspend fun searchSongs(query: String, limit: Int = 25): List<Song> = withContext(dispatchers.io) {
        if (query.isBlank()) return@withContext emptyList()
        LofiQLogger.i(LofiQLogger.Category.SEARCH, "Searching YouTube Music for: '$query'")

        // 1. Try YouTube Music InnerTube Search API
        try {
            val innerTubeResults = searchInnerTube(query, limit).filter { isValidSong(it) }
            if (innerTubeResults.isNotEmpty()) {
                LofiQLogger.d(LofiQLogger.Category.SEARCH, "InnerTube returned ${innerTubeResults.size} songs")
                return@withContext innerTubeResults
            }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.SEARCH, "InnerTube search failed: ${e.message}")
        }

        // 2. Try Piped YouTube Music Search fallback (fast bounded mirrors with health tracking)
        val healthyMirrors = mirrorManager.getHealthySearchMirrors()
        for (instance in healthyMirrors) {
            val searchStart = System.currentTimeMillis()
            try {
                val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
                val url = "$instance/search?q=$encoded&filter=music_songs"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "LofiQ/1.0")
                    .build()

                val response = mirrorManager.fastMirrorClient.newCall(request).execute()
                val latency = System.currentTimeMillis() - searchStart
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: continue
                    val songs = parsePipedSearchJson(body).filter { isValidSong(it) }
                    if (songs.isNotEmpty()) {
                        mirrorManager.recordSuccess(instance, latency, MirrorCapability.SEARCH)
                        LofiQLogger.d(LofiQLogger.Category.SEARCH, "Piped instance $instance returned ${songs.size} songs in ${latency}ms")
                        return@withContext songs.take(limit)
                    } else {
                        mirrorManager.recordFailure(instance, "Empty search results", MirrorCapability.SEARCH)
                    }
                } else {
                    mirrorManager.recordFailure(instance, "HTTP ${response.code}", MirrorCapability.SEARCH)
                }
            } catch (e: Exception) {
                mirrorManager.recordFailure(instance, e.message, MirrorCapability.SEARCH)
                LofiQLogger.w(LofiQLogger.Category.SEARCH, "Piped search failed on $instance: ${e.message}")
            }
        }

        // 3. Fallback: YouTube standard public search endpoint
        try {
            val publicResults = searchPublicHtml(query, limit).filter { isValidSong(it) }
            if (publicResults.isNotEmpty()) {
                return@withContext publicResults
            }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.SEARCH, "Public search fallback failed: ${e.message}")
        }

        emptyList()
    }

    suspend fun getSearchSuggestions(query: String): List<String> = withContext(dispatchers.io) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.toString())
            val url = "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext emptyList()
                val rootArray = json.parseToJsonElement(body).jsonArray
                if (rootArray.size >= 2) {
                    val suggestions = rootArray[1].jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull }
                    return@withContext suggestions.take(10)
                }
            }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.SEARCH, "Failed to get YouTube search suggestions: ${e.message}")
        }
        emptyList()
    }

    suspend fun getTrendingSongs(limit: Int = 20): List<Song> = withContext(dispatchers.io) {
        val trendingQueries = listOf(
            "Top Hits Music 2026",
            "Trending Music Global",
            "Billboard Hot 100",
            "Popular Music Global Hits",
            "Top Songs This Week"
        )
        for (query in trendingQueries.shuffled()) {
            val fetched = searchSongs(query, limit = limit)
            val validSongs = fetched.filter { isValidSong(it) }
            if (validSongs.isNotEmpty()) {
                return@withContext validSongs.distinctBy { it.id }.take(limit)
            }
        }
        emptyList()
    }

    /**
     * Fetch radio recommendations based on seed video ID via InnerTube /next
     */
    suspend fun fetchRadioSongs(videoId: String, limit: Int = 25): List<Song> = withContext(dispatchers.io) {
        if (!isValidVideoId(videoId)) return@withContext emptyList()
        try {
            val url = "https://music.youtube.com/youtubei/v1/next?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8&prettyPrint=false"
            val payload = buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20251013.03.00")
                        put("hl", getClientLanguage())
                        put("gl", getClientCountry())
                    }
                }
                put("videoId", videoId)
                put("playlistId", "RDAMVM$videoId")
                put("isAudioOnly", true)
            }

            val reqBuilder = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .header("Referer", "https://music.youtube.com/")
                .header("Origin", "https://music.youtube.com")
                .header("X-Origin", "https://music.youtube.com")
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", "1.20251013.03.00")

            val authHeaders = authRepository?.getCachedAuthHeaders() ?: emptyMap()
            authHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }

            val response = httpClient.newCall(reqBuilder.build()).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body?.string() ?: return@withContext emptyList()
            val root = json.parseToJsonElement(body)
            val songs = parseRadioPanelVideos(root)
            if (songs.isNotEmpty()) {
                return@withContext songs.filter { it.id != videoId }.take(limit)
            }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "fetchRadioSongs failed for $videoId: ${e.message}")
        }
        emptyList()
    }

    /**
     * Fetch user's Liked Music songs via YouTube Music Innertube browse endpoint with full continuation pagination
     */
    suspend fun fetchBrowseLikedSongs(cookies: String, limit: Int = 1000): List<Song> = withContext(dispatchers.io) {
        val browseIds = listOf("VLLM", "FEmusic_liked_videos", "LM", "VLLL", "LL", "VLSE", "FEmusic_liked_tracks", "FEmusic_liked", "SE", "FEmusic_library_landing")
        for (browseId in browseIds) {
            try {
                val songs = fetchBrowseSongs(browseId = browseId, cookies = cookies, maxPages = 50)
                if (songs.isNotEmpty()) {
                    LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "Fetched ${songs.size} liked songs from browseId: $browseId")
                    return@withContext songs.take(limit)
                }
            } catch (e: Exception) {
                LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "Browse liked failed for $browseId: ${e.message}")
            }
        }
        emptyList()
    }

    /**
     * Fetch user's Library Playlists via YouTube Music Innertube browse endpoint with continuation pagination
     */
    suspend fun fetchBrowseUserPlaylists(cookies: String): List<ParsedPlaylist> = withContext(dispatchers.io) {
        val browseIds = listOf("FEmusic_liked_playlists", "FEmusic_library_playlists", "FEmusic_library_landing", "FEmusic_library_privately_owned_playlists")
        val playlists = mutableListOf<ParsedPlaylist>()

        for (browseId in browseIds) {
            try {
                var currentToken: String? = null
                var page = 0
                val maxPages = 20

                val firstBody = executeBrowseRequest(browseId, cookies)
                if (firstBody.isNotBlank()) {
                    val root = json.parseToJsonElement(firstBody)
                    val parsed = parseTwoRowPlaylists(root)
                    if (parsed.isNotEmpty()) {
                        playlists.addAll(parsed)
                    } else if (firstBody.length < 5000) {
                        LofiQLogger.d(LofiQLogger.Category.PLAYBACK, "[PLAYLIST][YOUTUBE] 0 playlists parsed, snippet ($browseId): ${firstBody.take(600)}")
                    }
                    currentToken = extractContinuationToken(root)
                }

                while (!currentToken.isNullOrBlank() && page < maxPages) {
                    page++
                    val contBody = executeContinuationRequest(currentToken, cookies)
                    if (contBody.isBlank()) break
                    val root = json.parseToJsonElement(contBody)
                    val parsed = parseTwoRowPlaylists(root)
                    if (parsed.isNotEmpty()) {
                        playlists.addAll(parsed)
                    }
                    val nextToken = extractContinuationToken(root)
                    if (nextToken == currentToken) break
                    currentToken = nextToken
                }
            } catch (e: Exception) {
                LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "Failed fetching playlists for $browseId: ${e.message}")
            }
        }

        playlists.distinctBy { it.id }
    }

    /**
     * Fetch tracks inside a specific playlist with full continuation pagination
     */
    suspend fun fetchBrowsePlaylistSongs(playlistId: String, cookies: String? = null): List<Song> = withContext(dispatchers.io) {
        val targetBrowseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
        fetchBrowseSongs(browseId = targetBrowseId, cookies = cookies, maxPages = 50)
    }

    /**
     * Extracts YouTube Playlist ID from various URL formats
     */
    fun extractYouTubePlaylistId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.startsWith("PL") || trimmed.startsWith("RD") || trimmed.startsWith("OLAK5uy_") || trimmed.startsWith("VLPL")) {
            return trimmed.removePrefix("VL")
        }
        val uri = try { android.net.Uri.parse(if (trimmed.startsWith("http")) trimmed else "https://$trimmed") } catch (_: Exception) { null }
        val listParam = uri?.getQueryParameter("list")
        if (!listParam.isNullOrBlank()) return listParam.removePrefix("VL")

        val pattern = """[?&]list=([a-zA-Z0-9_-]+)""".toRegex()
        val match = pattern.find(trimmed)
        if (match != null) return match.groupValues[1].removePrefix("VL")

        if (trimmed.contains("/playlist/")) {
            val after = trimmed.substringAfter("/playlist/").substringBefore("?").substringBefore("&").substringBefore("#").trim()
            if (after.isNotBlank()) return after.removePrefix("VL")
        }
        if (trimmed.contains("/browse/")) {
            val after = trimmed.substringAfter("/browse/").substringBefore("?").substringBefore("&").substringBefore("#").trim()
            if (after.isNotBlank()) return after.removePrefix("VL")
        }
        return null
    }

    /**
     * Fetches complete YouTube / YouTube Music playlist details (Title + all tracks)
     */
    suspend fun fetchYouTubePlaylistDetails(playlistUrlOrId: String, cookies: String? = null): Pair<String, List<Song>>? = withContext(dispatchers.io) {
        val playlistId = extractYouTubePlaylistId(playlistUrlOrId) ?: playlistUrlOrId.trim().removePrefix("VL")
        if (playlistId.isBlank()) return@withContext null

        val targetBrowseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
        val songs = mutableListOf<Song>()
        var playlistTitle = "YouTube Playlist"

        try {
            val firstBody = executeBrowseRequest(targetBrowseId, cookies)
            if (firstBody.isNotBlank()) {
                val root = json.parseToJsonElement(firstBody)
                val headerTitle = extractHeaderTitle(root)
                if (!headerTitle.isNullOrBlank()) {
                    playlistTitle = headerTitle
                }

                val initialSongs = parseResponsiveListItemsToSongs(root).ifEmpty { parseRadioPanelVideos(root) }
                songs.addAll(initialSongs)

                var currentToken = extractContinuationToken(root)
                var page = 0
                val maxPages = 50

                while (!currentToken.isNullOrBlank() && page < maxPages) {
                    page++
                    val contBody = executeContinuationRequest(currentToken, cookies)
                    if (contBody.isBlank()) break
                    val contRoot = json.parseToJsonElement(contBody)
                    val newSongs = parseResponsiveListItemsToSongs(contRoot).ifEmpty { parseRadioPanelVideos(contRoot) }
                    if (newSongs.isEmpty()) break
                    songs.addAll(newSongs)
                    val nextToken = extractContinuationToken(contRoot)
                    if (nextToken == currentToken) break
                    currentToken = nextToken
                }
            }

            // Fallback to standard web client if empty
            if (songs.isEmpty()) {
                val webClientSongs = fetchBrowseSongsViaWebClient(targetBrowseId)
                if (webClientSongs.isNotEmpty()) {
                    songs.addAll(webClientSongs)
                }
            }

            if (songs.isNotEmpty()) {
                return@withContext playlistTitle to songs.distinctBy { it.id }
            }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "fetchYouTubePlaylistDetails error for $playlistId: ${e.message}")
        }
        null
    }

    private fun extractHeaderTitle(root: JsonElement): String? {
        var foundTitle: String? = null
        fun traverse(el: JsonElement) {
            if (foundTitle != null) return
            when (el) {
                is JsonObject -> {
                    val headerKeys = listOf(
                        "musicDetailHeaderRenderer",
                        "musicResponsiveHeaderRenderer",
                        "musicVisualHeaderRenderer",
                        "musicHeaderRenderer",
                        "editablePlaylistHeaderRenderer"
                    )
                    for (hk in headerKeys) {
                        if (el.containsKey(hk)) {
                            val header = el[hk]?.jsonObject
                            val titleObj = header?.get("title")?.jsonObject
                            val runs = titleObj?.get("runs")?.jsonArray
                            val text = runs?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                                ?: titleObj?.get("simpleText")?.jsonPrimitive?.contentOrNull
                                ?: header?.get("title")?.jsonPrimitive?.contentOrNull
                            if (!text.isNullOrBlank()) {
                                foundTitle = decodeHtml(text)
                                return
                            }
                        }
                    }
                    for ((_, v) in el) {
                        traverse(v)
                    }
                }
                is JsonArray -> {
                    for (item in el) traverse(item)
                }
                else -> {}
            }
        }
        traverse(root)
        return foundTitle
    }

    fun extractContinuationToken(element: JsonElement): String? {
        var token: String? = null
        fun traverse(el: JsonElement) {
            if (token != null) return
            when (el) {
                is JsonObject -> {
                    if (el.containsKey("continuationCommand")) {
                        val cmd = el["continuationCommand"]?.jsonObject
                        val tok = cmd?.get("token")?.jsonPrimitive?.contentOrNull
                        if (!tok.isNullOrBlank()) {
                            token = tok
                            return
                        }
                    }
                    if (el.containsKey("nextContinuationData")) {
                        val data = el["nextContinuationData"]?.jsonObject
                        val tok = data?.get("continuation")?.jsonPrimitive?.contentOrNull
                        if (!tok.isNullOrBlank()) {
                            token = tok
                            return
                        }
                    }
                    if (el.containsKey("continuation")) {
                        val cont = el["continuation"]
                        if (cont is JsonPrimitive && cont.isString && cont.content.isNotBlank()) {
                            token = cont.content
                            return
                        }
                    }
                    for ((_, v) in el) {
                        traverse(v)
                    }
                }
                is JsonArray -> {
                    for (item in el) traverse(item)
                }
                else -> {}
            }
        }
        traverse(element)
        return token
    }

    private fun executeBrowseRequest(browseId: String, cookies: String? = null): String {
        val payload = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", "1.20261011.16.00")
                    put("hl", getClientLanguage())
                    put("gl", getClientCountry())
                }
            }
            put("browseId", browseId)
        }

        val requestBuilder = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/browse?key=AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30&prettyPrint=false")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
            .header("Referer", "https://music.youtube.com/")
            .header("Origin", "https://music.youtube.com")
            .header("X-Origin", "https://music.youtube.com")
            .header("X-YouTube-Client-Name", "67")
            .header("X-YouTube-Client-Version", "1.20261011.16.00")
            .header("X-Goog-AuthUser", "0")

        if (!cookies.isNullOrBlank()) {
            requestBuilder.header("Cookie", cookies)
            val sapisid = AuthCryptoUtils.extractSapisid(cookies)
            if (!sapisid.isNullOrBlank()) {
                val authHash = AuthCryptoUtils.generateSapisidHash(sapisid, "https://music.youtube.com")
                requestBuilder.header("Authorization", authHash)
            }
        }

        return try {
            val response = httpClient.newCall(requestBuilder.build()).execute()
            val body = if (response.isSuccessful) response.body?.string().orEmpty() else ""
            LofiQLogger.d(LofiQLogger.Category.PLAYBACK, "executeBrowseRequest browseId=$browseId code=${response.code} bodyLen=${body.length}")
            body
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "executeBrowseRequest failed for $browseId: ${e.message}")
            ""
        }
    }

    private fun executeContinuationRequest(continuationToken: String, cookies: String? = null): String {
        val payload = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", "1.20261011.16.00")
                    put("hl", getClientLanguage())
                    put("gl", getClientCountry())
                }
            }
            put("continuation", continuationToken)
        }

        val requestBuilder = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/browse?continuation=$continuationToken&type=next&key=AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30&prettyPrint=false")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
            .header("Referer", "https://music.youtube.com/")
            .header("Origin", "https://music.youtube.com")
            .header("X-Origin", "https://music.youtube.com")
            .header("X-YouTube-Client-Name", "67")
            .header("X-YouTube-Client-Version", "1.20261011.16.00")
            .header("X-Goog-AuthUser", "0")

        if (!cookies.isNullOrBlank()) {
            requestBuilder.header("Cookie", cookies)
            val sapisid = AuthCryptoUtils.extractSapisid(cookies)
            if (!sapisid.isNullOrBlank()) {
                val authHash = AuthCryptoUtils.generateSapisidHash(sapisid, "https://music.youtube.com")
                requestBuilder.header("Authorization", authHash)
            }
        }

        return try {
            val response = httpClient.newCall(requestBuilder.build()).execute()
            if (response.isSuccessful) response.body?.string().orEmpty() else ""
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "executeContinuationRequest failed: ${e.message}")
            ""
        }
    }

    private fun executeWebClientBrowseRequest(browseId: String, cookies: String? = null): String {
        return try {
            val payload = buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", "WEB")
                        put("clientVersion", "2.20251013.03.00")
                        put("hl", getClientLanguage())
                        put("gl", getClientCountry())
                    }
                }
                put("browseId", browseId)
            }
            val req = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/browse?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8&prettyPrint=false")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .header("Referer", "https://www.youtube.com/")
                .header("Origin", "https://www.youtube.com")
                .header("X-Origin", "https://www.youtube.com")
                .header("X-YouTube-Client-Name", "1")
                .header("X-YouTube-Client-Version", "2.20251013.03.00")

            if (!cookies.isNullOrBlank()) {
                req.header("Cookie", cookies)
                val sapisid = AuthCryptoUtils.extractSapisid(cookies)
                if (!sapisid.isNullOrBlank()) {
                    val authHash = AuthCryptoUtils.generateSapisidHash(sapisid, "https://www.youtube.com")
                    req.header("Authorization", authHash)
                }
            }

            val resp = httpClient.newCall(req.build()).execute()
            if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "executeWebClientBrowseRequest error: ${e.message}")
            ""
        }
    }

    private fun fetchBrowseSongsViaWebClient(browseId: String, cookies: String? = null): List<Song> {
        val body = executeWebClientBrowseRequest(browseId, cookies)
        return if (body.isNotBlank()) {
            try {
                val root = json.parseToJsonElement(body)
                parseResponsiveListItemsToSongs(root).ifEmpty { parseRadioPanelVideos(root) }
            } catch (_: Exception) {
                emptyList()
            }
        } else emptyList()
    }

    suspend fun fetchBrowseSongs(browseId: String, cookies: String? = null, maxPages: Int = 30): List<Song> = withContext(dispatchers.io) {
        val songs = mutableListOf<Song>()
        val seenTokens = mutableSetOf<String>()
        var continuationToken: String? = null
        var page = 0

        val firstBody = executeBrowseRequest(browseId, cookies)
        if (firstBody.isNotBlank()) {
            val root = json.parseToJsonElement(firstBody)
            val initialSongs = parseResponsiveListItemsToSongs(root).ifEmpty { parseRadioPanelVideos(root) }
            songs.addAll(initialSongs)
            continuationToken = extractContinuationToken(root)
            LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[LIBRARY][YOUTUBE] Browse initial items=${initialSongs.size} browseId=$browseId continuation=${!continuationToken.isNullOrBlank()}")
            if (initialSongs.isEmpty() && firstBody.length < 5000) {
                LofiQLogger.d(LofiQLogger.Category.PLAYBACK, "[LIBRARY][YOUTUBE] Browse body snippet ($browseId): ${firstBody.take(600)}")
            }
        }

        if (songs.isEmpty() && !cookies.isNullOrBlank()) {
            val webFallback = fetchBrowseSongsViaWebClient(browseId, cookies)
            if (webFallback.isNotEmpty()) {
                songs.addAll(webFallback)
                LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[LIBRARY][YOUTUBE] Web client fallback items=${webFallback.size} browseId=$browseId")
            }
        }

        while (!continuationToken.isNullOrBlank() && page < maxPages && seenTokens.add(continuationToken)) {
            page++
            val contBody = executeContinuationRequest(continuationToken, cookies)
            if (contBody.isBlank()) break
            val root = json.parseToJsonElement(contBody)
            val newSongs = parseResponsiveListItemsToSongs(root).ifEmpty { parseRadioPanelVideos(root) }
            if (newSongs.isEmpty()) break
            songs.addAll(newSongs)
            val nextToken = extractContinuationToken(root)
            LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[LIBRARY][YOUTUBE] Page $page loaded items=${newSongs.size} total=${songs.size} continuation=${!nextToken.isNullOrBlank()}")
            if (nextToken == continuationToken || (nextToken != null && seenTokens.contains(nextToken))) break
            continuationToken = nextToken
        }

        return@withContext songs.distinctBy { it.id }
    }

    fun parseResponsiveListItemsToSongs(root: JsonElement): List<Song> {
        val itemObjects = extractResponsiveListItems(root)
        val songs = mutableListOf<Song>()

        for (item in itemObjects) {
            // 1. Find Video ID
            val videoId = findVideoIdInItem(item) ?: continue

            // 2. Find Title
            val flexColumns = item["flexColumns"]?.jsonArray
            val titleObj = flexColumns?.getOrNull(0)?.jsonObject
                ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                ?.get("text")?.jsonObject
            val title = titleObj?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull
                ?: titleObj?.get("simpleText")?.jsonPrimitive?.contentOrNull
                ?: item["title"]?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                ?: item["title"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.contentOrNull
                ?: item["title"]?.jsonPrimitive?.contentOrNull
                ?: "Unknown Title"

            // 3. Find Artist & Album & Duration
            var artist = "YouTube Music Artist"
            var album = ""
            var durationSec = 0L

            if (flexColumns != null && flexColumns.size >= 2) {
                val subtitleCol = flexColumns.getOrNull(1)?.jsonObject
                    ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                    ?.get("text")?.jsonObject
                    ?.get("runs")?.jsonArray

                val (parsedArtist, parsedAlbum, parsedDuration) = extractMetadataFromRuns(subtitleCol, durationSec)
                artist = parsedArtist
                album = parsedAlbum
                if (parsedDuration > 0L) durationSec = parsedDuration

                // If album is still empty and 3rd column exists, check 3rd column
                if (album.isBlank() && flexColumns.size >= 3) {
                    val albumCol = flexColumns.getOrNull(2)?.jsonObject
                        ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                        ?.get("text")?.jsonObject?.get("runs")?.jsonArray
                    val (altArtist, altAlbum, _) = extractMetadataFromRuns(albumCol, 0L)
                    if (altArtist.isNotBlank() && altArtist != "YouTube Music Artist") {
                        album = altArtist
                    } else if (altAlbum.isNotBlank()) {
                        album = altAlbum
                    }
                }
            } else {
                // Fallback for playlistVideoRenderer / standard items
                val bylineRuns = item["shortBylineText"]?.jsonObject?.get("runs")?.jsonArray
                    ?: item["longBylineText"]?.jsonObject?.get("runs")?.jsonArray
                val (parsedArtist, parsedAlbum, parsedDuration) = extractMetadataFromRuns(bylineRuns, durationSec)
                artist = parsedArtist
                album = parsedAlbum
                if (parsedDuration > 0L) durationSec = parsedDuration

                val lenSec = item["lengthSeconds"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                if (lenSec != null && lenSec > 0) durationSec = lenSec
            }

            // Fixed columns duration check
            if (durationSec == 0L) {
                val fixedCols = item["fixedColumns"]?.jsonArray
                val fixedDur = fixedCols?.firstOrNull()?.jsonObject
                    ?.get("musicResponsiveListItemFixedColumnRenderer")?.jsonObject
                    ?.get("text")?.jsonObject
                    ?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("text")?.jsonPrimitive?.contentOrNull
                    ?: fixedCols?.firstOrNull()?.jsonObject
                        ?.get("musicResponsiveListItemFixedColumnRenderer")?.jsonObject
                        ?.get("text")?.jsonObject
                        ?.get("simpleText")?.jsonPrimitive?.contentOrNull

                if (!fixedDur.isNullOrBlank() && fixedDur.contains(":")) {
                    durationSec = parseDurationStringToSeconds(fixedDur)
                }
            }

            // Artwork
            val thumbs = item["thumbnail"]?.jsonObject
                ?.get("musicThumbnailRenderer")?.jsonObject
                ?.get("thumbnail")?.jsonObject
                ?.get("thumbnails")?.jsonArray
                ?: item["thumbnailRenderer"]?.jsonObject?.get("musicThumbnailRenderer")?.jsonObject?.get("thumbnail")?.jsonObject?.get("thumbnails")?.jsonArray
                ?: item["thumbnail"]?.jsonObject?.get("thumbnails")?.jsonArray
                ?: item["thumbnails"]?.jsonArray
            val rawArtwork = thumbs?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

            val artworkUrl = if (rawArtwork.startsWith("//")) "https:$rawArtwork" else rawArtwork
            val highRes = com.lofiq.core.common.ArtworkUtils.getHighResArtworkUrl(artworkUrl, videoId)

            songs.add(
                Song(
                    id = videoId,
                    title = decodeHtml(title),
                    artist = decodeHtml(artist),
                    album = decodeHtml(album),
                    durationSeconds = durationSec,
                    artworkUrl = highRes.ifBlank { artworkUrl },
                    highResArtworkUrl = highRes.ifBlank { artworkUrl },
                    providerId = "youtube",
                    quality = AudioQuality.HIGH
                )
            )
        }
        return songs.distinctBy { it.id }
    }

    private fun extractMetadataFromRuns(
        runs: JsonArray?,
        existingDuration: Long = 0L
    ): Triple<String, String, Long> {
        if (runs == null || runs.isEmpty()) {
            return Triple("YouTube Music Artist", "", existingDuration)
        }

        val rawTokens = mutableListOf<String>()
        for (run in runs) {
            val text = run.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: continue
            val parts = text.split(Regex("""[•·\u2022\u00B7\u2219|]"""))
            for (i in parts.indices) {
                val part = parts[i]
                if (part.isNotBlank()) rawTokens.add(part.trim())
                if (i < parts.size - 1) rawTokens.add("•")
            }
        }

        val sections = mutableListOf<String>()
        val currentSectionTokens = mutableListOf<String>()
        for (token in rawTokens) {
            if (token == "•") {
                if (currentSectionTokens.isNotEmpty()) {
                    sections.add(currentSectionTokens.joinToString(" ").trim())
                    currentSectionTokens.clear()
                }
            } else {
                currentSectionTokens.add(token)
            }
        }
        if (currentSectionTokens.isNotEmpty()) {
            sections.add(currentSectionTokens.joinToString(" ").trim())
        }

        var artist = "YouTube Music Artist"
        var album = ""
        var durationSec = existingDuration
        var foundArtist = false
        var foundAlbum = false

        for (sec in sections) {
            var cleanSec = sec.trim()
            if (cleanSec.isBlank()) continue

            // Check if section is standalone duration (e.g. "3:41", "05:22", "1:02:15")
            if (cleanSec.matches(Regex("""^\d{1,2}:\d{2}(:\d{2})?$"""))) {
                if (durationSec == 0L) {
                    durationSec = parseDurationStringToSeconds(cleanSec)
                }
                continue
            }

            // Extract any fused trailing duration like "Artist Name3:45" or "Album 4:20"
            val durMatch = Regex("""(?:\s*|\b)(\d{1,2}:\d{2}(?::\d{2})?)$""").find(cleanSec)
            if (durMatch != null) {
                if (durationSec == 0L) {
                    durationSec = parseDurationStringToSeconds(durMatch.groupValues[1])
                }
                cleanSec = cleanSec.substring(0, durMatch.range.first).trim()
            }

            // Strip trailing/leading punctuation
            cleanSec = cleanSec.replace(Regex("""^[•·\-|,\s]+|[•·\-|,\s]+$"""), "").trim()
            if (cleanSec.isBlank()) continue

            // Skip non-metadata descriptor tags
            if (cleanSec.equals("Song", ignoreCase = true) ||
                cleanSec.equals("Video", ignoreCase = true) ||
                cleanSec.equals("Single", ignoreCase = true) ||
                cleanSec.equals("Album", ignoreCase = true) ||
                cleanSec.equals("EP", ignoreCase = true) ||
                cleanSec.equals("Explicit", ignoreCase = true) ||
                cleanSec.equals("E", ignoreCase = true) ||
                cleanSec.matches(Regex("""(?i)^\d+([.,]\d+)?\s*(K|M|B)?\s*(views|plays)$"""))
            ) {
                continue
            }

            if (!foundArtist) {
                artist = cleanSec
                foundArtist = true
            } else if (!foundAlbum) {
                album = cleanSec
                foundAlbum = true
            }
        }

        return Triple(artist, album, durationSec)
    }

    fun parseRadioPanelVideos(root: JsonElement): List<Song> {
        val songs = mutableListOf<Song>()
        fun traverse(el: JsonElement) {
            when (el) {
                is JsonObject -> {
                    if (el.containsKey("playlistPanelVideoRenderer")) {
                        val item = el["playlistPanelVideoRenderer"]?.jsonObject
                        if (item != null) {
                            val vid = item["videoId"]?.jsonPrimitive?.contentOrNull
                            val title = item["title"]?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                                ?: item["title"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.contentOrNull

                            val runs = item["longBylineText"]?.jsonObject?.get("runs")?.jsonArray
                                ?: item["shortBylineText"]?.jsonObject?.get("runs")?.jsonArray
                            val (parsedArtist, parsedAlbum, parsedDur) = extractMetadataFromRuns(runs, 0L)

                            val lengthStr = item["lengthText"]?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                                ?: item["lengthText"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.contentOrNull
                            val durationSec = if (parsedDur > 0L) parsedDur else if (!lengthStr.isNullOrBlank()) parseDurationStringToSeconds(lengthStr) else 0L

                            val thumbs = item["thumbnail"]?.jsonObject?.get("thumbnails")?.jsonArray
                            val rawArtwork = thumbs?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                                ?: if (vid != null) "https://i.ytimg.com/vi/$vid/hqdefault.jpg" else ""
                            val artwork = if (rawArtwork.startsWith("//")) "https:$rawArtwork" else rawArtwork
                            val highRes = com.lofiq.core.common.ArtworkUtils.getHighResArtworkUrl(artwork, vid)

                            if (!vid.isNullOrBlank() && isValidVideoId(vid) && !title.isNullOrBlank()) {
                                songs.add(
                                    Song(
                                        id = vid,
                                        title = decodeHtml(title),
                                        artist = decodeHtml(parsedArtist),
                                        album = decodeHtml(parsedAlbum),
                                        durationSeconds = durationSec,
                                        artworkUrl = highRes.ifBlank { artwork },
                                        highResArtworkUrl = highRes.ifBlank { artwork },
                                        providerId = "youtube",
                                        quality = AudioQuality.HIGH
                                    )
                                )
                            }
                        }
                    } else {
                        for ((_, v) in el) {
                            traverse(v)
                        }
                    }
                }
                is JsonArray -> {
                    for (item in el) traverse(item)
                }
                else -> {}
            }
        }
        traverse(root)
        return songs.distinctBy { it.id }
    }

    internal fun parseTwoRowPlaylists(root: JsonElement): List<ParsedPlaylist> {
        val itemObjects = extractTwoRowItems(root)
        val playlists = mutableListOf<ParsedPlaylist>()

        for (item in itemObjects) {
            val title = item["title"]?.jsonObject
                ?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull
                ?: item["title"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.contentOrNull
                ?: item["title"]?.jsonPrimitive?.contentOrNull
                ?: continue

            val browseEndpoint = item["navigationEndpoint"]?.jsonObject
                ?.get("browseEndpoint")?.jsonObject
                ?: item["title"]?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject?.get("navigationEndpoint")?.jsonObject?.get("browseEndpoint")?.jsonObject
                ?: item["flexColumns"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                    ?.get("text")?.jsonObject
                    ?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("navigationEndpoint")?.jsonObject?.get("browseEndpoint")?.jsonObject
            var browseId = browseEndpoint?.get("browseId")?.jsonPrimitive?.contentOrNull

            if (browseId.isNullOrBlank()) {
                val watchPlaylistId = item["navigationEndpoint"]?.jsonObject
                    ?.get("watchEndpoint")?.jsonObject?.get("playlistId")?.jsonPrimitive?.contentOrNull
                    ?: item["overlay"]?.jsonObject
                        ?.get("musicItemThumbnailOverlayRenderer")?.jsonObject
                        ?.get("content")?.jsonObject
                        ?.get("musicPlayButtonRenderer")?.jsonObject
                        ?.get("playNavigationEndpoint")?.jsonObject
                        ?.get("watchEndpoint")?.jsonObject?.get("playlistId")?.jsonPrimitive?.contentOrNull
                if (!watchPlaylistId.isNullOrBlank()) {
                    browseId = if (watchPlaylistId.startsWith("VL")) watchPlaylistId else "VL$watchPlaylistId"
                }
            }

            if (browseId.isNullOrBlank()) continue

            // Filter for playlists (starts with VLPL, PL, RDCLAK, VL, FEmusic_library_...)
            if (!browseId.startsWith("VLPL") && !browseId.startsWith("PL") && !browseId.startsWith("VL") && !browseId.startsWith("RDCLAK") && !browseId.startsWith("FEmusic_liked")) {
                continue
            }

            val subtitleRuns = item["subtitle"]?.jsonObject?.get("runs")?.jsonArray
            val subtitle = subtitleRuns?.joinToString(" ") {
                it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
            } ?: item["subtitle"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.contentOrNull
              ?: item["flexColumns"]?.jsonArray?.getOrNull(1)?.jsonObject
                ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                ?.get("text")?.jsonObject
                ?.get("runs")?.jsonArray?.joinToString(" ") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }.orEmpty()

            val thumbs = (item["thumbnailRenderer"]?.jsonObject ?: item["thumbnail"]?.jsonObject)
                ?.get("musicThumbnailRenderer")?.jsonObject
                ?.get("thumbnail")?.jsonObject
                ?.get("thumbnails")?.jsonArray
                ?: item["thumbnail"]?.jsonObject?.get("thumbnails")?.jsonArray
            val artwork = thumbs?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull.orEmpty()

            val countPattern = Pattern.compile("""(\d+)\s+(?:songs?|tracks?)""", Pattern.CASE_INSENSITIVE)
            val matcher = countPattern.matcher(subtitle)
            val songCount = if (matcher.find()) matcher.group(1)?.toIntOrNull() ?: 0 else 0

            playlists.add(
                ParsedPlaylist(
                    id = browseId.removePrefix("VL"),
                    title = decodeHtml(title),
                    description = subtitle,
                    artworkUrl = artwork,
                    songCount = songCount
                )
            )
        }
        return playlists.distinctBy { it.id }
    }

    private fun findVideoIdInItem(item: JsonObject): String? {
        // Direct videoId field
        item["videoId"]?.jsonPrimitive?.contentOrNull?.let { if (it.isNotBlank() && it.length == 11) return it }

        // Overlay watch endpoint
        val playNav = item["overlay"]?.jsonObject
            ?.get("musicItemThumbnailOverlayRenderer")?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("musicPlayButtonRenderer")?.jsonObject
            ?.get("playNavigationEndpoint")?.jsonObject
        val vid1 = playNav?.get("watchEndpoint")?.jsonObject?.get("videoId")?.jsonPrimitive?.contentOrNull
        if (!vid1.isNullOrBlank() && vid1.length == 11) return vid1

        // Navigation endpoint
        val navEndpoint = item["navigationEndpoint"]?.jsonObject
        val vid2 = navEndpoint?.get("watchEndpoint")?.jsonObject?.get("videoId")?.jsonPrimitive?.contentOrNull
        if (!vid2.isNullOrBlank() && vid2.length == 11) return vid2

        // Playlist item data
        val vid3 = item["playlistItemData"]?.jsonObject?.get("videoId")?.jsonPrimitive?.contentOrNull
        if (!vid3.isNullOrBlank() && vid3.length == 11) return vid3

        // Title flex columns navigation endpoint
        val titleEndpoint = item["flexColumns"]?.jsonArray?.getOrNull(0)?.jsonObject
            ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
            ?.get("text")?.jsonObject
            ?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("navigationEndpoint")?.jsonObject
            ?.get("watchEndpoint")?.jsonObject
            ?.get("videoId")?.jsonPrimitive?.contentOrNull
        if (!titleEndpoint.isNullOrBlank() && titleEndpoint.length == 11) return titleEndpoint

        // Menu items watch endpoint
        val menuItems = item["menu"]?.jsonObject
            ?.get("menuRenderer")?.jsonObject
            ?.get("items")?.jsonArray
        if (menuItems != null) {
            for (menuItem in menuItems) {
                val menuNav = menuItem.jsonObject["menuNavigationItemRenderer"]?.jsonObject
                    ?.get("navigationEndpoint")?.jsonObject
                    ?.get("watchEndpoint")?.jsonObject
                    ?.get("videoId")?.jsonPrimitive?.contentOrNull
                if (!menuNav.isNullOrBlank() && menuNav.length == 11) return menuNav
            }
        }

        // Recursive fallback scan for any 11-char videoId
        var foundId: String? = null
        fun scan(el: JsonElement) {
            if (foundId != null) return
            when (el) {
                is JsonObject -> {
                    val vid = el["videoId"]?.jsonPrimitive?.contentOrNull
                    if (!vid.isNullOrBlank() && vid.length == 11 && vid.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
                        foundId = vid
                        return
                    }
                    for ((_, v) in el) {
                        scan(v)
                    }
                }
                is JsonArray -> {
                    for (v in el) scan(v)
                }
                else -> {}
            }
        }
        scan(item)
        return foundId
    }

    private fun extractResponsiveListItems(element: JsonElement): List<JsonObject> {
        val result = mutableListOf<JsonObject>()
        fun traverse(el: JsonElement) {
            when (el) {
                is JsonObject -> {
                    if (el.containsKey("musicResponsiveListItemRenderer")) {
                        el["musicResponsiveListItemRenderer"]?.jsonObject?.let { result.add(it) }
                    } else if (el.containsKey("playlistVideoRenderer")) {
                        el["playlistVideoRenderer"]?.jsonObject?.let { result.add(it) }
                    } else if (el.containsKey("compactVideoRenderer")) {
                        el["compactVideoRenderer"]?.jsonObject?.let { result.add(it) }
                    } else if (el.containsKey("videoRenderer")) {
                        el["videoRenderer"]?.jsonObject?.let { result.add(it) }
                    } else if (el.containsKey("playlistPanelVideoRenderer")) {
                        el["playlistPanelVideoRenderer"]?.jsonObject?.let { result.add(it) }
                    } else {
                        for ((_, v) in el) {
                            traverse(v)
                        }
                    }
                }
                is JsonArray -> {
                    for (item in el) {
                        traverse(item)
                    }
                }
                else -> {}
            }
        }
        traverse(element)
        return result
    }

    private fun extractTwoRowItems(element: JsonElement): List<JsonObject> {
        val result = mutableListOf<JsonObject>()
        fun traverse(el: JsonElement) {
            when (el) {
                is JsonObject -> {
                    if (el.containsKey("musicTwoRowItemRenderer")) {
                        el["musicTwoRowItemRenderer"]?.jsonObject?.let { result.add(it) }
                    } else if (el.containsKey("gridPlaylistRenderer")) {
                        el["gridPlaylistRenderer"]?.jsonObject?.let { result.add(it) }
                    } else if (el.containsKey("playlistRenderer")) {
                        el["playlistRenderer"]?.jsonObject?.let { result.add(it) }
                    } else if (el.containsKey("musicResponsiveListItemRenderer")) {
                        val respItem = el["musicResponsiveListItemRenderer"]?.jsonObject
                        // If it points to a playlist browseId, include it
                        val browseId = respItem?.get("navigationEndpoint")?.jsonObject?.get("browseEndpoint")?.jsonObject?.get("browseId")?.jsonPrimitive?.contentOrNull
                            ?: respItem?.get("flexColumns")?.jsonArray?.firstOrNull()?.jsonObject
                                ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                                ?.get("text")?.jsonObject
                                ?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject
                                ?.get("navigationEndpoint")?.jsonObject?.get("browseEndpoint")?.jsonObject?.get("browseId")?.jsonPrimitive?.contentOrNull
                        if (browseId != null && (browseId.startsWith("VL") || browseId.startsWith("PL") || browseId.startsWith("RDCLAK"))) {
                            respItem?.let { result.add(it) }
                        }
                    } else {
                        for ((_, v) in el) {
                            traverse(v)
                        }
                    }
                }
                is JsonArray -> {
                    for (item in el) {
                        traverse(item)
                    }
                }
                else -> {}
            }
        }
        traverse(element)
        return result
    }

    private fun searchInnerTube(query: String, limit: Int): List<Song> {
        val url = "https://music.youtube.com/youtubei/v1/search?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8&prettyPrint=false"
        val payload = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", "1.20251013.03.00")
                    put("hl", getClientLanguage())
                    put("gl", getClientCountry())
                }
            }
            put("query", query)
            put("params", "Eg-KAQwIARAAGAAgACgAMABqChAEEAMQCRAFEAo=") // Filter for songs/tracks
        }

        val reqBuilder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
            .header("Referer", "https://music.youtube.com/")
            .header("Origin", "https://music.youtube.com")
            .header("X-Origin", "https://music.youtube.com")
            .header("X-YouTube-Client-Name", "67")
            .header("X-YouTube-Client-Version", "1.20251013.03.00")

        val authHeaders = authRepository?.getCachedAuthHeaders() ?: emptyMap()
        authHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }

        val response = httpClient.newCall(reqBuilder.build()).execute()
        if (!response.isSuccessful) return emptyList()

        val body = response.body?.string() ?: return emptyList()
        val root = json.parseToJsonElement(body)
        return parseResponsiveListItemsToSongs(root).take(limit)
    }

    private fun parsePipedSearchJson(jsonStr: String): List<Song> {
        val songs = mutableListOf<Song>()
        try {
            val root = json.parseToJsonElement(jsonStr).jsonObject
            val items = root["items"]?.jsonArray ?: return emptyList()

            for (item in items) {
                val obj = item.jsonObject
                val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: continue
                val videoId = url.removePrefix("/watch?v=").substringBefore("&")
                if (videoId.isBlank()) continue

                val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "Unknown Title"
                val uploaderName = obj["uploaderName"]?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"
                val duration = obj["duration"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                val thumbnail = obj["thumbnail"]?.jsonPrimitive?.contentOrNull 
                    ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                val rawThumbnail = if (thumbnail.startsWith("//")) "https:$thumbnail" else thumbnail

                val highRes = com.lofiq.core.common.ArtworkUtils.getHighResArtworkUrl(rawThumbnail, videoId)

                songs.add(
                    Song(
                        id = videoId,
                        title = decodeHtml(title),
                        artist = decodeHtml(uploaderName),
                        album = "",
                        durationSeconds = duration,
                        artworkUrl = highRes.ifBlank { rawThumbnail },
                        highResArtworkUrl = highRes.ifBlank { rawThumbnail },
                        providerId = "youtube",
                        quality = AudioQuality.HIGH
                    )
                )
            }
        } catch (e: Exception) {
            LofiQLogger.e(LofiQLogger.Category.SEARCH, "Error parsing Piped JSON: ${e.message}")
        }
        return songs
    }

    private fun searchPublicHtml(query: String, limit: Int): List<Song> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val url = "https://www.youtube.com/results?search_query=$encoded&sp=EgIQAQ%253D%253D"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return emptyList()

        val html = response.body?.string() ?: return emptyList()
        val songs = mutableListOf<Song>()

        val pattern = Pattern.compile(""""videoId":"([a-zA-Z0-9_-]{11})".*?"title":\{"runs":\[\{"text":"([^"]+)"\}].*?"ownerText":\{"runs":\[\{"text":"([^"]+)"\}]""")
        val matcher = pattern.matcher(html)
        var count = 0
        while (matcher.find() && count < limit) {
            val videoId = matcher.group(1) ?: continue
            val title = matcher.group(2) ?: "Unknown Title"
            val artist = matcher.group(3) ?: "Unknown Artist"

            val highResUrl = com.lofiq.core.common.ArtworkUtils.getHighResArtworkUrl("https://i.ytimg.com/vi/$videoId/sddefault.jpg", videoId)
            songs.add(
                Song(
                    id = videoId,
                    title = decodeHtml(title),
                    artist = decodeHtml(artist),
                    album = "",
                    durationSeconds = 0L,
                    artworkUrl = highResUrl,
                    highResArtworkUrl = highResUrl,
                    providerId = "youtube",
                    quality = AudioQuality.HIGH
                )
            )
            count++
        }
        return songs
    }

    private fun parseDurationStringToSeconds(durationStr: String): Long {
        val parts = durationStr.trim().split(":")
        return when (parts.size) {
            2 -> (parts[0].toLongOrNull() ?: 0L) * 60 + (parts[1].toLongOrNull() ?: 0L)
            3 -> (parts[0].toLongOrNull() ?: 0L) * 3600 + (parts[1].toLongOrNull() ?: 0L) * 60 + (parts[2].toLongOrNull() ?: 0L)
            else -> 0L
        }
    }

    private fun isValidSong(song: Song): Boolean {
        return isValidVideoId(song.id) &&
                song.title.isNotBlank() &&
                song.title != "Unknown Title" &&
                song.artist.isNotBlank() &&
                song.artworkUrl.isNotBlank() &&
                song.providerId == "youtube"
    }

    private fun isValidVideoId(videoId: String): Boolean {
        return videoId.length == 11 && videoId.matches(Regex("^[a-zA-Z0-9_-]{11}$"))
    }

    private fun decodeHtml(input: String): String {
        return input.replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .trim()
    }
}

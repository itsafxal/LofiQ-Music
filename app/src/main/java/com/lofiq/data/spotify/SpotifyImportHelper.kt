package com.lofiq.data.spotify

import android.content.Context
import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.common.LofiQLogger
import com.lofiq.core.database.dao.PlaylistDao
import com.lofiq.core.database.dao.SongDao
import com.lofiq.core.database.entities.PlaylistEntity
import com.lofiq.core.database.entities.PlaylistSongCrossRef
import com.lofiq.core.model.Song
import com.lofiq.data.repository.MusicRepository
import com.lofiq.data.repository.toEntity
import com.lofiq.resolver.providers.youtube.YouTubeMetadataProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

data class SpotifyTrack(
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val artworkUrl: String = "",
    val id: String = ""
) {
    fun toSong(): Song {
        val songId = if (id.isNotBlank()) "sp_$id" else "sp_${title.hashCode()}_${artist.hashCode()}"
        return Song(
            id = songId,
            title = title,
            artist = artist.ifBlank { "Spotify Artist" },
            album = album,
            durationSeconds = (durationMs / 1000L).coerceAtLeast(0L),
            artworkUrl = artworkUrl,
            highResArtworkUrl = artworkUrl,
            isFavorite = true,
            providerId = "spotify"
        )
    }
}

data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val owner: String? = null,
    val imageUrl: String? = null,
    val trackCount: Int = 0
)

sealed interface SpotifyImportStatus {
    object Idle : SpotifyImportStatus
    object FetchingMetadata : SpotifyImportStatus
    data class MatchingTracks(
        val current: Int,
        val total: Int,
        val trackTitle: String,
        val playlistTitle: String = ""
    ) : SpotifyImportStatus
    data class Success(val playlistName: String, val importedCount: Int) : SpotifyImportStatus
    data class Error(val message: String) : SpotifyImportStatus
}

@Singleton
class SpotifyImportHelper @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val musicRepository: MusicRepository,
    private val youTubeMetadataProvider: YouTubeMetadataProvider,
    private val playlistDao: PlaylistDao,
    private val songDao: SongDao,
    private val dispatchers: DispatcherProvider
) {
    private val _activeImportStatus = MutableStateFlow<SpotifyImportStatus>(SpotifyImportStatus.Idle)
    val activeImportStatus: StateFlow<SpotifyImportStatus> = _activeImportStatus.asStateFlow()

    private val _lastCompletedImport = MutableStateFlow<SpotifyImportStatus.Success?>(null)
    val lastCompletedImport: StateFlow<SpotifyImportStatus.Success?> = _lastCompletedImport.asStateFlow()

    fun clearCompletedImport() {
        _lastCompletedImport.value = null
    }

    fun resetImportStatus() {
        _activeImportStatus.value = SpotifyImportStatus.Idle
    }

    fun cancelActiveImport() {
        _activeImportStatus.value = SpotifyImportStatus.Idle
    }

    fun startBackgroundImport(context: Context, url: String) {
        PlaylistImportService.startImport(context, url)
    }
    /**
     * Extracts type ("playlist", "album", "artist", "track") and ID from a Spotify URL.
     */
    fun extractTypeAndId(url: String): Pair<String?, String?> {
        val types = listOf("playlist", "album", "artist", "track")
        for (type in types) {
            val pattern = "$type/([a-zA-Z0-9]+)".toRegex()
            val match = pattern.find(url)
            if (match != null) return type to match.groupValues[1]
        }
        return null to null
    }

    /**
     * Resolves potential shortened links (e.g. spotify.link, youtu.be).
     */
    suspend fun resolveShortenedUrl(shortUrl: String): String = withContext(dispatchers.io) {
        try {
            val request = Request.Builder()
                .url(shortUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .build()
            val response = okHttpClient.newCall(request).execute()
            val finalUrl = response.request.url.toString()
            response.close()
            finalUrl
        } catch (e: Exception) {
            shortUrl
        }
    }

    /**
     * Fetches public Spotify access token from web player with TOTP calculation.
     */
    private suspend fun fetchSpotifyAnonymousToken(): String? = withContext(dispatchers.io) {
        val serverTime = SpotifyTotpUtils.fetchServerTimeSeconds(okHttpClient, json)
        val totp = SpotifyTotpUtils.generateTotp(serverTime)

        val endpoints = listOf(
            "https://open.spotify.com/get_access_token?reason=transport&productType=web_player&totp=$totp&totpVer=${SpotifyTotpUtils.TOTP_VERSION}&ts=$serverTime",
            "https://open.spotify.com/get_access_token?reason=transport&productType=web_player"
        )

        for (endpoint in endpoints) {
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Referer", "https://open.spotify.com/")
                    .header("Origin", "https://open.spotify.com")
                    .header("App-Platform", "WebPlayer")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    if (body.isNotBlank()) {
                        val root = json.parseToJsonElement(body).jsonObject
                        val token = root["accessToken"]?.jsonPrimitive?.contentOrNull
                        if (!token.isNullOrBlank()) {
                            LofiQLogger.d(LofiQLogger.Category.NETWORK, "Acquired Spotify anonymous token successfully")
                            return@withContext token
                        }
                    }
                }
            } catch (e: Exception) {
                LofiQLogger.w(LofiQLogger.Category.NETWORK, "Spotify anonymous token fetch attempt failed: ${e.message}")
            }
        }
        null
    }

    /**
     * Extracts token from embed HTML __NEXT_DATA__
     */
    private suspend fun extractTokenFromEmbed(playlistId: String): String? = withContext(dispatchers.io) {
        try {
            val url = "https://open.spotify.com/embed/playlist/$playlistId"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body?.string().orEmpty()
                val nextDataRegex = """<script id="__NEXT_DATA__" type="application/json">([^<]+)</script>""".toRegex()
                val match = nextDataRegex.find(html)
                if (match != null) {
                    val root = json.parseToJsonElement(match.groupValues[1])
                    return@withContext findAccessTokenRecursive(root, 0)
                }
            }
        } catch (_: Exception) {}
        null
    }

    private fun findAccessTokenRecursive(element: JsonElement, depth: Int): String? {
        if (depth > 8) return null
        if (element is JsonObject) {
            element["accessToken"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
            for ((_, v) in element) {
                val found = findAccessTokenRecursive(v, depth + 1)
                if (found != null) return found
            }
        } else if (element is JsonArray) {
            for (item in element) {
                val found = findAccessTokenRecursive(item, depth + 1)
                if (found != null) return found
            }
        }
        return null
    }

    /**
     * Fetches complete track list directly via Spotify Web API for playlist, album, artist, track.
     */
    private suspend fun fetchSpotifyTracksViaApi(
        id: String,
        type: String,
        token: String
    ): Pair<String, List<SpotifyTrack>>? = withContext(dispatchers.io) {
        try {
            var name = when (type) {
                "album" -> "Spotify Album"
                "artist" -> "Spotify Artist Top Tracks"
                "track" -> "Spotify Track"
                else -> "Spotify Playlist"
            }
            val allTracks = mutableListOf<SpotifyTrack>()

            when (type) {
                "track" -> {
                    val url = "https://api.spotify.com/v1/tracks/$id"
                    val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
                    val response = okHttpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val root = json.parseToJsonElement(body).jsonObject
                        val title = root["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val artists = root["artists"]?.jsonArray?.mapNotNull {
                            it.jsonObject["name"]?.jsonPrimitive?.contentOrNull
                        }?.joinToString(", ").orEmpty()
                        val albumObj = root["album"]?.jsonObject
                        val albumName = albumObj?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
                        val artwork = albumObj?.get("images")?.jsonArray?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull.orEmpty()
                        val durationMs = root["duration_ms"]?.jsonPrimitive?.longOrNull ?: 0L

                        if (title.isNotBlank()) {
                            allTracks.add(SpotifyTrack(title, artists, albumName, durationMs, artwork))
                            return@withContext title to allTracks
                        }
                    }
                }
                "artist" -> {
                    val url = "https://api.spotify.com/v1/artists/$id/top-tracks?market=from_token"
                    val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
                    val response = okHttpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val root = json.parseToJsonElement(body).jsonObject
                        val items = root["tracks"]?.jsonArray
                        if (items != null) {
                            for (item in items) {
                                val trackObj = item.jsonObject
                                val title = trackObj["name"]?.jsonPrimitive?.contentOrNull ?: continue
                                val artists = trackObj["artists"]?.jsonArray?.mapNotNull {
                                    it.jsonObject["name"]?.jsonPrimitive?.contentOrNull
                                }?.joinToString(", ").orEmpty()
                                val albumObj = trackObj["album"]?.jsonObject
                                val albumName = albumObj?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
                                val artwork = albumObj?.get("images")?.jsonArray?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull.orEmpty()
                                val durationMs = trackObj["duration_ms"]?.jsonPrimitive?.longOrNull ?: 0L
                                allTracks.add(SpotifyTrack(title, artists, albumName, durationMs, artwork))
                            }
                        }
                    }
                }
                "album" -> {
                    val url = "https://api.spotify.com/v1/albums/$id"
                    val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
                    val response = okHttpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val root = json.parseToJsonElement(body).jsonObject
                        name = root["name"]?.jsonPrimitive?.contentOrNull ?: name
                        val albumArtwork = root["images"]?.jsonArray?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull.orEmpty()

                        val items = root["tracks"]?.jsonObject?.get("items")?.jsonArray
                        if (items != null) {
                            for (item in items) {
                                val trackObj = item.jsonObject
                                val title = trackObj["name"]?.jsonPrimitive?.contentOrNull ?: continue
                                val artists = trackObj["artists"]?.jsonArray?.mapNotNull {
                                    it.jsonObject["name"]?.jsonPrimitive?.contentOrNull
                                }?.joinToString(", ").orEmpty()
                                val durationMs = trackObj["duration_ms"]?.jsonPrimitive?.longOrNull ?: 0L
                                allTracks.add(SpotifyTrack(title, artists, name, durationMs, albumArtwork))
                            }
                        }
                    }
                }
                else -> {
                    // Playlist pagination
                    var offset = 0
                    val limit = 100
                    var totalTracks = 1

                    while (offset < totalTracks) {
                        val url = "https://api.spotify.com/v1/playlists/$id/tracks".toHttpUrlOrNull()?.newBuilder()
                            ?.addQueryParameter("limit", limit.toString())
                            ?.addQueryParameter("offset", offset.toString())
                            ?.build() ?: break

                        val request = Request.Builder()
                            .url(url)
                            .header("Authorization", "Bearer $token")
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                            .build()

                        val response = okHttpClient.newCall(request).execute()
                        if (!response.isSuccessful) break

                        val body = response.body?.string() ?: break
                        val root = json.parseToJsonElement(body).jsonObject
                        totalTracks = root["total"]?.jsonPrimitive?.intOrNull ?: 0
                        val items = root["items"]?.jsonArray ?: break
                        if (items.isEmpty()) break

                        for (item in items) {
                            val trackObj = item.jsonObject["track"]?.jsonObject ?: continue
                            val title = trackObj["name"]?.jsonPrimitive?.contentOrNull ?: continue
                            val artistsArray = trackObj["artists"]?.jsonArray
                            val artists = artistsArray?.mapNotNull {
                                it.jsonObject["name"]?.jsonPrimitive?.contentOrNull
                            }?.joinToString(", ").orEmpty()

                            val albumObj = trackObj["album"]?.jsonObject
                            val albumName = albumObj?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
                            val imagesArray = albumObj?.get("images")?.jsonArray
                            val artworkUrl = imagesArray?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull.orEmpty()
                            val durationMs = trackObj["duration_ms"]?.jsonPrimitive?.longOrNull ?: 0L

                            allTracks.add(
                                SpotifyTrack(
                                    title = title,
                                    artist = artists,
                                    album = albumName,
                                    durationMs = durationMs,
                                    artworkUrl = artworkUrl
                                )
                            )
                        }
                        offset += items.size
                    }

                    // Fetch playlist title if available
                    try {
                        val plReq = Request.Builder()
                            .url("https://api.spotify.com/v1/playlists/$id?fields=name")
                            .header("Authorization", "Bearer $token")
                            .build()
                        val plRes = okHttpClient.newCall(plReq).execute()
                        if (plRes.isSuccessful) {
                            val plBody = plRes.body?.string().orEmpty()
                            val plJson = json.parseToJsonElement(plBody).jsonObject
                            plJson["name"]?.jsonPrimitive?.contentOrNull?.let { if (it.isNotBlank()) name = it }
                        }
                    } catch (_: Exception) {}
                }
            }

            if (allTracks.isNotEmpty()) {
                return@withContext name to allTracks
            }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.NETWORK, "Spotify API tracks fetch failed: ${e.message}")
        }
        null
    }

    /**
     * Fallback: Fetches playlist tracks from Spotify Open Embed HTML.
     */
    private suspend fun fetchSpotifyTracksViaEmbed(
        playlistId: String
    ): Pair<String, List<SpotifyTrack>>? = withContext(dispatchers.io) {
        try {
            val embedUrl = "https://open.spotify.com/embed/playlist/$playlistId"
            val request = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val html = response.body?.string() ?: return@withContext null
            val nextDataRegex = """<script id="__NEXT_DATA__" type="application/json">([^<]+)</script>""".toRegex()
            val match = nextDataRegex.find(html) ?: return@withContext null
            val jsonContent = match.groupValues[1]

            val root = json.parseToJsonElement(jsonContent).jsonObject
            val props = root["props"]?.jsonObject
            val pageProps = props?.get("pageProps")?.jsonObject
            val state = pageProps?.get("state")?.jsonObject
            val data = state?.get("data")?.jsonObject
            val entity = data?.get("entity")?.jsonObject ?: return@withContext null

            val playlistName = entity["name"]?.jsonPrimitive?.contentOrNull
                ?: entity["title"]?.jsonPrimitive?.contentOrNull ?: "Spotify Playlist"

            val trackList = entity["trackList"]?.jsonArray ?: return@withContext null
            val tracks = trackList.mapNotNull { elem ->
                val trackObj = elem.jsonObject
                val title = trackObj["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val subtitle = trackObj["subtitle"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val durationMs = trackObj["duration"]?.jsonPrimitive?.longOrNull ?: 0L
                val artwork = trackObj["album"]?.jsonObject?.get("images")?.jsonArray?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull.orEmpty()

                SpotifyTrack(
                    title = title,
                    artist = subtitle,
                    durationMs = durationMs,
                    artworkUrl = artwork
                )
            }

            if (tracks.isNotEmpty()) {
                return@withContext playlistName to tracks
            }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.NETWORK, "Spotify embed extraction failed: ${e.message}")
        }
        null
    }

    /**
     * Imports a playlist through the playlist-scraper API gateway fallback
     */
    private suspend fun fetchPlaylistFromBackend(playlistId: String): Pair<String, List<SpotifyTrack>>? = withContext(dispatchers.io) {
        try {
            val url = PLAYLIST_API_BASE_URL.toHttpUrlOrNull()?.newBuilder()
                ?.addPathSegments("api/v1/playlist/import")
                ?.addQueryParameter("url", "https://open.spotify.com/playlist/$playlistId")
                ?.build() ?: return@withContext null

            val client = okHttpClient.newBuilder()
                .readTimeout(60, TimeUnit.SECONDS)
                .connectTimeout(30, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null

                val body = response.body?.string() ?: return@withContext null
                val root = json.parseToJsonElement(body).jsonObject

                val isSuccess = root["success"]?.jsonPrimitive?.booleanOrNull ?: true
                if (!isSuccess) return@withContext null

                val result = root["data"]?.jsonObject ?: root
                val playlistObj = result["playlist"]?.jsonObject
                val name = playlistObj?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
                val playlistArtwork = playlistObj?.get("image_url")?.jsonPrimitive?.contentOrNull.orEmpty()

                val tracksArray = result["tracks"]?.jsonArray ?: return@withContext null
                val tracks = tracksArray.mapNotNull { element ->
                    val track = element.jsonObject
                    val title = track["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null

                    val artistsArray = track["artists"]?.jsonArray
                    val artists = artistsArray?.mapNotNull { artElem ->
                        if (artElem is JsonObject) {
                            artElem["name"]?.jsonPrimitive?.contentOrNull
                        } else {
                            artElem.jsonPrimitive.contentOrNull
                        }
                    }?.joinToString(", ").orEmpty()

                    val albumObj = track["album"]?.jsonObject
                    val albumName = albumObj?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
                    val artworkUrl = albumObj?.get("image_url")?.jsonPrimitive?.contentOrNull ?: playlistArtwork
                    val durationMs = track["duration_ms"]?.jsonPrimitive?.longOrNull ?: 0L

                    SpotifyTrack(
                        title = title,
                        artist = artists,
                        album = albumName,
                        durationMs = durationMs,
                        artworkUrl = artworkUrl
                    )
                }

                if (tracks.isNotEmpty()) {
                    return@withContext (if (name.isNotBlank()) name else "Spotify Playlist") to tracks
                }
            }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.NETWORK, "Playlist API scraper failed: ${e.message}")
        }
        null
    }

    /**
     * Extracts Spotify tracks via multi-stage cascade (TOTP Web API -> Backend Scraper -> Embed Scraper).
     */
    suspend fun getPlaylistTracks(playlistId: String, type: String = "playlist"): Pair<String, List<SpotifyTrack>>? = withContext(dispatchers.io) {
        // 1. Primary: Spotify Web API with TOTP anonymous token
        val token = fetchSpotifyAnonymousToken() ?: extractTokenFromEmbed(playlistId)
        if (!token.isNullOrBlank()) {
            val apiResult = fetchSpotifyTracksViaApi(playlistId, type, token)
            if (apiResult != null && apiResult.second.isNotEmpty()) {
                return@withContext apiResult
            }
        }

        // 2. Secondary: Backend scraper API
        if (type == "playlist") {
            val backendResult = fetchPlaylistFromBackend(playlistId)
            if (backendResult != null && backendResult.second.isNotEmpty()) {
                return@withContext backendResult
            }
        }

        // 3. Fallback: Spotify Embed Scraper
        if (type == "playlist") {
            fetchSpotifyTracksViaEmbed(playlistId)
        } else null
    }

    /**
     * Safely notifies the progress callback on the main thread and suppresses
     * unexpected caller exceptions (such as background Toast attempts).
     */
    private suspend fun notifyProgress(
        onProgress: (SpotifyImportStatus) -> Unit,
        status: SpotifyImportStatus
    ) {
        _activeImportStatus.value = status
        if (status is SpotifyImportStatus.Success) {
            _lastCompletedImport.value = status
        }
        try {
            withContext(dispatchers.main) {
                onProgress(status)
            }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.NETWORK, "Ignored exception in import onProgress callback: ${e.message}")
        }
    }

    /**
     * Universal Import Workflow (Supports Spotify & YouTube Music / YouTube Playlists)
     */
    suspend fun importSpotifyPlaylist(
        url: String,
        onProgress: (SpotifyImportStatus) -> Unit
    ): Result<Pair<String, Int>> = withContext(dispatchers.io) {
        try {
            notifyProgress(onProgress, SpotifyImportStatus.FetchingMetadata)

            val cleanUrl = url.trim()
            val finalUrl = if (cleanUrl.contains("spotify.link") || cleanUrl.contains("youtu.be")) {
                resolveShortenedUrl(cleanUrl)
            } else cleanUrl

            // 1. Check if it's a YouTube Music or YouTube Playlist URL
            val isYouTube = finalUrl.contains("music.youtube.com") || finalUrl.contains("youtube.com") || finalUrl.contains("list=")
            if (isYouTube) {
                return@withContext importYouTubePlaylistInternal(finalUrl, onProgress)
            }

            // 2. Otherwise process as Spotify URL
            val (type, id) = extractTypeAndId(finalUrl)
            if (id.isNullOrBlank()) {
                val err = "Invalid URL. Please paste a valid Spotify or YouTube Music playlist link."
                notifyProgress(onProgress, SpotifyImportStatus.Error(err))
                return@withContext Result.failure(IllegalArgumentException(err))
            }

            val result = getPlaylistTracks(id, type ?: "playlist")
            if (result == null || result.second.isEmpty()) {
                val err = "Could not load tracks. Please check the playlist link or your internet connection."
                notifyProgress(onProgress, SpotifyImportStatus.Error(err))
                return@withContext Result.failure(IllegalStateException(err))
            }

            val playlistTitle = result.first.ifBlank { "Spotify Import" }
            val spotifyTracks = result.second

            // Create Room Playlist Entity
            val playlistId = "spotify_pl_${UUID.randomUUID()}"
            val playlistEntity = PlaylistEntity(
                id = playlistId,
                name = playlistTitle,
                description = "Imported from Spotify (${spotifyTracks.size} tracks)",
                artworkUrl = spotifyTracks.firstOrNull { it.artworkUrl.isNotBlank() }?.artworkUrl ?: "",
                isSystem = false,
                createdAt = System.currentTimeMillis()
            )
            playlistDao.insertPlaylist(playlistEntity)

            var importedCount = 0
            val total = spotifyTracks.size

            for ((index, track) in spotifyTracks.withIndex()) {
                if (!kotlin.coroutines.coroutineContext.isActive) {
                    throw CancellationException("Import cancelled by user")
                }
                notifyProgress(
                    onProgress,
                    SpotifyImportStatus.MatchingTracks(
                        current = index + 1,
                        total = total,
                        trackTitle = track.title,
                        playlistTitle = playlistTitle
                    )
                )

                // Find candidate matching track
                val matchedSong = findBestMatchedSong(track)

                val finalSong = (matchedSong ?: Song(
                    id = "sp_${UUID.randomUUID()}",
                    title = track.title,
                    artist = track.artist.ifBlank { "Unknown Artist" },
                    album = track.album,
                    durationSeconds = (track.durationMs / 1000L).coerceAtLeast(0L),
                    artworkUrl = track.artworkUrl,
                    highResArtworkUrl = track.artworkUrl,
                    providerId = "spotify"
                ))

                // Save song and link to playlist
                songDao.insertOrUpdate(finalSong.toEntity())
                val crossRef = PlaylistSongCrossRef(
                    playlistId = playlistId,
                    songId = finalSong.id,
                    position = index,
                    addedAt = System.currentTimeMillis()
                )
                playlistDao.addSongToPlaylist(crossRef)
                importedCount++
            }

            notifyProgress(onProgress, SpotifyImportStatus.Success(playlistName = playlistTitle, importedCount = importedCount))
            Result.success(playlistTitle to importedCount)
        } catch (e: Exception) {
            val err = e.message ?: "Failed to import playlist"
            notifyProgress(onProgress, SpotifyImportStatus.Error(err))
            Result.failure(e)
        }
    }

    private suspend fun importYouTubePlaylistInternal(
        url: String,
        onProgress: (SpotifyImportStatus) -> Unit
    ): Result<Pair<String, Int>> = withContext(dispatchers.io) {
        try {
            val ytResult = youTubeMetadataProvider.fetchYouTubePlaylistDetails(url)
            if (ytResult == null || ytResult.second.isEmpty()) {
                val err = "Could not fetch YouTube playlist. Please ensure the playlist is public or unlisted."
                notifyProgress(onProgress, SpotifyImportStatus.Error(err))
                return@withContext Result.failure(IllegalStateException(err))
            }

            val playlistTitle = ytResult.first.ifBlank { "YouTube Import" }
            val ytSongs = ytResult.second

            val playlistId = "yt_pl_${UUID.randomUUID()}"
            val playlistEntity = PlaylistEntity(
                id = playlistId,
                name = playlistTitle,
                description = "Imported from YouTube Music (${ytSongs.size} tracks)",
                artworkUrl = ytSongs.firstOrNull()?.artworkUrl.orEmpty(),
                isSystem = false,
                createdAt = System.currentTimeMillis()
            )
            playlistDao.insertPlaylist(playlistEntity)

            var importedCount = 0
            val total = ytSongs.size

            for ((index, song) in ytSongs.withIndex()) {
                if (!kotlin.coroutines.coroutineContext.isActive) {
                    throw CancellationException("Import cancelled by user")
                }
                notifyProgress(
                    onProgress,
                    SpotifyImportStatus.MatchingTracks(
                        current = index + 1,
                        total = total,
                        trackTitle = song.title,
                        playlistTitle = playlistTitle
                    )
                )
                songDao.insertOrUpdate(song.toEntity())
                val crossRef = PlaylistSongCrossRef(
                    playlistId = playlistId,
                    songId = song.id,
                    position = index,
                    addedAt = System.currentTimeMillis()
                )
                playlistDao.addSongToPlaylist(crossRef)
                importedCount++
            }

            notifyProgress(onProgress, SpotifyImportStatus.Success(playlistName = playlistTitle, importedCount = importedCount))
            Result.success(playlistTitle to importedCount)
        } catch (e: Exception) {
            val err = e.message ?: "Failed to import YouTube playlist"
            notifyProgress(onProgress, SpotifyImportStatus.Error(err))
            Result.failure(e)
        }
    }

    private suspend fun findBestMatchedSong(track: SpotifyTrack): Song? {
        val cleanedTitle = coreTitleOf(track.title)
        val query = "$cleanedTitle ${track.artist}".trim()

        return try {
            val searchResults = musicRepository.searchSongs(query, limit = 5)
            if (searchResults.isEmpty()) return null

            val sourceTokens = normalizedTrackTokens(track.title)
            val sourceArtistTokens = normalizedTrackTokens(track.artist)

            searchResults.mapNotNull { candidate ->
                val resultTokens = normalizedTrackTokens(candidate.title)
                val resultArtistTokens = normalizedTrackTokens(candidate.artist)

                val tokenScore = tokenSimilarity(sourceTokens, resultTokens)
                val spellingScore = spellingSimilarity(sourceTokens, resultTokens)
                val titleScore = maxOf(tokenScore, spellingScore)

                val artistScore = tokenSimilarity(sourceArtistTokens, resultArtistTokens)
                val durationDelta = if (track.durationMs > 0 && candidate.durationSeconds > 0) {
                    abs(track.durationMs - candidate.durationSeconds * 1000L)
                } else Long.MAX_VALUE

                val durationScore = when {
                    durationDelta <= 6_000L -> 1.0
                    durationDelta <= 20_000L -> 0.6
                    else -> 0.2
                }

                val totalScore = titleScore * 0.65 + artistScore * 0.25 + durationScore * 0.10
                if (titleScore >= 0.4 || (titleScore >= 0.3 && durationDelta <= 6_000L)) {
                    candidate to totalScore
                } else null
            }.maxByOrNull { it.second }?.first ?: searchResults.firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizedTrackTokens(value: String): Set<String> {
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .lowercase(Locale.ROOT)
            .replace("&", " and ")
            .replace("[^\\p{L}\\p{N}\\p{M}]+".toRegex(), " ")
            .trim()

        val noise = setOf("official", "video", "audio", "lyrics", "lyric", "visualizer", "topic", "hd", "hq", "4k")
        return normalized.split(Regex("\\s+")).filter { it.isNotBlank() && it !in noise }.toSet()
    }

    private fun tokenSimilarity(left: Set<String>, right: Set<String>): Double {
        if (left.isEmpty() || right.isEmpty()) return 0.0
        if (left == right) return 1.0
        val overlap = left.intersect(right).size.toDouble()
        return overlap / maxOf(left.size, right.size).toDouble()
    }

    private fun spellingSimilarity(left: Set<String>, right: Set<String>): Double {
        val a = left.sorted().joinToString("")
        val b = right.sorted().joinToString("")
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val longest = maxOf(a.length, b.length)
        if (abs(a.length - b.length).toDouble() / longest > 0.25) return 0.0
        return (1.0 - levenshtein(a, b).toDouble() / longest) * 0.95
    }

    private fun levenshtein(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    private fun coreTitleOf(title: String): String {
        var core = title.replace(Regex("[\\(\\[\\{][^\\)\\]\\}]*[\\)\\]\\}]"), " ")
        val dashIndex = core.lastIndexOf(" - ")
        if (dashIndex > 0) {
            val tail = core.substring(dashIndex + 3).lowercase(Locale.ROOT)
            if (QUALIFIER_WORDS.any { it.containsMatchIn(tail) }) core = core.substring(0, dashIndex)
        }
        core = core.replace(Regex("\\b(feat\\.?|ft\\.?|featuring)\\b.*$", RegexOption.IGNORE_CASE), " ")
        return core.trim().ifBlank { title.trim() }
    }

    companion object {
        private const val PLAYLIST_API_BASE_URL = "https://playlist.suvojeetsengupta.in"

        private val QUALIFIER_WORDS = listOf(
            "remaster", "remastered", "radio edit", "single version", "album version",
            "mono", "stereo", "deluxe", "bonus track", "anniversary", "edit",
            "original mix", "explicit", "clean", "reissue", "digital"
        ).map { word -> Regex("(?<![a-z0-9])" + Regex.escape(word) + "(?![a-z0-9])", RegexOption.IGNORE_CASE) }
    }
}

package com.lofiq.data.spotify

import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.common.LofiQLogger
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpotifyWebClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val tokenManager: SpotifyTokenManager,
    private val dispatchers: DispatcherProvider
) {
    companion object {
        const val LIKED_ID = "liked"
        const val LIKED_COVER = "https://misc.scdn.co/liked-songs/liked-songs-640.png"

        private const val GQL_URL = "https://api-partner.spotify.com/pathfinder/v2/query"
        private val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaType()

        // Pathfinder persisted query SHA256 hashes
        private const val HASH_LIBRARY_V3 = "973e511ca44261fda7eebac8b653155e7caee3675abb4fb110cc1b8c78b091c3"
        private const val HASH_LIBRARY_V3_ALT = "390c78e5b951029bad359785e69b07b536a509c581cbcd0aded5e5067f187455"

        private const val HASH_PLAYLIST = "346811f856fb0b7e4f6c59f8ebea78dd081c6e2fb01b77c954b26259d5fc6763"
        private const val HASH_PLAYLIST_ALT = "21ea11a6ac75a5f3e01fd365d29c18e0a4a8b7dc09f89bbb5d73910190c6acaf"

        private const val HASH_LIKED_TRACKS = "087278b20b743578a6262c2b0b4bcd20d879c503cc359a2285baf083ef944240"
        private const val HASH_PROFILE = "08ffb4730af3746e04a8301396f20875dbbce10c75243803091a9274eacc8ac0"
    }

    private suspend fun buildAuthHeaders(spDc: String): Map<String, String> {
        val accessToken = tokenManager.getAccessToken(spDc)
            ?: throw IllegalStateException("Failed to obtain Spotify access token")
        val clientToken = tokenManager.getClientToken()

        return buildMap {
            put("Authorization", "Bearer $accessToken")
            put("Accept", "application/json")
            put("app-platform", "WebPlayer")
            put("Origin", "https://open.spotify.com")
            put("Referer", "https://open.spotify.com/")
            put("User-Agent", SpotifyTokenManager.WEB_PLAYER_UA)
            clientToken?.let { put("Client-Token", it) }
        }
    }

    private fun executeGql(
        operationName: String,
        hash: String,
        variables: JsonObject,
        headers: Map<String, String>
    ): JsonObject {
        val body = buildJsonObject {
            put("variables", variables)
            put("operationName", operationName)
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("version", 1)
                    put("sha256Hash", hash)
                }
            }
        }

        val request = Request.Builder()
            .url(GQL_URL)
            .post(body.toString().toRequestBody(MEDIA_TYPE_JSON))
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()

        val responseText = okHttpClient.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Spotify GraphQL $operationName failed (code ${response.code})")
            }
            payload
        }

        val root = json.parseToJsonElement(responseText).jsonObject
        val error = root.arr("errors")?.firstOrNull()?.jsonObject?.str("message")
        if (!error.isNullOrBlank()) {
            throw IllegalStateException("GraphQL error: $error")
        }
        return root
    }

    /**
     * Fetches the user profile via REST or GraphQL
     */
    suspend fun fetchUserProfile(spDc: String): SpotifyAccount = withContext(dispatchers.io) {
        val accessToken = tokenManager.getAccessToken(spDc)
        if (accessToken.isNullOrBlank()) {
            return@withContext SpotifyAccount(name = "Spotify User", isLoggedIn = true)
        }

        // Try REST API first
        try {
            val req = Request.Builder()
                .url("https://api.spotify.com/v1/me")
                .header("Authorization", "Bearer $accessToken")
                .header("User-Agent", SpotifyTokenManager.WEB_PLAYER_UA)
                .build()

            okHttpClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string().orEmpty()
                    val root = json.parseToJsonElement(body).jsonObject
                    val displayName = root["display_name"]?.jsonPrimitive?.contentOrNull
                        ?.ifBlank { null }
                        ?: root["id"]?.jsonPrimitive?.contentOrNull
                        ?: "Spotify User"
                    val email = root["email"]?.jsonPrimitive?.contentOrNull
                    val id = root["id"]?.jsonPrimitive?.contentOrNull
                    val product = root["product"]?.jsonPrimitive?.contentOrNull
                    val images = root["images"]?.jsonArray
                    val avatarUrl = images?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull

                    return@withContext SpotifyAccount(
                        name = displayName,
                        email = email,
                        avatarUrl = avatarUrl,
                        id = id,
                        product = product,
                        isLoggedIn = true
                    )
                }
            }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.NETWORK, "Spotify /v1/me profile fetch failed: ${e.message}")
        }

        // Try GraphQL profileAttributes fallback
        try {
            val headers = buildAuthHeaders(spDc)
            val root = executeGql("profileAttributes", HASH_PROFILE, buildJsonObject {}, headers)
            val profile = root.obj("data")?.obj("me")?.obj("profile")
            val name = profile?.str("name") ?: profile?.str("username") ?: "Spotify User"
            val avatarSources = profile?.obj("avatar")?.arr("sources")
            val avatarUrl = avatarSources?.firstOrNull()?.jsonObject?.str("url")
            return@withContext SpotifyAccount(
                name = name,
                avatarUrl = avatarUrl,
                isLoggedIn = true
            )
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.NETWORK, "Spotify GraphQL profile fetch failed: ${e.message}")
        }

        SpotifyAccount(name = "Spotify User", isLoggedIn = true)
    }

    /**
     * Fetches user's saved playlists
     */
    suspend fun fetchPlaylists(spDc: String, offset: Int = 0): Pair<List<SpotifyPlaylist>, Int> = withContext(dispatchers.io) {
        // Try GraphQL libraryV3
        try {
            val headers = buildAuthHeaders(spDc)
            val variables = buildJsonObject {
                putJsonArray("filters") { add("Playlists") }
                put("order", null as String?)
                put("textFilter", "")
                putJsonArray("features") {
                    add("LIKED_SONGS")
                    add("YOUR_EPISODES_V2")
                    add("PRERELEASES")
                    add("EVENTS")
                }
                put("limit", 50)
                put("offset", offset)
                put("flatten", true)
                putJsonArray("expandedFolders") {}
                put("folderUri", null as String?)
                put("includeFoldersWhenFlattening", false)
            }

            val root = try {
                executeGql("libraryV3", HASH_LIBRARY_V3, variables, headers)
            } catch (_: Exception) {
                executeGql("libraryV3", HASH_LIBRARY_V3_ALT, variables, headers)
            }

            val library = root.obj("data")?.obj("me")?.obj("libraryV3")
            if (library != null) {
                val rawItems = library.arr("items").orEmpty()
                val playlists = rawItems.mapNotNull { elem ->
                    val wrapper = elem.jsonObject.obj("item") ?: return@mapNotNull null
                    val uri = wrapper.str("_uri") ?: wrapper.str("uri") ?: return@mapNotNull null
                    val data = wrapper.obj("data") ?: return@mapNotNull null
                    val name = data.str("name").orEmpty()
                    if (name.isBlank()) return@mapNotNull null

                    val ownerName = data.obj("ownerV2")?.obj("data")?.str("name")
                    val cover = largestSource(data.obj("images"))

                    SpotifyPlaylist(
                        id = uri.substringAfterLast(":"),
                        name = name,
                        owner = ownerName,
                        imageUrl = cover
                    )
                }
                val totalCount = library.int("totalCount") ?: playlists.size
                return@withContext Pair(playlists, totalCount)
            }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.NETWORK, "Spotify GraphQL libraryV3 fetch failed: ${e.message}")
        }

        // REST fallback: /v1/me/playlists
        try {
            val accessToken = tokenManager.getAccessToken(spDc) ?: return@withContext Pair(emptyList(), 0)
            val url = "https://api.spotify.com/v1/me/playlists".toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("limit", "50")
                ?.addQueryParameter("offset", offset.toString())
                ?.build() ?: return@withContext Pair(emptyList(), 0)

            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .header("User-Agent", SpotifyTokenManager.WEB_PLAYER_UA)
                .build()

            okHttpClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string().orEmpty()
                    val root = json.parseToJsonElement(body).jsonObject
                    val total = root.int("total") ?: 0
                    val items = root.arr("items").orEmpty()
                    val playlists = items.mapNotNull { elem ->
                        val obj = elem.jsonObject
                        val id = obj.str("id") ?: return@mapNotNull null
                        val name = obj.str("name") ?: return@mapNotNull null
                        val owner = obj.obj("owner")?.str("display_name")
                        val images = obj.arr("images")
                        val cover = images?.firstOrNull()?.jsonObject?.str("url")
                        val tracksTotal = obj.obj("tracks")?.int("total") ?: 0

                        SpotifyPlaylist(
                            id = id,
                            name = name,
                            owner = owner,
                            imageUrl = cover,
                            trackCount = tracksTotal
                        )
                    }
                    return@withContext Pair(playlists, total)
                }
            }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.NETWORK, "Spotify REST /v1/me/playlists failed: ${e.message}")
        }

        Pair(emptyList(), 0)
    }

    /**
     * Fetches user's Liked Songs (fetchLibraryTracks)
     */
    suspend fun fetchLikedSongs(spDc: String, offset: Int = 0, limit: Int = 50): Pair<List<SpotifyTrack>, Int> = withContext(dispatchers.io) {
        // Try GraphQL fetchLibraryTracks
        try {
            val headers = buildAuthHeaders(spDc)
            val variables = buildJsonObject {
                put("offset", offset)
                put("limit", limit)
            }
            val root = executeGql("fetchLibraryTracks", HASH_LIKED_TRACKS, variables, headers)
            val tracksObj = root.obj("data")?.obj("me")?.obj("library")?.obj("tracks")
            if (tracksObj != null) {
                val rawItems = tracksObj.arr("items").orEmpty()
                val tracks = rawItems.mapNotNull { elem ->
                    val trackWrapper = elem.jsonObject.obj("track") ?: return@mapNotNull null
                    val data = trackWrapper.obj("data") ?: return@mapNotNull null
                    val title = data.str("name").orEmpty()
                    if (title.isBlank()) return@mapNotNull null
                    val uri = trackWrapper.str("_uri") ?: data.str("uri") ?: ""
                    val trackId = uri.substringAfterLast(":")

                    val artists = data.obj("artists")?.arr("items").orEmpty().mapNotNull { a ->
                        a.jsonObject.obj("profile")?.str("name")?.takeIf { it.isNotBlank() }
                    }.joinToString(", ")

                    val album = data.obj("albumOfTrack")
                    val albumName = album?.str("name").orEmpty()
                    val artworkUrl = album?.obj("coverArt")?.arr("sources")?.lastUrl().orEmpty()
                    val durationMs = (data.obj("trackDuration") ?: data.obj("duration"))?.int("totalMilliseconds")?.toLong() ?: 0L

                    SpotifyTrack(
                        id = trackId,
                        title = title,
                        artist = artists.ifBlank { "Spotify Artist" },
                        album = albumName,
                        durationMs = durationMs,
                        artworkUrl = artworkUrl
                    )
                }
                val totalCount = tracksObj.int("totalCount") ?: tracks.size
                return@withContext Pair(tracks, totalCount)
            }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.NETWORK, "Spotify GraphQL fetchLibraryTracks failed: ${e.message}")
        }

        // REST fallback: /v1/me/tracks
        try {
            val accessToken = tokenManager.getAccessToken(spDc) ?: return@withContext Pair(emptyList(), 0)
            val url = "https://api.spotify.com/v1/me/tracks".toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("limit", limit.toString())
                ?.addQueryParameter("offset", offset.toString())
                ?.build() ?: return@withContext Pair(emptyList(), 0)

            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .header("User-Agent", SpotifyTokenManager.WEB_PLAYER_UA)
                .build()

            okHttpClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string().orEmpty()
                    val root = json.parseToJsonElement(body).jsonObject
                    val total = root.int("total") ?: 0
                    val items = root.arr("items").orEmpty()
                    val tracks = items.mapNotNull { item ->
                        val trackObj = item.jsonObject["track"]?.jsonObject ?: return@mapNotNull null
                        val trackId = trackObj.str("id") ?: ""
                        val title = trackObj.str("name") ?: return@mapNotNull null
                        val artists = trackObj.arr("artists").orEmpty().mapNotNull {
                            it.jsonObject.str("name")
                        }.joinToString(", ")
                        val albumObj = trackObj.obj("album")
                        val albumName = albumObj?.str("name").orEmpty()
                        val images = albumObj?.arr("images")
                        val artworkUrl = images?.firstOrNull()?.jsonObject?.str("url").orEmpty()
                        val durationMs = trackObj.jsonPrimitive("duration_ms")?.intOrNull?.toLong() ?: 0L

                        SpotifyTrack(
                            id = trackId,
                            title = title,
                            artist = artists.ifBlank { "Spotify Artist" },
                            album = albumName,
                            durationMs = durationMs,
                            artworkUrl = artworkUrl
                        )
                    }
                    return@withContext Pair(tracks, total)
                }
            }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.NETWORK, "Spotify REST /v1/me/tracks failed: ${e.message}")
        }

        Pair(emptyList(), 0)
    }

    /**
     * Fetches tracks for a playlist (or liked songs if playlistId == LIKED_ID)
     */
    suspend fun fetchPlaylistTracks(
        spDc: String,
        playlistId: String,
        offset: Int = 0,
        limit: Int = 100
    ): Pair<List<SpotifyTrack>, Int> = withContext(dispatchers.io) {
        if (playlistId == LIKED_ID) {
            return@withContext fetchLikedSongs(spDc, offset, limit)
        }

        // Try GraphQL fetchPlaylist
        try {
            val headers = buildAuthHeaders(spDc)
            val variables = buildJsonObject {
                put("uri", "spotify:playlist:$playlistId")
                put("offset", offset)
                put("limit", limit)
                put("enableWatchFeedEntrypoint", false)
            }

            val root = try {
                executeGql("fetchPlaylist", HASH_PLAYLIST, variables, headers)
            } catch (_: Exception) {
                executeGql("fetchPlaylist", HASH_PLAYLIST_ALT, variables, headers)
            }

            val content = root.obj("data")?.obj("playlistV2")?.obj("content")
            if (content != null) {
                val rawItems = content.arr("items").orEmpty()
                val tracks = rawItems.mapNotNull { elem ->
                    val data = elem.jsonObject.obj("itemV2")?.obj("data") ?: return@mapNotNull null
                    val title = data.str("name").orEmpty()
                    if (title.isBlank()) return@mapNotNull null
                    val uri = data.str("uri") ?: data.str("_uri") ?: ""
                    val trackId = uri.substringAfterLast(":")

                    val artists = data.obj("artists")?.arr("items").orEmpty().mapNotNull { a ->
                        a.jsonObject.obj("profile")?.str("name")?.takeIf { it.isNotBlank() }
                    }.joinToString(", ")

                    val album = data.obj("albumOfTrack")
                    val albumName = album?.str("name").orEmpty()
                    val artworkUrl = album?.obj("coverArt")?.arr("sources")?.lastUrl().orEmpty()
                    val durationMs = data.obj("duration")?.int("totalMilliseconds")?.toLong() ?: 0L

                    SpotifyTrack(
                        id = trackId,
                        title = title,
                        artist = artists.ifBlank { "Spotify Artist" },
                        album = albumName,
                        durationMs = durationMs,
                        artworkUrl = artworkUrl
                    )
                }
                val totalCount = content.int("totalCount") ?: tracks.size
                return@withContext Pair(tracks, totalCount)
            }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.NETWORK, "Spotify GraphQL fetchPlaylist failed: ${e.message}")
        }

        // REST fallback: /v1/playlists/{id}/tracks
        try {
            val accessToken = tokenManager.getAccessToken(spDc) ?: return@withContext Pair(emptyList(), 0)
            val url = "https://api.spotify.com/v1/playlists/$playlistId/tracks".toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("limit", limit.toString())
                ?.addQueryParameter("offset", offset.toString())
                ?.build() ?: return@withContext Pair(emptyList(), 0)

            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .header("User-Agent", SpotifyTokenManager.WEB_PLAYER_UA)
                .build()

            okHttpClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string().orEmpty()
                    val root = json.parseToJsonElement(body).jsonObject
                    val total = root.int("total") ?: 0
                    val items = root.arr("items").orEmpty()
                    val tracks = items.mapNotNull { item ->
                        val trackObj = item.jsonObject["track"]?.jsonObject ?: return@mapNotNull null
                        val trackId = trackObj.str("id") ?: ""
                        val title = trackObj.str("name") ?: return@mapNotNull null
                        val artists = trackObj.arr("artists").orEmpty().mapNotNull {
                            it.jsonObject.str("name")
                        }.joinToString(", ")
                        val albumObj = trackObj.obj("album")
                        val albumName = albumObj?.str("name").orEmpty()
                        val images = albumObj?.arr("images")
                        val artworkUrl = images?.firstOrNull()?.jsonObject?.str("url").orEmpty()
                        val durationMs = trackObj.jsonPrimitive("duration_ms")?.intOrNull?.toLong() ?: 0L

                        SpotifyTrack(
                            id = trackId,
                            title = title,
                            artist = artists.ifBlank { "Spotify Artist" },
                            album = albumName,
                            durationMs = durationMs,
                            artworkUrl = artworkUrl
                        )
                    }
                    return@withContext Pair(tracks, total)
                }
            }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.NETWORK, "Spotify REST fetchPlaylist tracks failed: ${e.message}")
        }

        Pair(emptyList(), 0)
    }

    private fun largestSource(images: JsonObject?): String? =
        images?.arr("items")?.firstOrNull()?.jsonObject?.arr("sources")
            ?.mapNotNull { source ->
                val url = source.jsonObject.str("url") ?: return@mapNotNull null
                url to (source.jsonObject.int("width") ?: 0)
            }
            ?.maxByOrNull { it.second }
            ?.first

    private fun JsonArray.lastUrl(): String? =
        mapNotNull { it.jsonObject.str("url") }.lastOrNull()

    private fun JsonObject.obj(key: String): JsonObject? =
        this[key]?.takeIf { it !is JsonNull }?.let { runCatching { it.jsonObject }.getOrNull() }

    private fun JsonObject.arr(key: String): JsonArray? =
        this[key]?.takeIf { it !is JsonNull }?.let { runCatching { it.jsonArray }.getOrNull() }

    private fun JsonObject.str(key: String): String? =
        this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull

    private fun JsonObject.int(key: String): Int? =
        this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.intOrNull

    private fun JsonObject.jsonPrimitive(key: String) =
        this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive
}

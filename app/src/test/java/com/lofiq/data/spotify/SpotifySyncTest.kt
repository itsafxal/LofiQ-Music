package com.lofiq.data.spotify

import com.lofiq.core.common.DefaultDispatcherProvider
import com.lofiq.core.database.entities.PlaylistEntity
import com.lofiq.data.repository.toEntity
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SpotifySyncTest {

    private lateinit var json: Json
    private lateinit var spotifyAuthRepository: SpotifyAuthRepository

    @Before
    fun setUp() {
        json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
        spotifyAuthRepository = SpotifyAuthRepository(
            context = mockk(relaxed = true),
            songDao = mockk(relaxed = true),
            favoriteDao = mockk(relaxed = true),
            playlistDao = mockk(relaxed = true),
            musicRepository = mockk(relaxed = true),
            spotifyImportHelper = mockk(relaxed = true),
            tokenManager = mockk(relaxed = true),
            spotifyWebClient = mockk(relaxed = true),
            dispatchers = DefaultDispatcherProvider()
        )
    }

    @Test
    fun testParseSpotifyUserProfileJson() {
        val userProfileJson = """
        {
            "display_name": "Afsal Developer",
            "email": "developer@spotify.com",
            "id": "spotify_user_998",
            "product": "premium",
            "images": [
                {
                    "url": "https://i.scdn.co/image/ab6775700000ee85123456"
                }
            ]
        }
        """.trimIndent()

        val root = json.parseToJsonElement(userProfileJson).jsonObject
        val displayName = root["display_name"]?.jsonPrimitive?.content ?: "Spotify User"
        val email = root["email"]?.jsonPrimitive?.content
        val id = root["id"]?.jsonPrimitive?.content
        val product = root["product"]?.jsonPrimitive?.content
        val avatarUrl = root["images"]?.jsonArray?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content

        val account = SpotifyAccount(
            name = displayName,
            email = email,
            avatarUrl = avatarUrl,
            id = id,
            product = product,
            isLoggedIn = true
        )

        assertEquals("Afsal Developer", account.name)
        assertEquals("developer@spotify.com", account.email)
        assertEquals("spotify_user_998", account.id)
        assertEquals("premium", account.product)
        assertEquals("https://i.scdn.co/image/ab6775700000ee85123456", account.avatarUrl)
        assertTrue(account.isLoggedIn)
    }

    @Test
    fun testParseSpotifySavedTracksJson() {
        val tracksJson = """
        {
            "total": 120,
            "items": [
                {
                    "added_at": "2026-01-01T00:00:00Z",
                    "track": {
                        "id": "4cOdK2wGLETKBW3PvgPWqT",
                        "name": "Starboy",
                        "artists": [
                            {"name": "The Weeknd"},
                            {"name": "Daft Punk"}
                        ],
                        "album": {
                            "name": "Starboy",
                            "images": [
                                {"url": "https://i.scdn.co/image/starboy_cover"}
                            ]
                        },
                        "duration_ms": 230453
                    }
                }
            ]
        }
        """.trimIndent()

        val root = json.parseToJsonElement(tracksJson).jsonObject
        val total = root["total"]?.jsonPrimitive?.content?.toInt()
        val items = root["items"]?.jsonArray

        assertEquals(120, total)
        assertNotNull(items)
        assertEquals(1, items?.size)

        val trackObj = items!![0].jsonObject["track"]?.jsonObject
        assertNotNull(trackObj)

        val rawId = trackObj!!["id"]?.jsonPrimitive?.content
        val deterministicSongId = "sp_$rawId"
        val title = trackObj["name"]?.jsonPrimitive?.content
        val artists = trackObj["artists"]?.jsonArray?.joinToString(", ") {
            it.jsonObject["name"]?.jsonPrimitive?.content.orEmpty()
        }
        val durationSec = (trackObj["duration_ms"]?.jsonPrimitive?.content?.toLong() ?: 0L) / 1000L

        assertEquals("sp_4cOdK2wGLETKBW3PvgPWqT", deterministicSongId)
        assertEquals("Starboy", title)
        assertEquals("The Weeknd, Daft Punk", artists)
        assertEquals(230L, durationSec)
    }

    @Test
    fun testParseSpotifyPlaylistsJson() {
        val playlistsJson = """
        {
            "total": 5,
            "items": [
                {
                    "id": "37i9dQZF1DXcBWIGoYBM5M",
                    "name": "Today's Top Hits",
                    "description": "Jung Kook is on top of the Hottest 50!",
                    "images": [
                        {"url": "https://i.scdn.co/image/top_hits.jpg"}
                    ]
                }
            ]
        }
        """.trimIndent()

        val root = json.parseToJsonElement(playlistsJson).jsonObject
        val total = root["total"]?.jsonPrimitive?.content?.toInt()
        val items = root["items"]?.jsonArray

        assertEquals(5, total)
        assertEquals(1, items?.size)

        val pl = items!![0].jsonObject
        val plId = pl["id"]?.jsonPrimitive?.content
        val deterministicPlaylistId = "spotify_pl_$plId"
        val name = pl["name"]?.jsonPrimitive?.content
        val artwork = pl["images"]?.jsonArray?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content

        assertEquals("spotify_pl_37i9dQZF1DXcBWIGoYBM5M", deterministicPlaylistId)
        assertEquals("Today's Top Hits", name)
        assertEquals("https://i.scdn.co/image/top_hits.jpg", artwork)
    }

    @Test
    fun testSpotifyTrackToSongEntityConversionForLikedSongs() {
        val track = SpotifyTrack(
            id = "4cOdK2wGLETKBW3PvgPWqT",
            title = "Starboy",
            artist = "The Weeknd, Daft Punk",
            album = "Starboy",
            durationMs = 230453L,
            artworkUrl = "https://i.scdn.co/image/starboy_cover"
        )
        val song = track.toSong()
        val songEntity = song.toEntity()

        assertEquals("sp_4cOdK2wGLETKBW3PvgPWqT", song.id)
        assertEquals("sp_4cOdK2wGLETKBW3PvgPWqT", songEntity.id)
        assertEquals("Starboy", songEntity.title)
        assertEquals("The Weeknd, Daft Punk", songEntity.artist)
        assertEquals(230L, songEntity.durationSeconds)
        assertEquals("spotify", songEntity.providerId)
    }

    @Test
    fun testSpotifyPlaylistEntityMapping() {
        val pl = SpotifyPlaylist(
            id = "37i9dQZF1DXcBWIGoYBM5M",
            name = "Today's Top Hits",
            owner = "Spotify",
            imageUrl = "https://i.scdn.co/image/top_hits.jpg",
            trackCount = 50
        )
        val localPlaylistId = "spotify_pl_${pl.id}"
        val playlistEntity = PlaylistEntity(
            id = localPlaylistId,
            name = pl.name,
            description = pl.owner?.let { "Created by $it" }.orEmpty(),
            artworkUrl = pl.imageUrl.orEmpty(),
            isSystem = false,
            createdAt = 123456789L
        )

        assertEquals("spotify_pl_37i9dQZF1DXcBWIGoYBM5M", playlistEntity.id)
        assertEquals("Today's Top Hits", playlistEntity.name)
        assertEquals("Created by Spotify", playlistEntity.description)
        assertEquals("https://i.scdn.co/image/top_hits.jpg", playlistEntity.artworkUrl)
    }
}

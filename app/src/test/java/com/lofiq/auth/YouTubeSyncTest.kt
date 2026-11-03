package com.lofiq.auth

import com.lofiq.core.common.DefaultDispatcherProvider
import com.lofiq.resolver.providers.youtube.YouTubeMetadataProvider
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class YouTubeSyncTest {

    private lateinit var json: Json
    private lateinit var authRepository: AuthRepository
    private lateinit var youTubeMetadataProvider: YouTubeMetadataProvider

    @Before
    fun setUp() {
        json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
        authRepository = AuthRepository(
            context = mockk(relaxed = true),
            okHttpClient = mockk(relaxed = true),
            json = json,
            dispatchers = DefaultDispatcherProvider()
        )
        youTubeMetadataProvider = YouTubeMetadataProvider(
            httpClient = mockk(relaxed = true),
            json = json,
            dispatchers = DefaultDispatcherProvider()
        )
    }

    @Test
    fun testParseUserProfileFromActiveAccountHeaderRenderer() {
        val sampleJson = """
        {
            "activeAccountHeaderRenderer": {
                "accountName": {
                    "runs": [{"text": "Afsal Tech"}]
                },
                "email": {
                    "runs": [{"text": "afsal@example.com"}]
                },
                "accountPhoto": {
                    "thumbnails": [
                        {"url": "https://lh3.googleusercontent.com/a/avatar123=s96-c"}
                    ]
                },
                "channelHandle": {
                    "runs": [{"text": "@afsal_tech"}]
                },
                "endpoint": {
                    "browseEndpoint": {
                        "browseId": "UC1234567890abcdef"
                    }
                }
            }
        }
        """.trimIndent()

        val root = json.parseToJsonElement(sampleJson)
        val profile = authRepository.parseUserProfileFromElement(root)

        assertNotNull(profile)
        assertEquals("Afsal Tech", profile!!.name)
        assertEquals("afsal@example.com", profile.email)
        assertEquals("https://lh3.googleusercontent.com/a/avatar123=s512", profile.avatarUrl)
        assertEquals("UC1234567890abcdef", profile.channelId)
        assertTrue(profile.isLoggedIn)
    }

    @Test
    fun testParseUserProfileFromGoogleAccountHeaderRenderer() {
        val sampleJson = """
        {
            "responseContext": {},
            "contents": {
                "googleAccountHeaderRenderer": {
                    "name": {"simpleText": "Music Lover"},
                    "email": {"simpleText": "lover@gmail.com"},
                    "avatar": {
                        "thumbnails": [{"url": "https://yt3.ggpht.com/photo.jpg"}]
                    }
                }
            }
        }
        """.trimIndent()

        val root = json.parseToJsonElement(sampleJson)
        val profile = authRepository.parseUserProfileFromElement(root)

        assertNotNull(profile)
        assertEquals("Music Lover", profile!!.name)
        assertEquals("lover@gmail.com", profile.email)
        assertEquals("https://yt3.ggpht.com/photo.jpg", profile.avatarUrl)
        assertTrue(profile.isLoggedIn)
    }

    @Test
    fun testParseResponsiveListItemsToSongs_MultiColumn() {
        val sampleJson = """
        {
            "contents": [
                {
                    "musicResponsiveListItemRenderer": {
                        "videoId": "dQw4w9WgXcQ",
                        "flexColumns": [
                            {
                                "musicResponsiveListItemFlexColumnRenderer": {
                                    "text": {
                                        "runs": [{"text": "Never Gonna Give You Up"}]
                                    }
                                }
                            },
                            {
                                "musicResponsiveListItemFlexColumnRenderer": {
                                    "text": {
                                        "runs": [{"text": "Rick Astley"}]
                                    }
                                }
                            },
                            {
                                "musicResponsiveListItemFlexColumnRenderer": {
                                    "text": {
                                        "runs": [{"text": "Whenever You Need Somebody"}]
                                    }
                                }
                            }
                        ],
                        "fixedColumns": [
                            {
                                "musicResponsiveListItemFixedColumnRenderer": {
                                    "text": {
                                        "runs": [{"text": "3:33"}]
                                    }
                                }
                            }
                        ],
                        "thumbnail": {
                            "musicThumbnailRenderer": {
                                "thumbnail": {
                                    "thumbnails": [
                                        {"url": "https://lh3.googleusercontent.com/sample_art=w120-h120"}
                                    ]
                                }
                            }
                        }
                    }
                }
            ]
        }
        """.trimIndent()

        val root = json.parseToJsonElement(sampleJson)
        val songs = youTubeMetadataProvider.parseResponsiveListItemsToSongs(root)

        assertEquals(1, songs.size)
        val song = songs[0]
        assertEquals("dQw4w9WgXcQ", song.id)
        assertEquals("Never Gonna Give You Up", song.title)
        assertEquals("Rick Astley", song.artist)
        assertEquals("Whenever You Need Somebody", song.album)
        assertEquals(213L, song.durationSeconds)
        assertTrue(song.highResArtworkUrl.contains("-l90-rj"))
    }

    @Test
    fun testParseResponsiveListItemsToSongs_SingleSubtitleBulletColumn() {
        val sampleJson = """
        {
            "musicResponsiveListItemRenderer": {
                "flexColumns": [
                    {
                        "musicResponsiveListItemFlexColumnRenderer": {
                            "text": {
                                "runs": [{"text": "Blinding Lights"}]
                            }
                        }
                    },
                    {
                        "musicResponsiveListItemFlexColumnRenderer": {
                            "text": {
                                "runs": [
                                    {"text": "Song"},
                                    {"text": " • "},
                                    {"text": "The Weeknd"},
                                    {"text": " • "},
                                    {"text": "After Hours"},
                                    {"text": " • "},
                                    {"text": "3:20"}
                                ]
                            }
                        }
                    }
                ],
                "overlay": {
                    "musicItemThumbnailOverlayRenderer": {
                        "content": {
                            "musicPlayButtonRenderer": {
                                "playNavigationEndpoint": {
                                    "watchEndpoint": {
                                        "videoId": "4NRXx6U8ABQ"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        """.trimIndent()

        val root = json.parseToJsonElement(sampleJson)
        val songs = youTubeMetadataProvider.parseResponsiveListItemsToSongs(root)

        assertEquals(1, songs.size)
        val song = songs[0]
        assertEquals("4NRXx6U8ABQ", song.id)
        assertEquals("Blinding Lights", song.title)
        assertEquals("The Weeknd", song.artist)
        assertEquals("After Hours", song.album)
        assertEquals(200L, song.durationSeconds)
    }

    @Test
    fun testParseTwoRowPlaylists() {
        val sampleJson = """
        {
            "sectionListRenderer": {
                "contents": [
                    {
                        "musicTwoRowItemRenderer": {
                            "title": {
                                "runs": [{"text": "Chill Beats"}]
                            },
                            "subtitle": {
                                "runs": [{"text": "Playlist • 45 songs"}]
                            },
                            "navigationEndpoint": {
                                "browseEndpoint": {
                                    "browseId": "VLPL1234567890"
                                }
                            },
                            "thumbnailRenderer": {
                                "musicThumbnailRenderer": {
                                    "thumbnail": {
                                        "thumbnails": [{"url": "https://i.ytimg.com/pl_art.jpg"}]
                                    }
                                }
                            }
                        }
                    }
                ]
            }
        }
        """.trimIndent()

        val root = json.parseToJsonElement(sampleJson)
        val playlists = youTubeMetadataProvider.parseTwoRowPlaylists(root)

        assertEquals(1, playlists.size)
        val pl = playlists[0]
        assertEquals("PL1234567890", pl.id)
        assertEquals("Chill Beats", pl.title)
        assertEquals(45, pl.songCount)
    }
}

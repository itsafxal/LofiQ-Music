package com.lofiq.data.repository

import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.common.LofiQLogger
import com.lofiq.core.database.dao.MetadataCacheDao
import com.lofiq.core.database.entities.CachedMetadataEntity
import com.lofiq.core.model.Song
import com.lofiq.data.model.ArtistCardData
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeFeedCacheManager @Inject constructor(
    private val metadataCacheDao: MetadataCacheDao,
    private val json: Json,
    private val dispatchers: DispatcherProvider
) {
    private val inMemoryShelves = ConcurrentHashMap<String, List<Song>>()
    private val inMemoryArtists = Collections.synchronizedList(mutableListOf<ArtistCardData>())
    @Volatile private var isLoadedFromDisk = false

    companion object {
        private const val PREFIX_SHELF = "home_feed_shelf_v2_"
        private const val KEY_ARTISTS = "home_feed_artists_v2"

        val PRIMARY_SHELF_KEYS = listOf(
            "dynamic_artist_0",
            "dynamic_artist_1",
            "dynamic_artist_2",
            "dynamic_artist_3",
            "dynamic_artist_4",
            "dynamic_seed_song",
            "heavy_rotation",
            "new_releases",
            "spotify_recommendations",
            "youtube_mix",
            "malayalam_trending",
            "malayalam_melodies",
            "malayalam_sushin",
            "malayalam_nostalgia",
            "malayalam_lofi",
            "malayalam_hesham",
            "malayalam_vineeth",
            "malayalam_party",
            "malayalam_vidyasagar",
            "malayalam_hiphop",
            "malayalam_gopi",
            "tamil_hits",
            "tamil_anirudh",
            "tamil_rahman",
            "tamil_romance",
            "tamil_kuthu",
            "tamil_yuvan",
            "tamil_harris",
            "tamil_sid",
            "tamil_santhosh",
            "tamil_ilayaraja",
            "trending_india",
            "bollywood_hits",
            "hindi_arijit",
            "hindi_melodies",
            "hindi_shreya",
            "hindi_pritam",
            "hindi_soul",
            "hindi_sonu",
            "hindi_diljit",
            "suggestions_for_you",
            "south_hits",
            "punjabi_hits",
            "romantic_melodies",
            "lofi_chill",
            "indie_india",
            "party_anthems",
            "retro_classics",
            "global_top",
            "workout_beats",
            "acoustic_peace",
            "devotional_peace"
        )
    }

    suspend fun loadFromDisk(): Pair<Map<String, List<Song>>, List<ArtistCardData>> = withContext(dispatchers.io) {
        if (isLoadedFromDisk && inMemoryShelves.isNotEmpty()) {
            return@withContext Pair(HashMap(inMemoryShelves), ArrayList(inMemoryArtists))
        }

        val loadedShelves = mutableMapOf<String, List<Song>>()
        for (key in PRIMARY_SHELF_KEYS) {
            try {
                val entity = metadataCacheDao.get(PREFIX_SHELF + key)
                if (entity != null && entity.jsonContent.isNotBlank()) {
                    val songs = json.decodeFromString<List<Song>>(entity.jsonContent)
                    if (songs.isNotEmpty()) {
                        loadedShelves[key] = songs
                        inMemoryShelves[key] = songs
                    }
                }
            } catch (e: Exception) {
                LofiQLogger.w(LofiQLogger.Category.CACHE, "Failed to decode cached shelf '$key': ${e.message}")
            }
        }

        val loadedArtists = mutableListOf<ArtistCardData>()
        try {
            val entity = metadataCacheDao.get(KEY_ARTISTS)
            if (entity != null && entity.jsonContent.isNotBlank()) {
                val artists = json.decodeFromString<List<ArtistCardData>>(entity.jsonContent)
                if (artists.isNotEmpty()) {
                    loadedArtists.addAll(artists)
                    synchronized(inMemoryArtists) {
                        inMemoryArtists.clear()
                        inMemoryArtists.addAll(artists)
                    }
                }
            }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.CACHE, "Failed to decode cached artists: ${e.message}")
        }

        isLoadedFromDisk = true
        LofiQLogger.i(
            LofiQLogger.Category.CACHE,
            "HomeFeedCacheManager: Restored ${loadedShelves.size} shelves and ${loadedArtists.size} artists from persistent cache"
        )
        Pair(loadedShelves, loadedArtists)
    }

    suspend fun saveShelf(key: String, songs: List<Song>) = withContext(dispatchers.io) {
        if (songs.isEmpty()) return@withContext
        inMemoryShelves[key] = songs
        try {
            val jsonContent = json.encodeToString(songs)
            metadataCacheDao.put(
                CachedMetadataEntity(
                    key = PREFIX_SHELF + key,
                    jsonContent = jsonContent,
                    cachedAt = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.CACHE, "Failed to persist shelf '$key': ${e.message}")
        }
    }

    suspend fun saveArtists(artists: List<ArtistCardData>) = withContext(dispatchers.io) {
        if (artists.isEmpty()) return@withContext
        synchronized(inMemoryArtists) {
            inMemoryArtists.clear()
            inMemoryArtists.addAll(artists)
        }
        try {
            val jsonContent = json.encodeToString(artists)
            metadataCacheDao.put(
                CachedMetadataEntity(
                    key = KEY_ARTISTS,
                    jsonContent = jsonContent,
                    cachedAt = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.CACHE, "Failed to persist artists: ${e.message}")
        }
    }

    fun getMemoryCachedShelves(): Map<String, List<Song>> = HashMap(inMemoryShelves)

    fun getMemoryCachedArtists(): List<ArtistCardData> = synchronized(inMemoryArtists) { ArrayList(inMemoryArtists) }

    fun hasMemoryCache(): Boolean = inMemoryShelves.isNotEmpty()
}

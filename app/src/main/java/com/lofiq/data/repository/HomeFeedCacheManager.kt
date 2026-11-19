package com.lofiq.data.repository

import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.common.LofiQLogger
import com.lofiq.core.database.dao.MetadataCacheDao
import com.lofiq.core.database.entities.CachedMetadataEntity
import com.lofiq.core.model.Song
import com.lofiq.data.model.ArtistCardData
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val dispatchers: DispatcherProvider,
    private val diagnosticsTracker: com.lofiq.data.sync.SyncDiagnosticsTracker? = null
) {
    private val inMemoryShelves = ConcurrentHashMap<String, List<Song>>()
    private val inMemoryArtists = Collections.synchronizedList(mutableListOf<ArtistCardData>())
    @Volatile private var isLoadedFromDisk = false
    private val loadMutex = Mutex()

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

        loadMutex.withLock {
            if (isLoadedFromDisk && inMemoryShelves.isNotEmpty()) {
                return@withContext Pair(HashMap(inMemoryShelves), ArrayList(inMemoryArtists))
            }

            val loadedShelves = mutableMapOf<String, List<Song>>()
            try {
                // High-performance single-query batch load of all cached shelves (<20ms)
                val entities = metadataCacheDao.getByPrefix(PREFIX_SHELF)
                val entityMap = entities.associateBy { it.key.removePrefix(PREFIX_SHELF) }

                for (key in PRIMARY_SHELF_KEYS) {
                    val entity = entityMap[key]
                    if (entity != null && entity.jsonContent.isNotBlank()) {
                        try {
                            val songs = json.decodeFromString<List<Song>>(entity.jsonContent)
                            if (songs.isNotEmpty()) {
                                loadedShelves[key] = songs
                                inMemoryShelves[key] = songs
                            }
                        } catch (e: Exception) {
                            LofiQLogger.w(LofiQLogger.Category.CACHE, "Failed to decode cached shelf '$key': ${e.message}")
                        }
                    }
                }

                // Also load any dynamic/custom shelves that were persisted under PREFIX_SHELF
                for ((shelfKey, entity) in entityMap) {
                    if (!loadedShelves.containsKey(shelfKey) && entity.jsonContent.isNotBlank()) {
                        try {
                            val songs = json.decodeFromString<List<Song>>(entity.jsonContent)
                            if (songs.isNotEmpty()) {
                                loadedShelves[shelfKey] = songs
                                inMemoryShelves[shelfKey] = songs
                            }
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                LofiQLogger.w(LofiQLogger.Category.CACHE, "Batch shelf load error: ${e.message}")
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
            if (loadedShelves.isNotEmpty()) {
                diagnosticsTracker?.recordCacheHit()
            } else {
                diagnosticsTracker?.recordCacheMiss()
            }
            LofiQLogger.i(
                LofiQLogger.Category.CACHE,
                "HomeFeedCacheManager: Restored ${loadedShelves.size} shelves and ${loadedArtists.size} artists from persistent cache in a single batch"
            )
            Pair(loadedShelves, loadedArtists)
        }
    }

    suspend fun saveShelvesBatch(shelves: Map<String, List<Song>>) = withContext(dispatchers.io) {
        if (shelves.isEmpty()) return@withContext
        inMemoryShelves.putAll(shelves)
        val entities = shelves.mapNotNull { (key, songs) ->
            if (songs.isEmpty()) return@mapNotNull null
            try {
                val jsonContent = json.encodeToString(songs)
                CachedMetadataEntity(
                    key = PREFIX_SHELF + key,
                    jsonContent = jsonContent,
                    cachedAt = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                LofiQLogger.w(LofiQLogger.Category.CACHE, "Failed to serialize shelf '$key': ${e.message}")
                null
            }
        }
        if (entities.isNotEmpty()) {
            try {
                metadataCacheDao.putAll(entities)
            } catch (e: Exception) {
                LofiQLogger.w(LofiQLogger.Category.CACHE, "Failed to batch persist ${entities.size} shelves: ${e.message}")
            }
        }
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

    suspend fun invalidateShelf(key: String) = withContext(dispatchers.io) {
        inMemoryShelves.remove(key)
        try {
            metadataCacheDao.delete(PREFIX_SHELF + key)
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.CACHE, "Failed to invalidate shelf '$key': ${e.message}")
        }
    }

    suspend fun clearAllCaches() = withContext(dispatchers.io) {
        inMemoryShelves.clear()
        synchronized(inMemoryArtists) {
            inMemoryArtists.clear()
        }
        try {
            metadataCacheDao.deleteByPrefix(PREFIX_SHELF)
            metadataCacheDao.delete(KEY_ARTISTS)
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.CACHE, "Failed to clear feed caches: ${e.message}")
        }
    }
}

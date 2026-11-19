package com.lofiq.data.repository

import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.database.dao.MetadataCacheDao
import com.lofiq.core.database.entities.CachedMetadataEntity
import com.lofiq.core.model.Song
import com.lofiq.data.sync.SyncDiagnosticsTracker
import io.mockk.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeFeedCacheManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private lateinit var metadataCacheDao: MetadataCacheDao
    private lateinit var diagnosticsTracker: SyncDiagnosticsTracker
    private lateinit var manager: HomeFeedCacheManager

    private val testSong = Song(
        id = "song_1",
        title = "Lofi Vibes",
        artist = "Chill Artist",
        album = "Quiet Night",
        durationSeconds = 180,
        artworkUrl = "https://example.com/art.jpg"
    )

    @Before
    fun setUp() {
        metadataCacheDao = mockk(relaxed = true)
        diagnosticsTracker = mockk(relaxed = true)
        manager = HomeFeedCacheManager(
            metadataCacheDao = metadataCacheDao,
            json = json,
            dispatchers = testDispatcherProvider,
            diagnosticsTracker = diagnosticsTracker
        )
    }

    @Test
    fun `loadFromDisk loads all shelves via getByPrefix in a single batch`() = runTest(testDispatcher) {
        val shelfJson = json.encodeToString(listOf(testSong))
        val cachedEntity = CachedMetadataEntity(
            key = "home_feed_shelf_v2_trending_india",
            jsonContent = shelfJson,
            cachedAt = System.currentTimeMillis()
        )

        coEvery { metadataCacheDao.getByPrefix("home_feed_shelf_v2_") } returns listOf(cachedEntity)
        coEvery { metadataCacheDao.get("home_feed_artists_v2") } returns null

        val (shelves, _) = manager.loadFromDisk()

        coVerify(exactly = 1) { metadataCacheDao.getByPrefix("home_feed_shelf_v2_") }
        assertTrue(shelves.containsKey("trending_india"))
        assertEquals(1, shelves["trending_india"]?.size)
        assertEquals("Lofi Vibes", shelves["trending_india"]?.first()?.title)
        verify { diagnosticsTracker.recordCacheHit() }
    }

    @Test
    fun `loadFromDisk coalesces concurrent loads through mutex without duplicate DB reads`() = runTest(testDispatcher) {
        val shelfJson = json.encodeToString(listOf(testSong))
        val cachedEntity = CachedMetadataEntity(
            key = "home_feed_shelf_v2_trending_india",
            jsonContent = shelfJson,
            cachedAt = System.currentTimeMillis()
        )

        coEvery { metadataCacheDao.getByPrefix("home_feed_shelf_v2_") } returns listOf(cachedEntity)
        coEvery { metadataCacheDao.get("home_feed_artists_v2") } returns null

        // Trigger two concurrent loads
        val deferred1 = async { manager.loadFromDisk() }
        val deferred2 = async { manager.loadFromDisk() }

        val result1 = deferred1.await()
        val result2 = deferred2.await()

        // DB batch query should only be executed once due to loadMutex and isLoadedFromDisk flag
        coVerify(exactly = 1) { metadataCacheDao.getByPrefix("home_feed_shelf_v2_") }
        assertEquals(result1.first.size, result2.first.size)
    }

    @Test
    fun `memory cache returns instantly and hasMemoryCache reflects state`() = runTest(testDispatcher) {
        assertFalse(manager.hasMemoryCache())

        manager.saveShelf("chill_shelf", listOf(testSong))

        assertTrue(manager.hasMemoryCache())
        val memShelves = manager.getMemoryCachedShelves()
        assertEquals(1, memShelves.size)
        assertEquals("chill_shelf", memShelves.keys.first())
    }

    @Test
    fun `invalidateShelf removes key from memory and database`() = runTest(testDispatcher) {
        manager.saveShelf("shelf_to_remove", listOf(testSong))
        assertTrue(manager.getMemoryCachedShelves().containsKey("shelf_to_remove"))

        manager.invalidateShelf("shelf_to_remove")

        assertFalse(manager.getMemoryCachedShelves().containsKey("shelf_to_remove"))
        coVerify { metadataCacheDao.delete("home_feed_shelf_v2_shelf_to_remove") }
    }

    @Test
    fun `clearAllCaches clears all in-memory and disk caches`() = runTest(testDispatcher) {
        manager.saveShelf("shelf_1", listOf(testSong))
        manager.clearAllCaches()

        assertFalse(manager.hasMemoryCache())
        coVerify { metadataCacheDao.deleteByPrefix("home_feed_shelf_v2_") }
        coVerify { metadataCacheDao.delete("home_feed_artists_v2") }
    }
}

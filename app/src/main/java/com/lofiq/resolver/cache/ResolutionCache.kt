package com.lofiq.resolver.cache

import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.common.LofiQLogger
import com.lofiq.core.database.dao.MetadataCacheDao
import com.lofiq.core.database.entities.CachedMetadataEntity
import com.lofiq.resolver.core.ResolutionResult
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResolutionCache @Inject constructor(
    private val metadataCacheDao: MetadataCacheDao,
    private val json: Json,
    private val dispatchers: DispatcherProvider
) {
    // Pure thread-safe LRU memory cache (capacity 100)
    private val memoryCache: MutableMap<String, ResolutionResult> = Collections.synchronizedMap(
        object : LinkedHashMap<String, ResolutionResult>(100, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ResolutionResult>?): Boolean {
                return size > 100
            }
        }
    )

    companion object {
        private const val CACHE_PREFIX = "res_v4_"
    }

    suspend fun get(cacheKey: String): ResolutionResult? = withContext(dispatchers.io) {
        // 1. Check memory cache first
        val memoryResult = memoryCache[cacheKey]
        if (memoryResult != null) {
            if (isValidCachedResult(memoryResult)) {
                LofiQLogger.d(LofiQLogger.Category.CACHE, "Resolution memory cache hit for: $cacheKey")
                return@withContext memoryResult
            } else {
                memoryCache.remove(cacheKey)
            }
        }

        // 2. Check persistent DB cache with versioned prefix
        val dbKey = "$CACHE_PREFIX$cacheKey"
        try {
            val cachedEntity = metadataCacheDao.get(dbKey)
            if (cachedEntity != null) {
                val now = System.currentTimeMillis()
                if (cachedEntity.expiresAt == 0L || cachedEntity.expiresAt > now) {
                    val result = json.decodeFromString<ResolutionResult>(cachedEntity.jsonContent)
                    if (isValidCachedResult(result)) {
                        memoryCache[cacheKey] = result
                        LofiQLogger.d(LofiQLogger.Category.CACHE, "Resolution disk cache hit for: $cacheKey")
                        return@withContext result
                    } else {
                        metadataCacheDao.delete(dbKey)
                    }
                } else {
                    metadataCacheDao.delete(dbKey)
                }
            }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.CACHE, "Failed to read cached resolution: ${e.message}")
        }
        null
    }

    private fun isValidCachedResult(result: ResolutionResult): Boolean {
        if (result.isExpired) return false
        if (result.streamUrl.isBlank()) return false
        if (result.mimeType.isBlank() || (!result.mimeType.startsWith("audio/") && result.mimeType != "video/mp4")) return false
        if (result.bitrateKbps <= 0) return false
        return true
    }

    suspend fun put(cacheKey: String, result: ResolutionResult) = withContext(dispatchers.io) {
        if (!result.isCacheable) return@withContext

        // Put in memory cache
        memoryCache[cacheKey] = result

        // Put in persistent DB with versioned prefix
        val dbKey = "$CACHE_PREFIX$cacheKey"
        try {
            val jsonStr = json.encodeToString(result)
            metadataCacheDao.put(
                CachedMetadataEntity(
                    key = dbKey,
                    jsonContent = jsonStr,
                    cachedAt = System.currentTimeMillis(),
                    expiresAt = result.expiryEpochMs
                )
            )
            LofiQLogger.d(LofiQLogger.Category.CACHE, "Resolution cached for: $cacheKey")
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.CACHE, "Failed to persist resolution cache: ${e.message}")
        }
    }

    suspend fun invalidate(cacheKey: String) = withContext(dispatchers.io) {
        memoryCache.remove(cacheKey)
        val dbKey = "$CACHE_PREFIX$cacheKey"
        try {
            metadataCacheDao.delete(dbKey)
            metadataCacheDao.delete("resolution_$cacheKey")
            LofiQLogger.d(LofiQLogger.Category.CACHE, "Resolution invalidated for: $cacheKey")
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.CACHE, "Failed to delete cached resolution: ${e.message}")
        }
    }

    suspend fun clear() = withContext(dispatchers.io) {
        memoryCache.clear()
        metadataCacheDao.deleteExpired(Long.MAX_VALUE)
    }
}


package com.lofiq.resolver.providers

import com.lofiq.core.model.Song
import com.lofiq.resolver.core.ResolveRequest
import com.lofiq.resolver.core.ResolutionResult

data class ProviderCapabilities(
    val supportsSearch: Boolean = true,
    val supportsTrending: Boolean = true,
    val supportedBitrates: List<Int> = listOf(96, 160, 320),
    val requiresHeaders: Boolean = false
)

interface ProviderAdapter {
    val providerId: String
    val displayName: String
    val capabilities: ProviderCapabilities

    suspend fun canHandle(request: ResolveRequest): Boolean
    suspend fun resolve(request: ResolveRequest): ResolutionResult
    suspend fun search(query: String, page: Int = 1, limit: Int = 20): List<Song>
    suspend fun getSongDetails(songId: String): Song?
    suspend fun getTrending(page: Int = 1, limit: Int = 20): List<Song>
}

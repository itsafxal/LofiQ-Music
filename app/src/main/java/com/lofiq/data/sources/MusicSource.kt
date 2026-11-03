package com.lofiq.data.sources

interface MusicSource {
    val id: String
    val name: String
    val description: String
    val defaultEndpoint: String
    val capabilities: Set<SourceCapability>

    suspend fun healthCheck(): SourceHealthStatus
}

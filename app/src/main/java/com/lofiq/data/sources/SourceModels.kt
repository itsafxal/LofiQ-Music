package com.lofiq.data.sources

import kotlinx.serialization.Serializable

@Serializable
enum class SourceCapability {
    STREAMING_320,
    SEARCH,
    METADATA,
    LYRICS,
    CHARTS,
    RADIO,
    OFFLINE_STORAGE
}

@Serializable
enum class HealthState {
    ONLINE,
    DEGRADED,
    OFFLINE,
    CHECKING
}

@Serializable
data class SourceHealthStatus(
    val state: HealthState = HealthState.ONLINE,
    val latencyMs: Long = 0L,
    val lastCheckedTimestampMs: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
)

@Serializable
data class SourceConfig(
    val id: String,
    val name: String,
    val isEnabled: Boolean = true,
    val priority: Int = 0,
    val customEndpoint: String? = null
)

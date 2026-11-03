package com.lofiq.resolver.core

import com.lofiq.core.model.AudioQuality
import kotlinx.serialization.Serializable

enum class ResolverOutcome {
    SUCCESS,
    TRACK_NOT_FOUND,
    UNSUPPORTED_SERVICE,
    EXTRACTION_FAILED,
    NETWORK_TIMEOUT,
    RATE_LIMITED,
    AUTHENTICATION_REQUIRED,
    STREAM_EXPIRED,
    STREAM_VALIDATION_FAILED,
    UNSUPPORTED_AUDIO_FORMAT,
    MEDIA3_PREPARATION_FAILED,
    MEDIA3_PLAYBACK_FAILED,
    SOURCE_UNAVAILABLE,
    TIMEOUT,
    NETWORK_ERROR,
    EXTRACTION_ERROR,
    INVALID_SOURCE
}

enum class ResolverState {
    REQUESTED,
    CACHE_CHECK,
    PRIMARY_PROVIDER_RESOLUTION,
    STREAM_VALIDATION,
    PLAYBACK_PREPARATION,
    PLAYBACK_CONFIRMED,
    FAILED
}

@Serializable
data class ProviderAttemptSummary(
    val providerId: String,
    val outcome: ResolverOutcome,
    val reason: String,
    val latencyMs: Long
)

@Serializable
data class ResolveRequest(
    val songId: String,
    val songTitle: String,
    val artistName: String,
    val albumName: String = "",
    val durationSeconds: Long = 0L,
    val preferredQuality: AudioQuality = AudioQuality.HIGH,
    val providerHint: String? = null,
    val allowFallback: Boolean = true,
    val timeoutMs: Long = 15_000L,
    val requestId: String? = null
)

@Serializable
data class ResolutionResult(
    val songId: String,
    val streamUrl: String,
    val mimeType: String = "audio/mp4",
    val durationMs: Long = 0L,
    val bitrateKbps: Int = 320,
    val quality: AudioQuality = AudioQuality.HIGH,
    val providerId: String,
    val codec: String = "aac",
    val sampleRate: Int = 44100,
    val channels: Int = 2,
    val expiryEpochMs: Long = 0L, // 0 = permanent / not expired
    val headers: Map<String, String> = emptyMap(),
    val isCacheable: Boolean = true,
    val requestId: String = ""
) {
    val isExpired: Boolean
        get() = expiryEpochMs in 1..System.currentTimeMillis()
}

/**
 * Resolved audio source ready for Media3 / ExoPlayer consumption.
 */
typealias ResolvedAudioSource = ResolutionResult

sealed class ResolverException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class ProviderNotFound(val providerId: String) : ResolverException("No provider found for id: $providerId")
    class SourceUnavailable(val songId: String, val reason: String = "Audio stream not found") : ResolverException("Source unavailable for song $songId: $reason")
    class AuthRequired(val songId: String, val providerId: String, val reason: String = "Authentication required") : ResolverException("Auth required for song $songId on $providerId: $reason")
    class RateLimited(val songId: String, val providerId: String, val retryAfterMs: Long = 0L) : ResolverException("Rate limited for song $songId on $providerId")
    class NetworkError(message: String, cause: Throwable? = null) : ResolverException("Network error during resolution: $message", cause)
    class Timeout(val timeoutMs: Long) : ResolverException("Resolution timed out after ${timeoutMs}ms")
    class ExtractionError(val songId: String, val reason: String) : ResolverException("Extraction error for song $songId: $reason")
    class InvalidSource(val songId: String, val reason: String) : ResolverException("Invalid stream source for song $songId: $reason")
    class AllProvidersFailed(val songId: String, val attempts: List<ProviderAttemptSummary> = emptyList()) : ResolverException("All audio providers failed to resolve stream for: $songId (attempts: ${attempts.size})")
}

interface Resolver {
    suspend fun resolve(request: ResolveRequest): ResolutionResult
}


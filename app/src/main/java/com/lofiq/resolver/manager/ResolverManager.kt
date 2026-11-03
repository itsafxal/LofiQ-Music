package com.lofiq.resolver.manager

import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.common.LofiQLogger
import com.lofiq.resolver.cache.ResolutionCache
import com.lofiq.resolver.core.*
import com.lofiq.resolver.providers.ProviderAdapter
import kotlinx.coroutines.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResolverManager @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards ProviderAdapter>,
    private val cache: ResolutionCache,
    private val streamValidator: StreamValidator,
    private val dispatchers: DispatcherProvider
) : Resolver {

    // Request deduplication map to prevent simultaneous duplicate resolution network calls
    private val inFlightResolutions = ConcurrentHashMap<String, Deferred<ResolutionResult>>()
    private val managerScope = CoroutineScope(SupervisorJob() + dispatchers.io)

    override suspend fun resolve(request: ResolveRequest): ResolutionResult = withContext(dispatchers.io) {
        val reqId = request.requestId ?: UUID.randomUUID().toString().take(8)
        val cacheKey = "${request.songId}_${request.preferredQuality.name}"
        val startTime = System.currentTimeMillis()

        // 1. Check Resolution Cache (Memory + DB)
        val cached = cache.get(cacheKey)
        if (cached != null) {
            val duration = System.currentTimeMillis() - startTime
            LofiQLogger.i(
                LofiQLogger.Category.RESOLVER,
                "[RESOLVER][reqId=$reqId] CACHE_HIT songId=${request.songId} provider=${cached.providerId} duration=${duration}ms bitrate=${cached.bitrateKbps}kbps mime=${cached.mimeType}"
            )
            return@withContext cached.copy(requestId = reqId)
        }

        // 2. In-flight Request Deduplication
        val deferred = inFlightResolutions.compute(cacheKey) { _, existing ->
            if (existing != null && existing.isActive) {
                LofiQLogger.d(
                    LofiQLogger.Category.RESOLVER,
                    "[RESOLVER][reqId=$reqId] IN_FLIGHT_REUSE for songId=${request.songId}"
                )
                existing
            } else {
                managerScope.async {
                    executeResolutionPipeline(request.copy(requestId = reqId), cacheKey)
                }
            }
        }!!

        try {
            deferred.await()
        } finally {
            inFlightResolutions.remove(cacheKey, deferred)
        }
    }

    private suspend fun executeResolutionPipeline(
        request: ResolveRequest,
        cacheKey: String
    ): ResolutionResult {
        val reqId = request.requestId ?: UUID.randomUUID().toString().take(8)
        val pipelineStart = System.currentTimeMillis()

        LofiQLogger.i(
            LofiQLogger.Category.RESOLVER,
            "[RESOLVER][reqId=$reqId] STATE=REQUESTED songId=${request.songId} title='${request.songTitle}' artist='${request.artistName}' quality=${request.preferredQuality}"
        )

        return withTimeoutOrNull(request.timeoutMs) {
            val isYouTubeId = request.songId.length == 11 && !request.songId.contains(" ") && !request.songId.all { it.isDigit() }
            val isJioSaavnId = request.providerHint == "jiosaavn" || (request.songId.all { it.isDigit() } && request.songId.length >= 6)

            // Strict Priority Order:
            // Priority 1: JioSaavn (320kbps Direct High Fidelity)
            // Priority 2: Custom InnerTube (Instant 600ms Authenticated YouTube extraction)
            // Priority 3: Piped Mirror / Direct HTTP Provider
            val eligibleProviders = providers
                .filter { it.canHandle(request) }
                .sortedWith(
                    compareBy { provider ->
                        when {
                            request.providerHint != null && provider.providerId == request.providerHint -> 0
                            request.providerHint != null && provider.providerId != request.providerHint -> 10
                            // Priority 0: JioSaavn 320kbps Direct High Fidelity (Zero bot restrictions, sub-second resolution)
                            request.songTitle.isNotBlank() && provider.providerId == "jiosaavn" -> 1
                            isJioSaavnId && provider.providerId == "jiosaavn" -> 1
                            provider.providerId == "youtube" -> 2
                            provider.providerId == "piped" -> 3
                            provider.providerId == "direct_http" -> 4
                            else -> 5
                        }
                    }
                )

            if (eligibleProviders.isEmpty()) {
                val error = ResolverException.ProviderNotFound(request.providerHint ?: "any")
                LofiQLogger.w(LofiQLogger.Category.RESOLVER, "[RESOLVER][reqId=$reqId] STATE=FAILED reason=NO_ELIGIBLE_PROVIDERS for ${request.songId}")
                throw error
            }

            val attemptSummaries = mutableListOf<ProviderAttemptSummary>()

            for (provider in eligibleProviders) {
                val providerStart = System.currentTimeMillis()
                var retryCount = 0
                val maxRetries = 1 // Single retry bounded for transient network glitched connections

                while (retryCount <= maxRetries) {
                    try {
                        val currentRemaining = request.timeoutMs - (System.currentTimeMillis() - pipelineStart)
                        val providerTimeout = when (provider.providerId) {
                            "youtube" -> maxOf(8_000L, minOf(12_000L, currentRemaining))
                            else -> minOf(4_500L, maxOf(2_500L, currentRemaining))
                        }

                        LofiQLogger.i(
                            LofiQLogger.Category.RESOLVER,
                            "[RESOLVER][reqId=$reqId] STATE=PRIMARY_PROVIDER_RESOLUTION provider=${provider.providerId} (${provider.displayName}) try=${retryCount + 1}"
                        )

                        val result = withTimeoutOrNull(providerTimeout) {
                            provider.resolve(request)
                        } ?: throw ResolverException.Timeout(providerTimeout)

                        // Stream Validation
                        LofiQLogger.i(
                            LofiQLogger.Category.RESOLVER,
                            "[RESOLVER][reqId=$reqId] STATE=STREAM_VALIDATION provider=${provider.providerId} url=${result.streamUrl.take(60)}..."
                        )

                        val isValid = streamValidator.validateStream(result.streamUrl, result.headers)
                        if (!isValid) {
                            LofiQLogger.w(
                                LofiQLogger.Category.RESOLVER,
                                "[RESOLVER][reqId=$reqId] STREAM_VALIDATION_FAILED provider=${provider.providerId} url=${result.streamUrl.take(60)}..."
                            )
                            attemptSummaries.add(
                                ProviderAttemptSummary(
                                    providerId = provider.providerId,
                                    outcome = ResolverOutcome.STREAM_VALIDATION_FAILED,
                                    reason = "Validation probe rejected stream URL",
                                    latencyMs = System.currentTimeMillis() - providerStart
                                )
                            )
                            break // Fall back to next eligible provider in priority chain
                        }

                        // Store in ResolutionCache and return verified result
                        val finalResult = result.copy(requestId = reqId)
                        cache.put(cacheKey, finalResult)

                        val totalDuration = System.currentTimeMillis() - pipelineStart
                        LofiQLogger.i(
                            LofiQLogger.Category.RESOLVER,
                            "[RESOLVER][reqId=$reqId] STATE=PLAYBACK_CONFIRMED songId=${finalResult.songId} provider=${finalResult.providerId} duration=${totalDuration}ms bitrate=${finalResult.bitrateKbps}kbps mime=${finalResult.mimeType}"
                        )
                        return@withTimeoutOrNull finalResult

                    } catch (e: Exception) {
                        val duration = System.currentTimeMillis() - providerStart
                        val outcome = when (e) {
                            is ResolverException.AuthRequired -> ResolverOutcome.AUTHENTICATION_REQUIRED
                            is ResolverException.RateLimited -> ResolverOutcome.RATE_LIMITED
                            is ResolverException.Timeout -> ResolverOutcome.NETWORK_TIMEOUT
                            is ResolverException.NetworkError -> ResolverOutcome.NETWORK_ERROR
                            is ResolverException.ExtractionError -> ResolverOutcome.EXTRACTION_FAILED
                            is ResolverException.SourceUnavailable -> ResolverOutcome.SOURCE_UNAVAILABLE
                            else -> ResolverOutcome.SOURCE_UNAVAILABLE
                        }

                        val reason = e.message ?: e.javaClass.simpleName
                        attemptSummaries.add(
                            ProviderAttemptSummary(
                                providerId = provider.providerId,
                                outcome = outcome,
                                reason = reason,
                                latencyMs = duration
                            )
                        )

                        LofiQLogger.w(
                            LofiQLogger.Category.RESOLVER,
                            "[RESOLVER][reqId=$reqId] PROVIDER_FAILED provider=${provider.providerId} outcome=$outcome reason=$reason duration=${duration}ms"
                        )

                        // Retry only on transient network glitches
                        if ((outcome == ResolverOutcome.NETWORK_ERROR || outcome == ResolverOutcome.NETWORK_TIMEOUT) && retryCount < maxRetries) {
                            retryCount++
                            delay(200)
                            continue
                        }

                        // Non-recoverable or retry exhausted -> advance to next provider
                        break
                    }
                }

                if (!request.allowFallback) {
                    break
                }
            }

            val totalDuration = System.currentTimeMillis() - pipelineStart
            LofiQLogger.e(
                LofiQLogger.Category.RESOLVER,
                "[RESOLVER][reqId=$reqId] STATE=FAILED ALL_PROVIDERS_FAILED songId=${request.songId} attempts=${attemptSummaries.size} duration=${totalDuration}ms"
            )
            throw ResolverException.AllProvidersFailed(request.songId, attemptSummaries)
        } ?: throw ResolverException.Timeout(request.timeoutMs)
    }
}


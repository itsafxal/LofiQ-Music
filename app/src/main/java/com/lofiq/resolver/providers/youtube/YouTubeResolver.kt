package com.lofiq.resolver.providers.youtube

import com.lofiq.core.common.LofiQLogger
import com.lofiq.core.model.Song
import com.lofiq.resolver.core.ResolveRequest
import com.lofiq.resolver.core.ResolutionResult
import com.lofiq.resolver.core.ResolverException
import com.lofiq.resolver.core.TrackIdentityMatcher
import com.lofiq.resolver.providers.ProviderAdapter
import com.lofiq.resolver.providers.ProviderCapabilities
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeResolver private constructor(
    private val metadataProvider: YouTubeMetadataProvider,
    private val streamExtractor: YouTubeStreamExtractor,
    private val streamValidator: com.lofiq.resolver.core.StreamValidator?,
    @Suppress("UNUSED_PARAMETER") marker: Unit
) : ProviderAdapter {

    @Inject
    constructor(
        metadataProvider: YouTubeMetadataProvider,
        streamExtractor: YouTubeStreamExtractor,
        streamValidator: com.lofiq.resolver.core.StreamValidator
    ) : this(
        metadataProvider = metadataProvider,
        streamExtractor = streamExtractor,
        streamValidator = streamValidator,
        marker = Unit
    )

    constructor(
        metadataProvider: YouTubeMetadataProvider,
        streamExtractor: YouTubeStreamExtractor
    ) : this(
        metadataProvider = metadataProvider,
        streamExtractor = streamExtractor,
        streamValidator = null,
        marker = Unit
    )

    override val providerId: String = "youtube"
    override val displayName: String = "LofiQ Embedded YouTube Resolver"

    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsSearch = true,
        supportsTrending = true,
        supportedBitrates = listOf(50, 70, 128, 160),
        requiresHeaders = false
    )

    override suspend fun canHandle(request: ResolveRequest): Boolean {
        return request.providerHint == null || request.providerHint == providerId || request.providerHint == "spotify" || request.songTitle.isNotBlank()
    }

    override suspend fun resolve(request: ResolveRequest): ResolutionResult {
        LofiQLogger.i(LofiQLogger.Category.RESOLVER, "YouTubeResolver: resolving request for '${request.songTitle}' [id: ${request.songId}]")

        val candidateIds = mutableListOf<String>()

        // Check if songId is already a valid 11-char YouTube video ID
        val isDirectVideoId = request.songId.length == 11 && !request.songId.contains(" ")
        if (isDirectVideoId) {
            candidateIds.add(request.songId)
        }

        // Search candidates by title and artist if needed
        val query = "${request.songTitle} ${request.artistName}".trim()
        if (candidateIds.isEmpty() && query.isNotBlank()) {
            searchAndAddCandidates(query, request, candidateIds)
        }
        if (candidateIds.isEmpty() && query.isNotBlank()) {
            val fallbackQuery = "$query official audio".trim()
            searchAndAddCandidates(fallbackQuery, request, candidateIds)
        }

        if (candidateIds.isEmpty()) {
            throw ResolverException.SourceUnavailable(
                request.songId,
                "No YouTube audio match found matching identity for: '${request.songTitle}' by '${request.artistName}'"
            )
        }

        var lastException: Exception? = null
        var index = 0
        while (index < candidateIds.size && index < 3) {
            val candidateId = candidateIds[index]
            try {
                val updatedRequest = request.copy(songId = candidateId)
                val result = kotlinx.coroutines.withTimeoutOrNull(7500L) {
                    streamExtractor.extractAudioStream(updatedRequest)
                } ?: throw ResolverException.Timeout(7500L)

                // Verify candidate stream reaches valid playable audio before returning
                if (streamValidator != null) {
                    val isValid = streamValidator.validateStream(result.streamUrl, result.headers)
                    if (!isValid) {
                        throw ResolverException.ExtractionError(
                            candidateId,
                            "Stream validation probe rejected candidate $candidateId"
                        )
                    }
                }

                LofiQLogger.i(LofiQLogger.Category.RESOLVER, "YouTubeResolver successfully resolved candidate $candidateId for '${request.songTitle}'")
                return result
            } catch (e: Exception) {
                lastException = e
                LofiQLogger.w(LofiQLogger.Category.RESOLVER, "Candidate $candidateId failed: ${e.message}, trying next candidate...")

                // If direct video ID candidate failed and no fallback candidates were fetched yet, search now
                if (candidateIds.size <= 1 && query.isNotBlank()) {
                    searchAndAddCandidates(query, request, candidateIds)
                    if (candidateIds.size <= 1) {
                        searchAndAddCandidates("$query official audio", request, candidateIds)
                    }
                }
            }
            index++
        }

        throw lastException ?: ResolverException.SourceUnavailable(
            request.songId,
            "All candidate streams failed for '${request.songTitle}'"
        )
    }

    private suspend fun searchAndAddCandidates(
        query: String,
        request: ResolveRequest,
        candidateIds: MutableList<String>
    ) {
        if (query.isBlank()) return
        val searchResults = try {
            metadataProvider.searchSongs(query, limit = 10)
        } catch (_: Exception) {
            emptyList()
        }

        val userWantsKaraoke = request.songTitle.contains("karaoke", ignoreCase = true) ||
                request.songTitle.contains("instrumental", ignoreCase = true)

        // Strictly validate candidate identity against the requested track
        val validCandidates = searchResults.filter { candidate ->
            userWantsKaraoke || !TrackIdentityMatcher.isKaraokeOrIncompatibleTrack(candidate.title, candidate.artist)
        }.mapNotNull { candidate ->
            val eval = com.lofiq.resolver.core.TrackIdentityMatcher.evaluate(
                requestedTitle = request.songTitle,
                requestedArtist = request.artistName,
                requestedDurationSec = request.durationSeconds,
                candidate = com.lofiq.resolver.core.TrackIdentityMatcher.CandidateTrack(
                    providerId = providerId,
                    providerTrackId = candidate.id,
                    title = candidate.title,
                    artist = candidate.artist,
                    album = candidate.album,
                    durationSeconds = candidate.durationSeconds,
                    channelName = candidate.artist
                ),
                requestedAlbum = request.albumName
            )
            if (eval.isAccepted) {
                Pair(candidate.id, eval.matchScore)
            } else {
                LofiQLogger.d(
                    LofiQLogger.Category.RESOLVER,
                    "YouTubeResolver: rejected candidate '${candidate.title}' by '${candidate.artist}' (${eval.reason})"
                )
                null
            }
        }.sortedByDescending { it.second }

        validCandidates.forEach { (candId, _) ->
            if (!candidateIds.contains(candId)) {
                candidateIds.add(candId)
            }
        }
    }

    override suspend fun search(query: String, page: Int, limit: Int): List<Song> {
        return metadataProvider.searchSongs(query, limit)
    }

    override suspend fun getSongDetails(songId: String): Song? {
        // Construct Song metadata from YouTube videoId
        return Song(
            id = songId,
            title = "YouTube Track",
            artist = "YouTube Music",
            artworkUrl = "https://i.ytimg.com/vi/$songId/hqdefault.jpg",
            highResArtworkUrl = "https://i.ytimg.com/vi/$songId/hqdefault.jpg",
            providerId = providerId
        )
    }

    override suspend fun getTrending(page: Int, limit: Int): List<Song> {
        return metadataProvider.getTrendingSongs(limit)
    }
}

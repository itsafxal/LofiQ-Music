package com.lofiq.resolver.providers

import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.common.LofiQLogger
import com.lofiq.core.model.AudioQuality
import com.lofiq.core.model.Song
import com.lofiq.data.jiosaavn.JioSaavnClient
import com.lofiq.data.jiosaavn.JioSaavnSong
import com.lofiq.resolver.core.ResolveRequest
import com.lofiq.resolver.core.ResolutionResult
import com.lofiq.resolver.core.ResolverException
import com.lofiq.resolver.core.TrackIdentityMatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JioSaavnProviderAdapter @Inject constructor(
    private val jioSaavnClient: JioSaavnClient,
    private val dispatchers: DispatcherProvider
) : ProviderAdapter {

    override val providerId: String = "jiosaavn"
    override val displayName: String = "JioSaavn (320kbps)"
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsSearch = true,
        supportsTrending = true,
        supportedBitrates = listOf(96, 160, 320),
        requiresHeaders = false
    )

    override suspend fun canHandle(request: ResolveRequest): Boolean {
        if (request.providerHint != null && request.providerHint != providerId) {
            return false
        }
        return request.providerHint == "jiosaavn" || request.artistName.isNotBlank() || request.songTitle.isNotBlank()
    }

    override suspend fun resolve(request: ResolveRequest): ResolutionResult = withContext(dispatchers.io) {
        val startTime = System.currentTimeMillis()
        val reqId = request.requestId ?: ""

        val userWantsKaraoke = request.songTitle.contains("karaoke", ignoreCase = true) ||
                request.songTitle.contains("instrumental", ignoreCase = true)

        // 1. Try resolving directly if it's already a JioSaavn song ID
        if (request.providerHint == "jiosaavn" || (request.songId.all { it.isDigit() } && request.songId.length >= 6)) {
            val details = jioSaavnClient.getSongDetails(request.songId)
            if (details != null) {
                val isKaraoke = TrackIdentityMatcher.isKaraokeOrIncompatibleTrack(details.title, details.artist)
                if (!isKaraoke || userWantsKaraoke) {
                    val streamUrl = jioSaavnClient.resolveStreamUrl(details, request.preferredQuality)
                    if (!streamUrl.isNullOrBlank()) {
                        return@withContext ResolutionResult(
                            songId = request.songId,
                            streamUrl = streamUrl,
                            bitrateKbps = when (request.preferredQuality) {
                                AudioQuality.HIGH, AudioQuality.AUTO -> 320
                                AudioQuality.STANDARD -> 160
                                AudioQuality.DATA_SAVER -> 96
                            },
                            quality = request.preferredQuality,
                            mimeType = "audio/mp4",
                            providerId = providerId,
                            expiryEpochMs = System.currentTimeMillis() + 86400000L,
                            requestId = reqId
                        )
                    }
                }
            }
        }

        // 2. Search by cleaned title & artist for high-fidelity 320kbps stream match
        val cleanArtist = request.artistName.replace(
            Regex("(?i)youtube music artist|unknown|various artists|official video|official audio|t-series|sony music south|sony music|aditya music|lahari music|zee music company|zee music|yrf|saregama|speed records|eros now|tips official"),
            ""
        ).replace(Regex("""\d{1,2}:\d{2}(:\d{2})?$"""), "").trim()
        val cleanTitle = cleanSongTitle(request.songTitle)

        // Priority order: Title + Artist > Title + Album > Artist + Title > Clean Title
        val queries = listOf(
            if (cleanArtist.isNotBlank()) "$cleanTitle $cleanArtist".trim() else "",
            if (request.albumName.isNotBlank()) "$cleanTitle ${request.albumName}".trim() else "",
            if (cleanArtist.isNotBlank()) "$cleanArtist $cleanTitle".trim() else "",
            cleanTitle,
            request.songTitle
        ).filter { it.isNotBlank() }.distinct()

        for (query in queries) {
            val results = try {
                jioSaavnClient.searchSongs(query, limit = 5)
            } catch (e: Exception) {
                emptyList()
            }

            // Filter out karaoke/incompatible tracks if user requested regular song
            val candidatePool = if (userWantsKaraoke) results else results.filter {
                !TrackIdentityMatcher.isKaraokeOrIncompatibleTrack(it.title, it.artist)
            }

            // Evaluate and prioritize candidates by matching rules and score
            val evaluatedCandidates = candidatePool.map { candidate ->
                val eval = TrackIdentityMatcher.evaluate(
                    requestedTitle = request.songTitle,
                    requestedArtist = request.artistName,
                    requestedDurationSec = request.durationSeconds,
                    candidate = TrackIdentityMatcher.CandidateTrack(
                        providerId = providerId,
                        providerTrackId = candidate.id,
                        title = candidate.title,
                        artist = candidate.artist,
                        album = candidate.album,
                        durationSeconds = candidate.durationSeconds,
                        channelName = candidate.label.ifBlank { "jiosaavn" },
                        language = candidate.language
                    ),
                    requestedAlbum = request.albumName
                )
                Triple(candidate, eval, eval.matchScore)
            }

            val validCandidates = evaluatedCandidates
                .filter { (candidate, eval, _) ->
                    if (!eval.isAccepted || !passesLanguageCheck(request, candidate)) {
                        LofiQLogger.d(
                            LofiQLogger.Category.RESOLVER,
                            "[JIOSAAVN][reqId=$reqId] Rejected candidate '${candidate.title}' by '${candidate.artist}' for request '${request.songTitle}' by '${request.artistName}' (${eval.reason})"
                        )
                        false
                    } else {
                        true
                    }
                }
                .sortedByDescending { it.third }

            for ((candidate, eval, _) in validCandidates) {
                val streamUrl = jioSaavnClient.resolveStreamUrl(candidate, request.preferredQuality)
                if (!streamUrl.isNullOrBlank()) {
                    LofiQLogger.i(
                        LofiQLogger.Category.RESOLVER,
                        "[JIOSAAVN][reqId=$reqId] Resolved verified 320kbps stream for '${request.songTitle}' -> '${candidate.title}' [${eval.matchTier}] in ${System.currentTimeMillis() - startTime}ms (score=${String.format(java.util.Locale.US, "%.2f", eval.matchScore)})"
                    )
                    return@withContext ResolutionResult(
                        songId = request.songId,
                        streamUrl = streamUrl,
                        bitrateKbps = when (request.preferredQuality) {
                            AudioQuality.HIGH, AudioQuality.AUTO -> 320
                            AudioQuality.STANDARD -> 160
                            AudioQuality.DATA_SAVER -> 96
                        },
                        quality = request.preferredQuality,
                        mimeType = "audio/mp4",
                        providerId = providerId,
                        expiryEpochMs = System.currentTimeMillis() + 86400000L,
                        requestId = reqId
                    )
                }
            }
        }

        throw ResolverException.SourceUnavailable(
            songId = request.songId,
            reason = "No verified JioSaavn stream match found for: '${request.songTitle}' by '${request.artistName}'"
        )
    }

    private fun passesLanguageCheck(request: ResolveRequest, candidate: JioSaavnSong): Boolean {
        val languages = listOf("tamil", "telugu", "hindi", "malayalam", "kannada", "punjabi", "bengali", "marathi")
        val reqFull = "${request.songTitle} ${request.albumName} ${request.artistName}".lowercase()
        val candFull = "${candidate.title} ${candidate.album} ${candidate.artist}".lowercase()

        for (lang in languages) {
            val reqSpecifiesLang = reqFull.contains("($lang)") || reqFull.contains("[$lang]") || reqFull.contains(" $lang")
            val candSpecifiesLang = candFull.contains("($lang)") || candFull.contains("[$lang]") || candFull.contains(" $lang")

            if (candSpecifiesLang && !reqSpecifiesLang) {
                val reqIsOtherLang = languages.any { other -> other != lang && reqFull.contains(other) }
                if (reqIsOtherLang) return false
            }
        }
        return true
    }

    private fun cleanSongTitle(title: String): String {
        return title
            .replace(Regex("(?i)\\(from \".*?\"\\)|\\(from '.*?'\\)|\\[from \".*?\"\\]|\\[from '.*?'\\]|from \".*?\"|from '.*?'"), "")
            .replace(Regex("(?i)\\(official.*?\\)|\\[official.*?\\]|\\(lyric.*?\\)|\\[lyric.*?\\]|\\(audio\\)|\\[audio\\]|\\(video.*?\\)|\\[video.*?\\]"), "")
            .replace(Regex("(?i)\\(full.*?\\)|\\[full.*?\\]|\\(hd\\)|\\[hd\\]|\\(4k\\)|\\[4k\\]|\\(remix\\)|\\[remix\\]"), "")
            .replace(Regex("(?i)ft\\..*?|feat\\..*?"), "")
            .replace(Regex("(?i)[|\\-].*$"), "")
            .replace(Regex("(?i)\\b(full\\s*video\\s*song|full\\s*video|full\\s*song|video\\s*song|audio\\s*song|lyrical\\s*video|lyric\\s*video|official\\s*video|official\\s*audio|original\\s*track|4k\\s*ultra\\s*hd|4k|hd|remix)\\b"), "")
            .trim()
            .lowercase()
    }

    override suspend fun search(query: String, page: Int, limit: Int): List<Song> {
        return jioSaavnClient.searchSongs(query, limit).map { it.toSong() }
    }

    override suspend fun getSongDetails(songId: String): Song? {
        return jioSaavnClient.getSongDetails(songId)?.toSong()
    }

    override suspend fun getTrending(page: Int, limit: Int): List<Song> {
        return jioSaavnClient.searchSongs("Top Hits 2026", limit).map { it.toSong() }
    }
}

package com.lofiq.feature.debug

import com.lofiq.core.model.AudioQuality
import com.lofiq.core.model.Song

data class DiagnosticTestTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationSeconds: Long = 0L,
    val knownYouTubeId: String? = null
) {
    fun toSong(): Song = Song(
        id = knownYouTubeId ?: id,
        title = title,
        artist = artist,
        album = album,
        durationSeconds = durationSeconds,
        artworkUrl = "https://i.ytimg.com/vi/${knownYouTubeId ?: id}/hqdefault.jpg",
        highResArtworkUrl = "https://i.ytimg.com/vi/${knownYouTubeId ?: id}/maxresdefault.jpg"
    )

    companion object {
        val DEFAULT_SUITE = listOf(
            DiagnosticTestTrack(
                id = "rick_astley_never_gonna_give_you_up",
                title = "Never Gonna Give You Up",
                artist = "Rick Astley",
                durationSeconds = 213,
                knownYouTubeId = "dQw4w9WgXcQ"
            ),
            DiagnosticTestTrack(
                id = "the_weeknd_blinding_lights",
                title = "Blinding Lights",
                artist = "The Weeknd",
                durationSeconds = 200,
                knownYouTubeId = "4NRXx6U8ABQ"
            ),
            DiagnosticTestTrack(
                id = "ed_sheeran_shape_of_you",
                title = "Shape of You",
                artist = "Ed Sheeran",
                durationSeconds = 233,
                knownYouTubeId = "JGwWNGJdvx8"
            ),
            DiagnosticTestTrack(
                id = "the_kid_laroi_stay",
                title = "Stay",
                artist = "The Kid LAROI, Justin Bieber",
                durationSeconds = 141,
                knownYouTubeId = "kTJczUoc56U"
            ),
            DiagnosticTestTrack(
                id = "imagine_dragons_believer",
                title = "Believer",
                artist = "Imagine Dragons",
                durationSeconds = 204,
                knownYouTubeId = "7wtfhZwyrcc"
            ),
            DiagnosticTestTrack(
                id = "the_weeknd_starboy",
                title = "Starboy",
                artist = "The Weeknd",
                durationSeconds = 230,
                knownYouTubeId = "34Na4j8AVgA"
            ),
            DiagnosticTestTrack(
                id = "coldplay_a_sky_full_of_stars",
                title = "A Sky Full of Stars",
                artist = "Coldplay",
                durationSeconds = 268,
                knownYouTubeId = "VPRjCeoBqrI"
            )
        )
    }
}

enum class DiagnosticStatus {
    IDLE,
    RUNNING,
    PASS,
    PARTIAL,
    FAIL
}

enum class FailureCategory {
    NONE,
    SEARCH_FAILED,
    NO_SEARCH_RESULT,
    WRONG_TRACK_MATCH,
    METADATA_FAILED,
    LOGIN_REQUIRED,
    BOT_PROTECTION,
    CONTENT_UNAVAILABLE,
    NO_AUDIO_FORMAT,
    NO_DIRECT_URL,
    STREAM_VALIDATION_FAILED,
    HTTP_403,
    HTTP_404,
    HTTP_429,
    TIMEOUT,
    NETWORK_ERROR,
    MEDIA3_ERROR,
    UNKNOWN
}

data class ClientExtractionResult(
    val clientName: String,
    val httpStatus: Int,
    val playabilityStatus: String?,
    val playabilityReason: String?,
    val hasStreamingData: Boolean,
    val adaptiveFormatsCount: Int,
    val regularFormatsCount: Int,
    val usableAudioFormatsCount: Int,
    val hasDirectUrl: Boolean,
    val selectedMimeType: String?,
    val selectedCodec: String?,
    val selectedBitrateKbps: Int?,
    val selectedSampleRate: Int?,
    val selectedChannels: Int?,
    val latencyMs: Long,
    val failureCategory: FailureCategory
)

data class YouTubeSongDiagnostic(
    val track: DiagnosticTestTrack,
    val searchDurationMs: Long,
    val searchHttpStatus: Int,
    val searchResultCount: Int,
    val matchedVideoId: String?,
    val matchedTitle: String?,
    val matchedArtist: String?,
    val clientResults: List<ClientExtractionResult>,
    val overallStatus: DiagnosticStatus,
    val primaryFailureCategory: FailureCategory,
    val failureMessage: String?
)

data class JioSaavnSongDiagnostic(
    val track: DiagnosticTestTrack,
    val searchHttpStatus: Int,
    val searchCandidateCount: Int,
    val candidateTitles: List<String>,
    val candidateArtists: List<String>,
    val candidateIds: List<String>,
    val matchScore: Double,
    val isAccepted: Boolean,
    val matchReason: String,
    val decryptedUrlAvailable: Boolean,
    val cdnHost: String?,
    val bitrateKbps: Int,
    val streamValidationPass: Boolean,
    val latencyMs: Long,
    val overallStatus: DiagnosticStatus,
    val failureCategory: FailureCategory
)

data class PipedInstanceDiagnostic(
    val instanceUrl: String,
    val httpStatus: Int,
    val latencyMs: Long,
    val resultCount: Int,
    val firstResultTitle: String?,
    val isHealthy: Boolean,
    val errorMessage: String?
)

data class LyricsSongDiagnostic(
    val track: DiagnosticTestTrack,
    val lrclibMatched: Boolean,
    val lrclibHasSynced: Boolean,
    val lrclibHasPlain: Boolean,
    val lrclibLatencyMs: Long,
    val kugouMatched: Boolean,
    val kugouHasLrc: Boolean,
    val kugouLatencyMs: Long,
    val overallStatus: DiagnosticStatus
)

data class PipelineSongDiagnostic(
    val track: DiagnosticTestTrack,
    val requestedIdentity: String,
    val resolvedTitle: String?,
    val resolvedArtist: String?,
    val winningProvider: String?,
    val resolutionTimeMs: Long,
    val streamValidated: Boolean,
    val outcome: String,
    val overallStatus: DiagnosticStatus,
    val failureCategory: FailureCategory,
    val failureReason: String?
)

data class Media3PlaybackDiagnostic(
    val track: DiagnosticTestTrack,
    val streamUrlAvailable: Boolean,
    val providerUsed: String?,
    val resolutionDurationMs: Long,
    val prepareDurationMs: Long,
    val reachedReady: Boolean,
    val playbackStarted: Boolean,
    val actualPlaybackSeconds: Double,
    val pauseResumeVerified: Boolean,
    val playerError: String?,
    val overallStatus: DiagnosticStatus,
    val failureCategory: FailureCategory
)

data class ProviderScorecardRow(
    val provider: String,
    val searchPassRate: String,
    val metadataPassRate: String,
    val streamPassRate: String,
    val validationPassRate: String,
    val playbackPassRate: String,
    val avgLatencyMs: Long,
    val healthSummary: String
)

data class DiagnosticSuiteResult(
    val runNumber: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val totalDurationMs: Long = 0L,
    val isInternetConnected: Boolean = true,
    val dbStatus: String = "ONLINE",
    val cacheEntryCount: Int = 0,
    val jioSaavnDiagnostics: List<JioSaavnSongDiagnostic> = emptyList(),
    val youTubeDiagnostics: List<YouTubeSongDiagnostic> = emptyList(),
    val pipedDiagnostics: List<PipedInstanceDiagnostic> = emptyList(),
    val lyricsDiagnostics: List<LyricsSongDiagnostic> = emptyList(),
    val pipelineDiagnostics: List<PipelineSongDiagnostic> = emptyList(),
    val media3Diagnostics: List<Media3PlaybackDiagnostic> = emptyList(),
    val scorecards: List<ProviderScorecardRow> = emptyList(),
    val finalReportText: String = "",
    val logs: List<String> = emptyList()
)

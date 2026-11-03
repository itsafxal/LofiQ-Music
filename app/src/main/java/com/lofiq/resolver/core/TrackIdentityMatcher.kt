package com.lofiq.resolver.core

import java.util.Locale
import kotlin.math.abs

object TrackIdentityMatcher {

    enum class MatchTier {
        TITLE_AND_ARTIST,
        TITLE_AND_TRUSTED_CHANNEL,
        TITLE_AND_LANGUAGE,
        NONE
    }

    data class CandidateTrack(
        val providerId: String,
        val providerTrackId: String,
        val title: String,
        val artist: String,
        val album: String = "",
        val durationSeconds: Long = 0L,
        val channelName: String = "",
        val channelId: String = "",
        val language: String = "",
        val encryptedMediaUrl: String? = null
    )

    data class MatchEvaluation(
        val isAccepted: Boolean,
        val matchScore: Double,
        val reason: String,
        val titleScore: Double,
        val artistScore: Double,
        val durationDiffSec: Long,
        val matchTier: MatchTier = MatchTier.NONE,
        val isTitleMatched: Boolean = false,
        val isArtistMatched: Boolean = false,
        val isChannelMatched: Boolean = false,
        val isLanguageMatched: Boolean = false
    )

    object TrustedMusicChannels {
        private val TRUSTED_CHANNELS = setOf(
            // Pan-Indian & Bollywood Labels
            "t-series", "tseries", "t series", "gulshan kumar t-series", "t-series regional", "t-series south",
            "zee music company", "zee music", "zee music south",
            "sony music india", "sony music south", "sony music", "sony music entertainment",
            "yrf", "yash raj films",
            "saregama", "saregama music", "saregama india", "saregama malayalam", "saregama tamil", "saregama telugu", "saregama south",
            "tips official", "tips industries", "tips music", "tips",
            "eros now", "eros music",
            "speed records",
            "times music",
            "junglee music",
            "white hill music",
            "desi music factory",
            "svf music",

            // South Indian Labels (Malayalam, Tamil, Telugu, Kannada)
            "saina", "saina music", "saina video vision", "saina audio", "saina movies",
            "muzik247", "muzik 247", "muzik247 malayalam", "muzik 247 malayalam",
            "satyam videos", "satyam audios", "satyam audios & videos", "satyam",
            "manorama music", "manorama music songs", "manorama",
            "speed audio and video", "speed audio video", "speed audio & video", "speed audio", "speed video",
            "millennium audios", "millenium audios", "millennium audio",
            "goodwill entertainments", "goodwill",
            "think music", "think music india", "think music south",
            "aditya music", "aditya music india", "aditya music telugu",
            "lahari music", "lahari music south",
            "divo music", "divo movies", "divo",
            "surya music",
            "east coast audio", "east coast movies", "east coast",
            "123musix",
            "mc audios & videos", "mc audios and videos", "mc audios",
            "geetha arts",
            "anand audio",
            "dvoo records",
            "papyrus music",
            "trendmusic", "trend music south",

            // Global / International Labels
            "vevo",
            "universal music group", "universal music", "umg",
            "warner music group", "warner music", "warner records",
            "atlantic records", "atlantic",
            "columbia records",
            "interscope records", "interscope",
            "republic records",
            "rca records",
            "epic records",
            "capitol records",
            "def jam recordings", "def jam",
            "virgin records",
            "spinnin' records", "spinnin records",
            "monstercat",
            "ultra music",

            // Licensed Streaming Providers
            "jiosaavn", "saavn"
        )

        fun isTrustedChannel(channelName: String): Boolean {
            if (channelName.isBlank()) return false
            val norm = channelName.lowercase(Locale.ROOT).trim()
                .replace(Regex("[^a-z0-9\\s&]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (norm.isBlank()) return false
            if (norm in TRUSTED_CHANNELS) return true
            if (norm.endsWith(" vevo") || norm.endsWith("vevo")) return true

            return TRUSTED_CHANNELS.any { trusted ->
                norm == trusted ||
                norm.contains(trusted) ||
                (trusted.contains(norm) && norm.length >= 5)
            }
        }

        fun getAllTrustedChannels(): Set<String> = TRUSTED_CHANNELS
    }

    private val NOISE_SUFFIX_REGEX = Regex(
        "(?i)\\s*[\\[(](official\\s*(music\\s*)?video|official\\s*audio|lyric\\s*video|lyrics|audio|video|remaster(ed)?(\\s*\\d{4})?|radio\\s*edit|album\\s*version|single\\s*version|full\\s*video\\s*song|full\\s*song|visualizer|soundtrack|ost|audio\\s*track|original\\s*motion\\s*picture\\s*soundtrack|from\\s+[\"'].*?[\"']|from\\s+.*?|feat\\.?\\s+.*?|ft\\.?\\s+.*?|featuring\\s+.*?)[\\])]"
    )

    private val FEAT_SUFFIX_REGEX = Regex(
        "(?i)\\s+(feat\\.?|ft\\.?|featuring)\\s+.*$"
    )

    private val UNBRACKETED_NOISE_REGEX = Regex(
        "(?i)\\b(full\\s*video\\s*song|full\\s*video|full\\s*song|video\\s*song|audio\\s*song|lyrical\\s*video|lyric\\s*video|official\\s*video|official\\s*audio|original\\s*track|4k\\s*ultra\\s*hd|4k|hd|remastered|lyrics|lyrical)\\b"
    )

    private val RECORD_LABEL_REGEX = Regex(
        "(?i)\\b(t-series|sony\\s*music(\\s*south)?|aditya\\s*music|lahari\\s*music|zee\\s*music(\\s*company)?|yrf|saregama|speed\\s*records|eros\\s*now|tips(\\s*official)?|geetha\\s*arts|think\\s*music|muzik247|dvoo\\s*records|saina(\\s*music)?|satyam|manorama)\\b"
    )

    private val ARTIST_STOP_WORDS = setOf(
        "the", "a", "an", "and", "or", "of", "in", "by", "with", "feat", "ft", "featuring",
        "vs", "official", "music", "artist", "records", "audio", "channel", "vevo", "topic",
        "band", "sound", "studio", "production", "productions", "entertainment", "unknown",
        "unknown artist", "various", "various artists", "null"
    )

    val INCOMPATIBLE_VERSION_KEYWORDS = listOf(
        "karaoke",
        "karaoke version",
        "karaoke with lyrics",
        "instrumental",
        "backing track",
        "minus one",
        "minus 1",
        "female key",
        "male key",
        "lower key",
        "higher key",
        "pitch shifted",
        "tribute",
        "cover",
        "acoustic cover",
        "guitar cover",
        "piano cover",
        "flute cover",
        "violin cover",
        "slowed",
        "reverb",
        "slowed+reverb",
        "8d audio",
        "8d",
        "ringtone",
        "chipmunk",
        "parody",
        "reaction",
        "review"
    )

    fun isKaraokeOrIncompatibleTrack(title: String, artist: String = "", channel: String = ""): Boolean {
        val combined = "$title $artist $channel".lowercase(Locale.ROOT)
        return INCOMPATIBLE_VERSION_KEYWORDS.any { kw ->
            if (kw.contains(" ") || kw.contains("+") || kw.contains("-")) {
                combined.contains(kw)
            } else {
                Regex("\\b${Regex.escape(kw)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(combined)
            }
        }
    }

    private val LANGUAGE_KEYWORDS = mapOf(
        "malayalam" to listOf("malayalam", "malayali", "mallu"),
        "tamil" to listOf("tamil", "tamizh"),
        "telugu" to listOf("telugu"),
        "hindi" to listOf("hindi", "bollywood"),
        "kannada" to listOf("kannada"),
        "punjabi" to listOf("punjabi"),
        "bengali" to listOf("bengali", "bangla"),
        "marathi" to listOf("marathi"),
        "english" to listOf("english")
    )

    private val HTML_ENTITY_MAP = mapOf(
        "&quot;" to "\"",
        "&amp;" to "&",
        "&#039;" to "'",
        "&apos;" to "'",
        "&lt;" to "<",
        "&gt;" to ">"
    )

    fun normalize(input: String): String {
        var text = input
        HTML_ENTITY_MAP.forEach { (entity, replacement) ->
            text = text.replace(entity, replacement)
        }
        text = text.replace(NOISE_SUFFIX_REGEX, "")
            .replace(FEAT_SUFFIX_REGEX, "")
            .replace(UNBRACKETED_NOISE_REGEX, "")
            .trim()
        return text.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{Nd}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Transliteration-tolerant normalization for Latin-script text (especially Malayalam and Indic songs).
     * Handles common transliteration variations:
     * - Double vowels: aa -> a, ee -> i, oo -> u
     * - Consonants: zh -> l, th -> t, dh -> d, bh -> b, gh -> g, kh -> k, sh -> s, ch -> c
     * - Collapses double consonants: kk -> k, nn -> n, mm -> m, pp -> p, tt -> t, ll -> l, rr -> r, ss -> s
     */
    fun normalizeTransliteration(input: String): String {
        val norm = normalize(input)
        if (norm.isBlank()) return ""
        // If text contains non-ASCII characters (e.g. Malayalam script \u0D00..\u0D7F), return norm directly
        if (norm.any { it.code > 127 }) {
            return norm
        }
        return norm
            .replace("aa", "a")
            .replace("ee", "i")
            .replace("oo", "u")
            .replace("th", "t")
            .replace("dh", "d")
            .replace("bh", "b")
            .replace("gh", "g")
            .replace("kh", "k")
            .replace("zh", "l")
            .replace("sh", "s")
            .replace("ch", "c")
            .replace(Regex("(.)\\1+"), "$1")
    }

    fun isGenericArtist(artist: String): Boolean {
        val norm = normalize(artist)
        return norm.isBlank() || norm in ARTIST_STOP_WORDS || norm.contains("unknown") || norm.contains("various")
    }

    fun detectLanguage(text: String): String {
        if (text.isBlank()) return ""
        val lower = text.lowercase(Locale.ROOT)

        // 1. Check Unicode script ranges first
        for (ch in text) {
            val code = ch.code
            when (code) {
                in 0x0D00..0x0D7F -> return "malayalam"
                in 0x0B80..0x0BFF -> return "tamil"
                in 0x0C00..0x0C7F -> return "telugu"
                in 0x0C80..0x0CFF -> return "kannada"
                in 0x0900..0x097F -> return "hindi"
                in 0x0A00..0x0A7F -> return "punjabi"
                in 0x0980..0x09FF -> return "bengali"
            }
        }

        // 2. Check explicit bracketed or word tags
        for ((lang, keywords) in LANGUAGE_KEYWORDS) {
            for (kw in keywords) {
                val bracketed1 = "($kw)"
                val bracketed2 = "[$kw]"
                val surrounded = Regex("\\b$kw\\b")
                if (lower.contains(bracketed1) || lower.contains(bracketed2) || surrounded.containsMatchIn(lower)) {
                    return lang
                }
            }
        }

        // 3. Check known regional record label names (Saina Music, Muzik247, Satyam, Manorama, etc.)
        val malayalamLabels = setOf("saina", "muzik247", "satyam", "manorama", "east coast", "millennium", "goodwill", "123musix")
        if (malayalamLabels.any { lower.contains(it) }) return "malayalam"

        return ""
    }

    fun evaluate(
        requestedTitle: String,
        requestedArtist: String,
        requestedDurationSec: Long = 0L,
        candidate: CandidateTrack,
        requestedLanguage: String = "",
        requestedAlbum: String = ""
    ): MatchEvaluation {
        val normReqTitle = normalize(requestedTitle)
        val normCandTitle = normalize(candidate.title)

        val normReqArtist = normalize(requestedArtist).replace(RECORD_LABEL_REGEX, "").trim()
        val normCandArtist = normalize(candidate.artist).replace(RECORD_LABEL_REGEX, "").trim()

        // 1. Title Score Evaluation (including Segmented and Transliteration matching)
        var titleScore = calculateTitleScore(normReqTitle, normCandTitle)
        val segments = candidate.title.split("-", "|", ":", "•", "–", "—", "/", "~")
        if (segments.size > 1) {
            for (segment in segments) {
                val segNorm = normalize(segment)
                if (segNorm.isNotBlank()) {
                    val segScore = calculateTitleScore(normReqTitle, segNorm)
                    if (segScore > titleScore) {
                        titleScore = segScore
                    }
                }
            }
        }

        // 2. Artist Score Evaluation (Multi-artist, Album, and Segment cross-matching)
        var artistScore = calculateArtistScore(normReqArtist, normCandArtist)
        val normCandAlbum = normalize(candidate.album)
        if (normCandAlbum.isNotBlank() && normReqArtist.isNotBlank()) {
            val albumMatch = calculateArtistScore(normReqArtist, normCandAlbum)
            if (albumMatch > artistScore) artistScore = albumMatch
        }

        // Check if artist name appears in candidate title segments
        if (artistScore < 0.50 && segments.size > 1 && normReqArtist.isNotBlank()) {
            for (segment in segments) {
                val segNorm = normalize(segment)
                val segArtistScore = calculateArtistScore(normReqArtist, segNorm)
                if (segArtistScore > artistScore) {
                    artistScore = segArtistScore
                }
            }
        }

        // Check if candidate title mentions any requested artist name
        val reqArtists = extractArtistNames(requestedArtist)
        val titleMentionsReqArtist = reqArtists.isNotEmpty() && reqArtists.any { ra ->
            normCandTitle.contains(ra) ||
            normalizeTransliteration(candidate.title).contains(normalizeTransliteration(ra))
        }
        if (titleMentionsReqArtist && artistScore < 0.90) {
            artistScore = 0.90
        }

        // Determine if Artist matched
        val isArtistMatch = when {
            normReqArtist == normCandArtist && normReqArtist.isNotBlank() -> true
            normReqArtist.isBlank() || isGenericArtist(normReqArtist) -> false
            titleMentionsReqArtist -> true
            normCandArtist.isBlank() || isGenericArtist(normCandArtist) -> false
            artistScore >= 0.40 -> true
            isKnownCrossArtistMatch(normReqArtist, normCandArtist) -> true
            else -> false
        }

        // 3. Trusted Channel / Provider Evaluation
        val candidateChannel = candidate.channelName.ifBlank {
            if (TrustedMusicChannels.isTrustedChannel(candidate.artist)) candidate.artist else ""
        }
        val isTrustedChannelMatch = TrustedMusicChannels.isTrustedChannel(candidateChannel) ||
                TrustedMusicChannels.isTrustedChannel(candidate.artist) ||
                (candidate.providerId.equals("jiosaavn", ignoreCase = true)) ||
                segments.any { TrustedMusicChannels.isTrustedChannel(it) }

        // 4. Language Evaluation
        val reqLang = requestedLanguage.ifBlank { detectLanguage("$requestedTitle $requestedArtist $requestedAlbum") }
        val candLang = candidate.language.ifBlank { detectLanguage("${candidate.title} ${candidate.artist} ${candidate.album} ${candidate.channelName}") }
        val isLanguageMatch = reqLang.isNotBlank() && candLang.isNotBlank() && reqLang.equals(candLang, ignoreCase = true)

        // 5. Duration Difference
        val durationDiff = if (requestedDurationSec > 0 && candidate.durationSeconds > 0) {
            abs(requestedDurationSec - candidate.durationSeconds)
        } else {
            0L
        }

        val durationPenalty = when {
            requestedDurationSec <= 0 || candidate.durationSeconds <= 0 -> 0.0
            durationDiff <= 5 -> 0.0
            durationDiff <= 15 -> 0.05
            durationDiff <= 30 -> 0.15
            else -> 0.35
        }

        // 6. Contradictory Evidence Detection
        val (isContradictory, contradictoryReason) = checkContradictoryEvidence(
            requestedTitle = requestedTitle,
            requestedArtist = requestedArtist,
            requestedLang = reqLang,
            candidate = candidate,
            candidateLang = candLang
        )

        val isTitleAccepted = titleScore >= 0.55
        val isDurationAccepted = requestedDurationSec <= 0 || candidate.durationSeconds <= 0 || durationDiff <= 45

        if (isContradictory) {
            return MatchEvaluation(
                isAccepted = false,
                matchScore = 0.20,
                reason = contradictoryReason,
                titleScore = titleScore,
                artistScore = artistScore,
                durationDiffSec = durationDiff,
                matchTier = MatchTier.NONE,
                isTitleMatched = isTitleAccepted,
                isArtistMatched = isArtistMatch,
                isChannelMatched = isTrustedChannelMatch,
                isLanguageMatched = isLanguageMatch
            )
        }

        // Strict Artist Check: If requested artist is known and candidate artist is known but distinct (conflicting),
        // reject candidate immediately rather than falling back to trusted channel or language
        val isReqArtistSpecified = normReqArtist.isNotBlank() &&
                !isGenericArtist(normReqArtist) &&
                !TrustedMusicChannels.isTrustedChannel(requestedArtist)

        val isCandArtistSpecified = normCandArtist.isNotBlank() &&
                !isGenericArtist(normCandArtist) &&
                !TrustedMusicChannels.isTrustedChannel(candidate.artist)

        val hasExplicitArtistConflict = isReqArtistSpecified &&
                isCandArtistSpecified &&
                !isArtistMatch &&
                !titleMentionsReqArtist &&
                artistScore < 0.35

        if (hasExplicitArtistConflict) {
            return MatchEvaluation(
                isAccepted = false,
                matchScore = 0.15,
                reason = "Explicit artist mismatch: requested '${requestedArtist}' does not match candidate artist '${candidate.artist}'",
                titleScore = titleScore,
                artistScore = artistScore,
                durationDiffSec = durationDiff,
                matchTier = MatchTier.NONE,
                isTitleMatched = isTitleAccepted,
                isArtistMatched = false,
                isChannelMatched = isTrustedChannelMatch,
                isLanguageMatched = isLanguageMatch
            )
        }

        if (!isTitleAccepted) {
            return MatchEvaluation(
                isAccepted = false,
                matchScore = (titleScore * 0.40).coerceAtMost(0.40),
                reason = "Title mismatch (score=${String.format(Locale.US, "%.2f", titleScore)})",
                titleScore = titleScore,
                artistScore = artistScore,
                durationDiffSec = durationDiff,
                matchTier = MatchTier.NONE,
                isTitleMatched = false,
                isArtistMatched = isArtistMatch,
                isChannelMatched = isTrustedChannelMatch,
                isLanguageMatched = isLanguageMatch
            )
        }

        if (!isDurationAccepted) {
            return MatchEvaluation(
                isAccepted = false,
                matchScore = 0.25,
                reason = "Duration drift excessive (${durationDiff}s difference)",
                titleScore = titleScore,
                artistScore = artistScore,
                durationDiffSec = durationDiff,
                matchTier = MatchTier.NONE,
                isTitleMatched = true,
                isArtistMatched = isArtistMatch,
                isChannelMatched = isTrustedChannelMatch,
                isLanguageMatched = isLanguageMatch
            )
        }

        // Golden Rule: TITLE_MATCH AND (ARTIST_MATCH OR TRUSTED_CHANNEL_MATCH OR LANGUAGE_MATCH)
        // Matching Priority Preference Order:
        // 1. Matching title + matching artist
        // 2. Matching title + trusted official music channel/provider
        // 3. Matching title + reliable language match
        val (tier, finalScore, tierReason) = when {
            isArtistMatch -> {
                val consistencyBonus = (if (isTrustedChannelMatch) 0.05 else 0.0) + (if (isLanguageMatch) 0.05 else 0.0)
                val effectiveArtistScore = if (artistScore < 0.50) 0.70 else artistScore
                val score = (titleScore * 0.70 + effectiveArtistScore * 0.30 + consistencyBonus - durationPenalty).coerceIn(0.70, 1.0)
                Triple(MatchTier.TITLE_AND_ARTIST, score, "Matched Title + Artist")
            }
            isTrustedChannelMatch -> {
                val consistencyBonus = if (isLanguageMatch) 0.05 else 0.0
                val score = (titleScore * 0.70 + 0.15 + consistencyBonus - durationPenalty).coerceIn(0.65, 0.88)
                Triple(MatchTier.TITLE_AND_TRUSTED_CHANNEL, score, "Matched Title + Trusted Channel")
            }
            isLanguageMatch -> {
                val score = (titleScore * 0.60 + 0.12 - durationPenalty).coerceIn(0.55, 0.78)
                Triple(MatchTier.TITLE_AND_LANGUAGE, score, "Matched Title + Language")
            }
            else -> {
                val reason = "Rejected: title matched but neither artist, trusted channel, nor language matched (cand artist='${candidate.artist}', channel='${candidate.channelName}')"
                Triple(MatchTier.NONE, (titleScore * 0.40).coerceAtMost(0.45), reason)
            }
        }

        val isAccepted = tier != MatchTier.NONE && finalScore >= 0.50
        val reason = if (isAccepted) {
            "$tierReason (score=${String.format(Locale.US, "%.2f", finalScore)})"
        } else {
            tierReason
        }

        return MatchEvaluation(
            isAccepted = isAccepted,
            matchScore = finalScore,
            reason = reason,
            titleScore = titleScore,
            artistScore = artistScore,
            durationDiffSec = durationDiff,
            matchTier = tier,
            isTitleMatched = true,
            isArtistMatched = isArtistMatch,
            isChannelMatched = isTrustedChannelMatch,
            isLanguageMatched = isLanguageMatch
        )
    }

    private fun checkContradictoryEvidence(
        requestedTitle: String,
        requestedArtist: String,
        requestedLang: String,
        candidate: CandidateTrack,
        candidateLang: String
    ): Pair<Boolean, String> {
        val candText = "${candidate.title} ${candidate.artist} ${candidate.channelName}".lowercase(Locale.ROOT)
        val reqText = "$requestedTitle $requestedArtist".lowercase(Locale.ROOT)

        // 1. Incompatible version check (karaoke, instrumental, tribute, cover, slowed, reverb, etc.)
        for (kw in INCOMPATIBLE_VERSION_KEYWORDS) {
            val candHasKw = if (kw.contains(" ") || kw.contains("+") || kw.contains("-")) {
                candText.contains(kw)
            } else {
                Regex("\\b${Regex.escape(kw)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(candText)
            }

            val reqHasKw = if (kw.contains(" ") || kw.contains("+") || kw.contains("-")) {
                reqText.contains(kw)
            } else {
                Regex("\\b${Regex.escape(kw)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(reqText)
            }

            if (candHasKw && !reqHasKw) {
                return true to "Incompatible track version ($kw) when original track requested"
            }
        }

        // 2. Conflicting language check (when both requested and candidate languages are known and distinct)
        val indianLanguages = setOf("tamil", "telugu", "hindi", "malayalam", "kannada", "punjabi", "bengali", "marathi")
        if (requestedLang.isNotBlank() && candidateLang.isNotBlank()) {
            if (requestedLang in indianLanguages && candidateLang in indianLanguages &&
                !requestedLang.equals(candidateLang, ignoreCase = true)
            ) {
                return true to "Conflicting language: requested '$requestedLang' but candidate is '$candidateLang'"
            }
            if ((requestedLang in indianLanguages && candidateLang.equals("english", ignoreCase = true)) ||
                (requestedLang.equals("english", ignoreCase = true) && candidateLang in indianLanguages)
            ) {
                return true to "Conflicting language: requested '$requestedLang' but candidate is '$candidateLang'"
            }
        }

        return false to ""
    }

    private fun calculateTitleScore(req: String, cand: String): Double {
        if (req.isBlank() || cand.isBlank()) return 0.0
        val normReq = normalize(req)
        val normCand = normalize(cand)
        if (normReq.isBlank() || normCand.isBlank()) return 0.0
        if (normReq == normCand) return 1.0

        val transReq = normalizeTransliteration(req)
        val transCand = normalizeTransliteration(cand)
        if (transReq.isNotBlank() && transReq == transCand) return 1.0

        val titleNoise = setOf(
            "song", "video", "audio", "full", "track", "music", "original",
            "movie", "film", "the", "a", "an", "official", "lyrics", "lyric",
            "feat", "ft", "featuring"
        )
        val reqTokens = normReq.split(" ").filter { it.length > 1 && it !in titleNoise }.toSet()
        val candTokens = normCand.split(" ").filter { it.length > 1 && it !in titleNoise }.toSet()

        if (reqTokens.isEmpty() || candTokens.isEmpty()) {
            return if (" $normCand ".contains(" $normReq ")) 0.85 else 0.0
        }

        if (candTokens == reqTokens) return 1.0

        val transReqTokens = transReq.split(" ").filter { it.length > 1 && it !in titleNoise }.toSet()
        val transCandTokens = transCand.split(" ").filter { it.length > 1 && it !in titleNoise }.toSet()
        if (transReqTokens.isNotEmpty() && transReqTokens == transCandTokens) return 1.0

        if (candTokens.containsAll(reqTokens)) {
            val extraTokens = candTokens - reqTokens
            val ratio = reqTokens.size.toDouble() / candTokens.size.toDouble()
            // Reject single-word title matching unrelated longer title (e.g. "Stay" vs "Stay With Me")
            if (reqTokens.size == 1 && extraTokens.isNotEmpty()) {
                return (0.40 * ratio)
            }
            return (0.80 + (0.20 * ratio)).coerceIn(0.70, 1.0)
        }

        if (transCandTokens.containsAll(transReqTokens)) {
            val extraTokens = transCandTokens - transReqTokens
            val ratio = transReqTokens.size.toDouble() / transCandTokens.size.toDouble()
            if (transReqTokens.size == 1 && extraTokens.isNotEmpty()) {
                return (0.40 * ratio)
            }
            return (0.80 + (0.20 * ratio)).coerceIn(0.70, 1.0)
        }

        if (reqTokens.containsAll(candTokens)) {
            val extraTokens = reqTokens - candTokens
            val ratio = candTokens.size.toDouble() / reqTokens.size.toDouble()
            if (candTokens.size == 1 && extraTokens.isNotEmpty() && !extraTokens.any { it in titleNoise }) {
                return (0.75 + (0.25 * ratio)).coerceIn(0.65, 0.95)
            }
            return (0.80 + (0.20 * ratio)).coerceIn(0.70, 1.0)
        }

        if (transReqTokens.containsAll(transCandTokens)) {
            val extraTokens = transReqTokens - transCandTokens
            val ratio = transCandTokens.size.toDouble() / transReqTokens.size.toDouble()
            if (transCandTokens.size == 1 && extraTokens.isNotEmpty() && !extraTokens.any { it in titleNoise }) {
                return (0.75 + (0.25 * ratio)).coerceIn(0.65, 0.95)
            }
            return (0.80 + (0.20 * ratio)).coerceIn(0.70, 1.0)
        }

        val overlap = reqTokens.intersect(candTokens).size
        val dice = (2.0 * overlap) / (reqTokens.size + candTokens.size)

        val primaryToken = reqTokens.firstOrNull { it.length >= 4 }
        if (primaryToken != null && !candTokens.any { it.contains(primaryToken) || primaryToken.contains(it) }) {
            return (dice * 0.4).coerceAtMost(0.40)
        }

        return dice
    }

    private fun calculateArtistScore(req: String, cand: String): Double {
        if (req.isBlank() || cand.isBlank() || isGenericArtist(req)) return 0.75
        val normReq = normalize(req)
        val normCand = normalize(cand)
        if (normReq == normCand) return 1.0
        if (normCand.contains(normReq) || normReq.contains(normCand)) return 0.95

        val transReq = normalizeTransliteration(req)
        val transCand = normalizeTransliteration(cand)
        if (transReq.isNotBlank() && transReq == transCand) return 1.0

        val reqArtists = extractArtistNames(req)
        val candArtists = extractArtistNames(cand)

        // Multiple credited artists: match with any one counts as positive evidence (User request: at least 1 must match)
        if (reqArtists.isNotEmpty() && candArtists.isNotEmpty()) {
            for (ra in reqArtists) {
                for (ca in candArtists) {
                    if (ra == ca || ca.contains(ra) || ra.contains(ca)) {
                        return 0.95
                    }
                    val transRa = normalizeTransliteration(ra)
                    val transCa = normalizeTransliteration(ca)
                    if (transRa.isNotBlank() && (transRa == transCa || transCa.contains(transRa) || transRa.contains(transCa))) {
                        return 0.95
                    }
                }
            }
        }

        val reqTokens = extractArtistTokens(req)
        val candTokens = extractArtistTokens(cand)
        if (reqTokens.isEmpty() || candTokens.isEmpty()) return 0.60

        // Substring / key name matching (e.g. Yesudas, Chithra, Harisankar, Sushin, Anirudh)
        val reqTokens3 = reqTokens.filter { it.length >= 3 }
        val candTokens3 = candTokens.filter { it.length >= 3 }
        for (rt in reqTokens3) {
            for (ct in candTokens3) {
                if (rt == ct || ct.contains(rt) || rt.contains(ct)) {
                    return 0.90
                }
                val transRt = normalizeTransliteration(rt)
                val transCt = normalizeTransliteration(ct)
                if (transRt.isNotBlank() && (transRt == transCt || transCt.contains(transRt) || transRt.contains(transCt))) {
                    return 0.90
                }
            }
        }

        val overlap = reqTokens.intersect(candTokens).size
        if (overlap > 0) {
            val ratio = overlap.toDouble() / reqTokens.size
            return (0.70 + (0.30 * ratio)).coerceIn(0.70, 1.0)
        }

        return 0.0
    }

    fun extractArtistNames(artistStr: String): List<String> {
        if (artistStr.isBlank()) return emptyList()
        var text = artistStr
        HTML_ENTITY_MAP.forEach { (entity, replacement) ->
            text = text.replace(entity, replacement)
        }
        return text
            .split(Regex("(?i)\\s*[,/&;+|x•~]\\s*|\\s+(feat\\.?|ft\\.?|featuring|with|and)\\s+"))
            .map { normalize(it) }
            .filter { it.isNotBlank() && it !in ARTIST_STOP_WORDS && !isGenericArtist(it) }
    }

    private fun extractArtistTokens(artistStr: String): Set<String> {
        return artistStr.split(" ", ",", "&", "/", "+", ";", "|", "•", "~")
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.length >= 2 && it !in ARTIST_STOP_WORDS }
            .toSet()
    }

    private fun isKnownCrossArtistMatch(normReq: String, normCand: String): Boolean {
        if (normReq.isBlank() || normCand.isBlank() || isGenericArtist(normReq)) return true
        val reqArtists = extractArtistNames(normReq)
        val candArtists = extractArtistNames(normCand)
        if (reqArtists.any { ra -> candArtists.any { ca -> ra == ca || ca.contains(ra) || ra.contains(ca) } }) {
            return true
        }
        val reqTokens = extractArtistTokens(normReq)
        val candTokens = extractArtistTokens(normCand)
        return reqTokens.any { it in candTokens } || candTokens.any { it in reqTokens }
    }
}

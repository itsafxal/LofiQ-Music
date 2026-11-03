package com.lofiq.resolver

import com.lofiq.resolver.core.TrackIdentityMatcher
import com.lofiq.resolver.core.TrackIdentityMatcher.MatchTier
import org.junit.Assert.*
import org.junit.Test

class TrackIdentityMatcherTest {

    @Test
    fun `1 - exact title and exact artist is ACCEPTED with high match score`() {
        val candidate = TrackIdentityMatcher.CandidateTrack(
            providerId = "direct_http",
            providerTrackId = "p1",
            title = "Blinding Lights",
            artist = "The Weeknd",
            durationSeconds = 200
        )

        val eval = TrackIdentityMatcher.evaluate(
            requestedTitle = "Blinding Lights",
            requestedArtist = "The Weeknd",
            requestedDurationSec = 200,
            candidate = candidate
        )

        assertTrue(eval.isAccepted)
        assertEquals(MatchTier.TITLE_AND_ARTIST, eval.matchTier)
        assertTrue(eval.matchScore >= 0.95)
        assertEquals(1.0, eval.titleScore, 0.01)
        assertEquals(1.0, eval.artistScore, 0.01)
    }

    @Test
    fun `2 - same title but wrong artist and untrusted channel is REJECTED`() {
        // "Stay" by Rihanna vs requested "Stay" by The Kid LAROI
        val candidate = TrackIdentityMatcher.CandidateTrack(
            providerId = "direct_http",
            providerTrackId = "p2",
            title = "Stay",
            artist = "Rihanna, Mikky Ekko",
            channelName = "RandomUploader123",
            durationSeconds = 240
        )

        val eval = TrackIdentityMatcher.evaluate(
            requestedTitle = "Stay",
            requestedArtist = "The Kid LAROI",
            requestedDurationSec = 141,
            candidate = candidate
        )

        assertFalse("Same title with completely wrong artist and untrusted channel must be rejected", eval.isAccepted)
        assertEquals(MatchTier.NONE, eval.matchTier)
    }

    @Test
    fun `3 - same artist but wrong title is REJECTED`() {
        // "Starboy" vs requested "Blinding Lights"
        val candidate = TrackIdentityMatcher.CandidateTrack(
            providerId = "direct_http",
            providerTrackId = "p3",
            title = "Starboy",
            artist = "The Weeknd",
            durationSeconds = 230
        )

        val eval = TrackIdentityMatcher.evaluate(
            requestedTitle = "Blinding Lights",
            requestedArtist = "The Weeknd",
            requestedDurationSec = 200,
            candidate = candidate
        )

        assertFalse("Same artist with different title must be rejected", eval.isAccepted)
        assertTrue(eval.titleScore < 0.40)
        assertEquals(MatchTier.NONE, eval.matchTier)
    }

    @Test
    fun `4 - similar title with noise or remaster suffix evaluates SAFELY to ACCEPT`() {
        // "Never Gonna Give You Up (2022 Remaster)" vs requested "Never Gonna Give You Up"
        val candidate = TrackIdentityMatcher.CandidateTrack(
            providerId = "direct_http",
            providerTrackId = "p4",
            title = "Never Gonna Give You Up (2022 Remaster)",
            artist = "Rick Astley",
            durationSeconds = 213
        )

        val eval = TrackIdentityMatcher.evaluate(
            requestedTitle = "Never Gonna Give You Up",
            requestedArtist = "Rick Astley",
            requestedDurationSec = 213,
            candidate = candidate
        )

        assertTrue("Normalized remaster should match requested song", eval.isAccepted)
        assertEquals(MatchTier.TITLE_AND_ARTIST, eval.matchTier)
        assertTrue(eval.titleScore >= 0.90)
    }

    @Test
    fun `5 - different song with similar words is REJECTED`() {
        // "Stay With Me" by Sam Smith vs requested "Stay" by The Kid LAROI
        val candidate = TrackIdentityMatcher.CandidateTrack(
            providerId = "direct_http",
            providerTrackId = "p5",
            title = "Stay With Me",
            artist = "Sam Smith",
            durationSeconds = 172
        )

        val eval = TrackIdentityMatcher.evaluate(
            requestedTitle = "Stay",
            requestedArtist = "The Kid LAROI",
            requestedDurationSec = 141,
            candidate = candidate
        )

        assertFalse("Different song with partial word match must be rejected", eval.isAccepted)
        assertFalse(eval.isTitleMatched)
    }

    @Test
    fun `6 - multiple candidates selects the exact matching candidate over covers or karaoke`() {
        val karaokeCandidate = TrackIdentityMatcher.CandidateTrack(
            providerId = "direct_http",
            providerTrackId = "cand_0",
            title = "Shape of You (Karaoke Tribute)",
            artist = "Sing King Karaoke",
            durationSeconds = 233
        )

        val officialCandidate = TrackIdentityMatcher.CandidateTrack(
            providerId = "direct_http",
            providerTrackId = "cand_1",
            title = "Shape of You",
            artist = "Ed Sheeran",
            durationSeconds = 233
        )

        val candidates = listOf(karaokeCandidate, officialCandidate)

        val evals = candidates.map { cand ->
            cand to TrackIdentityMatcher.evaluate(
                requestedTitle = "Shape of You",
                requestedArtist = "Ed Sheeran",
                requestedDurationSec = 233,
                candidate = cand
            )
        }

        val karaokeEval = evals[0].second
        val officialEval = evals[1].second

        assertFalse("Karaoke tribute with wrong artist must be rejected", karaokeEval.isAccepted)
        assertTrue("Official candidate must be accepted", officialEval.isAccepted)

        val best = evals.filter { it.second.isAccepted }.maxByOrNull { it.second.matchScore }
        assertNotNull(best)
        assertEquals("cand_1", best?.first?.providerTrackId)
    }

    @Test
    fun `7 - excessive duration difference penalizes and rejects candidate`() {
        val extendedMix = TrackIdentityMatcher.CandidateTrack(
            providerId = "direct_http",
            providerTrackId = "p7",
            title = "Blinding Lights (10 Minute Extended Club Loop)",
            artist = "The Weeknd",
            durationSeconds = 600
        )

        val eval = TrackIdentityMatcher.evaluate(
            requestedTitle = "Blinding Lights",
            requestedArtist = "The Weeknd",
            requestedDurationSec = 200,
            candidate = extendedMix
        )

        assertFalse("Excessive duration drift must be rejected", eval.isAccepted)
    }

    @Test
    fun `8 - composer or featured artist query correctly matches multi-artist candidate`() {
        val candidate = TrackIdentityMatcher.CandidateTrack(
            providerId = "jiosaavn",
            providerTrackId = "p8",
            title = "Naanga Naalu Peru",
            artist = "Asal Kolaar, Sai Abhyankkar",
            durationSeconds = 210
        )

        val eval = TrackIdentityMatcher.evaluate(
            requestedTitle = "naanga naalu",
            requestedArtist = "sai abhyankkar",
            requestedDurationSec = 0,
            candidate = candidate
        )

        assertTrue("Candidate with composer/music director must be accepted", eval.isAccepted)
        assertEquals(MatchTier.TITLE_AND_ARTIST, eval.matchTier)
        assertTrue(eval.matchScore >= 0.70)
    }

    @Test
    fun `9 - title and trusted channel match is ACCEPTED when artist metadata is missing`() {
        // Artist metadata missing on request and candidate, but uploaded by trusted channel Sony Music South
        val candidate = TrackIdentityMatcher.CandidateTrack(
            providerId = "youtube",
            providerTrackId = "yt_sony",
            title = "Kannana Kanne - Official Lyric Video",
            artist = "Sony Music South",
            channelName = "Sony Music South",
            durationSeconds = 265
        )

        val eval = TrackIdentityMatcher.evaluate(
            requestedTitle = "Kannana Kanne",
            requestedArtist = "",
            requestedDurationSec = 265,
            candidate = candidate
        )

        assertTrue("Candidate with matching title and trusted channel should be accepted", eval.isAccepted)
        assertEquals(MatchTier.TITLE_AND_TRUSTED_CHANNEL, eval.matchTier)
        assertTrue(eval.isChannelMatched)
        assertTrue(eval.matchScore >= 0.70)
    }

    @Test
    fun `10 - title and trusted channel match is ACCEPTED when artist metadata does not match`() {
        // Requested track has artist "D. Imman", but YouTube candidate has artist listed as label "Sony Music South"
        val candidate = TrackIdentityMatcher.CandidateTrack(
            providerId = "youtube",
            providerTrackId = "yt_sony_label",
            title = "Kannana Kanne",
            artist = "Sony Music South",
            channelName = "Sony Music South",
            durationSeconds = 265
        )

        val eval = TrackIdentityMatcher.evaluate(
            requestedTitle = "Kannana Kanne",
            requestedArtist = "D. Imman",
            requestedDurationSec = 265,
            candidate = candidate
        )

        assertTrue("Candidate with trusted official channel match is accepted when artist doesn't match", eval.isAccepted)
        assertEquals(MatchTier.TITLE_AND_TRUSTED_CHANNEL, eval.matchTier)
        assertTrue(eval.isChannelMatched)
    }

    @Test
    fun `11 - title and language match is ACCEPTED when artist and channel metadata are unavailable`() {
        // Malayalam song requested with language, uploaded by unknown creator with generic artist
        val candidate = TrackIdentityMatcher.CandidateTrack(
            providerId = "youtube",
            providerTrackId = "yt_lang",
            title = "Malare",
            artist = "Unknown Artist",
            channelName = "SomeCommunityChannel",
            language = "malayalam",
            durationSeconds = 310
        )

        val eval = TrackIdentityMatcher.evaluate(
            requestedTitle = "Malare",
            requestedArtist = "",
            requestedDurationSec = 310,
            candidate = candidate,
            requestedLanguage = "malayalam"
        )

        assertTrue("Candidate with matching title and reliable language match should be accepted", eval.isAccepted)
        assertEquals(MatchTier.TITLE_AND_LANGUAGE, eval.matchTier)
        assertTrue(eval.isLanguageMatched)
        assertTrue(eval.matchScore >= 0.60)
    }

    @Test
    fun `12 - title and artist match is ACCEPTED when language metadata is completely missing`() {
        // Language metadata not present on requested track or candidate
        val candidate = TrackIdentityMatcher.CandidateTrack(
            providerId = "direct_http",
            providerTrackId = "p12",
            title = "Aaradhike",
            artist = "Sooraj Santhosh, Anne Amie",
            channelName = "",
            language = "",
            durationSeconds = 250
        )

        val eval = TrackIdentityMatcher.evaluate(
            requestedTitle = "Aaradhike",
            requestedArtist = "Sooraj Santhosh",
            requestedDurationSec = 250,
            candidate = candidate,
            requestedLanguage = ""
        )

        assertTrue("Strong artist match must not be rejected merely because language is missing", eval.isAccepted)
        assertEquals(MatchTier.TITLE_AND_ARTIST, eval.matchTier)
        assertTrue(eval.isArtistMatched)
    }

    @Test
    fun `13 - multiple credited artists matches when any one credited artist matches`() {
        // Requested track lists "Sid Sriram", candidate credits "A. R. Rahman, Sid Sriram, Shashaa Tirupati"
        val candidate = TrackIdentityMatcher.CandidateTrack(
            providerId = "jiosaavn",
            providerTrackId = "p13",
            title = "Marakkuma Nenjam",
            artist = "A. R. Rahman, Sid Sriram, Shashaa Tirupati",
            durationSeconds = 255
        )

        val eval = TrackIdentityMatcher.evaluate(
            requestedTitle = "Marakkuma Nenjam",
            requestedArtist = "Sid Sriram",
            requestedDurationSec = 255,
            candidate = candidate
        )

        assertTrue("Matching any one credited artist counts as positive evidence", eval.isAccepted)
        assertEquals(MatchTier.TITLE_AND_ARTIST, eval.matchTier)
        assertTrue(eval.isArtistMatched)
    }

    @Test
    fun `14 - malayalam songs with Latin-script titles and spelling variations match reliably`() {
        // Transliteration differences: "Mambazhakkaalam" vs "Mambazhakkalam"
        val candidate1 = TrackIdentityMatcher.CandidateTrack(
            providerId = "jiosaavn",
            providerTrackId = "p14_1",
            title = "Mambazhakkalam",
            artist = "K. S. Chithra",
            durationSeconds = 280
        )

        val eval1 = TrackIdentityMatcher.evaluate(
            requestedTitle = "Mambazhakkaalam",
            requestedArtist = "K. S. Chithra",
            requestedDurationSec = 280,
            candidate = candidate1
        )

        assertTrue("Malayalam Latin-script transliteration variation should match", eval1.isAccepted)
        assertEquals(MatchTier.TITLE_AND_ARTIST, eval1.matchTier)
        assertEquals(1.0, eval1.titleScore, 0.05)

        // Transliteration variation: "Pavizha Mazha" vs "Pavila Mazha"
        val candidate2 = TrackIdentityMatcher.CandidateTrack(
            providerId = "youtube",
            providerTrackId = "p14_2",
            title = "Pavila Mazha - Athiran",
            artist = "K. S. Harisankar",
            durationSeconds = 250
        )

        val eval2 = TrackIdentityMatcher.evaluate(
            requestedTitle = "Pavizha Mazha",
            requestedArtist = "K. S. Harisankar",
            requestedDurationSec = 250,
            candidate = candidate2
        )

        assertTrue("Malayalam zh/l transliteration should match", eval2.isAccepted)
        assertEquals(MatchTier.TITLE_AND_ARTIST, eval2.matchTier)
    }

    @Test
    fun `15 - native Malayalam Unicode script title matches`() {
        val candidate = TrackIdentityMatcher.CandidateTrack(
            providerId = "jiosaavn",
            providerTrackId = "p15",
            title = "മാമ്പഴക്കാലം",
            artist = "കെ. എസ്. ചിത്ര",
            durationSeconds = 280
        )

        val eval = TrackIdentityMatcher.evaluate(
            requestedTitle = "മാമ്പഴക്കാലം",
            requestedArtist = "കെ. എസ്. ചിത്ര",
            requestedDurationSec = 280,
            candidate = candidate
        )

        assertTrue("Native Malayalam Unicode text must match", eval.isAccepted)
        assertEquals(MatchTier.TITLE_AND_ARTIST, eval.matchTier)
        assertEquals(1.0, eval.titleScore, 0.01)
    }

    @Test
    fun `16 - untrusted channel with wrong artist and missing language is REJECTED`() {
        val candidate = TrackIdentityMatcher.CandidateTrack(
            providerId = "youtube",
            providerTrackId = "p16",
            title = "Malare",
            artist = "Unknown Singer 99",
            channelName = "CasualUploader_99",
            language = "",
            durationSeconds = 310
        )

        val eval = TrackIdentityMatcher.evaluate(
            requestedTitle = "Malare",
            requestedArtist = "Vijay Yesudas",
            requestedDurationSec = 310,
            candidate = candidate,
            requestedLanguage = ""
        )

        assertFalse("Candidate on untrusted channel without matching artist or language must be rejected", eval.isAccepted)
        assertEquals(MatchTier.NONE, eval.matchTier)
    }

    @Test
    fun `17 - conflicting language metadata is REJECTED as contradictory`() {
        // Requested Tamil version, candidate is Hindi version of the same song title
        val candidate = TrackIdentityMatcher.CandidateTrack(
            providerId = "jiosaavn",
            providerTrackId = "p17",
            title = "Arabic Kuthu",
            artist = "Anirudh Ravichander",
            language = "hindi",
            durationSeconds = 280
        )

        val eval = TrackIdentityMatcher.evaluate(
            requestedTitle = "Arabic Kuthu",
            requestedArtist = "Anirudh Ravichander",
            requestedDurationSec = 280,
            candidate = candidate,
            requestedLanguage = "tamil"
        )

        assertFalse("Conflicting language must be rejected as contradictory", eval.isAccepted)
        assertTrue(eval.reason.contains("Conflicting language"))
    }

    @Test
    fun `18 - incompatible version like slowed and reverb is REJECTED when standard track requested`() {
        val candidate = TrackIdentityMatcher.CandidateTrack(
            providerId = "youtube",
            providerTrackId = "p18",
            title = "Starboy (Slowed + Reverb)",
            artist = "The Weeknd",
            durationSeconds = 230
        )

        val eval = TrackIdentityMatcher.evaluate(
            requestedTitle = "Starboy",
            requestedArtist = "The Weeknd",
            requestedDurationSec = 230,
            candidate = candidate
        )

        assertFalse("Incompatible track version (slowed+reverb) must be rejected", eval.isAccepted)
        assertTrue(eval.reason.contains("Incompatible track version"))
    }

    @Test
    fun `19 - candidate preference priority ranks Title+Artist higher than Title+Channel and Title+Language`() {
        val candidateArtist = TrackIdentityMatcher.CandidateTrack(
            providerId = "youtube",
            providerTrackId = "cand_artist",
            title = "Aaradhike",
            artist = "Sooraj Santhosh",
            channelName = "RandomUploader",
            durationSeconds = 250
        )

        val candidateChannel = TrackIdentityMatcher.CandidateTrack(
            providerId = "youtube",
            providerTrackId = "cand_channel",
            title = "Aaradhike",
            artist = "Muzik247",
            channelName = "Muzik247",
            durationSeconds = 250
        )

        val candidateLanguage = TrackIdentityMatcher.CandidateTrack(
            providerId = "youtube",
            providerTrackId = "cand_language",
            title = "Aaradhike",
            artist = "Unknown",
            channelName = "CommunityChannel",
            language = "malayalam",
            durationSeconds = 250
        )

        val evalArtist = TrackIdentityMatcher.evaluate("Aaradhike", "Sooraj Santhosh", 250, candidateArtist, "malayalam")
        val evalChannel = TrackIdentityMatcher.evaluate("Aaradhike", "", 250, candidateChannel, "malayalam")
        val evalLanguage = TrackIdentityMatcher.evaluate("Aaradhike", "", 250, candidateLanguage, "malayalam")

        assertTrue(evalArtist.isAccepted)
        assertTrue(evalChannel.isAccepted)
        assertTrue(evalLanguage.isAccepted)

        assertEquals(MatchTier.TITLE_AND_ARTIST, evalArtist.matchTier)
        assertEquals(MatchTier.TITLE_AND_TRUSTED_CHANNEL, evalChannel.matchTier)
        assertEquals(MatchTier.TITLE_AND_LANGUAGE, evalLanguage.matchTier)

        // Strict Preference Order: Title+Artist > Title+Channel > Title+Language
        assertTrue("Title+Artist score must be greater than Title+Channel score", evalArtist.matchScore > evalChannel.matchScore)
        assertTrue("Title+Channel score must be greater than Title+Language score", evalChannel.matchScore > evalLanguage.matchScore)
    }

    @Test
    fun `20 - Muzik247 and T-Series recognized as trusted music channels`() {
        assertTrue(TrackIdentityMatcher.TrustedMusicChannels.isTrustedChannel("Muzik247"))
        assertTrue(TrackIdentityMatcher.TrustedMusicChannels.isTrustedChannel("Muzik247 Malayalam"))
        assertTrue(TrackIdentityMatcher.TrustedMusicChannels.isTrustedChannel("T-Series"))
        assertTrue(TrackIdentityMatcher.TrustedMusicChannels.isTrustedChannel("Sony Music South"))
        assertTrue(TrackIdentityMatcher.TrustedMusicChannels.isTrustedChannel("Aditya Music"))
        assertTrue(TrackIdentityMatcher.TrustedMusicChannels.isTrustedChannel("TaylorSwiftVEVO"))
        assertTrue(TrackIdentityMatcher.TrustedMusicChannels.isTrustedChannel("jiosaavn"))

        assertFalse(TrackIdentityMatcher.TrustedMusicChannels.isTrustedChannel("RandomVlogger"))
        assertFalse(TrackIdentityMatcher.TrustedMusicChannels.isTrustedChannel("CoolMusic123"))
    }

    @Test
    fun `21 - title with bracketed feat artist and from movie matches pure song on JioSaavn`() {
        val candidate = TrackIdentityMatcher.CandidateTrack(
            providerId = "jiosaavn",
            providerTrackId = "cand_radhimaa",
            title = "Radhimaa (From \"Think Indie\")",
            artist = "Sai Abhyankkar",
            durationSeconds = 210
        )

        val eval = TrackIdentityMatcher.evaluate(
            requestedTitle = "Radhimaa [From \"Think Indie\"] (feat. Sai Smriti)",
            requestedArtist = "Sai Abhyankkar , Nargis Teji , Asma Teji & Vivek",
            requestedDurationSec = 210,
            candidate = candidate
        )

        assertTrue("Radhimaa with feat Sai Smriti must be ACCEPTED for JioSaavn candidate", eval.isAccepted)
        assertEquals(MatchTier.TITLE_AND_ARTIST, eval.matchTier)
        assertTrue("Match score must be >= 0.85, got ${eval.matchScore}", eval.matchScore >= 0.85)
    }

    @Test
    fun `22 - title with feat artist matches base title`() {
        val candidate = TrackIdentityMatcher.CandidateTrack(
            providerId = "youtube",
            providerTrackId = "cand_starboy",
            title = "Starboy (feat. Daft Punk)",
            artist = "The Weeknd",
            durationSeconds = 230
        )

        val eval = TrackIdentityMatcher.evaluate(
            requestedTitle = "Starboy",
            requestedArtist = "The Weeknd",
            requestedDurationSec = 230,
            candidate = candidate
        )

        assertTrue("Starboy (feat. Daft Punk) must be ACCEPTED for requested Starboy", eval.isAccepted)
        assertEquals(MatchTier.TITLE_AND_ARTIST, eval.matchTier)
        assertTrue(eval.matchScore >= 0.85)
    }

    @Test
    fun `23 - candidate with identical title and trusted channel BUT conflicting artist is REJECTED`() {
        // Malayalam song Aaradhike by Sooraj Santhosh requested; candidate on Sony Music South but by Taylor Swift
        val candidate = TrackIdentityMatcher.CandidateTrack(
            providerId = "youtube",
            providerTrackId = "cand_wrong_artist",
            title = "Aaradhike",
            artist = "Taylor Swift",
            channelName = "Sony Music South",
            durationSeconds = 250
        )

        val eval = TrackIdentityMatcher.evaluate(
            requestedTitle = "Aaradhike",
            requestedArtist = "Sooraj Santhosh",
            requestedDurationSec = 250,
            candidate = candidate
        )

        assertFalse("Candidate with explicit conflicting artist must be REJECTED even on trusted channel", eval.isAccepted)
        assertTrue(eval.reason.contains("Explicit artist mismatch"))
        assertEquals(MatchTier.NONE, eval.matchTier)
    }

    @Test
    fun `24 - multi-artist song matches when at least one artist matches`() {
        val candidate = TrackIdentityMatcher.CandidateTrack(
            providerId = "jiosaavn",
            providerTrackId = "cand_multi_artist",
            title = "Aaradhike",
            artist = "Sooraj Santhosh, Anne Amie",
            durationSeconds = 250
        )

        val eval = TrackIdentityMatcher.evaluate(
            requestedTitle = "Aaradhike",
            requestedArtist = "Sooraj Santhosh, Anne Amie, Soubin Shahir",
            requestedDurationSec = 250,
            candidate = candidate
        )

        assertTrue("Candidate with matching artist in multi-artist list must be ACCEPTED", eval.isAccepted)
        assertEquals(MatchTier.TITLE_AND_ARTIST, eval.matchTier)
        assertTrue(eval.isArtistMatched)
    }

    @Test
    fun `25 - karaoke or backing track in channel name is REJECTED when regular track requested`() {
        val candidate = TrackIdentityMatcher.CandidateTrack(
            providerId = "youtube",
            providerTrackId = "cand_karaoke_channel",
            title = "Aaradhike (Full Song)",
            artist = "Malayalam Karaoke Tracks",
            channelName = "Sunil Karaoke Studio",
            durationSeconds = 250
        )

        val eval = TrackIdentityMatcher.evaluate(
            requestedTitle = "Aaradhike",
            requestedArtist = "Sooraj Santhosh",
            requestedDurationSec = 250,
            candidate = candidate
        )

        assertFalse("Karaoke track uploaded on karaoke channel must be REJECTED", eval.isAccepted)
        assertTrue(eval.reason.contains("Incompatible track version"))
    }

    @Test
    fun `26 - artist name present in candidate title segments matches when candidate artist is label`() {
        val candidate = TrackIdentityMatcher.CandidateTrack(
            providerId = "youtube",
            providerTrackId = "cand_muzik247_title_artist",
            title = "Aaradhike Video Song | Ambili | Soubin Shahir | Sooraj Santhosh | Muzik247",
            artist = "Muzik247",
            channelName = "Muzik247",
            durationSeconds = 250
        )

        val eval = TrackIdentityMatcher.evaluate(
            requestedTitle = "Aaradhike",
            requestedArtist = "Sooraj Santhosh",
            requestedDurationSec = 250,
            candidate = candidate
        )

        assertTrue("Candidate with artist in title segments must be ACCEPTED", eval.isAccepted)
        assertEquals(MatchTier.TITLE_AND_ARTIST, eval.matchTier)
        assertTrue(eval.isArtistMatched)
    }

    @Test
    fun `27 - conflicting language between Indian language and English is REJECTED`() {
        val candidate = TrackIdentityMatcher.CandidateTrack(
            providerId = "youtube",
            providerTrackId = "cand_english_conflict",
            title = "Stay",
            artist = "The Kid LAROI",
            channelName = "Columbia Records",
            language = "english",
            durationSeconds = 141
        )

        val eval = TrackIdentityMatcher.evaluate(
            requestedTitle = "Stay",
            requestedArtist = "Vijay Yesudas",
            requestedDurationSec = 141,
            candidate = candidate,
            requestedLanguage = "malayalam"
        )

        assertFalse("Conflicting language (English candidate for Malayalam request) must be REJECTED", eval.isAccepted)
    }

    @Test
    fun `28 - isKaraokeOrIncompatibleTrack detects karaoke and instrumental variants`() {
        assertTrue(TrackIdentityMatcher.isKaraokeOrIncompatibleTrack("Aaradhike Karaoke with Lyrics"))
        assertTrue(TrackIdentityMatcher.isKaraokeOrIncompatibleTrack("Aaradhike (Instrumental)"))
        assertTrue(TrackIdentityMatcher.isKaraokeOrIncompatibleTrack("Aaradhike", channel = "Malayalam Karaoke Hub"))
        assertTrue(TrackIdentityMatcher.isKaraokeOrIncompatibleTrack("Aaradhike (Minus One Track)"))
        assertTrue(TrackIdentityMatcher.isKaraokeOrIncompatibleTrack("Aaradhike (Acoustic Cover)"))
        assertTrue(TrackIdentityMatcher.isKaraokeOrIncompatibleTrack("Aaradhike (Slowed + Reverb)"))

        assertFalse(TrackIdentityMatcher.isKaraokeOrIncompatibleTrack("Aaradhike", "Sooraj Santhosh", "Muzik247"))
        assertFalse(TrackIdentityMatcher.isKaraokeOrIncompatibleTrack("Discovering Love", "Artist Name"))
    }
}


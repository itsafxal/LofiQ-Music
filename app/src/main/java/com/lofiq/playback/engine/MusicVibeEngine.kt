package com.lofiq.playback.engine

import com.lofiq.core.model.Song

enum class MusicLanguage(val displayName: String, val searchKeyword: String) {
    MALAYALAM("Malayalam", "Malayalam"),
    TAMIL("Tamil", "Tamil"),
    HINDI("Hindi", "Hindi Bollywood"),
    TELUGU("Telugu", "Telugu"),
    PUNJABI("Punjabi", "Punjabi"),
    KANNADA("Kannada", "Kannada"),
    ENGLISH("English", "English"),
    OTHER("All-Rounder", "")
}

enum class MusicMood(val displayName: String, val searchTags: List<String>, val emoji: String) {
    ROMANTIC("Romantic & Love", listOf("Romantic", "Love Hits", "Melodies", "Kadhal Songs", "Pranayam"), "❤️"),
    TRIP_VIBES("Trip & Drive Vibes", listOf("Trip Songs", "Night Drive", "Travel Vibe", "Bass Beats", "Party Dance"), "🚗"),
    BREAKUP_SAD("Sad & Heartbreak", listOf("Sad Songs", "Breakup Hits", "Emotional Melodies", "Heartbreak"), "💔"),
    CHILL_LOFI("Chill & Acoustic", listOf("Chill Acoustic", "Lo-Fi Beats", "Unplugged", "Calm Relax", "Midnight Vibe"), "☕"),
    RAP_HIPHOP("Rap & Hip-Hop", listOf("Rap Hits", "Hip-Hop Beats", "Drill Trap", "Street Beats"), "🔥"),
    DEVOTIONAL("Devotional & Spiritual", listOf("Devotional Hits", "Spiritual", "Bhajan", "Keerthanam"), "🙏"),
    ALL_ROUNDER("Vibe Hits", listOf("Top Hits", "Popular Songs", "Best Tracks"), "✨")
}

data class VibeClassification(
    val language: MusicLanguage,
    val mood: MusicMood
) {
    val displayTag: String
        get() = "${mood.emoji} ${language.displayName} ${mood.displayName}"
}

object MusicVibeEngine {

    // Common Malayalam indicators
    private val malayalamKeywords = listOf(
        "malayalam", "mollywood", "kerala", "muzik247", "saregama malayalam", "goodwill",
        "manorama music", "satyam audios", "millennium audios", "123 musiq", "think music malayalam",
        "sushin", "shyam", "shaan rahman", "hesham", "abdul wahab", "vidyasagar", "deepak dev",
        "gopi sundar", "gopisundar", "vineeth sreenivasan", "yesudas", "chithra", "chitra", "job kurian",
        "rex vijayan", "sithara", "krishnakumar", "vijay yesudas", "sujatha", "jassie gift", "sayanora",
        "sooraj santhosh", "sachin warrier", "bijibal", "jayachandran", "alphons", "aavesham", "manjummel",
        "varshangalkku", "premalu", "minnal", "hridayam", "kumbalangi", "premam", "mohanlal", "mammootty",
        "nivin", "dulquer", "dq", "prithviraj", "tovino", "fahadh", "asif ali", "kunchacko", "dabzee",
        "baby jean", "fejo", "neeraj madhav", "hanumankind", "vishnu vijay", "christo xavier", "vinayak sasikumar",
        "jakes bejoy", "bougainvillea", "aaradhike", "darshana", "illuminati", "jaada", "scene mone", "arm",
        "bramayugam", "neru", "turbo", "thalavara", "katha", "ente", "njan", "oru", "swapnam", "sneham",
        "kaathal", "mazha", "puzha", "kannil", "neeyum", "njanum", "raave", "venal", "omana", "kannodu",
        "mg sreekumar", "m.g. sreekumar", "unni menon", "madhu balakrishnan", "najim arshad", "shweta mohan",
        "aksomaniac", "axomanic", "circle tone", "circletone", "m.h.r", "mhr", "bhumi", "amsham"
    )

    // Common Tamil indicators
    private val tamilKeywords = listOf(
        "tamil", "kollywood", "sai abhyankkar", "sai abhyankar", "rathamaa", "aasa kooda", "katchi sera",
        "anirudh", "ar rahman", "a.r. rahman", "rahman", "yuvan", "harris jayaraj", "sid sriram", "imman",
        "santhosh narayanan", "ilaiyaraaja", "ilayaraja", "spb", "balasubrahmanyam", "pradeep kumar",
        "sean roldan", "gv prakash", "g.v. prakash", "vijay antony", "hiphop tamizha", "siddhu kumar",
        "stephen zechariah", "paal dabba", "amrit ramnath", "leon james", "dhibu ninan", "sam cs", "ghibran",
        "vijay", "thalapathy", "ajith", "thala", "surya", "suriya", "rajini", "superstar", "dhanush",
        "sivakarthikeyan", "sk", "kamal", "vikram", "leo", "jailer", "goat", "master", "kadhal", "anbe",
        "unnaale", "kanave", "vizhi", "uyire", "pirivu", "vali", "kuthu", "marana", "rowdy", "arabic kuthu",
        "hukum", "badass", "naa ready", "vikram vedha", "devara", "amaran", "dragon", "viduthalai"
    )

    // Common Hindi indicators
    private val hindiKeywords = listOf(
        "hindi", "bollywood", "t-series", "tseries", "zee music", "yrf", "sony music india",
        "tips official", "speed records", "white hill", "desi music factory", "saregama music",
        "arijit", "shreya ghoshal", "pritam", "vishal-shekhar", "sachin-jigar", "atif aslam",
        "kk", "mohit chauhan", "jubin nautiyal", "badshah", "neha kakkar", "amit trivedi",
        "alka yagnik", "kumar sanu", "sonu nigam", "darshan raval", "armaan malik", "mithoon",
        "honey singh", "yo yo", "tanishk bagchi", "b praak", "jasleen royal", "anuv jain",
        "prateek kuhad", "stebin ben", "vishal mishra", "king", "ap dhillon", "seedhe maut",
        "mc stan", "raftaar", "kesariya", "tum hi ho", "channa mereya", "ishq", "mohabbat",
        "dil", "pyaar", "zindagi", "sanam", "tere", "meri", "hum", "tum", "kahani", "duniya",
        "raataan lambiyan", "apna bana le", "chaleya", "o maahi", "ve kamleya", "animal", "jawan", "pathaan"
    )

    // Common Telugu indicators
    private val teluguKeywords = listOf(
        "telugu", "tollywood", "aditya music", "lahari music", "devi sri prasad", "dsp", "thaman",
        "anup rubens", "keeravaani", "keeravani", "anurag kulkarni", "ram miriyala", "ramajogayya",
        "prabhas", "allu arjun", "mahesh babu", "ntr", "ram charan", "pushpa", "rrr", "devara",
        "kalki", "salaar", "samajavaragamana", "butta bomma", "oo antava", "srivalli", "prema",
        "manasu", "cheliya", "ninnu", "naa", "neeli"
    )

    // Common Punjabi indicators
    private val punjabiKeywords = listOf(
        "punjabi", "pollywood", "diljit", "dosanjh", "ap dhillon", "karan aujla", "sidhu moose wala", "sidhu",
        "moosewala", "shubh", "gurdas maan", "jassie gill", "harrdy sandhu", "ammy virk", "jaani",
        "maninder buttar", "guru randhawa", "sukhe", "mankirt aulakh", "cheques", "no love", "softly",
        "goat", "lover", "born to shine", "proper patola", "high rated"
    )

    // Mood indicators
    private val romanticKeywords = listOf(
        "romantic", "romance", "love", "melody", "melodies", "kadhal", "kaadhal", "pranayam", "premam",
        "ishq", "mohabbat", "duet", "heart", "soulmate", "vizhi", "kannil", "sneham", "unakkaga", "kanave",
        "malare", "aaradhike", "darshana", "uyire", "thalli pogathey", "tum hi ho", "kesariya", "enodu nee",
        "kannazhaga", "anbe", "couple", "valentines", "sweet", "propose", "crush", "in love", "feel good love",
        "pyaar", "ishqbaazi", "saibo", "humsafar", "zaalima", "samajavaragamana", "amsham"
    )

    private val tripKeywords = listOf(
        "trip", "travel", "drive", "road", "ride", "night drive", "wanderlust", "journey", "highway",
        "vibe", "vibes", "party", "dance", "beat", "bass", "boosted", "phonk", "edm", "club", "kuthu",
        "fast", "aavesham", "illuminati", "jaada", "scene", "fire", "energy", "speed", "drift", "banger",
        "hype", "pump", "workout", "dj", "remix", "drop", "monster", "badass", "naa ready", "tauba tauba"
    )

    private val sadKeywords = listOf(
        "breakup", "break up", "sad", "tear", "tears", "pain", "crying", "heartbreak", "heart broken",
        "alone", "lonely", "dard", "pirivu", "vali", "sogam", "broken", "maranam", "dheema", "kanneer",
        "azhugai", "thaniye", "channa mereya", "judai", "tadap", "aloneness", "emotional", "depressed",
        "left me", "goodbye", "loss", "grief", "memories", "cry", "tanhaai", "roothna"
    )

    private val chillKeywords = listOf(
        "chill", "lofi", "lo-fi", "acoustic", "unplugged", "calm", "relax", "relaxing", "peace", "peaceful",
        "coffee", "breeze", "rain", "sleep", "soothing", "slowed", "reverb", "midnight", "soft",
        "aesthetic", "sunset", "rooftop", "lazy", "study", "ambient", "serene", "meditation"
    )

    private val rapKeywords = listOf(
        "rap", "hip hop", "hiphop", "hip-hop", "drill", "trap", "mc", "cypher", "flow", "bars", "diss",
        "fejo", "neeraj madhav", "dabzee", "baby jean", "hanumankind", "divine", "emiway", "mc stan",
        "seedhe maut", "krsna", "kr\$na", "raftaar", "badshah rap", "yo yo rap", "underground rap"
    )

    private val devotionalKeywords = listOf(
        "devotional", "bhajan", "keerthanam", "stotram", "aarti", "qawwali", "naath", "prayer",
        "harivarasanam", "ayyappa", "krishna", "shiva", "allah", "jesus", "gospel", "mantra", "chant",
        "hanuman chalisa", "shiva tandav", "suprabhatam", "anbe sivam", "spiritual"
    )

    fun classify(song: Song): VibeClassification {
        val lang = detectLanguage(song.title, song.artist, song.album)
        val mood = detectMood(song.title, song.artist, song.album)
        return VibeClassification(lang, mood)
    }

    fun detectLanguage(title: String, artist: String, album: String = ""): MusicLanguage {
        val text = "$title $artist $album".lowercase()

        // 1. Unicode script detection
        if (text.any { it in '\u0D00'..'\u0D7F' }) return MusicLanguage.MALAYALAM
        if (text.any { it in '\u0B80'..'\u0BFF' }) return MusicLanguage.TAMIL
        if (text.any { it in '\u0900'..'\u097F' }) return MusicLanguage.HINDI
        if (text.any { it in '\u0C00'..'\u0C7F' }) return MusicLanguage.TELUGU
        if (text.any { it in '\u0C80'..'\u0CFF' }) return MusicLanguage.KANNADA

        // 2. Exact keyword and artist matching
        if (malayalamKeywords.any { text.contains(it) }) return MusicLanguage.MALAYALAM
        if (tamilKeywords.any { text.contains(it) }) return MusicLanguage.TAMIL
        if (hindiKeywords.any { text.contains(it) }) return MusicLanguage.HINDI
        if (teluguKeywords.any { text.contains(it) }) return MusicLanguage.TELUGU
        if (punjabiKeywords.any { text.contains(it) }) return MusicLanguage.PUNJABI

        // Check for common western/english artists
        val englishIndicators = listOf(
            "ed sheeran", "taylor swift", "weeknd", "justin bieber", "drake", "billie eilish",
            "post malone", "bruno mars", "dua lipa", "eminem", "coldplay", "imagine dragons",
            "ariana grande", "maroon 5", "charlie puth", "shawn mendes", "olivia rodrigo",
            "chainsmokers", "marshmello", "alan walker", "david guetta", "calvin harris", "english"
        )
        if (englishIndicators.any { text.contains(it) }) return MusicLanguage.ENGLISH

        return MusicLanguage.OTHER
    }

    fun detectMood(title: String, artist: String, album: String = ""): MusicMood {
        val text = "$title $artist $album".lowercase()

        if (devotionalKeywords.any { text.contains(it) }) return MusicMood.DEVOTIONAL
        if (rapKeywords.any { text.contains(it) }) return MusicMood.RAP_HIPHOP
        if (sadKeywords.any { text.contains(it) }) return MusicMood.BREAKUP_SAD
        if (tripKeywords.any { text.contains(it) }) return MusicMood.TRIP_VIBES
        if (romanticKeywords.any { text.contains(it) }) return MusicMood.ROMANTIC
        if (chillKeywords.any { text.contains(it) }) return MusicMood.CHILL_LOFI

        return MusicMood.ROMANTIC // Default fallback vibe is melodic / romantic
    }

    // Key modern composers & hitmakers per ecosystem for companion recommendation boost
    private val topTamilModernArtists = listOf(
        "sai abhyankkar", "anirudh", "ar rahman", "a.r. rahman", "santhosh narayanan",
        "pradeep kumar", "sean roldan", "harris jayaraj", "yuvan", "gv prakash", "sid sriram", "leon james"
    )

    private val topMalayalamModernArtists = listOf(
        "sushin shyam", "sushin", "dabzee", "baby jean", "hanumankind", "hesham abdul wahab", "hesham",
        "shaan rahman", "gopi sundar", "rex vijayan", "job kurian", "vishnu vijay", "christo xavier",
        "jakes bejoy", "vineeth sreenivasan", "k.j. yesudas", "yesudas", "k.s. chithra", "chithra",
        "sujatha", "vijay yesudas", "sithara", "bijibal", "m jayachandran", "deepak dev", "vidyasagar",
        "aksomaniac", "axomanic", "circle tone", "circletone", "m.h.r", "mhr"
    )

    private val topHindiModernArtists = listOf(
        "arijit singh", "arijit", "pritam", "sachin-jigar", "vishal mishra", "jasleen royal",
        "anuv jain", "prateek kuhad", "diljit dosanjh", "b praak", "shreya ghoshal", "sonu nigam",
        "badshah", "neha kakkar", "armaan malik", "darshan raval", "kk", "atif aslam"
    )

    // Global curated popular artist directory for 80%+ Popular Artists ranking boost
    val popularArtistCatalog = setOf(
        // Malayalam
        "sushin shyam", "sushin", "hesham abdul wahab", "hesham", "shaan rahman", "vidyasagar", "deepak dev",
        "gopi sundar", "vineeth sreenivasan", "k.j. yesudas", "yesudas", "k.s. chithra", "chithra", "sujatha",
        "job kurian", "rex vijayan", "sithara", "vijay yesudas", "bijibal", "m jayachandran", "vishnu vijay",
        "christo xavier", "jakes bejoy", "dabzee", "baby jean", "fejo", "neeraj madhav", "hanumankind",
        "aksomaniac", "axomanic", "circle tone", "circletone", "m.h.r", "mhr",
        // Tamil
        "anirudh ravichander", "anirudh", "a.r. rahman", "ar rahman", "yuvan shankar raja", "yuvan",
        "harris jayaraj", "sid sriram", "santhosh narayanan", "ilaiyaraaja", "ilayaraja", "pradeep kumar",
        "gv prakash", "sai abhyankkar", "dhanush", "vijay antony", "sean roldan", "hiphop tamizha",
        // Hindi & Bollywood
        "arijit singh", "arijit", "shreya ghoshal", "pritam", "vishal-shekhar", "sachin-jigar", "atif aslam",
        "kk", "mohit chauhan", "jubin nautiyal", "badshah", "neha kakkar", "amit trivedi", "alka yagnik",
        "kumar sanu", "sonu nigam", "darshan raval", "armaan malik", "mithoon", "honey singh", "b praak",
        "jasleen royal", "anuv jain", "prateek kuhad", "vishal mishra", "king", "diljit dosanjh",
        // Global / English
        "taylor swift", "the weeknd", "ed sheeran", "billie eilish", "justin bieber", "drake", "bruno mars",
        "coldplay", "dua lipa", "post malone", "eminem", "ariana grande", "charlie puth", "shawn mendes"
    )

    fun getCompanionLanguages(lang: MusicLanguage): List<MusicLanguage> {
        // Enforce strict language isolation: never cross-pollinate Malayalam with Tamil/Hindi
        return if (lang != MusicLanguage.OTHER) listOf(lang) else emptyList()
    }

    fun getContextualSearchQueries(seedSong: Song, seedVibe: VibeClassification): List<String> {
        val queries = mutableListOf<String>()
        val cleanArtist = seedSong.artist.replace(Regex("""(?i)\b(feat|ft|official|audio|video|topic)\b.*"""), "").trim()

        // 1. Same artist's greatest hits
        if (cleanArtist.isNotBlank() && !cleanArtist.equals("Unknown", ignoreCase = true)) {
            queries.add("$cleanArtist Top Songs")
            queries.add("$cleanArtist Hits")
        }

        // 2. Modern Trending Songs in the exact same language
        when (seedVibe.language) {
            MusicLanguage.MALAYALAM -> {
                queries.add("Malayalam Top Hits")
                queries.add("Sushin Shyam Top Hits")
                queries.add("Malayalam Trending Songs 2026")
                queries.add("Hesham Abdul Wahab Hits")
            }
            MusicLanguage.TAMIL -> {
                queries.add("Tamil Trending Songs 2026")
                queries.add("Anirudh Ravichander Top Hits")
                queries.add("AR Rahman Tamil Top Hits")
            }
            MusicLanguage.HINDI -> {
                queries.add("Top Bollywood Hits 2026")
                queries.add("Arijit Singh Top Hits")
                queries.add("Latest Hindi Love Songs")
            }
            MusicLanguage.TELUGU -> {
                queries.add("Telugu Trending Songs 2026")
                queries.add("Top Telugu Hits")
            }
            MusicLanguage.PUNJABI -> {
                queries.add("Punjabi Trending Hits 2026")
                queries.add("Diljit Dosanjh Top Hits")
            }
            else -> {
                queries.add("Top Trending Global Hits")
            }
        }

        return queries.distinct()
    }

    fun rankAndFilterRecommendations(
        candidates: List<Song>,
        seedSong: Song,
        seedVibe: VibeClassification,
        limit: Int = 15
    ): List<Song> {
        val seedTitleLower = seedSong.title.lowercase()
        val seedArtistLower = seedSong.artist.lowercase()
        val seedIsOld = seedTitleLower.contains("80s") || seedTitleLower.contains("70s") || seedTitleLower.contains("retro") || seedTitleLower.contains("vintage")

        val scored = candidates
            .filter { it.id != seedSong.id }
            .distinctBy { it.id }
            .mapNotNull { song ->
                val candTitleLower = song.title.lowercase()
                val candArtistLower = song.artist.lowercase()

                // 1. Filter out junk, karaoke, ringtones, status videos, and non-music items
                val junkKeywords = listOf(
                    "karaoke", "ringtone", "status", "bgm", "instrumental", "dialogue",
                    "trailer", "teaser", "jukebox", "8d audio", "slowed", "reverb", "reaction",
                    "whatsapp", "non stop", "mashup"
                )
                if (junkKeywords.any { candTitleLower.contains(it) }) {
                    return@mapNotNull null
                }

                // 2. Filter out obscure old songs unless seed song itself was retro/oldies
                if (!seedIsOld) {
                    val oldKeywords = listOf(
                        "80s", "70s", "90s", "retro", "golden hits", "evergreen hits",
                        "old is gold", "vintage", "old songs", "purattasi", "padalgal", "old hits"
                    )
                    if (oldKeywords.any { candTitleLower.contains(it) }) {
                        return@mapNotNull null
                    }
                }

                val candLang = detectLanguage(song.title, song.artist, song.album)
                val candMood = detectMood(song.title, song.artist, song.album)

                // STRICT LANGUAGE PURITY:
                // If seed language is known, NEVER allow a different known language or unverified candidate!
                if (seedVibe.language != MusicLanguage.OTHER) {
                    val isSameArtist = candArtistLower.contains(seedArtistLower) ||
                            seedArtistLower.contains(candArtistLower) ||
                            candArtistLower.equals(seedArtistLower, ignoreCase = true)

                    if (!isSameArtist) {
                        // Reject if candidate belongs to a different language
                        if (candLang != seedVibe.language) {
                            return@mapNotNull null
                        }
                    }
                }

                var score = 0

                // Exact language match
                if (candLang == seedVibe.language && candMood == seedVibe.mood) {
                    score += 100
                } else if (candLang == seedVibe.language) {
                    score += 85
                } else if (candMood == seedVibe.mood) {
                    score += 40
                } else {
                    score += 20
                }

                // Massive Boost: Exact same artist or shared co-artist (e.g. Axomanic, MHR, Circletone)
                val seedArtists = seedArtistLower.split(Regex("""(?i),\s*|\s+&\s+|\s*/\s*|;\s*|\s+and\s+|\s+with\s+""")).map { it.trim() }.filter { it.length > 2 }
                val candArtists = candArtistLower.split(Regex("""(?i),\s*|\s+&\s+|\s*/\s*|;\s*|\s+and\s+|\s+with\s+""")).map { it.trim() }.filter { it.length > 2 }
                val sharesArtist = (seedArtists.isNotEmpty() && seedArtists.any { s -> candArtists.any { c -> c.contains(s) || s.contains(c) } }) ||
                        candArtistLower.contains(seedArtistLower) ||
                        seedArtistLower.contains(candArtistLower) ||
                        candArtistLower.equals(seedArtistLower, ignoreCase = true)

                if (sharesArtist && seedArtistLower.isNotBlank() && !seedArtistLower.equals("unknown", ignoreCase = true)) {
                    score += 95
                }

                // 80%+ Popular Artist Curation: Strong boost for verified popular hitmakers
                val isPopularArtist = popularArtistCatalog.any { candArtistLower.contains(it) || candTitleLower.contains(it) }
                if (isPopularArtist) {
                    score += 65
                }

                // Boost for top companion modern hitmakers in the same ecosystem
                val companionList = when (seedVibe.language) {
                    MusicLanguage.TAMIL -> topTamilModernArtists
                    MusicLanguage.MALAYALAM -> topMalayalamModernArtists
                    MusicLanguage.HINDI -> topHindiModernArtists
                    else -> emptyList()
                }
                if (companionList.any { candArtistLower.contains(it) || candTitleLower.contains(it) }) {
                    score += 40
                }

                song to score
            }

        // Sort descending by score
        return scored
            .sortedByDescending { it.second }
            .map { it.first }
            .take(limit)
    }
}

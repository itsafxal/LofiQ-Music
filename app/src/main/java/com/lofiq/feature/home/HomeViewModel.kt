package com.lofiq.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lofiq.core.common.LofiQLogger
import com.lofiq.core.model.Song
import com.lofiq.data.repository.LibraryRepository
import com.lofiq.data.repository.MusicRepository
import com.lofiq.playback.engine.PlaybackEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ShelfLayout {
    CAROUSEL,       // Standard 140dp boxy card
    COMPACT_GRID,   // 2-row horizontal compact list
    ARTIST_CIRCLE,  // Circular artist avatar cards
    FEATURED_LARGE  // Large 215dp featured cards ("Albums & singles" / "New Releases")
}

typealias ArtistCardData = com.lofiq.data.model.ArtistCardData

data class HomeShelf(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val layout: ShelfLayout = ShelfLayout.CAROUSEL,
    val songs: List<Song> = emptyList(),
    val artists: List<ArtistCardData> = emptyList(),
    val isLoading: Boolean = false
)

data class TasteArtistScore(
    val artistName: String,
    val score: Int,
    val sampleSongs: List<Song> = emptyList()
)

data class UserTaste(
    val topArtists: List<TasteArtistScore> = emptyList(),
    val dominantLanguage: String? = null,
    val seedRecentSong: Song? = null,
    val heavyRotationSongs: List<Song> = emptyList()
)

data class HomeUiState(
    val shelves: List<HomeShelf> = emptyList(),
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val selectedCategory: String = "All",
    val availableCategories: List<String> = listOf(
        "All",
        "Malayalam",
        "Tamil",
        "Hindi",
        "Trending India",
        "Made For You",
        "Romantic & Chill",
        "Party & Dance",
        "Workout",
        "Lo-Fi"
    )
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val libraryRepository: LibraryRepository,
    private val authRepository: com.lofiq.auth.AuthRepository,
    private val spotifyAuthRepository: com.lofiq.data.spotify.SpotifyAuthRepository,
    private val artistImageProvider: com.lofiq.data.artist.ArtistImageProvider,
    private val homeFeedCacheManager: com.lofiq.data.repository.HomeFeedCacheManager,
    private val preferences: com.lofiq.core.datastore.LofiQPreferences,
    val playbackEngine: PlaybackEngine,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    companion object {
        private val MALAYALAM_EXCLUDE_KEYWORDS = listOf(
            "tamil song", "tamil lyric", "tamil video", "tamil version", "(tamil)", "[tamil]", "- tamil",
            "telugu song", "telugu lyric", "telugu video", "(telugu)", "[telugu]", "- telugu",
            "kannada song", "(kannada)", "[kannada]",
            "hindi song", "bollywood", "kollywood", "tollywood"
        )

        private val MALAYALAM_ARTISTS = listOf(
            "sushin", "hesham", "vineeth", "yesudas", "chithra", "vidyasagar", "shaan rahman",
            "gopi sundar", "deepak dev", "dabzee", "baby jean", "fejo", "hanumankind",
            "rex vijayan", "job kurian", "sithara", "mg sreekumar", "m.g. sreekumar", "sujatha",
            "haricharan", "vijay yesudas", "sachin balu", "govind vasantha", "christo xavier",
            "jakes bejoy", "alphons", "bijibal", "ouseppachan", "raveendran", "johnson",
            "sharreth", "kailas menon", "neeraj madhav", "avial", "masala coffee",
            "thaikkudam bridge", "manjummel", "aavesham", "premalu", "bramayugam",
            "varshangalkku", "turbo", "kishkindha", "arm", "bougainvillea"
        )

        private val TAMIL_EXCLUDE_KEYWORDS = listOf(
            "malayalam song", "malayalam lyric", "malayalam video", "malayalam version", "(malayalam)", "[malayalam]", "- malayalam",
            "telugu song", "telugu lyric", "(telugu)", "[telugu]", "- telugu",
            "hindi song", "bollywood", "mollywood", "tollywood"
        )

        private val TAMIL_ARTISTS = listOf(
            "anirudh", "a.r. rahman", "ar rahman", "yuvan", "harris jayaraj", "ilaiyaraaja",
            "ilayaraja", "santhosh narayanan", "sid sriram", "sean roldan", "pradeep kumar",
            "spb", "s.p. balasubrahmanyam", "balasubrahmanyam", "hiphop tamizha", "g.v. prakash",
            "gv prakash", "d. imman", "karthi", "vijay", "ajith", "dhanush", "suriya",
            "silambarasan", "u1", "dhibu ninan", "sam c.s.", "leon james", "ghibran", "deva"
        )

        private val HINDI_EXCLUDE_KEYWORDS = listOf(
            "malayalam song", "(malayalam)", "[malayalam]", "- malayalam",
            "tamil song", "(tamil)", "[tamil]", "- tamil",
            "telugu song", "(telugu)", "[telugu]",
            "kannada song", "(kannada)", "[kannada]",
            "mollywood", "kollywood", "tollywood"
        )

        private val HINDI_ARTISTS = listOf(
            "arijit", "shreya ghoshal", "pritam", "atif aslam", "kk", "sonu nigam",
            "vishal mishra", "jasleen royal", "badshah", "honey singh", "diljit",
            "jubin", "sachin-jigar", "sachin jigar", "tanishk bagchi", "kumar sanu",
            "udit narayan", "alka yagnik", "lata mangeshkar", "kishore kumar",
            "rd burman", "mohit chauhan", "armaan malik", "darshan raval", "neha kakkar",
            "sunidhi chauhan", "amit trivedi", "shankar ehsaan loy", "vishal-shekhar"
        )

        private val EXCLUDED_ARTIST_TOKENS = setOf(
            "unknown artist", "unknown", "various artists", "various", "artist",
            "youtube", "topic", "audio", "music", "soundtrack", "ost", "theme",
            "t-series", "sony music", "zee music", "think music", "saregama",
            "speed records", "yrf", "tips", "lahari music", "aditya music", "muzik247"
        )
    }

    val isYouTubeBannerPermanentlyDismissed: StateFlow<Boolean> = preferences.isYouTubeBannerPermanentlyDismissed
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    private val _isSessionBannerDismissed = MutableStateFlow(false)
    val isSessionBannerDismissed: StateFlow<Boolean> = _isSessionBannerDismissed.asStateFlow()

    fun dismissYouTubeBanner(permanently: Boolean) {
        if (permanently) {
            viewModelScope.launch {
                preferences.setYouTubeBannerPermanentlyDismissed(true)
            }
        } else {
            _isSessionBannerDismissed.value = true
        }
    }

    val userAccount: StateFlow<com.lofiq.auth.UserAccount> = authRepository.userAccount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = com.lofiq.auth.UserAccount.GUEST
        )

    val spotifyAccount: StateFlow<com.lofiq.data.spotify.SpotifyAccount> = spotifyAuthRepository.spotifyAccount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = com.lofiq.data.spotify.SpotifyAccount.GUEST
        )

    private val _isAuthenticating = MutableStateFlow(false)
    val isAuthenticating: StateFlow<Boolean> = _isAuthenticating.asStateFlow()

    private val _artistDetailTracks = MutableStateFlow<List<Song>>(emptyList())
    val artistDetailTracks: StateFlow<List<Song>> = _artistDetailTracks.asStateFlow()

    private val _isArtistDetailLoading = MutableStateFlow(false)
    val isArtistDetailLoading: StateFlow<Boolean> = _isArtistDetailLoading.asStateFlow()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Remote category caches
    private val shelfCache = mutableMapOf<String, List<Song>>()
    private val artistCache = mutableListOf<ArtistCardData>()
    private var artistBasedSongs: Pair<String, List<Song>>? = null

    // Local cached data
    private var historySongs: List<Song> = emptyList()
    private var favoriteSongs: List<Song> = emptyList()
    private var cachedUserTaste: UserTaste? = null

    private var currentFeedBatch = 0
    private var isLoadingMoreShelves = false

    init {
        observeLocalData()
        observeAccountChanges()
        restoreFeedFromCache()
        loadInitialFeed()
    }

    private fun observeAccountChanges() {
        viewModelScope.launch {
            combine(spotifyAccount, userAccount) { sp, yt ->
                Pair(sp, yt)
            }.collect { (sp, yt) ->
                if (sp.isLoggedIn) {
                    refreshSpotifyPersonalization()
                }
                if (yt.isLoggedIn) {
                    refreshYouTubePersonalization()
                }
                rebuildShelves()
            }
        }
    }

    private fun refreshSpotifyPersonalization() {
        viewModelScope.launch {
            try {
                val spotifyFavorites = favoriteSongs.filter { it.providerId == "spotify" || it.id.startsWith("sp_") }
                val topArtist = spotifyFavorites
                    .map { it.artist.trim() }
                    .filter { it.isNotBlank() && it != "Unknown Artist" }
                    .groupingBy { it }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key

                if (topArtist != null) {
                    val recommended = musicRepository.searchSongs("$topArtist hits songs", limit = 20)
                    if (recommended.isNotEmpty()) {
                        shelfCache["spotify_recommendations"] = recommended
                        homeFeedCacheManager.saveShelf("spotify_recommendations", recommended)
                        rebuildShelves()
                    }
                } else {
                    val seedSong = spotifyFavorites.firstOrNull()
                    if (seedSong != null) {
                        val recommended = musicRepository.searchSongs("${seedSong.artist} ${seedSong.title}", limit = 20)
                        if (recommended.isNotEmpty()) {
                            shelfCache["spotify_recommendations"] = recommended
                            homeFeedCacheManager.saveShelf("spotify_recommendations", recommended)
                            rebuildShelves()
                        }
                    }
                }
            } catch (e: Exception) {
                LofiQLogger.w(LofiQLogger.Category.UI, "Error refreshing Spotify recommendations: ${e.message}")
            }
        }
    }

    private fun refreshYouTubePersonalization() {
        viewModelScope.launch {
            try {
                val ytFavorites = favoriteSongs.filter {
                    !it.id.startsWith("sp_") && !it.id.startsWith("local_") &&
                    it.providerId != "local_download" && it.providerId != "spotify" && it.providerId != "local"
                }
                val seedArtist = ytFavorites.firstOrNull()?.artist ?: historySongs.firstOrNull()?.artist
                if (!seedArtist.isNullOrBlank()) {
                    val recommended = musicRepository.searchSongs("$seedArtist top hits", limit = 20)
                    if (recommended.isNotEmpty()) {
                        shelfCache["youtube_mix"] = recommended
                        homeFeedCacheManager.saveShelf("youtube_mix", recommended)
                        rebuildShelves()
                    }
                }
            } catch (e: Exception) {
                LofiQLogger.w(LofiQLogger.Category.UI, "Error refreshing YouTube recommendations: ${e.message}")
            }
        }
    }

    private fun restoreFeedFromCache() {
        // Fast path 1: Instant in-memory cache (< 1ms)
        val memShelves = homeFeedCacheManager.getMemoryCachedShelves()
        val memArtists = homeFeedCacheManager.getMemoryCachedArtists()
        if (memShelves.isNotEmpty()) {
            shelfCache.putAll(memShelves)
            if (memArtists.isNotEmpty()) {
                artistCache.clear()
                artistCache.addAll(memArtists)
            }
            rebuildShelves()
            _uiState.update { it.copy(isInitialLoading = false) }
        }

        // Fast path 2: Async restore from Room disk cache
        viewModelScope.launch {
            val (diskShelves, diskArtists) = homeFeedCacheManager.loadFromDisk()
            if (diskShelves.isNotEmpty()) {
                shelfCache.putAll(diskShelves)
                if (diskArtists.isNotEmpty()) {
                    artistCache.clear()
                    artistCache.addAll(diskArtists)
                }
                rebuildShelves()
                _uiState.update { it.copy(isInitialLoading = false) }
            }
        }
    }

    private fun extractArtistsFromSong(song: Song): List<String> {
        val raw = song.artist
        if (raw.isBlank() || raw.equals("unknown artist", ignoreCase = true)) return emptyList()

        val cleaned = raw
            .replace(Regex("""(?i)\b(feat\.|ft\.|featuring)\b.*"""), "")
            .replace(Regex("""(?i)\(.*\)|\[.*\]"""), "")
            .trim()

        val tokens = cleaned.split(Regex("""(?i),\s*|\s+&\s+|\s*/\s*|;\s*|\s+and\s+|\s+with\s+"""))
            .map { it.trim().trim('"', '\'', '-', '|') }
            .filter { token ->
                token.length >= 2 &&
                !EXCLUDED_ARTIST_TOKENS.contains(token.lowercase()) &&
                !token.all { it.isDigit() }
            }

        return if (tokens.isNotEmpty()) tokens else listOf(raw.trim())
    }

    private fun extractUserTaste(history: List<Song>, favorites: List<Song>): UserTaste {
        val artistScores = mutableMapOf<String, Int>()
        val artistSampleSongs = mutableMapOf<String, MutableList<Song>>()
        val songFrequency = mutableMapOf<String, Int>()
        val songMap = mutableMapOf<String, Song>()

        for (song in favorites) {
            songMap[song.id] = song
            songFrequency[song.id] = (songFrequency[song.id] ?: 0) + 3
            val artists = extractArtistsFromSong(song)
            for (artist in artists) {
                val key = artist.lowercase()
                artistScores[key] = (artistScores[key] ?: 0) + 3
                artistSampleSongs.getOrPut(key) { mutableListOf() }.add(song)
            }
        }

        for (song in history) {
            songMap[song.id] = song
            val multiplier = song.playCount.coerceAtLeast(1L).toInt()
            songFrequency[song.id] = (songFrequency[song.id] ?: 0) + (2 * multiplier)
            val artists = extractArtistsFromSong(song)
            for (artist in artists) {
                val key = artist.lowercase()
                artistScores[key] = (artistScores[key] ?: 0) + (2 * multiplier)
                val list = artistSampleSongs.getOrPut(key) { mutableListOf() }
                if (list.none { it.id == song.id }) {
                    list.add(song)
                }
            }
        }

        val sortedArtistScores = artistScores.entries
            .sortedByDescending { it.value }
            .mapNotNull { entry ->
                val sample = artistSampleSongs[entry.key]?.firstOrNull() ?: return@mapNotNull null
                val sampleArtists = extractArtistsFromSong(sample)
                val formattedName = sampleArtists.firstOrNull { it.equals(entry.key, ignoreCase = true) }
                    ?: sample.artist.takeIf { it.isNotBlank() }
                    ?: entry.key.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                TasteArtistScore(
                    artistName = formattedName,
                    score = entry.value,
                    sampleSongs = artistSampleSongs[entry.key]?.distinctBy { it.id } ?: emptyList()
                )
            }

        val allSongs = (favorites + history).distinctBy { it.id }
        var malScore = 0
        var tamScore = 0
        var hinScore = 0
        for (s in allSongs) {
            val t = "${s.title} ${s.artist} ${s.album}".lowercase()
            if (t.any { it in '\u0D00'..'\u0D7F' } || t.contains("malayalam") || MALAYALAM_ARTISTS.any { t.contains(it) }) malScore++
            if (t.any { it in '\u0B80'..'\u0BFF' } || t.contains("tamil") || TAMIL_ARTISTS.any { t.contains(it) }) tamScore++
            if (t.any { it in '\u0900'..'\u097F' } || t.contains("hindi") || HINDI_ARTISTS.any { t.contains(it) }) hinScore++
        }
        val dominantLanguage = when {
            malScore >= tamScore && malScore >= hinScore && malScore > 0 -> "Malayalam"
            tamScore > malScore && tamScore >= hinScore -> "Tamil"
            hinScore > malScore && hinScore > tamScore -> "Hindi"
            else -> null
        }

        val seedRecent = history.firstOrNull() ?: favorites.firstOrNull()

        val heavyRotation = songFrequency.entries
            .filter { it.value >= 4 }
            .sortedByDescending { it.value }
            .mapNotNull { songMap[it.key] }
            .take(16)

        return UserTaste(
            topArtists = sortedArtistScores,
            dominantLanguage = dominantLanguage,
            seedRecentSong = seedRecent,
            heavyRotationSongs = heavyRotation
        )
    }

    private fun computeAvailableCategories(taste: UserTaste): List<String> {
        return buildList {
            add("All")
            add("Made For You")
            taste.topArtists.firstOrNull()?.let { add(it.artistName) }
            val orderedLanguages = when (taste.dominantLanguage) {
                "Malayalam" -> listOf("Malayalam", "Tamil", "Hindi")
                "Tamil" -> listOf("Tamil", "Malayalam", "Hindi")
                "Hindi" -> listOf("Hindi", "Malayalam", "Tamil")
                else -> listOf("Malayalam", "Tamil", "Hindi")
            }
            addAll(orderedLanguages)
            add("Trending India")
            add("Romantic & Chill")
            add("Party & Dance")
            add("Workout")
            add("Lo-Fi")
        }
    }

    private fun updateDynamicArtists(taste: UserTaste) {
        viewModelScope.launch {
            try {
                val userTopArtists = taste.topArtists.map { it.artistName }.take(12)
                val fallbackArtists = listOf(
                    "Sushin Shyam", "Anirudh Ravichander", "A.R. Rahman",
                    "K.S. Chithra", "K.J. Yesudas", "Arijit Singh",
                    "Shreya Ghoshal", "Sid Sriram", "Yuvan Shankar Raja",
                    "Harris Jayaraj", "Pritam", "Diljit Dosanjh"
                )
                val finalArtistNames = (userTopArtists + fallbackArtists).distinctBy { it.lowercase() }.take(12)

                val artistsDeferred = finalArtistNames.map { artistName ->
                    async {
                        val existingSample = taste.topArtists.firstOrNull { it.artistName.equals(artistName, ignoreCase = true) }?.sampleSongs ?: emptyList()
                        val songs = if (existingSample.size >= 10) {
                            existingSample
                        } else {
                            val fetched = musicRepository.searchSongs(artistName, limit = 20)
                            (existingSample + fetched).distinctBy { it.id }
                        }

                        val portraitUrl = artistImageProvider.getArtistPortraitUrl(artistName)
                        val finalArtwork = portraitUrl.ifEmpty {
                            songs.firstOrNull()?.highResArtworkUrl?.ifEmpty { songs.firstOrNull()?.artworkUrl } ?: ""
                        }

                        ArtistCardData(
                            id = "artist_${artistName.lowercase().replace(Regex("[^a-z0-9]"), "_")}",
                            name = artistName,
                            subtitle = if (userTopArtists.any { it.equals(artistName, ignoreCase = true) }) "From Your Taste" else "Artist",
                            artworkUrl = finalArtwork,
                            songs = songs
                        )
                    }
                }

                val resolvedArtists = artistsDeferred.map { it.await() }.filter { it.songs.isNotEmpty() }
                if (resolvedArtists.isNotEmpty()) {
                    artistCache.clear()
                    artistCache.addAll(resolvedArtists)
                    homeFeedCacheManager.saveArtists(resolvedArtists)
                    rebuildShelves()
                }
            } catch (e: Exception) {
                LofiQLogger.w(LofiQLogger.Category.UI, "Error updating dynamic artists: ${e.message}")
            }
        }
    }

    private fun updateDynamicTasteShelves(taste: UserTaste) {
        viewModelScope.launch {
            try {
                val artistJobs = taste.topArtists.take(4).mapIndexed { index, artistScore ->
                    async {
                        val key = "dynamic_artist_$index"
                        val artistName = artistScore.artistName
                        val existing = shelfCache[key]
                        if (existing.isNullOrEmpty() || !existing.any { it.artist.contains(artistName, ignoreCase = true) }) {
                            val tracks = if (artistScore.sampleSongs.size >= 14) {
                                artistScore.sampleSongs
                            } else {
                                val fetched = musicRepository.searchSongs("$artistName hits songs", limit = 18)
                                (artistScore.sampleSongs + fetched).distinctBy { it.id }
                            }
                            if (tracks.isNotEmpty()) {
                                shelfCache[key] = tracks
                                homeFeedCacheManager.saveShelf(key, tracks)
                            }
                        }
                    }
                }

                val seedJob = async {
                    val seed = taste.seedRecentSong
                    if (seed != null) {
                        val key = "dynamic_seed_song"
                        val existing = shelfCache[key]
                        if (existing.isNullOrEmpty()) {
                            val query = if (seed.artist.isNotBlank() && seed.artist != "Unknown Artist") {
                                "${seed.artist} similar songs"
                            } else {
                                "${seed.title} radio"
                            }
                            val tracks = musicRepository.searchSongs(query, limit = 16)
                            if (tracks.isNotEmpty()) {
                                shelfCache[key] = tracks
                                homeFeedCacheManager.saveShelf(key, tracks)
                            }
                        }
                    }
                }

                if (taste.heavyRotationSongs.isNotEmpty()) {
                    shelfCache["heavy_rotation"] = taste.heavyRotationSongs
                    homeFeedCacheManager.saveShelf("heavy_rotation", taste.heavyRotationSongs)
                }

                artistJobs.forEach { it.await() }
                seedJob.await()
                rebuildShelves()
            } catch (e: Exception) {
                LofiQLogger.w(LofiQLogger.Category.UI, "Error updating dynamic taste shelves: ${e.message}")
            }
        }
    }

    private fun observeLocalData() {
        viewModelScope.launch {
            combine(
                libraryRepository.observeHistory(),
                libraryRepository.observeFavorites()
            ) { history, favorites ->
                Pair(history, favorites)
            }.collect { (history, favorites) ->
                val prevTopArtist = cachedUserTaste?.topArtists?.firstOrNull()?.artistName
                historySongs = history
                favoriteSongs = favorites

                val taste = extractUserTaste(history, favorites)
                cachedUserTaste = taste

                val newTopArtist = taste.topArtists.firstOrNull()?.artistName
                val topArtistChanged = prevTopArtist != newTopArtist

                // Only perform heavy network taste updates if the user's top artist actually changed
                // (NOT on every single song playback!)
                if (topArtistChanged && taste.topArtists.isNotEmpty()) {
                    updateDynamicArtists(taste)
                    updateDynamicTasteShelves(taste)
                }

                if (topArtistChanged && newTopArtist != null && newTopArtist != artistBasedSongs?.first) {
                    fetchArtistRecommendations(newTopArtist)
                }

                // Smoothly update shelves with new history (recently played) without re-fetching storms
                rebuildShelves()
            }
        }
    }

    fun refreshFeed() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            currentFeedBatch = 0
            silentLiveRefresh()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun loadInitialFeed() {
        viewModelScope.launch {
            // Check disk cache first before showing any loading state
            if (shelfCache.isEmpty()) {
                val (diskShelves, diskArtists) = homeFeedCacheManager.loadFromDisk()
                if (diskShelves.isNotEmpty()) {
                    shelfCache.putAll(diskShelves)
                    if (diskArtists.isNotEmpty()) {
                        artistCache.clear()
                        artistCache.addAll(diskArtists)
                    }
                    rebuildShelves()
                }
            }

            // If we have cached data, DO NOT show skeletons! Render instantly with 0ms gap.
            if (shelfCache.isEmpty()) {
                _uiState.update { it.copy(isInitialLoading = true, errorMessage = null) }
                fetchFeedBatch(0)
            } else {
                _uiState.update { it.copy(isInitialLoading = false, errorMessage = null) }
                // Stale-While-Revalidate: fetch latest data in background silently
                silentLiveRefresh()
            }
            _uiState.update { it.copy(isInitialLoading = false) }
        }
    }

    private suspend fun silentLiveRefresh() {
        try {
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            coroutineScope {
                // 1. Live Trending India from real-time API
                val trendingDef = async {
                    val live = musicRepository.getTrendingSongs(limit = 20)
                    if (live.isNotEmpty()) {
                        shelfCache["trending_india"] = live
                        homeFeedCacheManager.saveShelf("trending_india", live)
                    }
                }

                // 2. Live Malayalam Trending (Strict Language Filtered)
                val malayalamDef = async {
                    fetchQuerySongs(
                        key = "malayalam_trending",
                        query = "Malayalam latest hit songs $currentYear official audio",
                        limit = 18,
                        languageFilter = "Malayalam",
                        autoRebuild = false
                    )
                }

                // 3. Live Tamil Blockbusters (Strict Language Filtered)
                val tamilDef = async {
                    fetchQuerySongs(
                        key = "tamil_hits",
                        query = "Tamil latest hit songs $currentYear official audio",
                        limit = 18,
                        languageFilter = "Tamil",
                        autoRebuild = false
                    )
                }

                // 4. Live Bollywood Hits (Strict Language Filtered)
                val hindiDef = async {
                    fetchQuerySongs(
                        key = "bollywood_hits",
                        query = "Top Bollywood hit songs $currentYear official audio",
                        limit = 16,
                        languageFilter = "Hindi",
                        autoRebuild = false
                    )
                }

                // 5. Live Albums & Singles / New Releases
                val newReleasesDef = async {
                    fetchQuerySongs(
                        key = "new_releases",
                        query = "Latest Indian songs albums singles $currentYear official audio",
                        limit = 16,
                        autoRebuild = false
                    )
                }

                trendingDef.await()
                malayalamDef.await()
                tamilDef.await()
                hindiDef.await()
                newReleasesDef.await()

                if (spotifyAccount.value.isLoggedIn) {
                    refreshSpotifyPersonalization()
                }
                if (userAccount.value.isLoggedIn) {
                    refreshYouTubePersonalization()
                }
            }

            rebuildShelves()
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.UI, "Silent live refresh error: ${e.message}")
        }
    }

    fun loadArtistCatalog(artist: ArtistCardData) {
        _artistDetailTracks.value = artist.songs
        _isArtistDetailLoading.value = true

        viewModelScope.launch {
            try {
                val name = artist.name
                val def1 = async { musicRepository.searchSongs("$name hits", limit = 30) }
                val def2 = async { musicRepository.searchSongs("$name songs", limit = 30) }
                val def3 = async { musicRepository.searchSongs("$name best tracks", limit = 25) }

                val r1 = def1.await()
                val r2 = def2.await()
                val r3 = def3.await()

                val combined = (artist.songs + r1 + r2 + r3)
                    .distinctBy { it.id }
                    .filter { song ->
                        val n = name.lowercase().trim()
                        val a = song.artist.lowercase()
                        val t = song.title.lowercase()
                        a.contains(n) || t.contains(n) || n.split(" ").filter { it.length > 2 }.any { a.contains(it) }
                    }

                if (combined.isNotEmpty()) {
                    _artistDetailTracks.value = combined
                } else {
                    _artistDetailTracks.value = (artist.songs + r1 + r2).distinctBy { it.id }
                }
            } catch (e: Exception) {
                LofiQLogger.w(LofiQLogger.Category.SEARCH, "Failed loading deep catalog for ${artist.name}: ${e.message}")
            } finally {
                _isArtistDetailLoading.value = false
            }
        }
    }

    fun clearArtistCatalog() {
        _artistDetailTracks.value = emptyList()
        _isArtistDetailLoading.value = false
    }

    fun loadMoreShelves() {
        if (isLoadingMoreShelves || _uiState.value.isInitialLoading) return
        viewModelScope.launch {
            isLoadingMoreShelves = true
            _uiState.update { it.copy(isLoadingMore = true) }
            currentFeedBatch++
            val category = _uiState.value.selectedCategory
            if (category == "All") {
                fetchFeedBatch(currentFeedBatch)
            } else {
                fetchCategoryBatch(category, currentFeedBatch)
            }
            _uiState.update { it.copy(isLoadingMore = false) }
            isLoadingMoreShelves = false
        }
    }

    fun selectCategory(category: String) {
        if (_uiState.value.selectedCategory == category) return
        _uiState.update { it.copy(selectedCategory = category) }
        currentFeedBatch = 0
        rebuildShelves()
        viewModelScope.launch {
            fetchCategoryBatch(category, batch = 0)
        }
    }

    private suspend fun fetchCategoryBatch(category: String, batch: Int) {
        try {
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            coroutineScope {
                when (category) {
                    "Malayalam" -> {
                        when (batch) {
                            0 -> {
                                val def1 = async { fetchQuerySongs("malayalam_trending", "Malayalam latest hit songs $currentYear official audio", 18, "Malayalam") }
                                val def2 = async { fetchQuerySongs("malayalam_sushin", "Sushin Shyam hits Malayalam songs", 16, "Malayalam") }
                                val def3 = async { fetchQuerySongs("malayalam_melodies", "Malayalam evergreen melody songs KJ Yesudas KS Chithra", 16, "Malayalam") }
                                val def4 = async { fetchQuerySongs("malayalam_nostalgia", "Malayalam golden 90s 2000s hit songs", 16, "Malayalam") }
                                val def5 = async { fetchQuerySongs("malayalam_lofi", "Malayalam chill lofi acoustic songs", 16, "Malayalam") }
                                def1.await(); def2.await(); def3.await(); def4.await(); def5.await()
                            }
                            1 -> {
                                val def1 = async { fetchQuerySongs("malayalam_hesham", "Hesham Abdul Wahab Malayalam hits", 16, "Malayalam") }
                                val def2 = async { fetchQuerySongs("malayalam_vineeth", "Vineeth Sreenivasan Malayalam hit songs", 16, "Malayalam") }
                                val def3 = async { fetchQuerySongs("malayalam_party", "Malayalam dance party songs hits", 16, "Malayalam") }
                                def1.await(); def2.await(); def3.await()
                            }
                            2 -> {
                                val def1 = async { fetchQuerySongs("malayalam_vidyasagar", "Vidyasagar Malayalam melody hits", 16, "Malayalam") }
                                val def2 = async { fetchQuerySongs("malayalam_hiphop", "Malayalam rap hip hop Dabzee Baby Jean Fejo", 16, "Malayalam") }
                                val def3 = async { fetchQuerySongs("malayalam_gopi", "Gopi Sundar Shaan Rahman Malayalam hits", 16, "Malayalam") }
                                def1.await(); def2.await(); def3.await()
                            }
                            else -> {
                                val def1 = async { fetchQuerySongs("malayalam_deepak", "Deepak Dev Malayalam hits", 16, "Malayalam") }
                                val def2 = async { fetchQuerySongs("malayalam_classic_duets", "Yesudas Chithra Malayalam duet songs", 16, "Malayalam") }
                                val def3 = async { fetchQuerySongs("malayalam_indie", "Malayalam indie acoustic songs Rex Vijayan Job Kurian", 16, "Malayalam") }
                                def1.await(); def2.await(); def3.await()
                            }
                        }
                    }
                    "Tamil" -> {
                        when (batch) {
                            0 -> {
                                val def1 = async { fetchQuerySongs("tamil_hits", "Tamil latest hit songs $currentYear official audio", 18, "Tamil") }
                                val def2 = async { fetchQuerySongs("tamil_anirudh", "Anirudh Ravichander Tamil hit songs", 16, "Tamil") }
                                val def3 = async { fetchQuerySongs("tamil_rahman", "AR Rahman Tamil hits melodies", 16, "Tamil") }
                                val def4 = async { fetchQuerySongs("tamil_romance", "Tamil romantic melody songs", 16, "Tamil") }
                                val def5 = async { fetchQuerySongs("tamil_kuthu", "Tamil kuthu dance party songs", 16, "Tamil") }
                                def1.await(); def2.await(); def3.await(); def4.await(); def5.await()
                            }
                            1 -> {
                                val def1 = async { fetchQuerySongs("tamil_yuvan", "Yuvan Shankar Raja Tamil hits", 16, "Tamil") }
                                val def2 = async { fetchQuerySongs("tamil_harris", "Harris Jayaraj Tamil melody songs", 16, "Tamil") }
                                val def3 = async { fetchQuerySongs("tamil_sid", "Sid Sriram Tamil hit songs", 16, "Tamil") }
                                def1.await(); def2.await(); def3.await()
                            }
                            2 -> {
                                val def1 = async { fetchQuerySongs("tamil_santhosh", "Santhosh Narayanan Tamil songs", 16, "Tamil") }
                                val def2 = async { fetchQuerySongs("tamil_ilayaraja", "Ilaiyaraaja Tamil golden classic songs", 16, "Tamil") }
                                val def3 = async { fetchQuerySongs("tamil_hiphop", "Hiphop Tamizha Tamil songs hits", 16, "Tamil") }
                                def1.await(); def2.await(); def3.await()
                            }
                            else -> {
                                val def1 = async { fetchQuerySongs("tamil_pradeep", "Pradeep Kumar Tamil melody songs", 16, "Tamil") }
                                val def2 = async { fetchQuerySongs("tamil_spb", "SP Balasubrahmanyam Tamil evergreen songs", 16, "Tamil") }
                                def1.await(); def2.await()
                            }
                        }
                    }
                    "Hindi" -> {
                        when (batch) {
                            0 -> {
                                val def1 = async { fetchQuerySongs("bollywood_hits", "Top Bollywood hit songs $currentYear official audio", 18, "Hindi") }
                                val def2 = async { fetchQuerySongs("hindi_arijit", "Arijit Singh Hindi romantic songs", 16, "Hindi") }
                                val def3 = async { fetchQuerySongs("hindi_melodies", "Bollywood romantic love songs Hindi", 16, "Hindi") }
                                val def4 = async { fetchQuerySongs("retro_classics", "90s 2000s Bollywood evergreen Hindi songs", 16, "Hindi") }
                                val def5 = async { fetchQuerySongs("party_anthems", "Desi party Bollywood dance songs Hindi", 16, "Hindi") }
                                def1.await(); def2.await(); def3.await(); def4.await(); def5.await()
                            }
                            1 -> {
                                val def1 = async { fetchQuerySongs("hindi_shreya", "Shreya Ghoshal Hindi best songs", 16, "Hindi") }
                                val def2 = async { fetchQuerySongs("hindi_pritam", "Pritam Bollywood chartbuster songs", 16, "Hindi") }
                                val def3 = async { fetchQuerySongs("hindi_soul", "Atif Aslam KK Hindi soulful songs", 16, "Hindi") }
                                def1.await(); def2.await(); def3.await()
                            }
                            2 -> {
                                val def1 = async { fetchQuerySongs("hindi_sonu", "Sonu Nigam Hindi evergreen hits", 16, "Hindi") }
                                val def2 = async { fetchQuerySongs("hindi_mishra", "Vishal Mishra Hindi romantic songs", 16, "Hindi") }
                                val def3 = async { fetchQuerySongs("hindi_diljit", "Diljit Dosanjh Hindi Punjabi top hits", 16, "Hindi") }
                                def1.await(); def2.await(); def3.await()
                            }
                            else -> {
                                val def1 = async { fetchQuerySongs("hindi_badshah", "Badshah Honey Singh party songs", 16, "Hindi") }
                                val def2 = async { fetchQuerySongs("hindi_lofi", "Bollywood Hindi lofi romantic songs", 16, "Hindi") }
                                def1.await(); def2.await()
                            }
                        }
                    }
                    "Trending India" -> {
                        when (batch) {
                            0 -> {
                                val def1 = async {
                                    val live = musicRepository.getTrendingSongs(18)
                                    if (live.isNotEmpty()) {
                                        shelfCache["trending_india"] = live
                                        homeFeedCacheManager.saveShelf("trending_india", live)
                                    }
                                }
                                val def2 = async { fetchQuerySongs("suggestions_for_you", "Top Indian Hits Today $currentYear", 16) }
                                val def3 = async { fetchQuerySongs("indie_india", "Indian Indie Pop Fresh Finds", 16) }
                                val def4 = async { fetchQuerySongs("punjabi_hits", "Latest Punjabi Hits", 16) }
                                def1.await(); def2.await(); def3.await(); def4.await()
                            }
                            else -> {
                                val def1 = async { fetchQuerySongs("trending_south", "South Indian viral hit songs $currentYear", 16) }
                                val def2 = async { fetchQuerySongs("trending_pop", "Indian Pop Chartbusters $currentYear", 16) }
                                def1.await(); def2.await()
                            }
                        }
                    }
                    "Romantic & Chill" -> {
                        val def1 = async { fetchQuerySongs("romantic_melodies", "Bollywood romantic love songs Hindi", 16, "Hindi") }
                        val def2 = async { fetchQuerySongs("lofi_chill", "Indian Lo-Fi Chill Beats", 16) }
                        val def3 = async { fetchQuerySongs("acoustic_peace", "Acoustic Pop Unplugged Hits", 16) }
                        def1.await(); def2.await(); def3.await()
                    }
                    "Party & Dance" -> {
                        val def1 = async { fetchQuerySongs("party_anthems", "Desi party Bollywood dance songs Hindi", 16, "Hindi") }
                        val def2 = async { fetchQuerySongs("punjabi_hits", "Latest Punjabi Hits", 16) }
                        val def3 = async { fetchQuerySongs("tamil_kuthu", "Tamil kuthu dance party songs", 16, "Tamil") }
                        def1.await(); def2.await(); def3.await()
                    }
                    "Workout" -> {
                        val def1 = async { fetchQuerySongs("workout_beats", "High Energy Indian Workout Hits", 16) }
                        def1.await()
                    }
                    "Lo-Fi" -> {
                        val def1 = async { fetchQuerySongs("lofi_chill", "Indian Lo-Fi Chill Beats", 16) }
                        val def2 = async { fetchQuerySongs("acoustic_peace", "Acoustic Pop Unplugged Hits", 16) }
                        def1.await(); def2.await()
                    }
                    else -> {
                        val def1 = async { fetchQuerySongs("cat_${category}_hits", "$category hits songs", 18) }
                        val def2 = async { fetchQuerySongs("cat_${category}_best", "$category best songs tracks", 18) }
                        val def3 = async { fetchQuerySongs("cat_${category}_latest", "$category latest songs $currentYear", 16) }
                        def1.await(); def2.await(); def3.await()
                    }
                }
            }
            rebuildShelves()
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.UI, "Error fetching category '$category' batch $batch: ${e.message}")
        }
    }

    private suspend fun fetchFeedBatch(batch: Int) {
        try {
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            coroutineScope {
                when (batch) {
                    0 -> {
                        // Batch 1: Malayalam, Tamil, Hindi, Trending India & Top Artists with Language Purity
                        val malayalamTrendingDeferred = async { fetchQuerySongs("malayalam_trending", "Malayalam latest hit songs $currentYear official audio", 18, "Malayalam", autoRebuild = false) }
                        val tamilHitsDeferred = async { fetchQuerySongs("tamil_hits", "Tamil latest hit songs $currentYear official audio", 18, "Tamil", autoRebuild = false) }
                        val trendingIndiaDeferred = async {
                            val live = musicRepository.getTrendingSongs(18)
                            if (live.isNotEmpty()) {
                                shelfCache["trending_india"] = live
                                homeFeedCacheManager.saveShelf("trending_india", live)
                            } else {
                                fetchQuerySongs("trending_india", "Trending Songs India $currentYear", 18, autoRebuild = false)
                            }
                        }
                        val bollywoodHitsDeferred = async { fetchQuerySongs("bollywood_hits", "Top Bollywood hit songs $currentYear official audio", 16, "Hindi", autoRebuild = false) }
                        val suggestionsDeferred = async { fetchQuerySongs("suggestions_for_you", "Top Indian Hits Today $currentYear", 16, autoRebuild = false) }
                        val malayalamMelodiesDeferred = async { fetchQuerySongs("malayalam_melodies", "Malayalam evergreen melody songs KJ Yesudas KS Chithra", 16, "Malayalam", autoRebuild = false) }
                        val tamilRomanceDeferred = async { fetchQuerySongs("tamil_romance", "Tamil romantic melody songs", 16, "Tamil", autoRebuild = false) }
                        val hindiMelodiesDeferred = async { fetchQuerySongs("hindi_melodies", "Bollywood romantic love songs Hindi", 16, "Hindi", autoRebuild = false) }
                        val punjabiHitsDeferred = async { fetchQuerySongs("punjabi_hits", "Latest Punjabi Hits", 16, autoRebuild = false) }
                        val sushinDeferred = async { fetchQuerySongs("malayalam_sushin", "Sushin Shyam hits Malayalam songs", 16, "Malayalam", autoRebuild = false) }
                        val anirudhDeferred = async { fetchQuerySongs("tamil_anirudh", "Anirudh Ravichander Tamil hit songs", 16, "Tamil", autoRebuild = false) }
                        val arijitDeferred = async { fetchQuerySongs("hindi_arijit", "Arijit Singh Hindi romantic songs", 16, "Hindi", autoRebuild = false) }
                        val partyDeferred = async { fetchQuerySongs("party_anthems", "Desi party Bollywood dance songs Hindi", 16, "Hindi", autoRebuild = false) }
                        val newReleasesDeferred = async { fetchQuerySongs("new_releases", "Latest Indian songs albums singles $currentYear official audio", 16, autoRebuild = false) }

                        // Dynamically update user taste, top artists, and taste-driven shelves
                        val taste = cachedUserTaste ?: extractUserTaste(historySongs, favoriteSongs)
                        cachedUserTaste = taste
                        updateDynamicArtists(taste)
                        updateDynamicTasteShelves(taste)

                        malayalamTrendingDeferred.await()
                        tamilHitsDeferred.await()
                        trendingIndiaDeferred.await()
                        bollywoodHitsDeferred.await()
                        suggestionsDeferred.await()
                        malayalamMelodiesDeferred.await()
                        tamilRomanceDeferred.await()
                        hindiMelodiesDeferred.await()
                        punjabiHitsDeferred.await()
                        sushinDeferred.await()
                        anirudhDeferred.await()
                        arijitDeferred.await()
                        partyDeferred.await()
                        newReleasesDeferred.await()

                        if (spotifyAccount.value.isLoggedIn) {
                            refreshSpotifyPersonalization()
                        }
                        if (userAccount.value.isLoggedIn) {
                            refreshYouTubePersonalization()
                        }
                    }
                    1 -> {
                        // Batch 2: Modern Romance, South Viral & Chartbusters
                        val heshamDeferred = async { fetchQuerySongs("malayalam_hesham", "Hesham Abdul Wahab Malayalam hits", 16, "Malayalam", autoRebuild = false) }
                        val vineethDeferred = async { fetchQuerySongs("malayalam_vineeth", "Vineeth Sreenivasan Malayalam hit songs", 16, "Malayalam", autoRebuild = false) }
                        val yuvanDeferred = async { fetchQuerySongs("tamil_yuvan", "Yuvan Shankar Raja Tamil hits", 16, "Tamil", autoRebuild = false) }
                        val harrisDeferred = async { fetchQuerySongs("tamil_harris", "Harris Jayaraj Tamil melody songs", 16, "Tamil", autoRebuild = false) }
                        val shreyaDeferred = async { fetchQuerySongs("hindi_shreya", "Shreya Ghoshal Hindi best songs", 16, "Hindi", autoRebuild = false) }
                        val pritamDeferred = async { fetchQuerySongs("hindi_pritam", "Pritam Bollywood chartbuster songs", 16, "Hindi", autoRebuild = false) }
                        val southViralDeferred = async { fetchQuerySongs("trending_south", "South Indian viral hit songs $currentYear", 16, autoRebuild = false) }

                        heshamDeferred.await()
                        vineethDeferred.await()
                        yuvanDeferred.await()
                        harrisDeferred.await()
                        shreyaDeferred.await()
                        pritamDeferred.await()
                        southViralDeferred.await()
                    }
                    2 -> {
                        // Batch 3: Acoustic, Hip-Hop, Lo-Fi, Indie
                        val romanticDeferred = async { fetchQuerySongs("romantic_melodies", "Bollywood romantic love songs Hindi", 16, "Hindi", autoRebuild = false) }
                        val lofiChillDeferred = async { fetchQuerySongs("lofi_chill", "Indian Lo-Fi Chill Beats", 16, autoRebuild = false) }
                        val indieIndiaDeferred = async { fetchQuerySongs("indie_india", "Indian Indie Pop Fresh Finds", 16, autoRebuild = false) }
                        val hiphopDeferred = async { fetchQuerySongs("malayalam_hiphop", "Malayalam rap hip hop Dabzee Baby Jean Fejo", 16, "Malayalam", autoRebuild = false) }
                        val sidDeferred = async { fetchQuerySongs("tamil_sid", "Sid Sriram Tamil hit songs", 16, "Tamil", autoRebuild = false) }
                        val soulDeferred = async { fetchQuerySongs("hindi_soul", "Atif Aslam KK Hindi soulful songs", 16, "Hindi", autoRebuild = false) }

                        romanticDeferred.await()
                        lofiChillDeferred.await()
                        indieIndiaDeferred.await()
                        hiphopDeferred.await()
                        sidDeferred.await()
                        soulDeferred.await()
                    }
                    else -> {
                        // Batch 4+: Global & Thematic Endless Shelves
                        val globalTopDeferred = async { fetchQuerySongs("global_top", "Global Top 50 Chartbusters", 16, autoRebuild = false) }
                        val workoutEnergyDeferred = async { fetchQuerySongs("workout_beats", "High Energy Indian Workout Hits", 16, autoRebuild = false) }
                        val acousticChillDeferred = async { fetchQuerySongs("acoustic_peace", "Acoustic Pop Unplugged Hits", 16, autoRebuild = false) }
                        val devotionalPeaceDeferred = async { fetchQuerySongs("devotional_peace", "Indian Soulful Melodies", 16, autoRebuild = false) }
                        val nostalgiaDeferred = async { fetchQuerySongs("malayalam_nostalgia", "Malayalam golden 90s 2000s hit songs", 16, "Malayalam", autoRebuild = false) }

                        globalTopDeferred.await()
                        workoutEnergyDeferred.await()
                        acousticChillDeferred.await()
                        devotionalPeaceDeferred.await()
                        nostalgiaDeferred.await()
                    }
                }
            }

            val topArtist = findTopArtist(historySongs, favoriteSongs)
            if (topArtist != null && topArtist != artistBasedSongs?.first) {
                fetchArtistRecommendations(topArtist)
            }

            rebuildShelves()
        } catch (e: Exception) {
            LofiQLogger.e(LofiQLogger.Category.UI, "Failed to fetch feed batch $batch: ${e.message}")
            if (historySongs.isEmpty() && favoriteSongs.isEmpty() && shelfCache.isEmpty()) {
                _uiState.update { it.copy(errorMessage = "Could not load music feed. Please check your internet connection.") }
            }
        }
    }

    private suspend fun fetchQuerySongs(
        key: String,
        query: String,
        limit: Int,
        languageFilter: String? = null,
        autoRebuild: Boolean = true
    ): List<Song> {
        return try {
            val rawSongs = musicRepository.searchSongs(query, limit = if (languageFilter != null) limit * 2 else limit)
            val songs = if (languageFilter != null) {
                val filtered = filterSongsByLanguage(rawSongs, languageFilter)
                if (filtered.isNotEmpty()) filtered.take(limit) else rawSongs.take(limit)
            } else {
                rawSongs.take(limit)
            }

            if (songs.isNotEmpty()) {
                shelfCache[key] = songs
                homeFeedCacheManager.saveShelf(key, songs)
                if (autoRebuild) {
                    rebuildShelves()
                }
            }
            songs
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.SEARCH, "Failed searching for query '$query': ${e.message}")
            emptyList()
        }
    }

    private fun filterSongsByLanguage(songs: List<Song>, targetLanguage: String): List<Song> {
        return songs.filter { song ->
            val text = "${song.title} ${song.artist} ${song.album}".lowercase()
            when (targetLanguage) {
                "Malayalam" -> {
                    val hasMalayalamScript = text.any { it in '\u0D00'..'\u0D7F' }
                    val hasMalayalamMarker = text.contains("malayalam") || text.contains("mollywood") ||
                            MALAYALAM_ARTISTS.any { text.contains(it) } || hasMalayalamScript
                    val hasExclude = MALAYALAM_EXCLUDE_KEYWORDS.any { text.contains(it) }
                    if (hasExclude && !text.contains("malayalam") && !hasMalayalamScript) {
                        false
                    } else if (hasMalayalamMarker) {
                        true
                    } else {
                        !hasExclude
                    }
                }
                "Tamil" -> {
                    val hasTamilScript = text.any { it in '\u0B80'..'\u0BFF' }
                    val hasTamilMarker = text.contains("tamil") || text.contains("kollywood") ||
                            TAMIL_ARTISTS.any { text.contains(it) } || hasTamilScript
                    val hasExclude = TAMIL_EXCLUDE_KEYWORDS.any { text.contains(it) }
                    if (hasExclude && !text.contains("tamil") && !hasTamilScript) {
                        false
                    } else if (hasTamilMarker) {
                        true
                    } else {
                        !hasExclude
                    }
                }
                "Hindi" -> {
                    val hasHindiScript = text.any { it in '\u0900'..'\u097F' }
                    val hasHindiMarker = text.contains("hindi") || text.contains("bollywood") ||
                            HINDI_ARTISTS.any { text.contains(it) } || hasHindiScript
                    val hasExclude = HINDI_EXCLUDE_KEYWORDS.any { text.contains(it) }
                    if (hasExclude && !text.contains("hindi") && !hasHindiScript) {
                        false
                    } else if (hasHindiMarker) {
                        true
                    } else {
                        !hasExclude
                    }
                }
                else -> true
            }
        }
    }

    private fun fetchArtistRecommendations(artist: String) {
        viewModelScope.launch {
            try {
                val songs = musicRepository.searchSongs(artist, limit = 16)
                if (songs.isNotEmpty()) {
                    artistBasedSongs = Pair(artist, songs)
                    rebuildShelves()
                }
            } catch (e: Exception) {
                LofiQLogger.w(LofiQLogger.Category.UI, "Failed to fetch recommendations for $artist: ${e.message}")
            }
        }
    }

    private fun findTopArtist(history: List<Song>, favorites: List<Song>): String? {
        val allSongs = history + favorites
        if (allSongs.isEmpty()) return null
        return allSongs
            .map { it.artist.trim() }
            .filter { it.isNotBlank() && it != "Unknown Artist" }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
    }

    private fun rebuildShelves() {
        val shelvesList = mutableListOf<HomeShelf>()
        val category = _uiState.value.selectedCategory

        when (category) {
            "Malayalam" -> {
                // 1. Malayalam Top Trending
                shelfCache["malayalam_trending"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_malayalam_trending",
                            title = "Malayalam Top Trending",
                            subtitle = "Latest Mollywood chartbusters & viral tracks",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
                // 2. Sushin Shyam & New Wave
                shelfCache["malayalam_sushin"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_malayalam_sushin",
                            title = "Sushin Shyam & New Wave",
                            subtitle = "Modern Mollywood soundscape",
                            layout = ShelfLayout.COMPACT_GRID,
                            songs = it
                        )
                    )
                }
                // 3. Malayalam Evergreen Melodies
                shelfCache["malayalam_melodies"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_malayalam_melodies",
                            title = "Malayalam Evergreen Melodies",
                            subtitle = "K.J. Yesudas, K.S. Chithra & Vidyasagar magic",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
                // 4. Malayalam Nostalgia
                shelfCache["malayalam_nostalgia"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_malayalam_nostalgia",
                            title = "90s & 2000s Mollywood Nostalgia",
                            subtitle = "Golden era classics you love",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
                // 5. Malayalam Lo-Fi & Chill
                shelfCache["malayalam_lofi"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_malayalam_lofi",
                            title = "Malayalam Lo-Fi & Acoustic",
                            subtitle = "Relaxed midnight melodies",
                            layout = ShelfLayout.COMPACT_GRID,
                            songs = it
                        )
                    )
                }
                // Batch 1 Shelves: Hesham, Vineeth, Party
                shelfCache["malayalam_hesham"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_malayalam_hesham",
                            title = "Hesham Abdul Wahab & Modern Romance",
                            subtitle = "Soulful contemporary melodies",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
                shelfCache["malayalam_vineeth"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_malayalam_vineeth",
                            title = "Vineeth Sreenivasan Top Hits",
                            subtitle = "Heartfelt feel-good anthems",
                            layout = ShelfLayout.COMPACT_GRID,
                            songs = it
                        )
                    )
                }
                shelfCache["malayalam_party"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_malayalam_party",
                            title = "Malayalam Party & Dance Anthems",
                            subtitle = "High energy Mollywood dance beats",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
                // Batch 2 Shelves: Vidyasagar, Hip-Hop, Gopi Sundar
                shelfCache["malayalam_vidyasagar"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_malayalam_vidyasagar",
                            title = "Vidyasagar Melodic Magic",
                            subtitle = "Evergreen nostalgic arrangements",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
                shelfCache["malayalam_hiphop"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_malayalam_hiphop",
                            title = "Malayalam Street Rap & Hip-Hop",
                            subtitle = "Dabzee, Baby Jean, Fejo & Hanumankind",
                            layout = ShelfLayout.COMPACT_GRID,
                            songs = it
                        )
                    )
                }
                shelfCache["malayalam_gopi"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_malayalam_gopi",
                            title = "Gopi Sundar & Shaan Rahman Hits",
                            subtitle = "Romantic melodies & viral beats",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
                // Batch 3+ Shelves: Deepak Dev, Duets, Indie
                shelfCache["malayalam_deepak"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_malayalam_deepak",
                            title = "Deepak Dev Blockbusters",
                            subtitle = "Stylized youth anthems",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
                shelfCache["malayalam_classic_duets"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_malayalam_classic_duets",
                            title = "Yesudas & Chithra Immortal Duets",
                            subtitle = "Eternal melodies of Mollywood",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
                shelfCache["malayalam_indie"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_malayalam_indie",
                            title = "Malayalam Indie & Acoustic Waves",
                            subtitle = "Rex Vijayan & Job Kurian acoustic vibe",
                            layout = ShelfLayout.COMPACT_GRID,
                            songs = it
                        )
                    )
                }
            }
            "Tamil" -> {
                // 1. Tamil Top Chartbusters
                shelfCache["tamil_hits"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_tamil_hits",
                            title = "Tamil Top Chartbusters",
                            subtitle = "Latest Kollywood blockbuster anthems",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
                // 2. Anirudh Rockstar Hits
                shelfCache["tamil_anirudh"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_tamil_anirudh",
                            title = "Anirudh Rockstar Hits",
                            subtitle = "High energy Kollywood bangers",
                            layout = ShelfLayout.COMPACT_GRID,
                            songs = it
                        )
                    )
                }
                // 3. AR Rahman Magic
                shelfCache["tamil_rahman"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_tamil_rahman",
                            title = "A.R. Rahman Tamil Magic",
                            subtitle = "Timeless melodies by the maestro",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
                // 4. Tamil Romantic Melodies
                shelfCache["tamil_romance"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_tamil_romance",
                            title = "Tamil Romantic Melodies",
                            subtitle = "Heartfelt love songs & acoustic serenades",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
                // 5. Tamil Kuthu & Street Beats
                shelfCache["tamil_kuthu"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_tamil_kuthu",
                            title = "Kuthu & Street Beats",
                            subtitle = "Electrifying dance tracks",
                            layout = ShelfLayout.COMPACT_GRID,
                            songs = it
                        )
                    )
                }
                // Batch 1 Shelves: Yuvan, Harris, Sid Sriram
                shelfCache["tamil_yuvan"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_tamil_yuvan",
                            title = "Yuvan Shankar Raja Drugs",
                            subtitle = "Soul-stirring youth anthems",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
                shelfCache["tamil_harris"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_tamil_harris",
                            title = "Harris Jayaraj Melodic Beats",
                            subtitle = "Sophisticated urban romantic hits",
                            layout = ShelfLayout.COMPACT_GRID,
                            songs = it
                        )
                    )
                }
                shelfCache["tamil_sid"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_tamil_sid",
                            title = "Sid Sriram Soulful Hits",
                            subtitle = "Emotional acoustic serenades",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
                // Batch 2 Shelves: Santhosh Narayanan, Ilaiyaraaja, Hiphop Tamizha
                shelfCache["tamil_santhosh"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_tamil_santhosh",
                            title = "Santhosh Narayanan Folk Waves",
                            subtitle = "Raw acoustic & folk fusion",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
                shelfCache["tamil_ilayaraja"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_tamil_ilayaraja",
                            title = "Ilaiyaraaja Golden Classics",
                            subtitle = "Orchestral wonders of Tamil cinema",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
                shelfCache["tamil_hiphop"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_tamil_hiphop",
                            title = "Hiphop Tamizha Commercial Bests",
                            subtitle = "High tempo youth anthems",
                            layout = ShelfLayout.COMPACT_GRID,
                            songs = it
                        )
                    )
                }
                // Batch 3 Shelves: Pradeep Kumar & SPB
                shelfCache["tamil_pradeep"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_tamil_pradeep",
                            title = "Pradeep Kumar Acoustic Melodies",
                            subtitle = "Intimate indie vocal tracks",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
                shelfCache["tamil_spb"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_tamil_spb",
                            title = "SP Balasubrahmanyam Evergreen Hits",
                            subtitle = "Legendary voice of South cinema",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
            }
            "Hindi" -> {
                // 1. Bollywood Top Chartbusters
                shelfCache["bollywood_hits"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_bollywood_hits",
                            title = "Bollywood Blockbusters",
                            subtitle = "All-time popular Hindi anthems",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
                // 2. Arijit Singh & Soulful Hits
                shelfCache["hindi_arijit"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_hindi_arijit",
                            title = "Arijit Singh & Soulful Hits",
                            subtitle = "Heart-touching Bollywood vocals",
                            layout = ShelfLayout.COMPACT_GRID,
                            songs = it
                        )
                    )
                }
                // 3. Hindi Romantic Melodies
                shelfCache["hindi_melodies"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_hindi_melodies",
                            title = "Hindi Romantic Melodies",
                            subtitle = "Evergreen love anthems & acoustic versions",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
                // 4. 90s & 2000s Bollywood Retro
                shelfCache["retro_classics"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_retro_classics",
                            title = "90s & 2000s Bollywood Evergreen",
                            subtitle = "Nostalgic classics from the golden decade",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
                // 5. Bollywood Party Anthems
                shelfCache["party_anthems"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_party_anthems",
                            title = "Desi Bollywood Party Anthems",
                            subtitle = "High tempo club & celebration tracks",
                            layout = ShelfLayout.COMPACT_GRID,
                            songs = it
                        )
                    )
                }
                // Batch 1 Shelves: Shreya, Pritam, Soul
                shelfCache["hindi_shreya"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_hindi_shreya",
                            title = "Shreya Ghoshal Melody Queen",
                            subtitle = "Timeless vocal gems",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
                shelfCache["hindi_pritam"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_hindi_pritam",
                            title = "Pritam Bollywood Magic",
                            subtitle = "Unstoppable radio & chart toppers",
                            layout = ShelfLayout.COMPACT_GRID,
                            songs = it
                        )
                    )
                }
                shelfCache["hindi_soul"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_hindi_soul",
                            title = "Atif Aslam & KK Soulful Serenades",
                            subtitle = "Emotionally loaded nostalgic ballads",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
                // Batch 2 Shelves: Sonu Nigam, Vishal Mishra, Diljit
                shelfCache["hindi_sonu"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_hindi_sonu",
                            title = "Sonu Nigam Golden Era Hits",
                            subtitle = "Masterclass in melody & expression",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
                shelfCache["hindi_mishra"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_hindi_mishra",
                            title = "Vishal Mishra & Modern Love",
                            subtitle = "Contemporary acoustic feelings",
                            layout = ShelfLayout.COMPACT_GRID,
                            songs = it
                        )
                    )
                }
                shelfCache["hindi_diljit"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_hindi_diljit",
                            title = "Diljit Dosanjh Global Anthems",
                            subtitle = "International Punjabi & Bollywood wave",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
                // Batch 3 Shelves: Badshah, Lo-Fi
                shelfCache["hindi_badshah"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_hindi_badshah",
                            title = "Badshah & Party Anthems",
                            subtitle = "Club bangers & infectious hooks",
                            layout = ShelfLayout.COMPACT_GRID,
                            songs = it
                        )
                    )
                }
                shelfCache["hindi_lofi"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_hindi_lofi",
                            title = "Bollywood Lo-Fi & Midnight Chill",
                            subtitle = "Atmospheric late night acoustic reworks",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
            }
            "Trending India" -> {
                // 1. Trending Now in India
                shelfCache["trending_india"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_trending_india",
                            title = "Trending Now in India",
                            subtitle = "Top chartbusters right now",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
                // 2. Suggestions / Viral Hits
                shelfCache["suggestions_for_you"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_suggestions",
                            title = "India Viral Hits",
                            subtitle = "Fast rising tracks on charts",
                            layout = ShelfLayout.COMPACT_GRID,
                            songs = it
                        )
                    )
                }
                // 3. Indie Pop Fresh Finds
                shelfCache["indie_india"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_indie_india",
                            title = "Indian Indie Pop Fresh Finds",
                            subtitle = "New generation independent music",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
                // 4. Punjabi Hits & Pop
                shelfCache["punjabi_hits"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_punjabi_hits",
                            title = "Punjabi Hits & Pop",
                            subtitle = "High energy beats & grooves",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
                // Batch 1 Shelves: South Indian Viral, Indian Pop
                shelfCache["trending_south"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_trending_south",
                            title = "South Indian Viral Hits 2026",
                            subtitle = "Malayalam, Tamil & Telugu chartbusters",
                            layout = ShelfLayout.COMPACT_GRID,
                            songs = it
                        )
                    )
                }
                shelfCache["trending_pop"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_trending_pop",
                            title = "Indian Pop & Desi Hits",
                            subtitle = "Modern multi-lingual radio favorites",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
            }
            "Made For You" -> {
                val spotifyFavorites = favoriteSongs.filter { it.providerId == "spotify" || it.id.startsWith("sp_") }
                val youtubeFavorites = favoriteSongs.filter {
                    !it.id.startsWith("sp_") && !it.id.startsWith("local_") &&
                    it.providerId != "local_download" && it.providerId != "spotify" && it.providerId != "local"
                }
                val isSpotifyConnected = spotifyAccount.value.isLoggedIn
                val isYouTubeConnected = userAccount.value.isLoggedIn

                // 1. Made For The Day (Taste Mix)
                val dailyMix = (favoriteSongs + historySongs + (shelfCache["malayalam_trending"] ?: emptyList()) + (shelfCache["trending_india"] ?: emptyList()))
                    .distinctBy { it.id }
                    .take(20)
                if (dailyMix.isNotEmpty()) {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_made_for_the_day",
                            title = "Made For The Day",
                            subtitle = if (isSpotifyConnected || isYouTubeConnected) "Specially crafted from your connected profiles" else "Specially crafted for your taste",
                            layout = ShelfLayout.CAROUSEL,
                            songs = dailyMix
                        )
                    )
                }

                // 2. Spotify Favorites (if connected)
                if (isSpotifyConnected && spotifyFavorites.isNotEmpty()) {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_made_for_you_spotify",
                            title = "Your Spotify Favorites",
                            subtitle = "Liked tracks synced from Spotify",
                            layout = ShelfLayout.COMPACT_GRID,
                            songs = spotifyFavorites.take(20)
                        )
                    )
                }

                // 3. YouTube Favorites (if connected)
                if (isYouTubeConnected && youtubeFavorites.isNotEmpty()) {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_made_for_you_youtube",
                            title = "From Your YouTube Music",
                            subtitle = "Liked songs from YouTube Music",
                            layout = ShelfLayout.COMPACT_GRID,
                            songs = youtubeFavorites.take(20)
                        )
                    )
                }

                // 4. Spotify Personalized Recommendations
                shelfCache["spotify_recommendations"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_spotify_recommendations_made",
                            title = "Based on Your Spotify Taste",
                            subtitle = "Inspired by your heavy rotation",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }

                // 5. YouTube Music Mix
                shelfCache["youtube_mix"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_youtube_mix_made",
                            title = "YouTube Music Mix",
                            subtitle = "Tailored tracks based on your YouTube listening",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }

                if (historySongs.isNotEmpty()) {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_recent",
                            title = "Recently Played",
                            subtitle = "Jump back in",
                            layout = ShelfLayout.CAROUSEL,
                            songs = historySongs.take(20)
                        )
                    )
                }

                if (favoriteSongs.isNotEmpty()) {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_favorites",
                            title = "Your Favorites & Loved",
                            subtitle = "Tracks you have saved across all services",
                            layout = ShelfLayout.COMPACT_GRID,
                            songs = favoriteSongs.take(20)
                        )
                    )
                }

                shelfCache["suggestions_for_you"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_suggestions",
                            title = "Suggestions For You",
                            subtitle = "Tailored tracks you might enjoy",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
            }
            "Romantic & Chill" -> {
                shelfCache["romantic_melodies"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_romantic",
                            title = "Romantic Melodies",
                            subtitle = "Heartfelt love songs & acoustics",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
                shelfCache["lofi_chill"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_lofi_chill",
                            title = "Late Night Lo-Fi & Chill",
                            subtitle = "Relaxing lofi beats & mellow vibes",
                            layout = ShelfLayout.COMPACT_GRID,
                            songs = it
                        )
                    )
                }
                shelfCache["acoustic_peace"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_acoustic_peace",
                            title = "Acoustic Pop & Unplugged",
                            subtitle = "Pure acoustic strings & raw vocals",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
            }
            "Party & Dance" -> {
                shelfCache["party_anthems"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_party_anthems",
                            title = "Desi Party Anthems",
                            subtitle = "Club bangers & high energy hits",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
                shelfCache["punjabi_hits"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_punjabi_hits",
                            title = "Punjabi Dance Beats",
                            subtitle = "High energy grooves",
                            layout = ShelfLayout.COMPACT_GRID,
                            songs = it
                        )
                    )
                }
            }
            "Workout" -> {
                shelfCache["workout_beats"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_workout_beats",
                            title = "High Energy Workout Hits",
                            subtitle = "Pump up your workout session",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
            }
            "Lo-Fi" -> {
                shelfCache["lofi_chill"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_lofi_chill",
                            title = "Indian Lo-Fi Chill Beats",
                            subtitle = "Midnight ambient lo-fi",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }
            }
            else -> {
                // "All" - Curated Master Feed with Rich Visual Hierarchy & Deep Infinite Shelves
                val spotifyFavorites = favoriteSongs.filter { it.providerId == "spotify" || it.id.startsWith("sp_") }
                val youtubeFavorites = favoriteSongs.filter {
                    !it.id.startsWith("sp_") && !it.id.startsWith("local_") &&
                    it.providerId != "local_download" && it.providerId != "spotify" && it.providerId != "local"
                }
                val isSpotifyConnected = spotifyAccount.value.isLoggedIn
                val isYouTubeConnected = userAccount.value.isLoggedIn

                // 1. Quick Picks (COMPACT_GRID - 4 items per column)
                // If user has listening history: show recently played songs in exact chronological order (newest first).
                // If history has fewer than 16 songs, pad with curated suggestions in deterministic order (no shuffle).
                // If history is empty (new user): show curated popular suggestions in deterministic order.
                val quickPicksCandidates = if (historySongs.isNotEmpty()) {
                    val recentUnique = historySongs.distinctBy { it.id }
                    if (recentUnique.size >= 16) {
                        recentUnique.take(16)
                    } else {
                        val fallbackPool = buildList {
                            shelfCache["trending_india"]?.let { addAll(it) }
                            shelfCache["malayalam_trending"]?.let { addAll(it) }
                            shelfCache["tamil_hits"]?.let { addAll(it) }
                            shelfCache["bollywood_hits"]?.let { addAll(it) }
                            shelfCache["suggestions_for_you"]?.let { addAll(it) }
                        }.distinctBy { it.id }
                        (recentUnique + fallbackPool.filter { fb -> recentUnique.none { it.id == fb.id } }).take(16)
                    }
                } else {
                    // New user / fresh install: curated popular picks in fixed deterministic order (no random shuffle)
                    buildList {
                        shelfCache["trending_india"]?.let { addAll(it.take(4)) }
                        shelfCache["malayalam_trending"]?.let { addAll(it.take(4)) }
                        shelfCache["tamil_hits"]?.let { addAll(it.take(4)) }
                        shelfCache["bollywood_hits"]?.let { addAll(it.take(4)) }
                        shelfCache["suggestions_for_you"]?.let { addAll(it.take(4)) }
                    }.distinctBy { it.id }.take(16)
                }

                val quickPicksSubtitle = if (historySongs.isNotEmpty()) {
                    "Recently played & picks for you"
                } else {
                    "Top trending songs you'll love"
                }

                if (quickPicksCandidates.isNotEmpty()) {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_quick_picks",
                            title = "Quick Picks",
                            subtitle = quickPicksSubtitle,
                            layout = ShelfLayout.COMPACT_GRID,
                            songs = quickPicksCandidates
                        )
                    )
                }

                // 2. Latest Releases (FEATURED_LARGE - Big Cards)
                (shelfCache["new_releases"] ?: shelfCache["trending_india"])?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_new_releases",
                            title = "Latest Releases",
                            subtitle = "Fresh drops and new music releases",
                            layout = ShelfLayout.FEATURED_LARGE,
                            songs = it
                        )
                    )
                }

                // 3. Spotify Personalized Shelves (if connected)
                if (isSpotifyConnected) {
                    if (spotifyFavorites.isNotEmpty()) {
                        shelvesList.add(
                            HomeShelf(
                                id = "shelf_spotify_favorites",
                                title = "From Your Spotify Library",
                                subtitle = "Your top liked tracks synced from Spotify",
                                layout = ShelfLayout.COMPACT_GRID,
                                songs = spotifyFavorites.take(16)
                            )
                        )
                    }
                    shelfCache["spotify_recommendations"]?.takeIf { it.isNotEmpty() }?.let {
                        shelvesList.add(
                            HomeShelf(
                                id = "shelf_spotify_recommendations",
                                title = "Based on Your Spotify Taste",
                                subtitle = "Recommendations curated from your Spotify profile",
                                layout = ShelfLayout.CAROUSEL,
                                songs = it
                            )
                        )
                    }
                }

                // 4. YouTube Personalized Shelves (if connected)
                if (isYouTubeConnected) {
                    if (youtubeFavorites.isNotEmpty()) {
                        shelvesList.add(
                            HomeShelf(
                                id = "shelf_youtube_favorites",
                                title = "From Your YouTube Music",
                                subtitle = "Your synced liked tracks & library",
                                layout = ShelfLayout.COMPACT_GRID,
                                songs = youtubeFavorites.take(16)
                            )
                        )
                    }
                    shelfCache["youtube_mix"]?.takeIf { it.isNotEmpty() }?.let {
                        shelvesList.add(
                            HomeShelf(
                                id = "shelf_youtube_mix",
                                title = "YouTube Music Mix",
                                subtitle = "Recommended based on your YouTube listening",
                                layout = ShelfLayout.CAROUSEL,
                                songs = it
                            )
                        )
                    }
                }

                val taste = cachedUserTaste ?: extractUserTaste(historySongs, favoriteSongs)
                val hasUserTaste = taste.topArtists.isNotEmpty()

                // Top Artists Shelf (ARTIST_CIRCLE)
                if (artistCache.isNotEmpty()) {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_top_artists",
                            title = if (hasUserTaste) "Artists You Love" else "Top Popular Artists",
                            subtitle = if (hasUserTaste) "Based on your liked songs & listening history" else "Explore popular voices across Malayalam, Tamil & Hindi",
                            layout = ShelfLayout.ARTIST_CIRCLE,
                            artists = artistCache
                        )
                    )
                }

                // DYNAMIC USER TOP ARTIST SHELF #0 (User's #1 most listened artist!)
                val topArtist0 = taste.topArtists.getOrNull(0)?.artistName
                val dynamic0 = (if (topArtist0 != null) shelfCache["dynamic_artist_0"] else null)
                    ?: shelfCache["malayalam_sushin"] ?: shelfCache["malayalam_trending"]
                dynamic0?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_dynamic_artist_0",
                            title = if (topArtist0 != null) "Best of $topArtist0" else "Malayalam Hits & New Wave",
                            subtitle = if (topArtist0 != null) "Because you listen to $topArtist0" else "Modern Mollywood soundscape",
                            layout = ShelfLayout.COMPACT_GRID,
                            songs = it
                        )
                    )
                }

                // DYNAMIC USER TOP ARTIST SHELF #1 (User's #2 most listened artist!)
                val topArtist1 = taste.topArtists.getOrNull(1)?.artistName
                val dynamic1 = (if (topArtist1 != null) shelfCache["dynamic_artist_1"] else null)
                    ?: shelfCache["tamil_anirudh"] ?: shelfCache["tamil_hits"]
                dynamic1?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_dynamic_artist_1",
                            title = if (topArtist1 != null) "More from $topArtist1" else "Tamil Rockstar Hits",
                            subtitle = if (topArtist1 != null) "Based on your recent listening" else "High energy Kollywood bangers",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }

                // DYNAMIC SEED SONG RECOMMENDATIONS ("Because You Listened to...")
                val seed = taste.seedRecentSong
                shelfCache["dynamic_seed_song"]?.takeIf { it.isNotEmpty() && seed != null }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_dynamic_seed_song",
                            title = "Because You Listened to ${seed!!.title}",
                            subtitle = "Similar tracks & recommendations inspired by ${seed.artist}",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }

                // DYNAMIC HEAVY ROTATION
                shelfCache["heavy_rotation"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_heavy_rotation",
                            title = "Your Heavy Rotation",
                            subtitle = "Songs you keep coming back to",
                            layout = ShelfLayout.COMPACT_GRID,
                            songs = it
                        )
                    )
                }

                // DYNAMIC USER TOP ARTIST SHELF #2 (User's #3 most listened artist!)
                val topArtist2 = taste.topArtists.getOrNull(2)?.artistName
                val dynamic2 = (if (topArtist2 != null) shelfCache["dynamic_artist_2"] else null)
                    ?: shelfCache["hindi_arijit"] ?: shelfCache["bollywood_hits"]
                dynamic2?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_dynamic_artist_2",
                            title = if (topArtist2 != null) "Deep Dive: $topArtist2" else "Bollywood Soulful Hits",
                            subtitle = if (topArtist2 != null) "Popular chartbusters & essentials" else "Heart-touching Bollywood vocals",
                            layout = ShelfLayout.COMPACT_GRID,
                            songs = it
                        )
                    )
                }

                // DYNAMIC USER TOP ARTIST SHELF #3 (User's #4 most listened artist!)
                val topArtist3 = taste.topArtists.getOrNull(3)?.artistName
                val dynamic3 = (if (topArtist3 != null) shelfCache["dynamic_artist_3"] else null)
                    ?: shelfCache["tamil_yuvan"]
                dynamic3?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(
                        HomeShelf(
                            id = "shelf_dynamic_artist_3",
                            title = if (topArtist3 != null) "In the Spotlight: $topArtist3" else "South Cinema Chartbusters",
                            subtitle = if (topArtist3 != null) "Hits & collaborations" else "Soul-stirring youth anthems",
                            layout = ShelfLayout.CAROUSEL,
                            songs = it
                        )
                    )
                }

                // LANGUAGE SHELVES ORDERED BY USER'S DOMINANT TASTE
                when (taste.dominantLanguage) {
                    "Tamil" -> {
                        shelfCache["tamil_hits"]?.takeIf { it.isNotEmpty() }?.let {
                            shelvesList.add(HomeShelf(id = "shelf_tamil_hits", title = "Tamil Blockbusters", subtitle = "Latest Kollywood blockbuster anthems", layout = ShelfLayout.CAROUSEL, songs = it))
                        }
                        shelfCache["tamil_romance"]?.takeIf { it.isNotEmpty() }?.let {
                            shelvesList.add(HomeShelf(id = "shelf_tamil_romance", title = "Tamil Romantic Melodies", subtitle = "Soulful love songs", layout = ShelfLayout.COMPACT_GRID, songs = it))
                        }
                        shelfCache["malayalam_trending"]?.takeIf { it.isNotEmpty() }?.let {
                            shelvesList.add(HomeShelf(id = "shelf_malayalam_trending", title = "Malayalam Top Trending", subtitle = "Latest Mollywood chartbusters & viral tracks", layout = ShelfLayout.CAROUSEL, songs = it))
                        }
                        shelfCache["bollywood_hits"]?.takeIf { it.isNotEmpty() }?.let {
                            shelvesList.add(HomeShelf(id = "shelf_bollywood_hits", title = "Bollywood Blockbusters", subtitle = "All-time popular Hindi anthems", layout = ShelfLayout.CAROUSEL, songs = it))
                        }
                        shelfCache["trending_india"]?.takeIf { it.isNotEmpty() }?.let {
                            shelvesList.add(HomeShelf(id = "shelf_trending_india", title = "Trending Now in India", subtitle = "Top chartbusters right now", layout = ShelfLayout.COMPACT_GRID, songs = it))
                        }
                    }
                    "Hindi" -> {
                        shelfCache["bollywood_hits"]?.takeIf { it.isNotEmpty() }?.let {
                            shelvesList.add(HomeShelf(id = "shelf_bollywood_hits", title = "Bollywood Blockbusters", subtitle = "All-time popular Hindi anthems", layout = ShelfLayout.CAROUSEL, songs = it))
                        }
                        shelfCache["hindi_melodies"]?.takeIf { it.isNotEmpty() }?.let {
                            shelvesList.add(HomeShelf(id = "shelf_hindi_melodies", title = "Hindi Romantic Melodies", subtitle = "Evergreen love anthems", layout = ShelfLayout.COMPACT_GRID, songs = it))
                        }
                        shelfCache["trending_india"]?.takeIf { it.isNotEmpty() }?.let {
                            shelvesList.add(HomeShelf(id = "shelf_trending_india", title = "Trending Now in India", subtitle = "Top chartbusters right now", layout = ShelfLayout.COMPACT_GRID, songs = it))
                        }
                        shelfCache["malayalam_trending"]?.takeIf { it.isNotEmpty() }?.let {
                            shelvesList.add(HomeShelf(id = "shelf_malayalam_trending", title = "Malayalam Top Trending", subtitle = "Latest Mollywood chartbusters & viral tracks", layout = ShelfLayout.CAROUSEL, songs = it))
                        }
                        shelfCache["tamil_hits"]?.takeIf { it.isNotEmpty() }?.let {
                            shelvesList.add(HomeShelf(id = "shelf_tamil_hits", title = "Tamil Blockbusters", subtitle = "Latest Kollywood blockbuster anthems", layout = ShelfLayout.CAROUSEL, songs = it))
                        }
                    }
                    else -> {
                        // Default / Malayalam Dominant
                        shelfCache["malayalam_trending"]?.takeIf { it.isNotEmpty() }?.let {
                            shelvesList.add(HomeShelf(id = "shelf_malayalam_trending", title = "Malayalam Top Trending", subtitle = "Latest Mollywood chartbusters & viral tracks", layout = ShelfLayout.CAROUSEL, songs = it))
                        }
                        shelfCache["tamil_hits"]?.takeIf { it.isNotEmpty() }?.let {
                            shelvesList.add(HomeShelf(id = "shelf_tamil_hits", title = "Tamil Blockbusters", subtitle = "Latest Kollywood blockbuster anthems", layout = ShelfLayout.CAROUSEL, songs = it))
                        }
                        shelfCache["trending_india"]?.takeIf { it.isNotEmpty() }?.let {
                            shelvesList.add(HomeShelf(id = "shelf_trending_india", title = "Trending Now in India", subtitle = "Top chartbusters right now", layout = ShelfLayout.COMPACT_GRID, songs = it))
                        }
                        shelfCache["bollywood_hits"]?.takeIf { it.isNotEmpty() }?.let {
                            shelvesList.add(HomeShelf(id = "shelf_bollywood_hits", title = "Bollywood Blockbusters", subtitle = "All-time popular Hindi anthems", layout = ShelfLayout.CAROUSEL, songs = it))
                        }
                        shelfCache["malayalam_melodies"]?.takeIf { it.isNotEmpty() }?.let {
                            shelvesList.add(HomeShelf(id = "shelf_malayalam_melodies", title = "Malayalam Evergreen Melodies", subtitle = "K.J. Yesudas, K.S. Chithra & Vidyasagar magic", layout = ShelfLayout.COMPACT_GRID, songs = it))
                        }
                    }
                }

                // THEMATIC & MOOD SHELVES
                (shelfCache["trending_south"] ?: shelfCache["suggestions_for_you"])?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(HomeShelf(id = "shelf_trending_south", title = "South Indian Viral Hits", subtitle = "Viral tracks across the south", layout = ShelfLayout.COMPACT_GRID, songs = it))
                }
                shelfCache["indie_india"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(HomeShelf(id = "shelf_indie_india", title = "Indian Indie Pop Fresh Finds", subtitle = "New generation independent music", layout = ShelfLayout.CAROUSEL, songs = it))
                }
                shelfCache["lofi_chill"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(HomeShelf(id = "shelf_lofi_chill", title = "Late Night Lo-Fi & Chill", subtitle = "Relaxing lofi beats", layout = ShelfLayout.COMPACT_GRID, songs = it))
                }
                (shelfCache["party_anthems"] ?: shelfCache["punjabi_hits"])?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(HomeShelf(id = "shelf_party_anthems", title = "Desi Party Anthems", subtitle = "Club bangers & celebration tracks", layout = ShelfLayout.CAROUSEL, songs = it))
                }
                shelfCache["punjabi_hits"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(HomeShelf(id = "shelf_punjabi_hits", title = "Punjabi Hits & Pop", subtitle = "High energy beats & grooves", layout = ShelfLayout.COMPACT_GRID, songs = it))
                }
                shelfCache["workout_beats"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(HomeShelf(id = "shelf_workout_beats", title = "High Energy Workout Hits", subtitle = "Pump up your workout session", layout = ShelfLayout.CAROUSEL, songs = it))
                }
                shelfCache["acoustic_peace"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(HomeShelf(id = "shelf_acoustic_peace", title = "Acoustic Pop & Unplugged", subtitle = "Pure acoustic strings & raw vocals", layout = ShelfLayout.COMPACT_GRID, songs = it))
                }
                shelfCache["malayalam_nostalgia"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(HomeShelf(id = "shelf_malayalam_nostalgia", title = "90s & 2000s Mollywood Nostalgia", subtitle = "Golden era Malayalam classics", layout = ShelfLayout.CAROUSEL, songs = it))
                }
                shelfCache["global_top"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(HomeShelf(id = "shelf_global_top", title = "Global Top 50 Chartbusters", subtitle = "Worldwide trending hits", layout = ShelfLayout.COMPACT_GRID, songs = it))
                }
                shelfCache["devotional_peace"]?.takeIf { it.isNotEmpty() }?.let {
                    shelvesList.add(HomeShelf(id = "shelf_devotional_peace", title = "Soulful Melodies & Peace", subtitle = "Calm and serene musical moments", layout = ShelfLayout.CAROUSEL, songs = it))
                }
            }
        }

        _uiState.update { it.copy(shelves = shelvesList) }
        prefetchShelfArtworks(shelvesList)
    }

    private fun prefetchShelfArtworks(shelves: List<HomeShelf>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val imageLoader = coil.Coil.imageLoader(context)
            val songsToPreload = shelves.take(6).flatMap { it.songs.take(4) }.distinctBy { it.id }
            for (song in songsToPreload) {
                val artworkUrl = com.lofiq.core.common.ArtworkUtils.getOptimalArtwork(song)
                if (artworkUrl.isNotBlank()) {
                    runCatching {
                        val req = coil.request.ImageRequest.Builder(context)
                            .data(artworkUrl)
                            .size(300, 300)
                            .build()
                        imageLoader.enqueue(req)
                    }
                }
            }
        }
    }

    fun playSong(song: Song, shelfSongs: List<Song>) {
        val startIndex = shelfSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        playbackEngine.playQueue(shelfSongs, startIndex)
    }

    fun playArtist(artist: ArtistCardData) {
        if (artist.songs.isNotEmpty()) {
            playbackEngine.playQueue(artist.songs, 0)
        } else {
            viewModelScope.launch {
                val songs = musicRepository.searchSongs(artist.name, limit = 20)
                if (songs.isNotEmpty()) {
                    playbackEngine.playQueue(songs, 0)
                }
            }
        }
    }

    fun onAuthCookiesCaptured(cookies: String) {
        viewModelScope.launch {
            _isAuthenticating.value = true
            authRepository.saveCookiesAndSyncProfile(cookies)
            val syncLiked = launch { libraryRepository.syncRemoteLikedSongs(cookies) }
            val syncPlaylists = launch { libraryRepository.syncRemotePlaylists(cookies) }
            syncLiked.join()
            syncPlaylists.join()
            _isAuthenticating.value = false
            refreshFeed()
        }
    }

    fun onSpotifyCookiesCaptured(cookies: String) {
        viewModelScope.launch {
            _isAuthenticating.value = true
            val result = spotifyAuthRepository.saveCookiesAndSyncProfile(cookies)
            if (result.isSuccess) {
                spotifyAuthRepository.syncSpotifyLikedSongs()
                spotifyAuthRepository.syncSpotifyPlaylists()
            }
            _isAuthenticating.value = false
            refreshFeed()
        }
    }

    fun refreshAuthProfile() {
        viewModelScope.launch {
            val cookies = authRepository.getCookies() ?: return@launch
            _isAuthenticating.value = true
            authRepository.saveCookiesAndSyncProfile(cookies)
            val syncLiked = launch { libraryRepository.syncRemoteLikedSongs(cookies) }
            val syncPlaylists = launch { libraryRepository.syncRemotePlaylists(cookies) }
            syncLiked.join()
            syncPlaylists.join()
            _isAuthenticating.value = false
            refreshFeed()
        }
    }

    fun signOutAuth() {
        viewModelScope.launch {
            authRepository.signOut()
            refreshFeed()
        }
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            libraryRepository.toggleFavorite(song)
        }
    }
}

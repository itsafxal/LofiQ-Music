package com.lofiq.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lofiq.core.common.LofiQLogger
import com.lofiq.core.model.Song
import com.lofiq.data.repository.LibraryRepository
import com.lofiq.data.repository.MusicRepository
import com.lofiq.data.repository.SearchHistoryRepository
import com.lofiq.playback.engine.PlaybackEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val topResult: Song? = null,
    val searchResults: List<Song> = emptyList(),
    val querySuggestions: List<String> = emptyList(),
    val isSearching: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val libraryRepository: LibraryRepository,
    private val searchHistoryRepository: SearchHistoryRepository,
    val playbackEngine: PlaybackEngine
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val searchHistory: StateFlow<List<String>> = searchHistoryRepository.searchHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            _searchQuery
                .debounce(220L)
                .distinctUntilChanged()
                .collect { query ->
                    performSearch(query)
                }
        }
    }

    fun onQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
        _uiState.update { it.copy(query = newQuery) }
    }

    fun submitQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isNotBlank()) {
            _searchQuery.value = trimmed
            _uiState.update { it.copy(query = trimmed) }
            saveQueryToHistory(trimmed)
            performSearch(trimmed)
        }
    }

    fun clearQuery() {
        _searchQuery.value = ""
        _uiState.update {
            it.copy(
                query = "",
                topResult = null,
                searchResults = emptyList(),
                querySuggestions = emptyList(),
                isSearching = false
            )
        }
    }

    fun removeHistoryItem(query: String) {
        viewModelScope.launch {
            searchHistoryRepository.removeQuery(query)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            searchHistoryRepository.clearHistory()
        }
    }

    private fun saveQueryToHistory(query: String) {
        viewModelScope.launch {
            searchHistoryRepository.addQuery(query)
        }
    }

    fun refresh() {
        val trimmed = _searchQuery.value.trim()
        if (trimmed.isNotBlank()) {
            viewModelScope.launch {
                _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
                try {
                    val suggestionsDef = async { musicRepository.getSearchSuggestions(trimmed) }
                    val songsDef = async { musicRepository.searchSongs(trimmed, limit = 30) }

                    val suggestions = try { suggestionsDef.await() } catch (e: Exception) { emptyList() }
                    val results = try { songsDef.await() } catch (e: Exception) { emptyList() }

                    val cleanQ = trimmed.lowercase()
                    val bestMatch = results.firstOrNull { song ->
                        val t = song.title.lowercase()
                        t == cleanQ || t.startsWith(cleanQ)
                    } ?: results.firstOrNull()

                    val otherResults = if (bestMatch != null) {
                        results.filter { it.id != bestMatch.id }
                    } else {
                        results
                    }

                    _uiState.update {
                        it.copy(
                            topResult = bestMatch,
                            searchResults = otherResults,
                            querySuggestions = suggestions.filter { s -> !s.equals(trimmed, ignoreCase = true) },
                            isSearching = false,
                            isRefreshing = false
                        )
                    }
                } catch (e: Exception) {
                    _uiState.update { it.copy(isRefreshing = false) }
                }
            }
        }
    }

    private fun performSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _uiState.update {
                it.copy(
                    topResult = null,
                    searchResults = emptyList(),
                    querySuggestions = emptyList(),
                    isSearching = false
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, errorMessage = null) }
            try {
                // 1. Fetch suggestions and songs concurrently
                val suggestionsDef = async { musicRepository.getSearchSuggestions(trimmed) }
                val songsDef = async { musicRepository.searchSongs(trimmed, limit = 30) }

                val suggestions = try { suggestionsDef.await() } catch (e: Exception) { emptyList() }
                val results = try { songsDef.await() } catch (e: Exception) { emptyList() }

                // 2. Identify Best Match / Top Result (e.g. "Amsham" when searching "am" or "amsh")
                val cleanQ = trimmed.lowercase()
                val bestMatch = results.firstOrNull { song ->
                    val t = song.title.lowercase()
                    t == cleanQ || t.startsWith(cleanQ)
                } ?: results.firstOrNull()

                val otherResults = if (bestMatch != null) {
                    results.filter { it.id != bestMatch.id }
                } else {
                    results
                }

                _uiState.update {
                    it.copy(
                        topResult = bestMatch,
                        searchResults = otherResults,
                        querySuggestions = suggestions.filter { s -> !s.equals(trimmed, ignoreCase = true) },
                        isSearching = false
                    )
                }
                LofiQLogger.d(LofiQLogger.Category.SEARCH, "Search completed for '$trimmed': top='${bestMatch?.title}', ${otherResults.size} others")
            } catch (e: Exception) {
                LofiQLogger.e(LofiQLogger.Category.SEARCH, "Search error: ${e.message}")
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        errorMessage = "Failed to search: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun playSong(song: Song) {
        // Save the chosen song's clean title to search history (not intermediate typed fragments)
        val cleanTitle = song.title
            .replace(Regex("""(?i)\(official\s*(music\s*)?video\)"""), "")
            .replace(Regex("""(?i)\[official\s*(music\s*)?video\]"""), "")
            .replace(Regex("""(?i)\(official\s*audio\)"""), "")
            .replace(Regex("""(?i)\[official\s*audio\]"""), "")
            .replace(Regex("""(?i)\(lyrics\)"""), "")
            .replace(Regex("""(?i)\[lyrics\]"""), "")
            .replace(Regex("""(?i)\(full\s*song\)"""), "")
            .replace(Regex("""(?i)\[full\s*song\]"""), "")
            .trim()
            .ifBlank { song.title }

        saveQueryToHistory(cleanTitle)

        // Play the chosen song and allow PlaybackEngine to construct the smart radio queue
        // (matching artist hits, Malayalam/vibe recommendations, and YouTube radio)
        // instead of queuing unrelated search results!
        playbackEngine.playSong(song)
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            libraryRepository.toggleFavorite(song)
        }
    }
}


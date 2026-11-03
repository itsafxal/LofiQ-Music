package com.lofiq.data.model

import com.lofiq.core.model.Song
import kotlinx.serialization.Serializable

@Serializable
enum class SearchFilter(val displayName: String) {
    ALL("All"),
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    PLAYLISTS("Playlists"),
    VIDEOS("Videos")
}

@Serializable
data class SearchSuggestion(
    val query: String,
    val isHistory: Boolean = false
)

@Serializable
data class SearchResult(
    val query: String,
    val songs: List<Song> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val artists: List<ArtistItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
    val continuationToken: String? = null
) {
    val isEmpty: Boolean
        get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()

    val totalItemCount: Int
        get() = songs.size + albums.size + artists.size + playlists.size
}

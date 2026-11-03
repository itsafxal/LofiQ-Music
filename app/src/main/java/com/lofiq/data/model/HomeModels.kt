package com.lofiq.data.model

import com.lofiq.core.model.Song
import kotlinx.serialization.Serializable

@Serializable
enum class ShelfLayout {
    BOXY_CARDS,
    COMPACT_GRID,
    CIRCULAR_ARTISTS,
    HORIZONTAL_CAROUSEL
}

@Serializable
data class HomeShelf(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val layout: ShelfLayout = ShelfLayout.BOXY_CARDS,
    val items: List<MusicItem> = emptyList(),
    val seeMoreQuery: String? = null
)

@Serializable
data class HomeFeed(
    val chips: List<String> = listOf("Energize", "Relax", "Focus", "Commute", "Workout", "Party"),
    val selectedChip: String? = null,
    val quickPicks: List<Song> = emptyList(),
    val shelves: List<HomeShelf> = emptyList(),
    val isRefreshing: Boolean = false
)

@Serializable
data class ArtistCardData(
    val id: String,
    val name: String,
    val subtitle: String = "Artist",
    val artworkUrl: String,
    val songs: List<Song> = emptyList()
)


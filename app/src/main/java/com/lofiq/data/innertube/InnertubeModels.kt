package com.lofiq.data.innertube

import com.lofiq.core.model.Song
import com.lofiq.data.model.AlbumItem
import com.lofiq.data.model.ArtistItem
import com.lofiq.data.model.PlaylistItem
import kotlinx.serialization.Serializable

@Serializable
data class InnertubeContext(
    val clientName: String = "WEB_REMIX",
    val clientVersion: String = "1.20260320.01.00",
    val hl: String = "en",
    val gl: String = "US"
)

@Serializable
data class InnertubeBrowseItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val artworkUrl: String = "",
    val type: String = "song" // "song", "album", "artist", "playlist"
) {
    fun toSong(): Song = Song(
        id = id,
        title = title,
        artist = subtitle,
        artworkUrl = artworkUrl,
        highResArtworkUrl = artworkUrl.replace("=w120-h120", "=w1200-h1200")
    )
}

@Serializable
data class InnertubeShelf(
    val title: String,
    val items: List<InnertubeBrowseItem> = emptyList()
)

@Serializable
data class InnertubeBrowseResult(
    val shelves: List<InnertubeShelf> = emptyList(),
    val continuationToken: String? = null
)

@Serializable
data class InnertubeNextResult(
    val currentSongId: String,
    val upcomingSongs: List<Song> = emptyList(),
    val lyricsBrowseId: String? = null
)

package com.lofiq.data.model

import com.lofiq.core.model.Song
import kotlinx.serialization.Serializable

@Serializable
data class ArtistPageDetails(
    val id: String,
    val name: String,
    val bannerUrl: String = "",
    val avatarUrl: String = "",
    val description: String = "",
    val subscriberCount: String = "",
    val topSongs: List<Song> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val singles: List<AlbumItem> = emptyList(),
    val relatedArtists: List<ArtistItem> = emptyList()
)

@Serializable
data class AlbumPageDetails(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String = "",
    val artworkUrl: String = "",
    val releaseYear: String = "",
    val trackCount: Int = 0,
    val durationSeconds: Long = 0L,
    val description: String = "",
    val tracks: List<Song> = emptyList(),
    val otherAlbumsByArtist: List<AlbumItem> = emptyList()
)

package com.lofiq.data.model

import com.lofiq.core.model.AudioQuality
import com.lofiq.core.model.Song
import kotlinx.serialization.Serializable

/**
 * Common polymorphic interface for all playable/browsable music media items.
 */
@Serializable
sealed interface MusicItem {
    val id: String
    val title: String
    val artworkUrl: String
}

@Serializable
data class TrackItem(
    override val id: String,
    override val title: String,
    val artist: String,
    val album: String = "",
    val durationSeconds: Long = 0L,
    override val artworkUrl: String = "",
    val highResArtworkUrl: String = "",
    val isExplicit: Boolean = false,
    val quality: AudioQuality = AudioQuality.HIGH,
    val isFavorite: Boolean = false
) : MusicItem {
    val durationMs: Long
        get() = durationSeconds * 1000L

    fun toSong(): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationSeconds = durationSeconds,
        artworkUrl = artworkUrl,
        highResArtworkUrl = highResArtworkUrl,
        quality = quality,
        isFavorite = isFavorite
    )
}

@Serializable
data class AlbumItem(
    override val id: String,
    override val title: String,
    val artist: String,
    val year: String = "",
    override val artworkUrl: String = "",
    val trackCount: Int = 0,
    val browseId: String = ""
) : MusicItem

@Serializable
data class ArtistItem(
    override val id: String,
    override val title: String, // Artist name
    val imageUrl: String = "",
    val subscriberCount: String = "",
    val description: String = ""
) : MusicItem {
    override val artworkUrl: String
        get() = imageUrl
}

@Serializable
data class PlaylistItem(
    override val id: String,
    override val title: String,
    val author: String = "YouTube Music",
    val songCount: Int = 0,
    override val artworkUrl: String = "",
    val description: String = "",
    val isCustom: Boolean = false
) : MusicItem

@Serializable
data class VideoItem(
    override val id: String,
    override val title: String,
    val channelTitle: String,
    val viewsCount: String = "",
    override val artworkUrl: String = "",
    val durationSeconds: Long = 0L
) : MusicItem

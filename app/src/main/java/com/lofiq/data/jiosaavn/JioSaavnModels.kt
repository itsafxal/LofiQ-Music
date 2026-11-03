package com.lofiq.data.jiosaavn

import com.lofiq.core.model.AudioQuality
import com.lofiq.core.model.Song
import kotlinx.serialization.Serializable

@Serializable
data class JioSaavnSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val year: String = "",
    val durationSeconds: Long = 0L,
    val artworkUrl: String = "",
    val highResArtworkUrl: String = "",
    val encryptedMediaUrl: String = "",
    val mediaPreviewUrl: String = "",
    val has320kbps: Boolean = true,
    val isExplicit: Boolean = false,
    val language: String = "",
    val label: String = ""
) {
    val durationMs: Long
        get() = durationSeconds * 1000L

    fun toSong(quality: AudioQuality = AudioQuality.HIGH): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationSeconds = durationSeconds,
        artworkUrl = artworkUrl,
        highResArtworkUrl = highResArtworkUrl,
        providerId = "jiosaavn",
        quality = quality
    )
}

@Serializable
data class JioSaavnAlbum(
    val id: String,
    val title: String,
    val artist: String,
    val year: String = "",
    val artworkUrl: String = "",
    val songCount: Int = 0,
    val songs: List<JioSaavnSong> = emptyList()
)

@Serializable
data class JioSaavnArtist(
    val id: String,
    val name: String,
    val imageUrl: String = "",
    val followerCount: String = "",
    val topSongs: List<JioSaavnSong> = emptyList(),
    val topAlbums: List<JioSaavnAlbum> = emptyList()
)

@Serializable
data class JioSaavnSearchResult(
    val songs: List<JioSaavnSong> = emptyList(),
    val albums: List<JioSaavnAlbum> = emptyList(),
    val artists: List<JioSaavnArtist> = emptyList()
)

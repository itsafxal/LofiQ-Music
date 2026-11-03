package com.lofiq.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationSeconds: Long = 0L,
    val artworkUrl: String = "",
    val highResArtworkUrl: String = "",
    val sourceUrl: String = "",
    val isFavorite: Boolean = false,
    val providerId: String = "youtube",
    val quality: AudioQuality = AudioQuality.HIGH,
    val addedAt: Long = System.currentTimeMillis(),
    val playCount: Long = 0L,
    val lyrics: String? = null
) {
    val durationMs: Long
        get() = durationSeconds * 1000L
}

@Serializable
data class Artist(
    val id: String,
    val name: String,
    val imageUrl: String = "",
    val songCount: Int = 0,
    val description: String = ""
)

@Serializable
data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String = "",
    val year: String = "",
    val songCount: Int = 0
)

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val description: String = "",
    val artworkUrl: String = "",
    val isSystem: Boolean = false,
    val songCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class QueueItem(
    val id: String,
    val song: Song,
    val originalIndex: Int = 0,
    val isCurrent: Boolean = false
)

package com.lofiq.data.firebase

import com.lofiq.core.model.AudioQuality
import com.lofiq.core.model.Playlist
import com.lofiq.core.model.Song
import kotlinx.serialization.Serializable

@Serializable
data class FirebaseUserProfile(
    val uid: String = "",
    val displayName: String = "LofiQ User",
    val email: String? = null,
    val photoUrl: String? = null,
    val isAnonymous: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class FirestoreFavoriteSong(
    val songId: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUrl: String = "",
    val highResArtworkUrl: String = "",
    val durationSeconds: Long = 0L,
    val providerId: String = "youtube",
    val sourceUrl: String = "",
    val addedAt: Long = System.currentTimeMillis()
) {
    fun toSong(): Song {
        return Song(
            id = songId,
            title = title,
            artist = artist,
            album = album,
            artworkUrl = artworkUrl,
            highResArtworkUrl = highResArtworkUrl.ifEmpty { artworkUrl },
            durationSeconds = durationSeconds,
            providerId = providerId,
            sourceUrl = sourceUrl,
            isFavorite = true,
            quality = AudioQuality.HIGH,
            addedAt = addedAt
        )
    }

    companion object {
        fun fromSong(song: Song, addedAt: Long = System.currentTimeMillis()): FirestoreFavoriteSong {
            return FirestoreFavoriteSong(
                songId = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                artworkUrl = song.artworkUrl,
                highResArtworkUrl = song.highResArtworkUrl,
                durationSeconds = song.durationSeconds,
                providerId = song.providerId,
                sourceUrl = song.sourceUrl,
                addedAt = addedAt
            )
        }
    }
}

data class FirestorePlaylist(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val artworkUrl: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val songCount: Int = 0,
    val songs: List<FirestoreFavoriteSong> = emptyList()
) {
    fun toPlaylist(): Playlist {
        return Playlist(
            id = id,
            name = name,
            description = description,
            artworkUrl = artworkUrl,
            songCount = songCount.takeIf { it > 0 } ?: songs.size,
            isSystem = false,
            createdAt = createdAt
        )
    }
}

data class FirestoreConnectedCredentials(
    val ytCookies: String? = null,
    val spotifyCookies: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)

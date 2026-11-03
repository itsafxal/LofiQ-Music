package com.lofiq.data.model

import com.lofiq.core.model.Song
import kotlinx.serialization.Serializable

@Serializable
data class PlaylistDetails(
    val id: String,
    val title: String,
    val author: String = "",
    val description: String = "",
    val artworkUrl: String = "",
    val songCount: Int = 0,
    val durationSeconds: Long = 0L,
    val isEditable: Boolean = false,
    val tracks: List<Song> = emptyList(),
    val continuationToken: String? = null
)

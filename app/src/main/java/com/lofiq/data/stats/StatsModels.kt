package com.lofiq.data.stats

import com.lofiq.core.model.Song
import kotlinx.serialization.Serializable

@Serializable
enum class StatsTimeRange(val displayName: String) {
    TODAY("Today"),
    LAST_7_DAYS("Last 7 Days"),
    LAST_30_DAYS("Last 30 Days"),
    THIS_YEAR("This Year"),
    ALL_TIME("All Time")
}

@Serializable
data class TopItemStat(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val artworkUrl: String = "",
    val playCount: Long = 0L,
    val totalMinutesListened: Long = 0L
)

@Serializable
data class ListeningOverview(
    val totalPlayCount: Long = 0L,
    val totalMinutesPlayed: Long = 0L,
    val uniqueTracksCount: Int = 0,
    val uniqueArtistsCount: Int = 0,
    val topSongs: List<TopItemStat> = emptyList(),
    val topArtists: List<TopItemStat> = emptyList(),
    val topAlbums: List<TopItemStat> = emptyList()
)

@Serializable
data class StatsForNerds(
    val songId: String = "",
    val title: String = "",
    val artist: String = "",
    val provider: String = "",
    val streamUrl: String = "",
    val mimeType: String = "audio/mp4",
    val codec: String = "AAC-LC",
    val bitrateKbps: Int = 320,
    val sampleRateHz: Int = 44100,
    val channelCount: Int = 2,
    val bufferHealthMs: Long = 0L,
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val networkSpeedKbps: Long = 0L
)

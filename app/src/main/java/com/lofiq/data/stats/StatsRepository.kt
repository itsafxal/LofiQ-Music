package com.lofiq.data.stats

import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.database.dao.PlaybackHistoryDao
import com.lofiq.core.database.dao.SongDao
import com.lofiq.core.model.Song
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepository @Inject constructor(
    private val songDao: SongDao,
    private val historyDao: PlaybackHistoryDao,
    private val dispatchers: DispatcherProvider
) {
    suspend fun getListeningOverview(timeRange: StatsTimeRange = StatsTimeRange.ALL_TIME): ListeningOverview = withContext(dispatchers.io) {
        val songs = songDao.observeAll().firstOrNull() ?: emptyList()
        if (songs.isEmpty()) return@withContext ListeningOverview()

        val totalPlays = songs.sumOf { it.playCount }
        val totalMinutes = songs.sumOf { (it.playCount * it.durationSeconds) / 60 }

        val topSongs = songs
            .filter { it.playCount > 0 }
            .sortedByDescending { it.playCount }
            .take(10)
            .map {
                TopItemStat(
                    id = it.id,
                    title = it.title,
                    subtitle = it.artist,
                    artworkUrl = it.highResArtworkUrl.ifEmpty { it.artworkUrl },
                    playCount = it.playCount,
                    totalMinutesListened = (it.playCount * it.durationSeconds) / 60
                )
            }

        val topArtists = songs
            .filter { it.playCount > 0 }
            .groupBy { it.artist }
            .map { (artist, songList) ->
                val artistPlays = songList.sumOf { it.playCount }
                val artistMinutes = songList.sumOf { (it.playCount * it.durationSeconds) / 60 }
                val sampleArtwork = songList.firstOrNull()?.let { it.highResArtworkUrl.ifEmpty { it.artworkUrl } } ?: ""
                TopItemStat(
                    id = artist,
                    title = artist,
                    subtitle = "${songList.size} tracks",
                    artworkUrl = sampleArtwork,
                    playCount = artistPlays,
                    totalMinutesListened = artistMinutes
                )
            }
            .sortedByDescending { it.playCount }
            .take(10)

        val topAlbums = songs
            .filter { it.playCount > 0 && it.album.isNotBlank() }
            .groupBy { it.album }
            .map { (album, songList) ->
                val albumPlays = songList.sumOf { it.playCount }
                val albumMinutes = songList.sumOf { (it.playCount * it.durationSeconds) / 60 }
                val sampleArtwork = songList.firstOrNull()?.let { it.highResArtworkUrl.ifEmpty { it.artworkUrl } } ?: ""
                TopItemStat(
                    id = album,
                    title = album,
                    subtitle = songList.firstOrNull()?.artist ?: "",
                    artworkUrl = sampleArtwork,
                    playCount = albumPlays,
                    totalMinutesListened = albumMinutes
                )
            }
            .sortedByDescending { it.playCount }
            .take(10)

        ListeningOverview(
            totalPlayCount = totalPlays,
            totalMinutesPlayed = totalMinutes,
            uniqueTracksCount = songs.size,
            uniqueArtistsCount = songs.distinctBy { it.artist }.size,
            topSongs = topSongs,
            topArtists = topArtists,
            topAlbums = topAlbums
        )
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun getStatsForNerds(
        song: Song?,
        exoPlayer: ExoPlayer,
        provider: String = "YouTube / JioSaavn",
        streamUrl: String = ""
    ): StatsForNerds {
        if (song == null) return StatsForNerds()

        val format = exoPlayer.audioFormat
        val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
        val duration = exoPlayer.duration.coerceAtLeast(0L)
        val buffered = exoPlayer.bufferedPosition.coerceAtLeast(0L)
        val bufferHealth = (buffered - currentPos).coerceAtLeast(0L)

        val mime = format?.sampleMimeType ?: "audio/mp4"
        val bitrate = if (format != null && format.bitrate > 0) format.bitrate / 1000 else 320
        val sampleRate = format?.sampleRate ?: 44100
        val channels = format?.channelCount ?: 2
        val codec = format?.codecs ?: if (mime.contains("opus")) "Opus" else "AAC-LC"

        return StatsForNerds(
            songId = song.id,
            title = song.title,
            artist = song.artist,
            provider = provider,
            streamUrl = streamUrl,
            mimeType = mime,
            codec = codec,
            bitrateKbps = bitrate,
            sampleRateHz = sampleRate,
            channelCount = channels,
            bufferHealthMs = bufferHealth,
            currentPositionMs = currentPos,
            totalDurationMs = duration
        )
    }
}

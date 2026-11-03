package com.lofiq.feature.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.lofiq.core.model.Song
import com.lofiq.playback.engine.PlaybackEngine
import com.lofiq.ui.components.CollectionDetailScreen

@Composable
fun DetailScreen(
    title: String,
    subtitle: String,
    artworkUrl: String,
    tracks: List<Song>,
    playbackEngine: PlaybackEngine,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playerState by playbackEngine.playerState.collectAsState()
    val currentPlayingId = playerState.currentSong?.id

    CollectionDetailScreen(
        title = title,
        subtitle = subtitle,
        typeLabel = "COLLECTION",
        artworkUrl = artworkUrl,
        tracks = tracks,
        isPlaying = playerState.isPlaying,
        currentSongId = currentPlayingId,
        onBack = onNavigateBack,
        onPlaySong = { song, queue ->
            val index = queue.indexOf(song).coerceAtLeast(0)
            playbackEngine.playQueue(queue, index)
        },
        onPlayAll = {
            if (tracks.isNotEmpty()) {
                playbackEngine.playQueue(tracks, 0)
            }
        },
        onShuffle = {
            if (tracks.isNotEmpty()) {
                playbackEngine.playQueue(tracks.shuffled(), 0)
            }
        },
        modifier = modifier
    )
}

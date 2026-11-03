package com.lofiq.data.scrobbling

import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.common.LofiQLogger
import com.lofiq.core.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScrobbleManager @Inject constructor(
    private val lastFmScrobbler: LastFmScrobbler,
    private val listenBrainzScrobbler: ListenBrainzScrobbler,
    private val dispatchers: DispatcherProvider
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private var lastScrobbledSongId: String? = null

    fun onTrackStarted(song: Song, credentialsList: List<ScrobbleCredentials>) {
        val track = ScrobbleTrack(
            title = song.title,
            artist = song.artist,
            album = song.album,
            durationSeconds = song.durationSeconds
        )

        scope.launch {
            for (cred in credentialsList) {
                if (!cred.isEnabled) continue
                when (cred.service) {
                    ScrobbleService.LAST_FM -> lastFmScrobbler.updateNowPlaying(track, cred)
                    ScrobbleService.LISTEN_BRAINZ -> listenBrainzScrobbler.updateNowPlaying(track, cred)
                }
            }
        }
    }

    fun onTrackThresholdReached(song: Song, credentialsList: List<ScrobbleCredentials>) {
        if (lastScrobbledSongId == song.id) return
        lastScrobbledSongId = song.id

        val track = ScrobbleTrack(
            title = song.title,
            artist = song.artist,
            album = song.album,
            durationSeconds = song.durationSeconds
        )

        scope.launch {
            for (cred in credentialsList) {
                if (!cred.isEnabled) continue
                when (cred.service) {
                    ScrobbleService.LAST_FM -> {
                        val success = lastFmScrobbler.scrobble(track, cred)
                        if (success) LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[SCROBBLE] Scrobbled '${song.title}' to Last.fm")
                    }
                    ScrobbleService.LISTEN_BRAINZ -> {
                        val success = listenBrainzScrobbler.scrobble(track, cred)
                        if (success) LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[SCROBBLE] Scrobbled '${song.title}' to ListenBrainz")
                    }
                }
            }
        }
    }
}

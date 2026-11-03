package com.lofiq.data.scrobbling

interface Scrobbler {
    val service: ScrobbleService
    suspend fun updateNowPlaying(track: ScrobbleTrack, credentials: ScrobbleCredentials): Boolean
    suspend fun scrobble(track: ScrobbleTrack, credentials: ScrobbleCredentials): Boolean
}

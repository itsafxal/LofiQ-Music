package com.lofiq.data.scrobbling

import kotlinx.serialization.Serializable

@Serializable
enum class ScrobbleService(val displayName: String) {
    LAST_FM("Last.fm"),
    LISTEN_BRAINZ("ListenBrainz")
}

@Serializable
data class ScrobbleCredentials(
    val service: ScrobbleService,
    val isEnabled: Boolean = false,
    val sessionKey: String = "",
    val username: String = "",
    val token: String = "",
    val apiKey: String = "",
    val apiSecret: String = ""
)

@Serializable
data class ScrobbleTrack(
    val title: String,
    val artist: String,
    val album: String = "",
    val durationSeconds: Long = 0L,
    val timestampSeconds: Long = System.currentTimeMillis() / 1000L
)

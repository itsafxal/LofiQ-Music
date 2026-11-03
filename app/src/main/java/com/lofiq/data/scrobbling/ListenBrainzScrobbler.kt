package com.lofiq.data.scrobbling

import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.common.LofiQLogger
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListenBrainzScrobbler @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val dispatchers: DispatcherProvider
) : Scrobbler {

    override val service: ScrobbleService = ScrobbleService.LISTEN_BRAINZ
    private val apiUrl = "https://api.listenbrainz.org/1/submit-listens"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun updateNowPlaying(track: ScrobbleTrack, credentials: ScrobbleCredentials): Boolean = withContext(dispatchers.io) {
        if (!credentials.isEnabled || credentials.token.isBlank()) return@withContext false
        submitListen("playing_now", track, null, credentials.token)
    }

    override suspend fun scrobble(track: ScrobbleTrack, credentials: ScrobbleCredentials): Boolean = withContext(dispatchers.io) {
        if (!credentials.isEnabled || credentials.token.isBlank()) return@withContext false
        submitListen("single", track, track.timestampSeconds, credentials.token)
    }

    private fun submitListen(type: String, track: ScrobbleTrack, timestamp: Long?, token: String): Boolean {
        val payload = buildJsonObject {
            put("listen_type", type)
            put("payload", buildJsonArray {
                add(buildJsonObject {
                    if (timestamp != null) {
                        put("listened_at", timestamp)
                    }
                    putJsonObject("track_metadata") {
                        put("artist_name", track.artist)
                        put("track_name", track.title)
                        if (track.album.isNotBlank()) put("release_name", track.album)
                        putJsonObject("additional_info") {
                            put("media_player", "LofiQ")
                            put("submission_client", "LofiQ")
                            if (track.durationSeconds > 0) {
                                put("duration_ms", track.durationSeconds * 1000L)
                            }
                        }
                    }
                })
            })
        }.toString()

        val request = Request.Builder()
            .url(apiUrl)
            .header("Authorization", "Token $token")
            .post(payload.toRequestBody(jsonMediaType))
            .build()

        return try {
            val response = okHttpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "ListenBrainz scrobble error: ${e.message}")
            false
        }
    }
}

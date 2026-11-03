package com.lofiq.data.scrobbling

import com.lofiq.BuildConfig
import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.common.LofiQLogger
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LastFmScrobbler @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val dispatchers: DispatcherProvider
) : Scrobbler {

    override val service: ScrobbleService = ScrobbleService.LAST_FM

    // Standard LofiQ Last.fm client credentials
    private val apiKey = BuildConfig.LASTFM_API_KEY
    private val apiSecret = BuildConfig.LASTFM_API_SECRET
    private val apiUrl = "https://ws.audioscrobbler.com/2.0/"

    override suspend fun updateNowPlaying(track: ScrobbleTrack, credentials: ScrobbleCredentials): Boolean = withContext(dispatchers.io) {
        val effectiveKey = credentials.apiKey.ifBlank { apiKey }
        val effectiveSecret = credentials.apiSecret.ifBlank { apiSecret }
        if (!credentials.isEnabled || credentials.sessionKey.isBlank() || effectiveKey.isBlank() || effectiveSecret.isBlank()) return@withContext false

        val params = mutableMapOf(
            "method" to "track.updateNowPlaying",
            "track" to track.title,
            "artist" to track.artist,
            "api_key" to effectiveKey,
            "sk" to credentials.sessionKey
        )
        if (track.album.isNotBlank()) params["album"] = track.album
        if (track.durationSeconds > 0) params["duration"] = track.durationSeconds.toString()

        params["api_sig"] = generateSignature(params, effectiveSecret)
        params["format"] = "json"

        val formBuilder = FormBody.Builder()
        params.forEach { (k, v) -> formBuilder.add(k, v) }

        val request = Request.Builder()
            .url(apiUrl)
            .post(formBuilder.build())
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "Last.fm updateNowPlaying error: ${e.message}")
            false
        }
    }

    override suspend fun scrobble(track: ScrobbleTrack, credentials: ScrobbleCredentials): Boolean = withContext(dispatchers.io) {
        val effectiveKey = credentials.apiKey.ifBlank { apiKey }
        val effectiveSecret = credentials.apiSecret.ifBlank { apiSecret }
        if (!credentials.isEnabled || credentials.sessionKey.isBlank() || effectiveKey.isBlank() || effectiveSecret.isBlank()) return@withContext false

        val params = mutableMapOf(
            "method" to "track.scrobble",
            "track" to track.title,
            "artist" to track.artist,
            "timestamp" to track.timestampSeconds.toString(),
            "api_key" to effectiveKey,
            "sk" to credentials.sessionKey
        )
        if (track.album.isNotBlank()) params["album"] = track.album
        if (track.durationSeconds > 0) params["duration"] = track.durationSeconds.toString()

        params["api_sig"] = generateSignature(params, effectiveSecret)
        params["format"] = "json"

        val formBuilder = FormBody.Builder()
        params.forEach { (k, v) -> formBuilder.add(k, v) }

        val request = Request.Builder()
            .url(apiUrl)
            .post(formBuilder.build())
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "Last.fm scrobble error: ${e.message}")
            false
        }
    }

    private fun generateSignature(params: Map<String, String>, secret: String): String {
        val sortedString = params.toSortedMap()
            .filter { it.key != "format" }
            .map { "${it.key}${it.value}" }
            .joinToString("") + secret

        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(sortedString.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

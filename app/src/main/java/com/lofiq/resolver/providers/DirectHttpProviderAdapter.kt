package com.lofiq.resolver.providers

import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.common.LofiQLogger
import com.lofiq.core.model.AudioQuality
import com.lofiq.core.model.Song
import com.lofiq.resolver.core.ResolveRequest
import com.lofiq.resolver.core.ResolutionResult
import com.lofiq.resolver.core.ResolverException
import com.lofiq.resolver.core.TrackIdentityMatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DirectHttpProviderAdapter @Inject constructor(
    private val httpClient: OkHttpClient,
    private val json: Json,
    private val dispatchers: DispatcherProvider
) : ProviderAdapter {

    override val providerId: String = "direct_http"
    override val displayName: String = "Direct Stream Provider"
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsSearch = true,
        supportsTrending = true,
        supportedBitrates = listOf(96, 160, 320),
        requiresHeaders = false
    )

    private val desKey = "38346591".toByteArray(StandardCharsets.UTF_8)

    override suspend fun canHandle(request: ResolveRequest): Boolean {
        return request.allowFallback || request.providerHint == providerId ||
                request.songId.startsWith("http://") || request.songId.startsWith("https://")
    }

    override suspend fun resolve(request: ResolveRequest): ResolutionResult = withContext(dispatchers.io) {
        // Case 1: Direct HTTP URL
        if (request.songId.startsWith("http://") || request.songId.startsWith("https://")) {
            return@withContext ResolutionResult(
                songId = request.songId,
                streamUrl = request.songId,
                mimeType = if (request.songId.endsWith(".m4a") || request.songId.endsWith(".mp4")) "audio/mp4" else "audio/mpeg",
                durationMs = request.durationSeconds * 1000L,
                bitrateKbps = request.preferredQuality.bitrateKbps,
                quality = request.preferredQuality,
                providerId = providerId,
                codec = "aac",
                sampleRate = 44100,
                channels = 2,
                expiryEpochMs = 0L,
                isCacheable = true
            )
        }

        // Case 2: Resolve stream via embedded direct audio source
        val query = "${request.songTitle} ${request.artistName}".trim()
        LofiQLogger.i(
            LofiQLogger.Category.RESOLVER,
            "[REQUEST] title='${request.songTitle}' artist='${request.artistName}' trackId='${request.songId}' duration=${request.durationSeconds}s"
        )

        try {
            val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
            val searchUrl = "https://www.jiosaavn.com/api.php?__call=autocomplete.get&query=$encodedQuery&_format=json&_marker=0&ctx=android"
            val searchReq = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val searchResp = httpClient.newCall(searchReq).execute()
            val searchBody = searchResp.use { resp ->
                if (!resp.isSuccessful) {
                    throw ResolverException.SourceUnavailable(request.songId, "Direct provider search HTTP ${resp.code}")
                }
                resp.body?.string() ?: throw ResolverException.SourceUnavailable(request.songId, "Empty search response")
            }

            val searchRoot = json.parseToJsonElement(searchBody).jsonObject
            val songsData = searchRoot["songs"]?.jsonObject?.get("data")?.jsonArray

            if (songsData.isNullOrEmpty()) {
                throw ResolverException.SourceUnavailable(request.songId, "No direct matching stream found for '$query'")
            }

            // Inspect bounded list of candidate search results (up to 5 candidates)
            val candidateIds = songsData.take(5).mapNotNull { item ->
                item.jsonObject["id"]?.jsonPrimitive?.contentOrNull
            }

            if (candidateIds.isEmpty()) {
                throw ResolverException.SourceUnavailable(request.songId, "No candidate IDs in search response")
            }

            // Batch fetch details for candidate IDs to get full metadata & encrypted media URLs
            val pidsParam = candidateIds.joinToString(",")
            val detailsUrl = "https://www.jiosaavn.com/api.php?__call=song.getDetails&pids=$pidsParam&_format=json&_marker=0&ctx=android"
            val detailsReq = Request.Builder()
                .url(detailsUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val detailsResp = httpClient.newCall(detailsReq).execute()
            val detailsBody = detailsResp.use { resp ->
                if (!resp.isSuccessful) {
                    throw ResolverException.SourceUnavailable(request.songId, "Direct provider details HTTP ${resp.code}")
                }
                resp.body?.string() ?: throw ResolverException.SourceUnavailable(request.songId, "Empty details response")
            }

            val detailsRoot = json.parseToJsonElement(detailsBody).jsonObject

            var acceptedCandidate: TrackIdentityMatcher.CandidateTrack? = null
            var bestScore = 0.0

            for (pId in candidateIds) {
                val songObj = detailsRoot[pId]?.jsonObject ?: continue
                val candTitle = songObj["song"]?.jsonPrimitive?.contentOrNull
                    ?: songObj["title"]?.jsonPrimitive?.contentOrNull
                    ?: ""
                val candArtist = songObj["primary_artists"]?.jsonPrimitive?.contentOrNull
                    ?: songObj["singers"]?.jsonPrimitive?.contentOrNull
                    ?: ""
                val candAlbum = songObj["album"]?.jsonPrimitive?.contentOrNull ?: ""
                val candDuration = songObj["duration"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                val encUrl = songObj["encrypted_media_url"]?.jsonPrimitive?.contentOrNull

                if (candTitle.isBlank() || encUrl.isNullOrBlank()) continue

                val candidate = TrackIdentityMatcher.CandidateTrack(
                    providerId = providerId,
                    providerTrackId = pId,
                    title = candTitle,
                    artist = candArtist,
                    album = candAlbum,
                    durationSeconds = candDuration,
                    encryptedMediaUrl = encUrl
                )

                val eval = TrackIdentityMatcher.evaluate(
                    requestedTitle = request.songTitle,
                    requestedArtist = request.artistName,
                    requestedDurationSec = request.durationSeconds,
                    candidate = candidate
                )

                val decision = if (eval.isAccepted) "ACCEPTED" else "REJECTED"
                LofiQLogger.i(
                    LofiQLogger.Category.RESOLVER,
                    "[CANDIDATE] providerId=$providerId pId=$pId title='${candidate.title}' artist='${candidate.artist}' duration=${candidate.durationSeconds}s matchScore=${String.format("%.2f", eval.matchScore)} DECISION=$decision reason=${eval.reason}"
                )

                if (eval.isAccepted && eval.matchScore > bestScore) {
                    bestScore = eval.matchScore
                    acceptedCandidate = candidate
                }
            }

            val chosen = acceptedCandidate ?: throw ResolverException.SourceUnavailable(
                songId = request.songId,
                reason = "No candidate passed track identity verification for '${request.songTitle}' by '${request.artistName}'"
            )

            LofiQLogger.i(
                LofiQLogger.Category.RESOLVER,
                "[ACCEPTED] requested='${request.songTitle}' by '${request.artistName}' -> resolved='${chosen.title}' by '${chosen.artist}' [pId=${chosen.providerTrackId}] provider=$providerId matchScore=${String.format("%.2f", bestScore)}"
            )

            val rawStreamUrl = decryptDesMediaUrl(chosen.encryptedMediaUrl!!)

            // Select quality bitrate with automatic fallback
            val finalStreamUrl = when (request.preferredQuality) {
                AudioQuality.DATA_SAVER -> rawStreamUrl.replace("_320.mp4", "_96.mp4").replace("_160.mp4", "_96.mp4")
                AudioQuality.STANDARD -> rawStreamUrl.replace("_320.mp4", "_160.mp4").replace("_96.mp4", "_160.mp4")
                AudioQuality.HIGH, AudioQuality.AUTO -> rawStreamUrl.replace("_96.mp4", "_320.mp4").replace("_160.mp4", "_320.mp4")
            }

            val bitrateKbps = when (request.preferredQuality) {
                AudioQuality.DATA_SAVER -> 96
                AudioQuality.STANDARD -> 160
                AudioQuality.HIGH, AudioQuality.AUTO -> 320
            }

            val durationSeconds = if (chosen.durationSeconds > 0) chosen.durationSeconds else request.durationSeconds

            ResolutionResult(
                songId = request.songId,
                streamUrl = finalStreamUrl,
                mimeType = "audio/mp4",
                durationMs = durationSeconds * 1000L,
                bitrateKbps = bitrateKbps,
                quality = request.preferredQuality,
                providerId = providerId,
                codec = "aac",
                sampleRate = 44100,
                channels = 2,
                expiryEpochMs = 0L,
                isCacheable = true
            )
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.RESOLVER, "DirectHttpProvider failed for ${request.songId}: ${e.message}")
            throw if (e is ResolverException) e else ResolverException.SourceUnavailable(request.songId, e.message ?: "Unknown error")
        }
    }

    private fun decryptDesMediaUrl(encryptedUrl: String): String {
        val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
        val keySpec = SecretKeySpec(desKey, "DES")
        cipher.init(Cipher.DECRYPT_MODE, keySpec)
        val decodedBytes = java.util.Base64.getDecoder().decode(encryptedUrl)
        val decrypted = cipher.doFinal(decodedBytes)
        return String(decrypted, StandardCharsets.UTF_8)
            .replace("_96.mp4", "_320.mp4")
            .replace("_160.mp4", "_320.mp4")
    }

    override suspend fun search(query: String, page: Int, limit: Int): List<Song> = emptyList()
    override suspend fun getSongDetails(songId: String): Song? = null
    override suspend fun getTrending(page: Int, limit: Int): List<Song> = emptyList()
}



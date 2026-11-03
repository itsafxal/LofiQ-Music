package com.lofiq.data.artist

import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.common.LofiQLogger
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtistImageProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val dispatchers: DispatcherProvider
) {
    private val runtimeCache = ConcurrentHashMap<String, String>()

    // Direct High-Resolution CDN Studio Portraits (500x500 CDN static files)
    private val curatedArtistPortraits = mapOf(
        "arijit singh" to "https://cdn-images.dzcdn.net/images/artist/ac5350cff290edd5b69fa584b8b1bd4f/500x500-000000-80-0-0.jpg",
        "anirudh ravichander" to "https://cdn-images.dzcdn.net/images/artist/9da0a547b39e99bc35c6a9724aef91bf/500x500-000000-80-0-0.jpg",
        "anirudh" to "https://cdn-images.dzcdn.net/images/artist/9da0a547b39e99bc35c6a9724aef91bf/500x500-000000-80-0-0.jpg",
        "a.r. rahman" to "https://cdn-images.dzcdn.net/images/artist/bd34315ef977a62a9e28c1ab19bb8ac4/500x500-000000-80-0-0.jpg",
        "ar rahman" to "https://cdn-images.dzcdn.net/images/artist/bd34315ef977a62a9e28c1ab19bb8ac4/500x500-000000-80-0-0.jpg",
        "shreya ghoshal" to "https://cdn-images.dzcdn.net/images/artist/3bb832d37d10ff2affcfa9afdc7c68a0/500x500-000000-80-0-0.jpg",
        "sid sriram" to "https://cdn-images.dzcdn.net/images/artist/fbe3e1d17fc6958e047f011f74233f82/500x500-000000-80-0-0.jpg",
        "diljit dosanjh" to "https://cdn-images.dzcdn.net/images/artist/79b85e695e0ca6529e56bf3b628e92bd/500x500-000000-80-0-0.jpg",
        "pritam" to "https://cdn-images.dzcdn.net/images/artist/d4914ccd414067cd5e2c108867079a85/500x500-000000-80-0-0.jpg",
        "sushin shyam" to "https://cdn-images.dzcdn.net/images/artist/6ba914ca28d2c5cc21dc3effa07c690d/500x500-000000-80-0-0.jpg",
        "sonu nigam" to "https://cdn-images.dzcdn.net/images/artist/812220125c4f0db57050438b65afcf78/500x500-000000-80-0-0.jpg",
        "k.s. chithra" to "https://cdn-images.dzcdn.net/images/artist/8b1cb6dd371bf47574e8af73276bbced/500x500-000000-80-0-0.jpg",
        "ks chithra" to "https://cdn-images.dzcdn.net/images/artist/8b1cb6dd371bf47574e8af73276bbced/500x500-000000-80-0-0.jpg",
        "k.j. yesudas" to "https://cdn-images.dzcdn.net/images/artist/b42a9228338e4e3792e51bcad5e14cb9/500x500-000000-80-0-0.jpg",
        "kj yesudas" to "https://cdn-images.dzcdn.net/images/artist/b42a9228338e4e3792e51bcad5e14cb9/500x500-000000-80-0-0.jpg",
        "atif aslam" to "https://cdn-images.dzcdn.net/images/artist/0ea90444148fff9c11d77f06a344724e/500x500-000000-80-0-0.jpg",
        "yuvan shankar raja" to "https://cdn-images.dzcdn.net/images/artist/1dbf7d81a2e964d9c707e53478407974/500x500-000000-80-0-0.jpg",
        "yuvan" to "https://cdn-images.dzcdn.net/images/artist/1dbf7d81a2e964d9c707e53478407974/500x500-000000-80-0-0.jpg",
        "taylor swift" to "https://cdn-images.dzcdn.net/images/artist/cc2495870fe1a792ad0cdb05501ad5ec/500x500-000000-80-0-0.jpg",
        "harris jayaraj" to "https://cdn-images.dzcdn.net/images/artist/b10caf45eae518a2b16997874ded7143/500x500-000000-80-0-0.jpg",
        "g.v. prakash kumar" to "https://cdn-images.dzcdn.net/images/artist/3c26ba207ebaeef950daa30f2e0f20e2/500x500-000000-80-0-0.jpg",
        "gv prakash" to "https://cdn-images.dzcdn.net/images/artist/3c26ba207ebaeef950daa30f2e0f20e2/500x500-000000-80-0-0.jpg",
        "santhosh narayanan" to "https://cdn-images.dzcdn.net/images/artist/74004fed94dddf9ae2d7c83084eaf1ad/500x500-000000-80-0-0.jpg",
        "devi sri prasad" to "https://cdn-images.dzcdn.net/images/artist/abd95cd3882021d0f16006cc391f993c/500x500-000000-80-0-0.jpg",
        "dsp" to "https://cdn-images.dzcdn.net/images/artist/abd95cd3882021d0f16006cc391f993c/500x500-000000-80-0-0.jpg",
        "armaan malik" to "https://cdn-images.dzcdn.net/images/artist/3aacd4e00a34aefb6041d30e0cc5bc5e/500x500-000000-80-0-0.jpg",
        "neha kakkar" to "https://cdn-images.dzcdn.net/images/artist/3a0f7ba65d6d8c1081b461ee49cb59e8/500x500-000000-80-0-0.jpg",
        "badshah" to "https://cdn-images.dzcdn.net/images/artist/5b90b89299a7d42f81d79afa263a85d2/500x500-000000-80-0-0.jpg",
        "sunidhi chauhan" to "https://cdn-images.dzcdn.net/images/artist/37dbd33ae8cf7075e247968acf4b48da/500x500-000000-80-0-0.jpg",
        "mohit chauhan" to "https://cdn-images.dzcdn.net/images/artist/f9533880207ac5714b65e760e5686af8/500x500-000000-80-0-0.jpg",
        "alka yagnik" to "https://cdn-images.dzcdn.net/images/artist/ebb52754c04679e33acf4d6056fa211a/500x500-000000-80-0-0.jpg",
        "kumar sanu" to "https://cdn-images.dzcdn.net/images/artist/7ad58f1c03087a082e22a718acc3f1fc/500x500-000000-80-0-0.jpg",
        "udit narayan" to "https://cdn-images.dzcdn.net/images/artist/287f18b3d4798dfff688a9246184b084/500x500-000000-80-0-0.jpg",
        "kk" to "https://cdn-images.dzcdn.net/images/artist/205e23b632a1877c197921edee92bb49/500x500-000000-80-0-0.jpg",
        "kishore kumar" to "https://cdn-images.dzcdn.net/images/artist/5972263348ad902e29a4749e748ff452/500x500-000000-80-0-0.jpg",
        "lata mangeshkar" to "https://cdn-images.dzcdn.net/images/artist/837d46f90f541736e07817f463317c80/500x500-000000-80-0-0.jpg",
        "mohammed rafi" to "https://cdn-images.dzcdn.net/images/artist/9e79b89a9b2073fae0cc2f6bce278abe/500x500-000000-80-0-0.jpg",
        "darshan raval" to "https://cdn-images.dzcdn.net/images/artist/9428424029a929927b7081b70d3a0fc6/500x500-000000-80-0-0.jpg",
        "jubin nautiyal" to "https://cdn-images.dzcdn.net/images/artist/9031b483049b5551abcb11529873b4f6/500x500-000000-80-0-0.jpg",
        "guru randhawa" to "https://cdn-images.dzcdn.net/images/artist/108309345087dbd61b29766185e93b72/500x500-000000-80-0-0.jpg",
        "yo yo honey singh" to "https://cdn-images.dzcdn.net/images/artist/7859b461c10352f02a11368905f0903f/500x500-000000-80-0-0.jpg",
        "honey singh" to "https://cdn-images.dzcdn.net/images/artist/7859b461c10352f02a11368905f0903f/500x500-000000-80-0-0.jpg",
        "karan aujla" to "https://cdn-images.dzcdn.net/images/artist/a91a1d5ea91e85e4f0966569b50e8d6a/500x500-000000-80-0-0.jpg",
        "ap dhillon" to "https://cdn-images.dzcdn.net/images/artist/52594ac9fa763dc163ed13d21cb130ec/500x500-000000-80-0-0.jpg",
        "shankar mahadevan" to "https://cdn-images.dzcdn.net/images/artist/aefd6e3d941a0a1fd8baea6cf4a03c6a/500x500-000000-80-0-0.jpg",
        "hariharan" to "https://cdn-images.dzcdn.net/images/artist/a649d4b377e1a336844cb3788ec4d5c9/500x500-000000-80-0-0.jpg",
        "jonita gandhi" to "https://cdn-images.dzcdn.net/images/artist/f756c825ab0eb367cd85f077c28d0719/500x500-000000-80-0-0.jpg",
        "the weeknd" to "https://cdn-images.dzcdn.net/images/artist/581693b4724a7fcfa754455101e13a44/500x500-000000-80-0-0.jpg",
        "ed sheeran" to "https://cdn-images.dzcdn.net/images/artist/d6bb84390641d8ae9118228d9544e53d/500x500-000000-80-0-0.jpg",
        "dua lipa" to "https://cdn-images.dzcdn.net/images/artist/877872aaf75694f11d53c318700ab2b5/500x500-000000-80-0-0.jpg",
        "billie eilish" to "https://cdn-images.dzcdn.net/images/artist/8eab1a9a644889aabaca1e193e05f984/500x500-000000-80-0-0.jpg",
        "drake" to "https://cdn-images.dzcdn.net/images/artist/70223888f501f4b843142e071abda364/500x500-000000-80-0-0.jpg",
        "justin bieber" to "https://cdn-images.dzcdn.net/images/artist/fe097f693cebf1f882e3da79e99e3bf9/500x500-000000-80-0-0.jpg",
        "post malone" to "https://cdn-images.dzcdn.net/images/artist/a5a8cca44e7eab2db7d44e039bed2574/500x500-000000-80-0-0.jpg",
        "bruno mars" to "https://cdn-images.dzcdn.net/images/artist/90f0b5b11df4f87ee878f38569b5995b/500x500-000000-80-0-0.jpg",
        "ariana grande" to "https://cdn-images.dzcdn.net/images/artist/721d8fab84b315502de422b8d0901509/500x500-000000-80-0-0.jpg",
        "coldplay" to "https://cdn-images.dzcdn.net/images/artist/3087954bca22f306324912e5ac8375c3/500x500-000000-80-0-0.jpg",
        "zayn" to "https://cdn-images.dzcdn.net/images/artist/1789e943d6bc7ee4a6efe32233d1a90b/500x500-000000-80-0-0.jpg"
    )

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Get verified artist portrait image URL.
     * Checks curated dictionary first, then runtime cache, and falls back to dynamic artist search.
     */
    suspend fun getArtistPortraitUrl(artistName: String): String = withContext(dispatchers.io) {
        val cleanName = artistName.trim().lowercase()
        if (cleanName.isBlank() || cleanName == "unknown artist") return@withContext ""

        // 1. Direct curated match
        curatedArtistPortraits[cleanName]?.let { return@withContext it }

        // Exact word match in curated list
        for ((name, url) in curatedArtistPortraits) {
            if (cleanName == name || cleanName.split(",", "&", "/").any { it.trim() == name }) {
                return@withContext url
            }
        }

        // 2. Check in-memory cache
        runtimeCache[cleanName]?.let { return@withContext it }

        // 3. Dynamic lookup via Deezer Artist Search API (Universal High-Definition Portraits)
        try {
            val deezerUrl = "https://api.deezer.com/search/artist".toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("q", artistName.trim())
                ?.addQueryParameter("limit", "1")
                ?.build()

            if (deezerUrl != null) {
                val request = Request.Builder()
                    .url(deezerUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .get()
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (body.isNotBlank()) {
                        val root = json.parseToJsonElement(body).jsonObject
                        val dataArray = root["data"]?.jsonArray
                        val firstObj = dataArray?.firstOrNull()?.jsonObject
                        val picBig = firstObj?.get("picture_big")?.jsonPrimitive?.content
                            ?: firstObj?.get("picture_medium")?.jsonPrimitive?.content
                            ?: firstObj?.get("picture")?.jsonPrimitive?.content

                        if (!picBig.isNullOrBlank() && !picBig.contains("artist-default", ignoreCase = true)) {
                            runtimeCache[cleanName] = picBig
                            return@withContext picBig
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.SEARCH, "Deezer artist image lookup failed for $artistName: ${e.message}")
        }

        // 4. Dynamic lookup via YouTube Music InnerTube Search (Channel & Artist Avatars)
        try {
            val ytUrl = "https://music.youtube.com/youtubei/v1/search?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8&prettyPrint=false"
            val payload = """{"context":{"client":{"clientName":"WEB_REMIX","clientVersion":"1.20251013.03.00","hl":"en","gl":"IN"}},"query":"${artistName.trim().replace("\"", "\\\"")}","params":"Eg-KAQwIABAAGAAgACgAMABqChAGGAMQCBAEEAU%3D"}"""
            val req = Request.Builder()
                .url(ytUrl)
                .post(payload.toRequestBody(jsonMediaType))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://music.youtube.com/")
                .header("Origin", "https://music.youtube.com")
                .build()

            val response = okHttpClient.newCall(req).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val match = Regex("""https://(?:lh3\.googleusercontent\.com|yt3\.ggpht\.com)/[a-zA-Z0-9_\-]+""").find(body)
                if (match != null) {
                    val avatarUrl = "${match.value}=s500-c-k-c0x00ffffff-no-rj"
                    runtimeCache[cleanName] = avatarUrl
                    return@withContext avatarUrl
                }
            }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.SEARCH, "YouTube Music artist avatar lookup failed for $artistName: ${e.message}")
        }

        // 5. Dynamic lookup via JioSaavn Autocomplete
        try {
            val saavnUrl = "https://www.jiosaavn.com/api.php".toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("__call", "autocomplete.get")
                ?.addQueryParameter("query", artistName.trim())
                ?.addQueryParameter("_format", "json")
                ?.addQueryParameter("_marker", "0")
                ?.addQueryParameter("ctx", "android")
                ?.build()

            if (saavnUrl != null) {
                val request = Request.Builder()
                    .url(saavnUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .get()
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (body.isNotBlank()) {
                        val root = json.parseToJsonElement(body).jsonObject
                        val artistsObj = root["artists"]?.jsonObject
                        val data = artistsObj?.get("data")?.jsonArray
                        val first = data?.firstOrNull()?.jsonObject
                        val rawImage = first?.get("image")?.jsonPrimitive?.content
                        if (!rawImage.isNullOrBlank() && !rawImage.contains("default", ignoreCase = true)) {
                            val highRes = rawImage.replace("150x150", "500x500").replace("50x50", "500x500")
                            runtimeCache[cleanName] = highRes
                            return@withContext highRes
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.SEARCH, "JioSaavn artist image lookup failed for $artistName: ${e.message}")
        }

        ""
    }
}


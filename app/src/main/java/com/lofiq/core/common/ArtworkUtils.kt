package com.lofiq.core.common

import com.lofiq.core.model.Song

object ArtworkUtils {

    /**
     * Enhances any artwork URL to guaranteed HTTPS and maximum available resolution.
     * - JioSaavn: forces https:// and upgrades 50x50 / 150x150 to 500x500
     * - YouTube Music (googleusercontent / ggpht): upgrades to =w800-h800-l90-rj
     * - YouTube Video (i.ytimg.com): preserves maxresdefault / hq720 / sddefault, or resolves from videoId
     * - Spotify (i.scdn.co): ensures https://
     */
    fun getHighResArtworkUrl(url: String, fallbackVideoId: String? = null): String {
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            return if (!fallbackVideoId.isNullOrBlank() && fallbackVideoId.length == 11) {
                "https://i.ytimg.com/vi/$fallbackVideoId/sddefault.jpg"
            } else {
                ""
            }
        }

        // 1. Protocol normalization
        val httpsUrl = when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("http://") -> "https://" + trimmed.removePrefix("http://")
            else -> trimmed
        }

        // 2. JioSaavn CDN (c.saavncdn.com)
        if (httpsUrl.contains("saavncdn.com")) {
            return httpsUrl
                .replace(Regex("""[-_]50x50\."""), "-500x500.")
                .replace(Regex("""[-_]150x150\."""), "-500x500.")
                .replace("50x50", "500x500")
                .replace("150x150", "500x500")
        }

        // 3. YouTube Music (Google User Content / GGPHT)
        if (httpsUrl.contains("googleusercontent.com") || httpsUrl.contains("ggpht.com")) {
            return httpsUrl
                .replace(Regex("""=w\d+-h\d+.*"""), "=w800-h800-l90-rj")
                .replace(Regex("""=s\d+.*"""), "=s800")
        }

        // 4. YouTube Video Thumbnails (i.ytimg.com)
        if (httpsUrl.contains("i.ytimg.com")) {
            val videoId = fallbackVideoId?.takeIf { it.length == 11 }
                ?: if (httpsUrl.contains("/vi/")) {
                    httpsUrl.substringAfter("/vi/").substringBefore("/").substringBefore("?")
                } else null

            return when {
                httpsUrl.contains("maxresdefault.jpg") || httpsUrl.contains("hq720.jpg") -> httpsUrl
                httpsUrl.contains("sddefault.jpg") -> httpsUrl
                videoId != null -> "https://i.ytimg.com/vi/$videoId/sddefault.jpg"
                else -> httpsUrl
            }
        }

        return httpsUrl
    }

    /**
     * Resolves the optimal high-resolution artwork URL for a given Song.
     * Prefers highResArtworkUrl over artworkUrl, and applies high-res transformations.
     */
    fun getOptimalArtwork(song: Song): String {
        val candidate = song.highResArtworkUrl.ifBlank { song.artworkUrl }
        return getHighResArtworkUrl(candidate, song.id)
    }

    /**
     * Returns a reliable secondary fallback URL (e.g. for YouTube or GoogleUserContent)
     * if the primary high-res URL fails to load.
     */
    fun getFallbackArtwork(song: Song): String? {
        val optimal = getOptimalArtwork(song)
        return when {
            optimal.contains("=w800") -> optimal.replace("=w800-h800-l90-rj", "=w544-h544-l90-rj")
            optimal.contains("sddefault.jpg") || optimal.contains("maxresdefault.jpg") || optimal.contains("hq720.jpg") -> {
                val vid = if (song.id.length == 11) song.id else optimal.substringAfter("/vi/").substringBefore("/")
                "https://i.ytimg.com/vi/$vid/hqdefault.jpg"
            }
            optimal.contains("500x500") -> optimal.replace("500x500", "150x150")
            song.id.length == 11 -> "https://i.ytimg.com/vi/${song.id}/hqdefault.jpg"
            else -> null
        }
    }
}

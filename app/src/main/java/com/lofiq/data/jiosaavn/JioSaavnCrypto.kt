package com.lofiq.data.jiosaavn

import android.util.Base64
import com.lofiq.core.model.AudioQuality
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object JioSaavnCrypto {

    private val DES_KEYS = listOf("38346591", "38343638")

    /**
     * Decrypts JioSaavn's encrypted_media_url to a direct CDN stream URL.
     */
    fun decryptMediaUrl(encryptedUrl: String, quality: AudioQuality = AudioQuality.HIGH): String? {
        if (encryptedUrl.isBlank()) return null
        val trimmed = encryptedUrl.trim()

        for (key in DES_KEYS) {
            try {
                val keyBytes = key.toByteArray(StandardCharsets.UTF_8)
                val keySpec = SecretKeySpec(keyBytes, "DES")
                val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, keySpec)

                val decodedBytes = Base64.decode(trimmed, Base64.DEFAULT)
                val decryptedBytes = cipher.doFinal(decodedBytes)
                val rawUrl = String(decryptedBytes, StandardCharsets.UTF_8).trim()

                if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
                    val httpsUrl = if (rawUrl.startsWith("http://")) rawUrl.replaceFirst("http://", "https://") else rawUrl
                    return adjustBitrateQuality(httpsUrl, quality)
                }
            } catch (e: Exception) {
                // Try next key
            }
        }
        return null
    }

    /**
     * Replaces bitrate suffix (_96.mp4 / _160.mp4 / _320.mp4 / _320.m4a) according to AudioQuality.
     */
    fun adjustBitrateQuality(url: String, quality: AudioQuality): String {
        val targetSuffix = when (quality) {
            AudioQuality.HIGH, AudioQuality.AUTO -> "_320.mp4"
            AudioQuality.STANDARD -> "_160.mp4"
            AudioQuality.DATA_SAVER -> "_96.mp4"
        }

        val httpsUrl = if (url.startsWith("http://")) url.replaceFirst("http://", "https://") else url

        return httpsUrl
            .replace("_96.mp4", targetSuffix)
            .replace("_160.mp4", targetSuffix)
            .replace("_320.mp4", targetSuffix)
            .replace("_96.m4a", targetSuffix)
            .replace("_160.m4a", targetSuffix)
            .replace("_320.m4a", targetSuffix)
            .replace("_96_p.mp4", targetSuffix)
    }

    /**
     * Derives a direct 320kbps CDN stream from media_preview_url.
     */
    fun deriveStreamFromPreviewUrl(previewUrl: String, quality: AudioQuality = AudioQuality.HIGH): String? {
        if (previewUrl.isBlank()) return null
        val targetSuffix = when (quality) {
            AudioQuality.HIGH, AudioQuality.AUTO -> "_320.mp4"
            AudioQuality.STANDARD -> "_160.mp4"
            AudioQuality.DATA_SAVER -> "_96.mp4"
        }
        val securePreview = if (previewUrl.startsWith("http://")) previewUrl.replaceFirst("http://", "https://") else previewUrl
        return securePreview
            .replace("preview.saavncdn.com", "aac.saavncdn.com")
            .replace("_96_p.mp4", targetSuffix)
            .replace("_96.mp4", targetSuffix)
    }
}

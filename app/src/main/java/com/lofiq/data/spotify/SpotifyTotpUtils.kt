package com.lofiq.data.spotify

import com.lofiq.core.common.LofiQLogger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * TOTP Generator matching Spotify Web Player algorithm and rotating secrets.
 */
object SpotifyTotpUtils {
    private val TOTP_SECRET_CIPHER = intArrayOf(
        12, 56, 76, 33, 88, 44, 88, 33, 78, 78, 11, 66, 22, 22, 55, 69, 54
    )

    const val TOTP_VERSION = 5

    fun fetchServerTimeSeconds(okHttpClient: OkHttpClient, json: Json): Long {
        try {
            val request = Request.Builder()
                .url("https://open.spotify.com/server-time")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .addHeader("Referer", "https://open.spotify.com/")
                .build()
            val response = okHttpClient.newCall(request).execute()
            val serverTime = try {
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    if (body.isNotBlank()) {
                        val jsonObj = json.parseToJsonElement(body).jsonObject
                        jsonObj["serverTime"]?.jsonPrimitive?.longOrNull
                    } else null
                } else null
            } finally {
                response.close()
            }
            if (serverTime != null && serverTime > 0) return serverTime
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.NETWORK, "Failed to fetch Spotify server time: ${e.message}")
        }
        return System.currentTimeMillis() / 1000
    }

    fun generateTotp(timeSeconds: Long): String {
        return try {
            val transformed = TOTP_SECRET_CIPHER.mapIndexed { i, n -> n xor ((i % 33) + 9) }
            val key = transformed.joinToString("").toByteArray(Charsets.UTF_8)
            val counter = timeSeconds / 30
            val msg = ByteBuffer.allocate(8).putLong(counter).array()

            val mac = Mac.getInstance("HmacSHA1")
            mac.init(SecretKeySpec(key, "HmacSHA1"))
            val hash = mac.doFinal(msg)

            val offset = (hash[hash.size - 1].toInt() and 0x0f)
            val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
                ((hash[offset + 1].toInt() and 0xff) shl 16) or
                ((hash[offset + 2].toInt() and 0xff) shl 8) or
                (hash[offset + 3].toInt() and 0xff)

            val otp = binary % 1_000_000
            otp.toString().padStart(6, '0')
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.NETWORK, "TOTP generation failed: ${e.message}")
            "000000"
        }
    }
}

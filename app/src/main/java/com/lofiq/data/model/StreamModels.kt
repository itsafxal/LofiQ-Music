package com.lofiq.data.model

import com.lofiq.core.model.AudioQuality
import kotlinx.serialization.Serializable

@Serializable
enum class AudioCodec {
    AAC,
    OPUS,
    MP3,
    FLAC,
    UNKNOWN
}

@Serializable
data class AudioFormatInfo(
    val itag: Int,
    val mimeType: String,
    val codec: AudioCodec,
    val bitrateKbps: Int,
    val sampleRateHz: Int = 44100,
    val channelCount: Int = 2,
    val contentLengthBytes: Long = 0L,
    val quality: AudioQuality = AudioQuality.HIGH,
    val streamUrl: String
)

@Serializable
data class StreamInfo(
    val songId: String,
    val streamUrl: String,
    val format: AudioFormatInfo? = null,
    val expiresAtTimestampMs: Long = 0L,
    val sourceProvider: String = "youtube_piped",
    val headers: Map<String, String> = emptyMap()
)

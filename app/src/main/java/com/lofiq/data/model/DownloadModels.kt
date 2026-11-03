package com.lofiq.data.model

import com.lofiq.core.model.AudioQuality
import com.lofiq.core.model.Song
import kotlinx.serialization.Serializable

@Serializable
enum class DownloadStatus {
    NOT_DOWNLOADED,
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

@Serializable
data class DownloadTask(
    val songId: String,
    val song: Song,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val filePath: String = "",
    val quality: AudioQuality = AudioQuality.HIGH,
    val error: String? = null,
    val downloadedAt: Long = System.currentTimeMillis()
)

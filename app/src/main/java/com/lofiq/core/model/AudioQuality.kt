package com.lofiq.core.model

enum class AudioQuality(
    val label: String,
    val subtitle: String,
    val bitrateKbps: Int
) {
    AUTO("Auto (Adaptive)", "Adapts to network speed (Wi-Fi / 4G / 2G)", 0),
    HIGH("High Quality", "320 kbps MP3 / 160 kbps Opus Hi-Fi", 320),
    STANDARD("Standard", "160 kbps MP3 / 128 kbps AAC", 160),
    DATA_SAVER("Data Saver", "96 kbps MP3 / 48-50 kbps Opus (Low data)", 96);

    companion object {
        val DEFAULT = AUTO
        fun fromString(value: String?): AudioQuality {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: DEFAULT
        }
    }
}

enum class PlaybackRepeatMode {
    OFF,
    ALL,
    ONE
}

package com.lofiq.data.lyrics.model

enum class LyricsSyncType {
    LINE_SYNCED,
    PLAIN,
    NOT_FOUND
}

data class LyricsLine(
    val timeMs: Long,
    val text: String,
    val translation: String? = null
)

data class Lyrics(
    val songId: String,
    val lines: List<LyricsLine>,
    val syncType: LyricsSyncType,
    val source: String,
    val plainText: String? = null,
    val translation: String? = null
)

data class LyricsResult(
    val lrcText: String? = null,
    val plainText: String? = null,
    val source: String,
    val isSynced: Boolean = !lrcText.isNullOrBlank()
)

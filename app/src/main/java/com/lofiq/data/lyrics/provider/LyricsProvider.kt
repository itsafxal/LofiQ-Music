package com.lofiq.data.lyrics.provider

import com.lofiq.data.lyrics.model.LyricsResult

interface LyricsProvider {
    val name: String
    suspend fun getLyrics(
        title: String,
        artist: String,
        album: String? = null,
        durationSeconds: Long? = null
    ): LyricsResult?
}

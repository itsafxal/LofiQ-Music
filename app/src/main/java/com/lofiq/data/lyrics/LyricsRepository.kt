package com.lofiq.data.lyrics

import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.common.LofiQLogger
import com.lofiq.core.model.Song
import com.lofiq.data.lyrics.model.Lyrics
import com.lofiq.data.lyrics.model.LyricsSyncType
import com.lofiq.data.lyrics.parser.LrcParser
import com.lofiq.data.lyrics.provider.KugouLyricsProvider
import com.lofiq.data.lyrics.provider.LrcLibLyricsProvider
import com.lofiq.data.lyrics.provider.LyricsProvider
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsRepository @Inject constructor(
    private val lrcLibProvider: LrcLibLyricsProvider,
    private val kugouProvider: KugouLyricsProvider,
    private val dispatchers: DispatcherProvider
) {
    private val memoryCache = ConcurrentHashMap<String, Lyrics>()
    private val providers: List<LyricsProvider> = listOf(lrcLibProvider, kugouProvider)

    suspend fun getLyrics(song: Song): Lyrics = withContext(dispatchers.io) {
        // 1. Check in-memory cache
        memoryCache[song.id]?.let { return@withContext it }

        // 2. Check if song already has embedded/saved lyrics
        if (!song.lyrics.isNullOrBlank()) {
            val parsedLines = LrcParser.parse(song.lyrics)
            if (parsedLines.isNotEmpty()) {
                val lyrics = Lyrics(
                    songId = song.id,
                    lines = parsedLines,
                    syncType = LyricsSyncType.LINE_SYNCED,
                    source = "Embedded",
                    plainText = song.lyrics
                )
                memoryCache[song.id] = lyrics
                return@withContext lyrics
            } else {
                val plainLines = LrcParser.parsePlainText(song.lyrics)
                val lyrics = Lyrics(
                    songId = song.id,
                    lines = plainLines,
                    syncType = LyricsSyncType.PLAIN,
                    source = "Embedded",
                    plainText = song.lyrics
                )
                memoryCache[song.id] = lyrics
                return@withContext lyrics
            }
        }

        // 3. Query providers sequentially (Waterfall: LRCLIB -> Kugou)
        for (provider in providers) {
            try {
                LofiQLogger.d(LofiQLogger.Category.PLAYBACK, "[LYRICS] Fetching lyrics for '${song.title}' from ${provider.name}")
                val result = provider.getLyrics(
                    title = song.title,
                    artist = song.artist,
                    album = song.album.takeIf { it.isNotBlank() },
                    durationSeconds = song.durationSeconds.takeIf { it > 0 }
                )

                if (result != null) {
                    if (!result.lrcText.isNullOrBlank()) {
                        val lines = LrcParser.parse(result.lrcText)
                        if (lines.isNotEmpty()) {
                            val lyrics = Lyrics(
                                songId = song.id,
                                lines = lines,
                                syncType = LyricsSyncType.LINE_SYNCED,
                                source = provider.name,
                                plainText = result.plainText ?: result.lrcText
                            )
                            memoryCache[song.id] = lyrics
                            LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[LYRICS] Loaded ${lines.size} synced lines from ${provider.name}")
                            return@withContext lyrics
                        }
                    }

                    if (!result.plainText.isNullOrBlank()) {
                        val lines = LrcParser.parsePlainText(result.plainText)
                        val lyrics = Lyrics(
                            songId = song.id,
                            lines = lines,
                            syncType = LyricsSyncType.PLAIN,
                            source = provider.name,
                            plainText = result.plainText
                        )
                        memoryCache[song.id] = lyrics
                        LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[LYRICS] Loaded plain lyrics from ${provider.name}")
                        return@withContext lyrics
                    }
                }
            } catch (e: Exception) {
                LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[LYRICS] Provider ${provider.name} failed: ${e.message}")
            }
        }

        // 4. Return NOT_FOUND if all failed
        val emptyLyrics = Lyrics(
            songId = song.id,
            lines = emptyList(),
            syncType = LyricsSyncType.NOT_FOUND,
            source = "None"
        )
        memoryCache[song.id] = emptyLyrics
        emptyLyrics
    }

    fun clearCache() {
        memoryCache.clear()
    }
}

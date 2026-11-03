package com.lofiq.data.lyrics.parser

import com.lofiq.data.lyrics.model.LyricsLine
import java.util.regex.Pattern

object LrcParser {

    private val TIME_TAG_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})(?:\\.(\\d{1,3}))?]")
    private val OFFSET_PATTERN = Pattern.compile("\\[offset:\\s*([+-]?\\d+)]", Pattern.CASE_INSENSITIVE)
    private val WORD_TAG_PATTERN = Pattern.compile("<\\d{1,2}:\\d{1,2}(?:\\.\\d{1,3})?>")

    /**
     * Parses an LRC formatted string into a sorted list of [LyricsLine].
     * Returns empty list if no valid timestamped lines are found.
     */
    fun parse(lrcContent: String): List<LyricsLine> {
        if (lrcContent.isBlank()) return emptyList()

        val lines = lrcContent.lines()
        var offsetMs = 0L

        // Check for global offset tag
        for (line in lines) {
            val offsetMatcher = OFFSET_PATTERN.matcher(line.trim())
            if (offsetMatcher.find()) {
                offsetMs = offsetMatcher.group(1)?.toLongOrNull() ?: 0L
            }
        }

        val parsedLines = mutableListOf<LyricsLine>()

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("[ti:") || line.startsWith("[ar:") ||
                line.startsWith("[al:") || line.startsWith("[by:") || line.startsWith("[re:") ||
                line.startsWith("[ve:") || line.startsWith("[offset:")
            ) {
                continue
            }

            val matcher = TIME_TAG_PATTERN.matcher(line)
            val timestamps = mutableListOf<Long>()
            var lastMatchEnd = 0

            while (matcher.find()) {
                val minutes = matcher.group(1)?.toLongOrNull() ?: 0L
                val seconds = matcher.group(2)?.toLongOrNull() ?: 0L
                val fracStr = matcher.group(3)

                val millis = when {
                    fracStr == null -> 0L
                    fracStr.length == 1 -> fracStr.toLong() * 100L
                    fracStr.length == 2 -> fracStr.toLong() * 10L
                    fracStr.length >= 3 -> fracStr.take(3).toLong()
                    else -> 0L
                }

                val totalMs = (minutes * 60_000L) + (seconds * 1_000L) + millis + offsetMs
                timestamps.add(totalMs.coerceAtLeast(0L))
                lastMatchEnd = matcher.end()
            }

            if (timestamps.isNotEmpty()) {
                val rawLyricText = line.substring(lastMatchEnd).trim()
                // Strip word-level timing tags if present (<00:12.34>)
                val cleanedText = WORD_TAG_PATTERN.matcher(rawLyricText).replaceAll("").trim()

                for (ts in timestamps) {
                    parsedLines.add(LyricsLine(timeMs = ts, text = cleanedText))
                }
            }
        }

        // Sort by time and filter empty consecutive entries
        return parsedLines.sortedBy { it.timeMs }
    }

    /**
     * Parses plain text into non-synced [LyricsLine] list.
     */
    fun parsePlainText(plainText: String): List<LyricsLine> {
        if (plainText.isBlank()) return emptyList()
        return plainText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapIndexed { index, text -> LyricsLine(timeMs = index.toLong(), text = text) }
    }
}

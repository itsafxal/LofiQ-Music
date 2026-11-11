import 'lyrics_models.dart';

class LrcParser {
  static final RegExp _timeTag = RegExp(r'\[(\d{1,2}):(\d{1,2})(?:\.(\d{1,3}))?\]');
  static final RegExp _offsetTag = RegExp(r'\[offset:\s*([+-]?\d+)\]', caseSensitive: false);

  static List<LyricsLine> parse(String lrcContent) {
    if (lrcContent.trim().isEmpty) return [];

    final lines = lrcContent.split('\n');
    int offsetMs = 0;

    for (final line in lines) {
      final match = _offsetTag.firstMatch(line.trim());
      if (match != null) {
        offsetMs = int.tryParse(match.group(1) ?? '0') ?? 0;
      }
    }

    final parsedLines = <LyricsLine>[];

    for (final rawLine in lines) {
      final line = rawLine.trim();
      if (line.isEmpty ||
          line.startsWith('[ti:') ||
          line.startsWith('[ar:') ||
          line.startsWith('[al:') ||
          line.startsWith('[by:') ||
          line.startsWith('[offset:')) {
        continue;
      }

      final matches = _timeTag.allMatches(line);
      if (matches.isEmpty) continue;

      final text = line.replaceAll(_timeTag, '').trim();
      if (text.isEmpty) continue;

      for (final match in matches) {
        final minutes = int.tryParse(match.group(1) ?? '0') ?? 0;
        final seconds = int.tryParse(match.group(2) ?? '0') ?? 0;
        final fracStr = match.group(3);

        int millis = 0;
        if (fracStr != null) {
          if (fracStr.length == 1) {
            millis = (int.tryParse(fracStr) ?? 0) * 100;
          } else if (fracStr.length == 2) {
            millis = (int.tryParse(fracStr) ?? 0) * 10;
          } else {
            millis = int.tryParse(fracStr.substring(0, 3)) ?? 0;
          }
        }

        final totalMs = (minutes * 60000) + (seconds * 1000) + millis + offsetMs;
        parsedLines.add(LyricsLine(timeMs: totalMs < 0 ? 0 : totalMs, text: text));
      }
    }

    parsedLines.sort((a, b) => a.timeMs.compareTo(b.timeMs));
    return parsedLines;
  }
}


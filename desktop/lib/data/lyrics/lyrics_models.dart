enum LyricsSyncType {
  lineSynced,
  plain,
  notFound;
}

class LyricsLine {
  final int timeMs;
  final String text;
  final String? translation;

  const LyricsLine({
    required this.timeMs,
    required this.text,
    this.translation,
  });

  int get timestampMs => timeMs;
}

class Lyrics {
  final String songId;
  final List<LyricsLine> lines;
  final LyricsSyncType syncType;
  final String source;
  final String? plainText;

  const Lyrics({
    required this.songId,
    required this.lines,
    required this.syncType,
    required this.source,
    this.plainText,
  });

  bool get isSynced => syncType == LyricsSyncType.lineSynced && lines.isNotEmpty;
}

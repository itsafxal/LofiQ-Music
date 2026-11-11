import 'dart:convert';
import 'package:http/http.dart' as http;
import '../../core/models/song.dart';
import '../../core/utils/logger.dart';
import 'lrc_parser.dart';
import 'lyrics_models.dart';

class LyricsRepository {
  static final LyricsRepository instance = LyricsRepository._();
  factory LyricsRepository() => instance;
  LyricsRepository._();

  final Map<String, Lyrics> _cache = {};

  void clearCache() => _cache.clear();

  Future<Lyrics?> fetchLyrics({
    required String title,
    required String artist,
    required int durationMs,
    bool forceRefresh = false,
  }) async {
    final song = Song(
      id: 'lyr_${title}_$artist',
      title: title,
      artist: artist,
      album: '',
      durationSeconds: durationMs ~/ 1000,
      artworkUrl: '',
      highResArtworkUrl: '',
      sourceUrl: '',
      providerId: 'local',
    );
    if (forceRefresh) {
      _cache.remove(song.id);
    }
    return getLyrics(song);
  }

  Future<Lyrics> getLyrics(Song song) async {
    final cached = _cache[song.id];
    if (cached != null) return cached;

    // 1. Try LRCLIB
    try {
      final lrclibLyrics = await _fetchFromLrcLib(song);
      if (lrclibLyrics != null) {
        _cache[song.id] = lrclibLyrics;
        return lrclibLyrics;
      }
    } catch (e) {
      LofiQLogger.d(LogCategory.lyrics, 'LRCLIB failed for ${song.title}: $e');
    }

    // 2. Try Kugou
    try {
      final kugouLyrics = await _fetchFromKugou(song);
      if (kugouLyrics != null) {
        _cache[song.id] = kugouLyrics;
        return kugouLyrics;
      }
    } catch (e) {
      LofiQLogger.d(LogCategory.lyrics, 'Kugou failed for ${song.title}: $e');
    }

    final notFound = Lyrics(
      songId: song.id,
      lines: const [],
      syncType: LyricsSyncType.notFound,
      source: 'none',
      plainText: 'No synchronized lyrics available for this track.',
    );
    _cache[song.id] = notFound;
    return notFound;
  }

  Future<Lyrics?> _fetchFromLrcLib(Song song) async {
    final uri = Uri.parse(
      'https://lrclib.net/api/get?track_name=${Uri.encodeComponent(song.title)}&artist_name=${Uri.encodeComponent(song.artist)}&duration=${song.durationSeconds}',
    );
    final res = await http.get(
      uri,
      headers: {'User-Agent': 'LofiQ Music Player V1.0.2 Windows'},
    ).timeout(const Duration(seconds: 4));

    if (res.statusCode != 200) return null;
    final data = jsonDecode(res.body) as Map<String, dynamic>;

    final syncedLyrics = data['syncedLyrics'] as String?;
    final plainLyrics = data['plainLyrics'] as String?;

    if (syncedLyrics != null && syncedLyrics.isNotEmpty) {
      final lines = LrcParser.parse(syncedLyrics);
      if (lines.isNotEmpty) {
        return Lyrics(
          songId: song.id,
          lines: lines,
          syncType: LyricsSyncType.lineSynced,
          source: 'LRCLIB',
          plainText: plainLyrics,
        );
      }
    }

    if (plainLyrics != null && plainLyrics.isNotEmpty) {
      return Lyrics(
        songId: song.id,
        lines: const [],
        syncType: LyricsSyncType.plain,
        source: 'LRCLIB',
        plainText: plainLyrics,
      );
    }
    return null;
  }

  Future<Lyrics?> _fetchFromKugou(Song song) async {
    final searchUri = Uri.parse(
      'http://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword=${Uri.encodeComponent('${song.title} ${song.artist}')}&duration=${song.durationMs}&hash=',
    );
    final res = await http.get(searchUri).timeout(const Duration(seconds: 4));
    if (res.statusCode != 200) return null;

    final data = jsonDecode(res.body) as Map<String, dynamic>;
    final candidates = data['candidates'] as List<dynamic>?;
    if (candidates == null || candidates.isEmpty) return null;

    final best = candidates.first as Map<String, dynamic>;
    final id = best['id'];
    final accessKey = best['accesskey'];

    if (id == null || accessKey == null) return null;

    final downloadUri = Uri.parse(
      'http://lyrics.kugou.com/download?ver=1&client=pc&id=$id&accesskey=$accessKey&fmt=lrc&charset=utf8',
    );
    final dlRes = await http.get(downloadUri).timeout(const Duration(seconds: 4));
    if (dlRes.statusCode != 200) return null;

    final dlData = jsonDecode(dlRes.body) as Map<String, dynamic>;
    final contentB64 = dlData['content'] as String?;
    if (contentB64 == null) return null;

    final rawLrc = utf8.decode(base64Decode(contentB64));
    final lines = LrcParser.parse(rawLrc);

    if (lines.isNotEmpty) {
      return Lyrics(
        songId: song.id,
        lines: lines,
        syncType: LyricsSyncType.lineSynced,
        source: 'Kugou',
      );
    }
    return null;
  }
}

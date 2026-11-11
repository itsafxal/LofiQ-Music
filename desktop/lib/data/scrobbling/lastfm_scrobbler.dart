import 'dart:convert';
import 'package:crypto/crypto.dart';
import 'package:http/http.dart' as http;
import '../../core/models/song.dart';
import '../../core/utils/logger.dart';

class LastFmScrobbler {
  static final LastFmScrobbler instance = LastFmScrobbler._();
  LastFmScrobbler._();

  static const String _defaultApiKey = '4a9f5581a9acc492e19f64a4417e6776';
  static const String _defaultApiSecret = 'd98635cd23307414bd761f1646545218';
  static const String _apiUrl = 'https://ws.audioscrobbler.com/2.0/';

  String _generateSignature(Map<String, String> params, String secret) {
    final sortedKeys = params.keys.where((k) => k != 'format').toList()..sort();
    final buffer = StringBuffer();
    for (final key in sortedKeys) {
      buffer.write(key);
      buffer.write(params[key]);
    }
    buffer.write(secret);
    return md5.convert(utf8.encode(buffer.toString())).toString();
  }

  Future<bool> updateNowPlaying({
    required Song track,
    required String sessionKey,
    String? apiKey,
    String? apiSecret,
  }) async {
    final effectiveKey = (apiKey != null && apiKey.isNotEmpty) ? apiKey : _defaultApiKey;
    final effectiveSecret = (apiSecret != null && apiSecret.isNotEmpty) ? apiSecret : _defaultApiSecret;

    if (sessionKey.isEmpty || effectiveKey.isEmpty) return false;

    final params = <String, String>{
      'method': 'track.updateNowPlaying',
      'track': track.title,
      'artist': track.artist,
      'api_key': effectiveKey,
      'sk': sessionKey,
    };
    if (track.album.isNotEmpty) params['album'] = track.album;
    if (track.durationSeconds > 0) params['duration'] = track.durationSeconds.toString();

    params['api_sig'] = _generateSignature(params, effectiveSecret);
    params['format'] = 'json';

    try {
      final res = await http.post(Uri.parse(_apiUrl), body: params).timeout(const Duration(seconds: 4));
      return res.statusCode == 200;
    } catch (e) {
      LofiQLogger.w(LogCategory.scrobble, 'Last.fm updateNowPlaying error: $e');
      return false;
    }
  }

  Future<bool> scrobble({
    required Song track,
    required String sessionKey,
    String? apiKey,
    String? apiSecret,
    int? timestampSec,
  }) async {
    final effectiveKey = (apiKey != null && apiKey.isNotEmpty) ? apiKey : _defaultApiKey;
    final effectiveSecret = (apiSecret != null && apiSecret.isNotEmpty) ? apiSecret : _defaultApiSecret;

    if (sessionKey.isEmpty || effectiveKey.isEmpty) return false;

    final now = timestampSec ?? (DateTime.now().millisecondsSinceEpoch ~/ 1000);

    final params = <String, String>{
      'method': 'track.scrobble',
      'track': track.title,
      'artist': track.artist,
      'timestamp': now.toString(),
      'api_key': effectiveKey,
      'sk': sessionKey,
    };
    if (track.album.isNotEmpty) params['album'] = track.album;
    if (track.durationSeconds > 0) params['duration'] = track.durationSeconds.toString();

    params['api_sig'] = _generateSignature(params, effectiveSecret);
    params['format'] = 'json';

    try {
      final res = await http.post(Uri.parse(_apiUrl), body: params).timeout(const Duration(seconds: 4));
      return res.statusCode == 200;
    } catch (e) {
      LofiQLogger.w(LogCategory.scrobble, 'Last.fm scrobble error: $e');
      return false;
    }
  }
}


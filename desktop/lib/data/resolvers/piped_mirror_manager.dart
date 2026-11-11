import 'dart:convert';
import 'package:http/http.dart' as http;
import '../../core/utils/logger.dart';

class PipedMirrorManager {
  static final PipedMirrorManager instance = PipedMirrorManager._();
  PipedMirrorManager._();

  static const List<String> defaultMirrors = [
    'https://api.piped.private.coffee',
    'https://pipedapi.tokhmi.xyz',
    'https://piped-api.lunar.icu',
    'https://api-piped.mha.fi',
    'https://pipedapi.kavin.rocks',
  ];

  final Map<String, int> _failureTimestamps = {};

  Future<String?> extractAudioStream(String videoId) async {
    final now = DateTime.now().millisecondsSinceEpoch;

    for (final mirror in defaultMirrors) {
      final lastFail = _failureTimestamps[mirror] ?? 0;
      if (now - lastFail < 3 * 60 * 1000) {
        continue; // Cooldown for 3 minutes
      }

      try {
        final uri = Uri.parse('$mirror/streams/$videoId');
        final res = await http.get(uri).timeout(const Duration(seconds: 4));
        if (res.statusCode != 200) {
          _failureTimestamps[mirror] = now;
          continue;
        }

        final data = jsonDecode(res.body) as Map<String, dynamic>;
        final audioStreams = data['audioStreams'] as List<dynamic>?;
        if (audioStreams == null || audioStreams.isEmpty) {
          _failureTimestamps[mirror] = now;
          continue;
        }

        // Sort by highest bitrate
        audioStreams.sort((a, b) {
          final bitA = (a['bitrate'] as num?)?.toInt() ?? 0;
          final bitB = (b['bitrate'] as num?)?.toInt() ?? 0;
          return bitB.compareTo(bitA);
        });

        final bestStream = audioStreams.first as Map<String, dynamic>;
        final streamUrl = bestStream['url'] as String?;
        if (streamUrl != null && streamUrl.isNotEmpty) {
          LofiQLogger.i(LogCategory.resolver, 'Resolved stream via Piped mirror $mirror');
          return streamUrl;
        }
      } catch (e) {
        _failureTimestamps[mirror] = now;
      }
    }
    return null;
  }
}


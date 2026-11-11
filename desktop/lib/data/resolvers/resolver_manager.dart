import '../../core/models/audio_quality.dart';
import '../../core/models/song.dart';
import '../../core/utils/logger.dart';
import 'jiosaavn_client.dart';
import 'youtube_stream_extractor.dart';

class ResolverManager {
  static final ResolverManager instance = ResolverManager._();
  factory ResolverManager() => instance;
  ResolverManager._();

  final Map<String, String> _urlCache = {};

  Future<String?> resolveStreamUrl(Song song, {AudioQuality quality = AudioQuality.high}) async {
    // 1. Check in-memory URL cache
    final cached = _urlCache[song.id];
    if (cached != null && cached.isNotEmpty) {
      return cached;
    }

    // 2. If already a direct playable HTTP / file URL
    if (song.sourceUrl.isNotEmpty && (song.sourceUrl.startsWith('http') || song.sourceUrl.startsWith('file://') || song.sourceUrl.contains(':\\'))) {
      _urlCache[song.id] = song.sourceUrl;
      return song.sourceUrl;
    }

    // 3. Resolve based on provider ID
    if (song.providerId == 'jiosaavn') {
      // Direct JioSaavn stream
      if (song.sourceUrl.isNotEmpty) {
        final decrypted = JioSaavnClient.instance.decryptMediaUrl(song.sourceUrl, quality: quality);
        if (decrypted != null) {
          _urlCache[song.id] = decrypted;
          return decrypted;
        }
      }
    }

    final rawId = song.id.replaceAll(RegExp(r'^(yt_|js_)'), '');

    // 4. Resolve via YouTube stream extractor
    final ytUrl = await YouTubeStreamExtractor.instance.resolveAudioStream(rawId, quality: quality);
    if (ytUrl != null && ytUrl.isNotEmpty) {
      _urlCache[song.id] = ytUrl;
      return ytUrl;
    }

    // 5. Fallback: Search JioSaavn by track title & artist
    try {
      final jsResults = await JioSaavnClient.instance.search('${song.title} ${song.artist}');
      if (jsResults.isNotEmpty) {
        final first = jsResults.first;
        if (first.sourceUrl.isNotEmpty) {
          _urlCache[song.id] = first.sourceUrl;
          return first.sourceUrl;
        }
      }
    } catch (_) {}

    LofiQLogger.w(LogCategory.resolver, 'Could not resolve audio stream for track: ${song.title}');
    return null;
  }

  void clearCache() {
    _urlCache.clear();
  }
}

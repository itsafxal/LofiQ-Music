import 'dart:convert';
import 'dart:typed_data';
import 'package:http/http.dart' as http;
import 'package:pointycastle/api.dart';
import 'package:pointycastle/block/desede_engine.dart';
import 'package:pointycastle/block/modes/ecb.dart';
import 'package:pointycastle/padded_block_cipher/padded_block_cipher_impl.dart';
import 'package:pointycastle/paddings/pkcs7.dart';
import '../../core/models/audio_quality.dart';
import '../../core/models/song.dart';
import '../../core/utils/logger.dart';

class JioSaavnClient {
  static final JioSaavnClient instance = JioSaavnClient._();
  JioSaavnClient._();

  static const List<String> _desKeys = ['38346591', '38343638'];

  /// Decrypts JioSaavn's encrypted_media_url to a direct CDN stream URL.
  String? decryptMediaUrl(String encryptedUrl, {AudioQuality quality = AudioQuality.high}) {
    if (encryptedUrl.trim().isEmpty) return null;
    final trimmed = encryptedUrl.trim();

    // If it's already a preview URL, derive directly
    if (trimmed.contains('saavncdn.com')) {
      return deriveStreamFromPreviewUrl(trimmed, quality: quality);
    }

    Uint8List encryptedBytes;
    try {
      encryptedBytes = base64Decode(trimmed);
    } catch (_) {
      return null;
    }

    for (final keyStr in _desKeys) {
      try {
        final tripleKey = '$keyStr$keyStr$keyStr';
        final keyBytes = Uint8List.fromList(utf8.encode(tripleKey));
        final cipher = PaddedBlockCipherImpl(
          PKCS7Padding(),
          ECBBlockCipher(DESedeEngine()),
        );
        final params = PaddedBlockCipherParameters(
          KeyParameter(keyBytes),
          null,
        );
        cipher.init(false, params); // false = decrypt

        final decrypted = cipher.process(encryptedBytes);
        final rawUrl = utf8.decode(decrypted).trim();

        if (rawUrl.startsWith('http://') || rawUrl.startsWith('https://')) {
          final httpsUrl = rawUrl.startsWith('http://')
              ? rawUrl.replaceFirst('http://', 'https://')
              : rawUrl;
          return _adjustBitrate(httpsUrl, quality);
        }
      } catch (_) {
        // Try next key
      }
    }
    return null;
  }

  /// Derives a direct 320kbps CDN stream from media_preview_url.
  String? deriveStreamFromPreviewUrl(String previewUrl, {AudioQuality quality = AudioQuality.high}) {
    if (previewUrl.trim().isEmpty) return null;
    final targetSuffix = switch (quality) {
      AudioQuality.high || AudioQuality.auto => '_320.mp4',
      AudioQuality.standard => '_160.mp4',
      AudioQuality.dataSaver => '_96.mp4',
    };
    final securePreview = previewUrl.startsWith('http://')
        ? previewUrl.replaceFirst('http://', 'https://')
        : previewUrl;
    return securePreview
        .replaceFirst('preview.saavncdn.com', 'aac.saavncdn.com')
        .replaceFirst('_96_p.mp4', targetSuffix)
        .replaceFirst('_96.mp4', targetSuffix);
  }

  String adjustBitrate(String url, AudioQuality quality) => _adjustBitrate(url, quality);

  String _adjustBitrate(String url, AudioQuality quality) {
    final targetSuffix = switch (quality) {
      AudioQuality.high || AudioQuality.auto => '_320.mp4',
      AudioQuality.standard => '_160.mp4',
      AudioQuality.dataSaver => '_96.mp4',
    };

    return url
        .replaceFirst('_96.mp4', targetSuffix)
        .replaceFirst('_160.mp4', targetSuffix)
        .replaceFirst('_320.mp4', targetSuffix)
        .replaceFirst('_96.m4a', targetSuffix)
        .replaceFirst('_160.m4a', targetSuffix)
        .replaceFirst('_320.m4a', targetSuffix)
        .replaceFirst('_96_p.mp4', targetSuffix);
  }

  /// Searches JioSaavn for tracks matching query using authoritative search.getResults API
  Future<List<Song>> search(String query, {int limit = 20}) async {
    try {
      final uri = Uri.parse(
        'https://www.jiosaavn.com/api.php?__call=search.getResults&q=${Uri.encodeComponent(query)}&_format=json&_marker=0&api_version=4&ctx=android&n=$limit',
      );
      final res = await http.get(
        uri,
        headers: {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'},
      ).timeout(const Duration(seconds: 8));

      if (res.statusCode != 200) return [];

      final root = jsonDecode(res.body) as Map<String, dynamic>;
      final results = root['results'] as List<dynamic>? ?? [];

      final songs = <Song>[];
      for (final item in results) {
        if (item is! Map<String, dynamic>) continue;

        final songId = item['id']?.toString() ?? '';
        final title = _cleanHtml(item['song']?.toString() ?? item['title']?.toString() ?? '');
        final moreInfo = item['more_info'] as Map<String, dynamic>?;

        final primaryArtists = item['primary_artists']?.toString();
        final singers = moreInfo?['singers']?.toString();
        final music = moreInfo?['music']?.toString();
        final subtitle = item['subtitle']?.toString();

        final artistCandidates = [primaryArtists, singers, music, subtitle].whereType<String>();
        final artist = artistCandidates.isNotEmpty
            ? _cleanHtml(artistCandidates.first)
            : 'Unknown Artist';

        final album = _cleanHtml(item['album']?.toString() ?? moreInfo?['album']?.toString() ?? '');
        final durationStr = item['duration']?.toString() ?? moreInfo?['duration']?.toString() ?? '0';
        final durationSec = int.tryParse(durationStr) ?? 0;

        final rawImage = item['image']?.toString() ?? '';
        final highResImage = rawImage
            .replaceAll('150x150', '500x500')
            .replaceAll('http://', 'https://');

        final encryptedUrl = moreInfo?['encrypted_media_url']?.toString() ?? '';
        final mediaPreviewUrl = moreInfo?['media_preview_url']?.toString() ??
            item['media_preview_url']?.toString() ?? '';

        // Store encrypted URL if present, or preview URL as fallback token
        final sourceToken = encryptedUrl.isNotEmpty ? encryptedUrl : mediaPreviewUrl;

        // Immediately attempt stream pre-resolution
        String resolvedStream = '';
        if (encryptedUrl.isNotEmpty) {
          resolvedStream = decryptMediaUrl(encryptedUrl) ?? '';
        }
        if (resolvedStream.isEmpty && mediaPreviewUrl.isNotEmpty) {
          resolvedStream = deriveStreamFromPreviewUrl(mediaPreviewUrl) ?? '';
        }

        if (songId.isNotEmpty && title.isNotEmpty) {
          songs.add(Song(
            id: 'js_$songId',
            title: title,
            artist: artist,
            album: album,
            durationSeconds: durationSec > 0 ? durationSec : 210,
            artworkUrl: highResImage.isNotEmpty ? highResImage : rawImage,
            highResArtworkUrl: highResImage.isNotEmpty ? highResImage : rawImage,
            sourceUrl: resolvedStream.isNotEmpty ? resolvedStream : sourceToken,
            providerId: 'jiosaavn',
            quality: AudioQuality.high,
          ));
        }
      }
      return songs;
    } catch (e) {
      LofiQLogger.w(LogCategory.resolver, 'JioSaavn search failed: $e');
      return [];
    }
  }

  static String _cleanHtml(String str) {
    return str
        .replaceAll('&quot;', '"')
        .replaceAll('&amp;', '&')
        .replaceAll('&#039;', "'")
        .replaceAll('&lt;', '<')
        .replaceAll('&gt;', '>');
  }
}

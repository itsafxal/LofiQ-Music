import 'dart:convert';
import 'package:http/http.dart' as http;
import '../../core/models/audio_quality.dart';
import '../../core/models/song.dart';
import '../../core/utils/logger.dart';
import 'piped_mirror_manager.dart';
import 'youtube_cipher_decipherer.dart';

class ExtractedAudioStream {
  final String url;
  final String mimeType;
  final int bitrate;
  final int durationMs;
  final String codec;

  const ExtractedAudioStream({
    required this.url,
    required this.mimeType,
    required this.bitrate,
    required this.durationMs,
    required this.codec,
  });
}

class YouTubeStreamExtractor {
  static final YouTubeStreamExtractor instance = YouTubeStreamExtractor._();
  YouTubeStreamExtractor._();

  static const String _innertubeKey = 'AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8';

  Future<String?> resolveAudioStream(String videoId, {AudioQuality quality = AudioQuality.high}) async {
    // 1. Try Android TestSuite InnerTube client (fastest, unthrottled, no bot-check)
    try {
      final stream = await _extractViaAndroidTestSuite(videoId, quality);
      if (stream != null) return stream;
    } catch (e) {
      LofiQLogger.d(LogCategory.resolver, 'Android TestSuite failed for $videoId: $e');
    }

    // 2. Try TV Simply Embedded Player
    try {
      final stream = await _extractViaTvSimplyEmbedded(videoId, quality);
      if (stream != null) return stream;
    } catch (e) {
      LofiQLogger.d(LogCategory.resolver, 'TV Simply Embedded failed for $videoId: $e');
    }

    // 2. Try Android VR InnerTube client
    try {
      final stream = await _extractViaAndroidVr(videoId, quality);
      if (stream != null) return stream;
    } catch (e) {
      LofiQLogger.d(LogCategory.resolver, 'Android VR failed for $videoId: $e');
    }

    // 3. Try iOS InnerTube client
    try {
      final stream = await _extractViaIos(videoId, quality);
      if (stream != null) return stream;
    } catch (e) {
      LofiQLogger.d(LogCategory.resolver, 'iOS InnerTube failed for $videoId: $e');
    }

    // 4. Try Piped and Invidious Mirror Proxy Fallback
    try {
      final stream = await PipedMirrorManager.instance.extractAudioStream(videoId);
      if (stream != null) return stream;
    } catch (e) {
      LofiQLogger.d(LogCategory.resolver, 'Piped mirror failed for $videoId: $e');
    }

    return null;
  }

  Future<String?> _extractViaAndroidTestSuite(String videoId, AudioQuality quality) async {
    final uri = Uri.parse('https://www.youtube.com/youtubei/v1/player?key=$_innertubeKey&prettyPrint=false');
    final payload = {
      'context': {
        'client': {
          'clientName': 'ANDROID_TESTSUITE',
          'clientVersion': '1.9',
          'androidSdkVersion': 30,
          'osName': 'Android',
          'osVersion': '11',
          'hl': 'en',
          'gl': 'US',
        },
      },
      'videoId': videoId,
      'contentCheckOk': true,
      'racyCheckOk': true,
      'playbackContext': {
        'contentPlaybackContext': {
          'html5Preference': 'HTML5_PREF_WANTS',
        },
      },
    };

    final res = await http.post(
      uri,
      headers: {
        'Content-Type': 'application/json; charset=utf-8',
        'User-Agent': 'Google-YouTube-Internal-Testing-Suite',
        'X-YouTube-Client-Name': '3',
        'X-YouTube-Client-Version': '1.9',
      },
      body: jsonEncode(payload),
    ).timeout(const Duration(seconds: 5));

    if (res.statusCode != 200) return null;
    return _parsePlayerResponse(res.body, quality);
  }

  Future<String?> _extractViaTvSimplyEmbedded(String videoId, AudioQuality quality) async {
    final uri = Uri.parse('https://www.youtube.com/youtubei/v1/player?key=$_innertubeKey&prettyPrint=false');
    final payload = {
      'context': {
        'client': {
          'clientName': 'TVHTML5_SIMPLY_EMBEDDED_PLAYER',
          'clientVersion': '2.0',
          'platform': 'TV',
          'hl': 'en',
          'gl': 'US',
        },
        'thirdParty': {
          'embedUrl': 'https://www.youtube.com/embed/$videoId',
        },
      },
      'videoId': videoId,
      'contentCheckOk': true,
      'racyCheckOk': true,
      'playbackContext': {
        'contentPlaybackContext': {
          'html5Preference': 'HTML5_PREF_WANTS',
        },
      },
    };

    final res = await http.post(
      uri,
      headers: {
        'Content-Type': 'application/json; charset=utf-8',
        'User-Agent': 'Mozilla/5.0 (SMART-TV; Linux; Tizen 5.0) AppleWebKit/538.1 (KHTML, like Gecko) Version/5.0 TV Safari/538.1',
        'X-YouTube-Client-Name': '85',
        'X-YouTube-Client-Version': '2.0',
      },
      body: jsonEncode(payload),
    ).timeout(const Duration(seconds: 4));

    if (res.statusCode != 200) return null;
    return _parsePlayerResponse(res.body, quality);
  }

  Future<String?> _extractViaAndroidVr(String videoId, AudioQuality quality) async {
    final uri = Uri.parse('https://www.youtube.com/youtubei/v1/player?key=$_innertubeKey&prettyPrint=false');
    final payload = {
      'context': {
        'client': {
          'clientName': 'ANDROID_VR',
          'clientVersion': '1.56.21',
          'deviceMake': 'Oculus',
          'deviceModel': 'Quest 3',
          'osName': 'Android',
          'osVersion': '12',
          'androidSdkVersion': 32,
          'hl': 'en',
          'gl': 'US',
        },
      },
      'videoId': videoId,
      'contentCheckOk': true,
      'racyCheckOk': true,
    };

    final res = await http.post(
      uri,
      headers: {
        'Content-Type': 'application/json; charset=utf-8',
        'User-Agent': 'com.google.android.apps.youtube.vr/1.56.21 (Linux; U; Android 12; Quest 3)',
        'X-YouTube-Client-Name': '64',
        'X-YouTube-Client-Version': '1.56.21',
      },
      body: jsonEncode(payload),
    ).timeout(const Duration(seconds: 4));

    if (res.statusCode != 200) return null;
    return _parsePlayerResponse(res.body, quality);
  }

  Future<String?> _extractViaIos(String videoId, AudioQuality quality) async {
    final uri = Uri.parse('https://www.youtube.com/youtubei/v1/player?key=$_innertubeKey&prettyPrint=false');
    final payload = {
      'context': {
        'client': {
          'clientName': 'IOS',
          'clientVersion': '19.45.4',
          'deviceMake': 'Apple',
          'deviceModel': 'iPhone16,2',
          'osName': 'iOS',
          'osVersion': '18.1.0',
          'hl': 'en',
          'gl': 'US',
        },
      },
      'videoId': videoId,
      'contentCheckOk': true,
      'racyCheckOk': true,
    };

    final res = await http.post(
      uri,
      headers: {
        'Content-Type': 'application/json; charset=utf-8',
        'User-Agent': 'com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X; en_US)',
        'X-YouTube-Client-Name': '5',
        'X-YouTube-Client-Version': '19.45.4',
      },
      body: jsonEncode(payload),
    ).timeout(const Duration(seconds: 4));

    if (res.statusCode != 200) return null;
    return _parsePlayerResponse(res.body, quality);
  }

  Future<String?> _parsePlayerResponse(String body, AudioQuality quality) async {
    try {
      final root = jsonDecode(body) as Map<String, dynamic>;
      final streamingData = root['streamingData'] as Map<String, dynamic>?;
      if (streamingData == null) return null;

      final adaptiveFormats = streamingData['adaptiveFormats'] as List<dynamic>? ?? [];
      final audioStreams = <ExtractedAudioStream>[];

      for (final format in adaptiveFormats) {
        final obj = format as Map<String, dynamic>;
        final mimeType = obj['mimeType']?.toString() ?? '';
        if (!mimeType.startsWith('audio/')) continue;

        String? streamUrl = obj['url']?.toString();
        final signatureCipher = obj['signatureCipher']?.toString() ?? obj['cipher']?.toString();

        if (streamUrl == null && signatureCipher != null) {
          streamUrl = await YouTubeCipherDecipherer.instance.decipher(signatureCipher);
        }

        if (streamUrl != null && streamUrl.isNotEmpty) {
          final bitrate = (obj['bitrate'] as num?)?.toInt() ?? 128000;
          final durationMs = int.tryParse(obj['approxDurationMs']?.toString() ?? '0') ?? 0;
          final codec = mimeType.contains('webm') ? 'opus' : 'aac';

          audioStreams.add(ExtractedAudioStream(
            url: streamUrl,
            mimeType: mimeType.split(';')[0].trim(),
            bitrate: bitrate,
            durationMs: durationMs,
            codec: codec,
          ));
        }
      }

      if (audioStreams.isEmpty) return null;

      // Sort by highest bitrate
      audioStreams.sort((a, b) => b.bitrate.compareTo(a.bitrate));

      final selected = switch (quality) {
        AudioQuality.dataSaver => audioStreams.last,
        AudioQuality.standard => audioStreams.firstWhere(
            (s) => s.bitrate >= 90000 && s.bitrate <= 140000,
            orElse: () => audioStreams[audioStreams.length ~/ 2],
          ),
        AudioQuality.high || AudioQuality.auto => audioStreams.first,
      };

      return selected.url;
    } catch (e) {
      LofiQLogger.w(LogCategory.resolver, 'Failed to parse player response: $e');
      return null;
    }
  }

  /// Searches YouTube Music for tracks
  Future<List<Song>> search(String query) async {
    try {
      final uri = Uri.parse('https://music.youtube.com/youtubei/v1/search?key=$_innertubeKey');
      final payload = {
        'context': {
          'client': {
            'clientName': 'WEB_REMIX',
            'clientVersion': '1.20241028.01.00',
            'hl': 'en',
            'gl': 'US',
          },
        },
        'query': query,
        'params': 'EgWKAQIIAWoQEAMQBBAJEAoQBRAREBAQFQ%3D%3D', // Filter for Songs
      };

      final res = await http.post(
        uri,
        headers: {
          'Content-Type': 'application/json',
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',
          'X-YouTube-Client-Name': '67',
          'X-YouTube-Client-Version': '1.20241028.01.00',
        },
        body: jsonEncode(payload),
      ).timeout(const Duration(seconds: 5));

      if (res.statusCode != 200) return [];
      return _parseSearchResponse(res.body);
    } catch (e) {
      LofiQLogger.w(LogCategory.resolver, 'YouTube search failed: $e');
      return [];
    }
  }

  List<Song> _parseSearchResponse(String body) {
    final songs = <Song>[];
    try {
      final root = jsonDecode(body) as Map<String, dynamic>;
      // Deep traverse search response for musicResponsiveListItemRenderer
      final str = jsonEncode(root);
      final idMatches = RegExp(r'"videoId":"([a-zA-Z0-9_\-]{11})"').allMatches(str);
      final titleMatches = RegExp(r'"title":\{"runs":\[\{"text":"([^"]+)"\}').allMatches(str);

      final ids = idMatches.map((m) => m.group(1)!).toSet().toList();
      final titles = titleMatches.map((m) => m.group(1)!).toList();

      for (int i = 0; i < ids.length && i < titles.length; i++) {
        final videoId = ids[i];
        final title = titles[i];
        final artworkUrl = 'https://i.ytimg.com/vi/$videoId/hqdefault.jpg';

        songs.add(Song(
          id: 'yt_$videoId',
          title: title,
          artist: 'YouTube Artist',
          album: 'LofiQ Stream',
          durationSeconds: 200,
          artworkUrl: artworkUrl,
          highResArtworkUrl: 'https://i.ytimg.com/vi/$videoId/maxresdefault.jpg',
          sourceUrl: '',
          providerId: 'youtube',
          quality: AudioQuality.high,
        ));
      }
    } catch (_) {}
    return songs;
  }
}

class Range {
  final int min;
  final int max;
  const Range(this.min, this.max);
  bool contains(int val) => val >= min && val <= max;
}

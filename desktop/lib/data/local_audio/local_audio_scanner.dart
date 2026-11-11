import 'dart:io';
import 'package:path/path.dart' as p;
import '../../core/models/audio_quality.dart';
import '../../core/models/song.dart';
import '../../core/utils/logger.dart';

class LocalAudioScanner {
  static final LocalAudioScanner instance = LocalAudioScanner._();
  factory LocalAudioScanner() => instance;
  LocalAudioScanner._();

  static const Set<String> _supportedExtensions = {
    '.mp3',
    '.m4a',
    '.flac',
    '.wav',
    '.ogg',
    '.aac',
    '.opus',
    '.wma'
  };

  static const List<String> _excludedKeywords = [
    'call recording',
    'call_rec',
    'voice recorder',
    'whatsapp',
    'telegram',
    'ringtone',
    'notification',
    'alarm'
  ];

  Future<List<Song>> scanWindowsMusicFolder([String? customPath]) => scanWindowsMusicDirectory(customPath);

  Future<List<Song>> scanWindowsMusicDirectory([String? customPath]) async {
    final songs = <Song>[];
    String musicPath = customPath ?? '';

    if (musicPath.isEmpty) {
      final userProfile = Platform.environment['USERPROFILE'] ?? '';
      if (userProfile.isNotEmpty) {
        musicPath = p.join(userProfile, 'Music');
      }
    }

    if (musicPath.isEmpty) return songs;
    final dir = Directory(musicPath);
    if (!dir.existsSync()) return songs;

    try {
      final entities = dir.listSync(recursive: true, followLinks: false);
      for (final entity in entities) {
        if (entity is! File) continue;

        final ext = p.extension(entity.path).toLowerCase();
        if (!_supportedExtensions.contains(ext)) continue;

        final pathLower = entity.path.toLowerCase();
        if (_excludedKeywords.any((kw) => pathLower.contains(kw))) continue;

        try {
          final stat = entity.statSync();
          if (stat.size < 500 * 1024) continue; // Skip audio < 500 KB (ringtones/clicks)

          final filename = p.basenameWithoutExtension(entity.path);
          // Split artist - title if dash exists
          String artist = 'Local Audio';
          String title = filename;
          if (filename.contains(' - ')) {
            final parts = filename.split(' - ');
            artist = parts[0].trim();
            title = parts.sublist(1).join(' - ').trim();
          }

          songs.add(Song(
            id: 'local_${entity.path.hashCode}',
            title: title,
            artist: artist,
            album: 'Device Storage',
            durationSeconds: 210, // Approximation until loaded
            artworkUrl: '',
            highResArtworkUrl: '',
            sourceUrl: entity.path,
            providerId: 'local',
            quality: AudioQuality.high,
          ));
        } catch (_) {}
      }
      LofiQLogger.i(LogCategory.app, 'Scanned ${songs.length} local audio files in $musicPath');
    } catch (e) {
      LofiQLogger.w(LogCategory.app, 'Error scanning local music directory: $e');
    }

    return songs;
  }
}

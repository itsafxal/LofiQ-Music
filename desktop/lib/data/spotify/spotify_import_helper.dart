import 'dart:convert';
import 'package:http/http.dart' as http;
import '../../core/database/local_storage.dart';
import '../../core/models/playlist.dart';
import '../../core/models/song.dart';
import '../../core/utils/logger.dart';
import '../resolvers/jiosaavn_client.dart';
import '../resolvers/youtube_stream_extractor.dart';

class SpotifyTrackItem {
  final String title;
  final String artist;
  final String album;
  final int durationMs;
  final String artworkUrl;

  const SpotifyTrackItem({
    required this.title,
    required this.artist,
    this.album = '',
    this.durationMs = 0,
    this.artworkUrl = '',
  });
}

class SpotifyPlaylistResult {
  final String playlistName;
  final int totalTracks;
  final List<Song> matchedSongs;

  const SpotifyPlaylistResult({
    required this.playlistName,
    required this.totalTracks,
    required this.matchedSongs,
  });
}

class SpotifyImportHelper {
  static final SpotifyImportHelper instance = SpotifyImportHelper._();
  factory SpotifyImportHelper() => instance;

  SpotifyImportHelper._();

  String? extractPlaylistId(String urlOrId) {
    if (!urlOrId.contains('/') && !urlOrId.contains(':')) {
      return urlOrId.trim();
    }
    final match = RegExp(r'playlist[/:]([a-zA-Z0-9]{22})').firstMatch(urlOrId);
    return match?.group(1);
  }

  Future<List<SpotifyTrackItem>> fetchPlaylistTracks(String playlistId) async {
    try {
      final embedUrl = 'https://open.spotify.com/embed/playlist/$playlistId';
      final res = await http.get(
        Uri.parse(embedUrl),
        headers: {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'},
      ).timeout(const Duration(seconds: 6));

      if (res.statusCode != 200) return [];

      final html = res.body;
      final scriptMatch = RegExp(r'<script id="__NEXT_DATA__" type="application/json">([\s\S]+?)</script>').firstMatch(html);
      if (scriptMatch == null) return [];

      final jsonStr = scriptMatch.group(1)!;
      final root = jsonDecode(jsonStr) as Map<String, dynamic>;
      final entity = root['props']?['pageProps']?['state']?['data']?['entity'];
      if (entity == null) return [];

      final trackList = entity['trackList'] as List<dynamic>? ?? [];
      final tracks = <SpotifyTrackItem>[];

      for (final item in trackList) {
        final title = item['title']?.toString() ?? '';
        final subtitle = item['subtitle']?.toString() ?? '';
        final durationMs = (item['duration'] as num?)?.toInt() ?? 0;

        if (title.isNotEmpty) {
          tracks.add(SpotifyTrackItem(
            title: title,
            artist: subtitle,
            durationMs: durationMs,
          ));
        }
      }
      return tracks;
    } catch (e) {
      LofiQLogger.w(LogCategory.app, 'Spotify import fetch error: $e');
      return [];
    }
  }

  Future<SpotifyPlaylistResult> importSpotifyPlaylist({
    required String playlistUrl,
    void Function(int current, int total, String songTitle)? onProgress,
  }) async {
    final playlistId = extractPlaylistId(playlistUrl);
    if (playlistId == null) {
      return const SpotifyPlaylistResult(playlistName: 'Invalid URL', totalTracks: 0, matchedSongs: []);
    }

    final items = await fetchPlaylistTracks(playlistId);
    if (items.isEmpty) {
      return const SpotifyPlaylistResult(playlistName: 'Unknown Playlist', totalTracks: 0, matchedSongs: []);
    }

    final matched = <Song>[];
    for (int i = 0; i < items.length; i++) {
      final track = items[i];
      onProgress?.call(i + 1, items.length, track.title);

      Song? matchedSong;
      try {
        final jsResults = await JioSaavnClient.instance.search('${track.title} ${track.artist}');
        if (jsResults.isNotEmpty) {
          matchedSong = jsResults.first;
        }
      } catch (_) {}

      if (matchedSong == null) {
        try {
          final ytResults = await YouTubeStreamExtractor.instance.search('${track.title} ${track.artist}');
          if (ytResults.isNotEmpty) {
            matchedSong = ytResults.first;
          }
        } catch (_) {}
      }

      if (matchedSong != null) {
        await LocalStorage.instance.upsertSong(matchedSong);
        matched.add(matchedSong);
      }
    }

    return SpotifyPlaylistResult(
      playlistName: 'Spotify Playlist ($playlistId)',
      totalTracks: items.length,
      matchedSongs: matched,
    );
  }

  Future<Playlist?> importPlaylist({
    required String urlOrId,
    required String playlistName,
    void Function(int current, int total, String songTitle)? onProgress,
  }) async {
    final result = await importSpotifyPlaylist(playlistUrl: urlOrId, onProgress: onProgress);
    if (result.matchedSongs.isEmpty) return null;

    final newPlaylist = Playlist(
      id: 'sp_import_${DateTime.now().millisecondsSinceEpoch}',
      name: playlistName.isNotEmpty ? playlistName : result.playlistName,
      description: 'Imported from Spotify on Windows Desktop',
      songIds: result.matchedSongs.map((s) => s.id).toList(),
      createdAt: DateTime.now().millisecondsSinceEpoch,
    );

    await LocalStorage.instance.savePlaylist(newPlaylist);
    return newPlaylist;
  }
}

import 'dart:convert';
import 'dart:io';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import '../models/song.dart';
import '../models/playlist.dart';
import '../utils/logger.dart';

class LocalStorage {
  static LocalStorage? _instance;
  static LocalStorage get instance => _instance ??= LocalStorage._();

  factory LocalStorage() => instance;

  LocalStorage._();

  File? _dbFile;
  Map<String, dynamic> _data = {
    'songs': <String, dynamic>{},
    'playlists': <String, dynamic>{},
    'favorites': <String, dynamic>{},
    'history': <dynamic>[],
    'queueSnapshot': <String, dynamic>{},
  };

  bool _initialized = false;

  Future<void> init() async {
    if (_initialized) return;
    try {
      final appSupportDir = await getApplicationSupportDirectory();
      final lofiqDir = Directory(p.join(appSupportDir.path, 'LofiQ'));
      if (!lofiqDir.existsSync()) {
        lofiqDir.createSync(recursive: true);
      }
      _dbFile = File(p.join(lofiqDir.path, 'lofiq_desktop_store.json'));

      if (_dbFile!.existsSync()) {
        final content = await _dbFile!.readAsString();
        if (content.trim().isNotEmpty) {
          _data = jsonDecode(content) as Map<String, dynamic>;
        }
      } else {
        await _flush();
      }
      _initialized = true;
      LofiQLogger.i(LogCategory.database, 'Initialized LocalStorage at ${_dbFile!.path}');
    } catch (e, stack) {
      LofiQLogger.e(LogCategory.database, 'Failed to initialize LocalStorage', e, stack);
    }
  }

  Future<void> _flush() async {
    if (_dbFile == null) return;
    try {
      await _dbFile!.writeAsString(jsonEncode(_data));
    } catch (e) {
      LofiQLogger.w(LogCategory.database, 'Failed to flush database to disk: $e');
    }
  }

  // --- SONGS ---
  Future<void> upsertSong(Song song) async {
    final songs = _data['songs'] as Map<String, dynamic>? ?? {};
    songs[song.id] = song.toJson();
    _data['songs'] = songs;
    await _flush();
  }

  Song? getSong(String id) {
    final songs = _data['songs'] as Map<String, dynamic>? ?? {};
    final json = songs[id] as Map<String, dynamic>?;
    if (json == null) return null;
    return Song.fromJson(json);
  }

  List<Song> getAllSongs() {
    final songs = _data['songs'] as Map<String, dynamic>? ?? {};
    return songs.values.map((e) => Song.fromJson(e as Map<String, dynamic>)).toList();
  }

  // --- FAVORITES ---
  Future<void> setFavorite(String songId, bool isFav) async {
    final favs = _data['favorites'] as Map<String, dynamic>? ?? {};
    if (isFav) {
      favs[songId] = DateTime.now().millisecondsSinceEpoch;
    } else {
      favs.remove(songId);
    }
    _data['favorites'] = favs;
    await _flush();
  }

  bool isFavorite(String songId) {
    final favs = _data['favorites'] as Map<String, dynamic>? ?? {};
    return favs.containsKey(songId);
  }

  List<String> getFavoriteIds() {
    final favs = _data['favorites'] as Map<String, dynamic>? ?? {};
    return favs.keys.toList();
  }

  List<Song> getFavoriteSongs() {
    final ids = getFavoriteIds();
    final songs = <Song>[];
    for (final id in ids) {
      final s = getSong(id);
      if (s != null) songs.add(s);
    }
    return songs;
  }

  // --- PLAYLISTS ---
  Future<Playlist> createPlaylist(String name, {String description = ''}) async {
    final id = 'pl_${DateTime.now().millisecondsSinceEpoch}';
    final playlist = Playlist(
      id: id,
      name: name,
      description: description,
      createdAt: DateTime.now().millisecondsSinceEpoch,
    );
    await savePlaylist(playlist);
    return playlist;
  }

  Future<void> addTracksToPlaylist(String playlistId, List<String> songIds) async {
    final playlists = _data['playlists'] as Map<String, dynamic>? ?? {};
    final json = playlists[playlistId] as Map<String, dynamic>?;
    if (json == null) return;
    final pl = Playlist.fromJson(json);
    final updated = pl.copyWith(songIds: [...pl.songIds, ...songIds]);
    await savePlaylist(updated);
  }

  Future<void> savePlaylist(Playlist playlist) async {
    final playlists = _data['playlists'] as Map<String, dynamic>? ?? {};
    playlists[playlist.id] = playlist.toJson();
    _data['playlists'] = playlists;
    await _flush();
  }

  Future<void> deletePlaylist(String id) async {
    final playlists = _data['playlists'] as Map<String, dynamic>? ?? {};
    playlists.remove(id);
    _data['playlists'] = playlists;
    await _flush();
  }

  List<Playlist> getAllPlaylists() {
    final playlists = _data['playlists'] as Map<String, dynamic>? ?? {};
    return playlists.values.map((e) => Playlist.fromJson(e as Map<String, dynamic>)).toList();
  }

  // --- PLAYBACK HISTORY ---
  Future<void> recordHistory(String songId, int durationPlayedSec) async {
    final history = _data['history'] as List<dynamic>? ?? [];
    history.insert(0, {
      'songId': songId,
      'playedAt': DateTime.now().millisecondsSinceEpoch,
      'durationPlayed': durationPlayedSec,
    });
    if (history.length > 200) {
      history.removeRange(200, history.length);
    }
    _data['history'] = history;
    await _flush();
  }

  List<Map<String, dynamic>> getHistory() {
    final history = _data['history'] as List<dynamic>? ?? [];
    return history.map((e) => Map<String, dynamic>.from(e as Map)).toList();
  }

  // --- QUEUE SNAPSHOT ---
  Future<void> saveQueueSnapshot({
    required List<Song> queue,
    required int currentIndex,
    required int seekPositionMs,
  }) async {
    _data['queueSnapshot'] = {
      'queue': queue.map((s) => s.toJson()).toList(),
      'currentIndex': currentIndex,
      'seekPositionMs': seekPositionMs,
      'savedAt': DateTime.now().millisecondsSinceEpoch,
    };
    await _flush();
  }

  Map<String, dynamic>? getQueueSnapshot() {
    final snapshot = _data['queueSnapshot'] as Map<String, dynamic>?;
    if (snapshot == null || snapshot.isEmpty) return null;
    return snapshot;
  }
}

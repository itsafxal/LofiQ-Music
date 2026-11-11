import 'package:flutter_test/flutter_test.dart';
import 'package:lofiq_desktop/core/database/local_storage.dart';
import 'package:lofiq_desktop/core/models/song.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('LocalStorage Forensic Persistence Tests', () {
    late LocalStorage storage;

    setUp(() async {
      storage = LocalStorage.instance;
      await storage.init();
    });

    test('Playlist creation, track addition, and persistence', () async {
      final pl = await storage.createPlaylist('Study Session', description: 'Midnight study vibes');
      expect(pl.name, 'Study Session');
      expect(pl.id, startsWith('pl_'));

      // Add tracks
      await storage.addTracksToPlaylist(pl.id, ['track_1', 'track_2']);

      final playlists = storage.getAllPlaylists();
      final found = playlists.firstWhere((p) => p.id == pl.id);
      expect(found.songIds, ['track_1', 'track_2']);

      // Cleanup
      await storage.deletePlaylist(pl.id);
      final remaining = storage.getAllPlaylists();
      expect(remaining.any((p) => p.id == pl.id), isFalse);
    });

    test('Favorites toggling and retrieval', () async {
      const testSong = Song(
        id: 'fav_test_song_1',
        title: 'Favorite Track',
        artist: 'Artist Test',
        album: 'Album Test',
        durationSeconds: 180,
        artworkUrl: '',
        highResArtworkUrl: '',
        sourceUrl: '',
        providerId: 'local',
      );

      await storage.upsertSong(testSong);
      await storage.setFavorite(testSong.id, true);

      expect(storage.isFavorite(testSong.id), isTrue);
      expect(storage.getFavoriteIds(), contains(testSong.id));

      final favSongs = storage.getFavoriteSongs();
      expect(favSongs.any((s) => s.id == testSong.id), isTrue);

      // Remove favorite
      await storage.setFavorite(testSong.id, false);
      expect(storage.isFavorite(testSong.id), isFalse);
    });

    test('Playback history records timestamps and durations', () async {
      await storage.recordHistory('song_hist_1', 120);
      final history = storage.getHistory();

      expect(history, isNotEmpty);
      expect(history.first['songId'], 'song_hist_1');
      expect(history.first['durationPlayed'], 120);
      expect(history.first['playedAt'], isNotNull);
    });

    test('Queue snapshot persists and can be restored', () async {
      const song = Song(
        id: 'snapshot_song_1',
        title: 'Snapshot Test',
        artist: 'Tester',
        album: '',
        durationSeconds: 200,
        artworkUrl: '',
        highResArtworkUrl: '',
        sourceUrl: '',
        providerId: 'test',
      );

      await storage.saveQueueSnapshot(
        queue: [song],
        currentIndex: 0,
        seekPositionMs: 45000,
      );

      final snapshot = storage.getQueueSnapshot();
      expect(snapshot, isNotNull);
      expect(snapshot!['currentIndex'], 0);
      expect(snapshot['seekPositionMs'], 45000);
      expect((snapshot['queue'] as List).length, 1);
    });
  });
}


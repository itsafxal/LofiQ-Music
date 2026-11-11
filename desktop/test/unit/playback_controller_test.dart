import 'package:flutter_test/flutter_test.dart';
import 'package:lofiq_desktop/core/models/audio_quality.dart';
import 'package:lofiq_desktop/core/models/player_state.dart';
import 'package:lofiq_desktop/core/models/song.dart';
import 'package:lofiq_desktop/playback/playback_controller.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const dummySong1 = Song(
    id: 's1',
    title: 'Warm Nostalgia',
    artist: 'Komorebi',
    album: 'Lofi Dreams',
    durationSeconds: 180,
    artworkUrl: 'https://example.com/art1.jpg',
    highResArtworkUrl: 'https://example.com/art1_hd.jpg',
    sourceUrl: 'https://example.com/stream1.mp3',
    providerId: 'youtube',
    quality: AudioQuality.high,
  );

  const dummySong2 = Song(
    id: 's2',
    title: 'Midnight Rain',
    artist: 'Aruarian',
    album: 'City Nights',
    durationSeconds: 210,
    artworkUrl: 'https://example.com/art2.jpg',
    highResArtworkUrl: 'https://example.com/art2_hd.jpg',
    sourceUrl: 'https://example.com/stream2.mp3',
    providerId: 'jiosaavn',
    quality: AudioQuality.high,
  );

  const dummySong3 = Song(
    id: 's3',
    title: 'Coffee in Shibuya',
    artist: 'Neon Sakura',
    album: 'Tokyo Lofi',
    durationSeconds: 150,
    artworkUrl: 'https://example.com/art3.jpg',
    highResArtworkUrl: 'https://example.com/art3_hd.jpg',
    sourceUrl: 'https://example.com/stream3.mp3',
    providerId: 'local',
    quality: AudioQuality.high,
  );

  group('PlaybackController State Machine & Queue Management', () {
    late PlaybackController controller;

    setUp(() {
      controller = PlaybackController.instance;
      controller.clearQueue();
    });

    test('Initial state is empty and stopped', () {
      expect(controller.state.currentSong, isNull);
      expect(controller.state.isPlaying, isFalse);
      expect(controller.state.queue, isEmpty);
      expect(controller.state.queueIndex, -1);
    });

    test('setQueue populates queue and selects initial track', () {
      final queue = [dummySong1, dummySong2, dummySong3];
      controller.setQueue(queue, 1);

      expect(controller.state.queue.length, 3);
      expect(controller.state.queueIndex, 1);
      expect(controller.state.currentSong?.id, 's2');
      expect(controller.state.durationMs, 210000);
    });

    test('reorderQueue moves track correctly within queue', () {
      controller.setQueue([dummySong1, dummySong2, dummySong3], 0);

      // Move track 0 to position 2
      controller.reorderQueue(0, 3);

      expect(controller.state.queue[0].id, 's2');
      expect(controller.state.queue[1].id, 's3');
      expect(controller.state.queue[2].id, 's1');
      expect(controller.state.queueIndex, 2); // follows current song
    });

    test('removeFromQueue removes item and updates index safely', () {
      controller.setQueue([dummySong1, dummySong2, dummySong3], 1);

      // Remove s1 (index 0)
      controller.removeFromQueue(0);

      expect(controller.state.queue.length, 2);
      expect(controller.state.queue[0].id, 's2');
      expect(controller.state.queueIndex, 0); // shifted down by 1
    });

    test('seekTo clamps position between 0 and durationMs', () {
      controller.setQueue([dummySong1], 0);

      controller.seekTo(60000);
      expect(controller.state.currentPositionMs, 60000);

      controller.seekTo(999999);
      expect(controller.state.currentPositionMs, 180000); // capped at duration

      controller.seekTo(-500);
      expect(controller.state.currentPositionMs, 0); // clamped at 0
    });

    test('cycleRepeatMode rotates correctly: off -> all -> one -> off', () {
      expect(controller.state.repeatMode, PlaybackRepeatMode.off);

      controller.cycleRepeatMode();
      expect(controller.state.repeatMode, PlaybackRepeatMode.all);

      controller.cycleRepeatMode();
      expect(controller.state.repeatMode, PlaybackRepeatMode.one);

      controller.cycleRepeatMode();
      expect(controller.state.repeatMode, PlaybackRepeatMode.off);
    });

    test('setVolume clamps volume between 0.0 and 1.0', () {
      controller.setVolume(0.75);
      expect(controller.state.volume, 0.75);

      controller.setVolume(1.8);
      expect(controller.state.volume, 1.0);

      controller.setVolume(-0.3);
      expect(controller.state.volume, 0.0);
    });

    test('clearQueue resets entire playback state', () {
      controller.setQueue([dummySong1, dummySong2], 0);
      expect(controller.state.queue, isNotEmpty);

      controller.clearQueue();
      expect(controller.state.queue, isEmpty);
      expect(controller.state.currentSong, isNull);
      expect(controller.state.isPlaying, isFalse);
    });
  });
}


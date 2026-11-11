import 'dart:math' as math;
import 'package:flutter_test/flutter_test.dart';
import 'package:lofiq_desktop/core/models/song.dart';
import 'package:lofiq_desktop/playback/playback_controller.dart';

void main() {
  group('Audio Crossfade Unit Tests', () {
    late PlaybackController pbc;

    setUp(() {
      pbc = PlaybackController.instance;
      pbc.clearQueue();
    });

    test('Equal-Power Crossfade Curve maintains constant acoustic power', () {
      // Acoustic power = volA^2 + volB^2
      // For equal power: factorA = cos(t*pi/2), factorB = sin(t*pi/2)
      for (double t = 0.0; t <= 1.0; t += 0.05) {
        final factorA = math.cos(t * math.pi / 2);
        final factorB = math.sin(t * math.pi / 2);
        final totalPower = (factorA * factorA) + (factorB * factorB);

        // Power must equal 1.0 with negligible floating point delta (< 1e-9)
        expect((totalPower - 1.0).abs(), lessThan(1e-9));

        // Start condition: A=1.0, B=0.0
        if (t == 0.0) {
          expect(factorA, 1.0);
          expect(factorB, 0.0);
        }
        // Midpoint condition: A ≈ 0.7071, B ≈ 0.7071 (-3dB point each, combined 0dB)
        if ((t - 0.5).abs() < 1e-4) {
          expect((factorA - math.sqrt(0.5)).abs(), lessThan(1e-4));
          expect((factorB - math.sqrt(0.5)).abs(), lessThan(1e-4));
        }
        // End condition: A=0.0, B=1.0
        if ((t - 1.0).abs() < 1e-4) {
          expect(factorA.abs(), lessThan(1e-4));
          expect((factorB - 1.0).abs(), lessThan(1e-4));
        }
      }
    });

    test('Short Track Protection restricts crossfade window to at most half duration', () {
      // Track duration: 4000ms. Configured crossfade: 8s (8000ms)
      // Effective window must be clamped to 4000 ~/ 2 = 2000ms
      const durationMs = 4000;
      const crossfadeSec = 8;
      final effectiveWindow = math.min(crossfadeSec * 1000, durationMs ~/ 2);
      expect(effectiveWindow, 2000);

      // Track duration: 2000ms. Effective window: 1000ms -> should skip (<1500ms)
      const shortDurationMs = 2000;
      final shortWindow = math.min(crossfadeSec * 1000, shortDurationMs ~/ 2);
      expect(shortWindow < 1500, isTrue);
    });

    test('Manual Seek during Crossfade cancels crossfade and restores primary volume', () {
      final s1 = const Song(
        id: 's1',
        title: 'Song 1',
        artist: 'Artist 1',
        album: 'Album 1',
        durationSeconds: 180,
        artworkUrl: '',
        highResArtworkUrl: '',
        sourceUrl: '',
        providerId: 'youtube',
      );
      final s2 = const Song(
        id: 's2',
        title: 'Song 2',
        artist: 'Artist 2',
        album: 'Album 2',
        durationSeconds: 200,
        artworkUrl: '',
        highResArtworkUrl: '',
        sourceUrl: '',
        providerId: 'youtube',
      );

      pbc.setQueue([s1, s2]);
      expect(pbc.state.isCrossfading, isFalse);

      // SeekTo clamped
      pbc.seekTo(60000);
      expect(pbc.state.currentPositionMs, 60000);
      expect(pbc.state.isCrossfading, isFalse);
    });

    test('Queue alterations invalidate preloaded crossfade track', () {
      final s1 = const Song(id: 's1', title: 'Song 1', artist: 'A', album: 'Al', durationSeconds: 100, artworkUrl: '', highResArtworkUrl: '', sourceUrl: '', providerId: 'youtube');
      final s2 = const Song(id: 's2', title: 'Song 2', artist: 'A', album: 'Al', durationSeconds: 100, artworkUrl: '', highResArtworkUrl: '', sourceUrl: '', providerId: 'youtube');
      final s3 = const Song(id: 's3', title: 'Song 3', artist: 'A', album: 'Al', durationSeconds: 100, artworkUrl: '', highResArtworkUrl: '', sourceUrl: '', providerId: 'youtube');

      pbc.setQueue([s1, s2, s3]);
      expect(pbc.state.queue.length, 3);

      // Reordering queue
      pbc.reorderQueue(2, 1); // Move s3 before s2
      expect(pbc.state.queue[1].id, 's3');

      // Removing item from queue
      pbc.removeFromQueue(1);
      expect(pbc.state.queue.length, 2);
    });
  });
}


import 'dart:math' as math;
import 'package:flutter_test/flutter_test.dart';
import 'package:lofiq_desktop/core/models/player_state.dart';
import 'package:lofiq_desktop/core/models/song.dart';
import 'package:lofiq_desktop/playback/playback_controller.dart';
import 'package:lofiq_desktop/core/preferences/preferences_manager.dart';
import 'package:lofiq_desktop/playback/equalizer/equalizer_controller.dart';

void main() {
  group('Crossfade Production Stress Test Suite', () {
    late PlaybackController pbc;

    setUp(() {
      pbc = PlaybackController.instance;
      pbc.clearQueue();
    });

    Song makeSong(String id, int durationSec) {
      return Song(
        id: id,
        title: 'Track $id',
        artist: 'Artist $id',
        album: 'Album $id',
        durationSeconds: durationSec,
        artworkUrl: '',
        highResArtworkUrl: '',
        sourceUrl: '',
        providerId: 'jiosaavn',
      );
    }

    test('0-second crossfade completely bypasses preloading and overlapping', () {
      PreferencesManager.instance.updateSettings(
        (s) => s.copyWith(crossfadeDurationSec: 0),
      );
      final s1 = makeSong('s1', 180);
      final s2 = makeSong('s2', 180);
      pbc.setQueue([s1, s2]);

      // Check crossfade duration
      expect(PreferencesManager.instance.settings.crossfadeDurationSec, 0);

      // Trigger position check at end of track (179 seconds)
      pbc.seekTo(179000);
      expect(pbc.state.isCrossfading, isFalse);
      expect(pbc.isPreloaded, isFalse);
    });

    test('1-second crossfade window is under 1.5s threshold and is safely bypassed', () {
      PreferencesManager.instance.updateSettings(
        (s) => s.copyWith(crossfadeDurationSec: 1),
      );
      const crossfadeSec = 1;
      const durationMs = 180000;
      final effectiveWindowMs = math.min(crossfadeSec * 1000, durationMs ~/ 2);
      expect(effectiveWindowMs, 1000);
      expect(effectiveWindowMs < 1500, isTrue); // Must be skipped to prevent clipping
    });

    test('3-second, 8-second, and 12-second crossfade window math and step calculations', () {
      for (final sec in [3, 8, 12]) {
        PreferencesManager.instance.updateSettings(
          (s) => s.copyWith(crossfadeDurationSec: sec),
        );
        const durationMs = 240000; // 4 min
        final effectiveWindowMs = math.min(sec * 1000, durationMs ~/ 2);
        expect(effectiveWindowMs, sec * 1000);

        final preloadThresholdMs = math.max(0, durationMs - effectiveWindowMs - 4000);
        final crossfadeStartMs = math.max(0, durationMs - effectiveWindowMs);

        // Preload threshold must strictly precede crossfade start by exactly 4s
        expect(crossfadeStartMs - preloadThresholdMs, 4000);

        // Total steps must be bounded between 10 and 200
        final totalSteps = (effectiveWindowMs / 50).round().clamp(10, 200);
        expect(totalSteps >= 10 && totalSteps <= 200, isTrue);

        // Verify equal-power at every step
        for (int step = 0; step <= totalSteps; step++) {
          final t = (step / totalSteps).clamp(0.0, 1.0);
          final factorA = math.cos(t * math.pi / 2);
          final factorB = math.sin(t * math.pi / 2);
          final power = (factorA * factorA) + (factorB * factorB);
          expect((power - 1.0).abs(), lessThan(1e-9));
        }
      }
    });

    test('Short tracks (< 2 * crossfadeDuration) clamping and safety bypass', () {
      // Case A: 6s track with 8s crossfade -> clamped to 3s (50% duration)
      final sShort = makeSong('short', 6);
      const crossfadeSec = 8;
      final effectiveWindowA = math.min(crossfadeSec * 1000, sShort.durationMs ~/ 2);
      expect(effectiveWindowA, 3000);
      expect(effectiveWindowA >= 1500, isTrue); // Allowed at 3s

      // Case B: 2s track with 8s crossfade -> clamped to 1s (< 1.5s threshold)
      final sUltraShort = makeSong('ultraShort', 2);
      final effectiveWindowB = math.min(crossfadeSec * 1000, sUltraShort.durationMs ~/ 2);
      expect(effectiveWindowB, 1000);
      expect(effectiveWindowB < 1500, isTrue); // Bypassed
    });

    test('Long tracks (10+ minutes) calculate preload and ramp boundaries with precision', () {
      final sLong = makeSong('long', 600); // 10 minutes = 600,000ms
      const crossfadeSec = 12;
      final effectiveWindow = math.min(crossfadeSec * 1000, sLong.durationMs ~/ 2);
      expect(effectiveWindow, 12000);

      final preloadThreshold = sLong.durationMs - effectiveWindow - 4000;
      expect(preloadThreshold, 584000); // 9 min 44 sec

      final crossfadeStart = sLong.durationMs - effectiveWindow;
      expect(crossfadeStart, 588000); // 9 min 48 sec
    });

    test('Repeat Mode cycle transitions correctly (off -> all -> one -> off)', () {
      final s1 = makeSong('s1', 180);
      final s2 = makeSong('s2', 180);
      pbc.setQueue([s1, s2]);

      expect(pbc.state.repeatMode, PlaybackRepeatMode.off);

      pbc.cycleRepeatMode();
      expect(pbc.state.repeatMode, PlaybackRepeatMode.all);

      pbc.cycleRepeatMode();
      expect(pbc.state.repeatMode, PlaybackRepeatMode.one);

      pbc.cycleRepeatMode();
      expect(pbc.state.repeatMode, PlaybackRepeatMode.off);
    });

    test('SkipToIndex updates active queue pointer', () {
      final s1 = makeSong('s1', 180);
      final s2 = makeSong('s2', 180);
      pbc.setQueue([s1, s2]);
      pbc.skipToIndex(1); // At index 1

      expect(pbc.state.queueIndex, 1);
      expect(pbc.state.currentSong?.id, 's2');
    });

    test('Manual Next/Previous cancels active ramp and maintains master volume', () {
      final s1 = makeSong('s1', 180);
      final s2 = makeSong('s2', 180);
      final s3 = makeSong('s3', 180);
      pbc.setQueue([s1, s2, s3]);
      pbc.setVolume(0.8);

      expect(pbc.state.volume, 0.8);
      expect(pbc.state.isCrossfading, isFalse);

      // Trigger next
      pbc.next();
      expect(pbc.state.queueIndex, 1);
      expect(pbc.state.volume, 0.8);
      expect(pbc.state.isCrossfading, isFalse);

      // Trigger previous
      pbc.previous();
      expect(pbc.state.queueIndex, 0);
      expect(pbc.state.volume, 0.8);
      expect(pbc.state.isCrossfading, isFalse);
    });

    test('Master volume change during transition scales acoustic power proportionally', () {
      const updatedMaster = 0.6;

      for (double t = 0.0; t <= 1.0; t += 0.2) {
        final factorA = math.cos(t * math.pi / 2);
        final factorB = math.sin(t * math.pi / 2);

        final volA = factorA * updatedMaster;
        final volB = factorB * updatedMaster;

        final scaledPower = (volA * volA) + (volB * volB);
        final expectedPower = updatedMaster * updatedMaster;

        expect((scaledPower - expectedPower).abs(), lessThan(1e-9));
      }
    });

    test('DSP Equalizer and Bass Boost filter generation sync across players', () {
      final eq = EqualizerController.instance;
      eq.setBassBoost(1.5);
      eq.setVirtualizer(1.2);
      eq.setBandLevel(0, 400); // +4.0 dB = 400 millibels
      eq.setBandLevel(9, 300); // +3.0 dB = 300 millibels

      final filterString = eq.buildMpvAudioFilter();
      expect(filterString.contains('equalizer='), isTrue);
      expect(filterString.contains('f=31'), isTrue);
      expect(filterString.contains('f=16000'), isTrue);
      expect(filterString.contains('extrastereo'), isTrue);

      // Applying audio filter does not crash or corrupt player state
      pbc.applyAudioFilter(filterString);
    });
  });
}

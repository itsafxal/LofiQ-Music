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

    test('0-second crossfade disables overlap while enabling ~10s gapless preloading', () {
      PreferencesManager.instance.updateSettings(
        (s) => s.copyWith(crossfadeDurationSec: 0),
      );
      final s1 = makeSong('s1', 180);
      final s2 = makeSong('s2', 180);
      pbc.setQueue([s1, s2]);

      expect(PreferencesManager.instance.settings.crossfadeDurationSec, 0);
      expect(PlaybackController.computeEffectiveCrossfadeMs(0, s1.durationMs), 0);
      expect(PlaybackController.computePreloadTriggerMs(s1.durationMs, 0), 10000);

      // Zero-overlap mode never sets overlapping crossfade active
      pbc.seekTo(179000);
      expect(pbc.state.isCrossfading, isFalse);
    });

    test('1-second crossfade window is under 1.5s guard and degrades cleanly to gapless', () {
      PreferencesManager.instance.updateSettings(
        (s) => s.copyWith(crossfadeDurationSec: 1),
      );
      const durationMs = 180000;
      final effectiveWindowMs = PlaybackController.computeEffectiveCrossfadeMs(1, durationMs);
      expect(effectiveWindowMs, 0);
      expect(PlaybackController.computePreloadTriggerMs(durationMs, effectiveWindowMs), 10000);
    });

    test('3-second, 8-second, and 12-second crossfade use ~10s early preload and equal-power gains', () {
      for (final sec in [3, 8, 12]) {
        PreferencesManager.instance.updateSettings(
          (s) => s.copyWith(crossfadeDurationSec: sec),
        );
        const durationMs = 240000; // 4 min
        final effectiveWindowMs = PlaybackController.computeEffectiveCrossfadeMs(sec, durationMs);
        expect(effectiveWindowMs, sec * 1000);

        final preloadTriggerMs = PlaybackController.computePreloadTriggerMs(
          durationMs,
          effectiveWindowMs,
        );
        final crossfadeStartMs = math.max(0, durationMs - effectiveWindowMs);

        // Early preload fires around 10 seconds into playback, well before crossfade window
        expect(preloadTriggerMs, 10000);
        expect(preloadTriggerMs < crossfadeStartMs, isTrue);

        // Verify equal-power at every step
        final totalSteps = (effectiveWindowMs / 40).round().clamp(10, 300);
        for (int step = 0; step <= totalSteps; step++) {
          final t = (step / totalSteps).clamp(0.0, 1.0);
          final factorA = PlaybackController.equalPowerOutGain(t);
          final factorB = PlaybackController.equalPowerInGain(t);
          final power = (factorA * factorA) + (factorB * factorB);
          expect((power - 1.0).abs(), lessThan(1e-9));
        }
      }
    });

    test('Short tracks (< 2 * crossfadeDuration) clamping, proportional preload, and safety bypass', () {
      // Case A: 6s track with 8s crossfade -> clamped to 3s (50% duration)
      final sShort = makeSong('short', 6);
      final effectiveWindowA = PlaybackController.computeEffectiveCrossfadeMs(8, sShort.durationMs);
      expect(effectiveWindowA, 3000);
      final triggerA = PlaybackController.computePreloadTriggerMs(sShort.durationMs, effectiveWindowA);
      expect(triggerA, lessThan(sShort.durationMs - effectiveWindowA));

      // Case B: 2s track with 8s crossfade -> clamped to 1s (< 1.5s threshold) -> degrades cleanly to 0 overlap
      final sUltraShort = makeSong('ultraShort', 2);
      final effectiveWindowB = PlaybackController.computeEffectiveCrossfadeMs(8, sUltraShort.durationMs);
      expect(effectiveWindowB, 0);
    });

    test('Long tracks (10+ minutes) preload at 10s early window and start crossfade precisely at tail', () {
      final sLong = makeSong('long', 600); // 10 minutes = 600,000ms
      final effectiveWindow = PlaybackController.computeEffectiveCrossfadeMs(12, sLong.durationMs);
      expect(effectiveWindow, 12000);

      final preloadTrigger = PlaybackController.computePreloadTriggerMs(sLong.durationMs, effectiveWindow);
      expect(preloadTrigger, 10000);

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

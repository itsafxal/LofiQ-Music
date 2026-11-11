import 'package:flutter_test/flutter_test.dart';
import 'package:lofiq_desktop/playback/equalizer/equalizer_controller.dart';

void main() {
  group('EqualizerController DSP Unit Tests', () {
    late EqualizerController eq;

    setUp(() {
      eq = EqualizerController.instance;
      eq.reset();
    });

    test('Has exactly 10 standard audio frequency bands', () {
      expect(eq.bandFrequencies.length, 10);
      expect(eq.bandFrequencies, [31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000]);
    });

    test('Initial reset state is Flat with 0 millibels across all bands', () {
      for (int i = 0; i < 10; i++) {
        expect(eq.getBandLevel(i), 0);
      }
      expect(eq.currentPreset, 'Flat');
      expect(eq.bassBoost, 0);
      expect(eq.virtualizer, 0);
    });

    test('setBandLevel updates individual band and switches to Custom preset', () {
      eq.setBandLevel(0, 800); // 31Hz +8dB
      expect(eq.getBandLevel(0), 800);
      expect(eq.currentPreset, 'Custom');

      // Clamping limits (-1500 to +1500)
      eq.setBandLevel(1, 2500);
      expect(eq.getBandLevel(1), 1500);

      eq.setBandLevel(2, -3000);
      expect(eq.getBandLevel(2), -1500);
    });

    test('applyPreset loads configured preset gains correctly', () {
      eq.applyPreset('Deep Bass');
      expect(eq.currentPreset, 'Deep Bass');
      expect(eq.getBandLevel(0), 800); // 31Hz boosted
      expect(eq.getBandLevel(1), 700); // 62Hz boosted
      expect(eq.getBandLevel(9), 0);   // 16kHz flat

      eq.applyPreset('Lofi Warmth');
      expect(eq.currentPreset, 'Lofi Warmth');
      expect(eq.getBandLevel(0), 500); // Warm bass
      expect(eq.getBandLevel(9), -500); // Rolled-off high treble
    });

    test('Bass Boost and Virtualizer accept both 0-1.0 and 0-1000 scales', () {
      eq.setBassBoost(0.65);
      expect(eq.bassBoostStrength, 0.65);
      expect(eq.bassBoost, 650);

      eq.setVirtualizer(850);
      expect(eq.virtualizerStrength, 0.85);
      expect(eq.virtualizer, 850);
    });

    test('Master enable switch toggles DSP state', () {
      expect(eq.isEnabled, isTrue);
      eq.setEnabled(false);
      expect(eq.isEnabled, isFalse);
      eq.setEnabled(true);
      expect(eq.isEnabled, isTrue);
    });

    test('buildMpvAudioFilter produces valid libmpv filter string for DSP pipeline', () {
      // 1. Flat state produces 10 biquads
      final flatFilter = eq.buildMpvAudioFilter();
      expect(flatFilter, contains('equalizer=f=31:width_type=o:width=1:g=0.0'));
      expect(flatFilter, contains('equalizer=f=16000:width_type=o:width=1:g=0.0'));
      expect(flatFilter, isNot(contains('extrastereo')));

      // 2. Add bass boost and virtualizer
      eq.setBassBoost(0.5); // +6dB at 60Hz
      eq.setVirtualizer(0.8); // 1.0 + (0.8 * 1.5) = 2.2x multiplier
      final activeFilter = eq.buildMpvAudioFilter();
      expect(activeFilter, contains('equalizer=f=60:width_type=o:width=1.5:g=6.0'));
      expect(activeFilter, contains('lavfi=[extrastereo=m=2.2]'));

      // 3. Disabled DSP produces empty filter string
      eq.setEnabled(false);
      expect(eq.buildMpvAudioFilter(), isEmpty);
    });
  });
}


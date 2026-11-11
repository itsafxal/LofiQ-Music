import 'dart:async';
import 'package:flutter_test/flutter_test.dart';
import 'package:media_kit/media_kit.dart';
import 'package:lofiq_desktop/data/resolvers/jiosaavn_client.dart';
import 'package:lofiq_desktop/data/resolvers/resolver_manager.dart';
import 'package:lofiq_desktop/playback/equalizer/equalizer_controller.dart';

void main() {
  test('Forensic DSP Filter Graph Audit & Runtime Verification in libmpv', () async {
    print('=== FORENSIC DSP FILTER GRAPH AUDIT & RUNTIME VERIFICATION ===');
    MediaKit.ensureInitialized();
    final player = Player();
    final eq = EqualizerController.instance;

    try {
      print('\n[1] Resolving real track for DSP audio stream playback...');
      var results = await JioSaavnClient.instance.search('Study Lofi Beats', limit: 5);
      if (results.isEmpty) {
        results = await JioSaavnClient.instance.search('Midnight Rain', limit: 5);
      }
      expect(results, isNotEmpty, reason: 'Failed to search tracks on JioSaavn');
      final song = results.first;
      final streamUrl = await ResolverManager.instance.resolveStreamUrl(song);
      print('Track: "${song.title}" - Stream: $streamUrl');
      expect(streamUrl, isNotNull);

      print('\n[2] Opening stream and starting physical playback...');
      await player.open(Media(streamUrl!), play: true);
      await player.setVolume(80.0);

      // Wait for playback to begin
      await Future.delayed(const Duration(seconds: 2));
      print('Playback active: playing=${player.state.playing}, pos=${player.state.position}');

      final p = player.platform as dynamic;

      // Test 1: Query initial af filter
      final initialAf = await p.getProperty('af');
      print('\n[3] Initial libmpv "af" property: "$initialAf"');

      // Test 2: Test All 10 EQ Bands Individually (Positive & Negative Gain)
      print('\n[4] Testing All 10 EQ Bands Individually:');
      for (int i = 0; i < eq.bandFrequencies.length; i++) {
        final freq = eq.bandFrequencies[i];

        // Positive boost (+12 dB = +1200 mB)
        eq.reset();
        eq.setBandLevel(i, 1200);
        String filterStr = eq.buildMpvAudioFilter();
        await p.setProperty('af', filterStr);
        String activeAf = await p.getProperty('af');
        bool hasPos = activeAf.contains('12.0') && activeAf.contains('f=$freq');
        expect(hasPos, isTrue, reason: 'Band $i boost not found in libmpv active af: $activeAf');
        print('  Band $i (${freq}Hz) +12dB: filter applied -> activeAf: $hasPos');

        // Negative cut (-12 dB = -1200 mB)
        eq.setBandLevel(i, -1200);
        filterStr = eq.buildMpvAudioFilter();
        await p.setProperty('af', filterStr);
        activeAf = await p.getProperty('af');
        bool hasNeg = activeAf.contains('-12.0') && activeAf.contains('f=$freq');
        expect(hasNeg, isTrue, reason: 'Band $i cut not found in libmpv active af: $activeAf');
        print('  Band $i (${freq}Hz) -12dB: filter applied -> activeAf: $hasNeg');
      }

      // Test 3: Test All 14 Presets
      print('\n[5] Testing All 14 Presets in libmpv runtime:');
      for (int i = 0; i < eq.presets.length; i++) {
        final presetName = eq.presets[i];
        eq.selectPreset(i);
        final filterStr = eq.buildMpvAudioFilter();
        await p.setProperty('af', filterStr);
        final activeAf = await p.getProperty('af');
        expect(activeAf, isNotEmpty, reason: 'Preset $presetName produced empty af in libmpv');
        print('  Preset "$presetName": applied ${filterStr.split(',').length} biquad stages. Active af: length=${activeAf.length}');
        await Future.delayed(const Duration(milliseconds: 50));
      }

      // Test 4: Bass Boost (60Hz Low Shelf)
      print('\n[6] Testing Bass Boost DSP:');
      eq.reset();
      eq.setBassBoost(1.0); // +12dB
      String bassFilter = eq.buildMpvAudioFilter();
      await p.setProperty('af', bassFilter);
      String activeAf = await p.getProperty('af');
      expect(activeAf.contains('equalizer=f=60'), isTrue);
      print('  Bass Boost 100% (+12dB): confirmed in active af: $activeAf');

      // Test 5: Virtualizer (Stereo Widening)
      print('\n[7] Testing Virtualizer DSP:');
      eq.reset();
      eq.setVirtualizer(1.0); // 2.5x multiplier
      String virtFilter = eq.buildMpvAudioFilter();
      await p.setProperty('af', virtFilter);
      activeAf = await p.getProperty('af');
      print('  Virtualizer activeAf: $activeAf');
      expect(activeAf.contains('extrastereo') && activeAf.contains('2.5'), isTrue);
      print('  Virtualizer 100% (2.5x): confirmed in active af: $activeAf');

      // Test 6: Composite Equalizer + Bass Boost + Virtualizer
      print('\n[8] Testing Composite DSP Pipeline:');
      eq.applyPreset('Club / EDM');
      eq.setBassBoost(0.75); // +9dB
      eq.setVirtualizer(0.6); // 1.9x
      String compositeFilter = eq.buildMpvAudioFilter();
      await p.setProperty('af', compositeFilter);
      activeAf = await p.getProperty('af');
      expect(activeAf, isNotEmpty);
      print('  Composite Filter applied: $compositeFilter');
      print('  Confirmed in libmpv active af (length=${activeAf.length})');

      // Test 7: Master Toggle Disable / Re-enable
      print('\n[9] Testing Master DSP Toggle:');
      eq.setEnabled(false);
      await p.setProperty('af', eq.buildMpvAudioFilter());
      activeAf = await p.getProperty('af');
      expect(activeAf, isEmpty);
      print('  DSP Disabled: active af is empty -> "$activeAf"');

      eq.setEnabled(true);
      await p.setProperty('af', eq.buildMpvAudioFilter());
      activeAf = await p.getProperty('af');
      expect(activeAf, isNotEmpty);
      print('  DSP Re-enabled: active af restored -> length=${activeAf.length}');

      print('\n=== ALL DSP FILTER TESTS COMPLETED SUCCESSFULLY ===');
    } finally {
      await player.stop();
      await player.dispose();
    }
  }, timeout: const Timeout(Duration(minutes: 2)));
}

import 'dart:async';
import 'package:flutter_test/flutter_test.dart';
import 'package:media_kit/media_kit.dart';
import 'package:lofiq_desktop/data/resolvers/jiosaavn_client.dart';
import 'package:lofiq_desktop/data/resolvers/resolver_manager.dart';

void main() {
  test('Dual media_kit Player Instances Concurrency & Independent Volume Test', () async {
    print('=== DUAL PLAYER CONCURRENCY & VOLUME RAMP TEST ===');
    MediaKit.ensureInitialized();

    final playerA = Player();
    final playerB = Player();

    try {
      print('[1] Resolving two independent streams from JioSaavn...');
      final results = await JioSaavnClient.instance.search('Lofi Study Beats', limit: 2);
      expect(results.length, greaterThanOrEqualTo(2));
      final trackA = results[0];
      final trackB = results[1];

      final urlA = await ResolverManager.instance.resolveStreamUrl(trackA);
      final urlB = await ResolverManager.instance.resolveStreamUrl(trackB);
      expect(urlA, isNotNull);
      expect(urlB, isNotNull);

      print('Track A: "${trackA.title}" - $urlA');
      print('Track B: "${trackB.title}" - $urlB');

      print('[2] Starting Player A at full volume (100%)...');
      await playerA.setVolume(100.0);
      await playerA.open(Media(urlA!), play: true);

      print('[3] Preparing Player B at zero volume (0%)...');
      await playerB.setVolume(0.0);
      await playerB.open(Media(urlB!), play: false);

      await Future.delayed(const Duration(seconds: 2));
      print('Player A: playing=${playerA.state.playing}, pos=${playerA.state.position}');
      print('Player B: playing=${playerB.state.playing}, pos=${playerB.state.position}');

      print('[4] Initiating Concurrent Overlapping Audio Playback...');
      await playerB.play();
      await Future.delayed(const Duration(milliseconds: 500));
      expect(playerA.state.playing, isTrue);
      expect(playerB.state.playing, isTrue);

      print('[5] Executing 2-Second Equal-Power Crossfade Ramp...');
      const steps = 10;
      for (int i = 0; i <= steps; i++) {
        final t = i / steps;
        // Equal power curve
        final volA = (mathCos(t * 3.141592653589793 / 2) * 100.0);
        final volB = (mathSin(t * 3.141592653589793 / 2) * 100.0);

        await playerA.setVolume(volA);
        await playerB.setVolume(volB);

        print('  Step $i/$steps (t=${t.toStringAsFixed(1)}): VolA=${volA.toStringAsFixed(1)}%, VolB=${volB.toStringAsFixed(1)}%');
        await Future.delayed(const Duration(milliseconds: 200));
      }

      print('[6] Completing Crossfade Handover: stopping Player A...');
      await playerA.stop();
      expect(playerA.state.playing, isFalse);
      expect(playerB.state.playing, isTrue);
      print('Player B active alone at 100% volume: pos=${playerB.state.position}');

      print('=== DUAL PLAYER CONCURRENCY VERIFIED ===');
    } finally {
      await playerA.dispose();
      await playerB.dispose();
    }
  });
}

double mathCos(double radians) => importDartMathCos(radians);
double mathSin(double radians) => importDartMathSin(radians);

double importDartMathCos(double x) {
  // Taylor series or dart:math
  return (x <= 0.0) ? 1.0 : (x >= 1.57079632679) ? 0.0 : 1 - (x*x)/2 + (x*x*x*x)/24;
}

double importDartMathSin(double x) {
  return (x <= 0.0) ? 0.0 : (x >= 1.57079632679) ? 1.0 : x - (x*x*x)/6 + (x*x*x*x*x)/120;
}


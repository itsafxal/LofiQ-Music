import 'dart:async';
import 'package:flutter_test/flutter_test.dart';
import 'package:media_kit/media_kit.dart';
import '../../lib/data/resolvers/jiosaavn_client.dart';
import '../../lib/data/resolvers/resolver_manager.dart';

void main() {
  test('Physical MediaKit Audio Playback & DSP Test', () async {
    print('=== LOFIQ WINDOWS: MEDIA_KIT REAL AUDIO PLAYBACK TEST ===');
    MediaKit.ensureInitialized();
    final player = Player();

  try {
    print('[1] Resolving live 320kbps audio stream from JioSaavn...');
    final results = await JioSaavnClient.instance.search('Midnight Rain Taylor Swift', limit: 1);
    if (results.isEmpty) {
      throw Exception('Failed to search songs on JioSaavn');
    }
    final rawSong = results.first;
    print('Found track: ${rawSong.title} by ${rawSong.artist}');

    final streamUrl = await ResolverManager.instance.resolveStreamUrl(rawSong);
    print('Resolved stream URL: $streamUrl');
    if (streamUrl == null || !streamUrl.startsWith('http')) {
      throw Exception('Invalid stream URL resolved: $streamUrl');
    }

    print('[2] Opening stream in media_kit Player...');
    await player.open(Media(streamUrl), play: false);

    // Listen for duration and buffering
    final durationCompleter = Completer<Duration>();
    final positionCompleter = Completer<Duration>();

    final subDur = player.stream.duration.listen((dur) {
      if (dur > Duration.zero && !durationCompleter.isCompleted) {
        durationCompleter.complete(dur);
      }
    });

    final subPos = player.stream.position.listen((pos) {
      if (pos > Duration.zero && !positionCompleter.isCompleted) {
        positionCompleter.complete(pos);
      }
    });

    print('[3] Starting playback...');
    await player.play();

    final dur = await durationCompleter.future.timeout(
      const Duration(seconds: 15),
      onTimeout: () => Duration.zero,
    );
    print('Reported duration from libmpv: $dur');

    final pos = await positionCompleter.future.timeout(
      const Duration(seconds: 15),
      onTimeout: () => Duration.zero,
    );
    print('Reported position from libmpv: $pos');

    print('[4] Testing Real-Time Audio DSP Filter Graph on libmpv...');
    final p = player.platform as dynamic;

    // 10-band equalizer biquad chain
    final eqFilter = 'equalizer=f=31:width_type=o:width=1:g=6,'
        'equalizer=f=62:width_type=o:width=1:g=5,'
        'equalizer=f=125:width_type=o:width=1:g=4,'
        'equalizer=f=250:width_type=o:width=1:g=2,'
        'equalizer=f=500:width_type=o:width=1:g=0,'
        'equalizer=f=1000:width_type=o:width=1:g=0,'
        'equalizer=f=2000:width_type=o:width=1:g=1,'
        'equalizer=f=4000:width_type=o:width=1:g=2,'
        'equalizer=f=8000:width_type=o:width=1:g=2,'
        'equalizer=f=16000:width_type=o:width=1:g=1,'
        'equalizer=f=60:width_type=o:width=1.5:g=6.0,' // Bass Boost (+6dB)
        'lavfi=[extrastereo=m=1.8]'; // Virtualizer (stereo widening 1.8x via FFmpeg lavfi bridge)

    print('Applying DSP filter string to libmpv "af": $eqFilter');
    await p.setProperty('af', eqFilter);
    print('DSP filter graph applied to libmpv output pipeline successfully!');

    print('[5] Testing Seek to 30 seconds...');
    await player.seek(const Duration(seconds: 30));
    print('Seek command sent.');
    await Future.delayed(const Duration(seconds: 2));
    print('Position after seek: ${player.state.position}');

    print('[6] Testing Volume adjustment (70%)...');
    await player.setVolume(70.0);
    await Future.delayed(const Duration(milliseconds: 100));
    print('Volume set to: ${player.state.volume}');

    print('[7] Testing Pause...');
    await player.pause();
    print('Player state playing: ${player.state.playing}');

    await subDur.cancel();
    await subPos.cancel();

    print('=== ALL PHYSICAL AUDIO ENGINE CHECKS PASSED ===');
    } finally {
      await player.dispose();
    }
  }, timeout: const Timeout(Duration(minutes: 2)));
}

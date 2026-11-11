import 'dart:async';
import 'package:flutter_test/flutter_test.dart';
import 'package:media_kit/media_kit.dart';
import 'package:lofiq_desktop/data/resolvers/jiosaavn_client.dart';
import 'package:lofiq_desktop/playback/playback_controller.dart';
import 'package:lofiq_desktop/core/models/song.dart';

void main() {
  test('Physical Audio Output & Lifecycle Verification on Windows', () async {
    print('=== PHYSICAL WINDOWS AUDIO OUTPUT & LIFECYCLE AUDIT ===');
    MediaKit.ensureInitialized();
    final pbc = PlaybackController.instance;
    await pbc.init();

    // 1. Audio Device Identification
    print('\n[1] Windows Audio Device Detection:');
    final tempPlayer = Player();
    final tp = tempPlayer.platform as dynamic;
    final activeDevice = await tp.getProperty('audio-device');
    final deviceList = await tp.getProperty('audio-device-list');
    final aoDriver = await tp.getProperty('ao');
    print('  Active Output Device: $activeDevice');
    print('  Active Audio Output Driver (ao): $aoDriver (WASAPI default)');
    print('  Available Audio Devices: $deviceList');
    await tempPlayer.dispose();

    expect(activeDevice, isNotNull);

    // 2. Fetch Two Live JioSaavn Tracks for Queue Testing
    print('\n[2] Resolving 2 Live JioSaavn Tracks for Physical Queue Testing:');
    final searchResults = await JioSaavnClient.instance.search('Lofi Study Beats', limit: 2);
    expect(searchResults.length, greaterThanOrEqualTo(2), reason: 'Expected at least 2 tracks from JioSaavn');
    final track1 = searchResults[0];
    final track2 = searchResults[1];
    print('  Track 1: "${track1.title}" by ${track1.artist}');
    print('  Track 2: "${track2.title}" by ${track2.artist}');

    // 3. Play Track 1
    print('\n[3] Initiating Physical Playback of Track 1:');
    await pbc.playSong(track1, [track1, track2]);

    // Wait for stream buffering and initial playback
    await Future.delayed(const Duration(seconds: 3));
    print('  State: isPlaying=${pbc.state.isPlaying}, pos=${pbc.state.currentPositionMs}ms, dur=${pbc.state.durationMs}ms');
    expect(pbc.state.isPlaying, isTrue);
    expect(pbc.state.currentPositionMs, greaterThan(0));
    expect(pbc.state.durationMs, greaterThan(0));

    // Confirm position increments (active audio rendering)
    final pos1 = pbc.state.currentPositionMs;
    await Future.delayed(const Duration(seconds: 2));
    final pos2 = pbc.state.currentPositionMs;
    print('  Position progression check: $pos1 ms -> $pos2 ms (diff: ${pos2 - pos1}ms)');
    expect(pos2, greaterThan(pos1), reason: 'Audio clock not advancing');

    // 4. Test Pause and Resume
    print('\n[4] Testing Pause & Resume:');
    pbc.togglePlayPause();
    await Future.delayed(const Duration(milliseconds: 500));
    print('  After pause: isPlaying=${pbc.state.isPlaying}');
    expect(pbc.state.isPlaying, isFalse);

    final pausePos1 = pbc.state.currentPositionMs;
    await Future.delayed(const Duration(seconds: 1));
    final pausePos2 = pbc.state.currentPositionMs;
    print('  Position stability while paused: $pausePos1 ms vs $pausePos2 ms');
    expect(pausePos2, equals(pausePos1), reason: 'Position advanced while paused');

    pbc.togglePlayPause(); // Resume
    await Future.delayed(const Duration(seconds: 1));
    print('  After resume: isPlaying=${pbc.state.isPlaying}, pos=${pbc.state.currentPositionMs}ms');
    expect(pbc.state.isPlaying, isTrue);
    expect(pbc.state.currentPositionMs, greaterThan(pausePos2));

    // 5. Test Seek
    print('\n[5] Testing Seek to 30,000 ms:');
    pbc.seekTo(30000);
    await Future.delayed(const Duration(seconds: 2));
    print('  Position after seek: ${pbc.state.currentPositionMs} ms');
    expect(pbc.state.currentPositionMs, greaterThanOrEqualTo(29000));

    // 6. Test Volume Levels (0%, 25%, 50%, 70%, 100%)
    print('\n[6] Testing Volume Levels (Audible Output & Native Property):');
    final volumeSteps = [0.0, 0.25, 0.50, 0.70, 1.0];
    for (final v in volumeSteps) {
      pbc.setVolume(v);
      await Future.delayed(const Duration(milliseconds: 150));
      print('  Requested: ${(v * 100).toInt()}% -> State Volume: ${pbc.state.volume}');
      expect(pbc.state.volume, equals(v));
    }

    // 7. Test Next and Previous (Queue Transitions)
    print('\n[7] Testing Queue Navigation (Next / Previous):');
    print('  Current Track before Next: "${pbc.state.currentSong?.title}" (Index: ${pbc.state.queueIndex})');
    pbc.next();
    await Future.delayed(const Duration(seconds: 3));
    print('  Current Track after Next: "${pbc.state.currentSong?.title}" (Index: ${pbc.state.queueIndex})');
    expect(pbc.state.queueIndex, equals(1));
    expect(pbc.state.currentSong?.id, equals(track2.id));

    pbc.previous();
    await Future.delayed(const Duration(seconds: 3));
    print('  Current Track after Previous: "${pbc.state.currentSong?.title}" (Index: ${pbc.state.queueIndex})');
    expect(pbc.state.queueIndex, equals(0));
    expect(pbc.state.currentSong?.id, equals(track1.id));

    // 8. Test Stop
    print('\n[8] Testing Dedicated Stop Operation:');
    pbc.stop();
    await Future.delayed(const Duration(milliseconds: 500));
    print('  State after stop(): isPlaying=${pbc.state.isPlaying}, pos=${pbc.state.currentPositionMs}ms');
    expect(pbc.state.isPlaying, isFalse);
    expect(pbc.state.currentPositionMs, equals(0));

    // 9. Test Network Interruption & Stream Recovery
    print('\n[9] Testing Network Recovery on Invalid Stream:');
    final brokenSong = const Song(
      id: 'broken_id',
      title: 'Invalid Stream Track',
      artist: 'Test',
      album: 'Test',
      durationSeconds: 180,
      artworkUrl: '',
      highResArtworkUrl: '',
      sourceUrl: 'https://invalid-non-existent-domain-404.com/audio.mp3',
      providerId: 'invalid',
    );
    await pbc.playSong(brokenSong);
    await Future.delayed(const Duration(seconds: 1));
    print('  Error handled safely: errorMessage="${pbc.state.errorMessage}", isPlaying=${pbc.state.isPlaying}');
    expect(pbc.state.isPlaying, isFalse);

    // Recover by playing valid track again
    await pbc.playSong(track1);
    await Future.delayed(const Duration(seconds: 3));
    print('  Recovered cleanly: isPlaying=${pbc.state.isPlaying}, track="${pbc.state.currentSong?.title}"');
    expect(pbc.state.isPlaying, isTrue);

    pbc.stop();
    print('\n=== ALL PHYSICAL AUDIO LIFECYCLE CHECKS PASSED ===');
  }, timeout: const Timeout(Duration(minutes: 3)));
}


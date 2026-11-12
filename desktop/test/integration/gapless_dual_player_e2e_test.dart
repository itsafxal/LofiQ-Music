import 'dart:io';
import 'dart:math' as math;
import 'dart:typed_data';
import 'package:flutter_test/flutter_test.dart';
import 'package:lofiq_desktop/core/models/audio_quality.dart';
import 'package:lofiq_desktop/core/models/player_state.dart';
import 'package:lofiq_desktop/core/models/song.dart';
import 'package:lofiq_desktop/core/preferences/preferences_manager.dart';
import 'package:lofiq_desktop/playback/equalizer/equalizer_controller.dart';
import 'package:lofiq_desktop/playback/playback_controller.dart';
import 'package:media_kit/media_kit.dart';

/// Generates a real 16-bit mono PCM WAV file on disk so libmpv decodes & plays
/// real audio frames offline without network dependencies.
Future<File> _writeTestWavFile(
  Directory dir,
  String name, {
  required int durationSec,
  required double freqHz,
}) async {
  const sampleRate = 22050;
  final numSamples = sampleRate * durationSec;
  final dataSize = numSamples * 2;
  final buffer = ByteData(44 + dataSize);

  void writeAscii(int offset, String text) {
    for (int i = 0; i < text.length; i++) {
      buffer.setUint8(offset + i, text.codeUnitAt(i));
    }
  }

  writeAscii(0, 'RIFF');
  buffer.setUint32(4, 36 + dataSize, Endian.little);
  writeAscii(8, 'WAVE');
  writeAscii(12, 'fmt ');
  buffer.setUint32(16, 16, Endian.little); // PCM header size
  buffer.setUint16(20, 1, Endian.little); // PCM format
  buffer.setUint16(22, 1, Endian.little); // Mono
  buffer.setUint32(24, sampleRate, Endian.little);
  buffer.setUint32(28, sampleRate * 2, Endian.little);
  buffer.setUint16(32, 2, Endian.little);
  buffer.setUint16(34, 16, Endian.little);
  writeAscii(36, 'data');
  buffer.setUint32(40, dataSize, Endian.little);

  for (int i = 0; i < numSamples; i++) {
    final sample = (math.sin(2 * math.pi * freqHz * i / sampleRate) * 8000).round();
    buffer.setInt16(44 + (i * 2), sample, Endian.little);
  }

  final file = File('${dir.path}${Platform.pathSeparator}$name.wav');
  await file.writeAsBytes(buffer.buffer.asUint8List(), flush: true);
  return file;
}

Future<void> _waitUntil(
  bool Function() predicate, {
  Duration timeout = const Duration(seconds: 15),
  Duration poll = const Duration(milliseconds: 50),
}) async {
  final deadline = DateTime.now().add(timeout);
  while (DateTime.now().isBefore(deadline)) {
    if (predicate()) return;
    await Future.delayed(poll);
  }
  throw TimeoutException('Condition not met within ${timeout.inMilliseconds}ms');
}

class TimeoutException implements Exception {
  final String message;
  TimeoutException(this.message);
  @override
  String toString() => message;
}

void main() {
  late Directory tempDir;
  late PlaybackController pbc;
  late Song song1;
  late Song song2;
  late Song song3;
  final Map<String, String> urlMap = {};
  final Set<String> failingIds = {};

  setUpAll(() async {
    MediaKit.ensureInitialized();
    tempDir = await Directory.systemTemp.createTemp('lofiq_desktop_gapless_e2e_');
    final f1 = await _writeTestWavFile(tempDir, 'track1', durationSec: 9, freqHz: 220);
    final f2 = await _writeTestWavFile(tempDir, 'track2', durationSec: 9, freqHz: 330);
    final f3 = await _writeTestWavFile(tempDir, 'track3', durationSec: 9, freqHz: 440);

    song1 = Song(
      id: 'wav_1',
      title: 'Lofi Sine 1',
      artist: 'Test Synth',
      album: 'Suite',
      durationSeconds: 9,
      artworkUrl: '',
      highResArtworkUrl: '',
      sourceUrl: f1.path,
      providerId: 'local',
      quality: AudioQuality.high,
    );
    song2 = Song(
      id: 'wav_2',
      title: 'Lofi Sine 2',
      artist: 'Test Synth',
      album: 'Suite',
      durationSeconds: 9,
      artworkUrl: '',
      highResArtworkUrl: '',
      sourceUrl: f2.path,
      providerId: 'local',
      quality: AudioQuality.high,
    );
    song3 = Song(
      id: 'wav_3',
      title: 'Lofi Sine 3',
      artist: 'Test Synth',
      album: 'Suite',
      durationSeconds: 9,
      artworkUrl: '',
      highResArtworkUrl: '',
      sourceUrl: f3.path,
      providerId: 'local',
      quality: AudioQuality.high,
    );

    urlMap['wav_1'] = f1.path;
    urlMap['wav_2'] = f2.path;
    urlMap['wav_3'] = f3.path;

    pbc = PlaybackController.instance;
    pbc.streamResolverOverride = (song) async {
      if (failingIds.contains(song.id)) {
        throw Exception('Simulated resolver failure for ${song.id}');
      }
      return urlMap[song.id];
    };
    await pbc.init();
    pbc.setVolume(0.15); // quiet test output
  });

  tearDownAll(() async {
    pbc.clearQueue();
    pbc.streamResolverOverride = null;
    await tempDir.delete(recursive: true);
  });

  setUp(() {
    failingIds.clear();
    pbc.clearQueue();
    PreferencesManager.instance.updateSettings(
      (s) => s.copyWith(crossfadeDurationSec: 3, audioQuality: AudioQuality.high),
    );
  });

  test('Dual-player real libmpv crossfade prepares silently, overlaps concurrently, and swaps slots repeatedly A->B->A', () async {
    // Track duration 9s, crossfade 3s -> preload ~2.25s, crossfade starts at ~6s
    final initialActiveA = pbc.isPlayerAActive;
    await pbc.playSong(song1, [song1, song2, song3]);

    await _waitUntil(() => pbc.state.isPlaying && pbc.state.currentPositionMs > 500);
    expect(pbc.isPreloaded, isFalse);

    // Wait for early preload into secondary slot
    await _waitUntil(() => pbc.isPreloaded && pbc.isSecondaryReady, timeout: const Duration(seconds: 6));
    expect(pbc.preloadedSong?.id, 'wav_2');
    expect(pbc.state.isCrossfading, isFalse);

    // Apply EQ filter mid-flight and confirm both slots tolerate live DSP injection
    EqualizerController.instance.applyPreset('Warm');
    pbc.applyAudioFilter(EqualizerController.instance.buildMpvAudioFilter());

    // Wait for overlap crossfade to start
    await _waitUntil(() => pbc.state.isCrossfading, timeout: const Duration(seconds: 6));
    expect(pbc.state.currentSong?.id, 'wav_1');

    // Wait for first atomic handover A -> B
    await _waitUntil(() => !pbc.state.isCrossfading && pbc.state.currentSong?.id == 'wav_2', timeout: const Duration(seconds: 6));
    expect(pbc.isPlayerAActive, isNot(initialActiveA));
    expect(pbc.state.queueIndex, 1);
    expect(pbc.state.currentPositionMs, greaterThan(500)); // Real incoming playback elapsed during crossfade

    // Wait for second consecutive preload & handover B -> A for Track 3
    await _waitUntil(() => pbc.isPreloaded && pbc.isSecondaryReady && pbc.preloadedSong?.id == 'wav_3', timeout: const Duration(seconds: 6));
    await _waitUntil(() => !pbc.state.isCrossfading && pbc.state.currentSong?.id == 'wav_3', timeout: const Duration(seconds: 8));
    expect(pbc.isPlayerAActive, initialActiveA);
    expect(pbc.state.queueIndex, 2);
  });

  test('Crossfade 0s enables early gapless preload and seamless zero-overlap cut at track end', () async {
    PreferencesManager.instance.updateSettings((s) => s.copyWith(crossfadeDurationSec: 0));
    final initialActiveA = pbc.isPlayerAActive;

    await pbc.playSong(song1, [song1, song2]);
    await _waitUntil(() => pbc.isPreloaded && pbc.isSecondaryReady, timeout: const Duration(seconds: 6));
    expect(pbc.state.isCrossfading, isFalse);
    expect(pbc.preloadedSong?.id, 'wav_2');

    // Seek near tail (7.8s of 9s) and wait for gapless zero-overlap promotion
    pbc.seekTo(7800);
    await _waitUntil(() => pbc.state.currentSong?.id == 'wav_2', timeout: const Duration(seconds: 5));
    expect(pbc.state.isCrossfading, isFalse);
    expect(pbc.isPlayerAActive, isNot(initialActiveA));
    expect(pbc.state.queueIndex, 1);
  });

  test('Failed preload isolates secondary slot error without stopping primary track', () async {
    failingIds.add('wav_2');
    await pbc.playSong(song1, [song1, song2]);

    await _waitUntil(() => pbc.state.isPlaying && pbc.state.currentPositionMs > 3500, timeout: const Duration(seconds: 6));
    expect(pbc.state.currentSong?.id, 'wav_1');
    expect(pbc.isPreloaded, isFalse);
    expect(pbc.state.errorMessage, isNull);
    expect(pbc.state.isPlaying, isTrue);
  });

  test('Pause/resume mid-crossfade, manual skip promotion, and seek cancellation behave cleanly', () async {
    await pbc.playSong(song1, [song1, song2, song3]);
    await _waitUntil(() => pbc.isPreloaded && pbc.isSecondaryReady, timeout: const Duration(seconds: 6));

    // Seek to crossfade start window and wait for overlap
    pbc.seekTo(6100);
    await _waitUntil(() => pbc.state.isCrossfading, timeout: const Duration(seconds: 3));

    // Pause mid-crossfade
    pbc.togglePlayPause();
    expect(pbc.state.isPlaying, isFalse);
    expect(pbc.state.isCrossfading, isTrue);

    // Resume mid-crossfade
    pbc.togglePlayPause();
    expect(pbc.state.isPlaying, isTrue);

    // Manual skip immediately promotes prepared secondary
    pbc.next();
    expect(pbc.state.isCrossfading, isFalse);
    expect(pbc.state.currentSong?.id, 'wav_2');

    // Repeat ONE invalidates secondary preload cleanly
    pbc.cycleRepeatMode(); // ALL
    expect(pbc.state.repeatMode, PlaybackRepeatMode.all);
    pbc.cycleRepeatMode(); // ONE
    expect(pbc.state.repeatMode, PlaybackRepeatMode.one);
    expect(pbc.isPreloaded, isFalse);
  });
}


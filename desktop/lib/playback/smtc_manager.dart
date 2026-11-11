import 'dart:async';
import 'package:smtc_windows/smtc_windows.dart';
import '../core/models/player_state.dart';
import '../core/models/song.dart';
import '../core/utils/logger.dart';
import 'playback_controller.dart';

class SMTCManager {
  static final SMTCManager instance = SMTCManager._();
  factory SMTCManager() => instance;

  SMTCManager._();

  SMTCWindows? _smtc;
  StreamSubscription<PressedButton>? _buttonSub;
  StreamSubscription<bool>? _shuffleSub;
  StreamSubscription<RepeatMode>? _repeatSub;
  bool _isInitialized = false;

  Song? _lastSong;
  bool? _lastIsPlaying;

  Future<void> init() async {
    if (_isInitialized) return;

    try {
      await SMTCWindows.initialize();
      _smtc = SMTCWindows(
        config: const SMTCConfig(
          playEnabled: true,
          pauseEnabled: true,
          nextEnabled: true,
          prevEnabled: true,
          stopEnabled: true,
          fastForwardEnabled: false,
          rewindEnabled: false,
        ),
        enabled: true,
      );

      _buttonSub = _smtc!.buttonPressStream.listen(_onButtonPressed);
      _shuffleSub = _smtc!.shuffleChangeStream.listen((enabled) {
        PlaybackController.instance.toggleShuffle();
      });
      _repeatSub = _smtc!.repeatModeChangeStream.listen((mode) {
        PlaybackController.instance.cycleRepeatMode();
      });

      _isInitialized = true;
      LofiQLogger.i(LogCategory.playback, 'Windows SMTC initialized successfully with hardware media key listeners.');

      // Bind listener to PlaybackController
      PlaybackController.instance.addListener(_onPlaybackStateChanged);
    } catch (e) {
      LofiQLogger.w(LogCategory.playback, 'Windows SMTC initialization skipped/failed: $e');
    }
  }

  void _onButtonPressed(PressedButton button) {
    LofiQLogger.d(LogCategory.playback, 'SMTC Media Key Pressed: $button');
    switch (button) {
      case PressedButton.play:
      case PressedButton.pause:
        PlaybackController.instance.togglePlayPause();
        break;
      case PressedButton.stop:
        PlaybackController.instance.stop();
        break;
      case PressedButton.next:
        PlaybackController.instance.next();
        break;
      case PressedButton.previous:
        PlaybackController.instance.previous();
        break;
      default:
        break;
    }
  }

  void _onPlaybackStateChanged() {
    if (_smtc == null || !_isInitialized) return;

    final state = PlaybackController.instance.state;

    // 1. Metadata synchronization
    if (state.currentSong != _lastSong) {
      _lastSong = state.currentSong;
      if (state.currentSong != null) {
        final song = state.currentSong!;
        _smtc!.updateMetadata(
          MusicMetadata(
            title: song.title,
            artist: song.artist,
            album: song.album.isNotEmpty ? song.album : 'LofiQ Music',
            albumArtist: song.artist,
            thumbnail: song.artworkUrl.isNotEmpty ? song.artworkUrl : null,
          ),
        );
      } else {
        _smtc!.clearMetadata();
      }
    }

    // 2. Playback Status synchronization
    if (state.isPlaying != _lastIsPlaying) {
      _lastIsPlaying = state.isPlaying;
      if (state.currentSong == null) {
        _smtc!.setPlaybackStatus(PlaybackStatus.stopped);
      } else if (state.isPlaying) {
        _smtc!.setPlaybackStatus(PlaybackStatus.playing);
      } else {
        _smtc!.setPlaybackStatus(PlaybackStatus.paused);
      }
    }

    // 3. Timeline update (throttled to avoid IPC saturation)
    if (state.durationMs > 0) {
      _smtc!.updateTimeline(
        PlaybackTimeline(
          positionMs: state.currentPositionMs,
          startTimeMs: 0,
          endTimeMs: state.durationMs,
        ),
      );
    }

    // 4. Shuffle & Repeat synchronization
    _smtc!.setShuffleEnabled(state.shuffleEnabled);
    _smtc!.setRepeatMode(switch (state.repeatMode) {
      PlaybackRepeatMode.off => RepeatMode.none,
      PlaybackRepeatMode.all => RepeatMode.list,
      PlaybackRepeatMode.one => RepeatMode.track,
    });
  }

  Future<void> dispose() async {
    PlaybackController.instance.removeListener(_onPlaybackStateChanged);
    await _buttonSub?.cancel();
    await _shuffleSub?.cancel();
    await _repeatSub?.cancel();
    await _smtc?.dispose();
    _smtc = null;
    _isInitialized = false;
  }
}

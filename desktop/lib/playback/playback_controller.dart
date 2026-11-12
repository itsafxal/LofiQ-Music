import 'dart:async';
import 'dart:math' as math;
import 'package:flutter/foundation.dart';
import 'package:media_kit/media_kit.dart';
import '../core/database/local_storage.dart';
import '../core/models/audio_quality.dart';
import '../core/models/player_state.dart';
import '../core/models/song.dart';
import '../core/preferences/preferences_manager.dart';
import '../core/utils/logger.dart';
import '../data/resolvers/resolver_manager.dart';
import '../data/scrobbling/lastfm_scrobbler.dart';
import 'equalizer/equalizer_controller.dart';

class PlaybackController extends ChangeNotifier {
  static final PlaybackController instance = PlaybackController._();
  factory PlaybackController() => instance;

  PlaybackController._();

  static const int preloadTargetMs = 10000;
  static const int gaplessCutThresholdMs = 180;

  /// Effective overlap window in ms (`0` when disabled or `< 1500ms` guard).
  static int computeEffectiveCrossfadeMs(int crossfadeSec, int durationMs) {
    if (crossfadeSec <= 0 || durationMs <= 0) return 0;
    final capped = math.min(crossfadeSec * 1000, durationMs ~/ 2);
    return capped >= 1500 ? capped : 0;
  }

  /// Target active playback position (ms) to start silent secondary preloading.
  /// Defaults to ~10 seconds into playback and scales smoothly for shorter tracks.
  static int computePreloadTriggerMs(int durationMs, int effectiveCrossfadeMs) {
    if (durationMs <= 0) return preloadTargetMs;
    final proportionalTarget = math.min(
      preloadTargetMs,
      math.max(2000, (durationMs * 0.25).round()),
    );
    if (effectiveCrossfadeMs > 0) {
      if (durationMs > effectiveCrossfadeMs + 1500) {
        return math.max(
          0,
          math.min(proportionalTarget, durationMs - effectiveCrossfadeMs - 1500),
        );
      }
      return math.min(proportionalTarget, durationMs ~/ 3);
    }
    if (durationMs > 4000) {
      return math.max(0, math.min(proportionalTarget, durationMs - 2000));
    }
    return durationMs ~/ 3;
  }

  static double equalPowerOutGain(double t) =>
      math.cos(t.clamp(0.0, 1.0) * math.pi / 2);

  static double equalPowerInGain(double t) =>
      math.sin(t.clamp(0.0, 1.0) * math.pi / 2);

  LofiQPlayerState _state = const LofiQPlayerState();
  LofiQPlayerState get state => _state;

  int get currentIndex => _state.queueIndex;
  int get positionMs => _state.currentPositionMs;

  // Dual Player Architecture: Slot A and Slot B
  Player? _playerA;
  Player? _playerB;
  bool _isPlayerAActive = true;

  Player? get _activePlayer => _isPlayerAActive ? _playerA : _playerB;
  Player? get _secondaryPlayer => _isPlayerAActive ? _playerB : _playerA;

  // Subscriptions for Slot A & Slot B
  final List<StreamSubscription<dynamic>> _playerSubs = [];

  // Fallback ticker for unit tests without native libmpv
  Timer? _positionTicker;
  bool _hasScrobbledCurrent = false;

  // Preferences tracking
  AudioQuality? _lastAudioQuality;
  int? _lastCrossfadeSec;
  bool _preferencesListenerAttached = false;
  bool _eqListenerAttached = false;

  // Dual-player Preload & Crossfade State
  Timer? _crossfadeRampTimer;
  int _preloadEpoch = 0;
  bool _isPreloading = false;
  bool _isPreloaded = false;
  bool _isSecondaryReady = false;
  bool _secondaryBuffering = false;
  int _secondaryDurationMs = 0;
  int _secondaryBufferMs = 0;
  String? _inFlightPreloadSongId;
  final Set<String> _failedPreloadIds = {};
  Song? _preloadedSong;
  String? _preloadedStreamUrl;
  String? _transitionTargetSongId;
  double _currentCrossfadeT = 0.0;
  bool _isTransitionPaused = false;
  bool _userPaused = false;
  String? _lastWaitLogSongId;

  @visibleForTesting
  Future<String?> Function(Song song)? streamResolverOverride;

  bool get isPreloaded => _isPreloaded;
  bool get isPreloading => _isPreloading;
  bool get isSecondaryReady => _isSecondaryReady;
  bool get isPlayerAActive => _isPlayerAActive;
  Song? get preloadedSong => _preloadedSong;
  String? get preloadedStreamUrl => _preloadedStreamUrl;

  Future<void> init() async {
    // 1. Initialize Dual native media_kit Players (Slot A and Slot B)
    try {
      await _disposePlayersOnly();
      _playerA = Player();
      _playerB = Player();
      _configureMpvPlayer(_playerA!);
      _configureMpvPlayer(_playerB!);

      _setupPlayerSubscriptions(_playerA!, isSlotA: true);
      _setupPlayerSubscriptions(_playerB!, isSlotA: false);

      LofiQLogger.i(
        LogCategory.playback,
        '[GAPLESS] Dual media_kit audio players initialized for gapless & equal-power crossfade.',
      );
    } catch (e) {
      LofiQLogger.w(
        LogCategory.playback,
        '[PLAYBACK_RECOVERY] Native audio player unavailable; falling back to state simulation: $e',
      );
    }

    // 2. Wire Equalizer DSP listener to libmpv pipeline across both players
    if (!_eqListenerAttached) {
      EqualizerController.instance.addListener(_onEqualizerChanged);
      _eqListenerAttached = true;
    }

    // 3. Observe PreferencesManager updates (dynamic quality & crossfade)
    final initialSettings = PreferencesManager.instance.settings;
    _lastAudioQuality = initialSettings.audioQuality;
    _lastCrossfadeSec = initialSettings.crossfadeDurationSec;
    if (!_preferencesListenerAttached) {
      PreferencesManager.instance.addListener(_onPreferencesChanged);
      _preferencesListenerAttached = true;
    }

    // 4. Restore queue snapshot if present
    final snapshot = LocalStorage.instance.getQueueSnapshot();
    if (snapshot != null) {
      try {
        final queueList = (snapshot['queue'] as List<dynamic>?)
            ?.map((e) => Song.fromJson(e as Map<String, dynamic>))
            .toList() ?? [];
        final currentIndex = (snapshot['currentIndex'] as num?)?.toInt() ?? -1;
        final seekMs = (snapshot['seekPositionMs'] as num?)?.toInt() ?? 0;

        if (queueList.isNotEmpty && currentIndex >= 0 && currentIndex < queueList.length) {
          final restoredSong = queueList[currentIndex];
          _state = _state.copyWith(
            queue: queueList,
            queueIndex: currentIndex,
            currentSong: restoredSong,
            durationMs: restoredSong.durationMs,
            currentPositionMs: seekMs,
          );
          LofiQLogger.i(LogCategory.playback, 'Restored session queue: ${restoredSong.title}');
          notifyListeners();
        }
      } catch (e) {
        LofiQLogger.w(LogCategory.playback, 'Error restoring queue snapshot: $e');
      }
    }
  }

  void _onEqualizerChanged() {
    applyAudioFilter(EqualizerController.instance.buildMpvAudioFilter());
  }

  void _onPreferencesChanged() {
    final settings = PreferencesManager.instance.settings;
    if (_lastCrossfadeSec != settings.crossfadeDurationSec) {
      _lastCrossfadeSec = settings.crossfadeDurationSec;
      LofiQLogger.i(
        LogCategory.playback,
        '[CROSSFADE] Crossfade duration updated to ${settings.crossfadeDurationSec}s',
      );
    }
    if (_lastAudioQuality != settings.audioQuality) {
      _lastAudioQuality = settings.audioQuality;
      _failedPreloadIds.clear();
      if (_state.isCrossfading) {
        _cancelCrossfade(restoreVolume: true);
      } else {
        _invalidateSecondaryPreload('audioQualityChanged');
      }
      _reloadCurrentTrackOnQualityChange();
    }
  }

  Future<void> _reloadCurrentTrackOnQualityChange() async {
    final current = _state.currentSong;
    if (current == null || current.providerId == 'local' || _activePlayer == null) {
      return;
    }
    final savedPosMs = _state.currentPositionMs;
    final wasPlaying = _state.isPlaying;
    final streamUrl = await _resolveStreamUrl(current);
    if (streamUrl == null || streamUrl.isEmpty || _state.currentSong?.id != current.id) {
      return;
    }
    try {
      await _activePlayer!.open(Media(streamUrl), play: false);
      _applyAudioFilterToPlayer(_activePlayer, EqualizerController.instance.buildMpvAudioFilter());
      await _activePlayer!.setVolume(_state.volume * 100.0);
      if (savedPosMs > 0) {
        await _activePlayer!.seek(Duration(milliseconds: savedPosMs));
      }
      if (wasPlaying) {
        await _activePlayer!.play();
      }
      LofiQLogger.i(
        LogCategory.playback,
        '[PLAYBACK_RECOVERY] Switched active stream quality at ${savedPosMs}ms for "${current.title}"',
      );
      if (_state.durationMs > 0) {
        _checkCrossfadeTriggers(savedPosMs);
      }
    } catch (e) {
      LofiQLogger.w(LogCategory.playback, '[PLAYBACK_RECOVERY] Quality reload failed: $e');
    }
  }

  void _configureMpvPlayer(Player player) {
    try {
      final p = player.platform as dynamic;
      p.setProperty('cache', 'yes');
      p.setProperty('demuxer-max-bytes', '16777216');
      p.setProperty('demuxer-readahead-secs', '25');
      p.setProperty('audio-display', 'no');
      p.setProperty('gapless-audio', 'yes');
    } catch (_) {
      // Safe fallback on platforms/mocks without mpv property setter
    }
  }

  void _setupPlayerSubscriptions(Player player, {required bool isSlotA}) {
    bool isActiveSlot() => isSlotA == _isPlayerAActive;

    _playerSubs.add(
      player.stream.position.listen((pos) {
        if (isActiveSlot()) {
          _handleActivePositionUpdate(pos.inMilliseconds);
        }
      }),
    );

    _playerSubs.add(
      player.stream.duration.listen((dur) {
        if (dur <= Duration.zero) return;
        if (isActiveSlot()) {
          _state = _state.copyWith(durationMs: dur.inMilliseconds);
          notifyListeners();
        } else if (_isPreloaded && _preloadedSong != null) {
          _secondaryDurationMs = dur.inMilliseconds;
          _evaluateSecondaryReadiness(player);
        }
      }),
    );

    _playerSubs.add(
      player.stream.buffer.listen((buf) {
        if (!isActiveSlot() && _isPreloaded) {
          _secondaryBufferMs = buf.inMilliseconds;
          _evaluateSecondaryReadiness(player);
        }
      }),
    );

    _playerSubs.add(
      player.stream.playing.listen((playing) {
        if (isActiveSlot()) {
          if (!_state.isCrossfading) {
            _state = _state.copyWith(isPlaying: playing);
            notifyListeners();
          }
        } else if (playing && !_state.isCrossfading) {
          // Guard: secondary slot must remain silent and paused until transition starts
          LofiQLogger.w(
            LogCategory.playback,
            '[SECONDARY_PLAYER] Guard triggered: muting & pausing premature secondary playback',
          );
          player.setVolume(0.0);
          player.pause();
        }
      }),
    );

    _playerSubs.add(
      player.stream.buffering.listen((buffering) {
        if (isActiveSlot()) {
          _state = _state.copyWith(isLoading: buffering);
          notifyListeners();
        } else if (_isPreloaded) {
          _secondaryBuffering = buffering;
          _evaluateSecondaryReadiness(player);
        }
      }),
    );

    _playerSubs.add(
      player.stream.completed.listen((completed) {
        if (isActiveSlot() && completed && !_userPaused && !_state.isCrossfading && _state.currentSong != null) {
          _handleActiveTrackCompleted();
        }
      }),
    );

    _playerSubs.add(
      player.stream.error.listen((err) {
        if (isActiveSlot()) {
          _handleActivePlayerError(err);
        } else {
          _handleSecondaryPlayerError(err);
        }
      }),
    );
  }

  void _evaluateSecondaryReadiness(Player secondary) {
    if (!_isPreloaded || _preloadedSong == null) return;
    final bool hasMediaOrBuffer =
        _secondaryDurationMs > 0 ||
        secondary.state.duration > Duration.zero ||
        _secondaryBufferMs > 0 ||
        secondary.state.buffer > Duration.zero;
    final bool readyNow = !_secondaryBuffering && hasMediaOrBuffer;
    if (readyNow && !_isSecondaryReady) {
      _isSecondaryReady = true;
      LofiQLogger.i(
        LogCategory.playback,
        '[SECONDARY_PLAYER] READY: "${_preloadedSong?.title}" (dur=${_secondaryDurationMs}ms, buf=${_secondaryBufferMs}ms, vol=0%)',
      );
      notifyListeners();
    }
  }

  void _handleSecondaryPlayerError(String err) {
    final failedId = _preloadedSong?.id ?? _inFlightPreloadSongId;
    final failedTitle = _preloadedSong?.title ?? 'unknown';
    LofiQLogger.w(
      LogCategory.playback,
      '[PLAYBACK_RECOVERY] [SECONDARY_PLAYER] Isolated preload error for "$failedTitle": $err. Primary track undisturbed.',
    );
    if (failedId != null) {
      _failedPreloadIds.add(failedId);
    }
    if (_state.isCrossfading && _transitionTargetSongId == failedId) {
      _cancelCrossfade(restoreVolume: true);
    } else {
      _invalidateSecondaryPreload('secondaryError');
    }
  }

  void _handleActiveTrackCompleted() {
    if (_state.repeatMode == PlaybackRepeatMode.one) {
      if (_activePlayer != null) {
        _activePlayer!.seek(Duration.zero);
        _activePlayer!.play();
        _state = _state.copyWith(currentPositionMs: 0);
        notifyListeners();
      }
      return;
    }

    final nextSong = _peekNextSong();
    if (nextSong != null &&
        _isPreloaded &&
        _isSecondaryReady &&
        _preloadedSong?.id == nextSong.id &&
        _secondaryPlayer != null) {
      _performGaplessCut(nextSong);
      return;
    }

    next();
  }

  void _handleActivePlayerError(String err) {
    LofiQLogger.e(LogCategory.playback, '[PLAYBACK_RECOVERY] libmpv active decode error: $err');
    _state = _state.copyWith(
      isLoading: false,
      isPlaying: false,
      errorMessage: 'Audio decode error: $err',
    );
    notifyListeners();
  }

  void _handleActivePositionUpdate(int posMs) {
    _state = _state.copyWith(currentPositionMs: posMs);
    _checkScrobbleThreshold(posMs);
    _checkCrossfadeTriggers(posMs);
    notifyListeners();
  }

  void _checkCrossfadeTriggers(int posMs) {
    final durationMs = _state.durationMs;
    if (durationMs <= 0 || !_state.isPlaying) return;

    final nextSong = _peekNextSong();
    if (nextSong == null || _state.repeatMode == PlaybackRepeatMode.one) return;

    final settings = PreferencesManager.instance.settings;
    final effectiveWindowMs = computeEffectiveCrossfadeMs(
      settings.crossfadeDurationSec,
      durationMs,
    );
    final preloadTriggerMs = computePreloadTriggerMs(durationMs, effectiveWindowMs);

    // Stage 1: Early Preload at ~10s into active track (crossfade & gapless)
    if (posMs >= preloadTriggerMs &&
        !_isPreloaded &&
        !_isPreloading &&
        !_state.isCrossfading &&
        !_failedPreloadIds.contains(nextSong.id)) {
      LofiQLogger.i(
        LogCategory.playback,
        '[PRELOAD] Reached ${posMs}ms (trigger=${preloadTriggerMs}ms) -> preparing "${nextSong.title}" on secondary slot',
      );
      _preloadNextSong(nextSong);
    }

    // Stage 2: Crossfade or Zero-Overlap Gapless Transition
    final remainingMs = math.max(0, durationMs - posMs);
    if (effectiveWindowMs > 0) {
      if (remainingMs <= effectiveWindowMs && !_state.isCrossfading && posMs >= 2000) {
        if (_isPreloaded && _isSecondaryReady && _preloadedSong?.id == nextSong.id) {
          _startCrossfade(effectiveWindowMs, nextSong);
        } else if (_lastWaitLogSongId != nextSong.id) {
          _lastWaitLogSongId = nextSong.id;
          LofiQLogger.w(
            LogCategory.playback,
            '[CROSSFADE] Waiting for incoming "${nextSong.title}" readiness (preloaded=$_isPreloaded, ready=$_isSecondaryReady, rem=${remainingMs}ms) without interrupting active playback',
          );
        }
      }
    } else if (remainingMs <= gaplessCutThresholdMs &&
        !_state.isCrossfading &&
        posMs >= 2000 &&
        _isPreloaded &&
        _isSecondaryReady &&
        _preloadedSong?.id == nextSong.id) {
      _performGaplessCut(nextSong);
    }
  }

  int _peekNextIndex() {
    if (_state.queue.isEmpty) return -1;
    if (_state.repeatMode == PlaybackRepeatMode.one) {
      return _state.queueIndex;
    }
    final nextIdx = _state.queueIndex + 1;
    if (nextIdx < _state.queue.length) {
      return nextIdx;
    }
    if (_state.repeatMode == PlaybackRepeatMode.all) {
      return 0;
    }
    return -1;
  }

  Song? _peekNextSong() {
    final idx = _peekNextIndex();
    if (idx >= 0 && idx < _state.queue.length) {
      return _state.queue[idx];
    }
    return null;
  }

  Future<String?> _resolveStreamUrl(Song song) {
    final override = streamResolverOverride;
    if (override != null) {
      return override(song);
    }
    return ResolverManager.instance.resolveStreamUrl(song);
  }

  void _invalidateSecondaryPreload(String reason) {
    _preloadEpoch++;
    if (_isPreloaded || _isPreloading) {
      LofiQLogger.i(LogCategory.playback, '[PRELOAD] Invalidating secondary preload ($reason)');
    }
    _isPreloading = false;
    _isPreloaded = false;
    _isSecondaryReady = false;
    _secondaryBuffering = false;
    _secondaryDurationMs = 0;
    _secondaryBufferMs = 0;
    _inFlightPreloadSongId = null;
    _preloadedSong = null;
    _preloadedStreamUrl = null;
    _lastWaitLogSongId = null;
    try {
      _secondaryPlayer?.setVolume(0.0);
      _secondaryPlayer?.stop();
    } catch (_) {}
  }

  Future<void> _preloadNextSong(Song nextSong) async {
    if (_isPreloading || _secondaryPlayer == null) return;
    if (_isPreloaded && _preloadedSong?.id == nextSong.id) return;

    final int targetEpoch = ++_preloadEpoch;
    _isPreloading = true;
    _isSecondaryReady = false;
    _secondaryBuffering = true;
    _secondaryDurationMs = 0;
    _secondaryBufferMs = 0;
    _inFlightPreloadSongId = nextSong.id;

    try {
      final streamUrl = await _resolveStreamUrl(nextSong);
      if (targetEpoch != _preloadEpoch || _peekNextSong()?.id != nextSong.id || _secondaryPlayer == null) {
        return;
      }
      if (streamUrl == null || streamUrl.isEmpty) {
        _failedPreloadIds.add(nextSong.id);
        LofiQLogger.w(
          LogCategory.playback,
          '[PLAYBACK_RECOVERY] [PRELOAD] Empty stream URL for "${nextSong.title}"; active playback unaffected.',
        );
        return;
      }

      final sec = _secondaryPlayer!;
      _preloadedSong = nextSong;
      _preloadedStreamUrl = streamUrl;
      _isPreloaded = true;

      _applyAudioFilterToPlayer(sec, EqualizerController.instance.buildMpvAudioFilter());
      await sec.setVolume(0.0);
      await sec.open(Media(streamUrl), play: false);

      if (targetEpoch != _preloadEpoch || _peekNextSong()?.id != nextSong.id) {
        _invalidateSecondaryPreload('epochSupersededAfterOpen');
        return;
      }

      // Immediately seek to 0 and check synchronous state in case stream already emitted before tick
      await sec.seek(Duration.zero);
      if (sec.state.duration > Duration.zero) {
        _secondaryDurationMs = sec.state.duration.inMilliseconds;
      }
      if (sec.state.buffer > Duration.zero) {
        _secondaryBufferMs = sec.state.buffer.inMilliseconds;
      }
      _secondaryBuffering = sec.state.buffering;
      _evaluateSecondaryReadiness(sec);

      // Even if duration event arrives async, open(play:false) succeeded cleanly without error;
      // verify shortly after in case local/WAV stream sets duration synchronously slightly late.
      if (!_isSecondaryReady) {
        Future.delayed(const Duration(milliseconds: 250), () {
          if (targetEpoch == _preloadEpoch && _isPreloaded && !_isSecondaryReady && _secondaryPlayer != null) {
            if (_secondaryPlayer!.state.duration > Duration.zero) {
              _secondaryDurationMs = _secondaryPlayer!.state.duration.inMilliseconds;
            }
            if (_secondaryPlayer!.state.buffer > Duration.zero) {
              _secondaryBufferMs = _secondaryPlayer!.state.buffer.inMilliseconds;
            }
            _secondaryBuffering = _secondaryPlayer!.state.buffering;
            _evaluateSecondaryReadiness(_secondaryPlayer!);
          }
        });
      }

      LofiQLogger.i(
        LogCategory.playback,
        '[SECONDARY_PLAYER] Opened "${nextSong.title}" silently on slot ${_isPlayerAActive ? "B" : "A"} (vol=0%, play=false)',
      );
    } catch (e) {
      if (targetEpoch == _preloadEpoch) {
        _failedPreloadIds.add(nextSong.id);
        _invalidateSecondaryPreload('preloadException');
      }
      LofiQLogger.w(
        LogCategory.playback,
        '[PLAYBACK_RECOVERY] [PRELOAD] Isolated failure for "${nextSong.title}": $e',
      );
    } finally {
      if (targetEpoch == _preloadEpoch) {
        _isPreloading = false;
        _inFlightPreloadSongId = null;
      }
    }
  }

  void _startCrossfade(int durationMs, Song targetSong) {
    if (_state.isCrossfading || !_isPreloaded || !_isSecondaryReady || _secondaryPlayer == null) {
      return;
    }

    _transitionTargetSongId = targetSong.id;
    _isTransitionPaused = false;
    _currentCrossfadeT = 0.0;
    _state = _state.copyWith(isCrossfading: true);
    notifyListeners();

    LofiQLogger.i(
      LogCategory.playback,
      '[CROSSFADE] Starting equal-power dual-player overlap -> in="${targetSong.title}", window=${durationMs}ms, outPos=${_state.currentPositionMs}ms',
    );

    final outgoing = _activePlayer;
    final incoming = _secondaryPlayer!;
    incoming.setVolume(0.0);
    incoming.play();

    final Stopwatch tickClock = Stopwatch()..start();
    int elapsedMs = 0;

    _crossfadeRampTimer?.cancel();
    _crossfadeRampTimer = Timer.periodic(const Duration(milliseconds: 40), (timer) {
      final int deltaMs = tickClock.elapsedMilliseconds;
      tickClock.reset();

      if (!_state.isCrossfading) {
        timer.cancel();
        return;
      }
      if (!_state.isPlaying || _isTransitionPaused) {
        return; // Freeze progression cleanly when paused
      }

      elapsedMs += deltaMs;
      final t = (elapsedMs / durationMs).clamp(0.0, 1.0);
      _currentCrossfadeT = t;

      final factorA = equalPowerOutGain(t);
      final factorB = equalPowerInGain(t);
      final masterVol = _state.volume;

      outgoing?.setVolume(factorA * masterVol * 100.0);
      incoming.setVolume(factorB * masterVol * 100.0);

      if (t >= 1.0) {
        timer.cancel();
        _crossfadeRampTimer = null;
        _completeCrossfadeHandover(isGaplessCut: false);
      }
    });
  }

  void _performGaplessCut(Song targetSong) {
    if (_state.isCrossfading || !_isPreloaded || _secondaryPlayer == null) return;
    _transitionTargetSongId = targetSong.id;
    LofiQLogger.i(
      LogCategory.playback,
      '[GAPLESS] Zero-overlap handover -> in="${targetSong.title}" at pos=0ms',
    );
    final incoming = _secondaryPlayer!;
    incoming.seek(Duration.zero);
    incoming.setVolume(_state.volume * 100.0);
    incoming.play();
    _completeCrossfadeHandover(isGaplessCut: true);
  }

  void _completeCrossfadeHandover({bool isGaplessCut = false}) {
    final oldPrimary = _activePlayer;
    final newPrimary = _secondaryPlayer;
    final nextSong = _preloadedSong;

    if (newPrimary == null || nextSong == null) {
      _cancelCrossfade(restoreVolume: true);
      return;
    }

    final int nextIndex = _peekNextIndex();

    // 1. Atomic slot swap first so stopping oldPrimary emits no spurious active events
    _isPlayerAActive = !_isPlayerAActive;

    // 2. Ensure new primary owns full master volume and is actively playing
    newPrimary.setVolume(_state.volume * 100.0);
    if (!newPrimary.state.playing) {
      newPrimary.play();
    }

    // 3. Silence & stop old primary so it returns cleanly to idle secondary role
    try {
      oldPrimary?.setVolume(0.0);
      oldPrimary?.stop();
    } catch (_) {}

    // 4. Reset transition & preload state for the next track cycle
    _preloadEpoch++;
    _isPreloaded = false;
    _isPreloading = false;
    _isSecondaryReady = false;
    _secondaryBuffering = false;
    _secondaryDurationMs = 0;
    _secondaryBufferMs = 0;
    _inFlightPreloadSongId = null;
    _preloadedSong = null;
    _preloadedStreamUrl = null;
    _transitionTargetSongId = null;
    _isTransitionPaused = false;
    _currentCrossfadeT = 0.0;
    _lastWaitLogSongId = null;
    _failedPreloadIds.clear();

    // 5. Sync real incoming playback position & duration
    final int realPosMs = isGaplessCut ? 0 : newPrimary.state.position.inMilliseconds;
    final int realDurMs = newPrimary.state.duration > Duration.zero
        ? newPrimary.state.duration.inMilliseconds
        : nextSong.durationMs;

    _hasScrobbledCurrent = false;
    _state = _state.copyWith(
      currentSong: nextSong,
      queueIndex: nextIndex,
      isCrossfading: false,
      isPlaying: true,
      isLoading: newPrimary.state.buffering,
      currentPositionMs: realPosMs,
      durationMs: realDurMs,
    );
    notifyListeners();

    _triggerNowPlaying(nextSong);
    LocalStorage.instance.saveQueueSnapshot(
      queue: _state.queue,
      currentIndex: _state.queueIndex,
      seekPositionMs: realPosMs,
    );

    LofiQLogger.i(
      LogCategory.playback,
      '[PLAYER_HANDOVER] ${isGaplessCut ? "GAPLESS" : "CROSSFADE"} handover complete -> activeSlot=${_isPlayerAActive ? "A" : "B"}, track="${nextSong.title}", pos=${realPosMs}ms',
    );
  }

  void _cancelCrossfade({bool restoreVolume = true}) {
    _crossfadeRampTimer?.cancel();
    _crossfadeRampTimer = null;

    final hadState = _state.isCrossfading || _isPreloaded || _isPreloading;
    if (hadState) {
      _invalidateSecondaryPreload('cancelledTransition');
    }

    _transitionTargetSongId = null;
    _isTransitionPaused = false;
    _currentCrossfadeT = 0.0;

    if (restoreVolume) {
      _activePlayer?.setVolume(_state.volume * 100.0);
    }

    if (_state.isCrossfading) {
      _state = _state.copyWith(isCrossfading: false);
      notifyListeners();
    }
  }

  void _startTicker() {
    _positionTicker?.cancel();
    _positionTicker = Timer.periodic(const Duration(milliseconds: 250), (timer) {
      if (!_state.isPlaying) return;

      final newPos = _state.currentPositionMs + 250;
      if (_state.durationMs > 0 && newPos >= _state.durationMs) {
        next();
      } else {
        _state = _state.copyWith(currentPositionMs: newPos);
        _checkScrobbleThreshold(newPos);
        _checkCrossfadeTriggers(newPos);
        notifyListeners();
      }
    });
  }

  void _stopTicker() {
    _positionTicker?.cancel();
    _positionTicker = null;
  }

  void _checkScrobbleThreshold(int currentPosMs) {
    if (_hasScrobbledCurrent || _state.currentSong == null) return;
    final durationMs = _state.durationMs;
    if (durationMs <= 0) return;

    // Last.fm rule: Scrobble after 50% or 4 minutes of track played
    final threshold = math.min(durationMs ~/ 2, 240 * 1000);
    if (currentPosMs >= threshold) {
      _hasScrobbledCurrent = true;
      final song = _state.currentSong!;
      final settings = PreferencesManager.instance.settings;

      if (settings.scrobbleLastFmEnabled && (settings.lastFmSessionKey?.isNotEmpty ?? false)) {
        LastFmScrobbler.instance.scrobble(
          track: song,
          sessionKey: settings.lastFmSessionKey!,
        );
        LofiQLogger.i(LogCategory.scrobble, 'Scrobbled to Last.fm: ${song.title}');
      }

      // Record to local playback history
      LocalStorage.instance.recordHistory(song.id, currentPosMs ~/ 1000);
      LocalStorage.instance.upsertSong(song.copyWith(playCount: song.playCount + 1));
    }
  }

  Future<void> playSong(Song song, [List<Song>? queueContext]) async {
    _cancelCrossfade(restoreVolume: false);
    _preloadEpoch++;
    _failedPreloadIds.clear();
    _hasScrobbledCurrent = false;
    _userPaused = false;

    List<Song> currentQueue = queueContext ?? _state.queue;
    int index = currentQueue.indexOf(song);

    if (index == -1) {
      currentQueue = List<Song>.from(currentQueue)..add(song);
      index = currentQueue.length - 1;
    }

    _state = _state.copyWith(
      currentSong: song,
      queue: currentQueue,
      queueIndex: index,
      isLoading: true,
      isPlaying: false,
      currentPositionMs: 0,
      durationMs: song.durationMs,
      clearError: true,
    );
    notifyListeners();

    // Resolve audio stream URL via authoritative multi-provider cascade (or test override)
    final streamUrl = await _resolveStreamUrl(song);
    if (streamUrl == null || streamUrl.isEmpty) {
      _state = _state.copyWith(
        isLoading: false,
        isPlaying: false,
        errorMessage: 'Unable to stream "${song.title}". Please try another track.',
      );
      notifyListeners();
      return;
    }

    if (_activePlayer != null) {
      try {
        await _activePlayer!.setVolume(_state.volume * 100.0);
        await _activePlayer!.open(Media(streamUrl), play: true);
        applyAudioFilter(EqualizerController.instance.buildMpvAudioFilter());

        _state = _state.copyWith(
          isLoading: false,
          isPlaying: true,
          currentSong: song.copyWith(sourceUrl: streamUrl),
        );
        notifyListeners();
      } catch (e) {
        LofiQLogger.e(LogCategory.playback, '[PLAYBACK_RECOVERY] Error playing stream on native player: $e');
        _state = _state.copyWith(
          isLoading: false,
          isPlaying: false,
          errorMessage: 'Unable to stream "${song.title}": $e',
        );
        notifyListeners();
        return;
      }
    } else {
      _state = _state.copyWith(
        isLoading: false,
        isPlaying: true,
        currentSong: song.copyWith(sourceUrl: streamUrl),
      );
      _startTicker();
      notifyListeners();
    }

    // Trigger Last.fm Now Playing
    _triggerNowPlaying(song);

    // Save snapshot
    LocalStorage.instance.saveQueueSnapshot(
      queue: _state.queue,
      currentIndex: _state.queueIndex,
      seekPositionMs: 0,
    );
  }

  void _triggerNowPlaying(Song song) {
    final settings = PreferencesManager.instance.settings;
    if (settings.scrobbleLastFmEnabled && (settings.lastFmSessionKey?.isNotEmpty ?? false)) {
      LastFmScrobbler.instance.updateNowPlaying(
        track: song,
        sessionKey: settings.lastFmSessionKey!,
      );
    }
  }

  void togglePlayPause() {
    if (_state.currentSong == null) {
      if (_state.queue.isNotEmpty) {
        playSong(_state.queue.first);
      }
      return;
    }

    if (_activePlayer != null) {
      if (_state.isPlaying) {
        _userPaused = true;
        if (_state.isCrossfading) {
          _isTransitionPaused = true;
          _secondaryPlayer?.pause();
        }
        _activePlayer!.pause();
        _state = _state.copyWith(isPlaying: false);
      } else {
        _userPaused = false;
        _isTransitionPaused = false;
        _activePlayer!.play();
        if (_state.isCrossfading) {
          _secondaryPlayer?.play();
        }
        _state = _state.copyWith(isPlaying: true);
      }
      notifyListeners();
    } else {
      if (_state.isPlaying) {
        _userPaused = true;
        _isTransitionPaused = true;
        _state = _state.copyWith(isPlaying: false);
        _stopTicker();
      } else {
        _userPaused = false;
        _isTransitionPaused = false;
        _state = _state.copyWith(isPlaying: true);
        _startTicker();
      }
      notifyListeners();
    }

    LocalStorage.instance.saveQueueSnapshot(
      queue: _state.queue,
      currentIndex: _state.queueIndex,
      seekPositionMs: _state.currentPositionMs,
    );
  }

  void stop() {
    _userPaused = true;
    _cancelCrossfade(restoreVolume: false);
    _preloadEpoch++;
    _failedPreloadIds.clear();

    if (_playerA != null) {
      _playerA!.stop();
      _playerA!.setVolume(0.0);
    }
    if (_playerB != null) {
      _playerB!.stop();
      _playerB!.setVolume(0.0);
    }

    _stopTicker();
    _state = _state.copyWith(
      isPlaying: false,
      isCrossfading: false,
      currentPositionMs: 0,
    );
    notifyListeners();

    LocalStorage.instance.saveQueueSnapshot(
      queue: _state.queue,
      currentIndex: _state.queueIndex,
      seekPositionMs: 0,
    );
  }

  void seekTo(int positionMs) {
    if (_state.durationMs <= 0) return;
    final clamped = positionMs.clamp(0, _state.durationMs);

    // Manual seek during an active crossfade cancels the overlap cleanly and restores primary volume
    if (_state.isCrossfading) {
      _cancelCrossfade(restoreVolume: true);
    }

    _state = _state.copyWith(currentPositionMs: clamped);
    notifyListeners();

    if (_activePlayer != null) {
      _activePlayer!.seek(Duration(milliseconds: clamped));
    }

    LocalStorage.instance.saveQueueSnapshot(
      queue: _state.queue,
      currentIndex: _state.queueIndex,
      seekPositionMs: clamped,
    );
  }

  void next() {
    if (_state.queue.isEmpty) return;

    if (_state.repeatMode == PlaybackRepeatMode.one) {
      seekTo(0);
      return;
    }

    final expectedNext = _peekNextSong();
    // Promote ready secondary player seamlessly for both in-progress crossfade and ready gapless preload
    if (expectedNext != null &&
        _isPreloaded &&
        _isSecondaryReady &&
        _preloadedSong?.id == expectedNext.id &&
        _secondaryPlayer != null) {
      final wasCrossfading = _state.isCrossfading;
      _crossfadeRampTimer?.cancel();
      _crossfadeRampTimer = null;
      if (!wasCrossfading) {
        _secondaryPlayer!.seek(Duration.zero);
        _secondaryPlayer!.play();
      }
      _completeCrossfadeHandover(isGaplessCut: !wasCrossfading);
      return;
    }

    _cancelCrossfade(restoreVolume: false);

    final nextIndex = _state.queueIndex + 1;
    if (nextIndex < _state.queue.length) {
      playSong(_state.queue[nextIndex]);
    } else if (_state.repeatMode == PlaybackRepeatMode.all) {
      playSong(_state.queue.first);
    } else {
      stop();
    }
  }

  void previous() {
    if (_state.queue.isEmpty) return;

    _cancelCrossfade(restoreVolume: true);

    if (_state.currentPositionMs > 3000) {
      seekTo(0);
      return;
    }

    final prevIndex = _state.queueIndex - 1;
    if (prevIndex >= 0) {
      playSong(_state.queue[prevIndex]);
    } else {
      seekTo(0);
    }
  }

  void toggleShuffle() {
    final nextShuffle = !_state.shuffleEnabled;
    if (nextShuffle && _state.queue.isNotEmpty) {
      final current = _state.currentSong;
      final shuffled = List<Song>.from(_state.queue)..shuffle();
      if (current != null) {
        shuffled.remove(current);
        shuffled.insert(0, current);
      }
      _state = _state.copyWith(
        shuffleEnabled: true,
        queue: shuffled,
        queueIndex: 0,
      );
    } else {
      _state = _state.copyWith(shuffleEnabled: false);
    }
    if (_state.isCrossfading) {
      _cancelCrossfade(restoreVolume: true);
    } else {
      _checkPreloadValidity();
    }
    notifyListeners();
  }

  void shuffleQueue() {
    if (_state.queue.isEmpty) return;
    final current = _state.currentSong;
    final shuffled = List<Song>.from(_state.queue)..shuffle();
    if (current != null) {
      shuffled.remove(current);
      shuffled.insert(0, current);
    }
    _state = _state.copyWith(
      shuffleEnabled: true,
      queue: shuffled,
      queueIndex: 0,
    );
    if (_state.isCrossfading) {
      _cancelCrossfade(restoreVolume: true);
    } else {
      _checkPreloadValidity();
    }
    notifyListeners();
  }

  void cycleRepeatMode() {
    final nextMode = switch (_state.repeatMode) {
      PlaybackRepeatMode.off => PlaybackRepeatMode.all,
      PlaybackRepeatMode.all => PlaybackRepeatMode.one,
      PlaybackRepeatMode.one => PlaybackRepeatMode.off,
    };
    _state = _state.copyWith(repeatMode: nextMode);
    if (_state.isCrossfading) {
      _cancelCrossfade(restoreVolume: true);
    } else if (nextMode == PlaybackRepeatMode.one) {
      _invalidateSecondaryPreload('repeatOneEnabled');
    } else {
      _checkPreloadValidity();
    }
    notifyListeners();
  }

  void setVolume(double vol) {
    final clamped = vol.clamp(0.0, 1.0);
    _state = _state.copyWith(volume: clamped);
    notifyListeners();

    if (_state.isCrossfading) {
      final factorA = equalPowerOutGain(_currentCrossfadeT);
      final factorB = equalPowerInGain(_currentCrossfadeT);
      _activePlayer?.setVolume(factorA * clamped * 100.0);
      _secondaryPlayer?.setVolume(factorB * clamped * 100.0);
    } else {
      _activePlayer?.setVolume(clamped * 100.0);
    }
  }

  void applyAudioFilter(String filterString) {
    _applyAudioFilterToPlayer(_playerA, filterString);
    _applyAudioFilterToPlayer(_playerB, filterString);
  }

  void _applyAudioFilterToPlayer(Player? player, String filterString) {
    if (player == null) return;
    try {
      final p = player.platform as dynamic;
      p.setProperty('af', filterString);
      LofiQLogger.d(LogCategory.playback, 'Applied mpv audio filter string: $filterString');
    } catch (e) {
      LofiQLogger.w(LogCategory.playback, 'Could not set mpv audio filter property: $e');
    }
  }

  void setQueue(List<Song> songs, [int initialIndex = 0]) {
    if (songs.isEmpty) return;
    final safeIndex = initialIndex.clamp(0, songs.length - 1);
    playSong(songs[safeIndex], songs);
  }

  void clearQueue() {
    stop();
    _state = const LofiQPlayerState();
    notifyListeners();
  }

  void skipToIndex(int index) {
    if (index < 0 || index >= _state.queue.length) return;
    playSong(_state.queue[index]);
  }

  void reorderQueue(int oldIndex, int newIndex) {
    if (oldIndex < 0 || oldIndex >= _state.queue.length) return;
    if (newIndex > oldIndex) {
      newIndex -= 1;
    }
    final queue = List<Song>.from(_state.queue);
    final song = queue.removeAt(oldIndex);
    queue.insert(newIndex, song);

    int newQueueIndex = _state.queueIndex;
    if (oldIndex == _state.queueIndex) {
      newQueueIndex = newIndex;
    } else if (oldIndex < _state.queueIndex && newIndex >= _state.queueIndex) {
      newQueueIndex -= 1;
    } else if (oldIndex > _state.queueIndex && newIndex <= _state.queueIndex) {
      newQueueIndex += 1;
    }

    _state = _state.copyWith(queue: queue, queueIndex: newQueueIndex);
    _checkPreloadValidity();
    notifyListeners();
  }

  void addToQueue(Song song) {
    final updated = List<Song>.from(_state.queue)..add(song);
    _state = _state.copyWith(queue: updated);
    _checkPreloadValidity();
    notifyListeners();
  }

  void removeFromQueue(int index) {
    if (index < 0 || index >= _state.queue.length) return;
    final updated = List<Song>.from(_state.queue)..removeAt(index);
    int newIndex = _state.queueIndex;
    if (index < newIndex) {
      newIndex--;
    } else if (index == newIndex) {
      if (updated.isNotEmpty) {
        newIndex = math.min(newIndex, updated.length - 1);
        playSong(updated[newIndex], updated);
        return;
      } else {
        clearQueue();
        return;
      }
    }
    _state = _state.copyWith(queue: updated, queueIndex: newIndex);
    _checkPreloadValidity();
    notifyListeners();
  }

  void _checkPreloadValidity() {
    if (!_isPreloaded && !_isPreloading) return;
    final nextExpectedSong = _peekNextSong();
    final currentTargetId = _preloadedSong?.id ?? _inFlightPreloadSongId;
    if (nextExpectedSong?.id != currentTargetId) {
      LofiQLogger.i(LogCategory.playback, '[PRELOAD] Queue modification invalidated preloaded track. Resetting secondary slot.');
      if (_state.isCrossfading) {
        _cancelCrossfade(restoreVolume: true);
      } else {
        _invalidateSecondaryPreload('queueModified');
      }
    }
  }

  Future<void> _disposePlayersOnly() async {
    _crossfadeRampTimer?.cancel();
    _crossfadeRampTimer = null;

    for (final sub in _playerSubs) {
      await sub.cancel();
    }
    _playerSubs.clear();

    await _playerA?.dispose();
    await _playerB?.dispose();
    _playerA = null;
    _playerB = null;
  }

  @override
  void dispose() {
    _crossfadeRampTimer?.cancel();
    _crossfadeRampTimer = null;

    for (final sub in _playerSubs) {
      sub.cancel();
    }
    _playerSubs.clear();

    if (_eqListenerAttached) {
      EqualizerController.instance.removeListener(_onEqualizerChanged);
      _eqListenerAttached = false;
    }
    if (_preferencesListenerAttached) {
      PreferencesManager.instance.removeListener(_onPreferencesChanged);
      _preferencesListenerAttached = false;
    }

    _playerA?.dispose();
    _playerB?.dispose();
    _playerA = null;
    _playerB = null;
    _stopTicker();
    super.dispose();
  }
}

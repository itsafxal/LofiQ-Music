import 'dart:async';
import 'dart:math' as math;
import 'package:flutter/foundation.dart';
import 'package:media_kit/media_kit.dart';
import '../core/database/local_storage.dart';
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

  // Subscriptions for Player A
  StreamSubscription<Duration>? _posSubA;
  StreamSubscription<Duration>? _durSubA;
  StreamSubscription<bool>? _playingSubA;
  StreamSubscription<bool>? _bufferingSubA;
  StreamSubscription<bool>? _completedSubA;
  StreamSubscription<String>? _errorSubA;

  // Subscriptions for Player B
  StreamSubscription<Duration>? _posSubB;
  StreamSubscription<Duration>? _durSubB;
  StreamSubscription<bool>? _playingSubB;
  StreamSubscription<bool>? _bufferingSubB;
  StreamSubscription<bool>? _completedSubB;
  StreamSubscription<String>? _errorSubB;

  // Fallback ticker for non-native environments
  Timer? _positionTicker;
  bool _hasScrobbledCurrent = false;

  // Crossfade Engine State
  Timer? _crossfadeRampTimer;
  bool _isPreloading = false;
  bool _isPreloaded = false;
  Song? _preloadedSong;
  String? _preloadedStreamUrl;
  double _currentCrossfadeT = 0.0;

  bool get isPreloaded => _isPreloaded;
  Song? get preloadedSong => _preloadedSong;
  String? get preloadedStreamUrl => _preloadedStreamUrl;

  Future<void> init() async {
    // 1. Initialize Dual native media_kit Players (Slot A and Slot B)
    try {
      _playerA = Player();
      _playerB = Player();

      _setupPlayerSubscriptionsA();
      _setupPlayerSubscriptionsB();

      LofiQLogger.i(LogCategory.playback, 'Dual media_kit audio players initialized successfully for real crossfade.');
    } catch (e) {
      LofiQLogger.w(LogCategory.playback, 'Native audio player unavailable; falling back to state simulation: $e');
    }

    // 2. Wire Equalizer DSP listener to libmpv pipeline across both players
    EqualizerController.instance.addListener(() {
      applyAudioFilter(EqualizerController.instance.buildMpvAudioFilter());
    });

    // 3. Restore queue snapshot if present
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

  void _setupPlayerSubscriptionsA() {
    _posSubA = _playerA!.stream.position.listen((pos) {
      if (_isPlayerAActive) {
        _handleActivePositionUpdate(pos.inMilliseconds);
      }
    });

    _durSubA = _playerA!.stream.duration.listen((dur) {
      if (_isPlayerAActive && dur > Duration.zero) {
        _state = _state.copyWith(durationMs: dur.inMilliseconds);
        notifyListeners();
      }
    });

    _playingSubA = _playerA!.stream.playing.listen((playing) {
      if (_isPlayerAActive && !_state.isCrossfading) {
        _state = _state.copyWith(isPlaying: playing);
        notifyListeners();
      }
    });

    _bufferingSubA = _playerA!.stream.buffering.listen((buffering) {
      if (_isPlayerAActive) {
        _state = _state.copyWith(isLoading: buffering);
        notifyListeners();
      }
    });

    _completedSubA = _playerA!.stream.completed.listen((completed) {
      if (_isPlayerAActive && completed && _state.isPlaying && !_state.isCrossfading) {
        next();
      }
    });

    _errorSubA = _playerA!.stream.error.listen((err) {
      if (_isPlayerAActive) {
        _handleActivePlayerError(err);
      }
    });
  }

  void _setupPlayerSubscriptionsB() {
    _posSubB = _playerB!.stream.position.listen((pos) {
      if (!_isPlayerAActive) {
        _handleActivePositionUpdate(pos.inMilliseconds);
      }
    });

    _durSubB = _playerB!.stream.duration.listen((dur) {
      if (!_isPlayerAActive && dur > Duration.zero) {
        _state = _state.copyWith(durationMs: dur.inMilliseconds);
        notifyListeners();
      }
    });

    _playingSubB = _playerB!.stream.playing.listen((playing) {
      if (!_isPlayerAActive && !_state.isCrossfading) {
        _state = _state.copyWith(isPlaying: playing);
        notifyListeners();
      }
    });

    _bufferingSubB = _playerB!.stream.buffering.listen((buffering) {
      if (!_isPlayerAActive) {
        _state = _state.copyWith(isLoading: buffering);
        notifyListeners();
      }
    });

    _completedSubB = _playerB!.stream.completed.listen((completed) {
      if (!_isPlayerAActive && completed && _state.isPlaying && !_state.isCrossfading) {
        next();
      }
    });

    _errorSubB = _playerB!.stream.error.listen((err) {
      if (!_isPlayerAActive) {
        _handleActivePlayerError(err);
      }
    });
  }

  void _handleActivePlayerError(String err) {
    LofiQLogger.e(LogCategory.playback, 'libmpv native stream decode error: $err');
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
    final settings = PreferencesManager.instance.settings;
    final crossfadeSec = settings.crossfadeDurationSec;
    if (crossfadeSec <= 0 || _state.durationMs <= 0) return;

    // Short-track protection: Crossfade window cannot exceed half of total track duration
    final effectiveWindowMs = math.min(crossfadeSec * 1000, _state.durationMs ~/ 2);
    if (effectiveWindowMs < 1500) return; // Skip crossfade for tracks shorter than 3s

    // 1. Preload Trigger: Start preloading 4 seconds before the crossfade window begins
    final preloadThresholdMs = math.max(0, _state.durationMs - effectiveWindowMs - 4000);
    if (posMs >= preloadThresholdMs && !_isPreloaded && !_isPreloading && !_state.isCrossfading) {
      final nextSong = _peekNextSong();
      if (nextSong != null) {
        _preloadNextSong(nextSong);
      }
    }

    // 2. Crossfade Ramp Trigger: Start equal-power overlapping volume transition
    final crossfadeStartMs = math.max(0, _state.durationMs - effectiveWindowMs);
    if (posMs >= crossfadeStartMs && _isPreloaded && !_state.isCrossfading && _state.isPlaying) {
      _startCrossfade(effectiveWindowMs);
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

  Future<void> _preloadNextSong(Song nextSong) async {
    if (_isPreloading || _isPreloaded || _secondaryPlayer == null) return;
    _isPreloading = true;
    try {
      final streamUrl = await ResolverManager.instance.resolveStreamUrl(nextSong);
      if (streamUrl != null && streamUrl.isNotEmpty && _isPreloading) {
        // Apply identical DSP filter graph to secondary player
        _applyAudioFilterToPlayer(_secondaryPlayer!, EqualizerController.instance.buildMpvAudioFilter());
        // Set volume to 0 (silent preparation)
        await _secondaryPlayer!.setVolume(0.0);
        // Pre-open stream without audio output
        await _secondaryPlayer!.open(Media(streamUrl), play: false);

        _preloadedSong = nextSong;
        _preloadedStreamUrl = streamUrl;
        _isPreloaded = true;
        LofiQLogger.i(LogCategory.playback, 'Preloaded secondary track for crossfade: "${nextSong.title}"');
      }
    } catch (e) {
      LofiQLogger.w(LogCategory.playback, 'Preload failed for "${nextSong.title}": $e');
      _isPreloaded = false;
      _preloadedSong = null;
      _preloadedStreamUrl = null;
    } finally {
      _isPreloading = false;
    }
  }

  void _startCrossfade(int durationMs) {
    if (_state.isCrossfading || !_isPreloaded || _secondaryPlayer == null) return;

    _state = _state.copyWith(isCrossfading: true);
    notifyListeners();
    LofiQLogger.i(LogCategory.playback, 'Starting equal-power audio crossfade ramp over ${durationMs}ms');

    // Begin playback on secondary player
    _secondaryPlayer!.play();

    final totalSteps = (durationMs / 50).round().clamp(10, 200);
    final stepInterval = Duration(milliseconds: (durationMs / totalSteps).round());
    int currentStep = 0;

    _crossfadeRampTimer?.cancel();
    _crossfadeRampTimer = Timer.periodic(stepInterval, (timer) {
      if (!_state.isPlaying || !_state.isCrossfading) {
        return; // Paused or cancelled
      }

      currentStep++;
      final t = (currentStep / totalSteps).clamp(0.0, 1.0);
      _currentCrossfadeT = t;

      // Equal-power crossfade curve: cos(t*pi/2) and sin(t*pi/2)
      // Preserves total acoustic energy: volA^2 + volB^2 = masterVolume^2
      final factorA = math.cos(t * math.pi / 2);
      final factorB = math.sin(t * math.pi / 2);

      final masterVol = _state.volume;
      _activePlayer?.setVolume(factorA * masterVol * 100.0);
      _secondaryPlayer?.setVolume(factorB * masterVol * 100.0);

      if (t >= 1.0) {
        timer.cancel();
        _crossfadeRampTimer = null;
        _completeCrossfadeHandover();
      }
    });
  }

  void _completeCrossfadeHandover() {
    final oldPrimary = _activePlayer;
    final newPrimary = _secondaryPlayer;
    final nextSong = _preloadedSong;

    if (newPrimary == null || nextSong == null) {
      _cancelCrossfade(restoreVolume: true);
      return;
    }

    // 1. Stop old player and silence it
    oldPrimary?.stop();
    oldPrimary?.setVolume(0.0);

    // 2. Identify new queue index
    final nextIndex = _peekNextIndex();

    // 3. Swap active player slot
    _isPlayerAActive = !_isPlayerAActive;

    // 4. Ensure new primary player output volume is set to full master volume
    newPrimary.setVolume(_state.volume * 100.0);

    // 5. Reset preload state
    _isPreloaded = false;
    _isPreloading = false;
    _preloadedSong = null;
    _preloadedStreamUrl = null;
    _currentCrossfadeT = 0.0;

    // 6. Update authoritative playback state
    _hasScrobbledCurrent = false;
    _state = _state.copyWith(
      currentSong: nextSong,
      queueIndex: nextIndex,
      isCrossfading: false,
      currentPositionMs: 0,
      durationMs: nextSong.durationMs,
    );
    notifyListeners();

    // 7. Last.fm and Local snapshot updates
    _triggerNowPlaying(nextSong);
    LocalStorage.instance.saveQueueSnapshot(
      queue: _state.queue,
      currentIndex: _state.queueIndex,
      seekPositionMs: 0,
    );

    LofiQLogger.i(LogCategory.playback, 'Audio crossfade handover completed: "${nextSong.title}" is now primary.');
  }

  void _cancelCrossfade({bool restoreVolume = true}) {
    _crossfadeRampTimer?.cancel();
    _crossfadeRampTimer = null;

    if (_state.isCrossfading || _isPreloaded) {
      _secondaryPlayer?.stop();
      _secondaryPlayer?.setVolume(0.0);
    }

    _isPreloaded = false;
    _isPreloading = false;
    _preloadedSong = null;
    _preloadedStreamUrl = null;
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
    _hasScrobbledCurrent = false;

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

    // Resolve audio stream URL via authoritative multi-provider cascade
    final streamUrl = await ResolverManager.instance.resolveStreamUrl(song);
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
        await _activePlayer!.open(Media(streamUrl), play: true);
        applyAudioFilter(EqualizerController.instance.buildMpvAudioFilter());
        await _activePlayer!.setVolume(_state.volume * 100.0);

        _state = _state.copyWith(
          isLoading: false,
          isPlaying: true,
          currentSong: song.copyWith(sourceUrl: streamUrl),
        );
        notifyListeners();
      } catch (e) {
        LofiQLogger.e(LogCategory.playback, 'Error playing stream on native player: $e');
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
        _activePlayer!.pause();
        if (_state.isCrossfading) {
          _secondaryPlayer?.pause();
        }
        _state = _state.copyWith(isPlaying: false);
      } else {
        _activePlayer!.play();
        if (_state.isCrossfading) {
          _secondaryPlayer?.play();
        }
        _state = _state.copyWith(isPlaying: true);
      }
      notifyListeners();
    } else {
      if (_state.isPlaying) {
        _state = _state.copyWith(isPlaying: false);
        _stopTicker();
      } else {
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
    _cancelCrossfade(restoreVolume: false);

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

    // Manual seek invalidates/cancels active crossfade and restores primary volume
    _cancelCrossfade(restoreVolume: true);

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

    // If crossfade is already actively transitioning to next track, complete the handover immediately
    if (_state.isCrossfading && _isPreloaded) {
      _crossfadeRampTimer?.cancel();
      _crossfadeRampTimer = null;
      _completeCrossfadeHandover();
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
    _cancelCrossfade(restoreVolume: true);
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
    _cancelCrossfade(restoreVolume: true);
    notifyListeners();
  }

  void cycleRepeatMode() {
    final nextMode = switch (_state.repeatMode) {
      PlaybackRepeatMode.off => PlaybackRepeatMode.all,
      PlaybackRepeatMode.all => PlaybackRepeatMode.one,
      PlaybackRepeatMode.one => PlaybackRepeatMode.off,
    };
    _state = _state.copyWith(repeatMode: nextMode);
    _cancelCrossfade(restoreVolume: true);
    notifyListeners();
  }

  void setVolume(double vol) {
    final clamped = vol.clamp(0.0, 1.0);
    _state = _state.copyWith(volume: clamped);
    notifyListeners();

    if (_state.isCrossfading) {
      final factorA = math.cos(_currentCrossfadeT * math.pi / 2);
      final factorB = math.sin(_currentCrossfadeT * math.pi / 2);
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
    if (!_isPreloaded || _preloadedSong == null) return;
    final nextExpectedSong = _peekNextSong();
    if (nextExpectedSong?.id != _preloadedSong!.id) {
      LofiQLogger.i(LogCategory.playback, 'Queue modification invalidated preloaded track. Resetting crossfade preload.');
      _cancelCrossfade(restoreVolume: true);
    }
  }

  @override
  void dispose() {
    _crossfadeRampTimer?.cancel();
    _crossfadeRampTimer = null;

    _posSubA?.cancel();
    _durSubA?.cancel();
    _playingSubA?.cancel();
    _bufferingSubA?.cancel();
    _completedSubA?.cancel();
    _errorSubA?.cancel();

    _posSubB?.cancel();
    _durSubB?.cancel();
    _playingSubB?.cancel();
    _bufferingSubB?.cancel();
    _completedSubB?.cancel();
    _errorSubB?.cancel();

    _playerA?.dispose();
    _playerB?.dispose();
    _stopTicker();
    super.dispose();
  }
}

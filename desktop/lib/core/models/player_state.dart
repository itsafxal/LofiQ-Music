import 'song.dart';

enum PlaybackRepeatMode {
  off,
  all,
  one;

  static PlaybackRepeatMode fromString(String? val) {
    if (val == null) return PlaybackRepeatMode.off;
    for (final m in PlaybackRepeatMode.values) {
      if (m.name.toLowerCase() == val.toLowerCase()) return m;
    }
    return PlaybackRepeatMode.off;
  }
}

class LofiQPlayerState {
  final Song? currentSong;
  final bool isPlaying;
  final bool isLoading;
  final int currentPositionMs;
  final int durationMs;
  final List<Song> queue;
  final int queueIndex;
  final bool shuffleEnabled;
  final PlaybackRepeatMode repeatMode;
  final double volume;
  final bool isCrossfading;
  final String? errorMessage;

  const LofiQPlayerState({
    this.currentSong,
    this.isPlaying = false,
    this.isLoading = false,
    this.currentPositionMs = 0,
    this.durationMs = 0,
    this.queue = const [],
    this.queueIndex = -1,
    this.shuffleEnabled = false,
    this.repeatMode = PlaybackRepeatMode.off,
    this.volume = 1.0,
    this.isCrossfading = false,
    this.errorMessage,
  });

  int get positionMs => currentPositionMs;
  int get currentIndex => queueIndex;

  double get progressFraction {
    if (durationMs <= 0) return 0.0;
    final frac = currentPositionMs / durationMs;
    return frac.clamp(0.0, 1.0);
  }

  LofiQPlayerState copyWith({
    Song? currentSong,
    bool clearCurrentSong = false,
    bool? isPlaying,
    bool? isLoading,
    int? currentPositionMs,
    int? durationMs,
    List<Song>? queue,
    int? queueIndex,
    bool? shuffleEnabled,
    PlaybackRepeatMode? repeatMode,
    double? volume,
    bool? isCrossfading,
    String? errorMessage,
    bool clearError = false,
  }) {
    return LofiQPlayerState(
      currentSong: clearCurrentSong ? null : (currentSong ?? this.currentSong),
      isPlaying: isPlaying ?? this.isPlaying,
      isLoading: isLoading ?? this.isLoading,
      currentPositionMs: currentPositionMs ?? this.currentPositionMs,
      durationMs: durationMs ?? this.durationMs,
      queue: queue ?? this.queue,
      queueIndex: queueIndex ?? this.queueIndex,
      shuffleEnabled: shuffleEnabled ?? this.shuffleEnabled,
      repeatMode: repeatMode ?? this.repeatMode,
      volume: volume ?? this.volume,
      isCrossfading: isCrossfading ?? this.isCrossfading,
      errorMessage: clearError ? null : (errorMessage ?? this.errorMessage),
    );
  }
}

class TimeFormatUtils {
  static String formatDuration(int seconds) {
    if (seconds <= 0) return '00:00';
    final m = seconds ~/ 60;
    final s = seconds % 60;
    return '${m.toString().padLeft(2, '0')}:${s.toString().padLeft(2, '0')}';
  }

  static String formatDurationMs(int ms) {
    return formatDuration(ms ~/ 1000);
  }
}


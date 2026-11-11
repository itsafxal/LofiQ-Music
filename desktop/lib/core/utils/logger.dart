import 'dart:developer' as developer;

enum LogCategory {
  app,
  playback,
  resolver,
  database,
  lyrics,
  scrobble,
  sync,
  dsp;

  String get tag => name.toUpperCase();
}

class LofiQLogger {
  static void d(LogCategory category, String message) {
    developer.log('[$category] $message', name: 'LofiQ.DEBUG');
  }

  static void i(LogCategory category, String message) {
    developer.log('[$category] $message', name: 'LofiQ.INFO');
  }

  static void w(LogCategory category, String message, [Object? error]) {
    developer.log('[$category] ⚠️ $message', name: 'LofiQ.WARN', error: error);
  }

  static void e(LogCategory category, String message, [Object? error, StackTrace? stack]) {
    developer.log('[$category] ❌ $message', name: 'LofiQ.ERROR', error: error, stackTrace: stack);
  }
}


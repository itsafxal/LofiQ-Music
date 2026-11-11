import 'dart:async';
import 'package:flutter/foundation.dart';

class SleepTimerManager extends ChangeNotifier {
  static final SleepTimerManager instance = SleepTimerManager._();
  SleepTimerManager._();

  Timer? _timer;
  int _remainingSeconds = 0;
  bool _isActive = false;
  void Function()? _onComplete;

  int get remainingSeconds => _remainingSeconds;
  bool get isActive => _isActive;

  void start(int minutes, {void Function()? onComplete}) {
    cancel();
    _remainingSeconds = minutes * 60;
    _isActive = true;
    _onComplete = onComplete;
    notifyListeners();

    _timer = Timer.periodic(const Duration(seconds: 1), (t) {
      if (_remainingSeconds > 0) {
        _remainingSeconds--;
        notifyListeners();
      } else {
        cancel();
        _onComplete?.call();
      }
    });
  }

  void cancel() {
    _timer?.cancel();
    _timer = null;
    _remainingSeconds = 0;
    _isActive = false;
    notifyListeners();
  }
}


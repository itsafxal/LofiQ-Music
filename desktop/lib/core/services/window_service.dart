import 'dart:io';
import 'package:flutter/services.dart';

/// Service providing native Windows window control (minimize, maximize, close, drag)
/// communicating with the Win32 runner via 'lofiq/window' method channel.
class WindowService {
  static const MethodChannel _channel = MethodChannel('lofiq/window');

  static Future<void> minimize() async {
    if (!Platform.isWindows) return;
    try {
      await _channel.invokeMethod('minimize');
    } catch (_) {}
  }

  static Future<void> maximize() async {
    if (!Platform.isWindows) return;
    try {
      await _channel.invokeMethod('maximize');
    } catch (_) {}
  }

  static Future<void> close() async {
    if (!Platform.isWindows) return;
    try {
      await _channel.invokeMethod('close');
    } catch (_) {
      exit(0);
    }
  }

  static Future<void> startDragging() async {
    if (!Platform.isWindows) return;
    try {
      await _channel.invokeMethod('startDragging');
    } catch (_) {}
  }

  static Future<bool> isMaximized() async {
    if (!Platform.isWindows) return false;
    try {
      final res = await _channel.invokeMethod<bool>('isMaximized');
      return res ?? false;
    } catch (_) {
      return false;
    }
  }
}

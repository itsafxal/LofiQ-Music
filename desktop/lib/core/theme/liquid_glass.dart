import 'package:flutter/material.dart';

class LiquidGlassTokens {
  /// Top-down specular reflection border gradient - signature hallmark of LofiQ fluid glass.
  static LinearGradient specularBorder({double alpha = 0.28}) {
    return LinearGradient(
      begin: Alignment.topCenter,
      end: Alignment.bottomCenter,
      colors: [
        Colors.white.withValues(alpha: alpha),
        Colors.white.withValues(alpha: alpha * 0.35),
        Colors.white.withValues(alpha: alpha * 0.06),
      ],
      stops: const [0.0, 0.45, 1.0],
    );
  }

  /// Subtle classic border for non-glass or high-performance mode.
  static Border classicBorder() {
    return Border.all(
      color: Colors.white.withValues(alpha: 0.08),
      width: 1.0,
    );
  }

  /// Card corner radius standard
  static final BorderRadius cardRadius = BorderRadius.circular(16.0);
  static const double cardRadiusValue = 16.0;
  static final BorderRadius pillRadius = BorderRadius.circular(999.0);
  static final BorderRadius sheetRadius = BorderRadius.circular(24.0);
}

typedef LiquidGlass = LiquidGlassTokens;

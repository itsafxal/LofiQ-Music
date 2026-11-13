import 'package:flutter/material.dart';

/// Six distinct Liquid Glass Material Presets according to visual hierarchy & elevation.
enum GlassPreset {
  light(
    blurSigma: 16.0,
    bgAlpha: 0.45,
    baseSubstrate: Color(0xFF20202C),
    borderAlpha: 0.35,
    defaultCornerRadius: 16.0,
    elevation: 0.0,
  ),
  dark(
    blurSigma: 20.0,
    bgAlpha: 0.65,
    baseSubstrate: Color(0xFF101018),
    borderAlpha: 0.28,
    defaultCornerRadius: 20.0,
    elevation: 4.0,
  ),
  floating(
    blurSigma: 24.0,
    bgAlpha: 0.75,
    baseSubstrate: Color(0xFF0E0E14),
    borderAlpha: 0.38,
    defaultCornerRadius: 24.0,
    elevation: 16.0,
  ),
  elevated(
    blurSigma: 22.0,
    bgAlpha: 0.72,
    baseSubstrate: Color(0xFF181824),
    borderAlpha: 0.32,
    defaultCornerRadius: 20.0,
    elevation: 8.0,
  ),
  subtle(
    blurSigma: 12.0,
    bgAlpha: 0.28,
    baseSubstrate: Color(0xFF1A1A22),
    borderAlpha: 0.18,
    defaultCornerRadius: 16.0,
    elevation: 0.0,
  ),
  modal(
    blurSigma: 30.0,
    bgAlpha: 0.88,
    baseSubstrate: Color(0xFF0B0B10),
    borderAlpha: 0.40,
    defaultCornerRadius: 28.0,
    elevation: 24.0,
  );

  final double blurSigma;
  final double bgAlpha;
  final Color baseSubstrate;
  final double borderAlpha;
  final double defaultCornerRadius;
  final double elevation;

  const GlassPreset({
    required this.blurSigma,
    required this.bgAlpha,
    required this.baseSubstrate,
    required this.borderAlpha,
    required this.defaultCornerRadius,
    required this.elevation,
  });
}

class LiquidGlassTokens {
  // Corner Radius Tokens
  static const double radiusSmall = 12.0;
  static const double radiusControl = 16.0;
  static const double radiusCard = 20.0;
  static const double radiusPanel = 24.0;
  static const double radiusModal = 28.0;
  static const double radiusCapsule = 999.0;

  static final BorderRadius cardRadius = BorderRadius.circular(radiusCard);
  static const double cardRadiusValue = radiusCard;
  static final BorderRadius pillRadius = BorderRadius.circular(radiusCapsule);
  static final BorderRadius sheetRadius = BorderRadius.circular(radiusModal);

  /// Top-down specular reflection border gradient - signature hallmark of LofiQ fluid glass.
  static LinearGradient specularBorder({double alpha = 0.28}) {
    return LinearGradient(
      begin: Alignment.topCenter,
      end: Alignment.bottomCenter,
      colors: [
        Colors.white.withValues(alpha: alpha),
        Colors.white.withValues(alpha: alpha * 0.35),
        Colors.white.withValues(alpha: alpha * 0.05),
      ],
      stops: const [0.0, 0.45, 1.0],
    );
  }

  /// Top-down subtle inner sheen gradient for physical glass depth.
  static LinearGradient specularSheen({double alpha = 0.08}) {
    return LinearGradient(
      begin: Alignment.topCenter,
      end: Alignment.bottomCenter,
      colors: [
        Colors.white.withValues(alpha: alpha),
        Colors.white.withValues(alpha: alpha * 0.20),
        Colors.transparent,
      ],
      stops: const [0.0, 0.35, 1.0],
    );
  }

  /// Subtle classic border for non-glass or high-performance mode.
  static Border classicBorder() {
    return Border.all(
      color: Colors.white.withValues(alpha: 0.08),
      width: 1.0,
    );
  }
}

typedef LiquidGlass = LiquidGlassTokens;

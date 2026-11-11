import 'package:flutter/material.dart';
import 'colors.dart';

class LofiQTypography {
  static const TextStyle displayLarge = TextStyle(
    fontSize: 32.0,
    fontWeight: FontWeight.w700,
    color: LofiQColors.textPrimary,
    letterSpacing: -0.5,
  );

  static const TextStyle titleLarge = TextStyle(
    fontSize: 22.0,
    fontWeight: FontWeight.w700,
    color: LofiQColors.textPrimary,
    letterSpacing: -0.3,
  );

  static const TextStyle titleMedium = TextStyle(
    fontSize: 16.0,
    fontWeight: FontWeight.w600,
    color: LofiQColors.textPrimary,
  );

  static const TextStyle bodyMedium = TextStyle(
    fontSize: 14.0,
    fontWeight: FontWeight.w400,
    color: LofiQColors.textSecondary,
  );

  static const TextStyle bodySmall = TextStyle(
    fontSize: 12.0,
    fontWeight: FontWeight.w400,
    color: LofiQColors.textTertiary,
  );

  static const TextStyle monoSmall = TextStyle(
    fontSize: 11.0,
    fontFamily: 'monospace',
    fontWeight: FontWeight.w500,
    color: LofiQColors.textSecondary,
    letterSpacing: 0.5,
  );
}

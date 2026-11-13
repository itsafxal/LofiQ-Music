import 'package:flutter/material.dart';

class LofiQColors {
  // Pure & Near-Black Monochrome Foundations
  static const Color backgroundBlack = Color(0xFF070707);
  static const Color surfaceBlack = Color(0xFF111111);
  static const Color surfaceElevated = Color(0xFF181818);
  static const Color surfaceCard = Color(0xFF1C1C1E);
  static const Color surfaceBorder = Color(0xFF282828);

  // Typography Tones
  static const Color textPrimary = Color(0xFFFFFFFF);
  static const Color textSecondary = Color(0xFF9E9EA5);
  static const Color textTertiary = Color(0xFF636366);

  // Brand & Accent Colors
  static const Color brandPurple = Color(0xFF7C3AED);
  static const Color brandPurpleLight = Color(0xFFA78BFA);
  static const Color brandTangerine = Color(0xFFEB7D00);
  static const Color brandVanilla = Color(0xFFEBE3A7);
  static const Color brandBrunswickGreen = Color(0xFF2C5745);
  static const Color brandDrabDarkBrown = Color(0xFF2E2910);
  static const Color accentWhite = Color(0xFFFFFFFF);
  static const Color accentGray = Color(0xFF3A3A3C);
  static const Color accentRed = Color(0xFFFF453A);
  static const Color accentBlue = Color(0xFF0A84FF);
  static const Color brandPink = Color(0xFFFF2D55);
  static const Color brandTeal = Color(0xFF00C7BE);

  // Aliases for Consistency
  static const Color neonPurple = brandPurple;
  static const Color neonCyan = brandTeal;
  static const Color cardBackground = surfaceCard;
  static const Color glassBorderSubtle = surfaceBorder;

  // Liquid Glass Fills (Translucent)
  static final Color glassSurface = const Color(0xFF101016).withValues(alpha: 0.62);
  static final Color glassSurfaceElevated = const Color(0xFF181822).withValues(alpha: 0.72);
  static final Color glassCard = const Color(0xFF161620).withValues(alpha: 0.58);
  static final Color glassCardSubtle = Colors.white.withValues(alpha: 0.06);
  static final Color glassPill = Colors.white.withValues(alpha: 0.10);
  static final Color glassPillActive = Colors.white.withValues(alpha: 0.22);

  // Overlays
  static const Color playerBackgroundDim = Color(0xCC050505);
  static const Color glassmorphismOverlay = Color(0x33252525);
  static const Color glassmorphismBorder = Color(0x22FFFFFF);
}

// Top-level Brand Color Constants
const Color brandTangerine = LofiQColors.brandTangerine;
const Color brandVanilla = LofiQColors.brandVanilla;
const Color brandBrunswickGreen = LofiQColors.brandBrunswickGreen;
const Color brandDrabDarkBrown = LofiQColors.brandDrabDarkBrown;

import 'dart:ui';
import 'package:flutter/material.dart';
import '../../core/theme/colors.dart';
import '../../core/theme/liquid_glass.dart';

class GlassCard extends StatelessWidget {
  final Widget child;
  final EdgeInsetsGeometry? padding;
  final EdgeInsetsGeometry? margin;
  final BorderRadius? borderRadius;
  final VoidCallback? onTap;
  final double blurSigma;
  final Color? backgroundColor;

  const GlassCard({
    super.key,
    required this.child,
    this.padding,
    this.margin,
    this.borderRadius,
    this.onTap,
    this.blurSigma = 18.0,
    this.backgroundColor,
  });

  @override
  Widget build(BuildContext context) {
    final effectiveRadius = borderRadius ?? LiquidGlassTokens.cardRadius;
    final effectiveBg = backgroundColor ?? LofiQColors.glassCard;

    Widget card = Container(
      margin: margin,
      decoration: BoxDecoration(
        color: effectiveBg,
        borderRadius: effectiveRadius,
        border: Border.fromBorderSide(
          BorderSide(
            color: Colors.white.withValues(alpha: 0.14),
            width: 1.0,
          ),
        ),
      ),
      child: ClipRRect(
        borderRadius: effectiveRadius,
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: blurSigma, sigmaY: blurSigma),
          child: Padding(
            padding: padding ?? const EdgeInsets.all(16.0),
            child: child,
          ),
        ),
      ),
    );

    if (onTap != null) {
      return InkWell(
        onTap: onTap,
        borderRadius: effectiveRadius,
        child: card,
      );
    }

    return card;
  }
}


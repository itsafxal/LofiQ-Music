import 'package:flutter/material.dart';
import '../../core/theme/colors.dart';

class DynamicAmbientBackground extends StatelessWidget {
  final Widget child;
  final Color ambientColor;

  const DynamicAmbientBackground({
    super.key,
    required this.child,
    this.ambientColor = LofiQColors.brandPurple,
  });

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        // Base pure black background
        Container(color: LofiQColors.backgroundBlack),

        // Ambient radial glow top-left
        Positioned(
          top: -120,
          left: -100,
          width: 500,
          height: 500,
          child: Container(
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              gradient: RadialGradient(
                colors: [
                  ambientColor.withValues(alpha: 0.22),
                  ambientColor.withValues(alpha: 0.08),
                  Colors.transparent,
                ],
                stops: const [0.0, 0.5, 1.0],
              ),
            ),
          ),
        ),

        // Ambient radial glow bottom-right
        Positioned(
          bottom: -150,
          right: -100,
          width: 600,
          height: 600,
          child: Container(
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              gradient: RadialGradient(
                colors: [
                  ambientColor.withValues(alpha: 0.16),
                  ambientColor.withValues(alpha: 0.05),
                  Colors.transparent,
                ],
                stops: const [0.0, 0.45, 1.0],
              ),
            ),
          ),
        ),

        // Child content
        child,
      ],
    );
  }
}


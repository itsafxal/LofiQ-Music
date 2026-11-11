import 'dart:math' as math;
import 'package:flutter/material.dart';
import '../../core/theme/colors.dart';

class RotaryKnob extends StatefulWidget {
  final double value; // 0.0 to 1.0
  final String label;
  final ValueChanged<double> onChanged;
  final double size;
  final Color activeColor;

  const RotaryKnob({
    super.key,
    required this.value,
    required this.label,
    required this.onChanged,
    this.size = 80.0,
    this.activeColor = LofiQColors.brandPurple,
  });

  @override
  State<RotaryKnob> createState() => _RotaryKnobState();
}

class _RotaryKnobState extends State<RotaryKnob> {
  void _handlePanUpdate(DragUpdateDetails details) {
    final delta = -details.delta.dy / 150.0;
    final newVal = (widget.value + delta).clamp(0.0, 1.0);
    widget.onChanged(newVal);
  }

  @override
  Widget build(BuildContext context) {
    final angle = -math.pi * 0.75 + (widget.value * math.pi * 1.5);

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        GestureDetector(
          onVerticalDragUpdate: _handlePanUpdate,
          child: Container(
            width: widget.size,
            height: widget.size,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: LofiQColors.surfaceCard,
              border: Border.all(
                color: Colors.white.withValues(alpha: 0.18),
                width: 1.5,
              ),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.4),
                  blurRadius: 8,
                  offset: const Offset(0, 4),
                ),
              ],
            ),
            child: Stack(
              alignment: Alignment.center,
              children: [
                // Active indicator arc / needle
                Transform.rotate(
                  angle: angle,
                  child: Align(
                    alignment: Alignment.topCenter,
                    child: Container(
                      width: 4,
                      height: widget.size * 0.28,
                      margin: const EdgeInsets.only(top: 6),
                      decoration: BoxDecoration(
                        color: widget.activeColor,
                        borderRadius: BorderRadius.circular(2),
                        boxShadow: [
                          BoxShadow(
                            color: widget.activeColor.withValues(alpha: 0.8),
                            blurRadius: 6,
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
                // Center knob cap
                Container(
                  width: widget.size * 0.45,
                  height: widget.size * 0.45,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: LofiQColors.surfaceBlack,
                    border: Border.all(
                      color: Colors.white.withValues(alpha: 0.12),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 8.0),
        Text(
          widget.label,
          style: const TextStyle(
            fontSize: 10.0,
            fontWeight: FontWeight.w700,
            letterSpacing: 0.8,
            color: LofiQColors.textSecondary,
          ),
        ),
      ],
    );
  }
}

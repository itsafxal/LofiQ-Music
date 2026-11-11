import 'dart:math' as math;
import 'package:flutter/material.dart';
import '../../core/models/seekbar_style.dart';
import '../../core/theme/colors.dart';

class AnimatedWaveformSeekbar extends StatefulWidget {
  final int currentPositionMs;
  final int durationMs;
  final bool isPlaying;
  final SeekbarStyle style;
  final ValueChanged<int> onSeek;

  const AnimatedWaveformSeekbar({
    super.key,
    required this.currentPositionMs,
    required this.durationMs,
    this.isPlaying = false,
    this.style = SeekbarStyle.lofiqWave,
    required this.onSeek,
  });

  @override
  State<AnimatedWaveformSeekbar> createState() => _AnimatedWaveformSeekbarState();
}

class _AnimatedWaveformSeekbarState extends State<AnimatedWaveformSeekbar>
    with SingleTickerProviderStateMixin {
  late AnimationController _animController;

  @override
  void initState() {
    super.initState();
    _animController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1400),
    );
    if (widget.isPlaying) {
      _animController.repeat();
    }
  }

  @override
  void didUpdateWidget(covariant AnimatedWaveformSeekbar oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.isPlaying && !_animController.isAnimating) {
      _animController.repeat();
    } else if (!widget.isPlaying && _animController.isAnimating) {
      _animController.stop();
    }
  }

  @override
  void dispose() {
    _animController.dispose();
    super.dispose();
  }

  double get progressFraction {
    if (widget.durationMs <= 0) return 0.0;
    return (widget.currentPositionMs / widget.durationMs).clamp(0.0, 1.0);
  }

  void _handleTap(TapDownDetails details, double width) {
    if (width <= 0 || widget.durationMs <= 0) return;
    final frac = (details.localPosition.dx / width).clamp(0.0, 1.0);
    widget.onSeek((frac * widget.durationMs).toInt());
  }

  void _handleDrag(DragUpdateDetails details, double width) {
    if (width <= 0 || widget.durationMs <= 0) return;
    final frac = (details.localPosition.dx / width).clamp(0.0, 1.0);
    widget.onSeek((frac * widget.durationMs).toInt());
  }

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final width = constraints.maxWidth;
        return GestureDetector(
          onTapDown: (details) => _handleTap(details, width),
          onHorizontalDragUpdate: (details) => _handleDrag(details, width),
          child: AnimatedBuilder(
            animation: _animController,
            builder: (context, child) {
              return CustomPaint(
                size: Size(width, 36),
                painter: _SeekbarPainter(
                  progressFraction: progressFraction,
                  animValue: _animController.value,
                  style: widget.style,
                  isPlaying: widget.isPlaying,
                ),
              );
            },
          ),
        );
      },
    );
  }
}

class _SeekbarPainter extends CustomPainter {
  final double progressFraction;
  final double animValue;
  final SeekbarStyle style;
  final bool isPlaying;

  _SeekbarPainter({
    required this.progressFraction,
    required this.animValue,
    required this.style,
    required this.isPlaying,
  });

  @override
  void paint(Canvas canvas, Size size) {
    switch (style) {
      case SeekbarStyle.lofiqWave:
        _paintWave(canvas, size);
        break;
      case SeekbarStyle.neonBar:
        _paintNeonBar(canvas, size);
        break;
      case SeekbarStyle.dotMatrix:
        _paintDotMatrix(canvas, size);
        break;
      case SeekbarStyle.retroTape:
      case SeekbarStyle.thinLine:
        _paintThinLine(canvas, size);
        break;
    }
  }

  void _paintWave(Canvas canvas, Size size) {
    const barCount = 48;
    final barWidth = size.width / barCount;
    final progressX = size.width * progressFraction;

    final playedPaint = Paint()
      ..color = LofiQColors.textPrimary
      ..style = PaintingStyle.fill
      ..strokeCap = StrokeCap.round;

    final unplayedPaint = Paint()
      ..color = Colors.white.withValues(alpha: 0.18)
      ..style = PaintingStyle.fill
      ..strokeCap = StrokeCap.round;

    for (int i = 0; i < barCount; i++) {
      final x = i * barWidth + (barWidth * 0.25);
      final isPlayed = x <= progressX;

      // Base sine height
      final baseHeight = math.sin((i / barCount) * math.pi) * 20.0 + 6.0;
      double height = baseHeight;

      if (isPlaying && isPlayed) {
        final waveOffset = math.sin((animValue * 2 * math.pi) + (i * 0.4)) * 5.0;
        height = (height + waveOffset).clamp(4.0, size.height);
      }

      final y = (size.height - height) / 2;
      final rect = RRect.fromRectAndRadius(
        Rect.fromLTWH(x, y, barWidth * 0.5, height),
        const Radius.circular(2.0),
      );

      canvas.drawRRect(rect, isPlayed ? playedPaint : unplayedPaint);
    }
  }

  void _paintNeonBar(Canvas canvas, Size size) {
    final centerY = size.height / 2;
    final progressX = size.width * progressFraction;

    // Background track
    canvas.drawLine(
      Offset(0, centerY),
      Offset(size.width, centerY),
      Paint()
        ..color = Colors.white.withValues(alpha: 0.12)
        ..strokeWidth = 6.0
        ..strokeCap = StrokeCap.round,
    );

    // Neon glowing active track
    if (progressX > 0) {
      canvas.drawLine(
        Offset(0, centerY),
        Offset(progressX, centerY),
        Paint()
          ..color = LofiQColors.brandPurpleLight
          ..strokeWidth = 6.0
          ..strokeCap = StrokeCap.round
          ..maskFilter = const MaskFilter.blur(BlurStyle.solid, 4.0),
      );

      // Handle thumb
      canvas.drawCircle(
        Offset(progressX, centerY),
        6.0,
        Paint()..color = LofiQColors.textPrimary,
      );
    }
  }

  void _paintDotMatrix(Canvas canvas, Size size) {
    const dotCount = 40;
    final step = size.width / dotCount;
    final centerY = size.height / 2;
    final progressX = size.width * progressFraction;

    for (int i = 0; i < dotCount; i++) {
      final x = i * step + (step / 2);
      final isPlayed = x <= progressX;
      canvas.drawCircle(
        Offset(x, centerY),
        2.5,
        Paint()..color = isPlayed ? LofiQColors.textPrimary : Colors.white.withValues(alpha: 0.15),
      );
    }
  }

  void _paintThinLine(Canvas canvas, Size size) {
    final centerY = size.height / 2;
    final progressX = size.width * progressFraction;

    // Background line
    canvas.drawLine(
      Offset(0, centerY),
      Offset(size.width, centerY),
      Paint()
        ..color = Colors.white.withValues(alpha: 0.15)
        ..strokeWidth = 2.0,
    );

    // Active line
    if (progressX > 0) {
      canvas.drawLine(
        Offset(0, centerY),
        Offset(progressX, centerY),
        Paint()
          ..color = LofiQColors.textPrimary
          ..strokeWidth = 2.5,
      );

      // Scrubber thumb
      canvas.drawCircle(
        Offset(progressX, centerY),
        4.5,
        Paint()..color = LofiQColors.textPrimary,
      );
    }
  }

  @override
  bool shouldRepaint(covariant _SeekbarPainter oldDelegate) {
    return oldDelegate.progressFraction != progressFraction ||
        oldDelegate.animValue != animValue ||
        oldDelegate.style != style ||
        oldDelegate.isPlaying != isPlaying;
  }
}


import 'package:flutter/material.dart';
import '../../core/theme/colors.dart';
import '../../playback/equalizer/equalizer_controller.dart';
import '../components/glass/glass_components.dart';
import '../components/rotary_knob.dart';

class EqualizerScreen extends StatefulWidget {
  final EqualizerController equalizerController;

  const EqualizerScreen({super.key, required this.equalizerController});

  @override
  State<EqualizerScreen> createState() => _EqualizerScreenState();
}

class _EqualizerScreenState extends State<EqualizerScreen> {
  @override
  void initState() {
    super.initState();
    widget.equalizerController.addListener(_onEqChanged);
  }

  @override
  void dispose() {
    widget.equalizerController.removeListener(_onEqChanged);
    super.dispose();
  }

  void _onEqChanged() {
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final eq = widget.equalizerController;
    final bands = eq.bandFrequencies;

    return Scaffold(
      backgroundColor: Colors.transparent,
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(28.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Top Bar
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: LofiQColors.brandTangerine.withValues(alpha: 0.16),
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(
                      color: LofiQColors.brandTangerine.withValues(alpha: 0.35),
                      width: 1.0,
                    ),
                  ),
                  child: const Icon(Icons.graphic_eq_rounded, color: LofiQColors.brandTangerine, size: 28),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '10-Band Parametric Equalizer & DSP',
                        style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                          color: LofiQColors.textPrimary,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        'Studio-grade acoustic response tuning, analog warmth, bass exciter, and 3D spatializer',
                        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                          color: LofiQColors.textSecondary,
                        ),
                      ),
                    ],
                  ),
                ),
                // Master Enable Switch
                Row(
                  children: [
                    Text(
                      eq.isEnabled ? 'DSP Active' : 'DSP Bypassed',
                      style: TextStyle(
                        color: eq.isEnabled ? LofiQColors.brandTangerine : LofiQColors.textSecondary,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(width: 12),
                    GlassSwitch(
                      value: eq.isEnabled,
                      activeColor: LofiQColors.brandTangerine,
                      onChanged: (val) => eq.setEnabled(val),
                    ),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 24),

            // Bezier Frequency Curve Visualizer
            GlassPanel(
              title: 'FREQUENCY RESPONSE CURVE',
              subtitle: 'Dynamic interpolated spline representing acoustic filter output',
              action: GlassButton(
                onPressed: () => eq.reset(),
                isPrimary: false,
                padding: const EdgeInsets.symmetric(horizontal: 12.0, vertical: 8.0),
                child: const Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.restart_alt_rounded, size: 15, color: LofiQColors.brandVanilla),
                    SizedBox(width: 6),
                    Text('Reset Flat', style: TextStyle(color: LofiQColors.brandVanilla, fontSize: 12, fontWeight: FontWeight.bold)),
                  ],
                ),
              ),
              child: SizedBox(
                height: 140,
                width: double.infinity,
                child: CustomPaint(
                  painter: _EqCurvePainter(
                    bandGains: eq.bandLevels,
                    isEnabled: eq.isEnabled,
                  ),
                ),
              ),
            ),
            const SizedBox(height: 24),

            // Preset Selector Chips
            const Text(
              'Acoustic Presets',
              style: TextStyle(
                color: LofiQColors.textPrimary,
                fontWeight: FontWeight.bold,
                fontSize: 16.0,
              ),
            ),
            const SizedBox(height: 12),
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: eq.presets.map((preset) {
                  final isCurrent = eq.currentPreset == preset;
                  return Padding(
                    padding: const EdgeInsets.only(right: 8.0),
                    child: GestureDetector(
                      onTap: () => eq.applyPreset(preset),
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
                        decoration: BoxDecoration(
                          color: isCurrent
                              ? LofiQColors.brandTangerine.withValues(alpha: 0.22)
                              : Colors.white.withValues(alpha: 0.06),
                          borderRadius: BorderRadius.circular(14.0),
                          border: Border.all(
                            color: isCurrent
                                ? LofiQColors.brandTangerine.withValues(alpha: 0.6)
                                : Colors.white.withValues(alpha: 0.12),
                            width: 1.0,
                          ),
                        ),
                        child: Text(
                          preset,
                          style: TextStyle(
                            color: isCurrent ? LofiQColors.brandTangerine : LofiQColors.textSecondary,
                            fontWeight: isCurrent ? FontWeight.bold : FontWeight.normal,
                            fontSize: 13.0,
                          ),
                        ),
                      ),
                    ),
                  );
                }).toList(),
              ),
            ),
            const SizedBox(height: 24),

            // 10-Band Sliders Grid
            GlassPanel(
              title: 'BAND GAIN CONTROLS (±15 dB)',
              subtitle: 'Individual parametric gain faders from deep 31Hz sub-bass to 16kHz air',
              child: SizedBox(
                height: 220,
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                  children: List.generate(10, (index) {
                    final freqHz = bands[index];
                    final freqLabel = freqHz >= 1000 ? '${(freqHz / 1000).toStringAsFixed(0)}k' : '$freqHz';
                    final gain = eq.getBandLevel(index);
                    final gainDb = (gain / 100.0).toStringAsFixed(1);

                    return Column(
                      children: [
                        Text(
                          '${gain > 0 ? "+" : ""}$gainDb',
                          style: TextStyle(
                            color: gain == 0 ? LofiQColors.textSecondary : LofiQColors.brandTangerine,
                            fontSize: 11,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        const SizedBox(height: 8),
                        Expanded(
                          child: RotatedBox(
                            quarterTurns: 3,
                            child: SliderTheme(
                              data: SliderThemeData(
                                trackHeight: 4,
                                thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 7),
                                overlayShape: const RoundSliderOverlayShape(overlayRadius: 14),
                                activeTrackColor: eq.isEnabled ? LofiQColors.brandTangerine : Colors.grey,
                                inactiveTrackColor: Colors.white.withValues(alpha: 0.12),
                                thumbColor: eq.isEnabled ? Colors.white : Colors.grey,
                              ),
                              child: Slider(
                                value: gain.toDouble(),
                                min: -1500,
                                max: 1500,
                                onChanged: eq.isEnabled
                                    ? (val) => eq.setBandLevel(index, val.round())
                                    : null,
                              ),
                            ),
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          freqLabel,
                          style: const TextStyle(
                            color: LofiQColors.textSecondary,
                            fontSize: 12,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ],
                    );
                  }),
                ),
              ),
            ),
            const SizedBox(height: 24),

            // Bass Boost & Virtualizer Knobs
            Row(
              children: [
                Expanded(
                  child: GlassPanel(
                    title: 'BASS EXCITER',
                    subtitle: 'Sub-harmonic acoustic low-end exciter',
                    icon: Icons.speaker_rounded,
                    child: Column(
                      children: [
                        RotaryKnob(
                          value: eq.bassBoost / 1000.0,
                          label: 'BASS BOOST',
                          activeColor: LofiQColors.brandTangerine,
                          onChanged: eq.isEnabled
                              ? (v) => eq.setBassBoost((v * 1000).round())
                              : (v) {},
                        ),
                        const SizedBox(height: 8),
                        Text(
                          '${(eq.bassBoost / 10).toStringAsFixed(0)}%',
                          style: const TextStyle(
                            color: LofiQColors.brandTangerine,
                            fontWeight: FontWeight.bold,
                            fontSize: 18,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(width: 20),
                Expanded(
                  child: GlassPanel(
                    title: 'SPATIAL VIRTUALIZER',
                    subtitle: '3D binaural panoramic stereo expansion',
                    icon: Icons.surround_sound_rounded,
                    child: Column(
                      children: [
                        RotaryKnob(
                          value: eq.virtualizer / 1000.0,
                          label: '3D EXPANSION',
                          activeColor: LofiQColors.brandVanilla,
                          onChanged: eq.isEnabled
                              ? (v) => eq.setVirtualizer((v * 1000).round())
                              : (v) {},
                        ),
                        const SizedBox(height: 8),
                        Text(
                          '${(eq.virtualizer / 10).toStringAsFixed(0)}%',
                          style: const TextStyle(
                            color: LofiQColors.brandVanilla,
                            fontWeight: FontWeight.bold,
                            fontSize: 18,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _EqCurvePainter extends CustomPainter {
  final List<int> bandGains;
  final bool isEnabled;

  _EqCurvePainter({required this.bandGains, required this.isEnabled});

  @override
  void paint(Canvas canvas, Size size) {
    // Draw 0dB reference center line
    final zeroLineY = size.height / 2;
    final gridPaint = Paint()
      ..color = Colors.white.withValues(alpha: 0.12)
      ..strokeWidth = 1
      ..style = PaintingStyle.stroke;

    canvas.drawLine(Offset(0, zeroLineY), Offset(size.width, zeroLineY), gridPaint);
    canvas.drawLine(
      Offset(0, zeroLineY - size.height * 0.35),
      Offset(size.width, zeroLineY - size.height * 0.35),
      gridPaint..color = Colors.white.withValues(alpha: 0.05),
    );
    canvas.drawLine(
      Offset(0, zeroLineY + size.height * 0.35),
      Offset(size.width, zeroLineY + size.height * 0.35),
      gridPaint..color = Colors.white.withValues(alpha: 0.05),
    );

    if (bandGains.isEmpty) return;

    final points = <Offset>[];
    final stepX = size.width / (bandGains.length - 1);

    for (int i = 0; i < bandGains.length; i++) {
      final gainRatio = (bandGains[i] / 1500.0).clamp(-1.0, 1.0);
      final y = zeroLineY - (gainRatio * (size.height * 0.45));
      points.add(Offset(i * stepX, y));
    }

    final path = Path();
    path.moveTo(points.first.dx, points.first.dy);

    for (int i = 0; i < points.length - 1; i++) {
      final p0 = points[i];
      final p1 = points[i + 1];
      final controlPointX = (p0.dx + p1.dx) / 2;
      path.cubicTo(controlPointX, p0.dy, controlPointX, p1.dy, p1.dx, p1.dy);
    }

    // Fill under curve
    final fillPath = Path.from(path)
      ..lineTo(size.width, size.height)
      ..lineTo(0, size.height)
      ..close();

    final fillGradient = LinearGradient(
      colors: isEnabled
          ? [
              LofiQColors.brandTangerine.withValues(alpha: 0.35),
              LofiQColors.brandVanilla.withValues(alpha: 0.04),
            ]
          : [Colors.grey.withValues(alpha: 0.15), Colors.transparent],
      begin: Alignment.topCenter,
      end: Alignment.bottomCenter,
    );

    canvas.drawPath(
      fillPath,
      Paint()
        ..shader = fillGradient.createShader(Rect.fromLTWH(0, 0, size.width, size.height))
        ..style = PaintingStyle.fill,
    );

    // Stroke line
    final linePaint = Paint()
      ..color = isEnabled ? LofiQColors.brandTangerine : Colors.grey
      ..strokeWidth = 3
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round;

    canvas.drawPath(path, linePaint);

    // Draw point nodes
    final dotPaint = Paint()
      ..color = isEnabled ? LofiQColors.brandVanilla : Colors.grey
      ..style = PaintingStyle.fill;

    for (final p in points) {
      canvas.drawCircle(p, 4, dotPaint);
    }
  }

  @override
  bool shouldRepaint(covariant _EqCurvePainter oldDelegate) {
    return oldDelegate.isEnabled != isEnabled || oldDelegate.bandGains != bandGains;
  }
}

import 'package:flutter/material.dart';
import '../../core/theme/colors.dart';
import '../../playback/equalizer/equalizer_controller.dart';
import '../components/glass_card.dart';
import '../components/rotary_knob.dart';

class EqualizerScreen extends StatefulWidget {
  final EqualizerController equalizerController;

  const EqualizerScreen({Key? key, required this.equalizerController}) : super(key: key);

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
                    color: LofiQColors.neonPurple.withOpacity(0.15),
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: LofiQColors.neonPurple.withOpacity(0.3)),
                  ),
                  child: const Icon(Icons.graphic_eq_rounded, color: LofiQColors.neonPurple, size: 28),
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
                        color: eq.isEnabled ? LofiQColors.neonPurple : LofiQColors.textSecondary,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(width: 12),
                    Switch(
                      value: eq.isEnabled,
                      onChanged: (val) => eq.setEnabled(val),
                      activeColor: LofiQColors.neonPurple,
                      activeTrackColor: LofiQColors.neonPurple.withOpacity(0.4),
                    ),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 24),

            // Bezier Frequency Curve Visualizer
            GlassCard(
              padding: const EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Text(
                        'Frequency Response Curve',
                        style: TextStyle(color: LofiQColors.textPrimary, fontWeight: FontWeight.bold, fontSize: 16),
                      ),
                      TextButton.icon(
                        onPressed: () => eq.reset(),
                        icon: const Icon(Icons.restart_alt_rounded, size: 16, color: LofiQColors.textSecondary),
                        label: const Text('Reset Flat', style: TextStyle(color: LofiQColors.textSecondary)),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  SizedBox(
                    height: 140,
                    width: double.infinity,
                    child: CustomPaint(
                      painter: _EqCurvePainter(
                        bandGains: eq.bandLevels,
                        isEnabled: eq.isEnabled,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 24),

            // Preset Selector Chips
            Text(
              'Acoustic Presets',
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                color: LofiQColors.textPrimary,
                fontWeight: FontWeight.bold,
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
                    child: ChoiceChip(
                      label: Text(preset),
                      selected: isCurrent,
                      onSelected: (_) => eq.applyPreset(preset),
                      selectedColor: LofiQColors.neonPurple.withOpacity(0.3),
                      backgroundColor: LofiQColors.cardBackground,
                      side: BorderSide(
                        color: isCurrent ? LofiQColors.neonPurple : LofiQColors.glassBorderSubtle,
                      ),
                      labelStyle: TextStyle(
                        color: isCurrent ? LofiQColors.neonPurple : LofiQColors.textSecondary,
                        fontWeight: isCurrent ? FontWeight.bold : FontWeight.normal,
                      ),
                    ),
                  );
                }).toList(),
              ),
            ),
            const SizedBox(height: 24),

            // 10-Band Sliders Grid
            GlassCard(
              padding: const EdgeInsets.all(24),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Band Gain Controls (±15 dB)',
                    style: TextStyle(color: LofiQColors.textPrimary, fontWeight: FontWeight.bold, fontSize: 16),
                  ),
                  const SizedBox(height: 24),
                  SizedBox(
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
                                color: gain == 0 ? LofiQColors.textSecondary : LofiQColors.neonPurple,
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
                                    activeTrackColor: eq.isEnabled ? LofiQColors.neonPurple : Colors.grey,
                                    inactiveTrackColor: LofiQColors.glassBorderSubtle,
                                    thumbColor: eq.isEnabled ? LofiQColors.neonCyan : Colors.grey,
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
                ],
              ),
            ),
            const SizedBox(height: 24),

            // Bass Boost & Virtualizer Knobs
            Row(
              children: [
                Expanded(
                  child: GlassCard(
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      children: [
                        RotaryKnob(
                          value: eq.bassBoost / 1000.0,
                          label: 'BASS EXCITER',
                          activeColor: LofiQColors.neonPurple,
                          onChanged: eq.isEnabled
                              ? (v) => eq.setBassBoost((v * 1000).round())
                              : (v) {},
                        ),
                        const SizedBox(height: 8),
                        Text(
                          '${(eq.bassBoost / 10).toStringAsFixed(0)}%',
                          style: const TextStyle(
                            color: LofiQColors.neonPurple,
                            fontWeight: FontWeight.bold,
                            fontSize: 18,
                          ),
                        ),
                        const SizedBox(height: 4),
                        const Text(
                          'Sub-harmonic bass boost',
                          style: TextStyle(color: LofiQColors.textSecondary, fontSize: 12),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(width: 20),
                Expanded(
                  child: GlassCard(
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      children: [
                        RotaryKnob(
                          value: eq.virtualizer / 1000.0,
                          label: 'SPATIAL VIRTUALIZER',
                          activeColor: LofiQColors.neonCyan,
                          onChanged: eq.isEnabled
                              ? (v) => eq.setVirtualizer((v * 1000).round())
                              : (v) {},
                        ),
                        const SizedBox(height: 8),
                        Text(
                          '${(eq.virtualizer / 10).toStringAsFixed(0)}%',
                          style: const TextStyle(
                            color: LofiQColors.neonCyan,
                            fontWeight: FontWeight.bold,
                            fontSize: 18,
                          ),
                        ),
                        const SizedBox(height: 4),
                        const Text(
                          '3D binaural stereo expansion',
                          style: TextStyle(color: LofiQColors.textSecondary, fontSize: 12),
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
      ..color = LofiQColors.glassBorderSubtle
      ..strokeWidth = 1
      ..style = PaintingStyle.stroke;

    canvas.drawLine(Offset(0, zeroLineY), Offset(size.width, zeroLineY), gridPaint);
    canvas.drawLine(Offset(0, zeroLineY - size.height * 0.35), Offset(size.width, zeroLineY - size.height * 0.35), gridPaint..color = LofiQColors.glassBorderSubtle.withOpacity(0.3));
    canvas.drawLine(Offset(0, zeroLineY + size.height * 0.35), Offset(size.width, zeroLineY + size.height * 0.35), gridPaint..color = LofiQColors.glassBorderSubtle.withOpacity(0.3));

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
    final fillPath = Path.from(path);
    fillPath.lineTo(size.width, size.height);
    fillPath.lineTo(0, size.height);
    fillPath.close();

    final fillGradient = LinearGradient(
      colors: isEnabled
          ? [LofiQColors.neonPurple.withOpacity(0.3), LofiQColors.neonCyan.withOpacity(0.02)]
          : [Colors.grey.withOpacity(0.15), Colors.transparent],
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
      ..color = isEnabled ? LofiQColors.neonPurple : Colors.grey
      ..strokeWidth = 3
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round;

    canvas.drawPath(path, linePaint);

    // Draw point nodes
    final dotPaint = Paint()
      ..color = isEnabled ? LofiQColors.neonCyan : Colors.grey
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


import 'dart:io';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lofiq_desktop/core/theme/colors.dart';
import 'package:lofiq_desktop/playback/equalizer/equalizer_controller.dart';
import 'package:lofiq_desktop/playback/playback_controller.dart';
import 'package:lofiq_desktop/ui/components/glass/glass_components.dart';
import 'package:lofiq_desktop/ui/screens/diagnostics_screen.dart';
import 'package:lofiq_desktop/ui/screens/equalizer_screen.dart';
import 'package:lofiq_desktop/ui/screens/explore_screen.dart';
import 'package:lofiq_desktop/ui/screens/library_screen.dart';
import 'package:lofiq_desktop/ui/screens/local_music_screen.dart';
import 'package:lofiq_desktop/ui/screens/lyrics_screen.dart';
import 'package:lofiq_desktop/ui/screens/queue_screen.dart';
import 'package:lofiq_desktop/ui/screens/search_screen.dart';
import 'package:lofiq_desktop/ui/screens/settings_screen.dart';
import 'package:lofiq_desktop/ui/screens/spotify_import_screen.dart';

Widget wrapWithQaShell(Widget child, {Size size = const Size(1280, 800)}) {
  return MaterialApp(
    debugShowCheckedModeBanner: false,
    theme: ThemeData.dark().copyWith(
      scaffoldBackgroundColor: LofiQColors.backgroundBlack,
    ),
    home: Scaffold(
      backgroundColor: LofiQColors.backgroundBlack,
      body: RepaintBoundary(
        key: const ValueKey('qa_root'),
        child: SizedBox(
          width: size.width,
          height: size.height,
          child: Stack(
            children: [
              Positioned.fill(
                child: Container(
                  decoration: const BoxDecoration(
                    gradient: RadialGradient(
                      center: Alignment(-0.6, -0.5),
                      radius: 1.2,
                      colors: [
                        Color(0xFF351A05),
                        Color(0xFF0F1B14),
                        Color(0xFF070707),
                      ],
                    ),
                  ),
                ),
              ),
              Positioned.fill(
                child: child,
              ),
            ],
          ),
        ),
      ),
    ),
  );
}

Future<void> captureFrame(WidgetTester tester, Widget screen, String filename, {Size size = const Size(1280, 800)}) async {
  await tester.pumpWidget(wrapWithQaShell(screen, size: size));
  await tester.pump();

  await tester.runAsync(() async {
    final boundary = tester.renderObject(find.byKey(const ValueKey('qa_root'))) as RenderRepaintBoundary;
    final image = await boundary.toImage(pixelRatio: 1.0);
    final byteData = await image.toByteData(format: ui.ImageByteFormat.png);
    final pngBytes = byteData!.buffer.asUint8List();
    final dir = Directory('qa_screenshots/windows');
    if (!dir.existsSync()) dir.createSync(recursive: true);
    await File('qa_screenshots/windows/$filename').writeAsBytes(pngBytes);
    print('Captured: qa_screenshots/windows/$filename (${pngBytes.length} bytes)');
  });
}

void main() {
  testWidgets('Capture All Transformed Screens & Glass Components Specimen Sheet', (tester) async {
    tester.view.physicalSize = const Size(1280, 800);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);

    final pbc = PlaybackController.instance;
    final eq = EqualizerController.instance;

    print('\n=== INITIATING VISUAL SCREENSHOT CAPTURE FOR ALL LIQUID GLASS SCREENS ===');

    // 1. Explore Screen
    await captureFrame(tester, ExploreScreen(playbackController: pbc), '01_explore_screen.png');

    // 2. Search Screen
    await captureFrame(tester, const SearchScreen(), '02_search_screen.png');

    // 3. Library Screen
    await captureFrame(tester, LibraryScreen(onOpenLocalMusic: () {}), '03_library_screen.png');

    // 4. On-Device Audio Screen
    await captureFrame(tester, LocalMusicScreen(playbackController: pbc), '04_local_music_screen.png');

    // 5. Equalizer and DSP Rack Screen
    await captureFrame(tester, EqualizerScreen(equalizerController: eq), '05_equalizer_dsp_screen.png');

    // 6. Spotify Importer Screen
    await captureFrame(tester, SpotifyImportScreen(playbackController: pbc), '06_spotify_import_screen.png');

    // 7. Settings Screen
    await captureFrame(tester, const SettingsScreen(), '07_settings_screen.png');

    // 8. Diagnostics Screen
    await captureFrame(tester, const DiagnosticsScreen(), '08_diagnostics_screen.png');

    // 9. Queue Side Panel
    await captureFrame(tester, QueueScreen(playbackController: pbc), '09_queue_side_panel.png', size: const Size(420, 800));

    // 10. Lyrics Side Panel
    await captureFrame(tester, LyricsScreen(playbackController: pbc), '10_lyrics_side_panel.png', size: const Size(420, 800));

    // 11. Modal GlassDialog (Create Playlist Alert)
    final dialogSpecimen = Stack(
      children: [
        Center(
          child: GlassDialog(
            title: 'Create New Playlist',
            icon: Icons.playlist_add_rounded,
            content: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const GlassInput(
                  hintText: 'Enter playlist title (e.g. Midnight Beats)...',
                  prefixIcon: Icons.music_note_rounded,
                ),
                const SizedBox(height: 12),
                Text(
                  'Your playlist will be saved with custom Liquid Glass cover art and offline metadata.',
                  style: TextStyle(color: LofiQColors.textSecondary, fontSize: 13),
                ),
              ],
            ),
            actions: [
              GlassButton(
                onPressed: () {},
                child: const Text('Cancel', style: TextStyle(color: LofiQColors.textSecondary)),
              ),
              GlassButton(
                isPrimary: true,
                accentColor: brandTangerine,
                onPressed: () {},
                child: const Text('Create Playlist'),
              ),
            ],
          ),
        ),
      ],
    );
    await captureFrame(tester, dialogSpecimen, '11_modal_glass_dialog.png');

    // 12. Windows Compact Resize Layout (1024x600)
    await captureFrame(
      tester,
      ExploreScreen(playbackController: pbc),
      '12_compact_resize_1024x600.png',
      size: const Size(1024, 600),
    );

    // 13. High-DPI Scaling Specimen (1920x1080 Viewport)
    await captureFrame(
      tester,
      SettingsScreen(),
      '13_high_dpi_scaling_1080p.png',
      size: const Size(1920, 1080),
    );

    // 14. Comprehensive 14-Component Specimen Sheet
    final specimenDashboard = SingleChildScrollView(
      padding: const EdgeInsets.all(28.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'LofiQ Liquid Glass — 14 Shared Glass Components Specimen Sheet',
            style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold, color: Colors.white),
          ),
          const SizedBox(height: 6),
          Text(
            'Visual QA audit verifying real Skia/Impeller backdrop blur, 135-degree specular rims, squircle corners, and Tangerine accents',
            style: TextStyle(color: LofiQColors.textSecondary, fontSize: 13),
          ),
          const SizedBox(height: 24),

          // Presets
          const Text('1. GlassSurface — 6 Material Presets', style: TextStyle(fontWeight: FontWeight.bold, color: brandVanilla)),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(
                child: GlassSurface(
                  preset: GlassPreset.light,
                  padding: const EdgeInsets.all(12),
                  child: const Center(child: Text('Light Glass', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600))),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: GlassSurface(
                  preset: GlassPreset.dark,
                  padding: const EdgeInsets.all(12),
                  child: const Center(child: Text('Dark Glass', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600))),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: GlassSurface(
                  preset: GlassPreset.floating,
                  padding: const EdgeInsets.all(12),
                  child: const Center(child: Text('Floating Glass', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600))),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: GlassSurface(
                  preset: GlassPreset.elevated,
                  padding: const EdgeInsets.all(12),
                  child: const Center(child: Text('Elevated Glass', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600))),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: GlassSurface(
                  preset: GlassPreset.subtle,
                  padding: const EdgeInsets.all(12),
                  child: const Center(child: Text('Subtle Glass', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600))),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: GlassSurface(
                  preset: GlassPreset.modal,
                  padding: const EdgeInsets.all(12),
                  child: const Center(child: Text('Modal Glass', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600))),
                ),
              ),
            ],
          ),
          const SizedBox(height: 20),

          // Cards & Panels
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: GlassCard(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Icon(Icons.album_rounded, color: brandTangerine, size: 20),
                          const SizedBox(width: 8),
                          const Text('2. GlassCard Component', style: TextStyle(fontWeight: FontWeight.bold)),
                        ],
                      ),
                      const SizedBox(height: 6),
                      Text('Continuous 20dp squircle corners with 135-degree specular highlight reflection.',
                          style: TextStyle(color: LofiQColors.textSecondary, fontSize: 12)),
                    ],
                  ),
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: GlassPanel(
                  title: '3. GlassPanel Component',
                  subtitle: 'Heavy-duty section surface',
                  action: GlassIconButton(onPressed: () {}, icon: Icons.tune_rounded, size: 32, iconSize: 16),
                  child: const Text('Panel body with structured header and action slot.', style: TextStyle(fontSize: 12)),
                ),
              ),
            ],
          ),
          const SizedBox(height: 20),

          // Buttons, IconButtons, Switch
          Wrap(
            spacing: 14,
            runSpacing: 12,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              GlassButton(
                isPrimary: true,
                onPressed: () {},
                child: const Text('4. GlassButton Primary'),
              ),
              GlassButton(
                isPrimary: false,
                onPressed: () {},
                child: const Text('Secondary Button'),
              ),
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Text('5. GlassIconButton: ', style: TextStyle(fontSize: 12)),
                  GlassIconButton(onPressed: () {}, icon: Icons.play_arrow_rounded, iconColor: brandTangerine),
                  const SizedBox(width: 6),
                  GlassIconButton(onPressed: () {}, icon: Icons.favorite_rounded, iconColor: brandVanilla),
                ],
              ),
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Text('14. GlassSwitch: ', style: TextStyle(fontSize: 12)),
                  GlassSwitch(value: true, onChanged: (_) {}),
                  const SizedBox(width: 8),
                  GlassSwitch(value: false, onChanged: (_) {}),
                ],
              ),
            ],
          ),
          const SizedBox(height: 20),

          // Input, SegmentedControl, Slider
          Row(
            children: [
              const Expanded(
                flex: 4,
                child: GlassInput(
                  hintText: '12. GlassInput with active specular border...',
                  prefixIcon: Icons.search_rounded,
                ),
              ),
              const SizedBox(width: 16),
              Expanded(
                flex: 4,
                child: GlassSegmentedControl(
                  selectedIndex: 0,
                  items: const ['Top Tracks', 'Albums', 'Playlists'],
                  onSelect: (_) {},
                ),
              ),
              const SizedBox(width: 16),
              Expanded(
                flex: 3,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('13. GlassSlider (75%)', style: TextStyle(fontSize: 11)),
                    GlassSlider(value: 0.75, onChanged: (_) {}),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
    );
    await captureFrame(tester, specimenDashboard, '14_components_specimen_sheet.png', size: const Size(1280, 960));

    print('=== ALL 14 VISUAL SCREENSHOTS CAPTURED SUCCESSFULLY ===');
  });
}

import 'package:flutter/material.dart';
import '../../core/theme/colors.dart';
import '../../playback/playback_controller.dart';
import '../../playback/equalizer/equalizer_controller.dart';
import '../components/dynamic_ambient_background.dart';
import '../components/glass/glass_components.dart';
import '../components/mini_player.dart';
import '../components/window_titlebar.dart';
import '../screens/home_screen.dart';
import '../screens/explore_screen.dart';
import '../screens/search_screen.dart';
import '../screens/library_screen.dart';
import '../screens/local_music_screen.dart';
import '../screens/equalizer_screen.dart';
import '../screens/spotify_import_screen.dart';
import '../screens/settings_screen.dart';
import '../screens/diagnostics_screen.dart';
import '../screens/lyrics_screen.dart';
import '../screens/queue_screen.dart';

enum DesktopSidePanel { none, queue, lyrics }

class DesktopShell extends StatefulWidget {
  final EqualizerController equalizerController;

  const DesktopShell({
    super.key,
    required this.equalizerController,
  });

  @override
  State<DesktopShell> createState() => _DesktopShellState();
}

class _DesktopShellState extends State<DesktopShell> {
  int _navIndex = 0;
  DesktopSidePanel _sidePanel = DesktopSidePanel.none;

  void _onSelectNav(int index) {
    setState(() {
      _navIndex = index;
    });
  }

  void _toggleSidePanel(DesktopSidePanel panel) {
    setState(() {
      if (_sidePanel == panel) {
        _sidePanel = DesktopSidePanel.none;
      } else {
        _sidePanel = panel;
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: LofiQColors.backgroundBlack,
      body: DynamicAmbientBackground(
        child: Column(
          children: [
            // Custom Windows Titlebar
            const WindowTitlebar(),

            // Body: Nav Rail + Content Viewport + Optional Side Panel
            Expanded(
              child: Row(
                children: [
                  // --- LEFT NAVIGATION RAIL ---
                  _buildNavRail(),

                  // Vertical Divider
                  Container(
                    width: 1.0,
                    color: Colors.white.withValues(alpha: 0.08),
                  ),

                  // --- CENTER MAIN CONTENT VIEWPORT ---
                  Expanded(
                    child: AnimatedSwitcher(
                      duration: const Duration(milliseconds: 200),
                      child: _buildScreen(_navIndex),
                    ),
                  ),

                  // --- OPTIONAL RIGHT SIDE PANEL (Lyrics / Queue) ---
                  if (_sidePanel != DesktopSidePanel.none) ...[
                    Container(
                      width: 1.0,
                      color: Colors.white.withValues(alpha: 0.08),
                    ),
                    SizedBox(
                      width: 380,
                      child: _buildSidePanel(),
                    ),
                  ],
                ],
              ),
            ),

            // --- BOTTOM PERSISTENT PLAYER BAR ---
            DesktopPlayerBar(
              onOpenQueue: () => _toggleSidePanel(DesktopSidePanel.queue),
              onOpenLyrics: () => _toggleSidePanel(DesktopSidePanel.lyrics),
              onOpenEqualizer: () => _onSelectNav(5),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildNavRail() {
    return GlassNavigationSurface(
      cornerRadius: 0.0,
      child: Container(
        width: 230,
        padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 20.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // LofiQ Branding Logo
            Padding(
              padding: const EdgeInsets.only(left: 8.0, bottom: 24.0),
              child: Row(
                children: [
                  Container(
                    width: 34,
                    height: 34,
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(9.0),
                      boxShadow: [
                        BoxShadow(
                          color: brandTangerine.withOpacity(0.45),
                          blurRadius: 10,
                          spreadRadius: 1,
                        ),
                      ],
                    ),
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(9.0),
                      child: Image.asset(
                        'assets/images/app_logo.png',
                        width: 34,
                        height: 34,
                        fit: BoxFit.cover,
                        errorBuilder: (_, _, _) => Container(
                          decoration: BoxDecoration(
                            gradient: const LinearGradient(
                              colors: [brandTangerine, brandBrunswickGreen],
                              begin: Alignment.topLeft,
                              end: Alignment.bottomRight,
                            ),
                            borderRadius: BorderRadius.circular(9.0),
                          ),
                          child: const Center(
                            child: Icon(Icons.headphones_rounded, color: Colors.white, size: 20),
                          ),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  const Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'LofiQ',
                        style: TextStyle(
                          fontSize: 20,
                          fontWeight: FontWeight.w900,
                          letterSpacing: -0.5,
                          color: Colors.white,
                        ),
                      ),
                      Text(
                        'DESKTOP V1.0.3',
                        style: TextStyle(
                          fontSize: 9,
                          fontWeight: FontWeight.w700,
                          letterSpacing: 1.2,
                          color: brandTangerine,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),

            // Main Navigation Items
            _buildNavSectionTitle('MAIN'),
            _buildNavItem(0, 'Home', Icons.home_rounded),
            _buildNavItem(1, 'Explore', Icons.explore_rounded),
            _buildNavItem(2, 'Search', Icons.search_rounded),
            _buildNavItem(3, 'Library', Icons.library_music_rounded),
            _buildNavItem(4, 'On-Device Audio', Icons.folder_special_rounded),

            const SizedBox(height: 16),
            _buildNavSectionTitle('TOOLS & DSP'),
            _buildNavItem(5, 'Equalizer & DSP', Icons.graphic_eq_rounded),
            _buildNavItem(6, 'Spotify Importer', Icons.import_export_rounded),

            const Spacer(),

            _buildNavSectionTitle('SYSTEM'),
            _buildNavItem(7, 'Settings', Icons.settings_rounded),
            _buildNavItem(8, 'Diagnostics', Icons.network_check_rounded),
          ],
        ),
      ),
    );
  }

  Widget _buildNavSectionTitle(String title) {
    return Padding(
      padding: const EdgeInsets.only(left: 12.0, bottom: 8.0, top: 4.0),
      child: Text(
        title,
        style: const TextStyle(
          color: LofiQColors.textTertiary,
          fontSize: 10,
          fontWeight: FontWeight.w800,
          letterSpacing: 1.1,
        ),
      ),
    );
  }

  Widget _buildNavItem(int index, String label, IconData icon) {
    final isSelected = _navIndex == index;

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2.0),
      child: GlassSurface(
        preset: isSelected ? GlassPreset.light : GlassPreset.subtle,
        borderRadius: BorderRadius.circular(10.0),
        hasGlow: isSelected,
        glowColor: brandTangerine,
        onTap: () => _onSelectNav(index),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12.0, vertical: 10.0),
          child: Row(
            children: [
              Icon(
                icon,
                size: 20,
                color: isSelected ? brandTangerine : LofiQColors.textSecondary,
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Text(
                  label,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
                    color: isSelected ? Colors.white : LofiQColors.textSecondary,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildScreen(int index) {
    final playback = PlaybackController.instance;

    switch (index) {
      case 0:
        return HomeScreen(
          onNavigateTab: (tab) {
            if (tab == 'search') _onSelectNav(2);
            else if (tab == 'library') _onSelectNav(3);
            else if (tab == 'explore') _onSelectNav(1);
          },
          onOpenSpotifyImport: () => _onSelectNav(6),
        );
      case 1:
        return ExploreScreen(playbackController: playback);
      case 2:
        return const SearchScreen();
      case 3:
        return LibraryScreen(
          onOpenLocalMusic: () => _onSelectNav(4),
        );
      case 4:
        return LocalMusicScreen(playbackController: playback);
      case 5:
        return EqualizerScreen(equalizerController: widget.equalizerController);
      case 6:
        return SpotifyImportScreen(playbackController: playback);
      case 7:
        return SettingsScreen(onOpenDiagnostics: () => _onSelectNav(8));
      case 8:
        return const DiagnosticsScreen();
      default:
        return HomeScreen(
          onNavigateTab: (tab) {},
          onOpenSpotifyImport: () => _onSelectNav(6),
        );
    }
  }

  Widget _buildSidePanel() {
    final playback = PlaybackController.instance;

    return Container(
      color: LofiQColors.surfaceBlack.withValues(alpha: 0.92),
      child: Column(
        children: [
          // Side panel header with close button
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 12.0),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  _sidePanel == DesktopSidePanel.queue ? 'Current Queue' : 'Synchronized Lyrics',
                  style: const TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.bold,
                    fontSize: 16,
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.close_rounded, size: 20, color: LofiQColors.textSecondary),
                  onPressed: () => setState(() => _sidePanel = DesktopSidePanel.none),
                  tooltip: 'Close panel',
                ),
              ],
            ),
          ),
          const Divider(color: LofiQColors.surfaceBorder, height: 1),
          Expanded(
            child: _sidePanel == DesktopSidePanel.queue
                ? QueueScreen(playbackController: playback)
                : LyricsScreen(playbackController: playback),
          ),
        ],
      ),
    );
  }
}

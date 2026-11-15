import 'package:flutter/material.dart';
import '../../core/services/window_service.dart';
import '../../core/theme/colors.dart';
import '../../playback/playback_controller.dart';

class WindowTitlebar extends StatefulWidget {
  const WindowTitlebar({super.key});

  @override
  State<WindowTitlebar> createState() => _WindowTitlebarState();
}

class _WindowTitlebarState extends State<WindowTitlebar> {
  bool _isHoveringClose = false;
  bool _isHoveringMin = false;
  bool _isHoveringMax = false;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onPanStart: (_) => WindowService.startDragging(),
      onDoubleTap: () => WindowService.maximize(),
      child: Container(
        height: 38.0,
        decoration: BoxDecoration(
          color: const Color(0xFF070707),
          border: Border(
            bottom: BorderSide(
              color: Colors.white.withValues(alpha: 0.08),
              width: 1.0,
            ),
          ),
        ),
        child: Row(
          children: [
            const SizedBox(width: 12.0),
            // LofiQ Branding Logo
            ClipRRect(
              borderRadius: BorderRadius.circular(6.0),
              child: Image.asset(
                'assets/images/app_logo.png',
                width: 22,
                height: 22,
                fit: BoxFit.cover,
                errorBuilder: (_, __, ___) => Container(
                  width: 22,
                  height: 22,
                  decoration: BoxDecoration(
                    gradient: const LinearGradient(
                      colors: [LofiQColors.brandPurple, LofiQColors.brandTangerine],
                    ),
                    borderRadius: BorderRadius.circular(6.0),
                  ),
                  child: const Icon(Icons.headphones_rounded, size: 14, color: Colors.white),
                ),
              ),
            ),
            const SizedBox(width: 10.0),
            const Text(
              'LofiQ Music',
              style: TextStyle(
                fontSize: 12.0,
                fontWeight: FontWeight.w700,
                color: Colors.white,
                letterSpacing: 0.2,
              ),
            ),
            const SizedBox(width: 8.0),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 6.0, vertical: 1.5),
              decoration: BoxDecoration(
                color: LofiQColors.brandTangerine.withValues(alpha: 0.15),
                borderRadius: BorderRadius.circular(4.0),
                border: Border.all(
                  color: LofiQColors.brandTangerine.withValues(alpha: 0.35),
                  width: 0.8,
                ),
              ),
              child: const Text(
                'V1.0.5 Desktop',
                style: TextStyle(
                  fontSize: 9.5,
                  fontWeight: FontWeight.w600,
                  color: LofiQColors.brandTangerine,
                  letterSpacing: 0.5,
                ),
              ),
            ),

            // Center Draggable Now-Playing Ticker
            Expanded(
              child: GestureDetector(
                behavior: HitTestBehavior.translucent,
                onPanStart: (_) => WindowService.startDragging(),
                onDoubleTap: () => WindowService.maximize(),
                child: ListenableBuilder(
                  listenable: PlaybackController.instance,
                  builder: (context, _) {
                    final song = PlaybackController.instance.state.currentSong;
                    if (song == null) return const SizedBox.shrink();
                    return Center(
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(
                            Icons.graphic_eq_rounded,
                            size: 14,
                            color: PlaybackController.instance.state.isPlaying
                                ? LofiQColors.brandPurpleLight
                                : LofiQColors.textTertiary,
                          ),
                          const SizedBox(width: 6),
                          Flexible(
                            child: Text(
                              '${song.title} • ${song.artist}',
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                fontSize: 11.5,
                                color: LofiQColors.textSecondary,
                                fontWeight: FontWeight.w500,
                              ),
                            ),
                          ),
                        ],
                      ),
                    );
                  },
                ),
              ),
            ),

            // Functional Window Controls (Minimize, Maximize, Close)
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                _buildWindowButton(
                  icon: Icons.remove,
                  tooltip: 'Minimize',
                  isHovering: _isHoveringMin,
                  onHoverChange: (h) => setState(() => _isHoveringMin = h),
                  onTap: () => WindowService.minimize(),
                ),
                _buildWindowButton(
                  icon: Icons.crop_square_rounded,
                  tooltip: 'Maximize',
                  isHovering: _isHoveringMax,
                  onHoverChange: (h) => setState(() => _isHoveringMax = h),
                  onTap: () => WindowService.maximize(),
                ),
                _buildWindowButton(
                  icon: Icons.close_rounded,
                  tooltip: 'Close',
                  isHovering: _isHoveringClose,
                  isClose: true,
                  onHoverChange: (h) => setState(() => _isHoveringClose = h),
                  onTap: () => WindowService.close(),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildWindowButton({
    required IconData icon,
    required String tooltip,
    required bool isHovering,
    required ValueChanged<bool> onHoverChange,
    required VoidCallback onTap,
    bool isClose = false,
  }) {
    Color bg = Colors.transparent;
    Color iconColor = LofiQColors.textSecondary;

    if (isHovering) {
      if (isClose) {
        bg = const Color(0xFFE81123);
        iconColor = Colors.white;
      } else {
        bg = Colors.white.withValues(alpha: 0.1);
        iconColor = Colors.white;
      }
    }

    return MouseRegion(
      onEnter: (_) => onHoverChange(true),
      onExit: (_) => onHoverChange(false),
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: onTap,
        child: Tooltip(
          message: tooltip,
          waitDuration: const Duration(milliseconds: 600),
          child: Container(
            width: 44,
            height: 38,
            alignment: Alignment.center,
            color: bg,
            child: Icon(icon, size: 14, color: iconColor),
          ),
        ),
      ),
    );
  }
}

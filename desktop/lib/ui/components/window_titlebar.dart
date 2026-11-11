import 'dart:io';
import 'package:flutter/material.dart';
import '../../core/theme/colors.dart';
import '../../playback/playback_controller.dart';

class WindowsTitlebar extends StatelessWidget {
  const WindowsTitlebar({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 38.0,
      padding: const EdgeInsets.symmetric(horizontal: 14.0),
      color: LofiQColors.backgroundBlack,
      child: Row(
        children: [
          // LofiQ icon & title
          ClipRRect(
            borderRadius: BorderRadius.circular(5.0),
            child: Image.asset(
              'assets/images/app_logo.png',
              width: 22,
              height: 22,
              fit: BoxFit.cover,
              errorBuilder: (_, _, _) => Container(
                width: 22,
                height: 22,
                decoration: BoxDecoration(
                  gradient: const LinearGradient(
                    colors: [LofiQColors.brandPurple, LofiQColors.brandPurpleLight],
                  ),
                  borderRadius: BorderRadius.circular(5.0),
                ),
                child: const Icon(Icons.graphic_eq_rounded, size: 14, color: Colors.white),
              ),
            ),
          ),
          const SizedBox(width: 10.0),
          const Text(
            'LofiQ Music',
            style: TextStyle(
              fontSize: 12.0,
              fontWeight: FontWeight.w700,
              color: LofiQColors.textPrimary,
              letterSpacing: 0.2,
            ),
          ),
          const SizedBox(width: 8.0),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 5.0, vertical: 1.5),
            decoration: BoxDecoration(
              color: Colors.white.withValues(alpha: 0.08),
              borderRadius: BorderRadius.circular(4.0),
            ),
            child: const Text(
              'V1.0.2 Desktop',
              style: TextStyle(fontSize: 10, color: LofiQColors.textTertiary),
            ),
          ),

          // Center now-playing ticker in titlebar
          Expanded(
            child: ListenableBuilder(
              listenable: PlaybackController.instance,
              builder: (context, _) {
                final song = PlaybackController.instance.state.currentSong;
                if (song == null) return const SizedBox.shrink();
                return Center(
                  child: Text(
                    '${song.title} • ${song.artist}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 11.5,
                      color: LofiQColors.textTertiary,
                    ),
                  ),
                );
              },
            ),
          ),

          // Right decorative window controls
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              _winButton(Icons.remove, () {}),
              _winButton(Icons.crop_square, () {}),
              _winButton(Icons.close, () => exit(0), isClose: true),
            ],
          ),
        ],
      ),
    );
  }

  Widget _winButton(IconData icon, VoidCallback onTap, {bool isClose = false}) {
    return InkWell(
      onTap: onTap,
      child: Container(
        width: 32,
        height: 38,
        alignment: Alignment.center,
        child: Icon(
          icon,
          size: 14,
          color: LofiQColors.textSecondary,
        ),
      ),
    );
  }
}

typedef WindowTitlebar = WindowsTitlebar;

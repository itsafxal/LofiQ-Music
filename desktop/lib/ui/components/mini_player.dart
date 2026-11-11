import 'dart:ui';
import 'package:flutter/material.dart';
import '../../core/database/local_storage.dart';
import '../../core/models/player_state.dart';
import '../../core/preferences/preferences_manager.dart';
import '../../core/theme/colors.dart';
import '../../core/utils/time_format.dart';
import '../../playback/playback_controller.dart';
import 'animated_waveform_seekbar.dart';

class DesktopPlayerBar extends StatefulWidget {
  final VoidCallback onOpenQueue;
  final VoidCallback onOpenLyrics;
  final VoidCallback onOpenEqualizer;

  const DesktopPlayerBar({
    super.key,
    required this.onOpenQueue,
    required this.onOpenLyrics,
    required this.onOpenEqualizer,
  });

  @override
  State<DesktopPlayerBar> createState() => _DesktopPlayerBarState();
}

class _DesktopPlayerBarState extends State<DesktopPlayerBar> {
  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: PlaybackController.instance,
      builder: (context, _) {
        final state = PlaybackController.instance.state;
        final currentSong = state.currentSong;

        if (currentSong == null) {
          return const SizedBox.shrink();
        }

        final isFav = LocalStorage.instance.isFavorite(currentSong.id);
        final seekbarStyle = PreferencesManager.instance.settings.seekbarStyle;

        return ClipRRect(
          child: BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 20.0, sigmaY: 20.0),
            child: Container(
              height: 84.0,
              padding: const EdgeInsets.symmetric(horizontal: 20.0),
              decoration: BoxDecoration(
                color: LofiQColors.surfaceBlack.withValues(alpha: 0.85),
                border: Border(
                  top: BorderSide(
                    color: Colors.white.withValues(alpha: 0.12),
                    width: 1.0,
                  ),
                ),
              ),
              child: Row(
                children: [
                  // --- LEFT: TRACK METADATA ---
                  Expanded(
                    flex: 3,
                    child: Row(
                      children: [
                        ClipRRect(
                          borderRadius: BorderRadius.circular(8.0),
                          child: Container(
                            width: 52,
                            height: 52,
                            color: LofiQColors.surfaceCard,
                            child: currentSong.artworkUrl.isNotEmpty
                                ? Image.network(
                                    currentSong.artworkUrl,
                                    fit: BoxFit.cover,
                                    errorBuilder: (_, __, ___) => const Icon(Icons.music_note, color: LofiQColors.textTertiary),
                                  )
                                : const Icon(Icons.music_note, color: LofiQColors.textTertiary),
                          ),
                        ),
                        const SizedBox(width: 12.0),
                        Expanded(
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                currentSong.title,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(
                                  fontSize: 14.0,
                                  fontWeight: FontWeight.w600,
                                  color: LofiQColors.textPrimary,
                                ),
                              ),
                              const SizedBox(height: 3.0),
                              Text(
                                currentSong.artist,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(
                                  fontSize: 12.0,
                                  color: LofiQColors.textSecondary,
                                ),
                              ),
                            ],
                          ),
                        ),
                        IconButton(
                          icon: Icon(
                            isFav ? Icons.favorite_rounded : Icons.favorite_border_rounded,
                            size: 20,
                            color: isFav ? LofiQColors.accentRed : LofiQColors.textTertiary,
                          ),
                          onPressed: () {
                            setState(() {
                              LocalStorage.instance.setFavorite(currentSong.id, !isFav);
                            });
                          },
                        ),
                      ],
                    ),
                  ),

                  // --- CENTER: CONTROLS & SEEKBAR ---
                  Expanded(
                    flex: 6,
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        // Buttons row
                        Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            IconButton(
                              icon: Icon(
                                Icons.shuffle_rounded,
                                size: 18,
                                color: state.shuffleEnabled ? LofiQColors.brandPurpleLight : LofiQColors.textTertiary,
                              ),
                              onPressed: PlaybackController.instance.toggleShuffle,
                              tooltip: 'Shuffle',
                            ),
                            IconButton(
                              icon: const Icon(Icons.skip_previous_rounded, size: 24, color: LofiQColors.textPrimary),
                              onPressed: PlaybackController.instance.previous,
                              tooltip: 'Previous',
                            ),
                            const SizedBox(width: 6.0),
                            FloatingActionButton.small(
                              onPressed: PlaybackController.instance.togglePlayPause,
                              backgroundColor: LofiQColors.textPrimary,
                              elevation: 2,
                              child: state.isLoading
                                  ? const SizedBox(
                                      width: 18,
                                      height: 18,
                                      child: CircularProgressIndicator(strokeWidth: 2, color: Colors.black),
                                    )
                                  : Icon(
                                      state.isPlaying ? Icons.pause_rounded : Icons.play_arrow_rounded,
                                      color: Colors.black,
                                      size: 26,
                                    ),
                            ),
                            const SizedBox(width: 6.0),
                            IconButton(
                              icon: const Icon(Icons.skip_next_rounded, size: 24, color: LofiQColors.textPrimary),
                              onPressed: PlaybackController.instance.next,
                              tooltip: 'Next',
                            ),
                            IconButton(
                              icon: Icon(
                                state.repeatMode == PlaybackRepeatMode.one
                                    ? Icons.repeat_one_rounded
                                    : Icons.repeat_rounded,
                                size: 18,
                                color: state.repeatMode != PlaybackRepeatMode.off
                                    ? LofiQColors.brandPurpleLight
                                    : LofiQColors.textTertiary,
                              ),
                              onPressed: PlaybackController.instance.cycleRepeatMode,
                              tooltip: 'Repeat (${state.repeatMode.name})',
                            ),
                          ],
                        ),

                        // Waveform Seekbar row
                        Row(
                          children: [
                            Text(
                              TimeFormatUtils.formatDurationMs(state.currentPositionMs),
                              style: const TextStyle(fontSize: 11.0, color: LofiQColors.textTertiary),
                            ),
                            const SizedBox(width: 8.0),
                            Expanded(
                              child: AnimatedWaveformSeekbar(
                                currentPositionMs: state.currentPositionMs,
                                durationMs: state.durationMs,
                                isPlaying: state.isPlaying,
                                style: seekbarStyle,
                                onSeek: PlaybackController.instance.seekTo,
                              ),
                            ),
                            const SizedBox(width: 8.0),
                            Text(
                              TimeFormatUtils.formatDurationMs(state.durationMs),
                              style: const TextStyle(fontSize: 11.0, color: LofiQColors.textTertiary),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),

                  // --- RIGHT: QUICK TOOLS & VOLUME ---
                  Expanded(
                    flex: 3,
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.end,
                      children: [
                        IconButton(
                          icon: const Icon(Icons.lyrics_rounded, size: 19, color: LofiQColors.textSecondary),
                          onPressed: widget.onOpenLyrics,
                          tooltip: 'Real-Time Synced Lyrics',
                        ),
                        IconButton(
                          icon: const Icon(Icons.equalizer_rounded, size: 19, color: LofiQColors.textSecondary),
                          onPressed: widget.onOpenEqualizer,
                          tooltip: 'Equalizer & Audio DSP',
                        ),
                        IconButton(
                          icon: const Icon(Icons.queue_music_rounded, size: 20, color: LofiQColors.textSecondary),
                          onPressed: widget.onOpenQueue,
                          tooltip: 'Play Queue (${state.queue.length})',
                        ),
                        const SizedBox(width: 8.0),

                        // Volume icon & slider
                        Icon(
                          state.volume > 0.5
                              ? Icons.volume_up_rounded
                              : (state.volume > 0 ? Icons.volume_down_rounded : Icons.volume_off_rounded),
                          size: 18,
                          color: LofiQColors.textSecondary,
                        ),
                        SizedBox(
                          width: 80,
                          child: SliderTheme(
                            data: SliderThemeData(
                              thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 5),
                              trackHeight: 3,
                              activeTrackColor: LofiQColors.textPrimary,
                              inactiveTrackColor: Colors.white.withValues(alpha: 0.15),
                              thumbColor: LofiQColors.textPrimary,
                            ),
                            child: Slider(
                              value: state.volume,
                              onChanged: PlaybackController.instance.setVolume,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}


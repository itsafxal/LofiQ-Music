import 'package:flutter/material.dart';
import '../../core/theme/colors.dart';
import '../../core/utils/time_format.dart';
import '../../core/database/local_storage.dart';
import '../../playback/playback_controller.dart';
import '../components/glass/glass_components.dart';

class QueueScreen extends StatefulWidget {
  final PlaybackController playbackController;

  const QueueScreen({Key? key, required this.playbackController}) : super(key: key);

  @override
  State<QueueScreen> createState() => _QueueScreenState();
}

class _QueueScreenState extends State<QueueScreen> {
  final LocalStorage _storage = LocalStorage();

  @override
  void initState() {
    super.initState();
    widget.playbackController.addListener(_onQueueChanged);
  }

  @override
  void dispose() {
    widget.playbackController.removeListener(_onQueueChanged);
    super.dispose();
  }

  void _onQueueChanged() {
    if (mounted) setState(() {});
  }

  Future<void> _saveQueueAsPlaylist() async {
    final queue = widget.playbackController.state.queue;
    if (queue.isEmpty) return;

    final controller = TextEditingController(text: 'Queue ${DateTime.now().month}/${DateTime.now().day}');
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => GlassDialog(
        title: 'Save Queue as Playlist',
        content: GlassInput(
          controller: controller,
          autofocus: true,
          hintText: 'Enter playlist title...',
        ),
        actions: [
          GlassButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel', style: TextStyle(color: LofiQColors.textSecondary)),
          ),
          GlassButton(
            isPrimary: true,
            accentColor: brandTangerine,
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Save'),
          ),
        ],
      ),
    );

    if (confirmed == true && controller.text.trim().isNotEmpty) {
      final playlist = await _storage.createPlaylist(controller.text.trim());
      await _storage.addTracksToPlaylist(playlist.id, queue.map((s) => s.id).toList());
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Playlist "${controller.text.trim()}" created with ${queue.length} tracks'),
            backgroundColor: LofiQColors.cardBackground,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = widget.playbackController.state;
    final queue = state.queue;
    final currentIndex = state.currentIndex;
    final currentSong = state.currentSong;

    return Scaffold(
      backgroundColor: Colors.transparent,
      body: LayoutBuilder(
        builder: (context, constraints) {
          final isCompact = constraints.maxWidth < 580;
          final contentPadding = isCompact ? const EdgeInsets.all(16.0) : const EdgeInsets.all(28.0);

          return Padding(
            padding: contentPadding,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Top Bar
                Row(
                  children: [
                    Container(
                      padding: EdgeInsets.all(isCompact ? 8.0 : 12.0),
                      decoration: BoxDecoration(
                        color: LofiQColors.neonPurple.withOpacity(0.15),
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: LofiQColors.neonPurple.withOpacity(0.3)),
                      ),
                      child: Icon(Icons.queue_music_rounded, color: LofiQColors.neonPurple, size: isCompact ? 20.0 : 28.0),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            isCompact ? 'Queue' : 'Playback Queue',
                            style: isCompact
                                ? const TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: LofiQColors.textPrimary)
                                : Theme.of(context).textTheme.headlineMedium?.copyWith(
                                    color: LofiQColors.textPrimary,
                                    fontWeight: FontWeight.bold,
                                  ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            '${queue.length} tracks • ${isCompact ? "Manage & reorder" : "Reorder, manage, or export to a playlist"}',
                            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                              color: LofiQColors.textSecondary,
                              fontSize: 12,
                            ),
                          ),
                        ],
                      ),
                    ),
                    // Actions
                    if (isCompact) ...[
                      GlassIconButton(
                        tooltip: 'Save as Playlist',
                        onPressed: queue.isEmpty ? null : _saveQueueAsPlaylist,
                        icon: Icons.playlist_add_rounded,
                        iconColor: brandTangerine,
                      ),
                    ] else ...[
                      GlassButton(
                        onPressed: queue.isEmpty ? null : _saveQueueAsPlaylist,
                        isPrimary: true,
                        accentColor: brandTangerine,
                        child: const Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(Icons.playlist_add_rounded, size: 18),
                            SizedBox(width: 8),
                            Text('Save as Playlist'),
                          ],
                        ),
                      ),
                    ],
                    const SizedBox(width: 8),
                    GlassIconButton(
                      tooltip: 'Shuffle Queue',
                      onPressed: queue.isEmpty ? null : () => widget.playbackController.shuffleQueue(),
                      icon: Icons.shuffle_rounded,
                      iconColor: brandTangerine,
                    ),
                    const SizedBox(width: 6),
                    GlassIconButton(
                      tooltip: 'Clear Queue',
                      onPressed: queue.isEmpty ? null : () => widget.playbackController.clearQueue(),
                      icon: Icons.clear_all_rounded,
                      iconColor: Colors.redAccent,
                    ),
                  ],
                ),
                const SizedBox(height: 20),

            // Now Playing Card
            if (currentSong != null) ...[
              const Text(
                'NOW PLAYING',
                style: TextStyle(
                  color: brandTangerine,
                  fontSize: 12,
                  fontWeight: FontWeight.bold,
                  letterSpacing: 1.2,
                ),
              ),
              const SizedBox(height: 10),
              GlassCard(
                preset: GlassPreset.elevated,
                hasGlow: true,
                glowColor: brandTangerine,
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                child: Row(
                  children: [
                    Container(
                      width: 52,
                      height: 52,
                      decoration: BoxDecoration(
                        color: LofiQColors.cardBackground,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(10),
                        child: currentSong.artworkUrl.isNotEmpty
                            ? Image.network(
                                currentSong.artworkUrl,
                                fit: BoxFit.cover,
                                errorBuilder: (_, __, ___) => const Icon(Icons.music_note_rounded, color: LofiQColors.neonPurple),
                              )
                            : const Icon(Icons.music_note_rounded, color: LofiQColors.neonPurple),
                      ),
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            currentSong.title,
                            style: const TextStyle(
                              color: LofiQColors.textPrimary,
                              fontWeight: FontWeight.bold,
                              fontSize: 16,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                          const SizedBox(height: 4),
                          Text(
                            currentSong.artist,
                            style: const TextStyle(color: LofiQColors.textSecondary, fontSize: 13),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ],
                      ),
                    ),
                    Text(
                      TimeFormatUtils.formatDurationMs(currentSong.durationMs),
                      style: const TextStyle(color: LofiQColors.textSecondary, fontSize: 13),
                    ),
                    const SizedBox(width: 16),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                      decoration: BoxDecoration(
                        color: LofiQColors.neonPurple.withOpacity(0.2),
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: LofiQColors.neonPurple.withOpacity(0.4)),
                      ),
                      child: const Row(
                        children: [
                          Icon(Icons.graphic_eq_rounded, size: 14, color: LofiQColors.neonPurple),
                          SizedBox(width: 6),
                          Text(
                            'Active',
                            style: TextStyle(color: LofiQColors.neonPurple, fontSize: 11, fontWeight: FontWeight.bold),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),
            ],

            // Up Next Header
            Text(
              'UP NEXT (${queue.length})',
              style: const TextStyle(
                color: LofiQColors.textSecondary,
                fontSize: 12,
                fontWeight: FontWeight.bold,
                letterSpacing: 1.2,
              ),
            ),
            const SizedBox(height: 12),

            // Queue List (Reorderable)
            Expanded(
              child: queue.isEmpty
                  ? Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(Icons.queue_music_outlined, size: 64, color: Colors.white.withOpacity(0.2)),
                          const SizedBox(height: 16),
                          const Text(
                            'Queue is empty. Add songs from Home, Search, or Library.',
                            style: TextStyle(color: LofiQColors.textSecondary, fontSize: 15),
                          ),
                        ],
                      ),
                    )
                  : ReorderableListView.builder(
                      itemCount: queue.length,
                      onReorder: (oldIndex, newIndex) {
                        widget.playbackController.reorderQueue(oldIndex, newIndex);
                      },
                      itemBuilder: (context, index) {
                        final song = queue[index];
                        final isNowPlaying = index == currentIndex;

                        return Container(
                          key: ValueKey('${song.id}_$index'),
                          margin: const EdgeInsets.only(bottom: 8),
                          child: GlassCard(
                            preset: isNowPlaying ? GlassPreset.light : GlassPreset.subtle,
                            hasGlow: isNowPlaying,
                            glowColor: brandTangerine,
                            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                            child: Row(
                              children: [
                                ReorderableDragStartListener(
                                  index: index,
                                  child: const Icon(Icons.drag_indicator_rounded, color: LofiQColors.textSecondary, size: 20),
                                ),
                                const SizedBox(width: 12),
                                ClipRRect(
                                  borderRadius: BorderRadius.circular(8),
                                  child: song.artworkUrl.isNotEmpty
                                      ? Image.network(
                                          song.artworkUrl,
                                          width: 40,
                                          height: 40,
                                          fit: BoxFit.cover,
                                          errorBuilder: (_, __, ___) => _buildPlaceholder(),
                                        )
                                      : _buildPlaceholder(),
                                ),
                                const SizedBox(width: 14),
                                Expanded(
                                  child: InkWell(
                                    onTap: () => widget.playbackController.skipToIndex(index),
                                    child: Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        Text(
                                          song.title,
                                          style: TextStyle(
                                            color: isNowPlaying ? brandTangerine : LofiQColors.textPrimary,
                                            fontWeight: isNowPlaying ? FontWeight.bold : FontWeight.normal,
                                            fontSize: 14,
                                          ),
                                          maxLines: 1,
                                          overflow: TextOverflow.ellipsis,
                                        ),
                                        const SizedBox(height: 2),
                                        Text(
                                          song.artist,
                                          style: const TextStyle(color: LofiQColors.textSecondary, fontSize: 12),
                                          maxLines: 1,
                                          overflow: TextOverflow.ellipsis,
                                        ),
                                      ],
                                    ),
                                  ),
                                ),
                                Text(
                                  TimeFormatUtils.formatDurationMs(song.durationMs),
                                  style: const TextStyle(color: LofiQColors.textSecondary, fontSize: 13),
                                ),
                                const SizedBox(width: 8),
                                GlassIconButton(
                                  icon: Icons.close_rounded,
                                  iconSize: 18,
                                  size: 32,
                                  iconColor: LofiQColors.textSecondary,
                                  onPressed: () => widget.playbackController.removeFromQueue(index),
                                  tooltip: 'Remove from queue',
                                ),
                              ],
                            ),
                          ),
                        );
                      },
                    ),
            ),
          ],
        ),
      );
    },
  ),
);
  }

  Widget _buildPlaceholder() {
    return Container(
      width: 40,
      height: 40,
      color: LofiQColors.cardBackground,
      child: const Icon(Icons.music_note_rounded, size: 20, color: LofiQColors.neonPurple),
    );
  }
}


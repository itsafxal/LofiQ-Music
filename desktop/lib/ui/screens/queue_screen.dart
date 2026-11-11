import 'package:flutter/material.dart';
import '../../core/models/song.dart';
import '../../core/models/player_state.dart';
import '../../core/theme/colors.dart';
import '../../core/theme/liquid_glass.dart';
import '../../core/utils/time_format.dart';
import '../../core/database/local_storage.dart';
import '../../playback/playback_controller.dart';
import '../components/glass_card.dart';

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
      builder: (ctx) => AlertDialog(
        backgroundColor: LofiQColors.cardBackground,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: const BorderSide(color: LofiQColors.glassBorderSubtle),
        ),
        title: const Text('Save Queue as Playlist', style: TextStyle(color: LofiQColors.textPrimary)),
        content: TextField(
          controller: controller,
          autofocus: true,
          style: const TextStyle(color: LofiQColors.textPrimary),
          decoration: const InputDecoration(
            hintText: 'Enter playlist title...',
            hintStyle: TextStyle(color: LofiQColors.textSecondary),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel', style: TextStyle(color: LofiQColors.textSecondary)),
          ),
          ElevatedButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: ElevatedButton.styleFrom(backgroundColor: LofiQColors.neonPurple),
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
      body: Padding(
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
                  child: const Icon(Icons.queue_music_rounded, color: LofiQColors.neonPurple, size: 28),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Playback Queue',
                        style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                          color: LofiQColors.textPrimary,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        '${queue.length} tracks • Reorder, manage, or export to a playlist',
                        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                          color: LofiQColors.textSecondary,
                        ),
                      ),
                    ],
                  ),
                ),
                // Actions
                ElevatedButton.icon(
                  onPressed: queue.isEmpty ? null : _saveQueueAsPlaylist,
                  icon: const Icon(Icons.playlist_add_rounded, size: 18),
                  label: const Text('Save as Playlist'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: LofiQColors.cardBackground,
                    foregroundColor: LofiQColors.neonPurple,
                    side: const BorderSide(color: LofiQColors.glassBorderSubtle),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  ),
                ),
                const SizedBox(width: 12),
                IconButton(
                  tooltip: 'Shuffle Queue',
                  onPressed: queue.isEmpty ? null : () => widget.playbackController.shuffleQueue(),
                  icon: const Icon(Icons.shuffle_rounded, color: LofiQColors.neonCyan),
                  style: IconButton.styleFrom(
                    backgroundColor: LofiQColors.cardBackground,
                    side: const BorderSide(color: LofiQColors.glassBorderSubtle),
                  ),
                ),
                const SizedBox(width: 8),
                IconButton(
                  tooltip: 'Clear Queue',
                  onPressed: queue.isEmpty ? null : () => widget.playbackController.clearQueue(),
                  icon: const Icon(Icons.clear_all_rounded, color: Colors.redAccent),
                  style: IconButton.styleFrom(
                    backgroundColor: LofiQColors.cardBackground,
                    side: const BorderSide(color: LofiQColors.glassBorderSubtle),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 24),

            // Now Playing Card
            if (currentSong != null) ...[
              const Text(
                'NOW PLAYING',
                style: TextStyle(
                  color: LofiQColors.neonPurple,
                  fontSize: 12,
                  fontWeight: FontWeight.bold,
                  letterSpacing: 1.2,
                ),
              ),
              const SizedBox(height: 10),
              GlassCard(
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
                        child: currentSong.artworkUrl != null
                            ? Image.network(
                                currentSong.artworkUrl!,
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
                          decoration: BoxDecoration(
                            color: isNowPlaying
                                ? LofiQColors.neonPurple.withOpacity(0.12)
                                : LofiQColors.cardBackground.withOpacity(0.7),
                            borderRadius: BorderRadius.circular(12),
                            border: Border.all(
                              color: isNowPlaying
                                  ? LofiQColors.neonPurple.withOpacity(0.4)
                                  : LofiQColors.glassBorderSubtle,
                            ),
                          ),
                          child: ListTile(
                            contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                            leading: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                ReorderableDragStartListener(
                                  index: index,
                                  child: const Icon(Icons.drag_indicator_rounded, color: LofiQColors.textSecondary, size: 20),
                                ),
                                const SizedBox(width: 12),
                                ClipRRect(
                                  borderRadius: BorderRadius.circular(8),
                                  child: song.artworkUrl != null
                                      ? Image.network(
                                          song.artworkUrl!,
                                          width: 40,
                                          height: 40,
                                          fit: BoxFit.cover,
                                          errorBuilder: (_, __, ___) => _buildPlaceholder(),
                                        )
                                      : _buildPlaceholder(),
                                ),
                              ],
                            ),
                            title: Text(
                              song.title,
                              style: TextStyle(
                                color: isNowPlaying ? LofiQColors.neonCyan : LofiQColors.textPrimary,
                                fontWeight: isNowPlaying ? FontWeight.bold : FontWeight.normal,
                              ),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                            subtitle: Text(
                              song.artist,
                              style: const TextStyle(color: LofiQColors.textSecondary, fontSize: 12),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                            trailing: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Text(
                                  TimeFormatUtils.formatDurationMs(song.durationMs),
                                  style: const TextStyle(color: LofiQColors.textSecondary, fontSize: 13),
                                ),
                                const SizedBox(width: 8),
                                IconButton(
                                  icon: const Icon(Icons.close_rounded, size: 18, color: LofiQColors.textSecondary),
                                  onPressed: () => widget.playbackController.removeFromQueue(index),
                                  tooltip: 'Remove from queue',
                                ),
                              ],
                            ),
                            onTap: () => widget.playbackController.skipToIndex(index),
                          ),
                        );
                      },
                    ),
            ),
          ],
        ),
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


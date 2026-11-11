import 'package:flutter/material.dart';
import '../../core/models/song.dart';
import '../../core/theme/colors.dart';
import '../../core/theme/liquid_glass.dart';
import '../../data/local_audio/local_audio_scanner.dart';
import '../../playback/playback_controller.dart';
import '../components/glass_card.dart';
import '../components/song_row.dart';

class LocalMusicScreen extends StatefulWidget {
  final PlaybackController playbackController;

  const LocalMusicScreen({Key? key, required this.playbackController}) : super(key: key);

  @override
  State<LocalMusicScreen> createState() => _LocalMusicScreenState();
}

class _LocalMusicScreenState extends State<LocalMusicScreen> {
  final LocalAudioScanner _scanner = LocalAudioScanner();
  List<Song> _localSongs = [];
  bool _isScanning = false;
  String _searchFilter = '';

  @override
  void initState() {
    super.initState();
    _loadLocalMusic();
  }

  Future<void> _loadLocalMusic() async {
    setState(() => _isScanning = true);
    try {
      final songs = await _scanner.scanWindowsMusicFolder();
      if (mounted) {
        setState(() {
          _localSongs = songs;
          _isScanning = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isScanning = false);
      }
    }
  }

  List<Song> get _filteredSongs {
    if (_searchFilter.trim().isEmpty) return _localSongs;
    final q = _searchFilter.toLowerCase();
    return _localSongs.where((s) =>
      s.title.toLowerCase().contains(q) ||
      s.artist.toLowerCase().contains(q) ||
      (s.album?.toLowerCase().contains(q) ?? false)
    ).toList();
  }

  void _playAll({bool shuffle = false}) {
    if (_localSongs.isEmpty) return;
    final queue = List<Song>.from(_localSongs);
    if (shuffle) {
      queue.shuffle();
    }
    widget.playbackController.setQueue(queue, 0);
  }

  @override
  Widget build(BuildContext context) {
    final filtered = _filteredSongs;

    return Scaffold(
      backgroundColor: Colors.transparent,
      body: Padding(
        padding: const EdgeInsets.all(28.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Header Bar
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: LofiQColors.neonCyan.withOpacity(0.15),
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: LofiQColors.neonCyan.withOpacity(0.3)),
                  ),
                  child: const Icon(Icons.folder_special_rounded, color: LofiQColors.neonCyan, size: 28),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'On-Device Audio Library',
                        style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                          color: LofiQColors.textPrimary,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        '${_localSongs.length} local audio files discovered in Windows Music directory',
                        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                          color: LofiQColors.textSecondary,
                        ),
                      ),
                    ],
                  ),
                ),
                // Rescan Action Button
                ElevatedButton.icon(
                  onPressed: _isScanning ? null : _loadLocalMusic,
                  icon: _isScanning
                      ? const SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                        )
                      : const Icon(Icons.refresh_rounded, size: 18),
                  label: Text(_isScanning ? 'Scanning...' : 'Rescan Folder'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: LofiQColors.cardBackground,
                    foregroundColor: LofiQColors.neonCyan,
                    side: const BorderSide(color: LofiQColors.glassBorderSubtle),
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  ),
                ),
                const SizedBox(width: 12),
                ElevatedButton.icon(
                  onPressed: _localSongs.isEmpty ? null : () => _playAll(shuffle: false),
                  icon: const Icon(Icons.play_arrow_rounded, size: 20),
                  label: const Text('Play All'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: LofiQColors.neonPurple,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  ),
                ),
                const SizedBox(width: 8),
                IconButton(
                  tooltip: 'Shuffle All',
                  onPressed: _localSongs.isEmpty ? null : () => _playAll(shuffle: true),
                  icon: const Icon(Icons.shuffle_rounded, color: LofiQColors.neonCyan),
                  style: IconButton.styleFrom(
                    backgroundColor: LofiQColors.cardBackground,
                    side: const BorderSide(color: LofiQColors.glassBorderSubtle),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 20),

            // Search Bar Filter
            GlassCard(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
              child: TextField(
                style: const TextStyle(color: LofiQColors.textPrimary),
                onChanged: (val) => setState(() => _searchFilter = val),
                decoration: InputDecoration(
                  icon: const Icon(Icons.search_rounded, color: LofiQColors.textSecondary),
                  hintText: 'Filter local tracks by title, artist, or album...',
                  hintStyle: const TextStyle(color: LofiQColors.textSecondary),
                  border: InputBorder.none,
                  suffixIcon: _searchFilter.isNotEmpty
                      ? IconButton(
                          icon: const Icon(Icons.close_rounded, color: LofiQColors.textSecondary),
                          onPressed: () => setState(() => _searchFilter = ''),
                        )
                      : null,
                ),
              ),
            ),
            const SizedBox(height: 20),

            // Song List
            Expanded(
              child: _isScanning
                  ? const Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          CircularProgressIndicator(color: LofiQColors.neonCyan),
                          SizedBox(height: 16),
                          Text('Scanning Windows Music directory for audio files...',
                              style: TextStyle(color: LofiQColors.textSecondary)),
                        ],
                      ),
                    )
                  : filtered.isEmpty
                      ? Center(
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Icon(Icons.audio_file_outlined, size: 64, color: Colors.white.withOpacity(0.2)),
                              const SizedBox(height: 16),
                              Text(
                                _localSongs.isEmpty
                                    ? 'No local music found in your Windows Music folder'
                                    : 'No matches found for "$_searchFilter"',
                                style: const TextStyle(color: LofiQColors.textSecondary, fontSize: 16),
                              ),
                            ],
                          ),
                        )
                      : ListView.builder(
                          itemCount: filtered.length,
                          itemBuilder: (context, index) {
                            final song = filtered[index];
                            final isPlaying = widget.playbackController.state.currentSong?.id == song.id;

                            return SongRow(
                              song: song,
                              isPlaying: isPlaying,
                              onTap: () {
                                widget.playbackController.setQueue(filtered, index);
                              },
                            );
                          },
                        ),
            ),
          ],
        ),
      ),
    );
  }
}

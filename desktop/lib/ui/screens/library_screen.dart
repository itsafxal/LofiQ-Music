import 'package:flutter/material.dart';
import '../../core/database/local_storage.dart';
import '../../core/models/playlist.dart';
import '../../core/models/song.dart';
import '../../core/theme/colors.dart';
import '../../playback/playback_controller.dart';
import '../components/glass_card.dart';
import '../components/song_row.dart';

class LibraryScreen extends StatefulWidget {
  final VoidCallback onOpenLocalMusic;

  const LibraryScreen({
    super.key,
    required this.onOpenLocalMusic,
  });

  @override
  State<LibraryScreen> createState() => _LibraryScreenState();
}

class _LibraryScreenState extends State<LibraryScreen> {
  Playlist? _selectedPlaylist;

  void _createPlaylistDialog() {
    final textController = TextEditingController();
    showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          backgroundColor: LofiQColors.surfaceCard,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          title: const Text('Create New Playlist', style: TextStyle(color: LofiQColors.textPrimary)),
          content: TextField(
            controller: textController,
            style: const TextStyle(color: LofiQColors.textPrimary),
            decoration: const InputDecoration(
              hintText: 'Enter playlist title...',
              hintStyle: TextStyle(color: LofiQColors.textTertiary),
              enabledBorder: UnderlineInputBorder(borderSide: BorderSide(color: Colors.white24)),
              focusedBorder: UnderlineInputBorder(borderSide: BorderSide(color: LofiQColors.brandPurpleLight)),
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Cancel', style: TextStyle(color: LofiQColors.textSecondary)),
            ),
            ElevatedButton(
              style: ElevatedButton.styleFrom(backgroundColor: LofiQColors.brandPurple),
              onPressed: () {
                final name = textController.text.trim();
                if (name.isNotEmpty) {
                  final newPl = Playlist(
                    id: 'pl_${DateTime.now().millisecondsSinceEpoch}',
                    name: name,
                    createdAt: DateTime.now().millisecondsSinceEpoch,
                  );
                  LocalStorage.instance.savePlaylist(newPl);
                  setState(() {});
                  Navigator.pop(context);
                }
              },
              child: const Text('Create', style: TextStyle(color: Colors.white)),
            ),
          ],
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final favorites = LocalStorage.instance.getFavoriteSongs();
    final playlists = LocalStorage.instance.getAllPlaylists();

    if (_selectedPlaylist != null) {
      return _buildPlaylistDetailView(_selectedPlaylist!);
    }

    return SingleChildScrollView(
      padding: const EdgeInsets.all(28.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Library Header
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text(
                'Your Library',
                style: TextStyle(
                  fontSize: 26.0,
                  fontWeight: FontWeight.w700,
                  color: LofiQColors.textPrimary,
                  letterSpacing: -0.5,
                ),
              ),
              ElevatedButton.icon(
                icon: const Icon(Icons.add_rounded, size: 18),
                label: const Text('New Playlist'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: LofiQColors.surfaceElevated,
                  foregroundColor: Colors.white,
                  side: BorderSide(color: Colors.white.withValues(alpha: 0.12)),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                ),
                onPressed: _createPlaylistDialog,
              ),
            ],
          ),
          const SizedBox(height: 24.0),

          // Two Top Primary Tiles: Favorites & On-Device Music
          Row(
            children: [
              // Favorites Card
              Expanded(
                child: GlassCard(
                  onTap: () {
                    if (favorites.isNotEmpty) {
                      PlaybackController.instance.setQueue(favorites, 0);
                    }
                  },
                  backgroundColor: const Color(0xFF2B0938).withValues(alpha: 0.7),
                  child: Row(
                    children: [
                      Container(
                        width: 56,
                        height: 56,
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(12),
                          gradient: const LinearGradient(
                            colors: [LofiQColors.brandPurple, Color(0xFFE11D48)],
                          ),
                        ),
                        child: const Icon(Icons.favorite_rounded, color: Colors.white, size: 28),
                      ),
                      const SizedBox(width: 16.0),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text(
                              'Liked Tracks',
                              style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700, color: Colors.white),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              '${favorites.length} favorite songs',
                              style: TextStyle(fontSize: 12, color: Colors.white.withValues(alpha: 0.7)),
                            ),
                          ],
                        ),
                      ),
                      if (favorites.isNotEmpty)
                        const Icon(Icons.play_circle_filled_rounded, color: Colors.white70, size: 32),
                    ],
                  ),
                ),
              ),
              const SizedBox(width: 20.0),

              // On-Device Windows Music Card
              Expanded(
                child: GlassCard(
                  onTap: widget.onOpenLocalMusic,
                  backgroundColor: const Color(0xFF0F1E36).withValues(alpha: 0.7),
                  child: Row(
                    children: [
                      Container(
                        width: 56,
                        height: 56,
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(12),
                          gradient: const LinearGradient(
                            colors: [Color(0xFF0284C7), Color(0xFF0D9488)],
                          ),
                        ),
                        child: const Icon(Icons.folder_special_rounded, color: Colors.white, size: 28),
                      ),
                      const SizedBox(width: 16.0),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: const [
                            Text(
                              'On-Device Audio',
                              style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700, color: Colors.white),
                            ),
                            SizedBox(height: 4),
                            Text(
                              'Scan Windows Music folder',
                              style: TextStyle(fontSize: 12, color: Colors.white70),
                            ),
                          ],
                        ),
                      ),
                      const Icon(Icons.arrow_forward_ios_rounded, color: Colors.white70, size: 18),
                    ],
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 32.0),

          // User Playlists Section
          const Text(
            'Custom Playlists',
            style: TextStyle(
              fontSize: 18.0,
              fontWeight: FontWeight.w700,
              color: LofiQColors.textPrimary,
            ),
          ),
          const SizedBox(height: 14.0),

          if (playlists.isEmpty)
            Center(
              child: Padding(
                padding: const EdgeInsets.symmetric(vertical: 40.0),
                child: Column(
                  children: [
                    Icon(Icons.queue_music_rounded, size: 48, color: Colors.white.withValues(alpha: 0.15)),
                    const SizedBox(height: 12.0),
                    const Text(
                      'No custom playlists created yet.',
                      style: TextStyle(color: LofiQColors.textTertiary, fontSize: 14.0),
                    ),
                  ],
                ),
              ),
            )
          else
            GridView.builder(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 4,
                crossAxisSpacing: 18.0,
                mainAxisSpacing: 18.0,
                childAspectRatio: 1.1,
              ),
              itemCount: playlists.length,
              itemBuilder: (context, index) {
                final pl = playlists[index];
                return GlassCard(
                  onTap: () => setState(() => _selectedPlaylist = pl),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Container(
                        width: 48,
                        height: 48,
                        decoration: BoxDecoration(
                          color: LofiQColors.brandPurple.withValues(alpha: 0.2),
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: const Icon(Icons.music_note_rounded, color: LofiQColors.brandPurpleLight, size: 24),
                      ),
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            pl.name,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(fontSize: 14.0, fontWeight: FontWeight.w700, color: Colors.white),
                          ),
                          const SizedBox(height: 3.0),
                          Text(
                            '${pl.songIds.length} tracks',
                            style: const TextStyle(fontSize: 12.0, color: LofiQColors.textSecondary),
                          ),
                        ],
                      ),
                    ],
                  ),
                );
              },
            ),
        ],
      ),
    );
  }

  Widget _buildPlaylistDetailView(Playlist playlist) {
    final songs = <Song>[];
    for (final id in playlist.songIds) {
      final s = LocalStorage.instance.getSong(id);
      if (s != null) songs.add(s);
    }

    return Padding(
      padding: const EdgeInsets.all(28.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              IconButton(
                icon: const Icon(Icons.arrow_back_rounded, color: LofiQColors.textPrimary),
                onPressed: () => setState(() => _selectedPlaylist = null),
              ),
              const SizedBox(width: 8.0),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      playlist.name,
                      style: const TextStyle(fontSize: 24.0, fontWeight: FontWeight.w700, color: LofiQColors.textPrimary),
                    ),
                    Text(
                      '${songs.length} tracks • ${playlist.description}',
                      style: const TextStyle(fontSize: 12.0, color: LofiQColors.textTertiary),
                    ),
                  ],
                ),
              ),
              if (songs.isNotEmpty)
                ElevatedButton.icon(
                  icon: const Icon(Icons.play_arrow_rounded),
                  label: const Text('Play All'),
                  style: ElevatedButton.styleFrom(backgroundColor: LofiQColors.brandPurple),
                  onPressed: () => PlaybackController.instance.setQueue(songs, 0),
                ),
            ],
          ),
          const SizedBox(height: 20.0),
          Expanded(
            child: songs.isEmpty
                ? const Center(
                    child: Text('This playlist is empty.', style: TextStyle(color: LofiQColors.textTertiary)),
                  )
                : GlassCard(
                    padding: const EdgeInsets.symmetric(vertical: 8.0, horizontal: 4.0),
                    child: ListView.separated(
                      itemCount: songs.length,
                      separatorBuilder: (_, __) => Divider(color: Colors.white.withValues(alpha: 0.05), height: 1),
                      itemBuilder: (context, index) {
                        final song = songs[index];
                        return SongRow(
                          song: song,
                          index: index,
                          onTap: () => PlaybackController.instance.playSong(song, songs),
                        );
                      },
                    ),
                  ),
          ),
        ],
      ),
    );
  }
}


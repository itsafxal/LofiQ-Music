import 'package:flutter/material.dart';
import '../../core/theme/colors.dart';
import '../../core/database/local_storage.dart';
import '../../data/spotify/spotify_import_helper.dart';
import '../../playback/playback_controller.dart';
import '../components/glass/glass_components.dart';
import '../components/song_row.dart';

class SpotifyImportScreen extends StatefulWidget {
  final PlaybackController playbackController;

  const SpotifyImportScreen({Key? key, required this.playbackController}) : super(key: key);

  @override
  State<SpotifyImportScreen> createState() => _SpotifyImportScreenState();
}

class _SpotifyImportScreenState extends State<SpotifyImportScreen> {
  final TextEditingController _urlController = TextEditingController();
  final SpotifyImportHelper _importHelper = SpotifyImportHelper();
  final LocalStorage _storage = LocalStorage();

  bool _isImporting = false;
  double _importProgress = 0.0;
  String _importStatus = '';
  SpotifyPlaylistResult? _importResult;

  @override
  void dispose() {
    _urlController.dispose();
    super.dispose();
  }

  Future<void> _startImport() async {
    final url = _urlController.text.trim();
    if (url.isEmpty) return;

    final playlistId = _importHelper.extractPlaylistId(url);
    if (playlistId == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Invalid Spotify URL. Please paste a valid playlist link.'),
          backgroundColor: Colors.redAccent,
        ),
      );
      return;
    }

    setState(() {
      _isImporting = true;
      _importProgress = 0.0;
      _importStatus = 'Scraping Spotify playlist metadata...';
      _importResult = null;
    });

    try {
      final result = await _importHelper.importSpotifyPlaylist(
        playlistUrl: url,
        onProgress: (current, total, trackName) {
          if (mounted) {
            setState(() {
              _importProgress = total > 0 ? (current / total) : 0.0;
              _importStatus = 'Matching track ($current / $total): "$trackName"...';
            });
          }
        },
      );

      if (mounted) {
        setState(() {
          _isImporting = false;
          _importResult = result;
          _importStatus = 'Completed! Found ${result.matchedSongs.length} out of ${result.totalTracks} tracks.';
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isImporting = false;
          _importStatus = 'Import failed: $e';
        });
      }
    }
  }

  Future<void> _saveToLibrary() async {
    if (_importResult == null || _importResult!.matchedSongs.isEmpty) return;

    final songs = _importResult!.matchedSongs;
    final playlist = await _storage.createPlaylist(_importResult!.playlistName);
    await _storage.addTracksToPlaylist(playlist.id, songs.map((s) => s.id).toList());

    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Saved "${_importResult!.playlistName}" (${songs.length} tracks) to your Library!'),
          backgroundColor: LofiQColors.cardBackground,
        ),
      );
    }
  }

  void _playNow() {
    if (_importResult == null || _importResult!.matchedSongs.isEmpty) return;
    widget.playbackController.setQueue(_importResult!.matchedSongs, 0);
  }

  @override
  Widget build(BuildContext context) {
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
                    color: const Color(0xFF1DB954).withOpacity(0.15),
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: const Color(0xFF1DB954).withOpacity(0.3)),
                  ),
                  child: const Icon(Icons.import_export_rounded, color: Color(0xFF1DB954), size: 28),
                ),
                const SizedBox(width: 16),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Spotify Playlist Importer',
                      style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                        color: LofiQColors.textPrimary,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'Seamlessly import public Spotify playlists and map them to lossless streams',
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: LofiQColors.textSecondary,
                      ),
                    ),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 24),

            // Input Card
            GlassPanel(
              title: 'Paste Spotify Playlist URL',
              subtitle: 'Example: https://open.spotify.com/playlist/37i9dQZF1DXdLEN7aqioXM',
              icon: Icons.link_rounded,
              preset: GlassPreset.dark,
              padding: const EdgeInsets.all(24),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: GlassInput(
                          controller: _urlController,
                          hintText: 'https://open.spotify.com/playlist/...',
                          prefixIcon: Icons.library_music_rounded,
                        ),
                      ),
                      const SizedBox(width: 16),
                      GlassButton(
                        onPressed: _isImporting ? null : _startImport,
                        isPrimary: true,
                        accentColor: const Color(0xFF1DB954),
                        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            if (_isImporting)
                              const SizedBox(
                                width: 18,
                                height: 18,
                                child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                              )
                            else
                              const Icon(Icons.cloud_download_rounded, size: 20),
                            const SizedBox(width: 8),
                            Text(_isImporting ? 'Importing...' : 'Start Import'),
                          ],
                        ),
                      ),
                    ],
                  ),

                  // Progress Indicator
                  if (_isImporting) ...[
                    const SizedBox(height: 20),
                    LinearProgressIndicator(
                      value: _importProgress > 0 ? _importProgress : null,
                      backgroundColor: Colors.white12,
                      color: const Color(0xFF1DB954),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    const SizedBox(height: 10),
                    Text(
                      _importStatus,
                      style: const TextStyle(color: LofiQColors.textSecondary, fontSize: 13),
                    ),
                  ],
                ],
              ),
            ),
            const SizedBox(height: 24),

            // Import Results
            if (_importResult != null) ...[
              GlassCard(
                preset: GlassPreset.elevated,
                hasGlow: true,
                glowColor: const Color(0xFF1DB954),
                padding: const EdgeInsets.all(20),
                child: Row(
                  children: [
                    Container(
                      width: 56,
                      height: 56,
                      decoration: BoxDecoration(
                        color: const Color(0xFF1DB954).withOpacity(0.2),
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: const Color(0xFF1DB954).withOpacity(0.4)),
                      ),
                      child: const Icon(Icons.playlist_play_rounded, color: Color(0xFF1DB954), size: 32),
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            _importResult!.playlistName,
                            style: const TextStyle(
                              color: LofiQColors.textPrimary,
                              fontWeight: FontWeight.bold,
                              fontSize: 18,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            'Matched ${_importResult!.matchedSongs.length} of ${_importResult!.totalTracks} tracks (${((_importResult!.matchedSongs.length / (_importResult!.totalTracks > 0 ? _importResult!.totalTracks : 1)) * 100).toStringAsFixed(0)}% match rate)',
                            style: const TextStyle(color: LofiQColors.textSecondary, fontSize: 13),
                          ),
                        ],
                      ),
                    ),
                    GlassButton(
                      onPressed: _saveToLibrary,
                      preset: GlassPreset.subtle,
                      child: const Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(Icons.library_add_rounded, size: 18),
                          SizedBox(width: 6),
                          Text('Save to Library'),
                        ],
                      ),
                    ),
                    const SizedBox(width: 12),
                    GlassButton(
                      onPressed: _playNow,
                      isPrimary: true,
                      accentColor: brandTangerine,
                      child: const Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(Icons.play_arrow_rounded, size: 20),
                          SizedBox(width: 6),
                          Text('Play Now'),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 20),

              // Matched Tracks List
              ListView.builder(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                itemCount: _importResult!.matchedSongs.length,
                itemBuilder: (context, index) {
                  final song = _importResult!.matchedSongs[index];
                  return SongRow(
                    song: song,
                    isPlaying: widget.playbackController.state.currentSong?.id == song.id,
                    onTap: () {
                      widget.playbackController.setQueue(_importResult!.matchedSongs, index);
                    },
                  );
                },
              ),
            ],
          ],
        ),
      ),
    );
  }
}

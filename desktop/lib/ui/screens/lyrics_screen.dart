import 'package:flutter/material.dart';
import '../../core/models/song.dart';
import '../../core/models/player_state.dart';
import '../../core/theme/colors.dart';
import '../../core/theme/liquid_glass.dart';
import '../../data/lyrics/lyrics_models.dart';
import '../../data/lyrics/lyrics_repository.dart';
import '../../playback/playback_controller.dart';
import '../components/glass_card.dart';

class LyricsScreen extends StatefulWidget {
  final PlaybackController playbackController;

  const LyricsScreen({Key? key, required this.playbackController}) : super(key: key);

  @override
  State<LyricsScreen> createState() => _LyricsScreenState();
}

class _LyricsScreenState extends State<LyricsScreen> {
  final LyricsRepository _lyricsRepo = LyricsRepository();
  final ScrollController _scrollController = ScrollController();

  Lyrics? _lyrics;
  bool _isLoading = false;
  String? _loadedSongId;
  int _activeLineIndex = -1;

  @override
  void initState() {
    super.initState();
    widget.playbackController.addListener(_onPlaybackStateChanged);
    _checkAndFetchLyrics();
  }

  @override
  void dispose() {
    widget.playbackController.removeListener(_onPlaybackStateChanged);
    _scrollController.dispose();
    super.dispose();
  }

  void _onPlaybackStateChanged() {
    final state = widget.playbackController.state;
    if (state.currentSong?.id != _loadedSongId) {
      _checkAndFetchLyrics();
    } else if (_lyrics != null && _lyrics!.syncType == LyricsSyncType.lineSynced) {
      _updateActiveLine(state.positionMs);
    }
  }

  void _updateActiveLine(int positionMs) {
    if (_lyrics == null || _lyrics!.lines.isEmpty) return;
    final lines = _lyrics!.lines;

    int foundIndex = -1;
    for (int i = 0; i < lines.length; i++) {
      if (positionMs >= lines[i].timestampMs) {
        foundIndex = i;
      } else {
        break;
      }
    }

    if (foundIndex != _activeLineIndex) {
      setState(() => _activeLineIndex = foundIndex);
      _autoScrollToActiveLine(foundIndex);
    }
  }

  void _autoScrollToActiveLine(int index) {
    if (!_scrollController.hasClients || index < 0) return;
    const itemHeight = 56.0;
    final targetOffset = (index * itemHeight) - 150.0;
    _scrollController.animateTo(
      targetOffset.clamp(0.0, _scrollController.position.maxScrollExtent),
      duration: const Duration(milliseconds: 300),
      curve: Curves.easeOutCubic,
    );
  }

  Future<void> _checkAndFetchLyrics({bool forceRefresh = false}) async {
    final song = widget.playbackController.state.currentSong;
    if (song == null) {
      setState(() {
        _lyrics = null;
        _loadedSongId = null;
        _activeLineIndex = -1;
      });
      return;
    }

    if (!forceRefresh && song.id == _loadedSongId) return;

    setState(() {
      _isLoading = true;
      _loadedSongId = song.id;
      _activeLineIndex = -1;
    });

    final lyrics = await _lyricsRepo.fetchLyrics(
      title: song.title,
      artist: song.artist,
      durationMs: song.durationMs,
      forceRefresh: forceRefresh,
    );

    if (mounted) {
      setState(() {
        _lyrics = lyrics;
        _isLoading = false;
      });
      if (lyrics != null && lyrics.syncType == LyricsSyncType.lineSynced) {
        _updateActiveLine(widget.playbackController.state.positionMs);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = widget.playbackController.state;
    final song = state.currentSong;

    if (song == null) {
      return const Scaffold(
        backgroundColor: Colors.transparent,
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.music_off_rounded, size: 64, color: Colors.white24),
              SizedBox(height: 16),
              Text(
                'No music currently playing',
                style: TextStyle(color: LofiQColors.textSecondary, fontSize: 16),
              ),
            ],
          ),
        ),
      );
    }

    return Scaffold(
      backgroundColor: Colors.transparent,
      body: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 40.0, vertical: 24.0),
        child: Column(
          children: [
            // Header Bar
            Row(
              children: [
                ClipRRect(
                  borderRadius: BorderRadius.circular(12),
                  child: song.artworkUrl != null
                      ? Image.network(
                          song.artworkUrl!,
                          width: 48,
                          height: 48,
                          fit: BoxFit.cover,
                          errorBuilder: (_, __, ___) => _buildPlaceholderArt(),
                        )
                      : _buildPlaceholderArt(),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        song.title,
                        style: const TextStyle(
                          color: LofiQColors.textPrimary,
                          fontSize: 18,
                          fontWeight: FontWeight.bold,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 2),
                      Text(
                        '${song.artist} • ${_lyrics != null ? "Source: ${_lyrics!.source}" : "Fetching"}',
                        style: const TextStyle(color: LofiQColors.textSecondary, fontSize: 13),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ],
                  ),
                ),
                if (_lyrics != null)
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                      color: _lyrics!.isSynced
                          ? LofiQColors.neonCyan.withOpacity(0.15)
                          : Colors.white.withOpacity(0.08),
                      borderRadius: BorderRadius.circular(20),
                      border: Border.all(
                        color: _lyrics!.isSynced
                            ? LofiQColors.neonCyan.withOpacity(0.4)
                            : Colors.white24,
                      ),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(
                          _lyrics!.isSynced ? Icons.sync_rounded : Icons.text_snippet_rounded,
                          size: 14,
                          color: _lyrics!.isSynced ? LofiQColors.neonCyan : Colors.white60,
                        ),
                        const SizedBox(width: 6),
                        Text(
                          _lyrics!.isSynced ? 'Line Synced' : 'Plain Text',
                          style: TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.bold,
                            color: _lyrics!.isSynced ? LofiQColors.neonCyan : Colors.white60,
                          ),
                        ),
                      ],
                    ),
                  ),
                const SizedBox(width: 12),
                IconButton(
                  tooltip: 'Reload Lyrics',
                  icon: const Icon(Icons.refresh_rounded, color: LofiQColors.textSecondary),
                  onPressed: () => _checkAndFetchLyrics(forceRefresh: true),
                ),
              ],
            ),
            const Divider(color: LofiQColors.glassBorderSubtle, height: 32),

            // Lyrics Scroll Body
            Expanded(
              child: _isLoading
                  ? const Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          CircularProgressIndicator(color: LofiQColors.neonPurple),
                          SizedBox(height: 16),
                          Text('Fetching synced lyrics from LRCLIB & Kugou...',
                              style: TextStyle(color: LofiQColors.textSecondary)),
                        ],
                      ),
                    )
                  : _lyrics == null || _lyrics!.lines.isEmpty
                      ? Center(
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              const Icon(Icons.lyrics_outlined, size: 64, color: Colors.white24),
                              const SizedBox(height: 16),
                              Text(
                                'No lyrics available for "${song.title}"',
                                style: const TextStyle(color: LofiQColors.textSecondary, fontSize: 16),
                              ),
                              const SizedBox(height: 12),
                              ElevatedButton.icon(
                                onPressed: () => _checkAndFetchLyrics(forceRefresh: true),
                                icon: const Icon(Icons.search_rounded, size: 16),
                                label: const Text('Search Again'),
                                style: ElevatedButton.styleFrom(
                                  backgroundColor: LofiQColors.cardBackground,
                                  foregroundColor: LofiQColors.neonPurple,
                                  side: const BorderSide(color: LofiQColors.glassBorderSubtle),
                                ),
                              ),
                            ],
                          ),
                        )
                      : ListView.builder(
                          controller: _scrollController,
                          itemCount: _lyrics!.lines.length,
                          padding: const EdgeInsets.symmetric(vertical: 80),
                          itemBuilder: (context, index) {
                            final line = _lyrics!.lines[index];
                            final isActive = _lyrics!.isSynced && index == _activeLineIndex;
                            final isPast = _lyrics!.isSynced && index < _activeLineIndex;

                            return InkWell(
                              onTap: _lyrics!.isSynced
                                  ? () => widget.playbackController.seekTo(line.timestampMs)
                                  : null,
                              borderRadius: BorderRadius.circular(12),
                              hoverColor: Colors.white.withOpacity(0.04),
                              child: Padding(
                                padding: const EdgeInsets.symmetric(vertical: 12.0, horizontal: 16.0),
                                child: AnimatedDefaultTextStyle(
                                  duration: const Duration(milliseconds: 250),
                                  style: TextStyle(
                                    fontSize: isActive ? 26 : 20,
                                    fontWeight: isActive ? FontWeight.bold : FontWeight.w500,
                                    color: isActive
                                        ? Colors.white
                                        : isPast
                                            ? Colors.white.withOpacity(0.6)
                                            : Colors.white.withOpacity(0.3),
                                    letterSpacing: -0.3,
                                    height: 1.4,
                                    shadows: isActive
                                        ? [
                                            BoxShadow(
                                              color: LofiQColors.neonPurple.withOpacity(0.6),
                                              blurRadius: 18,
                                              spreadRadius: 4,
                                            ),
                                          ]
                                        : null,
                                  ),
                                  child: Text(
                                    line.text,
                                    textAlign: TextAlign.center,
                                  ),
                                ),
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

  Widget _buildPlaceholderArt() {
    return Container(
      width: 48,
      height: 48,
      color: LofiQColors.cardBackground,
      child: const Icon(Icons.music_note_rounded, color: LofiQColors.neonPurple),
    );
  }
}


import 'dart:async';
import 'package:flutter/material.dart';
import '../../core/database/local_storage.dart';
import '../../core/models/song.dart';
import '../../core/theme/colors.dart';
import '../../data/resolvers/jiosaavn_client.dart';
import '../../data/resolvers/youtube_stream_extractor.dart';
import '../../playback/playback_controller.dart';
import '../components/glass/glass_components.dart';
import '../components/song_row.dart';

class SearchScreen extends StatefulWidget {
  const SearchScreen({super.key});

  @override
  State<SearchScreen> createState() => _SearchScreenState();
}

class _SearchScreenState extends State<SearchScreen> {
  final TextEditingController _controller = TextEditingController();
  Timer? _debounceTimer;

  bool _isLoading = false;
  List<Song> _searchResults = [];
  int _activeFilterIndex = 0; // 0: All, 1: JioSaavn, 2: YouTube
  final List<String> _filters = const ['All Providers', 'JioSaavn (320k)', 'YouTube Audio'];

  void _onQueryChanged(String query) {
    _debounceTimer?.cancel();
    if (query.trim().isEmpty) {
      setState(() {
        _isLoading = false;
        _searchResults = [];
      });
      return;
    }

    _debounceTimer = Timer(const Duration(milliseconds: 400), () {
      _performSearch(query.trim());
    });
  }

  Future<void> _performSearch(String query) async {
    setState(() => _isLoading = true);

    final combinedResults = <Song>[];

    try {
      // 1. Search JioSaavn for 320k tracks
      if (_activeFilterIndex == 0 || _activeFilterIndex == 1) {
        final js = await JioSaavnClient.instance.search(query);
        combinedResults.addAll(js);
      }

      // 2. Search YouTube
      if (_activeFilterIndex == 0 || _activeFilterIndex == 2) {
        final yt = await YouTubeStreamExtractor.instance.search(query);
        combinedResults.addAll(yt);
      }
    } catch (_) {}

    // Cache discovered songs into LocalStorage
    for (final song in combinedResults) {
      await LocalStorage.instance.upsertSong(song);
    }

    if (mounted) {
      setState(() {
        _isLoading = false;
        _searchResults = combinedResults;
      });
    }
  }

  @override
  void dispose() {
    _debounceTimer?.cancel();
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(28.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Search Header Title with Brand Badge
          Row(
            children: [
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: LofiQColors.brandTangerine.withValues(alpha: 0.16),
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(
                    color: LofiQColors.brandTangerine.withValues(alpha: 0.35),
                    width: 1.0,
                  ),
                ),
                child: const Icon(Icons.search_rounded, color: LofiQColors.brandTangerine, size: 24),
              ),
              const SizedBox(width: 14),
              const Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Search & Discovery',
                    style: TextStyle(
                      fontSize: 24.0,
                      fontWeight: FontWeight.bold,
                      color: LofiQColors.textPrimary,
                      letterSpacing: -0.4,
                    ),
                  ),
                  SizedBox(height: 2.0),
                  Text(
                    'Discover 320kbps master audio and lossless lofi soundscapes across multiple providers',
                    style: TextStyle(color: LofiQColors.textSecondary, fontSize: 12.0),
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 20.0),

          // Search Input Bar
          GlassInput(
            controller: _controller,
            hintText: 'Search songs, artists, albums, or lofi playlists...',
            prefixIcon: Icons.search_rounded,
            onChanged: _onQueryChanged,
            onSubmitted: (q) => _performSearch(q.trim()),
          ),
          const SizedBox(height: 14.0),

          // Segmented Provider Filter
          SizedBox(
            width: 480,
            child: GlassSegmentedControl(
              items: _filters,
              selectedIndex: _activeFilterIndex,
              onSelect: (idx) {
                setState(() => _activeFilterIndex = idx);
                if (_controller.text.isNotEmpty) {
                  _performSearch(_controller.text.trim());
                }
              },
            ),
          ),
          const SizedBox(height: 20.0),

          // Search Results List
          Expanded(
            child: _isLoading
                ? const Center(
                    child: CircularProgressIndicator(color: LofiQColors.brandTangerine),
                  )
                : _searchResults.isEmpty
                    ? Center(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(Icons.search_rounded, size: 48, color: Colors.white.withValues(alpha: 0.15)),
                            const SizedBox(height: 12.0),
                            Text(
                              _controller.text.isEmpty
                                  ? 'Type above to search high-fidelity audio streams'
                                  : 'No results found for "${_controller.text}"',
                              style: const TextStyle(color: LofiQColors.textTertiary, fontSize: 14.0),
                            ),
                          ],
                        ),
                      )
                    : GlassCard(
                        padding: const EdgeInsets.symmetric(vertical: 8.0, horizontal: 4.0),
                        child: ListView.separated(
                          itemCount: _searchResults.length,
                          separatorBuilder: (_, _) => Divider(color: Colors.white.withValues(alpha: 0.05), height: 1),
                          itemBuilder: (context, index) {
                            final song = _searchResults[index];
                            return SongRow(
                              song: song,
                              index: index,
                              onTap: () => PlaybackController.instance.playSong(song, _searchResults),
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

import 'dart:async';
import 'package:flutter/material.dart';
import '../../core/database/local_storage.dart';
import '../../core/models/song.dart';
import '../../core/theme/colors.dart';
import '../../data/resolvers/jiosaavn_client.dart';
import '../../data/resolvers/youtube_stream_extractor.dart';
import '../../playback/playback_controller.dart';
import '../components/glass_card.dart';
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
  String _activeFilter = 'All'; // 'All', 'JioSaavn', 'YouTube'

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
      if (_activeFilter == 'All' || _activeFilter == 'JioSaavn') {
        final js = await JioSaavnClient.instance.search(query);
        combinedResults.addAll(js);
      }

      // 2. Search YouTube
      if (_activeFilter == 'All' || _activeFilter == 'YouTube') {
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
          // Search Header Title
          const Text(
            'Search & Discovery',
            style: TextStyle(
              fontSize: 26.0,
              fontWeight: FontWeight.w700,
              color: LofiQColors.textPrimary,
              letterSpacing: -0.5,
            ),
          ),
          const SizedBox(height: 18.0),

          // Search Input Bar
          Container(
            height: 48,
            decoration: BoxDecoration(
              color: LofiQColors.surfaceCard,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: Colors.white.withValues(alpha: 0.12)),
            ),
            child: Row(
              children: [
                const Padding(
                  padding: EdgeInsets.symmetric(horizontal: 14.0),
                  child: Icon(Icons.search_rounded, color: LofiQColors.textTertiary, size: 20),
                ),
                Expanded(
                  child: TextField(
                    controller: _controller,
                    onChanged: _onQueryChanged,
                    style: const TextStyle(color: LofiQColors.textPrimary, fontSize: 14.0),
                    decoration: const InputDecoration(
                      hintText: 'Search songs, artists, albums, or lofi playlists...',
                      hintStyle: TextStyle(color: LofiQColors.textTertiary, fontSize: 14.0),
                      border: InputBorder.none,
                    ),
                  ),
                ),
                if (_controller.text.isNotEmpty)
                  IconButton(
                    icon: const Icon(Icons.clear_rounded, size: 18, color: LofiQColors.textTertiary),
                    onPressed: () {
                      _controller.clear();
                      _onQueryChanged('');
                    },
                  ),
              ],
            ),
          ),
          const SizedBox(height: 14.0),

          // Filter Chips
          Row(
            children: ['All', 'JioSaavn', 'YouTube'].map((filter) {
              final isSel = filter == _activeFilter;
              return Padding(
                padding: const EdgeInsets.only(right: 8.0),
                child: ChoiceChip(
                  label: Text(filter == 'JioSaavn' ? 'JioSaavn (320 kbps)' : filter),
                  selected: isSel,
                  onSelected: (val) {
                    if (val) {
                      setState(() => _activeFilter = filter);
                      if (_controller.text.isNotEmpty) {
                        _performSearch(_controller.text.trim());
                      }
                    }
                  },
                  selectedColor: LofiQColors.brandPurple,
                  backgroundColor: Colors.white.withValues(alpha: 0.06),
                  labelStyle: TextStyle(
                    fontSize: 12.0,
                    color: isSel ? Colors.white : LofiQColors.textSecondary,
                    fontWeight: isSel ? FontWeight.w600 : FontWeight.w400,
                  ),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                ),
              );
            }).toList(),
          ),
          const SizedBox(height: 20.0),

          // Search Results List
          Expanded(
            child: _isLoading
                ? const Center(
                    child: CircularProgressIndicator(color: LofiQColors.brandPurpleLight),
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
                                  ? 'Type to search high-fidelity audio streams'
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
                          separatorBuilder: (_, __) => Divider(color: Colors.white.withValues(alpha: 0.05), height: 1),
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


import 'package:flutter/material.dart';
import '../../core/database/local_storage.dart';
import '../../core/models/song.dart';
import '../../core/theme/colors.dart';
import '../../playback/playback_controller.dart';
import '../components/glass_card.dart';
import '../components/song_row.dart';

class HomeScreen extends StatefulWidget {
  final ValueChanged<String> onNavigateTab;
  final VoidCallback onOpenSpotifyImport;

  const HomeScreen({
    super.key,
    required this.onNavigateTab,
    required this.onOpenSpotifyImport,
  });

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  String _selectedMood = 'All';
  final List<String> _moodPills = ['All', 'Chill', 'Focus', 'Night', 'Sleep', 'Ambient', 'Beats'];

  // Default featured lofi tracks for instant playback out-of-the-box
  static const List<Song> _featuredTracks = [
    Song(
      id: 'yt_jfKfPfyJRdk',
      title: 'Lofi Hip Hop Radio - Beats to Relax/Study to',
      artist: 'Lofi Girl',
      album: 'Lofi Girl Live & Sessions',
      durationSeconds: 240,
      artworkUrl: 'https://i.ytimg.com/vi/jfKfPfyJRdk/hqdefault.jpg',
      highResArtworkUrl: 'https://i.ytimg.com/vi/jfKfPfyJRdk/maxresdefault.jpg',
      sourceUrl: '',
      providerId: 'youtube',
    ),
    Song(
      id: 'yt_5qap5aO4i9A',
      title: 'Lofi Hip Hop - Chill Beats',
      artist: 'ChilledCow',
      album: 'Midnight Lofi',
      durationSeconds: 215,
      artworkUrl: 'https://i.ytimg.com/vi/5qap5aO4i9A/hqdefault.jpg',
      highResArtworkUrl: 'https://i.ytimg.com/vi/5qap5aO4i9A/maxresdefault.jpg',
      sourceUrl: '',
      providerId: 'youtube',
    ),
    Song(
      id: 'yt_DWcJFNfaw9c',
      title: 'Coffee Shop Ambience & Rainy Lofi Beats',
      artist: 'Kofi Music',
      album: 'Rainy Cafe Vibes',
      durationSeconds: 260,
      artworkUrl: 'https://i.ytimg.com/vi/DWcJFNfaw9c/hqdefault.jpg',
      highResArtworkUrl: 'https://i.ytimg.com/vi/DWcJFNfaw9c/maxresdefault.jpg',
      sourceUrl: '',
      providerId: 'youtube',
    ),
    Song(
      id: 'yt_rUxyKA_-grg',
      title: 'Midnight Coding & Cyberpunk Lofi',
      artist: 'Tokyo Nights',
      album: 'Cyber Lofi',
      durationSeconds: 230,
      artworkUrl: 'https://i.ytimg.com/vi/rUxyKA_-grg/hqdefault.jpg',
      highResArtworkUrl: 'https://i.ytimg.com/vi/rUxyKA_-grg/maxresdefault.jpg',
      sourceUrl: '',
      providerId: 'youtube',
    ),
  ];

  @override
  Widget build(BuildContext context) {
    final recentSongs = LocalStorage.instance.getAllSongs();
    final displayTracks = recentSongs.isNotEmpty ? recentSongs : _featuredTracks;

    return SingleChildScrollView(
      padding: const EdgeInsets.all(28.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Top Hero Greeting & Spotify Importer Banner
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: const [
                    Text(
                      'Welcome to LofiQ',
                      style: TextStyle(
                        fontSize: 26.0,
                        fontWeight: FontWeight.w700,
                        color: LofiQColors.textPrimary,
                        letterSpacing: -0.5,
                      ),
                    ),
                    SizedBox(height: 4.0),
                    Text(
                      'Immerse in pure liquid glass acoustics and ambient streams.',
                      style: TextStyle(fontSize: 14.0, color: LofiQColors.textSecondary),
                    ),
                  ],
                ),
              ),
              ElevatedButton.icon(
                icon: const Icon(Icons.playlist_add_rounded, size: 18),
                label: const Text('Import Spotify Playlist'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: LofiQColors.brandPurple,
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                ),
                onPressed: widget.onOpenSpotifyImport,
              ),
            ],
          ),
          const SizedBox(height: 24.0),

          // Mood Pills Rail
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: _moodPills.map((mood) {
                final isSelected = mood == _selectedMood;
                return Padding(
                  padding: const EdgeInsets.only(right: 8.0),
                  child: ChoiceChip(
                    label: Text(mood),
                    selected: isSelected,
                    onSelected: (val) {
                      if (val) setState(() => _selectedMood = mood);
                    },
                    selectedColor: LofiQColors.brandPurple,
                    backgroundColor: Colors.white.withValues(alpha: 0.08),
                    labelStyle: TextStyle(
                      color: isSelected ? Colors.white : LofiQColors.textSecondary,
                      fontWeight: isSelected ? FontWeight.w600 : FontWeight.w400,
                    ),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
                  ),
                );
              }).toList(),
            ),
          ),
          const SizedBox(height: 28.0),

          // Featured Ambient Station Cards (Horizontal grid)
          const Text(
            'Curated Ambient Stations',
            style: TextStyle(
              fontSize: 18.0,
              fontWeight: FontWeight.w700,
              color: LofiQColors.textPrimary,
            ),
          ),
          const SizedBox(height: 14.0),

          Row(
            children: [
              Expanded(
                child: _stationCard(
                  title: 'Deep Focus Chill',
                  subtitle: 'Binaural beats & warm keys',
                  gradient: const [Color(0xFF2E0854), Color(0xFF4A0E4E)],
                  icon: Icons.headphones_rounded,
                  onTap: () => PlaybackController.instance.playSong(_featuredTracks[0], _featuredTracks),
                ),
              ),
              const SizedBox(width: 18.0),
              Expanded(
                child: _stationCard(
                  title: 'Rainy Cafe Vibes',
                  subtitle: 'Gentle raindrops & soft jazz',
                  gradient: const [Color(0xFF0F2027), Color(0xFF203A43)],
                  icon: Icons.coffee_rounded,
                  onTap: () => PlaybackController.instance.playSong(_featuredTracks[2], _featuredTracks),
                ),
              ),
              const SizedBox(width: 18.0),
              Expanded(
                child: _stationCard(
                  title: 'Midnight Coding',
                  subtitle: 'Synthwave & dark ambient',
                  gradient: const [Color(0xFF141E30), Color(0xFF243B55)],
                  icon: Icons.code_rounded,
                  onTap: () => PlaybackController.instance.playSong(_featuredTracks[3], _featuredTracks),
                ),
              ),
            ],
          ),
          const SizedBox(height: 32.0),

          // Recommended Tracks & Quick Play List
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text(
                'Instant Lofi Streams',
                style: TextStyle(
                  fontSize: 18.0,
                  fontWeight: FontWeight.w700,
                  color: LofiQColors.textPrimary,
                ),
              ),
              TextButton(
                onPressed: () => widget.onNavigateTab('search'),
                child: const Text('Search More →', style: TextStyle(color: LofiQColors.brandPurpleLight)),
              ),
            ],
          ),
          const SizedBox(height: 12.0),

          GlassCard(
            padding: const EdgeInsets.symmetric(vertical: 8.0, horizontal: 4.0),
            child: ListView.separated(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              itemCount: displayTracks.length,
              separatorBuilder: (_, __) => Divider(color: Colors.white.withValues(alpha: 0.05), height: 1),
              itemBuilder: (context, index) {
                final song = displayTracks[index];
                return SongRow(
                  song: song,
                  index: index,
                  onTap: () => PlaybackController.instance.playSong(song, displayTracks),
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  Widget _stationCard({
    required String title,
    required String subtitle,
    required List<Color> gradient,
    required IconData icon,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(16.0),
      child: Container(
        height: 120,
        padding: const EdgeInsets.all(18.0),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(16.0),
          gradient: LinearGradient(
            colors: gradient,
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          border: Border.all(
            color: Colors.white.withValues(alpha: 0.15),
            width: 1.0,
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Icon(icon, color: Colors.white, size: 28),
                const Icon(Icons.play_circle_filled_rounded, color: Colors.white70, size: 28),
              ],
            ),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 15.0, fontWeight: FontWeight.w700, color: Colors.white),
                ),
                const SizedBox(height: 2.0),
                Text(
                  subtitle,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(fontSize: 12.0, color: Colors.white.withValues(alpha: 0.7)),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

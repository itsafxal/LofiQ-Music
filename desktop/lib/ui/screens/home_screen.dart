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
    Song(
      id: 'yt_7NOSDKb0HlU',
      title: 'Jazzy Lofi Hip Hop - Smooth Vibes',
      artist: 'Chillhop Music',
      album: 'Coffee Jazz Sessions',
      durationSeconds: 210,
      artworkUrl: 'https://i.ytimg.com/vi/7NOSDKb0HlU/hqdefault.jpg',
      highResArtworkUrl: 'https://i.ytimg.com/vi/7NOSDKb0HlU/maxresdefault.jpg',
      sourceUrl: '',
      providerId: 'youtube',
    ),
    Song(
      id: 'yt_lTRiuFIWV54',
      title: '1 A.M Study Session - Deep Focus',
      artist: 'Lofi Girl',
      album: 'Night Study Tapes',
      durationSeconds: 275,
      artworkUrl: 'https://i.ytimg.com/vi/lTRiuFIWV54/hqdefault.jpg',
      highResArtworkUrl: 'https://i.ytimg.com/vi/lTRiuFIWV54/maxresdefault.jpg',
      sourceUrl: '',
      providerId: 'youtube',
    ),
    Song(
      id: 'yt_5wRWniH6Po4',
      title: 'Rainy Night in Tokyo - Sleep Lofi',
      artist: 'Tokyo Ambience',
      album: 'Midnight Rain',
      durationSeconds: 245,
      artworkUrl: 'https://i.ytimg.com/vi/5wRWniH6Po4/hqdefault.jpg',
      highResArtworkUrl: 'https://i.ytimg.com/vi/5wRWniH6Po4/maxresdefault.jpg',
      sourceUrl: '',
      providerId: 'youtube',
    ),
    Song(
      id: 'yt_kgx4WGK0oNU',
      title: 'Cozy Winter Coffee Shop Ambience',
      artist: 'Kofi Music',
      album: 'Acoustic Sanctuary',
      durationSeconds: 290,
      artworkUrl: 'https://i.ytimg.com/vi/kgx4WGK0oNU/hqdefault.jpg',
      highResArtworkUrl: 'https://i.ytimg.com/vi/kgx4WGK0oNU/maxresdefault.jpg',
      sourceUrl: '',
      providerId: 'youtube',
    ),
  ];

  List<Song> _getFilteredTracks(List<Song> baseTracks) {
    if (_selectedMood == 'All') return baseTracks;
    final moodLower = _selectedMood.toLowerCase();
    final filtered = baseTracks.where((s) {
      final text = '${s.title} ${s.artist} ${s.album}'.toLowerCase();
      switch (moodLower) {
        case 'chill':
          return text.contains('chill') || text.contains('relax') || text.contains('lofi') || text.contains('smooth');
        case 'focus':
          return text.contains('study') || text.contains('focus') || text.contains('coding') || text.contains('session');
        case 'night':
          return text.contains('night') || text.contains('midnight') || text.contains('dark') || text.contains('1 a.m');
        case 'sleep':
          return text.contains('sleep') || text.contains('rain') || text.contains('soft') || text.contains('cozy');
        case 'ambient':
          return text.contains('ambience') || text.contains('ambient') || text.contains('coffee') || text.contains('cafe');
        case 'beats':
          return text.contains('beats') || text.contains('hip hop') || text.contains('cyberpunk') || text.contains('jazz');
        default:
          return true;
      }
    }).toList();
    return filtered.isNotEmpty ? filtered : baseTracks;
  }

  @override
  Widget build(BuildContext context) {
    final recentSongs = LocalStorage.instance.getAllSongs();
    final rawTracks = recentSongs.isNotEmpty ? recentSongs : _featuredTracks;
    final displayTracks = _getFilteredTracks(rawTracks);

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

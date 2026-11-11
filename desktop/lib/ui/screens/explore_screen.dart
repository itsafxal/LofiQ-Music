import 'package:flutter/material.dart';
import '../../core/models/song.dart';
import '../../core/models/audio_quality.dart';
import '../../core/theme/colors.dart';
import '../../core/theme/liquid_glass.dart';
import '../../playback/playback_controller.dart';

class ExploreScreen extends StatefulWidget {
  final PlaybackController playbackController;

  const ExploreScreen({Key? key, required this.playbackController}) : super(key: key);

  @override
  State<ExploreScreen> createState() => _ExploreScreenState();
}

class _ExploreScreenState extends State<ExploreScreen> {
  String _selectedMood = 'All';

  final List<String> _moods = const [
    'All',
    'Chill Lofi',
    'Deep Study',
    'Coffee Shop',
    'Midnight Rain',
    'Synthwave',
    'Anime Vibes',
    'Sleep & Relax',
    'Gaming Chill',
  ];

  final List<Map<String, dynamic>> _stations = const [
    {
      'title': 'Midnight Coffee Beats',
      'subtitle': 'Warm vinyl cracks & acoustic piano melodies',
      'trackCount': 24,
      'gradient': [Color(0xFF3B1E08), Color(0xFF1E1005)],
      'icon': Icons.coffee_rounded,
      'tracks': [
        {'id': 'cf1', 'title': 'Midnight Espresso', 'artist': 'Komorebi Sound', 'duration': 158},
        {'id': 'cf2', 'title': 'Late Night Study', 'artist': 'Aruarian Dreams', 'duration': 172},
        {'id': 'cf3', 'title': 'Rainy Windowsill', 'artist': 'City Whisper', 'duration': 144},
      ],
    },
    {
      'title': 'Tokyo Neon Dreamscape',
      'subtitle': 'Synth-drenched chillhop from Shibuya alleys',
      'trackCount': 30,
      'gradient': [Color(0xFF2C0B4D), Color(0xFF0F041A)],
      'icon': Icons.nightlife_rounded,
      'tracks': [
        {'id': 'tk1', 'title': 'Shibuya Crossing at 2 AM', 'artist': 'Neon Sakura', 'duration': 185},
        {'id': 'tk2', 'title': 'Cyber Memories', 'artist': 'RetroGlitch', 'duration': 210},
        {'id': 'tk3', 'title': 'Vapor Lights', 'artist': 'Lofi Syndicate', 'duration': 163},
      ],
    },
    {
      'title': 'Deep Concentration Flow',
      'subtitle': '432Hz ambient binaural soundscapes & smooth bass',
      'trackCount': 18,
      'gradient': [Color(0xFF0B3330), Color(0xFF041413)],
      'icon': Icons.psychology_rounded,
      'tracks': [
        {'id': 'dc1', 'title': 'Alpha Wave Horizon', 'artist': 'Zenith Audio', 'duration': 240},
        {'id': 'dc2', 'title': 'Monk State Meditation', 'artist': 'Calm Resonance', 'duration': 310},
        {'id': 'dc3', 'title': 'Deep Flow 432Hz', 'artist': 'Harmonic Waves', 'duration': 285},
      ],
    },
    {
      'title': 'Gentle Rain on Canvas',
      'subtitle': 'Natural thunderstorm textures with lo-fi rhodes',
      'trackCount': 35,
      'gradient': [Color(0xFF132B45), Color(0xFF07111C)],
      'icon': Icons.water_drop_rounded,
      'tracks': [
        {'id': 'rn1', 'title': 'Droplets on Glass', 'artist': 'Atmosphere Vibe', 'duration': 195},
        {'id': 'rn2', 'title': 'Distant Thunder Tape', 'artist': 'Soft Analog', 'duration': 225},
        {'id': 'rn3', 'title': 'Subtle Downpour', 'artist': 'Rainy Attic', 'duration': 178},
      ],
    },
    {
      'title': 'Analog Nostalgia Cassette',
      'subtitle': 'Tape saturation, warble and dusty drum loops',
      'trackCount': 22,
      'gradient': [Color(0xFF422114), Color(0xFF1F0C05)],
      'icon': Icons.album_rounded,
      'tracks': [
        {'id': 'an1', 'title': 'Dusty Attic Tapes', 'artist': 'Cassette Head', 'duration': 167},
        {'id': 'an2', 'title': '1998 Summer Memory', 'artist': 'Vintage Soul', 'duration': 189},
        {'id': 'an3', 'title': 'Rewind The Tape', 'artist': 'Lofi Chronicle', 'duration': 152},
      ],
    },
    {
      'title': 'Anime Nostalgia & Melancholy',
      'subtitle': 'Emotional Ghibli-inspired lo-fi piano beats',
      'trackCount': 28,
      'gradient': [Color(0xFF4A1525), Color(0xFF1F080F)],
      'icon': Icons.movie_filter_rounded,
      'tracks': [
        {'id': 'am1', 'title': 'Train to Nowhere', 'artist': 'Spirited Waves', 'duration': 204},
        {'id': 'am2', 'title': 'Hill of Memories', 'artist': 'Totoro Echoes', 'duration': 176},
        {'id': 'am3', 'title': 'Wind Rising Melodies', 'artist': 'Studio Lofi', 'duration': 218},
      ],
    },
  ];

  void _playStation(Map<String, dynamic> station) {
    final rawTracks = station['tracks'] as List<Map<String, dynamic>>;
    final songs = rawTracks.map((t) => Song(
      id: t['id'] as String,
      title: t['title'] as String,
      artist: t['artist'] as String,
      album: station['title'] as String,
      durationSeconds: t['duration'] as int,
      artworkUrl: '',
      highResArtworkUrl: '',
      sourceUrl: '',
      providerId: 'lofi',
      quality: AudioQuality.high,
    )).toList();

    widget.playbackController.setQueue(songs, 0);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('Playing station: ${station['title']}'),
        backgroundColor: LofiQColors.cardBackground,
        behavior: SnackBarBehavior.floating,
        duration: const Duration(seconds: 2),
      ),
    );
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
            // Title Header
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: LofiQColors.neonPurple.withOpacity(0.15),
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: LofiQColors.neonPurple.withOpacity(0.3)),
                  ),
                  child: const Icon(Icons.explore_rounded, color: LofiQColors.neonPurple, size: 28),
                ),
                const SizedBox(width: 16),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Explore Soundscapes',
                      style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                        color: LofiQColors.textPrimary,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'Curated ambient stations, binaural study moods, and lo-fi radio frequencies',
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: LofiQColors.textSecondary,
                      ),
                    ),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 24),

            // Mood Filter Chips
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: _moods.map((mood) {
                  final isSelected = _selectedMood == mood;
                  return Padding(
                    padding: const EdgeInsets.only(right: 8.0),
                    child: FilterChip(
                      label: Text(mood),
                      selected: isSelected,
                      onSelected: (_) => setState(() => _selectedMood = mood),
                      selectedColor: LofiQColors.neonPurple.withOpacity(0.25),
                      backgroundColor: LofiQColors.cardBackground,
                      side: BorderSide(
                        color: isSelected ? LofiQColors.neonPurple : LofiQColors.glassBorderSubtle,
                      ),
                      labelStyle: TextStyle(
                        color: isSelected ? LofiQColors.neonPurple : LofiQColors.textSecondary,
                        fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                      ),
                    ),
                  );
                }).toList(),
              ),
            ),
            const SizedBox(height: 28),

            // Curated Grid
            Text(
              'Featured Ambient Stations',
              style: Theme.of(context).textTheme.titleLarge?.copyWith(
                color: LofiQColors.textPrimary,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 16),

            GridView.builder(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 2,
                crossAxisSpacing: 18,
                mainAxisSpacing: 18,
                childAspectRatio: 2.2,
              ),
              itemCount: _stations.length,
              itemBuilder: (context, index) {
                final station = _stations[index];
                final gradients = station['gradient'] as List<Color>;

                return Container(
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      colors: gradients,
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                    ),
                    borderRadius: LiquidGlass.cardRadius,
                    border: Border.all(color: LofiQColors.glassBorderSubtle),
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withOpacity(0.35),
                        blurRadius: 16,
                        offset: const Offset(0, 8),
                      ),
                    ],
                  ),
                  child: Material(
                    color: Colors.transparent,
                    child: InkWell(
                      borderRadius: LiquidGlass.cardRadius,
                      onTap: () => _playStation(station),
                      hoverColor: Colors.white.withOpacity(0.05),
                      child: Padding(
                        padding: const EdgeInsets.all(20.0),
                        child: Row(
                          children: [
                            Container(
                              width: 64,
                              height: 64,
                              decoration: BoxDecoration(
                                color: Colors.white.withOpacity(0.1),
                                borderRadius: BorderRadius.circular(16),
                                border: Border.all(color: Colors.white.withOpacity(0.15)),
                              ),
                              child: Icon(
                                station['icon'] as IconData,
                                color: Colors.white.withOpacity(0.9),
                                size: 32,
                              ),
                            ),
                            const SizedBox(width: 20),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                mainAxisAlignment: MainAxisAlignment.center,
                                children: [
                                  Text(
                                    station['title'] as String,
                                    style: const TextStyle(
                                      color: Colors.white,
                                      fontSize: 18,
                                      fontWeight: FontWeight.bold,
                                    ),
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                  const SizedBox(height: 6),
                                  Text(
                                    station['subtitle'] as String,
                                    style: TextStyle(
                                      color: Colors.white.withOpacity(0.7),
                                      fontSize: 13,
                                    ),
                                    maxLines: 2,
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                  const SizedBox(height: 8),
                                  Text(
                                    '${station['trackCount']} Tracks • Continuous Play',
                                    style: TextStyle(
                                      color: LofiQColors.neonCyan.withOpacity(0.9),
                                      fontSize: 12,
                                      fontWeight: FontWeight.w500,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            const SizedBox(width: 12),
                            Container(
                              padding: const EdgeInsets.all(12),
                              decoration: BoxDecoration(
                                color: LofiQColors.neonPurple,
                                shape: BoxShape.circle,
                                boxShadow: [
                                  BoxShadow(
                                    color: LofiQColors.neonPurple.withOpacity(0.5),
                                    blurRadius: 10,
                                    spreadRadius: 2,
                                  ),
                                ],
                              ),
                              child: const Icon(
                                Icons.play_arrow_rounded,
                                color: Colors.white,
                                size: 24,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                );
              },
            ),
          ],
        ),
      ),
    );
  }
}

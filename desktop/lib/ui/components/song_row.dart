import 'package:flutter/material.dart';
import '../../core/database/local_storage.dart';
import '../../core/models/song.dart';
import '../../core/theme/colors.dart';
import '../../core/utils/time_format.dart';
import '../../playback/playback_controller.dart';

class SongRow extends StatefulWidget {
  final Song song;
  final bool isPlaying;
  final VoidCallback onTap;
  final int? index;

  const SongRow({
    super.key,
    required this.song,
    this.isPlaying = false,
    required this.onTap,
    this.index,
  });

  @override
  State<SongRow> createState() => _SongRowState();
}

class _SongRowState extends State<SongRow> {
  bool _isHovered = false;
  late bool _isFavorite;

  @override
  void initState() {
    super.initState();
    _isFavorite = LocalStorage.instance.isFavorite(widget.song.id);
  }

  void _toggleFavorite() {
    setState(() {
      _isFavorite = !_isFavorite;
    });
    LocalStorage.instance.setFavorite(widget.song.id, _isFavorite);
    LocalStorage.instance.upsertSong(widget.song);
  }

  @override
  Widget build(BuildContext context) {
    final isCurrent = PlaybackController.instance.state.currentSong?.id == widget.song.id;

    return MouseRegion(
      onEnter: (_) => setState(() => _isHovered = true),
      onExit: (_) => setState(() => _isHovered = false),
      child: InkWell(
        onTap: widget.onTap,
        borderRadius: BorderRadius.circular(8.0),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 12.0, vertical: 8.0),
          decoration: BoxDecoration(
            color: isCurrent
                ? LofiQColors.brandPurple.withValues(alpha: 0.14)
                : (_isHovered ? Colors.white.withValues(alpha: 0.05) : Colors.transparent),
            borderRadius: BorderRadius.circular(8.0),
          ),
          child: Row(
            children: [
              // Track index or playing indicator
              if (widget.index != null)
                SizedBox(
                  width: 28,
                  child: Text(
                    '${widget.index! + 1}',
                    style: TextStyle(
                      fontSize: 12,
                      color: isCurrent ? LofiQColors.brandPurpleLight : LofiQColors.textTertiary,
                      fontWeight: isCurrent ? FontWeight.w700 : FontWeight.w400,
                    ),
                  ),
                ),

              // Artwork thumbnail
              ClipRRect(
                borderRadius: BorderRadius.circular(6.0),
                child: Container(
                  width: 42,
                  height: 42,
                  color: LofiQColors.surfaceElevated,
                  child: widget.song.artworkUrl.isNotEmpty
                      ? Image.network(
                          widget.song.artworkUrl,
                          fit: BoxFit.cover,
                          errorBuilder: (_, __, ___) => _defaultArtwork(),
                        )
                      : _defaultArtwork(),
                ),
              ),
              const SizedBox(width: 14.0),

              // Title and artist
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      widget.song.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontSize: 14.0,
                        fontWeight: isCurrent ? FontWeight.w700 : FontWeight.w500,
                        color: isCurrent ? LofiQColors.brandPurpleLight : LofiQColors.textPrimary,
                      ),
                    ),
                    const SizedBox(height: 3.0),
                    Text(
                      widget.song.artist,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 12.0,
                        color: LofiQColors.textSecondary,
                      ),
                    ),
                  ],
                ),
              ),

              // Quality badge
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6.0, vertical: 2.0),
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.06),
                  borderRadius: BorderRadius.circular(4.0),
                ),
                child: Text(
                  widget.song.providerId == 'jiosaavn' ? '320k' : 'HQ',
                  style: const TextStyle(fontSize: 10, color: LofiQColors.textTertiary),
                ),
              ),
              const SizedBox(width: 12.0),

              // Favorite button
              IconButton(
                icon: Icon(
                  _isFavorite ? Icons.favorite_rounded : Icons.favorite_border_rounded,
                  size: 18,
                  color: _isFavorite ? LofiQColors.accentRed : LofiQColors.textTertiary,
                ),
                onPressed: _toggleFavorite,
                splashRadius: 18,
              ),

              // Duration
              SizedBox(
                width: 44,
                child: Text(
                  TimeFormatUtils.formatDuration(widget.song.durationSeconds),
                  textAlign: TextAlign.right,
                  style: const TextStyle(
                    fontSize: 12.0,
                    color: LofiQColors.textTertiary,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _defaultArtwork() {
    return const Icon(
      Icons.music_note_rounded,
      color: LofiQColors.textTertiary,
      size: 20,
    );
  }
}


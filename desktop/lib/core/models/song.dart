import 'audio_quality.dart';

class Song {
  final String id;
  final String title;
  final String artist;
  final String album;
  final int durationSeconds;
  final String artworkUrl;
  final String highResArtworkUrl;
  final String sourceUrl;
  final String providerId; // 'youtube', 'jiosaavn', 'local', 'piped'
  final AudioQuality quality;
  final int addedAt;
  final int playCount;
  final String? lyrics;

  const Song({
    required this.id,
    required this.title,
    required this.artist,
    required this.album,
    required this.durationSeconds,
    required this.artworkUrl,
    required this.highResArtworkUrl,
    required this.sourceUrl,
    required this.providerId,
    this.quality = AudioQuality.high,
    int? addedAt,
    this.playCount = 0,
    this.lyrics,
  }) : addedAt = addedAt ?? 0;

  int get durationMs => durationSeconds * 1000;

  Song copyWith({
    String? id,
    String? title,
    String? artist,
    String? album,
    int? durationSeconds,
    String? artworkUrl,
    String? highResArtworkUrl,
    String? sourceUrl,
    String? providerId,
    AudioQuality? quality,
    int? addedAt,
    int? playCount,
    String? lyrics,
  }) {
    return Song(
      id: id ?? this.id,
      title: title ?? this.title,
      artist: artist ?? this.artist,
      album: album ?? this.album,
      durationSeconds: durationSeconds ?? this.durationSeconds,
      artworkUrl: artworkUrl ?? this.artworkUrl,
      highResArtworkUrl: highResArtworkUrl ?? this.highResArtworkUrl,
      sourceUrl: sourceUrl ?? this.sourceUrl,
      providerId: providerId ?? this.providerId,
      quality: quality ?? this.quality,
      addedAt: addedAt ?? this.addedAt,
      playCount: playCount ?? this.playCount,
      lyrics: lyrics ?? this.lyrics,
    );
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'title': title,
    'artist': artist,
    'album': album,
    'durationSeconds': durationSeconds,
    'artworkUrl': artworkUrl,
    'highResArtworkUrl': highResArtworkUrl,
    'sourceUrl': sourceUrl,
    'providerId': providerId,
    'quality': quality.name,
    'addedAt': addedAt,
    'playCount': playCount,
    'lyrics': lyrics,
  };

  factory Song.fromJson(Map<String, dynamic> json) => Song(
    id: json['id'] as String,
    title: json['title'] as String? ?? 'Unknown Title',
    artist: json['artist'] as String? ?? 'Unknown Artist',
    album: json['album'] as String? ?? 'Unknown Album',
    durationSeconds: (json['durationSeconds'] as num?)?.toInt() ?? 0,
    artworkUrl: json['artworkUrl'] as String? ?? '',
    highResArtworkUrl: json['highResArtworkUrl'] as String? ?? '',
    sourceUrl: json['sourceUrl'] as String? ?? '',
    providerId: json['providerId'] as String? ?? 'youtube',
    quality: AudioQuality.fromString(json['quality'] as String?),
    addedAt: (json['addedAt'] as num?)?.toInt() ?? 0,
    playCount: (json['playCount'] as num?)?.toInt() ?? 0,
    lyrics: json['lyrics'] as String?,
  );

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is Song && runtimeType == other.runtimeType && id == other.id;

  @override
  int get hashCode => id.hashCode;
}


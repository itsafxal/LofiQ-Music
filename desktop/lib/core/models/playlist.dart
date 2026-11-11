class Playlist {
  final String id;
  final String name;
  final String description;
  final String artworkUrl;
  final bool isSystem;
  final int createdAt;
  final List<String> songIds;

  const Playlist({
    required this.id,
    required this.name,
    this.description = '',
    this.artworkUrl = '',
    this.isSystem = false,
    int? createdAt,
    this.songIds = const [],
  }) : createdAt = createdAt ?? 0;

  Playlist copyWith({
    String? id,
    String? name,
    String? description,
    String? artworkUrl,
    bool? isSystem,
    int? createdAt,
    List<String>? songIds,
  }) {
    return Playlist(
      id: id ?? this.id,
      name: name ?? this.name,
      description: description ?? this.description,
      artworkUrl: artworkUrl ?? this.artworkUrl,
      isSystem: isSystem ?? this.isSystem,
      createdAt: createdAt ?? this.createdAt,
      songIds: songIds ?? this.songIds,
    );
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'name': name,
    'description': description,
    'artworkUrl': artworkUrl,
    'isSystem': isSystem,
    'createdAt': createdAt,
    'songIds': songIds,
  };

  factory Playlist.fromJson(Map<String, dynamic> json) => Playlist(
    id: json['id'] as String,
    name: json['name'] as String? ?? 'Untitled Playlist',
    description: json['description'] as String? ?? '',
    artworkUrl: json['artworkUrl'] as String? ?? '',
    isSystem: json['isSystem'] as bool? ?? false,
    createdAt: (json['createdAt'] as num?)?.toInt() ?? 0,
    songIds: (json['songIds'] as List<dynamic>?)?.map((e) => e.toString()).toList() ?? [],
  );
}


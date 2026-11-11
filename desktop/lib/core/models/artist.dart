class Artist {
  final String id;
  final String name;
  final String imageUrl;
  final int songCount;
  final String description;

  const Artist({
    required this.id,
    required this.name,
    this.imageUrl = '',
    this.songCount = 0,
    this.description = '',
  });

  Map<String, dynamic> toJson() => {
    'id': id,
    'name': name,
    'imageUrl': imageUrl,
    'songCount': songCount,
    'description': description,
  };

  factory Artist.fromJson(Map<String, dynamic> json) => Artist(
    id: json['id'] as String,
    name: json['name'] as String? ?? 'Unknown Artist',
    imageUrl: json['imageUrl'] as String? ?? '',
    songCount: (json['songCount'] as num?)?.toInt() ?? 0,
    description: json['description'] as String? ?? '',
  );
}


enum SeekbarStyle {
  lofiqWave('LOFIQ_WAVE', 'LofiQ Wave', 'Animated real-time acoustic waveform'),
  neonBar('NEON_BAR', 'Neon Bar', 'Vibrant glowing neon progress bar'),
  thinLine('THIN_LINE', 'Thin Line', 'Ultra-minimalist modern hairline bar'),
  dotMatrix('DOT_MATRIX', 'Dot Matrix', 'Digital retro LED dot-matrix array'),
  retroTape('RETRO_TAPE', 'Retro Tape', 'Vintage analog cassette tape visualizer');

  final String key;
  final String displayName;
  final String description;

  const SeekbarStyle(this.key, this.displayName, this.description);

  String get label => displayName;

  static SeekbarStyle fromString(String? val) {
    if (val == null) return SeekbarStyle.lofiqWave;
    final upper = val.toUpperCase();
    for (final s in SeekbarStyle.values) {
      if (s.name.toUpperCase() == upper || s.key == upper) return s;
    }
    return SeekbarStyle.lofiqWave;
  }
}

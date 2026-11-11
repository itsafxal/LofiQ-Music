enum AudioQuality {
  high('HIGH', 320, '320 kbps (High Fidelity)'),
  standard('STANDARD', 160, '160 kbps (Standard)'),
  dataSaver('DATA_SAVER', 96, '96 kbps (Data Saver)'),
  auto('AUTO', 0, 'Auto (Adaptive)');

  final String key;
  final int bitrateKbps;
  final String label;

  const AudioQuality(this.key, this.bitrateKbps, this.label);

  static AudioQuality fromString(String? val) {
    if (val == null) return AudioQuality.high;
    final upper = val.toUpperCase();
    for (final q in AudioQuality.values) {
      if (q.name.toUpperCase() == upper || q.key == upper) return q;
    }
    return AudioQuality.high;
  }
}


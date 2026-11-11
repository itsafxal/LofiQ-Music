import 'package:flutter/foundation.dart';

class EqPreset {
  final String name;
  final List<int> bandLevels; // in millibels (-1500 to +1500)
  const EqPreset(this.name, this.bandLevels);
}

class EqualizerController extends ChangeNotifier {
  static final EqualizerController instance = EqualizerController._();
  factory EqualizerController() => instance;

  EqualizerController._();

  bool _isEnabled = true;
  double _bassBoostStrength = 0.0; // 0.0 to 1.0
  double _virtualizerStrength = 0.0; // 0.0 to 1.0
  int _selectedPresetIndex = 0;

  // 10 Standard ISO Octave Audio Equalizer Bands (Hz)
  final List<int> _bandFrequencies = [31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000];
  final List<int> _bandLevels = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0]; // in millibels

  static const int minLevel = -1500;
  static const int maxLevel = 1500;

  static const List<EqPreset> _presetList = [
    EqPreset('Flat', [0, 0, 0, 0, 0, 0, 0, 0, 0, 0]),
    EqPreset('Deep Bass', [800, 700, 500, 300, 100, 0, 0, 0, 0, 0]),
    EqPreset('Bass Boost', [600, 500, 400, 200, 0, 0, 100, 200, 200, 100]),
    EqPreset('Club / EDM', [500, 400, 300, 100, -100, 200, 400, 500, 400, 300]),
    EqPreset('Rock', [500, 400, 300, 100, 0, 100, 300, 400, 500, 400]),
    EqPreset('Pop', [-100, 0, 100, 300, 400, 300, 200, 100, 0, -200]),
    EqPreset('Hip Hop', [700, 600, 400, 200, 0, 0, 200, 300, 400, 300]),
    EqPreset('Studio Master', [100, 100, 50, 0, 0, 0, 50, 100, 100, 50]),
    EqPreset('Vocal Booster', [-200, -100, 0, 200, 500, 600, 500, 300, 100, 0]),
    EqPreset('Lofi Warmth', [500, 400, 300, 200, 100, 0, -200, -300, -400, -500]),
    EqPreset('Acoustic', [400, 300, 200, 100, 200, 300, 400, 300, 200, 100]),
    EqPreset('Jazz', [300, 200, 100, 100, 200, 300, 400, 300, 200, 100]),
    EqPreset('Classical', [400, 300, 200, 100, -100, 0, 200, 300, 400, 400]),
    EqPreset('Electronic', [500, 400, 200, 0, -200, 200, 300, 400, 500, 400]),
  ];

  bool get isEnabled => _isEnabled;
  double get bassBoostStrength => _bassBoostStrength;
  double get virtualizerStrength => _virtualizerStrength;
  int get bassBoost => (_bassBoostStrength * 1000).round();
  int get virtualizer => (_virtualizerStrength * 1000).round();
  int get selectedPresetIndex => _selectedPresetIndex;

  List<int> get bandFrequencies => List.unmodifiable(_bandFrequencies);
  List<int> get bandLevels => List.unmodifiable(_bandLevels);
  List<String> get presets => _presetList.map((p) => p.name).toList();

  String get currentPreset {
    if (_selectedPresetIndex >= 0 && _selectedPresetIndex < _presetList.length) {
      return _presetList[_selectedPresetIndex].name;
    }
    return 'Custom';
  }

  void setEnabled(bool enabled) {
    _isEnabled = enabled;
    notifyListeners();
  }

  void setBassBoost(num strength) {
    if (strength > 1.0) {
      _bassBoostStrength = (strength / 1000.0).clamp(0.0, 1.0);
    } else {
      _bassBoostStrength = strength.toDouble().clamp(0.0, 1.0);
    }
    notifyListeners();
  }

  void setVirtualizer(num strength) {
    if (strength > 1.0) {
      _virtualizerStrength = (strength / 1000.0).clamp(0.0, 1.0);
    } else {
      _virtualizerStrength = strength.toDouble().clamp(0.0, 1.0);
    }
    notifyListeners();
  }

  int getBandLevel(int bandIndex) {
    if (bandIndex < 0 || bandIndex >= _bandLevels.length) return 0;
    return _bandLevels[bandIndex];
  }

  void setBandLevel(int bandIndex, int levelMillibels) {
    if (bandIndex < 0 || bandIndex >= _bandLevels.length) return;
    _bandLevels[bandIndex] = levelMillibels.clamp(minLevel, maxLevel);
    _selectedPresetIndex = -1; // custom
    notifyListeners();
  }

  void applyPreset(String name) {
    final idx = _presetList.indexWhere((p) => p.name.toLowerCase() == name.toLowerCase());
    if (idx != -1) {
      selectPreset(idx);
    }
  }

  void selectPreset(int index) {
    if (index < 0 || index >= _presetList.length) return;
    _selectedPresetIndex = index;
    final preset = _presetList[index];
    for (int i = 0; i < _bandLevels.length && i < preset.bandLevels.length; i++) {
      _bandLevels[i] = preset.bandLevels[i];
    }
    notifyListeners();
  }

  void reset() {
    applyPreset('Flat');
    setBassBoost(0);
    setVirtualizer(0);
  }

  /// Generates the libmpv audio filter ('af') parameter string for physical DSP processing.
  String buildMpvAudioFilter() {
    if (!_isEnabled) return '';

    final filterParts = <String>[];

    // 1. 10-Band ISO Graphic Equalizer Biquad Filter Pipeline
    // Each band converts millibels (-1500 to +1500) to decibels (-15.0dB to +15.0dB)
    for (int i = 0; i < _bandFrequencies.length && i < _bandLevels.length; i++) {
      final freq = _bandFrequencies[i];
      final gainDb = _bandLevels[i] / 100.0;
      filterParts.add('equalizer=f=$freq:width_type=o:width=1:g=$gainDb');
    }

    // 2. Bass Boost DSP Filter: Low-shelf biquad filter centered at 60Hz with up to +12dB gain
    if (_bassBoostStrength > 0.0) {
      final bassGainDb = _bassBoostStrength * 12.0;
      filterParts.add('equalizer=f=60:width_type=o:width=1.5:g=$bassGainDb');
    }

    // 3. Virtualizer DSP Filter: Stereo widening spatial acoustic expansion
    // Extrastereo multiplier scales from 1.0 (natural stereo) up to 2.5 (wide soundstage)
    // Routed through libmpv's FFmpeg filter bridge (lavfi) for hardware DSP execution
    if (_virtualizerStrength > 0.0) {
      final stereoMultiplier = 1.0 + (_virtualizerStrength * 1.5);
      filterParts.add('lavfi=[extrastereo=m=$stereoMultiplier]');
    }

    return filterParts.join(',');
  }
}

import 'audio_quality.dart';
import 'seekbar_style.dart';

class UserSettings {
  final AudioQuality audioQuality;
  final int crossfadeDurationSec;
  final bool prefetchEnabled;
  final bool dynamicBackgroundEnabled;
  final bool liquidGlassEnabled;
  final bool fullScreenArtworkEnabled;
  final bool equalizerEnabled;
  final SeekbarStyle seekbarStyle;
  final bool performanceModeEnabled;
  final String appIconTheme;
  final String customBrandColorHex;
  final bool scrobbleLastFmEnabled;
  final String? lastFmSessionKey;
  final String? lastFmUsername;
  final bool scrobbleListenBrainzEnabled;
  final String? listenBrainzToken;
  final bool discordRpcEnabled;
  final String downloadDirectory;

  const UserSettings({
    this.audioQuality = AudioQuality.high,
    this.crossfadeDurationSec = 8,
    this.prefetchEnabled = true,
    this.dynamicBackgroundEnabled = true,
    this.liquidGlassEnabled = true,
    this.fullScreenArtworkEnabled = true,
    this.equalizerEnabled = true,
    this.seekbarStyle = SeekbarStyle.lofiqWave,
    this.performanceModeEnabled = false,
    this.appIconTheme = 'purple',
    this.customBrandColorHex = '#7C3AED',
    this.scrobbleLastFmEnabled = false,
    this.lastFmSessionKey,
    this.lastFmUsername,
    this.scrobbleListenBrainzEnabled = false,
    this.listenBrainzToken,
    this.discordRpcEnabled = true,
    this.downloadDirectory = '',
  });

  int get crossfadeDurationSeconds => crossfadeDurationSec;
  bool get lastFmEnabled => scrobbleLastFmEnabled;

  UserSettings copyWith({
    AudioQuality? audioQuality,
    int? crossfadeDurationSec,
    int? crossfadeDurationSeconds,
    bool? prefetchEnabled,
    bool? dynamicBackgroundEnabled,
    bool? liquidGlassEnabled,
    bool? fullScreenArtworkEnabled,
    bool? equalizerEnabled,
    SeekbarStyle? seekbarStyle,
    bool? performanceModeEnabled,
    String? appIconTheme,
    String? customBrandColorHex,
    bool? scrobbleLastFmEnabled,
    bool? lastFmEnabled,
    String? lastFmSessionKey,
    String? lastFmUsername,
    bool? scrobbleListenBrainzEnabled,
    String? listenBrainzToken,
    bool? discordRpcEnabled,
    String? downloadDirectory,
  }) {
    return UserSettings(
      audioQuality: audioQuality ?? this.audioQuality,
      crossfadeDurationSec: crossfadeDurationSeconds ?? crossfadeDurationSec ?? this.crossfadeDurationSec,
      prefetchEnabled: prefetchEnabled ?? this.prefetchEnabled,
      dynamicBackgroundEnabled: dynamicBackgroundEnabled ?? this.dynamicBackgroundEnabled,
      liquidGlassEnabled: liquidGlassEnabled ?? this.liquidGlassEnabled,
      fullScreenArtworkEnabled: fullScreenArtworkEnabled ?? this.fullScreenArtworkEnabled,
      equalizerEnabled: equalizerEnabled ?? this.equalizerEnabled,
      seekbarStyle: seekbarStyle ?? this.seekbarStyle,
      performanceModeEnabled: performanceModeEnabled ?? this.performanceModeEnabled,
      appIconTheme: appIconTheme ?? this.appIconTheme,
      customBrandColorHex: customBrandColorHex ?? this.customBrandColorHex,
      scrobbleLastFmEnabled: lastFmEnabled ?? scrobbleLastFmEnabled ?? this.scrobbleLastFmEnabled,
      lastFmSessionKey: lastFmSessionKey ?? this.lastFmSessionKey,
      lastFmUsername: lastFmUsername ?? this.lastFmUsername,
      scrobbleListenBrainzEnabled: scrobbleListenBrainzEnabled ?? this.scrobbleListenBrainzEnabled,
      listenBrainzToken: listenBrainzToken ?? this.listenBrainzToken,
      discordRpcEnabled: discordRpcEnabled ?? this.discordRpcEnabled,
      downloadDirectory: downloadDirectory ?? this.downloadDirectory,
    );
  }
}

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/audio_quality.dart';
import '../models/seekbar_style.dart';
import '../models/user_settings.dart';
import '../utils/logger.dart';

class PreferencesManager extends ChangeNotifier {
  static PreferencesManager? _instance;
  static PreferencesManager get instance => _instance ??= PreferencesManager._();
  factory PreferencesManager() => instance;

  PreferencesManager._();

  SharedPreferences? _prefs;
  UserSettings _settings = const UserSettings();

  UserSettings get settings => _settings;

  Future<void> init() async {
    _prefs = await SharedPreferences.getInstance();
    _loadSettings();
    LofiQLogger.i(LogCategory.app, 'Loaded Windows UserPreferences');
  }

  void _loadSettings() {
    if (_prefs == null) return;
    _settings = UserSettings(
      audioQuality: AudioQuality.fromString(_prefs!.getString('audio_quality')),
      crossfadeDurationSec: _prefs!.getInt('crossfade_duration_sec') ?? 8,
      prefetchEnabled: _prefs!.getBool('prefetch_enabled') ?? true,
      dynamicBackgroundEnabled: _prefs!.getBool('dynamic_background_enabled') ?? true,
      liquidGlassEnabled: _prefs!.getBool('liquid_glass_enabled') ?? true,
      fullScreenArtworkEnabled: _prefs!.getBool('full_screen_artwork_enabled') ?? true,
      equalizerEnabled: _prefs!.getBool('equalizer_enabled') ?? true,
      seekbarStyle: SeekbarStyle.fromString(_prefs!.getString('seekbar_style')),
      performanceModeEnabled: _prefs!.getBool('performance_mode_enabled') ?? false,
      appIconTheme: _prefs!.getString('app_icon_theme') ?? 'purple',
      customBrandColorHex: _prefs!.getString('custom_brand_color') ?? '#7C3AED',
      scrobbleLastFmEnabled: _prefs!.getBool('scrobble_lastfm_enabled') ?? false,
      lastFmSessionKey: _prefs!.getString('lastfm_session_key'),
      lastFmUsername: _prefs!.getString('lastfm_username'),
      scrobbleListenBrainzEnabled: _prefs!.getBool('scrobble_listenbrainz_enabled') ?? false,
      listenBrainzToken: _prefs!.getString('listenbrainz_token'),
      discordRpcEnabled: _prefs!.getBool('discord_rpc_enabled') ?? true,
      downloadDirectory: _prefs!.getString('download_directory') ?? '',
    );
    notifyListeners();
  }

  Future<void> saveSettings(UserSettings newSettings) async {
    await updateSettings((_) => newSettings);
  }

  Future<void> updateSettings(UserSettings Function(UserSettings current) updater) async {
    _settings = updater(_settings);
    notifyListeners();
    if (_prefs == null) return;

    await _prefs!.setString('audio_quality', _settings.audioQuality.key);
    await _prefs!.setInt('crossfade_duration_sec', _settings.crossfadeDurationSec);
    await _prefs!.setBool('prefetch_enabled', _settings.prefetchEnabled);
    await _prefs!.setBool('dynamic_background_enabled', _settings.dynamicBackgroundEnabled);
    await _prefs!.setBool('liquid_glass_enabled', _settings.liquidGlassEnabled);
    await _prefs!.setBool('full_screen_artwork_enabled', _settings.fullScreenArtworkEnabled);
    await _prefs!.setBool('equalizer_enabled', _settings.equalizerEnabled);
    await _prefs!.setString('seekbar_style', _settings.seekbarStyle.key);
    await _prefs!.setBool('performance_mode_enabled', _settings.performanceModeEnabled);
    await _prefs!.setString('app_icon_theme', _settings.appIconTheme);
    await _prefs!.setString('custom_brand_color', _settings.customBrandColorHex);
    await _prefs!.setBool('scrobble_lastfm_enabled', _settings.scrobbleLastFmEnabled);
    if (_settings.lastFmSessionKey != null) {
      await _prefs!.setString('lastfm_session_key', _settings.lastFmSessionKey!);
    }
    if (_settings.lastFmUsername != null) {
      await _prefs!.setString('lastfm_username', _settings.lastFmUsername!);
    }
    await _prefs!.setBool('scrobble_listenbrainz_enabled', _settings.scrobbleListenBrainzEnabled);
    if (_settings.listenBrainzToken != null) {
      await _prefs!.setString('listenbrainz_token', _settings.listenBrainzToken!);
    }
    await _prefs!.setBool('discord_rpc_enabled', _settings.discordRpcEnabled);
    await _prefs!.setString('download_directory', _settings.downloadDirectory);
  }
}

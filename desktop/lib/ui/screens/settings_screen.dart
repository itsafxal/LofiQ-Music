import 'package:flutter/material.dart';
import '../../core/models/audio_quality.dart';
import '../../core/models/seekbar_style.dart';
import '../../core/models/user_settings.dart';
import '../../core/preferences/preferences_manager.dart';
import '../../core/theme/colors.dart';
import '../../data/update/windows_update_manager.dart';
import '../components/glass_card.dart';

class SettingsScreen extends StatefulWidget {
  final VoidCallback? onOpenDiagnostics;

  const SettingsScreen({Key? key, this.onOpenDiagnostics}) : super(key: key);

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  final PreferencesManager _prefs = PreferencesManager();
  final WindowsUpdateManager _updateManager = WindowsUpdateManager();

  late UserSettings _settings;
  bool _isCheckingUpdate = false;
  String? _updateStatus;
  UpdateInfo? _availableUpdate;

  final TextEditingController _lastFmUsernameController = TextEditingController();
  final TextEditingController _lastFmSessionController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _settings = _prefs.settings;
    _lastFmUsernameController.text = _settings.lastFmUsername ?? '';
    _lastFmSessionController.text = _settings.lastFmSessionKey ?? '';
  }

  @override
  void dispose() {
    _lastFmUsernameController.dispose();
    _lastFmSessionController.dispose();
    super.dispose();
  }

  Future<void> _updateSettings(UserSettings newSettings) async {
    setState(() => _settings = newSettings);
    await _prefs.saveSettings(newSettings);
  }

  Future<void> _checkForUpdate() async {
    setState(() {
      _isCheckingUpdate = true;
      _updateStatus = 'Querying primary and secondary update servers...';
      _availableUpdate = null;
    });

    final update = await _updateManager.checkForUpdate();

    if (mounted) {
      setState(() {
        _isCheckingUpdate = false;
        _availableUpdate = update;
        if (update != null) {
          _updateStatus = 'New version ${update.versionName} (Build ${update.versionCode}) available from ${update.source}!';
        } else {
          _updateStatus = 'LofiQ V1.0.2 is up to date (Build 65).';
        }
      });
    }
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
            // Top Bar
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: LofiQColors.neonPurple.withOpacity(0.15),
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: LofiQColors.neonPurple.withOpacity(0.3)),
                  ),
                  child: const Icon(Icons.settings_rounded, color: LofiQColors.neonPurple, size: 28),
                ),
                const SizedBox(width: 16),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Settings & Configuration',
                      style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                        color: LofiQColors.textPrimary,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'Audio stream quality, dynamic visualizers, Last.fm scrobbler, and failover updater',
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: LofiQColors.textSecondary,
                      ),
                    ),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 28),

            // Section 1: Audio Playback Quality
            _buildSectionHeader('AUDIO PLAYBACK & STREAMING', Icons.audiotrack_rounded),
            GlassCard(
              padding: const EdgeInsets.all(20),
              child: Column(
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('Streaming Audio Quality',
                              style: TextStyle(color: LofiQColors.textPrimary, fontWeight: FontWeight.bold, fontSize: 16)),
                          SizedBox(height: 4),
                          Text('Higher bitrates deliver richer bass and crystal lo-fi tape dynamics',
                              style: TextStyle(color: LofiQColors.textSecondary, fontSize: 12)),
                        ],
                      ),
                      DropdownButton<AudioQuality>(
                        value: _settings.audioQuality,
                        dropdownColor: LofiQColors.surfaceBlack,
                        underline: const SizedBox(),
                        style: const TextStyle(color: LofiQColors.neonCyan, fontWeight: FontWeight.bold),
                        items: AudioQuality.values.map((q) {
                          return DropdownMenuItem<AudioQuality>(
                            value: q,
                            child: Text(q.label),
                          );
                        }).toList(),
                        onChanged: (val) {
                          if (val != null) {
                            _updateSettings(_settings.copyWith(audioQuality: val));
                          }
                        },
                      ),
                    ],
                  ),
                  const Divider(color: LofiQColors.glassBorderSubtle, height: 28),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text('Crossfade Duration',
                              style: TextStyle(color: LofiQColors.textPrimary, fontWeight: FontWeight.bold, fontSize: 16)),
                          const SizedBox(height: 4),
                          Text('${_settings.crossfadeDurationSeconds} seconds smooth overlap between consecutive tracks',
                              style: const TextStyle(color: LofiQColors.textSecondary, fontSize: 12)),
                        ],
                      ),
                      SizedBox(
                        width: 180,
                        child: Slider(
                          value: _settings.crossfadeDurationSeconds.toDouble(),
                          min: 0,
                          max: 12,
                          divisions: 12,
                          activeColor: LofiQColors.neonPurple,
                          inactiveColor: LofiQColors.glassBorderSubtle,
                          onChanged: (val) {
                            _updateSettings(_settings.copyWith(crossfadeDurationSeconds: val.round()));
                          },
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 24),

            // Section 2: Visual Interface & Liquid Glass
            _buildSectionHeader('LIQUID GLASS & VISUAL STYLING', Icons.palette_rounded),
            GlassCard(
              padding: const EdgeInsets.all(20),
              child: Column(
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('Seekbar Visual Waveform Style',
                              style: TextStyle(color: LofiQColors.textPrimary, fontWeight: FontWeight.bold, fontSize: 16)),
                          SizedBox(height: 4),
                          Text('Custom animated wave generator applied to player bar',
                              style: TextStyle(color: LofiQColors.textSecondary, fontSize: 12)),
                        ],
                      ),
                      DropdownButton<SeekbarStyle>(
                        value: _settings.seekbarStyle,
                        dropdownColor: LofiQColors.surfaceBlack,
                        underline: const SizedBox(),
                        style: const TextStyle(color: LofiQColors.neonPurple, fontWeight: FontWeight.bold),
                        items: SeekbarStyle.values.map((s) {
                          return DropdownMenuItem<SeekbarStyle>(
                            value: s,
                            child: Text(s.label),
                          );
                        }).toList(),
                        onChanged: (val) {
                          if (val != null) {
                            _updateSettings(_settings.copyWith(seekbarStyle: val));
                          }
                        },
                      ),
                    ],
                  ),
                  const Divider(color: LofiQColors.glassBorderSubtle, height: 28),
                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    title: const Text('Dynamic Ambient Background Glow',
                        style: TextStyle(color: LofiQColors.textPrimary, fontWeight: FontWeight.bold, fontSize: 16)),
                    subtitle: const Text('Reacts dynamically to dominant album artwork colors with fluid radial blooms',
                        style: TextStyle(color: LofiQColors.textSecondary, fontSize: 12)),
                    value: _settings.dynamicBackgroundEnabled,
                    activeColor: LofiQColors.neonPurple,
                    onChanged: (val) {
                      _updateSettings(_settings.copyWith(dynamicBackgroundEnabled: val));
                    },
                  ),
                ],
              ),
            ),
            const SizedBox(height: 24),

            // Section 3: Last.fm Scrobbler
            _buildSectionHeader('LAST.FM SCROBBLER & INTEGRATION', Icons.radio_rounded),
            GlassCard(
              padding: const EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    title: const Text('Enable Last.fm Scrobbling',
                        style: TextStyle(color: LofiQColors.textPrimary, fontWeight: FontWeight.bold, fontSize: 16)),
                    subtitle: const Text('Automatically scrobbles played tracks once 50% or 4 minutes of track has played',
                        style: TextStyle(color: LofiQColors.textSecondary, fontSize: 12)),
                    value: _settings.lastFmEnabled,
                    activeColor: LofiQColors.neonPurple,
                    onChanged: (val) {
                      _updateSettings(_settings.copyWith(lastFmEnabled: val));
                    },
                  ),
                  if (_settings.lastFmEnabled) ...[
                    const SizedBox(height: 16),
                    TextField(
                      controller: _lastFmUsernameController,
                      style: const TextStyle(color: LofiQColors.textPrimary),
                      decoration: const InputDecoration(
                        labelText: 'Last.fm Username',
                        labelStyle: TextStyle(color: LofiQColors.textSecondary),
                        border: OutlineInputBorder(),
                        focusedBorder: OutlineInputBorder(borderSide: BorderSide(color: LofiQColors.neonPurple)),
                      ),
                      onChanged: (val) => _updateSettings(_settings.copyWith(lastFmUsername: val.trim())),
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: _lastFmSessionController,
                      style: const TextStyle(color: LofiQColors.textPrimary),
                      obscureText: true,
                      decoration: const InputDecoration(
                        labelText: 'Session Key / Secret Token',
                        labelStyle: TextStyle(color: LofiQColors.textSecondary),
                        border: OutlineInputBorder(),
                        focusedBorder: OutlineInputBorder(borderSide: BorderSide(color: LofiQColors.neonPurple)),
                      ),
                      onChanged: (val) => _updateSettings(_settings.copyWith(lastFmSessionKey: val.trim())),
                    ),
                  ],
                ],
              ),
            ),
            const SizedBox(height: 24),

            // Section 4: Dual Failover Updater & Version Info
            _buildSectionHeader('IN-APP UPDATER & FAILOVER ARCHITECTURE', Icons.system_update_rounded),
            GlassCard(
              padding: const EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(8),
                        decoration: BoxDecoration(
                          color: LofiQColors.neonCyan.withOpacity(0.15),
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: const Icon(Icons.verified_rounded, color: LofiQColors.neonCyan, size: 20),
                      ),
                      const SizedBox(width: 12),
                      const Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('LofiQ Desktop Edition V1.0.2',
                              style: TextStyle(color: LofiQColors.textPrimary, fontWeight: FontWeight.bold, fontSize: 16)),
                          SizedBox(height: 2),
                          Text('Version Code 65 • Package com.sargamify.app • Commit aeec71b',
                              style: TextStyle(color: LofiQColors.textSecondary, fontSize: 12)),
                        ],
                      ),
                      const Spacer(),
                      ElevatedButton.icon(
                        onPressed: _isCheckingUpdate ? null : _checkForUpdate,
                        icon: _isCheckingUpdate
                            ? const SizedBox(
                                width: 14,
                                height: 14,
                                child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                              )
                            : const Icon(Icons.refresh_rounded, size: 16),
                        label: Text(_isCheckingUpdate ? 'Checking...' : 'Check for Updates'),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: LofiQColors.neonPurple,
                          foregroundColor: Colors.white,
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                        ),
                      ),
                    ],
                  ),
                  if (_updateStatus != null) ...[
                    const SizedBox(height: 16),
                    Container(
                      padding: const EdgeInsets.all(12),
                      width: double.infinity,
                      decoration: BoxDecoration(
                        color: _availableUpdate != null
                            ? LofiQColors.neonPurple.withOpacity(0.15)
                            : Colors.white.withOpacity(0.05),
                        borderRadius: BorderRadius.circular(10),
                        border: Border.all(
                          color: _availableUpdate != null ? LofiQColors.neonPurple : LofiQColors.glassBorderSubtle,
                        ),
                      ),
                      child: Text(
                        _updateStatus!,
                        style: TextStyle(
                          color: _availableUpdate != null ? LofiQColors.neonCyan : LofiQColors.textSecondary,
                          fontSize: 13,
                        ),
                      ),
                    ),
                  ],
                ],
              ),
            ),
            const SizedBox(height: 24),

            // Section 5: Advanced Diagnostics
            _buildSectionHeader('DIAGNOSTICS & SYSTEM', Icons.build_rounded),
            GlassCard(
              padding: const EdgeInsets.all(20),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Network Endpoint Diagnostics',
                          style: TextStyle(color: LofiQColors.textPrimary, fontWeight: FontWeight.bold, fontSize: 16)),
                      SizedBox(height: 4),
                      Text('Probe YouTube InnerTube, JioSaavn 320k, Piped, LRCLIB, and Vercel/Netlify latency',
                          style: TextStyle(color: LofiQColors.textSecondary, fontSize: 12)),
                    ],
                  ),
                  ElevatedButton.icon(
                    onPressed: widget.onOpenDiagnostics,
                    icon: const Icon(Icons.network_check_rounded, size: 18),
                    label: const Text('Open Diagnostics'),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: LofiQColors.cardBackground,
                      foregroundColor: Colors.amber,
                      side: const BorderSide(color: LofiQColors.glassBorderSubtle),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSectionHeader(String title, IconData icon) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12.0),
      child: Row(
        children: [
          Icon(icon, size: 16, color: LofiQColors.neonPurple),
          const SizedBox(width: 8),
          Text(
            title,
            style: const TextStyle(
              color: LofiQColors.neonPurple,
              fontSize: 12,
              fontWeight: FontWeight.bold,
              letterSpacing: 1.2,
            ),
          ),
        ],
      ),
    );
  }
}

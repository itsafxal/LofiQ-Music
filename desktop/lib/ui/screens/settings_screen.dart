import 'package:flutter/material.dart';
import '../../core/models/audio_quality.dart';
import '../../core/models/seekbar_style.dart';
import '../../core/models/user_settings.dart';
import '../../core/preferences/preferences_manager.dart';
import '../../core/theme/colors.dart';
import '../../data/update/windows_update_manager.dart';
import '../components/glass/glass_components.dart';

class SettingsScreen extends StatefulWidget {
  final VoidCallback? onOpenDiagnostics;

  const SettingsScreen({super.key, this.onOpenDiagnostics});

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
          _updateStatus = 'LofiQ V1.0.3 is up to date (Build 66).';
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
                    color: LofiQColors.brandTangerine.withValues(alpha: 0.16),
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(
                      color: LofiQColors.brandTangerine.withValues(alpha: 0.35),
                      width: 1.0,
                    ),
                  ),
                  child: const Icon(Icons.settings_rounded, color: LofiQColors.brandTangerine, size: 28),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
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
                ),
              ],
            ),
            const SizedBox(height: 28),

            // Section 1: Audio Playback Quality
            GlassPanel(
              title: 'AUDIO PLAYBACK & STREAMING',
              subtitle: 'Studio-grade bitrates, acoustic crossfade, and DSP streaming settings',
              icon: Icons.audiotrack_rounded,
              child: Column(
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('Streaming Audio Quality',
                                style: TextStyle(color: LofiQColors.textPrimary, fontWeight: FontWeight.bold, fontSize: 15)),
                            SizedBox(height: 4),
                            Text('Higher bitrates deliver richer bass and crystal lo-fi tape dynamics',
                                style: TextStyle(color: LofiQColors.textSecondary, fontSize: 12)),
                          ],
                        ),
                      ),
                      const SizedBox(width: 16),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 14.0, vertical: 4.0),
                        decoration: BoxDecoration(
                          color: Colors.white.withValues(alpha: 0.08),
                          borderRadius: BorderRadius.circular(12.0),
                          border: Border.all(color: Colors.white.withValues(alpha: 0.15)),
                        ),
                        child: DropdownButton<AudioQuality>(
                          value: _settings.audioQuality,
                          dropdownColor: const Color(0xFF14141E),
                          underline: const SizedBox(),
                          style: const TextStyle(color: LofiQColors.brandTangerine, fontWeight: FontWeight.bold),
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
                      ),
                    ],
                  ),
                  const Divider(color: Colors.white12, height: 28),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text('Crossfade Duration',
                                style: TextStyle(color: LofiQColors.textPrimary, fontWeight: FontWeight.bold, fontSize: 15)),
                            const SizedBox(height: 4),
                            Text('${_settings.crossfadeDurationSeconds}s smooth acoustic overlap between consecutive tracks',
                                style: const TextStyle(color: LofiQColors.textSecondary, fontSize: 12)),
                          ],
                        ),
                      ),
                      const SizedBox(width: 16),
                      SizedBox(
                        width: 200,
                        child: GlassSlider(
                          value: _settings.crossfadeDurationSeconds.toDouble(),
                          min: 0,
                          max: 12,
                          activeColor: LofiQColors.brandTangerine,
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
            GlassPanel(
              title: 'LIQUID GLASS & VISUAL STYLING',
              subtitle: 'Liquid glass atmosphere, dynamic artwork color blooms, and waveform styles',
              icon: Icons.palette_rounded,
              child: Column(
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('Seekbar Visual Waveform Style',
                                style: TextStyle(color: LofiQColors.textPrimary, fontWeight: FontWeight.bold, fontSize: 15)),
                            SizedBox(height: 4),
                            Text('Custom animated wave generator applied to player bar',
                                style: TextStyle(color: LofiQColors.textSecondary, fontSize: 12)),
                          ],
                        ),
                      ),
                      const SizedBox(width: 16),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 14.0, vertical: 4.0),
                        decoration: BoxDecoration(
                          color: Colors.white.withValues(alpha: 0.08),
                          borderRadius: BorderRadius.circular(12.0),
                          border: Border.all(color: Colors.white.withValues(alpha: 0.15)),
                        ),
                        child: DropdownButton<SeekbarStyle>(
                          value: _settings.seekbarStyle,
                          dropdownColor: const Color(0xFF14141E),
                          underline: const SizedBox(),
                          style: const TextStyle(color: LofiQColors.brandVanilla, fontWeight: FontWeight.bold),
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
                      ),
                    ],
                  ),
                  const Divider(color: Colors.white12, height: 28),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('Dynamic Ambient Background Glow',
                                style: TextStyle(color: LofiQColors.textPrimary, fontWeight: FontWeight.bold, fontSize: 15)),
                            SizedBox(height: 4),
                            Text('Reacts dynamically to dominant album artwork colors with fluid radial blooms',
                                style: TextStyle(color: LofiQColors.textSecondary, fontSize: 12)),
                          ],
                        ),
                      ),
                      GlassSwitch(
                        value: _settings.dynamicBackgroundEnabled,
                        activeColor: LofiQColors.brandTangerine,
                        onChanged: (val) {
                          _updateSettings(_settings.copyWith(dynamicBackgroundEnabled: val));
                        },
                      ),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 24),

            // Section 3: Last.fm Scrobbler
            GlassPanel(
              title: 'LAST.FM SCROBBLER & INTEGRATION',
              subtitle: 'Real-time track scrobbling & listening telemetry',
              icon: Icons.radio_rounded,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('Enable Last.fm Scrobbling',
                                style: TextStyle(color: LofiQColors.textPrimary, fontWeight: FontWeight.bold, fontSize: 15)),
                            SizedBox(height: 4),
                            Text('Automatically scrobbles played tracks once 50% or 4 minutes has played',
                                style: TextStyle(color: LofiQColors.textSecondary, fontSize: 12)),
                          ],
                        ),
                      ),
                      GlassSwitch(
                        value: _settings.lastFmEnabled,
                        activeColor: LofiQColors.brandTangerine,
                        onChanged: (val) {
                          _updateSettings(_settings.copyWith(lastFmEnabled: val));
                        },
                      ),
                    ],
                  ),
                  if (_settings.lastFmEnabled) ...[
                    const SizedBox(height: 18),
                    GlassInput(
                      controller: _lastFmUsernameController,
                      hintText: 'Last.fm Username',
                      prefixIcon: Icons.person_rounded,
                      onChanged: (val) => _updateSettings(_settings.copyWith(lastFmUsername: val.trim())),
                    ),
                    const SizedBox(height: 12),
                    GlassInput(
                      controller: _lastFmSessionController,
                      hintText: 'Session Key / Secret Token',
                      prefixIcon: Icons.vpn_key_rounded,
                      onChanged: (val) => _updateSettings(_settings.copyWith(lastFmSessionKey: val.trim())),
                    ),
                  ],
                ],
              ),
            ),
            const SizedBox(height: 24),

            // Section 4: Dual Failover Updater & Version Info
            GlassPanel(
              title: 'IN-APP UPDATER & FAILOVER ARCHITECTURE',
              subtitle: 'Dual-host redundant update pipeline (Vercel Primary + Netlify Backup)',
              icon: Icons.system_update_rounded,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(8),
                        decoration: BoxDecoration(
                          color: LofiQColors.brandBrunswickGreen.withValues(alpha: 0.35),
                          borderRadius: BorderRadius.circular(10),
                          border: Border.all(color: Colors.greenAccent.withValues(alpha: 0.4)),
                        ),
                        child: const Icon(Icons.verified_rounded, color: Colors.greenAccent, size: 20),
                      ),
                      const SizedBox(width: 14),
                      const Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('LofiQ Desktop Edition V1.0.3',
                                style: TextStyle(color: LofiQColors.textPrimary, fontWeight: FontWeight.bold, fontSize: 15)),
                            SizedBox(height: 2),
                            Text('Version Code 66 • Package com.sargamify.app',
                                style: TextStyle(color: LofiQColors.textSecondary, fontSize: 12)),
                          ],
                        ),
                      ),
                      const SizedBox(width: 12),
                      GlassButton(
                        onPressed: _isCheckingUpdate ? null : _checkForUpdate,
                        isPrimary: true,
                        accentColor: LofiQColors.brandTangerine,
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            if (_isCheckingUpdate)
                              const SizedBox(
                                width: 14,
                                height: 14,
                                child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                              )
                            else
                              const Icon(Icons.refresh_rounded, size: 16, color: Colors.white),
                            const SizedBox(width: 8),
                            Text(
                              _isCheckingUpdate ? 'Checking...' : 'Check for Updates',
                              style: const TextStyle(fontWeight: FontWeight.bold, color: Colors.white, fontSize: 13),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  if (_updateStatus != null) ...[
                    const SizedBox(height: 16),
                    GlassCard(
                      preset: GlassPreset.subtle,
                      padding: const EdgeInsets.all(14),
                      hasGlow: _availableUpdate != null,
                      glowColor: LofiQColors.brandTangerine,
                      child: Row(
                        children: [
                          Icon(
                            _availableUpdate != null ? Icons.download_rounded : Icons.info_outline_rounded,
                            size: 18,
                            color: _availableUpdate != null ? LofiQColors.brandTangerine : LofiQColors.textSecondary,
                          ),
                          const SizedBox(width: 10),
                          Expanded(
                            child: Text(
                              _updateStatus!,
                              style: TextStyle(
                                color: _availableUpdate != null ? LofiQColors.brandVanilla : LofiQColors.textSecondary,
                                fontSize: 13,
                                fontWeight: _availableUpdate != null ? FontWeight.w600 : FontWeight.normal,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ],
              ),
            ),
            const SizedBox(height: 24),

            // Section 5: Advanced Diagnostics
            GlassPanel(
              title: 'DIAGNOSTICS & SYSTEM TELEMETRY',
              subtitle: 'Endpoint health, latency meters, and resolver inspection',
              icon: Icons.build_rounded,
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Network Endpoint Diagnostics',
                            style: TextStyle(color: LofiQColors.textPrimary, fontWeight: FontWeight.bold, fontSize: 15)),
                        SizedBox(height: 4),
                        Text('Probe YouTube InnerTube, JioSaavn 320k, Piped, LRCLIB, and Vercel/Netlify latency',
                            style: TextStyle(color: LofiQColors.textSecondary, fontSize: 12)),
                      ],
                    ),
                  ),
                  const SizedBox(width: 16),
                  GlassButton(
                    onPressed: widget.onOpenDiagnostics,
                    isPrimary: false,
                    child: const Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(Icons.network_check_rounded, size: 16, color: LofiQColors.brandVanilla),
                        SizedBox(width: 8),
                        Text('Open Diagnostics', style: TextStyle(color: LofiQColors.brandVanilla, fontWeight: FontWeight.bold)),
                      ],
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
}

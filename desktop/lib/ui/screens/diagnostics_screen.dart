import 'dart:async';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import '../../core/theme/colors.dart';
import '../../data/resolvers/resolver_manager.dart';
import '../../data/lyrics/lyrics_repository.dart';
import '../components/glass/glass_components.dart';

class DiagnosticsScreen extends StatefulWidget {
  const DiagnosticsScreen({Key? key}) : super(key: key);

  @override
  State<DiagnosticsScreen> createState() => _DiagnosticsScreenState();
}

class EndpointStatus {
  final String name;
  final String url;
  final String service;
  bool isTesting;
  bool isOnline;
  int latencyMs;
  int statusCode;
  String? errorMessage;

  EndpointStatus({
    required this.name,
    required this.url,
    required this.service,
    this.isTesting = false,
    this.isOnline = false,
    this.latencyMs = 0,
    this.statusCode = 0,
    this.errorMessage,
  });
}

class _DiagnosticsScreenState extends State<DiagnosticsScreen> {
  final List<EndpointStatus> _endpoints = [
    EndpointStatus(
      name: 'Primary Update Endpoint',
      url: 'https://lofiq-web.vercel.app/version.json',
      service: 'Vercel Dual-Deploy',
    ),
    EndpointStatus(
      name: 'Backup Failover Endpoint',
      url: 'https://mybackuplofiq.netlify.app/version.json',
      service: 'Netlify Internal Backup',
    ),
    EndpointStatus(
      name: 'JioSaavn Search & Cipher API',
      url: 'https://www.jiosaavn.com/api.php?__call=autocomplete.get&_format=json&_marker=0&query=lofi',
      service: 'JioSaavn 320kbps Lossless',
    ),
    EndpointStatus(
      name: 'YouTube InnerTube API',
      url: 'https://www.youtube.com/youtubei/v1/player',
      service: 'YouTube Audio Streaming',
    ),
    EndpointStatus(
      name: 'LRCLIB Synced Lyrics API',
      url: 'https://lrclib.net/api/get?track_name=test&artist_name=test&duration=100',
      service: 'LRCLIB Karaoke Provider',
    ),
    EndpointStatus(
      name: 'Kugou Lyrics Search',
      url: 'http://mobilecdn.kugou.com/api/v3/search/song?keyword=lofi&page=1&pagesize=1',
      service: 'Kugou Secondary Provider',
    ),
    EndpointStatus(
      name: 'Piped Mirror (Kavin.rocks)',
      url: 'https://pipedapi.kavin.rocks',
      service: 'Piped Fallback Gateway',
    ),
  ];

  bool _isTestingAll = false;

  @override
  void initState() {
    super.initState();
    _testAllEndpoints();
  }

  Future<void> _testEndpoint(EndpointStatus ep) async {
    setState(() {
      ep.isTesting = true;
      ep.errorMessage = null;
    });

    final stopwatch = Stopwatch()..start();
    try {
      final uri = Uri.parse(ep.url);
      final resp = await http.get(uri).timeout(const Duration(seconds: 8));
      stopwatch.stop();

      setState(() {
        ep.isTesting = false;
        ep.latencyMs = stopwatch.elapsedMilliseconds;
        ep.statusCode = resp.statusCode;
        ep.isOnline = resp.statusCode >= 200 && resp.statusCode < 400;
        if (!ep.isOnline) {
          ep.errorMessage = 'HTTP ${resp.statusCode}';
        }
      });
    } catch (e) {
      stopwatch.stop();
      setState(() {
        ep.isTesting = false;
        ep.isOnline = false;
        ep.latencyMs = stopwatch.elapsedMilliseconds;
        ep.errorMessage = e.toString().contains('TimeoutException') ? 'Timed out (8s)' : 'Connection failed';
      });
    }
  }

  Future<void> _testAllEndpoints() async {
    setState(() => _isTestingAll = true);
    await Future.wait(_endpoints.map((ep) => _testEndpoint(ep)));
    if (mounted) {
      setState(() => _isTestingAll = false);
    }
  }

  void _clearCaches() {
    ResolverManager().clearCache();
    LyricsRepository().clearCache();
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('All stream and lyrics caches cleared successfully'),
        backgroundColor: LofiQColors.cardBackground,
      ),
    );
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
                    color: Colors.amber.withOpacity(0.15),
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: Colors.amber.withOpacity(0.3)),
                  ),
                  child: const Icon(Icons.network_check_rounded, color: Colors.amber, size: 28),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Network & Infrastructure Diagnostics',
                        style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                          color: LofiQColors.textPrimary,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        'Live round-trip latency, service health, failover verification, and resolver telemetry',
                        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                          color: LofiQColors.textSecondary,
                        ),
                      ),
                    ],
                  ),
                ),
                GlassButton(
                  onPressed: _isTestingAll ? null : _testAllEndpoints,
                  isPrimary: true,
                  accentColor: brandTangerine,
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      if (_isTestingAll)
                        const SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                        )
                      else
                        const Icon(Icons.speed_rounded, size: 18),
                      const SizedBox(width: 8),
                      Text(_isTestingAll ? 'Testing...' : 'Probe All Services'),
                    ],
                  ),
                ),
                const SizedBox(width: 12),
                GlassButton(
                  onPressed: _clearCaches,
                  preset: GlassPreset.subtle,
                  child: const Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(Icons.cleaning_services_rounded, size: 18),
                      SizedBox(width: 8),
                      Text('Flush Caches'),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 24),

            // Endpoint List
            ListView.builder(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              itemCount: _endpoints.length,
              itemBuilder: (context, index) {
                final ep = _endpoints[index];

                Color statusColor = Colors.grey;
                String statusText = 'Pending';

                if (ep.isTesting) {
                  statusColor = Colors.amber;
                  statusText = 'Probing...';
                } else if (ep.isOnline) {
                  statusColor = ep.latencyMs < 350 ? Colors.greenAccent : Colors.orangeAccent;
                  statusText = '${ep.latencyMs} ms';
                } else if (ep.errorMessage != null) {
                  statusColor = Colors.redAccent;
                  statusText = ep.errorMessage!;
                }

                return Padding(
                  padding: const EdgeInsets.only(bottom: 12.0),
                  child: GlassCard(
                    preset: ep.isOnline ? GlassPreset.dark : GlassPreset.subtle,
                    hasGlow: ep.isOnline,
                    glowColor: statusColor,
                    padding: const EdgeInsets.all(18),
                    child: Row(
                      children: [
                        Container(
                          width: 12,
                          height: 12,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: statusColor,
                            boxShadow: [
                              BoxShadow(color: statusColor.withOpacity(0.5), blurRadius: 8, spreadRadius: 2),
                            ],
                          ),
                        ),
                        const SizedBox(width: 16),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                ep.name,
                                style: const TextStyle(
                                  color: LofiQColors.textPrimary,
                                  fontWeight: FontWeight.bold,
                                  fontSize: 16,
                                ),
                              ),
                              const SizedBox(height: 4),
                              Text(
                                '${ep.service} • ${ep.url}',
                                style: const TextStyle(color: LofiQColors.textSecondary, fontSize: 12),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                              ),
                            ],
                          ),
                        ),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                          decoration: BoxDecoration(
                            color: statusColor.withOpacity(0.15),
                            borderRadius: BorderRadius.circular(8),
                            border: Border.all(color: statusColor.withOpacity(0.3)),
                          ),
                          child: Text(
                            statusText,
                            style: TextStyle(
                              color: statusColor,
                              fontWeight: FontWeight.bold,
                              fontSize: 13,
                            ),
                          ),
                        ),
                        const SizedBox(width: 12),
                        GlassIconButton(
                          tooltip: 'Probe this endpoint',
                          icon: Icons.refresh_rounded,
                          iconSize: 20,
                          iconColor: LofiQColors.textSecondary,
                          onPressed: ep.isTesting ? null : () => _testEndpoint(ep),
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
          ],
        ),
      ),
    );
  }
}


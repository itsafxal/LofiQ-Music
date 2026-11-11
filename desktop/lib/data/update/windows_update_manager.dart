import 'dart:convert';
import 'package:http/http.dart' as http;
import '../../core/utils/logger.dart';

class WindowsUpdateInfo {
  final int versionCode;
  final String versionName;
  final String releaseDate;
  final String apkUrl;
  final String apkSize;
  final List<String> releaseNotes;
  final bool hasUpdate;
  final String checksum;
  final String source;

  const WindowsUpdateInfo({
    required this.versionCode,
    required this.versionName,
    required this.releaseDate,
    required this.apkUrl,
    required this.apkSize,
    required this.releaseNotes,
    required this.hasUpdate,
    required this.checksum,
    this.source = 'Vercel Primary',
  });
}

typedef UpdateInfo = WindowsUpdateInfo;

class WindowsUpdateManager {
  static final WindowsUpdateManager instance = WindowsUpdateManager._();
  factory WindowsUpdateManager() => instance;

  WindowsUpdateManager._();

  static const String _primaryUrl = 'https://lofiq-web.vercel.app/version.json';
  static const String _secondaryUrl = 'https://mybackuplofiq.netlify.app/version.json';
  static const int currentVersionCode = 65;
  static const String currentVersionName = '1.0.2';

  Future<WindowsUpdateInfo?> checkForUpdates() async {
    // 1. Try Primary (Vercel)
    try {
      final info = await _fetchFromUrl(_primaryUrl, 'Vercel Primary');
      if (info != null) return info;
    } catch (e) {
      LofiQLogger.w(LogCategory.app, 'Primary update check failed (Vercel): $e');
    }

    // 2. Failover to Secondary (Netlify)
    try {
      final info = await _fetchFromUrl(_secondaryUrl, 'Netlify Backup');
      if (info != null) return info;
    } catch (e) {
      LofiQLogger.w(LogCategory.app, 'Secondary update check failed (Netlify): $e');
    }

    return null;
  }

  Future<WindowsUpdateInfo?> checkForUpdate() => checkForUpdates();

  Future<WindowsUpdateInfo?> _fetchFromUrl(String url, String sourceName) async {
    final res = await http.get(Uri.parse(url)).timeout(const Duration(seconds: 5));
    if (res.statusCode != 200) return null;

    final data = jsonDecode(res.body) as Map<String, dynamic>;
    final vCode = (data['versionCode'] as num?)?.toInt() ?? 0;
    final vName = data['versionName']?.toString() ?? '';
    final rDate = data['releaseDate']?.toString() ?? '';
    final apk = data['apkUrl']?.toString() ?? '';
    final size = data['apkSize']?.toString() ?? '';
    final notes = (data['releaseNotes'] as List<dynamic>?)?.map((e) => e.toString()).toList() ?? [];
    final sha = data['checksumSha256']?.toString() ?? '';

    final hasUpdate = vCode > currentVersionCode;

    return WindowsUpdateInfo(
      versionCode: vCode,
      versionName: vName,
      releaseDate: rDate,
      apkUrl: apk,
      apkSize: size,
      releaseNotes: notes,
      hasUpdate: hasUpdate,
      checksum: sha,
      source: sourceName,
    );
  }
}

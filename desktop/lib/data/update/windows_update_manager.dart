import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;
import '../../core/utils/logger.dart';

class WindowsUpdateInfo {
  final int versionCode;
  final String versionName;
  final String releaseDate;
  final String installerUrl;
  final String portableUrl;
  final String installerSize;
  final String portableSize;
  final List<String> releaseNotes;
  final bool hasUpdate;
  final String checksum;
  final String source;

  // Backward compatibility aliases
  String get apkUrl => installerUrl;
  String get apkSize => installerSize;

  const WindowsUpdateInfo({
    required this.versionCode,
    required this.versionName,
    required this.releaseDate,
    required this.installerUrl,
    required this.portableUrl,
    required this.installerSize,
    required this.portableSize,
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
  static const int currentVersionCode = 68;
  static const String currentVersionName = '1.0.5';

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

    // Target windows-specific release block first
    final Map<String, dynamic> winData = data['windows'] is Map<String, dynamic>
        ? data['windows'] as Map<String, dynamic>
        : data;

    final vCode = (winData['versionCode'] as num?)?.toInt() ?? 0;
    final vName = winData['versionName']?.toString() ?? '';
    final rDate = winData['releaseDate']?.toString() ?? '';

    final baseUrl = url.replaceAll('/version.json', '');
    String rawInstaller = winData['installerUrl']?.toString() ?? winData['apkUrl']?.toString() ?? '';
    if (rawInstaller.startsWith('/')) {
      rawInstaller = '$baseUrl$rawInstaller';
    }

    String rawPortable = winData['portableUrl']?.toString() ?? '';
    if (rawPortable.startsWith('/')) {
      rawPortable = '$baseUrl$rawPortable';
    }

    final installerSize = winData['installerSize']?.toString() ?? winData['apkSize']?.toString() ?? '';
    final portableSize = winData['portableSize']?.toString() ?? '';
    final notes = (winData['releaseNotes'] as List<dynamic>?)?.map((e) => e.toString()).toList() ?? [];
    final sha = winData['installerChecksum']?.toString() ?? winData['checksum']?.toString() ?? '';

    final hasUpdate = vCode > currentVersionCode;

    return WindowsUpdateInfo(
      versionCode: vCode,
      versionName: vName,
      releaseDate: rDate,
      installerUrl: rawInstaller,
      portableUrl: rawPortable,
      installerSize: installerSize,
      portableSize: portableSize,
      releaseNotes: notes,
      hasUpdate: hasUpdate,
      checksum: sha,
      source: sourceName,
    );
  }

  /// Launch the installer download or trigger direct update execution
  Future<bool> launchUpdate(String url) async {
    if (url.isEmpty) return false;
    try {
      if (Platform.isWindows) {
        await Process.start('explorer.exe', [url]);
        return true;
      }
    } catch (e) {
      LofiQLogger.e(LogCategory.app, 'Failed to launch update URL: $e');
    }
    return false;
  }
}

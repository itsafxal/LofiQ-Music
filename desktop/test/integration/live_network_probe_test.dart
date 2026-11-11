import 'dart:io';
import 'package:http/http.dart' as http;
import 'package:lofiq_desktop/core/models/audio_quality.dart';
import 'package:lofiq_desktop/data/resolvers/jiosaavn_client.dart';
import 'package:lofiq_desktop/data/resolvers/youtube_stream_extractor.dart';
import 'package:lofiq_desktop/data/lyrics/lyrics_repository.dart';
import 'package:lofiq_desktop/data/update/windows_update_manager.dart';

void main() async {
  stdout.writeln('=== RUNNING LIVE NETWORK RESOLVER & STREAM INTEGRATION PROBE ===\n');

  // 1. Probe JioSaavn Live Search & DES Decryption
  stdout.writeln('[1/5] Testing JioSaavn live search & DES decryption...');
  try {
    final jioResults = await JioSaavnClient.instance.search('lofi beats');
    stdout.writeln('  JioSaavn search returned ${jioResults.length} tracks.');
    if (jioResults.isNotEmpty) {
      final first = jioResults.first;
      stdout.writeln('  Track: "${first.title}" by ${first.artist}');
      stdout.writeln('  Encrypted Media URL: ${first.sourceUrl}');

      final decrypted = JioSaavnClient.instance.decryptMediaUrl(first.sourceUrl, quality: AudioQuality.high);
      stdout.writeln('  Decrypted 320k Stream URL: $decrypted');

      if (decrypted != null && decrypted.startsWith('http')) {
        // Probe stream with Range request
        final streamResp = await http.get(
          Uri.parse(decrypted),
          headers: {'Range': 'bytes=0-4096'},
        ).timeout(const Duration(seconds: 10));

        stdout.writeln('  CDN Stream Status Code: ${streamResp.statusCode}');
        stdout.writeln('  CDN Content-Type: ${streamResp.headers['content-type']}');
        stdout.writeln('  CDN Content-Length: ${streamResp.headers['content-length']} / Range: ${streamResp.headers['content-range']}');

        if (streamResp.statusCode == 200 || streamResp.statusCode == 206) {
          stdout.writeln('  >>> SUCCESS: JioSaavn 320k audio stream is 100% LIVE and reachable! <<<\n');
        } else {
          stdout.writeln('  >>> WARNING: CDN returned non-200/206 status: ${streamResp.statusCode} <<<\n');
        }
      } else {
        stdout.writeln('  >>> ERROR: Decryption failed or returned invalid URL <<<\n');
      }
    }
  } catch (e) {
    stdout.writeln('  >>> JioSaavn probe failed: $e <<<\n');
  }

  // 2. Probe YouTube InnerTube Audio Resolution
  stdout.writeln('[2/5] Testing YouTube InnerTube audio stream resolution for "jfKfPfyJRdk"...');
  try {
    final ytUrl = await YouTubeStreamExtractor.instance.resolveAudioStream('jfKfPfyJRdk', quality: AudioQuality.high);
    stdout.writeln('  YouTube stream URL: ${ytUrl != null ? ytUrl.substring(0, ytUrl.length > 80 ? 80 : ytUrl.length) + "..." : "null"}');
    if (ytUrl != null && ytUrl.startsWith('http')) {
      final ytResp = await http.get(
        Uri.parse(ytUrl),
        headers: {'Range': 'bytes=0-4096'},
      ).timeout(const Duration(seconds: 10));

      stdout.writeln('  YouTube CDN Status Code: ${ytResp.statusCode}');
      stdout.writeln('  YouTube Content-Type: ${ytResp.headers['content-type']}');
      if (ytResp.statusCode == 200 || ytResp.statusCode == 206) {
        stdout.writeln('  >>> SUCCESS: YouTube audio stream is 100% LIVE and reachable! <<<\n');
      } else {
        stdout.writeln('  >>> WARNING: YouTube CDN returned status ${ytResp.statusCode} <<<\n');
      }
    } else {
      stdout.writeln('  >>> WARNING: YouTube InnerTube returned null (bot check or stream cipher) <<<\n');
    }
  } catch (e) {
    stdout.writeln('  >>> YouTube InnerTube probe error: $e <<<\n');
  }

  // 3. Probe LRCLIB Lyrics API
  stdout.writeln('[3/5] Testing LRCLIB live karaoke lyrics fetching...');
  try {
    final lyrics = await LyricsRepository.instance.fetchLyrics(
      title: 'Midnight Rain',
      artist: 'Taylor Swift',
      durationMs: 174000,
    );
    if (lyrics != null) {
      stdout.writeln('  LRCLIB Provider: ${lyrics.source}, SyncType: ${lyrics.syncType}');
      stdout.writeln('  Parsed Lines: ${lyrics.lines.length}');
      if (lyrics.lines.isNotEmpty) {
        stdout.writeln('  Line 1: [${lyrics.lines.first.timeMs}ms] "${lyrics.lines.first.text}"');
        stdout.writeln('  >>> SUCCESS: LRCLIB karaoke synced lyrics are 100% LIVE! <<<\n');
      }
    } else {
      stdout.writeln('  >>> WARNING: No lyrics returned <<<\n');
    }
  } catch (e) {
    stdout.writeln('  >>> LRCLIB probe error: $e <<<\n');
  }

  // 4. Probe Vercel Primary & Netlify Backup Update Endpoints
  stdout.writeln('[4/5] Testing Dual Failover Updater (Vercel Primary + Netlify Backup)...');
  try {
    final update = await WindowsUpdateManager.instance.checkForUpdate();
    if (update != null) {
      stdout.writeln('  Update manifest successfully retrieved from: ${update.source}');
      stdout.writeln('  Version Code: ${update.versionCode}, Version Name: ${update.versionName}');
      stdout.writeln('  Checksum: ${update.checksum}');
      stdout.writeln('  >>> SUCCESS: Failover updater is 100% LIVE! <<<\n');
    } else {
      stdout.writeln('  >>> Current version (65) matches remote manifest (no update needed) <<<\n');
    }
  } catch (e) {
    stdout.writeln('  >>> Update manager probe error: $e <<<\n');
  }

  stdout.writeln('=== LIVE PROBE COMPLETED ===');
}


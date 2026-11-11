import 'package:http/http.dart' as http;
import '../../core/utils/logger.dart';

abstract class TransformOp {
  List<String> apply(List<String> chars);
}

class ReverseOp implements TransformOp {
  @override
  List<String> apply(List<String> chars) => chars.reversed.toList();
}

class SliceOp implements TransformOp {
  final int count;
  SliceOp(this.count);

  @override
  List<String> apply(List<String> chars) {
    if (count > 0 && count < chars.length) {
      return chars.sublist(count);
    }
    return chars;
  }
}

class SwapOp implements TransformOp {
  final int index;
  SwapOp(this.index);

  @override
  List<String> apply(List<String> chars) {
    if (chars.isNotEmpty && index >= 0 && index < chars.length) {
      final temp = chars[0];
      chars[0] = chars[index];
      chars[index] = temp;
    }
    return chars;
  }
}

class YouTubeCipherDecipherer {
  static final YouTubeCipherDecipherer instance = YouTubeCipherDecipherer._();
  YouTubeCipherDecipherer._();

  List<TransformOp>? _cachedTransforms;
  DateTime? _lastFetchTime;

  Future<String?> decipher(String signatureCipher) async {
    try {
      final params = Uri.splitQueryString(signatureCipher);
      final url = params['url'];
      if (url == null || url.isEmpty) return null;

      final s = params['s'];
      final sp = params['sp'] ?? 'sig';

      if (s == null || s.isEmpty) {
        return url;
      }

      final transforms = await _getOrFetchTransforms();
      if (transforms == null || transforms.isEmpty) {
        LofiQLogger.w(LogCategory.resolver, 'Player JS transforms unavailable for deciphering');
        return null;
      }

      var chars = s.split('');
      for (final op in transforms) {
        chars = op.apply(chars);
      }
      final decSig = chars.join('');

      final delimiter = url.contains('?') ? '&' : '?';
      return '$url$delimiter$sp=$decSig';
    } catch (e) {
      LofiQLogger.w(LogCategory.resolver, 'Decipher signature failed: $e');
      return null;
    }
  }

  Future<List<TransformOp>?> _getOrFetchTransforms() async {
    if (_cachedTransforms != null &&
        _lastFetchTime != null &&
        DateTime.now().difference(_lastFetchTime!).inHours < 12) {
      return _cachedTransforms;
    }

    try {
      final playerJsUrl = await _fetchPlayerJsUrl();
      if (playerJsUrl == null) return null;

      final res = await http.get(Uri.parse(playerJsUrl)).timeout(const Duration(seconds: 5));
      if (res.statusCode != 200) return null;

      final body = res.body;
      final transforms = _parsePlayerJsTransforms(body);
      if (transforms.isNotEmpty) {
        _cachedTransforms = transforms;
        _lastFetchTime = DateTime.now();
        LofiQLogger.i(LogCategory.resolver, 'Extracted ${transforms.length} player JS signature transforms');
        return transforms;
      }
    } catch (e) {
      LofiQLogger.w(LogCategory.resolver, 'Failed to fetch/parse player JS: $e');
    }
    return null;
  }

  Future<String?> _fetchPlayerJsUrl() async {
    try {
      final res = await http.get(
        Uri.parse('https://www.youtube.com/iframe_api'),
        headers: {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'},
      ).timeout(const Duration(seconds: 4));

      if (res.statusCode == 200) {
        final match = RegExp(r'player\\\/([a-zA-Z0-9_\-]+)\\\/player_ias\.vflset\\\/[a-zA-Z0-9_\-]+\\\/base\.js')
            .firstMatch(res.body);
        if (match != null) {
          final clean = match.group(0)!.replaceAll(r'\/', '/');
          return 'https://www.youtube.com/$clean';
        }
      }
    } catch (_) {}
    return 'https://www.youtube.com/s/player/22c07386/player_ias.vflset/en_US/base.js';
  }

  List<TransformOp> _parsePlayerJsTransforms(String js) {
    final ops = <TransformOp>[];
    try {
      // Find main decipher function
      final fnMatch = RegExp(r'\b[a-zA-Z0-9$_]+\s*=\s*function\([a-zA-Z0-9$_]+\)\{([a-zA-Z0-9$_]+=[a-zA-Z0-9$_]+\.split\(""\);.+?\.join\(""\))\};')
          .firstMatch(js);
      if (fnMatch == null) return ops;

      final fnBody = fnMatch.group(1)!;
      final lines = fnBody.split(';');

      // Find the helper object name
      final objMatch = RegExp(r'([a-zA-Z0-9$_]+)\.([a-zA-Z0-9$_]+)\(').firstMatch(fnBody);
      if (objMatch == null) return ops;
      final objName = objMatch.group(1)!;

      // Extract helper object methods
      final objDefMatch = RegExp('${RegExp.escape(objName)}=\\{([\\s\\S]+?)\\};').firstMatch(js);
      final methodMap = <String, String>{};
      if (objDefMatch != null) {
        final methods = objDefMatch.group(1)!.split('},');
        for (final m in methods) {
          final nameMatch = RegExp(r'([a-zA-Z0-9$_]+):function').firstMatch(m);
          if (nameMatch == null) continue;
          final methodName = nameMatch.group(1)!;
          if (m.contains('reverse')) {
            methodMap[methodName] = 'reverse';
          } else if (m.contains('splice') || m.contains('slice')) {
            methodMap[methodName] = 'slice';
          } else {
            methodMap[methodName] = 'swap';
          }
        }
      }

      for (final line in lines) {
        final callMatch = RegExp('${RegExp.escape(objName)}\\.([a-zA-Z0-9\$_]+)\\([^,]+,(\\d+)\\)').firstMatch(line);
        if (callMatch != null) {
          final method = callMatch.group(1)!;
          final arg = int.parse(callMatch.group(2)!);
          final opType = methodMap[method] ?? 'swap';

          if (opType == 'reverse') {
            ops.add(ReverseOp());
          } else if (opType == 'slice') {
            ops.add(SliceOp(arg));
          } else {
            ops.add(SwapOp(arg));
          }
        }
      }
    } catch (e) {
      LofiQLogger.w(LogCategory.resolver, 'Error in regex decipher parse: $e');
    }
    return ops;
  }
}

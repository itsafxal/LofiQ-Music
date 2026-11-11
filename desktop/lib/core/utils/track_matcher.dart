import 'dart:math' as math;

class CandidateTrack {
  final String providerId;
  final String providerTrackId;
  final String title;
  final String artist;
  final String album;
  final int durationSeconds;
  final String channelName;
  final String? encryptedMediaUrl;

  const CandidateTrack({
    required this.providerId,
    required this.providerTrackId,
    required this.title,
    required this.artist,
    this.album = '',
    this.durationSeconds = 0,
    this.channelName = '',
    this.encryptedMediaUrl,
  });
}

class MatchEvaluation {
  final bool isAccepted;
  final double matchScore;
  final String reason;
  final double titleScore;
  final double artistScore;
  final int durationDiffSec;

  const MatchEvaluation({
    required this.isAccepted,
    required this.matchScore,
    required this.reason,
    required this.titleScore,
    required this.artistScore,
    required this.durationDiffSec,
  });
}

class TrackIdentityMatcher {
  static const Set<String> _trustedChannels = {
    // Pan-Indian & Bollywood Labels
    't-series', 'tseries', 't series', 'zee music company', 'zee music',
    'sony music india', 'sony music', 'sony music entertainment',
    'yrf', 'yash raj films', 'saregama', 'saregama music', 'saregama india',
    'tips official', 'tips music', 'tips', 'eros now', 'speed records',
    'times music', 'junglee music', 'desi music factory',
    // South Indian Labels
    'saina', 'saina music', 'muzik247', 'satyam audios', 'manorama music',
    'think music', 'aditya music', 'lahari music', 'divo music',
    // International Labels
    'vevo', 'universal music group', 'umg', 'warner music group', 'atlantic records',
    'columbia records', 'interscope records', 'republic records', 'rca records',
    'def jam recordings', 'spinnin\' records', 'monstercat',
    // Streaming Providers
    'jiosaavn', 'saavn'
  };

  static int levenshteinDistance(String s1, String s2) => _levenshteinDistance(s1, s2);
  static String normalize(String s) => sanitize(s);
  static double similarityScore(String s1, String s2) => stringSimilarity(s1, s2);
  static bool isDurationMatch(int d1, int d2, {int maxDeltaSeconds = 10}) => (d1 - d2).abs() <= maxDeltaSeconds;

  static String sanitize(String input) {
    var s = input.toLowerCase();
    // Remove typical YouTube brackets and tags
    s = s.replaceAll(RegExp(r'\((official|video|audio|lyric|lyrics|hd|4k|remastered|extended)[^\)]*\)'), '');
    s = s.replaceAll(RegExp(r'\[(official|video|audio|lyric|lyrics|hd|4k|remastered|extended)[^\]]*\]'), '');
    s = s.replaceAll(RegExp(r'[^a-z0-9\s]'), ' ');
    return s.replaceAll(RegExp(r'\s+'), ' ').trim();
  }

  static double stringSimilarity(String s1, String s2) {
    final a = sanitize(s1);
    final b = sanitize(s2);
    if (a.isEmpty && b.isEmpty) return 1.0;
    if (a.isEmpty || b.isEmpty) return 0.0;
    if (a == b) return 1.0;
    if (a.contains(b) || b.contains(a)) return 0.85;

    final dist = _levenshteinDistance(a, b);
    final maxLen = math.max(a.length, b.length);
    if (maxLen == 0) return 1.0;
    return (1.0 - (dist / maxLen)).clamp(0.0, 1.0);
  }

  static int _levenshteinDistance(String s1, String s2) {
    final m = s1.length;
    final n = s2.length;
    List<int> v0 = List<int>.generate(n + 1, (i) => i);
    List<int> v1 = List<int>.filled(n + 1, 0);

    for (int i = 0; i < m; i++) {
      v1[0] = i + 1;
      for (int j = 0; j < n; j++) {
        final cost = (s1[i] == s2[j]) ? 0 : 1;
        v1[j + 1] = math.min(
          v1[j] + 1,
          math.min(v0[j + 1] + 1, v0[j] + cost),
        );
      }
      for (int j = 0; j <= n; j++) {
        v0[j] = v1[j];
      }
    }
    return v0[n];
  }

  static MatchEvaluation evaluate({
    required String queryTitle,
    required String queryArtist,
    int expectedDurationSec = 0,
    required CandidateTrack candidate,
  }) {
    final titleScore = stringSimilarity(queryTitle, candidate.title);
    final artistScore = stringSimilarity(queryArtist, candidate.artist);
    
    final candidateChannel = candidate.channelName.toLowerCase().trim();
    final isTrustedChannel = _trustedChannels.any((tc) => candidateChannel.contains(tc));
    
    int durationDiff = 0;
    if (expectedDurationSec > 0 && candidate.durationSeconds > 0) {
      durationDiff = (expectedDurationSec - candidate.durationSeconds).abs();
    }

    double totalScore = (titleScore * 0.6) + (artistScore * 0.4);
    if (isTrustedChannel) {
      totalScore = math.min(1.0, totalScore + 0.15);
    }

    if (durationDiff > 45) {
      totalScore *= 0.7; // penalize mismatched duration / music video intro skits
    }

    final isAccepted = totalScore >= 0.65;
    final reason = isAccepted
        ? 'Matched with ${(totalScore * 100).toStringAsFixed(1)}% confidence'
        : 'Score ${(totalScore * 100).toStringAsFixed(1)}% below acceptance threshold';

    return MatchEvaluation(
      isAccepted: isAccepted,
      matchScore: totalScore,
      reason: reason,
      titleScore: titleScore,
      artistScore: artistScore,
      durationDiffSec: durationDiff,
    );
  }
}

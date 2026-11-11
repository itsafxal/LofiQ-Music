import 'package:flutter_test/flutter_test.dart';
import 'package:lofiq_desktop/core/utils/track_matcher.dart';

void main() {
  group('TrackIdentityMatcher Verification', () {
    test('Calculates accurate Levenshtein distance', () {
      expect(TrackIdentityMatcher.levenshteinDistance('kitten', 'sitting'), 3);
      expect(TrackIdentityMatcher.levenshteinDistance('lofi', 'lofi'), 0);
      expect(TrackIdentityMatcher.levenshteinDistance('', 'test'), 4);
    });

    test('Computes string similarity score between 0.0 and 1.0', () {
      expect(TrackIdentityMatcher.similarityScore('midnight rain', 'midnight rain'), 1.0);
      expect(TrackIdentityMatcher.similarityScore('coffee shop', 'tea shop'), greaterThan(0.5));
      expect(TrackIdentityMatcher.similarityScore('apple', 'zebra'), lessThan(0.3));
    });

    test('Normalizes strings by stripping punctuation, extra spaces, and common fluff', () {
      final normalized = TrackIdentityMatcher.normalize('Lofi Girl - Coffee (Official Audio) [HQ]');
      expect(normalized.toLowerCase(), contains('coffee'));
      expect(normalized.contains('['), isFalse);
    });

    test('Matches tracks within tolerance duration window', () {
      // 180s target track
      expect(TrackIdentityMatcher.isDurationMatch(180, 185, maxDeltaSeconds: 10), isTrue);
      expect(TrackIdentityMatcher.isDurationMatch(180, 172, maxDeltaSeconds: 10), isTrue);
      expect(TrackIdentityMatcher.isDurationMatch(180, 220, maxDeltaSeconds: 10), isFalse);
    });
  });
}


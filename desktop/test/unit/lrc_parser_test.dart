import 'package:flutter_test/flutter_test.dart';
import 'package:lofiq_desktop/data/lyrics/lrc_parser.dart';

void main() {
  group('LrcParser Unit Tests', () {
    test('Parses standard centisecond LRC timestamps', () {
      const lrc = '''
[ti:Midnight Rain]
[ar:Aruarian]
[00:04.50]Looking out the window
[00:12.80]Gentle drops fall on the street
[00:25.00]Analog warmth in the background
''';

      final lines = LrcParser.parse(lrc);
      expect(lines.length, 3);
      expect(lines[0].text, 'Looking out the window');
      expect(lines[0].timeMs, 4500);

      expect(lines[1].text, 'Gentle drops fall on the street');
      expect(lines[1].timeMs, 12800);

      expect(lines[2].text, 'Analog warmth in the background');
      expect(lines[2].timeMs, 25000);
    });

    test('Parses 3-digit millisecond timestamps', () {
      const lrc = '''
[01:15.350]Synthesizer fades into memory
[01:22.025]Late night city hums
''';

      final lines = LrcParser.parse(lrc);
      expect(lines.length, 2);
      expect(lines[0].timeMs, 75350);
      expect(lines[0].text, 'Synthesizer fades into memory');
      expect(lines[1].timeMs, 82025);
    });

    test('Sorts lines chronologically regardless of source line order', () {
      const lrc = '''
[00:30.00]Third line
[00:10.00]First line
[00:20.00]Second line
''';

      final lines = LrcParser.parse(lrc);
      expect(lines.length, 3);
      expect(lines[0].text, 'First line');
      expect(lines[1].text, 'Second line');
      expect(lines[2].text, 'Third line');
    });

    test('Ignores metadata headers and empty lines', () {
      const lrc = '''
[offset:0]
[length: 03:45]
[by: LofiQ Scraper]

[00:05.00]First real lyric
''';

      final lines = LrcParser.parse(lrc);
      expect(lines.length, 1);
      expect(lines[0].text, 'First real lyric');
    });

    test('Returns empty list for empty or non-LRC input', () {
      expect(LrcParser.parse(''), isEmpty);
      expect(LrcParser.parse('Just plain lyrics with no timestamps'), isEmpty);
    });
  });
}


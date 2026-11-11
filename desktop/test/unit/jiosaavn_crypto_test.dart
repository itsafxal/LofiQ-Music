import 'dart:convert';
import 'dart:typed_data';
import 'package:flutter_test/flutter_test.dart';
import 'package:pointycastle/api.dart';
import 'package:pointycastle/block/desede_engine.dart';
import 'package:pointycastle/block/modes/ecb.dart';
import 'package:pointycastle/padded_block_cipher/padded_block_cipher_impl.dart';
import 'package:pointycastle/paddings/pkcs7.dart';
import 'package:lofiq_desktop/core/models/audio_quality.dart';
import 'package:lofiq_desktop/data/resolvers/jiosaavn_client.dart';

void main() {
  group('JioSaavn Bitrate & DES Decryption Tests', () {
    final client = JioSaavnClient.instance;

    test('Bitrate adjuster converts suffix to target quality', () {
      const originalUrl = 'https://aac.saavncdn.com/999/chill_sample_96.mp4';

      final high320 = client.adjustBitrate(originalUrl, AudioQuality.high);
      expect(high320, 'https://aac.saavncdn.com/999/chill_sample_320.mp4');

      final standard160 = client.adjustBitrate(originalUrl, AudioQuality.standard);
      expect(standard160, 'https://aac.saavncdn.com/999/chill_sample_160.mp4');

      final dataSaver96 = client.adjustBitrate(originalUrl, AudioQuality.dataSaver);
      expect(dataSaver96, 'https://aac.saavncdn.com/999/chill_sample_96.mp4');
    });

    test('DES Decryption round-trip decrypts media URL successfully', () {
      const plainMediaUrl = 'https://aac.saavncdn.com/456/lofi_beats_96.mp4';
      const desKey = '38346591';
      final tripleKey = '$desKey$desKey$desKey';

      // 1. Encrypt with DES EDE / ECB
      final keyBytes = Uint8List.fromList(utf8.encode(tripleKey));
      final encryptCipher = PaddedBlockCipherImpl(
        PKCS7Padding(),
        ECBBlockCipher(DESedeEngine()),
      );
      encryptCipher.init(true, PaddedBlockCipherParameters(KeyParameter(keyBytes), null));

      final plainBytes = Uint8List.fromList(utf8.encode(plainMediaUrl));
      final encryptedBytes = encryptCipher.process(plainBytes);
      final encryptedBase64 = base64Encode(encryptedBytes);

      // 2. Pass encrypted token to JioSaavnClient.decryptMediaUrl
      final decryptedResult = client.decryptMediaUrl(encryptedBase64, quality: AudioQuality.high);

      expect(decryptedResult, isNotNull);
      expect(decryptedResult, 'https://aac.saavncdn.com/456/lofi_beats_320.mp4');
    });

    test('Invalid or corrupted cipher strings safely return null without throwing', () {
      expect(client.decryptMediaUrl(''), isNull);
      expect(client.decryptMediaUrl('not-base64-random-string'), isNull);
      expect(client.decryptMediaUrl('SGVsbG8gV29ybGQ='), isNull); // Valid base64, but not encrypted URL
    });
  });
}


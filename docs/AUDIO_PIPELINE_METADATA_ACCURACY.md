# Audio Pipeline Metadata Accuracy & Truthfulness Policy

## 1. Zero-Fabrication Invariant
LofiQ's Audio Pipeline Inspector adheres to an absolute truthfulness standard:
- **No Fabricated Bitrates**: If the stream manifest or container does not report an explicit average bitrate (`Format.bitrate == Format.NO_VALUE`), the pipeline displays `Unknown (VBR)` rather than estimating or claiming an arbitrary 320 kbps.
- **No False Lossless Claims**: A stream is only marked `Lossless` if its codec is verified as `audio/flac`, `audio/alac`, or raw `audio/raw`. Standard lossy AAC (`mp4a-latm`), MP3, and Opus streams are explicitly labeled `Lossy Compression`.
- **Accurate Resampling Truth**: Because the Android AudioTrack mixer operates at a fixed device frequency (typically 48,000 Hz on modern Qualcomm and MediaTek hardware), a 44,100 Hz AAC stream will be resampled by Android's audio HAL. LofiQ reports this truthfully as `44.1 kHz → 48.0 kHz (Android AudioTrack Mixer)` rather than claiming "Bit-Perfect Passthrough".

---

## 2. Technical Derivations

### Codec & Container Detection
| MIME Type | Display Codec | Container | Default Lossless |
| :--- | :--- | :--- | :--- |
| `audio/mp4a-latm` | AAC-LC / HE-AAC | M4A / MP4 | No |
| `audio/opus` | Opus | WebM / OGG | No |
| `audio/flac` | FLAC | Native FLAC | Yes |
| `audio/mpeg` | MP3 | MP3 Audio | No |
| `audio/raw` | PCM (Uncompressed) | WAV / Linear | Yes |

### Hardware vs Software Decoder Detection
The decoder classification is determined by querying `MediaCodecInfo.isHardwareAccelerated` (API 29+) with legacy naming heuristics as fallback:
- Hardware decoders: Prefix `c2.qti.`, `OMX.qcom.`, `OMX.Exynos.`, `OMX.MTK.`, `c2.mtk.`, `c2.exynos.`.
- Software fallback decoders: Prefix `c2.android.`, `OMX.google.`, `OMX.SEC.`.

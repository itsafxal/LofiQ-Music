# Audio Pipeline Architecture & BitChord Implementation

## 1. Executive Summary & Design Inspiration
The LofiQ Live Audio Pipeline Inspector converts the static provider quality badge (`HQ`, `Hi-Fi`, `Pulse`, `Offline`) into an interactive, high-precision audio diagnostic modal. 

Inspired by the open-source **BitChord** project (`kushagrasinghx/BitChord`), this feature provides transparent, real-time visualization of the audio signal chain from network source to hardware speaker/DAC.

### Licensing & Clean-Room Architecture
- **Upstream Reference**: BitChord is licensed under GPL-3.0.
- **Implementation Strategy**: A clean-room, independently architected Jetpack Compose implementation built exclusively on LofiQ's existing Liquid Glass design system, Media3 ExoPlayer listeners, and Kotlin Coroutines. No upstream code was copied or imported, ensuring complete intellectual property hygiene.

---

## 2. Five-Stage Audio Signal Chain

```
[ Track Info ]
   │
   ▼
[ Decoder Stage ] (MediaCodec hardware / software)
   │
   ▼
[ Resampler Stage ] (Stream sample rate vs AudioTrack mixer rate)
   │
   ▼
[ DSP & Effects ] (Equalizer, Bass Boost, Virtualizer, Speed)
   │
   ▼
[ Output Stage ] (Hardware device, routing, sample rate)
```

### Stage 1: Track Info (`TrackInfoStage`)
- **Metadata Source**: Extracted from active `MediaItem` and Media3 `Tracks.Group` formats on `onTracksChanged`.
- **Metrics**: Source Provider (`JioSaavn Official Stream`, `YouTube Music CDN`, `Pulse Direct HTTP`, `Local On-Device Audio`), Audio Codec (`AAC`, `Opus`, `FLAC`, `MP3`), Container Format (`M4A`, `WebM`, `FLAC`, `MP3`), Average Bitrate (e.g. `320 kbps`, `128 kbps`, or `Unknown (VBR)`), Sample Rate (`44.1 kHz`, `48.0 kHz`, `96.0 kHz`), Channels (`Stereo 2.0`, `Mono 1.0`, `Surround 5.1`), and Lossless status.

### Stage 2: Decoder Stage (`DecoderStage`)
- **Metadata Source**: Captured live from `AnalyticsListener.onAudioDecoderInitialized` and confirmed via `onAudioInputFormatChanged`.
- **Fallback Resolution**: When querying already-active streams where the initialization event previously fired, the stage inspects the active format and queries `MediaCodecList` for the primary decoder component.
- **Metrics**: Decoder Name (e.g., `c2.android.aac.decoder`, `OMX.qcom.audio.decoder.aac`), Decoder Classification (`Hardware Accelerated` vs `Software Core`), Input MIME Type (`audio/mp4a-latm`, `audio/opus`), and Decoded PCM Format (`PCM 16-bit Little-Endian`).

### Stage 3: Resampler Stage (`ResamplerStage`)
- **Metadata Source**: Compares the decoded stream sample rate with the native hardware mixer sample rate queried from `AudioManager.getProperty(PROPERTY_OUTPUT_SAMPLE_RATE)` or `AudioDeviceInfo`.
- **Metrics**: 
  - `isResampling = (inputRate != outputRate)`
  - Active Engine: `Android AudioTrack Mixer` or `Direct Bit-Perfect Pass-through`.
  - Conversion Path: e.g. `44.1 kHz → 48.0 kHz (Resampled)` or `48.0 kHz → 48.0 kHz (Bit-Perfect)`.

### Stage 4: DSP & Audio Effects Stage (`DspStage`)
- **Metadata Source**: Direct inspection of `AudioEffectsController` and ExoPlayer `PlaybackParameters`.
- **Metrics**: Equalizer State (`Active` + Preset name e.g. `Flat`, `Bass Booster`, `Acoustic`), 10-band ISO frequencies, Bass Boost (`Active` + strength or `Bypassed`), Virtualizer 3D (`Active` + strength or `Bypassed`), Playback Speed (`1.0x`), and Pitch Shift (`1.0x`).

### Stage 5: Audio Output Routing (`OutputStage`)
- **Metadata Source**: Integrated with LofiQ's `AudioRoutingManager` observing `AudioManager.registerAudioDeviceCallback`.
- **Metrics**: Device Name (e.g., `Built-in Speaker`, `Sony WH-1000XM4`, `USB Hi-Res DAC`), Output Type (`SPEAKER`, `BLUETOOTH_A2DP`, `WIRED_HEADSET`, `USB_HEADSET`), and Native Sample Rate (`48,000 Hz`).

---

## 3. Dual-Player Handover & Thread Safety

LofiQ's dual-player architecture alternates between two active ExoPlayer slots (`playerSlotA` and `playerSlotB`) for gapless playback and crossfading.

`AudioPipelineMonitor` adheres to the following thread-safety rules:
1. **Dynamic Rebinding (`activePlayerFlow`)**:
   - `AudioPipelineMonitor` collects `playbackEngine.activePlayerFlow`.
   - Upon player promotion, the monitor detaches its `Player.Listener` and `AnalyticsListener` from the outgoing player and attaches them synchronously to the newly promoted player.
2. **Stale Callback Discard Guard**:
   - Outgoing players may trigger callbacks (e.g. stop, clear, reset) while completing fade-out.
   - All monitor callback handlers enforce `if (player !== currentObservedPlayer) return`, preventing old player events from corrupting or clearing new track metadata.
3. **Audio Thread Protection**:
   - ExoPlayer audio threads are never blocked. All calculations, state updates, and lookups occur on `Dispatchers.Main` / `Dispatchers.IO` using Kotlin coroutines and `StateFlow`.

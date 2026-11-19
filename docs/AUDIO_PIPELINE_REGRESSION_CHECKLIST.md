# Audio Pipeline Regression Checklist

Before merging or publishing changes affecting the audio pipeline or playback engine, execute and verify all checks below.

---

## 1. Playback Continuity & Audio Thread Integrity
- [x] Opening or dismissing the Audio Pipeline dialog does not cause any stutter, dropout, or pause in active music playback.
- [x] All metadata extractions in `AudioPipelineMonitor` execute on Main / IO dispatchers and do not block ExoPlayer audio rendering threads.
- [x] Dual-player track transitions (gapless & crossfade) continue smoothly while the Audio Pipeline dialog remains open.

---

## 2. Dynamic Player Handover & Callback Safety
- [x] When track transitions trigger active player handover (`activePlayerFlow` emission), `AudioPipelineMonitor` detaches from the outgoing player and binds to the incoming player.
- [x] Discarded/outgoing player callbacks (e.g. `onTracksChanged`, `onAudioInputFormatChanged`) are rejected by identity check: `if (player !== currentObservedPlayer) return`.
- [x] Switching between different audio sources (JioSaavn 320kbps AAC, YouTube Opus 160kbps, Local FLAC) updates the UI promptly within 1 frame.

---

## 3. Metadata Accuracy & Truthfulness
- [x] Unverified bitrates display `Unknown (VBR)` instead of fabricated values.
- [x] AAC and Opus streams are clearly marked as `Lossy Compression`.
- [x] Resampling detection accurately compares stream input rate against native hardware mixer rate (48,000 Hz).
- [x] Hardware routing changes (e.g., unplugging headphones, connecting Bluetooth) reflect in real time without restarting the stream.

# Playback Transition State Machine & Intent Preservation Invariants

## Overview

In a dual-player architecture (Slot A and Slot B), track transitions occur through either gapless handover or equal-power crossfade. The critical reliability requirement is that automatic transitions, manual track advances, and queue changes must never leave the player in an unintended paused state.

## Core State Machine Invariants

1. **Explicit Playback Intent**:
   - `PlaybackIntent.PLAY`: User explicitly started playback or continuous playback is active.
   - `PlaybackIntent.USER_PAUSED`: User explicitly tapped pause.
   - `PlaybackIntent.USER_STOPPED`: User explicitly closed the mini-player or invoked `stop()`.
   - `PlaybackIntent.AUDIO_FOCUS_LOSS_TRANSIENT`: System audio focus interrupted playback.
   - Playback transitions must preserve `PlaybackIntent.PLAY` unless the user explicitly requested a pause/stop.

2. **Atomic Slot Handover Guard**:
   - The secondary player slot listener incorporates a guard that pauses premature secondary audio leaks.
   - During handover, `isHandoverInProgress` must be set to `true`, and `isSlotAActive` must be flipped **before** unmuting and playing the incoming slot.
   - `newPrimary` must be promoted to the `MediaSession` before cleaning up the outgoing slot so the foreground service status is never dropped.

3. **Incoming Player Audio Readiness**:
   - When active playback reaches `STATE_READY`, if `playbackIntent == PlaybackIntent.PLAY && !slotPlayer.isPlaying`, `slotPlayer.play()` is invoked immediately.
   - `onPlayWhenReadyChanged` detects and corrects any unauthorized transitions to `playWhenReady = false`.

4. **Independent High-Frequency Progress Flow**:
   - `playbackProgress` flow (updated every 200ms) is separated from `playerState`, `currentSongFlow`, and `isPlayingFlow`.
   - Root UI containers observe only low-frequency state flows, eliminating recomposition storms across track changes.

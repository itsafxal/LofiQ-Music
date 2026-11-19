# LofiQ Playback Reliability Invariants

## Core Principles

LofiQ Music utilizes a dual-ExoPlayer architecture (Slot A and Slot B) designed for seamless gapless transitions and equal-power crossfading. To prevent silent playback termination, CPU kernel sleep, and foreground service demotion, every component must uphold the following architectural invariants.

---

### Invariant 1: Continuous AudioFocus Ownership
- **Rule**: AudioFocus is acquired at the application level before audible playback starts and must **never** be abandoned during track transitions, gapless handovers, crossfades, or secondary player preloading.
- **Enforcement**:
  - `audioFocusManager.ensureAudioFocus()` is invoked synchronously in `performPlayerHandoverLocked()` prior to silencing the outgoing player.
  - Secondary slot operates strictly with `setAudioAttributes(mediaAudioAttributes, handleAudioFocus = false)` and `volume = 0f`. It must never compete for or steal audio focus.
  - Abandonment occurs **only** on explicit user action (`stop()`, `pause()`) or permanent audio focus loss.

---

### Invariant 2: MediaSession Handover Precedence & Rebind Guard
- **Rule**: During slot promotion, the MediaSession must bind to the newly promoted active player **before** the outgoing player is paused or stopped. Duplicate rebind triggers must be guarded.
- **Enforcement**:
  - `sessionHandoverCallback?.invoke(newPrimary)` is called synchronously inside `performPlayerHandoverLocked()` prior to `oldPrimary.stop()`.
  - In `LofiQMediaSessionService`, `bindSessionToPlayer()` checks `if (currentPlayer is ForwardingPlayer && currentPlayer.wrappedPlayer === nextActive) return`.
  - This eliminates duplicate rebinds from the asynchronous `activePlayerFlow` collector, avoiding listener detachment churn and notification rebuild races.

---

### Invariant 3: Foreground Service Non-Demotion
- **Rule**: `LofiQMediaSessionService` must **never** drop its foreground service status (`stopForeground(false)`) while a playback session is in flight, even if the active ExoPlayer is transiently in `STATE_ENDED`, `STATE_BUFFERING`, or `STATE_IDLE` during track resolution.
- **Enforcement**:
  - `hasActivePlaybackSession()` returns `true` whenever:
    - `playerState.isPlaying == true`
    - `playerState.isBuffering == true`
    - `playerState.isCrossfading == true`
    - `isTransitioning == true`
    - `playbackJob?.isActive == true`
    - `activePlayer.playWhenReady == true` (and not idle/ended).
  - In `onUpdateNotification()`, `mustStayInForeground = startInForegroundRequired || isEnginePlaying || isPlayerPlaying || hasActiveSession`.
  - In `onTaskRemoved()`, `stopSelf()` is called **only** if `!playbackEngine.hasActivePlaybackSession()`. Swiping the app from Recents while music is playing or transitioning never kills the service.

---

### Invariant 4: Targeted, Bounded WakeLock Lifecycle
- **Rule**: The Android kernel suspends the CPU when the screen is OFF unless an active partial WakeLock is held or ExoPlayer is actively writing audio buffers to the AudioTrack. WakeLocks must be acquired during asynchronous network resolution and buffering, and released immediately upon buffer readiness.
- **Enforcement**:
  - **PlaybackWakeLock** (`LofiQ:PlaybackWakeLock`, 25s limit): Acquired at the start of `startPlaybackInternal()`. Released when the active player fires `Player.STATE_READY` in `attachSlotListener`, or on failure/cancellation.
  - **PreloadWakeLock** (`LofiQ:PreloadWakeLock`, 25s limit): Acquired at the start of `preloadNextTrack()`. Released when the secondary player fires `Player.STATE_READY` in `attachSlotListener`, or on failure/cancellation.
  - **TransitionWakeLock** (`LofiQ:TransitionWakeLock`, 20s limit): Acquired during equal-power volume ramping and crossfade loops. Released in `performPlayerHandoverLocked()` or cancellation.
  - **Indefinite WakeLocks are strictly forbidden**. All locks use timeouts between 5,000ms and 35,000ms.

---

### Invariant 5: Safe State Persistence & Cold Boot Restoration
- **Rule**: After process death or reboot, the UI mini-player must be restored in a **paused** state without requiring full network resolution or auto-playing audio. Explicit user Stop must never be overridden.
- **Enforcement**:
  - Explicit user `stop()` sets `wasExplicitlyStopped = true` in `LofiQPreferences` and clears `queueSnapshotDao`.
  - When playback is active or paused, `persistQueue()` saves the snapshot, and `saveLastPlayback(songId, positionMs)` records the exact position.
  - On engine `init`, if `wasExplicitlyStopped == false` and persisted queue items exist:
    - Queue and `currentSong` are loaded into `_playerState`.
    - `currentPositionMs` is restored from preferences.
    - `isPlaying = false`, `isBuffering = false`, `isReady = true`.
    - The mini-player appears immediately in the UI. When the user taps Play, playback resumes at the saved position without skipping.

---

### Invariant 6: Resilient Premature EOF Recovery
- **Rule**: Network timeouts, CDN connection drops, and expired streaming URLs must trigger bounded re-resolution without terminating the playback session or silently skipping tracks.
- **Enforcement**:
  - In `handleTrackEnded()`, if `pos < expectedDur - 15_000L` and `retryCount < 2`, the event is classified as premature EOF.
  - `PlaybackWakeLock` is acquired, the cached streaming URL is invalidated (`resolutionCache.invalidate()`), and `startPlaybackInternal(curSong, initialSeekMs = pos)` re-resolves a fresh streaming URL and resumes at `pos`.
  - Events are recorded in `PlaybackDiagnosticLogger` under `CATEGORY_PREMATURE_EOF`.

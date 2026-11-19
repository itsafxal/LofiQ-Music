# Playback Reliability Regression Checklist

Before merging any pull request modifying `PlaybackEngine`, `LofiQMediaSessionService`, `PlaybackWakeLockManager`, or related resolver and audio focus code, verify that all checklist items pass.

---

### 1. Dual-Player & Handover Verification
- [ ] **Synchronous Precedence**: Does `sessionHandoverCallback?.invoke(newPrimary)` execute strictly before `oldPrimary.stop()` and `oldPrimary.clearMediaItems()`?
- [ ] **Idempotent MediaSession Binding**: Does `bindSessionToPlayer` verify whether `(session.player as? ForwardingPlayer)?.wrappedPlayer === nextActive` before reassigning?
- [ ] **AudioFocus Invariance**: Is `audioFocusManager.ensureAudioFocus()` called during handover without calling `abandonAudioFocus()`?
- [ ] **Secondary Slot Isolation**: Does the secondary ExoPlayer remain silent (`volume = 0f`), paused (`playWhenReady = false`), and detached from audio focus until transition starts?

---

### 2. Screen-Off & WakeLock Invariants
- [ ] **Sequential Track Start**: Does `startPlaybackInternal` acquire `playbackWakeLock` (25s max) before launching stream resolution?
- [ ] **Ready Release**: Does `attachSlotListener` release `playbackWakeLock` when the active player fires `Player.STATE_READY`?
- [ ] **Preload Buffer Completion**: Is `preloadWakeLock` retained until the secondary player reaches `Player.STATE_READY` (or error/cancellation)?
- [ ] **No Leaks on Cancellation**: Do all error, cancellation, and stop paths call `wakeLockManager.releaseAll()`?
- [ ] **No Indefinite Locks**: Are all WakeLocks bounded by a strict timeout (<= 35 seconds)?

---

### 3. Foreground Service Continuity
- [ ] **Notification Status**: Does `onUpdateNotification()` verify `playbackEngine.hasActivePlaybackSession()` before deciding `mustStayInForeground`?
- [ ] **Boundary Immunity**: Does the service stay in foreground when active player reports `Player.STATE_ENDED` or `Player.STATE_BUFFERING`?
- [ ] **Task Removal**: Does `onTaskRemoved()` keep the service alive if `hasActivePlaybackSession() == true`?
- [ ] **Foreground Start Deadlines**: Does `onStartCommand()` post a foreground notification immediately when an active session is in flight?

---

### 4. Process Death & State Restoration
- [ ] **Cold Boot Mini-Player**: On app launch with a persisted queue, does the mini-player appear in a paused state (`isPlaying = false`) with the correct track and seek position?
- [ ] **No Silent Autoplay**: Does the app abstain from audio playback on cold boot until the user explicitly taps Play?
- [ ] **Intentional Stop Isolation**: When a user presses Stop, is `wasExplicitlyStopped` set to `true`, and is state restoration suppressed on the next launch?

---

### 5. Diagnostics & Observability
- [ ] **Categorized Logging**: Are terminations logged via `PlaybackDiagnosticLogger.logTermination()` with canonical categories (`USER_STOP`, `USER_PAUSE`, `PREMATURE_EOF`, `STREAM_ERROR`, `HANDOVER_FAILURE`)?
- [ ] **Ring Buffer Persistence**: Does `playback_diagnostic_events.json` update on critical lifecycle events without logging signed URLs or authentication tokens?
- [ ] **Logcat Accessibility**: Can events be filtered cleanly using `adb logcat -s LofiQBackgroundPlayback`?

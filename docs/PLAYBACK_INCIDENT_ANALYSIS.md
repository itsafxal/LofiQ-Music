# Incident Analysis: Intermittent Playback Termination & Missing Mini-Player

## 1. Executive Summary
- **Incident Description**: Users experienced intermittent playback termination during track boundaries and sequential song playback. The failure occurred substantially more frequently when the display was turned OFF. Furthermore, reopening the application revealed a blank home screen with no mini-player, behaving as though no playback session existed.
- **Severity**: High (Production Playback Reliability)
- **Status**: Resolved permanently across dual-player engine, foreground service, wake lock management, and persistent session state restoration.

---

## 2. Observed Symptoms & User Impact
1. **Mid-Song & Boundary Termination**: Songs played normally until near the end or into the next track. The subsequent song either stopped halfway through or failed to begin.
2. **Screen-Off Prevalence**: Terminations occurred rarely when the screen was actively manipulated, but reliably reproduced when the screen was turned off for multiple consecutive song transitions.
3. **Vanished Mini-Player**: Reopening the app did not show a paused or active mini-player; the queue appeared lost and `playerState.currentSong` was `null`.
4. **App Resets**: Users were forced to re-search or select a playlist from scratch.

---

## 3. Confirmed Root Causes

### Root Cause 1: Kernel CPU Sleep During Screen-Off Primary Stream Resolution
- **Investigation**: In `PlaybackEngine.kt`, WakeLocks were only held during crossfade loops and secondary track preloading. When a track completed and the next track was loaded via `startPlaybackInternal()` / `loadAndPlaySong()`, the engine made asynchronous network calls (`resolverManager.resolve()`) without holding a WakeLock.
- **Mechanism**: When the device screen is OFF and ExoPlayer is not actively rendering audio (between tracks), Android's kernel enters deep CPU sleep (suspend-to-RAM). The coroutine executing the HTTP stream resolution froze mid-flight or timed out (SocketTimeoutException), causing silent playback termination.

### Root Cause 2: Premature Preload WakeLock Release
- **Investigation**: In `preloadNextTrack()`, `wakeLockManager.releasePreloadWakeLock()` was called immediately after `sec.prepare()` at line 2158.
- **Mechanism**: `ExoPlayer.prepare()` is asynchronous; at line 2158 the secondary player was almost always in `Player.STATE_BUFFERING`, not `Player.STATE_READY`. Releasing the WakeLock allowed the CPU to sleep before the secondary player finished buffering audio chunks. When the transition boundary arrived, the secondary track was not ready, forcing a fallback to the unprotected sequential path.

### Root Cause 3: Foreground Service Demotion During Track Boundaries
- **Investigation**: In `LofiQMediaSessionService.kt`, `onUpdateNotification()` computed:
  ```kotlin
  val isPlayerPlaying = session.player.playWhenReady &&
      session.player.playbackState != Player.STATE_IDLE &&
      session.player.playbackState != Player.STATE_ENDED
  val mustStayInForeground = startInForegroundRequired || isEnginePlaying || isPlayerPlaying
  ```
- **Mechanism**: When a song reached `STATE_ENDED` or while the active player was waiting for the next buffer, `isPlayerPlaying` became `false`. If `isEnginePlaying` flickered, `mustStayInForeground` became `false`. Media3 called `stopForeground(false)`. Once demoted from foreground status while the screen was OFF, Android's Low Memory Killer (LMK) and Background Execution Limits terminated the service process within seconds.
- Additionally, `onTaskRemoved()` called `stopSelf()` if `player.playbackState == Player.STATE_ENDED`, killing the service if the user dismissed recent apps during a song transition.

### Root Cause 4: Duplicate MediaSession Rebinding Race
- **Investigation**: In `LofiQMediaSessionService.kt`, both the synchronous `sessionHandoverCallback` and the asynchronous `activePlayerFlow.drop(1).collect` invoked `session.player = createForwardingPlayer(nextActive)`.
- **Mechanism**: This triggered redundant back-to-back rebinds, causing listener detach/reattach thrashing, notification rebuild contention, and transient null-player states during atomic slot promotion.

### Root Cause 5: Unrestored Queue & State on Process Restart
- **Investigation**: In `PlaybackEngine.init`, no restoration logic existed to read `QueueSnapshotDao` or `LofiQPreferences.lastPlayedSongId`.
- **Mechanism**: When the Android OS terminated the app process (due to memory pressure or post-demotion), cold-starting the app initialized `PlaybackEngine` with an empty queue (`currentQueue.isEmpty()`) and `_playerState.value.currentSong = null`. In `MainScreen.kt`, the mini-player visibility check (`visible = playerState.currentSong != null`) evaluated to `false`, leaving the user with an empty UI.

### Root Cause 6: Premature EOF Re-Preparing Expired Streaming URLs
- **Investigation**: In `handleTrackEnded()`, premature EOF detection called `active.prepare()` on the existing `MediaItem`.
- **Mechanism**: When an EOF was caused by an expired signed CDN/YouTube URL (URLs often expire after 2–6 hours), calling `active.prepare()` re-requested the identical expired URL, failing repeatedly or looping into track end.

---

## 4. Permanent Architectural Remediations

| Component | Modification | Impact |
| :--- | :--- | :--- |
| `PlaybackWakeLockManager.kt` | Added `playbackWakeLock` with 25s bounded acquire/release. Extended preload timeout to 25s. | Guards stream resolution against CPU kernel sleep. |
| `PlaybackEngine.kt` | Acquired `playbackWakeLock` in `startPlaybackInternal()`; released upon `Player.STATE_READY` in `attachSlotListener`. | Guarantees continuous CPU execution until audio is rendering. |
| `PlaybackEngine.kt` | Deferred `releasePreloadWakeLock()` until secondary slot reaches `Player.STATE_READY` in `attachSlotListener`. | Ensures secondary buffer is fully populated before CPU sleep. |
| `PlaybackEngine.kt` | Added `hasActivePlaybackSession()`; updated `handleTrackEnded()` to invalidate cache and re-resolve on premature EOF. | Prevents silent stall on expired URLs; informs foreground service of in-flight sessions. |
| `PlaybackEngine.kt` | Implemented `restoreSavedPlaybackSessionIfAvailable()` in `init` for paused cold-boot state restoration. | Restores mini-player and queue on process restart without auto-play. |
| `LofiQMediaSessionService.kt` | Factored out `bindSessionToPlayer()` with identity check (`wrappedPlayer === nextActive`). | Eliminates duplicate rebind thrashing and notification rebuild races. |
| `LofiQMediaSessionService.kt` | Factored `hasActivePlaybackSession()` into `mustStayInForeground` and `onTaskRemoved()`. | Service remains in foreground throughout track boundaries and buffering. |
| `PlaybackDiagnosticLogger.kt` | Added thread-safe 100-entry ring buffer with disk persistence across process death. | Comprehensive post-mortem observability for `USER_STOP`, `PREMATURE_EOF`, etc. |
| `LofiQPreferences.kt` | Added `WAS_EXPLICITLY_STOPPED` key and flow. | Differentiates intentional user stops from process termination. |
| `Daos.kt` | Added `getSnapshotItems()` to `QueueSnapshotDao`. | Restores accurate `isCurrent` track metadata on app launch. |

---

## 5. Verification
- Regression test suite `ScreenOffPlaybackRegressionTest.kt` passes with new assertions covering:
  - Screen-off sequential resolution acquiring and releasing `playbackWakeLock`.
  - Non-demotion of foreground service during in-flight sessions.
  - Safe paused restoration on cold boot.
  - Explicit user stop clearing state and releasing all locks.

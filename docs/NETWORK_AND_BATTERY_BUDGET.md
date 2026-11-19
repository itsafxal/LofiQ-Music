# Network & Battery Budget Specifications

## Overview
LofiQ Music enforces strict boundaries on background networking and CPU wakefulness to prevent mobile data depletion, battery drain, and Google Play Store policy violations.

---

## 1. Network Usage Budget

| Operation | Frequency | Data Budget | Conditions |
| :--- | :--- | :--- | :--- |
| **Startup Feed Rendering** | Every launch | **0 KB** (from cache) | Always offline-first |
| **Foreground SWR Refresh** | At most once every 15 min | **~25 KB** (compact JSON) | Active UI only |
| **Background Scheduled Sync** | Every 4 hours | **~25 KB** (metered) / **~1.2 MB** (Wi-Fi) | Battery not low + network connected |
| **FCM Event Invalidation** | On push trigger | **< 1 KB** (FCM payload) | Server invalidation only |
| **App Update Check** | On launch / periodic WorkManager | **~1.5 KB** (version.json) | Deduplicated & throttled |
| **Full APK Update Download** | User-authorized | **~25 - 30 MB** (one-time) | Explicit user approval |

### Metered Connection Protection
- Large artwork pre-caching is disabled on metered networks (`ConnectivityManager.isActiveNetworkMetered == true`).
- Audio stream resolution occurs only upon user playback request; audio is never pre-downloaded during metadata sync.

---

## 2. Battery & WakeLock Constraints

### Playback WakeLock Invariants (Preserved)
- Active playback relies on `C.WAKE_MODE_NETWORK` within ExoPlayer.
- Dual-player transitions use bounded wake locks with strict timeouts (15 seconds for playback wake locks, 20 seconds for preload wake locks).
- **Zero Indefinite WakeLocks**: The app never holds unbounded CPU wake locks.

### Background Sync Battery Rules
- `BackgroundSyncWorker` periodic requests require `setRequiresBatteryNotLow(true)`.
- No sync worker requests battery optimization exemptions (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
- Realtime Firestore snapshot listeners are attached exclusively during active foreground sessions and cleanly disconnected in `onActivityStopped()`.
- The 5-minute foreground live sync timer is cancelled immediately when the app moves to the background.

---

## 3. Diagnostics & Telemetry (`SyncDiagnosticsTracker`)
The singleton `SyncDiagnosticsTracker` monitors real-time efficiency metrics:
- Total bytes transferred across all HTTP requests.
- HTTP request counter.
- Cache hit/miss ratio (target: **> 85%** cache hits across ordinary usage).
- Last successful sync duration and timestamp.
- Number of skipped unchanged records.
- Total failure and retry counts.

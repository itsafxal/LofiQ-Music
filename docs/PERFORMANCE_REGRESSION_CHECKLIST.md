# Performance & Playback Regression Checklist

Before committing modifications to the data layer, caching system, background workers, or update subsystem, verify each item in this checklist:

---

## 1. Startup & Caching Invariants
- [ ] Does cold boot render cached songs in under 500 ms without waiting for network responses?
- [ ] Are shelf database reads executed using `getByPrefix` in a single query rather than 50+ individual queries?
- [ ] Is `loadMutex` in place to coalesce concurrent cache load requests?
- [ ] Does the app refrain from showing full-page skeletons when valid cached data exists?
- [ ] On clean install (empty cache), does the app display graceful loading indicators rather than crashing?

## 2. Background Sync & Network Discipline
- [ ] Does `BackgroundSyncWorker` declare `NetworkType.CONNECTED` and `setRequiresBatteryNotLow(true)`?
- [ ] Is metered mobile data respected so large artwork files are not pre-cached over cellular data?
- [ ] Is foreground live sync throttled to a minimum of 15 minutes between network refreshes?
- [ ] Are foreground timers cancelled when the application moves to the background?
- [ ] Are Firestore realtime listeners unregistered in `onStop()` / background transition?

## 3. App Update System Safety
- [ ] Does `isInstalledViaGooglePlay` accurately discriminate Play Store installations from sideloaded APKs?
- [ ] Does the APK download verify streaming SHA-256 checksums before initiating installation?
- [ ] Does `PackageManager.getPackageArchiveInfo()` verify that the package name matches `com.sargamify.app`?
- [ ] Are update notifications deduplicated with a 24-hour cooldown for the same version?
- [ ] Does tapping "Later" dismiss the notification and record a deferral timestamp without nagging the user?
- [ ] Does the update dialog show installed vs new versions and warn if music playback is active?

## 4. Playback Subsystem Regression Invariants (CRITICAL)
- [ ] Does dual-player gapless crossfade handover remain completely intact?
- [ ] Are playback and preload WakeLocks strictly bounded by timeouts (no indefinite wake locks)?
- [ ] Does `LofiQMediaSessionService` retain foreground service status throughout song transitions?
- [ ] Is cold boot playback restoration paused (`isPlaying = false`) without silent autoplay?
- [ ] Does explicit Stop clear the session cleanly without reviving on relaunch?
- [ ] Does background data sync run strictly on `Dispatchers.IO` without competing with ExoPlayer audio rendering?

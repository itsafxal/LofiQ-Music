# Agent Instructions & Playback Architectural Guidelines

## Strict Operational Rules for AI Agents

1. **Root Directory Cleanliness**:
   - **Never create markdown or text report files in the project root directory**.
   - All documentation, incident analyses, and architectural invariants must reside exclusively inside `docs/`.

2. **Playback Subsystem Modifications**:
   - Before modifying any file in `app/src/main/java/com/lofiq/playback/`:
     - Read and strictly observe `docs/PLAYBACK_RELIABILITY_INVARIANTS.md`.
     - Review `docs/PLAYBACK_INCIDENT_ANALYSIS.md` for historical context on screen-off failures.
     - Execute all checks in `docs/PLAYBACK_REGRESSION_CHECKLIST.md`.
   - Never replace the dual-player gapless/crossfade architecture with a single player.
   - Never acquire unbounded WakeLocks (indefinite wake locks will cause severe battery drain and Play Store policy rejection).
   - Never remove `sessionHandoverCallback` or the idempotent player rebind guard in `LofiQMediaSessionService`.
   - Never drop foreground service status during track transitions or buffering.
   - On cold boot, always restore the playback session in a **paused** state (`isPlaying = false`). Never implement silent autoplay on application launch.

3. **Startup Performance & Offline-First Invariants**:
   - Prioritize reading local cache before performing network requests.
   - Cached data must never be blocked by remote requests (<500ms startup to cached UI).
   - Never replace an already populated Home Screen with a full-page spinner or blank skeleton.
   - Use batch SQLite queries (`getByPrefix`) for shelf deserialization; never loop sequential single-key queries.

4. **Background Synchronization & Battery Protection**:
   - Background catalog sync must run on `Dispatchers.IO` and must never block ExoPlayer audio threads.
   - Background work must be bounded, cancelable, and idempotent.
   - No ordinary background task may depend on exact five-minute timing or continuous polling loops.
   - Cleanly unregister realtime listeners and cancel foreground timers on activity stop / lifecycle teardown.
   - Never preload large artwork collections on metered mobile networks.

5. **Smart Application Update Invariants**:
   - Differentiate Google Play installations from direct APK sideloads.
   - Direct APK updates must always validate SHA-256 checksum and package identity before triggering the system installer.
   - Update notifications must be version-deduplicated (suppress repeated notifications for the same version within 24 hours).
   - Never interrupt or reset active music playback to prompt or install an update. Always warn the user if playback will pause.

6. **Audio Pipeline & Metadata Invariants**:
   - Never fabricate bitrate, codec, or bit-perfect claims. Display "Unknown" or "—" when unverified.
   - Never block audio threads with metadata resolution or reflection. Run all pipeline inspection on Main/IO coroutines.
   - Guard every listener callback against stale ExoPlayer instances during dual-player handovers (`player !== currentObservedPlayer`).
   - The Audio Pipeline dialog must never disrupt, reinitialize, or reset active playback sessions.

7. **Release Integrity & Versioning Invariants**:
   - Never publish or advertise a release before validating its APK artifact and version code progression.
   - The About screen and update comparison must always query the runtime `PackageManager` via `AppVersionProvider`, never hardcoded strings or remote metadata.
   - Never launch the system installer if the downloaded APK's `versionCode` is less than or equal to the currently installed `versionCode`.
   - Never treat opening the installer or downloading an APK as a completed installation.

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

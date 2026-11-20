# Player Resource Lifecycle & Dual-Player Audio Architecture

## Invariants

1. **Dual-Player Slot Isolation**:
   - `playerSlotA` and `playerSlotB` share alternating responsibilities as Primary (audible, active MediaSession owner) and Secondary (silent, preloaded).
   - Under no circumstances may both players output audible audio simultaneously outside bounded crossfade windows.

2. **Clean Release of Outgoing Primary**:
   - Outgoing player must be muted (`volume = 0f`), paused, stopped, and cleared (`clearMediaItems()`) **after** the incoming player has been registered with the `MediaSession`.
   - Never release or stop a slot player while it is still the designated session player of `MediaSessionService`.

3. **AudioSession & DSP Slot Management**:
   - Only a single `AudioEffectsController` instance is permitted per application process.
   - `AudioPipelineMonitor` and `MainScreen` attach to the active `audioSessionId` without creating duplicate effect instances.

# Performance & Playback Regression Checklist

## Verification Scenarios

| Scenario | Objective | Validation Invariant |
|---|---|---|
| **P-01** | Progressive 10-Song Playback | Heap memory stabilizes without progressive GC thrashing |
| **P-02** | Rapid Track Skip (Next x5) | Prior network and decode jobs cancel immediately |
| **P-03** | Auto-Advance (Song A -> B) | Song B starts playing without pausing at 0ms |
| **P-04** | Manual Next on Preloaded Slot | Handover is immediate, `playbackIntent == PLAY` preserved |
| **P-05** | Explicit Pause Intent | `playbackIntent == USER_PAUSED`; no autoplay on handover |
| **P-06** | Screen-Off Transition | MediaSession foreground service maintained throughout |
| **P-07** | Background Audio Focus Interruption | Dwell in transient pause; resume on focus regain |
| **P-08** | Cold Boot Restoration | Player restores queue and position in paused state |
| **P-09** | Recomposition Budget | Home screen does not recompose on 200ms position ticks |
| **P-10** | Palette Cache Boundedness | Palette cache size <= 20 entries under long sessions |

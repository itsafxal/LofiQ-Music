# Android UI Performance & Recomposition Prevention Architecture

## Problem Statement

After playing 2-3 songs, progressive lag and UI stuttering occurred due to:
1. High-frequency position tracking (200ms ticks) updating a monolithic `playerState`.
2. Root composables (`MainScreen`, `HomeScreen`) collecting `playerState.collectAsState()`, forcing full-screen recompositions (including 42dp Gaussian blur shaders and multi-row carousels) 5 times per second.
3. Unmanaged concurrent background jobs (recommendation fetches, artwork decodes, history writes) launching on every song change without cancellation.
4. Unbounded bitmap and color palette caches.

## Architectural Solutions Implemented

### 1. Isolated High-Frequency Progress State Flow
- `PlaybackEngine` exposes `playbackProgress: StateFlow<PlaybackProgress>` for seekbars and timing indicators only.
- `HomeScreen` and `MainScreen` observe isolated `currentSongFlow: StateFlow<Song?>` and `isPlayingFlow: StateFlow<Boolean>`.
- Recomposition frequency drops from ~300 recompositions per minute to ~2 per track.

### 2. Managed Coroutine Job Handles
- `recommendationsJob`: Cancelled and restarted on track change to prevent accumulating 5-10 concurrent YouTube searches.
- `artworkJob` and `prefetchArtworkJob`: Scoped and cancelled on subsequent track requests.
- `prefetchShelfArtworksJob`: Capped at 12 items with debounced cancellation.

### 3. Bounded Palette and Image Caching
- `paletteCache` in `AmbientAppAtmosphere` upgraded to a thread-safe synchronized LRU cache bounded to 20 entries.
- Native `AudioEffectsController` unified under `@Singleton PlaybackEngine` to eliminate duplicate AudioTrack sessions.

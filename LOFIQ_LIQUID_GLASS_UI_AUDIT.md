# LofiQ — Premium Liquid Glass UI Audit
## Complete Application-Wide Design Inventory & Architecture Analysis

**Date:** November 2026  
**Application:** LofiQ Music  
**Target Platforms:** Android (Kotlin + Jetpack Compose) & Windows Desktop (Flutter)  
**Reference Benchmark:** BitChord Liquid Glass Architecture & Apple Music High-Fidelity Translucency (Visual & Architectural Study, PR #1108 / Issue #1107)  
**Licensing Notice:** GPL-3.0 Compliance strictly maintained — zero vendored or copied source code; 100% original, appropriately licensed LofiQ implementation.

---

## 1. Executive Summary & Audit Scope

This audit provides a comprehensive, multi-platform inventory of all user interface components, screens, dialogs, overlays, and navigation structures across both the **Android** and **Windows Desktop** applications of LofiQ Music. 

In accordance with project requirements, this audit excludes the **Home Screen** (which is scheduled for an independent future phase) while establishing a unified Liquid Glass material design system for all other existing screens:
1. Settings & Preferences
2. In-App Application Update System
3. Dialogs, Alerts, Popups, and Contextual Menus
4. Bottom Sheets & Modal Sheets
5. Audio Player Controls, Sleep Timer, Lyrics, & Queue
6. 10-Band Parametric Equalizer & DSP Audio Effects
7. Search & Discovery Filters / Category Tabs
8. Library, Playlists, & Local Music Management
9. Profile, Authentication, & Spotify Integration
10. Listen Together Real-Time Collaborative Listening
11. Developer Tools & Network Diagnostics

---

## 2. Android (Kotlin + Jetpack Compose) Audit

### 2.1 Architecture & Theme Engine
- **Framework:** Jetpack Compose (BOM 2024.10.01) with Material 3 (1.3.x).
- **Compilation Target:** compileSdk 35, minSdk 26, targetSdk 35, Java 17 / Kotlin 2.0.21.
- **Theme Foundation:** `app/src/main/java/com/lofiq/ui/theme/Theme.kt` (`LofiQTheme`), `Color.kt`, `Type.kt`.
- **Dynamic Atmosphere Engine:** `AmbientAppAtmosphere.kt` and `DynamicArtworkBackground.kt` extract dynamic palette colors using AndroidX Palette (`ArtworkColorUtils.kt`) and render multi-stop vertical gradients with radial blooms and dark scrims.
- **Current Glass Implementation:** An initial `LiquidGlassTokens` and `LiquidGlassContainer` exists in `LiquidGlass.kt`. However, it relies solely on simple vertical linear gradient brushes and border strokes without genuine backdrop sampling, unified material presets, or specialized glass controls (sliders, switches, inputs, dialogs, segmented controls).
- **Performance Guardrail:** `LocalPerformanceModeEnabled` and `DevicePerformanceManager.isLowSpecDevice(context)` selectively toggle off expensive GPU blur passes for low-RAM devices (<=4GB) to ensure uninterrupted 60 FPS audio playback.

### 2.2 Screen Composables Inventory (Non-Home)

| Screen / Feature | Location | Existing Visual Style | Target Liquid Glass Transformation |
| :--- | :--- | :--- | :--- |
| **Settings Screen** | `feature/settings/SettingsScreen.kt` | Flat `SurfaceCard` containers with simple 1px outline borders; standard Material switches and sliders. | `GlassPanel` grouped categories, `GlassCard` rows, `GlassSwitch`, `GlassSlider`, continuous 20dp squircles, specular rim lighting. |
| **Appearance & Theme** | `feature/settings/AppearanceThemeScreen.kt` | Static grid cards with solid dark background (`0xFF121218`). | `GlassCard` with dynamic preview tiles, floating active selection pills, translucent backdrop sampling. |
| **App Icon Studio** | `feature/settings/AppIconStudioScreen.kt` | Flat card grid with theme alias switches. | Floating glass preview pedestal, specular badges with Tangerine accent glow. |
| **Seekbar Style Studio** | `feature/settings/SeekbarStyleScreen.kt` | Simple vertical list of seekbar styles. | Interactive `GlassCard` preview containers with live animated waveform waveforms. |
| **Search Screen** | `feature/search/SearchScreen.kt` | Standard `TextField` with `CardGlassFill`, pill chips with border overlay. | `GlassInput` search bar with inner specular sheen, `GlassSegmentedControl` filter chips, translucent search history rows. |
| **Library Screen** | `feature/library/LibraryScreen.kt` | Solid `SurfaceElevated` / `0xFF0F0F14` sheets, flat cards, Material action icons. | `GlassNavigationSurface` tab headers, `GlassCard` playlist tiles, translucent stats banners, specular quick actions. |
| **Local Music Screen** | `feature/local/LocalMusicScreen.kt` | Dark list with standard permission card. | `GlassSurface` header with storage quota metrics, `GlassCard` track items with subtle Tangerine play indicator. |
| **Collection Detail** | `ui/components/CollectionDetailScreen.kt` | Hero banner with dark backdrop and standard buttons. | Translucent floating hero panel with backdrop blur, `GlassButton` play/shuffle controls, blurred sticky header. |
| **Explore Screen** | `feature/explore/ExploreScreen.kt` | Grid cards with solid gradients and static icons. | `GlassCard` mood capsules with dynamic color refraction and specular borders. |
| **Profile Screen** | `feature/profile/ProfileScreen.kt` | Monochrome dark cards (`0xFF0F0F0F`, `0xFF181818`), plain borders. | Translucent `GlassCard` identity card, glass stats badges, glass provider integration tiles. |
| **Equalizer Screen** | `feature/equalizer/EqualizerScreen.kt` | Solid dark card (`0xFF131318`), plain border (`0xFF26262F`). | Multi-stage `GlassPanel` housing the frequency curve, `GlassCard` faders, glass preset chips, translucent rotary dials. |
| **Developer Screen** | `feature/developer/DeveloperScreen.kt` | Monochrome cards (`0xFF0F0F0F`), plain buttons. | Clean `GlassCard` diagnostic monitors, monospace debug readout panels with frosted backdrop. |
| **Resolver Diagnostics** | `feature/debug/ResolverDiagnosticsScreen.kt` | Flat status indicators. | `GlassCard` latency meters with glowing status pills (Emerald Green, Amber, Red). |

### 2.3 Dialogs, Bottom Sheets & Popups Inventory

| Modal / Overlay Component | Location | Existing Implementation | Deficiencies & Target Glass Upgrade |
| :--- | :--- | :--- | :--- |
| **In-App Update Dialog** | `feature/settings/SettingsScreen.kt` & `MainScreen.kt` | Material `AlertDialog` with solid `Color(0xFF141416)`, solid progress bar. | Upgrade to `GlassDialog`: frosted translucent backdrop, specular rim highlight, glowing Tangerine progress fill, glass action buttons. |
| **Queue Bottom Sheet** | `feature/queue/QueueSheet.kt` | `ModalBottomSheet` with `0xFF101018` (alpha 0.88), custom drag handle. | Upgrade to `GlassBottomSheet`: genuine backdrop blur sampling, specular top edge, floating reorder pills, translucent item cells. |
| **Lyrics Bottom Sheet** | `feature/lyrics/LyricsSheet.kt` | `ModalBottomSheet` with `0xFF101018` (alpha 0.88). | Upgrade to `GlassBottomSheet`: full translucent backdrop, glowing karaoke line focus, glass sync mode toggle. |
| **Equalizer Bottom Sheet** | `feature/equalizer/EqualizerBottomSheet.kt` | `ModalBottomSheet` with `Color(0xFF0C0C10)`, solid cards. | Full glass modal with translucent fader slots and specular curve container. |
| **Sleep Timer Sheet** | `feature/player/SleepTimerSheet.kt` | Standard bottom sheet with plain minute chips. | `GlassBottomSheet` with circular glass countdown ring and glowing Tangerine presets. |
| **Album Songs Sheet** | `feature/player/AlbumSongsSheet.kt` | Plain dark sheet. | `GlassBottomSheet` with blurred album header and glass track list. |
| **Listen Together Sheet** | `feature/sync/ListenTogetherBottomSheet.kt` | `ModalBottomSheet` with `Color(0xFF101010)`. | Translucent glass room cards, glowing host/listener badges, glass pin code inputs, glass participant avatar bubbles. |
| **Add To Playlist Dialog** | `feature/playlist/AddToPlaylistDialog.kt` | `ModalBottomSheet` with `Color(0xFF0F0F14)` and solid cards. | `GlassBottomSheet` with glass search input, translucent playlist rows, and glass creation button. |
| **Spotify Import & Login Dialogs** | `feature/spotify/Spotify*.kt` | Standard Compose `AlertDialog` with solid containers. | `GlassDialog` with frosted glass backdrop, specular borders, and progress glow. |
| **Account Dialog & Login Sheet** | `auth/AccountDialog.kt`, `LoginSheet.kt` | Custom `Dialog` with solid `SurfaceCard` and plain outlines. | `GlassDialog` & `GlassBottomSheet` with glass avatar pedestal and subtle specular sheens. |
| **Seamless Play / Theme Dialogs**| `feature/settings/*Dialog.kt` | Standard `AlertDialog` components. | Standardized `GlassDialog` with smooth scale transitions and continuous rounded corners. |

### 2.4 Floating Surfaces & Player Controls
- **Bottom Navigation Bar (`BottomNavBar.kt`):** Floating frosted capsule with 36dp rounded corners, shadow, and spring indicator pill. Already possesses strong liquid aesthetics, but needs integration with the unified glass token suite.
- **Mini Player (`MiniPlayer.kt`):** Floating capsule above the nav bar with swipe gesture and liquid play/pause button. Needs centralized specular rim gradient and translucent backdrop sampling.
- **Full Player Sheet (`FullPlayerSheet.kt`):** Edge-to-edge full-bleed artwork background with floating glass pill controls for Repeat, Shuffle, Audio Device picker, Queue, and Lyrics. Needs unified glass button styling.

---

## 3. Windows Desktop (Flutter) Audit

### 3.1 Architecture & Theme Engine
- **Framework:** Flutter 3.44.5 (Dart 3.12.2), Windows Desktop Target.
- **Audio & DSP Core:** `media_kit` (libmpv 2.0 native bindings) with 10-band ffmpeg `equalizer`, bass exciter, spatializer, and dual-instance gapless crossfading.
- **Theme Foundation:** `desktop/lib/core/theme/colors.dart` (`LofiQColors`), `typography.dart`, `liquid_glass.dart`.
- **Dynamic Background:** `dynamic_ambient_background.dart` with animated Canvas mesh gradients and radial blooms reacting to active track artwork.
- **Current Glass Component:** `GlassCard` (`desktop/lib/ui/components/glass_card.dart`) wraps a `BackdropFilter` with `ImageFilter.blur(sigmaX: 18, sigmaY: 18)`. However, the container color is placed on the parent `Container` rather than the filtered content layer, causing contrast flattening, and no dedicated glass controls (buttons, switches, sliders, dialogs, inputs) exist.

### 3.2 Screen Widgets Inventory (Non-Home)

| Screen / Feature | Location | Existing Visual Style | Target Liquid Glass Transformation |
| :--- | :--- | :--- | :--- |
| **Settings Screen** | `desktop/lib/ui/screens/settings_screen.dart` | `SingleChildScrollView` with `GlassCard` wrappers, raw `DropdownButton`, standard `Switch`, and basic update button. | Reusable `GlassPanel` sections, `GlassSwitch`, `GlassSlider`, `GlassSegmentedControl`, glass dropdown menu, and dedicated failover updater glass cards. |
| **10-Band Equalizer & DSP**| `desktop/lib/ui/screens/equalizer_screen.dart` | Basic `GlassCard` with standard sliders, vertical faders, and rotary knobs. | Translucent `GlassPanel` curve analyzer, `GlassCard` studio fader rack, rotary knob glass pedestals, preset glass pills. |
| **Search Screen** | `desktop/lib/ui/screens/search_screen.dart` | Raw `Container` with text input and `ChoiceChip` items. | Translucent `GlassInput` search bar, `GlassSegmentedControl` search provider filter, `GlassCard` track list. |
| **Library Screen** | `desktop/lib/ui/screens/library_screen.dart` | Grid of `GlassCard` items, standard `AlertDialog` for creation. | Translucent glass shelf containers, glass playlist cards with specular rim gradients, `GlassDialog` creation modal. |
| **Local Music Screen** | `desktop/lib/ui/screens/local_music_screen.dart` | Search text field in raw container, basic song list. | `GlassInput` search field, glass folder statistics bar, glass scan progress indicator. |
| **Spotify Importer** | `desktop/lib/ui/screens/spotify_import_screen.dart` | Basic text field and import progress bar. | `GlassInput` Spotify URL box, `GlassCard` progress telemetry, glass track resolution list. |
| **Lyrics Screen** | `desktop/lib/ui/screens/lyrics_screen.dart` | Side panel / full screen with text list. | Frosted glass backdrop sampling, floating karaoke line illumination, glass provider switcher. |
| **Queue Screen** | `desktop/lib/ui/screens/queue_screen.dart` | Reorderable list inside `GlassCard`, basic save dialog. | Translucent glass queue rows, glass reorder handles, `GlassDialog` save modal. |
| **Diagnostics Screen** | `desktop/lib/ui/screens/diagnostics_screen.dart` | Card list of endpoints with latency badges. | Frosted glass telemetry cards, glowing status diodes, glass ping trigger buttons. |
| **Explore Screen** | `desktop/lib/ui/screens/explore_screen.dart` | Mood chips and station cards with solid gradients. | Translucent `GlassCard` station cards with dynamic backdrop refraction and specular borders. |

### 3.3 Overlays, Menus & Shell Controls
- **Desktop Shell (`desktop_shell.dart`):** Navigation rail currently uses opaque `surfaceBlack` (`0.85` alpha) with a plain 1px white border. Needs transformation into a frosted `GlassNavigationSurface` that subtly samples the dynamic ambient artwork beneath the sidebar.
- **Window Titlebar (`window_titlebar.dart`):** Custom Windows drag bar. Needs glass surface integration with seamless specular top border.
- **Desktop Player Bar (`mini_player.dart`):** Persistent bottom bar with `BackdropFilter` (sigma 20). Needs continuous corner styling, specular top highlight gradient, and refined glass button controls.
- **Context Menus & Dialogs:** Currently standard Flutter `AlertDialog` with solid `surfaceCard` backgrounds. Must be replaced with `GlassDialog` featuring continuous rounded borders and backdrop blur.

---

## 4. Shared Design Tokens vs. Platform-Specific Implementations

| Capability | Shared Design Token / Concept | Android Jetpack Compose Implementation | Windows Desktop Flutter Implementation |
| :--- | :--- | :--- | :--- |
| **Glass Blur** | Blur radius: 16dp–28dp (standard), 32dp (modal) | `Modifier.graphicsLayer { renderEffect = ... }` / `Modifier.blur()` / Fallback scrim | `BackdropFilter(filter: ImageFilter.blur(sigmaX, sigmaY))` |
| **Specular Rim**| Top-to-bottom vertical gradient: `alpha 0.28` -> `0.10` -> `0.02` | `Brush.verticalGradient` applied via `Modifier.border` or custom DrawScope | `LinearGradient` applied via `CustomPainter` or `BoxDecoration(border: Border.all / gradient)` |
| **Corner Geometry** | Continuous squircle: Controls (12–16dp), Cards (18–24dp), Dialogs (24–30dp), Bars (Capsule) | `RoundedCornerShape(dp)` | `BorderRadius.circular(double)` with `ClipRRect` |
| **Glass Presets**| Light, Dark, Floating, Elevated, Subtle, Modal | Centralized enum/data class with Compose composables | Centralized class with Flutter Widget constructors |
| **Color Palette**| Tangerine (`#EB7D00`), Vanilla (`#EBE3A7`), Brunswick Green (`#2C5745`), Drab Brown (`#2E2910`), Deep Black (`#070707`) | Jetpack Compose `Color` tokens | Flutter `Color` constants in `LofiQColors` |
| **Perf Fallback**| High-performance / reduced transparency mode | `LocalPerformanceModeEnabled` bypasses GPU blur, rendering hardware-accelerated dark cards | `PreferencesManager.settings.performanceMode` disables `BackdropFilter` |

---

## 5. Deliberately Excluded Components (Phase 7 Home Page Scope)

In strict adherence to the project specification, the following files and components are **deliberately preserved and excluded** from redesign in this phase:
- **Android Home Screen:** `app/src/main/java/com/lofiq/feature/home/HomeScreen.kt` & `HomeViewModel.kt`
- **Windows Desktop Home Screen:** `desktop/lib/ui/screens/home_screen.dart`
- **Home Page Artwork Shelves & Carousels:** All shelf item layouts, section order, discovery algorithms, and category hero banners within `HomeScreen` remain untouched.
- Shared components utilized by Home (e.g. `SongRow.kt`, `AlbumCard.kt`, `DynamicBackground.kt`) maintain strict backwards-compatibility so that their behavior, layout bounds, and visual presentation on the Home screen remain 100% stable.

---

## 6. Audit Conclusion & Next Steps

Both platforms share a robust modern audio foundation (`media3` ExoPlayer on Android and `media_kit` libmpv on Windows) and high-quality dynamic ambient artwork backdrops. However, both platforms currently suffer from fragmented glass styling: screens inconsistently mix solid opaque cards (`0xFF141419`), plain single-color borders (`0xFF26262F`), and raw unstyled Material dialogs with occasional partial blur effects.

**Action Plan:**
1. Formulate the comprehensive specification in `LOFIQ_LIQUID_GLASS_DESIGN_SYSTEM.md`.
2. Construct the centralized Android Liquid Glass library with all 14 reusable components and backdrop blur management.
3. Construct the matching Windows Desktop Flutter Liquid Glass library with all 14 reusable components and `BackdropFilter` optimization.
4. Systematically transform every non-Home screen, dialog, sheet, player control, equalizer, and update workflow across both platforms.
5. Verify build integrity (`gradlew compileDebugKotlin`, `flutter analyze`, `flutter test`) and document before/after verification evidence.

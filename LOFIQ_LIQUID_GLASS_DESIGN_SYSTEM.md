# LofiQ — Liquid Glass Design System
## Unified Multi-Platform Design Specification & Component Architecture

**Version:** 2.0  
**Target Platforms:** Android (Kotlin + Jetpack Compose) & Windows Desktop (Flutter)  
**Brand Identity:** LofiQ Music  
**Visual Direction:** High-Fidelity Translucent Liquid Glass with Continuous Geometry, Specular Rim Highlights, and Neutral Dark Material Substrates.

---

## 1. Design System Philosophy & Core Principles

The LofiQ Liquid Glass Design System is inspired by modern optical physical materials—frosted mineral glass, multi-layer optical refraction, dynamic light-lifting, and specular sheen—harmoniously blended with LofiQ's signature lo-fi aesthetic and brand palette.

### 1.1 Non-Negotiable Rules
1. **Material Depth over Flat Translucency:** A glass surface must never look like an ordinary translucent card or gray film. It must exhibit subtle specular rim lighting on its upper perimeter, soft bottom edge light falloff, continuous squircle curvature, and genuine backdrop sampling.
2. **Neutral Substrate with Brand Vibrancy:** The glass material substrate remains deep, neutral, and translucent (`0xFF101016` to `0xFF161622` with 55%–85% opacity). Brand colors are applied deliberately:
   - **Tangerine (`#EB7D00`):** Active selection indicators, play buttons, active switch thumbs, live seekbar heads, and glowing progress indicators.
   - **Vanilla (`#EBE3A7`):** Warm subtle labels, secondary highlight badges, and vintage analog accent meters.
   - **Brunswick Green (`#2C5745`):** Offline status, local storage confirmation, and secure connectivity badges.
   - **Drab Dark Brown (`#2E2910`):** Warm earthy background warmth when parsing warm album covers.
   - **Deep Foundation (`#070707`):** Near-black true obsidian backdrop providing deep contrast beneath floating glass.
3. **No Code Copying / GPL-3.0 Clean Room Isolation:** All components are written from scratch as original, clean-room LofiQ code adhering to Android and Flutter native idioms.

---

## 2. Material Presets & Hierarchy

Not all surfaces have identical optical density. Surfaces are stratified by their elevation and visual weight:

| Preset Name | Blur Radius | Surface Alpha | Base Substrate | Border Specular Alpha | Primary Use Case |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **1. Light Glass** | 16dp / sigma 16 | 0.40–0.48 | `Color(0xFF20202C)` | 0.35 -> 0.08 | Quick-access pill buttons, chips, filter tags, active segment highlights. |
| **2. Dark Glass** | 20dp / sigma 20 | 0.62–0.68 | `Color(0xFF101018)` | 0.28 -> 0.05 | Standard content cards, track rows, settings tiles, equalizer fader bays. |
| **3. Floating Glass**| 24dp / sigma 24 | 0.72–0.78 | `Color(0xFF0E0E14)` | 0.38 -> 0.08 | Floating bottom navigation bar, floating mini-player capsule, desktop titlebar. |
| **4. Elevated Glass**| 22dp / sigma 22 | 0.70–0.76 | `Color(0xFF181824)` | 0.32 -> 0.06 | Tooltips, contextual dropdown menus, floating action bars, popover panels. |
| **5. Subtle Glass** | 12dp / sigma 12 | 0.20–0.30 | `Color(0xFF1A1A22)` | 0.18 -> 0.04 | Inactive input backgrounds, unselected tab pills, secondary container dividers. |
| **6. Modal Glass** | 30dp / sigma 30 | 0.85–0.90 | `Color(0xFF0B0B10)` | 0.40 -> 0.10 | Bottom sheets, confirmation alerts, update dialogs, playlist creation sheets. |

---

## 3. Continuous Geometry & Corner Tokens

Sharp edges destroy the illusion of fluid liquid glass. All components use continuous squircle corner geometry:

```
+--------------------------------------------------------------+
| Token                  | Radius      | Component Usage       |
+--------------------------------------------------------------+
| RadiusSmall            | 12.0 dp     | Tooltips, mini pills  |
| RadiusControl          | 16.0 dp     | Buttons, switches, eq |
| RadiusCard             | 20.0 dp     | Settings cards, tiles |
| RadiusPanel            | 24.0 dp     | Large containers      |
| RadiusModal            | 28.0 dp     | Dialogs, bottom sheets|
| RadiusCapsule          | 999.0 dp    | Floating nav bars     |
+--------------------------------------------------------------+
```

---

## 4. Optical Lighting System (The Specular Rim)

The hallmark of premium Liquid Glass is the directional specular rim:
- **Upper Rim:** Reflects light falling from the top edge (`Color.White` with 28%–40% alpha, tapering off downwards).
- **Body Sheen:** A subtle linear sheen gradient across the upper 40% of the surface (`Color.White` with 4%–8% alpha).
- **Lower Rim:** Fades out to 4%–6% alpha to let the backdrop breathe naturally.
- **Inner Shadow / Depth:** Soft elevation drop shadow with deep black alpha (`0.45`–`0.70`) ensuring high separation from the ambient background.

```
       Top Edge Highlight (White ~35% alpha)
     +-----------------------------------------+
     | \ \ \  Subtle Inner Sheen (~6%) \ \ \   |
     |                                         |
     |      Neutral Dark Translucent Glass     |
     |                                         |
     |                                         |
     +-----------------------------------------+
       Bottom Edge Falloff (White ~4% alpha)
```

---

## 5. Centralized Component Suite (14 Core Glass Primitives)

Both Android (Compose) and Windows Desktop (Flutter) expose the identical 14-component suite:

### 1. `GlassSurface`
The primary container foundation with configurable `GlassPreset`, border gradient, and optional backdrop blur.

### 2. `GlassCard`
The standard interactive or informational card primitive with continuous 20dp corners, optional hover scale, click feedback, and edge glow.

### 3. `GlassPanel`
Large grouping container (e.g. Settings sections, Equalizer racks) with continuous 24dp corners and header support.

### 4. `GlassButton`
Tactile action button with active Tangerine / Vanilla accents, subtle scale depression on press, and specular border.

### 5. `GlassIconButton`
Circular or squircle icon button with smooth hover highlight and tap haptics.

### 6. `GlassNavigationSurface`
Floating capsule or rail surface with elevated blur (sigma 24+) designed for bottom nav bars and desktop navigation rails.

### 7. `GlassDialog`
Modal alert container with 28dp continuous corners, high blur (sigma 30), deep scrim backdrop, and primary/secondary glass action buttons.

### 8. `GlassBottomSheet`
Modal bottom sheet with continuous top-start/top-end 28dp radius, specular top drag handle, and deep translucent backdrop.

### 9. `GlassSegmentedControl`
Interactive multi-segment selector with an animated sliding glass pill indicator.

### 10. `GlassMenu`
Floating popover menu with elevated glass material, rounded 16dp corners, and crisp divider separators.

### 11. `GlassTooltip`
Subtle, lightweight floating pill with 12dp corners and high contrast text.

### 12. `GlassInput`
Translucent text field container with inner specular reflection, focused Tangerine border highlight, and clear button.

### 13. `GlassSlider`
Precision audio slider with glowing Tangerine active track, frosted background groove, and tactile thumb indicator.

### 14. `GlassSwitch`
Smooth toggle switch featuring a frosted glass pill track and a glowing tactile thumb that springs smoothly between on/off states.

---

## 6. Performance & Graceful Fallback Protocol

### Android
- Devices with Android 12+ (API 31+) utilize hardware-accelerated `RenderEffect.createBlurEffect` or Compose `Modifier.blur`.
- When `LocalPerformanceModeEnabled` is TRUE (or on devices with <= 4GB RAM), the rendering pipeline automatically bypasses GPU blur passes and renders hardware-accelerated solid opaque dark cards (`#141418`) with the identical specular rim gradient, guaranteeing a rock-solid 60 FPS.

### Windows Desktop (Flutter)
- Uses `BackdropFilter` with `ImageFilter.blur(sigmaX, sigmaY)`.
- When `PreferencesManager.settings.performanceMode` is enabled, `BackdropFilter` is safely bypassed, falling back to clean semi-opaque decorated containers without GPU frame overhead.

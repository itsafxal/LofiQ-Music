package com.lofiq.data.settings

import com.lofiq.core.model.AudioQuality
import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT,
    AMOLED_BLACK
}

@Serializable
enum class LyricsSourcePreference {
    AUTO,
    LRCLIB_FIRST,
    KUGOU_FIRST,
    EMBEDDED_ONLY
}

@Serializable
data class AudioPlaybackSettings(
    val streamingQuality: AudioQuality = AudioQuality.AUTO,
    val downloadQuality: AudioQuality = AudioQuality.HIGH,
    val crossfadeSeconds: Int = 8, // 0 to 12 seconds (Seamless Play)
    val isGaplessEnabled: Boolean = true,
    val isAutomixEnabled: Boolean = false,
    val isVolumeNormalizationEnabled: Boolean = true,
    val isSmartAutoplayEnabled: Boolean = true,
    val streamOverCellular: Boolean = true
)

@Serializable
data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val isDynamicColorsEnabled: Boolean = true,
    val isLiquidGlassBlurEnabled: Boolean = true,
    val isArtworkMeshGlowEnabled: Boolean = true,
    val isWaveformVisualizerEnabled: Boolean = true
)

@Serializable
data class LyricsDisplaySettings(
    val sourcePreference: LyricsSourcePreference = LyricsSourcePreference.AUTO,
    val isLiveLyricsPreviewEnabled: Boolean = true,
    val isRomanizationEnabled: Boolean = false,
    val isTranslationEnabled: Boolean = false,
    val keepScreenOnInLyrics: Boolean = true,
    val lyricsTextScale: Float = 1.0f
)

@Serializable
data class UserAppSettings(
    val audio: AudioPlaybackSettings = AudioPlaybackSettings(),
    val appearance: AppearanceSettings = AppearanceSettings(),
    val lyrics: LyricsDisplaySettings = LyricsDisplaySettings(),
    val isDiscordPresenceEnabled: Boolean = false,
    val isOfflineModeOnly: Boolean = false
)

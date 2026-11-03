package com.lofiq.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.model.AudioQuality
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider
) {
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(dispatchers.io + SupervisorJob()),
        produceFile = { context.preferencesDataStoreFile("lofiq_settings") }
    )

    private object Keys {
        val STREAMING_QUALITY = stringPreferencesKey("streaming_quality")
        val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
        val CROSSFADE_SECONDS = intPreferencesKey("crossfade_seconds")
        val GAPLESS_ENABLED = booleanPreferencesKey("gapless_enabled")
        val AUTOMIX_ENABLED = booleanPreferencesKey("automix_enabled")
        val VOLUME_NORMALIZATION = booleanPreferencesKey("volume_normalization")
        val SMART_AUTOPLAY = booleanPreferencesKey("smart_autoplay")
        val STREAM_OVER_CELLULAR = booleanPreferencesKey("stream_over_cellular")

        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
        val LIQUID_GLASS_BLUR = booleanPreferencesKey("liquid_glass_blur")
        val ARTWORK_MESH_GLOW = booleanPreferencesKey("artwork_mesh_glow")
        val WAVEFORM_VISUALIZER = booleanPreferencesKey("waveform_visualizer")

        val LYRICS_SOURCE_PREF = stringPreferencesKey("lyrics_source_pref")
        val LIVE_LYRICS_PREVIEW = booleanPreferencesKey("live_lyrics_preview")
        val LYRICS_ROMANIZATION = booleanPreferencesKey("lyrics_romanization")
        val LYRICS_TRANSLATION = booleanPreferencesKey("lyrics_translation")
        val KEEP_SCREEN_ON_LYRICS = booleanPreferencesKey("keep_screen_on_lyrics")
        val LYRICS_TEXT_SCALE = floatPreferencesKey("lyrics_text_scale")

        val DISCORD_PRESENCE = booleanPreferencesKey("discord_presence")
        val OFFLINE_MODE_ONLY = booleanPreferencesKey("offline_mode_only")
    }

    val appSettings: Flow<UserAppSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            UserAppSettings(
                audio = AudioPlaybackSettings(
                    streamingQuality = AudioQuality.fromString(prefs[Keys.STREAMING_QUALITY]),
                    downloadQuality = AudioQuality.fromString(prefs[Keys.DOWNLOAD_QUALITY]),
                    crossfadeSeconds = prefs[Keys.CROSSFADE_SECONDS] ?: 8,
                    isGaplessEnabled = prefs[Keys.GAPLESS_ENABLED] ?: true,
                    isAutomixEnabled = prefs[Keys.AUTOMIX_ENABLED] ?: false,
                    isVolumeNormalizationEnabled = prefs[Keys.VOLUME_NORMALIZATION] ?: true,
                    isSmartAutoplayEnabled = prefs[Keys.SMART_AUTOPLAY] ?: true,
                    streamOverCellular = prefs[Keys.STREAM_OVER_CELLULAR] ?: true
                ),
                appearance = AppearanceSettings(
                    themeMode = try {
                        ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name)
                    } catch (e: Exception) {
                        ThemeMode.DARK
                    },
                    isDynamicColorsEnabled = prefs[Keys.DYNAMIC_COLORS] ?: true,
                    isLiquidGlassBlurEnabled = prefs[Keys.LIQUID_GLASS_BLUR] ?: true,
                    isArtworkMeshGlowEnabled = prefs[Keys.ARTWORK_MESH_GLOW] ?: true,
                    isWaveformVisualizerEnabled = prefs[Keys.WAVEFORM_VISUALIZER] ?: true
                ),
                lyrics = LyricsDisplaySettings(
                    sourcePreference = try {
                        LyricsSourcePreference.valueOf(prefs[Keys.LYRICS_SOURCE_PREF] ?: LyricsSourcePreference.AUTO.name)
                    } catch (e: Exception) {
                        LyricsSourcePreference.AUTO
                    },
                    isLiveLyricsPreviewEnabled = prefs[Keys.LIVE_LYRICS_PREVIEW] ?: true,
                    isRomanizationEnabled = prefs[Keys.LYRICS_ROMANIZATION] ?: false,
                    isTranslationEnabled = prefs[Keys.LYRICS_TRANSLATION] ?: false,
                    keepScreenOnInLyrics = prefs[Keys.KEEP_SCREEN_ON_LYRICS] ?: true,
                    lyricsTextScale = prefs[Keys.LYRICS_TEXT_SCALE] ?: 1.0f
                ),
                isDiscordPresenceEnabled = prefs[Keys.DISCORD_PRESENCE] ?: false,
                isOfflineModeOnly = prefs[Keys.OFFLINE_MODE_ONLY] ?: false
            )
        }

    suspend fun setStreamingQuality(quality: AudioQuality) {
        dataStore.edit { it[Keys.STREAMING_QUALITY] = quality.name }
    }

    suspend fun setCrossfadeSeconds(seconds: Int) {
        dataStore.edit { it[Keys.CROSSFADE_SECONDS] = seconds.coerceIn(0, 12) }
    }

    suspend fun setAutomixEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTOMIX_ENABLED] = enabled }
    }

    suspend fun setSmartAutoplayEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SMART_AUTOPLAY] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setLiquidGlassBlurEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.LIQUID_GLASS_BLUR] = enabled }
    }

    suspend fun setDiscordPresenceEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.DISCORD_PRESENCE] = enabled }
    }

    suspend fun setLyricsSourcePreference(pref: LyricsSourcePreference) {
        dataStore.edit { it[Keys.LYRICS_SOURCE_PREF] = pref.name }
    }
}

package com.lofiq.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.lofiq.core.model.AudioQuality
import com.lofiq.core.model.PlaybackRepeatMode
import com.lofiq.core.model.SeekbarStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LofiQPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { context.preferencesDataStoreFile("lofiq_user_preferences") }
    )

    private object PreferencesKeys {
        val AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        val REPEAT_MODE = stringPreferencesKey("repeat_mode")
        val SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
        val LAST_PLAYED_SONG_ID = stringPreferencesKey("last_played_song_id")
        val LAST_PLAYED_POSITION_MS = longPreferencesKey("last_played_position_ms")
        val EQUALIZER_ENABLED = booleanPreferencesKey("equalizer_enabled")
        val STREAM_OVER_CELLULAR = booleanPreferencesKey("stream_over_cellular")
        val DYNAMIC_BACKGROUND_ENABLED = booleanPreferencesKey("dynamic_background_enabled")
        val LIQUID_GLASS_ENABLED = booleanPreferencesKey("liquid_glass_enabled")
        val FULL_SCREEN_ARTWORK_ENABLED = booleanPreferencesKey("full_screen_artwork_enabled")
        val CROSSFADE_DURATION_SEC = intPreferencesKey("crossfade_duration_sec")
        val PREFETCH_ENABLED = booleanPreferencesKey("prefetch_enabled")
        val YOUTUBE_BANNER_PERMANENTLY_DISMISSED = booleanPreferencesKey("youtube_banner_permanently_dismissed")
        val APP_ICON_THEME = stringPreferencesKey("app_icon_theme")
        val CUSTOM_BRAND_COLOR = stringPreferencesKey("custom_brand_color")
        val SEEKBAR_STYLE = stringPreferencesKey("seekbar_style")
        val PERFORMANCE_MODE_ENABLED = booleanPreferencesKey("performance_mode_enabled")
    }

    val crossfadeDurationSec: Flow<Int> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[PreferencesKeys.CROSSFADE_DURATION_SEC] ?: 8 }

    val prefetchEnabled: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[PreferencesKeys.PREFETCH_ENABLED] ?: true }

    val audioQuality: Flow<AudioQuality> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            AudioQuality.fromString(prefs[PreferencesKeys.AUDIO_QUALITY])
        }

    val repeatMode: Flow<PlaybackRepeatMode> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            val modeStr = prefs[PreferencesKeys.REPEAT_MODE] ?: PlaybackRepeatMode.OFF.name
            try {
                PlaybackRepeatMode.valueOf(modeStr)
            } catch (e: Exception) {
                PlaybackRepeatMode.OFF
            }
        }

    val shuffleEnabled: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[PreferencesKeys.SHUFFLE_ENABLED] ?: false }

    val lastPlayedSongId: Flow<String?> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[PreferencesKeys.LAST_PLAYED_SONG_ID] }

    val lastPlayedPositionMs: Flow<Long> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[PreferencesKeys.LAST_PLAYED_POSITION_MS] ?: 0L }

    val dynamicBackgroundEnabled: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[PreferencesKeys.DYNAMIC_BACKGROUND_ENABLED] ?: true }

    val liquidGlassEnabled: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[PreferencesKeys.LIQUID_GLASS_ENABLED] ?: com.lofiq.core.common.DevicePerformanceManager.shouldDefaultToLiquidGlass(context) }

    val fullScreenArtworkEnabled: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[PreferencesKeys.FULL_SCREEN_ARTWORK_ENABLED] ?: true }

    val isYouTubeBannerPermanentlyDismissed: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[PreferencesKeys.YOUTUBE_BANNER_PERMANENTLY_DISMISSED] ?: false }

    val seekbarStyle: Flow<SeekbarStyle> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            SeekbarStyle.fromString(prefs[PreferencesKeys.SEEKBAR_STYLE])
        }

    val performanceModeEnabled: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            prefs[PreferencesKeys.PERFORMANCE_MODE_ENABLED]
                ?: com.lofiq.core.common.DevicePerformanceManager.shouldDefaultToPerformanceMode(context)
        }

    val appIconTheme: Flow<String> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[PreferencesKeys.APP_ICON_THEME] ?: "purple" }

    val customBrandColor: Flow<String?> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[PreferencesKeys.CUSTOM_BRAND_COLOR] }

    suspend fun setCrossfadeDurationSec(seconds: Int) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.CROSSFADE_DURATION_SEC] = seconds.coerceIn(0, 12)
        }
    }

    suspend fun setPrefetchEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.PREFETCH_ENABLED] = enabled
        }
    }

    suspend fun setAudioQuality(quality: AudioQuality) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.AUDIO_QUALITY] = quality.name
        }
    }

    suspend fun setRepeatMode(mode: PlaybackRepeatMode) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.REPEAT_MODE] = mode.name
        }
    }

    suspend fun setShuffleEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.SHUFFLE_ENABLED] = enabled
        }
    }

    suspend fun saveLastPlayback(songId: String, positionMs: Long) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.LAST_PLAYED_SONG_ID] = songId
            prefs[PreferencesKeys.LAST_PLAYED_POSITION_MS] = positionMs
        }
    }

    suspend fun setDynamicBackgroundEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.DYNAMIC_BACKGROUND_ENABLED] = enabled
        }
    }

    suspend fun setLiquidGlassEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.LIQUID_GLASS_ENABLED] = enabled
        }
    }

    suspend fun setFullScreenArtworkEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.FULL_SCREEN_ARTWORK_ENABLED] = enabled
        }
    }

    suspend fun setYouTubeBannerPermanentlyDismissed(dismissed: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.YOUTUBE_BANNER_PERMANENTLY_DISMISSED] = dismissed
        }
    }

    suspend fun setAppIconTheme(themeId: String) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.APP_ICON_THEME] = themeId
        }
    }

    suspend fun setCustomBrandColor(hex: String?) {
        dataStore.edit { prefs ->
            if (hex != null) {
                prefs[PreferencesKeys.CUSTOM_BRAND_COLOR] = hex
            } else {
                prefs.remove(PreferencesKeys.CUSTOM_BRAND_COLOR)
            }
        }
    }

    suspend fun setSeekbarStyle(style: SeekbarStyle) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.SEEKBAR_STYLE] = style.name
        }
    }

    suspend fun setPerformanceModeEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.PERFORMANCE_MODE_ENABLED] = enabled
        }
    }
}

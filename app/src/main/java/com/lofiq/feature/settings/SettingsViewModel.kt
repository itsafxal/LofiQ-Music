package com.lofiq.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lofiq.core.model.AudioQuality
import com.lofiq.core.update.AppUpdateManager
import com.lofiq.core.update.UpdateInfo
import com.lofiq.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.ui.graphics.Color
import com.lofiq.core.common.AppIconManager
import com.lofiq.core.common.AppIconTheme
import javax.inject.Inject

import com.lofiq.core.model.SeekbarStyle

data class SettingsUiState(
    val audioQuality: AudioQuality = AudioQuality.AUTO,
    val crossfadeSeconds: Int = 8,
    val dynamicBackground: Boolean = true,
    val liquidGlassEnabled: Boolean = false,
    val fullScreenArtworkEnabled: Boolean = true,
    val appIconTheme: AppIconTheme = AppIconTheme.PURPLE,
    val customBrandColorHex: String? = null,
    val seekbarStyle: SeekbarStyle = SeekbarStyle.LOFIQ_WAVE,
    val performanceModeEnabled: Boolean = false,
    val cacheClearedMessage: String? = null,
    val isCheckingUpdate: Boolean = false,
    val isDownloadingUpdate: Boolean = false,
    val downloadProgress: Float = 0f,
    val updateInfo: UpdateInfo? = null,
    val updateError: String? = null,
    val isUpToDate: Boolean = false
) {
    val activeBrandColor: Color
        get() = com.lofiq.ui.theme.AccentWhite
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val appIconManager: AppIconManager,
    val appUpdateManager: AppUpdateManager
) : ViewModel() {

    private val _updateState = MutableStateFlow(
        UpdateStateHolder()
    )

    data class UpdateStateHolder(
        val isChecking: Boolean = false,
        val isDownloading: Boolean = false,
        val downloadProgress: Float = 0f,
        val updateInfo: UpdateInfo? = null,
        val updateError: String? = null,
        val isUpToDate: Boolean = false
    )

    private data class VisualSettingsPack(
        val iconTheme: AppIconTheme,
        val customHex: String?,
        val seekStyle: SeekbarStyle,
        val performanceMode: Boolean,
        val crossfadeSec: Int
    )

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            settingsRepository.audioQuality,
            settingsRepository.dynamicBackground,
            settingsRepository.liquidGlassEnabled,
            settingsRepository.fullScreenArtworkEnabled,
            combine(
                settingsRepository.appIconTheme,
                settingsRepository.customBrandColor,
                settingsRepository.seekbarStyle,
                settingsRepository.performanceModeEnabled,
                settingsRepository.crossfadeDurationSec
            ) { iconTheme, customHex, seekStyle, perfMode, crossfade ->
                VisualSettingsPack(iconTheme, customHex, seekStyle, perfMode, crossfade)
            }
        ) { quality, dynamicBg, liquidGlass, fullScreenArtwork, pack ->
            SettingsUiState(
                audioQuality = quality,
                crossfadeSeconds = pack.crossfadeSec,
                dynamicBackground = dynamicBg,
                liquidGlassEnabled = liquidGlass,
                fullScreenArtworkEnabled = fullScreenArtwork,
                appIconTheme = pack.iconTheme,
                customBrandColorHex = pack.customHex,
                seekbarStyle = pack.seekStyle,
                performanceModeEnabled = pack.performanceMode
            )
        },
        _updateState
    ) { baseState, updateState ->
        baseState.copy(
            isCheckingUpdate = updateState.isChecking,
            isDownloadingUpdate = updateState.isDownloading,
            downloadProgress = updateState.downloadProgress,
            updateInfo = updateState.updateInfo,
            updateError = updateState.updateError,
            isUpToDate = updateState.isUpToDate
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    val currentLauncherIcon: AppIconTheme
        get() = appIconManager.getCurrentActiveIconTheme()

    fun setSeekbarStyle(style: SeekbarStyle) {
        viewModelScope.launch {
            settingsRepository.setSeekbarStyle(style)
        }
    }

    fun setInAppTheme(theme: AppIconTheme) {
        viewModelScope.launch {
            settingsRepository.setCustomBrandColor(theme.hexCode)
            settingsRepository.setAppIconTheme(theme)
        }
    }

    fun applyLauncherIcon(theme: AppIconTheme) {
        viewModelScope.launch {
            settingsRepository.setCustomBrandColor(theme.hexCode)
            settingsRepository.setAppIconTheme(theme)
            appIconManager.setAppIcon(theme, killProcessAfter = true)
        }
    }

    fun setAppIconTheme(theme: AppIconTheme) {
        setInAppTheme(theme)
    }

    fun setCustomBrandColor(color: Color) {
        val r = (color.red * 255).toInt().coerceIn(0, 255)
        val g = (color.green * 255).toInt().coerceIn(0, 255)
        val b = (color.blue * 255).toInt().coerceIn(0, 255)
        val hex = String.format("#%02X%02X%02X", r, g, b)
        val nearestAlias = AppIconTheme.findNearestAlias(color)
        viewModelScope.launch {
            settingsRepository.setCustomBrandColor(hex)
            settingsRepository.setAppIconTheme(nearestAlias)
        }
    }

    fun applyCustomColorAndRestart(color: Color) {
        val r = (color.red * 255).toInt().coerceIn(0, 255)
        val g = (color.green * 255).toInt().coerceIn(0, 255)
        val b = (color.blue * 255).toInt().coerceIn(0, 255)
        val hex = String.format("#%02X%02X%02X", r, g, b)
        val nearestAlias = AppIconTheme.findNearestAlias(color)
        viewModelScope.launch {
            settingsRepository.setCustomBrandColor(hex)
            settingsRepository.setAppIconTheme(nearestAlias)
            appIconManager.setAppIcon(nearestAlias, killProcessAfter = true)
        }
    }

    fun setAudioQuality(quality: AudioQuality) {
        viewModelScope.launch {
            settingsRepository.setAudioQuality(quality)
        }
    }

    fun setCrossfadeSeconds(seconds: Int) {
        viewModelScope.launch {
            settingsRepository.setCrossfadeDurationSec(seconds)
        }
    }

    fun setDynamicBackground(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDynamicBackground(enabled)
        }
    }

    fun setLiquidGlassEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setLiquidGlassEnabled(enabled)
        }
    }

    fun setFullScreenArtworkEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setFullScreenArtworkEnabled(enabled)
        }
    }

    fun setPerformanceModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPerformanceModeEnabled(enabled)
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            settingsRepository.clearCache()
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _updateState.update { it.copy(isChecking = true, updateError = null, isUpToDate = false) }
            val result = appUpdateManager.checkForUpdate()
            result.onSuccess { info ->
                if (info.hasUpdate) {
                    _updateState.update { it.copy(isChecking = false, updateInfo = info, isUpToDate = false) }
                    appUpdateManager.showUpdateAvailableNotification(context, info)
                } else {
                    _updateState.update { it.copy(isChecking = false, updateInfo = null, isUpToDate = true) }
                    appUpdateManager.dismissUpdateNotification(context)
                }
            }.onFailure { error ->
                _updateState.update {
                    it.copy(
                        isChecking = false,
                        updateError = error.localizedMessage ?: "Failed to check for updates",
                        isUpToDate = false
                    )
                }
            }
        }
    }

    fun dismissUpdateDialog() {
        _updateState.update { it.copy(updateInfo = null, isUpToDate = false, updateError = null) }
    }

    fun downloadAndInstallUpdate(context: Context, updateInfo: UpdateInfo) {
        viewModelScope.launch {
            _updateState.update { it.copy(isDownloading = true, downloadProgress = 0f) }
            val result = appUpdateManager.downloadApk(context, updateInfo.apkUrl, updateInfo.checksum) { progress ->
                _updateState.update { it.copy(downloadProgress = progress) }
            }
            result.onSuccess { file ->
                _updateState.update { it.copy(isDownloading = false, updateInfo = null) }
                appUpdateManager.dismissUpdateNotification(context)
                appUpdateManager.clearDismissedUpdate(context)
                appUpdateManager.installApk(context, file)
            }.onFailure { error ->
                _updateState.update {
                    it.copy(
                        isDownloading = false,
                        updateError = "Download failed: ${error.localizedMessage}"
                    )
                }
            }
        }
    }
}

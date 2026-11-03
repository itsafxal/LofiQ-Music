package com.lofiq.feature.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.lofiq.core.common.LofiQLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiagnosticsUiState(
    val isRunning: Boolean = false,
    val runCount: Int = 0,
    val currentStep: String = "Ready to test",
    val latestResult: DiagnosticSuiteResult? = null,
    val runHistory: List<DiagnosticSuiteResult> = emptyList(),
    val liveLogs: List<String> = emptyList(),
    val customTitleInput: String = "",
    val customArtistInput: String = "",
    val appLogSearchQuery: String = "",
    val selectedCategoryFilter: LofiQLogger.Category? = null,
    val onlyErrorsFilter: Boolean = false
)

@UnstableApi
@HiltViewModel
class ResolverDiagnosticsViewModel @Inject constructor(
    private val diagnosticEngine: ResolverDiagnosticEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosticsUiState())
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    // Real-time in-memory app runtime logs from LofiQLogger
    val appRuntimeLogs: StateFlow<List<LofiQLogger.LogEntry>> = LofiQLogger.liveLogs

    fun updateCustomTitle(title: String) {
        _uiState.update { it.copy(customTitleInput = title) }
    }

    fun updateCustomArtist(artist: String) {
        _uiState.update { it.copy(customArtistInput = artist) }
    }

    fun updateAppLogSearchQuery(query: String) {
        _uiState.update { it.copy(appLogSearchQuery = query) }
    }

    fun setCategoryFilter(category: LofiQLogger.Category?) {
        _uiState.update { it.copy(selectedCategoryFilter = category) }
    }

    fun toggleOnlyErrors(onlyErrors: Boolean) {
        _uiState.update { it.copy(onlyErrorsFilter = onlyErrors) }
    }

    fun clearAppRuntimeLogs() {
        LofiQLogger.clearLiveLogs()
    }

    fun runAllTests() {
        if (_uiState.value.isRunning) return
        viewModelScope.launch {
            val nextRunNumber = _uiState.value.runCount + 1
            _uiState.update {
                it.copy(
                    isRunning = true,
                    currentStep = "Starting Diagnostic Suite Run #$nextRunNumber...",
                    liveLogs = emptyList()
                )
            }

            val result = diagnosticEngine.runFullDiagnosticSuite(
                runNumber = nextRunNumber,
                testTracks = DiagnosticTestTrack.DEFAULT_SUITE,
                onLog = { logLine ->
                    _uiState.update { state ->
                        state.copy(
                            currentStep = logLine.substringAfter("] ").take(60),
                            liveLogs = (state.liveLogs + logLine).takeLast(250)
                        )
                    }
                }
            )

            _uiState.update {
                it.copy(
                    isRunning = false,
                    runCount = nextRunNumber,
                    currentStep = "Completed Run #$nextRunNumber",
                    latestResult = result,
                    runHistory = it.runHistory + result,
                    liveLogs = result.logs
                )
            }
        }
    }

    fun runCustomSongTest() {
        val title = _uiState.value.customTitleInput.trim()
        val artist = _uiState.value.customArtistInput.trim()
        if (title.isEmpty()) return
        if (_uiState.value.isRunning) return

        viewModelScope.launch {
            val nextRunNumber = _uiState.value.runCount + 1
            val customTrack = DiagnosticTestTrack(
                id = "custom_${System.currentTimeMillis()}",
                title = title,
                artist = artist
            )

            _uiState.update {
                it.copy(
                    isRunning = true,
                    currentStep = "Testing Custom Song '$title'...",
                    liveLogs = emptyList()
                )
            }

            val result = diagnosticEngine.runFullDiagnosticSuite(
                runNumber = nextRunNumber,
                testTracks = listOf(customTrack),
                onLog = { logLine ->
                    _uiState.update { state ->
                        state.copy(
                            currentStep = logLine.substringAfter("] ").take(60),
                            liveLogs = (state.liveLogs + logLine).takeLast(250)
                        )
                    }
                }
            )

            _uiState.update {
                it.copy(
                    isRunning = false,
                    runCount = nextRunNumber,
                    currentStep = "Custom Song Test Completed",
                    latestResult = result,
                    runHistory = it.runHistory + result,
                    liveLogs = result.logs
                )
            }
        }
    }

    fun clearHistory() {
        _uiState.update {
            it.copy(
                runCount = 0,
                currentStep = "Ready",
                latestResult = null,
                runHistory = emptyList(),
                liveLogs = emptyList()
            )
        }
    }
}

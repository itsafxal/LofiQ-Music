package com.lofiq.data.sources

import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.common.LofiQLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceManager @Inject constructor(
    private val jioSaavnSource: JioSaavnSource,
    private val youTubeMusicSource: YouTubeMusicSource,
    private val lrcLibSource: LrcLibSource,
    private val dispatchers: DispatcherProvider
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)

    val registeredSources: List<MusicSource> = listOf(
        jioSaavnSource,
        youTubeMusicSource,
        lrcLibSource
    )

    private val _healthStatuses = MutableStateFlow<Map<String, SourceHealthStatus>>(emptyMap())
    val healthStatuses: StateFlow<Map<String, SourceHealthStatus>> = _healthStatuses.asStateFlow()

    private val _isCheckingHealth = MutableStateFlow(false)
    val isCheckingHealth: StateFlow<Boolean> = _isCheckingHealth.asStateFlow()

    fun runAllHealthChecks() {
        if (_isCheckingHealth.value) return
        scope.launch {
            _isCheckingHealth.value = true
            LofiQLogger.d(LofiQLogger.Category.NETWORK, "[SOURCES] Running health checks across ${registeredSources.size} sources...")

            val deferreds = registeredSources.map { source ->
                async {
                    _healthStatuses.update { it + (source.id to SourceHealthStatus(state = HealthState.CHECKING)) }
                    val status = source.healthCheck()
                    source.id to status
                }
            }

            val results = deferreds.awaitAll().toMap()
            _healthStatuses.value = results
            _isCheckingHealth.value = false
            LofiQLogger.i(LofiQLogger.Category.NETWORK, "[SOURCES] Completed health checks. All systems evaluated.")
        }
    }

    suspend fun checkSource(sourceId: String): SourceHealthStatus = withContext(dispatchers.io) {
        val source = registeredSources.find { it.id == sourceId }
            ?: return@withContext SourceHealthStatus(state = HealthState.OFFLINE, errorMessage = "Source not found")

        _healthStatuses.update { it + (sourceId to SourceHealthStatus(state = HealthState.CHECKING)) }
        val status = source.healthCheck()
        _healthStatuses.update { it + (sourceId to status) }
        status
    }
}

package com.lofiq.data.sync

import android.content.Context
import com.lofiq.core.common.LofiQLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Diagnostic metrics snapshot for network data consumption, cache efficiency, and background sync health.
 */
data class SyncDiagnostics(
    val bytesTransferred: Long = 0L,
    val httpRequestCount: Long = 0L,
    val cacheHitCount: Long = 0L,
    val cacheMissCount: Long = 0L,
    val lastSuccessfulSyncTimestamp: Long = 0L,
    val skippedUnchangedRecords: Long = 0L,
    val lastSyncDurationMs: Long = 0L,
    val retryCount: Long = 0L,
    val lastFailureReason: String? = null,
    val totalFailures: Long = 0L
) {
    val cacheHitRatio: Float
        get() {
            val total = cacheHitCount + cacheMissCount
            return if (total > 0L) (cacheHitCount.toFloat() / total.toFloat()) else 1.0f
        }

    val formattedDataUsage: String
        get() = when {
            bytesTransferred < 1024 -> "$bytesTransferred B"
            bytesTransferred < 1024 * 1024 -> "%.1f KB".format(bytesTransferred / 1024f)
            else -> "%.2f MB".format(bytesTransferred / (1024f * 1024f))
        }
}

/**
 * Thread-safe singleton tracker recording network requests, byte volume, cache hit/miss ratio,
 * and sync operation health. Persists aggregated metrics to SharedPreferences across application cycles.
 */
@Singleton
class SyncDiagnosticsTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _diagnostics = MutableStateFlow(
        SyncDiagnostics(
            bytesTransferred = prefs.getLong(KEY_BYTES, 0L),
            httpRequestCount = prefs.getLong(KEY_HTTP_REQUESTS, 0L),
            cacheHitCount = prefs.getLong(KEY_CACHE_HITS, 0L),
            cacheMissCount = prefs.getLong(KEY_CACHE_MISSES, 0L),
            lastSuccessfulSyncTimestamp = prefs.getLong(KEY_LAST_SUCCESS, 0L),
            skippedUnchangedRecords = prefs.getLong(KEY_SKIPPED_RECORDS, 0L),
            lastSyncDurationMs = prefs.getLong(KEY_LAST_DURATION, 0L),
            retryCount = prefs.getLong(KEY_RETRIES, 0L),
            lastFailureReason = prefs.getString(KEY_LAST_FAILURE, null),
            totalFailures = prefs.getLong(KEY_TOTAL_FAILURES, 0L)
        )
    )

    val diagnostics: StateFlow<SyncDiagnostics> = _diagnostics.asStateFlow()

    @Synchronized
    fun recordHttpRequest(bytes: Long = 0L) {
        _diagnostics.update { current ->
            val updated = current.copy(
                httpRequestCount = current.httpRequestCount + 1,
                bytesTransferred = current.bytesTransferred + bytes.coerceAtLeast(0L)
            )
            persistSnapshot(updated)
            updated
        }
    }

    @Synchronized
    fun recordCacheHit() {
        _diagnostics.update { current ->
            val updated = current.copy(cacheHitCount = current.cacheHitCount + 1)
            persistSnapshot(updated)
            updated
        }
    }

    @Synchronized
    fun recordCacheMiss() {
        _diagnostics.update { current ->
            val updated = current.copy(cacheMissCount = current.cacheMissCount + 1)
            persistSnapshot(updated)
            updated
        }
    }

    @Synchronized
    fun recordSyncSuccess(durationMs: Long, skippedRecords: Long = 0L) {
        val now = System.currentTimeMillis()
        _diagnostics.update { current ->
            val updated = current.copy(
                lastSuccessfulSyncTimestamp = now,
                lastSyncDurationMs = durationMs,
                skippedUnchangedRecords = current.skippedUnchangedRecords + skippedRecords.coerceAtLeast(0L),
                lastFailureReason = null
            )
            persistSnapshot(updated)
            updated
        }
        LofiQLogger.i(
            LofiQLogger.Category.CACHE,
            "Sync succeeded in ${durationMs}ms (skipped $skippedRecords unchanged records)"
        )
    }

    @Synchronized
    fun recordSyncFailure(reason: String) {
        _diagnostics.update { current ->
            val updated = current.copy(
                lastFailureReason = reason,
                totalFailures = current.totalFailures + 1
            )
            persistSnapshot(updated)
            updated
        }
        LofiQLogger.w(LofiQLogger.Category.CACHE, "Sync failure recorded: $reason")
    }

    @Synchronized
    fun recordRetry() {
        _diagnostics.update { current ->
            val updated = current.copy(retryCount = current.retryCount + 1)
            persistSnapshot(updated)
            updated
        }
    }

    @Synchronized
    fun resetDiagnostics() {
        val reset = SyncDiagnostics()
        _diagnostics.value = reset
        prefs.edit().clear().apply()
    }

    private fun persistSnapshot(snapshot: SyncDiagnostics) {
        prefs.edit()
            .putLong(KEY_BYTES, snapshot.bytesTransferred)
            .putLong(KEY_HTTP_REQUESTS, snapshot.httpRequestCount)
            .putLong(KEY_CACHE_HITS, snapshot.cacheHitCount)
            .putLong(KEY_CACHE_MISSES, snapshot.cacheMissCount)
            .putLong(KEY_LAST_SUCCESS, snapshot.lastSuccessfulSyncTimestamp)
            .putLong(KEY_SKIPPED_RECORDS, snapshot.skippedUnchangedRecords)
            .putLong(KEY_LAST_DURATION, snapshot.lastSyncDurationMs)
            .putLong(KEY_RETRIES, snapshot.retryCount)
            .putString(KEY_LAST_FAILURE, snapshot.lastFailureReason)
            .putLong(KEY_TOTAL_FAILURES, snapshot.totalFailures)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "lofiq_sync_diagnostics"
        private const val KEY_BYTES = "bytes_transferred"
        private const val KEY_HTTP_REQUESTS = "http_requests"
        private const val KEY_CACHE_HITS = "cache_hits"
        private const val KEY_CACHE_MISSES = "cache_misses"
        private const val KEY_LAST_SUCCESS = "last_success_timestamp"
        private const val KEY_SKIPPED_RECORDS = "skipped_records"
        private const val KEY_LAST_DURATION = "last_duration_ms"
        private const val KEY_RETRIES = "retry_count"
        private const val KEY_LAST_FAILURE = "last_failure_reason"
        private const val KEY_TOTAL_FAILURES = "total_failures"
    }
}

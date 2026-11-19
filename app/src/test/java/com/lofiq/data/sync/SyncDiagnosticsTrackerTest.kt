package com.lofiq.data.sync

import android.content.Context
import android.content.SharedPreferences
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SyncDiagnosticsTrackerTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    private val prefsStore = mutableMapOf<String, Any>()

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockPrefs = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)

        prefsStore.clear()

        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs
        every { mockPrefs.edit() } returns mockEditor

        every { mockPrefs.getLong(any(), any()) } answers {
            val key = firstArg<String>()
            val def = secondArg<Long>()
            (prefsStore[key] as? Long) ?: def
        }
        every { mockPrefs.getString(any(), any()) } answers {
            val key = firstArg<String>()
            val def = secondArg<String?>()
            (prefsStore[key] as? String) ?: def
        }

        every { mockEditor.putLong(any(), any()) } answers {
            val key = firstArg<String>()
            val v = secondArg<Long>()
            prefsStore[key] = v
            mockEditor
        }
        every { mockEditor.putString(any(), any()) } answers {
            val key = firstArg<String>()
            val v = secondArg<String?>()
            if (v != null) prefsStore[key] = v else prefsStore.remove(key)
            mockEditor
        }
        every { mockEditor.apply() } returns Unit
        every { mockEditor.clear() } answers {
            prefsStore.clear()
            mockEditor
        }
    }

    @Test
    fun `recordHttpRequest increments counter and aggregates bytes`() {
        val tracker = SyncDiagnosticsTracker(mockContext)

        tracker.recordHttpRequest(bytes = 1024)
        tracker.recordHttpRequest(bytes = 2048)

        val diag = tracker.diagnostics.value
        assertEquals(2L, diag.httpRequestCount)
        assertEquals(3072L, diag.bytesTransferred)
        assertEquals("3.0 KB", diag.formattedDataUsage)
    }

    @Test
    fun `cache hit ratio calculates accurately`() {
        val tracker = SyncDiagnosticsTracker(mockContext)

        tracker.recordCacheHit()
        tracker.recordCacheHit()
        tracker.recordCacheHit()
        tracker.recordCacheMiss()

        val diag = tracker.diagnostics.value
        assertEquals(3L, diag.cacheHitCount)
        assertEquals(1L, diag.cacheMissCount)
        assertEquals(0.75f, diag.cacheHitRatio, 0.001f)
    }

    @Test
    fun `recordSyncSuccess records duration and updates timestamp`() {
        val tracker = SyncDiagnosticsTracker(mockContext)

        tracker.recordSyncSuccess(durationMs = 450L, skippedRecords = 12L)

        val diag = tracker.diagnostics.value
        assertEquals(450L, diag.lastSyncDurationMs)
        assertEquals(12L, diag.skippedUnchangedRecords)
        assertTrue(diag.lastSuccessfulSyncTimestamp > 0L)
        assertNull(diag.lastFailureReason)
    }

    @Test
    fun `recordSyncFailure logs reason and failure count`() {
        val tracker = SyncDiagnosticsTracker(mockContext)

        tracker.recordSyncFailure("Connection timeout")
        tracker.recordRetry()

        val diag = tracker.diagnostics.value
        assertEquals("Connection timeout", diag.lastFailureReason)
        assertEquals(1L, diag.totalFailures)
        assertEquals(1L, diag.retryCount)
    }
}

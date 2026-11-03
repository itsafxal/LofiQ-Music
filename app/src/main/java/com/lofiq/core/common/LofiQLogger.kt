package com.lofiq.core.common

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicLong

object LofiQLogger {
    private const val BASE_TAG = "LofiQ"
    private const val MAX_LOG_ENTRIES = 1000
    private val idCounter = AtomicLong(0)

    enum class Level {
        DEBUG, INFO, WARN, ERROR
    }

    enum class Category(val prefix: String) {
        APP("[APP]"),
        UI("[UI]"),
        SEARCH("[SEARCH]"),
        RESOLVER("[RESOLVER]"),
        PROVIDER("[PROVIDER]"),
        PREFETCH("[PREFETCH]"),
        CACHE("[CACHE]"),
        PLAYBACK("[PLAYBACK]"),
        PLAYER("[PLAYER]"),
        NETWORK("[NETWORK]"),
        DATABASE("[DATABASE]"),
        ERROR("[ERROR]")
    }

    data class LogEntry(
        val id: Long,
        val timestamp: String,
        val category: Category,
        val level: Level,
        val message: String,
        val throwable: String? = null
    ) {
        fun formattedText(): String {
            val err = if (throwable != null) "\nException: $throwable" else ""
            return "[$timestamp] ${level.name} ${category.prefix} $message$err"
        }
    }

    private val _liveLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val liveLogs: StateFlow<List<LogEntry>> = _liveLogs.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private fun addEntry(category: Category, level: Level, message: String, throwable: Throwable? = null) {
        val entry = LogEntry(
            id = idCounter.incrementAndGet(),
            timestamp = timeFormat.format(Date()),
            category = category,
            level = level,
            message = message,
            throwable = throwable?.stackTraceToString()
        )
        synchronized(_liveLogs) {
            val current = _liveLogs.value
            val next = if (current.size >= MAX_LOG_ENTRIES) {
                current.drop(current.size - MAX_LOG_ENTRIES + 1) + entry
            } else {
                current + entry
            }
            _liveLogs.value = next
        }
    }

    fun clearLiveLogs() {
        synchronized(_liveLogs) {
            _liveLogs.value = emptyList()
        }
    }

    fun d(category: Category, message: String) {
        addEntry(category, Level.DEBUG, message)
        try {
            Log.d(BASE_TAG, "${category.prefix} $message")
        } catch (e: Exception) {
            println("$BASE_TAG: ${category.prefix} $message")
        }
    }

    fun i(category: Category, message: String) {
        addEntry(category, Level.INFO, message)
        try {
            Log.i(BASE_TAG, "${category.prefix} $message")
        } catch (e: Exception) {
            println("$BASE_TAG: ${category.prefix} $message")
        }
    }

    fun w(category: Category, message: String, throwable: Throwable? = null) {
        addEntry(category, Level.WARN, message, throwable)
        try {
            Log.w(BASE_TAG, "${category.prefix} $message", throwable)
        } catch (e: Exception) {
            println("$BASE_TAG: [WARN] ${category.prefix} $message: ${throwable?.message}")
        }
    }

    fun e(category: Category, message: String, throwable: Throwable? = null) {
        addEntry(category, Level.ERROR, message, throwable)
        try {
            Log.e(BASE_TAG, "${category.prefix} $message", throwable)
        } catch (e: Exception) {
            System.err.println("$BASE_TAG: [ERROR] ${category.prefix} $message: ${throwable?.message}")
        }
    }
}

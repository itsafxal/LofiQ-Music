package com.lofiq.playback.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors screen ON / OFF transitions to detect background display-off states
 * and deliver immediate notification to diagnostic listeners.
 */
@Singleton
class ScreenStateMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val diagnosticLogger: PlaybackDiagnosticLogger
) {
    private val listeners = CopyOnWriteArrayList<(isScreenOn: Boolean) -> Unit>()
    private var isRegistered = false

    private val powerManager by lazy {
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    }

    val isScreenInteractive: Boolean
        get() = powerManager?.isInteractive ?: true

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    diagnosticLogger.logScreenState(isInteractive = true)
                    notifyListeners(isScreenOn = true)
                }
                Intent.ACTION_SCREEN_OFF -> {
                    diagnosticLogger.logScreenState(isInteractive = false)
                    notifyListeners(isScreenOn = false)
                }
            }
        }
    }

    fun startMonitoring() {
        if (isRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        runCatching {
            context.registerReceiver(screenReceiver, filter)
            isRegistered = true
            diagnosticLogger.logScreenState(isScreenInteractive)
        }
    }

    fun stopMonitoring() {
        if (!isRegistered) return
        runCatching {
            context.unregisterReceiver(screenReceiver)
            isRegistered = false
        }
    }

    fun addListener(listener: (isScreenOn: Boolean) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (isScreenOn: Boolean) -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners(isScreenOn: Boolean) {
        for (listener in listeners) {
            runCatching { listener(isScreenOn) }
        }
    }
}

package com.lofiq.playback.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import com.lofiq.core.common.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Authoritative, singleton Audio Focus manager for LofiQ.
 *
 * Responsibilities:
 * 1. Holds a single, continuous system AudioFocus grant for the application.
 * 2. Prevents dual ExoPlayers from abandoning or stealing audio focus from each other during
 *    crossfade overlap and atomic handover.
 * 3. Handles external audio interruptions (phone calls, navigation prompts, transient focus loss)
 *    and audio becoming noisy (headphone disconnection).
 * 4. Ensures audio focus remains permanently active during screen-off track transitions.
 */
@Singleton
class PlaybackAudioFocusManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val diagnosticLogger: PlaybackDiagnosticLogger
) {
    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private val managerScope = CoroutineScope(SupervisorJob() + dispatchers.main)

    var onPausePlayback: (() -> Unit)? = null
    var onResumePlayback: (() -> Unit)? = null
    var onDuckVolume: ((scale: Float) -> Unit)? = null

    private var hasFocus = false
    private var pausedByTransientLoss = false
    private var isNoisyReceiverRegistered = false

    val isAudioFocusHeld: Boolean
        get() = hasFocus

    private val audioAttributes by lazy {
        runCatching {
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        }.getOrNull()
    }

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        managerScope.launch {
            handleAudioFocusChange(focusChange)
        }
    }

    private var audioFocusRequest: AudioFocusRequest? = null

    private fun getOrCreateFocusRequest(): AudioFocusRequest? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest == null) {
                audioFocusRequest = runCatching {
                    val builder = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAcceptsDelayedFocusGain(true)
                        .setOnAudioFocusChangeListener(focusChangeListener)
                    audioAttributes?.let { builder.setAudioAttributes(it) }
                    builder.build()
                }.getOrNull()
            }
        }
        return audioFocusRequest
    }

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                diagnosticLogger.logAudioFocus("NOISY_BECOMING", "Headphones/Bluetooth disconnected -> pausing")
                onPausePlayback?.invoke()
            }
        }
    }

    private fun registerNoisyReceiver() {
        if (!isNoisyReceiverRegistered) {
            runCatching {
                context.registerReceiver(
                    becomingNoisyReceiver,
                    IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                )
                isNoisyReceiverRegistered = true
            }
        }
    }

    private fun unregisterNoisyReceiver() {
        if (isNoisyReceiverRegistered) {
            runCatching {
                context.unregisterReceiver(becomingNoisyReceiver)
                isNoisyReceiverRegistered = false
            }
        }
    }

    /**
     * Requests primary audio focus from the Android system.
     * Safe to call multiple times (idempotent if already held).
     */
    fun requestAudioFocus(): Boolean {
        if (hasFocus) {
            diagnosticLogger.logAudioFocus("ALREADY_HELD", "GRANTED")
            return true
        }

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getOrCreateFocusRequest()?.let { audioManager.requestAudioFocus(it) }
                ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }

        hasFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        diagnosticLogger.logAudioFocus(
            "REQUEST",
            if (hasFocus) "GRANTED" else "FAILED ($result)",
            "SDK=${Build.VERSION.SDK_INT}"
        )

        if (hasFocus) {
            pausedByTransientLoss = false
            registerNoisyReceiver()
        }
        return hasFocus
    }

    /**
     * Verifies that audio focus remains held. If not held, requests it immediately.
     */
    fun ensureAudioFocus(): Boolean {
        return if (hasFocus) true else requestAudioFocus()
    }

    /**
     * Abandons audio focus only when playback is explicitly paused or stopped by the user.
     * Never called during internal player handover or crossfade.
     */
    fun abandonAudioFocus() {
        if (!hasFocus) return
        unregisterNoisyReceiver()

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getOrCreateFocusRequest()?.let { audioManager.abandonAudioFocusRequest(it) }
                ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }

        hasFocus = false
        pausedByTransientLoss = false
        diagnosticLogger.logAudioFocus("ABANDON", "RESULT=$result")
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                diagnosticLogger.logAudioFocus("FOCUS_CHANGE", "AUDIOFOCUS_GAIN")
                hasFocus = true
                onDuckVolume?.invoke(1.0f)
                if (pausedByTransientLoss) {
                    pausedByTransientLoss = false
                    onResumePlayback?.invoke()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                diagnosticLogger.logAudioFocus("FOCUS_CHANGE", "AUDIOFOCUS_LOSS -> pausing permanently")
                hasFocus = false
                pausedByTransientLoss = false
                unregisterNoisyReceiver()
                onPausePlayback?.invoke()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                diagnosticLogger.logAudioFocus("FOCUS_CHANGE", "AUDIOFOCUS_LOSS_TRANSIENT -> pausing temporarily")
                hasFocus = false
                pausedByTransientLoss = true
                onPausePlayback?.invoke()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                diagnosticLogger.logAudioFocus("FOCUS_CHANGE", "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> ducking volume to 20%")
                onDuckVolume?.invoke(0.2f)
            }
        }
    }
}

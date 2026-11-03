package com.lofiq.feature.equalizer

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lofiq.core.common.LofiQLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class EqPreset(
    val name: String,
    val bandLevels: List<Int> // Level in millibels (-1000 to +1000 typically)
)

@Singleton
class AudioEffectsController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    var isEnabled by mutableStateOf(false)
        private set

    var bassBoostStrength by mutableFloatStateOf(0f) // 0.0 to 1.0
        private set

    var virtualizerStrength by mutableFloatStateOf(0f) // 0.0 to 1.0
        private set

    var selectedPresetIndex by mutableIntStateOf(0)
        private set

    val bandFrequencies = mutableStateListOf<Int>(60, 230, 910, 3600, 14000) // in Hz
    val bandLevels = mutableStateListOf<Int>(0, 0, 0, 0, 0) // in millibels
    var minLevel by mutableIntStateOf(-1500)
    var maxLevel by mutableIntStateOf(1500)

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var currentSessionId: Int = 0

    val presets = listOf(
        EqPreset("Flat", listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
        EqPreset("Deep Bass", listOf(800, 600, 400, 200, 0, 0, 0, 0, 0, 0)),
        EqPreset("Bass Boost", listOf(600, 400, 200, 100, 0, 0, 0, 0, 100, 200)),
        EqPreset("Club / EDM", listOf(500, 400, 200, 0, -100, 200, 400, 500, 400, 300)),
        EqPreset("Rock", listOf(500, 300, 100, -100, 0, 200, 400, 500, 400, 300)),
        EqPreset("Pop", listOf(-100, 100, 200, 400, 500, 400, 300, 100, -100, -200)),
        EqPreset("Hip Hop", listOf(700, 500, 400, 100, 0, 100, 200, 300, 400, 300)),
        EqPreset("Studio Master", listOf(100, 50, 0, 0, 0, 50, 100, 150, 100, 50)),
        EqPreset("Vocal Boost", listOf(-200, -100, 100, 400, 600, 600, 400, 200, 100, 0)),
        EqPreset("Lofi Warmth", listOf(400, 300, 200, 100, 0, -100, -200, -300, -400, -500)),
        EqPreset("Acoustic", listOf(400, 300, 200, 100, 200, 300, 400, 300, 200, 100)),
        EqPreset("Jazz", listOf(300, 200, 100, 200, 300, 400, 300, 200, 100, 0))
    )

    fun attachSession(sessionId: Int) {
        if (sessionId == 0 || sessionId == currentSessionId) return
        release()
        currentSessionId = sessionId

        try {
            equalizer = Equalizer(0, sessionId).apply {
                enabled = isEnabled
            }
            bassBoost = BassBoost(0, sessionId).apply {
                enabled = isEnabled
                if (strengthSupported) {
                    setStrength((bassBoostStrength * 1000).toInt().toShort())
                }
            }
            virtualizer = Virtualizer(0, sessionId).apply {
                enabled = isEnabled
                if (strengthSupported) {
                    setStrength((virtualizerStrength * 1000).toInt().toShort())
                }
            }

            equalizer?.let { eq ->
                minLevel = eq.bandLevelRange[0].toInt()
                maxLevel = eq.bandLevelRange[1].toInt()
                val numBands = eq.numberOfBands.toInt()

                bandFrequencies.clear()
                bandLevels.clear()

                for (i in 0 until numBands) {
                    bandFrequencies.add(eq.getCenterFreq(i.toShort()) / 1000) // Convert mHz to Hz
                    bandLevels.add(eq.getBandLevel(i.toShort()).toInt())
                }
            }
            LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[DSP] Attached Equalizer & FX to session=$sessionId")
        } catch (e: Exception) {
            LofiQLogger.e(LofiQLogger.Category.PLAYBACK, "[DSP] Failed to attach Equalizer: ${e.message}")
        }
    }

    fun toggleEnabled(enabled: Boolean) {
        isEnabled = enabled
        try {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
            virtualizer?.enabled = enabled
        } catch (e: Exception) {
            LofiQLogger.e(LofiQLogger.Category.PLAYBACK, "[DSP] Toggle FX error: ${e.message}")
        }
    }

    fun setBandLevel(bandIndex: Int, level: Int) {
        if (bandIndex in bandLevels.indices) {
            bandLevels[bandIndex] = level
            try {
                equalizer?.setBandLevel(bandIndex.toShort(), level.toShort())
            } catch (e: Exception) {
                LofiQLogger.e(LofiQLogger.Category.PLAYBACK, "[DSP] Set band level error: ${e.message}")
            }
        }
    }

    fun applyPreset(index: Int) {
        if (index in presets.indices) {
            selectedPresetIndex = index
            val preset = presets[index]
            preset.bandLevels.forEachIndexed { i, lvl ->
                if (i < bandLevels.size) {
                    setBandLevel(i, lvl)
                }
            }
        }
    }

    fun setBassBoost(strength: Float) {
        bassBoostStrength = strength.coerceIn(0f, 1f)
        try {
            bassBoost?.takeIf { it.strengthSupported }?.setStrength((bassBoostStrength * 1000).toInt().toShort())
        } catch (e: Exception) {
            LofiQLogger.e(LofiQLogger.Category.PLAYBACK, "[DSP] Set bass boost error: ${e.message}")
        }
    }

    fun setVirtualizer(strength: Float) {
        virtualizerStrength = strength.coerceIn(0f, 1f)
        try {
            virtualizer?.takeIf { it.strengthSupported }?.setStrength((virtualizerStrength * 1000).toInt().toShort())
        } catch (e: Exception) {
            LofiQLogger.e(LofiQLogger.Category.PLAYBACK, "[DSP] Set virtualizer error: ${e.message}")
        }
    }

    fun release() {
        try {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()
        } catch (e: Exception) {
            // Ignored
        }
        equalizer = null
        bassBoost = null
        virtualizer = null
    }
}

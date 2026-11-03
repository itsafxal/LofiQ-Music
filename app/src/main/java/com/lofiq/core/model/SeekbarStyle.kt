package com.lofiq.core.model

/**
 * Visual styling options for the player seekbar in LofiQ.
 */
enum class SeekbarStyle(
    val label: String,
    val description: String
) {
    LOFIQ_WAVE(
        label = "LofiQ Waveform",
        description = "Signature multi-layer animated sine waves with beat pulse"
    ),
    WAVEFORM(
        label = "Waveform",
        description = "Animated audio visualizer amplitude bars"
    ),
    WAVE_LINE(
        label = "Wave Line",
        description = "Smooth flowing sinusoidal wave line with riding orb"
    ),
    CLASSIC(
        label = "Classic",
        description = "Minimalist sleek track with pill thumb & end gap"
    ),
    DOTS(
        label = "Dots",
        description = "Animated bouncing beads & pulse trail"
    ),
    GRADIENT_BAR(
        label = "Gradient",
        description = "Lush glowing multi-color gradient bar with aura"
    ),
    NEON(
        label = "Neon Glow",
        description = "Cyberpunk high-glow luminous laser neon beam"
    ),
    BLOCKS(
        label = "Blocks",
        description = "Segmented digital matrix tech blocks"
    ),
    MATERIAL(
        label = "Material 3",
        description = "Standard Google Material 3 slider"
    ),
    M3E_WAVY(
        label = "M3 Expressive",
        description = "Android 15 / M3 Expressive wavy progress line"
    );

    val displayName: String
        get() = label

    companion object {
        val DEFAULT = LOFIQ_WAVE

        fun fromString(name: String?): SeekbarStyle {
            if (name == null) return DEFAULT
            return try {
                valueOf(name)
            } catch (_: Exception) {
                DEFAULT
            }
        }
    }
}

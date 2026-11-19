package com.lofiq.core.audio.pipeline

/**
 * Verified stage 1: Source stream and container metadata.
 */
data class TrackInfoStage(
    val sourceProvider: String = "Unknown Provider",
    val qualityBadge: String = "Hi-Fi",
    val codec: String = "—",
    val container: String = "—",
    val bitrateBps: Int = -1,
    val sampleRateHz: Int = -1,
    val channelCount: Int = -1,
    val bitDepth: String = "—",
    val isLossless: Boolean? = null
) {
    val formattedBitrate: String
        get() = when {
            bitrateBps > 0 -> "${bitrateBps / 1000} kbps"
            else -> "Unknown (VBR)"
        }

    val formattedSampleRate: String
        get() = when {
            sampleRateHz > 0 -> "%.1f kHz".format(sampleRateHz / 1000f)
            else -> "—"
        }

    val formattedChannels: String
        get() = when (channelCount) {
            1 -> "Mono (1.0)"
            2 -> "Stereo (2.0)"
            6 -> "Surround (5.1)"
            8 -> "Surround (7.1)"
            else -> if (channelCount > 0) "$channelCount ch" else "—"
        }

    val compressionType: String
        get() = when (isLossless) {
            true -> "Lossless"
            false -> "Lossy Compressed"
            null -> "—"
        }
}

/**
 * Verified stage 2: MediaCodec audio decoder characteristics.
 */
data class DecoderStage(
    val decoderName: String = "—",
    val decoderType: String = "—", // e.g., "Hardware (MediaCodec)", "Software (MediaCodec)"
    val inputMime: String = "—",
    val outputPcmEncoding: String = "—", // e.g., "PCM 16-bit Int", "PCM 32-bit Float"
    val outputSampleRateHz: Int = -1,
    val outputChannels: Int = -1,
    val isHardwareAccelerated: Boolean? = null
) {
    val formattedSampleRate: String
        get() = when {
            outputSampleRateHz > 0 -> "%.1f kHz".format(outputSampleRateHz / 1000f)
            else -> "—"
        }

    val formattedChannels: String
        get() = when (outputChannels) {
            1 -> "Mono (1.0)"
            2 -> "Stereo (2.0)"
            else -> if (outputChannels > 0) "$outputChannels ch" else "—"
        }
}

/**
 * Verified stage 3: Audio resampling / rate conversion status.
 */
data class ResamplerStage(
    val inputSampleRateHz: Int = -1,
    val outputSampleRateHz: Int = -1,
    val isResamplingRequired: Boolean = false,
    val statusText: String = "Native / Direct",
    val implementationDetails: String = "Android AudioTrack Native Mixer"
) {
    val formattedConversion: String
        get() = when {
            inputSampleRateHz > 0 && outputSampleRateHz > 0 && inputSampleRateHz != outputSampleRateHz ->
                "%.1f kHz → %.1f kHz".format(inputSampleRateHz / 1000f, outputSampleRateHz / 1000f)
            outputSampleRateHz > 0 ->
                "Direct Native (%.1f kHz)".format(outputSampleRateHz / 1000f)
            inputSampleRateHz > 0 ->
                "Direct Native (%.1f kHz)".format(inputSampleRateHz / 1000f)
            else -> "—"
        }
}

/**
 * Verified stage 4: DSP and audio effects processing.
 */
data class DspStage(
    val isEqEnabled: Boolean = false,
    val activePresetName: String? = null,
    val bassBoostPercent: Int = 0,
    val virtualizerPercent: Int = 0,
    val playbackSpeed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val activeEffectsSummary: String = "Bypassed / Flat (Direct Pass-through)"
) {
    val hasActiveEffects: Boolean
        get() = isEqEnabled || bassBoostPercent > 0 || virtualizerPercent > 0 || playbackSpeed != 1.0f || pitch != 1.0f
}

/**
 * Verified stage 5: Hardware audio routing and output sinks.
 */
data class OutputStage(
    val routeType: String = "Built-in Speaker",
    val deviceName: String = "Phone Speaker",
    val isBluetooth: Boolean = false,
    val isHeadphones: Boolean = false,
    val sampleRateHz: Int = -1,
    val channelConfig: String = "Stereo (2.0)",
    val isOffloadActive: Boolean = false,
    val hardwareEncoding: String = "PCM 16-bit"
) {
    val formattedSampleRate: String
        get() = when {
            sampleRateHz > 0 -> "%.1f kHz".format(sampleRateHz / 1000f)
            else -> "48.0 kHz (Native)"
        }
}

/**
 * Complete real-time snapshot of LofiQ's live audio signal pipeline.
 */
data class AudioPipelineInfo(
    val trackId: String = "",
    val trackTitle: String = "No Track Loaded",
    val trackArtist: String = "",
    val isLiveActive: Boolean = false,
    val trackInfo: TrackInfoStage = TrackInfoStage(),
    val decoder: DecoderStage = DecoderStage(),
    val resampler: ResamplerStage = ResamplerStage(),
    val dsp: DspStage = DspStage(),
    val output: OutputStage = OutputStage(),
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)

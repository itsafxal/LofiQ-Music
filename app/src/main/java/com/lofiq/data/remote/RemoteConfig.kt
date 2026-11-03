package com.lofiq.data.remote

import com.lofiq.core.model.Song
import kotlinx.serialization.Serializable

@Serializable
data class DiscordActivity(
    val details: String,
    val state: String,
    val largeImageKey: String = "",
    val largeImageText: String = "",
    val startTimestamp: Long = System.currentTimeMillis(),
    val endTimestamp: Long = 0L
)

@Serializable
data class RemoteConfig(
    val pipedInstances: List<String> = listOf(
        "https://api.piped.private.coffee",
        "https://pipedapi.tokhmi.xyz",
        "https://piped-api.lunar.icu"
    ),
    val in楽曲Instances: List<String> = listOf(
        "https://invidious.nerdvpn.de",
        "https://inv.nadeko.net"
    ),
    val jioSaavnApiUrl: String = "https://www.jiosaavn.com/api.php",
    val lrcLibApiUrl: String = "https://lrclib.net/api",
    val defaultAudioBitrateKbps: Int = 320
)

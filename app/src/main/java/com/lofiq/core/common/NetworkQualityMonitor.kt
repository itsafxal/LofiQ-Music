package com.lofiq.core.common

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.lofiq.core.model.AudioQuality
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkQualityMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Inspects current network capabilities (Wi-Fi, 5G/4G, 3G/2G, Metered, Data Saver)
     * and evaluates the optimal streaming audio quality.
     */
    fun determineNetworkQuality(): AudioQuality {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return AudioQuality.HIGH

        val activeNetwork = connectivityManager.activeNetwork ?: return AudioQuality.DATA_SAVER
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return AudioQuality.DATA_SAVER

        // 1. Check if device has global Data Saver enabled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (connectivityManager.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED) {
                LofiQLogger.d(LofiQLogger.Category.PLAYBACK, "[NETWORK] System Data Saver is active -> Selecting DATA_SAVER")
                return AudioQuality.DATA_SAVER
            }
        }

        // 2. Wi-Fi or Ethernet: high quality by default
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        ) {
            LofiQLogger.d(LofiQLogger.Category.PLAYBACK, "[NETWORK] Unmetered Wi-Fi/Ethernet detected -> Selecting HIGH")
            return AudioQuality.HIGH
        }

        // 3. Cellular network: assess bandwidth & signal tier
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            val downlinkKbps = capabilities.linkDownstreamBandwidthKbps
            val isMetered = connectivityManager.isActiveNetworkMetered

            LofiQLogger.d(
                LofiQLogger.Category.PLAYBACK,
                "[NETWORK] Cellular connection: downlink=${downlinkKbps}kbps metered=$isMetered"
            )

            return when {
                // Very slow connection, weak signal or poor coverage (< 1000 kbps) -> Data Saver (avoids buffering!)
                downlinkKbps in 1..999 -> AudioQuality.DATA_SAVER
                // Moderate 3G/4G (1000 - 3000 kbps) -> Standard
                downlinkKbps in 1000..3000 -> AudioQuality.STANDARD
                // Fast 4G / 5G (> 3000 kbps)
                downlinkKbps > 3000 -> AudioQuality.HIGH
                // Unknown downlink: if metered, prefer standard; otherwise high
                isMetered -> AudioQuality.STANDARD
                else -> AudioQuality.HIGH
            }
        }

        return AudioQuality.HIGH
    }

    /**
     * Resolves the effective concrete quality for the given preference.
     * If user chose AUTO, returns the network-adaptive quality; otherwise returns the explicit choice.
     */
    fun getEffectiveQuality(preference: AudioQuality): AudioQuality {
        return if (preference == AudioQuality.AUTO) {
            determineNetworkQuality()
        } else {
            preference
        }
    }
}

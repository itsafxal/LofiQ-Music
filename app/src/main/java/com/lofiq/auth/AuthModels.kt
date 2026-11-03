package com.lofiq.auth

import kotlinx.serialization.Serializable
import java.security.MessageDigest

@Serializable
data class UserAccount(
    val name: String = "Guest User",
    val email: String? = null,
    val avatarUrl: String? = null,
    val channelId: String? = null,
    val isLoggedIn: Boolean = false
) {
    companion object {
        val GUEST = UserAccount(
            name = "Guest",
            email = null,
            avatarUrl = null,
            channelId = null,
            isLoggedIn = false
        )
    }
}

data class AuthState(
    val account: UserAccount = UserAccount.GUEST,
    val cookies: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface ProviderSyncStatus {
    object Idle : ProviderSyncStatus
    object NotConnected : ProviderSyncStatus
    object Authenticated : ProviderSyncStatus
    object LoadingProfile : ProviderSyncStatus
    data class SyncingLikedSongs(val current: Int = 0, val total: Int = 0) : ProviderSyncStatus
    data class SyncingPlaylists(val current: Int = 0, val total: Int = 0) : ProviderSyncStatus
    data class SyncingPlaylistTracks(val playlistName: String, val current: Int = 0, val total: Int = 0) : ProviderSyncStatus
    data class Completed(val likedCount: Int = 0, val playlistCount: Int = 0, val timestamp: Long = System.currentTimeMillis()) : ProviderSyncStatus
    data class Failed(val error: String, val timestamp: Long = System.currentTimeMillis()) : ProviderSyncStatus
}

object AuthCryptoUtils {
    /**
     * Generates standard YouTube Music SAPISIDHASH Authorization header
     * Format: SAPISIDHASH <timestamp>_<sha1(timestamp + " " + sapisid + " " + origin)>
     */
    fun generateSapisidHash(sapisid: String, origin: String = "https://music.youtube.com"): String {
        val timestamp = System.currentTimeMillis() / 1000
        val cleanSapisid = sapisid.trim().removeSurrounding("\"")
        val input = "$timestamp $cleanSapisid $origin"
        val md = MessageDigest.getInstance("SHA-1")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${timestamp}_$hex"
    }

    /**
     * Extracts SAPISID or __Secure-3PAPISID from a raw Cookie header string
     */
    fun extractSapisid(cookieHeader: String): String? {
        val cookies = cookieHeader.split(";", "\n", "\r").map { it.trim() }
        val sapisidCookie = cookies.firstOrNull { it.startsWith("SAPISID=", ignoreCase = true) }
            ?: cookies.firstOrNull { it.startsWith("__Secure-3PAPISID=", ignoreCase = true) }
            ?: cookies.firstOrNull { it.startsWith("__Secure-1PAPISID=", ignoreCase = true) }
            ?: cookies.firstOrNull { it.startsWith("APISID=", ignoreCase = true) }
            ?: cookies.firstOrNull { it.startsWith("SSID=", ignoreCase = true) }

        return sapisidCookie?.substringAfter("=")?.trim()?.removeSurrounding("\"")?.substringBefore(";")
    }
}

package com.lofiq.core.common

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.lofiq.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verified representation of the currently installed application package.
 */
data class InstalledAppVersion(
    val versionName: String,
    val versionCode: Long,
    val displayVersion: String,
    val displayBuild: String,
    val fullDisplayName: String
)

/**
 * Authoritative, singleton provider for the genuinely installed application version.
 * Reads directly from Android [android.content.pm.PackageManager] to ensure runtime truth
 * rather than relying on compile-time constants or remote server metadata.
 */
@Singleton
class AppVersionProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Resolves the real installed package version from the Android OS.
     */
    fun getInstalledVersion(): InstalledAppVersion {
        return resolveVersion(context)
    }

    val versionName: String
        get() = getInstalledVersion().versionName

    val versionCode: Long
        get() = getInstalledVersion().versionCode

    val displayVersion: String
        get() = getInstalledVersion().displayVersion

    val displayBuild: String
        get() = getInstalledVersion().displayBuild

    val fullDisplayName: String
        get() = getInstalledVersion().fullDisplayName

    companion object {
        /**
         * Static helper to resolve package info safely from any [Context] without DI.
         */
        fun resolveVersion(context: Context): InstalledAppVersion {
            return try {
                val pm = context.packageManager
                val packageInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(context.packageName, 0)
                }
                val vName = packageInfo.versionName ?: BuildConfig.VERSION_NAME
                val vCode = PackageInfoCompat.getLongVersionCode(packageInfo)
                InstalledAppVersion(
                    versionName = vName,
                    versionCode = vCode,
                    displayVersion = "v$vName",
                    displayBuild = "Build $vCode",
                    fullDisplayName = "LofiQ Music v$vName (Build $vCode)"
                )
            } catch (e: Exception) {
                InstalledAppVersion(
                    versionName = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE.toLong(),
                    displayVersion = "v${BuildConfig.VERSION_NAME}",
                    displayBuild = "Build ${BuildConfig.VERSION_CODE}",
                    fullDisplayName = "LofiQ Music v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})"
                )
            }
        }
    }
}

package com.lofiq.core

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import com.lofiq.core.update.AppUpdateManager
import com.lofiq.core.update.UpdateInfo
import io.mockk.*
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class AppUpdateManagerGooglePlayTest {

    private lateinit var mockContext: Context
    private lateinit var mockPackageManager: PackageManager
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var manager: AppUpdateManager

    private val prefsMap = mutableMapOf<String, Any>()

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockPackageManager = mockk(relaxed = true)
        mockPrefs = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)
        okHttpClient = OkHttpClient()

        prefsMap.clear()

        every { mockContext.packageName } returns "com.sargamify.app"
        every { mockContext.packageManager } returns mockPackageManager
        every { mockContext.getSharedPreferences("lofiq_update_prefs", Context.MODE_PRIVATE) } returns mockPrefs
        every { mockPrefs.edit() } returns mockEditor

        every { mockPrefs.getString(any(), any()) } answers {
            val key = firstArg<String>()
            val def = secondArg<String?>()
            (prefsMap[key] as? String) ?: def
        }
        every { mockPrefs.getLong(any(), any()) } answers {
            val key = firstArg<String>()
            val def = secondArg<Long>()
            (prefsMap[key] as? Long) ?: def
        }

        every { mockEditor.putString(any(), any()) } answers {
            val key = firstArg<String>()
            val v = secondArg<String?>()
            if (v != null) prefsMap[key] = v else prefsMap.remove(key)
            mockEditor
        }
        every { mockEditor.putLong(any(), any()) } answers {
            val key = firstArg<String>()
            val v = secondArg<Long>()
            prefsMap[key] = v
            mockEditor
        }
        every { mockEditor.apply() } returns Unit

        manager = AppUpdateManager(okHttpClient)
    }

    @Test
    fun `isInstalledViaGooglePlay returns true when installer package is com_android_vending`() {
        @Suppress("DEPRECATION")
        every { mockPackageManager.getInstallerPackageName("com.sargamify.app") } returns "com.android.vending"

        val isPlay = manager.isInstalledViaGooglePlay(mockContext)
        assertTrue(isPlay)
    }

    @Test
    fun `isInstalledViaGooglePlay returns false when installed directly via APK sideload`() {
        @Suppress("DEPRECATION")
        every { mockPackageManager.getInstallerPackageName("com.sargamify.app") } returns null

        val isPlay = manager.isInstalledViaGooglePlay(mockContext)
        assertFalse(isPlay)
    }

    @Test
    fun `shouldNotifyUpdate suppresses notifications within 24-hour cooldown for same version`() {
        val updateInfo = UpdateInfo(
            versionCode = 70,
            versionName = "1.0.7",
            releaseDate = "2026-11-19",
            apkUrl = "https://lofiq-web.vercel.app/downloads/LofiQ.apk",
            apkSize = "28 MB",
            releaseNotes = listOf("Fresh updates"),
            hasUpdate = true
        )

        // Fresh check - should notify
        assertTrue(manager.shouldNotifyUpdate(mockContext, updateInfo))

        // Record notification shown 2 hours ago
        prefsMap["last_notified_version"] = "1.0.7"
        prefsMap["last_notified_timestamp"] = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2)

        // Should be suppressed due to 24-hour deduplication
        assertFalse(manager.shouldNotifyUpdate(mockContext, updateInfo))

        // After 25 hours have elapsed, should notify again
        prefsMap["last_notified_timestamp"] = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(25)
        assertTrue(manager.shouldNotifyUpdate(mockContext, updateInfo))
    }

    @Test
    fun `shouldNotifyUpdate notifies immediately if newer version differs from previous notification`() {
        prefsMap["last_notified_version"] = "1.0.6"
        prefsMap["last_notified_timestamp"] = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5)

        val newerUpdate = UpdateInfo(
            versionCode = 71,
            versionName = "1.0.7",
            releaseDate = "2026-11-19",
            apkUrl = "https://lofiq-web.vercel.app/downloads/LofiQ.apk",
            apkSize = "28 MB",
            releaseNotes = listOf("New version available"),
            hasUpdate = true
        )

        // Different version -> notifies immediately without waiting for cooldown
        assertTrue(manager.shouldNotifyUpdate(mockContext, newerUpdate))
    }
}

package com.lofiq.core.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.lofiq.core.common.AppVersionProvider
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class AppVersionAndReleaseIntegrityTest {

    private lateinit var mockContext: Context
    private lateinit var mockPackageManager: PackageManager
    private lateinit var appUpdateManager: AppUpdateManager

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockPackageManager = mockk(relaxed = true)
        every { mockContext.packageName } returns "com.sargamify.app"
        every { mockContext.packageManager } returns mockPackageManager

        appUpdateManager = AppUpdateManager(
            okHttpClient = mockk(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `test AppVersionProvider reads installed package info accurately`() {
        val packageInfo = PackageInfo().apply {
            versionName = "1.0.7"
            @Suppress("DEPRECATION")
            versionCode = 70
        }
        every { mockPackageManager.getPackageInfo("com.sargamify.app", any<Int>()) } returns packageInfo

        val version = AppVersionProvider.resolveVersion(mockContext)
        assertEquals("1.0.7", version.versionName)
        assertEquals(70L, version.versionCode)
        assertEquals("v1.0.7", version.displayVersion)
        assertEquals("Build 70", version.displayBuild)
    }

    @Test
    fun `test newer version detection returns update available`() {
        // Installed: v1.0.6 (Build 69), Remote: v1.0.7 (Build 70)
        val isAvailable = appUpdateManager.isUpdateAvailable(
            currentVersionCode = 69,
            currentVersionName = "1.0.6",
            remoteVersionCode = 70,
            remoteVersionName = "1.0.7"
        )
        assertTrue("Newer remote versionCode must trigger updateAvailable", isAvailable)
    }

    @Test
    fun `test same version detection returns up to date`() {
        // Installed: v1.0.7 (Build 70), Remote: v1.0.7 (Build 70)
        val isAvailable = appUpdateManager.isUpdateAvailable(
            currentVersionCode = 70,
            currentVersionName = "1.0.7",
            remoteVersionCode = 70,
            remoteVersionName = "1.0.7"
        )
        assertFalse("Same remote versionCode must report up to date (no update)", isAvailable)
    }

    @Test
    fun `test older version detection rejects downgrade`() {
        // Installed: v1.0.7 (Build 70), Remote: v1.0.6 (Build 69)
        val isAvailable = appUpdateManager.isUpdateAvailable(
            currentVersionCode = 70,
            currentVersionName = "1.0.7",
            remoteVersionCode = 69,
            remoteVersionName = "1.0.6"
        )
        assertFalse("Older remote versionCode must be rejected as an update", isAvailable)
    }

    @Test
    fun `test stale downloaded APK matching installed version code is rejected`() {
        // The downloaded APK has versionCode 69, which is <= installed versionCode 69 (or 70)
        val mockApkFile = mockk<File>(relaxed = true)
        every { mockApkFile.absolutePath } returns "/fake/cache/LofiQ.apk"

        val archivePackageInfo = PackageInfo().apply {
            packageName = "com.sargamify.app"
            @Suppress("DEPRECATION")
            versionCode = 69
        }
        every { mockPackageManager.getPackageArchiveInfo("/fake/cache/LofiQ.apk", 0) } returns archivePackageInfo

        // When installed version is 69, an APK with versionCode 69 MUST be rejected to prevent infinite loop
        val isValid = appUpdateManager.verifyApkPackage(
            context = mockContext,
            apkFile = mockApkFile,
            expectedPackageName = "com.sargamify.app",
            expectedVersionCode = 70,
            minVersionCode = 69L
        )
        assertFalse("Downloaded APK with equal or lower version code must fail verification", isValid)
    }

    @Test
    fun `test genuine newer downloaded APK passes verification`() {
        val mockApkFile = mockk<File>(relaxed = true)
        every { mockApkFile.absolutePath } returns "/fake/cache/LofiQ.apk"

        val archivePackageInfo = PackageInfo().apply {
            packageName = "com.sargamify.app"
            @Suppress("DEPRECATION")
            versionCode = 70
        }
        every { mockPackageManager.getPackageArchiveInfo("/fake/cache/LofiQ.apk", 0) } returns archivePackageInfo

        val isValid = appUpdateManager.verifyApkPackage(
            context = mockContext,
            apkFile = mockApkFile,
            expectedPackageName = "com.sargamify.app",
            expectedVersionCode = 70,
            minVersionCode = 69L
        )
        assertTrue("Downloaded APK with higher version code matching expected must pass", isValid)
    }

    @Test
    fun `test foreign package name in downloaded APK is rejected`() {
        val mockApkFile = mockk<File>(relaxed = true)
        every { mockApkFile.absolutePath } returns "/fake/cache/Malicious.apk"

        val archivePackageInfo = PackageInfo().apply {
            packageName = "com.other.app"
            @Suppress("DEPRECATION")
            versionCode = 70
        }
        every { mockPackageManager.getPackageArchiveInfo("/fake/cache/Malicious.apk", 0) } returns archivePackageInfo

        val isValid = appUpdateManager.verifyApkPackage(
            context = mockContext,
            apkFile = mockApkFile,
            expectedPackageName = "com.sargamify.app",
            expectedVersionCode = 70
        )
        assertFalse("APK with mismatching package name must be rejected", isValid)
    }

    @Test
    fun `test semantic version comparator handles standard and edge formats`() {
        assertEquals(1, appUpdateManager.compareSemanticVersions("1.0.7", "1.0.6"))
        assertEquals(0, appUpdateManager.compareSemanticVersions("1.0.7", "1.0.7"))
        assertEquals(-1, appUpdateManager.compareSemanticVersions("1.0.6", "1.0.7"))
        assertEquals(1, appUpdateManager.compareSemanticVersions("1.1.0", "1.0.9"))
        assertEquals(0, appUpdateManager.compareSemanticVersions("1.0.7-beta", "1.0.7"))
    }
}

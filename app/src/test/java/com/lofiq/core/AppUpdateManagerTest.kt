package com.lofiq.core

import com.lofiq.core.update.AppUpdateManager
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppUpdateManagerTest {

    private lateinit var appUpdateManager: AppUpdateManager

    @Before
    fun setUp() {
        appUpdateManager = AppUpdateManager(OkHttpClient())
    }

    @Test
    fun `compareSemanticVersions correctly evaluates versions`() {
        // Equal
        assertEquals(0, appUpdateManager.compareSemanticVersions("1.2.0", "1.2.0"))
        assertEquals(0, appUpdateManager.compareSemanticVersions("v1.2.0", "1.2.0"))

        // v1 > v2
        assertTrue(appUpdateManager.compareSemanticVersions("1.2.1", "1.2.0") > 0)
        assertTrue(appUpdateManager.compareSemanticVersions("2.0.0", "1.2.0") > 0)
        assertTrue(appUpdateManager.compareSemanticVersions("1.3.0", "1.2.9") > 0)

        // v1 < v2
        assertTrue(appUpdateManager.compareSemanticVersions("1.1.1", "1.2.0") < 0)
        assertTrue(appUpdateManager.compareSemanticVersions("1.2.0", "2.0.0") < 0)
    }

    @Test
    fun `isUpdateAvailable returns false when already on identical version`() {
        // User is running 1.2.0, server has 1.2.0 -> no update
        val hasUpdate = appUpdateManager.isUpdateAvailable(
            currentVersionCode = 3,
            currentVersionName = "1.2.0",
            remoteVersionCode = 3,
            remoteVersionName = "1.2.0"
        )
        assertFalse(hasUpdate)
    }

    @Test
    fun `isUpdateAvailable returns true when current version is same name but remote code was bumped`() {
        // User installed 1.2.0 build 2, server has hotfix 1.2.0 build 3 -> update available
        val hasUpdate = appUpdateManager.isUpdateAvailable(
            currentVersionCode = 2,
            currentVersionName = "1.2.0",
            remoteVersionCode = 3,
            remoteVersionName = "1.2.0"
        )
        assertTrue(hasUpdate)
    }

    @Test
    fun `isUpdateAvailable returns true when genuinely new update is released`() {
        // User on 1.1.1, server has 1.2.0
        val hasUpdate1 = appUpdateManager.isUpdateAvailable(
            currentVersionCode = 2,
            currentVersionName = "1.1.1",
            remoteVersionCode = 3,
            remoteVersionName = "1.2.0"
        )
        assertTrue(hasUpdate1)

        // User on 1.2.0, server has 2.0.0
        val hasUpdate2 = appUpdateManager.isUpdateAvailable(
            currentVersionCode = 3,
            currentVersionName = "1.2.0",
            remoteVersionCode = 4,
            remoteVersionName = "2.0.0"
        )
        assertTrue(hasUpdate2)
    }

    @Test
    fun `isUpdateAvailable returns false when user has newer build than server`() {
        // User on 2.0.0, server still has 1.2.0
        val hasUpdate = appUpdateManager.isUpdateAvailable(
            currentVersionCode = 4,
            currentVersionName = "2.0.0",
            remoteVersionCode = 3,
            remoteVersionName = "1.2.0"
        )
        assertFalse(hasUpdate)
    }

    @Test
    fun `WORK_NAME_PERIODIC_UPDATE is defined with correct unique work identifier`() {
        assertEquals("lofiq_periodic_update_check", AppUpdateManager.WORK_NAME_PERIODIC_UPDATE)
    }
}

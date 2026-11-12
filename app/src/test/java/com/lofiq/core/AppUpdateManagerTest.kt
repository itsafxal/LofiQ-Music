package com.lofiq.core

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.util.Log
import com.lofiq.core.update.AppUpdateManager
import com.lofiq.core.update.EndpointType
import com.lofiq.core.update.FailureCategory
import com.lofiq.core.update.HttpException
import com.lofiq.core.update.MetadataValidationException
import com.lofiq.core.update.SanitizedUpdateException
import com.lofiq.core.update.UpdateEndpoint
import com.lofiq.core.update.UpdateInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.file.Files
import java.security.MessageDigest
import javax.net.ssl.SSLException

class AppUpdateManagerTest {

    private lateinit var tempDir: File
    private lateinit var mockContext: Context
    private lateinit var mockPackageManager: PackageManager

    private val sampleChecksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

    private val validPrimaryJson = """
        {
            "versionCode": 65,
            "versionName": "1.0.2",
            "releaseDate": "2026-11-03",
            "apkUrl": "https://lofiq-web.vercel.app/downloads/LofiQ.apk",
            "apkSize": "25 MB",
            "checksum": "$sampleChecksum",
            "releaseNotes": ["Test release notes primary"]
        }
    """.trimIndent()

    private val validSecondaryJson = """
        {
            "versionCode": 65,
            "versionName": "1.0.2",
            "releaseDate": "2026-11-03",
            "apkUrl": "https://mybackuplofiq.netlify.app/downloads/LofiQ.apk",
            "apkSize": "25 MB",
            "checksum": "$sampleChecksum",
            "releaseNotes": ["Test release notes secondary"]
        }
    """.trimIndent()

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0

        tempDir = Files.createTempDirectory("lofiq_update_test").toFile()
        mockContext = mockk(relaxed = true)
        mockPackageManager = mockk(relaxed = true)

        every { mockContext.cacheDir } returns tempDir
        every { mockContext.packageName } returns "com.sargamify.app"
        every { mockContext.packageManager } returns mockPackageManager
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
        unmockkAll()
    }

    private fun createMockClient(handler: (Request) -> Response): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain -> handler(chain.request()) })
            .build()
    }

    private fun jsonResponse(request: Request, json: String, code: Int = 200, message: String = "OK"): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(message)
            .body(json.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private fun byteResponse(request: Request, bytes: ByteArray, code: Int = 200, message: String = "OK"): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(message)
            .header("Content-Length", bytes.size.toString())
            .body(bytes.toResponseBody("application/vnd.android.package-archive".toMediaType()))
            .build()
    }

    // ==========================================
    // 1. Version Comparison & Availability Tests
    // ==========================================

    @Test
    fun `compareSemanticVersions correctly evaluates versions`() {
        val manager = AppUpdateManager(OkHttpClient())
        assertEquals(0, manager.compareSemanticVersions("1.2.0", "1.2.0"))
        assertEquals(0, manager.compareSemanticVersions("v1.2.0", "1.2.0"))
        assertEquals(0, manager.compareSemanticVersions("V1.0.1", "1.0.1"))

        assertTrue(manager.compareSemanticVersions("1.2.1", "1.2.0") > 0)
        assertTrue(manager.compareSemanticVersions("2.0.0", "1.2.0") > 0)
        assertTrue(manager.compareSemanticVersions("1.3.0", "1.2.9") > 0)

        assertTrue(manager.compareSemanticVersions("1.1.1", "1.2.0") < 0)
        assertTrue(manager.compareSemanticVersions("1.2.0", "2.0.0") < 0)
    }

    @Test
    fun `isUpdateAvailable correctly detects new versions and hotfixes`() {
        val manager = AppUpdateManager(OkHttpClient())

        // Same version, same code -> false
        assertFalse(manager.isUpdateAvailable(64, "1.0.1", 64, "1.0.1"))

        // Hotfix: Same version, bumped code -> true
        assertTrue(manager.isUpdateAvailable(64, "1.0.1", 65, "1.0.1"))

        // Higher version -> true
        assertTrue(manager.isUpdateAvailable(64, "1.0.1", 65, "1.0.2"))

        // User on newer build -> false
        assertFalse(manager.isUpdateAvailable(65, "1.0.2", 64, "1.0.1"))

        // Legacy version string (e.g. 2.5.3 with code 63) vs new version (1.0.2 with code 65) -> true
        assertTrue(manager.isUpdateAvailable(63, "2.5.3", 65, "1.0.2"))

        // Exact match -> false
        assertFalse(manager.isUpdateAvailable(65, "1.0.2", 65, "1.0.2"))

        // Bumped version code -> true
        assertTrue(manager.isUpdateAvailable(65, "1.0.2", 66, "1.0.3"))

        // Lower remote version code -> false
        assertFalse(manager.isUpdateAvailable(65, "1.0.2", 64, "1.0.1"))
    }

    @Test
    fun `WORK_NAME_PERIODIC_UPDATE is defined with correct unique work identifier`() {
        assertEquals("lofiq_periodic_update_check", AppUpdateManager.WORK_NAME_PERIODIC_UPDATE)
    }

    // ==========================================
    // 2. Primary & Fallback In-App Failover Tests
    // ==========================================

    @Test
    fun `primary endpoint success returns update info with PRIMARY endpoint`() = runTest {
        var primaryHit = false
        var secondaryHit = false

        val client = createMockClient { req ->
            val url = req.url.toString()
            if (url.contains("lofiq-web.vercel.app")) {
                primaryHit = true
                jsonResponse(req, validPrimaryJson)
            } else if (url.contains("mybackuplofiq.netlify.app")) {
                secondaryHit = true
                jsonResponse(req, validSecondaryJson)
            } else {
                jsonResponse(req, "{}", 404, "Not Found")
            }
        }

        val manager = AppUpdateManager(client)
        val result = manager.checkForUpdate(currentVersionCode = 65, currentVersionName = "1.0.2")

        assertTrue(result.isSuccess)
        val info = result.getOrThrow()
        assertTrue(primaryHit)
        assertFalse("Secondary should never be invoked when primary succeeds", secondaryHit)
        assertEquals(EndpointType.PRIMARY, info.resolvedEndpoint)
        assertEquals(65, info.versionCode)
        assertEquals("1.0.2", info.versionName)
        assertEquals(sampleChecksum, info.checksum)
    }

    @Test
    fun `primary timeout triggers secondary fallback to Netlify`() = runTest {
        var primaryHit = false
        var secondaryHit = false

        val client = createMockClient { req ->
            val url = req.url.toString()
            if (url.contains("lofiq-web.vercel.app")) {
                primaryHit = true
                throw SocketTimeoutException("Primary connection timed out")
            } else if (url.contains("mybackuplofiq.netlify.app")) {
                secondaryHit = true
                jsonResponse(req, validSecondaryJson)
            } else {
                jsonResponse(req, "{}", 404, "Not Found")
            }
        }

        val manager = AppUpdateManager(client)
        val result = manager.checkForUpdate(currentVersionCode = 65, currentVersionName = "1.0.2")

        assertTrue(result.isSuccess)
        val info = result.getOrThrow()
        assertTrue(primaryHit)
        assertTrue("Secondary should be invoked when primary times out", secondaryHit)
        assertEquals(EndpointType.SECONDARY, info.resolvedEndpoint)
        assertEquals(65, info.versionCode)
        assertEquals("1.0.2", info.versionName)
    }

    @Test
    fun `primary DNS failure triggers secondary fallback to Netlify`() = runTest {
        var primaryHit = false
        var secondaryHit = false

        val client = createMockClient { req ->
            val url = req.url.toString()
            if (url.contains("lofiq-web.vercel.app")) {
                primaryHit = true
                throw UnknownHostException("Unable to resolve host: lofiq-web.vercel.app")
            } else if (url.contains("mybackuplofiq.netlify.app")) {
                secondaryHit = true
                jsonResponse(req, validSecondaryJson)
            } else {
                jsonResponse(req, "{}", 404, "Not Found")
            }
        }

        val manager = AppUpdateManager(client)
        val result = manager.checkForUpdate(currentVersionCode = 65, currentVersionName = "1.0.2")

        assertTrue(result.isSuccess)
        val info = result.getOrThrow()
        assertTrue(primaryHit)
        assertTrue("Secondary should be invoked on primary DNS failure", secondaryHit)
        assertEquals(EndpointType.SECONDARY, info.resolvedEndpoint)
        assertEquals(65, info.versionCode)
    }

    @Test
    fun `primary HTTP 503 triggers secondary fallback to Netlify`() = runTest {
        var primaryHit = false
        var secondaryHit = false

        val client = createMockClient { req ->
            val url = req.url.toString()
            if (url.contains("lofiq-web.vercel.app")) {
                primaryHit = true
                jsonResponse(req, "Service Unavailable", 503, "Service Unavailable")
            } else if (url.contains("mybackuplofiq.netlify.app")) {
                secondaryHit = true
                jsonResponse(req, validSecondaryJson)
            } else {
                jsonResponse(req, "{}", 404, "Not Found")
            }
        }

        val manager = AppUpdateManager(client)
        val result = manager.checkForUpdate(currentVersionCode = 65, currentVersionName = "1.0.2")

        assertTrue(result.isSuccess)
        val info = result.getOrThrow()
        assertTrue(primaryHit)
        assertTrue("Secondary should be invoked on HTTP 503", secondaryHit)
        assertEquals(EndpointType.SECONDARY, info.resolvedEndpoint)
    }

    @Test
    fun `primary failure with stale secondary does not falsely claim app is up to date and leaks no host`() = runTest {
        val staleSecondaryJson = """
            {
                "versionCode": 64,
                "versionName": "1.0.1",
                "releaseDate": "2026-11-03",
                "apkUrl": "https://mybackuplofiq.netlify.app/downloads/LofiQ.apk",
                "apkSize": "21.6 MB",
                "checksum": "$sampleChecksum",
                "releaseNotes": ["Stale backup release notes"]
            }
        """.trimIndent()

        val client = createMockClient { req ->
            val url = req.url.toString()
            if (url.contains("lofiq-web.vercel.app")) {
                throw SocketTimeoutException("Primary timed out")
            } else if (url.contains("mybackuplofiq.netlify.app")) {
                jsonResponse(req, staleSecondaryJson)
            } else {
                jsonResponse(req, "{}", 404, "Not Found")
            }
        }

        val manager = AppUpdateManager(client)
        // Current version is 65 (newer than secondary's 64)
        val result = manager.checkForUpdate(currentVersionCode = 65, currentVersionName = "1.0.2")

        // Must FAIL so that UI does not falsely show "LofiQ is up to date!"
        assertTrue("Check must fail when secondary mirror is stale", result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception is SanitizedUpdateException)
        assertEquals(AppUpdateManager.MSG_ERROR_CONNECTING, exception!!.message)

        // Zero host leakage verification
        val msg = exception.message ?: ""
        assertFalse("Must never expose Netlify hostname in error", msg.contains("netlify", ignoreCase = true))
        assertFalse("Must never expose Vercel hostname in error", msg.contains("vercel", ignoreCase = true))
        assertFalse("Must never expose URLs in error", msg.contains("http://", ignoreCase = true) || msg.contains("https://", ignoreCase = true))
    }

    @Test
    fun `primary failure with newer secondary offers update successfully`() = runTest {
        val newerSecondaryJson = """
            {
                "versionCode": 66,
                "versionName": "1.0.3",
                "releaseDate": "2026-11-04",
                "apkUrl": "https://mybackuplofiq.netlify.app/downloads/LofiQ.apk",
                "apkSize": "22 MB",
                "checksum": "$sampleChecksum",
                "releaseNotes": ["Newer backup release notes"]
            }
        """.trimIndent()

        val client = createMockClient { req ->
            val url = req.url.toString()
            if (url.contains("lofiq-web.vercel.app")) {
                throw SocketTimeoutException("Primary timed out")
            } else if (url.contains("mybackuplofiq.netlify.app")) {
                jsonResponse(req, newerSecondaryJson)
            } else {
                jsonResponse(req, "{}", 404, "Not Found")
            }
        }

        val manager = AppUpdateManager(client)
        val result = manager.checkForUpdate(currentVersionCode = 65, currentVersionName = "1.0.2")

        assertTrue(result.isSuccess)
        val info = result.getOrThrow()
        assertTrue(info.hasUpdate)
        assertEquals(66, info.versionCode)
        assertEquals("1.0.3", info.versionName)
        assertEquals(EndpointType.SECONDARY, info.resolvedEndpoint)
    }

    @Test
    fun `primary HTTP 404 does NOT trigger fallback and fails immediately`() = runTest {
        var primaryHit = false
        var secondaryHit = false

        val client = createMockClient { req ->
            val url = req.url.toString()
            if (url.contains("lofiq-web.vercel.app")) {
                primaryHit = true
                jsonResponse(req, "Not Found", 404, "Not Found")
            } else if (url.contains("mybackuplofiq.netlify.app")) {
                secondaryHit = true
                jsonResponse(req, validSecondaryJson)
            } else {
                jsonResponse(req, "{}", 404, "Not Found")
            }
        }

        val manager = AppUpdateManager(client)
        val result = manager.checkForUpdate()

        assertTrue(result.isFailure)
        assertTrue(primaryHit)
        assertFalse("Secondary must NOT be invoked on HTTP 404 (non-qualifying failure)", secondaryHit)
    }

    @Test
    fun `primary HTTP 403 does NOT trigger fallback and fails immediately`() = runTest {
        var primaryHit = false
        var secondaryHit = false

        val client = createMockClient { req ->
            val url = req.url.toString()
            if (url.contains("lofiq-web.vercel.app")) {
                primaryHit = true
                jsonResponse(req, "Forbidden", 403, "Forbidden")
            } else if (url.contains("mybackuplofiq.netlify.app")) {
                secondaryHit = true
                jsonResponse(req, validSecondaryJson)
            } else {
                jsonResponse(req, "{}", 404, "Not Found")
            }
        }

        val manager = AppUpdateManager(client)
        val result = manager.checkForUpdate()

        assertTrue(result.isFailure)
        assertTrue(primaryHit)
        assertFalse("Secondary must NOT be invoked on HTTP 403 (non-qualifying failure)", secondaryHit)
    }

    @Test
    fun `invalid primary metadata does NOT trigger fallback and fails immediately`() = runTest {
        var primaryHit = false
        var secondaryHit = false

        val client = createMockClient { req ->
            val url = req.url.toString()
            if (url.contains("lofiq-web.vercel.app")) {
                primaryHit = true
                jsonResponse(req, "{ malformed json }")
            } else if (url.contains("mybackuplofiq.netlify.app")) {
                secondaryHit = true
                jsonResponse(req, validSecondaryJson)
            } else {
                jsonResponse(req, "{}", 404, "Not Found")
            }
        }

        val manager = AppUpdateManager(client)
        val result = manager.checkForUpdate()

        assertTrue(result.isFailure)
        assertTrue(primaryHit)
        assertFalse("Secondary must NOT be invoked on malformed metadata (non-qualifying failure)", secondaryHit)
    }

    @Test
    fun `missing versionCode in primary metadata does NOT trigger fallback`() = runTest {
        var secondaryHit = false
        val badJson = """
            {
                "versionName": "1.0.2",
                "checksum": "$sampleChecksum"
            }
        """.trimIndent()

        val client = createMockClient { req ->
            val url = req.url.toString()
            if (url.contains("lofiq-web.vercel.app")) {
                jsonResponse(req, badJson)
            } else if (url.contains("mybackuplofiq.netlify.app")) {
                secondaryHit = true
                jsonResponse(req, validSecondaryJson)
            } else {
                jsonResponse(req, "{}", 404, "Not Found")
            }
        }

        val manager = AppUpdateManager(client)
        val result = manager.checkForUpdate()

        assertTrue(result.isFailure)
        assertFalse("Secondary must NOT be invoked when primary metadata lacks versionCode", secondaryHit)
    }

    // ==========================================
    // 3. Metadata Parsing & Consistency Tests
    // ==========================================

    @Test
    fun `valid backup metadata parses and validates correctly`() {
        val manager = AppUpdateManager(OkHttpClient())
        val info = manager.parseAndValidateMetadata(validSecondaryJson, AppUpdateManager.SECONDARY_ENDPOINT)

        assertEquals(65, info.versionCode)
        assertEquals("1.0.2", info.versionName)
        assertEquals("2026-11-03", info.releaseDate)
        assertEquals("25 MB", info.apkSize)
        assertEquals(sampleChecksum, info.checksum)
        assertEquals(EndpointType.SECONDARY, info.resolvedEndpoint)
        assertEquals(1, info.releaseNotes.size)
        assertEquals("Test release notes secondary", info.releaseNotes[0])
    }

    @Test(expected = MetadataValidationException::class)
    fun `parseAndValidateMetadata throws on missing checksum`() {
        val manager = AppUpdateManager(OkHttpClient())
        val noChecksumJson = """
            {
                "versionCode": 65,
                "versionName": "1.0.2"
            }
        """.trimIndent()
        manager.parseAndValidateMetadata(noChecksumJson, AppUpdateManager.PRIMARY_ENDPOINT)
    }

    @Test(expected = MetadataValidationException::class)
    fun `parseAndValidateMetadata throws on invalid hex checksum`() {
        val manager = AppUpdateManager(OkHttpClient())
        val badHexJson = """
            {
                "versionCode": 65,
                "versionName": "1.0.2",
                "checksum": "not_a_valid_64_hex_string"
            }
        """.trimIndent()
        manager.parseAndValidateMetadata(badHexJson, AppUpdateManager.PRIMARY_ENDPOINT)
    }

    @Test
    fun `metadata consistency check succeeds when attributes match and fails on mismatch`() {
        val manager = AppUpdateManager(OkHttpClient())

        val primaryInfo = manager.parseAndValidateMetadata(validPrimaryJson, AppUpdateManager.PRIMARY_ENDPOINT)
        val secondaryInfo = manager.parseAndValidateMetadata(validSecondaryJson, AppUpdateManager.SECONDARY_ENDPOINT)

        // Matching
        assertTrue(manager.verifyMetadataConsistency(primaryInfo, secondaryInfo))

        // Mismatched version code
        val mismatchedCodeInfo = secondaryInfo.copy(versionCode = 66)
        assertFalse(manager.verifyMetadataConsistency(primaryInfo, mismatchedCodeInfo))

        // Mismatched version name
        val mismatchedNameInfo = secondaryInfo.copy(versionName = "1.0.3")
        assertFalse(manager.verifyMetadataConsistency(primaryInfo, mismatchedNameInfo))

        // Mismatched checksum
        val mismatchedChecksumInfo = secondaryInfo.copy(checksum = "0000000000000000000000000000000000000000000000000000000000000000")
        assertFalse(manager.verifyMetadataConsistency(primaryInfo, mismatchedChecksumInfo))
    }

    // ==========================================
    // 4. APK Integrity & Download Failover Tests
    // ==========================================

    @Test
    fun `corrupted primary APK checksum mismatch reports failure without fallback`() = runTest {
        var secondaryHit = false
        val dummyBytes = "Dummy Corrupted APK Payload".toByteArray()

        val client = createMockClient { req ->
            val url = req.url.toString()
            if (url.contains("lofiq-web.vercel.app")) {
                byteResponse(req, dummyBytes)
            } else if (url.contains("mybackuplofiq.netlify.app")) {
                secondaryHit = true
                byteResponse(req, dummyBytes)
            } else {
                jsonResponse(req, "{}", 404, "Not Found")
            }
        }

        val manager = AppUpdateManager(client)
        // Pass a checksum that definitely does not match dummyBytes
        val result = manager.downloadApk(
            context = mockContext,
            apkUrl = "https://lofiq-web.vercel.app/downloads/LofiQ.apk",
            expectedChecksum = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
            endpointType = EndpointType.PRIMARY
        ) {}

        assertTrue(result.isFailure)
        assertFalse("Secondary download must NOT be attempted when primary fails checksum integrity", secondaryHit)
        val updateFile = File(File(tempDir, "updates"), "LofiQ-latest.apk")
        assertFalse("Corrupted APK file must be deleted on integrity failure", updateFile.exists())
    }

    @Test
    fun `corrupted backup APK checksum mismatch reports failure and deletes file`() = runTest {
        val dummyBytes = "Dummy Corrupted Backup APK Payload".toByteArray()

        val client = createMockClient { req ->
            byteResponse(req, dummyBytes)
        }

        val manager = AppUpdateManager(client)
        val result = manager.downloadApk(
            context = mockContext,
            apkUrl = "https://mybackuplofiq.netlify.app/downloads/LofiQ.apk",
            expectedChecksum = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
            endpointType = EndpointType.SECONDARY
        ) {}

        assertTrue(result.isFailure)
        val updateFile = File(File(tempDir, "updates"), "LofiQ-latest.apk")
        assertFalse("Corrupted backup APK file must be deleted on integrity failure", updateFile.exists())
    }

    @Test
    fun `incomplete download detection deletes file and reports failure`() = runTest {
        val shortBytes = ByteArray(50) { 1 }

        val client = createMockClient { req ->
            // Claims 500 bytes but emits only 50 bytes
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("Content-Length", "500")
                .body(shortBytes.toResponseBody("application/vnd.android.package-archive".toMediaType()))
                .build()
        }

        val manager = AppUpdateManager(client)
        val result = manager.downloadApk(
            context = mockContext,
            apkUrl = "https://lofiq-web.vercel.app/downloads/LofiQ.apk",
            expectedChecksum = null,
            endpointType = EndpointType.PRIMARY
        ) {}

        assertTrue(result.isFailure)
        val updateFile = File(File(tempDir, "updates"), "LofiQ-latest.apk")
        assertFalse("Incomplete APK file must be deleted", updateFile.exists())
    }

    @Test
    fun `primary APK download timeout falls back to secondary APK download`() = runTest {
        var primaryHit = false
        var secondaryHit = false
        val validBytes = "Valid APK Payload".toByteArray()
        val validHash = MessageDigest.getInstance("SHA-256").digest(validBytes).joinToString("") { "%02x".format(it) }

        // Mock valid package info so package verification passes
        val packageInfo = PackageInfo().apply {
            packageName = "com.sargamify.app"
        }
        every { mockPackageManager.getPackageArchiveInfo(any<String>(), any<Int>()) } returns packageInfo

        val client = createMockClient { req ->
            val url = req.url.toString()
            if (url.contains("lofiq-web.vercel.app")) {
                primaryHit = true
                throw SocketTimeoutException("Primary download timed out")
            } else if (url.contains("mybackuplofiq.netlify.app")) {
                secondaryHit = true
                byteResponse(req, validBytes)
            } else {
                jsonResponse(req, "{}", 404, "Not Found")
            }
        }

        val manager = AppUpdateManager(client)
        val result = manager.downloadApk(
            context = mockContext,
            apkUrl = "https://lofiq-web.vercel.app/downloads/LofiQ.apk",
            expectedChecksum = validHash,
            endpointType = EndpointType.PRIMARY
        ) {}

        assertTrue(primaryHit)
        assertTrue("Secondary APK download should be attempted after qualifying primary failure", secondaryHit)
        assertTrue(result.isSuccess)
        val file = result.getOrThrow()
        assertTrue(file.exists())
    }

    @Test
    fun `both endpoints unavailable fails safely without exposing backup URL`() = runTest {
        val client = createMockClient {
            throw SocketTimeoutException("Timed out connecting")
        }

        val manager = AppUpdateManager(client)
        val result = manager.checkForUpdate()

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error is SanitizedUpdateException)

        val message = error?.message.orEmpty()
        assertFalse("Error message must not expose backup domain", message.contains("mybackuplofiq.netlify.app", ignoreCase = true))
        assertFalse("Error message must not expose netlify", message.contains("netlify", ignoreCase = true))
        assertFalse("Error message must not expose vercel", message.contains("vercel", ignoreCase = true))
        assertFalse("Error message must not contain raw URLs", message.contains("http://", ignoreCase = true) || message.contains("https://", ignoreCase = true))
    }

    // ==========================================
    // 5. Package Identity & Security Tests
    // ==========================================

    @Test
    fun `verifyApkPackage validates matching package and rejects mismatched package`() {
        val manager = AppUpdateManager(OkHttpClient())
        val dummyFile = File(tempDir, "test.apk").apply { writeText("dummy") }

        // Valid match
        val validPackageInfo = PackageInfo().apply {
            packageName = "com.sargamify.app"
            @Suppress("DEPRECATION")
            versionCode = 65
        }
        every { mockPackageManager.getPackageArchiveInfo(dummyFile.absolutePath, 0) } returns validPackageInfo
        assertTrue(manager.verifyApkPackage(mockContext, dummyFile, "com.sargamify.app", 65))

        // Mismatched package name
        val mismatchedPackageInfo = PackageInfo().apply {
            packageName = "com.malicious.fakeapp"
            @Suppress("DEPRECATION")
            versionCode = 65
        }
        every { mockPackageManager.getPackageArchiveInfo(dummyFile.absolutePath, 0) } returns mismatchedPackageInfo
        assertFalse(manager.verifyApkPackage(mockContext, dummyFile, "com.sargamify.app", 65))

        // Corrupted archive (returns null archive info)
        every { mockPackageManager.getPackageArchiveInfo(dummyFile.absolutePath, 0) } returns null
        assertFalse(manager.verifyApkPackage(mockContext, dummyFile, "com.sargamify.app", 65))
    }

    // ==========================================
    // 6. User-Facing Error Sanitization Tests
    // ==========================================

    @Test
    fun `user facing error messages NEVER expose backup hostname or URLs`() {
        val manager = AppUpdateManager(OkHttpClient())

        val testExceptions = listOf(
            HttpException(401, "Unauthorized on https://mybackuplofiq.netlify.app/version.json"),
            HttpException(403, "Forbidden on https://mybackuplofiq.netlify.app/version.json"),
            HttpException(404, "Not Found on https://mybackuplofiq.netlify.app/version.json"),
            HttpException(500, "Server Error on https://mybackuplofiq.netlify.app/version.json"),
            HttpException(503, "Service Unavailable on https://mybackuplofiq.netlify.app/version.json"),
            SocketTimeoutException("Timeout connecting to https://mybackuplofiq.netlify.app/version.json"),
            UnknownHostException("Unable to resolve host: mybackuplofiq.netlify.app"),
            ConnectException("Connection refused by mybackuplofiq.netlify.app:443"),
            MetadataValidationException("Malformed JSON at https://mybackuplofiq.netlify.app/version.json"),
            SecurityException("Integrity failed for https://mybackuplofiq.netlify.app/downloads/LofiQ.apk"),
            IOException("Generic transport error from https://mybackuplofiq.netlify.app")
        )

        for (ex in testExceptions) {
            val sanitized = manager.sanitizeException(ex)
            val msg = sanitized.message.orEmpty()
            assertTrue("Sanitized message must not be blank", msg.isNotBlank())
            assertFalse("Must never expose Netlify hostname: $msg", msg.contains("mybackuplofiq.netlify.app", ignoreCase = true))
            assertFalse("Must never mention Netlify: $msg", msg.contains("netlify", ignoreCase = true))
            assertFalse("Must never mention Vercel: $msg", msg.contains("vercel", ignoreCase = true))
            assertFalse("Must never contain URLs: $msg", msg.contains("http://", ignoreCase = true) || msg.contains("https://", ignoreCase = true))
        }
    }

    // ==========================================
    // 7. Failure Classification Policy Tests
    // ==========================================

    @Test
    fun `classifyHttpFailure correctly categorizes 5xx as qualifying and 4xx as non-qualifying`() {
        val manager = AppUpdateManager(OkHttpClient())

        // 5xx qualifying
        val (cat500, qual500) = manager.classifyHttpFailure(500)
        assertEquals(FailureCategory.SERVER_ERROR_5XX, cat500)
        assertTrue(qual500)

        val (cat503, qual503) = manager.classifyHttpFailure(503)
        assertEquals(FailureCategory.SERVER_ERROR_5XX, cat503)
        assertTrue(qual503)

        // 4xx non-qualifying
        val (cat401, qual401) = manager.classifyHttpFailure(401)
        assertEquals(FailureCategory.CLIENT_ERROR_4XX, cat401)
        assertFalse(qual401)

        val (cat403, qual403) = manager.classifyHttpFailure(403)
        assertEquals(FailureCategory.CLIENT_ERROR_4XX, cat403)
        assertFalse(qual403)

        val (cat404, qual404) = manager.classifyHttpFailure(404)
        assertEquals(FailureCategory.CLIENT_ERROR_4XX, cat404)
        assertFalse(qual404)
    }

    @Test
    fun `classifyException correctly categorizes network vs validation errors`() {
        val manager = AppUpdateManager(OkHttpClient())

        // Network / transport qualifying
        assertTrue(manager.classifyException(SocketTimeoutException()).second)
        assertEquals(FailureCategory.TIMEOUT, manager.classifyException(SocketTimeoutException()).first)

        assertTrue(manager.classifyException(UnknownHostException()).second)
        assertEquals(FailureCategory.DNS_FAILURE, manager.classifyException(UnknownHostException()).first)

        assertTrue(manager.classifyException(ConnectException()).second)
        assertEquals(FailureCategory.CONNECTION_REFUSED, manager.classifyException(ConnectException()).first)

        assertTrue(manager.classifyException(SocketException()).second)
        assertEquals(FailureCategory.CONNECTION_RESET, manager.classifyException(SocketException()).first)

        assertTrue(manager.classifyException(SSLException("TLS error")).second)
        assertEquals(FailureCategory.TRANSPORT_TLS_ERROR, manager.classifyException(SSLException("TLS error")).first)

        // Validation / security non-qualifying
        assertFalse(manager.classifyException(MetadataValidationException("bad manifest")).second)
        assertEquals(FailureCategory.METADATA_INVALID, manager.classifyException(MetadataValidationException("bad manifest")).first)

        assertFalse(manager.classifyException(SecurityException("bad hash")).second)
        assertEquals(FailureCategory.INTEGRITY_CHECKSUM_MISMATCH, manager.classifyException(SecurityException("bad hash")).first)
    }

    @Test
    fun `calculateSha256 produces exact digest for files`() {
        val manager = AppUpdateManager(OkHttpClient())
        val testFile = File(tempDir, "sha_test.bin").apply { writeBytes("Hello LofiQ".toByteArray()) }

        val computed = manager.calculateSha256(testFile)
        val expected = MessageDigest.getInstance("SHA-256")
            .digest("Hello LofiQ".toByteArray())
            .joinToString("") { "%02x".format(it) }

        assertEquals(expected, computed)
        assertTrue(manager.verifyChecksum(testFile, expected))
        assertFalse(manager.verifyChecksum(testFile, "0000000000000000000000000000000000000000000000000000000000000000"))
    }
}

package com.lofiq.core.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.lofiq.BuildConfig
import com.lofiq.MainActivity
import com.lofiq.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

/**
 * Distribution endpoint identifier: Primary (Vercel) vs Operational Secondary (Netlify).
 */
enum class EndpointType {
    PRIMARY,
    SECONDARY
}

/**
 * Failure category used for internal structured logging and failure classification.
 */
enum class FailureCategory {
    TIMEOUT,
    DNS_FAILURE,
    CONNECTION_REFUSED,
    CONNECTION_RESET,
    TRANSPORT_TLS_ERROR,
    SERVER_ERROR_5XX,
    CLIENT_ERROR_4XX,
    METADATA_INVALID,
    INTEGRITY_CHECKSUM_MISMATCH,
    CORRUPTED_DOWNLOAD,
    PACKAGE_IDENTITY_MISMATCH,
    METADATA_MISMATCH,
    UNKNOWN
}

/**
 * Centralized release endpoint configuration.
 */
data class UpdateEndpoint(
    val type: EndpointType,
    val metadataUrl: String,
    val defaultApkUrl: String
)

/**
 * Validated release update metadata model.
 */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val releaseDate: String,
    val apkUrl: String,
    val apkSize: String,
    val releaseNotes: List<String>,
    val hasUpdate: Boolean,
    val checksum: String? = null,
    val resolvedEndpoint: EndpointType = EndpointType.PRIMARY
)

/**
 * Thrown when release metadata is missing required attributes or malformed.
 */
class MetadataValidationException(message: String) : Exception(message)

/**
 * HTTP exception encapsulating status code for failure classification.
 */
class HttpException(val code: Int, message: String) : IOException(message)

/**
 * Safe user-facing exception sanitized to prevent internal fallback hostname leakage.
 */
class SanitizedUpdateException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Singleton
class AppUpdateManager @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "AppUpdateManager"
        const val UPDATE_CHANNEL_ID = "lofiq_app_updates"
        const val UPDATE_NOTIFICATION_ID = 4001
        const val EXTRA_SHOW_UPDATE = "extra_show_update"
        const val EXTRA_START_DOWNLOAD = "extra_start_download"
        const val WORK_NAME_PERIODIC_UPDATE = "lofiq_periodic_update_check"
        private const val REQUEST_CODE_UPDATE = 4001
        private const val REQUEST_CODE_DOWNLOAD = 4002

        // Primary: Vercel (The ONLY publicly advertised release portal)
        const val PRIMARY_BASE_URL = "https://lofiq-web.vercel.app"
        const val PRIMARY_UPDATE_ENDPOINT = "$PRIMARY_BASE_URL/version.json"
        const val PRIMARY_DEFAULT_APK_URL = "$PRIMARY_BASE_URL/downloads/LofiQ.apk"

        // Secondary: Netlify (Private operational fallback - NEVER exposed in user UI)
        const val SECONDARY_BASE_URL = "https://mybackuplofiq.netlify.app"
        const val SECONDARY_UPDATE_ENDPOINT = "$SECONDARY_BASE_URL/version.json"
        const val SECONDARY_DEFAULT_APK_URL = "$SECONDARY_BASE_URL/downloads/LofiQ.apk"

        val PRIMARY_ENDPOINT = UpdateEndpoint(
            type = EndpointType.PRIMARY,
            metadataUrl = PRIMARY_UPDATE_ENDPOINT,
            defaultApkUrl = PRIMARY_DEFAULT_APK_URL
        )

        val SECONDARY_ENDPOINT = UpdateEndpoint(
            type = EndpointType.SECONDARY,
            metadataUrl = SECONDARY_UPDATE_ENDPOINT,
            defaultApkUrl = SECONDARY_DEFAULT_APK_URL
        )

        // User-facing neutral message constants (guaranteed free of hostnames/URLs)
        const val MSG_ERROR_CONNECTING = "Unable to connect to the update service. Please check your internet connection."
        const val MSG_ERROR_GENERAL = "Unable to check for updates right now. Please try again later."
        const val MSG_ERROR_DOWNLOAD = "Unable to download the update right now. Please try again later."
        const val MSG_ERROR_INTEGRITY = "Update integrity check failed. The downloaded file appears to be corrupted."
        const val MSG_ERROR_PACKAGE = "Update package verification failed."
    }

    /**
     * Check if a newer version of LofiQ is available.
     * Enforces strict request priority:
     * 1. Query PRIMARY (Vercel) endpoint.
     * 2. If PRIMARY succeeds with valid metadata, use it.
     * 3. If PRIMARY suffers a qualifying failure (timeout, DNS, 5xx), fallback to SECONDARY (Netlify).
     * 4. Non-qualifying failures (401, 403, 404, malformed metadata) fail immediately without fallback.
     */
    suspend fun checkForUpdate(
        customEndpoint: String? = null
    ): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        if (!customEndpoint.isNullOrBlank()) {
            val endpoint = UpdateEndpoint(
                type = EndpointType.PRIMARY,
                metadataUrl = customEndpoint,
                defaultApkUrl = customEndpoint.substringBefore("/version.json") + "/downloads/LofiQ.apk"
            )
            return@withContext fetchMetadataFromEndpoint(endpoint).mapCatching { it }
        }

        val primaryStartTime = System.currentTimeMillis()

        // 1. Always attempt PRIMARY (Vercel) first
        val primaryResult = fetchMetadataFromEndpoint(PRIMARY_ENDPOINT)
        if (primaryResult.isSuccess) {
            val info = primaryResult.getOrThrow()
            logDiagnostic(
                endpoint = EndpointType.PRIMARY,
                requestType = "METADATA_FETCH",
                status = "SUCCESS",
                durationMs = System.currentTimeMillis() - primaryStartTime
            )
            return@withContext Result.success(info)
        }

        // Primary failed — evaluate failure category and fallback eligibility
        val primaryException = primaryResult.exceptionOrNull()!!
        val (category, qualifiesForFallback) = evaluateFailure(primaryException)

        logDiagnostic(
            endpoint = EndpointType.PRIMARY,
            requestType = "METADATA_FETCH",
            status = "FAILURE",
            category = category,
            durationMs = System.currentTimeMillis() - primaryStartTime,
            extra = "Qualifies for fallback: $qualifiesForFallback"
        )

        if (!qualifiesForFallback) {
            // Non-qualifying failure (e.g. 401, 403, 404, invalid metadata): Do NOT fallback
            return@withContext Result.failure(sanitizeException(primaryException))
        }

        // 2. Qualifying failure on Primary -> Initiate automatic failover to SECONDARY (Netlify)
        val secondaryStartTime = System.currentTimeMillis()
        logDiagnostic(
            endpoint = EndpointType.SECONDARY,
            requestType = "METADATA_FETCH",
            status = "FALLBACK_INITIATED",
            category = category,
            extra = "Switching to emergency backup due to primary ${category.name}"
        )

        val secondaryResult = fetchMetadataFromEndpoint(SECONDARY_ENDPOINT)
        if (secondaryResult.isSuccess) {
            val info = secondaryResult.getOrThrow()
            logDiagnostic(
                endpoint = EndpointType.SECONDARY,
                requestType = "METADATA_FETCH",
                status = "SUCCESS",
                durationMs = System.currentTimeMillis() - secondaryStartTime
            )
            return@withContext Result.success(info)
        }

        // Both primary and secondary failed
        val secondaryException = secondaryResult.exceptionOrNull()!!
        val (secCategory, _) = evaluateFailure(secondaryException)
        logDiagnostic(
            endpoint = EndpointType.SECONDARY,
            requestType = "METADATA_FETCH",
            status = "FAILURE",
            category = secCategory,
            durationMs = System.currentTimeMillis() - secondaryStartTime,
            extra = "Both primary and secondary metadata requests failed"
        )

        Result.failure(SanitizedUpdateException(MSG_ERROR_GENERAL, primaryException))
    }

    /**
     * Download the update APK to the app's cache directory with progress reporting.
     * Enforces integrity verification, package identity validation, and automatic
     * failover to the backup APK download endpoint on qualifying primary transport failures.
     */
    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        expectedChecksum: String? = null,
        endpointType: EndpointType = EndpointType.PRIMARY,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val destinationFile = File(updatesDir, "LofiQ-latest.apk")

        val initialUrl = apkUrl
        val fallbackUrl = if (endpointType == EndpointType.PRIMARY && initialUrl.contains("lofiq-web.vercel.app")) {
            initialUrl.replace("lofiq-web.vercel.app", "mybackuplofiq.netlify.app")
        } else if (endpointType == EndpointType.PRIMARY && !initialUrl.contains("mybackuplofiq.netlify.app")) {
            SECONDARY_DEFAULT_APK_URL
        } else {
            null
        }

        val startTime = System.currentTimeMillis()

        // 1. Initial APK download attempt
        val firstAttemptResult = executeApkDownload(
            url = initialUrl,
            destinationFile = destinationFile,
            onProgress = onProgress
        )

        if (firstAttemptResult.isSuccess) {
            // Verify SHA-256 integrity
            if (!expectedChecksum.isNullOrBlank()) {
                val checksumVerified = verifyChecksum(destinationFile, expectedChecksum)
                if (!checksumVerified) {
                    destinationFile.delete()
                    logDiagnostic(
                        endpoint = endpointType,
                        requestType = "APK_DOWNLOAD",
                        status = "FAILURE",
                        category = FailureCategory.INTEGRITY_CHECKSUM_MISMATCH,
                        durationMs = System.currentTimeMillis() - startTime
                    )
                    // Checksum mismatch must NOT fallback to secondary (Section 6)
                    return@withContext Result.failure(SanitizedUpdateException(MSG_ERROR_INTEGRITY))
                }
            }

            // Verify package identity and integrity
            if (!verifyApkPackage(context, destinationFile)) {
                destinationFile.delete()
                logDiagnostic(
                    endpoint = endpointType,
                    requestType = "APK_DOWNLOAD",
                    status = "FAILURE",
                    category = FailureCategory.PACKAGE_IDENTITY_MISMATCH
                )
                return@withContext Result.failure(SanitizedUpdateException(MSG_ERROR_PACKAGE))
            }

            logDiagnostic(
                endpoint = endpointType,
                requestType = "APK_DOWNLOAD",
                status = "SUCCESS",
                durationMs = System.currentTimeMillis() - startTime
            )
            return@withContext Result.success(destinationFile)
        }

        // First attempt failed
        val firstException = firstAttemptResult.exceptionOrNull()!!
        val (category, qualifiesForFallback) = evaluateFailure(firstException)

        logDiagnostic(
            endpoint = endpointType,
            requestType = "APK_DOWNLOAD",
            status = "FAILURE",
            category = category,
            durationMs = System.currentTimeMillis() - startTime,
            extra = "Qualifies for fallback: $qualifiesForFallback"
        )

        // Evaluate whether to fallback to secondary download
        if (!qualifiesForFallback || fallbackUrl == null || endpointType == EndpointType.SECONDARY) {
            destinationFile.delete()
            return@withContext Result.failure(sanitizeException(firstException))
        }

        // 2. Qualifying primary download failure -> Initiate automatic failover to SECONDARY APK download
        val fallbackStartTime = System.currentTimeMillis()
        logDiagnostic(
            endpoint = EndpointType.SECONDARY,
            requestType = "APK_DOWNLOAD",
            status = "FALLBACK_INITIATED",
            category = category,
            extra = "Switching to emergency backup APK download"
        )

        val fallbackResult = executeApkDownload(
            url = fallbackUrl,
            destinationFile = destinationFile,
            onProgress = onProgress
        )

        if (fallbackResult.isSuccess) {
            // Verify SHA-256 integrity on fallback download
            if (!expectedChecksum.isNullOrBlank()) {
                val checksumVerified = verifyChecksum(destinationFile, expectedChecksum)
                if (!checksumVerified) {
                    destinationFile.delete()
                    logDiagnostic(
                        endpoint = EndpointType.SECONDARY,
                        requestType = "APK_DOWNLOAD",
                        status = "FAILURE",
                        category = FailureCategory.INTEGRITY_CHECKSUM_MISMATCH,
                        durationMs = System.currentTimeMillis() - fallbackStartTime
                    )
                    return@withContext Result.failure(SanitizedUpdateException(MSG_ERROR_INTEGRITY))
                }
            }

            // Verify package identity on fallback download
            if (!verifyApkPackage(context, destinationFile)) {
                destinationFile.delete()
                logDiagnostic(
                    endpoint = EndpointType.SECONDARY,
                    requestType = "APK_DOWNLOAD",
                    status = "FAILURE",
                    category = FailureCategory.PACKAGE_IDENTITY_MISMATCH
                )
                return@withContext Result.failure(SanitizedUpdateException(MSG_ERROR_PACKAGE))
            }

            logDiagnostic(
                endpoint = EndpointType.SECONDARY,
                requestType = "APK_DOWNLOAD",
                status = "SUCCESS",
                durationMs = System.currentTimeMillis() - fallbackStartTime
            )
            return@withContext Result.success(destinationFile)
        }

        // Both primary and secondary download attempts failed
        destinationFile.delete()
        val fallbackException = fallbackResult.exceptionOrNull()!!
        val (secCategory, _) = evaluateFailure(fallbackException)
        logDiagnostic(
            endpoint = EndpointType.SECONDARY,
            requestType = "APK_DOWNLOAD",
            status = "FAILURE",
            category = secCategory,
            durationMs = System.currentTimeMillis() - fallbackStartTime,
            extra = "Both primary and secondary APK downloads failed"
        )

        Result.failure(SanitizedUpdateException(MSG_ERROR_DOWNLOAD, firstException))
    }

    /**
     * Backward-compatible overload for [downloadApk] defaulting to [EndpointType.PRIMARY].
     */
    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        expectedChecksum: String? = null,
        onProgress: (Float) -> Unit
    ): Result<File> = downloadApk(
        context = context,
        apkUrl = apkUrl,
        expectedChecksum = expectedChecksum,
        endpointType = EndpointType.PRIMARY,
        onProgress = onProgress
    )

    /**
     * Fetches and parses release metadata from a given [UpdateEndpoint].
     */
    private fun fetchMetadataFromEndpoint(endpoint: UpdateEndpoint): Result<UpdateInfo> {
        return try {
            val request = Request.Builder()
                .url(endpoint.metadataUrl)
                .header("User-Agent", "LofiQ-Android/${BuildConfig.VERSION_NAME}")
                .header("Cache-Control", "no-cache")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return Result.failure(HttpException(response.code, "HTTP ${response.code} from ${endpoint.type}"))
            }

            val body = response.body?.string() ?: return Result.failure(IOException("Empty response body"))
            val info = parseAndValidateMetadata(body, endpoint)
            Result.success(info)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Downloads an APK byte stream into [destinationFile] and verifies content-length.
     */
    private fun executeApkDownload(
        url: String,
        destinationFile: File,
        onProgress: (Float) -> Unit
    ): Result<Unit> {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "LofiQ-Android/${BuildConfig.VERSION_NAME}")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return Result.failure(HttpException(response.code, "HTTP ${response.code} downloading APK"))
            }

            val body = response.body ?: return Result.failure(IOException("Empty response body"))
            val contentLength = body.contentLength()

            body.byteStream().use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            val progress = (totalRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                            onProgress(progress)
                        }
                    }
                    output.flush()
                }
            }

            if (contentLength > 0 && destinationFile.length() < contentLength) {
                destinationFile.delete()
                return Result.failure(IOException("Incomplete download: expected $contentLength bytes, received ${destinationFile.length()}"))
            }

            Result.success(Unit)
        } catch (e: Throwable) {
            destinationFile.delete()
            Result.failure(e)
        }
    }

    /**
     * Parses and strictly validates JSON release metadata according to the required schema.
     */
    fun parseAndValidateMetadata(
        jsonString: String,
        endpoint: UpdateEndpoint
    ): UpdateInfo {
        val json = try {
            JSONObject(jsonString)
        } catch (e: Exception) {
            throw MetadataValidationException("Invalid JSON structure in release metadata")
        }

        val versionCode = json.optInt("versionCode", -1)
        if (versionCode <= 0) {
            throw MetadataValidationException("Missing or invalid 'versionCode' ($versionCode) in release metadata")
        }

        val versionName = json.optString("versionName", "").trim()
        if (versionName.isBlank()) {
            throw MetadataValidationException("Missing or invalid 'versionName' in release metadata")
        }

        val rawApkUrl = json.optString("apkUrl", "").trim()
        val checksum = json.optString("checksum", "").ifBlank {
            json.optString("sha256", "").ifBlank { null }
        }?.trim()

        if (checksum.isNullOrBlank()) {
            throw MetadataValidationException("Missing trusted SHA-256 checksum in release metadata")
        }
        if (!checksum.matches(Regex("^[a-fA-F0-9]{64}$"))) {
            throw MetadataValidationException("Malformed SHA-256 checksum: must be a 64-character hex string")
        }

        val baseDomain = endpoint.metadataUrl.substringBefore("/version.json")
        val fullApkUrl = if (rawApkUrl.startsWith("http://") || rawApkUrl.startsWith("https://")) {
            rawApkUrl
        } else if (rawApkUrl.isNotBlank()) {
            if (rawApkUrl.startsWith("/")) "$baseDomain$rawApkUrl" else "$baseDomain/$rawApkUrl"
        } else {
            endpoint.defaultApkUrl
        }

        val releaseDate = json.optString("releaseDate", "")
        val apkSize = json.optString("apkSize", "")
        val notesArray = json.optJSONArray("releaseNotes")
        val releaseNotes = mutableListOf<String>()
        if (notesArray != null) {
            for (i in 0 until notesArray.length()) {
                val note = notesArray.optString(i)
                if (note.isNotBlank()) releaseNotes.add(note)
                if (releaseNotes.size >= 7) break
            }
        }

        val hasUpdate = isUpdateAvailable(
            currentVersionCode = BuildConfig.VERSION_CODE,
            currentVersionName = BuildConfig.VERSION_NAME,
            remoteVersionCode = versionCode,
            remoteVersionName = versionName
        )

        return UpdateInfo(
            versionCode = versionCode,
            versionName = versionName,
            releaseDate = releaseDate,
            apkUrl = fullApkUrl,
            apkSize = apkSize,
            releaseNotes = releaseNotes,
            hasUpdate = hasUpdate,
            checksum = checksum,
            resolvedEndpoint = endpoint.type
        )
    }

    /**
     * Validates consistency between Primary and Secondary release metadata.
     * Prevents silently accepting a release if primary and secondary versions disagree.
     */
    fun verifyMetadataConsistency(primary: UpdateInfo, secondary: UpdateInfo): Boolean {
        val matches = primary.versionCode == secondary.versionCode &&
                primary.versionName.equals(secondary.versionName, ignoreCase = true) &&
                primary.checksum.equals(secondary.checksum, ignoreCase = true)
        if (!matches) {
            Log.w(
                TAG,
                "Metadata mismatch between endpoints: Primary(code=${primary.versionCode}, ver=${primary.versionName}, sha=${primary.checksum}) vs Secondary(code=${secondary.versionCode}, ver=${secondary.versionName}, sha=${secondary.checksum})"
            )
        }
        return matches
    }

    /**
     * Verifies that the downloaded APK matches the expected SHA-256 checksum.
     */
    fun verifyChecksum(file: File, expectedChecksum: String): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        val computed = calculateSha256(file)
        val matches = computed.equals(expectedChecksum.trim(), ignoreCase = true)
        if (!matches) {
            Log.w(TAG, "SHA-256 mismatch: computed=$computed, expected=${expectedChecksum.trim()}")
        }
        return matches
    }

    /**
     * Inspects the APK package to verify packageName and valid archive structure.
     */
    fun verifyApkPackage(
        context: Context,
        apkFile: File,
        expectedPackageName: String = context.packageName,
        expectedVersionCode: Int? = null
    ): Boolean {
        return try {
            val pm = context.packageManager ?: return true
            val archiveInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, 0) ?: run {
                Log.e(TAG, "Downloaded file is not a valid Android APK archive")
                return false
            }
            if (archiveInfo.packageName != expectedPackageName) {
                Log.e(TAG, "Package name mismatch: downloaded=${archiveInfo.packageName}, expected=$expectedPackageName")
                return false
            }
            val archiveVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                archiveInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                archiveInfo.versionCode
            }
            if (expectedVersionCode != null && archiveVersionCode != expectedVersionCode) {
                Log.e(TAG, "Version code mismatch: downloaded=$archiveVersionCode, expected=$expectedVersionCode")
                return false
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error inspecting APK package info: ${e.message}")
            false
        }
    }

    /**
     * Evaluates any exception to determine failure category and fallback eligibility.
     */
    fun evaluateFailure(t: Throwable): Pair<FailureCategory, Boolean> {
        return when (t) {
            is HttpException -> classifyHttpFailure(t.code)
            else -> classifyException(t)
        }
    }

    /**
     * Classifies HTTP status codes according to failover policy.
     * 5xx Server Errors -> Qualifying fallback
     * 4xx Client Errors (401, 403, 404, etc.) -> Non-qualifying
     */
    fun classifyHttpFailure(code: Int): Pair<FailureCategory, Boolean> {
        return when (code) {
            in 500..599 -> FailureCategory.SERVER_ERROR_5XX to true
            in 400..499 -> FailureCategory.CLIENT_ERROR_4XX to false
            else -> FailureCategory.UNKNOWN to false
        }
    }

    /**
     * Classifies network/transport exceptions according to failover policy.
     */
    fun classifyException(t: Throwable): Pair<FailureCategory, Boolean> {
        return when (t) {
            is SocketTimeoutException, is InterruptedIOException ->
                FailureCategory.TIMEOUT to true
            is UnknownHostException ->
                FailureCategory.DNS_FAILURE to true
            is ConnectException ->
                FailureCategory.CONNECTION_REFUSED to true
            is SocketException ->
                FailureCategory.CONNECTION_RESET to true
            is SSLException ->
                FailureCategory.TRANSPORT_TLS_ERROR to true
            is IOException ->
                FailureCategory.TRANSPORT_TLS_ERROR to true
            is JSONException, is MetadataValidationException ->
                FailureCategory.METADATA_INVALID to false
            is SecurityException ->
                FailureCategory.INTEGRITY_CHECKSUM_MISMATCH to false
            else ->
                FailureCategory.UNKNOWN to false
        }
    }

    /**
     * Sanitizes raw exceptions into neutral, user-friendly messages guaranteed
     * to never expose the backup hostname, internal endpoints, or URLs.
     */
    fun sanitizeException(t: Throwable): Exception {
        val userMessage = when (t) {
            is HttpException -> {
                when (t.code) {
                    401, 403 -> "Update service access is restricted (${t.code}). Please try again later."
                    404 -> "The update package was not found on the server. Please try again later."
                    in 500..599 -> "The update server is currently undergoing maintenance. Please try again later."
                    else -> "Update service returned an error (${t.code}). Please try again later."
                }
            }
            is SocketTimeoutException, is InterruptedIOException ->
                "Connection to the update service timed out. Please try again later."
            is UnknownHostException, is ConnectException ->
                MSG_ERROR_CONNECTING
            is MetadataValidationException ->
                "Unable to verify the update manifest. Please try again later."
            is SecurityException ->
                MSG_ERROR_INTEGRITY
            else ->
                MSG_ERROR_GENERAL
        }
        return SanitizedUpdateException(userMessage, t)
    }

    /**
     * Internal diagnostic logger conforming to Section 8 logging requirements.
     * Records endpoint, request type, status, failure category, and timing without
     * leaking sensitive user data or displaying to ordinary users.
     */
    private fun logDiagnostic(
        endpoint: EndpointType,
        requestType: String,
        status: String,
        category: FailureCategory? = null,
        httpCode: Int? = null,
        durationMs: Long? = null,
        extra: String? = null
    ) {
        val sb = StringBuilder()
        sb.append("[$TAG Diagnostic] ")
        sb.append("Endpoint: ").append(endpoint.name)
        sb.append(" | Request: ").append(requestType)
        sb.append(" | Status: ").append(status)
        if (category != null) sb.append(" | Category: ").append(category.name)
        if (httpCode != null) sb.append(" | HTTP: ").append(httpCode)
        if (durationMs != null) sb.append(" | Duration: ").append(durationMs).append("ms")
        if (extra != null) sb.append(" | Detail: ").append(extra)
        Log.i(TAG, sb.toString())
    }

    /**
     * Determines whether [remoteVersionName] or [remoteVersionCode] represents
     * a genuinely newer release than the currently running build.
     */
    fun isUpdateAvailable(
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
        currentVersionName: String = BuildConfig.VERSION_NAME,
        remoteVersionCode: Int,
        remoteVersionName: String
    ): Boolean {
        val cleanCurrentName = currentVersionName.trim().removePrefix("v").removePrefix("V")
        val cleanRemoteName = remoteVersionName.trim().removePrefix("v").removePrefix("V")

        if (cleanRemoteName.isBlank() || cleanCurrentName.isBlank()) {
            return remoteVersionCode > currentVersionCode
        }

        val semVerDiff = compareSemanticVersions(cleanRemoteName, cleanCurrentName)

        return when {
            semVerDiff > 0 -> true
            semVerDiff < 0 -> false
            else -> remoteVersionCode > currentVersionCode
        }
    }

    /**
     * Compares two semantic version strings (e.g., "1.2.0" and "1.1.1").
     */
    fun compareSemanticVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(Regex("[^0-9]+")).filter { it.isNotBlank() }.mapNotNull { it.toIntOrNull() }
        val parts2 = v2.split(Regex("[^0-9]+")).filter { it.isNotBlank() }.mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) {
                return p1.compareTo(p2)
            }
        }
        return 0
    }

    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun shouldPromptAutomaticUpdate(context: Context, info: UpdateInfo): Boolean {
        return info.hasUpdate
    }

    fun markUpdateDismissed(context: Context, versionName: String) {
        val prefs = context.getSharedPreferences("lofiq_update_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("dismissed_version", versionName)
            .putLong("dismissed_timestamp", System.currentTimeMillis())
            .apply()
    }

    fun shouldNotifyUpdate(context: Context, info: UpdateInfo): Boolean {
        if (!info.hasUpdate) return false
        val prefs = context.getSharedPreferences("lofiq_update_prefs", Context.MODE_PRIVATE)
        val lastNotifiedVersion = prefs.getString("last_notified_version", null)
        val lastNotifiedTime = prefs.getLong("last_notified_timestamp", 0L)
        val now = System.currentTimeMillis()

        if (lastNotifiedVersion != info.versionName) {
            return true
        }

        return (now - lastNotifiedTime) >= TimeUnit.HOURS.toMillis(12)
    }

    fun markNotificationShown(context: Context, info: UpdateInfo) {
        val prefs = context.getSharedPreferences("lofiq_update_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("last_notified_version", info.versionName)
            .putInt("last_notified_version_code", info.versionCode)
            .putLong("last_notified_timestamp", System.currentTimeMillis())
            .apply()
    }

    suspend fun checkForUpdateAndNotify(context: Context): Result<UpdateInfo> {
        val result = checkForUpdate()
        result.onSuccess { info ->
            if (info.hasUpdate) {
                val isForeground = com.lofiq.LofiQApp.isAppInForeground
                if (!isForeground && shouldNotifyUpdate(context, info)) {
                    showUpdateAvailableNotification(context, info)
                    markNotificationShown(context, info)
                }
            } else {
                dismissUpdateNotification(context)
                clearDismissedUpdate(context)
            }
        }
        return result
    }

    fun clearDismissedUpdate(context: Context) {
        val prefs = context.getSharedPreferences("lofiq_update_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    fun createUpdateNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                UPDATE_CHANNEL_ID,
                "App Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when a newer version of LofiQ is available to install"
                setShowBadge(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                enableLights(true)
                lightColor = android.graphics.Color.WHITE
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun showUpdateAvailableNotification(context: Context, info: UpdateInfo) {
        if (!info.hasUpdate) return

        createUpdateNotificationChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "Cannot show update notification: POST_NOTIFICATIONS permission not granted")
                return
            }
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val openIntent = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent(context, MainActivity::class.java)).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SHOW_UPDATE, true)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_UPDATE,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val downloadIntent = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent(context, MainActivity::class.java)).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SHOW_UPDATE, true)
            putExtra(EXTRA_START_DOWNLOAD, true)
        }
        val downloadPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_DOWNLOAD,
            downloadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "LofiQ v${info.versionName} Available! 🚀"
        val contentText = if (info.releaseNotes.isNotEmpty()) {
            info.releaseNotes.first().trimStart { it == '•' || it == '-' || it == '*' || it.isWhitespace() }.take(100)
        } else {
            "A new version of LofiQ is available to install."
        }

        val bigText = buildString {
            append("A new update (v${info.versionName}) is ready to download.")
            if (info.releaseNotes.isNotEmpty()) {
                append("\n\nWhat's new:\n")
                info.releaseNotes.take(4).forEach { note ->
                    val cleanNote = note.trimStart { it == '•' || it == '-' || it == '*' || it.isWhitespace() }
                    append("• $cleanNote\n")
                }
            }
            if (info.apkSize.isNotBlank()) {
                append("\nSize: ${info.apkSize}")
            }
            append("\nTap to install the latest version.")
        }.trim()

        val largeIcon = runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.app_logo_transparent)
        }.getOrNull()

        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .apply {
                if (largeIcon != null) {
                    setLargeIcon(largeIcon)
                }
            }
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setColor(0xFF8B5CF6.toInt())
            .addAction(
                android.R.drawable.stat_sys_download,
                "Update Now",
                downloadPendingIntent
            )
            .build()

        try {
            notificationManager.notify(UPDATE_NOTIFICATION_ID, notification)
            Log.i(TAG, "Update notification displayed for version ${info.versionName}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed posting update notification: ${e.message}")
        }
    }

    fun dismissUpdateNotification(context: Context) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(UPDATE_NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to dismiss update notification: ${e.message}")
        }
    }

    fun schedulePeriodicUpdateCheck(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicWork = PeriodicWorkRequestBuilder<AppUpdateWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC_UPDATE,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWork
            )
            Log.i(TAG, "Periodic background update check scheduled via WorkManager (15m interval)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed scheduling background update check: ${e.message}")
        }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            dismissUpdateNotification(context)
            val authority = "${context.packageName}.fileprovider"
            val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed launching APK installation intent", e)
        }
    }
}

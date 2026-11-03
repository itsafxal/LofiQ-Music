package com.lofiq.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.common.LofiQLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val dispatchers: DispatcherProvider
) {
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(dispatchers.io + SupervisorJob()),
        produceFile = { context.preferencesDataStoreFile("lofiq_auth_preferences") }
    )

    private object PreferencesKeys {
        val COOKIES = stringPreferencesKey("auth_cookies")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_AVATAR = stringPreferencesKey("user_avatar")
        val USER_CHANNEL_ID = stringPreferencesKey("user_channel_id")
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
    }

    @Volatile
    private var inMemoryCookies: String? = null

    init {
        CoroutineScope(dispatchers.io).launch {
            dataStore.data.catch { }.collect { prefs ->
                inMemoryCookies = prefs[PreferencesKeys.COOKIES]
            }
        }
    }

    val userAccount: Flow<UserAccount> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            val isLoggedIn = prefs[PreferencesKeys.IS_LOGGED_IN] ?: false
            if (!isLoggedIn) {
                UserAccount.GUEST
            } else {
                UserAccount(
                    name = prefs[PreferencesKeys.USER_NAME] ?: "YouTube Music User",
                    email = prefs[PreferencesKeys.USER_EMAIL],
                    avatarUrl = prefs[PreferencesKeys.USER_AVATAR],
                    channelId = prefs[PreferencesKeys.USER_CHANNEL_ID],
                    isLoggedIn = true
                )
            }
        }

    val rawCookies: Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs -> prefs[PreferencesKeys.COOKIES] }

    fun getCachedCookies(): String? = inMemoryCookies

    fun getCachedAuthHeaders(): Map<String, String> {
        val cookies = inMemoryCookies ?: return emptyMap()
        val sapisid = AuthCryptoUtils.extractSapisid(cookies) ?: return emptyMap()
        val authHash = AuthCryptoUtils.generateSapisidHash(sapisid)

        return mapOf(
            "Cookie" to cookies,
            "Authorization" to authHash,
            "Origin" to "https://music.youtube.com",
            "X-Origin" to "https://music.youtube.com",
            "Referer" to "https://music.youtube.com/",
            "X-YouTube-Client-Name" to "67",
            "X-YouTube-Client-Version" to "1.20261011.16.00",
            "X-Goog-AuthUser" to "0",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        )
    }

    suspend fun getCookies(): String? = withContext(dispatchers.io) {
        inMemoryCookies ?: dataStore.data.map { it[PreferencesKeys.COOKIES] }.firstOrNull()
    }

    suspend fun getAuthHeaders(): Map<String, String> = withContext(dispatchers.io) {
        val cookies = getCookies() ?: return@withContext emptyMap()
        val sapisid = AuthCryptoUtils.extractSapisid(cookies) ?: return@withContext emptyMap()
        val authHash = AuthCryptoUtils.generateSapisidHash(sapisid)

        mapOf(
            "Cookie" to cookies,
            "Authorization" to authHash,
            "Origin" to "https://music.youtube.com",
            "X-Origin" to "https://music.youtube.com",
            "Referer" to "https://music.youtube.com/",
            "X-YouTube-Client-Name" to "67",
            "X-YouTube-Client-Version" to "1.20261011.16.00",
            "X-Goog-AuthUser" to "0",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        )
    }

    suspend fun saveCookiesAndSyncProfile(cookies: String, preExtractedAccount: UserAccount? = null): Result<UserAccount> = withContext(dispatchers.io) {
        try {
            inMemoryCookies = cookies
            val sapisid = AuthCryptoUtils.extractSapisid(cookies)
            if (sapisid.isNullOrBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Invalid cookies: SAPISID not found"))
            }

            // Save raw cookies first
            dataStore.edit { prefs ->
                prefs[PreferencesKeys.COOKIES] = cookies
                prefs[PreferencesKeys.IS_LOGGED_IN] = true
            }

            // Fetch profile info from YouTube Music Innertube API or use pre-extracted info
            val profile = fetchUserProfile(cookies)
            val finalProfile = if (profile.isLoggedIn && profile.name.isNotBlank() && profile.name != "Guest" && profile.name != "YouTube Music User") {
                profile
            } else if (preExtractedAccount != null && preExtractedAccount.name.isNotBlank() && preExtractedAccount.name != "Guest" && preExtractedAccount.name != "YouTube Music User") {
                preExtractedAccount.copy(isLoggedIn = true)
            } else if (profile.isLoggedIn && !profile.avatarUrl.isNullOrBlank()) {
                profile
            } else {
                UserAccount(
                    name = preExtractedAccount?.name?.takeIf { it.isNotBlank() } ?: "YouTube Music User",
                    email = preExtractedAccount?.email ?: profile.email,
                    avatarUrl = preExtractedAccount?.avatarUrl ?: profile.avatarUrl,
                    channelId = preExtractedAccount?.channelId ?: profile.channelId,
                    isLoggedIn = true
                )
            }

            dataStore.edit { prefs ->
                prefs[PreferencesKeys.COOKIES] = cookies
                prefs[PreferencesKeys.USER_NAME] = finalProfile.name
                if (finalProfile.email != null) prefs[PreferencesKeys.USER_EMAIL] = finalProfile.email
                if (finalProfile.avatarUrl != null) prefs[PreferencesKeys.USER_AVATAR] = finalProfile.avatarUrl
                if (finalProfile.channelId != null) prefs[PreferencesKeys.USER_CHANNEL_ID] = finalProfile.channelId
                prefs[PreferencesKeys.IS_LOGGED_IN] = true
            }

            LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "YouTube Music Auth successful for: ${finalProfile.name}")
            Result.success(finalProfile)
        } catch (e: Exception) {
            LofiQLogger.e(LofiQLogger.Category.PLAYBACK, "Auth sync failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun fetchUserProfile(cookies: String): UserAccount = withContext(dispatchers.io) {
        val sapisid = AuthCryptoUtils.extractSapisid(cookies) ?: return@withContext UserAccount.GUEST
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()

        // 1. Try YouTube Music Innertube account_menu
        try {
            val ytMusicAuthHash = AuthCryptoUtils.generateSapisidHash(sapisid, "https://music.youtube.com")
            val musicPayload = buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20261011.16.00")
                        put("hl", "en")
                        put("gl", "IN")
                    }
                }
            }.toString()

            val musicReq = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/account/account_menu?key=AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30&prettyPrint=false")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .header("Referer", "https://music.youtube.com/")
                .header("Origin", "https://music.youtube.com")
                .header("X-Origin", "https://music.youtube.com")
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", "1.20261011.16.00")
                .header("X-Goog-AuthUser", "0")
                .header("Cookie", cookies)
                .header("Authorization", ytMusicAuthHash)
                .post(musicPayload.toRequestBody(jsonMediaType))
                .build()

            val response = okHttpClient.newCall(musicReq).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrBlank()) {
                    val parsed = parseUserProfileFromElement(json.parseToJsonElement(body))
                    if (parsed != null && parsed.isLoggedIn) {
                        LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[ACCOUNT][YOUTUBE] Profile request completed status=200 profileFound=true accountName=${parsed.name}")
                        return@withContext parsed
                    }
                }
            } else {
                LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[ACCOUNT][YOUTUBE] Profile request status=${response.code}")
            }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[ACCOUNT][YOUTUBE] fetchUserProfile music account_menu error: ${e.message}")
        }

        // 2. Try YouTube standard web account_menu with proper YouTube origin
        try {
            val ytAuthHash = AuthCryptoUtils.generateSapisidHash(sapisid, "https://www.youtube.com")
            val ytPayload = buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", "WEB")
                        put("clientVersion", "2.20251013.03.00")
                        put("hl", "en")
                        put("gl", "IN")
                    }
                }
            }.toString()

            val ytReq = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/account/account_menu?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8&prettyPrint=false")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .header("Referer", "https://www.youtube.com/")
                .header("Origin", "https://www.youtube.com")
                .header("X-Origin", "https://www.youtube.com")
                .header("X-YouTube-Client-Name", "1")
                .header("X-YouTube-Client-Version", "2.20251013.03.00")
                .header("X-Goog-AuthUser", "0")
                .header("Cookie", cookies)
                .header("Authorization", ytAuthHash)
                .post(ytPayload.toRequestBody(jsonMediaType))
                .build()

            val response = okHttpClient.newCall(ytReq).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrBlank()) {
                    val parsed = parseUserProfileFromElement(json.parseToJsonElement(body))
                    if (parsed != null && parsed.isLoggedIn) {
                        return@withContext parsed
                    }
                }
            }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "fetchUserProfile youtube account_menu error: ${e.message}")
        }

        // 3. Fallback: query browse endpoint for FEmusic_library_landing
        try {
            val ytMusicAuthHash = AuthCryptoUtils.generateSapisidHash(sapisid, "https://music.youtube.com")
            val browsePayload = buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20261011.16.00")
                        put("hl", "en")
                        put("gl", "IN")
                    }
                }
                put("browseId", "FEmusic_library_landing")
            }.toString()

            val browseRequest = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/browse?key=AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30&prettyPrint=false")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .header("Referer", "https://music.youtube.com/")
                .header("Origin", "https://music.youtube.com")
                .header("X-Origin", "https://music.youtube.com")
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", "1.20261011.16.00")
                .header("X-Goog-AuthUser", "0")
                .header("Cookie", cookies)
                .header("Authorization", ytMusicAuthHash)
                .post(browsePayload.toRequestBody(jsonMediaType))
                .build()

            val browseResp = okHttpClient.newCall(browseRequest).execute()
            if (browseResp.isSuccessful) {
                val body = browseResp.body?.string()
                if (!body.isNullOrBlank()) {
                    val parsed = parseUserProfileFromElement(json.parseToJsonElement(body))
                    if (parsed != null && parsed.isLoggedIn) {
                        return@withContext parsed
                    }
                }
            }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "fetchUserProfile browse fallback error: ${e.message}")
        }

        // 4. Fallback: Parse HTML from music.youtube.com directly
        try {
            val htmlReq = Request.Builder()
                .url("https://music.youtube.com/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .header("Cookie", cookies)
                .get()
                .build()

            val htmlResp = okHttpClient.newCall(htmlReq).execute()
            if (htmlResp.isSuccessful) {
                val html = htmlResp.body?.string().orEmpty()
                val parsed = parseUserProfileFromHtml(html)
                if (parsed != null && parsed.isLoggedIn) {
                    return@withContext parsed
                }
            }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "fetchUserProfile html fallback error: ${e.message}")
        }

        UserAccount.GUEST
    }

    private fun parseUserProfileFromHtml(html: String): UserAccount? {
        try {
            // Find ytInitialData JSON object inside script
            val pattern = java.util.regex.Pattern.compile("""(?:window\["ytInitialData"\]|var ytInitialData|ytInitialData)\s*=\s*(\{.+?\});""", java.util.regex.Pattern.DOTALL)
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                val jsonStr = matcher.group(1)
                if (!jsonStr.isNullOrBlank()) {
                    val element = json.parseToJsonElement(jsonStr)
                    val parsed = parseUserProfileFromElement(element)
                    if (parsed != null) return parsed
                }
            }

            // Fallback regex matching for avatar and account name in HTML
            val avatarPattern = java.util.regex.Pattern.compile("""https://lh3\.googleusercontent\.com/[a-zA-Z0-9_\-=]+""")
            val avatarMatcher = avatarPattern.matcher(html)
            val avatarUrl = if (avatarMatcher.find()) avatarMatcher.group(0) else null

            val namePattern = java.util.regex.Pattern.compile(""""USER_NAME":\s*"([^"]+)"""")
            val nameMatcher = namePattern.matcher(html)
            val userName = if (nameMatcher.find()) nameMatcher.group(1) else null

            if (!userName.isNullOrBlank() || !avatarUrl.isNullOrBlank()) {
                return UserAccount(
                    name = userName ?: "YouTube Music User",
                    email = null,
                    avatarUrl = avatarUrl,
                    channelId = null,
                    isLoggedIn = true
                )
            }
        } catch (_: Exception) {}
        return null
    }

    fun parseUserProfileFromElement(element: JsonElement): UserAccount? {
        var foundName: String? = null
        var foundEmail: String? = null
        var foundAvatar: String? = null
        var foundHandle: String? = null
        var foundChannelId: String? = null

        fun traverse(el: JsonElement) {
            when (el) {
                is JsonObject -> {
                    // Check activeAccountHeaderRenderer
                    if (el.containsKey("activeAccountHeaderRenderer")) {
                        val header = el["activeAccountHeaderRenderer"]?.jsonObject
                        if (header != null) {
                            val nameRuns = header["accountName"]?.jsonObject?.get("runs")?.jsonArray
                            val name = nameRuns?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                                ?: header["accountName"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.contentOrNull

                            val emailRuns = header["email"]?.jsonObject?.get("runs")?.jsonArray
                            val email = emailRuns?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                                ?: header["email"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.contentOrNull

                            val handleRuns = header["channelHandle"]?.jsonObject?.get("runs")?.jsonArray
                            val handle = handleRuns?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                                ?: header["channelHandle"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.contentOrNull

                            val thumbs = (header["accountPhoto"]?.jsonObject ?: header["avatar"]?.jsonObject)
                                ?.get("thumbnails")?.jsonArray
                            val avatar = thumbs?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull

                            val channelBrowseId = header["serviceEndpoint"]?.jsonObject
                                ?.get("browseEndpoint")?.jsonObject?.get("browseId")?.jsonPrimitive?.contentOrNull
                                ?: header["navigationEndpoint"]?.jsonObject
                                    ?.get("browseEndpoint")?.jsonObject?.get("browseId")?.jsonPrimitive?.contentOrNull
                                ?: header["endpoint"]?.jsonObject
                                    ?.get("browseEndpoint")?.jsonObject?.get("browseId")?.jsonPrimitive?.contentOrNull

                            val highResAvatar = if (avatar?.contains("googleusercontent.com") == true || avatar?.contains("ggpht.com") == true) {
                                avatar.replace(Regex("""=s\d+.*"""), "=s512").replace(Regex("""=w\d+-h\d+.*"""), "=w512-h512")
                            } else avatar

                            if (!name.isNullOrBlank()) foundName = name
                            if (!email.isNullOrBlank()) foundEmail = email
                            if (!handle.isNullOrBlank()) foundHandle = handle
                            if (!avatar.isNullOrBlank()) foundAvatar = highResAvatar
                            if (!channelBrowseId.isNullOrBlank()) foundChannelId = channelBrowseId
                        }
                    }

                    // Check googleAccountHeaderRenderer
                    if (el.containsKey("googleAccountHeaderRenderer")) {
                        val header = el["googleAccountHeaderRenderer"]?.jsonObject
                        if (header != null) {
                            val name = header["name"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.contentOrNull
                                ?: header["name"]?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                            val email = header["email"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.contentOrNull
                                ?: header["email"]?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                            val thumbs = header["avatar"]?.jsonObject?.get("thumbnails")?.jsonArray
                            val avatar = thumbs?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull

                            if (!name.isNullOrBlank() && foundName == null) foundName = name
                            if (!email.isNullOrBlank() && foundEmail == null) foundEmail = email
                            if (!avatar.isNullOrBlank() && foundAvatar == null) foundAvatar = avatar
                        }
                    }

                    // Check musicAccountButtonRenderer / accountButton
                    if (el.containsKey("musicAccountButtonRenderer")) {
                        val btn = el["musicAccountButtonRenderer"]?.jsonObject
                        if (btn != null) {
                            val thumbs = btn["avatar"]?.jsonObject?.get("thumbnails")?.jsonArray
                                ?: btn["accountPhoto"]?.jsonObject?.get("thumbnails")?.jsonArray
                            val avatar = thumbs?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                            if (!avatar.isNullOrBlank() && foundAvatar == null) foundAvatar = avatar

                            val label = btn["accessibility"]?.jsonObject?.get("accessibilityData")?.jsonObject?.get("label")?.jsonPrimitive?.contentOrNull
                            if (!label.isNullOrBlank()) {
                                if (label.contains("(") && label.contains(")")) {
                                    val emailPart = label.substringAfter("(").substringBefore(")").trim()
                                    val namePart = label.substringBefore("(").replace("Google Account:", "").replace("Account information for", "").trim()
                                    if (namePart.isNotBlank() && foundName == null) foundName = namePart
                                    if (emailPart.isNotBlank() && foundEmail == null) foundEmail = emailPart
                                } else if (label.contains("for ")) {
                                    val namePart = label.substringAfter("for ").trim()
                                    if (namePart.isNotBlank() && foundName == null) foundName = namePart
                                } else if (label.contains("Google Account:")) {
                                    val namePart = label.substringAfter("Google Account:").trim()
                                    if (namePart.isNotBlank() && foundName == null) foundName = namePart
                                }
                            }
                        }
                    }

                    // Check accountItemRenderer
                    if (el.containsKey("accountItemRenderer")) {
                        val item = el["accountItemRenderer"]?.jsonObject
                        if (item != null) {
                            val name = item["accountName"]?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                                ?: item["accountName"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.contentOrNull
                            val email = item["accountEmail"]?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                                ?: item["accountEmail"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.contentOrNull
                            val thumbs = item["accountPhoto"]?.jsonObject?.get("thumbnails")?.jsonArray
                            val avatar = thumbs?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull

                            val channelBrowseId = item["serviceEndpoint"]?.jsonObject
                                ?.get("browseEndpoint")?.jsonObject?.get("browseId")?.jsonPrimitive?.contentOrNull

                            if (!name.isNullOrBlank() && foundName == null) foundName = name
                            if (!email.isNullOrBlank() && foundEmail == null) foundEmail = email
                            if (!avatar.isNullOrBlank() && foundAvatar == null) foundAvatar = avatar
                            if (!channelBrowseId.isNullOrBlank() && foundChannelId == null) foundChannelId = channelBrowseId
                        }
                    }

                    for ((_, v) in el) {
                        traverse(v)
                    }
                }
                is JsonArray -> {
                    for (item in el) {
                        traverse(item)
                    }
                }
                else -> {}
            }
        }

        traverse(element)

        return if (foundName != null || foundAvatar != null || foundEmail != null) {
            UserAccount(
                name = foundName ?: foundEmail?.substringBefore("@") ?: "YouTube Music User",
                email = foundEmail ?: foundHandle,
                avatarUrl = foundAvatar,
                channelId = foundChannelId ?: foundHandle,
                isLoggedIn = true
            )
        } else {
            null
        }
    }

    suspend fun signOut() = withContext(dispatchers.io) {
        dataStore.edit { prefs ->
            prefs.remove(PreferencesKeys.COOKIES)
            prefs.remove(PreferencesKeys.USER_NAME)
            prefs.remove(PreferencesKeys.USER_EMAIL)
            prefs.remove(PreferencesKeys.USER_AVATAR)
            prefs.remove(PreferencesKeys.USER_CHANNEL_ID)
            prefs[PreferencesKeys.IS_LOGGED_IN] = false
        }
        withContext(dispatchers.main) {
            try {
                android.webkit.CookieManager.getInstance().removeAllCookies(null)
                android.webkit.CookieManager.getInstance().flush()
            } catch (_: Exception) {}
        }
        LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "User signed out and cookies cleared")
    }
}

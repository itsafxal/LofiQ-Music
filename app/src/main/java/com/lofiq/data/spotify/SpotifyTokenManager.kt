package com.lofiq.data.spotify

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lofiq.core.common.LofiQLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles acquiring and caching authenticated access tokens from Spotify's web player
 * using the user's active session cookie (sp_dc).
 */
@Singleton
class SpotifyTokenManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    companion object {
        private const val BRIDGE_NAME = "LofiQSpotifyTokenBridge"
        private const val HARVEST_TIMEOUT_MS = 25_000L
        private const val DEFAULT_TOKEN_LIFETIME_MS = 3_600_000L
        internal const val WEB_PLAYER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/128.0.0.0 Safari/537.36"

        private val SPOTIFY_COOKIE_URLS = listOf(
            "https://open.spotify.com",
            "https://accounts.spotify.com",
            "https://www.spotify.com",
            "https://spotify.com"
        )
    }

    private val harvestMutex = Mutex()

    @Volatile private var cachedAccessToken: String? = null
    @Volatile private var accessTokenExpiresAtMs = 0L
    @Volatile private var cachedClientId: String? = null

    @Volatile private var cachedSession: SessionInfo? = null
    @Volatile private var cachedClientToken: String? = null
    @Volatile private var clientTokenExpiresAtMs = 0L

    private data class SessionInfo(val clientVersion: String, val deviceId: String)
    private data class HarvestedToken(val token: String, val expiresAt: Long, val clientId: String?)

    fun invalidate() {
        cachedAccessToken = null
        accessTokenExpiresAtMs = 0L
        cachedClientId = null
        cachedClientToken = null
        clientTokenExpiresAtMs = 0L
        cachedSession = null
    }

    /**
     * Clears all Spotify cookies and cached tokens across all Spotify domains.
     */
    fun clearSession() {
        invalidate()
        try {
            val cookies = CookieManager.getInstance()
            for (url in SPOTIFY_COOKIE_URLS) {
                val names = cookies.getCookie(url)?.split(";")
                    ?.map { it.substringBefore("=").trim() }
                    ?.filter { it.isNotEmpty() }
                    .orEmpty()
                for (name in names) {
                    val expired = "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/"
                    cookies.setCookie(url, expired)
                    cookies.setCookie(url, "$expired; Domain=.spotify.com")
                }
            }
            cookies.flush()
            WebStorage.getInstance().deleteAllData()
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.NETWORK, "Failed to clear Spotify web session: ${e.message}")
        }
    }

    /**
     * Extracts clean sp_dc cookie value from either a raw token or full cookie string.
     */
    fun extractSpDc(cookieString: String): String {
        return cookieString.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("sp_dc=") }
            ?.substringAfter("sp_dc=")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: cookieString.trim()
    }

    /**
     * Retrieves an active bearer access token, harvesting one if missing or near expiry.
     */
    suspend fun getAccessToken(rawCookie: String): String? {
        val spDc = extractSpDc(rawCookie)
        if (spDc.isBlank()) return null

        val now = System.currentTimeMillis()
        cachedAccessToken?.let { if (now < accessTokenExpiresAtMs - 30_000L) return it }

        return harvestMutex.withLock {
            val stillNow = System.currentTimeMillis()
            cachedAccessToken?.let { if (stillNow < accessTokenExpiresAtMs - 30_000L) return@withLock it }

            LofiQLogger.i(LofiQLogger.Category.NETWORK, "Harvesting fresh Spotify access token via offscreen web view...")
            val harvested = withContext(Dispatchers.Main) {
                harvestViaWebView(context, spDc)
            }
            if (harvested == null) {
                LofiQLogger.w(LofiQLogger.Category.NETWORK, "Spotify token harvest timed out or failed")
                return@withLock null
            }

            cachedAccessToken = harvested.token
            accessTokenExpiresAtMs = harvested.expiresAt
            harvested.clientId?.let { cachedClientId = it }
            LofiQLogger.i(LofiQLogger.Category.NETWORK, "Spotify access token successfully harvested, valid until: ${harvested.expiresAt}")
            harvested.token
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun harvestViaWebView(context: Context, cookie: String): HarvestedToken? {
        val deferred = CompletableDeferred<HarvestedToken?>()

        val cookieManager = CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setCookie("https://open.spotify.com/", "sp_dc=$cookie; Domain=.spotify.com; Path=/; Secure")
            setCookie("https://accounts.spotify.com/", "sp_dc=$cookie; Domain=.spotify.com; Path=/; Secure")
            flush()
        }

        runCatching { WebStorage.getInstance().deleteAllData() }

        var webView: WebView? = null
        return try {
            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = WEB_PLAYER_UA
                cookieManager.setAcceptThirdPartyCookies(this, true)
                addJavascriptInterface(TokenBridge(deferred), BRIDGE_NAME)

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        view.evaluateJavascript(HOOK_SCRIPT, null)
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        view.evaluateJavascript(HOOK_SCRIPT, null)
                    }
                }
                loadUrl("https://open.spotify.com/")
            }

            withTimeoutOrNull(HARVEST_TIMEOUT_MS) { deferred.await() }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.NETWORK, "Spotify token harvest exception: ${e.message}")
            null
        } finally {
            runCatching {
                webView?.removeJavascriptInterface(BRIDGE_NAME)
                webView?.stopLoading()
                webView?.destroy()
            }
        }
    }

    private class TokenBridge(private val deferred: CompletableDeferred<HarvestedToken?>) {
        @JavascriptInterface
        fun onTokenPayload(payload: String?) {
            if (payload.isNullOrBlank() || deferred.isCompleted) return
            runCatching {
                val json = Json { ignoreUnknownKeys = true; isLenient = true }
                val root = json.parseToJsonElement(payload).jsonObject
                val token = root["accessToken"]?.jsonPrimitive?.contentOrNull
                val anonymous = root["isAnonymous"]?.jsonPrimitive?.contentOrNull
                    ?.toBooleanStrictOrNull() ?: false
                if (token.isNullOrBlank() || anonymous) return
                val expiresAt = root["accessTokenExpirationTimestampMs"]?.jsonPrimitive?.contentOrNull
                    ?.toLongOrNull()?.takeIf { it > System.currentTimeMillis() }
                    ?: (System.currentTimeMillis() + DEFAULT_TOKEN_LIFETIME_MS)
                val clientId = root["clientId"]?.jsonPrimitive?.contentOrNull
                deferred.complete(HarvestedToken(token, expiresAt, clientId))
            }
        }
    }

    private val HOOK_SCRIPT = """
        (function () {
          if (window.__lofiqTokenHook) return;
          window.__lofiqTokenHook = true;
          var report = function (body) {
            try { $BRIDGE_NAME.onTokenPayload(body); } catch (e) {}
          };
          var isToken = function (u) {
            try { return String(u).indexOf('/api/token') !== -1; } catch (e) { return false; }
          };
          var origFetch = window.fetch;
          if (origFetch) {
            window.fetch = function (input, init) {
              var url = (input && input.url) ? input.url : input;
              var result = origFetch.apply(this, arguments);
              if (isToken(url)) {
                try {
                  result.then(function (res) {
                    res.clone().text().then(report).catch(function () {});
                  }).catch(function () {});
                } catch (e) {}
              }
              return result;
            };
          }
          var origOpen = XMLHttpRequest.prototype.open;
          XMLHttpRequest.prototype.open = function (method, url) {
            this.__lofiqUrl = url;
            return origOpen.apply(this, arguments);
          };
          var origSend = XMLHttpRequest.prototype.send;
          XMLHttpRequest.prototype.send = function () {
            var xhr = this;
            try {
              xhr.addEventListener('load', function () {
                if (isToken(xhr.__lofiqUrl)) {
                  try { report(xhr.responseText); } catch (e) {}
                }
              });
            } catch (e) {}
            return origSend.apply(this, arguments);
          };
        })();
    """.trimIndent()

    /**
     * Mints client token required alongside bearer token by certain Spotify partner endpoints.
     */
    suspend fun getClientToken(): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cachedClientToken?.let { if (now < clientTokenExpiresAtMs - 30_000L) return@withContext it }

        val clientId = cachedClientId ?: return@withContext null
        val session = fetchSessionInfo() ?: return@withContext null

        val payload = buildJsonObject {
            putJsonObject("client_data") {
                put("client_version", session.clientVersion)
                put("client_id", clientId)
                putJsonObject("js_sdk_data") {
                    put("device_brand", "unknown")
                    put("device_model", "unknown")
                    put("os", "android")
                    put("os_version", android.os.Build.VERSION.RELEASE.orEmpty())
                    put("device_id", session.deviceId)
                    put("device_type", "smartphone")
                }
            }
        }

        val request = Request.Builder()
            .url("https://clienttoken.spotify.com/v1/clienttoken")
            .post(payload.toString().toByteArray(Charsets.UTF_8).toRequestBody("application/json".toMediaType()))
            .header("Accept", "application/json")
            .header("User-Agent", WEB_PLAYER_UA)
            .build()

        runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val root = json.parseToJsonElement(body).jsonObject
                if (root["response_type"]?.jsonPrimitive?.contentOrNull != "RESPONSE_GRANTED_TOKEN_RESPONSE") {
                    return@withContext null
                }
                val granted = root["granted_token"]?.jsonObject
                val token = granted?.get("token")?.jsonPrimitive?.contentOrNull ?: return@withContext null
                val ttlSeconds = granted["expires_after_seconds"]?.jsonPrimitive?.contentOrNull
                    ?.toLongOrNull() ?: 3600L

                cachedClientToken = token
                clientTokenExpiresAtMs = now + ttlSeconds * 1000L
                token
            }
        }.getOrNull()
    }

    private fun fetchSessionInfo(): SessionInfo? {
        cachedSession?.let { return it }

        val request = Request.Builder()
            .url("https://open.spotify.com")
            .header("User-Agent", WEB_PLAYER_UA)
            .build()

        return runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val spT = response.headers("Set-Cookie").firstNotNullOfOrNull { header ->
                    Regex("""sp_t=([^;]+)""").find(header)?.groupValues?.get(1)
                }

                val configB64 = Regex("""<script id="appServerConfig" type="text/plain">([^<]+)</script>""")
                    .find(body)?.groupValues?.get(1) ?: return@use null

                val configJson = String(Base64.getDecoder().decode(configB64), Charsets.UTF_8)
                val clientVersion = json.parseToJsonElement(configJson).jsonObject["clientVersion"]
                    ?.jsonPrimitive?.contentOrNull ?: return@use null

                val session = SessionInfo(clientVersion, spT ?: java.util.UUID.randomUUID().toString())
                cachedSession = session
                session
            }
        }.getOrNull()
    }
}

package com.lofiq.feature.spotify

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lofiq.ui.theme.*
import kotlinx.coroutines.delay

private const val SPOTIFY_LOGIN_URL =
    "https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2F"

private const val SPOTIFY_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpotifyLoginDialog(
    onDismiss: () -> Unit,
    onAuthSuccess: (String) -> Unit
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var pageTitle by remember { mutableStateOf("Sign In to Spotify") }
    var currentUrl by remember { mutableStateOf(SPOTIFY_LOGIN_URL) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var authCaptured by remember { mutableStateOf(false) }

    val cookieManager = remember { CookieManager.getInstance() }

    fun checkAndExtractCookies(url: String?) {
        if (authCaptured) return

        val urlsToInspect = listOf(
            "https://open.spotify.com",
            "https://accounts.spotify.com",
            "https://spotify.com",
            "https://www.spotify.com"
        )

        val cookieMap = mutableMapOf<String, String>()
        for (u in urlsToInspect) {
            val cookieStr = cookieManager.getCookie(u).orEmpty()
            for (pair in cookieStr.split(";")) {
                val trimmed = pair.trim()
                if (trimmed.contains("=")) {
                    val k = trimmed.substringBefore("=").trim()
                    val v = trimmed.substringAfter("=").trim()
                    if (k.isNotBlank() && !cookieMap.containsKey(k)) {
                        cookieMap[k] = v
                    }
                }
            }
        }

        val spDc = cookieMap["sp_dc"]?.trim()
        val hasValidSpDc = !spDc.isNullOrBlank() && spDc.length >= 20
        val isNotOnLoginPage = !currentUrl.contains("/login", ignoreCase = true) || currentUrl.contains("open.spotify.com", ignoreCase = true)

        if (hasValidSpDc && isNotOnLoginPage) {
            authCaptured = true
            webViewInstance?.stopLoading()
            cookieManager.flush()
            val targetCookies = cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
            onAuthSuccess(targetCookies)
        }
    }

    // Background polling loop
    LaunchedEffect(authCaptured) {
        while (!authCaptured) {
            delay(350)
            checkAndExtractCookies(currentUrl)
        }
    }

    Dialog(
        onDismissRequest = {
            checkAndExtractCookies(currentUrl)
            if (!authCaptured) onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundBlack)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Surface(
                    color = SurfaceCard,
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    checkAndExtractCookies(currentUrl)
                                    if (!authCaptured) onDismiss()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close",
                                    tint = AccentWhite
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Lock,
                                        contentDescription = "Secure",
                                        tint = Color(0xFF1DB954),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = pageTitle,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentWhite
                                        ),
                                        maxLines = 1
                                    )
                                }
                                Text(
                                    text = if (currentUrl.contains("open.spotify.com")) "open.spotify.com" else "accounts.spotify.com",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    ),
                                    maxLines = 1
                                )
                            }

                            IconButton(
                                onClick = { webViewInstance?.reload() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Reload",
                                    tint = TextSecondary
                                )
                            }
                        }

                        // Progress Bar
                        if (isLoading && loadProgress < 100) {
                            LinearProgressIndicator(
                                progress = { loadProgress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp),
                                color = Color(0xFF1DB954),
                                trackColor = Color.Transparent
                            )
                        }
                    }
                }

                // WebView Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp))
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewInstance = this
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    setSupportZoom(true)
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    allowFileAccess = false
                                    allowContentAccess = false
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    userAgentString = SPOTIFY_USER_AGENT
                                }

                                val cm = CookieManager.getInstance()
                                cm.setAcceptCookie(true)
                                cm.setAcceptThirdPartyCookies(this, true)

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        loadProgress = newProgress
                                        isLoading = newProgress < 100
                                    }

                                    override fun onReceivedTitle(view: WebView?, title: String?) {
                                        if (!title.isNullOrBlank()) {
                                            pageTitle = title
                                        }
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        currentUrl = url.orEmpty()
                                        isLoading = true
                                        checkAndExtractCookies(url)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        currentUrl = url.orEmpty()
                                        isLoading = false
                                        checkAndExtractCookies(url)
                                    }

                                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                        super.doUpdateVisitedHistory(view, url, isReload)
                                        checkAndExtractCookies(url)
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val url = request?.url?.toString().orEmpty()

                                        if (url.startsWith("intent://") || url.startsWith("spotify:")) {
                                            try {
                                                val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)?.apply {
                                                    addCategory(Intent.CATEGORY_BROWSABLE)
                                                    component = null
                                                    selector = null
                                                }
                                                if (intent != null) {
                                                    val pm = view?.context?.packageManager
                                                    val info = pm?.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                                                    if (info != null) {
                                                        view?.context?.startActivity(intent)
                                                    }
                                                }
                                            } catch (_: Exception) {}
                                            return true
                                        }

                                        if (url.startsWith("market://")) {
                                            return true
                                        }

                                        checkAndExtractCookies(url)
                                        return false
                                    }
                                }

                                loadUrl(SPOTIFY_LOGIN_URL)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Authenticating Overlay
                    if (authCaptured) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(BackgroundBlack.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFF1DB954),
                                    strokeWidth = 3.dp
                                )
                                Text(
                                    text = "Connecting Spotify Account...",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = AccentWhite,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

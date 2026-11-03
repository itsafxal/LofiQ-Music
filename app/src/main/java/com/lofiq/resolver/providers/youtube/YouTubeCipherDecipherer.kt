package com.lofiq.resolver.providers.youtube

import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.common.LofiQLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure Kotlin signature cipher decipherer for YouTube streams.
 * Parses YouTube's player JavaScript (base.js) to extract and execute signature descrambling transformations
 * without requiring external JavaScript engines or runtimes.
 */
@Singleton
class YouTubeCipherDecipherer @Inject constructor(
    private val httpClient: OkHttpClient,
    private val dispatchers: DispatcherProvider
) {
    private val mutex = Mutex()
    private var cachedPlayerUrl: String? = null
    private var cachedTransforms: List<TransformOp>? = null

    internal sealed interface TransformOp {
        fun apply(chars: CharArray): CharArray

        data object Reverse : TransformOp {
            override fun apply(chars: CharArray): CharArray {
                chars.reverse()
                return chars
            }
        }

        data class Slice(val count: Int) : TransformOp {
            override fun apply(chars: CharArray): CharArray {
                return if (count in 1..chars.size) {
                    chars.copyOfRange(count, chars.size)
                } else chars
            }
        }

        data class Swap(val index: Int) : TransformOp {
            override fun apply(chars: CharArray): CharArray {
                if (chars.isNotEmpty() && index in chars.indices) {
                    val temp = chars[0]
                    chars[0] = chars[index]
                    chars[index] = temp
                }
                return chars
            }
        }
    }

    suspend fun decipher(signatureCipher: String): String? = withContext(dispatchers.io) {
        try {
            val params = parseQueryParams(signatureCipher)
            val url = params["url"] ?: return@withContext null
            val s = params["s"]
            val sp = params["sp"] ?: "sig"

            if (s.isNullOrBlank()) {
                return@withContext url
            }

            val transforms = getOrFetchTransforms()
            if (transforms.isNullOrEmpty()) {
                LofiQLogger.w(LofiQLogger.Category.RESOLVER, "Cannot decipher signature: player JS transforms unavailable")
                return@withContext null
            }

            var chars = s.toCharArray()
            for (op in transforms) {
                chars = op.apply(chars)
            }
            val decSig = String(chars)

            val delimiter = if (url.contains("?")) "&" else "?"
            "$url$delimiter$sp=$decSig"
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.RESOLVER, "Decipher signature failed: ${e.message}")
            null
        }
    }

    private suspend fun getOrFetchTransforms(): List<TransformOp>? = mutex.withLock {
        cachedTransforms?.let { return it }

        try {
            val playerJsUrl = fetchPlayerJsUrl()
            if (playerJsUrl != null) {
                val fastClient = httpClient.newBuilder()
                    .callTimeout(3500, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .connectTimeout(3000, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .readTimeout(3000, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .build()

                val req = Request.Builder()
                    .url(playerJsUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                    .build()

                val resp = fastClient.newCall(req).execute()
                resp.use { r ->
                    if (r.isSuccessful) {
                        val js = r.body?.string()
                        if (!js.isNullOrBlank()) {
                            val transforms = parseDecipherTransformsFromJs(js)
                            if (transforms != null && transforms.isNotEmpty()) {
                                cachedPlayerUrl = playerJsUrl
                                cachedTransforms = transforms
                                LofiQLogger.i(LofiQLogger.Category.RESOLVER, "Successfully cached ${transforms.size} decipher transformations from player JS")
                                return transforms
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.RESOLVER, "Failed fetching player JS transforms: ${e.message}")
        }

        LofiQLogger.w(LofiQLogger.Category.RESOLVER, "No valid decipher transformations could be parsed from player JS")
        return null
    }

    private fun fetchPlayerJsUrl(): String? {
        val targets = listOf("https://www.youtube.com/embed/dQw4w9WgXcQ", "https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        val fastClient = httpClient.newBuilder()
            .callTimeout(3000, java.util.concurrent.TimeUnit.MILLISECONDS)
            .connectTimeout(2500, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(2500, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()

        for (targetUrl in targets) {
            try {
                val req = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                    .build()
                val resp = fastClient.newCall(req).execute()
                resp.use { r ->
                    if (r.isSuccessful) {
                        val body = r.body?.string() ?: return@use
                        val jsUrlMatcher = Pattern.compile("\"jsUrl\":\"([^\"]+)\"").matcher(body)
                        if (jsUrlMatcher.find()) {
                            val raw = jsUrlMatcher.group(1)?.replace("\\/", "/")
                            if (raw != null) {
                                return if (raw.startsWith("http")) raw else "https://www.youtube.com$raw"
                            }
                        }
                        val pathMatcher = Pattern.compile("(/s/player/[a-zA-Z0-9_-]+/[a-zA-Z0-9_.-]+\\.vflset/[a-zA-Z0-9_-]+/(?:base|player_ias|player_embed_es6)\\.js)").matcher(body)
                        if (pathMatcher.find()) {
                            val raw = pathMatcher.group(1)
                            if (raw != null) return "https://www.youtube.com$raw"
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return "https://www.youtube.com/s/player/cbcfbf2f/player_embed_es6.vflset/en_GB/base.js"
    }

    internal fun parseDecipherTransformsFromJs(js: String): List<TransformOp>? {
        try {
            // 1. Locate the body of the decipher function: ... .split("")... .join("") ...
            val bodyPattern = Pattern.compile("([a-zA-Z0-9$]+)\\s*=\\s*\\1\\.split\\([\"']{2}\\);([\\s\\S]*?)(?:return\\s+\\1\\.join|\\1\\.join)")
            val bodyMatcher = bodyPattern.matcher(js)
            if (!bodyMatcher.find()) return null

            val funcBody = bodyMatcher.group(2) ?: return null

            // 2. Find the helper object name from the calls in funcBody: e.g. wD.swap(a, 12);
            val callPattern = Pattern.compile("([a-zA-Z0-9$]+)\\.([a-zA-Z0-9$]+)\\([a-zA-Z0-9$]+(?:,\\s*(\\d+))?\\)")
            val firstCallMatcher = callPattern.matcher(funcBody)
            if (!firstCallMatcher.find()) return null
            val objName = firstCallMatcher.group(1) ?: return null

            // 3. Find the definition of the helper object: var wD = { ... };
            val objDefPattern = Pattern.compile("(?:var|let|const)?\\s*" + Pattern.quote(objName) + "\\s*=\\s*\\{([\\s\\S]*?)\\};")
            val objDefMatcher = objDefPattern.matcher(js)
            if (!objDefMatcher.find()) return null
            val objDef = objDefMatcher.group(1) ?: return null

            // 4. Classify each method in the helper object (reverse, slice/splice, swap)
            val opMap = mutableMapOf<String, String>()
            val methodPattern = Pattern.compile("([a-zA-Z0-9$]+):\\s*function\\s*\\([^)]*\\)\\s*\\{([\\s\\S]*?)\\}")
            val methodMatcher = methodPattern.matcher(objDef)
            while (methodMatcher.find()) {
                val methodName = methodMatcher.group(1) ?: continue
                val methodBody = methodMatcher.group(2).orEmpty()
                when {
                    methodBody.contains("reverse") -> opMap[methodName] = "reverse"
                    methodBody.contains("splice") || methodBody.contains("slice") -> opMap[methodName] = "slice"
                    methodBody.contains("a[0]") || methodBody.contains("c=a[0]") || methodBody.contains("[0]=") || methodBody.contains("[0] =") -> opMap[methodName] = "swap"
                }
            }

            // 5. Build ordered transformations
            val resultOps = mutableListOf<TransformOp>()
            val callsMatcher = callPattern.matcher(funcBody)
            while (callsMatcher.find()) {
                val caller = callsMatcher.group(1) ?: continue
                if (caller != objName) continue
                val methodName = callsMatcher.group(2) ?: continue
                val param = callsMatcher.group(3)?.toIntOrNull() ?: 0
                val opType = opMap[methodName] ?: continue

                when (opType) {
                    "reverse" -> resultOps.add(TransformOp.Reverse)
                    "slice" -> resultOps.add(TransformOp.Slice(param))
                    "swap" -> resultOps.add(TransformOp.Swap(param))
                }
            }

            return resultOps.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.RESOLVER, "Parse decipher transforms error: ${e.message}")
            return null
        }
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val pairs = query.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            if (idx > 0) {
                val key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.name())
                val value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.name())
                map[key] = value
            }
        }
        return map
    }
}

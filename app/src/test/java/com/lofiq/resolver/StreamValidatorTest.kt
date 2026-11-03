package com.lofiq.resolver

import com.lofiq.core.common.DispatcherProvider
import com.lofiq.resolver.core.StreamValidator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StreamValidatorTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val main = testDispatcher
        override val io = testDispatcher
        override val default = testDispatcher
        override val unconfined = testDispatcher
    }

    private lateinit var validator: StreamValidator

    @Before
    fun setup() {
        val testClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                when {
                    url.contains("mock_valid_audio") -> {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(206)
                            .message("Partial Content")
                            .header("Content-Type", "audio/mp4")
                            .body("chunk".toResponseBody("audio/mp4".toMediaType()))
                            .build()
                    }
                    url.contains("mock_403_googlevideo") -> {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(403)
                            .message("Forbidden")
                            .header("Content-Type", "text/plain")
                            .body("Forbidden".toResponseBody("text/plain".toMediaType()))
                            .build()
                    }
                    url.contains("mock_404_jiosaavn") -> {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(404)
                            .message("Not Found")
                            .header("Content-Type", "text/html")
                            .body("Not Found".toResponseBody("text/html".toMediaType()))
                            .build()
                    }
                    else -> {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(500)
                            .message("Internal Server Error")
                            .body("Error".toResponseBody("text/plain".toMediaType()))
                            .build()
                    }
                }
            }
            .build()

        validator = StreamValidator(testClient, dispatchers)
    }

    @Test
    fun `validateStream accepts valid audio 206 stream`() = runTest(testDispatcher) {
        val validUrl = "https://example.com/stream/mock_valid_audio.mp4"
        val result = validator.validateStream(validUrl)
        assertTrue(result)
    }

    @Test
    fun `validateStream rejects 403 response even if googlevideo unexpired timestamp is present`() = runTest(testDispatcher) {
        val farFutureExpire = (System.currentTimeMillis() / 1000L) + 3600L
        val fakeGoogleVideo403Url = "https://rr5---sn.googlevideo.com/videoplayback?expire=$farFutureExpire&mock_403_googlevideo=1"
        val result = validator.validateStream(fakeGoogleVideo403Url)
        assertFalse("StreamValidator must not falsely accept 403 GoogleVideo stream", result)
    }

    @Test
    fun `validateStream rejects 404 response on purged CDN streams`() = runTest(testDispatcher) {
        val purgedUrl = "https://aac.saavncdn.com/208/mock_404_jiosaavn.mp4"
        val result = validator.validateStream(purgedUrl)
        assertFalse("StreamValidator must reject 404 purged streams", result)
    }

    @Test
    fun `validateStream rejects blank and malformed URLs`() = runTest(testDispatcher) {
        assertFalse(validator.validateStream(""))
        assertFalse(validator.validateStream("   "))
        assertFalse(validator.validateStream("not_a_valid_url"))
    }
}

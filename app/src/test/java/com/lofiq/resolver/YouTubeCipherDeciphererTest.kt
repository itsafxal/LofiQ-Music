package com.lofiq.resolver

import com.lofiq.core.common.DispatcherProvider
import com.lofiq.resolver.providers.youtube.YouTubeCipherDecipherer
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class YouTubeCipherDeciphererTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val main = testDispatcher
        override val io = testDispatcher
        override val default = testDispatcher
        override val unconfined = testDispatcher
    }

    private val mockHttpClient = mockk<OkHttpClient>(relaxed = true)
    private lateinit var decipherer: YouTubeCipherDecipherer

    @Before
    fun setup() {
        decipherer = YouTubeCipherDecipherer(mockHttpClient, dispatchers)
    }

    @Test
    fun `TransformOp Reverse correctly reverses char array`() {
        val input = "ABCDE".toCharArray()
        val result = YouTubeCipherDecipherer.TransformOp.Reverse.apply(input)
        assertEquals("EDCBA", String(result))
    }

    @Test
    fun `TransformOp Slice correctly trims prefix from char array`() {
        val input = "ABCDEF".toCharArray()
        val result = YouTubeCipherDecipherer.TransformOp.Slice(2).apply(input)
        assertEquals("CDEF", String(result))
    }

    @Test
    fun `TransformOp Swap correctly swaps first element with specified index`() {
        val input = "ABCDE".toCharArray()
        val result = YouTubeCipherDecipherer.TransformOp.Swap(3).apply(input)
        // Swap index 0 ('A') with index 3 ('D') -> "DBC AE"
        assertEquals("DBCAE", String(result))
    }

    @Test
    fun `parseDecipherTransformsFromJs parses standard YouTube player base_js format`() {
        val sampleJs = """
            var wD = {
                swap: function(a, b) { var c = a[0]; a[0] = a[b % a.length]; a[b % a.length] = c; },
                slice: function(a, b) { a.splice(0, b); },
                reverse: function(a) { a.reverse(); }
            };
            var xD = function(a) {
                a = a.split("");
                wD.swap(a, 12);
                wD.reverse(a);
                wD.slice(a, 3);
                wD.swap(a, 5);
                return a.join("");
            };
        """.trimIndent()

        val transforms = decipherer.parseDecipherTransformsFromJs(sampleJs)
        assertNotNull(transforms)
        assertEquals(4, transforms!!.size)
        assertTrue(transforms[0] is YouTubeCipherDecipherer.TransformOp.Swap)
        assertEquals(12, (transforms[0] as YouTubeCipherDecipherer.TransformOp.Swap).index)
        assertTrue(transforms[1] is YouTubeCipherDecipherer.TransformOp.Reverse)
        assertTrue(transforms[2] is YouTubeCipherDecipherer.TransformOp.Slice)
        assertEquals(3, (transforms[2] as YouTubeCipherDecipherer.TransformOp.Slice).count)
        assertTrue(transforms[3] is YouTubeCipherDecipherer.TransformOp.Swap)
        assertEquals(5, (transforms[3] as YouTubeCipherDecipherer.TransformOp.Swap).index)
    }

    @Test
    fun `parseDecipherTransformsFromJs handles alternative regex formats with single quotes`() {
        val sampleJs = """
            let kA = {
                rv: function(a) { a.reverse() },
                sl: function(a, b) { a.splice(0, b) },
                sw: function(a, b) { var c = a[0]; a[0] = a[b]; a[b] = c }
            };
            function mA(a) {
                a = a.split('');
                kA.rv(a);
                kA.sw(a, 2);
                kA.sl(a, 1);
                return a.join('');
            }
        """.trimIndent()

        val transforms = decipherer.parseDecipherTransformsFromJs(sampleJs)
        assertNotNull(transforms)
        assertEquals(3, transforms!!.size)
        assertTrue(transforms[0] is YouTubeCipherDecipherer.TransformOp.Reverse)
        assertTrue(transforms[1] is YouTubeCipherDecipherer.TransformOp.Swap)
        assertEquals(2, (transforms[1] as YouTubeCipherDecipherer.TransformOp.Swap).index)
        assertTrue(transforms[2] is YouTubeCipherDecipherer.TransformOp.Slice)
        assertEquals(1, (transforms[2] as YouTubeCipherDecipherer.TransformOp.Slice).count)
    }

    @Test
    fun `decipher returns null when player js transforms are unavailable`() = runTest(testDispatcher) {
        val signatureCipher = "url=https%3A%2F%2Frr1---sn.googlevideo.com%2Fvideoplayback&s=abcdef123456&sp=sig"
        val result = decipherer.decipher(signatureCipher)
        assertNull(result)
    }
}

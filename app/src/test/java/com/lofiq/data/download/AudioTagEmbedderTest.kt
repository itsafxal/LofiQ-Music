package com.lofiq.data.download

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class AudioTagEmbedderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testSanitizeArtwork_withJpegBytes() {
        val jpegHeader = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(100)
        val result = AudioTagEmbedder.sanitizeArtwork(jpegHeader)
        assertNotNull(result)
        assertEquals(jpegHeader.size, result?.size)
    }

    @Test
    fun testSanitizeArtwork_withPngBytes() {
        val pngHeader = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()) + ByteArray(100)
        val result = AudioTagEmbedder.sanitizeArtwork(pngHeader)
        assertNotNull(result)
        assertEquals(pngHeader.size, result?.size)
    }

    @Test
    fun testEmbedM4aMetadata_addsUdtaWithCoverArtAndTags() {
        val testFile = tempFolder.newFile("test_audio.m4a")

        // Build a minimal valid M4A file: [ftyp][moov][mdat]
        val ftypBox = ByteBuffer.allocate(32).apply {
            putInt(32)
            put("ftyp".toByteArray(StandardCharsets.US_ASCII))
            put("M4A ".toByteArray(StandardCharsets.US_ASCII))
            putInt(0)
            put("M4A mp42isom".toByteArray(StandardCharsets.US_ASCII))
        }.array()

        // Minimal moov box with a minimal trak container
        val trakBox = ByteBuffer.allocate(40).apply {
            putInt(40)
            put("trak".toByteArray(StandardCharsets.US_ASCII))
            putInt(32)
            put("mdia".toByteArray(StandardCharsets.US_ASCII))
            put(ByteArray(24))
        }.array()

        val moovBox = ByteBuffer.allocate(8 + trakBox.size).apply {
            putInt(8 + trakBox.size)
            put("moov".toByteArray(StandardCharsets.US_ASCII))
            put(trakBox)
        }.array()

        val mdatBox = ByteBuffer.allocate(1000).apply {
            putInt(1000)
            put("mdat".toByteArray(StandardCharsets.US_ASCII))
            put(ByteArray(992) { 0x55.toByte() })
        }.array()

        FileOutputStream(testFile).use { fos ->
            fos.write(ftypBox)
            fos.write(moovBox)
            fos.write(mdatBox)
        }

        assertTrue("Test file created", testFile.length() > 1000)

        val fakeCoverArt = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(200)

        // Embed tags
        val success = AudioTagEmbedder.embedMetadata(
            audioFile = testFile,
            title = "Afsal Lofi Melody",
            artist = "Afsal",
            album = "LofiQ Sessions",
            rawArtworkBytes = fakeCoverArt
        )

        assertTrue("Metadata embedding should succeed", success)
        assertTrue("Tagged file exists and has content", testFile.exists() && testFile.length() > 1000)

        // Verify that 'moov' now contains 'udta', 'meta', 'ilst', and 'covr'
        val bytes = testFile.readBytes()
        val fileContentStr = String(bytes, StandardCharsets.ISO_8859_1)

        assertTrue("File should contain 'moov'", fileContentStr.contains("moov"))
        assertTrue("File should contain 'udta'", fileContentStr.contains("udta"))
        assertTrue("File should contain 'meta'", fileContentStr.contains("meta"))
        assertTrue("File should contain 'ilst'", fileContentStr.contains("ilst"))
        assertTrue("File should contain 'covr'", fileContentStr.contains("covr"))
        assertTrue("File should contain 'Afsal Lofi Melody'", fileContentStr.contains("Afsal Lofi Melody"))
        assertTrue("File should contain 'LofiQ Sessions'", fileContentStr.contains("LofiQ Sessions"))
    }

    @Test
    fun testEmbedMp3Metadata_addsId3v2WithApicFrame() {
        val testFile = tempFolder.newFile("test_audio.mp3")

        // Minimal MP3 audio frames
        val mp3Data = ByteArray(2048) { 0xFF.toByte() }
        testFile.writeBytes(mp3Data)

        val fakeCoverArt = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(150)

        val success = AudioTagEmbedder.embedMetadata(
            audioFile = testFile,
            title = "Night Drive",
            artist = "Afsal",
            album = "Chill Vibes",
            rawArtworkBytes = fakeCoverArt
        )

        assertTrue("MP3 embedding should succeed", success)

        val bytes = testFile.readBytes()
        val contentStr = String(bytes, StandardCharsets.ISO_8859_1)

        assertEquals("Starts with ID3", "ID3", String(bytes, 0, 3, StandardCharsets.US_ASCII))
        assertTrue("Contains TIT2 frame", contentStr.contains("TIT2"))
        assertTrue("Contains TPE1 frame", contentStr.contains("TPE1"))
        assertTrue("Contains TALB frame", contentStr.contains("TALB"))
        assertTrue("Contains APIC frame", contentStr.contains("APIC"))
        assertTrue("Contains Night Drive", contentStr.contains("Night Drive"))
    }
}

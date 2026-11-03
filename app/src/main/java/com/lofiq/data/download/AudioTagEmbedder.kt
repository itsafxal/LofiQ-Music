package com.lofiq.data.download

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.lofiq.core.common.LofiQLogger
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Embeds metadata tags (Title, Artist, Album) and Cover Artwork (JPEG/PNG)
 * directly into audio files (M4A/MP4 and MP3).
 *
 * This ensures that when tracks are saved to device storage (Music/LofiQ)
 * or played in any external music player (Samsung Music, Mi Music, VLC, Poweramp),
 * the album cover artwork, track title, artist, and album are prominently displayed.
 */
object AudioTagEmbedder {

    private val TITLE_FOURCC = byteArrayOf(0xA9.toByte(), 'n'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte())
    private val ARTIST_FOURCC = byteArrayOf(0xA9.toByte(), 'A'.code.toByte(), 'R'.code.toByte(), 'T'.code.toByte())
    private val ALBUM_FOURCC = byteArrayOf(0xA9.toByte(), 'a'.code.toByte(), 'l'.code.toByte(), 'b'.code.toByte())
    private val COVR_FOURCC = "covr".toByteArray(StandardCharsets.US_ASCII)

    /**
     * Embeds metadata and artwork into the specified audio file.
     * Modifies the file in-place if supported (M4A or MP3).
     *
     * @param audioFile The local audio file to tag
     * @param title Track title
     * @param artist Track artist
     * @param album Album name
     * @param rawArtworkBytes Optional image bytes (JPEG, PNG, or WebP)
     * @return true if successfully embedded, false otherwise
     */
    fun embedMetadata(
        audioFile: File,
        title: String,
        artist: String,
        album: String,
        rawArtworkBytes: ByteArray?
    ): Boolean {
        if (!audioFile.exists() || audioFile.length() < 1000L) {
            return false
        }

        val artworkBytes = sanitizeArtwork(rawArtworkBytes)

        return try {
            val format = detectAudioFormat(audioFile)
            when (format) {
                AudioFormat.M4A -> {
                    embedM4aMetadata(audioFile, title, artist, album, artworkBytes)
                }
                AudioFormat.MP3 -> {
                    embedMp3Metadata(audioFile, title, artist, album, artworkBytes)
                }
                AudioFormat.UNKNOWN -> {
                    LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[TAGGER] Unknown container format for ${audioFile.name}; skipping tag embedding")
                    false
                }
            }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[TAGGER] Failed to embed metadata in ${audioFile.name}: ${e.message}")
            false
        }
    }

    private enum class AudioFormat {
        M4A,
        MP3,
        UNKNOWN
    }

    private fun detectAudioFormat(file: File): AudioFormat {
        FileInputStream(file).use { input ->
            val header = ByteArray(12)
            val bytesRead = input.read(header)
            if (bytesRead >= 8) {
                // Check M4A/MP4: starts with standard box header where bytes 4..7 are 'ftyp' or 'moov'
                val type = String(header, 4, 4, StandardCharsets.US_ASCII)
                if (type == "ftyp" || type == "moov") {
                    return AudioFormat.M4A
                }
                // Check MP3: starts with "ID3" or sync bytes 0xFF 0xFB/0xFA/0xF3/0xF2
                if (header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
                    return AudioFormat.MP3
                }
                if (header[0] == 0xFF.toByte() && (header[1].toInt() and 0xE0) == 0xE0) {
                    return AudioFormat.MP3
                }
            }
        }
        val ext = file.extension.lowercase()
        return when (ext) {
            "m4a", "mp4", "aac" -> AudioFormat.M4A
            "mp3" -> AudioFormat.MP3
            else -> AudioFormat.UNKNOWN
        }
    }

    /**
     * Ensures artwork is in standard JPEG or PNG format.
     * WebP or other formats are re-encoded to JPEG for universal player compatibility.
     */
    fun sanitizeArtwork(rawBytes: ByteArray?): ByteArray? {
        if (rawBytes == null || rawBytes.size < 64) return null

        val isJpeg = rawBytes[0] == 0xFF.toByte() && rawBytes[1] == 0xD8.toByte()
        val isPng = rawBytes.size > 8 &&
                rawBytes[0] == 0x89.toByte() &&
                rawBytes[1] == 0x50.toByte() &&
                rawBytes[2] == 0x4E.toByte() &&
                rawBytes[3] == 0x47.toByte()

        if (isJpeg || isPng) {
            return rawBytes
        }

        // Convert WebP or other formats to high quality JPEG via BitmapFactory
        return try {
            val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
            if (bitmap != null) {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
                bitmap.recycle()
                stream.toByteArray()
            } else {
                rawBytes
            }
        } catch (_: Throwable) {
            rawBytes
        }
    }

    // =========================================================================
    // M4A / MP4 Tag Embedding (ISO 14496-12 & iTunes metadata spec)
    // =========================================================================

    private data class Mp4BoxInfo(
        val offset: Long,
        val totalSize: Long,
        val headerSize: Int,
        val type: String
    )

    private fun readBoxInfo(raf: RandomAccessFile, offset: Long, fileLength: Long): Mp4BoxInfo? {
        if (offset + 8 > fileLength) return null
        raf.seek(offset)
        val sizeInt = raf.readInt().toLong() and 0xFFFFFFFFL
        val typeBytes = ByteArray(4)
        raf.readFully(typeBytes)
        val type = String(typeBytes, StandardCharsets.US_ASCII)
        var headerSize = 8
        val totalSize = when (sizeInt) {
            1L -> {
                if (offset + 16 > fileLength) return null
                headerSize = 16
                raf.readLong()
            }
            0L -> fileLength - offset
            else -> sizeInt
        }
        if (totalSize < headerSize || offset + totalSize > fileLength) return null
        return Mp4BoxInfo(offset, totalSize, headerSize, type)
    }

    private fun embedM4aMetadata(
        audioFile: File,
        title: String,
        artist: String,
        album: String,
        artworkBytes: ByteArray?
    ): Boolean {
        val fileLength = audioFile.length()
        var moovBox: Mp4BoxInfo? = null
        var mdatBox: Mp4BoxInfo? = null
        val topBoxes = mutableListOf<Mp4BoxInfo>()

        RandomAccessFile(audioFile, "r").use { raf ->
            var currentOffset = 0L
            while (currentOffset < fileLength) {
                val box = readBoxInfo(raf, currentOffset, fileLength) ?: break
                topBoxes.add(box)
                if (box.type == "moov") moovBox = box
                if (box.type == "mdat") mdatBox = box
                currentOffset += box.totalSize
            }
        }

        val moov = moovBox ?: return false

        // Read moov payload
        val moovPayloadSize = (moov.totalSize - moov.headerSize).toInt()
        val moovPayload = ByteArray(moovPayloadSize)
        RandomAccessFile(audioFile, "r").use { raf ->
            raf.seek(moov.offset + moov.headerSize)
            raf.readFully(moovPayload)
        }

        // Separate existing child boxes inside moov, dropping any old 'udta'
        val preservedChildren = mutableListOf<ByteArray>()
        var p = 0
        while (p + 8 <= moovPayload.size) {
            val childSize = ByteBuffer.wrap(moovPayload, p, 4).int.toLong() and 0xFFFFFFFFL
            if (childSize < 8 || p + childSize > moovPayload.size) {
                preservedChildren.add(moovPayload.copyOfRange(p, moovPayload.size))
                break
            }
            val childType = String(moovPayload, p + 4, 4, StandardCharsets.US_ASCII)
            if (childType != "udta") {
                preservedChildren.add(moovPayload.copyOfRange(p, p + childSize.toInt()))
            }
            p += childSize.toInt()
        }

        // Construct new udta box containing meta, hdlr, and ilst
        val udtaBox = buildUdtaBox(title, artist, album, artworkBytes)
        val allChildren = preservedChildren + udtaBox
        val newMoovPayloadSize = allChildren.sumOf { it.size }
        val newMoovTotalSize = 8L + newMoovPayloadSize
        val delta = newMoovTotalSize - moov.totalSize

        // If moov is placed before mdat, expanding moov shifts mdat by delta.
        // Therefore, chunk offsets in all stco/co64 boxes must be adjusted by +delta.
        val isMoovBeforeMdat = mdatBox != null && moov.offset < mdatBox.offset
        if (isMoovBeforeMdat && delta != 0L) {
            for (child in allChildren) {
                adjustChunkOffsetsInBox(child, delta)
            }
        }

        // Build new moov box
        val newMoovBuf = ByteBuffer.allocate(newMoovTotalSize.toInt())
        newMoovBuf.putInt(newMoovTotalSize.toInt())
        newMoovBuf.put("moov".toByteArray(StandardCharsets.US_ASCII))
        for (child in allChildren) {
            newMoovBuf.put(child)
        }
        val newMoovBytes = newMoovBuf.array()

        // Write output to a temporary file
        val tempOutput = File(audioFile.parentFile, "${audioFile.name}.tagtmp")
        try {
            FileOutputStream(tempOutput).use { fos ->
                RandomAccessFile(audioFile, "r").use { raf ->
                    // 1. Copy everything before moov
                    if (moov.offset > 0L) {
                        copyStreamSegment(raf, 0L, moov.offset, fos)
                    }

                    // 2. Write new moov
                    fos.write(newMoovBytes)

                    // 3. Copy everything after old moov
                    val remainingStart = moov.offset + moov.totalSize
                    val remainingLength = fileLength - remainingStart
                    if (remainingLength > 0L) {
                        copyStreamSegment(raf, remainingStart, remainingLength, fos)
                    }
                }
            }

            if (tempOutput.exists() && tempOutput.length() > 1000L) {
                if (audioFile.delete()) {
                    tempOutput.renameTo(audioFile)
                    LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[TAGGER] Successfully tagged M4A '${audioFile.name}' with artwork and metadata")
                    return true
                }
            }
            return false
        } finally {
            if (tempOutput.exists()) tempOutput.delete()
        }
    }

    private fun buildUdtaBox(
        title: String,
        artist: String,
        album: String,
        artworkBytes: ByteArray?
    ): ByteArray {
        val tagList = mutableListOf<ByteArray>()

        // 1. Title (©nam)
        if (title.isNotBlank()) {
            val dataBox = buildDataBox(1, title.toByteArray(StandardCharsets.UTF_8))
            tagList.add(buildTagBox(TITLE_FOURCC, dataBox))
        }

        // 2. Artist (©ART)
        if (artist.isNotBlank()) {
            val dataBox = buildDataBox(1, artist.toByteArray(StandardCharsets.UTF_8))
            tagList.add(buildTagBox(ARTIST_FOURCC, dataBox))
        }

        // 3. Album (©alb)
        if (album.isNotBlank()) {
            val dataBox = buildDataBox(1, album.toByteArray(StandardCharsets.UTF_8))
            tagList.add(buildTagBox(ALBUM_FOURCC, dataBox))
        }

        // 4. Cover Art (covr)
        if (artworkBytes != null && artworkBytes.isNotEmpty()) {
            val isPng = artworkBytes.size > 8 &&
                    artworkBytes[0] == 0x89.toByte() &&
                    artworkBytes[1] == 0x50.toByte() &&
                    artworkBytes[2] == 0x4E.toByte() &&
                    artworkBytes[3] == 0x47.toByte()
            val typeFlags = if (isPng) 14 else 13 // 13 = JPEG, 14 = PNG
            val dataBox = buildDataBox(typeFlags, artworkBytes)
            tagList.add(buildTagBox(COVR_FOURCC, dataBox))
        }

        // Build ilst box
        val ilstPayloadSize = tagList.sumOf { it.size }
        val ilstTotalSize = 8 + ilstPayloadSize
        val ilstBuf = ByteBuffer.allocate(ilstTotalSize)
        ilstBuf.putInt(ilstTotalSize)
        ilstBuf.put("ilst".toByteArray(StandardCharsets.US_ASCII))
        tagList.forEach { ilstBuf.put(it) }
        val ilstBox = ilstBuf.array()

        // Build hdlr box (required by Android MediaScanner & Apple iTunes specs)
        val hdlrSize = 34
        val hdlrBuf = ByteBuffer.allocate(hdlrSize)
        hdlrBuf.putInt(hdlrSize)
        hdlrBuf.put("hdlr".toByteArray(StandardCharsets.US_ASCII))
        hdlrBuf.putInt(0) // version (1) + flags (3) = 0
        hdlrBuf.putInt(0) // pre-defined
        hdlrBuf.put("mdir".toByteArray(StandardCharsets.US_ASCII)) // handler type
        hdlrBuf.put(ByteArray(12)) // 12 reserved bytes
        hdlrBuf.put(0.toByte()) // null name
        hdlrBuf.put(0.toByte()) // alignment padding
        val hdlrBox = hdlrBuf.array()

        // Build meta box (FullBox: size, 'meta', version+flags = 0)
        val metaPayloadSize = hdlrBox.size + ilstBox.size
        val metaTotalSize = 12 + metaPayloadSize
        val metaBuf = ByteBuffer.allocate(metaTotalSize)
        metaBuf.putInt(metaTotalSize)
        metaBuf.put("meta".toByteArray(StandardCharsets.US_ASCII))
        metaBuf.putInt(0) // version (1) + flags (3)
        metaBuf.put(hdlrBox)
        metaBuf.put(ilstBox)
        val metaBox = metaBuf.array()

        // Build udta container box
        val udtaTotalSize = 8 + metaBox.size
        val udtaBuf = ByteBuffer.allocate(udtaTotalSize)
        udtaBuf.putInt(udtaTotalSize)
        udtaBuf.put("udta".toByteArray(StandardCharsets.US_ASCII))
        udtaBuf.put(metaBox)
        return udtaBuf.array()
    }

    private fun buildDataBox(typeFlags: Int, payload: ByteArray): ByteArray {
        val totalSize = 16 + payload.size
        val buf = ByteBuffer.allocate(totalSize)
        buf.putInt(totalSize)
        buf.put("data".toByteArray(StandardCharsets.US_ASCII))
        buf.putInt(typeFlags) // 1 = UTF-8 text, 13 = JPEG, 14 = PNG
        buf.putInt(0) // locale indicator
        buf.put(payload)
        return buf.array()
    }

    private fun buildTagBox(fourcc: ByteArray, dataBox: ByteArray): ByteArray {
        val totalSize = 8 + dataBox.size
        val buf = ByteBuffer.allocate(totalSize)
        buf.putInt(totalSize)
        buf.put(fourcc)
        buf.put(dataBox)
        return buf.array()
    }

    /**
     * Recursively traverses an MP4 box in-memory to find and update chunk offsets (stco and co64).
     */
    private fun adjustChunkOffsetsInBox(boxBytes: ByteArray, delta: Long) {
        var p = 8
        while (p + 8 <= boxBytes.size) {
            val size = ByteBuffer.wrap(boxBytes, p, 4).int.toLong() and 0xFFFFFFFFL
            if (size < 8 || p + size > boxBytes.size) break
            val type = String(boxBytes, p + 4, 4, StandardCharsets.US_ASCII)
            when (type) {
                "trak", "mdia", "minf", "stbl" -> {
                    val subBox = boxBytes.copyOfRange(p, p + size.toInt())
                    adjustChunkOffsetsInBox(subBox, delta)
                    System.arraycopy(subBox, 0, boxBytes, p, size.toInt())
                }
                "stco" -> {
                    if (p + 16 <= boxBytes.size) {
                        val count = ByteBuffer.wrap(boxBytes, p + 12, 4).int
                        var entryPos = p + 16
                        for (i in 0 until count) {
                            if (entryPos + 4 > boxBytes.size) break
                            val oldOffset = ByteBuffer.wrap(boxBytes, entryPos, 4).int.toLong() and 0xFFFFFFFFL
                            val newOffset = (oldOffset + delta) and 0xFFFFFFFFL
                            ByteBuffer.wrap(boxBytes, entryPos, 4).putInt(newOffset.toInt())
                            entryPos += 4
                        }
                    }
                }
                "co64" -> {
                    if (p + 16 <= boxBytes.size) {
                        val count = ByteBuffer.wrap(boxBytes, p + 12, 4).int
                        var entryPos = p + 16
                        for (i in 0 until count) {
                            if (entryPos + 8 > boxBytes.size) break
                            val oldOffset = ByteBuffer.wrap(boxBytes, entryPos, 8).long
                            val newOffset = oldOffset + delta
                            ByteBuffer.wrap(boxBytes, entryPos, 8).putLong(newOffset)
                            entryPos += 8
                        }
                    }
                }
            }
            p += size.toInt()
        }
    }

    private fun copyStreamSegment(raf: RandomAccessFile, startOffset: Long, length: Long, fos: FileOutputStream) {
        raf.seek(startOffset)
        val buffer = ByteArray(64 * 1024)
        var remaining = length
        while (remaining > 0) {
            val toRead = remaining.coerceAtMost(buffer.size.toLong()).toInt()
            val read = raf.read(buffer, 0, toRead)
            if (read == -1) break
            fos.write(buffer, 0, read)
            remaining -= read
        }
    }

    // =========================================================================
    // MP3 Tag Embedding (ID3v2.3)
    // =========================================================================

    private fun embedMp3Metadata(
        audioFile: File,
        title: String,
        artist: String,
        album: String,
        artworkBytes: ByteArray?
    ): Boolean {
        var oldId3Size = 0L
        RandomAccessFile(audioFile, "r").use { raf ->
            val header = ByteArray(10)
            if (raf.read(header) == 10) {
                if (header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
                    val size = synchsafeToInt(header[6], header[7], header[8], header[9])
                    val hasFooter = (header[5].toInt() and 0x10) != 0
                    oldId3Size = 10L + size + if (hasFooter) 10L else 0L
                }
            }
        }

        val frames = mutableListOf<ByteArray>()
        if (title.isNotBlank()) frames.add(buildId3TextFrame("TIT2", title))
        if (artist.isNotBlank()) frames.add(buildId3TextFrame("TPE1", artist))
        if (album.isNotBlank()) frames.add(buildId3TextFrame("TALB", album))
        if (artworkBytes != null && artworkBytes.isNotEmpty()) {
            frames.add(buildId3ApicFrame(artworkBytes))
        }

        val framesSize = frames.sumOf { it.size }
        val id3Header = ByteBuffer.allocate(10)
        id3Header.put("ID3".toByteArray(StandardCharsets.US_ASCII))
        id3Header.put(3.toByte()) // ID3v2.3
        id3Header.put(0.toByte()) // revision
        id3Header.put(0.toByte()) // flags
        id3Header.put(intToSynchsafe(framesSize))

        val tempOutput = File(audioFile.parentFile, "${audioFile.name}.mp3tmp")
        try {
            FileOutputStream(tempOutput).use { fos ->
                fos.write(id3Header.array())
                frames.forEach { fos.write(it) }

                RandomAccessFile(audioFile, "r").use { raf ->
                    val fileLength = audioFile.length()
                    val audioLength = fileLength - oldId3Size
                    if (audioLength > 0L) {
                        copyStreamSegment(raf, oldId3Size, audioLength, fos)
                    }
                }
            }

            if (tempOutput.exists() && tempOutput.length() > 1000L) {
                if (audioFile.delete()) {
                    tempOutput.renameTo(audioFile)
                    LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[TAGGER] Successfully tagged MP3 '${audioFile.name}' with artwork and metadata")
                    return true
                }
            }
            return false
        } finally {
            if (tempOutput.exists()) tempOutput.delete()
        }
    }

    private fun buildId3TextFrame(frameId: String, text: String): ByteArray {
        val textBytes = text.toByteArray(StandardCharsets.UTF_8)
        val frameSize = 1 + textBytes.size // 1 byte encoding + text
        val buf = ByteBuffer.allocate(10 + frameSize)
        buf.put(frameId.toByteArray(StandardCharsets.US_ASCII))
        buf.putInt(frameSize)
        buf.putShort(0) // flags
        buf.put(3.toByte()) // 3 = UTF-8 encoding
        buf.put(textBytes)
        return buf.array()
    }

    private fun buildId3ApicFrame(imageBytes: ByteArray): ByteArray {
        val isPng = imageBytes.size > 8 &&
                imageBytes[0] == 0x89.toByte() &&
                imageBytes[1] == 0x50.toByte() &&
                imageBytes[2] == 0x4E.toByte() &&
                imageBytes[3] == 0x47.toByte()
        val mime = if (isPng) "image/png" else "image/jpeg"
        val mimeBytes = mime.toByteArray(StandardCharsets.ISO_8859_1)

        val frameSize = 1 + mimeBytes.size + 1 + 1 + 1 + imageBytes.size
        val buf = ByteBuffer.allocate(10 + frameSize)
        buf.put("APIC".toByteArray(StandardCharsets.US_ASCII))
        buf.putInt(frameSize)
        buf.putShort(0) // flags
        buf.put(0.toByte()) // ISO-8859-1 for MIME and description
        buf.put(mimeBytes)
        buf.put(0.toByte()) // null terminator
        buf.put(3.toByte()) // 0x03 = Cover (front)
        buf.put(0.toByte()) // empty description null terminator
        buf.put(imageBytes)
        return buf.array()
    }

    private fun synchsafeToInt(b0: Byte, b1: Byte, b2: Byte, b3: Byte): Int {
        return ((b0.toInt() and 0x7F) shl 21) or
                ((b1.toInt() and 0x7F) shl 14) or
                ((b2.toInt() and 0x7F) shl 7) or
                (b3.toInt() and 0x7F)
    }

    private fun intToSynchsafe(value: Int): ByteArray {
        return byteArrayOf(
            ((value shr 21) and 0x7F).toByte(),
            ((value shr 14) and 0x7F).toByte(),
            ((value shr 7) and 0x7F).toByte(),
            (value and 0x7F).toByte()
        )
    }
}

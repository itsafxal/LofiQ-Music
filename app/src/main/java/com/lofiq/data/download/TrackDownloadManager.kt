package com.lofiq.data.download

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.common.LofiQLogger
import com.lofiq.core.database.dao.FavoriteDao
import com.lofiq.core.database.dao.SongDao
import com.lofiq.core.database.entities.SongEntity
import com.lofiq.core.model.Song
import com.lofiq.data.repository.toEntity
import com.lofiq.resolver.core.ResolveRequest
import com.lofiq.resolver.manager.ResolverManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

enum class DownloadState {
    IDLE,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

data class TrackDownloadInfo(
    val songId: String,
    val state: DownloadState = DownloadState.IDLE,
    val progress: Float = 0f, // 0.0f to 1.0f
    val localFilePath: String? = null
)

@Singleton
class TrackDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resolverManager: ResolverManager,
    private val songDao: SongDao,
    private val favoriteDao: FavoriteDao,
    private val okHttpClient: OkHttpClient,
    private val dispatchers: DispatcherProvider
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val _downloads = MutableStateFlow<Map<String, TrackDownloadInfo>>(emptyMap())
    val downloads: StateFlow<Map<String, TrackDownloadInfo>> = _downloads.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Job>()

    init {
        ensureLofiQDirectoryExists()
        reconcileDownloads()
        scope.launch {
            songDao.observeDownloads().collect { downloadedEntities ->
                _downloads.update { current ->
                    val updated = current.toMutableMap()
                    for (entity in downloadedEntities) {
                        val file = File(entity.sourceUrl)
                        val lofiqFile = getPublicFileForSong(entity.id, entity.title, entity.artist)
                        val legacyFile = getLegacyDownloadFile(entity.id)

                        val resolvedPath = when {
                            file.exists() && file.length() > 10_000L -> file.absolutePath
                            lofiqFile.exists() && lofiqFile.length() > 10_000L -> lofiqFile.absolutePath
                            legacyFile.exists() && legacyFile.length() > 10_000L -> {
                                migrateLegacyDownload(entity, legacyFile)
                                lofiqFile.absolutePath
                            }
                            entity.sourceUrl.startsWith("content://") -> entity.sourceUrl
                            else -> null
                        }

                        if (resolvedPath != null) {
                            if (updated[entity.id]?.state != DownloadState.DOWNLOADING) {
                                updated[entity.id] = TrackDownloadInfo(
                                    songId = entity.id,
                                    state = DownloadState.COMPLETED,
                                    progress = 1f,
                                    localFilePath = resolvedPath
                                )
                            }
                        }
                    }
                    updated
                }
            }
        }
    }

    /**
     * Ensures the Music/LofiQ public directory exists in device shared storage.
     */
    fun ensureLofiQDirectoryExists(): File {
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val lofiqDir = File(musicDir, "LofiQ")
        try {
            if (!lofiqDir.exists()) {
                lofiqDir.mkdirs()
            }
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[DOWNLOAD] Cannot create Music/LofiQ folder: ${e.message}")
        }
        return lofiqDir
    }

    fun getPublicLofiQDir(): File {
        return ensureLofiQDirectoryExists()
    }

    fun sanitizeFileName(name: String): String {
        return name.replace(Regex("""[\\/:*?"<>|]"""), "_")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun buildDisplayFileName(title: String, artist: String, fallbackId: String): String {
        val cleanTitle = sanitizeFileName(title).ifBlank { "Track_$fallbackId" }
        val cleanArtist = sanitizeFileName(artist).ifBlank { "Unknown Artist" }
        return "$cleanTitle - $cleanArtist.m4a"
    }

    fun getPublicFileForSong(songId: String, title: String? = null, artist: String? = null): File {
        val dir = getPublicLofiQDir()
        val fileName = if (!title.isNullOrBlank()) {
            buildDisplayFileName(title, artist ?: "", songId)
        } else {
            "${songId.replace(Regex("""[^a-zA-Z0-9_-]"""), "_")}.m4a"
        }
        return File(dir, fileName)
    }

    fun getPrivateDownloadFile(songId: String, title: String? = null, artist: String? = null): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir, "downloads")
        val fileName = if (!title.isNullOrBlank()) {
            buildDisplayFileName(title, artist ?: "", songId)
        } else {
            "${songId.replace(Regex("""[^a-zA-Z0-9_-]"""), "_")}.m4a"
        }
        return File(dir, fileName)
    }

    private fun getLegacyDownloadFile(songId: String): File {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "downloads")
        return File(dir, "${songId.replace(Regex("""[^a-zA-Z0-9_-]"""), "_")}.m4a")
    }

    fun getDownloadedFile(songId: String, title: String? = null, artist: String? = null): File? {
        val info = _downloads.value[songId]
        if (info?.localFilePath != null) {
            val f = File(info.localFilePath)
            if (f.exists() && f.length() > 10_000L) return f
        }
        val lofiqFile = getPublicFileForSong(songId, title, artist)
        if (lofiqFile.exists() && lofiqFile.length() > 10_000L) return lofiqFile

        val legacyFile = getLegacyDownloadFile(songId)
        if (legacyFile.exists() && legacyFile.length() > 10_000L) return legacyFile

        val privateFile = getPrivateDownloadFile(songId, title, artist)
        if (privateFile.exists() && privateFile.length() > 10_000L) return privateFile

        return null
    }

    fun getDownloadInfo(songId: String): TrackDownloadInfo {
        return _downloads.value[songId] ?: TrackDownloadInfo(songId)
    }

    fun isDownloaded(song: Song): Boolean {
        val info = _downloads.value[song.id]
        if (info?.state == DownloadState.COMPLETED) {
            val path = info.localFilePath
            if (path != null) {
                if (path.startsWith("content://")) return true
                val f = File(path)
                if (f.exists() && f.length() > 10_000L) return true
            }
        }
        val file = getDownloadedFile(song.id, song.title, song.artist)
        if (file != null && file.exists() && file.length() > 10_000L) return true
        if (song.sourceUrl.isNotBlank() && (song.sourceUrl.startsWith("content://") || (File(song.sourceUrl).exists() && File(song.sourceUrl).length() > 10_000L))) return true
        return false
    }

    fun isDownloaded(songId: String): Boolean {
        val info = _downloads.value[songId]
        if (info?.state == DownloadState.COMPLETED) {
            val path = info.localFilePath
            if (path != null) {
                if (path.startsWith("content://")) return true
                val f = File(path)
                if (f.exists() && f.length() > 10_000L) return true
            }
        }
        val file = getDownloadedFile(songId)
        return file != null && file.exists() && file.length() > 10_000L
    }

    fun downloadSong(song: Song, saveToPublicStorage: Boolean = true) {
        val songId = song.id
        if (isDownloaded(song)) {
            val existingFile = getDownloadedFile(song.id, song.title, song.artist)
            val filePath = existingFile?.absolutePath ?: song.sourceUrl
            _downloads.update {
                it + (songId to TrackDownloadInfo(songId, DownloadState.COMPLETED, 1f, filePath))
            }
            scope.launch {
                val existing = songDao.getById(songId)
                val entity = (existing ?: song.toEntity()).copy(
                    sourceUrl = filePath.ifBlank { existing?.sourceUrl.orEmpty() },
                    providerId = "local_download"
                )
                songDao.insertOrUpdate(entity)
            }
            return
        }

        activeJobs[songId]?.cancel()
        val job = scope.launch {
            val tempFile = File(context.cacheDir, "lofiq_dl_${songId.hashCode()}_${System.currentTimeMillis()}.tmp")
            try {
                _downloads.update {
                    it + (songId to TrackDownloadInfo(songId, DownloadState.DOWNLOADING, 0.05f))
                }

                LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[DOWNLOAD] Resolving stream for '${song.title}'")
                val resolveReq = ResolveRequest(
                    songId = song.id,
                    songTitle = song.title,
                    artistName = song.artist,
                    albumName = song.album,
                    durationSeconds = song.durationSeconds,
                    preferredQuality = song.quality
                )
                val resolution = resolverManager.resolve(resolveReq)
                val streamUrl = resolution.streamUrl

                if (streamUrl.isBlank()) {
                    throw IllegalStateException("Unable to resolve audio stream URL")
                }

                _downloads.update {
                    it + (songId to TrackDownloadInfo(songId, DownloadState.DOWNLOADING, 0.20f))
                }

                val httpRequestBuilder = Request.Builder()
                    .url(streamUrl)

                resolution.headers.forEach { (key, value) ->
                    httpRequestBuilder.header(key, value)
                }
                if (!resolution.headers.containsKey("User-Agent")) {
                    httpRequestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                }

                val httpRequest = httpRequestBuilder.build()

                val response = okHttpClient.newCall(httpRequest).execute()
                if (!response.isSuccessful) throw IllegalStateException("Download HTTP ${response.code}")

                val body = response.body ?: throw IllegalStateException("Empty response body")
                val contentLength = body.contentLength().coerceAtLeast(1L)

                if (tempFile.exists()) {
                    tempFile.delete()
                }

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        var totalRead = 0L
                        var lastProgress = 0.20f

                        while (input.read(buffer).also { bytesRead = it } != -1 && isActive) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            val currentProgress = 0.20f + (totalRead.toFloat() / contentLength.toFloat()) * 0.65f
                            if (currentProgress - lastProgress >= 0.04f || currentProgress >= 0.85f) {
                                lastProgress = currentProgress
                                _downloads.update {
                                    it + (songId to TrackDownloadInfo(songId, DownloadState.DOWNLOADING, currentProgress.coerceIn(0f, 0.85f)))
                                }
                            }
                        }
                    }
                }

                if (!isActive) {
                    if (tempFile.exists()) tempFile.delete()
                    return@launch
                }

                if (tempFile.exists() && tempFile.length() > 10_000L) {
                    // Update progress while downloading artwork & embedding tags
                    _downloads.update {
                        it + (songId to TrackDownloadInfo(songId, DownloadState.DOWNLOADING, 0.90f))
                    }

                    // 1. Fetch album art image bytes
                    val artworkUrl = song.highResArtworkUrl.ifBlank { song.artworkUrl }
                    val artworkBytes = if (artworkUrl.isNotBlank()) {
                        fetchArtworkBytes(artworkUrl)
                    } else null

                    // 2. Embed cover artwork, title, artist & album tags into the audio container
                    runCatching {
                        AudioTagEmbedder.embedMetadata(
                            audioFile = tempFile,
                            title = song.title,
                            artist = song.artist,
                            album = song.album.ifBlank { "LofiQ Downloads" },
                            rawArtworkBytes = artworkBytes
                        )
                    }.onFailure { e ->
                        LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[DOWNLOAD] Tag embedding error for '${song.title}': ${e.message}")
                    }

                    _downloads.update {
                        it + (songId to TrackDownloadInfo(songId, DownloadState.DOWNLOADING, 0.96f))
                    }

                    // 3. Save to Public Storage (Music/LofiQ) or Private in-app storage based on permission
                    val displayFileName = buildDisplayFileName(song.title, song.artist, song.id)
                    val (savedPath, contentUri) = if (saveToPublicStorage) {
                        saveAudioToPublicLofiQ(song, tempFile, displayFileName)
                    } else {
                        saveAudioToPrivateAppStorage(song, tempFile, displayFileName)
                    }

                    val finalSourceUrl = savedPath.ifBlank { contentUri?.toString().orEmpty() }

                    // Save into Room Song Database
                    songDao.insertOrUpdate(
                        SongEntity(
                            id = song.id,
                            title = song.title,
                            artist = song.artist,
                            album = song.album,
                            durationSeconds = song.durationSeconds,
                            artworkUrl = song.artworkUrl,
                            highResArtworkUrl = song.highResArtworkUrl,
                            sourceUrl = finalSourceUrl,
                            providerId = "local_download",
                            quality = song.quality.name,
                            addedAt = System.currentTimeMillis()
                        )
                    )

                    _downloads.update {
                        it + (songId to TrackDownloadInfo(songId, DownloadState.COMPLETED, 1f, finalSourceUrl))
                    }
                    LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[DOWNLOAD] Successfully saved '${song.title}' (public=$saveToPublicStorage, path=$finalSourceUrl)")
                } else {
                    if (tempFile.exists()) tempFile.delete()
                    throw IllegalStateException("Downloaded file is empty or corrupted")
                }

            } catch (e: CancellationException) {
                if (tempFile.exists()) tempFile.delete()
                _downloads.update { it + (songId to TrackDownloadInfo(songId, DownloadState.IDLE)) }
            } catch (e: Exception) {
                if (tempFile.exists()) tempFile.delete()
                LofiQLogger.e(LofiQLogger.Category.PLAYBACK, "[DOWNLOAD] Failed for '${song.title}': ${e.message}")
                _downloads.update { it + (songId to TrackDownloadInfo(songId, DownloadState.FAILED)) }
            } finally {
                if (tempFile.exists()) tempFile.delete()
                activeJobs.remove(songId)
            }
        }
        activeJobs[songId] = job
    }

    private suspend fun fetchArtworkBytes(url: String): ByteArray? {
        return withContext(dispatchers.io) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()
                val response = okHttpClient.newCall(req).execute()
                if (response.isSuccessful) {
                    val body = response.body ?: return@withContext null
                    val bytes = body.bytes()
                    if (bytes.size > 64) bytes else null
                } else null
            } catch (e: Exception) {
                LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[DOWNLOAD] Failed to fetch artwork from $url: ${e.message}")
                null
            }
        }
    }

    private fun saveAudioToPrivateAppStorage(
        song: Song,
        tempFile: File,
        fileName: String
    ): Pair<String, Uri?> {
        val privateDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir, "downloads")
        if (!privateDir.exists()) {
            privateDir.mkdirs()
        }
        val targetFile = File(privateDir, fileName)
        if (targetFile.exists()) targetFile.delete()
        tempFile.copyTo(targetFile, overwrite = true)
        LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[DOWNLOAD] Saved '${song.title}' to private in-app storage: ${targetFile.absolutePath}")
        return Pair(targetFile.absolutePath, null)
    }

    private fun saveAudioToPublicLofiQ(
        song: Song,
        tempFile: File,
        fileName: String
    ): Pair<String, Uri?> {
        val lofiqDir = ensureLofiQDirectoryExists()
        var resultPath = ""
        var resultUri: Uri? = null

        // On Android 10+ (API 29+), use MediaStore to save directly into Music/LofiQ
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                // Remove any stale existing entry with identical name
                val existingUri = findMediaStoreUri(fileName)
                if (existingUri != null) {
                    runCatching { context.contentResolver.delete(existingUri, null, null) }
                }

                val contentValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.TITLE, song.title)
                    put(MediaStore.Audio.Media.ARTIST, song.artist)
                    put(MediaStore.Audio.Media.ALBUM, song.album)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/LofiQ")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                    put(MediaStore.Audio.Media.IS_MUSIC, 1)
                    if (song.durationSeconds > 0) {
                        put(MediaStore.Audio.Media.DURATION, song.durationSeconds * 1000)
                    }
                }

                val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        tempFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, contentValues, null, null)

                    resultUri = uri
                    resultPath = getFilePathFromContentUri(uri) ?: File(lofiqDir, fileName).absolutePath
                }
            } catch (e: Exception) {
                LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[DOWNLOAD] MediaStore insert error: ${e.message}, falling back to direct file")
            }
        }

        // Direct File copy fallback (for API < 29 or if MediaStore insert failed)
        if (resultPath.isBlank()) {
            val targetFile = File(lofiqDir, fileName)
            if (targetFile.exists()) targetFile.delete()
            tempFile.copyTo(targetFile, overwrite = true)
            resultPath = targetFile.absolutePath
        }

        // Always notify Android MediaScanner so all other music players on the device index the track immediately!
        if (resultPath.isNotBlank()) {
            runCatching {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(resultPath),
                    arrayOf("audio/mp4")
                ) { path, scannedUri ->
                    LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[DOWNLOAD] MediaScanner indexed: path=$path uri=$scannedUri")
                }
            }
        }

        return Pair(resultPath, resultUri)
    }

    private fun migrateLegacyDownload(entity: SongEntity, legacyFile: File) {
        scope.launch {
            try {
                val displayFileName = buildDisplayFileName(entity.title, entity.artist, entity.id)
                val targetFile = File(ensureLofiQDirectoryExists(), displayFileName)
                if (!targetFile.exists()) {
                    legacyFile.copyTo(targetFile, overwrite = true)
                }
                runCatching { legacyFile.delete() }

                val (path, uri) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveAudioToPublicLofiQ(
                        Song(
                            id = entity.id,
                            title = entity.title,
                            artist = entity.artist,
                            album = entity.album,
                            durationSeconds = entity.durationSeconds,
                            artworkUrl = entity.artworkUrl,
                            highResArtworkUrl = entity.highResArtworkUrl
                        ),
                        targetFile,
                        displayFileName
                    )
                } else {
                    Pair(targetFile.absolutePath, null)
                }

                val finalPath = path.ifBlank { targetFile.absolutePath }
                songDao.insertOrUpdate(entity.copy(sourceUrl = finalPath))
                _downloads.update { it + (entity.id to TrackDownloadInfo(entity.id, DownloadState.COMPLETED, 1f, finalPath)) }
                LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[DOWNLOAD] Migrated legacy track '${entity.title}' to Music/LofiQ")
            } catch (e: Exception) {
                LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[DOWNLOAD] Failed migrating legacy download ${entity.id}: ${e.message}")
            }
        }
    }

    private fun findMediaStoreUri(fileName: String): Uri? {
        return try {
            val projection = arrayOf(MediaStore.Audio.Media._ID)
            val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(fileName)
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getFilePathFromContentUri(uri: Uri): String? {
        return try {
            val proj = arrayOf(MediaStore.Audio.Media.DATA)
            context.contentResolver.query(uri, proj, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    if (idx != -1) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun deleteDownload(songId: String) {
        activeJobs[songId]?.cancel()
        activeJobs.remove(songId)
        scope.launch {
            try {
                val entity = songDao.getById(songId)
                val targetPath = entity?.sourceUrl ?: _downloads.value[songId]?.localFilePath

                if (!targetPath.isNullOrBlank()) {
                    if (targetPath.startsWith("content://")) {
                        runCatching { context.contentResolver.delete(Uri.parse(targetPath), null, null) }
                    } else {
                        val file = File(targetPath)
                        if (file.exists()) file.delete()
                        runCatching {
                            context.contentResolver.delete(
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                "${MediaStore.Audio.Media.DATA} = ?",
                                arrayOf(targetPath)
                            )
                        }
                    }
                }

                // Clean up any matching file in Music/LofiQ
                val lofiqFile = getPublicFileForSong(songId, entity?.title, entity?.artist)
                if (lofiqFile.exists()) {
                    lofiqFile.delete()
                }

                // Clean up legacy downloads
                val legacyFile = getLegacyDownloadFile(songId)
                if (legacyFile.exists()) {
                    legacyFile.delete()
                }

                // If song is also a favorite, preserve the song in database so Liked Songs doesn't lose it
                if (entity != null && favoriteDao.isFavorite(songId)) {
                    songDao.removeDownloadStatus(songId, fallbackProviderId = "youtube")
                } else {
                    songDao.deleteDownload(songId)
                }

                _downloads.update { it - songId }
                LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[DOWNLOAD] Deleted downloaded track $songId")
            } catch (e: Exception) {
                LofiQLogger.e(LofiQLogger.Category.PLAYBACK, "[DOWNLOAD] Failed to delete download $songId: ${e.message}")
            }
        }
    }

    /**
     * Scans Music/LofiQ and internal downloads folder, discovering any downloaded songs
     * on the device storage and ensuring they are populated into Room database and downloads state.
     */
    fun reconcileDownloads() {
        scope.launch {
            try {
                val lofiqDir = getPublicLofiQDir()
                val legacyDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "downloads")

                val currentDownloads = songDao.observeDownloads().firstOrNull() ?: emptyList()
                val currentDownloadsById = currentDownloads.associateBy { it.id }.toMutableMap()
                val currentDownloadsByPath = currentDownloads.associateBy { it.sourceUrl }.toMutableMap()

                val allKnownSongs = songDao.observeAll().firstOrNull() ?: emptyList()
                val allSongsByTitleArtist = allKnownSongs.associateBy {
                    "${sanitizeFileName(it.title).lowercase()}||${sanitizeFileName(it.artist).lowercase()}"
                }

                // 1. Scan Music/LofiQ for all offline audio files
                val lofiqFiles = lofiqDir.listFiles { file ->
                    file.isFile && file.length() > 10_000L && (file.extension.equals("m4a", ignoreCase = true) || file.extension.equals("mp3", ignoreCase = true))
                } ?: emptyArray()

                val songsToUpsert = mutableListOf<SongEntity>()
                val infoUpdates = mutableMapOf<String, TrackDownloadInfo>()

                for (file in lofiqFiles) {
                    val filePath = file.absolutePath
                    val existingDl = currentDownloadsByPath[filePath]
                    if (existingDl != null) {
                        infoUpdates[existingDl.id] = TrackDownloadInfo(existingDl.id, DownloadState.COMPLETED, 1f, filePath)
                        continue
                    }

                    // Match with known songs
                    val fileNameWithoutExt = file.nameWithoutExtension
                    var matchedSong: SongEntity? = null

                    if (fileNameWithoutExt.contains(" - ")) {
                        val parts = fileNameWithoutExt.split(" - ", limit = 2)
                        val titlePart = sanitizeFileName(parts[0]).lowercase()
                        val artistPart = sanitizeFileName(parts[1]).lowercase()
                        matchedSong = allSongsByTitleArtist["$titlePart||$artistPart"]
                    }

                    if (matchedSong == null && fileNameWithoutExt.contains(" - ")) {
                        val titlePart = sanitizeFileName(fileNameWithoutExt.split(" - ", limit = 2)[0]).lowercase()
                        matchedSong = allKnownSongs.firstOrNull {
                            sanitizeFileName(it.title).lowercase() == titlePart
                        }
                    }

                    if (matchedSong == null) {
                        matchedSong = allKnownSongs.firstOrNull {
                            it.id == fileNameWithoutExt ||
                            it.id.replace(Regex("""[^a-zA-Z0-9_-]"""), "_") == fileNameWithoutExt ||
                            fileNameWithoutExt.endsWith(it.id)
                        }
                    }

                    if (matchedSong != null) {
                        val updated = matchedSong.copy(
                            sourceUrl = filePath,
                            providerId = "local_download",
                            addedAt = if (matchedSong.providerId == "local_download") matchedSong.addedAt else file.lastModified()
                        )
                        songsToUpsert.add(updated)
                        infoUpdates[updated.id] = TrackDownloadInfo(updated.id, DownloadState.COMPLETED, 1f, filePath)
                        currentDownloadsById[updated.id] = updated
                        currentDownloadsByPath[filePath] = updated
                    } else {
                        val extracted = extractMetadataFromFile(file)
                        if (extracted != null) {
                            songsToUpsert.add(extracted)
                            infoUpdates[extracted.id] = TrackDownloadInfo(extracted.id, DownloadState.COMPLETED, 1f, filePath)
                            currentDownloadsById[extracted.id] = extracted
                            currentDownloadsByPath[filePath] = extracted
                        }
                    }
                }

                // 2. Scan legacy internal downloads directory
                val legacyFiles = legacyDir.listFiles { file ->
                    file.isFile && file.length() > 10_000L && file.extension.equals("m4a", ignoreCase = true)
                } ?: emptyArray()

                for (legacyFile in legacyFiles) {
                    val rawId = legacyFile.nameWithoutExtension
                    val matchedSong = allKnownSongs.firstOrNull {
                        it.id.replace(Regex("""[^a-zA-Z0-9_-]"""), "_") == rawId || it.id == rawId
                    }
                    if (matchedSong != null) {
                        migrateLegacyDownload(matchedSong, legacyFile)
                    }
                }

                // Batch persist all newly discovered or updated songs in a single transaction
                if (songsToUpsert.isNotEmpty()) {
                    songDao.insertOrUpdateAll(songsToUpsert)
                }
                if (infoUpdates.isNotEmpty()) {
                    _downloads.update { it + infoUpdates }
                }

                // 3. Clean up genuinely deleted downloads where file no longer exists anywhere
                val existingFilePaths = (lofiqFiles.map { it.absolutePath } + legacyFiles.map { it.absolutePath }).toSet()
                val existingFileNames = lofiqFiles.map { it.name }.toSet()
                val deletedIds = mutableListOf<String>()

                for ((id, entity) in currentDownloadsById) {
                    if (activeJobs[id]?.isActive == true) {
                        continue // Currently downloading, never delete
                    }
                    val f = File(entity.sourceUrl)
                    val publicF = getPublicFileForSong(entity.id, entity.title, entity.artist)
                    val privateF = getPrivateDownloadFile(entity.id, entity.title, entity.artist)
                    val legacyF = getLegacyDownloadFile(entity.id)
                    val exists = (entity.sourceUrl.isNotBlank() && (existingFilePaths.contains(entity.sourceUrl) || (f.exists() && f.length() > 10_000L))) ||
                                 existingFilePaths.contains(publicF.absolutePath) ||
                                 existingFileNames.contains(publicF.name) ||
                                 (publicF.exists() && publicF.length() > 10_000L) ||
                                 (privateF.exists() && privateF.length() > 10_000L) ||
                                 (legacyF.exists() && legacyF.length() > 10_000L) ||
                                 entity.sourceUrl.startsWith("content://") ||
                                 findMediaStoreUri(publicF.name) != null ||
                                 lofiqFiles.any {
                                     it.nameWithoutExtension.contains(entity.id) ||
                                     (entity.title.isNotBlank() && it.nameWithoutExtension.startsWith(sanitizeFileName(entity.title), ignoreCase = true))
                                 }

                    if (!exists) {
                        deletedIds.add(id)
                    }
                }

                for (id in deletedIds) {
                    if (favoriteDao.isFavorite(id)) {
                        songDao.removeDownloadStatus(id, fallbackProviderId = "youtube")
                    } else {
                        songDao.deleteDownload(id)
                    }
                }
                if (deletedIds.isNotEmpty()) {
                    _downloads.update { it - deletedIds.toSet() }
                }
            } catch (e: Exception) {
                LofiQLogger.e(LofiQLogger.Category.PLAYBACK, "[DOWNLOAD] Reconcile error: ${e.message}")
            }
        }
    }

    private fun extractMetadataFromFile(file: File): SongEntity? {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val title = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.trim()
                ?.ifBlank { null }
                ?: if (file.nameWithoutExtension.contains(" - ")) file.nameWithoutExtension.substringBefore(" - ").trim() else file.nameWithoutExtension
            val artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.trim()
                ?.ifBlank { null }
                ?: if (file.nameWithoutExtension.contains(" - ")) file.nameWithoutExtension.substringAfter(" - ").trim() else "Offline Track"
            val album = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim() ?: "Downloads"
            val durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val durationSec = (durationMs / 1000L).coerceAtLeast(0L)

            val stableId = "dl_" + file.name.replace(Regex("""[^a-zA-Z0-9_-]"""), "_")

            var embeddedArtUrl = ""
            val pic = retriever.embeddedPicture
            if (pic != null && pic.isNotEmpty()) {
                runCatching {
                    val artDir = File(context.filesDir, "extracted_art")
                    if (!artDir.exists()) artDir.mkdirs()
                    val artFile = File(artDir, "${stableId}.jpg")
                    if (!artFile.exists() || artFile.length() < 100L) {
                        artFile.writeBytes(pic)
                    }
                    embeddedArtUrl = "file://${artFile.absolutePath}"
                }
            }

            SongEntity(
                id = stableId,
                title = title.ifBlank { file.nameWithoutExtension },
                artist = artist.ifBlank { "Unknown Artist" },
                album = album,
                durationSeconds = durationSec,
                artworkUrl = embeddedArtUrl,
                highResArtworkUrl = embeddedArtUrl,
                sourceUrl = file.absolutePath,
                providerId = "local_download",
                quality = "HIGH",
                addedAt = file.lastModified()
            )
        } catch (e: Exception) {
            LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[DOWNLOAD] Cannot extract metadata from ${file.name}: ${e.message}")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}

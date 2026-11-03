package com.lofiq.feature.local

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.lofiq.core.common.TimeFormatUtils
import com.lofiq.core.model.AudioQuality
import com.lofiq.core.model.Song
import com.lofiq.playback.engine.PlaybackEngine
import com.lofiq.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMusicScreen(
    playbackEngine: PlaybackEngine,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val audioPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    var permissionRequestedOnce by remember { mutableStateOf(false) }
    var permissionDeniedByUser by remember { mutableStateOf(false) }
    var localSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            permissionDeniedByUser = false
            isLoading = true
            coroutineScope.launch {
                localSongs = scanDeviceAudio(context)
                isLoading = false
            }
        } else {
            permissionDeniedByUser = true
            isLoading = false
        }
    }

    // Auto-prompt system dialog on entry if not already granted; if already granted, directly scan
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            isLoading = true
            localSongs = scanDeviceAudio(context)
            isLoading = false
        } else if (!permissionRequestedOnce) {
            permissionRequestedOnce = true
            permissionLauncher.launch(audioPermission)
        }
    }

    val playerState by playbackEngine.playerState.collectAsState()
    val currentPlayingId = playerState.currentSong?.id

    val filteredSongs = remember(localSongs, searchQuery) {
        if (searchQuery.isBlank()) localSongs
        else localSongs.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true) ||
            it.album.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Local Music",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = AccentWhite
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = AccentWhite
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (hasPermission) {
                            isLoading = true
                            coroutineScope.launch {
                                localSongs = scanDeviceAudio(context)
                                isLoading = false
                            }
                        } else {
                            permissionLauncher.launch(audioPermission)
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh",
                            tint = AccentWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!hasPermission && permissionDeniedByUser) {
                // Storage permission prompt card only shown if user explicitly denied system dialog
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(SurfaceElevated)
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(24.dp))
                            .padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2563EB).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FolderShared,
                                contentDescription = null,
                                tint = Color(0xFF60A5FA),
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Text(
                            text = "Storage Permission Needed",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = AccentWhite,
                                fontSize = 20.sp
                            ),
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "To browse and play music files stored on your device without internet, LofiQ needs access to your local audio storage.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = TextSecondary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            ),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = onNavigateBack,
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Go Back", fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = {
                                    permissionLauncher.launch(audioPermission)
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2563EB),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.weight(1.3f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Allow Access", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else if (!hasPermission || isLoading) {
                // Sleek loading while system dialog is pending or audio is being scanned
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF2563EB),
                            modifier = Modifier.size(38.dp),
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = if (isLoading) "Scanning local music files..." else "Accessing audio storage...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            } else {
                // Search Input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search local tracks or artists...", color = TextTertiary) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = TextSecondary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear",
                                    tint = TextSecondary
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceBlack,
                        unfocusedContainerColor = SurfaceBlack,
                        focusedTextColor = AccentWhite,
                        unfocusedTextColor = AccentWhite,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = SurfaceElevated
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                )

                // Header info & Play All
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${filteredSongs.size} tracks found",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    )

                    if (filteredSongs.isNotEmpty()) {
                        Button(
                            onClick = { playbackEngine.playQueue(filteredSongs, 0) },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Play All",
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Play All",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (filteredSongs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FolderOff,
                                contentDescription = null,
                                tint = TextTertiary,
                                modifier = Modifier.size(54.dp)
                            )
                            Text(
                                text = if (searchQuery.isBlank()) "No audio files found on device" else "No matches found",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        itemsIndexed(filteredSongs) { index, song ->
                            val isCurrent = song.id == currentPlayingId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { playbackEngine.playQueue(filteredSongs, index) }
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Thumbnail / Note Icon
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SurfaceBlack),
                                    contentAlignment = Alignment.Center
                                ) {
                                    coil.compose.AsyncImage(
                                        model = coil.request.ImageRequest.Builder(LocalContext.current)
                                            .data(song.artworkUrl.ifEmpty { song.id })
                                            .crossfade(true)
                                            .error(com.lofiq.R.drawable.app_logo_transparent)
                                            .fallback(com.lofiq.R.drawable.app_logo_transparent)
                                            .build(),
                                        contentDescription = song.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                // Details
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary else AccentWhite,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = song.artist.ifEmpty { "Local Audio" },
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color.White.copy(alpha = 0.78f),
                                            fontWeight = FontWeight.Medium
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Duration
                                if (song.durationSeconds > 0) {
                                    Text(
                                        text = TimeFormatUtils.formatSeconds(song.durationSeconds),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color.White.copy(alpha = 0.75f),
                                            fontWeight = FontWeight.Medium
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
}

private suspend fun scanDeviceAudio(context: Context): List<Song> = withContext(Dispatchers.IO) {
    val songList = mutableListOf<Song>()
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DATA
    )

    // Filter out clips shorter than 20 seconds, and non-music audio flags where available
    val selection = StringBuilder().apply {
        append("${MediaStore.Audio.Media.IS_MUSIC} != 0")
        append(" AND ${MediaStore.Audio.Media.DURATION} >= 20000")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            append(" AND (${MediaStore.Audio.Media.IS_RINGTONE} == 0 OR ${MediaStore.Audio.Media.IS_RINGTONE} IS NULL)")
            append(" AND (${MediaStore.Audio.Media.IS_NOTIFICATION} == 0 OR ${MediaStore.Audio.Media.IS_NOTIFICATION} IS NULL)")
            append(" AND (${MediaStore.Audio.Media.IS_ALARM} == 0 OR ${MediaStore.Audio.Media.IS_ALARM} IS NULL)")
        }
    }.toString()

    val excludedFolderKeywords = listOf(
        "/call",
        "callrecording",
        "call_rec",
        "/recording",
        "/recordings",
        "/voice recorder",
        "/sound recorder",
        "/sound_recorder",
        "/voicemail",
        "/whatsapp",
        "/telegram",
        "/ringtones",
        "/notifications",
        "/alarms",
        "/.trashed",
        "/.cache",
        "/android/data"
    )

    try {
        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )

        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val dataCol = it.getColumnIndex(MediaStore.Audio.Media.DATA)

            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val rawTitle = it.getString(titleCol) ?: ""
                val rawArtist = it.getString(artistCol) ?: ""
                val rawAlbum = it.getString(albumCol) ?: ""
                val durationMs = it.getLong(durationCol)
                val filePath = if (dataCol != -1) it.getString(dataCol) else null

                val pathLower = filePath?.lowercase() ?: ""
                val titleLower = rawTitle.lowercase()

                // Exclude call recordings, voice notes, and non-music folders
                if (excludedFolderKeywords.any { kw -> pathLower.contains(kw) }) {
                    continue
                }

                // Exclude typical dialer/recorder naming formats
                val isCallRecordingName = titleLower.startsWith("call@") ||
                        titleLower.startsWith("call ") ||
                        titleLower.startsWith("call_") ||
                        titleLower.contains("call recording") ||
                        titleLower.contains("call_record") ||
                        titleLower.startsWith("recording_") ||
                        titleLower.startsWith("rec_") ||
                        titleLower.startsWith("ptt-") ||
                        titleLower.startsWith("aud-20") ||
                        titleLower.startsWith("voice ")

                if (isCallRecordingName) {
                    continue
                }

                val title = when {
                    rawTitle.isNotBlank() && !rawTitle.equals("<unknown>", ignoreCase = true) -> rawTitle
                    !filePath.isNullOrBlank() -> File(filePath).nameWithoutExtension
                    else -> "Unknown Track"
                }

                val artist = when {
                    rawArtist.isNotBlank() && !rawArtist.equals("<unknown>", ignoreCase = true) && !rawArtist.equals("Unknown Artist", ignoreCase = true) -> rawArtist
                    else -> "Local Audio"
                }

                val album = when {
                    rawAlbum.isNotBlank() && !rawAlbum.equals("<unknown>", ignoreCase = true) -> rawAlbum
                    else -> "Device Storage"
                }

                val contentUri: Uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                songList.add(
                    Song(
                        id = contentUri.toString(),
                        title = title,
                        artist = artist,
                        album = album,
                        durationSeconds = durationMs / 1000,
                        artworkUrl = contentUri.toString(),
                        highResArtworkUrl = contentUri.toString(),
                        sourceUrl = contentUri.toString(),
                        providerId = "local",
                        quality = AudioQuality.HIGH
                    )
                )
            }
        }
    } catch (e: Exception) {
        // Ignored
    }
    songList
}

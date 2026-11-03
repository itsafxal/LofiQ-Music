package com.lofiq.feature.spotify

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lofiq.data.spotify.SpotifyImportHelper
import com.lofiq.data.spotify.SpotifyImportStatus
import com.lofiq.ui.haptics.Haptic
import com.lofiq.ui.haptics.rememberHaptics
import com.lofiq.ui.theme.*
import kotlinx.coroutines.launch

import androidx.compose.ui.platform.LocalContext
import com.lofiq.data.spotify.PlaylistImportService

@Composable
fun SpotifyImportDialog(
    spotifyImportHelper: SpotifyImportHelper,
    onDismiss: () -> Unit,
    onImportSuccess: (playlistName: String, count: Int) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    val globalStatus by spotifyImportHelper.activeImportStatus.collectAsState()
    var playlistUrl by remember { mutableStateOf("") }

    val importStatus = globalStatus
    val isImporting = importStatus is SpotifyImportStatus.FetchingMetadata || importStatus is SpotifyImportStatus.MatchingTracks

    val isYouTube = playlistUrl.contains("youtube.com", ignoreCase = true) ||
            playlistUrl.contains("youtu.be", ignoreCase = true) ||
            playlistUrl.contains("list=", ignoreCase = true)

    val themeColor by animateColorAsState(
        targetValue = if (isYouTube) Color(0xFFFF0033) else Color(0xFF1DB954),
        label = "themeColor"
    )

    LaunchedEffect(Unit) {
        if (!isImporting) {
            spotifyImportHelper.resetImportStatus()
        }
    }

    Dialog(
        onDismissRequest = {
            if (isImporting) {
                android.widget.Toast.makeText(
                    context,
                    "Import continuing in background. Check notification for progress.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } else {
                spotifyImportHelper.resetImportStatus()
            }
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
            border = androidx.compose.foundation.BorderStroke(1.dp, themeColor.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon Badge
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(themeColor.copy(alpha = 0.15f))
                        .border(1.5.dp, themeColor.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isYouTube) Icons.Rounded.PlayCircle else Icons.Rounded.GraphicEq,
                        contentDescription = if (isYouTube) "YouTube Music" else "Spotify",
                        tint = themeColor,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isYouTube) "Import YouTube Playlist" else "Import Playlist",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = AccentWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Paste any Spotify or YouTube Music playlist link to import all tracks into your LOFIQ library.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Input Field & Paste Action
                OutlinedTextField(
                    value = playlistUrl,
                    onValueChange = {
                        playlistUrl = it
                        if (!isImporting && (importStatus is SpotifyImportStatus.Success || importStatus is SpotifyImportStatus.Error)) {
                            spotifyImportHelper.resetImportStatus()
                        }
                    },
                    placeholder = {
                        Text(
                            "Paste Spotify or YouTube Music link...",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                haptics.play(Haptic.Tap)
                                val text = clipboardManager.getText()?.text.orEmpty()
                                if (text.isNotBlank()) {
                                    playlistUrl = text.trim()
                                    if (!isImporting && (importStatus is SpotifyImportStatus.Success || importStatus is SpotifyImportStatus.Error)) {
                                        spotifyImportHelper.resetImportStatus()
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentPaste,
                                contentDescription = "Paste Link",
                                tint = themeColor
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = themeColor,
                        unfocusedBorderColor = SurfaceBorder,
                        focusedTextColor = AccentWhite,
                        unfocusedTextColor = AccentWhite,
                        cursorColor = themeColor,
                        focusedContainerColor = SurfaceCard,
                        unfocusedContainerColor = SurfaceCard
                    ),
                    enabled = !isImporting
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Progress Status Indicator
                when (val status = importStatus) {
                    SpotifyImportStatus.Idle -> {}

                    SpotifyImportStatus.FetchingMetadata -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = themeColor,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (isYouTube) "Fetching tracks from YouTube Music..." else "Fetching playlist from Spotify...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = themeColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }

                    is SpotifyImportStatus.MatchingTracks -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val progress = if (status.total > 0) status.current.toFloat() / status.total else 0f
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = themeColor,
                                trackColor = SurfaceBorder
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            val trackProgressText = if (status.playlistTitle.isNotBlank()) {
                                "${status.playlistTitle} (${status.current}/${status.total}): ${status.trackTitle}"
                            } else {
                                "Importing (${status.current}/${status.total}): ${status.trackTitle}"
                            }
                            Text(
                                text = trackProgressText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.90f),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }

                    is SpotifyImportStatus.Success -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1DB954).copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF1DB954),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Successfully imported ${status.importedCount} tracks into '${status.playlistName}'!",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = AccentWhite,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.5.sp
                                    )
                                )
                            }
                        }
                    }

                    is SpotifyImportStatus.Error -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFEF4444).copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = status.message,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFFFF8888),
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Actions: Cancel / Run in Background / Import / Import Another
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isImporting) {
                        OutlinedButton(
                            onClick = {
                                haptics.play(Haptic.Tap)
                                PlaylistImportService.cancelImport(context)
                                spotifyImportHelper.cancelActiveImport()
                            },
                            modifier = Modifier
                                .weight(0.9f)
                                .heightIn(min = 46.dp),
                            shape = RoundedCornerShape(22.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cancel", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, maxLines = 1)
                        }

                        Button(
                            onClick = {
                                haptics.play(Haptic.Select)
                                android.widget.Toast.makeText(
                                    context,
                                    "Import running in background. Check notification for live progress.",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1.5f)
                                .heightIn(min = 46.dp),
                            shape = RoundedCornerShape(22.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = themeColor,
                                contentColor = if (isYouTube) Color.White else Color.Black
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Run in Background",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else if (importStatus is SpotifyImportStatus.Success) {
                        OutlinedButton(
                            onClick = {
                                haptics.play(Haptic.Tap)
                                playlistUrl = ""
                                spotifyImportHelper.resetImportStatus()
                            },
                            modifier = Modifier
                                .weight(1.2f)
                                .heightIn(min = 46.dp),
                            shape = RoundedCornerShape(22.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.85f))
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import Another", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
                        }

                        Button(
                            onClick = {
                                haptics.play(Haptic.Select)
                                val name = importStatus.playlistName
                                val count = importStatus.importedCount
                                spotifyImportHelper.resetImportStatus()
                                onImportSuccess(name, count)
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 46.dp),
                            shape = RoundedCornerShape(22.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1DB954),
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("View", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                spotifyImportHelper.resetImportStatus()
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 46.dp),
                            shape = RoundedCornerShape(22.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.8f))
                        ) {
                            Text("Cancel", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                        }

                        Button(
                            onClick = {
                                if (playlistUrl.isNotBlank()) {
                                    haptics.play(Haptic.Select)
                                    focusManager.clearFocus()
                                    PlaylistImportService.startImport(context, playlistUrl.trim())
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 46.dp),
                            shape = RoundedCornerShape(22.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = themeColor,
                                contentColor = if (isYouTube) Color.White else Color.Black
                            ),
                            enabled = playlistUrl.isNotBlank()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Import",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

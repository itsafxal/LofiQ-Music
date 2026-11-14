package com.lofiq.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lofiq.core.common.TimeFormatUtils
import com.lofiq.core.model.Song
import com.lofiq.data.download.DownloadState
import com.lofiq.data.download.TrackDownloadInfo
import com.lofiq.data.download.TrackDownloadManager
import com.lofiq.playback.engine.PlaybackEngine
import com.lofiq.ui.haptics.Haptic
import com.lofiq.ui.haptics.rememberHaptics
import com.lofiq.ui.theme.*

/**
 * Global CompositionLocal to trigger the premium Song Context Menu from any screen or song item.
 */
val LocalSongContextMenu = compositionLocalOf<(Song) -> Unit> { {} }

/**
 * LofiQ Premium Liquid Glass Song Context Menu Bottom Sheet.
 *
 * Provides contextual actions:
 * 1. Play Next (LIFO immediate next track)
 * 2. Add to Queue (FIFO append to upcoming queue)
 * 3. Download (Integrated with TrackDownloadManager with live state feedback)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongContextMenuSheet(
    song: Song?,
    playbackEngine: PlaybackEngine,
    downloadManager: TrackDownloadManager? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (song == null) return

    val context = LocalContext.current
    val haptics = rememberHaptics()
    val isLiquidGlass = isLiquidGlassActive()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val downloadsMap: Map<String, TrackDownloadInfo> by if (downloadManager != null) {
        downloadManager.downloads.collectAsStateWithLifecycle()
    } else {
        remember { mutableStateOf(emptyMap()) }
    }
    val downloadInfo = downloadsMap[song.id]
    val isDownloaded = remember(downloadsMap, song.id) {
        downloadManager?.isDownloaded(song) == true || downloadInfo?.state == DownloadState.COMPLETED
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (isLiquidGlass) Color(0xFF101018).copy(alpha = 0.92f) else BackgroundBlack,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (isLiquidGlass) Brush.horizontalGradient(
                            listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.15f))
                        ) else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.25f)))
                    )
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            // Song Header Card with Liquid Glass specular styling
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .then(
                        if (isLiquidGlass) {
                            Modifier.background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.12f),
                                        Color.White.copy(alpha = 0.04f)
                                    )
                                )
                            )
                        } else {
                            Modifier.background(Color.White.copy(alpha = 0.06f))
                        }
                    )
                    .border(
                        width = 1.dp,
                        brush = if (isLiquidGlass) LiquidGlassTokens.specularBorder(0.25f)
                        else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.04f))),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtworkImage(
                    artworkUrl = com.lofiq.core.common.ArtworkUtils.getOptimalArtwork(song),
                    size = 56.dp,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = AccentWhite,
                            fontSize = 16.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = buildString {
                            append(song.artist)
                            if (song.durationSeconds > 0) {
                                append(" • ")
                                append(TimeFormatUtils.formatSeconds(song.durationSeconds))
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 13.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action 1: Play Next
            ContextMenuActionRow(
                icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                title = "Play Next",
                subtitle = "Insert immediately after current track",
                iconTint = AccentWhite,
                onClick = {
                    haptics.play(Haptic.Select)
                    playbackEngine.playNextInQueue(song)
                    Toast.makeText(context, "Playing next: ${song.title}", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Action 2: Add to Queue
            ContextMenuActionRow(
                icon = Icons.Rounded.QueueMusic,
                title = "Add to Queue",
                subtitle = "Append to bottom of upcoming queue",
                iconTint = AccentWhite,
                onClick = {
                    haptics.play(Haptic.Select)
                    playbackEngine.addToQueue(song)
                    Toast.makeText(context, "Added to queue: ${song.title}", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Action 3: Download
            val isDownloading = downloadInfo?.state == DownloadState.DOWNLOADING
            val isFailed = downloadInfo?.state == DownloadState.FAILED
            val progress = downloadInfo?.progress ?: 0f

            val downloadTitle = when {
                isDownloaded -> "Downloaded"
                isDownloading -> "Downloading (${(progress * 100).toInt()}%)"
                isFailed -> "Retry Download"
                else -> "Download"
            }

            val downloadSubtitle = when {
                isDownloaded -> "Available offline in Music/LofiQ"
                isDownloading -> "Saving track to device storage..."
                isFailed -> "Download failed. Tap to try again"
                else -> "Save to device for offline playback"
            }

            val downloadIcon: ImageVector = when {
                isDownloaded -> Icons.Rounded.CheckCircle
                isDownloading -> Icons.Rounded.Downloading
                isFailed -> Icons.Rounded.ErrorOutline
                else -> Icons.Rounded.CloudDownload
            }

            val downloadIconTint = when {
                isDownloaded -> BrandTeal
                isDownloading -> Color(0xFFC084FC)
                isFailed -> Color(0xFFEF4444)
                else -> AccentWhite
            }

            ContextMenuActionRow(
                icon = downloadIcon,
                title = downloadTitle,
                subtitle = downloadSubtitle,
                iconTint = downloadIconTint,
                trailingContent = {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(20.dp),
                            color = BrandTeal,
                            strokeWidth = 2.dp
                        )
                    }
                },
                onClick = {
                    haptics.play(Haptic.Select)
                    if (isDownloaded) {
                        Toast.makeText(context, "'${song.title}' is already downloaded", Toast.LENGTH_SHORT).show()
                    } else if (downloadManager != null) {
                        downloadManager.downloadSong(song)
                        Toast.makeText(context, "Starting download: ${song.title}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Download service unavailable", Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun ContextMenuActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLiquidGlass = isLiquidGlassActive()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isLiquidGlass) Color.White.copy(alpha = 0.06f)
                else Color.White.copy(alpha = 0.04f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = AccentWhite,
                    fontSize = 15.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondary,
                    fontSize = 12.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (trailingContent != null) {
            trailingContent()
        }
    }
}

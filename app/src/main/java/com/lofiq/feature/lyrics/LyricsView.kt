package com.lofiq.feature.lyrics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.lofiq.data.lyrics.model.LyricsLine
import com.lofiq.data.lyrics.model.LyricsSyncType
import com.lofiq.playback.engine.PlaybackEngine
import com.lofiq.ui.components.ArtworkImage
import com.lofiq.ui.theme.*

@Composable
fun SyncedLyricsContent(
    playbackEngine: PlaybackEngine,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null
) {
    val playerState by playbackEngine.playerState.collectAsStateWithLifecycle()
    val lyrics by playbackEngine.currentLyrics.collectAsStateWithLifecycle()
    val isLyricsLoading by playbackEngine.isLyricsLoading.collectAsStateWithLifecycle()

    val currentSong = playerState.currentSong
    val currentPos = playerState.currentPositionMs
    val lines = lyrics?.lines ?: emptyList()
    val syncType = lyrics?.syncType ?: LyricsSyncType.NOT_FOUND

    val activeIndex = remember(currentPos, lines, syncType) {
        if (syncType != LyricsSyncType.LINE_SYNCED || lines.isEmpty()) -1
        else {
            var found = -1
            for (i in lines.indices.reversed()) {
                if (currentPos >= lines[i].timeMs) {
                    found = i
                    break
                }
            }
            if (found == -1 && currentPos < (lines.firstOrNull()?.timeMs ?: 0L)) 0 else found
        }
    }

    val listState = rememberLazyListState()
    val isPerfMode = LocalPerformanceModeEnabled.current
    val isLiquidGlass = isLiquidGlassActive()
    val brandColor = LocalBrandColor.current

    // Smoothly auto-scroll to keep active lyric in focus with performance-aware physics
    LaunchedEffect(activeIndex, isPerfMode) {
        if (activeIndex >= 0 && activeIndex < lines.size) {
            val targetScroll = (activeIndex - 2).coerceAtLeast(0)
            if (isPerfMode) {
                listState.scrollToItem(targetScroll)
            } else {
                listState.animateScrollToItem(targetScroll)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBlack)
    ) {
        // Ambient album artwork blur background (Bypassed in Performance Mode to save GPU fillrate)
        if (!isPerfMode && isLiquidGlass && currentSong != null) {
            AsyncImage(
                model = currentSong.highResArtworkUrl.ifEmpty { currentSong.artworkUrl },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.22f)
                    .blur(64.dp)
            )
        }

        // Top gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            BackgroundBlack.copy(alpha = 0.75f),
                            Color.Transparent,
                            BackgroundBlack.copy(alpha = 0.9f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            // Header: Song info & Source badge
            if (currentSong != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        ArtworkImage(
                            artworkUrl = currentSong.highResArtworkUrl.ifEmpty { currentSong.artworkUrl },
                            size = 42.dp,
                            shape = RoundedCornerShape(10.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = currentSong.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = AccentWhite,
                                    fontSize = 15.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentSong.artist,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (onClose != null) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close Lyrics",
                                tint = AccentWhite,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Lyrics List or States
            when {
                isLyricsLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator(
                                color = LocalBrandColor.current,
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = "Fetching synced lyrics...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = TextSecondary,
                                    fontSize = 14.sp
                                )
                            )
                        }
                    }
                }

                syncType == LyricsSyncType.NOT_FOUND || lines.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MusicOff,
                                contentDescription = null,
                                tint = TextTertiary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Lyrics Not Available",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = AccentWhite,
                                    fontSize = 17.sp
                                )
                            )
                            Text(
                                text = "No synchronized lyrics found for this track yet.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                ),
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                            if (currentSong != null) {
                                Button(
                                    onClick = { playbackEngine.refreshLyrics() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White.copy(alpha = 0.15f),
                                        contentColor = AccentWhite
                                    ),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Retry Fetch", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(top = 32.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        itemsIndexed(
                            items = lines,
                            key = { idx, line -> "lyric_${idx}_${line.timeMs}" }
                        ) { index, line ->
                            val isActive = index == activeIndex
                            val isPast = activeIndex >= 0 && index < activeIndex

                            val targetScale = if (isActive && !isPerfMode) 1.05f else 1.0f
                            val scale by animateFloatAsState(
                                targetValue = targetScale,
                                animationSpec = if (isPerfMode) spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
                                else spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                                label = "lyricScale"
                            )

                            val targetAlpha = when {
                                isActive -> 1.0f
                                isPast -> 0.40f
                                else -> 0.30f
                            }
                            val alpha by animateFloatAsState(
                                targetValue = targetAlpha,
                                animationSpec = if (isPerfMode) spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
                                else spring(stiffness = Spring.StiffnessMediumLow),
                                label = "lyricAlpha"
                            )

                            val targetColor by animateColorAsState(
                                targetValue = if (isActive) AccentWhite else TextSecondary,
                                animationSpec = if (isPerfMode) tween(100) else spring(stiffness = Spring.StiffnessMediumLow),
                                label = "lyricColor"
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .scale(scale)
                                    .alpha(alpha)
                                    .clip(RoundedCornerShape(16.dp))
                                    .then(
                                        if (isActive && isLiquidGlass) {
                                            Modifier
                                                .background(brandColor.copy(alpha = 0.16f))
                                                .border(1.dp, brandColor.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                                        } else if (isActive) {
                                            Modifier.background(Color.White.copy(alpha = 0.08f))
                                        } else Modifier
                                    )
                                    .clickable {
                                        if (syncType == LyricsSyncType.LINE_SYNCED) {
                                            playbackEngine.seekTo(line.timeMs)
                                        }
                                    }
                                    .padding(vertical = 10.dp, horizontal = 12.dp)
                            ) {
                                Text(
                                    text = line.text,
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                                        color = targetColor,
                                        fontSize = if (isActive) 23.sp else 19.sp,
                                        lineHeight = if (isActive) 32.sp else 28.sp
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

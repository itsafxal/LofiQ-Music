package com.lofiq.feature.queue

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lofiq.core.model.Song
import com.lofiq.playback.engine.PlaybackEngine
import com.lofiq.ui.components.ArtworkImage
import com.lofiq.ui.components.WaveformIndicator
import com.lofiq.ui.haptics.Haptic
import com.lofiq.ui.haptics.rememberHaptics
import com.lofiq.ui.theme.*
import com.lofiq.ui.theme.isLiquidGlassActive
import com.lofiq.ui.theme.LocalPerformanceModeEnabled
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    playbackEngine: PlaybackEngine,
    onDismiss: () -> Unit
) {
    val playerState by playbackEngine.playerState.collectAsStateWithLifecycle()
    val smartRecommendations by playbackEngine.smartRecommendations.collectAsStateWithLifecycle()
    val isSmartAutoplay by playbackEngine.isSmartAutoplay.collectAsStateWithLifecycle()
    val currentVibe by playbackEngine.currentVibe.collectAsStateWithLifecycle()

    val currentSong = playerState.currentSong
    val queue = playerState.queue
    val currentIndex = playerState.currentQueueIndex

    val haptics = rememberHaptics()
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val isLiquidGlass = isLiquidGlassActive()
    val isPerfMode = LocalPerformanceModeEnabled.current

    // Authoritative upcoming songs extracted from queue
    val upcomingSongs = remember(queue, currentIndex) {
        if (currentIndex in queue.indices) {
            queue.subList((currentIndex + 1).coerceAtMost(queue.size), queue.size)
        } else {
            queue
        }
    }

    // Local mutable list for real-time interactive drag reordering
    var localUpcomingSongs by remember(upcomingSongs) { mutableStateOf(upcomingSongs) }

    val density = LocalDensity.current
    val defaultRowHeightPx = with(density) { 68.dp.toPx() }

    // Active drag-and-drop state
    var draggedSongId by remember { mutableStateOf<String?>(null) }
    var dragStartRelIndex by remember { mutableIntStateOf(-1) }
    var draggedCurrentIndex by remember { mutableIntStateOf(-1) }
    var accumulatedDragY by remember { mutableFloatStateOf(0f) }
    var rowHeightPx by remember { mutableFloatStateOf(0f) }
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }

    // Synchronize local list whenever upcomingSongs changes externally, unless user is actively dragging
    LaunchedEffect(upcomingSongs) {
        if (draggedSongId == null) {
            localUpcomingSongs = upcomingSongs
        }
    }

    // Drag handlers
    val onStartDrag: (index: Int, songId: String) -> Unit = { index, songId ->
        draggedSongId = songId
        dragStartRelIndex = index
        draggedCurrentIndex = index
        accumulatedDragY = 0f
        haptics.play(Haptic.Select)
    }

    val onDrag: (dragAmountY: Float) -> Unit = { dragAmountY ->
        accumulatedDragY += dragAmountY

        val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
        val measuredItemHeight = visibleItems.firstOrNull { it.key == draggedSongId }?.size?.toFloat()
        val effectiveRowHeight = measuredItemHeight ?: (if (rowHeightPx > 0f) rowHeightPx else defaultRowHeightPx)

        // Auto-scroll when dragging near viewport bounds
        if (visibleItems.isNotEmpty()) {
            val firstIndex = visibleItems.first().index
            val lastIndex = visibleItems.last().index
            val scrollDelta = 16f
            if (draggedCurrentIndex <= firstIndex + 1 && accumulatedDragY < -10f) {
                if (autoScrollJob?.isActive != true) {
                    autoScrollJob = coroutineScope.launch {
                        lazyListState.scrollBy(-scrollDelta)
                        accumulatedDragY -= scrollDelta
                    }
                }
            } else if (draggedCurrentIndex >= lastIndex - 2 && accumulatedDragY > 10f) {
                if (autoScrollJob?.isActive != true) {
                    autoScrollJob = coroutineScope.launch {
                        lazyListState.scrollBy(scrollDelta)
                        accumulatedDragY += scrollDelta
                    }
                }
            }
        }

        if (effectiveRowHeight > 0f && localUpcomingSongs.isNotEmpty() && dragStartRelIndex >= 0) {
            val slotDelta = (accumulatedDragY / effectiveRowHeight).roundToInt()
            val targetIndex = (dragStartRelIndex + slotDelta).coerceIn(0, localUpcomingSongs.lastIndex)
            if (targetIndex != draggedCurrentIndex && targetIndex in localUpcomingSongs.indices) {
                val mutable = localUpcomingSongs.toMutableList()
                val item = mutable.removeAt(draggedCurrentIndex)
                mutable.add(targetIndex, item)
                localUpcomingSongs = mutable
                draggedCurrentIndex = targetIndex
                haptics.play(Haptic.Tick)
            }
        }
    }

    val onEndDrag: () -> Unit = {
        autoScrollJob?.cancel()
        if (draggedSongId != null) {
            haptics.play(Haptic.ToggleOn)
            draggedSongId = null
            draggedCurrentIndex = -1
            dragStartRelIndex = -1
            accumulatedDragY = 0f
            // Commit authoritative reordered queue to playback engine
            playbackEngine.reorderUpcomingQueue(localUpcomingSongs)
        }
    }

    val onCancelDrag: () -> Unit = {
        autoScrollJob?.cancel()
        draggedSongId = null
        draggedCurrentIndex = -1
        dragStartRelIndex = -1
        accumulatedDragY = 0f
        localUpcomingSongs = upcomingSongs
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (isLiquidGlass) Color(0xFF101018).copy(alpha = 0.88f) else BackgroundBlack,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(44.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (isLiquidGlass) Brush.horizontalGradient(
                            listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.15f))
                        ) else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.25f)))
                    )
            )
        }
    ) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)
        ) {
            // Top Sheet Title & Dismiss
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Smart Playing Queue",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = AccentWhite,
                                fontSize = 22.sp
                            )
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${queue.size} tracks",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            )
                            Text(
                                text = "•",
                                color = TextTertiary,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "Infinite Radio",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFFC084FC),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close Queue",
                            tint = AccentWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 1. Now Playing Section (Frosted Glass Card)
            if (currentSong != null) {
                item {
                    Text(
                        text = "NOW PLAYING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.2.sp
                        ),
                        modifier = Modifier.padding(bottom = 8.dp, start = 2.dp)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.15f),
                                        Color.White.copy(alpha = 0.06f)
                                    )
                                )
                            )
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ArtworkImage(
                                artworkUrl = currentSong.highResArtworkUrl.ifEmpty { currentSong.artworkUrl },
                                size = 52.dp,
                                shape = RoundedCornerShape(10.dp)
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentSong.title,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = AccentWhite,
                                        fontSize = 15.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = currentSong.artist,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (playerState.isPlaying) {
                                WaveformIndicator(modifier = Modifier.padding(horizontal = 8.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // 2. Up Next Header & Clear Action
            val visibleRecommendations = smartRecommendations.filter { rec -> queue.none { it.id == rec.id } }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "UP NEXT (${localUpcomingSongs.size})",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.2.sp
                        )
                    )

                    if (localUpcomingSongs.isNotEmpty()) {
                        Text(
                            text = "Clear Upcoming",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFFEF4444),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { playbackEngine.clearUpcomingQueue() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // 3. Upcoming Queue Items with Long-Press Drag & Drop Reorder
            if (localUpcomingSongs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Smart autoplay is preparing matching tracks...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            } else {
                itemsIndexed(
                    items = localUpcomingSongs,
                    key = { _, song -> song.id }
                ) { relIndex, song ->
                    val actualIndex = currentIndex + 1 + relIndex
                    val isDragging = (draggedSongId == song.id)
                    val effectiveRowHeight = if (rowHeightPx > 0f) rowHeightPx else defaultRowHeightPx
                    val currentDragOffsetY = if (isDragging && effectiveRowHeight > 0f && dragStartRelIndex >= 0) {
                        (dragStartRelIndex - draggedCurrentIndex) * effectiveRowHeight + accumulatedDragY
                    } else {
                        0f
                    }

                    UpcomingQueueRow(
                        song = song,
                        relIndex = relIndex,
                        isDragging = isDragging,
                        isAnyDragging = (draggedSongId != null),
                        dragOffsetY = currentDragOffsetY,
                        onRowHeightMeasured = { h -> if (h > 0f) rowHeightPx = h },
                        onStartDrag = { onStartDrag(relIndex, song.id) },
                        onDrag = onDrag,
                        onEndDrag = onEndDrag,
                        onCancelDrag = onCancelDrag,
                        onPlay = {
                            if (draggedSongId == null) {
                                playbackEngine.playQueueItem(actualIndex)
                            }
                        },
                        onPlayNext = { playbackEngine.enqueuePriorityNext(actualIndex) },
                        onRemove = { playbackEngine.removeQueueItem(actualIndex) }
                    )
                }
            }

            // 4. Smart Recommendations Shelf
            if (visibleRecommendations.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "RECOMMENDED TRACKS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.2.sp
                        ),
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }

                itemsIndexed(
                    items = visibleRecommendations.take(6),
                    key = { _, song -> "rec_${song.id}" }
                ) { _, song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArtworkImage(
                            artworkUrl = song.highResArtworkUrl.ifEmpty { song.artworkUrl },
                            size = 46.dp,
                            shape = RoundedCornerShape(8.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary,
                                    fontSize = 13.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.78f),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.5.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Play Next button
                        IconButton(
                            onClick = { playbackEngine.playNextInQueue(song) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                contentDescription = "Play Next",
                                tint = AccentWhite,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Add to Queue button
                        IconButton(
                            onClick = { playbackEngine.addToQueue(song) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Add to Queue",
                                tint = AccentWhite,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LazyItemScope.UpcomingQueueRow(
    song: Song,
    relIndex: Int,
    isDragging: Boolean,
    isAnyDragging: Boolean,
    dragOffsetY: Float,
    onRowHeightMeasured: (Float) -> Unit,
    onStartDrag: () -> Unit,
    onDrag: (Float) -> Unit,
    onEndDrag: () -> Unit,
    onCancelDrag: () -> Unit,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onRemove: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "dragScale"
    )
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 12.dp else 0.dp,
        animationSpec = spring(),
        label = "dragElevation"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!isDragging) Modifier.animateItem() else Modifier)
            .zIndex(if (isDragging) 10f else 0f)
            .padding(vertical = 2.dp)
            .onGloballyPositioned { coords ->
                if (coords.size.height > 0) {
                    onRowHeightMeasured(coords.size.height.toFloat())
                }
            }
    ) {
        // Clean queue row card with smooth elevation and drag tracking
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationY = if (isDragging) dragOffsetY else 0f
                }
                .shadow(elevation = elevation, shape = RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isDragging) SurfaceElevated else Color.Transparent
                )
                .then(
                    if (isDragging) {
                        Modifier.border(1.dp, BrandTeal.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    } else {
                        Modifier
                    }
                )
                .clickable(enabled = !isAnyDragging) {
                    onPlay()
                }
                .padding(vertical = 7.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtworkImage(
                artworkUrl = song.highResArtworkUrl.ifEmpty { song.artworkUrl },
                size = 46.dp,
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDragging) BrandTeal else TextPrimary,
                        fontSize = 14.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.78f),
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 3-dot overflow menu
            Box {
                IconButton(
                    onClick = { if (!isAnyDragging) showMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Options",
                        tint = Color.White.copy(alpha = 0.78f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = showMenu && !isAnyDragging,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(SurfaceElevated)
                ) {
                    DropdownMenuItem(
                        text = { Text("Play Next", color = AccentWhite, fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Rounded.PlaylistPlay,
                                contentDescription = null,
                                tint = Color(0xFFC084FC),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            showMenu = false
                            onPlayNext()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove from Queue", color = Color(0xFFEF4444), fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            showMenu = false
                            onRemove()
                        }
                    )
                }
            }

            // Dedicated Drag Handle with generous 48dp hit area and instant drag-to-reorder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .pointerInput(song.id) {
                        detectVerticalDragGestures(
                            onDragStart = { onStartDrag() },
                            onDragEnd = { onEndDrag() },
                            onDragCancel = { onCancelDrag() },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.DragHandle,
                    contentDescription = "Reorder track",
                    tint = if (isDragging) BrandTeal else AccentWhite.copy(alpha = 0.55f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

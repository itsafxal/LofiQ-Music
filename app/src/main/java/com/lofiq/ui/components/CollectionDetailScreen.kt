package com.lofiq.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lofiq.core.common.TimeFormatUtils
import com.lofiq.core.model.Song
import com.lofiq.ui.theme.*

private val AccentGold = Color(0xFFE8C857)

@Composable
fun CollectionDetailScreen(
    title: String,
    subtitle: String,
    typeLabel: String = "PLAYLIST",
    artworkUrl: String = "",
    tracks: List<Song>,
    isPlaying: Boolean = false,
    currentSongId: String? = null,
    onBack: () -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onToggleFavorite: ((Song) -> Unit)? = null,
    onSongMoreClick: ((Song) -> Unit)? = null,
    onCollectionMoreClick: (() -> Unit)? = null,
    onSyncClick: (() -> Unit)? = null,
    headerBottomContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val brandColor = LocalBrandColor.current
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val filteredTracks = remember(tracks, searchQuery) {
        if (searchQuery.isBlank()) tracks
        else tracks.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true)
        }
    }

    // Determine backdrop artwork: artworkUrl or first track's artwork
    val backdropUrl = remember(artworkUrl, tracks) {
        artworkUrl.ifBlank { tracks.firstOrNull()?.highResArtworkUrl?.ifEmpty { tracks.firstOrNull()?.artworkUrl } ?: "" }
    }

    // Collect first 4 distinct artworks for 2x2 collage when no single image is provided
    val collageArtworks = remember(tracks, artworkUrl) {
        if (artworkUrl.isNotBlank()) emptyList()
        else tracks.mapNotNull { it.artworkUrl.takeIf { url -> url.isNotBlank() } }.distinct().take(4)
    }

    // Calculate duration summary
    val durationSummary = remember(tracks) {
        val totalSecs = tracks.sumOf { it.durationSeconds }
        if (totalSecs <= 0) ""
        else {
            val hrs = totalSecs / 3600
            val mins = (totalSecs % 3600) / 60
            if (hrs > 0) "$hrs HR $mins MIN" else "$mins MIN"
        }
    }

    Box(
        modifier = modifier.fillMaxSize().background(BackgroundBlack)
    ) {
        // Dynamic Ambient Blurred Backdrop with Enhanced Dark Scrim for High Legibility
        if (backdropUrl.isNotBlank()) {
            DynamicArtworkBackground(
                artworkUrl = backdropUrl,
                scrimBrush = Brush.verticalGradient(
                    0.00f to Color.Black.copy(alpha = 0.50f),
                    0.25f to Color.Black.copy(alpha = 0.60f),
                    0.55f to Color.Black.copy(alpha = 0.76f),
                    1.00f to Color.Black.copy(alpha = 0.92f)
                )
            )
        }

        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 0.dp, bottom = 175.dp + navBarBottom)
        ) {
            // TOP HEADER SECTION
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // TOP BAR: Back button on Left, Options/Menu on Right
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.40f))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = AccentWhite,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { isSearchActive = !isSearchActive },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (isSearchActive) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.40f))
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = "Search",
                                    tint = AccentWhite,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            if (onCollectionMoreClick != null) {
                                IconButton(
                                    onClick = onCollectionMoreClick,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.40f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreHoriz,
                                        contentDescription = "Options",
                                        tint = AccentWhite,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // HERO ARTWORK: 2x2 Collage OR Single Square Artwork
                    Box(
                        modifier = Modifier
                            .size(230.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (artworkUrl.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(artworkUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (collageArtworks.size >= 4) {
                            // Authentic 2x2 Album Art Collage
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(modifier = Modifier.weight(1f)) {
                                    AsyncImage(model = collageArtworks[0], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                                    AsyncImage(model = collageArtworks[1], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                                }
                                Row(modifier = Modifier.weight(1f)) {
                                    AsyncImage(model = collageArtworks[2], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                                    AsyncImage(model = collageArtworks[3], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                                }
                            }
                        } else if (tracks.isNotEmpty() && tracks.first().artworkUrl.isNotBlank()) {
                            AsyncImage(
                                model = tracks.first().artworkUrl,
                                contentDescription = title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = null,
                                tint = AccentWhite.copy(alpha = 0.6f),
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // TITLE
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = AccentWhite,
                            fontSize = 25.sp,
                            letterSpacing = (-0.4).sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // SUBTITLE (Golden Accent)
                    if (subtitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = AccentGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // METADATA LINE: "PLAYLIST • 9 TRACKS • 31 MIN"
                    Spacer(modifier = Modifier.height(6.dp))
                    val metaLine = buildString {
                        append(typeLabel.uppercase())
                        append(" • ${tracks.size} TRACKS")
                        if (durationSummary.isNotBlank()) {
                            append(" • $durationSummary")
                        }
                    }
                    Text(
                        text = metaLine,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.78f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            letterSpacing = 0.8.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // ACTION BUTTONS ROW (Shuffle, Big Play, Search, More)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Shuffle Button
                        IconButton(
                            onClick = onShuffle,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = "Shuffle",
                                tint = AccentWhite,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(20.dp))

                        // Hero Solid White Play Button
                        IconButton(
                            onClick = onPlayAll,
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(AccentWhite)
                        ) {
                            val isPlayingThis = isPlaying && tracks.any { it.id == currentSongId }
                            Icon(
                                imageVector = if (isPlayingThis) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = "Play",
                                tint = BackgroundBlack,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(20.dp))

                        // Search Button
                        IconButton(
                            onClick = {
                                isSearchActive = !isSearchActive
                                if (!isSearchActive) searchQuery = ""
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (isSearchActive) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.12f))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Search",
                                tint = AccentWhite,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(20.dp))

                        // More Button
                        IconButton(
                            onClick = { onCollectionMoreClick?.invoke() },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreHoriz,
                                contentDescription = "More",
                                tint = AccentWhite,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // INLINE SEARCH BAR (Revealed when search button tapped)
                    AnimatedVisibility(
                        visible = isSearchActive,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                            Surface(
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = RoundedCornerShape(22.dp),
                                color = Color.White.copy(alpha = 0.12f)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = AccentWhite, fontSize = 14.sp),
                                        decorationBox = { innerTextField ->
                                            if (searchQuery.isEmpty()) {
                                                Text("Find in $title...", color = TextTertiary, fontSize = 14.sp)
                                            }
                                            innerTextField()
                                        }
                                    )
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (headerBottomContent != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        headerBottomContent()
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                }
            }

            // TRACK LIST ITEMS ("Step step aayit oro-onno-ro aayitt")
            if (filteredTracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp, horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "No songs match \"$searchQuery\"" else "No songs in this collection.",
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            if (onSyncClick != null && searchQuery.isBlank()) {
                                Button(
                                    onClick = onSyncClick,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = BrandTeal,
                                        contentColor = BackgroundBlack
                                    ),
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Sync Now", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(filteredTracks, key = { index, song -> "collection_song_${index}_${song.id}" }) { index, song ->
                    val isCurrent = song.id == currentSongId

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPlaySong(song, filteredTracks) }
                            .then(
                                if (isCurrent) Modifier.background(brandColor.copy(alpha = 0.14f))
                                else Modifier
                            )
                            .padding(horizontal = 20.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Artwork Thumbnail
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (song.artworkUrl.isNotBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(song.artworkUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = song.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.60f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Waveform / Equalizer icon if currently playing
                            if (isCurrent && isPlaying) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.50f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    WaveformIndicator(modifier = Modifier.size(20.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        // Title & Artist Column
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isCurrent) brandColor else AccentWhite,
                                    fontSize = 15.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isCurrent) brandColor.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.82f),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.5.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Duration on the right
                        if (song.durationSeconds > 0) {
                            Text(
                                text = TimeFormatUtils.formatSeconds(song.durationSeconds),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isCurrent) brandColor.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.78f),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.5.sp
                                )
                            )
                        }

                        // Three-dot options menu
                        IconButton(
                            onClick = { onSongMoreClick?.invoke(song) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Song options",
                                tint = if (isCurrent) brandColor else Color.White.copy(alpha = 0.80f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

package com.lofiq.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lofiq.core.common.TimeFormatUtils
import com.lofiq.core.model.Song
import com.lofiq.ui.components.ArtworkImage
import com.lofiq.ui.components.SongRow
import com.lofiq.ui.components.WaveformIndicator
import com.lofiq.ui.haptics.Haptic
import com.lofiq.ui.haptics.rememberHaptics
import com.lofiq.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    initialQuery: String? = null,
    onSongPlay: (() -> Unit)? = null,
    viewModel: SearchViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val haptics = rememberHaptics()
    val focusManager = LocalFocusManager.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val playerState by viewModel.playbackEngine.playerState.collectAsStateWithLifecycle()
    val isLiquidGlass = LocalLiquidGlassEnabled.current
    val bottomScrollPadding = if (playerState.currentSong != null) 220.dp else 155.dp

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        if (initialQuery.isNullOrBlank()) {
            delay(280L)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank() && searchQuery != initialQuery) {
            viewModel.onQueryChange(initialQuery)
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Screen Header
            Text(
                text = "Search",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentWhite
                ),
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp)
            )

            // Search Text Field (Minimalist Dark Pill)
            TextField(
                value = searchQuery,
                onValueChange = { viewModel.onQueryChange(it) },
                placeholder = {
                    Text("Songs, artists, or albums...", color = TextTertiary, fontSize = 14.sp)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = TextSecondary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearQuery() }) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = "Clear",
                                tint = TextSecondary
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        viewModel.submitQuery(searchQuery)
                    }
                ),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = if (isLiquidGlass) LiquidGlassTokens.CardGlassFill else SurfaceCard,
                    unfocusedContainerColor = if (isLiquidGlass) LiquidGlassTokens.CardGlassFill else SurfaceCard,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = AccentWhite
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .then(
                        if (isLiquidGlass) {
                            Modifier.border(
                                LiquidGlassTokens.specularBorderStroke(),
                                RoundedCornerShape(14.dp)
                            )
                        } else Modifier
                    )
                    .focusRequester(focusRequester)
            )

            // Fast Query Suggestion Chips (As user types)
            if (searchQuery.isNotBlank() && uiState.querySuggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.querySuggestions, key = { it }) { suggestion ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isLiquidGlass) LiquidGlassTokens.PillGlassFill else SurfaceElevated)
                                .then(
                                    if (isLiquidGlass) {
                                        Modifier.border(LiquidGlassTokens.specularBorderStroke(0.20f), RoundedCornerShape(20.dp))
                                    } else Modifier
                                )
                                .clickable {
                                    haptics.play(Haptic.Tap)
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    viewModel.submitQuery(suggestion)
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = AccentWhite
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Content Area: Search History vs Search Results
            if (searchQuery.isBlank()) {
                if (searchHistory.isNotEmpty()) {
                    // Recent Search History List
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = bottomScrollPadding)
                    ) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recent Searches",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextSecondary,
                                        fontSize = 13.5.sp
                                    )
                                )
                                TextButton(
                                    onClick = {
                                        haptics.play(Haptic.Tap)
                                        viewModel.clearAllHistory()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Clear all",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = TextTertiary,
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }
                        }

                        items(searchHistory, key = { it }) { historyQuery ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptics.play(Haptic.Select)
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        viewModel.submitQuery(historyQuery)
                                    }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (isLiquidGlass) LiquidGlassTokens.PillGlassFill else SurfaceElevated)
                                        .then(
                                            if (isLiquidGlass) {
                                                Modifier.border(LiquidGlassTokens.specularBorderStroke(0.20f), CircleShape)
                                            } else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.History,
                                        contentDescription = "History",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Text(
                                    text = historyQuery,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = AccentWhite,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1
                                )

                                IconButton(
                                    onClick = {
                                        haptics.play(Haptic.Tap)
                                        viewModel.removeHistoryItem(historyQuery)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Remove",
                                        tint = TextTertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Empty explore prompt when no history
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Explore millions of songs & artists",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextTertiary)
                        )
                    }
                }
            } else if (uiState.isSearching && uiState.topResult == null && uiState.searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentWhite)
                }
            } else if (!uiState.isSearching && uiState.topResult == null && uiState.searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No results found for '$searchQuery'",
                        style = MaterialTheme.typography.bodyLarge.copy(color = TextSecondary)
                    )
                }
            } else {
                val pullToRefreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = {
                        haptics.play(Haptic.Select)
                        viewModel.refresh()
                    },
                    state = pullToRefreshState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    indicator = {
                        PullToRefreshDefaults.Indicator(
                            state = pullToRefreshState,
                            isRefreshing = uiState.isRefreshing,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp),
                            containerColor = SurfaceElevated,
                            color = Color(0xFFA855F7)
                        )
                    }
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = bottomScrollPadding)
                    ) {
                    // Top Result / Best Match Section
                    val topResult = uiState.topResult
                    if (topResult != null) {
                        item(key = "top_result_header") {
                            Text(
                                text = "Top Result",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary,
                                    fontSize = 13.5.sp
                                ),
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp)
                            )
                        }

                        item(key = "top_result_card") {
                            val isCurrentTop = playerState.currentSong?.id == topResult.id
                            BestMatchCard(
                                song = topResult,
                                isPlaying = playerState.isPlaying,
                                isCurrent = isCurrentTop,
                                isLiquidGlass = isLiquidGlass,
                                onPlayClick = {
                                    haptics.play(Haptic.Select)
                                    viewModel.playSong(topResult)
                                    onSongPlay?.invoke()
                                }
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                    }

                    // Remaining Results List
                    if (uiState.searchResults.isNotEmpty()) {
                        item(key = "songs_header") {
                            Text(
                                text = "Songs",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary,
                                    fontSize = 13.5.sp
                                ),
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 6.dp)
                            )
                        }

                        itemsIndexed(uiState.searchResults, key = { index, song -> "search_${index}_${song.id}" }) { _, song ->
                            val isCurrent = playerState.currentSong?.id == song.id
                            SongRow(
                                song = song,
                                isPlaying = playerState.isPlaying,
                                isCurrent = isCurrent,
                                onClick = {
                                    haptics.play(Haptic.Select)
                                    viewModel.playSong(song)
                                    onSongPlay?.invoke()
                                },
                                onFavoriteClick = { viewModel.toggleFavorite(song) }
                            )
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun BestMatchCard(
    song: Song,
    isPlaying: Boolean,
    isCurrent: Boolean,
    isLiquidGlass: Boolean,
    onPlayClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (isLiquidGlass) LiquidGlassTokens.CardGlassFill else SurfaceElevated)
            .then(
                if (isLiquidGlass) {
                    Modifier.border(
                        LiquidGlassTokens.specularBorderStroke(),
                        RoundedCornerShape(18.dp)
                    )
                } else Modifier
            )
            .clickable(onClick = onPlayClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Artwork
            ArtworkImage(
                artworkUrl = com.lofiq.core.common.ArtworkUtils.getOptimalArtwork(song),
                size = 72.dp,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Best match tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AccentWhite.copy(alpha = 0.12f))
                        .padding(horizontal = 7.dp, vertical = 2.5.dp)
                ) {
                    Text(
                        text = "BEST MATCH",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentWhite,
                            letterSpacing = 0.8.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentWhite
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = "${song.artist} • Song",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        color = TextSecondary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Play / Waveform Button
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (isCurrent) AccentWhite else AccentWhite.copy(alpha = 0.15f))
                    .clickable(onClick = onPlayClick),
                contentAlignment = Alignment.Center
            ) {
                if (isCurrent && isPlaying) {
                    Icon(
                        imageVector = Icons.Rounded.Pause,
                        contentDescription = "Pause",
                        tint = if (isCurrent) SurfaceBlack else AccentWhite,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play",
                        tint = if (isCurrent) SurfaceBlack else AccentWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

package com.lofiq.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import com.lofiq.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.lofiq.auth.AccountDialog
import com.lofiq.auth.LoginDialog
import com.lofiq.core.model.Song
import com.lofiq.ui.components.*
import com.lofiq.ui.haptics.Haptic
import com.lofiq.ui.haptics.rememberHaptics
import com.lofiq.ui.theme.*

@Composable
fun HomeScreen(
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onOpenListenTogether: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by viewModel.playbackEngine.playerState.collectAsStateWithLifecycle()
    val userAccount by viewModel.userAccount.collectAsStateWithLifecycle()
    val spotifyAccount by viewModel.spotifyAccount.collectAsStateWithLifecycle()
    val isAuthenticating by viewModel.isAuthenticating.collectAsStateWithLifecycle()
    val isYouTubePermanentlyDismissed by viewModel.isYouTubeBannerPermanentlyDismissed.collectAsStateWithLifecycle()
    val isYouTubeSessionDismissed by viewModel.isSessionBannerDismissed.collectAsStateWithLifecycle()
    val artistDetailTracks by viewModel.artistDetailTracks.collectAsStateWithLifecycle()
    val isArtistDetailLoading by viewModel.isArtistDetailLoading.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    var showAccountDialog by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var showSpotifyLoginDialog by remember { mutableStateOf(false) }
    var showDismissDialog by remember { mutableStateOf(false) }

    var selectedArtistForDetail by remember { mutableStateOf<ArtistCardData?>(null) }
    var selectedShelfForDetail by remember { mutableStateOf<HomeShelf?>(null) }

    androidx.activity.compose.BackHandler(enabled = selectedArtistForDetail != null || selectedShelfForDetail != null) {
        selectedArtistForDetail = null
        selectedShelfForDetail = null
        viewModel.clearArtistCatalog()
    }

    if (selectedArtistForDetail != null) {
        val artist = selectedArtistForDetail!!
        val tracksToDisplay = if (artistDetailTracks.isNotEmpty()) artistDetailTracks else artist.songs
        CollectionDetailScreen(
            title = artist.name,
            subtitle = if (isArtistDetailLoading && tracksToDisplay.size <= artist.songs.size) "Artist • Loading complete catalog..." else "Artist • ${tracksToDisplay.size} Tracks",
            typeLabel = "ARTIST",
            artworkUrl = artist.artworkUrl,
            tracks = tracksToDisplay,
            isPlaying = playerState.isPlaying,
            currentSongId = playerState.currentSong?.id,
            onBack = {
                selectedArtistForDetail = null
                viewModel.clearArtistCatalog()
            },
            onPlaySong = { song, queue ->
                haptics.play(Haptic.Tap)
                viewModel.playSong(song, queue)
            },
            onPlayAll = {
                if (tracksToDisplay.isNotEmpty()) {
                    haptics.play(Haptic.Tap)
                    viewModel.playSong(tracksToDisplay.first(), tracksToDisplay)
                }
            },
            onShuffle = {
                if (tracksToDisplay.isNotEmpty()) {
                    haptics.play(Haptic.Tap)
                    val shuffled = tracksToDisplay.shuffled()
                    viewModel.playSong(shuffled.first(), shuffled)
                }
            },
            modifier = modifier
        )
        return
    }

    if (selectedShelfForDetail != null) {
        val shelf = selectedShelfForDetail!!
        CollectionDetailScreen(
            title = shelf.title,
            subtitle = shelf.subtitle ?: "Curated Mix",
            typeLabel = "COLLECTION",
            artworkUrl = shelf.songs.firstOrNull()?.highResArtworkUrl?.ifEmpty { shelf.songs.firstOrNull()?.artworkUrl } ?: "",
            tracks = shelf.songs,
            isPlaying = playerState.isPlaying,
            currentSongId = playerState.currentSong?.id,
            onBack = { selectedShelfForDetail = null },
            onPlaySong = { song, queue ->
                haptics.play(Haptic.Tap)
                viewModel.playSong(song, queue)
            },
            onPlayAll = {
                if (shelf.songs.isNotEmpty()) {
                    haptics.play(Haptic.Tap)
                    viewModel.playSong(shelf.songs.first(), shelf.songs)
                }
            },
            onShuffle = {
                if (shelf.songs.isNotEmpty()) {
                    haptics.play(Haptic.Tap)
                    val shuffled = shelf.songs.shuffled()
                    viewModel.playSong(shuffled.first(), shuffled)
                }
            },
            modifier = modifier
        )
        return
    }

    // Detect endless scrolling when approaching bottom of list
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItem >= totalItems - 2
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMoreShelves()
        }
    }

    val pullToRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = {
            haptics.play(Haptic.Select)
            viewModel.refreshFeed()
        },
        state = pullToRefreshState,
        modifier = modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = uiState.isRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 10.dp),
                containerColor = SurfaceElevated,
                color = Color(0xFFA855F7)
            )
        }
    ) {
        val bottomScrollPadding = if (playerState.currentSong != null) 220.dp else 155.dp
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 0.dp, bottom = bottomScrollPadding)
        ) {
            // Header: Logo + "Listen Now" on Left; Live Room + Profile Avatar on Right
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo_transparent),
                            contentDescription = "LofiQ Logo",
                            modifier = Modifier.size(34.dp)
                        )
                        Text(
                            text = "Listen Now",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AccentWhite,
                                letterSpacing = (-0.6).sp
                            )
                        )
                    }

                    val isLiquidGlass = LocalLiquidGlassEnabled.current
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            onClick = {
                                haptics.play(Haptic.Tap)
                                onOpenListenTogether()
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isLiquidGlass) LiquidGlassTokens.PillGlassFill else Color.White.copy(alpha = 0.08f),
                            border = if (isLiquidGlass) LiquidGlassTokens.specularBorderStroke() else androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.GraphicEq,
                                    contentDescription = "Listen Together",
                                    tint = AccentWhite,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Live Room",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = AccentWhite,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }

                        // Profile / Settings Button
                        Surface(
                            onClick = {
                                haptics.play(Haptic.Tap)
                                onNavigateToSettings()
                            },
                            shape = CircleShape,
                            color = if (isLiquidGlass) LiquidGlassTokens.PillGlassFill else Color.White.copy(alpha = 0.08f),
                            border = if (isLiquidGlass) LiquidGlassTokens.specularBorderStroke() else androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                            modifier = Modifier.size(36.dp)
                        ) {
                            if (!userAccount?.avatarUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = userAccount?.avatarUrl,
                                    contentDescription = "Profile",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Person,
                                        contentDescription = "Profile",
                                        tint = AccentWhite,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }


            // Connect YouTube Music Card (Sleek horizontal card with top-right dismiss affordance)
            if (!userAccount.isLoggedIn && !isYouTubePermanentlyDismissed && !isYouTubeSessionDismissed) {
                item {
                    val isLiquidGlass = LocalLiquidGlassEnabled.current
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        color = if (isLiquidGlass) LiquidGlassTokens.CardGlassFill else Color(0xFF16161A),
                        shape = RoundedCornerShape(20.dp),
                        border = if (isLiquidGlass) LiquidGlassTokens.specularBorderStroke() else androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.08f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 14.dp, top = 14.dp, bottom = 16.dp)
                        ) {
                            // Top Row: Title + Close Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Sign in to YouTube Music",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = AccentWhite,
                                        fontSize = 17.sp,
                                        letterSpacing = (-0.3).sp
                                    )
                                )

                                IconButton(
                                    onClick = {
                                        haptics.play(Haptic.Tap)
                                        showDismissDialog = true
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Hide Card",
                                        tint = Color.White.copy(alpha = 0.45f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Bottom Row: Description + Sign In Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Sign in to sync personal playlists & liked songs. You can also connect anytime in Settings.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 16.dp)
                                )

                                Surface(
                                    onClick = {
                                        haptics.play(Haptic.Select)
                                        showLoginDialog = true
                                    },
                                    shape = RoundedCornerShape(24.dp),
                                    color = Color.White,
                                    modifier = Modifier.height(38.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 20.dp)
                                    ) {
                                        Text(
                                            text = "Sign in",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Black,
                                                fontSize = 13.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Initial Loading Skeletons
            if (uiState.isInitialLoading && uiState.shelves.isEmpty()) {
                items(4) { index ->
                    HomeShelfSkeleton(
                        title = when (index) {
                            0 -> "Malayalam Hits & Trending"
                            1 -> "Tamil Top Chartbusters"
                            2 -> "Trending Now in India"
                            else -> "Top Indian Artists"
                        },
                        isArtist = index == 3
                    )
                }
            }

            // Error State
            if (uiState.errorMessage != null && uiState.shelves.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "Unable to load music",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                haptics.play(Haptic.Tap)
                                viewModel.loadInitialFeed()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SurfaceElevated,
                                contentColor = AccentWhite
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Try Again", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Endless Rich Shelves
            itemsIndexed(
                items = uiState.shelves,
                key = { _, shelf -> shelf.id }
            ) { _, shelf ->
                when (shelf.layout) {
                    ShelfLayout.CAROUSEL -> {
                        HomeCarouselShelf(
                            shelf = shelf,
                            currentSong = playerState.currentSong,
                            isPlaying = playerState.isPlaying,
                            onSongClick = { song ->
                                haptics.play(Haptic.Tap)
                                viewModel.playSong(song, shelf.songs)
                            },
                            onShelfClick = {
                                haptics.play(Haptic.Tap)
                                selectedShelfForDetail = shelf
                            }
                        )
                    }
                    ShelfLayout.COMPACT_GRID -> {
                        HomeCompactGridShelf(
                            shelf = shelf,
                            currentSong = playerState.currentSong,
                            isPlaying = playerState.isPlaying,
                            onSongClick = { song ->
                                haptics.play(Haptic.Tap)
                                viewModel.playSong(song, shelf.songs)
                            },
                            onShelfClick = {
                                haptics.play(Haptic.Tap)
                                selectedShelfForDetail = shelf
                            },
                            onPlayAll = if (shelf.songs.isNotEmpty()) {
                                {
                                    haptics.play(Haptic.Tap)
                                    viewModel.playSong(shelf.songs.first(), shelf.songs)
                                }
                            } else null
                        )
                    }
                    ShelfLayout.ARTIST_CIRCLE -> {
                        HomeArtistShelf(
                            shelf = shelf,
                            onArtistClick = { artist ->
                                haptics.play(Haptic.Tap)
                                selectedArtistForDetail = artist
                                viewModel.loadArtistCatalog(artist)
                            }
                        )
                    }
                    ShelfLayout.FEATURED_LARGE -> {
                        HomeFeaturedLargeShelf(
                            shelf = shelf,
                            currentSong = playerState.currentSong,
                            isPlaying = playerState.isPlaying,
                            onSongClick = { song ->
                                haptics.play(Haptic.Tap)
                                viewModel.playSong(song, shelf.songs)
                            },
                            onShelfClick = {
                                haptics.play(Haptic.Tap)
                                selectedShelfForDetail = shelf
                            }
                        )
                    }
                }
            }

            // Bottom Pagination Loading Indicator
            if (uiState.isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = AccentWhite.copy(alpha = 0.6f),
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }

        // Account Profile Dialog
        if (showAccountDialog) {
            AccountDialog(
                userAccount = userAccount,
                isAuthenticating = isAuthenticating,
                onDismiss = { showAccountDialog = false },
                onStartLogin = {
                    showAccountDialog = false
                    showLoginDialog = true
                },
                onRefreshProfile = { viewModel.refreshAuthProfile() },
                onSignOut = { viewModel.signOutAuth() },
                onOpenSettings = onNavigateToSettings
            )
        }

        // Secure In-App YouTube Music Login Dialog
        if (showLoginDialog) {
            LoginDialog(
                onDismiss = { showLoginDialog = false },
                onAuthSuccess = { cookies ->
                    viewModel.onAuthCookiesCaptured(cookies)
                    showLoginDialog = false
                }
            )
        }

        // Secure In-App Spotify Login Dialog
        if (showSpotifyLoginDialog) {
            com.lofiq.feature.spotify.SpotifyLoginDialog(
                onDismiss = { showSpotifyLoginDialog = false },
                onAuthSuccess = { cookies ->
                    viewModel.onSpotifyCookiesCaptured(cookies)
                    showSpotifyLoginDialog = false
                }
            )
        }

        // YouTube Music Card Dismiss Confirmation Dialog
        if (showDismissDialog) {
            var dontShowAgain by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showDismissDialog = false },
                containerColor = Color(0xFF1E1E24),
                shape = RoundedCornerShape(24.dp),
                title = {
                    Text(
                        text = "Hide YouTube Music Card?",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = AccentWhite,
                            fontSize = 19.sp
                        )
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = "You can always connect or link your YouTube Music account anytime from Settings to automatically sync your playlists and liked songs.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = TextSecondary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        )

                        // Interactive "Don't show again" Checkbox Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { dontShowAgain = !dontShowAgain }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Checkbox(
                                checked = dontShowAgain,
                                onCheckedChange = { dontShowAgain = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = AccentWhite,
                                    uncheckedColor = Color.White.copy(alpha = 0.5f),
                                    checkmarkColor = BackgroundBlack
                                )
                            )
                            Column {
                                Text(
                                    text = "Don't show again",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = AccentWhite,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                )
                                Text(
                                    text = if (dontShowAgain) "Permanently hide this banner" else "Hide for this session only",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            haptics.play(Haptic.Select)
                            showDismissDialog = false
                            viewModel.dismissYouTubeBanner(permanently = dontShowAgain)
                            val msg = if (dontShowAgain) {
                                "Card permanently hidden. Connect anytime in Settings."
                            } else {
                                "Card hidden for now. Connect anytime in Settings."
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text(
                            text = "Hide",
                            color = AccentWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            haptics.play(Haptic.Tap)
                            showDismissDialog = false
                        }
                    ) {
                        Text(
                            text = "Cancel",
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun ShelfHeader(
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)? = null,
    onPlayAll: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null && onPlayAll == null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentWhite,
                    letterSpacing = (-0.3).sp
                )
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                )
            }
        }
        if (onPlayAll != null) {
            val isLiquidGlass = LocalLiquidGlassEnabled.current
            Surface(
                onClick = onPlayAll,
                shape = RoundedCornerShape(16.dp),
                color = if (isLiquidGlass) LiquidGlassTokens.PillGlassFill else Color.White.copy(alpha = 0.08f),
                border = if (isLiquidGlass) LiquidGlassTokens.specularBorderStroke() else androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play All",
                        tint = AccentWhite,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Play all",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = AccentWhite,
                            fontSize = 12.sp
                        )
                    )
                }
            }
        } else if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "See All",
                tint = TextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun HomeCarouselShelf(
    shelf: HomeShelf,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    onShelfClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (shelf.songs.isEmpty()) return
    val contextMenuTrigger = LocalSongContextMenu.current
    val haptics = rememberHaptics()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        ShelfHeader(title = shelf.title, subtitle = shelf.subtitle, onClick = onShelfClick)

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(
                items = shelf.songs,
                key = { index, song -> "${shelf.id}_${index}_${song.id}" }
            ) { _, song ->
                val isCurrent = currentSong?.id == song.id
                AlbumCard(
                    title = song.title,
                    subtitle = song.artist,
                    artworkUrl = song.highResArtworkUrl.ifEmpty { song.artworkUrl },
                    isPlaying = isPlaying && isCurrent,
                    isCurrent = isCurrent,
                    onClick = { onSongClick(song) },
                    onLongClick = {
                        haptics.play(Haptic.Select)
                        contextMenuTrigger(song)
                    }
                )
            }
        }
    }
}

@Composable
private fun HomeCompactGridShelf(
    shelf: HomeShelf,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    onShelfClick: (() -> Unit)? = null,
    onPlayAll: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (shelf.songs.isEmpty()) return
    val contextMenuTrigger = LocalSongContextMenu.current
    val haptics = rememberHaptics()

    val isQuickPicks = shelf.id == "shelf_quick_picks"
    // 4 items vertically stacked per section for Quick Picks or large shelves, 2 for others
    val chunkSize = if (isQuickPicks || shelf.songs.size >= 8) 4 else 2

    val songChunks = remember(shelf.songs, chunkSize) {
        shelf.songs.chunked(chunkSize)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        ShelfHeader(
            title = shelf.title,
            subtitle = shelf.subtitle,
            onClick = onShelfClick,
            onPlayAll = if (isQuickPicks) onPlayAll else null
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(
                items = songChunks,
                key = { index, _ -> "${shelf.id}_col_$index" }
            ) { colIndex, chunk ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    chunk.forEachIndexed { itemIndex, song ->
                        val isCurrent = currentSong?.id == song.id
                        val rank = if (isQuickPicks) null else (colIndex * chunkSize + itemIndex + 1)
                        CompactSongRowCard(
                            title = song.title,
                            subtitle = song.artist,
                            artworkUrl = song.highResArtworkUrl.ifEmpty { song.artworkUrl },
                            isPlaying = isPlaying && isCurrent,
                            isCurrent = isCurrent,
                            rank = rank,
                            onClick = { onSongClick(song) },
                            onLongClick = {
                                haptics.play(Haptic.Select)
                                contextMenuTrigger(song)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeArtistShelf(
    shelf: HomeShelf,
    onArtistClick: (ArtistCardData) -> Unit,
    modifier: Modifier = Modifier
) {
    if (shelf.artists.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        ShelfHeader(title = shelf.title, subtitle = shelf.subtitle)

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(
                items = shelf.artists,
                key = { _, artist -> "${shelf.id}_${artist.id}" }
            ) { _, artist ->
                ArtistCard(
                    name = artist.name,
                    subtitle = artist.subtitle,
                    artworkUrl = artist.artworkUrl,
                    onClick = { onArtistClick(artist) }
                )
            }
        }
    }
}

@Composable
private fun HomeFeaturedLargeShelf(
    shelf: HomeShelf,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    onShelfClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (shelf.songs.isEmpty()) return
    val contextMenuTrigger = LocalSongContextMenu.current
    val haptics = rememberHaptics()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
    ) {
        ShelfHeader(title = shelf.title, subtitle = shelf.subtitle, onClick = onShelfClick)

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(
                items = shelf.songs,
                key = { index, song -> "${shelf.id}_large_${index}_${song.id}" }
            ) { _, song ->
                val isCurrent = currentSong?.id == song.id
                FeaturedLargeAlbumCard(
                    title = song.title,
                    subtitle = if (song.album.isNotBlank()) song.album else "Single • ${song.artist}",
                    artworkUrl = song.highResArtworkUrl.ifEmpty { song.artworkUrl },
                    isPlaying = isPlaying && isCurrent,
                    isCurrent = isCurrent,
                    onClick = { onSongClick(song) },
                    onLongClick = {
                        haptics.play(Haptic.Select)
                        contextMenuTrigger(song)
                    }
                )
            }
        }
    }
}

@Composable
private fun HomeShelfSkeleton(
    title: String,
    isArtist: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentWhite.copy(alpha = 0.4f),
                    letterSpacing = (-0.3).sp
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            userScrollEnabled = false,
            modifier = Modifier.fillMaxWidth()
        ) {
            items(4) {
                if (isArtist) {
                    ArtistCardSkeleton()
                } else {
                    AlbumCardSkeleton()
                }
            }
        }
    }
}

@Composable
private fun HomeCategoryFilterRow(
    categories: List<String>,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isLiquidGlass = LocalLiquidGlassEnabled.current

    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        items(
            items = categories,
            key = { it }
        ) { category ->
            val isSelected = category == selectedCategory
            Surface(
                onClick = { onSelectCategory(category) },
                shape = RoundedCornerShape(20.dp),
                color = when {
                    isSelected -> AccentWhite
                    isLiquidGlass -> LiquidGlassTokens.PillGlassFill
                    else -> Color.White.copy(alpha = 0.08f)
                },
                border = when {
                    isSelected -> null
                    isLiquidGlass -> LiquidGlassTokens.specularBorderStroke()
                    else -> androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                },
                modifier = Modifier.height(34.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 14.dp)
                ) {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (isSelected) BackgroundBlack else AccentWhite.copy(alpha = 0.85f),
                            fontSize = 13.sp
                        )
                    )
                }
            }
        }
    }
}


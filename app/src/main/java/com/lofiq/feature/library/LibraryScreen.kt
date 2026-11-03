package com.lofiq.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.lofiq.R
import com.lofiq.auth.AccountDialog
import com.lofiq.auth.LoginDialog
import com.lofiq.core.model.Playlist
import com.lofiq.core.model.Song
import com.lofiq.ui.components.CollectionDetailScreen
import com.lofiq.ui.components.LofiQLogo
import com.lofiq.ui.components.SongRow
import com.lofiq.ui.haptics.Haptic
import com.lofiq.ui.haptics.rememberHaptics
import com.lofiq.ui.theme.*
import com.lofiq.feature.spotify.SpotifyImportDialog
import com.lofiq.feature.spotify.SpotifyLoginDialog

@Composable
fun LibraryScreen(
    onNavigateToLocalMusic: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val haptics = rememberHaptics()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by viewModel.playbackEngine.playerState.collectAsStateWithLifecycle()
    val userAccount by viewModel.userAccount.collectAsStateWithLifecycle()
    val spotifyAccount by viewModel.spotifyAccount.collectAsStateWithLifecycle()
    val isAuthenticating by viewModel.isAuthenticating.collectAsStateWithLifecycle()

    var showAccountDialog by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showSpotifyOptionsDialog by remember { mutableStateOf(false) }
    var showSpotifyLoginDialog by remember { mutableStateOf(false) }
    var showSpotifyImportDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    // System Back Press handling
    BackHandler(enabled = uiState.currentView != LibraryView.HOME || uiState.selectedPlaylist != null) {
        if (uiState.selectedPlaylist != null) {
            viewModel.closePlaylist()
        } else {
            viewModel.navigateBackToHome()
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        when (uiState.currentView) {
            LibraryView.HOME -> {
                LibraryHomeScreen(
                    uiState = uiState,
                    spotifyAccount = spotifyAccount,
                    onOpenSpotify = {
                        haptics.play(Haptic.Select)
                        showSpotifyOptionsDialog = true
                    },
                    onNavigateToLikedSongs = {
                        haptics.play(Haptic.Select)
                        viewModel.navigateToView(LibraryView.LIKED_SONGS)
                    },
                    onNavigateToDownloads = {
                        haptics.play(Haptic.Select)
                        viewModel.navigateToView(LibraryView.DOWNLOADS)
                    },
                    onNavigateToPlaylists = {
                        haptics.play(Haptic.Select)
                        viewModel.navigateToView(LibraryView.PLAYLISTS)
                    },
                    onNavigateToLocalMusic = {
                        haptics.play(Haptic.Select)
                        onNavigateToLocalMusic()
                    },
                    onPlaySong = { song, queue ->
                        haptics.play(Haptic.Tap)
                        viewModel.playSong(song, queue)
                    },
                    hasActiveSong = playerState.currentSong != null
                )
            }

            LibraryView.LIKED_SONGS -> {
                LikedSongsScreen(
                    uiState = uiState,
                    isPlaying = playerState.isPlaying,
                    currentSongId = playerState.currentSong?.id,
                    isLoggedIn = userAccount.isLoggedIn,
                    spotifyAccount = spotifyAccount,
                    onSyncLiked = { viewModel.syncYouTubeData() },
                    onBack = {
                        haptics.play(Haptic.Tap)
                        viewModel.navigateBackToHome()
                    },
                    onSelectFilter = { filter ->
                        haptics.play(Haptic.Tap)
                        viewModel.selectLikedFilter(filter)
                    },
                    onPlaySong = { song, queue ->
                        haptics.play(Haptic.Tap)
                        viewModel.playSong(song, queue)
                    },
                    onToggleFavorite = { song ->
                        haptics.play(Haptic.Tap)
                        viewModel.toggleFavorite(song)
                    }
                )
            }

            LibraryView.DOWNLOADS -> {
                LaunchedEffect(Unit) {
                    viewModel.refreshDownloads()
                }
                DownloadsScreen(
                    uiState = uiState,
                    isPlaying = playerState.isPlaying,
                    currentSongId = playerState.currentSong?.id,
                    onBack = {
                        haptics.play(Haptic.Tap)
                        viewModel.navigateBackToHome()
                    },
                    onPlaySong = { song, queue ->
                        haptics.play(Haptic.Tap)
                        viewModel.playSong(song, queue)
                    },
                    onToggleFavorite = { song ->
                        haptics.play(Haptic.Tap)
                        viewModel.toggleFavorite(song)
                    },
                    onDeleteDownload = { song ->
                        haptics.play(Haptic.Tap)
                        viewModel.deleteDownload(song)
                    }
                )
            }

            LibraryView.PLAYLISTS -> {
                PlaylistsScreen(
                    uiState = uiState,
                    isPlaying = playerState.isPlaying,
                    currentSongId = playerState.currentSong?.id,
                    isLoggedIn = userAccount.isLoggedIn,
                    spotifyAccount = spotifyAccount,
                    onSyncPlaylists = { viewModel.syncYouTubeData() },
                    onBack = {
                        haptics.play(Haptic.Tap)
                        if (uiState.selectedPlaylist != null) {
                            viewModel.closePlaylist()
                        } else {
                            viewModel.navigateBackToHome()
                        }
                    },
                    onCreatePlaylistClick = {
                        showCreatePlaylistDialog = true
                    },
                    onSelectFilter = { filter ->
                        haptics.play(Haptic.Tap)
                        viewModel.selectPlaylistFilter(filter)
                    },
                    onOpenPlaylist = { playlist ->
                        haptics.play(Haptic.Tap)
                        viewModel.openPlaylist(playlist)
                    },
                    onPlaySong = { song, queue ->
                        haptics.play(Haptic.Tap)
                        viewModel.playSong(song, queue)
                    },
                    onToggleFavorite = { song ->
                        haptics.play(Haptic.Tap)
                        viewModel.toggleFavorite(song)
                    }
                )
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

        // Secure Login Dialog
        if (showLoginDialog) {
            LoginDialog(
                onDismiss = { showLoginDialog = false },
                onAuthSuccess = { cookies ->
                    viewModel.onAuthCookiesCaptured(cookies)
                    showLoginDialog = false
                }
            )
        }

        // Dialog for creating a playlist
        if (showCreatePlaylistDialog) {
            AlertDialog(
                onDismissRequest = { showCreatePlaylistDialog = false },
                title = { Text("New Playlist", color = AccentWhite, fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Playlist Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AccentWhite,
                            unfocusedTextColor = AccentWhite,
                            cursorColor = AccentWhite,
                            focusedBorderColor = AccentWhite,
                            focusedLabelColor = AccentWhite
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newPlaylistName.isNotBlank()) {
                                viewModel.createPlaylist(newPlaylistName)
                                newPlaylistName = ""
                                showCreatePlaylistDialog = false
                            }
                        }
                    ) {
                        Text("Create", color = AccentWhite, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreatePlaylistDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = SurfaceElevated,
                shape = RoundedCornerShape(20.dp)
            )
        }

        // Spotify Integration Options Dialog
        if (showSpotifyOptionsDialog) {
            SpotifyOptionsDialog(
                spotifyAccount = spotifyAccount,
                onDismiss = { showSpotifyOptionsDialog = false },
                onOpenLogin = {
                    showSpotifyOptionsDialog = false
                    showSpotifyLoginDialog = true
                },
                onOpenImport = {
                    showSpotifyOptionsDialog = false
                    showSpotifyImportDialog = true
                },
                onSyncData = {
                    viewModel.syncSpotifyData()
                },
                onSignOut = {
                    viewModel.signOutSpotify()
                }
            )
        }

        // Spotify In-App Webview Login Dialog
        if (showSpotifyLoginDialog) {
            SpotifyLoginDialog(
                onDismiss = { showSpotifyLoginDialog = false },
                onAuthSuccess = { cookies ->
                    viewModel.onSpotifyCookiesCaptured(cookies)
                    showSpotifyLoginDialog = false
                }
            )
        }

        // Spotify Playlist Link Importer Dialog
        if (showSpotifyImportDialog) {
            SpotifyImportDialog(
                spotifyImportHelper = viewModel.spotifyImportHelper,
                onDismiss = { showSpotifyImportDialog = false },
                onImportSuccess = { _, _ ->
                    viewModel.syncLibrary()
                    showSpotifyImportDialog = false
                }
            )
        }
    }
}

/**
 * 1. Root Library Home View (Only shows the 4 primary Cards & Recent History/Stats)
 */
@Composable
private fun LibraryHomeScreen(
    uiState: LibraryUiState,
    spotifyAccount: com.lofiq.data.spotify.SpotifyAccount,
    onOpenSpotify: () -> Unit,
    onNavigateToLikedSongs: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToPlaylists: () -> Unit,
    onNavigateToLocalMusic: () -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    hasActiveSong: Boolean = false
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 0.dp, bottom = if (hasActiveSong) 220.dp else 155.dp)
    ) {
        // Top Bar & Title
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo_transparent),
                        contentDescription = "LofiQ Logo",
                        modifier = Modifier.size(34.dp)
                    )
                }

                Text(
                    text = "Library",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AccentWhite,
                        letterSpacing = (-0.6).sp
                    )
                )
            }
        }

        // 2x2 Grid of the 4 Main Category Cards
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Row 1: Liked Songs | Downloads
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OnDeviceCard(
                        title = "Liked Songs",
                        subtitle = "${uiState.favorites.size} songs",
                        icon = Icons.Rounded.Favorite,
                        gradient = Brush.linearGradient(
                            listOf(Color(0xFFE11D48), Color(0xFF881337))
                        ),
                        onClick = onNavigateToLikedSongs,
                        modifier = Modifier.weight(1f)
                    )

                    OnDeviceCard(
                        title = "Downloads",
                        subtitle = "${uiState.downloads.size} songs",
                        icon = Icons.Rounded.FileDownload,
                        gradient = Brush.linearGradient(
                            listOf(Color(0xFF2563EB), Color(0xFF1E3A8A))
                        ),
                        onClick = onNavigateToDownloads,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 2: Playlists | Local Music
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OnDeviceCard(
                        title = "Playlists",
                        subtitle = "${uiState.playlists.size} playlists",
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        gradient = Brush.linearGradient(
                            listOf(Color(0xFF7C3AED), Color(0xFF4C1D95))
                        ),
                        onClick = onNavigateToPlaylists,
                        modifier = Modifier.weight(1f)
                    )

                    OnDeviceCard(
                        title = "Local Music",
                        subtitle = "Audio files",
                        icon = Icons.Rounded.AudioFile,
                        gradient = Brush.linearGradient(
                            listOf(Color(0xFF0D9488), Color(0xFF134E4A))
                        ),
                        onClick = onNavigateToLocalMusic,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Spotify Integration Card (Replaces Stats & Overview)
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onOpenSpotify() },
                shape = RoundedCornerShape(20.dp),
                color = SurfaceElevated.copy(alpha = 0.85f),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (spotifyAccount.isLoggedIn) Color(0xFF1DB954).copy(alpha = 0.35f) else AccentWhite.copy(alpha = 0.08f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1DB954).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_spotify),
                                contentDescription = "Spotify Logo",
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (spotifyAccount.isLoggedIn) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Spotify Connected",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = AccentWhite,
                                            fontSize = 14.5.sp
                                        ),
                                        maxLines = 1
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1DB954))
                                    )
                                }
                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(
                                            SpanStyle(
                                                color = Color(0xFF1DB954),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        ) {
                                            append("with ${spotifyAccount.name.ifBlank { "User" }}")
                                        }
                                        append(" • Tap for options")
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Text(
                                    text = "Sign in with Spotify",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = AccentWhite,
                                        fontSize = 15.sp
                                    ),
                                    maxLines = 1
                                )
                                Text(
                                    text = "Sync your liked songs & import playlists",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (spotifyAccount.isLoggedIn) Color(0xFF1DB954).copy(alpha = 0.16f) else AccentWhite.copy(alpha = 0.08f),
                        border = BorderStroke(
                            0.8.dp,
                            if (spotifyAccount.isLoggedIn) Color(0xFF1DB954).copy(alpha = 0.35f) else AccentWhite.copy(alpha = 0.12f)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = if (spotifyAccount.isLoggedIn) "Options" else "Connect",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (spotifyAccount.isLoggedIn) Color(0xFF1DB954) else AccentWhite,
                                    fontSize = 11.sp
                                )
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = if (spotifyAccount.isLoggedIn) Color(0xFF1DB954) else AccentWhite,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dynamic Provider Filter Pills Row (All, Spotify [if active], YouTube [if active], LofiQ)
 */
@Composable
private fun ProviderFilterRow(
    selectedFilter: ProviderFilter,
    availableFilters: List<ProviderFilter>,
    onSelectFilter: (ProviderFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    if (availableFilters.size <= 1) return

    val haptics = rememberHaptics()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        availableFilters.forEach { filter ->
            val isSelected = filter == selectedFilter

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable {
                        haptics.play(Haptic.Tap)
                        onSelectFilter(filter)
                    },
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) AccentWhite else SurfaceElevated.copy(alpha = 0.85f),
                border = if (isSelected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    when (filter) {
                        ProviderFilter.ALL -> {
                            Icon(
                                imageVector = Icons.Rounded.AllInclusive,
                                contentDescription = null,
                                tint = if (isSelected) BackgroundBlack else TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        ProviderFilter.SPOTIFY -> {
                            Image(
                                painter = painterResource(id = R.drawable.ic_spotify),
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        ProviderFilter.YOUTUBE -> {
                            Image(
                                painter = painterResource(id = R.drawable.ic_youtube_music),
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        ProviderFilter.LOFIQ -> {
                            Image(
                                painter = painterResource(id = R.drawable.app_logo_transparent),
                                contentDescription = null,
                                colorFilter = if (isSelected) ColorFilter.tint(BackgroundBlack) else null,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Text(
                        text = filter.label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.5.sp,
                            color = if (isSelected) BackgroundBlack else TextSecondary
                        )
                    )
                }
            }
        }
    }
}

/**
 * 2. Dedicated Liked Songs View (Shows All, Spotify [if active], YouTube [if active], LofiQ dynamic filter tabs)
 */
@Composable
private fun LikedSongsScreen(
    uiState: LibraryUiState,
    isPlaying: Boolean,
    currentSongId: String?,
    isLoggedIn: Boolean = false,
    spotifyAccount: com.lofiq.data.spotify.SpotifyAccount,
    onSyncLiked: () -> Unit = {},
    onBack: () -> Unit,
    onSelectFilter: (ProviderFilter) -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onToggleFavorite: (Song) -> Unit
) {
    val isSpotifyActive = spotifyAccount.isLoggedIn || uiState.favorites.any { it.providerId == "spotify" || it.id.startsWith("sp_") }
    val isYouTubeActive = isLoggedIn

    val availableFilters = remember(isSpotifyActive, isYouTubeActive) {
        mutableListOf(ProviderFilter.ALL).apply {
            if (isSpotifyActive) add(ProviderFilter.SPOTIFY)
            if (isYouTubeActive) add(ProviderFilter.YOUTUBE)
            add(ProviderFilter.LOFIQ)
        }
    }

    val effectiveFilter = if (uiState.selectedLikedFilter in availableFilters) uiState.selectedLikedFilter else ProviderFilter.ALL
    val displayedTracks = remember(uiState.favorites, effectiveFilter, isLoggedIn) {
        uiState.copy(selectedLikedFilter = effectiveFilter).getFilteredFavorites(isYouTubeLoggedIn = isLoggedIn)
    }

    CollectionDetailScreen(
        title = "Liked Songs",
        subtitle = "You",
        typeLabel = "FAVORITES",
        tracks = displayedTracks,
        isPlaying = isPlaying,
        currentSongId = currentSongId,
        onBack = onBack,
        onPlaySong = onPlaySong,
        onSyncClick = if (isLoggedIn) onSyncLiked else null,
        onPlayAll = {
            if (displayedTracks.isNotEmpty()) {
                onPlaySong(displayedTracks.first(), displayedTracks)
            }
        },
        onShuffle = {
            if (displayedTracks.isNotEmpty()) {
                val shuffled = displayedTracks.shuffled()
                onPlaySong(shuffled.first(), shuffled)
            }
        },
        onToggleFavorite = onToggleFavorite,
        headerBottomContent = {
            ProviderFilterRow(
                selectedFilter = effectiveFilter,
                availableFilters = availableFilters,
                onSelectFilter = onSelectFilter
            )
        }
    )
}

/**
 * 3. Dedicated Downloads View
 */
@Composable
private fun DownloadsScreen(
    uiState: LibraryUiState,
    isPlaying: Boolean,
    currentSongId: String?,
    onBack: () -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onDeleteDownload: (Song) -> Unit
) {
    var songToDelete by remember { mutableStateOf<Song?>(null) }

    if (songToDelete != null) {
        val song = songToDelete!!
        AlertDialog(
            onDismissRequest = { songToDelete = null },
            title = {
                Text(
                    text = "Delete Download?",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = AccentWhite
                    )
                )
            },
            text = {
                Text(
                    text = "Remove '${song.title}' from offline downloads and device storage?",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteDownload(song)
                        songToDelete = null
                    }
                ) {
                    Text("Delete", color = Color(0xFFFF453A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { songToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = Color(0xFF1E1E24)
        )
    }
    val hasActiveSong = currentSongId != null
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 0.dp, bottom = if (hasActiveSong) 220.dp else 155.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onBack() }
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to Library",
                        tint = AccentWhite,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Library",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Downloads",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = AccentWhite,
                                fontSize = 26.sp,
                                letterSpacing = (-0.5).sp
                            )
                        )
                        Text(
                            text = "${uiState.downloads.size} songs • Saved to Music/LofiQ",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        )
                    }

                    if (uiState.downloads.isNotEmpty()) {
                        IconButton(
                            onClick = { onPlaySong(uiState.downloads.first(), uiState.downloads) },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(AccentWhite)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Play All Downloads",
                                tint = BackgroundBlack,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        }

        if (uiState.downloads.isEmpty()) {
            item {
                EmptyLibraryState(message = "No downloaded songs yet.\nTap download on any song in the player to listen offline without internet.")
            }
        } else {
            items(uiState.downloads.distinctBy { it.id }, key = { it.id }) { song ->
                val isCurrent = currentSongId == song.id
                SongRow(
                    song = song,
                    isPlaying = isPlaying,
                    isCurrent = isCurrent,
                    onClick = { onPlaySong(song, uiState.downloads) },
                    onFavoriteClick = { onToggleFavorite(song) },
                    onMoreClick = { songToDelete = song }
                )
            }
        }
    }
}

/**
 * 4. Dedicated Playlists View (List of playlists + dynamic provider filter tabs + single playlist view)
 */
@Composable
private fun PlaylistsScreen(
    uiState: LibraryUiState,
    isPlaying: Boolean,
    currentSongId: String?,
    isLoggedIn: Boolean = false,
    spotifyAccount: com.lofiq.data.spotify.SpotifyAccount,
    onSyncPlaylists: () -> Unit = {},
    onBack: () -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onSelectFilter: (ProviderFilter) -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onToggleFavorite: (Song) -> Unit
) {
    if (uiState.selectedPlaylist != null) {
        val pl = uiState.selectedPlaylist
        CollectionDetailScreen(
            title = pl.name,
            subtitle = if (pl.description.isNotBlank()) pl.description else "Custom Playlist",
            typeLabel = "PLAYLIST",
            artworkUrl = pl.artworkUrl,
            tracks = uiState.selectedPlaylistSongs,
            isPlaying = isPlaying,
            currentSongId = currentSongId,
            onBack = onBack,
            onPlaySong = onPlaySong,
            onPlayAll = {
                if (uiState.selectedPlaylistSongs.isNotEmpty()) {
                    onPlaySong(uiState.selectedPlaylistSongs.first(), uiState.selectedPlaylistSongs)
                }
            },
            onShuffle = {
                if (uiState.selectedPlaylistSongs.isNotEmpty()) {
                    val shuffled = uiState.selectedPlaylistSongs.shuffled()
                    onPlaySong(shuffled.first(), shuffled)
                }
            },
            onToggleFavorite = onToggleFavorite
        )
    } else {
        val isSpotifyActive = spotifyAccount.isLoggedIn || uiState.playlists.any { it.id.startsWith("spotify_pl_") || it.description.contains("Spotify", ignoreCase = true) }
        val isYouTubeActive = isLoggedIn || uiState.playlists.any { it.id.startsWith("yt_pl_") || it.description.contains("YouTube", ignoreCase = true) }

        val availableFilters = remember(isSpotifyActive, isYouTubeActive) {
            mutableListOf(ProviderFilter.ALL).apply {
                if (isSpotifyActive) add(ProviderFilter.SPOTIFY)
                if (isYouTubeActive) add(ProviderFilter.YOUTUBE)
                add(ProviderFilter.LOFIQ)
            }
        }

        val effectiveFilter = if (uiState.selectedPlaylistFilter in availableFilters) uiState.selectedPlaylistFilter else ProviderFilter.ALL
        val displayedPlaylists = remember(uiState.playlists, effectiveFilter, isLoggedIn) {
            uiState.copy(selectedPlaylistFilter = effectiveFilter).getFilteredPlaylists(isYouTubeLoggedIn = isLoggedIn)
        }

        val hasActiveSong = currentSongId != null
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 0.dp, bottom = if (hasActiveSong) 220.dp else 155.dp)
        ) {
            // Playlists Main View
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onBack() }
                            .padding(horizontal = 6.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to Library",
                            tint = AccentWhite,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Library",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Playlists",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = AccentWhite,
                                    fontSize = 26.sp,
                                    letterSpacing = (-0.5).sp
                                )
                            )
                            Text(
                                text = "${displayedPlaylists.size} playlists",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            )
                        }

                        IconButton(
                            onClick = onCreatePlaylistClick,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SurfaceElevated)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Create Playlist",
                                tint = AccentWhite,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // Dynamic Provider Filter Pills
            item {
                ProviderFilterRow(
                    selectedFilter = effectiveFilter,
                    availableFilters = availableFilters,
                    onSelectFilter = onSelectFilter,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            if (displayedPlaylists.isEmpty()) {
                item {
                    EmptyLibraryState(
                        message = if (uiState.isSyncing) "Syncing your playlists..." 
                                  else if (effectiveFilter != ProviderFilter.ALL) "No playlists found for ${effectiveFilter.label}."
                                  else if (isLoggedIn) "No playlists found in your local library.\nTap below to import your YouTube Music playlists." 
                                  else "No playlists found.\nConnect your Spotify or YouTube Music account, or tap + to create one.",
                        actionText = if (isLoggedIn && !uiState.isSyncing && effectiveFilter == ProviderFilter.ALL) "Sync YouTube Playlists" else null,
                        onActionClick = onSyncPlaylists
                    )
                }
            } else {
                itemsIndexed(displayedPlaylists, key = { index, playlist -> "pl_${index}_${playlist.id}" }) { _, playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPlaylist(playlist) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            if (playlist.artworkUrl.isNotBlank()) {
                                AsyncImage(
                                    model = playlist.artworkUrl,
                                    contentDescription = playlist.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                    contentDescription = null,
                                    tint = AccentWhite,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = AccentWhite
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (playlist.description.isNotBlank()) playlist.description else "${playlist.songCount} songs",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OnDeviceCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradient: Brush,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(116.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(gradient)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, AccentWhite, RoundedCornerShape(20.dp))
                } else {
                    Modifier.border(1.dp, AccentWhite.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                }
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.28f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = AccentWhite,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
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
                        color = AccentWhite.copy(alpha = 0.75f),
                        fontSize = 11.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(
    message: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = TextSecondary,
                lineHeight = 22.sp
            ),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (actionText != null && onActionClick != null) {
            Button(
                onClick = onActionClick,
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
                Text(
                    text = actionText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun SpotifyOptionsDialog(
    spotifyAccount: com.lofiq.data.spotify.SpotifyAccount,
    onDismiss: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenImport: () -> Unit,
    onSyncData: () -> Unit,
    onSignOut: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_spotify),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    text = if (spotifyAccount.isLoggedIn) "Spotify Connected" else "Spotify Integration",
                    color = AccentWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (spotifyAccount.isLoggedIn) {
                    // Profile Info
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceCard)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!spotifyAccount.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = spotifyAccount.avatarUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1DB954)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = spotifyAccount.name.take(1).uppercase(),
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = spotifyAccount.name,
                                color = AccentWhite,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Synced with LofiQ",
                                color = Color(0xFF1DB954),
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Action 1: Import Playlist via URL
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onOpenImport()
                            },
                        color = SurfaceCard,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.Link, contentDescription = null, tint = Color(0xFF1DB954), modifier = Modifier.size(20.dp))
                            Column {
                                Text("Import Playlist from Link", color = AccentWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Paste any Spotify playlist link to import", color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                    }

                    // Action 2: Sync Liked Songs & Playlists
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onSyncData()
                                onDismiss()
                            },
                        color = SurfaceCard,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.Sync, contentDescription = null, tint = Color(0xFF1DB954), modifier = Modifier.size(20.dp))
                            Column {
                                Text("Sync Liked Songs & Playlists", color = AccentWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Re-fetch and update library from Spotify", color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                    }

                    // Action 3: Disconnect Spotify
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onSignOut()
                                onDismiss()
                            },
                        color = SurfaceCard,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                            Text("Disconnect Spotify", color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                } else {
                    Text(
                        text = "Bring your Spotify library directly into LofiQ with high-fidelity audio resolution.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    // Option A: Log in to Spotify
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onOpenLogin()
                            },
                        color = SurfaceCard,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1DB954).copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_spotify),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Sign In with Spotify", color = AccentWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Auto-sync your Liked Songs and personal playlists", color = TextSecondary, fontSize = 11.5.sp)
                            }
                        }
                    }

                    // Option B: Import Playlist via Link
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onOpenImport()
                            },
                        color = SurfaceCard,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentWhite.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Link, contentDescription = null, tint = AccentWhite, modifier = Modifier.size(18.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Import Playlist from Link", color = AccentWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Paste any public Spotify or YouTube playlist URL", color = TextSecondary, fontSize = 11.5.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary)
            }
        },
        containerColor = SurfaceElevated,
        shape = RoundedCornerShape(24.dp)
    )
}

package com.lofiq.feature.explore

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.lofiq.R
import com.lofiq.auth.AccountDialog
import com.lofiq.auth.LoginDialog
import com.lofiq.ui.components.LofiQLogo
import com.lofiq.ui.components.SongRow
import com.lofiq.ui.haptics.Haptic
import com.lofiq.ui.haptics.rememberHaptics
import com.lofiq.ui.theme.*

data class MoodItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val gradientColors: List<Color>,
    val imageUrl: String,
    val searchQuery: String
)

val defaultMoods = listOf(
    MoodItem(
        id = "chill",
        title = "Chill",
        subtitle = "Coffee Shop Blend",
        gradientColors = listOf(Color(0xFFD97706), Color(0xFF92400E)),
        imageUrl = "https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?w=300&q=80",
        searchQuery = "Chill Acoustic Relaxing Songs"
    ),
    MoodItem(
        id = "commute",
        title = "Commute",
        subtitle = "Feel-Good Hip Hop",
        gradientColors = listOf(Color(0xFF8B5CF6), Color(0xFF5B21B6)),
        imageUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=300&q=80",
        searchQuery = "Commute Vibes Pop Hits"
    ),
    MoodItem(
        id = "energize",
        title = "Energize",
        subtitle = "pump-up pop",
        gradientColors = listOf(Color(0xFF4F46E5), Color(0xFF3730A3)),
        imageUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=300&q=80",
        searchQuery = "Energizing Workout Pop Hits"
    ),
    MoodItem(
        id = "feel_good",
        title = "Feel good",
        subtitle = "Sunshine Indie",
        gradientColors = listOf(Color(0xFFBE185D), Color(0xFF831843)),
        imageUrl = "https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?w=300&q=80",
        searchQuery = "Feel Good Happy Songs"
    ),
    MoodItem(
        id = "focus",
        title = "Focus",
        subtitle = "Lofi Loft",
        gradientColors = listOf(Color(0xFFEA580C), Color(0xFF9A3412)),
        imageUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=300&q=80",
        searchQuery = "Lofi Study Focus Beats"
    ),
    MoodItem(
        id = "gaming",
        title = "Gaming",
        subtitle = "Gaming Hits",
        gradientColors = listOf(Color(0xFF9333EA), Color(0xFF6B21A8)),
        imageUrl = "https://images.unsplash.com/photo-1538481199705-c710c4e965fc?w=300&q=80",
        searchQuery = "Gaming EDM Hits"
    ),
    MoodItem(
        id = "party",
        title = "Party",
        subtitle = "Bollywood Dance Essential",
        gradientColors = listOf(Color(0xFF2563EB), Color(0xFF1E40AF)),
        imageUrl = "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=300&q=80",
        searchQuery = "Bollywood Party Dance Hits"
    ),
    MoodItem(
        id = "romance",
        title = "Romance",
        subtitle = "Bollywood Romantic Moments",
        gradientColors = listOf(Color(0xFFDB2777), Color(0xFF9D174D)),
        imageUrl = "https://images.unsplash.com/photo-1516589178581-6cd7833ae3b2?w=300&q=80",
        searchQuery = "Bollywood Romantic Love Songs"
    ),
    MoodItem(
        id = "workout",
        title = "Workout",
        subtitle = "Power Bass & Gym",
        gradientColors = listOf(Color(0xFF0D9488), Color(0xFF115E59)),
        imageUrl = "https://images.unsplash.com/photo-1534438327276-14e5300c3a48?w=300&q=80",
        searchQuery = "Workout Gym Bass Boosted Songs"
    ),
    MoodItem(
        id = "desi_hits",
        title = "Desi Hits",
        subtitle = "Top Bollywood & Punjabi",
        gradientColors = listOf(Color(0xFFDC2626), Color(0xFF991B1B)),
        imageUrl = "https://images.unsplash.com/photo-1514533450685-4493e01d1fdc?w=300&q=80",
        searchQuery = "Top Bollywood Punjabi Hits 2026"
    ),
    MoodItem(
        id = "sleep",
        title = "Sleep & Relax",
        subtitle = "Calm Night & Rain",
        gradientColors = listOf(Color(0xFF334155), Color(0xFF0F172A)),
        imageUrl = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=300&q=80",
        searchQuery = "Calm Relaxing Deep Sleep Music"
    ),
    MoodItem(
        id = "soulful",
        title = "Soulful",
        subtitle = "Sufi & Acoustic",
        gradientColors = listOf(Color(0xFF0284C7), Color(0xFF0369A1)),
        imageUrl = "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=300&q=80",
        searchQuery = "Soulful Sufi Acoustic Hits"
    )
)

@Composable
fun ExploreScreen(
    onNavigateToSettings: () -> Unit = {},
    viewModel: ExploreViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val haptics = rememberHaptics()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val userAccount by viewModel.userAccount.collectAsStateWithLifecycle()
    val isAuthenticating by viewModel.isAuthenticating.collectAsStateWithLifecycle()
    val playerState by viewModel.playbackEngine.playerState.collectAsStateWithLifecycle()

    var showAccountDialog by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        if (uiState.selectedMood != null) {
            val mood = uiState.selectedMood!!
            // Mood Songs Detail Screen
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 0.dp, bottom = 120.dp)
            ) {
                // Top Header with Back button and Gradient Card
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
                                .clickable {
                                    haptics.play(Haptic.Tap)
                                    viewModel.clearSelectedMood()
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowBackIosNew,
                                contentDescription = "Back",
                                tint = AccentWhite,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Moods & moments",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Big Gradient Banner
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(Brush.linearGradient(mood.gradientColors))
                                .padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = mood.title,
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 28.sp,
                                            color = AccentWhite
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = mood.subtitle,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = Color.White.copy(alpha = 0.85f),
                                            fontSize = 13.sp
                                        )
                                    )
                                }

                                if (uiState.moodSongs.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            haptics.play(Haptic.Select)
                                            viewModel.playSong(uiState.moodSongs.first(), uiState.moodSongs)
                                        },
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(AccentWhite)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = "Play All",
                                            tint = BackgroundBlack,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Top Tracks",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = AccentWhite
                            )
                        )
                    }
                }

                if (uiState.isLoadingMoodSongs) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = AccentWhite.copy(alpha = 0.6f),
                                modifier = Modifier.size(30.dp),
                                strokeWidth = 2.5.dp
                            )
                        }
                    }
                } else if (uiState.moodSongs.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 60.dp, horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Unable to load songs for this category.\nPlease check your connection.",
                                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    itemsIndexed(uiState.moodSongs, key = { index, song -> "mood_${mood.id}_${index}_${song.id}" }) { _, song ->
                        val isCurrent = playerState.currentSong?.id == song.id
                        SongRow(
                            song = song,
                            isPlaying = playerState.isPlaying,
                            isCurrent = isCurrent,
                            onClick = {
                                haptics.play(Haptic.Tap)
                                viewModel.playSong(song, uiState.moodSongs)
                            },
                            onFavoriteClick = {
                                haptics.play(Haptic.Tap)
                                viewModel.toggleFavorite(song)
                            }
                        )
                    }
                }
            }
        } else {
            // Main Moods & Moments Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                item(span = { GridItemSpan(2) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(top = 2.dp, bottom = 8.dp)
                    ) {
                        // Top App Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.app_logo_transparent),
                                contentDescription = "LofiQ Logo",
                                modifier = Modifier.size(34.dp)
                            )

                            // Profile Avatar Button
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceElevated)
                                    .border(
                                        width = 1.5.dp,
                                        color = if (userAccount.isLoggedIn) AccentWhite else AccentWhite.copy(alpha = 0.2f),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        haptics.play(Haptic.Tap)
                                        showAccountDialog = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (userAccount.isLoggedIn && !userAccount.avatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = userAccount.avatarUrl,
                                        contentDescription = "User Avatar",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (userAccount.isLoggedIn) Icons.Rounded.Person else Icons.Rounded.AccountCircle,
                                        contentDescription = "Account",
                                        tint = if (userAccount.isLoggedIn) AccentWhite else AccentWhite.copy(alpha = 0.8f),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        // Bold "Explore" Title
                        Text(
                            text = "Explore",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 30.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AccentWhite,
                                letterSpacing = (-0.6).sp
                            )
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Subtitle: "Moods & moments"
                        Text(
                            text = "Moods & moments",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentWhite
                            )
                        )
                    }
                }

                // Mood & Moments Grid Items
                items(defaultMoods, key = { it.id }) { mood ->
                    MoodCard(
                        mood = mood,
                        onClick = {
                            haptics.play(Haptic.Select)
                            viewModel.selectMood(mood)
                        }
                    )
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
                onRefreshProfile = { viewModel.refreshProfile() },
                onSignOut = { viewModel.signOut() },
                onOpenSettings = onNavigateToSettings
            )
        }

        // Secure Login Dialog
        if (showLoginDialog) {
            LoginDialog(
                onDismiss = { showLoginDialog = false },
                onAuthSuccess = { cookies ->
                    viewModel.onCookiesCaptured(cookies)
                    showLoginDialog = false
                }
            )
        }
    }
}

@Composable
fun MoodCard(
    mood: MoodItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(mood.gradientColors))
            .clickable(onClick = onClick)
    ) {
        // Thumbnail tilted / positioned at bottom right
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 10.dp, y = 10.dp)
                .rotate(22f)
                .size(68.dp)
                .shadow(8.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceCard)
        ) {
            AsyncImage(
                model = mood.imageUrl,
                contentDescription = mood.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Mood Title & Subtitle on top-left
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 14.dp, top = 12.dp, end = 50.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = mood.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = AccentWhite,
                    fontSize = 17.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (mood.subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = mood.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

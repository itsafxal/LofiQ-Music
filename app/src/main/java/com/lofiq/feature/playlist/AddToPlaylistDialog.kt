package com.lofiq.feature.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lofiq.core.model.Playlist
import com.lofiq.core.model.Song
import com.lofiq.data.repository.LibraryRepository
import com.lofiq.ui.components.glass.*
import com.lofiq.ui.haptics.Haptic
import com.lofiq.ui.haptics.rememberHaptics
import com.lofiq.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistDialog(
    song: Song,
    libraryRepository: LibraryRepository,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val playlists by libraryRepository.observePlaylists().collectAsState(initial = emptyList())
    var showCreateNewDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var addedFeedback by remember { mutableStateOf<String?>(null) }

    val neonCyan = Color(0xFF00E5FF)
    val cardBg = Color(0xFF141419)
    val cardBorder = Color(0xFF26262F)

    GlassBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF9500).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlaylistAdd,
                            contentDescription = null,
                            tint = Color(0xFFFF9500),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Add to Playlist",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = AccentWhite,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = "Choose a playlist for '${song.title}'",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary,
                                fontSize = 12.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = AccentWhite,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Create New Playlist Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(neonCyan.copy(alpha = 0.12f))
                    .border(1.dp, neonCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .clickable {
                        haptics.play(Haptic.Select)
                        showCreateNewDialog = true
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = neonCyan,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Create New Playlist",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = neonCyan,
                        fontSize = 14.sp
                    )
                )
            }

            // Existing Playlists List
            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No custom playlists yet. Create one above!",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(playlists) { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(cardBg)
                                .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    haptics.play(Haptic.Select)
                                    coroutineScope.launch {
                                        libraryRepository.addSongToPlaylist(playlist.id, song)
                                        addedFeedback = "Added to ${playlist.name}"
                                        kotlinx.coroutines.delay(600)
                                        onDismiss()
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.QueueMusic,
                                    contentDescription = null,
                                    tint = AccentWhite,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = AccentWhite,
                                        fontSize = 14.sp
                                    )
                                )
                                Text(
                                    text = playlist.description.ifEmpty { "Playlist" },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.AddCircleOutline,
                                contentDescription = null,
                                tint = neonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // New Playlist Name Input Dialog (Liquid Glass Modal)
    if (showCreateNewDialog) {
        GlassDialog(
            onDismissRequest = { showCreateNewDialog = false },
            icon = Icons.Rounded.PlaylistAdd,
            title = "New Playlist",
            confirmButton = {
                GlassButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            coroutineScope.launch {
                                libraryRepository.createPlaylist(newPlaylistName.trim())
                                showCreateNewDialog = false
                                newPlaylistName = ""
                            }
                        }
                    },
                    isPrimary = true,
                    accentColor = BrandTangerine
                ) {
                    Text("Create", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                GlassButton(
                    onClick = { showCreateNewDialog = false },
                    preset = GlassPreset.SUBTLE
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        ) {
            GlassInput(
                value = newPlaylistName,
                onValueChange = { newPlaylistName = it },
                placeholder = "Playlist Name"
            )
        }
    }
}

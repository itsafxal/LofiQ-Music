package com.lofiq.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lofiq.core.common.TimeFormatUtils
import com.lofiq.core.model.Song
import com.lofiq.ui.theme.*

@Composable
fun SongRow(
    song: Song,
    isPlaying: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isLiquidGlass = isLiquidGlassActive()
    val brandColor = LocalBrandColor.current

    val animatedBg by animateColorAsState(
        targetValue = if (isCurrent) {
            if (isLiquidGlass) brandColor.copy(alpha = 0.15f) else brandColor.copy(alpha = 0.10f)
        } else Color.Transparent,
        label = "songRowBg"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(animatedBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Artwork
        ArtworkImage(
            artworkUrl = com.lofiq.core.common.ArtworkUtils.getOptimalArtwork(song),
            size = 52.dp,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        // Titles
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = if (isCurrent) AccentWhite else TextPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "${song.artist} • ${TimeFormatUtils.formatSeconds(song.durationSeconds)}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = if (isCurrent) AccentWhite.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.78f),
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Animated playing indicator if active
        if (isCurrent && isPlaying) {
            WaveformIndicator(modifier = Modifier.padding(horizontal = 8.dp))
        }

        // Favorite Toggle Button
        if (onFavoriteClick != null) {
            IconButton(
                onClick = onFavoriteClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (song.isFavorite) AccentRed else Color.White.copy(alpha = 0.70f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Overflow More Button
        if (onMoreClick != null) {
            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "More options",
                    tint = Color.White.copy(alpha = 0.78f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

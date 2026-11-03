package com.lofiq.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lofiq.ui.animation.LiquidShimmerBox
import com.lofiq.ui.theme.AccentWhite
import com.lofiq.ui.theme.BrandTeal
import com.lofiq.ui.theme.SurfaceElevated
import com.lofiq.ui.theme.TextTertiary

@Composable
fun ArtworkImage(
    artworkUrl: String,
    modifier: Modifier = Modifier,
    size: Dp? = null,
    shape: Shape = RoundedCornerShape(8.dp),
    contentDescription: String? = "Song Artwork"
) {
    val mod = if (size != null) modifier.size(size) else modifier
    val context = LocalContext.current

    // Build reliable high-res artwork URL via ArtworkUtils
    val resolvedUrl = remember(artworkUrl) {
        com.lofiq.core.common.ArtworkUtils.getHighResArtworkUrl(artworkUrl)
    }

    Box(
        modifier = mod
            .clip(shape)
            .background(SurfaceElevated),
        contentAlignment = Alignment.Center
    ) {
        if (resolvedUrl.isNotBlank()) {
            // Modern liquid shimmer placeholder while loading
            LiquidShimmerBox(
                modifier = Modifier.matchParentSize(),
                shape = shape
            )

            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(resolvedUrl)
                    .crossfade(200)
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun ArtistAvatar(
    name: String,
    artworkUrl: String,
    modifier: Modifier = Modifier,
    size: Dp = 104.dp
) {
    val initials = name.trim().split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")

    val gradientColors = listOf(
        Color(0xFF2E3440),
        Color(0xFF1E222A),
        Color(0xFF14171E)
    )

    val context = LocalContext.current

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.radialGradient(gradientColors)),
        contentAlignment = Alignment.Center
    ) {
        if (initials.isNotEmpty()) {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleLarge.copy(
                    color = AccentWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.32f).sp
                )
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                tint = BrandTeal,
                modifier = Modifier.size(size * 0.45f)
            )
        }

        if (artworkUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .crossfade(250)
                    .error(com.lofiq.R.drawable.app_logo)
                    .fallback(com.lofiq.R.drawable.app_logo)
                    .build(),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

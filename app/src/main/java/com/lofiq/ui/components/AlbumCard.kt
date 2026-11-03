package com.lofiq.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lofiq.ui.theme.AccentWhite
import com.lofiq.ui.theme.SurfaceElevated
import com.lofiq.ui.theme.TextPrimary
import com.lofiq.ui.theme.TextSecondary

@Composable
fun AlbumCard(
    title: String,
    subtitle: String,
    artworkUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    isCurrent: Boolean = false
) {
    Column(
        modifier = modifier
            .width(148.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            ArtworkImage(
                artworkUrl = artworkUrl,
                size = 140.dp,
                shape = RoundedCornerShape(12.dp)
            )

            // Playing state indicator overlay
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            if (isPlaying) Color.Black.copy(alpha = 0.45f)
                            else Color.Black.copy(alpha = 0.25f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPlaying) {
                        WaveformIndicator()
                    } else {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "❚❚",
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isCurrent) AccentWhite else TextPrimary,
                fontSize = 14.sp,
                lineHeight = 18.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AlbumCardSkeleton(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "SkeletonShimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SkeletonAlpha"
    )

    Column(
        modifier = modifier
            .width(148.dp)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceElevated.copy(alpha = alpha))
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .width(110.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SurfaceElevated.copy(alpha = alpha))
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(70.dp)
                .height(11.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SurfaceElevated.copy(alpha = alpha * 0.7f))
        )
    }
}

@Composable
fun ArtistCard(
    name: String,
    subtitle: String,
    artworkUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(116.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ArtistAvatar(
            name = name,
            artworkUrl = artworkUrl,
            size = 104.dp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = AccentWhite,
                fontSize = 13.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(
                color = TextSecondary,
                fontSize = 11.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ArtistCardSkeleton(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "SkeletonShimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ArtistSkeletonAlpha"
    )

    Column(
        modifier = modifier
            .width(116.dp)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(RoundedCornerShape(52.dp))
                .background(SurfaceElevated.copy(alpha = alpha))
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .width(72.dp)
                .height(13.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SurfaceElevated.copy(alpha = alpha))
        )
    }
}

@Composable
fun CompactSongRowCard(
    title: String,
    subtitle: String,
    artworkUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    isCurrent: Boolean = false,
    rank: Int? = null
) {
    Row(
        modifier = modifier
            .width(280.dp)
            .height(58.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (rank != null) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.labelMedium.copy(
                    color = if (isCurrent) AccentWhite else Color.White.copy(alpha = 0.75f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                ),
                modifier = Modifier.width(24.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            ArtworkImage(
                artworkUrl = artworkUrl,
                size = 48.dp,
                shape = RoundedCornerShape(8.dp)
            )

            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPlaying) {
                        WaveformIndicator()
                    } else {
                        Text(text = "❚❚", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isCurrent) AccentWhite else TextPrimary,
                    fontSize = 13.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (isCurrent) AccentWhite.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.78f),
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.5.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun WaveformIndicator(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "WaveformAnimation")
    
    val height1 by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = 16f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Bar1"
    )
    val height2 by infiniteTransition.animateFloat(
        initialValue = 16f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(350, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Bar2"
    )
    val height3 by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Bar3"
    )

    Row(
        modifier = modifier.height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .height(height1.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(AccentWhite)
        )
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .height(height2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(AccentWhite)
        )
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .height(height3.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(AccentWhite)
        )
    }
}

/**
 * Large Featured Album Card (220dp x 220dp) with bottom gradient overlay & metadata
 * Designed for "Albums & singles" / "New Releases" shelves as shown in Image 2.
 */
@Composable
fun FeaturedLargeAlbumCard(
    title: String,
    subtitle: String,
    artworkUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    isCurrent: Boolean = false
) {
    Box(
        modifier = modifier
            .width(215.dp)
            .height(215.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        ArtworkImage(
            artworkUrl = artworkUrl,
            size = 215.dp,
            shape = RoundedCornerShape(18.dp)
        )

        // Scrim gradient for readability at the bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.25f),
                            Color.Black.copy(alpha = 0.88f)
                        ),
                        startY = 180f
                    )
                )
        )

        if (isCurrent) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.40f)),
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying) {
                    WaveformIndicator()
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = 0.9f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "❚❚",
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Text metadata inside bottom of card
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = AccentWhite,
                    fontSize = 15.sp,
                    letterSpacing = (-0.2).sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

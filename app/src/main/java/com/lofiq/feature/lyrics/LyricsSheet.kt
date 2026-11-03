package com.lofiq.feature.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lofiq.playback.engine.PlaybackEngine
import com.lofiq.ui.theme.BackgroundBlack
import com.lofiq.ui.theme.isLiquidGlassActive

/**
 * LofiQ Modern Synchronized Lyrics Bottom Sheet with fluid liquid-glass styling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSheet(
    playbackEngine: PlaybackEngine,
    onDismiss: () -> Unit
) {
    val isLiquidGlass = isLiquidGlassActive()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            SyncedLyricsContent(
                playbackEngine = playbackEngine,
                onClose = onDismiss
            )
        }
    }
}

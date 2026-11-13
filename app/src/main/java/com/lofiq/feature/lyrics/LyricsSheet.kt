package com.lofiq.feature.lyrics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lofiq.playback.engine.PlaybackEngine
import com.lofiq.ui.components.glass.GlassBottomSheet

/**
 * LofiQ Modern Synchronized Lyrics Bottom Sheet with fluid liquid-glass styling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSheet(
    playbackEngine: PlaybackEngine,
    onDismiss: () -> Unit
) {
    GlassBottomSheet(
        onDismissRequest = onDismiss
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

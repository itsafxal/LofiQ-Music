package com.lofiq.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lofiq.MainActivity
import com.lofiq.core.common.LofiQLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles all click actions originating from the three home screen music widgets.
 * Dispatches commands directly to the authoritative PlaybackEngine and LibraryRepository.
 * Supports background store playback restoration when the app was in background or closed.
 */
class MusicWidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "[WIDGET] Action received: $action")

        val engine = MusicWidgetManager.getPlaybackEngine(context)
        val libraryRepo = MusicWidgetManager.getLibraryRepository(context)

        when (action) {
            MusicWidgetConstants.ACTION_PLAY_PAUSE -> {
                val state = engine?.playerState?.value
                if (engine == null || state?.currentSong == null) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val restored = engine?.restoreAndPlayLastTrack() ?: false
                            if (!restored) {
                                val launchIntent = (context.packageManager.getLaunchIntentForPackage(context.packageName)
                                    ?: Intent(context, MainActivity::class.java)).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                }
                                context.startActivity(launchIntent)
                            }
                            MusicWidgetManager.updateAllWidgets(context)
                        } catch (e: Exception) {
                            LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[WIDGET] Error restoring playback: ${e.message}")
                        } finally {
                            pendingResult.finish()
                        }
                    }
                } else {
                    engine.togglePlayPause()
                    MusicWidgetManager.updateAllWidgets(context)
                }
            }

            MusicWidgetConstants.ACTION_PREV -> {
                engine?.previous()
                MusicWidgetManager.updateAllWidgets(context)
            }

            MusicWidgetConstants.ACTION_NEXT -> {
                engine?.next()
                MusicWidgetManager.updateAllWidgets(context)
            }

            MusicWidgetConstants.ACTION_SHUFFLE -> {
                engine?.toggleShuffle()
                MusicWidgetManager.updateAllWidgets(context)
            }

            MusicWidgetConstants.ACTION_TOGGLE_FAVORITE -> {
                val currentSong = engine?.playerState?.value?.currentSong
                if (currentSong != null && libraryRepo != null) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            libraryRepo.toggleFavorite(currentSong)
                            MusicWidgetManager.updateAllWidgets(context)
                        } catch (e: Exception) {
                            LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[WIDGET] Failed to toggle favorite: ${e.message}")
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }

            MusicWidgetConstants.ACTION_UPDATE_ALL -> {
                MusicWidgetManager.updateAllWidgets(context)
            }
        }
    }
}

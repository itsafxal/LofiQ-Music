package com.lofiq.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import android.widget.RemoteViews
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.lofiq.MainActivity
import com.lofiq.R
import com.lofiq.core.common.ArtworkUtils
import com.lofiq.core.common.LofiQLogger
import com.lofiq.core.model.LofiQPlayerState
import com.lofiq.core.model.Song
import com.lofiq.data.repository.LibraryRepository
import com.lofiq.playback.engine.PlaybackEngine
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

object MusicWidgetManager {

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observerJob: Job? = null
    private var favoriteJob: Job? = null

    private var cachedPlaybackEngine: PlaybackEngine? = null
    private var cachedLibraryRepository: LibraryRepository? = null

    // Cache styled bitmaps: key = "${songId}_${widgetType}"
    private val bitmapCache = object : LruCache<String, Bitmap>(9) {}
    private var currentFavoriteState: Boolean = false
    private var lastObservedSongId: String? = null

    /**
     * Initializes the manager and starts listening to the authoritative PlaybackEngine StateFlow.
     */
    fun init(
        context: Context,
        playbackEngine: PlaybackEngine,
        libraryRepository: LibraryRepository
    ) {
        cachedPlaybackEngine = playbackEngine
        cachedLibraryRepository = libraryRepository

        observerJob?.cancel()
        observerJob = managerScope.launch {
            playbackEngine.playerState.collectLatest { state ->
                val song = state.currentSong
                if (song?.id != lastObservedSongId) {
                    lastObservedSongId = song?.id
                    favoriteJob?.cancel()
                    if (song != null) {
                        favoriteJob = launch {
                            libraryRepository.isFavorite(song.id)
                                .distinctUntilChanged()
                                .collectLatest { isFav ->
                                    currentFavoriteState = isFav
                                    updateAllWidgets(context, state, isFav)
                                }
                        }
                    } else {
                        currentFavoriteState = false
                    }
                }
                updateAllWidgets(context, state, currentFavoriteState)
            }
        }

        // Trigger immediate render on boot/init with background store lookup
        managerScope.launch(Dispatchers.IO) {
            updateAllWidgets(context)
        }
    }

    /**
     * Obtains PlaybackEngine either from cached instance or via Hilt EntryPoint.
     */
    fun getPlaybackEngine(context: Context): PlaybackEngine? {
        if (cachedPlaybackEngine != null) return cachedPlaybackEngine
        return runCatching {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                MusicWidgetEntryPoint::class.java
            )
            cachedPlaybackEngine = entryPoint.playbackEngine()
            cachedPlaybackEngine
        }.getOrNull()
    }

    /**
     * Obtains LibraryRepository either from cached instance or via Hilt EntryPoint.
     */
    fun getLibraryRepository(context: Context): LibraryRepository? {
        if (cachedLibraryRepository != null) return cachedLibraryRepository
        return runCatching {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                MusicWidgetEntryPoint::class.java
            )
            cachedLibraryRepository = entryPoint.libraryRepository()
            cachedLibraryRepository
        }.getOrNull()
    }

    /**
     * Updates all three home screen widgets across all active instances.
     * Accurately supports live playback changes and background store state.
     */
    fun updateAllWidgets(
        context: Context,
        passedState: LofiQPlayerState? = null,
        passedIsFavorite: Boolean? = null
    ) {
        managerScope.launch(Dispatchers.IO) {
            val appContext = context.applicationContext
            val appWidgetManager = AppWidgetManager.getInstance(appContext) ?: return@launch

            val squareIds = appWidgetManager.getAppWidgetIds(
                ComponentName(appContext, CompactSquareWidgetProvider::class.java)
            )
            val wideIds = appWidgetManager.getAppWidgetIds(
                ComponentName(appContext, WidePlayerWidgetProvider::class.java)
            )
            val pillIds = appWidgetManager.getAppWidgetIds(
                ComponentName(appContext, MinimalPillWidgetProvider::class.java)
            )

            // If no widgets are installed on the home screen, avoid unnecessary rendering
            if (squareIds.isEmpty() && wideIds.isEmpty() && pillIds.isEmpty()) {
                return@launch
            }

            val engine = getPlaybackEngine(appContext)
            val state = passedState ?: engine?.playerState?.value ?: LofiQPlayerState()
            
            // Check active song or restore last played song from persistent database store
            val currentSong = state.currentSong
            val displaySong = currentSong ?: runCatching {
                engine?.getSavedQueueOrEmpty()?.firstOrNull()
            }.getOrNull()

            val isFav = passedIsFavorite ?: if (displaySong != null) {
                currentFavoriteState
            } else {
                false
            }

            // Load and process bitmaps tailored to each widget style
            val (squareArt, wideArt, pillArt) = getArtworkBitmaps(appContext, displaySong)

            val launchIntent = getLaunchAppPendingIntent(appContext)
            val playPauseIntent = getActionPendingIntent(appContext, MusicWidgetConstants.ACTION_PLAY_PAUSE, 101)
            val prevIntent = getActionPendingIntent(appContext, MusicWidgetConstants.ACTION_PREV, 102)
            val nextIntent = getActionPendingIntent(appContext, MusicWidgetConstants.ACTION_NEXT, 103)
            val shuffleIntent = getActionPendingIntent(appContext, MusicWidgetConstants.ACTION_SHUFFLE, 104)
            val likeIntent = getActionPendingIntent(appContext, MusicWidgetConstants.ACTION_TOGGLE_FAVORITE, 105)

            val isPlaying = state.isPlaying
            val playPauseIcon = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play

            // 1. Update 2x2 Compact Square Widgets (Full-bleed artwork background + bottom centered controls)
            if (squareIds.isNotEmpty()) {
                for (id in squareIds) {
                    val views = RemoteViews(appContext.packageName, R.layout.widget_compact_square_2x2).apply {
                        setImageViewBitmap(R.id.widget_bg_artwork, squareArt)
                        setTextViewText(R.id.widget_song_title, displaySong?.title ?: "LofiQ Music")
                        setTextViewText(R.id.widget_song_artist, displaySong?.artist ?: "Tap to play")
                        setImageViewResource(R.id.widget_btn_play_pause, playPauseIcon)
                        setImageViewResource(R.id.widget_btn_prev, R.drawable.ic_widget_prev)
                        setImageViewResource(R.id.widget_btn_next, R.drawable.ic_widget_next)

                        setOnClickPendingIntent(R.id.widget_bg_artwork, launchIntent)
                        setOnClickPendingIntent(R.id.widget_song_title, launchIntent)
                        setOnClickPendingIntent(R.id.widget_btn_play_pause, playPauseIntent)
                        setOnClickPendingIntent(R.id.widget_btn_prev, prevIntent)
                        setOnClickPendingIntent(R.id.widget_btn_next, nextIntent)
                    }
                    runCatching {
                        appWidgetManager.updateAppWidget(id, views)
                    }.onFailure { e ->
                        LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[WIDGET] Failed to update widget $id: ${e.message}")
                    }
                }
            }

            // 2. Update 4x2 Wide Player Widgets (Full-bleed artwork background + bottom track info & controls)
            if (wideIds.isNotEmpty()) {
                for (id in wideIds) {
                    val views = RemoteViews(appContext.packageName, R.layout.widget_wide_4x2).apply {
                        setImageViewBitmap(R.id.widget_bg_artwork, wideArt)
                        setTextViewText(R.id.widget_song_title, displaySong?.title ?: "LofiQ Music")
                        setTextViewText(R.id.widget_song_artist, displaySong?.artist ?: "Tap to play")

                        setImageViewResource(R.id.widget_btn_play_pause, playPauseIcon)
                        setImageViewResource(R.id.widget_btn_prev, R.drawable.ic_widget_prev)
                        setImageViewResource(R.id.widget_btn_next, R.drawable.ic_widget_next)

                        setOnClickPendingIntent(R.id.widget_bg_artwork, launchIntent)
                        setOnClickPendingIntent(R.id.widget_clickable_area, launchIntent)
                        setOnClickPendingIntent(R.id.widget_btn_play_pause, playPauseIntent)
                        setOnClickPendingIntent(R.id.widget_btn_prev, prevIntent)
                        setOnClickPendingIntent(R.id.widget_btn_next, nextIntent)
                    }
                    runCatching {
                        appWidgetManager.updateAppWidget(id, views)
                    }.onFailure { e ->
                        LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[WIDGET] Failed to update widget $id: ${e.message}")
                    }
                }
            }

            // 3. Update 4x1 Minimal Pill Widgets (Pill capsule + circular artwork + title on top + 5 controls below)
            if (pillIds.isNotEmpty()) {
                val shuffleIcon = if (state.isShuffleEnabled) {
                    R.drawable.ic_widget_shuffle_on
                } else {
                    R.drawable.ic_widget_shuffle_off
                }
                val likeIcon = if (isFav) {
                    R.drawable.ic_widget_heart_filled
                } else {
                    R.drawable.ic_widget_heart_outline
                }

                for (id in pillIds) {
                    val views = RemoteViews(appContext.packageName, R.layout.widget_minimal_pill_4x1).apply {
                        setImageViewBitmap(R.id.widget_artwork, pillArt)
                        setTextViewText(R.id.widget_song_title, displaySong?.title ?: "LofiQ Music")

                        setImageViewResource(R.id.widget_btn_play_pause, playPauseIcon)
                        setImageViewResource(R.id.widget_btn_prev, R.drawable.ic_widget_prev)
                        setImageViewResource(R.id.widget_btn_next, R.drawable.ic_widget_next)
                        setImageViewResource(R.id.widget_btn_shuffle, shuffleIcon)
                        setImageViewResource(R.id.widget_btn_like, likeIcon)

                        setOnClickPendingIntent(R.id.widget_artwork, launchIntent)
                        setOnClickPendingIntent(R.id.widget_song_title, launchIntent)
                        setOnClickPendingIntent(R.id.widget_btn_play_pause, playPauseIntent)
                        setOnClickPendingIntent(R.id.widget_btn_prev, prevIntent)
                        setOnClickPendingIntent(R.id.widget_btn_next, nextIntent)
                        setOnClickPendingIntent(R.id.widget_btn_shuffle, shuffleIntent)
                        setOnClickPendingIntent(R.id.widget_btn_like, likeIntent)
                    }
                    runCatching {
                        appWidgetManager.updateAppWidget(id, views)
                    }.onFailure { e ->
                        LofiQLogger.w(LofiQLogger.Category.PLAYBACK, "[WIDGET] Failed to update widget $id: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Resolves and caches three distinct bitmaps tailored for the three widget designs.
     */
    private suspend fun getArtworkBitmaps(context: Context, song: Song?): Triple<Bitmap, Bitmap, Bitmap> {
        val songId = song?.id ?: "empty"
        val squareKey = "${songId}_square"
        val wideKey = "${songId}_wide"
        val pillKey = "${songId}_pill"

        val cachedSquare = bitmapCache.get(squareKey)
        val cachedWide = bitmapCache.get(wideKey)
        val cachedPill = bitmapCache.get(pillKey)

        if (cachedSquare != null && cachedWide != null && cachedPill != null) {
            return Triple(cachedSquare, cachedWide, cachedPill)
        }

        var sourceBitmap: Bitmap? = null
        if (song != null) {
            val optimalUrl = ArtworkUtils.getOptimalArtwork(song)
            if (optimalUrl.isNotBlank()) {
                sourceBitmap = loadSoftwareBitmap(context, optimalUrl)
            }
            if (sourceBitmap == null) {
                val fallbackUrl = ArtworkUtils.getFallbackArtwork(song)
                if (!fallbackUrl.isNullOrBlank()) {
                    sourceBitmap = loadSoftwareBitmap(context, fallbackUrl)
                }
            }
        }

        // 1. Square 2x2: Full-bleed solid center-cropped rectangle (600x600 px)
        val squareBitmap = if (sourceBitmap != null) {
            WidgetBitmapUtils.createCenterCropBitmap(sourceBitmap, 600, 600)
        } else {
            WidgetBitmapUtils.createDefaultArtworkBitmap(context, 600, 600, isCircle = false)
        }
        bitmapCache.put(squareKey, squareBitmap)

        // 2. Wide 4x2: Full-bleed solid center-cropped rectangle (800x400 px)
        val wideBitmap = if (sourceBitmap != null) {
            WidgetBitmapUtils.createCenterCropBitmap(sourceBitmap, 800, 400)
        } else {
            WidgetBitmapUtils.createDefaultArtworkBitmap(context, 800, 400, isCircle = false)
        }
        bitmapCache.put(wideKey, wideBitmap)

        // 3. Pill 4x1: Circular artwork (160x160 px circle)
        val pillBitmap = if (sourceBitmap != null) {
            WidgetBitmapUtils.createCircleBitmap(sourceBitmap, 160)
        } else {
            WidgetBitmapUtils.createDefaultArtworkBitmap(context, 160, 160, isCircle = true)
        }
        bitmapCache.put(pillKey, pillBitmap)

        return Triple(squareBitmap, wideBitmap, pillBitmap)
    }

    /**
     * Loads a software ARGB_8888 bitmap via Coil (allowHardware = false is mandatory for RemoteViews).
     */
    private suspend fun loadSoftwareBitmap(context: Context, url: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val loader = Coil.imageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(600, 600)
                .allowHardware(false)
                .build()
            val result = loader.execute(request)
            if (result is SuccessResult) {
                (result.drawable as? BitmapDrawable)?.bitmap
            } else null
        }.getOrNull()
    }

    private fun getActionPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MusicWidgetActionReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getLaunchAppPendingIntent(context: Context): PendingIntent {
        val intent = (context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java)).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            200,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

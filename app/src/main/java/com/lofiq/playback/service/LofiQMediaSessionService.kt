package com.lofiq.playback.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.*
import com.lofiq.MainActivity
import com.lofiq.R
import com.lofiq.core.common.LofiQLogger
import com.lofiq.playback.engine.PlaybackDiagnosticLogger
import com.lofiq.playback.engine.PlaybackEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class LofiQMediaSessionService : MediaSessionService() {

    @Inject
    lateinit var exoPlayer: ExoPlayer

    @Inject
    lateinit var playbackEngine: PlaybackEngine

    @Inject
    lateinit var listenTogetherManager: com.lofiq.feature.sync.ListenTogetherManager

    @Inject
    lateinit var diagnosticLogger: PlaybackDiagnosticLogger

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private fun createForwardingPlayer(targetPlayer: ExoPlayer): ForwardingPlayer {
        return object : ForwardingPlayer(targetPlayer) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(Player.COMMAND_PLAY_PAUSE)
                    .add(Player.COMMAND_STOP)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_STOP -> true
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun hasNextMediaItem(): Boolean = true
            override fun hasPreviousMediaItem(): Boolean = true

            override fun play() {
                playbackEngine.play()
            }

            override fun pause() {
                playbackEngine.pause()
            }

            override fun stop() {
                playbackEngine.stop()
            }

            override fun seekTo(positionMs: Long) {
                playbackEngine.seekTo(positionMs)
            }

            override fun seekToNext() {
                playbackEngine.next()
            }

            override fun seekToNextMediaItem() {
                playbackEngine.next()
            }

            override fun seekToPrevious() {
                playbackEngine.previous()
            }

            override fun seekToPreviousMediaItem() {
                playbackEngine.previous()
            }
        }
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "lofiq_playback_channel"
    }

    override fun onCreate() {
        super.onCreate()
        LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "LofiQMediaSessionService onCreate")
        listenTogetherManager.attachPlaybackEngine(playbackEngine)
        createNotificationChannel()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setNotificationId(NOTIFICATION_ID)
                .build()
        )

        val activityIntent = (packageManager.getLaunchIntentForPackage(packageName) ?: Intent(this, MainActivity::class.java)).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val forwardingPlayer = createForwardingPlayer(playbackEngine.activePlayer)

        val callback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val availableSessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .build()
                val availablePlayerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(Player.COMMAND_PLAY_PAUSE)
                    .add(Player.COMMAND_STOP)
                    .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                    .build()

                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(availableSessionCommands)
                    .setAvailablePlayerCommands(availablePlayerCommands)
                    .build()
            }

            override fun onPlayerCommandRequest(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                playerCommand: Int
            ): Int {
                when (playerCommand) {
                    Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                        playbackEngine.next()
                        return SessionResult.RESULT_SUCCESS
                    }
                    Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                        playbackEngine.previous()
                        return SessionResult.RESULT_SUCCESS
                    }
                    Player.COMMAND_STOP -> {
                        playbackEngine.stop()
                        return SessionResult.RESULT_SUCCESS
                    }
                }
                return super.onPlayerCommandRequest(session, controller, playerCommand)
            }
        }

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(pendingIntent)
            .setCallback(callback)
            .build()
        addSession(mediaSession!!)

        // Synchronous handover coordination: immediately re-bind MediaSession to promoted player
        // BEFORE the outgoing player is silenced/stopped in performPlayerHandoverLocked.
        playbackEngine.sessionHandoverCallback = { nextActive ->
            val session = mediaSession
            if (session != null) {
                session.player = createForwardingPlayer(nextActive)
                onUpdateNotification(session, startInForegroundRequired = true)
                diagnosticLogger.logServiceLifecycle(
                    "SESSION_PLAYER_PROMOTED_SYNCHRONOUSLY",
                    "slotSessionId=${nextActive.audioSessionId}, isPlaying=${nextActive.isPlaying}"
                )
            }
        }

        serviceScope.launch {
            playbackEngine.activePlayerFlow.drop(1).collect { nextActive ->
                mediaSession?.let { session ->
                    session.player = createForwardingPlayer(nextActive)
                    onUpdateNotification(session, startInForegroundRequired = true)
                }
                LofiQLogger.i(
                    LofiQLogger.Category.PLAYBACK,
                    "[PLAYER_HANDOVER] Updated MediaSession active ForwardingPlayer (sessionId=${nextActive.audioSessionId})"
                )
            }
        }
        runCatching {
            com.lofiq.widget.MusicWidgetManager.updateAllWidgets(this)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        diagnosticLogger.logServiceLifecycle("ON_START_COMMAND", "flags=$flags, startId=$startId")
        mediaSession?.let { session ->
            if (playbackEngine.playerState.value.isPlaying) {
                onUpdateNotification(session, startInForegroundRequired = true)
            }
        }
        return START_STICKY
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val isEnginePlaying = playbackEngine.playerState.value.isPlaying
        val isPlayerPlaying = session.player.playWhenReady &&
            session.player.playbackState != Player.STATE_IDLE &&
            session.player.playbackState != Player.STATE_ENDED
        val mustStayInForeground = startInForegroundRequired || isEnginePlaying || isPlayerPlaying
        diagnosticLogger.logServiceLifecycle(
            "ON_UPDATE_NOTIFICATION",
            "mustStayInForeground=$mustStayInForeground (req=$startInForegroundRequired, enginePlaying=$isEnginePlaying, playerPlaying=$isPlayerPlaying)"
        )
        super.onUpdateNotification(session, mustStayInForeground)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val syncState = listenTogetherManager.syncState.value
        if (syncState is com.lofiq.feature.sync.ListenTogetherState.Connected && !syncState.isHost) {
            LofiQLogger.i(
                LofiQLogger.Category.PLAYBACK,
                "[SERVICE] Task removed during active joiner session -> leaving room & stopping service"
            )
            listenTogetherManager.leaveRoom()
            stopSelf()
            return
        }

        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_desc)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        diagnosticLogger.logServiceLifecycle("ON_DESTROY")
        LofiQLogger.i(LofiQLogger.Category.PLAYBACK, "LofiQMediaSessionService onDestroy")
        playbackEngine.sessionHandoverCallback = null
        serviceScope.cancel()
        val syncState = listenTogetherManager.syncState.value
        if (syncState is com.lofiq.feature.sync.ListenTogetherState.Connected && !syncState.isHost) {
            listenTogetherManager.leaveRoom()
        }
        mediaSession?.let {
            removeSession(it)
            it.release()
        }
        mediaSession = null
        runCatching {
            com.lofiq.widget.MusicWidgetManager.updateAllWidgets(this)
        }
        super.onDestroy()
    }
}

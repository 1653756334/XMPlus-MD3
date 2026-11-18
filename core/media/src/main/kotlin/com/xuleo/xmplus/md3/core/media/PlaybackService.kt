package com.xuleo.xmplus.md3.core.media

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

interface PlaybackOwner {
    val playback: PlaybackController
}

/** Hosts the only ExoPlayer. The service stays foreground while media is playing. */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private val playback get() = (application as PlaybackOwner).playback

    override fun onCreate() {
        super.onCreate()
        player = createPlayer(this)
        val builder = MediaSession.Builder(this, SessionPlayer(player, playback))
        packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
            val openApp = PendingIntent.getActivity(
                this, 0, intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.setSessionActivity(openApp)
        }
        session = builder.build()
        playback.attach(player)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        if (controllerInfo.isTrusted || controllerInfo.packageName == packageName) session else null

    override fun onDestroy() {
        playback.detach(player)
        session.release()
        player.release()
        super.onDestroy()
    }
}

/** A system client may control the selected episode, never install media or play before authorization. */
@UnstableApi
private class SessionPlayer(player: Player, private val playback: PlaybackController) : ForwardingSimpleBasePlayer(player) {
    override fun getState(): State {
        val current = super.getState()
        val commands = Player.Commands.Builder()
        for (index in 0 until current.availableCommands.size()) {
            val command = current.availableCommands[index]
            if (command in SystemCommands) commands.add(command)
        }
        return current.buildUpon().setAvailableCommands(commands.build()).build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) playback.resume() else playback.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        playback.stop()
        return Futures.immediateVoidFuture()
    }

    private companion object {
        val SystemCommands = setOf(
            Player.COMMAND_PLAY_PAUSE, Player.COMMAND_STOP, Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_TIMELINE, Player.COMMAND_GET_METADATA, Player.COMMAND_GET_AUDIO_ATTRIBUTES,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
            Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD,
        )
    }
}

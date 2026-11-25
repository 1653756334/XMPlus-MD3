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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

interface PlaybackOwner {
    val playback: PlaybackController
    val music: MusicPlayback
}

/** Hosts the only ExoPlayer. The service stays foreground while media is playing. */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var sessionPlayer: SessionPlayer
    private lateinit var session: MediaSession
    private val playback get() = (application as PlaybackOwner).playback
    private val music get() = (application as PlaybackOwner).music
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        player = createPlayer(this)
        sessionPlayer = SessionPlayer(player, playback, music)
        val builder = MediaSession.Builder(this, sessionPlayer)
        packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
            val openApp = PendingIntent.getActivity(
                this, 0, intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.setSessionActivity(openApp)
        }
        session = builder.build()
        playback.attach(player)
        music.attach(player)
        // Whether a next episode exists comes from the album, not the player, so the session is told when that changes.
        scope.launch { playback.queue.map { it.hasNext }.distinctUntilChanged().collect { sessionPlayer.refreshCommands() } }
        scope.launch { music.queue.map { it.items.isNotEmpty() }.distinctUntilChanged().collect { sessionPlayer.refreshCommands() } }
        scope.launch { music.state.map { it.song != null }.distinctUntilChanged().collect { sessionPlayer.refreshCommands() } }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        if (controllerInfo.isTrusted || controllerInfo.packageName == packageName) session else null

    override fun onDestroy() {
        scope.cancel()
        music.detach(player)
        playback.detach(player)
        session.release()
        player.release()
        super.onDestroy()
    }
}

/** A system client may control the selected episode, never install media or play before authorization. */
@UnstableApi
private class SessionPlayer(
    player: Player,
    private val playback: PlaybackController,
    private val music: MusicPlayback,
) : ForwardingSimpleBasePlayer(player) {
    /** A song holds the player; starting either side stops the other, so only one has a current item. */
    private val songActive get() = music.state.value.song != null

    override fun getState(): State {
        val current = super.getState()
        val commands = Player.Commands.Builder()
        for (index in 0 until current.availableCommands.size()) {
            val command = current.availableCommands[index]
            if (command in SystemCommands) commands.add(command)
        }
        // The player holds one episode; the album decides what comes before and after it.
        commands.add(Player.COMMAND_SEEK_TO_PREVIOUS)
        commands.add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        if (if (songActive) music.queue.value.items.isNotEmpty() else playback.queue.value.hasNext) {
            commands.add(Player.COMMAND_SEEK_TO_NEXT)
            commands.add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        }
        return current.buildUpon().setAvailableCommands(commands.build()).build()
    }

    /** Asks the session to read the state again. Forwarded player events do not cover a change in the album's queue. */
    fun refreshCommands() = invalidateState()

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        when {
            songActive -> if (playWhenReady) music.resume() else music.pause()
            playWhenReady -> playback.resume()
            else -> playback.pause()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        if (songActive) {
            when (seekCommand) {
                Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> music.next()
                Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ->
                    if (music.state.value.positionMs > RestartThresholdMs) music.seekTo(0) else music.previous()
                else -> return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
            }
            return Futures.immediateVoidFuture()
        }
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> playback.nextEpisode()
            // Like a player's previous key: restart an episode that is under way, go back from its beginning.
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ->
                if (playback.state.value.positionMs > RestartThresholdMs) playback.seekTo(0) else playback.previousEpisode()
            else -> return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        if (songActive) music.pause() else playback.stop()
        return Futures.immediateVoidFuture()
    }

    private companion object {
        const val RestartThresholdMs = 3_000L
        val SystemCommands = setOf(
            Player.COMMAND_PLAY_PAUSE, Player.COMMAND_STOP, Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_TIMELINE, Player.COMMAND_GET_METADATA, Player.COMMAND_GET_AUDIO_ATTRIBUTES,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
            Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD,
        )
    }
}

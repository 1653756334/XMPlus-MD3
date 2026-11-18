package com.xuleo.xmplus.md3.core.media

import android.net.Uri
import androidx.annotation.MainThread
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.xuleo.xmplus.md3.core.account.AccountRepository
import com.xuleo.xmplus.md3.core.account.UsageLedger
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.core.protocol.PlaybackAuthorization
import com.xuleo.xmplus.md3.core.protocol.XmlyPlayback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The single path from episode selection to audible playback. All player access and immediate meter boundaries
 * belong to the main thread. The mutex serializes quota, sources and credential changes, not player callbacks.
 */
@MainThread
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackController(
    private val accounts: AccountRepository,
    private val ledger: UsageLedger,
    private val resolver: XmlyPlayback,
    private val awaitLedger: suspend () -> Unit,
) {
    private data class OpenSource(
        val mediaId: String,
        val authorization: PlaybackAuthorization,
        var refreshes: Int,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ordered = Mutex()
    private val engine = MutableStateFlow<ExoPlayer?>(null)
    private val mutableState = MutableStateFlow(PlaybackState())
    val state = mutableState.asStateFlow()

    private var revision = 0L
    private var command: Job? = null
    private var clock: Job? = null
    private var sampling: Job? = null
    private var opened: OpenSource? = null
    private var admitted = false
    private var wasPlaying = false
    private var accountChanges = 0

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (!isCurrent(player)) return
            recordPlaying(player)
            val request = revision
            val error = player.playerError
            when {
                admitted && error != null -> scope.launch(Dispatchers.Main) {
                    if (request == revision && admitted && isCurrent(player) && player.playerError === error) {
                        handleSourceError(player, error)
                    }
                }
                admitted && player.playbackState == Player.STATE_ENDED -> scope.launch(Dispatchers.Main) {
                    if (request == revision && admitted && isCurrent(player) && player.playbackState == Player.STATE_ENDED) {
                        finishEpisode()
                    }
                }
                else -> publishPlayer(player)
            }
        }
    }

    internal fun attach(player: ExoPlayer) {
        check(engine.value == null)
        player.addListener(listener)
        engine.value = player
        clock = scope.launch {
            var ticks = 0
            while (true) {
                delay(250)
                if (isCurrent(player)) publishPlayer(player)
                if (++ticks % 4 == 0) sample()
            }
        }
    }

    internal fun detach(player: ExoPlayer) {
        if (engine.value !== player) return
        val position = if (isCurrent(player)) player.currentPosition.coerceAtLeast(0) else mutableState.value.positionMs
        val request = supersede()
        player.removeListener(listener)
        engine.value = null
        opened = null
        clock?.cancel()
        val current = mutableState.value
        if (current.phase != PlaybackPhase.Failed && current.selection != null) {
            mutableState.value = current.copy(phase = PlaybackPhase.Paused, wantsToPlay = false, positionMs = position, canSeek = false)
        }
        completeCommand(request) { settleCurrent(keepUnsettled = true) }
    }

    fun play(selection: PlaybackSelection) {
        if (accountChanges > 0) return
        val current = mutableState.value
        if (current.selection?.track?.id == selection.track.id) {
            if (!current.wantsToPlay) resume()
        } else {
            open(selection, position = 0)
        }
    }

    fun toggle() {
        if (mutableState.value.wantsToPlay) pause() else resume()
    }

    fun resume() {
        if (accountChanges > 0) return
        val current = mutableState.value
        val selection = current.selection ?: return
        if (current.wantsToPlay) return
        val player = engine.value
        val source = opened
        if (player == null || source == null || player.playbackState == Player.STATE_IDLE || current.phase == PlaybackPhase.Failed) {
            open(selection, current.positionMs)
            return
        }
        val request = supersede()
        mutableState.value = current.copy(phase = PlaybackPhase.Preparing, wantsToPlay = true, errorCode = null, canSeek = false)
        completeCommand(request) {
            verifyAccount(source.authorization)
            settleCurrent(keepUnsettled = true)
            startMeter(source.authorization)
            currentCoroutineContext().ensureActive()
            if (current.phase == PlaybackPhase.Ended) player.seekTo(0)
            admitted = true
            mutableState.value = mutableState.value.copy(phase = PlaybackPhase.Buffering)
            player.play()
            recordPlaying(player)
            publishPlayer(player)
        }
    }

    fun pause() {
        val current = mutableState.value
        if (current.selection == null || !current.wantsToPlay) return
        val request = supersede()
        mutableState.value = mutableState.value.copy(phase = PlaybackPhase.Paused, wantsToPlay = false)
        completeCommand(request) { settleCurrent() }
    }

    fun seekTo(positionMs: Long) {
        val current = mutableState.value
        if (current.canSeek) engine.value?.seekTo(positionMs.coerceIn(0, current.durationMs))
    }

    fun skipBack() = skipBy(-PlaybackSkipMillis)

    fun skipForward() = skipBy(PlaybackSkipMillis)

    private fun skipBy(deltaMs: Long) {
        val player = engine.value ?: return
        seekTo(player.currentPosition + deltaMs)
    }

    fun stop() {
        val request = supersede()
        clearSource()
        mutableState.value = PlaybackState()
        completeCommand(request) { settleCurrent(keepUnsettled = true) }
    }

    /** Do not hold a repository lock while the old watch is saving to the same repository. */
    suspend fun <T> changeAccount(preserveJournal: Boolean = false, change: suspend () -> T): T =
        withContext(Dispatchers.Main.immediate) {
            accountChanges++
            try {
                supersede()
                clearSource()
                mutableState.value = PlaybackState()
                ordered.withLock {
                    awaitLedger()
                    if (preserveJournal) {
                        withContext(NonCancellable) { ledger.captureForRestore() }
                    } else {
                        settleCurrent(keepUnsettled = true)
                        val identity = accounts.primaryCredentials()?.identity
                        if (identity != null && ledger.hasDeferredUsage(identity)) {
                            withContext(NonCancellable) {
                                try {
                                    ledger.settleBeforeAccountChange(identity)
                                } catch (failure: XmlyException) {
                                    if (failure.code !in AccountSettlementErrors) throw failure
                                }
                            }
                        }
                    }
                    change()
                }
            } finally {
                accountChanges--
            }
        }

    private fun open(selection: PlaybackSelection, position: Long, refreshing: OpenSource? = null) {
        val request = supersede()
        clearSource()
        mutableState.value = PlaybackState(
            selection, PlaybackPhase.Preparing, wantsToPlay = true,
            positionMs = position, durationMs = (selection.track.durationSeconds * 1000).toLong().coerceAtLeast(0),
        )
        completeCommand(request) {
            val player = withTimeoutOrNull(5_000) { engine.filterNotNull().first() }
                ?: throw XmlyException("playback_unavailable")
            awaitLedger()
            settleCurrent(keepUnsettled = true)
            val credentials = accounts.primaryCredentials()
            if (refreshing != null && refreshing.authorization.credentialsVersion != credentials?.cacheIdentity) {
                throw XmlyException("account_mismatch", definitive = true)
            }
            val resolved = resolver.resolve(selection.track.id, credentials, ledger::queryQuota)
            if (resolved.track.id != selection.track.id ||
                (resolved.track.albumId.isNotBlank() && resolved.track.albumId != selection.album.id)
            ) throw XmlyException("id_mismatch", definitive = true)
            verifyAccount(resolved)
            startMeter(resolved)
            currentCoroutineContext().ensureActive()

            val metadata = MediaMetadata.Builder()
                .setTitle(selection.title).setAlbumTitle(selection.subtitle)
                .setArtist(selection.track.artist.ifBlank { selection.album.artist })
                .setArtworkUri(selection.coverUrl?.let(Uri::parse))
                .build()
            val mediaId = request.toString()
            val item = MediaItem.Builder().setMediaId(mediaId)
                .setUri(resolved.url.toString()).setMediaMetadata(metadata).build()
            opened = OpenSource(mediaId, resolved, refreshes = refreshing?.let { it.refreshes + 1 } ?: 0)
            player.setMediaItem(item, position)
            mutableState.value = mutableState.value.copy(phase = PlaybackPhase.Buffering)
            admitted = true
            player.prepare()
            player.play()
            recordPlaying(player)
            publishPlayer(player)
        }
    }

    private suspend fun verifyAccount(source: PlaybackAuthorization) {
        val current = accounts.primaryCredentials()
        if (current?.cacheIdentity != source.credentialsVersion ||
            (current?.identity ?: "anonymous") != source.accountIdentity
        ) throw XmlyException("account_mismatch", definitive = true)
    }

    private suspend fun startMeter(source: PlaybackAuthorization) {
        ledger.startWatch(source.accountIdentity, source.track.id, source.requiresListeningTime)
    }

    private suspend fun settleCurrent(keepUnsettled: Boolean = false) = withContext(NonCancellable) {
        try {
            ledger.stopWatch()
        } catch (failure: XmlyException) {
            if (!keepUnsettled || failure.code !in AccountSettlementErrors) throw failure
            // Old credentials or insufficient balance can prevent settlement, not recovery of the account.
            ledger.captureForRestore()
        }
    }

    /** Stop contributing elapsed time now, not when a pending network request next yields. */
    private fun supersede(): Long {
        revision++
        command?.cancel()
        mute()
        return revision
    }

    private fun mute() {
        admitted = false
        wasPlaying = false
        ledger.recordPlaybackState(playing = false, buffering = false)
        engine.value?.pause()
    }

    private fun clearSource() {
        opened = null
        engine.value?.let { player ->
            player.stop()
            player.clearMediaItems()
        }
    }

    private fun isCurrent(player: Player) = player === engine.value &&
        opened?.let { it.mediaId == player.currentMediaItem?.mediaId } == true

    private fun recordPlaying(player: Player) {
        val playing = admitted && player.isPlaying
        if (playing) opened?.refreshes = 0
        ledger.recordPlaybackState(playing, player.playbackState == Player.STATE_BUFFERING)
        if (wasPlaying && !playing) sample()
        wasPlaying = playing
    }

    private fun sample() {
        if (!admitted || sampling?.isActive == true) return
        val request = revision
        // Do not enter the ledger from inside a Player callback: closing/reopening a player can synchronously emit events.
        sampling = scope.launch(Dispatchers.Main) {
            ordered.withLock {
                if (admitted && request == revision) try {
                    ledger.sampleCurrentState()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (request == revision) fail(request, failure)
                }
            }
        }
    }

    private fun finishEpisode() {
        val request = supersede()
        val current = mutableState.value
        mutableState.value = current.copy(
            phase = PlaybackPhase.Ended, wantsToPlay = false, positionMs = current.durationMs,
        )
        completeCommand(request) { settleCurrent() }
    }

    private fun handleSourceError(player: Player, error: PlaybackException) {
        val source = opened ?: return
        val current = mutableState.value
        val selected = current.selection ?: return
        val position = player.currentPosition.coerceAtLeast(0)
        if (source.refreshes < MaxAuthorizationRefreshes && player.playWhenReady && error.hasExpiredAuthorization()) {
            open(selected, position, refreshing = source)
        } else {
            val request = supersede()
            clearSource()
            mutableState.value = current.copy(
                phase = PlaybackPhase.Failed, wantsToPlay = false, positionMs = position,
                canSeek = false, errorCode = errorCode(error),
            )
            completeCommand(request) { settleCurrent() }
        }
    }

    private fun publishPlayer(player: Player) {
        val current = mutableState.value
        if (current.phase == PlaybackPhase.Preparing || current.phase == PlaybackPhase.Failed) return
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it >= 0 } ?: current.durationMs
        val phase = when {
            player.playbackState == Player.STATE_ENDED -> PlaybackPhase.Ended
            player.isPlaying -> PlaybackPhase.Playing
            player.playWhenReady && player.playbackState == Player.STATE_BUFFERING -> PlaybackPhase.Buffering
            else -> PlaybackPhase.Paused
        }
        val position = player.currentPosition.coerceAtLeast(0).let { if (duration > 0) it.coerceAtMost(duration) else it }
        mutableState.value = current.copy(
            phase = phase, wantsToPlay = admitted && player.playWhenReady && phase != PlaybackPhase.Ended, positionMs = position,
            durationMs = duration, canSeek = duration > 0 && player.isCurrentMediaItemSeekable,
        )
    }

    private fun completeCommand(request: Long, block: suspend () -> Unit) {
        command = scope.launch(Dispatchers.Main) {
            ordered.withLock {
                if (request != revision) return@withLock
                try {
                    block()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (request == revision) fail(request, failure)
                }
            }
        }
    }

    private suspend fun fail(request: Long, failure: Exception) {
        mute()
        clearSource()
        val current = mutableState.value
        if (current.selection != null) {
            mutableState.value = current.copy(
                phase = PlaybackPhase.Failed, wantsToPlay = false, canSeek = false, errorCode = errorCode(failure),
            )
        }
        try {
            withContext(NonCancellable) { ledger.captureForRestore() }
        } catch (storageFailure: Exception) {
            // The ledger retains its in-memory sample when a local write fails. Do not replay a remote charge.
            if (request == revision && mutableState.value.selection != null) {
                mutableState.value = mutableState.value.copy(errorCode = errorCode(storageFailure))
            }
        }
    }

    private fun errorCode(failure: Exception): String =
        generateSequence<Throwable>(failure) { it.cause }.filterIsInstance<XmlyException>().firstOrNull()?.code
            ?: if (failure.hasExpiredAuthorization()) "media_unauthorized" else "playback_unavailable"

    private companion object {
        const val MaxAuthorizationRefreshes = 3
        val AccountSettlementErrors = setOf("login_required", "login_expired", "insufficient_quota")
    }
}

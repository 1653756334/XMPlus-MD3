package com.xuleo.xmplus.md3.core.media

import android.net.Uri
import android.os.SystemClock
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
import com.xuleo.xmplus.md3.core.model.AlbumDetail
import com.xuleo.xmplus.md3.core.model.Credentials
import com.xuleo.xmplus.md3.core.model.Track
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.core.protocol.PlaybackAuthorization
import com.xuleo.xmplus.md3.core.protocol.XmlyAlbum
import com.xuleo.xmplus.md3.core.protocol.XmlyHistory
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
    private val catalogue: XmlyAlbum,
    private val progress: PlaybackProgressStore,
    private val history: XmlyHistory,
    val historyUpload: HistoryUploader,
    private val bookmarks: PlaybackBookmarkStore,
    private val awaitLedger: suspend () -> Unit,
) {
    private class OpenSource(
        val mediaId: String,
        val authorization: PlaybackAuthorization,
        var refreshes: Int,
        var startPositionMs: Long,
    ) {
        var observation: PlaybackProgress? = null
        var checkpoint: PlaybackProgress? = null
        var checkpointAt = SystemClock.elapsedRealtime()
    }

    private data class QueueChoice(
        val queue: AlbumPlaybackQueue,
        val target: Track?,
        val keepLoaded: Boolean = false,
        val positionMs: Long? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ordered = Mutex()
    private val engine = MutableStateFlow<ExoPlayer?>(null)
    private val mutableState = MutableStateFlow(PlaybackState())
    val state = mutableState.asStateFlow()
    private val mutableQueue = MutableStateFlow(PlaybackQueueState())
    val queue = mutableQueue.asStateFlow()
    private val mutableSpeed = MutableStateFlow(1f)
    val speed = mutableSpeed.asStateFlow()
    private val mutableSleep = MutableStateFlow(SleepTimerState())
    val sleepTimer = mutableSleep.asStateFlow()

    private var revision = 0L
    private var command: Job? = null
    private var albumQueue: AlbumPlaybackQueue? = null
    private var queueWork: Job? = null
    private var queueIntent = 0L
    private var clock: Job? = null
    private var sleepJob: Job? = null
    private var sampling: Job? = null
    private var opened: OpenSource? = null
    private var cloudRestore: Job? = null
    private var cloudIntent = 0L
    private var restoreProgressOnRetry = false
    private var admitted = false
    private var wasPlaying = false
    private var accountChanges = 0

    private val listener = object : Player.Listener {
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            val source = opened?.takeIf { it.mediaId == newPosition.mediaItem?.mediaId } ?: return
            historyUpload.resetPositionAnchor()
            if (reason != Player.DISCONTINUITY_REASON_SEEK) return
            // Seeks from system controls can arrive here without going through seekTo().
            cancelCloudRestore()
            if (source.observation == null) source.startPositionMs = newPosition.positionMs
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (!isCurrent(player)) return
            recordPlaying(player, events.contains(Player.EVENT_POSITION_DISCONTINUITY))
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
                if (isCurrent(player)) {
                    if (admitted && player.isPlaying) opened?.let { source ->
                        observeProgress(player, source)
                        checkpointProgress(source)
                        if (cloudRestore == null && player.playbackState == Player.STATE_READY) {
                            val selection = mutableState.value.selection
                            val position = source.observation?.positionMs
                            if (selection != null && position != null) historyUpload.observe(
                                source.authorization, selection.album.id, selection.track.historyDirection, position,
                            )
                        }
                    }
                    publishPlayer(player)
                }
                if (++ticks % 4 == 0) sample()
            }
        }
    }

    internal fun detach(player: ExoPlayer) {
        if (engine.value !== player) return
        cancelQueueWork()
        val position = if (isCurrent(player)) player.currentPosition.coerceAtLeast(0) else mutableState.value.positionMs
        val request = supersede()
        player.removeListener(listener)
        engine.value = null
        opened = null
        clock?.cancel()
        val current = mutableState.value
        if (current.phase != PlaybackPhase.Failed && current.selection != null) {
            val phase = if (current.phase == PlaybackPhase.Ended) PlaybackPhase.Ended else PlaybackPhase.Paused
            mutableState.value = current.copy(phase = phase, wantsToPlay = false, positionMs = position, canSeek = false)
        }
        completeCommand(request) { settleCurrent(keepUnsettled = true) }
    }

    fun playAlbum(detail: AlbumDetail, track: Track) {
        if (accountChanges > 0) return
        val current = mutableState.value
        val loaded = current.selection?.takeIf { it.track.id == track.id && it.album.id == detail.album.id } != null
        if (!loaded) cancelCloudRestore()
        // With a failed queue, tapping the loaded episode rebuilds the queue and keeps it playing.
        if (loaded && mutableQueue.value.errorCode == null) {
            cancelQueueWork()
            if (!current.wantsToPlay) resume()
            return
        }
        // Resolve the official page before replacing the queue or stopping current audio.
        readQueue { credentials ->
            val hint = if (track.officialPosition == null && track.orderNo == null) {
                resolver.trackMetadata(track.id, credentials)
            } else track
            val page = AlbumPlaybackQueue.pageFor(hint)
            val result = readAlbumPage(detail, page, credentials)
            val queue = AlbumPlaybackQueue.start(detail, hint, page, result)
            QueueChoice(queue, queue.items.first { it.id == track.id }, keepLoaded = loaded)
        }
    }

    fun playQueued(trackId: String) {
        if (accountChanges > 0) return
        val snapshot = albumQueue ?: return
        val target = snapshot.items.firstOrNull { it.id == trackId } ?: return
        cancelQueueWork()
        if (mutableState.value.selection?.track?.id == trackId) {
            if (!mutableState.value.wantsToPlay) resume()
        } else {
            applyQueueChoice(QueueChoice(snapshot, target))
        }
    }

    fun previousEpisode() = moveEpisode(forward = false)

    fun nextEpisode() = moveEpisode(forward = true)

    private fun moveEpisode(forward: Boolean) {
        if (accountChanges > 0 || queueWork != null) return
        val snapshot = albumQueue ?: return
        val current = mutableState.value.selection?.track ?: return
        cancelCloudRestore()
        snapshot.neighbor(current.id, forward)?.let { next ->
            cancelQueueWork()
            applyQueueChoice(QueueChoice(snapshot, next))
            return
        }
        if (!forward && current.officialPosition == 1) {
            cancelQueueWork()
            applyQueueChoice(QueueChoice(snapshot, current, positionMs = 0))
            return
        }
        readQueue { credentials ->
            var window = snapshot.recheckEdge(forward)
            if (forward && window.refreshTailFirst) {
                val tail = window.lastPage
                window = window.merge(tail, readAlbumPage(window.detail, tail, credentials))
            } else if (!forward) {
                val head = AlbumPlaybackQueue.pageFor(window.items.first())
                window = window.refreshHead(readAlbumPage(window.detail, head, credentials))
            }
            var target = window.neighbor(current.id, forward)
            while (target == null && if (forward) window.hasLaterPage else window.hasEarlierPage) {
                val page = if (forward) window.lastPage + 1 else window.firstPage - 1
                window = window.merge(page, readAlbumPage(window.detail, page, credentials))
                target = window.neighbor(current.id, forward)
            }
            if (!forward && target == null) QueueChoice(window, current, positionMs = 0) else QueueChoice(window, target)
        }
    }

    private suspend fun readAlbumPage(detail: AlbumDetail, page: Int, credentials: Credentials?) =
        catalogue.episodePage(detail.album.id, page, ascending = true, recordDescending = detail.recordDescending, credentials = credentials)

    private fun readQueue(read: suspend (Credentials?) -> QueueChoice) {
        cancelQueueWork()
        val intent = queueIntent
        mutableQueue.value = mutableQueue.value.copy(isLoading = true)
        queueWork = scope.launch(Dispatchers.Main) {
            try {
                awaitLedger()
                val credentials = accounts.primaryCredentials()
                val choice = read(credentials)
                val currentCredentials = accounts.primaryCredentials()
                currentCoroutineContext().ensureActive()
                if (intent != queueIntent) return@launch
                if (credentials?.cacheIdentity != currentCredentials?.cacheIdentity) throw XmlyException("account_mismatch")
                queueWork = null
                applyQueueChoice(choice)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (intent == queueIntent) {
                    queueWork = null
                    mutableQueue.value = mutableQueue.value.copy(isLoading = false, errorCode = errorCode(failure))
                }
            } finally {
                if (intent == queueIntent && queueWork != null) {
                    queueWork = null
                    mutableQueue.value = mutableQueue.value.copy(isLoading = false)
                }
            }
        }
    }

    private fun cancelQueueWork() {
        queueIntent++
        queueWork?.cancel()
        queueWork = null
        val state = mutableQueue.value
        if (state.isLoading || state.errorCode != null) mutableQueue.value = state.copy(isLoading = false, errorCode = null)
    }

    private fun clearQueue() {
        cancelQueueWork()
        albumQueue = null
        mutableQueue.value = PlaybackQueueState()
    }

    private fun applyQueueChoice(choice: QueueChoice) {
        albumQueue = choice.queue
        val target = choice.target
        val currentId = target?.id ?: mutableState.value.selection?.track?.id
        mutableQueue.value = PlaybackQueueState(
            items = choice.queue.items, currentTrackId = currentId,
            hasNext = currentId != null && choice.queue.hasNext(currentId),
        )
        if (target == null) return
        val album = choice.queue.detail.album
        val loaded = mutableState.value.selection?.takeIf { it.track.id == target.id && it.album.id == album.id }
        if (choice.keepLoaded && loaded != null) {
            if (!mutableState.value.wantsToPlay) resume()
        } else {
            open(PlaybackSelection(target, album), position = choice.positionMs)
        }
    }

    /** Speed is kept for this session. Listening time before a change is billed at the old speed. */
    fun setSpeed(value: Float) {
        if (!value.isFinite() || value < PlaybackMinSpeed || value > PlaybackMaxSpeed) return
        mutableSpeed.value = value
        engine.value?.setPlaybackSpeed(value)
        if (!admitted) return
        val request = revision
        scope.launch(Dispatchers.Main) {
            ordered.withLock {
                if (admitted && request == revision) try {
                    ledger.sampleCurrentState(speed = value.toDouble())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (request == revision) fail(request, failure)
                }
            }
        }
    }
    fun toggle() {
        val current = mutableState.value
        if (current.wantsToPlay || queueWork != null) pause() else resume()
    }

    fun resume() = resumeAt()

    private fun resumeAt(explicitPosition: Long? = null) {
        if (accountChanges > 0) return
        val current = mutableState.value
        val selection = current.selection ?: return
        if (current.wantsToPlay) return
        cancelQueueWork()
        val player = engine.value
        val source = opened
        if (player == null || source == null || player.playbackState == Player.STATE_IDLE || current.phase == PlaybackPhase.Failed) {
            val position = explicitPosition ?: when {
                current.phase == PlaybackPhase.Ended -> 0L
                restoreProgressOnRetry -> null
                else -> current.positionMs
            }
            open(selection, position)
            return
        }
        val request = supersede()
        mutableState.value = current.copy(phase = PlaybackPhase.Preparing, wantsToPlay = true, errorCode = null, canSeek = false)
        completeCommand(request) {
            verifyAccount(source.authorization)
            settleCurrent(keepUnsettled = true)
            startMeter(source.authorization)
            currentCoroutineContext().ensureActive()
            val restartAt = explicitPosition ?: if (current.phase == PlaybackPhase.Ended) 0L else null
            if (restartAt != null) {
                source.observation = null
                source.startPositionMs = restartAt
                player.seekTo(restartAt)
            }
            admitted = true
            mutableState.value = mutableState.value.copy(phase = PlaybackPhase.Buffering)
            player.play()
            recordPlaying(player)
            publishPlayer(player)
        }
    }

    fun pause() {
        cancelQueueWork()
        val current = mutableState.value
        if (current.selection == null || (!current.wantsToPlay && !admitted)) return
        val request = supersede()
        mutableState.value = mutableState.value.copy(phase = PlaybackPhase.Paused, wantsToPlay = false)
        completeCommand(request) { settleCurrent() }
    }

    fun seekTo(positionMs: Long) {
        val current = mutableState.value
        if (current.canSeek) {
            cancelCloudRestore()
            if (current.phase == PlaybackPhase.Ended) cancelQueueWork()
            engine.value?.seekTo(positionMs.coerceIn(0, current.durationMs))
        }
    }

    fun bookmarkEpisode(): BookmarkEpisode? {
        if (accountChanges > 0 || engine.value?.let(::isCurrent) != true) return null
        val source = opened ?: return null
        val selection = mutableState.value.selection ?: return null
        return BookmarkEpisode(source.authorization.accountIdentity, selection.album.id, selection.track.id)
    }

    /** Capture the player clock now; the time spent typing a note does not move this point. */
    fun captureBookmark(episode: BookmarkEpisode): BookmarkDraft? {
        val position = bookmarkPosition(episode) ?: return null
        return BookmarkDraft(episode, position, System.currentTimeMillis())
    }

    suspend fun listBookmarks(episode: BookmarkEpisode): List<PlaybackBookmark> {
        checkBookmarkEpisode(episode)
        return bookmarks.list(episode)
    }

    suspend fun addBookmark(draft: BookmarkDraft, note: String): PlaybackBookmark {
        checkBookmarkEpisode(draft.episode)
        return bookmarks.add(draft, note)
    }

    suspend fun editBookmark(bookmark: PlaybackBookmark, note: String): PlaybackBookmark {
        checkBookmarkEpisode(bookmark.episode)
        return bookmarks.edit(bookmark, note)
    }

    suspend fun deleteBookmark(bookmark: PlaybackBookmark) {
        checkBookmarkEpisode(bookmark.episode)
        bookmarks.delete(bookmark)
    }

    fun playBookmark(bookmark: PlaybackBookmark): Boolean {
        if (bookmarkPosition(bookmark.episode) == null) return false
        val current = mutableState.value
        val selection = current.selection ?: return false
        val target = bookmark.positionMs.coerceIn(0, current.durationMs)
        // A bookmark is a manual position. Neither a pending next episode nor cloud resume may override it.
        cancelQueueWork()
        when {
            // An in-place seek clamps the exact end to the last frame. Reopen with the saved start so that
            // this is not mistaken for a natural finish (which would advance the queue or consume the timer).
            target == current.durationMs -> open(selection, target, requiredAccount = bookmark.episode.account)
            current.wantsToPlay -> seekTo(target)
            else -> resumeAt(target)
        }
        return true
    }

    private fun bookmarkPosition(episode: BookmarkEpisode): Long? {
        val current = mutableState.value
        if (!current.canSeek || bookmarkEpisode() != episode) return null
        return engine.value?.currentPosition?.coerceIn(0, current.durationMs)
    }

    private fun checkBookmarkEpisode(episode: BookmarkEpisode) {
        if (bookmarkEpisode() != episode) throw XmlyException("stale_bookmark")
    }

    fun skipBack() = skipBy(-PlaybackSkipMillis)

    fun skipForward() = skipBy(PlaybackSkipMillis)

    private fun skipBy(deltaMs: Long) {
        val player = engine.value ?: return
        seekTo(player.currentPosition + deltaMs)
    }

    /** Pause after [minutes] of wall-clock time, whether or not the audio is running. */
    fun startSleepTimer(minutes: Int) {
        if (mutableState.value.selection == null) return
        cancelSleepTimer()
        val deadline = SystemClock.elapsedRealtime() + minutes * 60_000L
        sleepJob = scope.launch {
            while (true) {
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0) break
                mutableSleep.value = SleepTimerState(remainingMs = (remaining + 999) / 1000 * 1000)
                delay(minOf(remaining, 1_000L))
            }
            sleepJob = null
            mutableSleep.value = SleepTimerState()
            pause()
        }
    }

    /** Pause when the current episode ends, in place of moving to the next one. */
    fun sleepAtEpisodeEnd() {
        if (mutableState.value.selection == null) return
        cancelSleepTimer()
        mutableSleep.value = SleepTimerState(atEpisodeEnd = true)
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        if (mutableSleep.value.active) mutableSleep.value = SleepTimerState()
    }

    fun stop() {
        clearQueue()
        cancelSleepTimer()
        val request = supersede()
        clearSource()
        mutableState.value = PlaybackState()
        completeCommand(request) { settleCurrent(keepUnsettled = true) }
    }

    /** Do not hold a repository lock while the old watch is saving to the same repository. */
    suspend fun <T> changeAccount(preserveJournal: Boolean = false, change: suspend () -> T): T =
        withContext(Dispatchers.Main.immediate) {
            accountChanges++
            historyUpload.beginAccountChange()
            try {
                clearQueue()
                cancelSleepTimer()
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
                historyUpload.endAccountChange()
                accountChanges--
            }
        }

    private fun open(
        selection: PlaybackSelection,
        position: Long?,
        refreshing: OpenSource? = null,
        requiredAccount: String? = null,
    ) {
        val request = supersede()
        val restoreIntent = cloudIntent
        restoreProgressOnRetry = position == null
        clearSource()
        mutableState.value = PlaybackState(
            selection, PlaybackPhase.Preparing, wantsToPlay = true,
            positionMs = position ?: 0, durationMs = (selection.track.durationSeconds * 1000).toLong().coerceAtLeast(0),
        )
        completeCommand(request) {
            val player = withTimeoutOrNull(5_000) { engine.filterNotNull().first() }
                ?: throw XmlyException("playback_unavailable")
            awaitLedger()
            settleCurrent(keepUnsettled = true)
            val credentials = accounts.primaryCredentials()
            if (requiredAccount != null && requiredAccount != (credentials?.identity ?: "anonymous")) {
                throw XmlyException("account_mismatch", definitive = true)
            }
            if (refreshing != null && refreshing.authorization.credentialsVersion != credentials?.cacheIdentity) {
                throw XmlyException("account_mismatch", definitive = true)
            }
            val resolved = resolver.resolve(selection.track.id, credentials, ledger::queryQuota)
            if (resolved.track.id != selection.track.id ||
                (resolved.track.albumId.isNotBlank() && resolved.track.albumId != selection.album.id)
            ) throw XmlyException("id_mismatch", definitive = true)
            val duration = mutableState.value.durationMs
            val local = if (position == null) progress.latest(resolved.accountIdentity, resolved.track.id) else null
            val start = position ?: local?.positionMs?.let {
                if (duration > 0) it.coerceAtMost(duration) else it
            } ?: 0L
            verifyAccount(resolved)
            currentCoroutineContext().ensureActive()
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
            val source = OpenSource(mediaId, resolved, refreshes = refreshing?.let { it.refreshes + 1 } ?: 0, startPositionMs = start)
            opened = source
            player.setMediaItem(item, start)
            player.setPlaybackSpeed(mutableSpeed.value)
            restoreProgressOnRetry = false
            mutableState.value = mutableState.value.copy(phase = PlaybackPhase.Buffering, positionMs = start)
            admitted = true
            player.prepare()
            player.play()
            recordPlaying(player)
            publishPlayer(player)
            if (position == null && credentials != null && cloudIntent == restoreIntent) {
                restoreCloudProgress(player, source, credentials, selection.album.id, local)
            }
        }
    }

    private fun restoreCloudProgress(
        player: ExoPlayer,
        source: OpenSource,
        credentials: Credentials,
        albumId: String,
        local: PlaybackProgress?,
    ) {
        // The local source is already admitted. History never holds the playback or settlement lock.
        val request = revision
        cloudRestore = scope.launch(Dispatchers.Main) {
            val task = currentCoroutineContext()[Job]
            fun active() = cloudRestore === task && request == revision && opened === source && isCurrent(player) &&
                admitted && player.playbackState != Player.STATE_ENDED && player.playerError == null
            try {
                if (!active()) return@launch
                val seekDeadline = SystemClock.elapsedRealtime() + CloudStartupWindowMs
                val cloud = withContext(Dispatchers.Default) { history.progress(credentials, albumId, source.authorization.track.id) }
                    ?: return@launch
                if (local != null && cloud.observedAtMs <= local.observedAtMs) return@launch

                // Give a fast result time to meet the timeline, without starting another wait after a slow query.
                if (!state.value.canSeek) {
                    val seekable = withTimeoutOrNull(seekDeadline - SystemClock.elapsedRealtime()) {
                        state.first { it.canSeek || it.phase == PlaybackPhase.Ended || it.phase == PlaybackPhase.Failed }.canSeek
                    }
                    if (seekable != true) return@launch
                }
                if (!active()) return@launch
                verifyAccount(source.authorization)
                currentCoroutineContext().ensureActive()
                if (!active() || !player.isCurrentMediaItemSeekable) return@launch
                val duration = player.duration.takeIf { it > 0 } ?: mutableState.value.durationMs
                if (duration <= 0) return@launch
                // Floor of 95% in milliseconds, without overflowing or rounding a position at exactly 95%.
                val threshold = duration - (duration / 20 + if (duration % 20 == 0L) 0 else 1)
                val target = if (cloud.positionMs > threshold) 0L else cloud.positionMs
                if (player.currentPosition == target) return@launch

                // Only playback observed after the seek may replace the saved progress.
                cloudRestore = null
                source.observation = null
                source.startPositionMs = target
                player.seekTo(target)
                publishPlayer(player)
            } catch (_: XmlyException) {
                // History is best effort. Keep the already running local position on error.
            } finally {
                if (cloudRestore === task) {
                    cloudRestore = null
                    if (opened === source && isCurrent(player) && !player.isPlaying) checkpointProgress(source, force = true)
                }
            }
        }
    }

    private fun cancelCloudRestore() {
        // New intents can supersede a restore even while its local source is still preparing.
        cloudIntent++
        val pending = cloudRestore ?: return
        cloudRestore = null
        pending.cancel()
    }

    private suspend fun verifyAccount(source: PlaybackAuthorization) {
        val current = accounts.primaryCredentials()
        if (current?.cacheIdentity != source.credentialsVersion ||
            (current?.identity ?: "anonymous") != source.accountIdentity
        ) throw XmlyException("account_mismatch", definitive = true)
    }

    private suspend fun startMeter(source: PlaybackAuthorization) {
        ledger.startWatch(source.accountIdentity, source.track.id, source.requiresListeningTime, speed = mutableSpeed.value.toDouble())
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
        cancelCloudRestore()
        historyUpload.finishPlayback()
        engine.value?.takeIf(::isCurrent)?.let { player ->
            opened?.takeIf { it.observation != null }?.let { source ->
                if (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_ENDED) {
                    observeProgress(player, source)
                }
                checkpointProgress(source, force = true)
            }
        }
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

    private fun recordPlaying(player: Player, positionChanged: Boolean = false) {
        val playing = admitted && player.isPlaying
        ledger.recordPlaybackState(playing, player.playbackState == Player.STATE_BUFFERING)
        if (wasPlaying && !playing) sample()
        opened?.let { source ->
            if (playing) source.refreshes = 0
            // Opening or seeking a source that never played must not overwrite a saved position.
            if (playing || (source.observation != null && (wasPlaying || positionChanged || player.playbackState == Player.STATE_ENDED))) {
                observeProgress(player, source)
                checkpointProgress(source, force = !playing)
            }
        }
        wasPlaying = playing
    }

    private fun observeProgress(player: Player, source: OpenSource) {
        if (player.playerError != null || player.playbackState == Player.STATE_IDLE) return
        val position = player.currentPosition.takeIf { it >= 0 } ?: return
        val duration = player.duration
        val bounded = if (duration > 0) position.coerceAtMost(duration) else position
        // Media3 can clamp a start at the exact end to just before it and briefly report isPlaying.
        if (source.observation == null && duration > 0 && maxOf(bounded, source.startPositionMs) >= duration) return
        if (source.observation?.positionMs != bounded) {
            source.observation = PlaybackProgress(bounded, System.currentTimeMillis())
        }
    }

    private fun checkpointProgress(source: OpenSource, force: Boolean = false) {
        if (source === opened && cloudRestore != null) return
        val observation = source.observation ?: return
        if (source.checkpoint == observation) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - source.checkpointAt < ProgressIntervalMs) return
        source.checkpoint = observation
        source.checkpointAt = now
        // Capture the authorizing identity before changing the selected source. Writes outlive a cancelled command.
        scope.launch {
            val saved = progress.save(source.authorization.accountIdentity, source.authorization.track.id, observation)
            if (!saved && source.checkpoint == observation) source.checkpoint = null
        }
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
        val advanceIntent = queueIntent
        // Restoring a position at the exact end can report ENDED before any audio plays.
        val played = opened?.observation != null
        val sleepAtEnd = played && mutableSleep.value.atEpisodeEnd
        if (sleepAtEnd) cancelSleepTimer()
        val shouldAdvance = played && queueWork == null && !sleepAtEnd
        val request = supersede()
        val current = mutableState.value
        mutableState.value = current.copy(
            phase = PlaybackPhase.Ended, wantsToPlay = false, positionMs = current.durationMs,
        )
        completeCommand(request) {
            settleCurrent(keepUnsettled = true)
            if (shouldAdvance) scope.launch(Dispatchers.Main) {
                if (revision == request && queueIntent == advanceIntent) moveEpisode(forward = true)
            }
        }
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
        const val ProgressIntervalMs = 5_000L
        const val CloudStartupWindowMs = 2_000L
        val AccountSettlementErrors = setOf("login_required", "login_expired", "insufficient_quota")
    }
}

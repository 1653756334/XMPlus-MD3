package com.xuleo.xmplus.md3.core.media

import android.content.Context
import android.os.SystemClock
import androidx.annotation.MainThread
import com.xuleo.xmplus.md3.core.account.AccountRepository
import com.xuleo.xmplus.md3.core.model.Credentials
import com.xuleo.xmplus.md3.core.protocol.PlaybackAuthorization
import com.xuleo.xmplus.md3.core.protocol.XmlyHistory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class HistoryUploadFeedback { Uploaded, Failed, SignIn }

data class HistoryUploadState(
    val ready: Boolean = false,
    val enabled: Boolean = false,
    val saving: Boolean = false,
    val consentError: Boolean = false,
    val sending: Boolean = false,
    val feedback: HistoryUploadFeedback? = null,
)

/** Independent of playback admission and of the listening-time ledger. All gates live on Main. */
@MainThread
class HistoryUploader internal constructor(
    context: Context,
    private val accounts: AccountRepository,
    private val history: XmlyHistory,
    private val clock: () -> Long,
    private val elapsed: () -> Long,
) {
    constructor(context: Context, accounts: AccountRepository, history: XmlyHistory) :
        this(context, accounts, history, System::currentTimeMillis, SystemClock::elapsedRealtime)

    private class Session(val source: PlaybackAuthorization, val album: String, val direction: Int, val started: Long) {
        var lastPosition: Long? = null
        var observation: HistoryObservation? = null
        var lastQueued: HistoryObservation? = null
        var queuedAt: Long? = null
    }

    private data class Request(val version: String?, val immediate: Boolean, val manual: Boolean = false)

    private val store = HistoryUploadStore(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(HistoryUploadState())
    val state = mutableState.asStateFlow()

    private var consentGeneration = 0L
    private var accountGeneration = 0L
    private var accountChanges = 0
    private var failedConsent: Boolean? = null
    private var session: Session? = null
    private var sending: Job? = null
    private var queuedRequest: Request? = null
    private var nextRegularAt = 0L

    init { loadConsent() }

    private fun loadConsent() {
        mutableState.value = mutableState.value.copy(saving = true, consentError = false)
        scope.launch {
            try {
                val enabled = store.readConsent()
                mutableState.value = mutableState.value.copy(ready = true, enabled = enabled, saving = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(saving = false, consentError = true)
            }
        }
    }

    /** The UI calls this only after confirmation when enabling; revocation takes effect before touching disk. */
    fun setEnabled(enabled: Boolean) {
        val current = mutableState.value
        if (!current.ready || current.saving || (current.enabled == enabled && !current.consentError)) return
        consentGeneration++
        session = null
        cancelSending()
        mutableState.value = mutableState.value.copy(enabled = false, saving = true, consentError = false, feedback = null)
        scope.launch {
            try {
                store.saveConsent(enabled)
                failedConsent = null
                mutableState.value = mutableState.value.copy(enabled = enabled, saving = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failedConsent = enabled
                // Stay off in this process if either enabling or revoking could not be persisted.
                mutableState.value = mutableState.value.copy(saving = false, consentError = true)
            }
        }
    }

    fun retryConsent() {
        val current = mutableState.value
        if (current.saving || !current.consentError) return
        if (!current.ready) loadConsent() else failedConsent?.let(::setEnabled)
    }

    fun retry() = schedule(Request(version = null, immediate = true, manual = true))

    internal fun beginAccountChange() {
        accountChanges++
        accountGeneration++
        cancelSending()
        mutableState.value = mutableState.value.copy(feedback = null)
    }

    internal fun endAccountChange() { accountChanges-- }

    internal fun resetPositionAnchor() {
        session?.lastPosition = null
    }

    /** Called only for ready audible playback. Seek notifications and paused ticks never call this. */
    internal fun observe(source: PlaybackAuthorization, album: String, direction: Int?, positionMs: Long) {
        if (!canSend() || source.credentialsVersion == null || direction == null) return
        val now = clock()
        val current = session?.takeIf { it.source === source } ?: Session(source, album, direction, now).also { session = it }
        val previous = current.lastPosition
        current.lastPosition = positionMs
        // A changed anchor is not listening: require actual forward movement after it.
        if (previous == null || positionMs <= previous || now <= current.started || now <= (current.observation?.endedAtMs ?: 0L)) return
        current.observation = HistoryObservation(source.accountIdentity, album, source.track.id, positionMs, current.started, now, direction)
        if (current.queuedAt?.let { elapsed() - it < SaveIntervalMs } != true) persist(current, immediate = false)
    }

    internal fun finishPlayback() {
        val current = session ?: return
        session = null
        persist(current, immediate = true)
    }

    private fun persist(current: Session, immediate: Boolean) {
        val observation = current.observation ?: return
        if (!mutableState.value.enabled) return
        val version = current.source.credentialsVersion ?: return
        val request = Request(version, immediate)
        if (current.lastQueued == observation) {
            if (immediate) schedule(request)
            return
        }
        current.lastQueued = observation
        current.queuedAt = elapsed()
        val consent = consentGeneration
        val owner = accountGeneration
        scope.launch {
            if (consent != consentGeneration) return@launch
            try {
                store.record(observation)
                if (consent == consentGeneration && owner == accountGeneration) schedule(request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (current.lastQueued == observation) current.lastQueued = null
                if (consent == consentGeneration && owner == accountGeneration && canSend()) {
                    mutableState.value = mutableState.value.copy(feedback = HistoryUploadFeedback.Failed)
                }
            }
        }
    }

    private fun canSend() = mutableState.value.enabled && accountChanges == 0

    private fun cancelSending() {
        val worker = sending
        sending = null
        queuedRequest = null
        nextRegularAt = 0
        worker?.cancel()
        mutableState.value = mutableState.value.copy(sending = false)
    }

    private fun schedule(request: Request) {
        if (!canSend() || (!request.immediate && elapsed() < nextRegularAt)) return
        if (sending != null) {
            if (queuedRequest?.manual != true) queuedRequest = request
            return
        }
        val consent = consentGeneration
        val account = accountGeneration
        fun active() = canSend() && consent == consentGeneration && account == accountGeneration
        mutableState.value = mutableState.value.copy(sending = true, feedback = if (request.manual) null else mutableState.value.feedback)
        sending = scope.launch(Dispatchers.Main) {
            val task = currentCoroutineContext()[Job]
            try {
                val credentials = accounts.primaryCredentials()
                if (!active()) return@launch
                if (credentials == null) {
                    if (request.manual) mutableState.value = mutableState.value.copy(feedback = HistoryUploadFeedback.SignIn)
                    return@launch
                }
                if (request.version != null && request.version != credentials.cacheIdentity) return@launch
                nextRegularAt = elapsed() + UploadIntervalMs
                val failed = flush(credentials, request.manual, ::active)
                if (!active()) return@launch
                val feedback = when {
                    failed -> HistoryUploadFeedback.Failed
                    request.manual -> HistoryUploadFeedback.Uploaded
                    mutableState.value.feedback == HistoryUploadFeedback.Failed && store.pending(credentials.identity).isEmpty() -> null
                    else -> mutableState.value.feedback
                }
                if (active()) mutableState.value = mutableState.value.copy(feedback = feedback)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (active()) mutableState.value = mutableState.value.copy(feedback = HistoryUploadFeedback.Failed)
            } finally {
                if (sending === task) {
                    sending = null
                    val next = queuedRequest
                    queuedRequest = null
                    mutableState.value = mutableState.value.copy(sending = false)
                    if (next != null && active()) schedule(next)
                }
            }
        }
    }

    private suspend fun flush(credentials: Credentials, manual: Boolean, active: () -> Boolean): Boolean {
        suspend fun stillAuthorized(): Boolean = active() && accounts.primaryCredentials()?.cacheIdentity == credentials.cacheIdentity && active()

        var failed = false
        for (entry in store.pending(credentials.identity)) {
            if (!stillAuthorized()) break
            if (entry.attempted && !manual) continue
            try {
                if (!store.markAttempted(entry.id, manual)) continue
                if (!stillAuthorized()) break
                val observation = entry.observation
                val newest = withContext(Dispatchers.Default) { history.newestInAlbum(credentials, observation.albumId) }
                if (!stillAuthorized()) break
                if (newest == null || newest <= observation.endedAtMs) {
                    currentCoroutineContext().ensureActive()
                    history.upload(
                        credentials, observation.albumId, observation.trackId, observation.positionMs,
                        observation.startedAtMs, observation.endedAtMs, observation.direction,
                    )
                }
                // Acknowledging one row can never erase a newer observation for the same album.
                store.acknowledge(entry.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
                // Leave the exact snapshot attempted. Background playback must not replay it.
            }
        }
        return failed
    }

    private companion object {
        const val SaveIntervalMs = 5_000L
        const val UploadIntervalMs = 30_000L
    }
}

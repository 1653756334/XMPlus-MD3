package com.xuleo.xmplus.md3.core.account

import com.xuleo.xmplus.md3.core.model.Credentials
import com.xuleo.xmplus.md3.core.model.ExternalRetry
import com.xuleo.xmplus.md3.core.model.Quota
import com.xuleo.xmplus.md3.core.model.XmlyException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Where the ledger keeps each account's [UsageState], and the login it needs to talk to the server. */
interface UsageStore {
    val identities: List<String>
    /** Null when there is no such account. */
    fun usage(identity: String): UsageState?
    fun credentials(identity: String): Credentials?
    suspend fun updateUsage(identity: String, update: (UsageState) -> UsageState)
}

/** The two server calls billing needs. */
interface UsageService {
    /** A strict read for settlement: it never waits out a rate limit and gives up quickly. */
    suspend fun quota(credentials: Credentials): Quota

    /** One report request. It is never repeated; [newDevice] is the last known flag. */
    suspend fun debit(credentials: Credentials, seconds: Int, newDevice: Boolean): Quota
}

/**
 * Port of XMPlus usage_ledger.dart: every second of listening time is charged exactly once, and none is lost.
 *
 * - The server has no idempotency key, so a debit is journaled durably *before* it is sent, and an entry that may have
 *   been sent is never sent again: its outcome is settled by reading the balance.
 * - Playback is metered locally into a fractional debt and reported in whole seconds. Seeking cannot create debt: the
 *   meter never sees a position, only playing/buffering events and the speed.
 * - One lock per account covers online debt and download prepayments; the balance lock (shared with reward claims) is
 *   taken only around a quote-journal-report-result sequence.
 *
 * Lock order is watch → account → balance → the account store's own lock.
 */
class UsageLedger(
    private val store: UsageStore,
    private val service: UsageService,
    private val locks: BalanceLocks,
    private val monotonicNanos: () -> Long = System::nanoTime,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private class Listening(val identity: String, val trackId: String, var speed: Double, var sampledAt: Long) {
        var settledAt = sampledAt
        var playing = false
        var suspended = false
        var unsaved = 0.0
    }

    private val accountLocks = ConcurrentHashMap<String, Mutex>()
    private val retryAt = ConcurrentHashMap<String, Long>()
    private val quotas = ConcurrentHashMap<String, Quota>()
    private val watchChanges = Mutex()
    @Volatile private var watch: Listening? = null

    /** True when metering stopped on an error and only a fresh [startWatch] can resume it. */
    val needsRestart: Boolean get() = watch?.let { synchronized(it) { it.suspended } } ?: false

    // region reads

    fun receipts(identity: String): List<DebitEntry> = state(identity).receipts
    fun isBlocked(identity: String): Boolean = state(identity).blocked
    fun debt(identity: String): Double = state(identity).debtSeconds

    /** Usage that could not be settled yet, so a caller may want to try again later. */
    fun hasDeferredUsage(identity: String): Boolean =
        retryAt.containsKey(identity) || isBlocked(identity) || (watch?.identity != identity && debt(identity) >= 1)

    // endregion

    /** Local recovery only: an entry caught mid-send by a crash becomes uncertain. Nothing is sent. */
    suspend fun load() {
        for (identity in store.identities) locked(identity) { recover(identity) }
    }

    /** A balance read for the UI. While something is unsettled it settles that instead of adding a second opinion. */
    suspend fun queryQuota(identity: String): Quota = locked(identity) {
        state(identity)
        credentialsFor(identity)
        if (isBlocked(identity)) refreshPending(identity) else quote(identity)
    }

    // region download prepayment

    /**
     * Charges a download up front, once per [taskId]. A repeat call returns the confirmed receipt instead of charging
     * again. The caller has already checked that the audio belongs to [identity]. Null when the audio is free.
     */
    suspend fun prepay(
        identity: String,
        taskId: String,
        trackId: String,
        durationSeconds: Double,
        requiresReward: Boolean,
        userConfirmed: Boolean,
        expectedSeconds: Int? = null,
        expectedBalance: Long? = null,
    ): DebitEntry? = locked(identity) {
        if (!requiresReward) return@locked null
        if (taskId.isBlank()) throw XmlyException("account_mismatch", definitive = true)
        val track = normalizeTrack(trackId)
        recover(identity)
        guard(identity)
        for (receipt in receipts(identity).asReversed()) {
            if (receipt.taskId != taskId) continue
            if (receipt.trackId != track) throw XmlyException("task_mismatch", definitive = true)
            if (receipt.state == DebitState.Confirmed && !receipt.historical) return@locked receipt
        }
        if (!durationSeconds.isFinite() || durationSeconds <= 0 || durationSeconds > Int.MAX_VALUE) {
            throw XmlyException("invalid_duration", definitive = true)
        }
        val seconds = ceil(durationSeconds).toInt()
        if (!userConfirmed) throw XmlyException("confirmation_required", definitive = true)
        settleLocked(identity, pause = false)
        // Settlement took its own balance lock; take this one only now. The fresh quote, the durable journal, the
        // report and its result must be atomic to reward claims.
        locks.withLock(identity) {
            val quota = quote(identity)
            if ((expectedSeconds != null && expectedSeconds != seconds) ||
                (expectedBalance != null && expectedBalance != quota.remainingSeconds)) {
                throw XmlyException("confirmation_changed", definitive = true)
            }
            if (quota.remainingSeconds < seconds) throw Insufficient
            val external = currentCoroutineContext()[ExternalRetry] != null
            var attempt = 0
            var receipt: DebitEntry? = null
            while (receipt == null) {
                val operation = DebitEntry(newId(), track, taskId, seconds, quota.remainingSeconds, null, clock(), DebitState.Submitting)
                try {
                    submit(identity, operation, online = false)
                    receipt = receipts(identity).single { it.operationId == operation.operationId }
                } catch (error: XmlyException) {
                    val rejected = receipts(identity).any { it.operationId == operation.operationId && it.state == DebitState.Rejected }
                    // Only a definite refusal is retried: the server said no, so nothing was spent.
                    if (external || !rejected || !error.definitive || attempt >= MaxPrepayRetries || error.code in NoRetry) throw error
                    sleep(maxOf(error.retryAfter?.inWholeMilliseconds ?: 0L, (1L shl attempt) * 1000))
                    guard(identity)
                    if (quote(identity).remainingSeconds != quota.remainingSeconds) throw XmlyException("confirmation_changed", definitive = true)
                    attempt++
                }
            }
            receipt
        }
    }

    // endregion

    // region metering

    /** Starts paused. Only real playback state events may enable metering. */
    suspend fun startWatch(identity: String, trackId: String, requiresReward: Boolean, prepaid: Boolean = false, speed: Double = 1.0) =
        watchChanges.withLock {
            validSpeed(speed)
            val track = normalizeTrack(trackId)
            stopWatchLocked()
            if (!requiresReward || prepaid || identity == Anonymous) return@withLock
            locked(identity) {
                recover(identity)
                guard(identity)
                val quota = quote(identity)
                val newDevice = quota.newDevice && quota.remainingSeconds == 0L
                if (!newDevice && quota.remainingSeconds - debt(identity) <= 0) throw Insufficient
                watch = Listening(identity, track, speed, monotonicNanos())
            }
        }

    /**
     * Records a player event the moment it happens. There is no position argument: seeking can never produce
     * billable seconds. Periodic saves go through [sampleCurrentState].
     */
    fun recordPlaybackState(playing: Boolean, buffering: Boolean, speed: Double? = null) {
        if (speed != null) validSpeed(speed)
        val current = watch ?: return
        synchronized(current) {
            if (current.suspended) return
            captureElapsed(current)
            current.playing = playing && !buffering
            if (speed != null) current.speed = speed
        }
    }

    /** Saves the latest device state without replacing it with queued UI state. */
    suspend fun sampleCurrentState(speed: Double? = null) {
        if (speed != null) {
            validSpeed(speed)
            watch?.let { synchronized(it) { captureElapsed(it); it.speed = speed } }
        }
        observe(playing = watch?.let { synchronized(it) { it.playing } } ?: false, buffering = false, stateAlreadyRecorded = true)
    }

    suspend fun observe(playing: Boolean, buffering: Boolean, speed: Double? = null, stateAlreadyRecorded: Boolean = false) {
        if (!stateAlreadyRecorded) recordPlaybackState(playing, buffering, speed)
        val observed = watch
        watchChanges.withLock {
            val current = observed
            if (current == null || current !== watch) return@withLock
            locked(current.identity) {
                try {
                    sample(current.identity)
                    if (playing && (retryAt.containsKey(current.identity) || isBlocked(current.identity))) checkAvailableUsage(current.identity)
                    // A later device event may already have updated the actual state.
                    if (!playing || monotonicNanos() - synchronized(current) { current.settledAt } >= SettleEveryNanos) {
                        settleOrDefer(current.identity, pause = false)
                    }
                } catch (failure: Exception) {
                    // Metering that cannot be saved or settled stops rather than guessing.
                    suspendWatch(current)
                    throw failure
                }
            }
        }
    }

    suspend fun stopWatch() = watchChanges.withLock { stopWatchLocked() }

    /** A restore pauses producers first; unknown journals are preserved without sending anything. */
    suspend fun captureForRestore() = watchChanges.withLock {
        val current = watch ?: return@withLock
        pauseWatch(current)
        locked(current.identity) {
            // Keep the watch and its unsaved sample reachable if persisting fails.
            sample(current.identity)
            watch = null
        }
    }

    // endregion

    // region settlement

    /** Previewing or prepaying a download does not pause playback; sampling carries on across the network waits. */
    suspend fun settleForDownload(identity: String) = locked(identity) { settleLocked(identity, pause = false) }

    /** The account-switch boundary. Callers pause the player first. */
    suspend fun settle(identity: String? = null) {
        if (identity != null) locked(identity) { settleLocked(identity) } else settleAccounts(exiting = false)
    }

    /** Also attempts usage that had already been saved when the last source was paused. */
    suspend fun settleBeforeAccountChange(identity: String) = locked(identity) { settleOrDefer(identity) }

    /** Exit refreshes unknown reports without replaying them. */
    suspend fun settleForExit() = settleAccounts(exiting = true)

    private suspend fun settleAccounts(exiting: Boolean) {
        for (identity in store.identities) {
            locked(identity) { if (exiting) settleOrDefer(identity) else settleLocked(identity) }
        }
    }

    private suspend fun settleOrDefer(identity: String, pause: Boolean = true) {
        retryAt[identity]?.let { if (it - monotonicNanos() > 0) return }
        try {
            settleLocked(identity, pause)
            retryAt.remove(identity)
        } catch (failure: XmlyException) {
            if (failure.code !in Deferrable) throw failure
            retryAt[identity] = monotonicNanos() + RetryAfterNanos
            watch?.takeIf { it.identity == identity }?.let { synchronized(it) { it.settledAt = monotonicNanos() } }
        }
    }

    /** Reports the whole seconds of debt, one balance-locked debit at a time. */
    private suspend fun settleLocked(identity: String, pause: Boolean = true) {
        val current = watch
        if (pause && current?.identity == identity) pauseWatch(current)
        recover(identity)
        sample(identity)
        if (!isBlocked(identity) && debt(identity) < 1 && current?.identity != identity) return
        guard(identity)
        var seconds = floor(debt(identity)).toLong()
        while (seconds > 0) {
            val amount = minOf(seconds, Int.MAX_VALUE.toLong()).toInt()
            locks.withLock(identity) {
                val quota = quote(identity)
                val newDevice = quota.newDevice && quota.remainingSeconds == 0L
                if (!newDevice && quota.remainingSeconds < amount) throw Insufficient
                val track = current?.takeIf { it.identity == identity }?.trackId.orEmpty()
                submit(identity, DebitEntry(newId(), track, "", amount, quota.remainingSeconds, null, clock(), DebitState.Submitting), online = true)
            }
            seconds = floor(debt(identity)).toLong()
        }
        if (current?.identity == identity) synchronized(current) { current.settledAt = monotonicNanos() }
    }

    private suspend fun stopWatchLocked() {
        val current = watch ?: return
        pauseWatch(current)
        locked(current.identity) {
            // A retry cooldown may skip the remote settlement, never the last local sample.
            sample(current.identity)
            settleOrDefer(current.identity)
        }
        watch = null
    }

    // endregion

    // region journal

    /**
     * Journal, send, settle. The entry is durable as "submitting" before any request exists, so a failed commit never
     * reaches the network, and a crash afterwards leaves an entry that blocks replay.
     */
    private suspend fun submit(identity: String, operation: DebitEntry, online: Boolean) {
        write(identity) { if (online) it.copy(pending = operation, uncertain = true) else it.copy(receipts = it.receipts + operation) }
        // While the balance is above 20 hours the day is free and the report endpoint is unavailable.
        val freeDay = operation.balanceBefore > FreeDaySeconds
        val balanceAfter = try {
            if (freeDay) operation.balanceBefore
            else service.debit(credentialsFor(identity), operation.seconds, quotas[identity]?.newDevice ?: false).remainingSeconds
        } catch (failure: Throwable) {
            if (failure is XmlyException && failure.definitive) {
                try {
                    finish(identity, operation, online, DebitState.Rejected)
                } catch (finishFailure: Exception) {
                    markUncertain(identity, operation, online)
                    throw UsageUncertain(finishFailure)
                }
                throw failure
            }
            if (failure is CancellationException) {
                markUncertain(identity, operation, online)
                throw failure
            }
            try {
                refreshPending(identity)
                return
            } catch (refreshFailure: Throwable) {
                markUncertain(identity, operation, online)
                throw refreshFailure
            }
        }
        val confirmed = operation.copy(balanceAfter = balanceAfter)
        quotas[identity] = Quota(balanceAfter, quotas[identity]?.newDevice ?: false, Instant.fromEpochMilliseconds(clock()))
        try {
            finish(identity, confirmed, online, DebitState.Confirmed)
        } catch (failure: Exception) {
            // The durable "submitting" entry stays authoritative even when the disk stays unavailable.
            markUncertain(identity, operation, online)
            throw UsageUncertain(failure)
        }
    }

    private suspend fun finish(identity: String, operation: DebitEntry, online: Boolean, state: DebitState) {
        write(identity) { ledger ->
            if (online) {
                val debt = if (state == DebitState.Confirmed) maxOf(0.0, ledger.debtSeconds - operation.seconds) else ledger.debtSeconds
                ledger.copy(debtSeconds = debt, pending = null, uncertain = false)
            } else {
                val index = ledger.receipts.indexOfFirst { it.operationId == operation.operationId }
                if (index < 0) throw XmlyException("invalid_ledger")
                ledger.copy(receipts = ledger.receipts.toMutableList().also { it[index] = operation.copy(state = state) })
            }
        }
    }

    private suspend fun markUncertain(identity: String, operation: DebitEntry, online: Boolean) {
        // Best effort, and it must survive cancellation: the "submitting" entry is already durable and blocks replay.
        withContext(NonCancellable) {
            try {
                write(identity) { ledger ->
                    val uncertain = operation.copy(state = DebitState.Uncertain)
                    if (online) {
                        ledger.copy(pending = uncertain, uncertain = true)
                    } else {
                        val index = ledger.receipts.indexOfFirst { it.operationId == operation.operationId }
                        if (index < 0) ledger else ledger.copy(receipts = ledger.receipts.toMutableList().also { it[index] = uncertain })
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    /** Adopts the server's balance instead of inferring each report's outcome, and closes every unsettled entry. */
    private suspend fun refreshPending(identity: String): Quota {
        val before = state(identity)
        val pending = before.pending
        if (pending != null && pending.seconds > before.debtSeconds) throw XmlyException("invalid_ledger")
        val quota = quote(identity)
        if (pending != null) finish(identity, pending.copy(balanceAfter = quota.remainingSeconds), online = true, state = DebitState.Confirmed)
        for (receipt in before.receipts) {
            if (receipt.state != DebitState.Submitting && receipt.state != DebitState.Uncertain) continue
            finish(identity, receipt.copy(balanceAfter = quota.remainingSeconds), online = false, state = DebitState.Confirmed)
        }
        if (pending == null && state(identity).uncertain) write(identity) { it.copy(uncertain = false) }
        return quota
    }

    private suspend fun recover(identity: String) {
        val current = state(identity)
        val recoverPending = current.pending?.state == DebitState.Submitting
        if (!recoverPending && current.receipts.none { it.state == DebitState.Submitting }) return
        write(identity) { ledger ->
            ledger.copy(
                receipts = ledger.receipts.map { if (it.state == DebitState.Submitting) it.copy(state = DebitState.Uncertain) else it },
                pending = if (recoverPending) ledger.pending?.copy(state = DebitState.Uncertain) else ledger.pending,
                uncertain = ledger.uncertain || recoverPending,
            )
        }
    }

    // endregion

    // region helpers

    private suspend fun sample(identity: String) {
        val current = watch
        if (current == null || current.identity != identity) return
        val amount = synchronized(current) { captureElapsed(current); current.unsaved }
        if (amount == 0.0) return
        try {
            write(identity) { it.copy(debtSeconds = it.debtSeconds + amount) }
            synchronized(current) { current.unsaved -= amount }
        } catch (failure: Exception) {
            suspendWatch(current)
            throw failure
        }
    }

    /** Caller holds the watch's monitor. */
    private fun captureElapsed(current: Listening) {
        val now = monotonicNanos()
        val elapsed = if (now > current.sampledAt) (now - current.sampledAt) / NanosPerSecond else 0.0
        if (current.playing) current.unsaved += elapsed * current.speed * ReportFactor
        current.sampledAt = now
    }

    private fun suspendWatch(current: Listening) = synchronized(current) {
        captureElapsed(current)
        current.playing = false
        current.suspended = true
    }

    private fun pauseWatch(current: Listening) = synchronized(current) {
        captureElapsed(current)
        current.playing = false
    }

    private fun checkAvailableUsage(identity: String) {
        val quota = quotas[identity] ?: return
        if (!(quota.newDevice && quota.remainingSeconds == 0L) && quota.remainingSeconds - debt(identity) <= 0) throw Insufficient
    }

    private suspend fun guard(identity: String) {
        credentialsFor(identity)
        if (isBlocked(identity)) refreshPending(identity)
    }

    private suspend fun quote(identity: String): Quota {
        val credentials = credentialsFor(identity)
        val quota = service.quota(credentials)
        // A login renewed during the read may belong to a different device: do not mix the two.
        if (credentialsFor(identity).cacheIdentity != credentials.cacheIdentity) throw XmlyException("account_mismatch", definitive = true)
        quotas[identity] = quota
        return quota
    }

    private suspend fun write(identity: String, update: (UsageState) -> UsageState) = store.updateUsage(identity, update)

    private fun state(identity: String): UsageState = store.usage(identity) ?: throw XmlyException("missing_account", definitive = true)

    private fun credentialsFor(identity: String): Credentials =
        store.credentials(identity) ?: throw XmlyException("login_required", definitive = true)

    private suspend fun <T> locked(identity: String, action: suspend () -> T): T =
        accountLocks.getOrPut(identity, ::Mutex).withLock { action() }

    private fun validSpeed(speed: Double) {
        if (!speed.isFinite() || speed < 0.5 || speed > 3.0) throw XmlyException("invalid_speed", definitive = true)
    }

    private fun normalizeTrack(value: String): String {
        val id = TrackPattern.matchEntire(value)?.groupValues?.get(1) ?: throw XmlyException("invalid_id", definitive = true)
        return "xmly:$id"
    }

    // endregion

    private companion object {
        const val Anonymous = "anonymous"
        const val ReportFactor = 1.0
        const val FreeDaySeconds = 20 * 3600L
        const val MaxPrepayRetries = 4
        const val NanosPerSecond = 1_000_000_000.0
        const val SettleEveryNanos = 120L * 1_000_000_000
        const val RetryAfterNanos = 30L * 1_000_000_000
        val TrackPattern = Regex("(?:xmly:)?([1-9][0-9]{0,18})")
        val Insufficient = XmlyException("insufficient_quota", definitive = true)
        val NoRetry = setOf(
            "invalid_duration", "invalid_ledger", "login_expired", "login_required", "account_mismatch",
            "invalid_endpoint", "insufficient_quota", "rate_limited",
        )
        val Deferrable = setOf(
            "usage_uncertain", "uncertain", "network", "network_error", "http_error", "rate_limited", "invalid_quota", "invalid_json",
        )
    }
}

/** The outcome of a debit could not be recorded, so it is treated as unknown until the balance is checked. */
private class UsageUncertain(cause: Throwable) : XmlyException("usage_uncertain", cause = cause)

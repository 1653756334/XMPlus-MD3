package com.xuleo.xmplus.md3.core.account

import com.xuleo.xmplus.md3.core.model.Credentials
import com.xuleo.xmplus.md3.core.model.Quota
import com.xuleo.xmplus.md3.core.model.XmlyException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One application-scoped writer. Verification never mounts a candidate account. Persist first,
 * publish second: failed validation/storage leaves the previous selection and credentials intact.
 * Media and ledger integrations must pause/settle their producers before invoking removal or renewal.
 */
class AccountRepository(
    private val store: AccountStore,
    private val quotaReader: QuotaReader,
    private val rewards: RewardClaimer,
    private val locks: BalanceLocks = BalanceLocks(),
) : UsageStore {
    private val mutex = Mutex()
    // Written only under [mutex]; the automatic top-up reads it without the lock to notice a change of mind.
    @Volatile private var snapshot: AccountSnapshot? = null
    private val expired: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val mutableState = MutableStateFlow<AccountsState>(AccountsState.Loading)
    val state = mutableState.asStateFlow()

    suspend fun load() = mutex.withLock {
        if (snapshot != null) return@withLock
        mutableState.value = AccountsState.Loading
        try {
            val loaded = store.read()
            AccountSnapshotCodec.validate(loaded)
            snapshot = loaded
            publish(loaded)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Corruption/key loss is not a first run. Preserve the file and prohibit writes.
            mutableState.value = AccountsState.Unavailable
        }
    }

    suspend fun importCredentials(input: String, makePrimary: Boolean, recoveryIdentity: String? = null) =
        importCredentials(Credentials.parse(input), makePrimary, recoveryIdentity)

    suspend fun importCredentials(credentials: Credentials, makePrimary: Boolean, recoveryIdentity: String? = null) = mutex.withLock {
        val before = loaded()
        if (recoveryIdentity != null) {
            active(recoveryIdentity)
            if (credentials.identity != recoveryIdentity) throw XmlyException("credential_mismatch", definitive = true)
        }
        val quota = quotaReader.query(credentials)
        currentCoroutineContext().ensureActive()
        val previous = before.records.firstOrNull { it.identity == credentials.identity }
        val record = AccountRecord(
            identity = credentials.identity,
            uid = credentials.uid,
            deviceHint = if (credentials.deviceId.length <= 4) "****" else "…" + credentials.deviceId.takeLast(4),
            credentials = credentials,
            quota = quota,
            rewardPaused = previous?.rewardPaused ?: false,
            usage = previous?.usage ?: UsageState(),
            retained = previous?.retained ?: kotlinx.serialization.json.JsonObject(emptyMap()),
        )
        val records = before.records.toMutableList()
        val index = records.indexOfFirst { it.identity == record.identity }
        if (index < 0) records.add(record) else records[index] = record
        val primary = if (recoveryIdentity == null && (makePrimary || before.primaryIdentity == null)) record.identity else before.primaryIdentity
        commit(before.copy(records = records.toList(), primaryIdentity = primary), clearExpiry = record.identity)
    }

    suspend fun setPrimary(identity: String) = mutex.withLock {
        val before = loaded()
        active(identity)
        if (before.primaryIdentity != identity) commit(before.copy(primaryIdentity = identity))
    }

    suspend fun remove(identity: String) = mutex.withLock {
        val before = loaded()
        active(identity)
        // Keep the record, including future ledger/library metadata; revoke only the login.
        val records = before.records.map { if (it.identity == identity) it.copy(credentials = null) else it }
        val remaining = records.filter { it.credentials != null }
        val primary = if (before.primaryIdentity == identity) {
            (remaining.firstOrNull { it.identity !in expired } ?: remaining.firstOrNull())?.identity
        } else before.primaryIdentity
        commit(before.copy(records = records, primaryIdentity = primary), clearExpiry = identity)
    }

    suspend fun refreshQuota(identity: String) = mutex.withLock {
        val before = loaded()
        val record = active(identity)
        if (identity in expired) throw XmlyException("login_expired", definitive = true)
        val quota = try {
            quotaReader.query(requireNotNull(record.credentials))
        } catch (failure: XmlyException) {
            if (failure.code == "login_expired") {
                expired.add(identity)
                publish(before)
            }
            throw failure
        }
        currentCoroutineContext().ensureActive()
        commit(before.copy(records = before.records.map {
            if (it.identity == identity) it.copy(quota = quota) else it
        }), clearExpiry = identity)
    }

    override val identities: List<String> get() = snapshot?.records?.map { it.identity }.orEmpty()

    override fun usage(identity: String): UsageState? = snapshot?.records?.firstOrNull { it.identity == identity }?.usage

    override fun credentials(identity: String): Credentials? = snapshot?.records?.firstOrNull { it.identity == identity }?.credentials

    override suspend fun updateUsage(identity: String, update: (UsageState) -> UsageState) = mutex.withLock {
        val before = loaded()
        val record = before.records.firstOrNull { it.identity == identity } ?: throw XmlyException("missing_account", definitive = true)
        val next = update(record.usage)
        if (next != record.usage) {
            commit(before.copy(records = before.records.map { if (it.identity == identity) it.copy(usage = next) else it }))
        }
    }

    /**
     * Merges an XMPlus backup into the accounts. Every login in it is verified with a balance read first, and one that
     * fails stops the whole import with nothing changed. What the device already has wins; the backup only adds.
     * Returns the number of accounts the backup carried.
     */
    suspend fun restoreArchive(contents: ArchiveContents): Int {
        val verified = HashMap<String, Quota>()
        for (archived in contents.accounts) {
            val credentials = archived.credentials ?: continue
            verified[archived.identity] = quotaReader.query(credentials)
        }
        mutex.withLock {
            val before = loaded()
            val records = before.records.toMutableList()
            for (archived in contents.accounts) {
                val index = records.indexOfFirst { it.identity == archived.identity }
                val merged = mergeArchivedAccount(archived, records.getOrNull(index), verified[archived.identity])
                if (index < 0) records.add(merged) else records[index] = merged
            }
            val primary = contents.primaryIdentity?.takeIf { id -> records.any { it.identity == id && it.credentials != null } }
            commit(before.copy(records = records, primaryIdentity = primary ?: before.primaryIdentity), clearExpiry = verified.keys)
        }
        return contents.accounts.size
    }

    suspend fun setAutoClaim(enabled: Boolean) = mutex.withLock {
        val before = loaded()
        if (before.autoClaim != enabled) commit(before.copy(autoClaim = enabled))
    }

    /** The user has checked the official app; claiming may run again. */
    suspend fun resumeRewards(identity: String) = mutex.withLock {
        val before = loaded()
        if (active(identity).rewardPaused) commit(before.withPaused(identity, false))
    }

    /**
     * One claim request, never repeated. Whatever goes wrong after it started, the outcome is unknown, so claiming
     * pauses for this account until [resumeRewards].
     */
    suspend fun claimOnce(identity: String) {
        // The account lock is only ever held briefly: the ledger writes under it while holding the balance lock.
        val credentials = mutex.withLock {
            val record = active(identity)
            if (identity in expired) throw XmlyException("login_expired", definitive = true)
            if (record.rewardPaused) throw XmlyException("reward_paused", definitive = true)
            requireNotNull(record.credentials)
        }
        val quota = try {
            locks.withLock(identity) {
                // A claim that failed while this one waited has paused the account.
                if (snapshot?.records?.firstOrNull { it.identity == identity }?.rewardPaused == true) {
                    throw XmlyException("reward_paused", definitive = true)
                }
                rewards.claim(credentials)
                quotaReader.query(credentials)
            }
        } catch (failure: Throwable) {
            withContext(NonCancellable) { mutex.withLock { recordFailure(identity, failure) } }
            throw failure
        }
        mutex.withLock {
            currentCoroutineContext().ensureActive()
            commit(loaded().withQuota(identity, quota))
        }
    }
    /**
     * Tops [identity] up from below [LowSeconds] towards [TargetSeconds] while automatic claiming stays enabled and
     * the login is unchanged. Each claim is a single request: a failure stops the loop and pauses the account. The
     * network work runs outside [mutex], so account screens stay responsive while this runs.
     */
    suspend fun topUp(identity: String) {
        val credentials = mutex.withLock { loaded().eligibleCredentials(identity) } ?: return
        fun allowed() = snapshot?.eligibleCredentials(identity)?.cacheIdentity == credentials.cacheIdentity
        var quota = try {
            quotaReader.query(credentials)
        } catch (failure: XmlyException) {
            if (failure.code == "login_expired") withContext(NonCancellable) { mutex.withLock { recordFailure(identity, failure) } }
            throw failure
        }
        if (!allowed() || quota.remainingSeconds >= LowSeconds) return
        // At most MaxClaims per run, so a one-second reward cannot keep this loop going for ever.
        for (count in 0 until MaxClaims) {
            if (!allowed() || quota.remainingSeconds >= TargetSeconds) break
            val previous = quota.remainingSeconds
            try {
                val claimed = locks.withLock(identity) {
                    if (!allowed()) return@withLock false
                    rewards.claim(credentials)
                    true
                }
                if (!claimed) break
                quota = quotaReader.query(credentials)
            } catch (failure: Throwable) {
                withContext(NonCancellable) { mutex.withLock { recordFailure(identity, failure) } }
                if (failure is CancellationException) throw failure
                break
            }
            if (quota.remainingSeconds <= previous) break
        }
        mutex.withLock { if (allowed()) commit(loaded().withQuota(identity, quota)) }
    }

    /** Caller holds [mutex]. */
    private suspend fun recordFailure(identity: String, failure: Throwable) {
        withContext(NonCancellable) {
            val current = loaded()
            if ((failure as? XmlyException)?.code == "login_expired") {
                // Definitely not applied: the login needs renewing, not the user's check of the official app.
                expired.add(identity)
                publish(current)
            } else if (current.records.any { it.identity == identity }) {
                commit(current.withPaused(identity, true))
            }
        }
    }
    private fun AccountSnapshot.eligibleCredentials(identity: String): Credentials? {
        if (!autoClaim || identity in expired) return null
        return records.firstOrNull { it.identity == identity && !it.rewardPaused }?.credentials
    }

    private fun AccountSnapshot.withPaused(identity: String, paused: Boolean) =
        copy(records = records.map { if (it.identity == identity) it.copy(rewardPaused = paused) else it })

    private fun AccountSnapshot.withQuota(identity: String, quota: Quota) =
        copy(records = records.map { if (it.identity == identity) it.copy(quota = quota) else it })

    private fun loaded() = snapshot ?: throw XmlyException("account_read_failed")

    private fun active(identity: String) = loaded().records.firstOrNull {
        it.identity == identity && it.credentials != null
    } ?: throw XmlyException("missing_account", definitive = true)

    private suspend fun commit(next: AccountSnapshot, clearExpiry: String?) = commit(next, listOfNotNull(clearExpiry))

    private suspend fun commit(next: AccountSnapshot, clearExpiry: Collection<String> = emptyList()) {
        AccountSnapshotCodec.validate(next)
        currentCoroutineContext().ensureActive()
        // Cancellation may stop verification, but cannot split the short durable local commit.
        withContext(NonCancellable) {
            store.write(next)
            snapshot = next
            expired.removeAll(clearExpiry.toSet())
            publish(next)
        }
    }

    private fun publish(value: AccountSnapshot) {
        mutableState.value = AccountsState.Ready(value.records.filter { it.credentials != null }.map {
            AccountSummary(it.identity, it.uid, it.deviceHint, it.quota,
                it.identity == value.primaryIdentity, it.identity in expired, it.rewardPaused)
        }, value.autoClaim)
    }

    private companion object {
        const val LowSeconds = 7_200
        const val TargetSeconds = 14_400
        const val MaxClaims = 64
    }
}

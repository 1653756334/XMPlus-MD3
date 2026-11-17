package com.xuleo.xmplus.md3.core.account

import com.xuleo.xmplus.md3.core.model.Credentials
import com.xuleo.xmplus.md3.core.model.XmlyException
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
class AccountRepository(private val store: AccountStore, private val quotaReader: QuotaReader) {
    private val mutex = Mutex()
    private var snapshot: AccountSnapshot? = null
    private val expired = mutableSetOf<String>()
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
            retained = previous?.retained ?: kotlinx.serialization.json.JsonObject(emptyMap()),
        )
        val records = before.records.toMutableList()
        val index = records.indexOfFirst { it.identity == record.identity }
        if (index < 0) records.add(record) else records[index] = record
        val primary = if (recoveryIdentity == null && (makePrimary || before.primaryIdentity == null)) record.identity else before.primaryIdentity
        commit(AccountSnapshot(records.toList(), primary), clearExpiry = record.identity)
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
        commit(AccountSnapshot(records, primary), clearExpiry = identity)
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

    private fun loaded() = snapshot ?: throw XmlyException("account_read_failed")

    private fun active(identity: String) = loaded().records.firstOrNull {
        it.identity == identity && it.credentials != null
    } ?: throw XmlyException("missing_account", definitive = true)

    private suspend fun commit(next: AccountSnapshot, clearExpiry: String? = null) {
        AccountSnapshotCodec.validate(next)
        currentCoroutineContext().ensureActive()
        // Cancellation may stop verification, but cannot split the short durable local commit.
        withContext(NonCancellable) {
            store.write(next)
            snapshot = next
            clearExpiry?.let(expired::remove)
            publish(next)
        }
    }

    private fun publish(value: AccountSnapshot) {
        mutableState.value = AccountsState.Ready(value.records.filter { it.credentials != null }.map {
            AccountSummary(it.identity, it.uid, it.deviceHint, it.quota,
                it.identity == value.primaryIdentity, it.identity in expired)
        })
    }
}

package com.xuleo.xmplus.md3.core.account

import com.xuleo.xmplus.md3.core.model.Credentials
import com.xuleo.xmplus.md3.core.model.Quota
import kotlinx.serialization.json.JsonObject

/** Durable private state. This representation must never be passed to the UI or saved in a Bundle. */
data class AccountRecord(
    val identity: String,
    val uid: String,
    val deviceHint: String,
    val credentials: Credentials?,
    val quota: Quota,
    /** Set when a claim's outcome is unknown or it failed; claiming stays off until the user has checked the official app. */
    val rewardPaused: Boolean = false,
    val usage: UsageState = UsageState(),
    val retained: JsonObject = JsonObject(emptyMap()),
)

data class AccountSnapshot(
    val records: List<AccountRecord> = emptyList(),
    val primaryIdentity: String? = null,
    /** Opt-in: top accounts up automatically. It applies to every account. */
    val autoClaim: Boolean = false,
)

interface AccountStore {
    suspend fun read(): AccountSnapshot
    suspend fun write(snapshot: AccountSnapshot)
}

/** A successful read validates a candidate login without claiming or spending listening time. */
fun interface QuotaReader {
    suspend fun query(credentials: Credentials): Quota
}

/** Sends one claim request. It never retries: the caller owns what an unknown outcome means. */
fun interface RewardClaimer {
    suspend fun claim(credentials: Credentials)
}

data class AccountSummary(
    val identity: String,
    val uid: String,
    val deviceHint: String,
    val quota: Quota,
    val primary: Boolean,
    val expired: Boolean,
    val rewardPaused: Boolean,
)

sealed interface AccountsState {
    data object Loading : AccountsState
    data object Unavailable : AccountsState
    data class Ready(val accounts: List<AccountSummary>, val autoClaim: Boolean = false) : AccountsState
}

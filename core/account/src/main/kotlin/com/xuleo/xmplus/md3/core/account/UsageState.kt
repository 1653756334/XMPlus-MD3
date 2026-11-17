package com.xuleo.xmplus.md3.core.account

/** The life of one debit: journaled before it is sent, then settled by the server's answer or by a balance check. */
enum class DebitState(val key: String) {
    Prepared("prepared"),
    Submitting("submitting"),
    Confirmed("confirmed"),
    Uncertain("uncertain"),
    Rejected("rejected");

    companion object {
        fun of(key: Any?): DebitState? = entries.firstOrNull { it.key == key }
    }
}

/**
 * One debit. A local [operationId] only identifies the journal entry: the server has no idempotency key, which is why
 * an entry that may have been sent is never sent again.
 *
 * A download prepayment carries a [taskId]. An online listening debit does not, and may have no [trackId].
 */
data class DebitEntry(
    val operationId: String,
    val trackId: String,
    val taskId: String,
    val seconds: Int,
    val balanceBefore: Long,
    val balanceAfter: Long?,
    val createdAt: Long,
    val state: DebitState,
    val historical: Boolean = false,
)

/**
 * The billing state of one account: listening time not yet reported ([debtSeconds], fractional), the online debit in
 * flight, and the receipts of download prepayments.
 */
data class UsageState(
    val debtSeconds: Double = 0.0,
    val pending: DebitEntry? = null,
    val uncertain: Boolean = false,
    val receipts: List<DebitEntry> = emptyList(),
) {
    /** Something may already have been sent, so nothing new may be until the balance has been checked. */
    val blocked: Boolean
        get() = uncertain || pending != null || receipts.any { it.state == DebitState.Submitting || it.state == DebitState.Uncertain }
}
package com.xuleo.xmplus.md3.core.model

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

/**
 * How one operation wants rate-limited reads handled. It travels in the coroutine context, so it applies to every
 * read below the call that installed it without threading a parameter through each API.
 *
 * With no policy a rate-limited read counts against the ordinary failure budget; with a policy it gets
 * [rateLimitRetries] extra attempts, each waiting at least [minimumRateLimitDelay] or the server's cooldown.
 */
class ReadPolicy(val rateLimitRetries: Int, val minimumRateLimitDelay: Duration = Duration.ZERO) :
    AbstractCoroutineContextElement(Key) {
    init {
        if (rateLimitRetries !in 0..1000 || minimumRateLimitDelay.isNegative()) throw XmlyException("invalid_retry")
    }

    companion object Key : CoroutineContext.Key<ReadPolicy>
}

/** The caller (a download, say) owns the retry budget, so the client reports each failure instead of retrying. */
data object ExternalRetry : CoroutineContext.Element, CoroutineContext.Key<ExternalRetry> {
    override val key: CoroutineContext.Key<*> get() = this
}
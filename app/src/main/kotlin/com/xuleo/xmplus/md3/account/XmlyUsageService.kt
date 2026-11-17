package com.xuleo.xmplus.md3.account

import com.xuleo.xmplus.md3.core.account.UsageService
import com.xuleo.xmplus.md3.core.model.Credentials
import com.xuleo.xmplus.md3.core.model.Quota
import com.xuleo.xmplus.md3.core.model.ReadPolicy
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.core.protocol.XmlyQuotaClient
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** The server calls billing needs, with the strictness settlement wants: no waiting out a rate limit, ten seconds at most. */
class XmlyUsageService(private val client: XmlyQuotaClient) : UsageService {
    override suspend fun quota(credentials: Credentials): Quota = try {
        withTimeout(QuoteTimeout) { withContext(ReadPolicy(rateLimitRetries = 0)) { client.queryQuota(credentials) } }
    } catch (timeout: TimeoutCancellationException) {
        throw XmlyException("network_error", cause = timeout)
    }

    override suspend fun debit(credentials: Credentials, seconds: Int, newDevice: Boolean): Quota =
        client.debit(credentials, seconds, newDevice)

    private companion object {
        val QuoteTimeout = 10.seconds
    }
}
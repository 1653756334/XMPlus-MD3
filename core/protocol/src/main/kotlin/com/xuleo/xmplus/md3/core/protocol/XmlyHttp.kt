package com.xuleo.xmplus.md3.core.protocol

import com.xuleo.xmplus.md3.core.model.Credentials
import com.xuleo.xmplus.md3.core.model.ExternalRetry
import com.xuleo.xmplus.md3.core.model.ReadPolicy
import com.xuleo.xmplus.md3.core.model.XmlyException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The one door to the XMPlus API hosts: it signs nothing and decodes nothing domain-specific, but owns the rules every
 * call shares. Reads are retried with backoff; anything else is sent once and reported as "uncertain" if its outcome
 * is unknown, because replaying a reward or a debit could apply it twice. A rate-limit cooldown is kept per account and
 * host, as the original app kept it per client.
 */
class XmlyHttp internal constructor(
    private val executor: HttpExecutor,
    private val clock: () -> Long,
    private val wait: suspend (Long) -> Unit,
) {
    constructor() : this(OkHttpExecutor(), System::currentTimeMillis, { delay(it) })

    private val cooldowns = ConcurrentHashMap<String, Long>()

    /** Calls [endpoint] and returns its JSON object once the server reports success. */
    internal suspend fun call(endpoint: Endpoint, credentials: Credentials?, timestamp: Long? = null, body: JsonObject? = null): JsonObject {
        if (endpoint.authenticated && credentials == null) throw XmlyException("login_required", definitive = true)
        // The request is built once: a retry must repeat the same signed body, not sign a new one.
        val request = Request.Builder().url(endpoint.host.origin + endpoint.resolve(timestamp))
            .header("Accept", "application/json")
            .apply { if (endpoint.authenticated) header("Cookie", credentials!!.cookie) }
            .method(endpoint.method, body?.toString()?.toRequestBody(JsonMediaType)).build()
        val key = "${credentials?.identity.orEmpty()}@${endpoint.host.origin}"
        val context = currentCoroutineContext()
        val external = context[ExternalRetry] != null
        val policy = context[ReadPolicy].takeIf { endpoint.idempotent }
        var failures = 0
        var rateRetries = 0
        while (true) {
            var rateDelay = 0L
            val cooling = (cooldowns[key] ?: 0) - clock()
            if (cooling > 0) {
                if (external || policy?.rateLimitRetries == 0) throw XmlyException("rate_limited", retryAfter = cooling.milliseconds)
                wait(cooling)
            }
            try {
                return attempt(request, endpoint, key)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: IOException) {
                if (!endpoint.idempotent || external || failures++ >= MaxFailures) {
                    throw XmlyException(if (endpoint.idempotent) "network" else "uncertain", cause = failure)
                }
            } catch (failure: XmlyException) {
                if (!endpoint.idempotent || external || failure.code != "rate_limited") throw failure
                if (policy != null) {
                    if (rateRetries++ >= policy.rateLimitRetries) throw failure
                    rateDelay = maxOf(failure.retryAfter?.inWholeMilliseconds ?: 0, policy.minimumRateLimitDelay.inWholeMilliseconds)
                } else if (failures++ >= MaxFailures) {
                    throw failure
                }
            }
            val backoff = (1L shl (failures - 1).coerceIn(0, 4)) * 1000
            wait(maxOf(backoff, (cooldowns[key] ?: 0) - clock(), rateDelay))
        }
    }

    private suspend fun attempt(request: Request, endpoint: Endpoint, key: String): JsonObject {
        val reply = try {
            executor.execute(request, MaxResponseBytes)
        } catch (failure: HttpTransportException) {
            // A broken body does not hide the status: a 429 still carries its cooldown.
            val status = failure.status?.takeIf { it != 200 } ?: throw failure
            HttpReply(status, failure.retryAfter, ByteArray(0))
        }
        when {
            reply.status == 429 -> rateLimit(key, parseRetryAfter(reply.retryAfter, clock()) ?: DefaultCooldown)
            reply.status == 401 || (reply.status == 403 && endpoint.authenticated) -> throw expired()
            reply.status in 500..599 -> throw ServerUnavailable()
            reply.status != 200 -> {
                val clientError = reply.status in 400..499
                throw XmlyException(if (clientError) "rejected" else "protocol", definitive = clientError)
            }
        }
        val bytes = reply.body ?: throw XmlyException("response_too_large")
        val payload = try {
            Json.parseToJsonElement(decodeUtf8Strict(bytes)) as? JsonObject ?: throw XmlyException("invalid_json")
        } catch (failure: XmlyException) {
            throw failure
        } catch (_: Exception) {
            throw XmlyException("invalid_json")
        }
        val code = listOf(payload["ret"], payload["status"]).firstOrNull { it != null && it != JsonNull }
        val text = (code as? JsonPrimitive)?.content ?: if (code == null) "0" else "invalid"
        when {
            text == endpoint.successCode -> return payload
            text == "429" -> rateLimit(key, DefaultCooldown)
            text == "401" -> throw expired()
            text == "924" && endpoint.host == Host.Mobile -> throw XmlyException("content_removed", definitive = true)
            text in PermissionCodes -> throw XmlyException("permission_denied", definitive = true)
            else -> throw XmlyException("rejected", definitive = true)
        }
    }

    private fun expired() = XmlyException("login_expired", definitive = true)

    private fun rateLimit(key: String, milliseconds: Long): Nothing {
        val bounded = milliseconds.coerceIn(0, (MaxDartDate - clock()).coerceAtLeast(0))
        cooldowns[key] = clock() + bounded
        throw XmlyException("rate_limited", retryAfter = bounded.milliseconds, definitive = true)
    }

    /** A 5xx is the server failing, not the request being wrong, so it is handled like a dropped connection. */
    private class ServerUnavailable : IOException("Service unavailable")

    private companion object {
        const val MaxFailures = 4
        const val MaxResponseBytes = 2 * 1024 * 1024
        const val DefaultCooldown = 30_000L
        val PermissionCodes = setOf("5", "403", "726")
        val JsonMediaType = "application/json; charset=utf-8".toMediaType()
    }
}
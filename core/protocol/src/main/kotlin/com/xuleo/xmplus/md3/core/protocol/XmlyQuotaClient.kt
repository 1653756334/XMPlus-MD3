package com.xuleo.xmplus.md3.core.protocol

import com.xuleo.xmplus.md3.core.model.Credentials
import com.xuleo.xmplus.md3.core.model.Quota
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.core.model.dartTrim
import java.io.IOException
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * Only the authenticated config/queryQuota slice of XMPlus xmly_client.dart. No reward or debit
 * endpoint exists here. A candidate stays separate until this read validates its credentials.
 */
class XmlyQuotaClient internal constructor(
    private val http: OkHttpClient,
    private val clock: () -> Long,
    private val wait: suspend (Long) -> Unit,
) {
    constructor() : this(OkHttpClient.Builder()
            .followRedirects(false).followSslRedirects(false)
            .cookieJar(CookieJar.NO_COOKIES).retryOnConnectionFailure(false)
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build(),
        System::currentTimeMillis, { delay(it) })
    private val mutex = Mutex()
    private var cooldownUntil = 0L

    suspend fun queryQuota(credentials: Credentials): Quota = mutex.withLock {
        // Dart creates the signed body once before retrying, not once per attempt.
        val params = linkedMapOf("deviceId" to credentials.deviceId, "timestamp" to clock().toString(), "uid" to credentials.uid)
        val signature = XmlyCrypto.activitySignature(params)
        val body = buildJsonObject {
            put("deviceId", credentials.deviceId)
            put("timestamp", params.getValue("timestamp"))
            put("signature", signature)
        }
        val request = Request.Builder().url(ConfigEndpoint)
            .header("Accept", "application/json").header("Cookie", credentials.cookie)
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        var failures = 0
        while (true) {
            val cooldown = cooldownUntil - clock()
            if (cooldown > 0) wait(cooldown)
            try {
                return@withLock decodeQuota(requestPayload(request), clock)
            } catch (failure: IOException) {
                if (failures++ >= 4) throw XmlyException("network")
            } catch (failure: XmlyException) {
                if (failure.code != "rate_limited" || failures++ >= 4) throw failure
            }
            wait(maxOf((1L shl (failures - 1).coerceIn(0, 4)) * 1000, cooldownUntil - clock()))
        }
        @Suppress("UNREACHABLE_CODE")
        throw XmlyException("network")
    }

    private suspend fun requestPayload(request: Request): JsonObject = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching { response.use(::readPayload) }
                if (continuation.isActive) continuation.resumeWith(result)
            }
        })
    }

    private fun readPayload(response: Response): JsonObject {
        when {
            response.code == 429 -> rateLimit(retryAfter(response.header("Retry-After")))
            response.code == 401 || response.code == 403 -> fail("login_expired", true)
            response.code in 500..599 -> throw IOException("Service unavailable")
            response.code != 200 -> fail(if (response.code in 400..499) "rejected" else "protocol", response.code in 400..499)
        }
        val body = response.body
        if (body.contentLength() > MaxResponseBytes) fail("response_too_large")
        val source = body.source()
        if (source.request(MaxResponseBytes + 1L)) fail("response_too_large")
        val payload = try {
            Json.parseToJsonElement(decodeUtf8Strict(source.readByteArray())) as? JsonObject ?: fail("invalid_json")
        } catch (_: Exception) { fail("invalid_json") }
        val code = listOf(payload["ret"], payload["status"]).firstOrNull { it != null && it != JsonNull }
        when ((code as? JsonPrimitive)?.content ?: if (code == null) "0" else "invalid") {
            "0" -> return payload
            "429" -> rateLimit(30_000)
            "401" -> fail("login_expired", true)
            "924" -> fail("content_removed", true)
            "5", "403", "726" -> fail("permission_denied", true)
            else -> fail("rejected", true)
        }
    }

    private fun rateLimit(milliseconds: Long): Nothing {
        val bounded = milliseconds.coerceIn(0, (MaxDartDate - clock()).coerceAtLeast(0))
        cooldownUntil = clock() + bounded
        throw XmlyException("rate_limited", retryAfter = bounded.milliseconds, definitive = true)
    }

    private fun retryAfter(value: String?): Long {
        val text = value?.dartTrim() ?: return 30_000
        if (text.isNotEmpty() && text.all { it in '0'..'9' }) {
            return BigInteger(text).multiply(BigInteger.valueOf(1000))
                .min(BigInteger.valueOf((MaxDartDate - clock()).coerceAtLeast(0))).toLong()
        }
        // dart:io HttpDate accepts these three historical HTTP date formats.
        for (pattern in listOf("EEE, dd MMM yyyy HH:mm:ss 'GMT'", "EEEE, dd-MMM-yyyy HH:mm:ss 'GMT'", "EEE MMM d HH:mm:ss yyyy")) {
            val parser = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("GMT"); isLenient = false }
            val position = java.text.ParsePosition(0)
            val date = parser.parse(text, position)
            if (date != null && position.index == text.length) return (date.time - clock()).coerceAtLeast(0)
        }
        return 30_000
    }

    companion object {
        private const val ConfigEndpoint = "https://mobile.ximalaya.com/lite-mobile/audi/func/v1/config"
        private const val MaxResponseBytes = 2 * 1024 * 1024
        private const val MaxDartDate = 8_640_000_000_000_000L

        internal fun decodeQuota(payload: JsonObject, clock: () -> Long): Quota {
            val data = payload["data"] as? JsonObject ?: fail("invalid_quota")
            fun decode(name: String): String = try {
                val field = data[name] as? JsonPrimitive ?: fail("invalid_quota")
                if (!field.isString) fail("invalid_quota")
                val text = XmlyCrypto.decrypt(field.content, XmlyCrypto.DurationKey)
                val divider = text.lastIndexOf('|')
                require(divider >= 0)
                val timestamp = parseInteger(text.substring(divider + 1)) ?: fail("invalid_quota")
                require(BigInteger.valueOf(clock()).subtract(BigInteger.valueOf(timestamp)).abs() < BigInteger.valueOf(180_000))
                text.substring(0, divider)
            } catch (_: Exception) { fail("invalid_quota") }
            val newDevice = when (decode("newDevice")) {
                "true" -> true
                "false" -> false
                else -> fail("invalid_quota")
            }
            val remaining = if (newDevice && !data.containsKey("availableListenDuration")) 0L
                else parseInteger(decode("availableListenDuration")) ?: fail("invalid_quota")
            if (remaining < 0) fail("invalid_quota")
            return Quota(remaining, newDevice, Instant.fromEpochMilliseconds(clock()))
        }

        private fun parseInteger(value: String): Long? {
            val text = value.dartTrim()
            val sign = if (text.startsWith('-')) -1 else 1
            val unsigned = if (text.startsWith('+') || text.startsWith('-')) text.drop(1) else text
            val hexadecimal = unsigned.startsWith("0x", ignoreCase = true)
            val digits = if (hexadecimal) unsigned.drop(2) else unsigned
            if (digits.isEmpty() || digits.any { if (hexadecimal) it !in "0123456789abcdefABCDEF" else it !in '0'..'9' }) return null
            return try { BigInteger(digits, if (hexadecimal) 16 else 10).multiply(BigInteger.valueOf(sign.toLong())).longValueExact() }
                catch (_: ArithmeticException) { null }
        }

        private fun fail(code: String, definitive: Boolean = false): Nothing = throw XmlyException(code, definitive = definitive)
    }
}

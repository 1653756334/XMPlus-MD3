package com.xuleo.xmplus.md3.core.protocol

import com.xuleo.xmplus.md3.core.model.Credentials
import com.xuleo.xmplus.md3.core.model.Quota
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.core.model.dartTrim
import java.math.BigInteger
import kotlinx.serialization.json.*
import kotlin.time.Instant

/**
 * The authenticated config (read) and report (debit) calls of XMPlus xmly_client.dart. A candidate stays separate
 * until the read validates its credentials, so both take the credentials as an argument rather than owning an account.
 */
class XmlyQuotaClient(
    private val http: XmlyHttp,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun queryQuota(credentials: Credentials): Quota =
        decodeQuota(http.call(Endpoints.Config, credentials, body = signedBody(credentials, null)), clock)

    /**
     * Spends [seconds] of listening time. This is a write: it is sent once, and an unknown outcome is the caller's to
     * resolve by reading the balance. The reply does not say whether the device is new, so [newDevice] is the last
     * known value.
     */
    suspend fun debit(credentials: Credentials, seconds: Int, newDevice: Boolean): Quota {
        if (seconds < 1) throw XmlyException("invalid_duration", definitive = true)
        return decodeQuota(http.call(Endpoints.Report, credentials, body = signedBody(credentials, seconds)), clock, newDevice)
    }

    // Dart creates the signed body once; the transport repeats that same body on a retry.
    private fun signedBody(credentials: Credentials, duration: Int?): JsonObject {
        val timestamp = clock().toString()
        val params = linkedMapOf("deviceId" to credentials.deviceId, "timestamp" to timestamp, "uid" to credentials.uid)
        if (duration != null) params["duration"] = duration.toString()
        val signature = XmlyCrypto.activitySignature(params)
        return buildJsonObject {
            put("deviceId", credentials.deviceId)
            put("timestamp", timestamp)
            if (duration != null) put("duration", duration.toString())
            put("signature", signature)
        }
    }

    companion object {
        /** [knownNewDevice] is set for replies that omit the flag (report); config always carries it. */
        internal fun decodeQuota(payload: JsonObject, clock: () -> Long, knownNewDevice: Boolean? = null): Quota {
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
            val newDevice = knownNewDevice ?: when (decode("newDevice")) {
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

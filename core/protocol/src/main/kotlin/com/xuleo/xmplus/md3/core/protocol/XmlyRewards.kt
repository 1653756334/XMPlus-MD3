package com.xuleo.xmplus.md3.core.protocol

import com.xuleo.xmplus.md3.core.model.Credentials
import com.xuleo.xmplus.md3.core.model.XmlyException
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Port of xmly/rewards.py. No advertisement completion fields are fabricated: the empty claim the official app
 * accepts is sent, with the generated identity, signature and ticket the server requires.
 *
 * A claim is a write, so [XmlyHttp] sends it at most once; the caller decides what an unknown outcome means.
 */
class XmlyRewards(
    private val http: XmlyHttp,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Returns once the server confirmed the claim; throws "reward_rejected" when it answered no. */
    suspend fun claim(credentials: Credentials) {
        val uid = credentials.uid.toLongOrNull() ?: throw XmlyException("auth_required", definitive = true)
        val requestId = UUID.randomUUID().toString()
        val timestamp = clock()
        val body = buildJsonObject {
            put("requestId", requestId)
            put("uid", uid)
            put("ts", timestamp)
            put("signature", md5Hex("$requestId&$uid&$timestamp&$Salt"))
            put("ticket", XmlyCrypto.freshTicket(credentials, attributes = "b=lite&s=lite_time_reward&u=$uid"))
        }
        val result = (http.call(Endpoints.RewardClaim, credentials, timestamp, body)["data"] as? JsonObject)
            ?.get("success") as? JsonPrimitive
        when (result?.takeUnless { it.isString }?.content) {
            "true" -> return
            "false" -> throw XmlyException("reward_rejected", definitive = true)
            else -> throw XmlyException("invalid_response")
        }
    }

    private companion object {
        const val Salt = "0ab3443b71834b33be675af744d76ef0aaa74cd2b60c4b67"
    }
}
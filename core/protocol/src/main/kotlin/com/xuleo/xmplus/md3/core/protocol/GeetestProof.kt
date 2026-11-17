package com.xuleo.xmplus.md3.core.protocol

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Short-lived verification proof. Never write it to saved state, logs or disk. */
class GeetestProof private constructor(private val fields: Map<String, String>) {
    internal val lot: String get() = fields.getValue("lot_number")
    fun toJson() = JsonObject(fields.mapValues { JsonPrimitive(it.value) })
    override fun toString() = "GeetestProof(redacted)"

    companion object {
        private val names = listOf("captcha_id", "lot_number", "pass_token", "gen_time", "captcha_output")
        private val printable = Regex("[\\x21-\\x7e]{1,8192}")

        fun fromApi(value: JsonObject): GeetestProof {
            val fields = names.associateWith { name ->
                (value[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf(printable::matches)
                    ?: throw LoginFailure("invalid_captcha")
            }
            if (!fields.getValue("captcha_id").equals(XmlyLogin.CaptchaId, true)) throw LoginFailure("invalid_captcha")
            return GeetestProof(fields)
        }

        fun fromBridge(value: JsonObject): GeetestProof {
            if (value.keys != names.toSet() || (value["captcha_id"] as? JsonPrimitive)?.content != XmlyLogin.CaptchaId) {
                throw LoginFailure("invalid_captcha")
            }
            return fromApi(value)
        }
    }
}

package com.xuleo.xmplus.md3.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.MessageDigest

private val TokenPattern = Regex("[1-9][0-9]{0,19}&[A-Za-z0-9._~+/=\\-]{1,8192}")
private val DeviceIdPattern = Regex("[A-Za-z0-9_.:\\-]{1,128}")
private val IdentifierPattern = Regex("(?:xmly:)?([1-9][0-9]{0,18})")

// Dart's `\s`: tab, line breaks, space and the Unicode space separators, including U+FEFF.
private const val Space = "\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF"
private val CookieField = Regex("(?:^|[;:$Space\"'`])1&_(token|device)[$Space]*=[$Space]*([^;$Space\"'`\\\\,]+)")

private fun sha256Hex(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

private fun invalid(): Nothing = throw XmlyException("invalid_credentials", definitive = true)

/** A Ximalaya account login: a `uid&token` pair and the device id it was issued to. */
class Credentials(token: String, deviceId: String) {
    val token: String = token.dartTrim().removePrefix("1&_token=")
    val deviceId: String = deviceId.dartTrim()

    init {
        if (!TokenPattern.matches(this.token) || !DeviceIdPattern.matches(this.deviceId)) invalid()
    }

    val uid: String get() = token.substringBefore('&')

    /** Identifies the account on this device, without exposing the token. */
    val identity: String get() = sha256Hex("$uid|$deviceId")

    /** Identifies the login itself: it changes whenever the token does. */
    val cacheIdentity: String get() = sha256Hex("$token|android&$deviceId&3.4.10")

    /** The Cookie header the Lite app sends. */
    val cookie: String
        get() = "1&_token=$token; 1&_device=android&$deviceId&3.4.10; channel=and-f5; impl=com.ximalaya.ting.lite; appid=1463"

    fun toJson(): JsonObject = buildJsonObject {
        put("token", token)
        put("device_id", deviceId)
    }

    override fun toString(): String = "Credentials(redacted)"

    companion object {
        /** Accepts a captured cookie string, or JSON holding `token` and `device_id` (or a cookie). */
        fun parse(input: String): Credentials {
            val text = input.dartTrim()
            if (!text.startsWith("{")) return parse(JsonObject(mapOf("cookie" to JsonPrimitive(text))))
            val json = try {
                Json.parseToJsonElement(text)
            } catch (_: Exception) {
                invalid()
            }
            return parse(json as? JsonObject ?: invalid())
        }

        fun parse(input: JsonObject): Credentials {
            var map = input
            (map["xmly"] as? JsonObject)?.let { map = it }
            (map["config"] as? JsonObject)?.let { map = it }
            val headers = map["headers"] as? JsonObject
            val cookie = firstPresent(headers?.get("cookie"), headers?.get("Cookie"), map["cookie"]).string()
            var token = map["token"].string(allowNonString = true)
            var device = firstPresent(map["device_id"], map["deviceId"]).string(allowNonString = true)
            if (cookie != null) {
                val parts = mutableMapOf<String, String>()
                for (match in CookieField.findAll(cookie)) {
                    val key = "1&_" + match.groupValues[1]
                    val value = match.groupValues[2]
                    if (parts[key] != null && parts[key] != value) invalid()
                    parts[key] = value
                }
                if (token == null) token = parts["1&_token"]
                val fields = (parts["1&_device"] ?: "").split('&')
                if (fields.size > 1 && device == null) device = fields[1]
            }
            return Credentials(token ?: invalid(), device ?: invalid())
        }
    }
}

private fun firstPresent(vararg values: JsonElement?): JsonElement? =
    values.firstOrNull { it != null && it != JsonNull }

// Dart's ?? accepts absent and JSON-null values, but never coerces credential types.
private fun JsonElement?.string(allowNonString: Boolean = false): String? {
    if (this == null || this == JsonNull) return null
    val primitive = this as? JsonPrimitive ?: return if (allowNonString) invalid() else null
    return if (primitive.isString) primitive.content else if (allowNonString) invalid() else null
}

/** Normalises a Ximalaya id: a positive integer, optionally written `xmly:<id>`. */
fun xmlyIdentifier(value: String): String {
    val match = IdentifierPattern.matchEntire(value) ?: throw XmlyException("invalid_id", definitive = true)
    return match.groupValues[1]
}

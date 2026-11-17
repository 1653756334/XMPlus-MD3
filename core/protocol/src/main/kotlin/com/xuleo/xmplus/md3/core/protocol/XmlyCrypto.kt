package com.xuleo.xmplus.md3.core.protocol

import com.xuleo.xmplus.md3.core.model.Credentials
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.core.model.dartTrim
import com.xuleo.xmplus.md3.core.model.xmlyIdentifier
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.math.floor

private val Base64Text = Regex("[A-Za-z0-9+/_\\-]*={0,2}")
private val PaidVersion = Regex("[0-9]+\\.[0-9]+\\.[0-9]+")
private val PaidFilename = Regex("[A-Za-z0-9][A-Za-z0-9/_.\\-]*\\.(?:m4a|mp3)")
private val PaidSign = Regex("[a-fA-F0-9]{32}")
private val PaidTimestamp = Regex("[0-9]{10,20}")

private const val MaxEncryptBytes = 2 * 1024 * 1024
private const val MaxDecryptChars = 4 * 1024 * 1024

private fun hex(value: String): ByteArray =
    ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

private fun ascii(value: String): ByteArray = value.toByteArray(Charsets.US_ASCII)

private fun md5(data: ByteArray): ByteArray = MessageDigest.getInstance("MD5").digest(data)

private fun lowerHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
internal fun md5Hex(value: String): String = lowerHex(md5(value.toByteArray(Charsets.UTF_8)))

/**
 * Ximalaya Lite protocol constants and primitives, ported rule for rule from XMPlus
 * `lib/api/xmly_crypto.dart` (itself from xmly/client.py, AGPL-3.0-or-later).
 */
object XmlyCrypto {
    /** AES key for play-protocol payloads. Do not modify. */
    val PlayKey: ByteArray = hex("bf56792c145e706d1dd26c04feba6a8c")

    /** AES key for duration payloads. Do not modify. */
    val DurationKey: ByteArray = ascii("jkq1hsksjxg4mf2y")

    private val TicketMaterial = hex("b0b1a8ac34e66efa95c7f4157cf8b6ba33dfc2075e41fb964f2e12dcaa")
    private const val ActivityMaterial = "q35435432sadks2i3546p2ndkcaqiwurhqfebt4kn"

    // Lite EncryptUtil.decryptByKey2 / decryptRc4ByPublicKeyN. Unlike the web protocol, mobile
    // fileId is AES encrypted and seed is not used.
    private val PaidFileKey = hex("6130516e6a54614139596963526a3748")
    private val PaidEpKey = ascii("MZe3GV3txvMOKb1p")

    private fun cipher(mode: Int, key: ByteArray): Cipher =
        Cipher.getInstance("AES/ECB/PKCS5Padding").apply { init(mode, SecretKeySpec(key, "AES")) }

    /**
     * JSON-encodes [value], encrypts it with AES-ECB and returns URL-safe base64 in 76-column lines.
     * Keep values to strings and integers: Kotlin prints large or small doubles differently from Dart.
     */
    fun encrypt(value: JsonObject, key: ByteArray): String {
        val raw = utf8Bytes(Json.encodeToString(JsonObject.serializer(), value))
        if (raw.size > MaxEncryptBytes) throw XmlyException("invalid_encoding")
        val encoded = Base64.getUrlEncoder().encodeToString(cipher(Cipher.ENCRYPT_MODE, key).doFinal(raw))
        return buildString {
            for (start in encoded.indices step 76) {
                append(encoded, start, minOf(start + 76, encoded.length)).append('\n')
            }
        }
    }

    fun decrypt(value: String, key: ByteArray): String = try {
        decryptOrThrow(value, key)
    } catch (_: Exception) {
        throw XmlyException("invalid_encoding")
    }

    private fun decryptOrThrow(value: String, key: ByteArray): String {
        require(value.length <= MaxDecryptChars) { "too long" }
        val text = value.replace("\r", "").replace("\n", "")
        require(Base64Text.matches(text)) { "characters" }
        val bytes = decodeBase64Dart(text)
        require(bytes.isNotEmpty() && bytes.size % 16 == 0) { "block size" }
        return decodeUtf8Strict(cipher(Cipher.DECRYPT_MODE, key).doFinal(bytes))
    }

    /**
     * Rebuilds the official mobile paid-media URL. [uid] is the listening account, not the
     * uploader uid in the paid metadata response.
     */
    fun paidMediaUrl(metadata: JsonObject, uid: String): String = try {
        paidMediaUrlOrThrow(metadata, uid)
    } catch (_: Exception) {
        throw XmlyException("invalid_paid_media")
    }

    private fun string(metadata: JsonObject, name: String): String {
        val element = metadata[name]
        require(element is JsonPrimitive && element.isString) { name }
        return element.content
    }

    private fun paidMediaUrlOrThrow(metadata: JsonObject, uid: String): String {
        val fileId = string(metadata, "fileId")
        val ep = string(metadata, "ep")
        val domain = string(metadata, "domain")
        val version = string(metadata, "apiVersion")
        val durationElement = metadata["duration"]
        require(durationElement is JsonPrimitive && !durationElement.isString) { "duration" }
        val duration = durationElement.content.toDoubleOrNull()
        require(
            fileId.length <= 16384 && ep.length <= 16384 && domain.length <= 1024 &&
                PaidVersion.matches(version) &&
                duration != null && duration.isFinite() && duration > 0 && duration <= 2147483647.0 &&
                duration == floor(duration),
        ) { "metadata" }
        val origin = parseOrigin(domain)

        val filename = decrypt(fileId, PaidFileKey).dartTrim()
        require(PaidFilename.matches(filename)) { "filename" }
        require(filename.split('/').none { it.isEmpty() || it == "." || it == ".." }) { "filename" }

        require(Base64Text.matches(ep)) { "ep" }
        val bytes = decodeBase64Dart(ep)
        require(bytes.isNotEmpty()) { "ep" }
        rc4(PaidEpKey, bytes)
        val parts = decodeUtf8Strict(bytes).dartTrim().split('-')
        require(parts.size == 4 && parts.none { it.isEmpty() } && PaidSign.matches(parts[1]) && PaidTimestamp.matches(parts[3])) { "ep" }

        val query = listOf(
            "buy_key" to parts[0],
            "duration" to duration.toLong().toString(),
            "sign" to parts[1],
            "timestamp" to parts[3],
            "token" to parts[2],
            "uid" to xmlyIdentifier(uid),
            "is_charge" to "true",
        ).joinToString("&") { (name, text) -> encodeQueryComponent(name) + "=" + encodeQueryComponent(text) }
        return "$origin/download/$version/$filename?$query"
    }

    // In-place RC4 (key schedule, then XOR with the keystream).
    private fun rc4(key: ByteArray, data: ByteArray) {
        val state = IntArray(256) { it }
        var j = 0
        for (i in 0 until 256) {
            j = (j + state[i] + (key[i % key.size].toInt() and 0xFF)) and 255
            state[i] = state[j].also { state[j] = state[i] }
        }
        var i = 0
        j = 0
        for (n in data.indices) {
            i = (i + 1) and 255
            j = (j + state[i]) and 255
            state[i] = state[j].also { state[j] = state[i] }
            data[n] = (data[n].toInt() xor state[(state[i] + state[j]) and 255]).toByte()
        }
    }

    /** MD5 over the sorted `key=value&` pairs plus the activity secret, lower-cased byte-wise. */
    fun activitySignature(params: Map<String, String>): String {
        val canonical = params.keys.sorted().joinToString("") { "$it=${params.getValue(it)}&" } + ActivityMaterial
        val bytes = utf8Bytes(canonical)
        for (index in bytes.indices) {
            if (bytes[index] in 65..90) bytes[index] = (bytes[index] + 32).toByte()
        }
        return lowerHex(md5(bytes))
    }

    /**
     * Builds a fresh `TAC` play ticket. The time and the UUID are part of the ticket, so two calls
     * never agree; [nowMillis] and [random] exist for callers that need to control them.
     */
    fun freshTicket(
        credentials: Credentials,
        attributes: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
        random: SecureRandom = SecureRandom(),
    ): String {
        val identity = md5(utf8Bytes("${credentials.uid}|${credentials.deviceId}"))
        val uuid = ByteArray(16).also { random.nextBytes(it) }
        uuid[6] = ((uuid[6].toInt() and 15) or 64).toByte()
        uuid[8] = ((uuid[8].toInt() and 63) or 128).toByte()
        val seconds = nowMillis / 1000
        val payload = ByteArray(36)
        payload[0] = (seconds shr 24).toByte()
        payload[1] = (seconds shr 16).toByte()
        payload[2] = (seconds shr 8).toByte()
        payload[3] = seconds.toByte()
        identity.copyInto(payload, 4)
        for (i in 0 until 16) payload[20 + i] = (uuid[i].toInt() xor identity[i].toInt()).toByte()
        val suffix = utf8Bytes(
            "com.ximalaya.ting.lite!1.3.24!3.4.10.3!" +
                (attributes ?: "b=lite&s=lite_track_play_url&&u=${credentials.uid}"),
        )
        val digest = MessageDigest.getInstance("SHA-256").run {
            update(payload)
            update(ascii("TAC"))
            update(TicketMaterial)
            update(suffix)
            digest()
        }
        return "TAC" + Base64.getUrlEncoder().withoutPadding().encodeToString(payload + digest + suffix)
    }
}
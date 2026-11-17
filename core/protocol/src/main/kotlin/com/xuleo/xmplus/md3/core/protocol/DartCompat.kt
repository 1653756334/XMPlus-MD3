package com.xuleo.xmplus.md3.core.protocol

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64

/** Strict UTF-8 decoding with Dart's habit of dropping one leading byte-order mark. */
internal fun decodeUtf8Strict(bytes: ByteArray): String {
    val text = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
    return if (text.startsWith('\uFEFF')) text.substring(1) else text
}

// Lone surrogates become U+FFFD, as in Dart; String.toByteArray would write '?'.
internal fun utf8Bytes(text: String): ByteArray {
    val encoder = Charsets.UTF_8.newEncoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
        .replaceWith(byteArrayOf(0xEF.toByte(), 0xBF.toByte(), 0xBD.toByte()))
    val buffer = encoder.encode(java.nio.CharBuffer.wrap(text))
    return ByteArray(buffer.remaining()).also { buffer.get(it) }
}

private const val Base64Alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

/**
 * Dart's `base64.decode(base64.normalize(text))`: either alphabet, padding optional but exact when
 * present, and the unused bits of the last character must be zero. Callers check the character set.
 */
internal fun decodeBase64Dart(text: String): ByteArray {
    val body = text.trimEnd('=')
    val padding = text.length - body.length
    val remainder = body.length % 4
    require(remainder != 1) { "invalid length" }
    val expectedPadding = (4 - remainder) % 4
    require(padding == 0 || padding == expectedPadding) { "invalid padding" }
    val standard = body.replace('-', '+').replace('_', '/')
    if (remainder != 0) {
        val unusedBits = if (remainder == 2) 4 else 2
        val last = Base64Alphabet.indexOf(standard.last())
        require(last and ((1 shl unusedBits) - 1) == 0) { "non-zero trailing bits" }
    }
    return Base64.getDecoder().decode(standard)
}

private val UnreservedQuery = BooleanArray(128).also { table ->
    for (c in 'A'..'Z') table[c.code] = true
    for (c in 'a'..'z') table[c.code] = true
    for (c in '0'..'9') table[c.code] = true
    for (c in "-._~") table[c.code] = true
}

private const val HexDigits = "0123456789ABCDEF"

// Dart's Uri.encodeQueryComponent: unreserved characters stay, a space becomes '+', everything
// else is written as percent-encoded UTF-8.
internal fun encodeQueryComponent(text: String): String {
    val out = StringBuilder()
    for (byte in utf8Bytes(text)) {
        val b = byte.toInt() and 0xFF
        when {
            b < 128 && UnreservedQuery[b] -> out.append(b.toChar())
            b == 0x20 -> out.append('+')
            else -> out.append('%').append(HexDigits[b shr 4]).append(HexDigits[b and 15])
        }
    }
    return out.toString()
}
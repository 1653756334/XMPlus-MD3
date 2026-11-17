package com.xuleo.xmplus.md3.core.protocol

private val SchemePattern = Regex("([A-Za-z][A-Za-z0-9+.\\-]*):(.*)", RegexOption.DOT_MATCHES_ALL)
private const val HexDigits = "0123456789ABCDEF"

/** An `http(s)://host[:port]` origin, normalised the way Dart's [Uri] writes it back out. */
internal class Origin(val scheme: String, val host: String, val port: Int?) {
    override fun toString(): String = buildString {
        append(scheme).append("://").append(host)
        if (port != null) append(':').append(port)
    }
}

private fun isRegNameChar(c: Char): Boolean =
    c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c in "-._~!$&'()*+,;="

private fun normalizeHost(raw: String): String {
    val out = StringBuilder()
    var i = 0
    while (i < raw.length) {
        val c = raw[i]
        if (c == '%') {
            require(i + 3 <= raw.length) { "bad escape" }
            val hex = raw.substring(i + 1, i + 3)
            require(hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) { "bad escape" }
            val value = hex.toInt(16)
            val decoded = value.toChar()
            if (value < 128 && isRegNameChar(decoded)) {
                out.append(decoded.lowercaseChar())
            } else {
                out.append('%').append(HexDigits[value shr 4]).append(HexDigits[value and 15])
            }
            i += 3
        } else if (isRegNameChar(c)) {
            out.append(c.lowercaseChar())
            i++
        } else {
            val codePoint = raw.codePointAt(i)
            val width = Character.charCount(codePoint)
            for (byte in utf8Bytes(raw.substring(i, i + width))) {
                val b = byte.toInt() and 0xFF
                out.append('%').append(HexDigits[b shr 4]).append(HexDigits[b and 15])
            }
            i += width
        }
    }
    return out.toString()
}

/**
 * Parses and validates a paid-media origin. Only http and https with port 80 or 443 and no
 * userinfo, path, query or fragment are accepted; everything else throws.
 */
internal fun parseOrigin(domain: String): Origin {
    val match = SchemePattern.matchEntire(domain) ?: throw IllegalArgumentException("no scheme")
    val scheme = match.groupValues[1].lowercase()
    require(scheme == "http" || scheme == "https") { "scheme" }
    val rest = match.groupValues[2]
    require(rest.startsWith("//")) { "no authority" }
    var end = 2
    while (end < rest.length && rest[end] != '/' && rest[end] != '?' && rest[end] != '#') end++
    val authority = rest.substring(2, end)
    val tail = rest.substring(end)
    require(tail.isEmpty() || tail == "/") { "path, query or fragment" }

    var hostPort = authority
    val at = authority.lastIndexOf('@')
    if (at >= 0) {
        require(at == 0) { "userinfo" }
        hostPort = authority.substring(1)
    }

    val host: String
    val portText: String?
    if (hostPort.startsWith("[")) {
        val close = hostPort.indexOf(']')
        require(close > 0) { "ipv6" }
        val literal = hostPort.substring(1, close)
        require(literal.contains(':') && literal.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' }) { "ipv6" }
        val after = hostPort.substring(close + 1)
        require(after.isEmpty() || after.startsWith(":")) { "ipv6 port" }
        host = "[" + literal.lowercase() + "]"
        portText = if (after.isEmpty()) null else after.substring(1)
    } else {
        val colon = hostPort.lastIndexOf(':')
        val hostText = if (colon >= 0) hostPort.substring(0, colon) else hostPort
        require(!hostText.contains(':')) { "host" }
        host = normalizeHost(hostText)
        portText = if (colon >= 0) hostPort.substring(colon + 1) else null
    }
    require(host.isNotEmpty()) { "empty host" }

    var port: Int? = null
    if (!portText.isNullOrEmpty()) {
        require(portText.all { it in '0'..'9' }) { "port" }
        val digits = portText.trimStart('0')
        require(digits.length <= 5) { "port" }
        val value = if (digits.isEmpty()) 0 else digits.toInt()
        require(value == 80 || value == 443) { "port" }
        port = value
    }
    val defaultPort = if (scheme == "https") 443 else 80
    return Origin(scheme, host, port.takeIf { it != defaultPort })
}
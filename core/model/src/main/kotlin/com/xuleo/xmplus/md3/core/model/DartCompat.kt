package com.xuleo.xmplus.md3.core.model

// Whitespace as Dart's String.trim() sees it, which differs from Kotlin's Char.isWhitespace
// (it includes U+FEFF and U+0085 and leaves out U+200B).
private fun isDartWhitespace(c: Char): Boolean = when (c.code) {
    in 0x09..0x0D, 0x20, 0x85, 0xA0, 0x1680, in 0x2000..0x200A, 0x2028, 0x2029, 0x202F, 0x205F, 0x3000, 0xFEFF -> true
    else -> false
}

fun String.dartTrim(): String {
    var start = 0
    var end = length
    while (start < end && isDartWhitespace(this[start])) start++
    while (end > start && isDartWhitespace(this[end - 1])) end--
    return substring(start, end)
}
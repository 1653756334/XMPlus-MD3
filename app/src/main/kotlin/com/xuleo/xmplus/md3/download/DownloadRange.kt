package com.xuleo.xmplus.md3.download

import com.xuleo.xmplus.md3.core.model.XmlyException

/** Episode numbers typed as numbers and ranges, such as `1,3,5-8` (DL-01 to DL-03). */
internal object DownloadRange {
    private const val MaxNumber = 5_000
    private const val MaxLength = 40_000
    private const val FullWidthComma = '\uFF0C'

    /** The numbers in [text], sorted and without repeats. Anything that is not a number or a range fails with `range_invalid`. */
    fun parse(text: String): List<Int> {
        if (text.length > MaxLength) invalid()
        val numbers = sortedSetOf<Int>()
        for (part in text.split(',', FullWidthComma).map(String::trim)) {
            val bounds = part.split('-').map(String::trim)
            val first = number(bounds.first())
            val last = when (bounds.size) {
                1 -> first
                2 -> number(bounds[1])
                else -> invalid()
            }
            if (first > last) invalid()
            for (number in first..last) numbers += number
        }
        return numbers.toList()
    }

    private fun number(text: String): Int {
        if (text.isEmpty() || text.length > 9 || text.any { it !in '0'..'9' }) invalid()
        return text.toInt().takeIf { it in 1..MaxNumber } ?: invalid()
    }

    private fun invalid(): Nothing = throw XmlyException("range_invalid", definitive = true)
}

package com.xuleo.xmplus.md3.core.model

/** The longest intro or outro that can be skipped, in seconds. */
const val MaxSkipSeconds = 3600

/** Seconds skipped at the start and at the end of every episode of one album. 0 means off. */
data class AlbumSkip(val introSeconds: Int = 0, val outroSeconds: Int = 0) {
    val isOff: Boolean get() = introSeconds == 0 && outroSeconds == 0

    fun coerced() = AlbumSkip(introSeconds.coerceIn(0, MaxSkipSeconds), outroSeconds.coerceIn(0, MaxSkipSeconds))
}
package com.xuleo.xmplus.md3.ui.components

import java.util.Locale

/** 75 seconds is 1:15, 3,725 is 1:02:05. */
fun formatDuration(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0)
    val hours = total / 3600
    val minutes = total % 3600 / 60
    val rest = total % 60
    return if (hours > 0) String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest)
    else String.format(Locale.ROOT, "%d:%02d", minutes, rest)
}
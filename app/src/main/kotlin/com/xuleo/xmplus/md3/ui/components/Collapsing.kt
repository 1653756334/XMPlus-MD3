package com.xuleo.xmplus.md3.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

/**
 * Chrome above a list that scrolls away with it. Scrolling takes the chrome first, so it shrinks before the content moves,
 * and scrolling back brings it back. Whatever sits below the chrome stays put. When fully hidden the chrome still keeps
 * [minHeight] pixels, so content below it starts clear of something drawn over the top.
 */
class CollapsingChrome(private val minHeight: Float = 0f) {
    /** The chrome natural height in pixels, measured on screen. */
    var height by mutableFloatStateOf(0f)

    /** 0 when fully shown, down to minus the travel when fully hidden. */
    var offset by mutableFloatStateOf(0f)
        private set

    /** How far the chrome can move: its height less what it keeps. */
    private val travel: Float get() = (height - minHeight).coerceAtLeast(0f)

    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val target = (offset + available.y).coerceIn(-travel, 0f)
            val consumed = target - offset
            offset = target
            return Offset(0f, consumed)
        }
    }

    /** The shift the content takes for a chrome measured at [measured] pixels. */
    fun shiftFor(measured: Float): Int = offset.coerceIn(-(measured - minHeight).coerceAtLeast(0f), 0f).roundToInt()

    fun expand() {
        offset = 0f
    }
}

/** Shows the part of the content that the chrome still leaves, so the layout below moves up with it. */
fun Modifier.collapsing(chrome: CollapsingChrome): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = chrome.shiftFor(placeable.height.toFloat())
    layout(placeable.width, placeable.height + shift) { placeable.place(0, shift) }
}
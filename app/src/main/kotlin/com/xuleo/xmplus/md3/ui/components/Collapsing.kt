package com.xuleo.xmplus.md3.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
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
 * Chrome above a list that scrolls away with it. Scrolling down takes the chrome first, so it shrinks before the content
 * moves. Scrolling back up brings it back: at once, or, with [expandOnlyAtTop], only once the content has nothing left to
 * scroll, so the chrome stays put while the list moves through its own rows. [minHeight] is what the chrome keeps showing
 * when fully hidden, so content below it starts clear of something drawn over the top.
 */
class CollapsingChrome(
    private val minHeight: Float = 0f,
    private val expandOnlyAtTop: Boolean = false,
) {
    /** The chrome natural height in pixels, measured on screen. */
    var height by mutableFloatStateOf(0f)

    /** 0 when fully shown, down to minus the travel when fully hidden. */
    var offset by mutableFloatStateOf(0f)
        private set

    /** How far the chrome can move: its height less what it keeps. */
    private val travel: Float get() = (height - minHeight).coerceAtLeast(0f)

    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // Upward scroll goes to the content first when it can still move, so the chrome only hears what is left over.
            if (expandOnlyAtTop && available.y > 0f) return Offset.Zero
            return move(available.y)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (!expandOnlyAtTop || available.y <= 0f) return Offset.Zero
            return move(available.y)
        }
    }

    private fun move(delta: Float): Offset {
        val target = (offset + delta).coerceIn(-travel, 0f)
        val consumed = target - offset
        offset = target
        return Offset(0f, consumed)
    }

    /** The shift the content takes for a chrome measured at [measured] pixels. */
    fun shiftFor(measured: Float): Int = offset.coerceIn(-(measured - minHeight).coerceAtLeast(0f), 0f).roundToInt()

    fun expand() {
        offset = 0f
    }

    /** Brings the chrome back over time, for an action such as scroll to top. */
    suspend fun animateExpand(spec: AnimationSpec<Float>) {
        animate(offset, 0f, animationSpec = spec) { value, _ -> offset = value }
    }
}

/** Shows the part of the content that the chrome still leaves, so the layout below moves up with it. */
fun Modifier.collapsing(chrome: CollapsingChrome): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = chrome.shiftFor(placeable.height.toFloat())
    layout(placeable.width, placeable.height + shift) { placeable.place(0, shift) }
}
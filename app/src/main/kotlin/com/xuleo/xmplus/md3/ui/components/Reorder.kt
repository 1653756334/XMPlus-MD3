package com.xuleo.xmplus.md3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.ui.icons.XmIcons

/**
 * Drag to reorder rows of a lazy list by their handle, as the music queue does: while a row is dragged the list shows
 * its own order, and [onMove] runs once, when the finger lifts. [keyOf] must give each row's lazy item key.
 */
@Stable
class ReorderState<T> internal constructor(
    private val list: LazyListState,
    private val keyOf: (T) -> Any,
    private val onMove: (from: Int, to: Int) -> Unit,
) {
    private var source: List<T> = emptyList()
    private var order by mutableStateOf<List<T>?>(null)
    private var from = -1
    private var draggedKey by mutableStateOf<Any?>(null)
    internal var offset by mutableFloatStateOf(0f)
        private set

    /** The rows to show: [current], or the dragged order while a row moves. */
    fun items(current: List<T>): List<T> {
        source = current
        return order ?: current
    }

    fun isDragging(item: T): Boolean = draggedKey != null && draggedKey == keyOf(item)

    internal fun start(item: T) {
        order = source
        draggedKey = keyOf(item)
        from = source.indexOfFirst { keyOf(it) == draggedKey }
        offset = 0f
    }

    /** Returns true when the row passed another one. */
    internal fun drag(delta: Float): Boolean {
        val key = draggedKey ?: return false
        val current = order ?: return false
        offset += delta
        val info = list.layoutInfo.visibleItemsInfo
        val self = info.firstOrNull { it.key == key } ?: return false
        val centre = (self.offset + self.size / 2 + offset).toInt()
        val target = info.firstOrNull { other ->
            other.key != key && centre in other.offset..(other.offset + other.size) &&
                (if (offset > 0) other.index > self.index else other.index < self.index)
        } ?: return false
        val to = current.indexOfFirst { keyOf(it) == target.key }
        val at = current.indexOfFirst { keyOf(it) == key }
        if (to < 0 || at < 0) return false
        order = current.toMutableList().apply { add(to, removeAt(at)) }
        // The row now sits where the other one was, so the finger's distance is measured from there.
        offset -= (target.offset - self.offset)
        return true
    }

    internal fun end() {
        val key = draggedKey
        val final = order
        if (key != null && final != null) {
            val to = final.indexOfFirst { keyOf(it) == key }
            if (from >= 0 && to >= 0 && to != from) onMove(from, to)
        }
        draggedKey = null
        from = -1
        offset = 0f
        order = null
    }
}

@Composable
fun <T> rememberReorderState(list: LazyListState, keyOf: (T) -> Any, onMove: (from: Int, to: Int) -> Unit): ReorderState<T> {
    val key by rememberUpdatedState(keyOf)
    val move by rememberUpdatedState(onMove)
    return remember(list) { ReorderState(list, { key(it) }, { a, b -> move(a, b) }) }
}

/** The row's place: lifted and following the finger while dragged, otherwise sliding to its new place. */
@Composable
fun <T> Modifier.reorderItem(scope: LazyItemScope, state: ReorderState<T>, item: T, animated: Boolean): Modifier = this then when {
    state.isDragging(item) -> Modifier.zIndex(1f)
        .graphicsLayer {
            translationY = state.offset
            shadowElevation = 6.dp.toPx()
        }
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    animated -> with(scope) { Modifier.animateItem() }
    else -> Modifier
}

/** The drag handle at a row's end. */
@Composable
fun <T> ReorderHandle(state: ReorderState<T>, item: T, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(item)
    Box(
        modifier.size(48.dp).pointerInput(state) {
            detectVerticalDragGestures(
                onDragStart = {
                    state.start(current)
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onDragEnd = state::end,
                onDragCancel = state::end,
            ) { change, delta ->
                change.consume()
                if (state.drag(delta)) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Icon(XmIcons.Outlined.DragHandle, stringResource(R.string.music_queue_reorder), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

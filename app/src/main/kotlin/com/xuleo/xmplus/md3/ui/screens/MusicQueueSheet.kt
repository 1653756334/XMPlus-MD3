@file:OptIn(ExperimentalMaterial3Api::class)

package com.xuleo.xmplus.md3.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.media.MusicPlayback
import com.xuleo.xmplus.md3.core.model.MusicItem
import com.xuleo.xmplus.md3.ui.components.AddToSheetSheet
import com.xuleo.xmplus.md3.ui.components.PlayingBars
import com.xuleo.xmplus.md3.ui.components.PlayingMarkSize
import com.xuleo.xmplus.md3.ui.components.XmMenu
import com.xuleo.xmplus.md3.ui.components.XmMenuItem
import com.xuleo.xmplus.md3.ui.components.XmSwap
import com.xuleo.xmplus.md3.ui.icons.XmIcons
import kotlinx.coroutines.launch

/**
 * The music queue: the play mode and count at the top with 清空; each row plays on tap, moves by its handle and has
 * a remove button. The current song is in the accent colour with the equalizer, and the list opens at it with one row
 * above. The sheet opens fully, so 回到当前 always lands on a row that is in view.
 */
@Composable
internal fun MusicQueueSheet(music: MusicPlayback, onClear: () -> Unit, onDismiss: () -> Unit) {
    val queue by music.queue.collectAsStateWithLifecycle()
    val animated = rememberAnimationsEnabled()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val list = rememberLazyListState(
        initialFirstVisibleItemIndex = (queue.items.indexOfFirst { it.key == queue.currentKey } - 1).coerceAtLeast(0),
    )
    var confirmClear by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // While a row is dragged the sheet shows its own order; the queue changes once, when the finger lifts.
    var order by remember { mutableStateOf<List<MusicItem>?>(null) }
    var draggedKey by remember { mutableStateOf<String?>(null) }
    var dragFrom by remember { mutableStateOf(-1) }
    var offset by remember { mutableFloatStateOf(0f) }
    val items = order ?: queue.items
    val currentIndex = items.indexOfFirst { it.key == queue.currentKey }
    fun locate() {
        val index = queue.items.indexOfFirst { it.key == queue.currentKey }
        if (index < 0) return
        val target = (index - 1).coerceAtLeast(0)
        scope.launch { if (animated) list.animateScrollToItem(target) else list.scrollToItem(target) }
    }
    fun dragBy(delta: Float) {
        val key = draggedKey ?: return
        val current = order ?: return
        offset += delta
        val info = list.layoutInfo.visibleItemsInfo
        val self = info.firstOrNull { it.key == key } ?: return
        val index = current.indexOfFirst { it.key == key }
        val centre = self.offset + self.size / 2 + offset
        val target = info.firstOrNull { other ->
            other.key != key && centre.toInt() in other.offset..(other.offset + other.size) &&
                (if (offset > 0) other.index > self.index else other.index < self.index)
        } ?: return
        val to = target.index
        if (index < 0 || to !in current.indices) return
        order = current.toMutableList().apply { add(to, removeAt(index)) }
        // The row now sits where the other one was, so the finger's distance is measured from there.
        offset -= (target.offset - self.offset)
    }
    fun dragEnd() {
        val key = draggedKey
        val final = order
        if (key != null && final != null) {
            val to = final.indexOfFirst { it.key == key }
            if (dragFrom >= 0 && to >= 0 && to != dragFrom) music.move(dragFrom, to)
        }
        draggedKey = null
        dragFrom = -1
        offset = 0f
        order = null
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentWindowInsets = { WindowInsets.safeDrawing },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.clip(MaterialTheme.shapes.large).clickable(role = Role.Button, onClick = music::toggleMode)
                        .heightIn(min = 48.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    XmSwap(queue.mode) { mode -> Icon(mode.icon, contentDescription = null) }
                    Text(stringResource(queue.mode.label), style = MaterialTheme.typography.titleMedium)
                    Text(
                        pluralStringResource(R.plurals.music_song_count, queue.items.size, queue.items.size),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(Modifier.weight(1f))
                IconButton(onClick = ::locate, enabled = currentIndex >= 0) {
                    Icon(XmIcons.Outlined.MyLocation, stringResource(R.string.player_queue_locate))
                }
                TextButton(onClick = { confirmClear = true }, enabled = queue.items.isNotEmpty()) {
                    Text(stringResource(R.string.music_queue_clear))
                }
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp), state = list) {
                itemsIndexed(items, key = { _, song -> song.key }) { index, song ->
                    val dragging = song.key == draggedKey
                    val rowModifier = if (dragging) {
                        Modifier.zIndex(1f).graphicsLayer { translationY = offset }
                    } else if (animated) {
                        Modifier.animateItem()
                    } else {
                        Modifier
                    }
                    QueueRow(
                        song = song,
                        current = index == currentIndex,
                        dragging = dragging,
                        onClick = { music.playAt(song.key) },
                        onRemove = { music.remove(song.key) },
                        handle = Modifier.pointerInput(song.key) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    order = queue.items
                                    draggedKey = song.key
                                    dragFrom = queue.items.indexOfFirst { it.key == song.key }
                                    offset = 0f
                                },
                                onDragEnd = ::dragEnd,
                                onDragCancel = ::dragEnd,
                            ) { change, delta ->
                                change.consume()
                                dragBy(delta)
                            }
                        },
                        modifier = rowModifier,
                    )
                }
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text(stringResource(R.string.music_queue_clear_title)) },
        confirmButton = {
            TextButton(onClick = { confirmClear = false; onClear() }) { Text(stringResource(R.string.music_queue_clear)) }
        },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun QueueRow(
    song: MusicItem,
    current: Boolean,
    dragging: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    handle: Modifier,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    Box {
    Surface(
        modifier.fillMaxWidth(),
        color = if (dragging) scheme.surfaceContainerHighest else scheme.surfaceContainerLow,
        shadowElevation = if (dragging) 4.dp else 0.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { menu = true }).heightIn(min = 56.dp)
                .padding(start = XmLayout.PagePadding, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (current) {
                PlayingBars(Modifier.padding(end = 12.dp).size(PlayingMarkSize), scheme.primary)
            }
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    song.title, style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (current) FontWeight.SemiBold else null,
                    color = if (current) scheme.primary else scheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (song.artist.isNotBlank()) {
                    Text(
                        song.artist, style = MaterialTheme.typography.bodySmall,
                        color = if (current) scheme.primary else scheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(XmIcons.Outlined.Close, stringResource(R.string.music_queue_remove), tint = scheme.onSurfaceVariant)
            }
            Box(handle.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(XmIcons.Outlined.DragHandle, stringResource(R.string.music_queue_reorder), tint = scheme.onSurfaceVariant)
            }
        }
    }
    // A long press keeps the row's own buttons clear: the menu holds 加入歌单 only.
    XmMenu(menu, { menu = false }) {
        XmMenuItem(
            stringResource(R.string.music_add_to_sheet), { menu = false; adding = true }, 0, 1,
            icon = XmIcons.Outlined.PlaylistAdd,
        )
    }
    }
    if (adding) AddToSheetSheet(listOf(song), onDismiss = { adding = false })
}

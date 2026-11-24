package com.xuleo.xmplus.md3.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.ui.icons.XmIcons
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.media.PlaybackQueueState
import com.xuleo.xmplus.md3.ui.components.LoadMoreEffect
import com.xuleo.xmplus.md3.ui.components.formatDuration
import kotlinx.coroutines.launch

/**
 * The album's episodes around the one playing. Scrolling toward either end loads the album page beyond it, so the list
 * runs from the first episode to the last; a page added above keeps the rows in view where they were.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerQueueSheet(
    queue: PlaybackQueueState,
    onSelect: (String) -> Unit,
    onCheckNew: () -> Unit,
    onLoadMore: (forward: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val current = queue.items.indexOfFirst { it.id == queue.currentTrackId }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = current.coerceAtLeast(0))
    val scope = rememberCoroutineScope()
    val sheet = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val latest by rememberUpdatedState(queue)
    val pagingError = queue.pagingErrorCode
    // Later pages: as the end comes into view. A failed page waits for its retry rather than asking again.
    LoadMoreEffect(
        { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index }, { list.layoutInfo.totalItemsCount },
        queue.items.size, queue.reachedEnd, pagingError,
    ) {
        if (!latest.reachedEnd && latest.pagingErrorCode == null) onLoadMore(true)
    }
    // Earlier pages: as the top comes into view.
    val nearTop by remember { derivedStateOf { list.firstVisibleItemIndex <= EarlierPageThreshold } }
    LaunchedEffect(nearTop, queue.hasEarlier, queue.items.size, pagingError) {
        if (nearTop && queue.hasEarlier && pagingError == null) onLoadMore(false)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentWindowInsets = { WindowInsets.safeDrawing },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(start = XmLayout.PagePadding, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.player_queue),
                    Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = { scope.launch { list.scrollToItem(current) } }, enabled = current >= 0) {
                    Icon(XmIcons.Outlined.MyLocation, stringResource(R.string.player_queue_locate))
                }
            }
            if (queue.isLoading || queue.pagingForward == false) LinearProgressIndicator(Modifier.fillMaxWidth())
            queue.errorCode?.let { code -> QueueError(code, onRetry = null) }
            if (pagingError != null && !queue.pagingErrorForward) QueueError(pagingError) { onLoadMore(false) }
            LazyColumn(
                Modifier.fillMaxWidth().fillMaxHeight(0.6f), state = list,
                contentPadding = PaddingValues(bottom = XmLayout.ItemGap),
            ) {
                items(queue.items, key = { it.id }) { track ->
                    val isCurrent = track.id == queue.currentTrackId
                    val label = stringResource(R.string.player_queue_current)
                    ListItem(
                        onClick = { onSelect(track.id) },
                        modifier = Modifier.fillMaxWidth().semantics { if (isCurrent) stateDescription = label },
                        leadingContent = {
                            if (isCurrent) Icon(XmIcons.Filled.Equalizer, null, tint = MaterialTheme.colorScheme.primary)
                            else Text(track.officialPosition?.toString().orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingContent = if (track.durationSeconds > 0) ({
                            Text(formatDuration(track.durationSeconds), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }) else null,
                        colors = ListItemDefaults.colors(
                            containerColor = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        ),
                    ) {
                        Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                when {
                    queue.pagingForward == true -> item(key = "paging") {
                        Box(Modifier.fillMaxWidth().padding(XmLayout.PagePadding), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                    pagingError != null && queue.pagingErrorForward -> item(key = "paging-error") {
                        QueueError(pagingError) { onLoadMore(true) }
                    }
                    queue.reachedEnd -> item(key = "check-new") {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = XmLayout.PagePadding),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = onCheckNew, enabled = !queue.isLoading) {
                                Icon(XmIcons.Outlined.Refresh, contentDescription = null, Modifier.size(18.dp))
                                Text(stringResource(R.string.player_queue_check), Modifier.padding(start = 8.dp))
                            }
                            queue.addedCount?.let { added ->
                                Text(
                                    if (added > 0) stringResource(R.string.player_queue_check_added, added) else stringResource(R.string.player_queue_check_none),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A failed read in one line, with a retry icon when there is something to retry. */
@Composable
private fun QueueError(code: String, onRetry: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(start = XmLayout.PagePadding, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(playbackMessage(code)), Modifier.weight(1f).padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
        )
        if (onRetry != null) IconButton(onClick = onRetry) { Icon(XmIcons.Outlined.Refresh, stringResource(R.string.action_retry)) }
    }
}

/** How close to the top, in rows, the list asks for the page before it. */
private const val EarlierPageThreshold = 3

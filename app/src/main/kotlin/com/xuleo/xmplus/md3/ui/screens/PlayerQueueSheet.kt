package com.xuleo.xmplus.md3.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.media.PlaybackQueueState
import com.xuleo.xmplus.md3.ui.components.formatDuration
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerQueueSheet(
    queue: PlaybackQueueState,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val current = queue.items.indexOfFirst { it.id == queue.currentTrackId }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = current.coerceAtLeast(0))
    val scope = rememberCoroutineScope()
    val sheet = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentWindowInsets = { WindowInsets.safeDrawing },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = XmLayout.PagePadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.player_queue),
                    Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (current >= 0) TextButton(onClick = { scope.launch { list.scrollToItem(current) } }) {
                    Text(stringResource(R.string.player_queue_locate))
                }
            }
            if (queue.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            queue.errorCode?.let { code ->
                Text(
                    stringResource(playbackMessage(code)),
                    Modifier.padding(horizontal = XmLayout.PagePadding, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
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
                            if (isCurrent) Icon(Icons.Filled.Equalizer, null, tint = MaterialTheme.colorScheme.primary)
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
            }
        }
    }
}

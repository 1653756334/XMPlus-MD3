package com.xuleo.xmplus.md3.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.media.BookmarkDraft
import com.xuleo.xmplus.md3.core.media.BookmarkEpisode
import com.xuleo.xmplus.md3.core.media.BookmarkNoteLimit
import com.xuleo.xmplus.md3.core.media.PlaybackBookmark
import com.xuleo.xmplus.md3.core.media.PlaybackController
import com.xuleo.xmplus.md3.core.media.isBookmarkNoteValid
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.ui.components.formatDuration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private sealed interface BookmarkEditor {
    val positionMs: Long

    data class Add(val draft: BookmarkDraft) : BookmarkEditor {
        override val positionMs get() = draft.positionMs
    }

    data class Edit(val bookmark: PlaybackBookmark) : BookmarkEditor {
        override val positionMs get() = bookmark.positionMs
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerBookmarksSheet(
    playback: PlaybackController,
    episode: BookmarkEpisode,
    canSeek: Boolean,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheet = rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    var bookmarks by remember(episode) { mutableStateOf<List<PlaybackBookmark>?>(null) }
    var loadFailed by remember(episode) { mutableStateOf(false) }
    var reload by remember(episode) { mutableIntStateOf(0) }
    var editor by remember(episode) { mutableStateOf<BookmarkEditor?>(null) }
    var deleting by remember(episode) { mutableStateOf<PlaybackBookmark?>(null) }
    var busy by remember(episode) { mutableStateOf(false) }
    var actionFailed by remember(episode) { mutableStateOf(false) }

    LaunchedEffect(episode, reload) {
        loadFailed = false
        try {
            bookmarks = playback.listBookmarks(episode)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            loadFailed = true
        }
    }

    fun change(write: suspend () -> Unit) {
        if (busy) return
        busy = true
        actionFailed = false
        scope.launch {
            try {
                write()
                editor = null
                deleting = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: XmlyException) {
                if (failure.code == "stale_bookmark") {
                    editor = null
                    deleting = null
                    reload++
                }
                actionFailed = true
            } catch (_: Exception) {
                actionFailed = true
            } finally {
                busy = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentWindowInsets = { WindowInsets.safeDrawing },
    ) {
        BookmarkSheetContent(
            rows = bookmarks,
            canSeek = canSeek,
            busy = busy,
            loadFailed = loadFailed,
            actionFailed = actionFailed && editor == null && deleting == null,
            onAdd = {
                val draft = playback.captureBookmark(episode)
                actionFailed = draft == null
                if (draft != null) editor = BookmarkEditor.Add(draft)
            },
            onRetry = { reload++ },
            onPlay = { if (playback.playBookmark(it)) onDismiss() else actionFailed = true },
            onEdit = { actionFailed = false; editor = BookmarkEditor.Edit(it) },
            onDelete = { actionFailed = false; deleting = it },
        )
    }

    editor?.let { target ->
        BookmarkEditorDialog(target, busy, actionFailed, onDismiss = { editor = null; actionFailed = false }) { note ->
            change {
                val rows = bookmarks.orEmpty()
                bookmarks = when (target) {
                    is BookmarkEditor.Add -> (rows + playback.addBookmark(target.draft, note)).sortedWith(
                        compareByDescending<PlaybackBookmark> { it.createdAtMs }.thenByDescending { it.id },
                    )
                    is BookmarkEditor.Edit -> {
                        val updated = playback.editBookmark(target.bookmark, note)
                        rows.map { if (it.id == updated.id) updated else it }
                    }
                }
            }
        }
    }
    deleting?.let { bookmark ->
        fun dismiss() { if (!busy) { deleting = null; actionFailed = false } }
        AlertDialog(
            onDismissRequest = ::dismiss,
            title = { Text(stringResource(R.string.bookmark_delete_title)) },
            text = if (actionFailed) ({ BookmarkError() }) else null,
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    change {
                        playback.deleteBookmark(bookmark)
                        bookmarks = bookmarks.orEmpty().filterNot { it.id == bookmark.id }
                    }
                }) { Text(stringResource(R.string.bookmark_delete_action), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = ::dismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun BookmarkSheetContent(
    rows: List<PlaybackBookmark>?,
    canSeek: Boolean,
    busy: Boolean,
    loadFailed: Boolean,
    actionFailed: Boolean,
    onAdd: () -> Unit,
    onRetry: () -> Unit,
    onPlay: (PlaybackBookmark) -> Unit,
    onEdit: (PlaybackBookmark) -> Unit,
    onDelete: (PlaybackBookmark) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Short lists wrap their content; long lists leave room for the fixed add action.
        val listLimit = (maxHeight * 0.6f).coerceAtMost(384.dp)
        Column(
            Modifier.fillMaxWidth().padding(bottom = XmLayout.ItemGap),
            verticalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
        ) {
            Text(
                stringResource(R.string.player_bookmarks),
                Modifier.padding(horizontal = XmLayout.PagePadding).semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )
            if (actionFailed) BookmarkError(Modifier.padding(horizontal = XmLayout.PagePadding))
            when {
                loadFailed -> Column(Modifier.padding(horizontal = XmLayout.PagePadding, vertical = XmLayout.ItemGap)) {
                    BookmarkError()
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                }
                rows == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = XmLayout.PagePadding))
                rows.isEmpty() -> EmptyBookmarks()
                else -> LazyColumn(
                    Modifier.weight(1f, fill = false).fillMaxWidth().heightIn(max = listLimit),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(rows, key = { it.id }) { row ->
                        BookmarkRow(
                            row, canSeek = canSeek, busy = busy,
                            onPlay = { onPlay(row) }, onEdit = { onEdit(row) }, onDelete = { onDelete(row) },
                        )
                    }
                }
            }
            Button(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().padding(horizontal = XmLayout.PagePadding),
                enabled = canSeek && !busy && !loadFailed && rows != null,
            ) {
                Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.bookmark_add), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun EmptyBookmarks() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = XmLayout.PagePadding, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
    ) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.BookmarkBorder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            stringResource(R.string.bookmark_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BookmarkRow(
    bookmark: PlaybackBookmark,
    canSeek: Boolean,
    busy: Boolean,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val time = formatDuration(bookmark.positionMs / 1_000.0)
    val playLabel = stringResource(R.string.action_play)
    ListItem(
        modifier = Modifier.clickable(enabled = canSeek && !busy, onClickLabel = playLabel, role = Role.Button, onClick = onPlay),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    time,
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        },
        trailingContent = { BookmarkOptions(busy, onEdit, onDelete) },
    ) {
        if (bookmark.note.isEmpty()) {
            Text(stringResource(R.string.bookmark_no_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(bookmark.note, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BookmarkOptions(busy: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Row {
        IconButton(onClick = onEdit, enabled = !busy) {
            Icon(Icons.Outlined.Edit, stringResource(R.string.bookmark_edit_note), tint = tint)
        }
        IconButton(onClick = onDelete, enabled = !busy) {
            Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.bookmark_delete_action), tint = tint)
        }
    }
}

@Composable
private fun BookmarkEditorDialog(
    target: BookmarkEditor,
    busy: Boolean,
    failed: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var note by remember(target) { mutableStateOf((target as? BookmarkEditor.Edit)?.bookmark?.note.orEmpty()) }
    val valid = remember(note) { isBookmarkNoteValid(note) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(stringResource(if (target is BookmarkEditor.Add) R.string.bookmark_add else R.string.bookmark_edit_note))
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(XmLayout.ItemGap)) {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        formatDuration(target.positionMs / 1_000.0), Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    minLines = 2,
                    maxLines = 5,
                    shape = MaterialTheme.shapes.medium,
                    label = { Text(stringResource(R.string.bookmark_note)) },
                    isError = !valid,
                    supportingText = if (valid) null else ({ Text(stringResource(R.string.bookmark_note_limit, BookmarkNoteLimit)) }),
                )
                if (failed) BookmarkError()
            }
        },
        confirmButton = { Button(enabled = !busy && valid, onClick = { onSave(note) }) { Text(stringResource(R.string.bookmark_save)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun BookmarkError(modifier: Modifier = Modifier) {
    Text(stringResource(R.string.bookmark_error), modifier, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

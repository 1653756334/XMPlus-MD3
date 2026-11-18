package com.xuleo.xmplus.md3.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
        Row(
            Modifier.fillMaxWidth().padding(horizontal = XmLayout.PagePadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.player_bookmarks), Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(enabled = canSeek && !busy && bookmarks != null, onClick = {
                val draft = playback.captureBookmark(episode)
                actionFailed = draft == null
                if (draft != null) editor = BookmarkEditor.Add(draft)
            }) { Text(stringResource(R.string.bookmark_add)) }
        }
        if (actionFailed && editor == null && deleting == null) BookmarkError(Modifier.padding(horizontal = XmLayout.PagePadding))
        val rows = bookmarks
        when {
            loadFailed -> Column(Modifier.padding(horizontal = XmLayout.PagePadding, vertical = XmLayout.ItemGap)) {
                BookmarkError()
                TextButton(onClick = { reload++ }) { Text(stringResource(R.string.action_retry)) }
            }
            rows == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
            rows.isEmpty() -> Text(
                stringResource(R.string.bookmark_empty),
                Modifier.padding(horizontal = XmLayout.PagePadding, vertical = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyColumn(
                Modifier.fillMaxWidth().fillMaxHeight(0.55f),
                contentPadding = PaddingValues(bottom = XmLayout.ItemGap),
            ) {
                items(rows, key = { it.id }) { row ->
                    BookmarkRow(
                        row, canSeek = canSeek, busy = busy,
                        onPlay = {
                            if (playback.playBookmark(row)) onDismiss() else actionFailed = true
                        },
                        onEdit = { actionFailed = false; editor = BookmarkEditor.Edit(row) },
                        onDelete = { actionFailed = false; deleting = row },
                    )
                }
            }
        }
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
                }) { Text(stringResource(R.string.bookmark_delete_action)) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = ::dismiss) { Text(stringResource(R.string.action_cancel)) } },
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
    ListItem(
        modifier = Modifier.clickable(enabled = canSeek && !busy, role = Role.Button, onClick = onPlay),
        supportingContent = if (bookmark.note.isEmpty()) null else ({
            Text(bookmark.note, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }),
        trailingContent = {
            Row {
                IconButton(onEdit, enabled = !busy) { Icon(Icons.Outlined.Edit, stringResource(R.string.bookmark_edit_note)) }
                IconButton(onDelete, enabled = !busy) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.bookmark_delete_action)) }
            }
        },
    ) { Text(formatDuration(bookmark.positionMs / 1_000.0)) }
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(formatDuration(target.positionMs / 1_000.0), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    minLines = 2,
                    maxLines = 5,
                    label = { Text(stringResource(R.string.bookmark_note)) },
                    isError = !valid,
                    supportingText = if (valid) null else ({ Text(stringResource(R.string.bookmark_note_limit, BookmarkNoteLimit)) }),
                )
                if (failed) BookmarkError()
            }
        },
        confirmButton = { TextButton(enabled = !busy && valid, onClick = { onSave(note) }) { Text(stringResource(R.string.bookmark_save)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun BookmarkError(modifier: Modifier = Modifier) {
    Text(stringResource(R.string.bookmark_error), modifier, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

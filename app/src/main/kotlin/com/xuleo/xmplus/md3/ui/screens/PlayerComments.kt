package com.xuleo.xmplus.md3.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ThumbUp as ThumbUpOutlined
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.model.Track
import com.xuleo.xmplus.md3.core.protocol.TrackComment
import com.xuleo.xmplus.md3.library.CommentActions
import com.xuleo.xmplus.md3.library.ListPage
import com.xuleo.xmplus.md3.library.ListSection
import com.xuleo.xmplus.md3.ui.components.Avatar
import com.xuleo.xmplus.md3.ui.components.InlineError
import com.xuleo.xmplus.md3.ui.components.LoadMoreEffect
import com.xuleo.xmplus.md3.ui.components.PagingFooter
import com.xuleo.xmplus.md3.ui.components.formatCount
import com.xuleo.xmplus.md3.ui.components.formatDate
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val FirstPage = 1L

/**
 * 评论 in the player: the track's comments, newest first, and a box to write one. Reading needs no account. Posting,
 * liking and deleting do; without one the player says so, and a write the server refuses says that instead.
 */
@Composable
internal fun PlayerComments(track: Track, actions: CommentActions?, modifier: Modifier = Modifier) {
    if (actions == null) return
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val scope = rememberCoroutineScope()
    val failed = stringResource(R.string.comment_failed)
    val signIn = stringResource(R.string.comment_sign_in)
    val list = remember(track.id, actions) {
        ListSection(scope, { true }) { cursor ->
            val page = (cursor ?: FirstPage).toInt()
            val result = actions.load(track.id, page)
            ListPage(result.items, if (result.hasMore) page + 1L else null)
        }
    }
    val state by list.flow.collectAsStateWithLifecycle()
    val lazy = rememberLazyListState()
    var ownUid by remember(actions) { mutableStateOf<String?>(null) }
    var draft by rememberSaveable(track.id) { mutableStateOf("") }
    var busy by remember(track.id) { mutableStateOf(false) }
    var pendingDelete by remember(track.id) { mutableStateOf<TrackComment?>(null) }
    LaunchedEffect(list) { list.ensureLoaded() }
    LaunchedEffect(actions) { ownUid = actions.uid() }
    LoadMoreEffect(
        { lazy.layoutInfo.visibleItemsInfo.lastOrNull()?.index },
        { state.items.size + 1 },
        state.next,
        state.items.size,
        onLoadMore = { list.loadMore() },
    )

    // Writes run one at a time. The list changes only after the server has confirmed the write.
    fun perform(action: suspend () -> Unit, onDone: () -> Unit = {}) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                if (!actions.signedIn()) {
                    Toast.makeText(context, signIn, Toast.LENGTH_SHORT).show()
                } else {
                    action()
                    onDone()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
            } finally {
                busy = false
            }
        }
    }
    fun toggleLike(comment: TrackComment) {
        val liked = !comment.liked
        val change = if (liked) 1L else -1L
        perform({ actions.setLiked(track.id, comment, liked) }) {
            list.replace { if (it.id == comment.id) it.copy(liked = liked, likes = (it.likes + change).coerceAtLeast(0L)) else it }
        }
    }

    Column(modifier) {
        val count = track.commentCount ?: 0
        if (count > 0) Text(
            pluralStringResource(R.plurals.comment_count, count, count),
            Modifier.padding(horizontal = XmLayout.PagePadding, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val failure = state.failure
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !state.loaded && failure == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.items.isEmpty() && failure != null -> InlineError(failure, onRetry = { list.refresh() }, modifier = Modifier.fillMaxSize())
                state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.comment_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(Modifier.fillMaxSize(), state = lazy) {
                    items(state.items, key = { it.id }) { comment ->
                        CommentRow(
                            comment = comment,
                            own = comment.uid.isNotEmpty() && comment.uid == ownUid,
                            locale = locale,
                            onLike = { toggleLike(comment) },
                            onDelete = { pendingDelete = comment },
                        )
                    }
                    item(key = "footer") {
                        PagingFooter(state.loading, failure != null, state.next != null, onRetry = { list.loadMore() })
                    }
                }
            }
        }
        CommentComposer(
            draft = draft,
            busy = busy,
            onDraft = { draft = it },
            onSend = {
                val content = draft
                perform({ actions.post(track.id, content) }) {
                    draft = ""
                    list.refresh()
                }
            },
        )
    }
    pendingDelete?.let { comment ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.comment_delete_title)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    perform({ actions.delete(track.id, comment) }) { list.remove { it.id == comment.id } }
                }) { Text(stringResource(R.string.comment_delete_confirm)) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun CommentRow(comment: TrackComment, own: Boolean, locale: Locale, onLike: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = XmLayout.PagePadding, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
    ) {
        Avatar(comment.avatarUrl, Modifier.size(36.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    comment.author, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                comment.createdAtMillis?.let { createdAt ->
                    Text(
                        formatDate(createdAt, locale), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(comment.content, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (comment.replyCount > 0) {
                    val replies = comment.replyCount
                    Text(
                        pluralStringResource(R.plurals.comment_replies, replies, replies), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (own) IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.comment_delete))
                }
                Text(formatCount(comment.likes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = onLike) {
                    Icon(
                        imageVector = if (comment.liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUpOutlined,
                        contentDescription = stringResource(R.string.comment_like),
                        tint = if (comment.liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentComposer(draft: String, busy: Boolean, onDraft: (String) -> Unit, onSend: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = XmLayout.PagePadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraft,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.comment_hint)) },
            maxLines = 3,
        )
        IconButton(onClick = onSend, enabled = !busy && draft.isNotBlank()) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.action_send))
        }
    }
}
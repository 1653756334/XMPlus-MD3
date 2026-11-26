package com.xuleo.xmplus.md3.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.ui.icons.XmIcons
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.model.Track
import com.xuleo.xmplus.md3.core.protocol.TrackComment
import com.xuleo.xmplus.md3.library.CommentActions
import com.xuleo.xmplus.md3.library.ListPage
import com.xuleo.xmplus.md3.library.ListSection
import com.xuleo.xmplus.md3.ui.components.ListSkeleton
import com.xuleo.xmplus.md3.ui.components.rememberShowMessage
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
    val showMessage = rememberShowMessage()
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
    var posting by remember(track.id) { mutableStateOf(false) }
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
    fun perform(action: suspend () -> Unit, post: Boolean = false, onDone: () -> Unit = {}) {
        if (busy) return
        busy = true
        posting = post
        scope.launch {
            try {
                if (!actions.signedIn()) {
                    showMessage(signIn)
                } else {
                    action()
                    onDone()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showMessage(failed)
            } finally {
                busy = false
                posting = false
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
            Modifier.padding(start = PlayerPagePadding, end = PlayerPagePadding, top = 4.dp, bottom = 4.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val failure = state.failure
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !state.loaded && failure == null -> ListSkeleton()
                state.items.isEmpty() && failure != null -> InlineError(failure, onRetry = { list.refresh() }, modifier = Modifier.fillMaxSize())
                state.items.isEmpty() -> Column(
                    Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(XmIcons.Outlined.ChatBubbleOutline, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.size(12.dp))
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
            posting = posting,
            onDraft = { draft = it },
            onSend = {
                val content = draft
                perform({ actions.post(track.id, content) }, post = true) {
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

/** Avatar, name, the comment, then when it was written, its replies, and the like and delete buttons on one quiet line. */
@Composable
private fun CommentRow(comment: TrackComment, own: Boolean, locale: Locale, onLike: () -> Unit, onDelete: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(start = PlayerPagePadding, end = PlayerPagePadding - 8.dp, top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(comment.avatarUrl, Modifier.size(36.dp))
        Column(Modifier.weight(1f)) {
            Text(
                comment.author, Modifier.padding(end = 8.dp), style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                comment.content, Modifier.padding(top = 4.dp, end = 8.dp),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp), color = scheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val replies = comment.replyCount
                val meta = listOfNotNull(
                    comment.createdAtMillis?.let { formatDate(it, locale) },
                    if (replies > 0) pluralStringResource(R.plurals.comment_replies, replies, replies) else null,
                ).joinToString(" · ")
                Text(
                    meta, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (own) IconButton(onClick = onDelete) {
                    Icon(XmIcons.Outlined.DeleteOutline, stringResource(R.string.comment_delete), Modifier.size(SmallIcon), tint = scheme.onSurfaceVariant)
                }
                LikeButton(comment.liked, comment.likes, onLike)
            }
        }
    }
}

/** The thumb and the count side by side, in the accent colour once liked. */
@Composable
private fun LikeButton(liked: Boolean, likes: Long, onClick: () -> Unit) {
    val tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.heightIn(min = 48.dp).clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.comment_like), onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            if (liked) XmIcons.Filled.ThumbUp else XmIcons.Outlined.ThumbUpOutlined, stringResource(R.string.comment_like),
            Modifier.size(SmallIcon), tint = tint,
        )
        if (likes > 0) Text(formatCount(likes), style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

/** A filled pill with no outline, like the album search field, and the send button in the accent colour. */
@Composable
private fun CommentComposer(draft: String, busy: Boolean, posting: Boolean, onDraft: (String) -> Unit, onSend: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(start = PlayerPagePadding, end = PlayerPagePadding - 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicTextField(
            value = draft,
            onValueChange = onDraft,
            modifier = Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp))
                .background(scheme.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 11.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.primary),
            maxLines = 4,
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (draft.isEmpty()) {
                        Text(stringResource(R.string.comment_hint), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                    }
                    inner()
                }
            },
        )
        if (posting) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        } else {
            IconButton(
                onClick = onSend, enabled = !busy && draft.isNotBlank(),
                colors = IconButtonDefaults.iconButtonColors(contentColor = scheme.primary),
            ) {
                Icon(XmIcons.Filled.Send, contentDescription = stringResource(R.string.action_send))
            }
        }
    }
}

/** The like and delete glyphs, smaller than a toolbar icon so the line stays quiet. */
private val SmallIcon = 18.dp

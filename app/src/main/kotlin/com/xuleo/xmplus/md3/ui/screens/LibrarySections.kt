package com.xuleo.xmplus.md3.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.media.BookmarkEntry
import com.xuleo.xmplus.md3.core.media.ListenRecord
import com.xuleo.xmplus.md3.core.media.SongResume
import com.xuleo.xmplus.md3.core.model.ContentKind
import com.xuleo.xmplus.md3.library.LibraryViewModel
import com.xuleo.xmplus.md3.library.ListState
import com.xuleo.xmplus.md3.ui.components.Avatar
import com.xuleo.xmplus.md3.ui.components.BrowsePage
import com.xuleo.xmplus.md3.ui.components.Cover
import com.xuleo.xmplus.md3.ui.components.ErrorState
import com.xuleo.xmplus.md3.ui.components.ListSkeleton
import com.xuleo.xmplus.md3.ui.components.LoadMoreEffect
import com.xuleo.xmplus.md3.ui.components.MusicCover
import com.xuleo.xmplus.md3.ui.components.PagingFooter
import com.xuleo.xmplus.md3.ui.components.formatCount
import com.xuleo.xmplus.md3.ui.components.formatDate
import com.xuleo.xmplus.md3.ui.components.formatDuration
import com.xuleo.xmplus.md3.ui.icons.XmIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** An episode counts as finished when it is within this many milliseconds of its end. */
private const val FinishedToleranceMs = 1_000L

/** The 听书 collections, each a page of its own opened from 听过. All but 书签 belong to the signed-in account. */
enum class LibrarySection(@StringRes val label: Int) {
    Cloud(R.string.listen_tab_cloud),
    Updates(R.string.listen_tab_updates),
    Subscriptions(R.string.listen_tab_subscriptions),
    Favorites(R.string.library_favorites),
    Bookmarks(R.string.listen_tab_bookmarks),
    Anchors(R.string.listen_tab_anchors),
}

/**
 * One 听书 collection. Account lists are read the first time the page is shown; 书签 are this device's. Tapping a cloud
 * history row or a bookmark plays it from its saved position; other rows open their album or anchor page.
 * Unsubscribing and unfavouriting happen in place.
 */
@Composable
fun LibrarySectionScreen(
    viewModel: LibraryViewModel,
    section: LibrarySection,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenAnchor: (String) -> Unit,
    onPlay: (String, String, Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val failed = stringResource(R.string.library_action_failed)
    val openAlbum: (String) -> Unit = { albumId -> if (albumId.isNotEmpty()) onOpenAlbum(albumId) }
    LaunchedEffect(section) {
        when (section) {
            LibrarySection.Cloud -> viewModel.cloudHistory.ensureLoaded()
            LibrarySection.Updates -> viewModel.updates.ensureLoaded()
            LibrarySection.Subscriptions -> viewModel.subscriptions.ensureLoaded()
            LibrarySection.Favorites -> viewModel.favorites.ensureLoaded()
            LibrarySection.Bookmarks -> viewModel.loadBookmarks()
            LibrarySection.Anchors -> viewModel.followings.ensureLoaded()
        }
    }
    // A change to the account. If the server refuses it, the row stays where it was and the snackbar says so.
    val attempt: (suspend () -> Unit) -> Unit = { change ->
        scope.launch {
            try {
                change()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                snackbar.showSnackbar(failed)
            }
        }
    }
    BrowsePage(stringResource(section.label), onBack, snackbarHost = { SnackbarHost(snackbar) }) {
        when (section) {
            LibrarySection.Cloud -> {
                val cloud by viewModel.cloudHistory.flow.collectAsStateWithLifecycle()
                ListingPage(
                    state = cloud,
                    signedOutText = R.string.listen_signed_out,
                    emptyText = R.string.listen_empty_cloud,
                    onRefresh = { viewModel.cloudHistory.refresh() },
                    onLoadMore = { viewModel.cloudHistory.loadMore() },
                    key = { "${it.albumId}/${it.trackId}" },
                ) { record ->
                    val locale = LocalConfiguration.current.locales[0]
                    ListingRow(
                        cover = record.coverUrl,
                        title = record.trackTitle.ifEmpty { record.albumTitle },
                        subtitle = record.albumTitle,
                        meta = joinMeta(
                            progressText(record.progress.positionMs, record.durationSeconds * 1_000L),
                            formatDate(record.progress.observedAtMs, locale),
                        ),
                        onOpen = { onPlay(record.albumId, record.trackId, resumeAt(record.progress.positionMs, record.durationSeconds * 1_000L)) },
                        roomy = true,
                    )
                }
            }
            LibrarySection.Updates -> {
                val updates by viewModel.updates.flow.collectAsStateWithLifecycle()
                ListingPage(
                    state = updates,
                    signedOutText = R.string.listen_signed_out,
                    emptyText = R.string.listen_empty_updates,
                    onRefresh = { viewModel.updates.refresh() },
                    onLoadMore = { viewModel.updates.loadMore() },
                    key = { "${it.track.id}/${it.publishedAtMillis}" },
                ) { update ->
                    val locale = LocalConfiguration.current.locales[0]
                    ListingRow(
                        cover = update.track.coverUrl,
                        title = update.track.title,
                        subtitle = update.track.albumTitle,
                        meta = formatDate(update.publishedAtMillis, locale),
                        onOpen = { openAlbum(update.track.albumId) },
                        roomy = true,
                    )
                }
            }
            LibrarySection.Subscriptions -> {
                val subscriptions by viewModel.subscriptions.flow.collectAsStateWithLifecycle()
                ListingPage(
                    state = subscriptions,
                    signedOutText = R.string.listen_signed_out,
                    emptyText = R.string.listen_empty_subscriptions,
                    onRefresh = { viewModel.subscriptions.refresh() },
                    onLoadMore = { viewModel.subscriptions.loadMore() },
                    key = { it.album.id },
                ) { item ->
                    val unread = item.unreadCount.toInt()
                    ListingRow(
                        cover = item.album.coverUrl,
                        title = item.album.title,
                        subtitle = item.latestTitle,
                        meta = if (unread > 0) pluralStringResource(R.plurals.listen_unplayed, unread, unread) else "",
                        onOpen = { openAlbum(item.album.id) },
                        large = true,
                        roomy = true,
                        trailing = {
                            IconButton(onClick = { attempt { viewModel.unsubscribe(item) } }) {
                                Icon(XmIcons.Filled.LibraryAddCheck, stringResource(R.string.listen_unsubscribe), tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                    )
                }
            }
            LibrarySection.Favorites -> {
                val favorites by viewModel.favorites.flow.collectAsStateWithLifecycle()
                ListingPage(
                    state = favorites,
                    signedOutText = R.string.listen_signed_out,
                    emptyText = R.string.listen_empty_favorites,
                    onRefresh = { viewModel.favorites.refresh() },
                    onLoadMore = { viewModel.favorites.loadMore() },
                    key = { it.id },
                ) { track ->
                    ListingRow(
                        cover = track.coverUrl,
                        title = track.title,
                        subtitle = track.albumTitle,
                        meta = "",
                        onOpen = { openAlbum(track.albumId) },
                        large = true,
                        roomy = true,
                        trailing = {
                            IconButton(onClick = { attempt { viewModel.unfavorite(track) } }) {
                                Icon(XmIcons.Filled.Favorite, stringResource(R.string.listen_unfavorite), tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                    )
                }
            }
            LibrarySection.Bookmarks -> {
                val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
                BookmarkPage(bookmarks, onPlay = { entry -> onPlay(entry.albumId, entry.trackId, entry.positionMs) })
            }
            LibrarySection.Anchors -> {
                val anchors by viewModel.followings.flow.collectAsStateWithLifecycle()
                ListingPage(
                    state = anchors,
                    signedOutText = R.string.listen_signed_out,
                    emptyText = R.string.listen_empty_anchors,
                    onRefresh = { viewModel.followings.refresh() },
                    onLoadMore = { viewModel.followings.loadMore() },
                    key = { it.uid },
                ) { anchor ->
                    val fans = anchor.fans
                    ListingRow(
                        cover = anchor.avatarUrl,
                        title = anchor.nickname,
                        subtitle = "",
                        meta = if (fans != null) pluralStringResource(R.plurals.anchor_follower_count, fans, formatCount(fans.toLong())) else "",
                        onOpen = { onOpenAnchor(anchor.uid) },
                        circle = true,
                        roomy = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkPage(bookmarks: List<BookmarkEntry>?, onPlay: (BookmarkEntry) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    when {
        bookmarks == null -> Box(Modifier.fillMaxSize())
        bookmarks.isEmpty() -> Note(stringResource(R.string.listen_empty_bookmarks))
        else -> LazyColumn(Modifier.fillMaxSize()) {
            items(bookmarks, key = { "${it.trackId}/${it.createdAtMs}" }) { entry ->
                ListingRow(
                    cover = entry.source.coverUrl,
                    title = entry.source.trackTitle.ifEmpty { stringResource(R.string.listen_untitled) },
                    subtitle = entry.source.albumTitle,
                    meta = joinMeta(
                        stringResource(R.string.listen_bookmark_at, formatDuration(entry.positionMs / 1_000.0)),
                        entry.note.ifEmpty { stringResource(R.string.bookmark_no_note) },
                        formatDate(entry.createdAtMs, locale),
                    ),
                    onOpen = { onPlay(entry) },
                    roomy = true,
                )
            }
        }
    }
}

/**
 * One list read page by page: the account's sign-in prompt, a spinner, a failure with a retry, an empty note, or the rows
 * with a footer that loads the next page as the end comes into view.
 */
@Composable
private fun <T> ListingPage(
    state: ListState<T>,
    @StringRes signedOutText: Int,
    @StringRes emptyText: Int,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    key: (T) -> Any,
    row: @Composable (T) -> Unit,
) {
    val lazy = rememberLazyListState()
    LoadMoreEffect(
        { lazy.layoutInfo.visibleItemsInfo.lastOrNull()?.index },
        { state.items.size + 1 },
        state.next,
        state.items.size,
        onLoadMore = onLoadMore,
    )
    val failure = state.failure
    when {
        !state.signedIn -> Note(stringResource(signedOutText))
        !state.loaded && failure == null -> ListSkeleton()
        state.items.isEmpty() && failure != null -> ErrorState(failure, onRefresh)
        state.items.isEmpty() -> Note(stringResource(emptyText))
        else -> LazyColumn(Modifier.fillMaxSize(), state = lazy) {
            items(state.items, key = key) { row(it) }
            item(key = "footer") { PagingFooter(state.loading, failure != null, state.next != null, onLoadMore) }
        }
    }
}

/**
 * One row of a 听过 list: the cover, a title on one line, the quiet lines of [subtitle] and [meta], an optional trailing
 * action, and a tap that opens it. Episodes and songs have a 48dp cover; [large] gives albums and sheets 56dp.
 * [roomy] gives a 听书 row more air: a 56dp cover and [subtitle] and [meta] on lines of their own.
 * [music] gives the cover the music placeholder; [onLongClick] starts selection where a list has it.
 */
@Composable
internal fun ListingRow(
    cover: String?,
    title: String,
    subtitle: String,
    meta: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    circle: Boolean = false,
    music: Boolean = false,
    large: Boolean = false,
    roomy: Boolean = false,
    highlighted: Boolean = false,
    /** Marks the cover as 听书 or 音乐 where both kinds share a list. */
    kind: ContentKind? = null,
    onLongClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val click = if (onLongClick != null) Modifier.combinedClickable(onClick = onOpen, onLongClick = onLongClick) else Modifier.clickable(onClick = onOpen)
    val shape = if (large || roomy) MaterialTheme.shapes.medium else MaterialTheme.shapes.small
    Row(
        modifier.fillMaxWidth().then(click).heightIn(min = if (roomy) 76.dp else 64.dp)
            .padding(horizontal = XmLayout.PagePadding, vertical = if (roomy) 10.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
    ) {
        Box(Modifier.size(if (large || roomy) 56.dp else 48.dp)) {
            when {
                circle -> Avatar(cover, Modifier.matchParentSize())
                music -> MusicCover(cover, Modifier.matchParentSize(), shape)
                else -> Cover(cover, Modifier.matchParentSize(), shape)
            }
            if (kind != null) KindBadge(kind, Modifier.align(Alignment.BottomStart).padding(3.dp), size = 16.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (roomy) 4.dp else 2.dp)) {
            Text(
                title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (highlighted) MaterialTheme.colorScheme.primary else Color.Unspecified,
            )
            // A 听书 row has room for both quiet lines; a music row keeps them together on one.
            if (roomy) {
                if (subtitle.isNotEmpty()) QuietLine(subtitle)
                if (meta.isNotEmpty()) QuietLine(meta)
            } else {
                val line = joinMeta(subtitle, meta)
                if (line.isNotEmpty()) QuietLine(line)
            }
        }
        trailing()
    }
}

@Composable
private fun QuietLine(text: String) {
    Text(
        text, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun Note(text: String, modifier: Modifier = Modifier) {
    // Inside a scrolling list the height is open, so the note takes only the room it needs.
    Box(modifier.fillMaxSize().padding(XmLayout.PagePadding), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Plays a local history row from where it stopped: an episode from its saved position, a song with the list it was
 * played from when that list is still kept. A finished item starts over.
 */
internal fun LibraryViewModel.resume(record: ListenRecord, onPlay: (String, String, Long) -> Unit, onPlaySong: (SongResume) -> Unit) {
    if (record.kind == ContentKind.Song) {
        resumeSong(record) { onPlaySong(it.copy(positionMs = resumeAt(it.positionMs, record.durationMs))) }
    } else {
        onPlay(record.albumId, record.trackId, resumeAt(record.positionMs, record.durationMs))
    }
}

/** Where playback starts from a saved position: a finished episode starts over. */
internal fun resumeAt(positionMs: Long, durationMs: Long): Long {
    val finished = durationMs > 0 && positionMs >= durationMs - FinishedToleranceMs
    return if (finished) 0L else positionMs
}

@Composable
internal fun progressText(positionMs: Long, durationMs: Long): String {
    val finished = durationMs > 0 && positionMs >= durationMs - FinishedToleranceMs
    return when {
        finished -> stringResource(R.string.history_finished)
        durationMs > 0 -> stringResource(
            R.string.history_progress,
            formatDuration(positionMs / 1_000.0),
            formatDuration(durationMs / 1_000.0),
        )
        else -> formatDuration(positionMs / 1_000.0)
    }
}

internal fun joinMeta(vararg parts: String): String = parts.filter { it.isNotEmpty() }.joinToString(" · ")

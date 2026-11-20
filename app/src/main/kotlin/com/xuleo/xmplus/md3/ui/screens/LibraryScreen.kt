package com.xuleo.xmplus.md3.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryAddCheck
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.xuleo.xmplus.md3.library.LibraryViewModel
import com.xuleo.xmplus.md3.library.ListState
import com.xuleo.xmplus.md3.ui.Destination
import com.xuleo.xmplus.md3.ui.components.Avatar
import com.xuleo.xmplus.md3.ui.components.Cover
import com.xuleo.xmplus.md3.ui.components.ErrorState
import com.xuleo.xmplus.md3.ui.components.LoadMoreEffect
import com.xuleo.xmplus.md3.ui.components.PagingFooter
import com.xuleo.xmplus.md3.ui.components.ScrollingDetailTabs
import com.xuleo.xmplus.md3.ui.components.SettlingPager
import com.xuleo.xmplus.md3.ui.components.formatCount
import com.xuleo.xmplus.md3.ui.components.formatDate
import com.xuleo.xmplus.md3.ui.components.formatDuration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** An episode counts as finished when it is within this many milliseconds of its end. */
private const val FinishedToleranceMs = 1_000L

/** The 听过 sections, in the order XMPlus shows them. */
private enum class ListeningSection(@StringRes val label: Int) {
    History(R.string.listen_tab_history),
    Cloud(R.string.listen_tab_cloud),
    Updates(R.string.listen_tab_updates),
    Subscriptions(R.string.listen_tab_subscriptions),
    Favorites(R.string.listen_tab_favorites),
    Bookmarks(R.string.listen_tab_bookmarks),
    Anchors(R.string.listen_tab_anchors),
}

/**
 * 听过. History and bookmarks are this device's; the other sections are the account's and are read the first time they
 * are shown. Tapping a history row, a cloud history row or a bookmark plays it from its saved position; other rows open their album or anchor page. Unsubscribing and unfavouriting happen in place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: LibraryViewModel, onOpenAlbum: (String) -> Unit, onOpenAnchor: (String) -> Unit, onPlay: (String, String, Long) -> Unit) {
    val pager = rememberPagerState { ListeningSection.entries.size }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val history by viewModel.listenHistory.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val cloud by viewModel.cloudHistory.flow.collectAsStateWithLifecycle()
    val updates by viewModel.updates.flow.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.flow.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.flow.collectAsStateWithLifecycle()
    val anchors by viewModel.followings.flow.collectAsStateWithLifecycle()
    val section = ListeningSection.entries[pager.currentPage]
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    val removed = stringResource(R.string.history_removed)
    val undo = stringResource(R.string.history_undo)
    val failed = stringResource(R.string.library_action_failed)
    val openAlbum: (String) -> Unit = { albumId -> if (albumId.isNotEmpty()) onOpenAlbum(albumId) }
    val historyRows = history.orEmpty()
    val historySelecting = selecting && section == ListeningSection.History
    val allSelected = historyRows.isNotEmpty() && selected.size == historyRows.size
    // Leaving the tab, or pressing back, ends selection.
    LaunchedEffect(section) {
        selecting = false
        selected = emptySet()
    }
    BackHandler(enabled = historySelecting) {
        selecting = false
        selected = emptySet()
    }
    // The selected rows leave together, and one Undo puts them all back.
    val deleteSelected: () -> Unit = {
        val chosen = historyRows.filter { it.albumId in selected }
        chosen.forEach(viewModel::removeHistory)
        selecting = false
        selected = emptySet()
        scope.launch {
            if (snackbar.showSnackbar(removed, undo, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                chosen.forEach(viewModel::restoreHistory)
            }
        }
    }
    LaunchedEffect(pager.currentPage) {
        when (ListeningSection.entries[pager.currentPage]) {
            ListeningSection.History -> Unit
            ListeningSection.Cloud -> viewModel.cloudHistory.ensureLoaded()
            ListeningSection.Updates -> viewModel.updates.ensureLoaded()
            ListeningSection.Subscriptions -> viewModel.subscriptions.ensureLoaded()
            ListeningSection.Favorites -> viewModel.favorites.ensureLoaded()
            ListeningSection.Bookmarks -> viewModel.loadBookmarks()
            ListeningSection.Anchors -> viewModel.followings.ensureLoaded()
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
    TabScaffold(
        Destination.Library.label,
        headline = if (historySelecting) stringResource(R.string.history_selected, selected.size) else null,
        navigation = {
            if (historySelecting) IconButton(onClick = { selecting = false; selected = emptySet() }) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel))
            }
        },
        actions = {
            if (section == ListeningSection.History && historyRows.isNotEmpty()) {
                if (selecting) {
                    IconButton(onClick = {
                        selected = if (allSelected) emptySet<String>() else historyRows.mapTo(HashSet()) { it.albumId }
                    }) {
                        Icon(Icons.Filled.DoneAll, contentDescription = stringResource(R.string.history_select_all))
                    }
                    IconButton(onClick = deleteSelected, enabled = selected.isNotEmpty()) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.history_remove))
                    }
                } else {
                    IconButton(onClick = { selecting = true }) {
                        Icon(Icons.Outlined.Checklist, contentDescription = stringResource(R.string.history_select))
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollingDetailTabs(pager.currentPage, ListeningSection.entries.map { stringResource(it.label) }, { index -> scope.launch { pager.animateScrollToPage(index) } })
            SettlingPager(pager, Modifier.weight(1f).fillMaxWidth()) { index ->
                when (ListeningSection.entries[index]) {
                    ListeningSection.History -> HistoryPage(
                        history,
                        selecting = selecting,
                        selected = selected,
                        onPlay = { record -> onPlay(record.albumId, record.trackId, resumeAt(record.positionMs, record.durationMs)) },
                        onToggle = { albumId -> selected = if (albumId in selected) selected - albumId else selected + albumId },
                    )
                    ListeningSection.Cloud -> ListingPage(
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
                        )
                    }
                    ListeningSection.Updates -> ListingPage(
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
                        )
                    }
                    ListeningSection.Subscriptions -> ListingPage(
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
                            trailing = {
                                IconButton(onClick = { attempt { viewModel.unsubscribe(item) } }) {
                                    Icon(Icons.Filled.LibraryAddCheck, stringResource(R.string.listen_unsubscribe), tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                        )
                    }
                    ListeningSection.Favorites -> ListingPage(
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
                            trailing = {
                                IconButton(onClick = { attempt { viewModel.unfavorite(track) } }) {
                                    Icon(Icons.Filled.Favorite, stringResource(R.string.listen_unfavorite), tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                        )
                    }
                    ListeningSection.Bookmarks -> BookmarkPage(bookmarks, onPlay = { entry -> onPlay(entry.albumId, entry.trackId, entry.positionMs) })
                    ListeningSection.Anchors -> ListingPage(
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
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryPage(
    records: List<ListenRecord>?,
    selecting: Boolean,
    selected: Set<String>,
    onPlay: (ListenRecord) -> Unit,
    onToggle: (String) -> Unit,
) {
    when {
        records == null -> Box(Modifier.fillMaxSize())
        records.isEmpty() -> Note(stringResource(R.string.history_empty))
        else -> LazyColumn(Modifier.fillMaxSize()) {
            items(records, key = { it.albumId }) { record ->
                HistoryRow(
                    record,
                    selecting = selecting,
                    checked = record.albumId in selected,
                    onPlay = { onPlay(record) },
                    onToggle = { onToggle(record.albumId) },
                )
            }
        }
    }
}

/** One album in local history: its latest episode, where playback stopped in it, and when. In selection mode a tap ticks the row. */
@Composable
private fun HistoryRow(record: ListenRecord, selecting: Boolean, checked: Boolean, onPlay: () -> Unit, onToggle: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    ListingRow(
        cover = record.coverUrl,
        title = record.albumTitle,
        subtitle = record.trackTitle,
        meta = joinMeta(progressText(record.positionMs, record.durationMs), formatDate(record.playedAtMs, locale)),
        onOpen = if (selecting) onToggle else onPlay,
        modifier = Modifier.background(MaterialTheme.colorScheme.surface),
        trailing = { if (selecting) Checkbox(checked = checked, onCheckedChange = null) },
    )
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
        !state.loaded && failure == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.items.isEmpty() && failure != null -> ErrorState(failure, onRefresh)
        state.items.isEmpty() -> Note(stringResource(emptyText))
        else -> LazyColumn(Modifier.fillMaxSize(), state = lazy) {
            items(state.items, key = key) { row(it) }
            item(key = "footer") { PagingFooter(state.loading, failure != null, state.next != null, onLoadMore) }
        }
    }
}

/** One row of a 听过 list: the cover, a title with its details, an optional trailing action, and a tap that opens it. */
@Composable
private fun ListingRow(
    cover: String?,
    title: String,
    subtitle: String,
    meta: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    circle: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = XmLayout.PagePadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
    ) {
        if (circle) Avatar(cover, Modifier.size(56.dp)) else Cover(cover, Modifier.size(56.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) Text(
                subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (meta.isNotEmpty()) Text(
                meta, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}

@Composable
private fun Note(text: String) {
    Box(Modifier.fillMaxSize().padding(XmLayout.PagePadding), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Where playback starts from a saved position: a finished episode starts over. */
private fun resumeAt(positionMs: Long, durationMs: Long): Long {
    val finished = durationMs > 0 && positionMs >= durationMs - FinishedToleranceMs
    return if (finished) 0L else positionMs
}

@Composable
private fun progressText(positionMs: Long, durationMs: Long): String {
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

private fun joinMeta(vararg parts: String): String = parts.filter { it.isNotEmpty() }.joinToString(" · ")
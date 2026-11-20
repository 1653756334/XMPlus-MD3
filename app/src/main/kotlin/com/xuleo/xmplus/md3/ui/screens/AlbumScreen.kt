@file:OptIn(ExperimentalMaterial3Api::class)

package com.xuleo.xmplus.md3.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LibraryAddCheck
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.browse.AlbumState
import com.xuleo.xmplus.md3.browse.AlbumViewModel
import com.xuleo.xmplus.md3.browse.SubscriptionNotice
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.download.DownloadState
import com.xuleo.xmplus.md3.core.download.DownloadTask
import com.xuleo.xmplus.md3.core.model.Album
import com.xuleo.xmplus.md3.core.model.AlbumDetail
import com.xuleo.xmplus.md3.core.model.Track
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.download.DownloadRange
import com.xuleo.xmplus.md3.library.LibraryToggle
import com.xuleo.xmplus.md3.ui.components.*

/** The pages of an album, in the order of their tabs. */
private object AlbumTab {
    const val Episodes = 1
    const val Reviews = 2
}

private val AlbumTabs = listOf(R.string.album_tab_introduction, R.string.album_tab_episodes, R.string.album_tab_reviews)

/** The toolbar over the episodes, and the search field that takes its place. */
private val ToolbarHeight = 48.dp
private val SearchHeight = 44.dp

/**
 * One album: a header that scrolls away over its blurred cover, then the tabs and their pages. The header comes back only
 * when its page is at the top, and the top bar stays clear until the header has gone.
 */
@Composable
internal fun AlbumScreen(
    viewModel: AlbumViewModel,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenAnchor: (String?) -> Unit,
    onPlayEpisode: (AlbumDetail, Track) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.start() }
    val context = LocalContext.current
    val notice = state.subscriptionNotice
    LaunchedEffect(viewModel, notice) {
        val message = when (notice) {
            SubscriptionNotice.Subscribed -> R.string.album_subscribed
            SubscriptionNotice.Unsubscribed -> R.string.album_unsubscribed
            SubscriptionNotice.Failed -> R.string.library_action_failed
            null -> return@LaunchedEffect
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearSubscriptionNotice()
    }
    val prompt = rememberDownloadPrompt(viewModel.downloads)
    val tasks by viewModel.downloadTasks.collectAsStateWithLifecycle()
    val downloads = remember(tasks) { tasks.associateBy { it.trackId } }
    BackHandler(enabled = state.selecting) { viewModel.stopSelecting() }
    val pager = rememberPagerState { AlbumTabs.size }
    // The lists live here rather than in their pages, so each keeps its place while its tab is off screen or search is open.
    val introList = rememberLazyListState()
    val episodesList = rememberLazyListState()
    val reviewsList = rememberLazyListState()
    LaunchedEffect(pager.currentPage) { if (pager.currentPage == AlbumTab.Reviews) viewModel.ensureReviews() }
    val detail = state.detail
    if (detail == null) {
        DetailPage(onBack) {
            if (state.headerLoading) ListSkeleton(Modifier.fillMaxSize())
            else ErrorState(state.headerErrorCode.orEmpty(), viewModel::retryHeader)
        }
    } else {
        DetailPage(
            onBack, detail.album.title,
            header = { AlbumHero(detail) { onOpenAnchor(detail.album.anchorUid) } },
            actions = { SubscribeAction(state.subscription, viewModel::toggleSubscription) },
        ) {
            DetailTabs(pager, AlbumTabs)
            // Picking episodes belongs to the episode list; another tab, or back, ends it.
            LaunchedEffect(pager.currentPage) { if (pager.currentPage != AlbumTab.Episodes) viewModel.stopSelecting() }
            SettlingPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
                when (page) {
                    AlbumTab.Episodes -> EpisodesPage(
                        state, detail, viewModel, episodesList, downloads, prompt,
                        onPlay = { onPlayEpisode(detail, it) },
                    )
                    AlbumTab.Reviews -> ReviewsPage(state, viewModel, reviewsList)
                    else -> IntroductionPage(state, viewModel, onOpenAlbum, introList)
                }
            }
        }
    }
    if (state.replyTo != null) RepliesSheet(state, viewModel)
    DownloadPromptHost(prompt)
}

/**
 * 订阅 at the end of the top bar, with the icons the 订阅 list in 听过 uses: filled in the accent colour when subscribed.
 * Before the account's list has been read it waits disabled; after a failed read a tap reads it again.
 */
@Composable
private fun SubscribeAction(state: LibraryToggle, onToggle: () -> Unit) {
    if (!state.signedIn) return
    val subscribed = state.on == true
    IconToggleButton(
        checked = subscribed,
        onCheckedChange = { onToggle() },
        enabled = !state.busy && (state.on != null || state.failed),
    ) {
        if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        else Icon(
            if (subscribed) Icons.Filled.LibraryAddCheck else Icons.Outlined.LibraryAdd,
            stringResource(if (subscribed) R.string.listen_unsubscribe else R.string.album_subscribe),
        )
    }
}

/** An episode that can be picked for download: not in the download list, or failed there. */
private fun DownloadTask?.pickable(): Boolean = this == null || state == DownloadState.Failed

/** A page's list, filling the space under the tab row and kept to the content column on wide windows. */
@Composable
private fun ColumnScope.AlbumListPage(list: LazyListState, content: LazyListScope.() -> Unit) {
    // Overscroll would take the upward drag at the top before the header can come back, so the lists do without it.
    CompositionLocalProvider(LocalOverscrollFactory provides null) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = XmLayout.ContentWidth).fillMaxSize(), list,
                contentPadding = PaddingValues(bottom = XmLayout.SectionGap), content = content,
            )
        }
    }
}

@Composable
private fun IntroductionPage(state: AlbumState, viewModel: AlbumViewModel, onOpenAlbum: (String) -> Unit, list: LazyListState) {
    Column(Modifier.fillMaxSize()) {
        AlbumListPage(list) { introduction(state, viewModel, onOpenAlbum) }
    }
}

@Composable
private fun EpisodesPage(
    state: AlbumState,
    detail: AlbumDetail,
    viewModel: AlbumViewModel,
    list: LazyListState,
    downloads: Map<String, DownloadTask>,
    prompt: DownloadPrompt?,
    onPlay: (Track) -> Unit,
) {
    var pickingRange by rememberSaveable { mutableStateOf(false) }
    val total = detail.album.trackCount
    fun download(queued: () -> Unit = {}, tracks: suspend () -> List<Track>) {
        prompt?.request(detail.album, total, queued, tracks)
    }
    val pickable = state.episodes.filter { downloads[it.id].pickable() }.mapTo(HashSet()) { it.id }
    val allPicked = pickable.isNotEmpty() && state.selected.containsAll(pickable)
    Column(Modifier.fillMaxSize()) {
        // The toolbar sits above the list rather than inside it. A field inside a list is scrolled into view when it takes
        // focus, which would pull the list back to its first row every time search opens.
        EpisodeToolbar(
            state, total, viewModel,
            selection = if (prompt == null) null else EpisodeSelection(
                allPicked = allPicked,
                canPickAll = pickable.isNotEmpty(),
                onPickAll = { viewModel.setSelected(if (allPicked) emptySet() else pickable) },
                onRange = { pickingRange = true },
                onDownload = {
                    val chosen = state.episodes.filter { it.id in state.selected }.sortedBy { it.officialPosition ?: Int.MAX_VALUE }
                    download(viewModel::stopSelecting) { chosen }
                },
            ),
        )
        if (searchResultsShown(state)) SearchResults(state, viewModel, onPlay)
        else EpisodeList(state, viewModel, list, downloads, onPlay, onDownload = if (prompt == null) null else { track -> download { listOf(track) } })
    }
    if (pickingRange) {
        RangeDialog(total, onDismiss = { pickingRange = false }) { numbers ->
            pickingRange = false
            download(viewModel::stopSelecting) { viewModel.downloads.episodesNumbered(detail, numbers) }
        }
    }
}

/** What the toolbar offers while episodes are being picked. */
private class EpisodeSelection(
    val allPicked: Boolean,
    val canPickAll: Boolean,
    val onPickAll: () -> Unit,
    val onRange: () -> Unit,
    val onDownload: () -> Unit,
)

@Composable
private fun ReviewsPage(state: AlbumState, viewModel: AlbumViewModel, list: LazyListState) {
    Column(Modifier.fillMaxSize()) {
        LoadMoreEffect(
            { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index }, { list.layoutInfo.totalItemsCount },
            state.reviewPage, state.reviewsLoading, state.newestReviews, onLoadMore = viewModel::loadMoreReviews,
        )
        AlbumListPage(list) { reviews(state, viewModel) }
    }
}

private fun searchResultsShown(state: AlbumState): Boolean =
    state.searchOpen && (state.hitPage > 0 || state.hitsLoading || state.hitsErrorCode != null)

/**
 * The episodes loaded so far. The next page loads as the end comes into view. The list keeps its place while search
 * results take its space.
 */
@Composable
private fun ColumnScope.EpisodeList(
    state: AlbumState,
    viewModel: AlbumViewModel,
    list: LazyListState,
    downloads: Map<String, DownloadTask>,
    onPlay: (Track) -> Unit,
    onDownload: ((Track) -> Unit)?,
) {
    LoadMoreEffect(
        { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index }, { list.layoutInfo.totalItemsCount },
        state.episodePage, state.episodesLoading, state.ascending, onLoadMore = viewModel::loadMoreEpisodes,
    )
    AlbumListPage(list) {
        when {
            state.episodes.isEmpty() && state.episodesLoading -> item(key = "episodes-loading") { SkeletonRow() }
            state.episodes.isEmpty() && state.episodesErrorCode != null ->
                item(key = "episodes-error") { InlineError(state.episodesErrorCode.orEmpty(), viewModel::retryEpisodes) }
            state.episodes.isEmpty() -> item(key = "episodes-empty") { EmptyText(R.string.album_episodes_empty) }
            else -> {
                items(state.episodes, key = { it.id }) { track ->
                    val task = downloads[track.id]
                    EpisodeRow(
                        track = track,
                        task = task,
                        selecting = state.selecting,
                        selected = track.id in state.selected,
                        onClick = {
                            if (!state.selecting) onPlay(track)
                            else if (task.pickable()) viewModel.toggleSelected(track.id)
                        },
                        onLongClick = if (onDownload != null && !state.selecting && task.pickable()) ({ viewModel.startSelecting(track.id) }) else null,
                        onDownload = onDownload?.let { { it(track) } },
                        onPause = { viewModel.pauseDownload(track.id) },
                        onResume = { viewModel.resumeDownload(track.id) },
                    )
                }
                item(key = "episodes-footer") {
                    PagingFooter(state.episodesLoading, state.episodesErrorCode != null, state.episodesHasMore, viewModel::retryEpisodes)
                }
            }
        }
    }
}

/** The search hits. A new search starts at its first hit; closing search returns to the episodes where they were. */
@Composable
private fun ColumnScope.SearchResults(state: AlbumState, viewModel: AlbumViewModel, onPlay: (Track) -> Unit) {
    val list = rememberLazyListState()
    LoadMoreEffect(
        { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index }, { list.layoutInfo.totalItemsCount },
        state.hitPage, state.hitsLoading, onLoadMore = viewModel::loadMoreHits,
    )
    AlbumListPage(list) {
        when {
            state.hits.isEmpty() && state.hitsLoading -> item(key = "hits-loading") { SkeletonRow() }
            state.hits.isEmpty() && state.hitsErrorCode != null ->
                item(key = "hits-error") { InlineError(state.hitsErrorCode.orEmpty(), viewModel::retryHits) }
            state.hits.isEmpty() -> item(key = "hits-empty") { EmptyText(R.string.search_empty) }
            else -> {
                items(state.hits, key = { "hit-" + it.id }) { track -> TrackRow(track, onClick = { onPlay(track) }) }
                item(key = "hits-footer") {
                    PagingFooter(state.hitsLoading, state.hitsErrorCode != null, state.hitsHasMore, viewModel::retryHits)
                }
            }
        }
    }
}

private fun LazyListScope.introduction(state: AlbumState, viewModel: AlbumViewModel, onOpenAlbum: (String) -> Unit) {
    val html = state.introduction
    when {
        html != null -> item(key = "introduction") {
            val blocks = remember(html) { introBlocks(html) }
            if (blocks.isEmpty()) EmptyText(R.string.album_intro_empty)
            else RichIntroduction(blocks, Modifier.padding(horizontal = XmLayout.PagePadding, vertical = 8.dp))
        }
        state.introductionErrorCode != null ->
            item(key = "introduction-error") { InlineError(state.introductionErrorCode.orEmpty(), viewModel::retryIntroduction) }
        else -> item(key = "introduction-loading") { SkeletonRow() }
    }
    state.similarErrorCode?.let { code -> item(key = "similar-error") { InlineError(code, viewModel::retrySimilar) } }
    val similar = state.similar ?: return
    if (similar.anchorAlbums.isNotEmpty()) {
        item(key = "anchor-albums-title") { SectionHeader(stringResource(R.string.album_more_from_anchor), Modifier.padding(top = 8.dp)) }
        item(key = "anchor-albums") { AlbumShelf(similar.anchorAlbums, onOpenAlbum) }
    }
    if (similar.recommendations.isNotEmpty()) {
        item(key = "recommend-title") { SectionHeader(stringResource(R.string.album_listeners_also), Modifier.padding(top = 8.dp)) }
        item(key = "recommend") { AlbumShelf(similar.recommendations, onOpenAlbum) }
    }
}

@Composable
private fun AlbumShelf(albums: List<Album>, onOpenAlbum: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = XmLayout.PagePadding),
        horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
    ) {
        items(albums, key = { it.id }) { album -> AlbumCard(album, null) { onOpenAlbum(album.id) } }
    }
}

private fun LazyListScope.reviews(state: AlbumState, viewModel: AlbumViewModel) {
    // Always the first item, empty until the score arrives. An item that appears above the first visible one is pushed out of view.
    item(key = "score") { state.score?.let { ScoreSummary(it) } }
    item(key = "review-sort") { ReviewSort(state.newestReviews, viewModel::setNewestReviews) }
    when {
        state.reviewPage == 0 && state.reviewsErrorCode != null ->
            item(key = "reviews-error") { InlineError(state.reviewsErrorCode.orEmpty(), viewModel::retryReviews) }
        state.reviewPage == 0 -> item(key = "reviews-loading") { SkeletonRow() }
        state.reviews.isEmpty() -> item(key = "reviews-empty") { EmptyText(R.string.album_reviews_empty) }
        else -> items(state.reviews, key = { it.id }) { ReviewRow(it, viewModel::openReplies) }
    }
    if (state.reviewPage > 0 && state.reviews.isNotEmpty()) {
        item(key = "reviews-footer") {
            PagingFooter(state.reviewsLoading, state.reviewsErrorCode != null, state.reviewsHasMore, viewModel::retryReviews)
        }
    }
}

private enum class ToolbarMode { Browse, Search, Select }

/**
 * The episode count and order, then icons to reverse the order, search, and pick episodes to download. Search and
 * picking each open in place of them, on the same strip.
 */
@Composable
private fun EpisodeToolbar(state: AlbumState, total: Int, viewModel: AlbumViewModel, selection: EpisodeSelection?) {
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val mode = when {
        state.selecting && selection != null -> ToolbarMode.Select
        state.searchOpen -> ToolbarMode.Search
        else -> ToolbarMode.Browse
    }
    AnimatedContent(
        targetState = mode,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 4.dp),
        transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
        label = "episodeToolbar",
    ) { shown ->
        when (shown) {
            ToolbarMode.Search -> SearchRow(state.query, viewModel)
            ToolbarMode.Select -> if (selection != null) SelectionRow(state.selected.size, selection, viewModel::stopSelecting)
            ToolbarMode.Browse -> ToolbarRow(
                ascending = state.ascending,
                total = total,
                onOrder = { viewModel.selectOrder(!state.ascending) },
                onSearch = viewModel::openSearch,
                onSelect = if (selection != null) ({ viewModel.startSelecting() }) else null,
            )
        }
    }
}

@Composable
private fun ToolbarRow(ascending: Boolean, total: Int, onOrder: () -> Unit, onSearch: () -> Unit, onSelect: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().height(ToolbarHeight), verticalAlignment = Alignment.CenterVertically) {
        Text(
            listOf(
                stringResource(R.string.album_total, total),
                stringResource(if (ascending) R.string.album_order_oldest else R.string.album_order_newest),
            ).joinToString(" · "),
            Modifier.weight(1f).padding(start = 12.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onOrder) {
            Icon(Icons.Outlined.SwapVert, stringResource(if (ascending) R.string.album_order_to_newest else R.string.album_order_to_oldest))
        }
        IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, stringResource(R.string.album_search_hint)) }
        if (onSelect != null) IconButton(onClick = onSelect) { Icon(Icons.Outlined.Download, stringResource(R.string.album_select_download)) }
    }
}

/** While picking: close, how many are picked, pick by number, pick everything loaded, and download what is picked. */
@Composable
private fun SelectionRow(count: Int, selection: EpisodeSelection, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(ToolbarHeight), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, stringResource(R.string.action_cancel)) }
        Text(
            pluralStringResource(R.plurals.album_selected, count, count), Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = selection.onRange) { Icon(Icons.Outlined.FormatListNumbered, stringResource(R.string.album_select_range)) }
        IconButton(onClick = selection.onPickAll, enabled = selection.canPickAll) {
            Icon(
                Icons.Outlined.DoneAll,
                stringResource(if (selection.allPicked) R.string.album_select_none else R.string.history_select_all),
                tint = if (selection.allPicked) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            )
        }
        FilledTonalIconButton(onClick = selection.onDownload, enabled = count > 0) {
            Icon(Icons.Outlined.Download, stringResource(R.string.album_download_selected))
        }
    }
}

/** Episode numbers typed as `1,3,5-8`; a malformed entry is reported under the field and nothing is read. */
@Composable
private fun RangeDialog(total: Int, onDismiss: () -> Unit, onConfirm: (List<Int>) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    fun submit() {
        val numbers = try {
            DownloadRange.parse(text)
        } catch (_: XmlyException) {
            null
        }
        if (numbers == null) invalid = true else onConfirm(numbers)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.FormatListNumbered, contentDescription = null) },
        title = { Text(stringResource(R.string.album_range_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    invalid = false
                },
                singleLine = true,
                isError = invalid,
                placeholder = { Text(stringResource(R.string.album_range_hint)) },
                supportingText = {
                    Text(if (invalid) stringResource(R.string.download_error_range) else stringResource(R.string.album_total, total))
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
        },
        confirmButton = { TextButton(onClick = ::submit, enabled = text.isNotBlank()) { Text(stringResource(R.string.player_download)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun SearchRow(query: String, viewModel: AlbumViewModel) {
    Row(Modifier.fillMaxWidth().height(ToolbarHeight), verticalAlignment = Alignment.CenterVertically) {
        SearchField(query, viewModel::setQuery, viewModel::submitSearch, Modifier.weight(1f))
        TextButton(onClick = viewModel::closeSearch) { Text(stringResource(R.string.action_cancel)) }
    }
}

/** A filled pill with no outline, as the search bar on 发现 is: the query, a clear button, and the hint when empty. */
@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, onSubmit: () -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.height(SearchHeight).clip(CircleShape).background(colors.surfaceContainerHigh).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.weight(1f).focusRequester(focus),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(stringResource(R.string.album_search_hint), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                }
                inner()
            },
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQuery("") }, Modifier.size(40.dp)) {
                Icon(Icons.Filled.Close, stringResource(R.string.search_clear), Modifier.size(18.dp))
            }
        }
    }
}

/**
 * Number, title and details, then the episode's download state at the end. While picking, a checkbox takes the
 * state's place on episodes that can still be downloaded. A long press starts picking with this episode.
 */
@Composable
private fun EpisodeRow(
    track: Track,
    task: DownloadTask?,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onDownload: (() -> Unit)?,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    Row(
        Modifier.fillMaxWidth().combinedClickable(role = Role.Button, onLongClick = onLongClick, onClick = onClick)
            .padding(start = XmLayout.PagePadding, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            track.officialPosition?.toString().orEmpty(), Modifier.width(48.dp), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                track.durationSeconds.takeIf { it > 0 }?.let { formatDuration(it) },
                track.publishedAtMillis?.let { formatDate(it, locale) },
                track.playCount?.let { stringResource(R.string.rank_plays, formatCount(it)) },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        when {
            selecting && task.pickable() -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Checkbox(checked = selected, onCheckedChange = null)
            }
            selecting -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { DownloadGlyph(task) }
            onDownload != null -> DownloadButton(task, onDownload, onPause, onResume)
            else -> Spacer(Modifier.width(12.dp))
        }
    }
}

@Composable
private fun ReviewSort(newest: Boolean, onSelect: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = XmLayout.PagePadding), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !newest, onClick = { onSelect(false) }, label = { Text(stringResource(R.string.album_review_top)) })
        FilterChip(selected = newest, onClick = { onSelect(true) }, label = { Text(stringResource(R.string.album_review_newest)) })
    }
}

@Composable
private fun RepliesSheet(state: AlbumState, viewModel: AlbumViewModel) {
    val list = rememberLazyListState()
    ModalBottomSheet(onDismissRequest = viewModel::closeReplies) {
        LoadMoreEffect(
            { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index }, { list.layoutInfo.totalItemsCount },
            state.replyPage, state.repliesLoading, onLoadMore = viewModel::loadMoreReplies,
        )
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.8f), list, contentPadding = PaddingValues(bottom = XmLayout.SectionGap)) {
            item(key = "replies-title") {
                Text(
                    stringResource(R.string.album_replies_title),
                    Modifier.padding(horizontal = XmLayout.PagePadding, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(state.replies, key = { it.id }) { ReplyRow(it) }
            item(key = "replies-footer") {
                PagingFooter(state.repliesLoading, state.repliesErrorCode != null, state.repliesHasMore, viewModel::retryReplies)
            }
        }
    }
}

@Composable
private fun EmptyText(text: Int) {
    Text(
        stringResource(text), Modifier.fillMaxWidth().padding(XmLayout.FormPadding),
        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
    )
}

@Composable
private fun SkeletonRow() {
    Box(Modifier.fillMaxWidth().padding(XmLayout.PagePadding).height(96.dp).skeleton())
}

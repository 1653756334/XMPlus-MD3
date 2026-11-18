@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.xuleo.xmplus.md3.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.browse.AlbumEpisodeChunk
import com.xuleo.xmplus.md3.browse.AlbumState
import com.xuleo.xmplus.md3.browse.AlbumViewModel
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.model.Album
import com.xuleo.xmplus.md3.core.model.AlbumDetail
import com.xuleo.xmplus.md3.core.model.Track
import com.xuleo.xmplus.md3.ui.components.*
import kotlinx.coroutines.launch

/** The pages of an album, in the order of their tabs. */
private object AlbumTab {
    const val Introduction = 0
    const val Episodes = 1
    const val Reviews = 2
    const val Count = 3
}

/** The top bar starts to show once the header has moved this far, and is fully shown [RevealSpan] further on. */
private val RevealStart = 24.dp
private val RevealSpan = 72.dp

/** The toolbar over the episodes, and the search field that takes its place. */
private val ToolbarHeight = 48.dp
private val SearchHeight = 44.dp

/**
 * One album: a header that scrolls away over its blurred cover, then the tabs and their pages. The header comes back only
 * when its page is at the top, and the top bar stays clear until the header has gone. Playback, subscription and download
 * controls are not offered yet.
 */
@Composable
internal fun AlbumScreen(viewModel: AlbumViewModel, onBack: () -> Unit, onOpenAlbum: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.start() }
    val density = LocalDensity.current
    val statusBar = WindowInsets.statusBars.getTop(density).toFloat()
    val barHeight = with(density) { AlbumTopBarHeight.toPx() }
    val chrome = remember(statusBar, barHeight) {
        CollapsingChrome(minHeight = statusBar + barHeight, expandOnlyAtTop = true)
    }
    val revealStart = with(density) { RevealStart.toPx() }
    val revealSpan = with(density) { RevealSpan.toPx() }
    val reveal = { ((-chrome.offset - revealStart) / revealSpan).coerceIn(0f, 1f) }
    val motion = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val pager = rememberPagerState(pageCount = { AlbumTab.Count })
    val scope = rememberCoroutineScope()
    // The lists live here rather than in their pages, so each keeps its place and scroll to top can reach the one on show.
    val introList = rememberLazyListState()
    val episodesList = rememberLazyListState()
    val reviewsList = rememberLazyListState()
    fun listFor(page: Int): LazyListState = when (page) {
        AlbumTab.Episodes -> episodesList
        AlbumTab.Reviews -> reviewsList
        else -> introList
    }
    val scrollToTop: () -> Unit = {
        scope.launch {
            launch { listFor(pager.currentPage).animateScrollToItem(0) }
            launch { chrome.animateExpand(motion) }
        }
    }
    var showRange by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(pager.currentPage) { if (pager.currentPage == AlbumTab.Reviews) viewModel.ensureReviews() }
    val detail = state.detail
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (detail == null) {
            Column(Modifier.fillMaxSize()) {
                AlbumTopBar(title = "", progress = { 0f }, onBack = onBack, onScrollToTop = {})
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (state.headerLoading) ListSkeleton(Modifier.fillMaxSize())
                    else ErrorState(state.headerErrorCode.orEmpty(), viewModel::retryHeader)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().clipToBounds().nestedScroll(chrome.connection)) {
                Box(Modifier.collapsing(chrome)) {
                    AlbumHero(detail, Modifier.onSizeChanged { chrome.height = it.height.toFloat() })
                }
                AlbumTabRow(pager.currentPage) { index -> scope.launch { pager.animateScrollToPage(index) } }
                HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
                    when (page) {
                        AlbumTab.Episodes -> EpisodesPage(state, detail, viewModel, episodesList) { showRange = true }
                        AlbumTab.Reviews -> ReviewsPage(state, viewModel, reviewsList)
                        else -> IntroductionPage(state, viewModel, onOpenAlbum, introList)
                    }
                }
            }
            AlbumTopBar(
                title = detail.album.title,
                progress = reveal,
                onBack = onBack,
                onScrollToTop = scrollToTop,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
    if (showRange && detail != null) {
        RangeSheet(
            total = detail.album.trackCount,
            chunkStart = state.chunkStart,
            onSelect = { showRange = false; viewModel.selectChunk(it) },
            onDismiss = { showRange = false },
        )
    }
    if (state.replyTo != null) RepliesSheet(state, viewModel)
}

/** The tabs, without a divider, so the pages run straight on from them. */
@Composable
private fun AlbumTabRow(selected: Int, onSelect: (Int) -> Unit) {
    PrimaryTabRow(selectedTabIndex = selected, containerColor = Color.Transparent, divider = {}) {
        listOf(R.string.album_tab_introduction, R.string.album_tab_episodes, R.string.album_tab_reviews).forEachIndexed { index, label ->
            Tab(selected = selected == index, onClick = { onSelect(index) }, text = { Text(stringResource(label), maxLines = 1) })
        }
    }
}

/** A page's list, kept to the content column on wide windows. */
@Composable
private fun AlbumListPage(list: LazyListState, content: LazyListScope.() -> Unit) {
    // Overscroll would take the upward drag at the top before the header can come back, so the lists do without it.
    CompositionLocalProvider(LocalOverscrollFactory provides null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = XmLayout.ContentWidth).fillMaxSize(), list,
                contentPadding = PaddingValues(bottom = XmLayout.SectionGap), content = content,
            )
        }
    }
}

@Composable
private fun IntroductionPage(state: AlbumState, viewModel: AlbumViewModel, onOpenAlbum: (String) -> Unit, list: LazyListState) {
    AlbumListPage(list) { introduction(state, viewModel, onOpenAlbum) }
}

@Composable
private fun EpisodesPage(state: AlbumState, detail: AlbumDetail, viewModel: AlbumViewModel, list: LazyListState, onRange: () -> Unit) {
    // Search hits page on demand; the effect asks for the next page when the end is near.
    LoadMoreEffect(
        { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index }, { list.layoutInfo.totalItemsCount },
        state.searchOpen, state.hitPage, state.hitsLoading, onLoadMore = viewModel::loadMoreHits,
    )
    AlbumListPage(list) { episodes(state, detail, viewModel, onRange) }
}

@Composable
private fun ReviewsPage(state: AlbumState, viewModel: AlbumViewModel, list: LazyListState) {
    LoadMoreEffect(
        { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index }, { list.layoutInfo.totalItemsCount },
        state.reviewPage, state.reviewsLoading, state.newestReviews, onLoadMore = viewModel::loadMoreReviews,
    )
    AlbumListPage(list) { reviews(state, viewModel) }
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

private fun LazyListScope.episodes(state: AlbumState, detail: AlbumDetail, viewModel: AlbumViewModel, onRange: () -> Unit) {
    // The toolbar sticks under the tabs while the rows scroll beneath it.
    stickyHeader(key = "episode-toolbar") {
        EpisodeToolbar(state, detail.album.trackCount, viewModel, onRange)
    }
    val searching = state.searchOpen && (state.hitPage > 0 || state.hitsLoading || state.hitsErrorCode != null)
    if (searching) {
        when {
            state.hits.isEmpty() && state.hitsLoading -> item(key = "hits-loading") { SkeletonRow() }
            state.hits.isEmpty() && state.hitsErrorCode != null ->
                item(key = "hits-error") { InlineError(state.hitsErrorCode.orEmpty(), viewModel::retryHits) }
            state.hits.isEmpty() -> item(key = "hits-empty") { EmptyText(R.string.search_empty) }
            else -> {
                items(state.hits, key = { "hit-" + it.id }) { TrackRow(it) }
                item(key = "hits-footer") {
                    PagingFooter(state.hitsLoading, state.hitsErrorCode != null, state.hitsHasMore, viewModel::retryHits)
                }
            }
        }
    } else {
        when {
            state.episodesLoading -> item(key = "episodes-loading") { SkeletonRow() }
            state.episodesErrorCode != null ->
                item(key = "episodes-error") { InlineError(state.episodesErrorCode.orEmpty(), viewModel::retryChunk) }
            state.episodes.isEmpty() -> item(key = "episodes-empty") { EmptyText(R.string.album_episodes_empty) }
            else -> items(state.episodes, key = { it.id }) { EpisodeRow(it) }
        }
    }
}

private fun LazyListScope.reviews(state: AlbumState, viewModel: AlbumViewModel) {
    state.score?.let { score -> item(key = "score") { ScoreSummary(score) } }
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

/** The sort, search and chunk controls. Search opens in place of them, so the same strip stays pinned as the list scrolls. */
@Composable
private fun EpisodeToolbar(state: AlbumState, total: Int, viewModel: AlbumViewModel, onRange: () -> Unit) {
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(
        targetState = state.searchOpen,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp),
        transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
        label = "episodeToolbar",
    ) { searching ->
        if (searching) {
            SearchRow(state.query, viewModel)
        } else {
            ToolbarRow(
                ascending = state.ascending,
                total = total,
                onOrder = { viewModel.selectOrder(!state.ascending) },
                onSearch = viewModel::openSearch,
                onRange = onRange,
            )
        }
    }
}

@Composable
private fun ToolbarRow(ascending: Boolean, total: Int, onOrder: () -> Unit, onSearch: () -> Unit, onRange: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(ToolbarHeight), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onOrder) {
            Icon(Icons.Filled.Sort, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(if (ascending) R.string.album_order_oldest else R.string.album_order_newest), maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, stringResource(R.string.album_search_hint)) }
        TextButton(onClick = onRange, enabled = total > 0) {
            Text(stringResource(R.string.album_total, total), maxLines = 1)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, Modifier.size(18.dp))
        }
    }
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

@Composable
private fun EpisodeRow(track: Track) {
    val locale = LocalConfiguration.current.locales[0]
    Row(Modifier.fillMaxWidth().padding(horizontal = XmLayout.PagePadding, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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
private fun RangeSheet(total: Int, chunkStart: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = XmLayout.PagePadding).padding(bottom = XmLayout.SectionGap),
            verticalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
        ) {
            Text(stringResource(R.string.album_choose_range), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (start in 1..total step AlbumEpisodeChunk) {
                    val end = minOf(start + AlbumEpisodeChunk - 1, total)
                    FilterChip(
                        selected = start == chunkStart,
                        onClick = { onSelect(start) },
                        label = { Text(stringResource(R.string.album_range, start, end)) },
                    )
                }
            }
        }
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
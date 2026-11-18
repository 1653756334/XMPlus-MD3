@file:OptIn(ExperimentalMaterial3Api::class)

package com.xuleo.xmplus.md3.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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

/** The three tabs of an album page. */
private object AlbumTab {
    const val Introduction = 0
    const val Episodes = 1
    const val Reviews = 2
}

/**
 * One album: its header, then 简介, 节目 and 评价. The controls that need playback, subscription or download are not
 * offered until those features exist.
 */
@Composable
internal fun AlbumScreen(viewModel: AlbumViewModel, onBack: () -> Unit, onOpenAlbum: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.start() }
    var tab by rememberSaveable { mutableStateOf(AlbumTab.Introduction) }
    var showRange by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(tab) { if (tab == AlbumTab.Reviews) viewModel.ensureReviews() }
    val list = rememberLazyListState()
    val detail = state.detail
    BrowsePage(title = detail?.album?.title.orEmpty(), onBack = onBack) {
        when {
            detail == null && state.headerLoading -> ListSkeleton(Modifier.weight(1f))
            detail == null -> ErrorState(state.headerErrorCode.orEmpty(), viewModel::retryHeader, Modifier.weight(1f))
            else -> AlbumList(state, detail, viewModel, tab, { tab = it }, list, { showRange = true }, onOpenAlbum)
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

@Composable
private fun ColumnScope.AlbumList(
    state: AlbumState,
    detail: AlbumDetail,
    viewModel: AlbumViewModel,
    tab: Int,
    onTab: (Int) -> Unit,
    list: LazyListState,
    onRange: () -> Unit,
    onOpenAlbum: (String) -> Unit,
) {
    // Search hits and reviews page on demand; the effect asks for the next page when the end is near.
    LoadMoreEffect(
        { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index }, { list.layoutInfo.totalItemsCount },
        tab, state.searchOpen, state.hitPage, state.hitsLoading, state.reviewPage, state.reviewsLoading, state.newestReviews,
        onLoadMore = {
            if (tab == AlbumTab.Episodes) viewModel.loadMoreHits() else if (tab == AlbumTab.Reviews) viewModel.loadMoreReviews()
        },
    )
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = XmLayout.ContentWidth).fillMaxSize(), list, contentPadding = PaddingValues(bottom = XmLayout.SectionGap)) {
            item(key = "header") { AlbumHeader(detail) }
            item(key = "tabs") { AlbumTabs(tab, onTab) }
            when (tab) {
                AlbumTab.Episodes -> episodes(state, detail, viewModel, onRange)
                AlbumTab.Reviews -> reviews(state, viewModel)
                else -> introduction(state, viewModel, onOpenAlbum)
            }
        }
    }
}

@Composable
private fun AlbumTabs(selected: Int, onSelect: (Int) -> Unit) {
    PrimaryTabRow(selectedTabIndex = selected, containerColor = MaterialTheme.colorScheme.surface) {
        listOf(R.string.album_tab_introduction, R.string.album_tab_episodes, R.string.album_tab_reviews).forEachIndexed { index, label ->
            Tab(selected = selected == index, onClick = { onSelect(index) }, text = { Text(stringResource(label), maxLines = 1) })
        }
    }
}

private fun LazyListScope.introduction(state: AlbumState, viewModel: AlbumViewModel, onOpenAlbum: (String) -> Unit) {
    item(key = "introduction") { Introduction(state, viewModel::retryIntroduction) }
    state.similarErrorCode?.let { code ->
        item(key = "similar-error") { InlineError(code, viewModel::retrySimilar) }
    }
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
private fun Introduction(state: AlbumState, onRetry: () -> Unit) {
    val html = state.introduction
    val text = remember(html) { html?.let(::plainText).orEmpty() }
    when {
        html != null -> Text(
            text.ifEmpty { stringResource(R.string.album_intro_empty) },
            Modifier.fillMaxWidth().padding(horizontal = XmLayout.PagePadding, vertical = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        state.introductionErrorCode != null -> InlineError(state.introductionErrorCode.orEmpty(), onRetry)
        else -> Box(Modifier.fillMaxWidth().padding(XmLayout.PagePadding).height(96.dp).skeleton())
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
    item(key = "episode-toolbar") {
        EpisodeToolbar(
            ascending = state.ascending,
            total = detail.album.trackCount,
            onOrder = { viewModel.selectOrder(!state.ascending) },
            onSearch = viewModel::openSearch,
            onRange = onRange,
        )
    }
    if (state.searchOpen) {
        item(key = "episode-search") {
            SearchField(state.query, viewModel::setQuery, viewModel::submitSearch, viewModel::closeSearch)
        }
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

@Composable
private fun EpisodeToolbar(ascending: Boolean, total: Int, onOrder: () -> Unit, onSearch: () -> Unit, onRange: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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
private fun SearchField(query: String, onQuery: (String) -> Unit, onSubmit: () -> Unit, onClose: () -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.album_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.action_close)) } },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        modifier = Modifier.fillMaxWidth().padding(horizontal = XmLayout.PagePadding, vertical = 4.dp),
    )
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
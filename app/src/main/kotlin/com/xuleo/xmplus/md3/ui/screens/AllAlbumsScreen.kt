package com.xuleo.xmplus.md3.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.browse.AllAlbumsState
import com.xuleo.xmplus.md3.browse.AllAlbumsViewModel
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.designsystem.XmPreview
import com.xuleo.xmplus.md3.core.designsystem.XmPreviews
import com.xuleo.xmplus.md3.core.model.Album
import com.xuleo.xmplus.md3.core.model.AlbumStats
import com.xuleo.xmplus.md3.core.model.BrowseCategory
import com.xuleo.xmplus.md3.ui.components.*

/** The sorts the site offers, in the order shown. The number is what the server expects. */
internal val AlbumSorts = listOf(
    1 to R.string.sort_default,
    2 to R.string.sort_recent,
    3 to R.string.sort_plays,
)

@Composable
fun AllAlbumsScreen(viewModel: AllAlbumsViewModel, category: Int, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(category) { viewModel.ensureStarted(category) }
    AllAlbumsContent(state, onBack, viewModel::select, viewModel::refresh, viewModel::loadMore, viewModel::retry)
}

@Composable
internal fun AllAlbumsContent(
    state: AllAlbumsState,
    onBack: () -> Unit,
    onSelect: (category: Int?, sort: Int?) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    BrowsePage(stringResource(R.string.browse_all_albums), onBack) {
        if (state.categories.isNotEmpty()) {
            Chips {
                item { FilterChip(state.category == 0, { onSelect(0, null) }, { Text(stringResource(R.string.browse_category_all)) }) }
                items(state.categories, key = { it.id }) { category ->
                    FilterChip(state.category == category.id, { onSelect(category.id, null) }, { Text(category.title, maxLines = 1) })
                }
            }
        }
        Chips {
            items(AlbumSorts, key = { it.first }) { (sort, label) ->
                FilterChip(state.sort == sort, { onSelect(null, sort) }, { Text(stringResource(label), maxLines = 1) })
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !state.loaded && state.errorCode != null -> ErrorState(state.errorCode, onRetry)
                !state.loaded -> GridSkeleton()
                state.albums.isEmpty() -> Text(
                    stringResource(R.string.browse_empty), Modifier.fillMaxSize().padding(XmLayout.FormPadding).wrapContentSize(),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                else -> PullToRefreshBox(state.refreshing, onRefresh) { AlbumGrid(state, onLoadMore, onRetry) }
            }
        }
    }
}

@Composable
private fun Chips(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = XmLayout.PagePadding, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun AlbumGrid(state: AllAlbumsState, onLoadMore: () -> Unit, onRetry: () -> Unit) {
    val grid = rememberLazyGridState()
    LoadMoreEffect({ grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index }, { grid.layoutInfo.totalItemsCount }, grid, state.page, state.loading,
        onLoadMore = onLoadMore)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            GridCells.Adaptive(112.dp), Modifier.widthIn(max = 960.dp).fillMaxSize(), grid,
            contentPadding = PaddingValues(XmLayout.PagePadding),
            horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
            verticalArrangement = Arrangement.spacedBy(XmLayout.SectionGap - 8.dp),
        ) {
            items(state.albums, key = { it.id }) { AlbumCard(it, state.stats[it.id], Modifier.fillMaxWidth()) }
            item(key = "footer", span = { GridItemSpan(maxLineSpan) }) {
                PagingFooter(state.loading && !state.refreshing, state.errorCode != null, state.hasMore, onRetry)
            }
        }
    }
}

private val sampleCategories = listOf(BrowseCategory(1, "Books"), BrowseCategory(2, "Music"))
private val sampleAlbums = (1..12).map { Album(it.toString(), "Album number $it with a longer title", "Anchor $it", "", null, 20) }

@XmPreviews
@Composable
private fun AllAlbumsPreview() = XmPreview {
    AllAlbumsContent(
        AllAlbumsState(sampleCategories, albums = sampleAlbums, stats = sampleAlbums.associate { it.id to AlbumStats(playCount = 123_456) }, page = 1, hasMore = true),
        {}, { _, _ -> }, {}, {}, {},
    )
}

@XmPreviews
@Composable
private fun AllAlbumsLoadingPreview() = XmPreview { AllAlbumsContent(AllAlbumsState(sampleCategories), {}, { _, _ -> }, {}, {}, {}) }
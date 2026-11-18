package com.xuleo.xmplus.md3.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.model.Album
import com.xuleo.xmplus.md3.core.model.AlbumStats

/** Albums in a grid that pages itself: it asks for more near the end and closes with a footer. */
@Composable
fun AlbumGrid(
    albums: List<Album>,
    stats: Map<String, AlbumStats>,
    page: Int,
    loading: Boolean,
    failed: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    val grid = rememberLazyGridState()
    LoadMoreEffect({ grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index }, { grid.layoutInfo.totalItemsCount }, grid, page, loading,
        onLoadMore = onLoadMore)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            GridCells.Adaptive(112.dp), Modifier.widthIn(max = 960.dp).fillMaxSize(), grid,
            contentPadding = PaddingValues(XmLayout.PagePadding),
            horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
            verticalArrangement = Arrangement.spacedBy(XmLayout.SectionGap - 8.dp),
        ) {
            items(albums, key = { it.id }) { AlbumCard(it, stats[it.id], Modifier.fillMaxWidth()) }
            item(key = "footer", span = { GridItemSpan(maxLineSpan) }) { PagingFooter(loading, failed, hasMore, onRetry) }
        }
    }
}
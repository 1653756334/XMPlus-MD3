package com.xuleo.xmplus.md3.ui.screens

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.ui.icons.XmIcons
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.browse.CategoriesState
import com.xuleo.xmplus.md3.browse.CategoriesViewModel
import com.xuleo.xmplus.md3.browse.ChannelList
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.designsystem.XmPreview
import com.xuleo.xmplus.md3.core.designsystem.XmPreviews
import com.xuleo.xmplus.md3.core.model.CategoryChannel
import com.xuleo.xmplus.md3.core.model.CategoryGroup
import com.xuleo.xmplus.md3.ui.components.*

/** Opens a category's albums: a channel of [groupId], or the group's own 精选 when [channel] is null. */
internal typealias OpenCategory = (group: CategoryGroup, channel: CategoryChannel?) -> Unit

@Composable
fun CategoriesView(viewModel: CategoriesViewModel, onOpen: OpenCategory) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.ensureLoaded() }
    CategoriesContent(state, viewModel::selectGroup, viewModel::refresh, viewModel::loadMore, viewModel::retry, onOpen)
}

@Composable
internal fun CategoriesContent(
    state: CategoriesState,
    onSelectGroup: (Int) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpen: OpenCategory,
) {
    Column(Modifier.fillMaxSize()) {
        val groups = state.groups
        val group = state.group
        val list = state.list
        if (!groups.isNullOrEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = XmLayout.PagePadding, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(groups, key = { it.id }) { FilterChip(it.id == state.groupId, { onSelectGroup(it.id) }, { Text(it.title, maxLines = 1) }) }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.groupsError != null && groups == null -> ErrorState(state.groupsError, onRetry)
                groups == null -> ListSkeleton()
                group == null || list == null || (list.errorCode != null && !list.loaded) ->
                    if (list?.errorCode != null) ErrorState(list.errorCode, onRetry)
                    else if (group == null) EmptyMessage() else ListSkeleton()
                else -> PullToRefreshBox(state.refreshing, onRefresh) { ChannelGrid(group, list, onLoadMore, onRetry, onOpen) }
            }
        }
    }
}

@Composable
private fun EmptyMessage() {
    Box(Modifier.fillMaxSize().padding(XmLayout.FormPadding), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.discover_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChannelGrid(group: CategoryGroup, list: ChannelList, onLoadMore: () -> Unit, onRetry: () -> Unit, onOpen: OpenCategory) {
    val grid = rememberLazyGridState()
    LoadMoreEffect({ grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index }, { grid.layoutInfo.totalItemsCount }, grid, list.page, list.loading,
        onLoadMore = onLoadMore)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            GridCells.Adaptive(150.dp), Modifier.widthIn(max = 960.dp).fillMaxSize(), grid,
            contentPadding = PaddingValues(XmLayout.PagePadding),
            horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
            verticalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
        ) {
            item(key = "featured", span = { GridItemSpan(maxLineSpan) }) {
                FilledTonalButton({ onOpen(group, null) }) {
                    Icon(XmIcons.Outlined.AutoAwesome, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.category_featured, group.title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            items(list.items, key = { it.channelId }) { channel -> ChannelCard(channel) { onOpen(group, channel) } }
            item(key = "footer", span = { GridItemSpan(maxLineSpan) }) {
                PagingFooter(list.loading, list.errorCode != null, list.hasMore, onRetry)
            }
        }
    }
}

@Composable
private fun ChannelCard(channel: CategoryChannel, onClick: () -> Unit) {
    Surface(onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (channel.coverUrl != null) Cover(channel.coverUrl, Modifier.size(40.dp), MaterialTheme.shapes.small)
            Text(channel.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val sampleGroup = CategoryGroup(1, "Books", emptyList())
private val sampleChannels = (1..9).map { CategoryChannel(it, "Channel $it", it, null) }

@XmPreviews
@Composable
private fun CategoriesPreview() = XmPreview {
    CategoriesContent(
        CategoriesState(listOf(sampleGroup, CategoryGroup(2, "Music", emptyList())), groupId = 1, lists = mapOf(1 to ChannelList(sampleChannels, 1))),
        {}, {}, {}, {}, { _, _ -> },
    )
}
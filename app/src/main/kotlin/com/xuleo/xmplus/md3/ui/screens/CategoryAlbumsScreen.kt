package com.xuleo.xmplus.md3.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.browse.CategoryAlbumsState
import com.xuleo.xmplus.md3.browse.CategoryAlbumsViewModel
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.designsystem.XmPreview
import com.xuleo.xmplus.md3.core.designsystem.XmPreviews
import com.xuleo.xmplus.md3.core.model.Album
import com.xuleo.xmplus.md3.core.model.CategoryChannel
import com.xuleo.xmplus.md3.core.model.CategoryChannelInfo
import com.xuleo.xmplus.md3.core.model.CategoryDimension
import com.xuleo.xmplus.md3.core.model.CategoryValue
import com.xuleo.xmplus.md3.ui.components.*

private val CategorySorts = listOf(
    1 to R.string.sort_default,
    2 to R.string.sort_newest,
    3 to R.string.sort_plays,
)

@Composable
fun CategoryAlbumsScreen(viewModel: CategoryAlbumsViewModel, groupId: Int, groupTitle: String, channelId: Int?, channelTitle: String?, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(groupId, channelId) { viewModel.ensureStarted(groupId, channelId) }
    val title = channelTitle ?: stringResource(R.string.category_featured, groupTitle)
    CategoryAlbumsContent(
        state, title, onBack, viewModel::selectSort, viewModel::applyFilters, viewModel::refresh, viewModel::loadMore, viewModel::retry, viewModel::retryInfo,
    )
}

@Composable
internal fun CategoryAlbumsContent(
    state: CategoryAlbumsState,
    title: String,
    onBack: () -> Unit,
    onSort: (Int) -> Unit,
    onFilters: (Map<Int, Int>) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onRetryInfo: () -> Unit,
) {
    var filtering by rememberSaveable { mutableStateOf(false) }
    BrowsePage(title, onBack) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = XmLayout.PagePadding, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(CategorySorts, key = { it.first }) { (sort, label) ->
                FilterChip(state.sort == sort, { onSort(sort) }, { Text(stringResource(label), maxLines = 1) })
            }
            val info = state.info
            if (info != null && info.dimensions.isNotEmpty()) {
                item(key = "filter") {
                    val label = if (state.filters.isEmpty()) stringResource(R.string.category_filter)
                    else stringResource(R.string.category_filter_count, state.filters.size)
                    AssistChip({ filtering = true }, { Text(label, maxLines = 1) },
                        leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null, Modifier.size(18.dp)) })
                }
            } else if (state.infoFailed) {
                item(key = "filter") { AssistChip(onRetryInfo, { Text(stringResource(R.string.category_filter_retry), maxLines = 1) }) }
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
                else -> PullToRefreshBox(state.refreshing, onRefresh) {
                    AlbumGrid(state.albums, emptyMap(), state.page, state.loading && !state.refreshing, state.errorCode != null, state.hasMore, onLoadMore, onRetry)
                }
            }
        }
    }
    val info = state.info
    if (filtering && info != null) FilterSheet(info, state.filters, { filtering = false; onFilters(it) }, { filtering = false })
}

/** The channel's filter rows. A row under a chosen value appears only while that value is chosen. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(info: CategoryChannelInfo, applied: Map<Int, Int>, onApply: (Map<Int, Int>) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(applied) }
    ModalBottomSheet(onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = XmLayout.FormPadding).padding(bottom = XmLayout.PagePadding),
            verticalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
        ) {
            for (dimension in info.reachableDimensions(draft)) {
                Text(dimension.title, style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(dimension.id !in draft, { draft = info.selectValue(draft, dimension.id, null) }, { Text(stringResource(R.string.browse_category_all)) })
                    for (value in dimension.values) {
                        FilterChip(draft[dimension.id] == value.id, { draft = info.selectValue(draft, dimension.id, value.id) }, { Text(value.title) })
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton({ draft = emptyMap() }) { Text(stringResource(R.string.filter_reset)) }
                Button({ onApply(info.pruneSelections(draft)) }) { Text(stringResource(R.string.filter_apply)) }
            }
        }
    }
}

private val sampleInfo = CategoryChannelInfo(
    CategoryChannel(1, "Channel", 1, null),
    listOf(CategoryDimension(1, "Region", listOf(CategoryValue(1, "All regions", emptyList())))),
)

@XmPreviews
@Composable
private fun CategoryAlbumsPreview() = XmPreview {
    CategoryAlbumsContent(
        CategoryAlbumsState(info = sampleInfo, albums = (1..12).map { Album("$it", "Album number $it", "Anchor", "", null, 10) }, page = 1, hasMore = true),
        "Books", {}, {}, {}, {}, {}, {}, {},
    )
}
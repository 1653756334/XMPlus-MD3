package com.xuleo.xmplus.md3.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.browse.RankCharts
import com.xuleo.xmplus.md3.browse.RankingsState
import com.xuleo.xmplus.md3.browse.RankingsViewModel
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.designsystem.XmPreview
import com.xuleo.xmplus.md3.core.designsystem.XmPreviews
import com.xuleo.xmplus.md3.core.model.Album
import com.xuleo.xmplus.md3.core.model.AlbumStats
import com.xuleo.xmplus.md3.core.model.AnchorProfile
import com.xuleo.xmplus.md3.core.model.RankBlock
import com.xuleo.xmplus.md3.core.model.RankCategory
import com.xuleo.xmplus.md3.core.model.RankTab
import com.xuleo.xmplus.md3.ui.components.*

@Composable
fun RankingsView(viewModel: RankingsViewModel, onOpenAlbum: (String) -> Unit = {}, onOpenAnchor: (String) -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.ensureLoaded() }
    RankingsContent(state, viewModel::selectCategory, viewModel::selectTab, viewModel::refresh, viewModel::retry, onOpenAlbum, onOpenAnchor)
}

@Composable
internal fun RankingsContent(
    state: RankingsState,
    onCategory: (Int) -> Unit,
    onTab: (Int) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onOpenAlbum: (String) -> Unit = {},
    onOpenAnchor: (String) -> Unit = {},
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        val categories = state.categories
        val category = state.category
        if (categories != null && category != null) {
            // One row: the category opens a sheet with the others, and the tabs of the chosen category follow it.
            Chips {
                item(key = "category") {
                    AssistChip({ choosing = true }, { Text(category.title, maxLines = 1) },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, Modifier.size(18.dp)) })
                }
                items(category.tabs, key = { it.rankingId }) { FilterChip(it.rankingId == state.rankingId, { onTab(it.rankingId) }, { Text(it.title, maxLines = 1) }) }
            }
            if (choosing) {
                CategorySheet(categories, state.categoryId,
                    onPick = { choosing = false; onCategory(it) }, onDismiss = { choosing = false })
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val charts = state.current
            when {
                state.errorCode != null && categories == null -> ErrorState(state.errorCode, onRetry)
                categories == null -> ListSkeleton()
                categories.isEmpty() -> Message(R.string.discover_empty)
                charts?.blocks != null -> PullToRefreshBox(state.refreshing, onRefresh) { Charts(charts.blocks, state.tab, onOpenAlbum, onOpenAnchor) }
                charts?.errorCode != null -> ErrorState(charts.errorCode, onRetry)
                else -> ListSkeleton()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CategorySheet(categories: List<RankCategory>, selected: Int?, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismiss) {
        Column(
            Modifier.padding(horizontal = XmLayout.FormPadding).padding(bottom = XmLayout.PagePadding),
            verticalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
        ) {
            Text(stringResource(R.string.discover_mode_categories), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    FilterChip(category.id == selected, { onPick(category.id) }, { Text(category.title, maxLines = 1) })
                }
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
private fun Message(text: Int) {
    Box(Modifier.fillMaxSize().padding(XmLayout.FormPadding), contentAlignment = Alignment.Center) {
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Charts(blocks: List<RankBlock>, tab: RankTab?, onOpenAlbum: (String) -> Unit, onOpenAnchor: (String) -> Unit) {
    val shown = blocks.filter { (it.contentType == 2 && it.albums.isNotEmpty()) || (it.contentType == 3 && it.anchors.isNotEmpty()) }
    if (shown.isEmpty()) {
        Message(R.string.discover_empty)
        return
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = XmLayout.ContentWidth).fillMaxSize(),
            contentPadding = PaddingValues(vertical = XmLayout.PagePadding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            shown.forEachIndexed { index, block ->
                val title = block.title.ifEmpty { if (index == 0) tab?.title.orEmpty() else "" }
                if (title.isNotEmpty()) item(key = "title-${block.rankingId}-$index") { SectionHeader(title, Modifier.padding(top = if (index == 0) 0.dp else XmLayout.SectionGap)) }
                if (block.contentType == 2) {
                    items(block.albums, key = { "album-${block.rankingId}-$index-${it.id}" }) { album ->
                        RankedAlbumRow(block.positions[album.id], album, block.stats[album.id]) { onOpenAlbum(album.id) }
                    }
                } else {
                    items(block.anchors, key = { "anchor-${block.rankingId}-$index-${it.uid}" }) { anchor ->
                        AnchorRow(anchor, position = block.positions[anchor.uid]) { onOpenAnchor(anchor.uid) }
                    }
                }
            }
        }
    }
}

private val sampleAlbums = (1..4).map { Album("a$it", "Chart album $it", "Anchor $it", "", null, 30) }
private val sampleBlock = RankBlock(
    1, "Hot", 2, sampleAlbums, emptyList(), sampleAlbums.associate { it.id to AlbumStats(score = 9.2, playCount = 1_234_567) },
    sampleAlbums.withIndex().associate { it.value.id to it.index + 1 },
)
private val sampleAnchorBlock = RankBlock(
    2, "Anchors", 3, emptyList(), listOf(AnchorProfile("u1", "Anchor One", verified = true, fans = 98_765)), emptyMap(), mapOf("u1" to 1),
)

@XmPreviews
@Composable
private fun RankingsPreview() = XmPreview {
    val tab = RankTab(1, 10, "Hot")
    RankingsContent(
        RankingsState(
            categories = listOf(RankCategory(1, "Books", listOf(tab))), categoryId = 1, rankingId = 10,
            charts = mapOf(10 to RankCharts(listOf(sampleBlock, sampleAnchorBlock))),
        ),
        {}, {}, {}, {},
    )
}

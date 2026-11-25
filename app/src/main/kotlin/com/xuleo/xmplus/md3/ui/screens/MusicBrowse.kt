package com.xuleo.xmplus.md3.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.model.MusicSheet
import com.xuleo.xmplus.md3.core.plugin.SheetTag
import com.xuleo.xmplus.md3.core.plugin.SheetTags
import com.xuleo.xmplus.md3.music.MusicBrowseViewModel
import com.xuleo.xmplus.md3.music.SheetSquare
import com.xuleo.xmplus.md3.music.TopLists
import com.xuleo.xmplus.md3.ui.components.ErrorState
import com.xuleo.xmplus.md3.ui.components.GridSkeleton
import com.xuleo.xmplus.md3.ui.components.ListSkeleton
import com.xuleo.xmplus.md3.ui.components.LoadMoreEffect
import com.xuleo.xmplus.md3.ui.components.PagingFooter
import com.xuleo.xmplus.md3.ui.components.SheetCard
import com.xuleo.xmplus.md3.ui.components.TopListRow
import com.xuleo.xmplus.md3.ui.icons.XmIcons

/** 推荐: the plugin's default list first, as MusicFree opens it; pinned tags beside it, every tag in a sheet. */
@Composable
internal fun RecommendPane(platform: String, browse: MusicBrowseViewModel, onOpenSheet: (MusicSheet) -> Unit) {
    LaunchedEffect(platform) { browse.openSquare(platform) }
    val square = browse.squares.collectAsStateWithLifecycle().value[platform] ?: SheetSquare(loading = true)
    var tagsOpen by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TagRow(square, onSelect = { browse.selectTag(platform, it) }) {
            if (square.tags == null && square.tagsFailed) browse.retryTags(platform) else tagsOpen = true
        }
        when {
            square.sheets.isNotEmpty() -> SheetGrid(square, { browse.loadMoreSheets(platform) }, { browse.retrySheets(platform) }, onOpenSheet)
            square.failed -> ErrorState("generic", { browse.retrySheets(platform) })
            square.loading || square.page == 0 -> GridSkeleton()
            else -> MusicNothing()
        }
    }
    val tags = square.tags
    if (tagsOpen && tags != null) {
        TagSheet(tags, square.tag, onSelect = { browse.selectTag(platform, it); tagsOpen = false }) { tagsOpen = false }
    }
}

/** 榜单: the plugin's groups, each ranking a row. */
@Composable
internal fun RankingPane(platform: String, browse: MusicBrowseViewModel, onOpenSheet: (MusicSheet) -> Unit) {
    LaunchedEffect(platform) { browse.openTopLists(platform) }
    val lists = browse.topLists.collectAsStateWithLifecycle().value[platform] ?: TopLists(loading = true)
    val groups = lists.groups
    when {
        groups == null && lists.failed -> ErrorState("generic", { browse.retryTopLists(platform) })
        groups == null -> ListSkeleton()
        groups.isEmpty() -> MusicNothing()
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
            groups.forEachIndexed { index, group ->
                if (group.title.isNotBlank()) {
                    item(key = "title:$index") {
                        Text(
                            group.title,
                            Modifier.padding(horizontal = XmLayout.PagePadding).padding(top = if (index == 0) 8.dp else 20.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                itemsIndexed(group.sheets, key = { row, _ -> "$index:$row" }) { _, sheet -> TopListRow(sheet) { onOpenSheet(sheet) } }
            }
        }
    }
}

@Composable
private fun TagRow(square: SheetSquare, onSelect: (SheetTag?) -> Unit, onAll: () -> Unit) {
    val pinned = square.tags?.pinned.orEmpty()
    val chosen = square.tag
    // A tag picked from the sheet stays in view, next to the default list.
    val picked = chosen?.takeIf { tag -> pinned.none { it.id == tag.id } }
    val hasMore = square.tags?.groups?.isNotEmpty() == true || square.tagsFailed
    LazyRow(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = XmLayout.PagePadding, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { FilterChip(chosen == null, { onSelect(null) }, { Text(stringResource(R.string.music_tag_default)) }) }
        if (picked != null) item { FilterChip(true, {}, { Text(picked.title, maxLines = 1) }) }
        items(pinned) { tag -> FilterChip(chosen?.id == tag.id, { onSelect(tag) }, { Text(tag.title, maxLines = 1) }) }
        if (hasMore) {
            item {
                FilterChip(
                    false, onAll, { Text(stringResource(R.string.music_tag_all)) },
                    trailingIcon = { Icon(XmIcons.Filled.ArrowDropDown, contentDescription = null, Modifier.size(18.dp)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TagSheet(tags: SheetTags, chosen: SheetTag?, onSelect: (SheetTag) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState()) {
        LazyColumn(
            contentPadding = PaddingValues(start = XmLayout.PagePadding, end = XmLayout.PagePadding, bottom = XmLayout.SectionGap),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(tags.groups) { group ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (group.title.isNotBlank()) {
                        Text(group.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.tags.forEach { tag ->
                            FilterChip(chosen?.id == tag.id, { onSelect(tag) }, { Text(tag.title, maxLines = 1) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetGrid(square: SheetSquare, onLoadMore: () -> Unit, onRetry: () -> Unit, onOpen: (MusicSheet) -> Unit) {
    val grid = rememberLazyGridState()
    LoadMoreEffect({ grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index }, { grid.layoutInfo.totalItemsCount }, grid, square.page, square.loading,
        onLoadMore = onLoadMore)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            GridCells.Adaptive(108.dp), Modifier.widthIn(max = 960.dp).fillMaxSize(), grid,
            contentPadding = PaddingValues(start = XmLayout.PagePadding, end = XmLayout.PagePadding, top = 8.dp, bottom = XmLayout.PagePadding),
            horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
            verticalArrangement = Arrangement.spacedBy(XmLayout.SectionGap - 8.dp),
        ) {
            items(square.sheets, key = { it.id }) { SheetCard(it, Modifier.fillMaxWidth()) { onOpen(it) } }
            item(key = "footer", span = { GridItemSpan(maxLineSpan) }) { PagingFooter(square.loading, square.failed, !square.isEnd, onRetry) }
        }
    }
}

/** A list the plugin returned empty. */
@Composable
private fun MusicNothing() {
    Box(Modifier.fillMaxSize().padding(XmLayout.FormPadding), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.music_nothing), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
    }
}

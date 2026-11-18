package com.xuleo.xmplus.md3.ui.screens

import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.browse.CategoriesViewModel
import com.xuleo.xmplus.md3.browse.RankingsViewModel
import com.xuleo.xmplus.md3.browse.ChannelState
import com.xuleo.xmplus.md3.browse.ChannelsState
import com.xuleo.xmplus.md3.browse.DiscoverState
import com.xuleo.xmplus.md3.browse.DiscoverViewModel
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.designsystem.XmPreview
import com.xuleo.xmplus.md3.core.designsystem.XmPreviews
import com.xuleo.xmplus.md3.core.model.Album
import com.xuleo.xmplus.md3.core.model.AlbumSection
import com.xuleo.xmplus.md3.core.model.AlbumStats
import com.xuleo.xmplus.md3.core.model.HomeChannel
import com.xuleo.xmplus.md3.core.model.HomeContent
import com.xuleo.xmplus.md3.core.model.HomeTab
import com.xuleo.xmplus.md3.core.model.HotWord
import com.xuleo.xmplus.md3.core.model.HotRanking
import com.xuleo.xmplus.md3.ui.components.*
import kotlinx.coroutines.launch

/** What the screen can ask for beyond its own state: loading, and opening other pages. */
internal class DiscoverActions(
    val onSearch: () -> Unit = {},
    val onReload: () -> Unit = {},
    val onLoad: (pageId: Int, refresh: Boolean) -> Unit = { _, _ -> },
    val onSelectTab: (pageId: Int, tabId: Int) -> Unit = { _, _ -> },
    val onRetryRanking: (pageId: Int) -> Unit = {},
    val onAllAlbums: (categoryId: Int) -> Unit = {},
    val onFullRanking: (pageId: Int) -> Unit = {},
)

/** Where 发现 can send the user. */
internal class DiscoverLinks(
    val onSearch: () -> Unit = {},
    val onAllAlbums: (categoryId: Int) -> Unit = {},
    val onFullRanking: (pageId: Int) -> Unit = {},
    val onCategory: OpenCategory = { _, _ -> },
)

internal enum class DiscoverMode(@StringRes val label: Int) {
    Recommend(R.string.discover_mode_recommend),
    Categories(R.string.discover_mode_categories),
    Rankings(R.string.discover_mode_rankings),
}

@Composable
internal fun DiscoverScreen(
    viewModel: DiscoverViewModel,
    categories: CategoriesViewModel,
    rankings: RankingsViewModel,
    hotWords: List<HotWord>,
    links: DiscoverLinks,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = remember(viewModel, links) {
        DiscoverActions(links.onSearch, viewModel::reload, viewModel::load, viewModel::selectTab, viewModel::retryRanking, links.onAllAlbums, links.onFullRanking)
    }
    DiscoverContent(state, hotWords, actions) { mode ->
        when (mode) {
            DiscoverMode.Categories -> CategoriesView(categories, links.onCategory)
            DiscoverMode.Rankings -> RankingsView(rankings)
            DiscoverMode.Recommend -> Unit
        }
    }
}

/** 发现: the search bar, a switch between the modes, and the mode's content. [other] draws every mode but 推荐. */
@Composable
internal fun DiscoverContent(
    state: DiscoverState,
    hotWords: List<HotWord>,
    actions: DiscoverActions,
    other: @Composable (DiscoverMode) -> Unit = {},
) {
    var mode by rememberSaveable { mutableStateOf(DiscoverMode.Recommend) }
    val holder = rememberSaveableStateHolder()
    // The search bar and mode row scroll away with the content; the channel tabs below them stay pinned.
    val chrome = remember { CollapsingChrome() }
    LaunchedEffect(mode) { chrome.expand() }
    Column(
        Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .clipToBounds()
            .nestedScroll(chrome.connection),
    ) {
        Box(Modifier.collapsing(chrome)) {
            Column(Modifier.onSizeChanged { chrome.height = it.height.toFloat() }) {
                SearchEntry(hotWords, actions.onSearch)
                ModeSwitch(DiscoverMode.entries, mode, { stringResource(it.label) }, { mode = it }, Modifier.fillMaxWidth().padding(top = 4.dp))            }
        }
        Crossfade(mode, Modifier.weight(1f).fillMaxWidth(), MaterialTheme.motionScheme.defaultEffectsSpec(), label = "discoverMode") { current ->
            holder.SaveableStateProvider(current.name) {
                if (current == DiscoverMode.Recommend) Column(Modifier.fillMaxSize()) { Recommendations(state, actions) } else other(current)
            }
        }
    }
}
@Composable
private fun ColumnScope.Recommendations(state: DiscoverState, actions: DiscoverActions) {
    when (val channels = state.channels) {
        ChannelsState.Loading -> {
            Row(Modifier.fillMaxWidth().padding(XmLayout.PagePadding), horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap)) {
                repeat(4) { Box(Modifier.size(width = 56.dp, height = 20.dp).skeleton()) }
            }
            PageSkeleton(Modifier.weight(1f))
        }
        is ChannelsState.Failed -> ErrorState(channels.code, actions.onReload, Modifier.weight(1f))
        is ChannelsState.Ready -> ChannelPager(channels.channels, state.pages, actions)
    }
}
@Composable
private fun ColumnScope.ChannelPager(
    channels: List<HomeChannel>,
    pages: Map<Int, ChannelState>,
    actions: DiscoverActions,
) {
    val pager = rememberPagerState { channels.size }
    val scope = rememberCoroutineScope()
    SecondaryScrollableTabRow(
        selectedTabIndex = pager.currentPage,
        containerColor = MaterialTheme.colorScheme.surface,
        edgePadding = XmLayout.PagePadding,
        divider = {},
    ) {
        channels.forEachIndexed { index, channel ->
            Tab(
                selected = pager.currentPage == index,
                onClick = { scope.launch { pager.animateScrollToPage(index) } },
                text = { Text(channel.title, maxLines = 1) },
            )
        }
    }
    LaunchedEffect(pager.currentPage, channels) { channels.getOrNull(pager.currentPage)?.let { actions.onLoad(it.pageId, false) } }
    HorizontalPager(pager, Modifier.weight(1f), key = { channels[it].pageId }) { index ->
        val channel = channels[index]
        ChannelPage(channel, pages[channel.pageId], actions)
    }
}

@Composable
private fun ChannelPage(
    channel: HomeChannel,
    state: ChannelState?,
    actions: DiscoverActions,
) {
    Column(Modifier.fillMaxSize()) {
        if (state != null && state.tabs.isNotEmpty()) SubTabs(state.tabs, state.tabId) { actions.onSelectTab(channel.pageId, it) }
        val content = state?.content
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                content != null -> PullToRefreshBox(state.loading, { actions.onLoad(channel.pageId, true) }) {
                    Feed(content, state.ranking, state.rankingFailed, actions, channel.pageId)
                }
                state?.errorCode != null -> ErrorState(state.errorCode, { actions.onLoad(channel.pageId, true) })
                else -> PageSkeleton()
            }
        }
    }
}

@Composable
private fun SubTabs(tabs: List<HomeTab>, selected: Int?, onSelect: (Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = XmLayout.PagePadding, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tabs, key = { it.tabId }) { tab ->
            FilterChip(selected = tab.tabId == selected, onClick = { onSelect(tab.tabId) }, label = { Text(tab.title, maxLines = 1) })
        }
    }
}

@Composable
private fun Feed(content: HomeContent, ranking: HotRanking?, rankingFailed: Boolean, actions: DiscoverActions, pageId: Int) {
    val sections = content.sections.filter { !it.ranking && it.albums.isNotEmpty() }
    val featured = sections.firstOrNull()?.takeIf { it.albums.size >= 3 }
    val shelves = if (featured != null) sections.drop(1) else sections
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = XmLayout.ContentWidth).fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = XmLayout.SectionGap),
            verticalArrangement = Arrangement.spacedBy(XmLayout.SectionGap),
        ) {
            if (featured != null) item(key = "featured") { Featured(featured, actions.onAllAlbums) }
            if (ranking != null && ranking.section.albums.isNotEmpty()) {
                item(key = "ranking") { RankingCard(ranking) { actions.onFullRanking(pageId) } }
            } else if (rankingFailed) {
                item(key = "ranking-retry") {
                    TextButton({ actions.onRetryRanking(pageId) }, Modifier.padding(horizontal = XmLayout.PagePadding)) { Text(stringResource(R.string.action_retry)) }
                }
            }
            items(shelves, key = { it.title + it.albums.first().id }) { Shelf(it, actions.onAllAlbums) }
            if (sections.isEmpty() && ranking == null) {
                item(key = "empty") {
                    Text(stringResource(R.string.discover_empty), Modifier.fillMaxWidth().padding(XmLayout.FormPadding),
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** A heading that offers 全部专辑 when the server names a category for the section. */
@Composable
private fun SectionHeader(title: String, section: AlbumSection, onAllAlbums: (Int) -> Unit) {
    val category = section.moreCategoryId
    SectionHeader(
        title, actionLabel = category?.let { stringResource(R.string.browse_all_albums) },
        onAction = category?.let { { onAllAlbums(it) } },
    )
}

@Composable
private fun Featured(section: AlbumSection, onAllAlbums: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(XmLayout.ItemGap)) {
        SectionHeader(section.title.ifEmpty { stringResource(R.string.discover_section_default) }, section = section, onAllAlbums = onAllAlbums)
        FeaturedCarousel(section)
    }
}

@Composable
private fun FeaturedCarousel(section: AlbumSection) {
    val albums = section.albums
    HorizontalMultiBrowseCarousel(
        state = rememberCarouselState { albums.size },
        preferredItemWidth = 240.dp,
        itemSpacing = 8.dp,
        contentPadding = PaddingValues(horizontal = XmLayout.PagePadding),
        modifier = Modifier.fillMaxWidth().height(200.dp),
    ) { index ->
        val album = albums[index]
        Box(Modifier.fillMaxSize().maskClip(MaterialTheme.shapes.extraLarge)) {
            Cover(album.coverUrl, Modifier.fillMaxSize(), RectangleShape)
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
                    .padding(16.dp),
            ) {
                Text(album.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (album.artist.isNotEmpty()) {
                    Text(album.artist, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun Shelf(section: AlbumSection, onAllAlbums: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(XmLayout.ItemGap)) {
        SectionHeader(section.title.ifEmpty { stringResource(R.string.discover_section_default) }, section = section, onAllAlbums = onAllAlbums)
        LazyRow(
            contentPadding = PaddingValues(horizontal = XmLayout.PagePadding),
            horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
        ) {
            items(section.albums, key = { it.id }) { AlbumCard(it, section.stats[it.id]) }
        }
    }
}

@Composable
private fun RankingCard(ranking: HotRanking, onFullRanking: () -> Unit) {
    val section = ranking.section
    Surface(
        Modifier.padding(horizontal = XmLayout.PagePadding).fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(vertical = XmLayout.PagePadding), verticalArrangement = Arrangement.spacedBy(XmLayout.ItemGap)) {
            SectionHeader(
                if (ranking.wholeSite) stringResource(R.string.discover_ranking_whole)
                else section.title.ifEmpty { stringResource(R.string.discover_ranking_default) },
                actionLabel = stringResource(R.string.ranking_full), onAction = onFullRanking,
            )
            section.albums.take(5).forEachIndexed { index, album ->
                RankRow(index + 1, album, section.stats[album.id], Modifier.padding(horizontal = XmLayout.PagePadding))
            }
        }
    }
}

@Composable
private fun PageSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(XmLayout.PagePadding), verticalArrangement = Arrangement.spacedBy(XmLayout.SectionGap)) {
        Box(Modifier.fillMaxWidth().height(180.dp).skeleton(MaterialTheme.shapes.extraLarge))
        repeat(2) {
            Column(verticalArrangement = Arrangement.spacedBy(XmLayout.ItemGap)) {
                Box(Modifier.size(width = 120.dp, height = 20.dp).skeleton())
                Row(horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap)) {
                    repeat(3) { Box(Modifier.size(104.dp).skeleton(MaterialTheme.shapes.medium)) }
                }
            }
        }
    }
}

private fun sampleAlbum(id: Int, title: String) = Album(id.toString(), title, "Anchor $id", "", null, 120)

private val sampleStats = AlbumStats(playCount = 123_456_789)

private val samplePage = ChannelState(
    tabs = listOf(HomeTab(1, "All"), HomeTab(2, "Free")),
    tabId = 1,
    contents = mapOf(
        1 to HomeContent(
            listOf(
                AlbumSection("Featured", (1..6).map { sampleAlbum(it, "Album $it") }, (1..6).associate { it.toString() to sampleStats }),
                AlbumSection("", (7..12).map { sampleAlbum(it, "A long album title that wraps onto a second line $it") }),
            ),
            emptyList(),
        ),
    ),
    ranking = HotRanking(
        AlbumSection("Hot", (20..26).map { sampleAlbum(it, "Chart album $it") }, (20..26).associate { it.toString() to sampleStats }, true),
        true,
    ),
)

@XmPreviews
@Composable
private fun DiscoverPreview() = XmPreview {
    DiscoverContent(
        DiscoverState(ChannelsState.Ready(listOf(HomeChannel(1, "Recommended", null), HomeChannel(2, "Novels", 3))), mapOf(1 to samplePage)),
        emptyList(), DiscoverActions(),
    )
}

@XmPreviews
@Composable
private fun DiscoverLoadingPreview() = XmPreview { DiscoverContent(DiscoverState(), emptyList(), DiscoverActions()) }

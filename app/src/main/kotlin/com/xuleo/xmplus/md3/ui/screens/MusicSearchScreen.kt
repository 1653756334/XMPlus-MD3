package com.xuleo.xmplus.md3.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.model.MusicAlbum
import com.xuleo.xmplus.md3.core.model.MusicArtist
import com.xuleo.xmplus.md3.core.model.MusicItem
import com.xuleo.xmplus.md3.core.model.MusicSheet
import com.xuleo.xmplus.md3.core.model.PluginMedia
import com.xuleo.xmplus.md3.core.plugin.InstalledPlugin
import com.xuleo.xmplus.md3.core.plugin.SearchType
import com.xuleo.xmplus.md3.music.MusicSearchViewModel
import com.xuleo.xmplus.md3.music.Paged
import com.xuleo.xmplus.md3.music.searchTypes
import com.xuleo.xmplus.md3.ui.components.MusicAlbumRow
import com.xuleo.xmplus.md3.ui.components.MusicArtistRow
import com.xuleo.xmplus.md3.ui.components.MusicSheetRow
import com.xuleo.xmplus.md3.ui.components.ScrollingDetailTabs
import com.xuleo.xmplus.md3.ui.components.SettlingPager
import com.xuleo.xmplus.md3.ui.components.SongRow
import kotlinx.coroutines.launch

@StringRes
private fun SearchType.label(): Int = when (this) {
    SearchType.Music -> R.string.music_tab_songs
    SearchType.Album -> R.string.music_tab_albums
    SearchType.Artist -> R.string.music_type_artist
    SearchType.Sheet -> R.string.music_type_sheet
    SearchType.Lyric -> R.string.music_type_lyric
}

/**
 * 音乐's search: the field and history as on 听书; under it one row with the types as tabs and the source at its end,
 * the same source and menu as 音乐. Each plugin and type is searched when it is first shown, and pages as it scrolls.
 */
@Composable
internal fun MusicSearchScreen(
    viewModel: MusicSearchViewModel,
    plugins: List<InstalledPlugin>,
    onBack: () -> Unit,
    onOpen: (PluginMedia) -> Unit,
    onPlay: (MusicItem) -> Unit,
    playingKey: String? = null,
    source: String? = null,
    onSource: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sources = plugins.filter { it.enabled && "search" in it.info.methods }
    val query = rememberTextFieldState(state.keyword.orEmpty())
    val focus = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    fun submit(text: String) {
        if (viewModel.submit(text)) focus.clearFocus()
    }
    LaunchedEffect(Unit) { if (state.keyword == null && query.text.isEmpty()) focusRequester.requestFocus() }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
        SearchFieldRow(query, focusRequester, invalid = false, R.string.music_search_hint, onBack, viewModel::clear, ::submit)
        val keyword = state.keyword
        if (keyword == null || sources.isEmpty()) {
            Suggestions(
                state.history, emptyList(), hotLoading = false,
                onPick = {
                    query.setTextAndPlaceCursorAtEnd(it)
                    submit(it)
                },
                onRemove = viewModel::removeHistory, onClear = viewModel::clearHistory,
            )
            return@Column
        }
        // The source is the one 音乐 shows; a plugin that cannot search falls back to the first that can, without
        // changing the choice, as on 音乐.
        val current = sources.firstOrNull { it.platform == source } ?: sources.first()
        // Each plugin opens on its own default type; a type picked on one plugin does not carry to another.
        key(current.platform) {
            val types = current.searchTypes()
            val pager = rememberPagerState { types.size }
            val scope = rememberCoroutineScope()
            val type = types[pager.currentPage.coerceIn(0, types.lastIndex)]
            // The tabs take only the space left of the source, so they scroll under nothing and the source stays clear.
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                ScrollingDetailTabs(
                    types.indexOf(type), types.map { stringResource(it.label()) },
                    { scope.launch { pager.scrollToPage(it) } }, Modifier.weight(1f).clipToBounds(),
                )
                SourcePicker(sources, current.platform, onSource)
                Spacer(Modifier.width(XmLayout.PagePadding))
            }
            LaunchedEffect(keyword, current.platform, type) { viewModel.open(current.platform, type) }
            // Swiping moves between the types as on the album page; a page is searched once it becomes current.
            SettlingPager(pager, Modifier.weight(1f), key = { types[it].key }) { page ->
                val pageType = types[page]
                key(keyword) {
                    Column(Modifier.fillMaxSize()) {
                        val paged = state.results[current.platform to pageType] ?: Paged(loading = true)
                        SearchResults(paged, { viewModel.loadMore(current.platform, pageType) },
                            { viewModel.retry(current.platform, pageType) }, onOpen, onPlay, playingKey)
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.SearchResults(
    paged: Paged<PluginMedia>,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (PluginMedia) -> Unit,
    onPlay: (MusicItem) -> Unit,
    playingKey: String?,
) {
    val list = rememberLazyListState()
    PagedList(paged, list, onLoadMore, onRetry) {
        itemsIndexed(paged.items, key = { index, item -> "$index:${item.id}" }) { _, item ->
            when (item) {
                is MusicItem -> SongRow(item, playing = item.key == playingKey) { onPlay(item) }
                is MusicAlbum -> MusicAlbumRow(item) { onOpen(item) }
                is MusicArtist -> MusicArtistRow(item) { onOpen(item) }
                is MusicSheet -> MusicSheetRow(item) { onOpen(item) }
            }
        }
    }
}

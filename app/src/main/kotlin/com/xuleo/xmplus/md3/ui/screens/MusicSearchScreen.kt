package com.xuleo.xmplus.md3.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.xuleo.xmplus.md3.ui.components.SongRow

@StringRes
private fun SearchType.label(): Int = when (this) {
    SearchType.Music -> R.string.music_tab_songs
    SearchType.Album -> R.string.music_tab_albums
    SearchType.Artist -> R.string.music_type_artist
    SearchType.Sheet -> R.string.music_type_sheet
    SearchType.Lyric -> R.string.music_type_lyric
}

/**
 * 音乐's search: the field and history as on 听书; results with the plugins as tabs and their types as chips under
 * them. Each plugin and type is searched when it is first shown, and pages as it scrolls.
 */
@Composable
internal fun MusicSearchScreen(
    viewModel: MusicSearchViewModel,
    plugins: List<InstalledPlugin>,
    onBack: () -> Unit,
    onOpen: (PluginMedia) -> Unit,
    onPlay: (MusicItem) -> Unit,
    playingKey: String? = null,
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
        var platform by rememberSaveable { mutableStateOf<String?>(null) }
        val source = sources.firstOrNull { it.platform == platform } ?: sources.first()
        if (sources.size > 1) {
            ScrollingDetailTabs(sources.indexOf(source), sources.map { it.platform }, { platform = sources[it].platform })
        }
        // Each plugin opens on its own default type; a type picked on one plugin does not carry to another.
        key(source.platform) {
            val types = source.searchTypes()
            var typeKey by rememberSaveable { mutableStateOf(types.first().key) }
            val type = types.firstOrNull { it.key == typeKey } ?: types.first()
            if (types.size > 1) {
                LazyRow(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = XmLayout.PagePadding, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(types) { FilterChip(it == type, { typeKey = it.key }, { Text(stringResource(it.label())) }) }
                }
            }
            LaunchedEffect(keyword, source.platform, type) { viewModel.open(source.platform, type) }
            key(keyword, type) {
                val paged = state.results[source.platform to type] ?: Paged(loading = true)
                SearchResults(paged, { viewModel.loadMore(source.platform, type) }, { viewModel.retry(source.platform, type) },
                    onOpen, onPlay, playingKey)
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

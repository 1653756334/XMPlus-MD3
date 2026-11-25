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
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
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
            var typeKey by rememberSaveable { mutableStateOf(types.first().key) }
            val type = types.firstOrNull { it.key == typeKey } ?: types.first()
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                TypeTabs(types, type, { typeKey = it.key }, Modifier.weight(1f))
                SourcePicker(sources, current.platform, onSource)
                Spacer(Modifier.width(XmLayout.PagePadding))
            }
            LaunchedEffect(keyword, current.platform, type) { viewModel.open(current.platform, type) }
            key(keyword, type) {
                val paged = state.results[current.platform to type] ?: Paged(loading = true)
                SearchResults(paged, { viewModel.loadMore(current.platform, type) }, { viewModel.retry(current.platform, type) },
                    onOpen, onPlay, playingKey)
            }
        }
    }
}

private val TabFade = 24.dp

/**
 * The search types as compact text tabs, as QQ 音乐 shows them: the chosen one in the accent colour with a short bar
 * under it. They scroll sideways when they do not fit beside the source, and fade out at the edge that has more, so
 * nothing looks cut off.
 */
@Composable
private fun TypeTabs(types: List<SearchType>, selected: SearchType, onSelect: (SearchType) -> Unit, modifier: Modifier = Modifier) {
    val list = rememberLazyListState()
    val index = types.indexOf(selected).coerceAtLeast(0)
    // A type chosen off screen, or restored after rotation, is brought into view.
    LaunchedEffect(index) {
        val info = list.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
        if (item == null || item.offset < 0 || item.offset + item.size > info.viewportEndOffset) list.animateScrollToItem(index)
    }
    LazyRow(
        modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
            drawContent()
            val fade = TabFade.toPx()
            if (list.canScrollBackward) {
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), 0f, fade), Offset.Zero, Size(fade, size.height), blendMode = BlendMode.DstIn)
            }
            if (list.canScrollForward) {
                drawRect(Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), size.width - fade, size.width),
                    Offset(size.width - fade, 0f), Size(fade, size.height), blendMode = BlendMode.DstIn)
            }
        },
        state = list,
        contentPadding = PaddingValues(start = XmLayout.PagePadding - 12.dp, end = 8.dp),
    ) {
        itemsIndexed(types, key = { _, type -> type.key }) { _, type ->
            val chosen = type == selected
            val spec = MaterialTheme.motionScheme.fastEffectsSpec<Color>()
            val text by animateColorAsState(if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, spec, label = "tab")
            val bar by animateColorAsState(if (chosen) MaterialTheme.colorScheme.primary else Color.Transparent, spec, label = "bar")
            Column(
                Modifier.heightIn(min = 48.dp).clip(MaterialTheme.shapes.small)
                    .selectable(chosen, role = Role.Tab) { onSelect(type) }.padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Text(
                    stringResource(type.label()), color = text, maxLines = 1,
                    style = MaterialTheme.typography.titleSmall, fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
                )
                Box(Modifier.padding(top = 4.dp).width(16.dp).height(3.dp).clip(CircleShape).background(bar))
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

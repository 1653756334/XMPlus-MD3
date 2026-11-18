package com.xuleo.xmplus.md3.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.browse.SearchResult
import com.xuleo.xmplus.md3.browse.SearchState
import com.xuleo.xmplus.md3.browse.SearchViewModel
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.designsystem.XmPreview
import com.xuleo.xmplus.md3.core.designsystem.XmPreviews
import com.xuleo.xmplus.md3.core.model.Album
import com.xuleo.xmplus.md3.core.model.AnchorProfile
import com.xuleo.xmplus.md3.core.model.HotWord
import com.xuleo.xmplus.md3.core.model.SearchKind
import com.xuleo.xmplus.md3.core.model.Track
import com.xuleo.xmplus.md3.ui.components.*
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The tabs, in the order they are shown. */
private val SearchTabs = listOf(SearchKind.All, SearchKind.Album, SearchKind.Track, SearchKind.Anchor)

@StringRes
private fun SearchKind.label(): Int = when (this) {
    SearchKind.All -> R.string.search_kind_all
    SearchKind.Album -> R.string.search_kind_album
    SearchKind.Track -> R.string.search_kind_track
    SearchKind.Anchor -> R.string.search_kind_anchor
}

@Composable
fun SearchScreen(viewModel: SearchViewModel, onOpenAlbum: (String) -> Unit = {}, onOpenAnchor: (String) -> Unit = {}, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadHotWords() }
    SearchContent(
        state, onBack, viewModel::submit, viewModel::clear, viewModel::dismissInvalid,
        viewModel::selectKind, viewModel::loadMore, viewModel::retry, onOpenAlbum, onOpenAnchor,
    )
}

@Composable
internal fun SearchContent(
    state: SearchState,
    onBack: () -> Unit,
    onSubmit: (String) -> Unit,
    onClear: () -> Unit,
    onEdit: () -> Unit,
    onSelectKind: (SearchKind) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: (SearchKind) -> Unit,
    onOpenAlbum: (String) -> Unit = {},
    onOpenAnchor: (String) -> Unit = {},
) {
    val query = rememberTextFieldState()
    val focus = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    fun submit(text: String) {
        focus.clearFocus()
        onSubmit(text)
    }
    LaunchedEffect(Unit) { if (state.keyword == null && query.text.isEmpty()) focusRequester.requestFocus() }
    // The warning goes away as soon as the text changes.
    LaunchedEffect(state.invalid) {
        if (state.invalid) {
            snapshotFlow { query.text.toString() }.drop(1).first()
            onEdit()
        }
    }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
        Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.Top) {
            IconButton(onBack, Modifier.padding(top = 4.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
            }
            TextField(
                state = query,
                modifier = Modifier.weight(1f).padding(vertical = 4.dp).focusRequester(focusRequester),
                placeholder = { Text(stringResource(R.string.search_hint), maxLines = 1) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.text.isNotEmpty()) {
                        IconButton({
                            query.setTextAndPlaceCursorAtEnd("")
                            onClear()
                            focusRequester.requestFocus()
                        }) { Icon(Icons.Filled.Close, stringResource(R.string.search_clear)) }
                    }
                },
                supportingText = if (state.invalid) {
                    { Text(stringResource(R.string.search_invalid)) }
                } else null,
                isError = state.invalid,
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                onKeyboardAction = KeyboardActionHandler { submit(query.text.toString()) },
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    errorContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            )
            TextButton({ submit(query.text.toString()) }, Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.search_submit)) }
        }
        if (state.keyword == null) {
            Suggestions(state.hotWords) {
                query.setTextAndPlaceCursorAtEnd(it.word)
                submit(it.word)
            }
        } else {
            Results(state, onSelectKind, onLoadMore, onRetry, onOpenAlbum, onOpenAnchor)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Suggestions(hotWords: List<HotWord>, onPick: (HotWord) -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        if (hotWords.isEmpty()) {
            Message(R.string.search_prompt)
        } else {
            Column(
                Modifier.widthIn(max = XmLayout.ContentWidth).fillMaxWidth().padding(XmLayout.PagePadding),
                verticalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
            ) {
                Text(stringResource(R.string.search_hot), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    hotWords.forEach { word -> AssistChip(onClick = { onPick(word) }, label = { Text(word.display, maxLines = 1) }) }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.Results(
    state: SearchState,
    onSelectKind: (SearchKind) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: (SearchKind) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenAnchor: (String) -> Unit,
) {
    val pager = rememberPagerState(SearchTabs.indexOf(state.kind)) { SearchTabs.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { onSelectKind(SearchTabs[it]) } }
    fun show(kind: SearchKind) {
        scope.launch { pager.animateScrollToPage(SearchTabs.indexOf(kind)) }
    }
    SecondaryTabRow(selectedTabIndex = pager.currentPage, containerColor = MaterialTheme.colorScheme.surface) {
        SearchTabs.forEachIndexed { index, kind ->
            Tab(selected = pager.currentPage == index, onClick = { show(kind) }, text = { Text(stringResource(kind.label()), maxLines = 1) })
        }
    }
    HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), key = { SearchTabs[it].code }) { index ->
        val kind = SearchTabs[index]
        ResultPane(kind, state.results[kind], ::show, onLoadMore, { onRetry(kind) }, onOpenAlbum, onOpenAnchor)
    }
}

@Composable
private fun ResultPane(
    kind: SearchKind,
    result: SearchResult?,
    onMore: (SearchKind) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenAnchor: (String) -> Unit,
) {
    when {
        result == null || (!result.loaded && result.errorCode == null) -> ListSkeleton()
        !result.loaded -> ErrorState(result.errorCode.orEmpty(), onRetry)
        result.empty -> Message(R.string.search_empty)
        else -> ResultList(kind, result, onMore, onLoadMore, onRetry, onOpenAlbum, onOpenAnchor)
    }
}

@Composable
private fun ResultList(
    kind: SearchKind,
    result: SearchResult,
    onMore: (SearchKind) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenAnchor: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    if (kind != SearchKind.All) {
        // Ask for the next page when the end is a few rows away; runs again whenever a page lands.
        LoadMoreEffect({ listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }, { listState.layoutInfo.totalItemsCount },
            listState, result.page, result.loading, onLoadMore = onLoadMore)
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = XmLayout.ContentWidth).fillMaxSize(), listState, contentPadding = PaddingValues(bottom = XmLayout.SectionGap)) {
            if (kind == SearchKind.All) {
                anchors(result.anchors, result.totals[SearchKind.Anchor] ?: 0, onMore, onOpenAnchor)
                albums(result.albums, result.totals[SearchKind.Album] ?: 0, onMore, onOpenAlbum)
                tracks(result.tracks, result.totals[SearchKind.Track] ?: 0, onMore)
            } else {
                items(result.anchors, key = { "anchor-" + it.uid }) { anchor -> AnchorRow(anchor) { onOpenAnchor(anchor.uid) } }
                items(result.albums, key = { "album-" + it.id }) { album -> AlbumRow(album) { onOpenAlbum(album.id) } }
                items(result.tracks, key = { "track-" + it.id }) { TrackRow(it) }
                item(key = "footer") { PagingFooter(result.loading, result.errorCode != null, result.hasMore, onRetry) }
            }
        }
    }
}

private fun LazyListScope.anchors(rows: List<AnchorProfile>, total: Int, onMore: (SearchKind) -> Unit, onOpenAnchor: (String) -> Unit) {
    if (rows.isEmpty()) return
    item(key = "head-anchor") { ResultHeader(R.string.search_kind_anchor, total > rows.size) { onMore(SearchKind.Anchor) } }
    items(rows, key = { "anchor-" + it.uid }) { anchor -> AnchorRow(anchor) { onOpenAnchor(anchor.uid) } }
}

private fun LazyListScope.albums(rows: List<Album>, total: Int, onMore: (SearchKind) -> Unit, onOpenAlbum: (String) -> Unit) {
    if (rows.isEmpty()) return
    item(key = "head-album") { ResultHeader(R.string.search_kind_album, total > rows.size) { onMore(SearchKind.Album) } }
    items(rows, key = { "album-" + it.id }) { album -> AlbumRow(album) { onOpenAlbum(album.id) } }
}

private fun LazyListScope.tracks(rows: List<Track>, total: Int, onMore: (SearchKind) -> Unit) {
    if (rows.isEmpty()) return
    item(key = "head-track") { ResultHeader(R.string.search_kind_track, total > rows.size) { onMore(SearchKind.Track) } }
    items(rows, key = { "track-" + it.id }) { TrackRow(it) }
}

@Composable
private fun ResultHeader(@StringRes title: Int, hasMore: Boolean, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = XmLayout.PagePadding, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (hasMore) TextButton(onMore) { Text(stringResource(R.string.search_more)) }
    }
}

@Composable
private fun Message(@StringRes text: Int) {
    Box(Modifier.fillMaxSize().padding(XmLayout.FormPadding), contentAlignment = Alignment.Center) {
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

private val sampleAlbums = (1..3).map { Album("a$it", "Album $it", "Anchor $it", "", null, 40 * it) }
private val sampleTracks = (1..3).map { Track("t$it", "Sound $it", "Anchor", "Album $it", "a$it", 75.0 * it) }
private val sampleAnchors = listOf(AnchorProfile("u1", "Anchor One", verified = true, intro = "A short line about this anchor.", fans = 123_456, albums = 4, tracks = 90))

@XmPreviews
@Composable
private fun SearchResultsPreview() = XmPreview {
    SearchContent(
        SearchState(
            hotWords = listOf(HotWord("a", "Hot")), keyword = "q",
            results = mapOf(SearchKind.All to SearchResult(sampleAlbums, sampleTracks, sampleAnchors, mapOf(SearchKind.Album to 30), page = 1)),
        ),
        {}, {}, {}, {}, {}, {}, {},
    )
}

@XmPreviews
@Composable
private fun SearchIdlePreview() = XmPreview {
    SearchContent(SearchState(hotWords = listOf(HotWord("a", "Hot"), HotWord("b", "Another hot word"))), {}, {}, {}, {}, {}, {}, {})
}

package com.xuleo.xmplus.md3.ui.screens

import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.ui.icons.XmIcons
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
        viewModel::removeHistory, viewModel::clearHistory,
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
    onRemoveHistory: (String) -> Unit = {},
    onClearHistory: () -> Unit = {},
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
        SearchFieldRow(query, focusRequester, state.invalid, R.string.search_hint, onBack, onClear, ::submit)
        if (state.keyword == null) {
            Suggestions(
                state.history, state.hotWords, hotLoading = !state.hotWordsSettled && state.hotWords.isEmpty(),
                onPick = {
                    query.setTextAndPlaceCursorAtEnd(it)
                    submit(it)
                },
                onRemove = onRemoveHistory, onClear = onClearHistory,
            )
        } else {
            Results(state, onSelectKind, onLoadMore, onRetry, onOpenAlbum, onOpenAnchor)
        }
    }
}

/** Back, the rounded search field with its clear button, and 搜索; shared by 听书 and 音乐. */
@Composable
internal fun SearchFieldRow(
    query: TextFieldState,
    focusRequester: FocusRequester,
    invalid: Boolean,
    @StringRes hint: Int,
    onBack: () -> Unit,
    onClear: () -> Unit,
    submit: (String) -> Unit,
) {
        Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.Top) {
            IconButton(onBack, Modifier.padding(top = 4.dp)) {
                Icon(XmIcons.Filled.ArrowBack, stringResource(R.string.action_back))
            }
            TextField(
                state = query,
                modifier = Modifier.weight(1f).padding(vertical = 4.dp).focusRequester(focusRequester),
                placeholder = { Text(stringResource(hint), maxLines = 1) },
                leadingIcon = { Icon(XmIcons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.text.isNotEmpty()) {
                        IconButton({
                            query.setTextAndPlaceCursorAtEnd("")
                            onClear()
                            focusRequester.requestFocus()
                        }) { Icon(XmIcons.Filled.Close, stringResource(R.string.search_clear)) }
                    }
                },
                supportingText = if (invalid) {
                    { Text(stringResource(R.string.search_invalid)) }
                } else null,
                isError = invalid,
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
}

private val ChipHeight = 32.dp
private val ChipGap = 8.dp
private const val HotPlaceholders = 8

/** Before a search: this device's history on top, then the popular searches as a ranked list; the two scroll together. */
@Composable
internal fun Suggestions(
    history: List<String>,
    hotWords: List<HotWord>,
    hotLoading: Boolean,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (history.isEmpty() && hotWords.isEmpty() && !hotLoading) return Message(R.string.search_prompt)
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = XmLayout.ContentWidth).fillMaxSize(), contentPadding = PaddingValues(bottom = XmLayout.SectionGap)) {
            if (history.isNotEmpty()) {
                item(key = "history-head") {
                    SectionHead(R.string.search_history) {
                        IconButton({ confirmClear = true }) {
                            Icon(XmIcons.Outlined.Delete, stringResource(R.string.search_history_clear))
                        }
                    }
                }
                item(key = "history") { HistoryChips(history, onPick, onRemove) }
            }
            if (hotLoading || hotWords.isNotEmpty()) {
                item(key = "hot-head") { SectionHead(R.string.search_hot) }
                if (hotLoading) {
                    items(HotPlaceholders, key = { "hot-placeholder-$it" }) { HotWordPlaceholder() }
                } else {
                    // The same word can be listed twice, so rows are keyed by rank.
                    itemsIndexed(hotWords, key = { index, _ -> "hot-$index" }) { index, word ->
                        HotWordRow(index + 1, word.display) { onPick(word.word) }
                    }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.search_history_clear_title)) },
            confirmButton = {
                TextButton({ confirmClear = false; onClear() }) { Text(stringResource(R.string.history_clear_confirm)) }
            },
            dismissButton = { TextButton({ confirmClear = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun SectionHead(@StringRes title: Int, action: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp).padding(start = XmLayout.PagePadding, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        action()
    }
}

/** One line of keywords; the arrow at its end opens the rest. A long press removes a keyword. */
@Composable
private fun HistoryChips(history: List<String>, onPick: (String) -> Unit, onRemove: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    CollapsibleChipRows(
        expanded,
        Modifier.fillMaxWidth().padding(horizontal = XmLayout.PagePadding)
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        toggle = {
            Surface(
                onClick = { expanded = !expanded }, shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(ChipHeight),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (expanded) XmIcons.Filled.KeyboardArrowUp else XmIcons.Filled.KeyboardArrowDown,
                        stringResource(if (expanded) R.string.search_history_collapse else R.string.search_history_expand),
                        Modifier.size(20.dp),
                    )
                }
            }
        },
    ) {
        history.forEach { keyword ->
            HistoryChip(keyword, onClick = { onPick(keyword) }) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onRemove(keyword)
            }
        }
    }
}

@Composable
private fun HistoryChip(keyword: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Box(
            Modifier.combinedClickable(
                onLongClickLabel = stringResource(R.string.search_history_delete), onLongClick = onLongClick, onClick = onClick,
            ).height(ChipHeight).widthIn(max = 200.dp).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(keyword, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Chips in rows. Collapsed, only the first row shows, with [toggle] at its end; expanded, every row shows and [toggle]
 * ends the last one. When everything fits on one row there is no [toggle].
 */
@Composable
private fun CollapsibleChipRows(
    expanded: Boolean,
    modifier: Modifier,
    toggle: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Layout(listOf(content, toggle), modifier) { (chipMeasurables, toggleMeasurables), constraints ->
        val gap = ChipGap.roundToPx()
        val width = constraints.maxWidth
        val switch = toggleMeasurables.first().measure(Constraints())
        val chips = chipMeasurables.map { it.measure(Constraints(maxWidth = (width - switch.width - gap).coerceAtLeast(0))) }
        fun span(line: List<Int>) = line.sumOf { chips[it].width } + gap * (line.size - 1)
        fun lines(firstWidth: Int): List<List<Int>> {
            val result = mutableListOf<MutableList<Int>>()
            chips.indices.forEach { index ->
                val line = result.lastOrNull()
                val room = if (result.size <= 1) firstWidth else width
                if (line != null && span(line) + gap + chips[index].width <= room) line += index else result += mutableListOf(index)
            }
            return result
        }
        val all = lines(width)
        val overflowing = all.size > 1
        val shown = if (overflowing && !expanded) lines(width - switch.width - gap).take(1) else all
        val switchRow = when {
            !overflowing -> -1
            !expanded -> 0
            span(shown.last()) + gap + switch.width <= width -> shown.size - 1
            else -> shown.size
        }
        val rowHeight = maxOf(chips.maxOfOrNull { it.height } ?: 0, switch.height)
        val rows = maxOf(shown.size, switchRow + 1)
        layout(width, (rows * rowHeight + (rows - 1) * gap).coerceAtLeast(0)) {
            shown.forEachIndexed { row, line ->
                var x = 0
                val y = row * (rowHeight + gap)
                line.forEach {
                    chips[it].placeRelative(x, y + (rowHeight - chips[it].height) / 2)
                    x += chips[it].width + gap
                }
            }
            if (switchRow >= 0) {
                switch.placeRelative(width - switch.width, switchRow * (rowHeight + gap) + (rowHeight - switch.height) / 2)
            }
        }
    }
}

/** A popular search with its rank; the top three stand out in the accent colour. */
@Composable
private fun HotWordRow(rank: Int, text: String, onClick: () -> Unit) {
    val top = rank <= 3
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 48.dp).padding(horizontal = XmLayout.PagePadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            rank.toString(), Modifier.width(32.dp), style = MaterialTheme.typography.titleMedium,
            fontWeight = if (top) FontWeight.Bold else FontWeight.Normal,
            color = if (top) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (top) FontWeight.Medium else null, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HotWordPlaceholder() {
    Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = XmLayout.PagePadding), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 16.dp, height = 16.dp).skeleton())
        Spacer(Modifier.width(16.dp))
        Box(Modifier.fillMaxWidth(0.5f).height(14.dp).skeleton())
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
    DetailTabs(pager, SearchTabs.map { it.label() })
    SettlingPager(pager, Modifier.weight(1f).fillMaxWidth(), key = { SearchTabs[it].code }) { index ->
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
    SearchContent(
        SearchState(
            hotWords = (1..12).map { HotWord("w$it", "Popular search $it") }, hotWordsSettled = true,
            history = listOf("Mystery", "A rather long keyword", "History", "Sci-fi", "Comedy", "Audiobook"),
        ),
        {}, {}, {}, {}, {}, {}, {},
    )
}

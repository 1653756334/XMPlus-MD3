@file:OptIn(ExperimentalMaterial3Api::class)

package com.xuleo.xmplus.md3.ui.screens

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.browse.AnchorListing
import com.xuleo.xmplus.md3.browse.AnchorListingState
import com.xuleo.xmplus.md3.browse.AnchorViewModel
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.model.AnchorProfile
import com.xuleo.xmplus.md3.library.LibraryToggle
import com.xuleo.xmplus.md3.ui.components.*

private val AnchorTabs = listOf(R.string.search_kind_album, R.string.album_tab_episodes)

@Composable
internal fun AnchorScreen(viewModel: AnchorViewModel, onOpenAlbum: (String) -> Unit, onBack: () -> Unit) {
    val header by viewModel.state.collectAsStateWithLifecycle()
    val albums by viewModel.albums.state.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.state.collectAsStateWithLifecycle()
    val pager = rememberPagerState { AnchorTabs.size }
    val albumList = rememberLazyListState()
    val trackList = rememberLazyListState()
    var albumQuery by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(viewModel) { viewModel.start() }
    LaunchedEffect(header.uid, pager.settledPage) {
        if (header.uid != null) (if (pager.settledPage == 0) viewModel.albums else viewModel.tracks).ensureLoaded()
    }

    if (header.uid == null) {
        DetailPage(onBack) {
            if (header.loading) ListSkeleton()
            else ErrorState(header.errorCode.orEmpty(), viewModel::retryProfile)
        }
        return
    }

    val profile = header.profile
    val hero: (@Composable () -> Unit)? = if (profile != null) ({ AnchorHero(profile, header.follow, viewModel::toggleFollow) }) else null
    val refreshing = if (pager.settledPage == 0) albums.refreshing else tracks.refreshing
    val refreshState = rememberPullToRefreshState()
    // Collapsing chrome is inside the refresh box so it expands before a downward drag can trigger a refresh.
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            viewModel.retryProfile()
            (if (pager.settledPage == 0) viewModel.albums else viewModel.tracks).refresh()
        },
        modifier = Modifier.fillMaxSize(),
        state = refreshState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = refreshState, isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars).padding(top = DetailTopBarHeight),
            )
        },
    ) {
        DetailPage(onBack, title = profile?.nickname.orEmpty(), header = hero) {
            header.errorCode?.let { InlineError(it, viewModel::retryProfile) }
            DetailTabs(pager, AnchorTabs, Modifier.widthIn(max = XmLayout.ContentWidth).fillMaxWidth())
            SettlingPager(pager, Modifier.weight(1f).fillMaxWidth()) { index ->
                if (index == 0) {
                    AnchorWorks(
                        albums, albumList, viewModel.albums, if (albums.keyword == null) R.string.anchor_no_albums else R.string.anchor_no_matches,
                        { it.album.id }, top = { AnchorSearchField(albumQuery, { albumQuery = it }, viewModel.albums::search) },
                    ) { item ->
                        AlbumRow(item.album, playCount = item.playCount, showArtist = false) { onOpenAlbum(item.album.id) }
                    }
                } else {
                    AnchorWorks(tracks, trackList, viewModel.tracks, R.string.anchor_no_tracks, { it.id }, showTrackLimit = true) { track ->
                        val open: (() -> Unit)? = track.albumId.takeIf(String::isNotEmpty)?.let { id -> { onOpenAlbum(id) } }
                        TrackRow(track, onClick = open)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnchorHero(profile: AnchorProfile, follow: LibraryToggle, onFollow: () -> Unit) {
    DetailHero(profile.backgroundUrl ?: profile.avatarUrl) {
        Column(
            Modifier.widthIn(max = XmLayout.ContentWidth).fillMaxWidth().padding(XmLayout.PagePadding),
            verticalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap)) {
                Avatar(profile.avatarUrl, Modifier.size(72.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            profile.nickname, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        if (profile.verified) Icon(
                            Icons.Filled.Verified, stringResource(R.string.anchor_verified), Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    val details = listOf(profile.verification, profile.location).filter(String::isNotBlank).joinToString(" · ")
                    if (details.isNotEmpty()) AnchorMetadata(details)
                    val fans = profile.fans
                    if (fans != null) AnchorMetadata(pluralStringResource(R.plurals.anchor_follower_count, fans, formatCount(fans.toLong())))
                }
                // Beside the avatar and name it belongs to, so it reads as this anchor's button.
                if (follow.signedIn) LibraryToggleButton(follow, R.string.anchor_following, R.string.anchor_follow, onFollow, compact = true)
            }
            if (profile.intro.isNotBlank()) {
                var truncated by remember(profile.intro) { mutableStateOf(false) }
                var showIntro by rememberSaveable(profile.uid) { mutableStateOf(false) }
                // A long introduction can be read without pushing the tabs and lists off a small screen.
                if (truncated) {
                    TextButton(onClick = { showIntro = true }, contentPadding = PaddingValues(0.dp)) {
                        AnchorMetadata(profile.intro, maxLines = 2)
                    }
                } else {
                    Text(
                        profile.intro, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, onTextLayout = { truncated = it.hasVisualOverflow },
                    )
                }
                if (showIntro) {
                    AlertDialog(
                        onDismissRequest = { showIntro = false }, title = { Text(profile.nickname) },
                        text = {
                            LazyColumn { item { Text(profile.intro) } }
                        },
                        confirmButton = { TextButton(onClick = { showIntro = false }) { Text(stringResource(R.string.action_close)) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun AnchorMetadata(text: String, maxLines: Int = 1) {
    Text(
        text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun <T> AnchorWorks(
    state: AnchorListingState<T>,
    list: LazyListState,
    listing: AnchorListing<T>,
    emptyLabel: Int,
    itemId: (T) -> String,
    showTrackLimit: Boolean = false,
    top: (@Composable () -> Unit)? = null,
    row: @Composable (T) -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = XmLayout.ContentWidth).fillMaxSize()) {
            top?.invoke()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    !state.loaded && state.errorCode != null -> ErrorState(state.errorCode, listing::retry)
                    !state.loaded -> ListSkeleton()
                    else -> {
                        LoadMoreEffect(
                            { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index }, { state.items.size + 1 },
                            state.next, state.items.size, onLoadMore = listing::loadMore,
                        )
                        CompositionLocalProvider(LocalOverscrollFactory provides null) {
                            LazyColumn(
                                Modifier.fillMaxSize(), list, contentPadding = PaddingValues(bottom = XmLayout.SectionGap),
                            ) {
                                val total = state.total
                                if (showTrackLimit && total != null && (state.reportedTotal ?: 0) > total) {
                                    item(key = "limit") {
                                        Box(Modifier.padding(XmLayout.PagePadding)) {
                                            AnchorMetadata(stringResource(R.string.anchor_track_limit, total), maxLines = 2)
                                        }
                                    }
                                }
                                if (state.items.isEmpty()) item(key = "empty") {
                                    Text(
                                        stringResource(emptyLabel), Modifier.fillMaxWidth().padding(XmLayout.FormPadding),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                                    )
                                }
                                items(state.items, key = { "work:" + itemId(it) }) { row(it) }
                                state.errorCode?.let { error -> item(key = "error") { InlineError(error, listing::retry) } }
                                if (state.items.isNotEmpty()) item(key = "footer") {
                                    PagingFooter(state.loading, failed = false, hasMore = state.next != null, onRetry = listing::loadMore)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val MaxKeywordLength = 200

/** A filled pill over the albums: the query, a clear button, and the hint when empty. Searches on Search or on clear. */
@Composable
private fun AnchorSearchField(query: String, onQuery: (String) -> Unit, onSubmit: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = XmLayout.PagePadding, vertical = 8.dp)
            .height(44.dp).clip(CircleShape).background(colors.surfaceContainerHigh).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = query,
            onValueChange = { onQuery(it.take(MaxKeywordLength)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit(query) }),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(stringResource(R.string.anchor_search_hint), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                }
                inner()
            },
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQuery(""); onSubmit("") }, Modifier.size(40.dp)) {
                Icon(Icons.Filled.Close, stringResource(R.string.search_clear), Modifier.size(18.dp))
            }
        }
    }
}

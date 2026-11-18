@file:OptIn(ExperimentalMaterial3Api::class)

package com.xuleo.xmplus.md3.ui.screens

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.browse.AnchorListing
import com.xuleo.xmplus.md3.browse.AnchorListingState
import com.xuleo.xmplus.md3.browse.AnchorOrder
import com.xuleo.xmplus.md3.browse.AnchorViewModel
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.model.AnchorContentPage
import com.xuleo.xmplus.md3.core.model.AnchorProfile
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
    val hero: (@Composable () -> Unit)? = if (profile != null) ({ AnchorHero(profile) }) else null
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
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { index ->
                if (index == 0) {
                    AnchorWorks(albums, albumList, viewModel.albums, R.string.anchor_no_albums, { it.album.id }) { item ->
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
private fun AnchorHero(profile: AnchorProfile) {
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
    row: @Composable (T) -> Unit,
) {
    val content = state.content
    var lastPageKey by rememberSaveable { mutableStateOf<String?>(null) }
    val pageKey = content?.let { "${state.order.name}:${it.page}" }
    LaunchedEffect(pageKey) {
        if (pageKey != null && lastPageKey != pageKey) {
            if (lastPageKey != null) list.scrollToItem(0)
            lastPageKey = pageKey
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = XmLayout.ContentWidth).fillMaxSize()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = XmLayout.PagePadding, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(AnchorOrder.entries, key = { it.name }) { order ->
                    FilterChip(
                        selected = order == state.order,
                        onClick = { listing.select(order) },
                        label = { Text(stringResource(order.label()), maxLines = 1) },
                    )
                }
            }
            when {
                content == null && state.errorCode != null -> ErrorState(state.errorCode, listing::retry)
                content == null -> ListSkeleton()
                else -> {
                    if (state.loading && !state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
                    CompositionLocalProvider(LocalOverscrollFactory provides null) {
                        LazyColumn(
                            Modifier.fillMaxSize(), list, contentPadding = PaddingValues(bottom = XmLayout.SectionGap),
                        ) {
                            val total = content.total
                            if (showTrackLimit && total != null && (content.reportedTotal ?: 0) > total) {
                                item(key = "limit") {
                                    Box(Modifier.padding(XmLayout.PagePadding)) {
                                        AnchorMetadata(stringResource(R.string.anchor_track_limit, total), maxLines = 2)
                                    }
                                }
                            }
                            if (content.items.isEmpty()) item(key = "empty") {
                                Text(
                                    stringResource(emptyLabel), Modifier.fillMaxWidth().padding(XmLayout.FormPadding),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                                )
                            }
                            items(content.items, key = { "work:" + itemId(it) }) { row(it) }
                            state.errorCode?.let { error -> item(key = "error") { InlineError(error, listing::retry) } }
                            if (content.page > 1 || content.hasMore) item(key = "pages") {
                                AnchorPages(content, state.loading, listing::goTo)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun AnchorOrder.label(): Int = when (this) {
    AnchorOrder.Default -> R.string.anchor_sort_default
    AnchorOrder.Newest -> R.string.sort_newest
    AnchorOrder.Oldest -> R.string.anchor_sort_oldest
    AnchorOrder.MostPlayed -> R.string.sort_plays
}

@Composable
private fun AnchorPages(content: AnchorContentPage<*>, loading: Boolean, onPage: (Int) -> Unit) {
    val maximum = content.total?.let { AnchorListing.maxPage(content) }
    var jump by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onPage(content.page - 1) }, enabled = !loading && content.page > 1) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.anchor_previous_page))
        }
        if (maximum != null && maximum > 1) {
            TextButton(onClick = { jump = true }, enabled = !loading) {
                Text(stringResource(R.string.anchor_page_position, content.page, maximum))
            }
        } else {
            Text(stringResource(R.string.anchor_page_number, content.page))
        }
        IconButton(onClick = { onPage(content.page + 1) }, enabled = !loading && content.hasMore) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.anchor_next_page))
        }
    }
    if (jump && maximum != null && maximum > 1) {
        var input by rememberSaveable { mutableStateOf("") }
        val page = input.toIntOrNull()?.takeIf { it in 1..maximum }
        AlertDialog(
            onDismissRequest = { jump = false },
            text = {
                OutlinedTextField(
                    value = input, onValueChange = { input = it.filter(Char::isDigit).take(10) },
                    label = { Text(stringResource(R.string.anchor_page_hint, maximum)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { page?.let(onPage); jump = false }, enabled = page != null && !loading) {
                    Text(stringResource(R.string.anchor_jump))
                }
            },
            dismissButton = { TextButton(onClick = { jump = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

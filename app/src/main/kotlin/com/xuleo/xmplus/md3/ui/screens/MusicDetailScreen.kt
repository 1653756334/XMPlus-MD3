package com.xuleo.xmplus.md3.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Dp
import com.xuleo.xmplus.md3.ui.components.AddToSheetSheet
import com.xuleo.xmplus.md3.ui.components.LocalSongDownloads
import com.xuleo.xmplus.md3.ui.components.SelectionToolbar
import com.xuleo.xmplus.md3.ui.components.SelectionToolbarSpace
import com.xuleo.xmplus.md3.ui.components.ToolbarAction
import com.xuleo.xmplus.md3.ui.components.rememberShowMessage
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.media.MusicLibrary
import com.xuleo.xmplus.md3.core.model.MusicAlbum
import com.xuleo.xmplus.md3.core.model.MusicArtist
import com.xuleo.xmplus.md3.core.model.MusicItem
import com.xuleo.xmplus.md3.core.model.MusicSheet
import com.xuleo.xmplus.md3.core.model.PluginMedia
import com.xuleo.xmplus.md3.music.MusicDetailKind
import com.xuleo.xmplus.md3.music.MusicDetailViewModel
import com.xuleo.xmplus.md3.music.Paged
import com.xuleo.xmplus.md3.ui.components.DetailHero
import com.xuleo.xmplus.md3.ui.components.DetailPage
import com.xuleo.xmplus.md3.ui.components.ErrorState
import com.xuleo.xmplus.md3.ui.components.ListSkeleton
import com.xuleo.xmplus.md3.ui.components.LocalMusicLibrary
import com.xuleo.xmplus.md3.ui.components.LoadMoreEffect
import com.xuleo.xmplus.md3.ui.components.MusicAlbumRow
import com.xuleo.xmplus.md3.ui.components.MusicCover
import com.xuleo.xmplus.md3.ui.components.PagerTabs
import com.xuleo.xmplus.md3.ui.components.PagingFooter
import com.xuleo.xmplus.md3.ui.components.SettlingPager
import com.xuleo.xmplus.md3.ui.components.SongRow
import com.xuleo.xmplus.md3.ui.components.formatCount
import com.xuleo.xmplus.md3.ui.icons.XmIcons

private val HeroCover = 120.dp
private val ArtistAvatar = 96.dp

/**
 * 歌单, 榜单, 专辑 and 歌手 from a plugin. The first three share the album page's head and a song list with 播放全部 on
 * top; 歌手 has its songs and albums in two tabs. Tapping a song plays the list from that song.
 */
@Composable
internal fun MusicDetailScreen(
    viewModel: MusicDetailViewModel,
    onBack: () -> Unit,
    onOpenAlbum: (MusicAlbum) -> Unit,
    onPlay: (List<MusicItem>, Int) -> Unit,
    onPlayNext: (MusicItem) -> Unit = {},
    playingKey: String? = null,
) {
    LaunchedEffect(viewModel) { viewModel.start() }
    val item by viewModel.item.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val title = item.title()
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val showMessage = rememberShowMessage()
    val downloads = LocalSongDownloads.current
    val library = LocalMusicLibrary.current
    // A long press on a song selects it; 加入歌单 and 下载 then act on all the chosen songs.
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var adding by remember { mutableStateOf<List<MusicItem>?>(null) }
    val chosen = songs.items.filter { it.key in selected }
    fun endSelection() {
        selecting = false
        selected = emptySet()
    }
    BackHandler(enabled = selecting, onBack = ::endSelection)
    val selection = SongSelection(
        active = selecting,
        selected = selected,
        onToggle = { song -> selected = if (song.key in selected) selected - song.key else selected + song.key },
        onStart = { song ->
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            selecting = true
            selected = setOf(song.key)
        },
    )
    DetailPage(
        onBack,
        if (selecting) stringResource(R.string.history_selected, selected.size) else title,
        header = { MusicHero(item) },
        navigation = if (selecting) ({
            IconButton(onClick = ::endSelection) { Icon(XmIcons.Outlined.Close, stringResource(R.string.action_cancel)) }
        }) else null,
        pinTitle = selecting,
        actions = {
            if (selecting) {
                val all = songs.items.isNotEmpty() && songs.items.all { it.key in selected }
                IconButton(onClick = { selected = if (all) emptySet() else songs.items.mapTo(HashSet()) { it.key } }) {
                    Icon(
                        XmIcons.Filled.DoneAll, stringResource(R.string.history_select_all),
                        tint = if (all) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
            } else {
                // MusicFree lets a plugin sheet be starred; the other kinds have nothing to keep.
                val sheet = item as? MusicSheet
                if (sheet != null && library != null) StarAction(library, sheet)
            }
        },
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize()) {
                if (viewModel.kind == MusicDetailKind.Artist) {
                    ArtistPages(viewModel, songs, onOpenAlbum, onPlay, onPlayNext, playingKey, selection)
                } else {
                    val list = rememberLazyListState()
                    SongList(songs, list, viewModel::loadMoreSongs, viewModel::retrySongs, onPlay, onPlayNext, playingKey, total = item.count(), selection)
                }
            }
            SelectionToolbar(selecting) {
                val any = chosen.isNotEmpty()
                if (library != null) ToolbarAction(R.string.music_add_to_sheet, XmIcons.Outlined.PlaylistAdd, any) { adding = chosen }
                if (downloads != null) ToolbarAction(R.string.player_download, XmIcons.Outlined.Download, any) {
                    val queue = chosen
                    endSelection()
                    scope.launch {
                        // Each song downloads in the download quality setting; songs already listed are skipped.
                        val added = queue.count { downloads.download(it, null) }
                        if (added > 0) showMessage(resources.getQuantityString(R.plurals.music_download_added, added, added))
                    }
                }
            }
        }
    }
    adding?.let { AddToSheetSheet(it, onDismiss = { adding = null }, onDone = ::endSelection) }
}

/** Multi-select on a plugin's song list: whether it is on, what is chosen, and how a row changes it. */
private class SongSelection(
    val active: Boolean,
    val selected: Set<String>,
    val onToggle: (MusicItem) -> Unit,
    val onStart: (MusicItem) -> Unit,
)

@Composable
private fun StarAction(library: MusicLibrary, sheet: MusicSheet) {
    val starred by library.starred.collectAsStateWithLifecycle()
    val on = starred.any { it.platform == sheet.platform && it.id == sheet.id }
    val haptics = LocalHapticFeedback.current
    val showMessage = rememberShowMessage()
    val starredText = stringResource(R.string.music_star_done)
    val unstarredText = stringResource(R.string.music_unstar_done)
    IconButton(onClick = {
        if (on) {
            library.unstar(sheet)
            haptics.performHapticFeedback(HapticFeedbackType.ToggleOff)
            showMessage(unstarredText)
        } else {
            library.star(sheet)
            haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
            showMessage(starredText)
        }
    }) {
        Icon(
            if (on) XmIcons.Filled.Star else XmIcons.Outlined.Star,
            stringResource(if (on) R.string.library_unstar else R.string.library_star),
            tint = if (on) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
}

@Composable
private fun ColumnScope.ArtistPages(
    viewModel: MusicDetailViewModel,
    songs: Paged<MusicItem>,
    onOpenAlbum: (MusicAlbum) -> Unit,
    onPlay: (List<MusicItem>, Int) -> Unit,
    onPlayNext: (MusicItem) -> Unit = {},
    playingKey: String?,
    selection: SongSelection,
) {
    val pager = rememberPagerState { 2 }
    val songList = rememberLazyListState()
    val albumList = rememberLazyListState()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    LaunchedEffect(pager.currentPage) { if (pager.currentPage == 1) viewModel.startAlbums() }
    PagerTabs(pager, listOf(stringResource(R.string.music_tab_songs), stringResource(R.string.music_tab_albums)))
    SettlingPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
        Column(Modifier.fillMaxWidth()) {
            if (page == 0) {
                SongList(songs, songList, viewModel::loadMoreSongs, viewModel::retrySongs, onPlay, onPlayNext, playingKey, total = null, selection)
            } else {
                PagedList(albums, albumList, viewModel::loadMoreAlbums, viewModel::retryAlbums) {
                    itemsIndexed(albums.items, key = { index, album -> "$index:${album.id}" }) { _, album ->
                        MusicAlbumRow(album) { onOpenAlbum(album) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.SongList(
    songs: Paged<MusicItem>,
    list: LazyListState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onPlay: (List<MusicItem>, Int) -> Unit,
    onPlayNext: (MusicItem) -> Unit = {},
    playingKey: String?,
    total: Int?,
    selection: SongSelection,
) {
    PagedList(songs, list, onLoadMore, onRetry, bottomSpace = if (selection.active) SelectionToolbarSpace else 8.dp) {
        item(key = "play_all") {
            PlayAllRow(total ?: songs.items.size.takeIf { songs.isEnd }) { if (!selection.active) onPlay(songs.items, 0) }
        }
        itemsIndexed(songs.items, key = { index, song -> "$index:${song.id}" }) { index, song ->
            SongRow(
                song,
                playing = song.key == playingKey,
                onPlayNext = { onPlayNext(song) },
                onLongClick = if (selection.active) null else ({ selection.onStart(song) }),
                trailing = if (selection.active) ({
                    Checkbox(checked = song.key in selection.selected, onCheckedChange = null, Modifier.padding(12.dp))
                }) else null,
            ) { if (selection.active) selection.onToggle(song) else onPlay(songs.items, index) }
        }
    }
}

/** A list that pages itself, with a skeleton before its first page and a retry when that page fails. */
@Composable
internal fun <T> ColumnScope.PagedList(
    paged: Paged<T>,
    list: LazyListState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    bottomSpace: Dp = 8.dp,
    content: LazyListScope.() -> Unit,
) {
    when {
        paged.page == 0 && paged.failed -> ErrorState("generic", onRetry, Modifier.weight(1f))
        paged.page == 0 -> ListSkeleton(Modifier.weight(1f))
        paged.items.isEmpty() -> MusicNothing(Modifier.weight(1f))
        else -> {
            LoadMoreEffect({ list.layoutInfo.visibleItemsInfo.lastOrNull()?.index }, { list.layoutInfo.totalItemsCount }, list, paged.page,
                paged.loading, onLoadMore = onLoadMore)
            LazyColumn(Modifier.weight(1f).fillMaxWidth().widthIn(max = XmLayout.ContentWidth), list, PaddingValues(bottom = bottomSpace)) {
                content()
                item(key = "footer") { PagingFooter(paged.loading, paged.failed, !paged.isEnd, onRetry) }
            }
        }
    }
}

/** The page's main action, as QQ 音乐 places it: under the head, with the count beside it. */
@Composable
internal fun PlayAllRow(count: Int?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(horizontal = XmLayout.PagePadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(XmIcons.Outlined.PlayCircle, contentDescription = null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.music_play_all), style = MaterialTheme.typography.titleMedium)
        if (count != null && count > 0) {
            Spacer(Modifier.width(6.dp))
            Text(
                pluralStringResource(R.plurals.music_song_count, count, count), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The head of an artist page: the avatar centred over its own blurred picture, then the name, the numbers and the
 * introduction the plugin gives. It reads differently from a sheet or an album, whose cover sits beside the title.
 */
@Composable
private fun ArtistHero(artist: MusicArtist) {
    DetailHero(artist.avatar) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = XmLayout.PagePadding).padding(bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MusicCover(
                artist.avatar, Modifier.size(ArtistAvatar).shadow(elevation = 8.dp, shape = CircleShape), CircleShape,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                artist.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
            heroNumbers(artist)?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                )
            }
            artist.description.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The head: blurred cover behind, the cover (round for an artist), the title, who made it and one line of numbers. */
@Composable
private fun MusicHero(item: PluginMedia) {
    if (item is MusicArtist) {
        ArtistHero(item)
        return
    }
    val shape = MaterialTheme.shapes.large
    DetailHero(item.cover()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = XmLayout.PagePadding).padding(bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            MusicCover(item.cover(), Modifier.size(HeroCover).shadow(elevation = 10.dp, shape = shape), shape)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    item.title(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                item.byline()?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                heroNumbers(item)?.let {
                    Text(
                        it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun heroNumbers(item: PluginMedia): String? {
    val parts = buildList {
        item.count()?.takeIf { it > 0 }?.let { add(pluralStringResource(R.plurals.music_song_count, it, it)) }
        when (item) {
            is MusicSheet -> item.playCount?.takeIf { it > 0 }?.let { add(stringResource(R.string.rank_plays, formatCount(it))) }
            is MusicAlbum -> item.date.takeIf { it.isNotBlank() }?.let { add(it) }
            is MusicArtist -> item.fans?.takeIf { it > 0 }?.let { add(stringResource(R.string.music_fans, formatCount(it))) }
            else -> Unit
        }
    }
    return parts.joinToString(" · ").ifEmpty { null }
}

private fun PluginMedia.title(): String = when (this) {
    is MusicSheet -> title
    is MusicAlbum -> title
    is MusicArtist -> name
    is MusicItem -> title
}

private fun PluginMedia.cover(): String? = when (this) {
    is MusicSheet -> coverUrl
    is MusicAlbum -> artwork
    is MusicArtist -> avatar
    is MusicItem -> artwork
}

private fun PluginMedia.byline(): String? = when (this) {
    is MusicSheet -> artist
    is MusicAlbum -> artist
    is MusicArtist -> null
    is MusicItem -> artist
}?.takeIf { it.isNotBlank() }

private fun PluginMedia.count(): Int? = when (this) {
    is MusicSheet -> worksNum
    is MusicAlbum -> worksNum
    is MusicArtist -> worksNum
    is MusicItem -> null
}

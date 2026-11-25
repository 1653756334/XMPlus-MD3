package com.xuleo.xmplus.md3.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
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
    playingKey: String? = null,
) {
    LaunchedEffect(viewModel) { viewModel.start() }
    val item by viewModel.item.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val title = item.title()
    DetailPage(onBack, title, header = { MusicHero(item) }) {
        if (viewModel.kind == MusicDetailKind.Artist) {
            ArtistPages(viewModel, songs, onOpenAlbum, onPlay, playingKey)
        } else {
            val list = rememberLazyListState()
            SongList(songs, list, viewModel::loadMoreSongs, viewModel::retrySongs, onPlay, playingKey, total = item.count())
        }
    }
}

@Composable
private fun ColumnScope.ArtistPages(
    viewModel: MusicDetailViewModel,
    songs: Paged<MusicItem>,
    onOpenAlbum: (MusicAlbum) -> Unit,
    onPlay: (List<MusicItem>, Int) -> Unit,
    playingKey: String?,
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
                SongList(songs, songList, viewModel::loadMoreSongs, viewModel::retrySongs, onPlay, playingKey, total = null)
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
    playingKey: String?,
    total: Int?,
) {
    PagedList(songs, list, onLoadMore, onRetry) {
        item(key = "play_all") { PlayAllRow(total ?: songs.items.size.takeIf { songs.isEnd }) { onPlay(songs.items, 0) } }
        itemsIndexed(songs.items, key = { index, song -> "$index:${song.id}" }) { index, song ->
            SongRow(song, playing = song.key == playingKey) { onPlay(songs.items, index) }
        }
    }
}

/** A list that pages itself, with a skeleton before its first page and a retry when that page fails. */
@Composable
private fun <T> ColumnScope.PagedList(
    paged: Paged<T>,
    list: LazyListState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    when {
        paged.page == 0 && paged.failed -> ErrorState("generic", onRetry, Modifier.weight(1f))
        paged.page == 0 -> ListSkeleton(Modifier.weight(1f))
        paged.items.isEmpty() -> MusicNothing(Modifier.weight(1f))
        else -> {
            LoadMoreEffect({ list.layoutInfo.visibleItemsInfo.lastOrNull()?.index }, { list.layoutInfo.totalItemsCount }, list, paged.page,
                paged.loading, onLoadMore = onLoadMore)
            LazyColumn(Modifier.weight(1f).fillMaxWidth().widthIn(max = XmLayout.ContentWidth), list, PaddingValues(bottom = 8.dp)) {
                content()
                item(key = "footer") { PagingFooter(paged.loading, paged.failed, !paged.isEnd, onRetry) }
            }
        }
    }
}

/** The page's main action, as QQ 音乐 places it: under the head, with the count beside it. */
@Composable
private fun PlayAllRow(count: Int?, onClick: () -> Unit) {
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

/** The head: blurred cover behind, the cover (round for an artist), the title, who made it and one line of numbers. */
@Composable
private fun MusicHero(item: PluginMedia) {
    val artist = item is MusicArtist
    val shape = if (artist) CircleShape else MaterialTheme.shapes.large
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
